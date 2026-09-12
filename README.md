# 鼠鼠探店

鼠鼠探店是一款面向大学生的校园生活与优惠发现应用，提供响应式 Web 首页、校区选择、学生优惠店铺发现、探店笔记、关注 Feed、优惠券秒杀和订单能力。项目基于 Spring Boot 2.3、MyBatis-Plus、MySQL、Redis、Caffeine 与 RocketMQ。

## 核心链路

- 秒杀：Redis 预扣库存，Lua 在一个原子操作中校验活动时间、库存和一人一券；MySQL 的 `stock > 0` 条件更新与 `(user_id, voucher_id)` 唯一索引提供最终防线。
- 异步订单：HTTP 请求仅完成限流、Redis 预占与 RocketMQ 异步投递，消费者使用按订单号的 Redis 分布式锁串行处理重复投递，再由数据库唯一索引保证最终幂等。预占事件会由同一个 Lua 原子写入 Redis 待处理区，只有消费者成功完成数据库事务后才清理；发送失败、消费失败、确认丢失或进程重启均由定时任务重投。接口返回的是“已受理”的订单号，可通过 `GET /voucher-order/{id}` 查询落库状态。
- 超时关闭：每 30 秒分批扫描超过 15 分钟的未支付订单，状态条件更新成功后同时回补 MySQL 与 Redis；Redis Lua 会核对订单号，重复执行不会重复回补，并保留一人一券标记。
- 订单状态：支付回调凭证校验、支付流水唯一索引和条件更新共同保证幂等；支付与超时关单并发时只有一个状态迁移能够成功。
- 库存对账：定时比较 MySQL、Redis 与待投递消息数量，正常异步差值标记为 `CONSISTENT`，异常差值标记为 `CHECK_REQUIRED` 并告警。
- 两级缓存：店铺详情先查 Caffeine，再查 Redis，最后互斥回源 MySQL。更新采用“更新数据库 → 删除缓存 → 提交后再次删除 → RocketMQ 广播补偿 → TTL 兜底”。
- 风控：`@RiskLimit` + AOP + Redis Lua 实现用户、IP、设备指纹三维滑动窗口，三个维度在同一脚本内原子判断。
- 校园发现：店铺绑定大学校区并携带学生优惠与场景标签，列表支持按校园热度、评分和价格分页排序；排序参数通过枚举白名单转换，不直接进入 SQL。
- 学生认证：学号以服务端盐值加 SHA-256 后存储，申请由运营凭证审核；学生专享券使用本地券策略缓存和 Redis 认证缓存校验，审核结果会主动失效共享缓存。
- 点评社区：笔记发布后写入关注者 Redis Feed，支持滚动分页；点赞和关注关系由 MySQL 唯一索引保证幂等，评论支持回复、分页与作者软删除。
- 文件安全：上传目录通过 `UPLOAD_DIR` 配置，限制 5MB 与图片扩展名/MIME/文件头，路径归一化阻断目录穿越；删除操作仅允许运营凭证调用。
- Web 前端：访问 `/` 即可进入完全以学生用户为中心的响应式校园发现首页，支持验证码登录/自动注册、登录态恢复、校区切换、限时秒杀、异步订单状态轮询、我的订单、模拟支付与取消回补，以及店铺筛选、搜索和详情；技术运行指标集中在独立仪表盘中，静态资源随 Spring Boot JAR 一起部署。

## 快速启动

要求：JDK 8+、Maven 3.6+、Docker Compose。

```bash
docker compose up -d
mvn spring-boot:run
```

默认端口：应用 `8081`、MySQL `3306`、Redis `6379`、RocketMQ NameServer `9876`、Broker `10911`。配置均可用 [.env.example](./.env.example) 中的环境变量覆盖。

启动成功后访问 [http://localhost:8081/](http://localhost:8081/) 查看鼠鼠探店用户首页，访问 [http://localhost:8081/dashboard.html](http://localhost:8081/dashboard.html) 查看技术仪表盘。

验证码默认只写入应用日志。仅在本地界面演示时可设置 `SHUSHU_AUTH_EXPOSE_CODE=true`，页面会显示并自动填写验证码；生产环境必须保持 `false` 并替换为真实短信服务。

支付回调、运营接口和学生身份摘要分别使用 `PAYMENT_CALLBACK_TOKEN`、`OPS_TOKEN` 和 `STUDENT_ID_SALT`。三者都应设置为不同的高强度随机值；未配置时对应的敏感操作会被拒绝。

RocketMQ 广播消费者的本地位点默认写入系统临时目录下的 `shushu-rocketmq-offsets`；生产环境可通过 `ROCKETMQ_LOCAL_OFFSET_DIR` 指向持久化且可写的目录。

首次创建的 MySQL 数据卷会自动导入 `src/main/resources/db/hmdp.sql`。若使用已经导入旧版脚本的数据库，需要手动执行一次：

```bash
mysql -uroot -p hmdp < src/main/resources/db/shushu_upgrade.sql
```

若数据库已经执行过上一版 `shushu_upgrade.sql`，本次订单状态升级需继续执行：

```bash
mysql -uroot -p hmdp < src/main/resources/db/shushu_order_v2.sql
```

若数据库还需要加入校区、学生优惠和校园标签，继续执行一次：

```bash
mysql -uroot -p hmdp < src/main/resources/db/shushu_campus_v3.sql
```

学生身份认证与学生专享券升级继续执行：

```bash
mysql -uroot -p hmdp < src/main/resources/db/shushu_student_v4.sql
```

点评、关注 Feed、评论和幂等点赞升级继续执行：

```bash
mysql -uroot -p hmdp < src/main/resources/db/shushu_social_v5.sql
```

订单列表查询索引升级继续执行：

```bash
mysql -uroot -p hmdp < src/main/resources/db/shushu_order_lifecycle_v6.sql
```

新增秒杀券示例（开始和结束时间需改成当前有效时间）：

```bash
curl -X POST http://localhost:8081/voucher/seckill \
  -H "Content-Type: application/json" \
  -H "X-Ops-Token: $OPS_TOKEN" \
  -d '{"shopId":1,"campusId":2,"studentOnly":1,"title":"校园夜宵5折券","subTitle":"学生专享","rules":"每人限购一份","payValue":500,"actualValue":1000,"type":1,"status":1,"stock":5,"beginTime":"2026-09-10T09:00:00","endTime":"2026-09-10T23:00:00"}'
```

登录沿用验证码流程：调用 `POST /user/code?phone=手机号`，从开发日志取得验证码，再调用 `POST /user/login` 获取 token。生产环境应替换日志验证码为真实短信服务。

## 压测与验收

项目提供两份 JMeter 5.6 测试计划。指标必须在目标部署环境实测，仓库不会把目标值伪装成已测结果。

本项目最近一次完整本地验收的环境、步骤与实测结果见 [performance/ACCEPTANCE.md](./performance/ACCEPTANCE.md)。

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
| GET | `/voucher/seckill/active?campusId=2` | 查询指定校区尚未结束的秒杀活动 |
| POST | `/user/code?phone=手机号` | 发送登录验证码；本地可通过开关返回演示验证码 |
| POST | `/user/login` | 验证码登录；手机号首次登录时自动注册 |
| POST | `/user/logout` | 删除当前 Redis 登录态 |
| GET | `/voucher-order/{orderId}` | 查询本人订单状态 |
| GET | `/voucher-order/me?current=1&status=2` | 分页查询本人订单，状态参数可选 |
| POST | `/voucher-order/{orderId}/pay` | 支付未关闭订单 |
| POST | `/voucher-order/{orderId}/cancel` | 主动取消本人待支付订单并回补库存 |
| POST | `/voucher-order/{orderId}/use` | 登录并携带 `X-Ops-Token` 核销已支付订单 |
| POST | `/voucher-order/payment/callback` | 携带 `X-Payment-Callback-Token` 的幂等支付回调；订单尚未落库时返回 503 以提示重试 |
| GET | `/shop/{id}` | 两级缓存查询店铺 |
| POST / PUT | `/shop` | 携带 `X-Ops-Token` 新增或更新店铺，并触发缓存一致性链路 |
| GET | `/shop/cache/stats` | 查询缓存命中与回源统计 |
| GET | `/campus?city=杭州市` | 查询已启用校区，城市参数可选 |
| GET | `/campus/{id}` | 查询校区详情 |
| GET | `/shop/of/campus?campusId=2&studentOnly=true&sort=hot&current=1` | 按校区发现学生优惠店铺；排序支持 `hot`、`score`、`price` |
| PUT | `/user/campus/{campusId}` | 设置当前用户的默认校区 |
| POST | `/student-verification` | 提交学生认证，参数为 `campusId`、`studentNo` |
| GET | `/student-verification/me` | 查询本人的学生认证状态 |
| POST | `/blog` | 发布探店笔记并扇出到关注者 Feed |
| GET | `/blog/{id}` | 查询笔记详情与本人点赞状态 |
| PUT | `/blog/like/{id}` | 幂等切换点赞状态 |
| GET | `/blog/of/follow?lastId=时间戳&offset=0` | 滚动分页查询关注 Feed |
| PUT | `/follow/{userId}/{followed}` | 关注或取消关注用户 |
| POST | `/blog-comments` | 发布一级评论或通过 `answerId` 回复评论 |
| GET | `/blog-comments/blog/{blogId}` | 分页查询笔记评论 |
| DELETE | `/blog-comments/{id}` | 作者软删除评论 |
| POST | `/upload/blog` | 登录后上传不超过 5MB 的图片 |
| DELETE | `/upload/blog?name=...` | 登录并携带 `X-Ops-Token` 删除上传图片 |
| GET | `/ops/student-verifications` | 登录并携带 `X-Ops-Token` 查询待审核认证 |
| POST | `/ops/student-verifications/{id}/review` | 登录并携带 `X-Ops-Token` 审核认证，参数为 `approved`、`rejectReason` |
| GET | `/ops/stock/reconciliation` | 登录并携带 `X-Ops-Token` 查询最近一次库存对账结果 |

## 构建验证

```bash
mvn clean test
```

数据库结构的最终约束位于 [hmdp.sql](./src/main/resources/db/hmdp.sql)，存量库按顺序执行 [shushu_upgrade.sql](./src/main/resources/db/shushu_upgrade.sql)、[shushu_order_v2.sql](./src/main/resources/db/shushu_order_v2.sql)、[shushu_campus_v3.sql](./src/main/resources/db/shushu_campus_v3.sql)、[shushu_student_v4.sql](./src/main/resources/db/shushu_student_v4.sql)、[shushu_social_v5.sql](./src/main/resources/db/shushu_social_v5.sql) 和 [shushu_order_lifecycle_v6.sql](./src/main/resources/db/shushu_order_lifecycle_v6.sql)。
