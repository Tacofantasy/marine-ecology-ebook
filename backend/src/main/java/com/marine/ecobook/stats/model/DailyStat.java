package com.marine.ecobook.stats.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("daily_stats")
public class DailyStat {

    @TableId
    private LocalDate statDate;

    private Long totalViewCount;

    private Long totalLikeCount;

    private Long viewDelta;

    private Long likeDelta;

    private Long publishedEbookCount;

    private Long activeUserCount;

    private Long totalWordCount;

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public Long getTotalViewCount() {
        return totalViewCount;
    }

    public void setTotalViewCount(Long totalViewCount) {
        this.totalViewCount = totalViewCount;
    }

    public Long getTotalLikeCount() {
        return totalLikeCount;
    }

    public void setTotalLikeCount(Long totalLikeCount) {
        this.totalLikeCount = totalLikeCount;
    }

    public Long getViewDelta() {
        return viewDelta;
    }

    public void setViewDelta(Long viewDelta) {
        this.viewDelta = viewDelta;
    }

    public Long getLikeDelta() {
        return likeDelta;
    }

    public void setLikeDelta(Long likeDelta) {
        this.likeDelta = likeDelta;
    }

    public Long getPublishedEbookCount() {
        return publishedEbookCount;
    }

    public void setPublishedEbookCount(Long publishedEbookCount) {
        this.publishedEbookCount = publishedEbookCount;
    }

    public Long getActiveUserCount() {
        return activeUserCount;
    }

    public void setActiveUserCount(Long activeUserCount) {
        this.activeUserCount = activeUserCount;
    }

    public Long getTotalWordCount() {
        return totalWordCount;
    }

    public void setTotalWordCount(Long totalWordCount) {
        this.totalWordCount = totalWordCount;
    }
}
