package com.hmdp.enums;

public enum StudentVerificationStatus {
    PENDING(0, "待审核"),
    VERIFIED(1, "已认证"),
    REJECTED(2, "已驳回");

    private final int code;
    private final String description;

    StudentVerificationStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static StudentVerificationStatus fromCode(Integer code) {
        if (code != null) {
            for (StudentVerificationStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("未知学生认证状态: " + code);
    }
}
