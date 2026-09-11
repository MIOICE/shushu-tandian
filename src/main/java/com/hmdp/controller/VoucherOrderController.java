package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.PaymentCallbackDTO;
import com.hmdp.risk.RiskLimit;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.security.OpsAuthorizer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

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
    private final OpsAuthorizer opsAuthorizer;

    public VoucherOrderController(IVoucherOrderService voucherOrderService,
                                  OpsAuthorizer opsAuthorizer) {
        this.voucherOrderService = voucherOrderService;
        this.opsAuthorizer = opsAuthorizer;
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

    @GetMapping("/me")
    public Result queryMyOrders(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "status", required = false) Integer status) {
        return voucherOrderService.queryMyOrders(current, status);
    }

    @PostMapping("/{id}/cancel")
    public Result cancelOrder(@PathVariable("id") Long orderId) {
        return voucherOrderService.cancelOrder(orderId);
    }

    @PostMapping("/{id}/use")
    public Result useOrder(
            @RequestHeader(value = "X-Ops-Token", required = false) String opsToken,
            @PathVariable("id") Long orderId) {
        opsAuthorizer.requireAuthorized(opsToken);
        return voucherOrderService.useOrder(orderId);
    }

    @PostMapping("/{id}/pay")
    public Result payOrder(@PathVariable("id") Long orderId) {
        return voucherOrderService.payOrder(orderId);
    }

    @PostMapping("/payment/callback")
    @RiskLimit(userLimit = 300, ipLimit = 300, deviceLimit = 300, windowSeconds = 60)
    public Result paymentCallback(
            @RequestHeader(value = "X-Payment-Callback-Token", required = false) String callbackToken,
            @RequestBody PaymentCallbackDTO callback) {
        return voucherOrderService.handlePaymentCallback(callbackToken, callback);
    }
}
