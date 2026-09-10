package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.event.VoucherOrderEvent;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrderEvent event);

    boolean closeExpiredOrder(Long orderId);

    Result queryOrder(Long orderId);

    Result payOrder(Long orderId);

}
