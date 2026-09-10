package com.hmdp.task;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class VoucherOrderTimeoutJob {

    private final IVoucherOrderService orderService;

    @Value("${shushu.order.timeout-minutes:15}")
    private long timeoutMinutes;

    @Value("${shushu.order.close-batch-size:100}")
    private int batchSize;

    public VoucherOrderTimeoutJob(IVoucherOrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${shushu.order.scan-delay-ms:30000}")
    public void closeExpiredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<VoucherOrder> expired = orderService.lambdaQuery()
                .select(VoucherOrder::getId)
                .eq(VoucherOrder::getStatus, 1)
                .lt(VoucherOrder::getCreateTime, deadline)
                .orderByAsc(VoucherOrder::getCreateTime)
                .last("LIMIT " + batchSize)
                .list();
        int closed = 0;
        for (VoucherOrder order : expired) {
            if (orderService.closeExpiredOrder(order.getId())) {
                closed++;
            }
        }
        if (closed > 0) {
            log.info("关闭 {} 个超时未支付订单并回补库存", closed);
        }
    }
}
