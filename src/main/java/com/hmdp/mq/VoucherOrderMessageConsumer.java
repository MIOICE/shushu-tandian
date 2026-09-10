package com.hmdp.mq;

import com.hmdp.event.VoucherOrderEvent;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.SeckillReservationService;
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
    private final SeckillReservationService reservationService;

    public VoucherOrderMessageConsumer(IVoucherOrderService orderService,
                                       SeckillReservationService reservationService) {
        this.orderService = orderService;
        this.reservationService = reservationService;
    }

    @Override
    public void onMessage(VoucherOrderEvent event) {
        log.debug("消费秒杀订单消息, orderId={}", event.getOrderId());
        orderService.createVoucherOrder(event);
        // 只有业务处理成功返回后才清理 Redis 待处理记录；ACK 失败会触发安全的幂等重投。
        reservationService.acknowledge(event.getVoucherId(), event.getOrderId());
    }
}
