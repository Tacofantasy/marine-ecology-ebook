package com.marine.ecobook.ebook.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ChapterReorderRequest(
        @NotEmpty(message = "章节排序数组不能为空")
        List<String> chapterIds) {
}
