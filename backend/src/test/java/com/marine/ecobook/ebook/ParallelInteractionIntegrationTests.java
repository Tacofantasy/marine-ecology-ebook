package com.marine.ecobook.ebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marine.ecobook.auth.mapper.UserMapper;
import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;
import com.marine.ecobook.category.mapper.CategoryMapper;
import com.marine.ecobook.category.model.Category;
import com.marine.ecobook.ebook.mapper.EbookMapper;
import com.marine.ecobook.ebook.model.Ebook;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真正并行的点赞/收藏请求验证：多线程同时调用同一互动接口，
 * 依赖数据库唯一键 + INSERT IGNORE 保证无重复行、无未处理异常。
 * <p>
 * 本类不使用 {@code @Transactional}，因为测试线程内的数据未提交，
 * 子线程发出的请求看不到主线程事务中的数据。改为手动登记创建的用户/电子书，
 * 在 {@link #cleanup()} 中按外键级联清理，不污染共享数据库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
class ParallelInteractionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EbookMapper ebookMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdUserIds = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> createdEbookIds = Collections.synchronizedList(new ArrayList<>());

    @AfterEach
    void cleanup() {
        for (Long ebookId : createdEbookIds) {
            jdbcTemplate.update("DELETE FROM ebooks WHERE id = ?", ebookId);
        }
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdEbookIds.clear();
        createdUserIds.clear();
    }

    @Test
    void concurrentLikesBySameUserLeaveSingleRow() throws Exception {
        User user = createUser();
        String token = login(user.getUsername());
        long ebookId = createPublishedEbook();

        List<MvcResult> results = runConcurrently(8, () ->
                mockMvc.perform(post("/api/ebooks/{id}/like", ebookId).header("satoken", token)).andReturn());

        for (MvcResult result : results) {
            JsonNode data = dataOf(result);
            assertEquals(0, data.path("code").asInt(), "所有并发点赞都应成功");
            assertTrue(data.path("data").path("liked").asBoolean());
            assertTrue(data.path("data").path("likeCount").isTextual());
        }
        assertEquals(1, countRows("likes", user.getId(), ebookId), "likes 应只有一行");

        mockMvc.perform(get("/api/ebooks/{id}/interaction", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value("1"));
    }

    @Test
    void concurrentFavoritesBySameUserLeaveSingleRow() throws Exception {
        User user = createUser();
        String token = login(user.getUsername());
        long ebookId = createPublishedEbook();

        List<MvcResult> results = runConcurrently(8, () ->
                mockMvc.perform(post("/api/ebooks/{id}/favorite", ebookId).header("satoken", token)).andReturn());

        for (MvcResult result : results) {
            JsonNode data = dataOf(result);
            assertEquals(0, data.path("code").asInt(), "所有并发收藏都应成功");
            assertTrue(data.path("data").path("favorited").asBoolean());
        }
        assertEquals(1, countRows("favorites", user.getId(), ebookId), "favorites 应只有一行");

        mockMvc.perform(get("/api/me/favorites").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void concurrentLikesByDistinctUsersAggregateCorrectly() throws Exception {
        long ebookId = createPublishedEbook();
        List<Callable<MvcResult>> actions = new ArrayList<>();
        int users = 8;
        for (int i = 0; i < users; i++) {
            User user = createUser();
            String token = login(user.getUsername());
            actions.add(() -> mockMvc.perform(
                    post("/api/ebooks/{id}/like", ebookId).header("satoken", token)).andReturn());
        }

        List<MvcResult> results = runConcurrently(actions);

        for (MvcResult result : results) {
            JsonNode data = dataOf(result);
            assertEquals(0, data.path("code").asInt());
            assertTrue(data.path("data").path("liked").asBoolean());
        }
        assertEquals(users, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE ebook_id = ?", Integer.class, ebookId));

        String anyToken = login(createUser().getUsername());
        mockMvc.perform(get("/api/ebooks/{id}/interaction", ebookId).header("satoken", anyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(String.valueOf(users)));
    }

    @Test
    void concurrentMixedLikeAndUnlikeStaysConsistentWithoutErrors() throws Exception {
        User user = createUser();
        String token = login(user.getUsername());
        long ebookId = createPublishedEbook();

        List<Callable<MvcResult>> actions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            actions.add(() -> mockMvc.perform(
                    post("/api/ebooks/{id}/like", ebookId).header("satoken", token)).andReturn());
        }
        for (int i = 0; i < 4; i++) {
            actions.add(() -> mockMvc.perform(
                    delete("/api/ebooks/{id}/like", ebookId).header("satoken", token)).andReturn());
        }

        List<MvcResult> results = runConcurrently(actions);

        for (MvcResult result : results) {
            assertEquals(0, dataOf(result).path("code").asInt(), "并发 like/unlike 均不应返回错误");
        }
        // 最终状态取决于执行顺序：行数只能为 0 或 1，但绝不应重复或报错。
        int rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE user_id = ? AND ebook_id = ?",
                Integer.class, user.getId(), ebookId);
        assertTrue(rows == 0 || rows == 1, "like/unlike 并发后行数应为 0 或 1，实际 " + rows);

        mockMvc.perform(get("/api/ebooks/{id}/interaction", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(rows > 0))
                .andExpect(jsonPath("$.data.likeCount").value(String.valueOf(rows)));
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int countRows(String table, long userId, long ebookId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ? AND ebook_id = ?",
                Integer.class, userId, ebookId);
    }

    /**
     * 让所有动作线程在同一栅栏上就绪后同时放行，制造真实并发请求。
     */
    private List<MvcResult> runConcurrently(int threads, Callable<MvcResult> action) throws Exception {
        List<Callable<MvcResult>> actions = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            actions.add(action);
        }
        return runConcurrently(actions);
    }

    private List<MvcResult> runConcurrently(List<Callable<MvcResult>> actions) throws Exception {
        int n = actions.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CyclicBarrier barrier = new CyclicBarrier(n);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> action : actions) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return action.call();
                }));
            }
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private User createUser() {
        String username = "itp" + suffix();
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setDisplayName(username);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setRole(UserRole.USER);
        user.setStatus(1);
        userMapper.insert(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private long createPublishedEbook() {
        Ebook ebook = new Ebook();
        ebook.setCategoryId(secondLevelCategory().getId());
        ebook.setTitle("并发互动测试" + suffix());
        ebook.setCoverUrl("/uploads/covers/parallel-test.webp");
        ebook.setSummary("用于并发互动集成测试的演示简介，长度超过二十个字符。");
        ebook.setSourceNote("并发测试自制内容。");
        ebook.setStatus("PUBLISHED");
        ebook.setPublishedAt(LocalDateTime.now());
        ebookMapper.insert(ebook);
        createdEbookIds.add(ebook.getId());
        return ebook.getId();
    }

    private Category secondLevelCategory() {
        return categoryMapper.selectList(null).stream()
                .filter(category -> category.getParentId() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("数据库缺少二级分类"));
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
}
