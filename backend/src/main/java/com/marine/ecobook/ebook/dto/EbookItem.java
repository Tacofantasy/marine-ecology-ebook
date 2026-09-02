package com.marine.ecobook.ebook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;

/**
 * likeCount 仅在公开查询（listPublic / getPublic）填充；
 * 管理端响应保持原有字段，该字段为 null 时序列化时直接省略。
 */
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
        LocalDateTime updatedAt,
        @JsonSerialize(using = ToStringSerializer.class)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long likeCount) {
}
