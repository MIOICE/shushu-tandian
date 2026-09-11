package com.hmdp.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_LOCK_KEY;

/**
 * Serializes duplicate RocketMQ deliveries for the same order across application instances.
 */
@Service
public class OrderCreationLockService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public OrderCreationLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String tryLock(Long orderId) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey(orderId), token, LOCK_TTL);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    public void unlock(Long orderId, String token) {
        redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey(orderId)), token);
    }

    private String lockKey(Long orderId) {
        return SECKILL_ORDER_LOCK_KEY + "{" + orderId + "}";
    }
}
