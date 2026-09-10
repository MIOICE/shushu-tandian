-- 已经执行过 shushu_upgrade.sql 的存量数据库，再执行一次本文件。
-- 新装数据库直接使用 hmdp.sql，无需执行本文件。
ALTER TABLE `tb_voucher_order`
    ADD COLUMN `pay_no` varchar(64) NULL DEFAULT NULL COMMENT '支付平台流水号' AFTER `pay_type`,
    ADD COLUMN `close_time` timestamp NULL DEFAULT NULL COMMENT '关闭时间' AFTER `pay_time`,
    ADD UNIQUE KEY `uk_pay_no` (`pay_no`) USING BTREE;
