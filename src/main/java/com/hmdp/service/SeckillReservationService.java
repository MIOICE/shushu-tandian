package com.hmdp.service;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.event.VoucherOrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.SECKILL_BEGIN_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_END_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_EVENT_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_PENDING_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

@Slf4j
@Service
public class SeckillReservationService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> seckillScript;
    private final DefaultRedisScript<Long> compensateScript;
    private final DefaultRedisScript<Long> expireScript;
    private final DefaultRedisScript<Long> ackScript;

    public SeckillReservationService(StringRedisTemplate redisTemplate,
                                     @Qualifier("seckillScript") DefaultRedisScript<Long> seckillScript,
                                     @Qualifier("seckillCompensateScript") DefaultRedisScript<Long> compensateScript,
                                     @Qualifier("seckillExpireScript") DefaultRedisScript<Long> expireScript,
                                     @Qualifier("seckillAckScript") DefaultRedisScript<Long> ackScript) {
        this.redisTemplate = redisTemplate;
        this.seckillScript = seckillScript;
        this.compensateScript = compensateScript;
        this.expireScript = expireScript;
        this.ackScript = ackScript;
    }

    public long reserve(Long voucherId, Long userId, Long orderId) {
        Long result = redisTemplate.execute(
                seckillScript,
                Arrays.asList(stockKey(voucherId), orderKey(voucherId), beginKey(voucherId), endKey(voucherId),
                        pendingKey(voucherId), eventKey(voucherId)),
                userId.toString(), orderId.toString(), Long.toString(System.currentTimeMillis())
        );
        return result == null ? 5L : result;
    }

    public boolean compensate(Long voucherId, Long userId, Long orderId) {
        Long result = redisTemplate.execute(
                compensateScript,
                Arrays.asList(stockKey(voucherId), orderKey(voucherId), pendingKey(voucherId), eventKey(voucherId)),
                userId.toString(), orderId.toString()
        );
        return result != null && result == 1L;
    }

    public boolean restoreExpired(Long voucherId, Long userId, Long orderId) {
        Long result = redisTemplate.execute(
                expireScript,
                Arrays.asList(stockKey(voucherId), orderKey(voucherId), pendingKey(voucherId), eventKey(voucherId)),
                userId.toString(), orderId.toString()
        );
        return result != null && result == 1L;
    }

    public void acknowledge(Long voucherId, Long orderId) {
        acknowledge(voucherId, orderId.toString());
    }

    private void acknowledge(Long voucherId, String orderId) {
        redisTemplate.execute(
                ackScript,
                Arrays.asList(pendingKey(voucherId), eventKey(voucherId)),
                orderId
        );
    }

    public List<VoucherOrderEvent> findPendingEvents(Long voucherId, long acceptedBefore, int limit) {
        Set<String> orderIds = redisTemplate.opsForZSet()
                .rangeByScore(pendingKey(voucherId), 0, acceptedBefore, 0, limit);
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> hashKeys = new ArrayList<>(orderIds);
        List<Object> payloads = redisTemplate.opsForHash().multiGet(eventKey(voucherId), hashKeys);
        List<VoucherOrderEvent> events = new ArrayList<>(orderIds.size());
        int index = 0;
        for (String orderId : orderIds) {
            Object payload = payloads == null || index >= payloads.size() ? null : payloads.get(index);
            index++;
            if (payload == null) {
                acknowledge(voucherId, orderId);
                continue;
            }
            try {
                String[] parts = payload.toString().split("\\|");
                events.add(new VoucherOrderEvent(
                        Long.valueOf(orderId), Long.valueOf(parts[0]), voucherId, Long.valueOf(parts[1])));
            } catch (RuntimeException exception) {
                log.error("待投递秒杀事件格式错误，清理无效记录, voucherId={}, orderId={}", voucherId, orderId, exception);
                acknowledge(voucherId, orderId);
            }
        }
        return events;
    }

    public void initialize(SeckillVoucher voucher, boolean overwriteStock) {
        Long voucherId = voucher.getVoucherId();
        String stock = Integer.toString(voucher.getStock());
        if (overwriteStock) {
            redisTemplate.opsForValue().set(stockKey(voucherId), stock);
            redisTemplate.delete(Arrays.asList(orderKey(voucherId), pendingKey(voucherId), eventKey(voucherId)));
        } else {
            redisTemplate.opsForValue().setIfAbsent(stockKey(voucherId), stock);
        }
        long begin = voucher.getBeginTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long end = voucher.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        redisTemplate.opsForValue().set(beginKey(voucherId), Long.toString(begin));
        redisTemplate.opsForValue().set(endKey(voucherId), Long.toString(end));
        Date expireAt = new Date(end + 24L * 60 * 60 * 1000);
        redisTemplate.expireAt(stockKey(voucherId), expireAt);
        redisTemplate.expireAt(beginKey(voucherId), expireAt);
        redisTemplate.expireAt(endKey(voucherId), expireAt);
    }

    private String stockKey(Long voucherId) {
        return SECKILL_STOCK_KEY + "{" + voucherId + "}";
    }

    private String orderKey(Long voucherId) {
        return SECKILL_ORDER_KEY + "{" + voucherId + "}";
    }

    private String beginKey(Long voucherId) {
        return SECKILL_BEGIN_KEY + "{" + voucherId + "}";
    }

    private String endKey(Long voucherId) {
        return SECKILL_END_KEY + "{" + voucherId + "}";
    }

    private String pendingKey(Long voucherId) {
        return SECKILL_PENDING_KEY + "{" + voucherId + "}";
    }

    private String eventKey(Long voucherId) {
        return SECKILL_EVENT_KEY + "{" + voucherId + "}";
    }
}
