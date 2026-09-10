package com.hmdp.risk;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RiskLimit {
    int userLimit() default 30;

    int ipLimit() default 100;

    int deviceLimit() default 50;

    int windowSeconds() default 60;
}
