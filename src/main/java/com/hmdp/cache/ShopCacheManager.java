package com.hmdp.cache;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class ShopCacheManager {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final Cache<Long, Shop> localCache;
    private final StringRedisTemplate redisTemplate;
    private final ShopCacheMetrics metrics;

    @Value("${shushu.cache.redis-ttl-minutes:30}")
    private long redisTtlMinutes;

    public ShopCacheManager(Cache<Long, Shop> localCache,
                            StringRedisTemplate redisTemplate,
                            ShopCacheMetrics metrics) {
        this.localCache = localCache;
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
    }

    public Shop get(Long id, Function<Long, Shop> databaseFallback) {
        metrics.request();
        Shop local = localCache.getIfPresent(id);
        if (local != null) {
            metrics.localHit();
            return local;
        }

        String key = CACHE_SHOP_KEY + id;
        for (int attempt = 0; attempt < 25; attempt++) {
            String json = redisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                Shop shop = JSONUtil.toBean(json, Shop.class);
                localCache.put(id, shop);
                metrics.redisHit();
                return shop;
            }
            if (json != null) {
                metrics.redisHit();
                return null;
            }

            String lockKey = LOCK_SHOP_KEY + id;
            String token = UUID.randomUUID().toString();
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, 10, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    String secondRead = redisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(secondRead)) {
                        Shop shop = JSONUtil.toBean(secondRead, Shop.class);
                        localCache.put(id, shop);
                        metrics.redisHit();
                        return shop;
                    }
                    metrics.databaseQuery();
                    Shop shop = databaseFallback.apply(id);
                    if (shop == null) {
                        redisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                        return null;
                    }
                    long ttlWithJitter = redisTtlMinutes + ThreadLocalRandom.current().nextLong(1, 6);
                    redisTemplate.opsForValue().set(
                            key, JSONUtil.toJsonStr(shop), ttlWithJitter, TimeUnit.MINUTES);
                    localCache.put(id, shop);
                    return shop;
                } finally {
                    redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), token);
                }
            }

            try {
                Thread.sleep(40);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("缓存等待被中断", exception);
            }
        }
        log.warn("等待店铺缓存重建超时, shopId={}", id);
        metrics.databaseQuery();
        return databaseFallback.apply(id);
    }

    public void evict(Long id) {
        localCache.invalidate(id);
        try {
            redisTemplate.delete(CACHE_SHOP_KEY + id);
        } catch (RuntimeException exception) {
            log.error("删除 Redis 店铺缓存失败, shopId={}", id, exception);
        }
    }

    public ShopCacheMetrics metrics() {
        return metrics;
    }
}
