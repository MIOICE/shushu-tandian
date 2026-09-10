-- 鼠鼠探店 v3：存量库校园场景升级脚本（在 shushu_order_v2.sql 之后执行一次）

CREATE TABLE IF NOT EXISTS `tb_campus` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '学校及校区名称',
  `city` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所在城市',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '校区地址',
  `x` double UNSIGNED NOT NULL COMMENT '经度',
  `y` double UNSIGNED NOT NULL COMMENT '纬度',
  `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_campus_name_city` (`name`, `city`) USING BTREE,
  INDEX `idx_city_status` (`city`, `status`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '大学校区';

INSERT INTO `tb_campus` (`id`, `name`, `city`, `address`, `x`, `y`, `status`)
VALUES
  (1, '浙江大学紫金港校区', '杭州市', '余杭塘路866号', 120.090, 30.305, 1),
  (2, '浙江工业大学朝晖校区', '杭州市', '潮王路18号', 120.164, 30.287, 1),
  (3, '杭州师范大学仓前校区', '杭州市', '余杭塘路2318号', 120.016, 30.295, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `city` = VALUES(`city`),
  `address` = VALUES(`address`),
  `x` = VALUES(`x`),
  `y` = VALUES(`y`),
  `status` = VALUES(`status`);

ALTER TABLE `tb_shop`
  ADD COLUMN `campus_id` bigint(20) UNSIGNED NULL DEFAULT NULL COMMENT '所属大学校区' AFTER `type_id`,
  ADD COLUMN `student_discount` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否提供学生优惠' AFTER `campus_id`,
  ADD COLUMN `tags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '校园场景标签，逗号分隔' AFTER `student_discount`,
  ADD INDEX `idx_campus_hot` (`campus_id`, `sold`) USING BTREE,
  ADD INDEX `idx_campus_score` (`campus_id`, `score`) USING BTREE;

UPDATE `tb_shop`
SET `campus_id` = 2,
    `student_discount` = IF(`id` IN (1, 2, 3, 6, 8, 10), 1, 0),
    `tags` = CASE
        WHEN `id` IN (1, 3, 6, 8) THEN '校园周边,学生优惠,聚餐'
        WHEN `id` IN (2, 5, 9) THEN '校园周边,夜宵'
        WHEN `id` IN (10, 11, 12, 13, 14) THEN '校园周边,团建'
        ELSE '校园周边'
    END
WHERE `campus_id` IS NULL;
