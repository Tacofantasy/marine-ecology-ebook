package com.marine.ecobook.ebook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EbookUpsertRequest(
        @NotNull(message = "请选择二级分类") Long categoryId,
        @NotBlank(message = "请输入电子书名称") @Size(max = 200, message = "电子书名称不能超过 200 个字符") String title,
        @Size(max = 500, message = "简介不能超过 500 个字符") String summary,
        @Size(max = 1000, message = "内容来源说明不能超过 1000 个字符") String sourceNote) {
}
