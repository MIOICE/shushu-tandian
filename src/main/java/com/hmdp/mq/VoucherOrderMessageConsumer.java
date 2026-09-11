package com.hmdp.mq;

import com.hmdp.event.VoucherOrderEvent;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.OrderCreationLockService;
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
    private final OrderCreationLockService orderCreationLockService;

    public VoucherOrderMessageConsumer(IVoucherOrderService orderService,
                                       SeckillReservationService reservationService,
                                       OrderCreationLockService orderCreationLockService) {
        this.orderService = orderService;
        this.reservationService = reservationService;
        this.orderCreationLockService = orderCreationLockService;
    }

    @Override
    public void onMessage(VoucherOrderEvent event) {
        log.debug("消费秒杀订单消息, orderId={}", event.getOrderId());
        String lockToken = orderCreationLockService.tryLock(event.getOrderId());
        if (lockToken == null) {
            // 同一消息的另一个副本仍在事务中，交给 RocketMQ 稍后重试，避免误回补 Redis 库存。
            throw new IllegalStateException("订单正在由另一个消费者处理: " + event.getOrderId());
        }
        try {
            orderService.createVoucherOrder(event);
            // 只有数据库事务成功提交后才清理 Redis 待处理记录；ACK 失败会触发安全的幂等重投。
            reservationService.acknowledge(event.getVoucherId(), event.getOrderId());
        } finally {
            orderCreationLockService.unlock(event.getOrderId(), lockToken);
        }
    }
}
