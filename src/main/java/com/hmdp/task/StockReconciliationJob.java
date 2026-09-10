package com.hmdp.task;

import com.hmdp.service.StockReconciliationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StockReconciliationJob {

    private final StockReconciliationService reconciliationService;

    public StockReconciliationJob(StockReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(
            initialDelayString = "${shushu.order.reconciliation-initial-delay-ms:30000}",
            fixedDelayString = "${shushu.order.reconciliation-delay-ms:60000}"
    )
    public void reconcile() {
        reconciliationService.refresh();
    }
}
