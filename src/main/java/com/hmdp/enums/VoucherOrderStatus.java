package com.hmdp.enums;

public enum VoucherOrderStatus {
    PENDING_PAYMENT(1, "待支付"),
    PAID(2, "已支付"),
    USED(3, "已核销"),
    CANCELLED(4, "已关闭"),
    REFUNDING(5, "退款中"),
    REFUNDED(6, "已退款");

    private final int code;
    private final String description;

    VoucherOrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean canTransitionTo(VoucherOrderStatus target) {
        if (this == PENDING_PAYMENT) {
            return target == PAID || target == CANCELLED;
        }
        if (this == PAID) {
            return target == USED || target == REFUNDING;
        }
        return this == REFUNDING && target == REFUNDED;
    }

    public static VoucherOrderStatus fromCode(Integer code) {
        if (code != null) {
            for (VoucherOrderStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }
}
