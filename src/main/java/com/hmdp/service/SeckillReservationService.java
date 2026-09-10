package com.hmdp.service;

import com.hmdp.entity.SeckillVoucher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;

import static com.hmdp.utils.RedisConstants.SECKILL_BEGIN_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_END_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

@Service
public class SeckillReservationService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> seckillScript;
    private final DefaultRedisScript<Long> compensateScript;
    private final DefaultRedisScript<Long> expireScript;

    public SeckillReservationService(StringRedisTemplate redisTemplate,
                                     @Qualifier("seckillScript") DefaultRedisScript<Long> seckillScript,
                                     @Qualifier("seckillCompensateScript") DefaultRedisScript<Long> compensateScript,
                                     @Qualifier("seckillExpireScript") DefaultRedisScript<Long> expireScript) {
        this.redisTemplate = redisTemplate;
        this.seckillScript = seckillScript;
        this.compensateScript = compensateScript;
        this.expireScript = expireScript;
    }

    public long reserve(Long voucherId, Long userId, Long orderId) {
        Long result = redisTemplate.execute(
                seckillScript,
                Arrays.asList(stockKey(voucherId), orderKey(voucherId), beginKey(voucherId), endKey(voucherId)),
                userId.toString(), orderId.toString(), Long.toString(System.currentTimeMillis())
        );
        return result == null ? 5L : result;
    }

    public boolean compensate(Long voucherId, Long userId, Long orderId) {
        Long result = redisTemplate.execute(
                compensateScript,
                Arrays.asList(stockKey(voucherId), orderKey(voucherId)),
                userId.toString(), orderId.toString()
        );
        return result != null && result == 1L;
    }

    public boolean restoreExpired(Long voucherId, Long userId, Long orderId) {
        Long result = redisTemplate.execute(
                expireScript,
                Arrays.asList(stockKey(voucherId), orderKey(voucherId)),
                userId.toString(), orderId.toString()
        );
        return result != null && result == 1L;
    }

    public void initialize(SeckillVoucher voucher, boolean overwriteStock) {
        Long voucherId = voucher.getVoucherId();
        String stock = Integer.toString(voucher.getStock());
        if (overwriteStock) {
            redisTemplate.opsForValue().set(stockKey(voucherId), stock);
            redisTemplate.delete(orderKey(voucherId));
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
}
