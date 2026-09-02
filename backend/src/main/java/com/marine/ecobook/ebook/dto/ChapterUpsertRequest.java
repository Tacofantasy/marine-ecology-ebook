package com.marine.ecobook.ebook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChapterUpsertRequest(
        @NotBlank(message = "请输入章节标题")
        @Size(max = 200, message = "章节标题不能超过 200 个字符")
        String title,

        @NotBlank(message = "请输入章节正文")
        String content,

        @Size(max = 1000, message = "来源补充不能超过 1000 个字符")
        String sourceNote) {
}
