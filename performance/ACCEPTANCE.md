# 鼠鼠探店本地验收报告

验收时间：2026-09-12（Asia/Shanghai）

## 环境

- Windows，JDK 17
- MySQL 8.0.39
- Redis 5.0.14.1
- Apache RocketMQ 5.1.4
- Apache JMeter 5.6.3
- 应用端口 8081，单实例

测试使用临时活动、测试手机号和本地令牌；数据库密码、业务令牌与登录 token 均未写入仓库。JMeter 原始 JTL 被 `.gitignore` 排除，避免提交本机测试数据。

## 结果摘要

| 验收项 | 负载 | 实测结果 | 结论 |
|---|---:|---:|---|
| 秒杀受理 | 10 线程 × 100 次，共 1000 次 | 平均 7ms，最小 2ms，最大 136ms，HTTP 错误 0 | 通过 |
| 防超卖 | 初始库存 10 | Redis 库存 0，MySQL 库存 0，订单 10 | 通过 |
| 防重复下单 | 10 个用户各重复请求 100 次 | 10 个用户、10 个订单，重复用户券组合 0 | 通过 |
| MQ 完整性 | 10 个成功预占 | 待投递 0，数据库落单 10 | 通过 |
| 两级缓存 | 2000 次/分钟 | 平均 1.19ms，命中 1999，回源 1，命中率 99.95%，HTTP 错误 0 | 通过 |
| 缓存更新补偿 | 同值更新店铺并重新查询 | 事务提交成功，缓存删除，MQ 补偿后下一次查询重新回源 | 通过 |
| 三维限流 | 同一用户/IP/设备 1000 次 | 放行 30，HTTP 429 共 970，拦截率 97% | 通过 |
| 超时关单 | 10 个待支付订单超过 15 分钟 | 10 个订单关闭，MySQL 与 Redis 库存均回补 | 通过 |

## 秒杀数据库核验

最终验收使用独立活动（本机测试券 ID 22），初始库存为 10。MQ 消费完成后先核验防超卖和幂等数据，再将订单时间调整为超过 15 分钟以复验定时关单：

```text
Redis stock        = 0
Redis reservations = 10
Redis pending      = 0
MySQL stock        = 0
MySQL orders       = 10
MySQL users        = 10
duplicate users    = 0
negative stocks    = 0
closed orders      = 10
restored DB stock  = 10
restored Redis     = 10
```

压测期间还模拟了 Broker 暂不可写与消息重投。测试发现并修复了“同一消息首次投递与定时重投并发消费时误回补 Redis”的边界：消费者现在先按订单号获取 Redis 分布式锁，再执行事务、清理待投递记录并安全释放锁。数据库的 `(user_id, voucher_id)` 唯一索引和 `stock > 0` 条件更新继续作为最终防线。

## 缓存指标快照

```json
{"requests":2000,"localHits":1998,"redisHits":1,"databaseQueries":1,"hitRate":0.9995}
```

测试前删除 Redis 中的目标店铺缓存，并在应用重启后的空 Caffeine 缓存上执行，因此包含冷启动回源。

随后用运营接口对店铺执行同值更新，事务提交后本地缓存与 Redis 缓存均被删除，RocketMQ `invalidate` 补偿消息正常消费；下一次读取的数据库查询计数从 0 增至 1，证明更新链路没有继续返回旧缓存。

## 风控指标

秒杀接口的窗口配置为每用户 30、每 IP 100、每设备 50 次/分钟。使用同一用户、IP 和设备指纹在一个窗口内发起 1000 次请求，用户维度最先达到阈值：

```text
HTTP 200 = 30
HTTP 429 = 970
拦截率   = 97.00%
```

## 复验

测试计划位于 `performance/shushu-seckill.jmx` 和 `performance/shushu-cache.jmx`，数据库核验 SQL 位于 `performance/verify.sql`。执行前请按 README 准备独立测试活动与登录 token，并将 `SHUSHU_RISK_ENABLED` 分别设置为秒杀正确性测试所需的 `false`、限流测试所需的 `true`。
