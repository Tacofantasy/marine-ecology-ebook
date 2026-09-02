package com.marine.ecobook.ebook.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;

/**
 * “我的收藏”列表项：公开电子书展示字段 + 当前用户的收藏时间 + 公开点赞总数。
 */
public record FavoriteEbookItem(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long categoryId,
        String categoryName,
        String title,
        String coverUrl,
        String summary,
        String status,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt,
        @JsonSerialize(using = ToStringSerializer.class) Long likeCount,
        LocalDateTime favoritedAt) {
}
