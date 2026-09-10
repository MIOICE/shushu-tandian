-- 已导入旧版 hmdp.sql 的数据库只需执行一次本文件。
-- 唯一索引是“0 重复下单”的数据库最终防线；联合索引服务于超时订单扫描。
ALTER TABLE `tb_voucher_order`
    ADD UNIQUE KEY `uk_user_voucher` (`user_id`, `voucher_id`) USING BTREE,
    ADD INDEX `idx_status_create_time` (`status`, `create_time`) USING BTREE;

-- 验收 SQL：两个查询均应返回空结果。
SELECT `user_id`, `voucher_id`, COUNT(*) AS duplicate_count
FROM `tb_voucher_order`
GROUP BY `user_id`, `voucher_id`
HAVING COUNT(*) > 1;

SELECT `voucher_id`, `stock`
FROM `tb_seckill_voucher`
WHERE `stock` < 0;
