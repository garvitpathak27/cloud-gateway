package com.chatapp.gateway.error;

public record GatewayError(
        String timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String requestId) {
}
