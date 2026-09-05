package com.marine.ecobook.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPasswordResetRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度须为 8 至 64 位")
        String password) {
}
