package com.marine.ecobook.ebook.dto;

import java.time.LocalDateTime;

public record EbookItem(
        Long id,
        Long categoryId,
        String categoryName,
        String title,
        String coverUrl,
        String summary,
        String sourceNote,
        String status,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt) {
}
