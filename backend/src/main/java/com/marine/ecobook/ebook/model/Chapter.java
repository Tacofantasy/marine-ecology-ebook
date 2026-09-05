package com.marine.ecobook.ebook.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import java.time.LocalDateTime;

@TableName("chapters")
public class Chapter {

    private Long id;
    private Long ebookId;
    private String title;
    private String content;
    private Integer sortOrder;
    private String status;
    private Long viewCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceNote;
    private Long wordCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEbookId() { return ebookId; }
    public void setEbookId(Long ebookId) { this.ebookId = ebookId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getViewCount() { return viewCount; }
    public void setViewCount(Long viewCount) { this.viewCount = viewCount; }
    public String getSourceNote() { return sourceNote; }
    public void setSourceNote(String sourceNote) { this.sourceNote = sourceNote; }
    public Long getWordCount() { return wordCount; }
    public void setWordCount(Long wordCount) { this.wordCount = wordCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
