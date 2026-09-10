package com.hmdp.security;

public class UnauthorizedOpsException extends RuntimeException {
    public UnauthorizedOpsException() {
        super("运营凭证无效");
    }
}
