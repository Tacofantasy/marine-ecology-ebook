package com.marine.ecobook.stats.controller;

import com.marine.ecobook.common.api.ApiResponse;
import com.marine.ecobook.stats.dto.StatsSummary;
import com.marine.ecobook.stats.dto.TrendPoint;
import com.marine.ecobook.stats.service.StatsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开统计接口：首页统计卡片与近 N 天趋势对访客开放，不设登录门槛。
 */
@RestController
@Validated
@RequestMapping("/api/stats")
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/summary")
    public ApiResponse<StatsSummary> summary() {
        return ApiResponse.success(statsService.summary());
    }

    @GetMapping("/trend")
    public ApiResponse<List<TrendPoint>> trend(
            @RequestParam(defaultValue = "30") @Min(value = 1, message = "天数必须大于 0")
            @Max(value = 90, message = "天数不能超过 90") int days) {
        return ApiResponse.success(statsService.trend(days));
    }
}
