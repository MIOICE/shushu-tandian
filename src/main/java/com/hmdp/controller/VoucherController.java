package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import com.hmdp.security.OpsAuthorizer;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;
    @Resource
    private OpsAuthorizer opsAuthorizer;

    /**
     * 新增普通券
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping
    public Result addVoucher(
            @RequestHeader(value = "X-Ops-Token", required = false) String opsToken,
            @RequestBody Voucher voucher) {
        opsAuthorizer.requireAuthorized(opsToken);
        return voucherService.addVoucher(voucher);
    }

    /**
     * 新增秒杀券
     * @param voucher 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(
            @RequestHeader(value = "X-Ops-Token", required = false) String opsToken,
            @RequestBody Voucher voucher) {
        opsAuthorizer.requireAuthorized(opsToken);
        return voucherService.addSeckillVoucher(voucher);
    }

    /**
     * 查询店铺的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }

    /**
     * 查询校区内尚未结束的秒杀活动，供用户首页聚合展示。
     */
    @GetMapping("/seckill/active")
    public Result queryActiveSeckillByCampus(@RequestParam("campusId") Long campusId) {
        return voucherService.queryActiveSeckillByCampus(campusId);
    }
}
