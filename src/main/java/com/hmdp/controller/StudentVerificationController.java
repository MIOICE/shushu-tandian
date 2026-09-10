package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.dto.StudentVerificationApplyDTO;
import com.hmdp.risk.RiskLimit;
import com.hmdp.service.IStudentVerificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student-verification")
public class StudentVerificationController {

    private final IStudentVerificationService verificationService;

    public StudentVerificationController(IStudentVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping
    @RiskLimit(userLimit = 5, ipLimit = 30, deviceLimit = 10, windowSeconds = 3600)
    public Result apply(@RequestBody StudentVerificationApplyDTO application) {
        return verificationService.apply(application);
    }

    @GetMapping("/me")
    public Result mine() {
        return verificationService.mine();
    }
}
