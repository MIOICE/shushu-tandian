-- 鼠鼠探店 v5：存量库点评社区闭环升级脚本（在 shushu_student_v4.sql 之后执行一次）

ALTER TABLE `tb_blog`
  ADD INDEX `idx_blog_hot` (`liked`, `create_time`) USING BTREE,
  ADD INDEX `idx_blog_user_time` (`user_id`, `create_time`) USING BTREE,
  ADD INDEX `idx_blog_shop_time` (`shop_id`, `create_time`) USING BTREE;

UPDATE `tb_blog_comments` SET `liked` = 0 WHERE `liked` IS NULL;
UPDATE `tb_blog_comments` SET `status` = 0 WHERE `status` IS NULL;

ALTER TABLE `tb_blog_comments`
  MODIFY COLUMN `liked` int(8) UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数',
  MODIFY COLUMN `status` tinyint(1) UNSIGNED NOT NULL DEFAULT 0 COMMENT '状态，0：正常，1：被举报，2：禁止查看',
  ADD INDEX `idx_comment_blog_status_time` (`blog_id`, `status`, `create_time`) USING BTREE,
  ADD INDEX `idx_comment_parent` (`parent_id`) USING BTREE;

CREATE TABLE IF NOT EXISTS `tb_blog_like` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `blog_id` bigint(20) UNSIGNED NOT NULL COMMENT '探店笔记id',
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '点赞用户id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_blog_user` (`blog_id`, `user_id`) USING BTREE,
  INDEX `idx_blog_time` (`blog_id`, `create_time`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '探店笔记点赞关系';

DELETE duplicate_follow
FROM `tb_follow` duplicate_follow
INNER JOIN `tb_follow` retained_follow
  ON duplicate_follow.`user_id` = retained_follow.`user_id`
  AND duplicate_follow.`follow_user_id` = retained_follow.`follow_user_id`
  AND duplicate_follow.`id` > retained_follow.`id`;

ALTER TABLE `tb_follow`
  ADD UNIQUE KEY `uk_user_follow` (`user_id`, `follow_user_id`) USING BTREE,
  ADD INDEX `idx_follow_user` (`follow_user_id`, `user_id`) USING BTREE;
