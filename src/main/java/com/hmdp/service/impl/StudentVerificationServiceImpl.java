package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.StudentVerificationApplyDTO;
import com.hmdp.dto.StudentVerificationReviewDTO;
import com.hmdp.entity.Campus;
import com.hmdp.entity.StudentVerification;
import com.hmdp.enums.StudentVerificationStatus;
import com.hmdp.mapper.StudentVerificationMapper;
import com.hmdp.service.ICampusService;
import com.hmdp.service.IStudentVerificationService;
import com.hmdp.service.StudentEligibilityService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class StudentVerificationServiceImpl
        extends ServiceImpl<StudentVerificationMapper, StudentVerification>
        implements IStudentVerificationService {

    private final ICampusService campusService;
    private final StudentEligibilityService eligibilityService;

    @Value("${shushu.student.id-salt:}")
    private String studentIdSalt;

    public StudentVerificationServiceImpl(ICampusService campusService,
                                          StudentEligibilityService eligibilityService) {
        this.campusService = campusService;
        this.eligibilityService = eligibilityService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result apply(StudentVerificationApplyDTO application) {
        if (application == null || application.getCampusId() == null || StrUtil.isBlank(application.getStudentNo())) {
            return Result.fail("校区和学号不能为空");
        }
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        String studentNo = application.getStudentNo().trim().toUpperCase(Locale.ROOT);
        if (!studentNo.matches("[A-Z0-9_-]{6,32}")) {
            return Result.fail("学号仅支持 6-32 位字母、数字、下划线或连字符");
        }
        Campus campus = campusService.getById(application.getCampusId());
        if (campus == null || !Integer.valueOf(1).equals(campus.getStatus())) {
            return Result.fail("校区不存在或已停用");
        }
        if (StrUtil.isBlank(studentIdSalt)) {
            return Result.fail("学生认证服务尚未配置安全盐值");
        }

        Long userId = UserHolder.getUser().getId();
        String studentNoHash = DigestUtil.sha256Hex(
                studentIdSalt + "|" + application.getCampusId() + "|" + studentNo);
        StudentVerification occupied = query()
                .eq("campus_id", application.getCampusId())
                .eq("student_no_hash", studentNoHash)
                .one();
        if (occupied != null && !userId.equals(occupied.getUserId())) {
            return Result.fail("该学生身份已被其他账号使用");
        }

        StudentVerification verification = query().eq("user_id", userId).one();
        if (verification != null
                && Integer.valueOf(StudentVerificationStatus.VERIFIED.getCode()).equals(verification.getStatus())) {
            return Result.fail("学生身份已经认证，如需变更请联系运营人员");
        }
        if (verification != null
                && Integer.valueOf(StudentVerificationStatus.PENDING.getCode()).equals(verification.getStatus())
                && application.getCampusId().equals(verification.getCampusId())
                && studentNoHash.equals(verification.getStudentNoHash())) {
            return Result.ok(decorate(verification));
        }

        if (verification == null) {
            verification = new StudentVerification().setUserId(userId);
        }
        verification.setCampusId(application.getCampusId())
                .setStudentNoHash(studentNoHash)
                .setStatus(StudentVerificationStatus.PENDING.getCode())
                .setReviewedAt(null)
                .setRejectReason(null);
        saveOrUpdate(verification);
        invalidateEligibilityAfterCommit(userId);
        return Result.ok(decorate(verification));
    }

    @Override
    public Result mine() {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        StudentVerification verification = query().eq("user_id", UserHolder.getUser().getId()).one();
        return Result.ok(verification == null ? null : decorate(verification));
    }

    @Override
    public Result queryPending(Integer current) {
        int pageNumber = current == null || current < 1 ? 1 : current;
        Page<StudentVerification> page = query()
                .eq("status", StudentVerificationStatus.PENDING.getCode())
                .orderByAsc("create_time")
                .page(new Page<>(pageNumber, 20));
        page.getRecords().forEach(this::decorate);
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result review(Long id, StudentVerificationReviewDTO review) {
        if (review == null || review.getApproved() == null) {
            return Result.fail("审核结果不能为空");
        }
        if (!review.getApproved() && StrUtil.isBlank(review.getRejectReason())) {
            return Result.fail("驳回时必须填写原因");
        }
        StudentVerification verification = getById(id);
        if (verification == null) {
            return Result.fail("认证申请不存在");
        }
        int targetStatus = review.getApproved()
                ? StudentVerificationStatus.VERIFIED.getCode()
                : StudentVerificationStatus.REJECTED.getCode();
        String rejectReason = review.getApproved() ? null : review.getRejectReason().trim();
        if (rejectReason != null && rejectReason.length() > 128) {
            return Result.fail("驳回原因不能超过 128 个字符");
        }
        boolean updated = update()
                .set("status", targetStatus)
                .set("reviewed_at", LocalDateTime.now())
                .set("reject_reason", rejectReason)
                .eq("id", id)
                .eq("status", StudentVerificationStatus.PENDING.getCode())
                .update();
        if (!updated) {
            return Result.fail("该申请已经审核，请勿重复操作");
        }
        invalidateEligibilityAfterCommit(verification.getUserId());
        verification = getById(id);
        return Result.ok(decorate(verification));
    }

    private StudentVerification decorate(StudentVerification verification) {
        verification.setStatusDescription(
                StudentVerificationStatus.fromCode(verification.getStatus()).getDescription());
        Campus campus = campusService.getById(verification.getCampusId());
        if (campus != null) {
            verification.setCampusName(campus.getName());
        }
        return verification;
    }

    private void invalidateEligibilityAfterCommit(Long userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eligibilityService.invalidateUser(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eligibilityService.invalidateUser(userId);
            }
        });
    }
}
