package com.marine.ecobook.stats.service;

import com.marine.ecobook.stats.dto.StatsSummary;
import com.marine.ecobook.stats.dto.TrendPoint;
import com.marine.ecobook.stats.mapper.DailyStatMapper;
import com.marine.ecobook.stats.model.DailyStat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 统计服务：首页汇总指标与每日快照。
 * <ul>
 *   <li>当日阅读量来自 Redis 日计数键（{@code stats:read:yyyy-MM-dd}，由阅读去重命中时自增，
 *       TTL 7 天防止键堆积）；当日点赞量由 likes.created_at 实时聚合；</li>
 *   <li>累计指标始终从业务表实时聚合，不依赖快照任务是否执行过；</li>
 *   <li>快照写入通过主键 + ON DUPLICATE KEY UPDATE 幂等，支持重复执行与历史回补；</li>
 *   <li>定时任务仅是入口，核心逻辑在普通方法中，便于集成测试直接调用断言。</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class StatsService {

    public static final String READ_DAILY_KEY_PREFIX = "stats:read:";
    private static final Duration READ_DAILY_TTL = Duration.ofDays(7);
    private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    /** 中文平均阅读速度（字/分钟），用于估算预计阅读时长。 */
    private static final long WORDS_PER_MINUTE = 400;

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);

    private final DailyStatMapper dailyStatMapper;
    private final StringRedisTemplate redisTemplate;

    public StatsService(DailyStatMapper dailyStatMapper, StringRedisTemplate redisTemplate) {
        this.dailyStatMapper = dailyStatMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 首页汇总卡片：累计阅读/点赞、当日阅读/点赞、已发布电子书数、有效用户数、
     * 内容总字数与预计阅读时长（分钟）。
     */
    public StatsSummary summary() {
        LocalDate today = LocalDate.now();
        long totalWordCount = dailyStatMapper.sumPublishedWordCount();
        return new StatsSummary(
                dailyStatMapper.sumTotalViewCount(),
                dailyStatMapper.countTotalLikes(),
                todayViewCount(today),
                dailyStatMapper.countLikesBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                dailyStatMapper.countPublishedEbooks(),
                dailyStatMapper.countActiveUsers(),
                totalWordCount,
                estimatedReadingMinutes(totalWordCount));
    }

    /**
     * 近 N 天趋势（默认 30）：从快照表读取，缺失的日期补零，保证前端拿到连续日期序列。
     */
    public List<TrendPoint> trend(int days) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1L);
        List<DailyStat> stats = dailyStatMapper.selectBetween(from, today);
        Map<LocalDate, DailyStat> byDate = new HashMap<>();
        for (DailyStat stat : stats) {
            byDate.put(stat.getStatDate(), stat);
        }
        List<TrendPoint> points = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate date = from.plusDays(i);
            DailyStat stat = byDate.get(date);
            points.add(new TrendPoint(
                    date.format(KEY_DATE_FORMAT),
                    stat == null ? 0L : stat.getViewDelta(),
                    stat == null ? 0L : stat.getLikeDelta()));
        }
        return points;
    }

    /**
     * 生成指定日期的统计快照。重复执行安全：主键冲突时覆盖更新。
     * 日增阅读量优先取 Redis 日计数键（与阅读去重口径一致）；键已过期时按 0 计。
     */
    public void snapshotDaily(LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        DailyStat stat = new DailyStat();
        stat.setStatDate(date);
        stat.setTotalViewCount(dailyStatMapper.sumTotalViewCount());
        stat.setTotalLikeCount(dailyStatMapper.countTotalLikes());
        stat.setViewDelta(readDailyCount(date));
        stat.setLikeDelta(dailyStatMapper.countLikesBetween(dayStart, dayEnd));
        stat.setPublishedEbookCount(dailyStatMapper.countPublishedEbooks());
        stat.setActiveUserCount(dailyStatMapper.countActiveUsers());
        stat.setTotalWordCount(dailyStatMapper.sumPublishedWordCount());
        dailyStatMapper.upsert(stat);
        log.info("统计快照完成 date={} viewDelta={} likeDelta={}", date, stat.getViewDelta(), stat.getLikeDelta());
    }

    /**
     * 聚合任务入口：每小时把当天实时数据聚合进当日快照行，
     * 让趋势图的"今天"数据点保持实时（与首页"今日阅读/点赞"卡片同一口径）。
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "${app.stats.aggregate-cron:0 0 * * * *}")
    public void scheduledAggregateToday() {
        snapshotDaily(LocalDate.now());
    }

    /**
     * 快照任务入口：默认在每日 00:05 为昨日生成快照（阅读/点赞在自然日结束后才完整）。
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "${app.stats.snapshot-cron:0 5 0 * * *}")
    public void scheduledSnapshot() {
        snapshotDaily(LocalDate.now().minusDays(1));
    }

    /**
     * 当日阅读量（Redis 日计数键读取，键缺失视为 0）。
     */
    public long todayViewCount(LocalDate date) {
        return readDailyCount(date);
    }

    /**
     * 阅读去重命中时调用：为指定日期的 Redis 日计数键自增并刷新 TTL。
     */
    public void incrementReadCount(LocalDate date) {
        String key = READ_DAILY_KEY_PREFIX + date.format(KEY_DATE_FORMAT);
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, READ_DAILY_TTL);
    }

    private long readDailyCount(LocalDate date) {
        String value = redisTemplate.opsForValue().get(READ_DAILY_KEY_PREFIX + date.format(KEY_DATE_FORMAT));
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private long estimatedReadingMinutes(long totalWordCount) {
        if (totalWordCount <= 0) {
            return 0L;
        }
        return Math.max(1L, Math.round(totalWordCount / (double) WORDS_PER_MINUTE));
    }
}
