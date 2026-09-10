package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.dto.StudentVerificationApplyDTO;
import com.hmdp.dto.StudentVerificationReviewDTO;
import com.hmdp.entity.StudentVerification;

public interface IStudentVerificationService extends IService<StudentVerification> {

    Result apply(StudentVerificationApplyDTO application);

    Result mine();

    Result queryPending(Integer current);

    Result review(Long id, StudentVerificationReviewDTO review);
}
