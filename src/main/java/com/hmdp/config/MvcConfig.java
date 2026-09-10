package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Paths;


@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Value("${shushu.upload.directory:./uploads}")
    private String uploadDirectory;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录拦截器
      registry.addInterceptor(new LoginInterceptor())
              .excludePathPatterns(
                      "/shop/**",
                      "/campus/**",
                      "/shop-type/**",
                      "/uploads/**",
                      "/voucher/**",
                      "/blog/hot",
                      "/user/code",
                      "/user/login",
                      "/voucher-order/payment/callback"
              ).order(1);

        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);




    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadDirectory).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
