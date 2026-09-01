package com.marine.ecobook.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank(message = "分类名称不能为空") @Size(max = 100, message = "分类名称不能超过100个字符") String name) {
}
