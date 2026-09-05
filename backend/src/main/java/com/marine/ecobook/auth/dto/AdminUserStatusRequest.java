package com.marine.ecobook.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminUserStatusRequest(
        @Min(value = 0, message = "账号状态只能为 0 或 1")
        @Max(value = 1, message = "账号状态只能为 0 或 1")
        int status) {
}
