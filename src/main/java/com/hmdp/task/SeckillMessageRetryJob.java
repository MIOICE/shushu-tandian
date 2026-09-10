package com.hmdp.task;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.event.VoucherOrderEvent;
import com.hmdp.mq.ShushuMessagePublisher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.SeckillReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class SeckillMessageRetryJob {

    private final ISeckillVoucherService voucherService;
    private final SeckillReservationService reservationService;
    private final ShushuMessagePublisher messagePublisher;

    @Value("${shushu.order.message-retry-delay-ms:5000}")
    private long retryDelayMillis;

    @Value("${shushu.order.message-retry-batch-size:100}")
    private int batchSize;

    public SeckillMessageRetryJob(ISeckillVoucherService voucherService,
                                  SeckillReservationService reservationService,
                                  ShushuMessagePublisher messagePublisher) {
        this.voucherService = voucherService;
        this.reservationService = reservationService;
        this.messagePublisher = messagePublisher;
    }

    @Scheduled(fixedDelayString = "${shushu.order.message-retry-scan-delay-ms:5000}")
    public void retryUnconfirmedMessages() {
        // Redis 待投递数据会保留到活动结束后一天，因此仅扫描这一时间范围内的活动。
        List<SeckillVoucher> vouchers = voucherService.lambdaQuery()
                .select(SeckillVoucher::getVoucherId)
                .gt(SeckillVoucher::getEndTime, LocalDateTime.now().minusDays(1))
                .list();
        long acceptedBefore = System.currentTimeMillis() - retryDelayMillis;
        int retried = 0;
        for (SeckillVoucher voucher : vouchers) {
            List<VoucherOrderEvent> events = reservationService.findPendingEvents(
                    voucher.getVoucherId(), acceptedBefore, batchSize);
            for (VoucherOrderEvent event : events) {
                // 待处理记录直到消费者完成数据库事务后才删除，因此这里同时覆盖发送失败和消费失败。
                messagePublisher.publishOrder(event);
                retried++;
            }
        }
        if (retried > 0) {
            log.warn("已重新投递 {} 条未获 Broker 确认的秒杀订单消息", retried);
        }
    }
}
