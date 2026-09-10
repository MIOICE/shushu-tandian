package com.hmdp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisScriptConfig {

    @Bean("seckillScript")
    public DefaultRedisScript<Long> seckillScript() {
        return script("lua/seckill_reserve.lua");
    }

    @Bean("seckillCompensateScript")
    public DefaultRedisScript<Long> seckillCompensateScript() {
        return script("lua/seckill_compensate.lua");
    }

    @Bean("seckillExpireScript")
    public DefaultRedisScript<Long> seckillExpireScript() {
        return script("lua/seckill_expire.lua");
    }

    @Bean("seckillAckScript")
    public DefaultRedisScript<Long> seckillAckScript() {
        return script("lua/seckill_ack.lua");
    }

    @Bean("rateLimitScript")
    public DefaultRedisScript<Long> rateLimitScript() {
        return script("lua/rate_limit.lua");
    }

    private DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
