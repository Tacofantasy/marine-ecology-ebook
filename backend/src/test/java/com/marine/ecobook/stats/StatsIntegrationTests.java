package com.marine.ecobook.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marine.ecobook.auth.mapper.UserMapper;
import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;
import com.marine.ecobook.category.mapper.CategoryMapper;
import com.marine.ecobook.category.model.Category;
import com.marine.ecobook.stats.mapper.DailyStatMapper;
import com.marine.ecobook.stats.model.DailyStat;
import com.marine.ecobook.stats.service.StatsService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统计模块集成测试：快照幂等、汇总口径、趋势补零、参数校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class StatsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private DailyStatMapper dailyStatMapper;

    @Autowired
    private StatsService statsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void todayTrendMatchesLiveSummaryBeforeSnapshot() {
        LocalDate today = LocalDate.now();
        statsService.incrementReadCount(today);
        assertEquals(statsService.summary().todayViewCount(), statsService.trend(1).get(0).viewDelta());
    }

    @Test
    void finalizedHistoryIsNotOverwrittenAfterRedisExpires() {
        LocalDate date = LocalDate.now().minusDays(8);
        jdbcTemplate.update("""
                INSERT INTO daily_stats (stat_date, total_view_count, view_delta, like_delta)
                VALUES (?, 120, 12, 3)
                """, date);
        statsService.snapshotDaily(date);
        DailyStat saved = dailyStatMapper.selectById(date);
        assertEquals(120L, saved.getTotalViewCount());
        assertEquals(12L, saved.getViewDelta());
        assertEquals(3L, saved.getLikeDelta());
    }

    // ------------------------------------------------------------------
    // 1. 快照任务幂等：同一天重复执行 snapshotDaily 数值不叠加、仅一行
    // ------------------------------------------------------------------

    @Test
    void snapshotDailyIsIdempotentWhenRunTwice() {
        LocalDate today = LocalDate.now();
        long expectedViews = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(view_count), 0) FROM ebooks", Long.class);
        long expectedLikes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM likes", Long.class);

        statsService.snapshotDaily(today);
        statsService.snapshotDaily(today);

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_stats WHERE stat_date = ?", Integer.class, today));
        DailyStat stat = dailyStatMapper.selectById(today);
        assertEquals(expectedViews, stat.getTotalViewCount(), "重复执行快照不应叠加累计阅读量");
        assertEquals(expectedLikes, stat.getTotalLikeCount(), "重复执行快照不应叠加累计点赞量");
    }

    // ------------------------------------------------------------------
    // 2. 当日阅读计数：Redis 日键自增、summary 返回今日值
    // ------------------------------------------------------------------

    @Test
    void summaryReflectsTodayViewCountFromRedisKey() throws Exception {
        LocalDate today = LocalDate.now();
        String key = StatsService.READ_DAILY_KEY_PREFIX + today;
        try {
            statsService.incrementReadCount(today);
            statsService.incrementReadCount(today);
            statsService.incrementReadCount(today);

            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/stats/summary"))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            // 今日阅读量来自 Redis，其他测试可能也有写入，只验证 >= 3
            long todayViews = objectMapper.readTree(body).path("data").path("todayViewCount").asLong();
            assertTrue(todayViews >= 3, "今日阅读量应不少于本测试自增的 3 次，实际 " + todayViews);
        } finally {
            redisTemplate.delete(key);
        }
    }

    // ------------------------------------------------------------------
    // 3. 预计阅读时长：与已发布章节字数联动（400 字/分钟）
    // ------------------------------------------------------------------

    @Test
    void summaryComputesEstimatedReadingMinutesFromWordCount() throws Exception {
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());
        long ebookId = createPublishedEbook(adminToken, "统计阅读时长测试" + suffix());
        // 追加一篇 800 字的已发布章节：预计阅读时长应 >= 2 分钟
        jdbcTemplate.update("""
                INSERT INTO chapters (ebook_id, title, content, sort_order, status, source_note, word_count)
                VALUES (?, '时长章节', '<p>字</p>', 2, 'PUBLISHED', '统计测试', 800)
                """, ebookId);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/stats/summary"))
                .andExpect(status().isOk())
                .andReturn();
        long minutes = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("estimatedReadingMinutes").asLong();
        assertTrue(minutes >= 2, "800 字至少应折算 2 分钟，实际 " + minutes);
    }

    // ------------------------------------------------------------------
    // 4. 趋势接口：连续日期、缺失日期补零、公开可访问
    // ------------------------------------------------------------------

    @Test
    void trendReturnsContinuousDatesWithZeroFill() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        try {
            // 只给昨天生成快照，今天与前天缺失
            jdbcTemplate.update("""
                    INSERT INTO daily_stats (stat_date, total_view_count, total_like_count, view_delta, like_delta,
                        published_ebook_count, active_user_count, total_word_count)
                    VALUES (?, 100, 10, 5, 2, 1, 1, 1000)
                    """, yesterday);

            MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/stats/trend")
                            .param("days", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            var array = objectMapper.readTree(body).path("data");
            assertEquals(today.minusDays(2).toString(), array.get(0).path("date").asText());
            assertEquals(yesterday.toString(), array.get(1).path("date").asText());
            assertEquals(5, array.get(1).path("viewDelta").asLong(), "昨日快照的日增阅读量应为 5");
            assertEquals(2, array.get(1).path("likeDelta").asLong(), "昨日快照的日增点赞量应为 2");
            assertEquals(0, array.get(0).path("viewDelta").asLong(), "缺失日期应补零");
            assertEquals(statsService.todayViewCount(today), array.get(2).path("viewDelta").asLong(), "今日趋势应与实时卡片一致");
        } finally {
            jdbcTemplate.update("DELETE FROM daily_stats WHERE stat_date = ?", yesterday);
        }
    }

    // ------------------------------------------------------------------
    // 5. 参数校验：days 越界返回 40001
    // ------------------------------------------------------------------

    @Test
    void trendRejectsInvalidDays() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/stats/trend").param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/stats/trend").param("days", "91"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private long createPublishedEbook(String adminToken, String title) throws Exception {
        long ebookId = createDraft(adminToken, title);
        uploadCover(adminToken, ebookId);
        jdbcTemplate.update("""
                INSERT INTO chapters (ebook_id, title, content, sort_order, status, source_note, word_count)
                VALUES (?, '统计测试章节', '<p>统计集成测试正文。</p>', 1, 'PUBLISHED', '统计测试自制内容。', 20)
                """, ebookId);
        mockMvc.perform(post("/api/admin/ebooks/{id}/publish", ebookId).header("satoken", adminToken))
                .andExpect(status().isOk());
        return ebookId;
    }

    private long createDraft(String adminToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/ebooks").header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(title)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void uploadCover(String adminToken, long ebookId) throws Exception {
        var cover = new org.springframework.mock.web.MockMultipartFile(
                "file", "cover.png", "image/png", createMinimalPng());
        mockMvc.perform(MockMvcRequestBuilders
                        .multipart("/api/admin/ebooks/cover").file(cover)
                        .param("ebookId", String.valueOf(ebookId)).header("satoken", adminToken))
                .andExpect(status().isOk());
    }

    private String payload(String title) {
        String summary = "这是一段用于发布校验的简介，长度超过二十个字符以满足发布要求。";
        return """
                {"categoryId":%d,"title":"%s","summary":"%s","sourceNote":"统计集成测试自制内容。"}
                """.formatted(secondLevelCategory().getId(), title, summary);
    }

    private Category secondLevelCategory() {
        return categoryMapper.selectList(null).stream()
                .filter(category -> category.getParentId() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("数据库缺少二级分类"));
    }

    private User createUser(UserRole role) {
        String username = "stx" + suffix();
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setDisplayName(username);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"%s\",\"password\":\"password123\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private byte[] createMinimalPng() throws java.io.IOException {
        var image = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF0000FF);
        var baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}
