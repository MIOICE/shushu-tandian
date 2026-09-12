package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Campus;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.ICampusService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.SeckillReservationService;
import com.hmdp.service.StudentEligibilityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.util.List;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private SeckillReservationService reservationService;

    @Resource
    private StudentEligibilityService eligibilityService;

    @Resource
    private IShopService shopService;

    @Resource
    private ICampusService campusService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    public Result queryActiveSeckillByCampus(Long campusId) {
        Campus campus = campusId == null ? null : campusService.getById(campusId);
        if (campus == null || !Integer.valueOf(1).equals(campus.getStatus())) {
            return Result.fail("校区不存在或未开放");
        }
        List<Voucher> vouchers = getBaseMapper().queryActiveSeckillByCampus(campusId, LocalDateTime.now());
        vouchers.forEach(voucher -> {
            Integer redisStock = reservationService.currentStock(voucher.getId());
            if (redisStock != null) {
                voucher.setStock(redisStock);
            }
        });
        return Result.ok(vouchers);
    }

    @Override
    public Result addVoucher(Voucher voucher) {
        String error = validateVoucher(voucher, false);
        if (error != null) {
            return Result.fail(error);
        }
        voucher.setType(0);
        if (voucher.getStatus() == null) {
            voucher.setStatus(1);
        }
        save(voucher);
        eligibilityService.invalidateVoucher(voucher.getId());
        return Result.ok(voucher.getId());
    }

    @Override
    @Transactional
    public Result addSeckillVoucher(Voucher voucher) {
        String error = validateVoucher(voucher, true);
        if (error != null) {
            return Result.fail(error);
        }
        voucher.setType(1);
        if (voucher.getStatus() == null) {
            voucher.setStatus(1);
        }
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eligibilityService.invalidateVoucher(voucher.getId());
                reservationService.initialize(seckillVoucher, true);
            }
        });
        return Result.ok(voucher.getId());
    }

    private String validateVoucher(Voucher voucher, boolean seckill) {
        if (voucher == null || voucher.getShopId() == null || shopService.getById(voucher.getShopId()) == null) {
            return "关联店铺不存在";
        }
        if (voucher.getTitle() == null || voucher.getTitle().trim().isEmpty()
                || voucher.getPayValue() == null || voucher.getActualValue() == null
                || voucher.getPayValue() < 0 || voucher.getActualValue() <= 0
                || voucher.getPayValue() > voucher.getActualValue()) {
            return "优惠券标题或金额配置不合法";
        }
        if (Integer.valueOf(1).equals(voucher.getStudentOnly())) {
            Campus campus = voucher.getCampusId() == null ? null : campusService.getById(voucher.getCampusId());
            if (campus == null || !Integer.valueOf(1).equals(campus.getStatus())) {
                return "学生专享券必须绑定有效校区";
            }
        }
        if (seckill && (voucher.getStock() == null || voucher.getStock() <= 0
                || voucher.getBeginTime() == null || voucher.getEndTime() == null
                || !voucher.getBeginTime().isBefore(voucher.getEndTime())
                || !voucher.getEndTime().isAfter(java.time.LocalDateTime.now()))) {
            return "秒杀库存或活动时间不合法";
        }
        return null;
    }
}
