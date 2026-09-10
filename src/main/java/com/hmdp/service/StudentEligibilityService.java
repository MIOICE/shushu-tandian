package com.hmdp.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmdp.entity.StudentVerification;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.StudentVerificationMapper;
import com.hmdp.mapper.VoucherMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.STUDENT_ELIGIBILITY_KEY;
import static com.hmdp.utils.RedisConstants.STUDENT_ELIGIBILITY_TTL;

@Service
public class StudentEligibilityService {

    private final VoucherMapper voucherMapper;
    private final StudentVerificationMapper verificationMapper;
    private final StringRedisTemplate redisTemplate;
    private final Cache<Long, Optional<Voucher>> voucherPolicies = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    public StudentEligibilityService(VoucherMapper voucherMapper,
                                     StudentVerificationMapper verificationMapper,
                                     StringRedisTemplate redisTemplate) {
        this.voucherMapper = voucherMapper;
        this.verificationMapper = verificationMapper;
        this.redisTemplate = redisTemplate;
    }

    public String validateClaim(Long userId, Long voucherId) {
        Voucher voucher = voucherPolicies.get(voucherId,
                id -> Optional.ofNullable(voucherMapper.selectById(id))).orElse(null);
        if (voucher == null || !Integer.valueOf(1).equals(voucher.getStudentOnly())) {
            return StudentEligibilityPolicy.validate(voucher, null);
        }
        return StudentEligibilityPolicy.validate(voucher, loadVerification(userId));
    }

    public void invalidateUser(Long userId) {
        redisTemplate.delete(STUDENT_ELIGIBILITY_KEY + userId);
    }

    public void invalidateVoucher(Long voucherId) {
        voucherPolicies.invalidate(voucherId);
    }

    private StudentVerification loadVerification(Long userId) {
        String key = STUDENT_ELIGIBILITY_KEY + userId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return decode(cached, userId);
        }

        StudentVerification verification = verificationMapper.selectOne(new QueryWrapper<StudentVerification>()
                .eq("user_id", userId));
        String encoded = verification == null
                ? "-1|0"
                : verification.getStatus() + "|" + verification.getCampusId();
        redisTemplate.opsForValue().set(key, encoded, STUDENT_ELIGIBILITY_TTL, TimeUnit.MINUTES);
        return verification;
    }

    private StudentVerification decode(String encoded, Long userId) {
        try {
            String[] parts = encoded.split("\\|");
            if (parts.length != 2 || "-1".equals(parts[0])) {
                return null;
            }
            return new StudentVerification()
                    .setUserId(userId)
                    .setStatus(Integer.valueOf(parts[0]))
                    .setCampusId(Long.valueOf(parts[1]));
        } catch (RuntimeException invalidCacheEntry) {
            redisTemplate.delete(STUDENT_ELIGIBILITY_KEY + userId);
            return null;
        }
    }
}
