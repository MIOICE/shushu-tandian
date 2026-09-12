-- 鼠鼠探店 v7：海南大学海甸校区演示数据（可重复执行）
-- 店铺名称与图片均为项目演示内容，不代表真实商家合作关系。

SET NAMES utf8mb4;

INSERT INTO `tb_campus` (`id`, `name`, `city`, `address`, `x`, `y`, `status`)
VALUES (4, '海南大学海甸校区', '海口市', '美兰区人民大道58号', 110.329, 20.059, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `city` = VALUES(`city`), `address` = VALUES(`address`),
  `x` = VALUES(`x`), `y` = VALUES(`y`), `status` = VALUES(`status`);

INSERT INTO `tb_shop`
  (`name`, `type_id`, `campus_id`, `student_discount`, `tags`, `images`, `area`, `address`,
   `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`)
SELECT '椰风清补凉', 1, 4, 1, '海大南门,清补凉,学生优惠', '/images/haidian/qingbuliang.svg',
       '海南大学南门', '海甸三西路校园南门旁', 110.329, 20.055, 16, 5862, 1286, 49, '11:00-23:30'
WHERE NOT EXISTS (SELECT 1 FROM `tb_shop` WHERE `campus_id` = 4 AND `name` = '椰风清补凉');

INSERT INTO `tb_shop`
  (`name`, `type_id`, `campus_id`, `student_discount`, `tags`, `images`, `area`, `address`,
   `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`)
SELECT '海甸阿侬海南粉', 1, 4, 1, '海南粉,早餐,学生优惠', '/images/haidian/hainan-noodles.svg',
       '海大北门', '海甸五西路校园北门旁', 110.326, 20.064, 18, 4930, 968, 48, '06:30-21:30'
WHERE NOT EXISTS (SELECT 1 FROM `tb_shop` WHERE `campus_id` = 4 AND `name` = '海甸阿侬海南粉');

INSERT INTO `tb_shop`
  (`name`, `type_id`, `campus_id`, `student_discount`, `tags`, `images`, `area`, `address`,
   `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`)
SELECT '椰语椰子鸡', 1, 4, 1, '椰子鸡,聚餐,学生优惠', '/images/haidian/coconut-chicken.svg',
       '海甸岛', '人民大道校园东侧', 110.335, 20.059, 72, 3725, 756, 47, '10:30-22:30'
WHERE NOT EXISTS (SELECT 1 FROM `tb_shop` WHERE `campus_id` = 4 AND `name` = '椰语椰子鸡');

INSERT INTO `tb_shop`
  (`name`, `type_id`, `campus_id`, `student_discount`, `tags`, `images`, `area`, `address`,
   `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`)
SELECT '琼味糟粕醋小馆', 1, 4, 0, '糟粕醋,海南风味,宿舍聚餐', '/images/haidian/zaopocu.svg',
       '海甸岛', '海甸二东路沿街', 110.341, 20.055, 65, 3189, 632, 46, '11:00-23:00'
WHERE NOT EXISTS (SELECT 1 FROM `tb_shop` WHERE `campus_id` = 4 AND `name` = '琼味糟粕醋小馆');

INSERT INTO `tb_shop`
  (`name`, `type_id`, `campus_id`, `student_discount`, `tags`, `images`, `area`, `address`,
   `x`, `y`, `avg_price`, `sold`, `comments`, `score`, `open_hours`)
SELECT '白沙门晚风烧烤', 1, 4, 0, '夜宵,烧烤,白沙门', '/images/haidian/beach-bbq.svg',
       '白沙门公园', '海甸六东路白沙门公园附近', 110.346, 20.071, 52, 2976, 541, 45, '17:00-02:00'
WHERE NOT EXISTS (SELECT 1 FROM `tb_shop` WHERE `campus_id` = 4 AND `name` = '白沙门晚风烧烤');

SET @haidian_demo_shop_id := (
  SELECT `id` FROM `tb_shop` WHERE `campus_id` = 4 AND `name` = '椰风清补凉' ORDER BY `id` LIMIT 1
);

INSERT INTO `tb_voucher`
  (`shop_id`, `campus_id`, `student_only`, `title`, `sub_title`, `rules`,
   `pay_value`, `actual_value`, `type`, `status`)
SELECT @haidian_demo_shop_id, 4, 0, '海甸开学季 20 元清补凉券', '限量 100 份，每位同学限购一份',
       '仅限椰风清补凉使用\n不可与其他优惠同享', 990, 2000, 1, 1
WHERE @haidian_demo_shop_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `tb_voucher` WHERE `campus_id` = 4 AND `title` = '海甸开学季 20 元清补凉券'
  );

SET @haidian_demo_voucher_id := (
  SELECT `id` FROM `tb_voucher`
  WHERE `campus_id` = 4 AND `title` = '海甸开学季 20 元清补凉券'
  ORDER BY `id` LIMIT 1
);

INSERT INTO `tb_seckill_voucher`
  (`voucher_id`, `stock`, `create_time`, `begin_time`, `end_time`, `update_time`)
SELECT @haidian_demo_voucher_id, 100, CURRENT_TIMESTAMP, '2026-01-01 00:00:00',
       '2035-12-31 23:59:59', CURRENT_TIMESTAMP
WHERE @haidian_demo_voucher_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `tb_seckill_voucher` WHERE `voucher_id` = @haidian_demo_voucher_id
  );
