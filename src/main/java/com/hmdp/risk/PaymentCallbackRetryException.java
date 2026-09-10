package com.hmdp.risk;

public class PaymentCallbackRetryException extends RuntimeException {
    public PaymentCallbackRetryException() {
        super("订单正在异步创建，请稍后重试支付回调");
    }
}
