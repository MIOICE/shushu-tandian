package com.hmdp.mq;

import com.hmdp.event.VoucherOrderEvent;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${shushu.mq.order-topic:shushu-order-topic}",
        selectorExpression = "seckill",
        consumerGroup = "shushu-order-consumer-group",
        maxReconsumeTimes = 8
)
public class VoucherOrderMessageConsumer implements RocketMQListener<VoucherOrderEvent> {

    private final IVoucherOrderService orderService;

    public VoucherOrderMessageConsumer(IVoucherOrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void onMessage(VoucherOrderEvent event) {
        log.debug("消费秒杀订单消息, orderId={}", event.getOrderId());
        orderService.createVoucherOrder(event);
    }
}
