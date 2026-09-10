package com.hmdp.risk;

import cn.hutool.crypto.digest.DigestUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Aspect
@Order(-100)
@Component
public class RiskLimitAspect {

    @Value("${shushu.risk.enabled:true}")
    private boolean enabled;

    private final HttpServletRequest request;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    public RiskLimitAspect(HttpServletRequest request,
                           StringRedisTemplate redisTemplate,
                           @Qualifier("rateLimitScript") DefaultRedisScript<Long> rateLimitScript) {
        this.request = request;
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
    }

    @Around("@annotation(limit)")
    public Object guard(ProceedingJoinPoint joinPoint, RiskLimit limit) throws Throwable {
        if (!enabled) {
            return joinPoint.proceed();
        }
        long now = System.currentTimeMillis();
        long windowMillis = limit.windowSeconds() * 1000L;
        String ip = clientIp();
        String device = deviceFingerprint(ip);
        UserDTO user = UserHolder.getUser();
        String userId = user == null ? "anonymous:" + ip : user.getId().toString();
        String member = now + ":" + UUID.randomUUID();

        Long rejectedDimension = redisTemplate.execute(
                rateLimitScript,
                Arrays.asList(
                        key("user", userId),
                        key("ip", ip),
                        key("device", device)
                ),
                Long.toString(now),
                Long.toString(windowMillis),
                member,
                Integer.toString(limit.userLimit()),
                Integer.toString(limit.ipLimit()),
                Integer.toString(limit.deviceLimit()),
                Integer.toString(limit.windowSeconds() + 1)
        );
        if (rejectedDimension == null) {
            throw new IllegalStateException("限流服务暂不可用");
        }
        if (rejectedDimension > 0) {
            String dimension = rejectedDimension == 1 ? "用户" : rejectedDimension == 2 ? "IP" : "设备";
            log.warn("风控拦截: dimension={}, userId={}, ip={}", dimension, userId, ip);
            throw new RateLimitExceededException(dimension);
        }
        return joinPoint.proceed();
    }

    private String clientIp() {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.trim().isEmpty() ? request.getRemoteAddr() : realIp.trim();
    }

    private String deviceFingerprint(String ip) {
        String supplied = request.getHeader("X-Device-Fingerprint");
        String source = supplied == null || supplied.trim().isEmpty()
                ? ip + "|" + String.valueOf(request.getHeader("User-Agent"))
                : supplied.trim();
        return DigestUtil.sha256Hex(source);
    }

    private String key(String dimension, String subject) {
        return RedisConstants.RATE_LIMIT_KEY + dimension + ":" + DigestUtil.sha256Hex(subject);
    }
}
