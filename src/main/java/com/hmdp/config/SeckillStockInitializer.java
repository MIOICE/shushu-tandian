package com.hmdp.config;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.SeckillReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SeckillStockInitializer {

    private final ISeckillVoucherService voucherService;
    private final SeckillReservationService reservationService;

    public SeckillStockInitializer(ISeckillVoucherService voucherService,
                                   SeckillReservationService reservationService) {
        this.voucherService = voucherService;
        this.reservationService = reservationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeActiveVouchers() {
        List<SeckillVoucher> vouchers = voucherService.list();
        for (SeckillVoucher voucher : vouchers) {
            if (voucher.getBeginTime() != null && voucher.getEndTime() != null) {
                reservationService.initialize(voucher, false);
            }
        }
        log.info("已装载 {} 个秒杀活动到 Redis（已有库存不会被覆盖）", vouchers.size());
    }
}
