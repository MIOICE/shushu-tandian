package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.StockReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops")
public class OpsController {

    private final StockReconciliationService reconciliationService;

    public OpsController(StockReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/stock/reconciliation")
    public Result stockReconciliation() {
        return Result.ok(reconciliationService.latest());
    }
}
