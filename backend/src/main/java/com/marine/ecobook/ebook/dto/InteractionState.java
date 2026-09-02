package com.marine.ecobook.ebook.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 当前登录用户对一本电子书的互动状态 + 公开点赞总数。
 * 不包含任何用户 ID、姓名或收藏统计。
 */
public record InteractionState(
        Boolean liked,
        Boolean favorited,
        @JsonSerialize(using = ToStringSerializer.class) Long likeCount) {
}
