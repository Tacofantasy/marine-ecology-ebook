package com.marine.ecobook.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "[A-Za-z0-9_]{3,64}", message = "用户名须为 3 至 64 位字母、数字或下划线")
        String username,
        @Email(message = "邮箱格式不正确")
        @Size(max = 255, message = "邮箱长度不能超过 255 位")
        String email,
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度须为 8 至 64 位")
        String password) {
}
