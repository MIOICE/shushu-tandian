package com.hmdp.mq;

import com.hmdp.cache.ShopCacheManager;
import com.hmdp.event.CacheInvalidationEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${shushu.mq.cache-topic:shushu-cache-topic}",
        selectorExpression = "invalidate",
        consumerGroup = "shushu-cache-consumer-group",
        messageModel = MessageModel.BROADCASTING
)
public class CacheInvalidationMessageConsumer implements RocketMQListener<CacheInvalidationEvent> {

    private final ShopCacheManager cacheManager;

    public CacheInvalidationMessageConsumer(ShopCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void onMessage(CacheInvalidationEvent event) {
        if (event != null && "shop".equals(event.getCacheName()) && event.getDataId() != null) {
            cacheManager.evict(event.getDataId());
            log.debug("已执行店铺缓存补偿删除, shopId={}", event.getDataId());
        }
    }
}
