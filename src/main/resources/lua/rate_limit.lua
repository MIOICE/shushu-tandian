-- 三个维度在一个 Lua 脚本中同时检查和写入，避免只消耗部分维度的配额。
-- KEYS: user, ip, device sliding-window sorted sets
-- ARGV: nowMillis, windowMillis, member, userLimit, ipLimit, deviceLimit, ttlSeconds
local minScore = tonumber(ARGV[1]) - tonumber(ARGV[2])
for i = 1, 3 do
    redis.call('ZREMRANGEBYSCORE', KEYS[i], 0, minScore)
end

local limits = {tonumber(ARGV[4]), tonumber(ARGV[5]), tonumber(ARGV[6])}
for i = 1, 3 do
    if limits[i] > 0 and redis.call('ZCARD', KEYS[i]) >= limits[i] then
        return i
    end
end

for i = 1, 3 do
    if limits[i] > 0 then
        redis.call('ZADD', KEYS[i], ARGV[1], ARGV[3])
        redis.call('EXPIRE', KEYS[i], ARGV[7])
    end
end
return 0
