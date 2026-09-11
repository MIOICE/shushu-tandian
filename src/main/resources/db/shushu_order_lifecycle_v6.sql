-- 鼠鼠探店 v6：存量库订单列表索引升级脚本（在 shushu_social_v5.sql 之后执行一次）

ALTER TABLE `tb_voucher_order`
  ADD INDEX `idx_user_create_time` (`user_id`, `create_time`) USING BTREE;
