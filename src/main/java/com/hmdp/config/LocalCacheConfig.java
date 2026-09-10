package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmdp.entity.Shop;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class LocalCacheConfig {

    @Bean
    public Cache<Long, Shop> shopLocalCache(
            @Value("${shushu.cache.maximum-size:10000}") long maximumSize,
            @Value("${shushu.cache.local-ttl-minutes:5}") long ttlMinutes) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}
