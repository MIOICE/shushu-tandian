-- 只有当前用户仍持有同一个订单号的预留时才回补，防止消息重试或定时任务重复回补。
-- KEYS: stock, reservation hash, pending zset, event hash
-- ARGV: userId, orderId
local reservedOrderId = redis.call('HGET', KEYS[2], ARGV[1])
if reservedOrderId == ARGV[2] then
    redis.call('HDEL', KEYS[2], ARGV[1])
    redis.call('INCR', KEYS[1])
    redis.call('ZREM', KEYS[3], ARGV[2])
    redis.call('HDEL', KEYS[4], ARGV[2])
    return 1
end
return 0
