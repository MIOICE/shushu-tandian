-- 超时订单回补库存，但保留用户已参与标记，与数据库一人一券唯一索引保持一致。
-- KEYS: stock, user->order reservation hash
-- ARGV: userId, orderId
local reservedOrderId = redis.call('HGET', KEYS[2], ARGV[1])
if reservedOrderId == ARGV[2] then
    redis.call('HSET', KEYS[2], ARGV[1], 'closed:' .. ARGV[2])
    redis.call('INCR', KEYS[1])
    return 1
end
return 0
