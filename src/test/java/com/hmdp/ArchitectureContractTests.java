package com.hmdp;

import com.hmdp.cache.ShopCacheMetrics;
import com.hmdp.controller.VoucherOrderController;
import com.hmdp.risk.RiskLimit;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureContractTests {

    @Test
    void seckillScriptChecksEligibilityBeforeDecrementingStock() throws IOException {
        String script = resource("lua/seckill_reserve.lua");
        assertTrue(script.contains("HEXISTS"));
        assertTrue(script.contains("tonumber(stock) <= 0"));
        assertTrue(script.indexOf("HEXISTS") < script.indexOf("DECR"));
        assertTrue(script.indexOf("tonumber(stock) <= 0") < script.indexOf("DECR"));
        assertTrue(script.contains("HSET"));
        assertTrue(script.contains("ZADD"));
        assertTrue(script.contains("PEXPIREAT"));
    }

    @Test
    void compensationScriptsAreOrderIdempotent() throws IOException {
        String release = resource("lua/seckill_compensate.lua");
        String expire = resource("lua/seckill_expire.lua");
        assertTrue(release.contains("reservedOrderId == ARGV[2]"));
        assertTrue(release.contains("HDEL"));
        assertTrue(expire.contains("reservedOrderId == ARGV[2]"));
        assertTrue(expire.contains("'closed:' .. ARGV[2]"));
        assertTrue(release.contains("ZREM"));
        assertTrue(expire.contains("ZREM"));
        String ack = resource("lua/seckill_ack.lua");
        assertTrue(ack.contains("ZREM"));
        assertTrue(ack.contains("HDEL"));
    }

    @Test
    void rateLimitChecksEveryDimensionBeforeRecordingRequest() throws IOException {
        String script = resource("lua/rate_limit.lua");
        assertTrue(script.contains("ZREMRANGEBYSCORE"));
        assertTrue(script.lastIndexOf("ZCARD") < script.lastIndexOf("ZADD"));
        assertTrue(script.contains("return i"));
    }

    @Test
    void cacheMetricsExposeAggregateHitRate() {
        ShopCacheMetrics metrics = new ShopCacheMetrics();
        for (int i = 0; i < 4; i++) {
            metrics.request();
        }
        metrics.localHit();
        metrics.localHit();
        metrics.redisHit();
        metrics.databaseQuery();

        Map<String, Object> snapshot = metrics.snapshot();
        assertEquals(4L, snapshot.get("requests"));
        assertEquals(1L, snapshot.get("databaseQueries"));
        assertEquals(0.75D, (Double) snapshot.get("hitRate"), 0.0001D);
    }

    @Test
    void databaseSchemaHasNoOversellAndNoDuplicateBackstops() throws IOException {
        String schema = resource("db/hmdp.sql");
        assertTrue(schema.contains("UNIQUE KEY `uk_user_voucher` (`user_id`, `voucher_id`)"));
        assertTrue(schema.contains("INDEX `idx_status_create_time` (`status`, `create_time`)"));
    }

    @Test
    void defaultRiskWindowProducesNinetySevenPercentInterceptionTarget() throws NoSuchMethodException {
        RiskLimit limit = VoucherOrderController.class
                .getMethod("seckillVoucher", Long.class)
                .getAnnotation(RiskLimit.class);
        assertEquals(30, limit.userLimit());
        assertEquals(970, 1000 - limit.userLimit());
        assertEquals(60, limit.windowSeconds());
    }

    private String resource(String path) throws IOException {
        return StreamUtils.copyToString(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
    }
}
