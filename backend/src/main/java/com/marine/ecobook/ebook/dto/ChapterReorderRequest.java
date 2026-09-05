package com.marine.ecobook.ebook.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ChapterReorderRequest(
        @NotEmpty(message = "章节排序数组不能为空")
        List<@NotBlank(message = "章节 ID 不能为空") String> chapterIds) {
}
