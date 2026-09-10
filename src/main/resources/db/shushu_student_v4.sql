-- 鼠鼠探店 v4：存量库学生认证与专享券升级脚本（在 shushu_campus_v3.sql 之后执行一次）

ALTER TABLE `tb_user_info`
  ADD COLUMN `campus_id` bigint(20) UNSIGNED NULL DEFAULT NULL COMMENT '默认大学校区' AFTER `city`;

CREATE TABLE IF NOT EXISTS `tb_student_verification` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户id',
  `campus_id` bigint(20) UNSIGNED NOT NULL COMMENT '学校校区id',
  `student_no_hash` char(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT '加盐后的学号摘要',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待审核，1已认证，2已驳回',
  `reviewed_at` timestamp NULL DEFAULT NULL COMMENT '审核时间',
  `reject_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '驳回原因',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_student_user` (`user_id`) USING BTREE,
  UNIQUE KEY `uk_campus_student_no` (`campus_id`, `student_no_hash`) USING BTREE,
  INDEX `idx_status_create_time` (`status`, `create_time`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '学生身份认证申请';

ALTER TABLE `tb_voucher`
  ADD COLUMN `campus_id` bigint(20) UNSIGNED NULL DEFAULT NULL COMMENT '适用大学校区' AFTER `shop_id`,
  ADD COLUMN `student_only` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否仅限认证学生' AFTER `campus_id`,
  ADD INDEX `idx_campus_student_status` (`campus_id`, `student_only`, `status`) USING BTREE;

UPDATE `tb_voucher` SET `campus_id` = 2 WHERE `id` = 1 AND `campus_id` IS NULL;
