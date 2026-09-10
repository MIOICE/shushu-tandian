package com.hmdp.config;

import com.hmdp.dto.Result;
import com.hmdp.risk.RateLimitExceededException;
import com.hmdp.risk.UnauthorizedPaymentCallbackException;
import com.hmdp.risk.InvalidPaymentCallbackException;
import com.hmdp.risk.PaymentCallbackRetryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InvalidPaymentCallbackException.class)
    public Result handleInvalidPaymentCallback(InvalidPaymentCallbackException e) {
        return Result.fail(e.getMessage());
    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(PaymentCallbackRetryException.class)
    public Result handlePaymentCallbackRetry(PaymentCallbackRetryException e) {
        return Result.fail(e.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(DuplicateKeyException.class)
    public Result handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("数据库唯一约束冲突", e);
        return Result.fail("支付流水号或订单记录已存在");
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(UnauthorizedPaymentCallbackException.class)
    public Result handleUnauthorizedPaymentCallback(UnauthorizedPaymentCallbackException e) {
        return Result.fail(e.getMessage());
    }

    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(RateLimitExceededException.class)
    public Result handleRateLimitException(RateLimitExceededException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }
}
