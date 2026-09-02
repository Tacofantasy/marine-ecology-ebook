package com.marine.ecobook.auth.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record AdminUserItem(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String username,
        String displayName,
        String email,
        String role,
        Integer status,
        String createdAt) {
}
