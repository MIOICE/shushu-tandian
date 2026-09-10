# 鼠鼠探店

鼠鼠探店是一款面向大学生的校园生活与优惠发现后端，提供探店笔记、店铺检索、关注 Feed、优惠券秒杀和订单能力。项目基于 Spring Boot 2.3、MyBatis-Plus、MySQL、Redis、Caffeine 与 RocketMQ。

## 核心链路

- 秒杀：Redis 预扣库存，Lua 在一个原子操作中校验活动时间、库存和一人一券；MySQL 的 `stock > 0` 条件更新与 `(user_id, voucher_id)` 唯一索引提供最终防线。
- 异步订单：HTTP 请求仅完成限流、Redis 预占与 RocketMQ 异步投递，消费者幂等创建订单。接口返回的是“已受理”的订单号，可通过 `GET /voucher-order/{id}` 查询落库状态。
- 超时关闭：每 30 秒分批扫描超过 15 分钟的未支付订单，状态条件更新成功后同时回补 MySQL 与 Redis；Redis Lua 会核对订单号，重复执行不会重复回补，并保留一人一券标记。
- 两级缓存：店铺详情先查 Caffeine，再查 Redis，最后互斥回源 MySQL。更新采用“更新数据库 → 删除缓存 → 提交后再次删除 → RocketMQ 广播补偿 → TTL 兜底”。
- 风控：`@RiskLimit` + AOP + Redis Lua 实现用户、IP、设备指纹三维滑动窗口，三个维度在同一脚本内原子判断。

## 快速启动

要求：JDK 8+、Maven 3.6+、Docker Compose。

```bash
docker compose up -d
mvn spring-boot:run
```

默认端口：应用 `8081`、MySQL `3306`、Redis `6379`、RocketMQ NameServer `9876`、Broker `10911`。配置均可用 [.env.example](./.env.example) 中的环境变量覆盖。

首次创建的 MySQL 数据卷会自动导入 `src/main/resources/db/hmdp.sql`。若使用已经导入旧版脚本的数据库，需要手动执行一次：

```bash
mysql -uroot -p hmdp < src/main/resources/db/shushu_upgrade.sql
```

新增秒杀券示例（开始和结束时间需改成当前有效时间）：

```bash
curl -X POST http://localhost:8081/voucher/seckill \
  -H "Content-Type: application/json" \
  -d '{"shopId":1,"title":"校园夜宵5折券","subTitle":"学生专享","rules":"每人限购一份","payValue":500,"actualValue":1000,"type":1,"status":1,"stock":5,"beginTime":"2026-09-10T09:00:00","endTime":"2026-09-10T23:00:00"}'
```

登录沿用验证码流程：调用 `POST /user/code?phone=手机号`，从开发日志取得验证码，再调用 `POST /user/login` 获取 token。生产环境应替换日志验证码为真实短信服务。

## 压测与验收

项目提供两份 JMeter 5.6 测试计划。指标必须在目标部署环境实测，仓库不会把目标值伪装成已测结果。

### 1. 秒杀：10 线程、1000 请求

准备 10 个登录 token，启动应用时关闭风控以隔离测试秒杀正确性：

```powershell
$env:SHUSHU_RISK_ENABLED="false"
mvn spring-boot:run

jmeter -n -t performance/shushu-seckill.jmx -l performance/seckill-result.jtl `
  -JvoucherId=1 -Jtoken_0=TOKEN0 -Jtoken_1=TOKEN1 -Jtoken_2=TOKEN2 `
  -Jtoken_3=TOKEN3 -Jtoken_4=TOKEN4 -Jtoken_5=TOKEN5 -Jtoken_6=TOKEN6 `
  -Jtoken_7=TOKEN7 -Jtoken_8=TOKEN8 -Jtoken_9=TOKEN9
```

每个线程固定使用一个用户并重复请求 100 次，能同时检验并发库存和重复提交。压测结束、MQ 消费完毕后执行 [performance/verify.sql](./performance/verify.sql)：负库存行数和重复用户券组合数都必须为 `0`，成功订单数不得超过活动初始库存。

### 2. 风控：目标拦截率 97%

以默认配置启动应用，复用同一用户、IP 和设备发送 1000 次请求。用户窗口上限为 30，理论上一个窗口内恰好拦截 970 次，即 97%；JTL 中 HTTP 429 的数量就是拦截数。

```bash
jmeter -n -t performance/shushu-seckill.jmx -l performance/risk-result.jtl \
  -JvoucherId=1 -JsharedIdentity=true -Jtoken_0=TOKEN0
```

### 3. 缓存：每分钟 2000 次查询

```bash
jmeter -n -t performance/shushu-cache.jmx -l performance/cache-result.jtl -JshopId=1
curl http://localhost:8081/shop/cache/stats
```

统计接口返回 `requests`、`localHits`、`redisHits`、`databaseQueries` 与 `hitRate`。目标是命中率不低于 98%、数据库回源不高于 40 次/分。单一热点在缓存稳定后通常只会首次回源；正式报告请保留对应的 JTL、接口快照和 MySQL 监控数据。

### 4. 异步响应耗时

JMeter 聚合报告中的秒杀接口平均/中位耗时用于对比改造前同步落库链路。目标值为约 50ms，但结果会受本机、网络、Redis 与 RocketMQ 部署方式影响，应以实际报告为准。

## 关键接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/voucher-order/seckill/{voucherId}` | 秒杀受理，返回订单号 |
| GET | `/voucher-order/{orderId}` | 查询本人订单状态 |
| POST | `/voucher-order/{orderId}/pay` | 支付未关闭订单 |
| GET | `/shop/{id}` | 两级缓存查询店铺 |
| PUT | `/shop` | 更新店铺并触发缓存一致性链路 |
| GET | `/shop/cache/stats` | 查询缓存命中与回源统计 |

## 构建验证

```bash
mvn clean test
```

数据库结构的最终约束位于 [hmdp.sql](./src/main/resources/db/hmdp.sql)，存量库升级脚本位于 [shushu_upgrade.sql](./src/main/resources/db/shushu_upgrade.sql)。
