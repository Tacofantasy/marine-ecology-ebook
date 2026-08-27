package com.marine.ecobook.common.api;

import java.time.Instant;

/**
 * Standard envelope shared by all HTTP APIs.
 */
public record ApiResponse<T>(int code, String message, T data, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResultCode.SUCCESS.code(), ResultCode.SUCCESS.message(), data, Instant.now());
    }

    public static ApiResponse<Void> failure(ResultCode resultCode) {
        return failure(resultCode, resultCode.message());
    }

    public static ApiResponse<Void> failure(ResultCode resultCode, String message) {
        return new ApiResponse<>(resultCode.code(), message, null, Instant.now());
    }
}
