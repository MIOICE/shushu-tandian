package com.hmdp.risk;

public class UnauthorizedPaymentCallbackException extends RuntimeException {
    public UnauthorizedPaymentCallbackException() {
        super("支付回调凭证无效");
    }
}
