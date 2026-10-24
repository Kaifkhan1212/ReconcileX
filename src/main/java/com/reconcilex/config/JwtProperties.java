package com.reconcilex.config;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class JwtProperties {

    @Value("${JWT_SECRET:${jwt.secret:}}")
    private String secret;

    private byte[] secretBytes;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("JWT_SECRET environment variable is missing or blank");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable must be at least 32 bytes (UTF-8). Found " + bytes.length + " bytes."
            );
        }
        this.secretBytes = bytes;
    }

    public byte[] getSecretBytes() {
        return secretBytes;
    }

    public String getSecret() {
        return secret;
    }
}
