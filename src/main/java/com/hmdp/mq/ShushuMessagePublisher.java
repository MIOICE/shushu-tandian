package com.hmdp.mq;

import com.hmdp.event.CacheInvalidationEvent;
import com.hmdp.event.VoucherOrderEvent;
import com.hmdp.service.SeckillReservationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ShushuMessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final SeckillReservationService reservationService;

    @Value("${shushu.mq.order-topic:shushu-order-topic}")
    private String orderTopic;

    @Value("${shushu.mq.cache-topic:shushu-cache-topic}")
    private String cacheTopic;

    public ShushuMessagePublisher(RocketMQTemplate rocketMQTemplate,
                                  SeckillReservationService reservationService) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.reservationService = reservationService;
    }

    public void publishOrder(VoucherOrderEvent event) {
        try {
            rocketMQTemplate.asyncSend(orderTopic + ":seckill", event, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("秒杀订单消息已投递, orderId={}, msgId={}", event.getOrderId(), sendResult.getMsgId());
                    try {
                        reservationService.acknowledge(event.getVoucherId(), event.getOrderId());
                    } catch (RuntimeException exception) {
                        // ACK 丢失只会触发幂等重投，不能回滚已经交给 Broker 的订单。
                        log.error("清理 Redis 待投递记录失败，将由重投任务兜底, orderId={}", event.getOrderId(), exception);
                    }
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("秒杀订单消息异步投递失败，保留待投递记录等待重试, orderId={}", event.getOrderId(), throwable);
                }
            }, 3000);
        } catch (RuntimeException exception) {
            log.error("秒杀订单消息投递失败，保留待投递记录等待重试, orderId={}", event.getOrderId(), exception);
        }
    }

    public void publishCacheInvalidation(Long shopId) {
        CacheInvalidationEvent event = new CacheInvalidationEvent("shop", shopId);
        try {
            rocketMQTemplate.asyncSend(cacheTopic + ":invalidate", event, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("缓存失效补偿消息已投递, shopId={}", shopId);
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("缓存失效补偿消息投递失败，将由 TTL 最终淘汰, shopId={}", shopId, throwable);
                }
            }, 3000);
        } catch (RuntimeException exception) {
            log.error("缓存失效补偿消息投递失败，将由 TTL 最终淘汰, shopId={}", shopId, exception);
        }
    }
}
