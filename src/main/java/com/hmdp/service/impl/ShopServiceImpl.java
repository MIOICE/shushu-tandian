package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.cache.ShopCacheManager;
import com.hmdp.dto.Result;
import com.hmdp.entity.Campus;
import com.hmdp.entity.Shop;
import com.hmdp.enums.ShopCampusSort;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mq.ShushuMessagePublisher;
import com.hmdp.service.ICampusService;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final ShopCacheManager cacheManager;
    private final ShushuMessagePublisher messagePublisher;
    private final ICampusService campusService;

    public ShopServiceImpl(ShopCacheManager cacheManager,
                           ShushuMessagePublisher messagePublisher,
                           ICampusService campusService) {
        this.cacheManager = cacheManager;
        this.messagePublisher = messagePublisher;
        this.campusService = campusService;
    }

    @Override
    public Result queryById(Long id) {
        Shop shop = cacheManager.get(id, this::getById);
        return shop == null ? Result.fail("店铺不存在") : Result.ok(shop);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺 id 不能为空");
        }
        if (!updateById(shop)) {
            return Result.fail("店铺不存在或更新失败");
        }

        // 第一次删除紧跟数据库更新；事务提交后通过 MQ 再删一次，覆盖并发回填旧值的窗口。
        cacheManager.evict(id);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheManager.evict(id);
                    messagePublisher.publishCacheInvalidation(id);
                }
            });
        } else {
            messagePublisher.publishCacheInvalidation(id);
        }
        return Result.ok();
    }

    @Override
    public Result cacheStats() {
        return Result.ok(cacheManager.metrics().snapshot());
    }

    @Override
    public Result queryByCampus(Long campusId, Integer current, Boolean studentOnly, String sort) {
        Campus campus = campusService.getById(campusId);
        if (campus == null || !Integer.valueOf(1).equals(campus.getStatus())) {
            return Result.fail("校区不存在或已停用");
        }

        QueryWrapper<Shop> wrapper = new QueryWrapper<>();
        wrapper.eq("campus_id", campusId)
                .eq(Boolean.TRUE.equals(studentOnly), "student_discount", 1);

        ShopCampusSort campusSort = ShopCampusSort.parse(sort);
        if (campusSort == ShopCampusSort.SCORE) {
            wrapper.orderByDesc("score").orderByDesc("sold");
        } else if (campusSort == ShopCampusSort.PRICE) {
            wrapper.orderByAsc("avg_price").orderByDesc("score");
        } else {
            wrapper.orderByDesc("sold").orderByDesc("comments");
        }

        int pageNumber = current == null || current < 1 ? 1 : current;
        Page<Shop> page = page(new Page<>(pageNumber, SystemConstants.DEFAULT_PAGE_SIZE), wrapper);
        page.getRecords().forEach(shop -> shop.setCampusName(campus.getName()));
        return Result.ok(page.getRecords());
    }
}
