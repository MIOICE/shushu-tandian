-- 秒杀验收：下面三个结果分别应为 0、0、且订单数不大于活动初始库存。
SELECT COUNT(*) AS negative_stock_rows
FROM tb_seckill_voucher
WHERE stock < 0;

SELECT COUNT(*) AS duplicate_user_voucher_rows
FROM (
    SELECT user_id, voucher_id
    FROM tb_voucher_order
    GROUP BY user_id, voucher_id
    HAVING COUNT(*) > 1
) duplicate_orders;

SELECT voucher_id, COUNT(*) AS created_orders
FROM tb_voucher_order
GROUP BY voucher_id;
