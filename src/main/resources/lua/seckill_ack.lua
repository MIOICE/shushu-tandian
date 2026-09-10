-- RocketMQ Broker 确认收到消息后清理 Redis 待投递记录。
-- KEYS: pending zset, event hash
-- ARGV: orderId
redis.call('ZREM', KEYS[1], ARGV[1])
redis.call('HDEL', KEYS[2], ARGV[1])
return 1
