package com.hmdp.service;

import com.hmdp.dto.StockReconciliationDTO;
import com.hmdp.entity.SeckillVoucher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class StockReconciliationService {

    private final ISeckillVoucherService voucherService;
    private final SeckillReservationService reservationService;
    private volatile List<StockReconciliationDTO> latest = Collections.emptyList();

    public StockReconciliationService(ISeckillVoucherService voucherService,
                                      SeckillReservationService reservationService) {
        this.voucherService = voucherService;
        this.reservationService = reservationService;
    }

    public List<StockReconciliationDTO> refresh() {
        List<SeckillVoucher> vouchers = voucherService.list();
        List<StockReconciliationDTO> results = new ArrayList<>(vouchers.size());
        LocalDateTime checkedAt = LocalDateTime.now();
        for (SeckillVoucher voucher : vouchers) {
            Integer redisStock = reservationService.currentStock(voucher.getVoucherId());
            long pending = reservationService.pendingCount(voucher.getVoucherId());
            Integer difference = redisStock == null ? null : voucher.getStock() - redisStock;
            String status;
            if (redisStock == null) {
                status = "REDIS_NOT_INITIALIZED";
            } else if (difference.longValue() == pending) {
                // 未送达 MQ 的订单已扣 Redis、尚未扣数据库，这部分差值是正常的。
                status = "CONSISTENT";
            } else {
                status = "CHECK_REQUIRED";
                log.warn("秒杀库存需要核查, voucherId={}, dbStock={}, redisStock={}, pending={}",
                        voucher.getVoucherId(), voucher.getStock(), redisStock, pending);
            }
            results.add(new StockReconciliationDTO(
                    voucher.getVoucherId(), voucher.getStock(), redisStock, pending, difference, status, checkedAt));
        }
        latest = Collections.unmodifiableList(results);
        return latest;
    }

    public List<StockReconciliationDTO> latest() {
        return latest.isEmpty() ? refresh() : latest;
    }
}
