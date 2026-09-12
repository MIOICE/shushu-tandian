package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.dto.Result;
import com.hmdp.dto.PaymentCallbackDTO;
import com.hmdp.enums.VoucherOrderStatus;
import com.hmdp.event.VoucherOrderEvent;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.ShushuMessagePublisher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.SeckillReservationService;
import com.hmdp.service.StudentEligibilityService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.risk.UnauthorizedPaymentCallbackException;
import com.hmdp.risk.InvalidPaymentCallbackException;
import com.hmdp.risk.PaymentCallbackRetryException;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
    private final StudentEligibilityService eligibilityService;

    @Value("${shushu.payment.callback-token:}")
    private String paymentCallbackToken;

    public VoucherOrderServiceImpl(RedisIdWorker redisIdWorker,
                                   SeckillReservationService reservationService,
                                   ISeckillVoucherService seckillVoucherService,
                                   ShushuMessagePublisher messagePublisher,
                                   StudentEligibilityService eligibilityService) {
        this.redisIdWorker = redisIdWorker;
        this.reservationService = reservationService;
        this.seckillVoucherService = seckillVoucherService;
        this.messagePublisher = messagePublisher;
        this.eligibilityService = eligibilityService;
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Long userId = UserHolder.getUser().getId();
        String eligibilityError = eligibilityService.validateClaim(userId, voucherId);
        if (eligibilityError != null) {
            return Result.fail(eligibilityError);
        }
        Long orderId = redisIdWorker.nextId("order");
        long result = reservationService.reserve(voucherId, userId, orderId);
        if (result != 0L) {
            return Result.fail(reservationError(result));
        }

        VoucherOrderEvent event = new VoucherOrderEvent(orderId, userId, voucherId, System.currentTimeMillis());
        messagePublisher.publishOrder(event);
        // 雪花订单号超过 JavaScript 安全整数范围，按字符串返回，避免前端轮询到错误订单。
        return Result.ok(orderId.toString());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrderEvent event) {
        if (getById(event.getOrderId()) != null) {
            return;
        }
        long existing = count(query().eq("user_id", event.getUserId())
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
        order.setStatus(VoucherOrderStatus.PENDING_PAYMENT.getCode());
        order.setPayType(1);
        order.setCreateTime(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.getAcceptedAt()), ZoneId.systemDefault()));
        save(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeExpiredOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (order == null || order.getStatus() == null
                || order.getStatus() != VoucherOrderStatus.PENDING_PAYMENT.getCode()) {
            return false;
        }
        boolean closed = update().set("status", VoucherOrderStatus.CANCELLED.getCode())
                .set("close_time", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", VoucherOrderStatus.PENDING_PAYMENT.getCode())
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
        order.setStatusDescription(VoucherOrderStatus.fromCode(order.getStatus()).getDescription());
        return Result.ok(order);
    }

    @Override
    public Result queryMyOrders(Integer current, Integer status) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        if (status != null) {
            try {
                VoucherOrderStatus.fromCode(status);
            } catch (IllegalArgumentException invalidStatus) {
                return Result.fail("订单状态不合法");
            }
        }
        int pageNumber = current == null || current < 1 ? 1 : current;
        Page<VoucherOrder> page = query()
                .eq("user_id", UserHolder.getUser().getId())
                .eq(status != null, "status", status)
                .orderByDesc("create_time")
                .page(new Page<>(pageNumber, 10));
        page.getRecords().forEach(this::fillStatusDescription);
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result cancelOrder(Long orderId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        VoucherOrder order = getById(orderId);
        if (order == null || !UserHolder.getUser().getId().equals(order.getUserId())) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != VoucherOrderStatus.PENDING_PAYMENT.getCode()) {
            return Result.fail("只有待支付订单可以取消");
        }
        return closeExpiredOrder(orderId) ? Result.ok() : Result.fail("订单状态已变化，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result useOrder(Long orderId) {
        VoucherOrder order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() == VoucherOrderStatus.USED.getCode()) {
            return Result.ok();
        }
        if (order.getStatus() != VoucherOrderStatus.PAID.getCode()) {
            return Result.fail("只有已支付订单可以核销");
        }
        boolean used = update()
                .set("status", VoucherOrderStatus.USED.getCode())
                .set("use_time", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", VoucherOrderStatus.PAID.getCode())
                .update();
        return used ? Result.ok() : Result.fail("订单状态已变化，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result payOrder(Long orderId) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        VoucherOrder order = getById(orderId);
        if (order == null || !order.getUserId().equals(UserHolder.getUser().getId())) {
            return Result.fail("订单不存在");
        }
        return markPaid(order, "MOCK-" + orderId, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result handlePaymentCallback(String callbackToken, PaymentCallbackDTO callback) {
        if (!validCallbackToken(callbackToken)) {
            throw new UnauthorizedPaymentCallbackException();
        }
        if (callback == null || callback.getOrderId() == null
                || callback.getPaymentNo() == null || callback.getPaymentNo().trim().isEmpty()) {
            throw new InvalidPaymentCallbackException("支付回调参数不完整");
        }
        String paymentNo = callback.getPaymentNo().trim();
        if (paymentNo.length() > 64) {
            throw new InvalidPaymentCallbackException("支付流水号长度不能超过 64");
        }
        LocalDateTime paidAt = callback.getPaidAt() == null ? LocalDateTime.now() : callback.getPaidAt();
        if (paidAt.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new InvalidPaymentCallbackException("支付时间不能晚于当前时间 5 分钟以上");
        }
        VoucherOrder order = getById(callback.getOrderId());
        if (order == null) {
            throw new PaymentCallbackRetryException();
        }
        return markPaid(order, paymentNo, paidAt);
    }

    private Result markPaid(VoucherOrder order, String paymentNo, LocalDateTime paidAt) {
        VoucherOrderStatus current = VoucherOrderStatus.fromCode(order.getStatus());
        if (current == VoucherOrderStatus.PAID) {
            return paymentNo.equals(order.getPayNo())
                    ? Result.ok(paymentNo)
                    : Result.fail("订单已由其他支付流水完成");
        }
        if (!current.canTransitionTo(VoucherOrderStatus.PAID)) {
            return Result.fail("订单当前状态不允许支付: " + current.getDescription());
        }
        boolean paid = update()
                .set("status", VoucherOrderStatus.PAID.getCode())
                .set("pay_no", paymentNo)
                .set("pay_time", paidAt)
                .eq("id", order.getId())
                .eq("status", VoucherOrderStatus.PENDING_PAYMENT.getCode())
                .update();
        if (paid) {
            return Result.ok(paymentNo);
        }
        VoucherOrder latest = getById(order.getId());
        if (latest != null && latest.getStatus() == VoucherOrderStatus.PAID.getCode()
                && paymentNo.equals(latest.getPayNo())) {
            return Result.ok(paymentNo);
        }
        return Result.fail("支付与关单发生竞争，订单未支付");
    }

    private boolean validCallbackToken(String provided) {
        if (provided == null || paymentCallbackToken == null || paymentCallbackToken.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                paymentCallbackToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    private void fillStatusDescription(VoucherOrder order) {
        order.setStatusDescription(VoucherOrderStatus.fromCode(order.getStatus()).getDescription());
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
