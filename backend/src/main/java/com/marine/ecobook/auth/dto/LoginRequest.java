package com.marine.ecobook.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "账号不能为空")
        @Size(max = 255, message = "账号长度不能超过 255 位")
        String account,
        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码长度不能超过 64 位")
        String password) {
}
