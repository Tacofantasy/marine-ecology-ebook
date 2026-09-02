package com.marine.ecobook.stats.dto;

public record StatsSummary(
        long totalViewCount,
        long totalLikeCount,
        long todayViewCount,
        long todayLikeCount,
        long publishedEbookCount,
        long activeUserCount,
        long totalWordCount,
        long estimatedReadingMinutes) {
}
