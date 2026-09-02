package com.marine.ecobook.ebook.mapper;

/**
 * 批量点赞聚合查询的结果行：ebookId -> likeCount。
 */
public class EbookLikeCount {

    private Long ebookId;
    private Long likeCount;

    public Long getEbookId() { return ebookId; }
    public void setEbookId(Long ebookId) { this.ebookId = ebookId; }
    public Long getLikeCount() { return likeCount; }
    public void setLikeCount(Long likeCount) { this.likeCount = likeCount; }
}
