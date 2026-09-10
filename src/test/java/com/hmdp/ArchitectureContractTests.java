package com.hmdp;

import com.hmdp.cache.ShopCacheMetrics;
import com.hmdp.controller.VoucherOrderController;
import com.hmdp.enums.ShopCampusSort;
import com.hmdp.entity.StudentVerification;
import com.hmdp.entity.Voucher;
import com.hmdp.enums.StudentVerificationStatus;
import com.hmdp.risk.RiskLimit;
import com.hmdp.enums.VoucherOrderStatus;
import com.hmdp.service.StudentEligibilityPolicy;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
        assertTrue(schema.contains("UNIQUE KEY `uk_pay_no` (`pay_no`)"));
        assertTrue(schema.contains("`close_time` timestamp NULL DEFAULT NULL"));
    }

    @Test
    void campusSchemaSupportsDiscoveryAndHotRanking() throws IOException {
        String schema = resource("db/hmdp.sql");
        assertTrue(schema.contains("CREATE TABLE `tb_campus`"));
        assertTrue(schema.contains("`campus_id` bigint(20) UNSIGNED"));
        assertTrue(schema.contains("`student_discount` tinyint(1) UNSIGNED"));
        assertTrue(schema.contains("INDEX `idx_campus_hot` (`campus_id`, `sold`)"));
        assertTrue(schema.contains("INDEX `idx_campus_score` (`campus_id`, `score`)"));
    }

    @Test
    void campusSortUsesOnlyKnownValuesAndDefaultsToHot() {
        assertEquals(ShopCampusSort.HOT, ShopCampusSort.parse(null));
        assertEquals(ShopCampusSort.HOT, ShopCampusSort.parse("unknown-column"));
        assertEquals(ShopCampusSort.SCORE, ShopCampusSort.parse("score"));
        assertEquals(ShopCampusSort.PRICE, ShopCampusSort.parse(" PRICE "));
    }

    @Test
    void studentOnlyVoucherRequiresVerifiedStudentFromMatchingCampus() {
        Voucher ordinary = new Voucher().setStudentOnly(0);
        assertEquals(null, StudentEligibilityPolicy.validate(ordinary, null));

        Voucher studentOnly = new Voucher().setStudentOnly(1).setCampusId(2L);
        assertEquals("该优惠仅限已认证大学生领取",
                StudentEligibilityPolicy.validate(studentOnly, null));

        StudentVerification wrongCampus = new StudentVerification()
                .setStatus(StudentVerificationStatus.VERIFIED.getCode())
                .setCampusId(1L);
        assertEquals("该优惠仅限指定校区的认证学生领取",
                StudentEligibilityPolicy.validate(studentOnly, wrongCampus));

        StudentVerification eligible = new StudentVerification()
                .setStatus(StudentVerificationStatus.VERIFIED.getCode())
                .setCampusId(2L);
        assertEquals(null, StudentEligibilityPolicy.validate(studentOnly, eligible));
    }

    @Test
    void studentVerificationSchemaProtectsIdentityAndReviewQueue() throws Exception {
        String schema = resource("db/hmdp.sql");
        assertTrue(schema.contains("CREATE TABLE `tb_student_verification`"));
        assertTrue(schema.contains("UNIQUE KEY `uk_student_user` (`user_id`)"));
        assertTrue(schema.contains("UNIQUE KEY `uk_campus_student_no` (`campus_id`, `student_no_hash`)"));
        assertTrue(schema.contains("INDEX `idx_campus_student_status` (`campus_id`, `student_only`, `status`)"));
        assertTrue(StudentVerification.class.getDeclaredField("studentNoHash")
                .isAnnotationPresent(JsonIgnore.class));
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

    @Test
    void orderStateMachineAllowsOnlyLegalTransitions() {
        assertTrue(VoucherOrderStatus.PENDING_PAYMENT.canTransitionTo(VoucherOrderStatus.PAID));
        assertTrue(VoucherOrderStatus.PENDING_PAYMENT.canTransitionTo(VoucherOrderStatus.CANCELLED));
        assertTrue(VoucherOrderStatus.PAID.canTransitionTo(VoucherOrderStatus.USED));
        assertTrue(VoucherOrderStatus.PAID.canTransitionTo(VoucherOrderStatus.REFUNDING));
        assertTrue(VoucherOrderStatus.REFUNDING.canTransitionTo(VoucherOrderStatus.REFUNDED));
        assertTrue(!VoucherOrderStatus.CANCELLED.canTransitionTo(VoucherOrderStatus.PAID));
        assertTrue(!VoucherOrderStatus.REFUNDED.canTransitionTo(VoucherOrderStatus.PAID));
    }

    private String resource(String path) throws IOException {
        return StreamUtils.copyToString(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
    }
}
