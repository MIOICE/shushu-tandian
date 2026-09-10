package com.hmdp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class OpsAuthorizer {

    @Value("${shushu.ops.token:}")
    private String configuredToken;

    public void requireAuthorized(String providedToken) {
        if (configuredToken == null || configuredToken.isEmpty() || providedToken == null
                || !MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedOpsException();
        }
    }
}
