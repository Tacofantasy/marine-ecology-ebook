package com.marine.ecobook.ebook.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;

public record ChapterDetail(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long ebookId,
        String title,
        Integer sortOrder,
        String status,
        String sourceNote,
        @JsonSerialize(using = ToStringSerializer.class) Long viewCount,
        String content,
        LocalDateTime updatedAt) {
}
