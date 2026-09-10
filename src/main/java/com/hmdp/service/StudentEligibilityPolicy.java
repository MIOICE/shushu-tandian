package com.hmdp.service;

import com.hmdp.entity.StudentVerification;
import com.hmdp.entity.Voucher;
import com.hmdp.enums.StudentVerificationStatus;

/**
 * 无状态的学生专享券资格规则，便于独立测试。
 */
public final class StudentEligibilityPolicy {

    private StudentEligibilityPolicy() {
    }

    public static String validate(Voucher voucher, StudentVerification verification) {
        if (voucher == null) {
            return "优惠券不存在";
        }
        if (!Integer.valueOf(1).equals(voucher.getStudentOnly())) {
            return null;
        }
        if (verification == null
                || !Integer.valueOf(StudentVerificationStatus.VERIFIED.getCode()).equals(verification.getStatus())) {
            return "该优惠仅限已认证大学生领取";
        }
        if (voucher.getCampusId() != null && !voucher.getCampusId().equals(verification.getCampusId())) {
            return "该优惠仅限指定校区的认证学生领取";
        }
        return null;
    }
}
