-- KEYS: stock, user->order reservation hash, begin time, end time
-- ARGV: userId, orderId, currentTimeMillis
local stock = redis.call('GET', KEYS[1])
local beginTime = redis.call('GET', KEYS[3])
local endTime = redis.call('GET', KEYS[4])

if (not stock) or (not beginTime) or (not endTime) then
    return 5
end

local now = tonumber(ARGV[3])
if now < tonumber(beginTime) then
    return 3
end
if now > tonumber(endTime) then
    return 4
end
if redis.call('HEXISTS', KEYS[2], ARGV[1]) == 1 then
    return 2
end
if tonumber(stock) <= 0 then
    return 1
end

redis.call('DECR', KEYS[1])
redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
-- 活动结束后仍保留一天，供异步消费和超时关闭完成幂等校验。
redis.call('PEXPIREAT', KEYS[2], tonumber(endTime) + 86400000)
return 0
