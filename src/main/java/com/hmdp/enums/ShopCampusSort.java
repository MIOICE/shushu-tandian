package com.hmdp.enums;

import java.util.Locale;

/**
 * 校园店铺列表允许的排序方式。
 */
public enum ShopCampusSort {
    HOT,
    SCORE,
    PRICE;

    public static ShopCampusSort parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return HOT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return HOT;
        }
    }
}
