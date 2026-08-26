package com.marine.ecobook.common.api;

import java.time.Instant;

/**
 * Standard envelope shared by all HTTP APIs.
 */
public record ApiResponse<T>(int code, String message, T data, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data, Instant.now());
    }
}
