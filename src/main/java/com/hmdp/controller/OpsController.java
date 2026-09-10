package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.dto.StudentVerificationReviewDTO;
import com.hmdp.security.OpsAuthorizer;
import com.hmdp.service.IStudentVerificationService;
import com.hmdp.service.StockReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops")
public class OpsController {

    private final StockReconciliationService reconciliationService;
    private final IStudentVerificationService verificationService;
    private final OpsAuthorizer opsAuthorizer;

    public OpsController(StockReconciliationService reconciliationService,
                         IStudentVerificationService verificationService,
                         OpsAuthorizer opsAuthorizer) {
        this.reconciliationService = reconciliationService;
        this.verificationService = verificationService;
        this.opsAuthorizer = opsAuthorizer;
    }

    @GetMapping("/stock/reconciliation")
    public Result stockReconciliation(
            @RequestHeader(value = "X-Ops-Token", required = false) String opsToken) {
        opsAuthorizer.requireAuthorized(opsToken);
        return Result.ok(reconciliationService.latest());
    }

    @GetMapping("/student-verifications")
    public Result pendingStudentVerifications(
            @RequestHeader(value = "X-Ops-Token", required = false) String opsToken,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        opsAuthorizer.requireAuthorized(opsToken);
        return verificationService.queryPending(current);
    }

    @PostMapping("/student-verifications/{id}/review")
    public Result reviewStudentVerification(
            @RequestHeader(value = "X-Ops-Token", required = false) String opsToken,
            @PathVariable("id") Long id,
            @RequestBody StudentVerificationReviewDTO review) {
        opsAuthorizer.requireAuthorized(opsToken);
        return verificationService.review(id, review);
    }
}
