package com.hmdp.cache;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ShopCacheMetrics {
    private final LongAdder requests = new LongAdder();
    private final LongAdder localHits = new LongAdder();
    private final LongAdder redisHits = new LongAdder();
    private final LongAdder databaseQueries = new LongAdder();

    public void request() {
        requests.increment();
    }

    public void localHit() {
        localHits.increment();
    }

    public void redisHit() {
        redisHits.increment();
    }

    public void databaseQuery() {
        databaseQueries.increment();
    }

    public Map<String, Object> snapshot() {
        long requestCount = requests.sum();
        long hits = localHits.sum() + redisHits.sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requests", requestCount);
        result.put("localHits", localHits.sum());
        result.put("redisHits", redisHits.sum());
        result.put("databaseQueries", databaseQueries.sum());
        result.put("hitRate", requestCount == 0 ? 0D : (double) hits / requestCount);
        return result;
    }
}
