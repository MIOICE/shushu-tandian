package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.dto.Result;
import com.hmdp.event.VoucherOrderEvent;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.ShushuMessagePublisher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.SeckillReservationService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final RedisIdWorker redisIdWorker;
    private final SeckillReservationService reservationService;
    private final ISeckillVoucherService seckillVoucherService;
    private final ShushuMessagePublisher messagePublisher;

    public VoucherOrderServiceImpl(RedisIdWorker redisIdWorker,
                                   SeckillReservationService reservationService,
                                   ISeckillVoucherService seckillVoucherService,
                                   ShushuMessagePublisher messagePublisher) {
        this.redisIdWorker = redisIdWorker;
        this.reservationService = reservationService;
        this.seckillVoucherService = seckillVoucherService;
        this.messagePublisher = messagePublisher;
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nextId("order");
        long result = reservationService.reserve(voucherId, userId, orderId);
        if (result != 0L) {
            return Result.fail(reservationError(result));
        }

        VoucherOrderEvent event = new VoucherOrderEvent(orderId, userId, voucherId, System.currentTimeMillis());
        if (!messagePublisher.publishOrder(event)) {
            return Result.fail("下单请求繁忙，请稍后重试");
        }
        return Result.ok(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrderEvent event) {
        if (getById(event.getOrderId()) != null) {
            return;
        }
        int existing = count(query().eq("user_id", event.getUserId())
                .eq("voucher_id", event.getVoucherId()).getWrapper());
        if (existing > 0) {
            log.info("忽略重复订单消息, userId={}, voucherId={}", event.getUserId(), event.getVoucherId());
            reservationService.restoreExpired(event.getVoucherId(), event.getUserId(), event.getOrderId());
            return;
        }

        boolean stockUpdated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", event.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!stockUpdated) {
            log.error("数据库库存不足，回补 Redis 预占, orderId={}", event.getOrderId());
            reservationService.compensate(event.getVoucherId(), event.getUserId(), event.getOrderId());
            return;
        }

        VoucherOrder order = new VoucherOrder();
        order.setId(event.getOrderId());
        order.setUserId(event.getUserId());
        order.setVoucherId(event.getVoucherId());
        order.setStatus(1);
        order.setPayType(1);
        order.setCreateTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.getAcceptedAt()), ZoneId.systemDefault()));
        save(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeExpiredOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (order == null || order.getStatus() == null || order.getStatus() != 1) {
            return false;
        }
        boolean closed = update().set("status", 4)
                .eq("id", orderId)
                .eq("status", 1)
                .update();
        if (!closed) {
            return false;
        }
        boolean stockRestored = seckillVoucherService.update()
                .setSql("stock = stock + 1")
                .eq("voucher_id", order.getVoucherId())
                .update();
        if (!stockRestored) {
            throw new IllegalStateException("订单对应的秒杀券不存在，关闭操作已回滚");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                reservationService.restoreExpired(order.getVoucherId(), order.getUserId(), order.getId());
            }
        });
        return true;
    }

    @Override
    public Result queryOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (UserHolder.getUser() == null || !order.getUserId().equals(UserHolder.getUser().getId())) {
            return Result.fail("无权查看该订单");
        }
        return Result.ok(order);
    }

    @Override
    public Result payOrder(Long orderId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        boolean paid = update().set("status", 2)
                .set("pay_time", LocalDateTime.now())
                .eq("id", orderId)
                .eq("user_id", UserHolder.getUser().getId())
                .eq("status", 1)
                .update();
        return paid ? Result.ok() : Result.fail("订单不存在、已支付或已关闭");
    }

    private String reservationError(long code) {
        if (code == 1L) {
            return "优惠券已抢完";
        }
        if (code == 2L) {
            return "每位同学限购一份";
        }
        if (code == 3L) {
            return "秒杀尚未开始";
        }
        if (code == 4L) {
            return "秒杀已经结束";
        }
        return "秒杀活动未初始化，请稍后重试";
    }
}
