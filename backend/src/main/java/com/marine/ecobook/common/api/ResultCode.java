package com.marine.ecobook.common.api;

import org.springframework.http.HttpStatus;

/**
 * Stable business codes shared by the frontend and backend.
 */
public enum ResultCode {
    SUCCESS(0, "success", HttpStatus.OK),
    BAD_REQUEST(40001, "请求参数不正确", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40101, "请先登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40301, "无权执行此操作", HttpStatus.FORBIDDEN),
    NOT_FOUND(40401, "资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(40501, "不支持的请求方法", HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(40901, "资源状态冲突", HttpStatus.CONFLICT),
    INTERNAL_ERROR(50000, "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
