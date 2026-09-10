package com.hmdp.risk;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String dimension) {
        super("请求过于频繁，已触发" + dimension + "维度风控，请稍后再试");
    }
}
