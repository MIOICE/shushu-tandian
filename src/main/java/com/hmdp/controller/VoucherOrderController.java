package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.risk.RiskLimit;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    private final IVoucherOrderService voucherOrderService;

    public VoucherOrderController(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    @PostMapping("seckill/{id}")
    @RiskLimit(userLimit = 30, ipLimit = 100, deviceLimit = 50, windowSeconds = 60)
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @GetMapping("/{id}")
    public Result queryOrder(@PathVariable("id") Long orderId) {
        return voucherOrderService.queryOrder(orderId);
    }

    @PostMapping("/{id}/pay")
    public Result payOrder(@PathVariable("id") Long orderId) {
        return voucherOrderService.payOrder(orderId);
    }
}
