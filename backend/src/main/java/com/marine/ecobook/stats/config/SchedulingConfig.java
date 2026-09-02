package com.marine.ecobook.stats.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务：统计快照（每日 00:05，见 StatsService.scheduledSnapshot）。
 * 可通过 app.stats.enabled=false 关闭（例如本地开发不需要后台任务）。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.stats.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
