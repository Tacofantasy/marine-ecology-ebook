package com.marine.ecobook.stats.dto;

public record TrendPoint(
        String date,
        long viewDelta,
        long likeDelta) {
}
