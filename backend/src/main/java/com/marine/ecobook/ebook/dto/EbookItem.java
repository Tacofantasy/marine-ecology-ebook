package com.marine.ecobook.ebook.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;

public record EbookItem(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long categoryId,
        String categoryName,
        String title,
        String coverUrl,
        String summary,
        String sourceNote,
        String status,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt) {
}
