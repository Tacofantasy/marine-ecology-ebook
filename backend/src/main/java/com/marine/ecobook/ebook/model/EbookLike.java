package com.marine.ecobook.ebook.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 电子书点赞记录，精确映射 likes 表。
 * <p>
 * 表名显式标注为 "likes"，避免与 Java 命名推断不一致。
 */
@TableName("likes")
public class EbookLike {

    private Long id;
    private Long userId;
    private Long ebookId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getEbookId() { return ebookId; }
    public void setEbookId(Long ebookId) { this.ebookId = ebookId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
