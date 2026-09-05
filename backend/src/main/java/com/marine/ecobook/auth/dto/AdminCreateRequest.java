package com.marine.ecobook.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminCreateRequest(
        @NotBlank(message = "登录名不能为空")
        @Pattern(regexp = "[A-Za-z0-9_]{3,64}", message = "登录名须为 3 至 64 位字母、数字或下划线")
        String username,
        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称长度不能超过 64 位")
        String displayName,
        @Email(message = "邮箱格式不正确")
        @Size(max = 255, message = "邮箱长度不能超过 255 位")
        String email,
        @NotBlank(message = "初始密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度须为 8 至 64 位")
        String password) {
}
