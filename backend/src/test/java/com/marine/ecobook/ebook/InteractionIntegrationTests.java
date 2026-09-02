package com.marine.ecobook.ebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marine.ecobook.auth.mapper.UserMapper;
import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;
import com.marine.ecobook.category.mapper.CategoryMapper;
import com.marine.ecobook.category.model.Category;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class InteractionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------
    // 1. USER 可点赞、读状态、取消点赞，likeCount 0 -> 1 -> 0
    // ------------------------------------------------------------------

    @Test
    void userCanLikeReadStateAndUnlikeWithCountTransition() throws Exception {
        User user = createUser(UserRole.USER);
        String token = login(user.getUsername());
        long ebookId = createPublishedEbook();

        mockMvc.perform(get("/api/ebooks/{id}/interaction", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.favorited").value(false))
                .andExpect(jsonPath("$.data.likeCount").value("0"));

        mockMvc.perform(post("/api/ebooks/{id}/like", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value("1"));

        mockMvc.perform(delete("/api/ebooks/{id}/like", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value("0"));
    }

    // ------------------------------------------------------------------
    // 2. 幂等：连续两次 POST like / DELETE like 均成功且表中至多一行
    // ------------------------------------------------------------------

    @Test
    void likeAndUnlikeAreIdempotent() throws Exception {
        User user = createUser(UserRole.USER);
        String token = login(user.getUsername());
        long ebookId = createPublishedEbook();
        long userId = user.getId();

        mockMvc.perform(post("/api/ebooks/{id}/like", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true));
        mockMvc.perform(post("/api/ebooks/{id}/like", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value("1"));
        assertEquals(1, countRows("likes", userId, ebookId));

        mockMvc.perform(delete("/api/ebooks/{id}/like", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false));
        mockMvc.perform(delete("/api/ebooks/{id}/like", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value("0"));
        assertEquals(0, countRows("likes", userId, ebookId));
    }

    @Test
    void favoriteAndUnfavoriteAreIdempotent() throws Exception {
        User user = createUser(UserRole.USER);
        String token = login(user.getUsername());
        long ebookId = createPublishedEbook();
        long userId = user.getId();

        mockMvc.perform(post("/api/ebooks/{id}/favorite", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(true));
        mockMvc.perform(post("/api/ebooks/{id}/favorite", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.favorited").value(true));
        assertEquals(1, countRows("favorites", userId, ebookId));

        mockMvc.perform(delete("/api/ebooks/{id}/favorite", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(false));
        mockMvc.perform(delete("/api/ebooks/{id}/favorite", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.favorited").value(false));
        assertEquals(0, countRows("favorites", userId, ebookId));
    }

    // ------------------------------------------------------------------
    // 3. 两名 USER 点赞同一本书，公开列表与详情返回 likeCount = 2 / 1
    // ------------------------------------------------------------------

    @Test
    void publicEndpointsAggregateLikeCountAcrossUsers() throws Exception {
        String title = "多人点赞测试" + suffix();
        long ebookId = createPublishedEbook(title);
        String token1 = login(createUser(UserRole.USER).getUsername());
        String token2 = login(createUser(UserRole.USER).getUsername());

        mockMvc.perform(post("/api/ebooks/{id}/like", ebookId).header("satoken", token1))
                .andExpect(jsonPath("$.data.likeCount").value("1"));
        mockMvc.perform(post("/api/ebooks/{id}/like", ebookId).header("satoken", token2))
                .andExpect(jsonPath("$.data.likeCount").value("2"));

        mockMvc.perform(get("/api/ebooks").param("keyword", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].likeCount").value("2"));

        mockMvc.perform(get("/api/ebooks/{id}", ebookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value("2"));

        mockMvc.perform(delete("/api/ebooks/{id}/like", ebookId).header("satoken", token1))
                .andExpect(jsonPath("$.data.likeCount").value("1"));

        mockMvc.perform(get("/api/ebooks/{id}", ebookId))
                .andExpect(jsonPath("$.data.likeCount").value("1"));
    }

    // ------------------------------------------------------------------
    // 4. 访客 40101；ADMIN / SUPER_ADMIN 40301
    // ------------------------------------------------------------------

    @Test
    void guestsAndAdministratorsCannotInteract() throws Exception {
        long ebookId = createPublishedEbook();

        // 访客
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get("/api/ebooks/{id}/interaction", ebookId),
                post("/api/ebooks/{id}/like", ebookId),
                delete("/api/ebooks/{id}/like", ebookId),
                post("/api/ebooks/{id}/favorite", ebookId),
                delete("/api/ebooks/{id}/favorite", ebookId),
                get("/api/me/favorites")}) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40101));
        }

        // ADMIN
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get("/api/ebooks/{id}/interaction", ebookId),
                post("/api/ebooks/{id}/like", ebookId),
                delete("/api/ebooks/{id}/like", ebookId),
                post("/api/ebooks/{id}/favorite", ebookId),
                delete("/api/ebooks/{id}/favorite", ebookId),
                get("/api/me/favorites")}) {
            mockMvc.perform(request.header("satoken", adminToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40301));
        }

        // SUPER_ADMIN
        String superAdminToken = login(createUser(UserRole.SUPER_ADMIN).getUsername());
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get("/api/ebooks/{id}/interaction", ebookId),
                post("/api/ebooks/{id}/like", ebookId),
                get("/api/me/favorites")}) {
            mockMvc.perform(request.header("satoken", superAdminToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40301));
        }

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE ebook_id = ?", Integer.class, ebookId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE ebook_id = ?", Integer.class, ebookId));
    }

    // ------------------------------------------------------------------
    // 5. 草稿、撤回、错误 ID：读写均 40401 且不产生记录
    // ------------------------------------------------------------------

    @Test
    void draftWithdrawnAndMissingEbooksAreRejected() throws Exception {
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());
        String userToken = login(createUser(UserRole.USER).getUsername());

        long draftId = createDraft(adminToken);

        // 草稿书
        assertAllInteractionEndpointsReject(userToken, draftId);
        // 撤回书（先发布再撤回）
        makePublishable(adminToken, draftId);
        publish(adminToken, draftId);
        mockMvc.perform(post("/api/admin/ebooks/{id}/unpublish", draftId).header("satoken", adminToken))
                .andExpect(status().isOk());
        assertAllInteractionEndpointsReject(userToken, draftId);
        // 不存在的书
        assertAllInteractionEndpointsReject(userToken, 999999999L);

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE ebook_id = ?", Integer.class, draftId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE ebook_id = ?", Integer.class, draftId));
    }

    // ------------------------------------------------------------------
    // 6. 收藏后撤回：我的收藏不返回，记录仍在；重新发布后恢复
    // ------------------------------------------------------------------

    @Test
    void withdrawnEbookDisappearsFromFavoritesUntilRepublished() throws Exception {
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());
        String userToken = login(createUser(UserRole.USER).getUsername());
        String title = "撤回恢复测试" + suffix();
        long ebookId = createPublishedEbook(title);

        mockMvc.perform(post("/api/ebooks/{id}/favorite", ebookId).header("satoken", userToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/favorites").header("satoken", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value(title));

        mockMvc.perform(post("/api/admin/ebooks/{id}/unpublish", ebookId).header("satoken", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/favorites").header("satoken", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE ebook_id = ?", Integer.class, ebookId));

        publish(adminToken, ebookId);

        mockMvc.perform(get("/api/me/favorites").header("satoken", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value(title));
    }

    // ------------------------------------------------------------------
    // 7. 删除草稿书时级联清理互动记录；已发布书仍拒绝直接删除
    // ------------------------------------------------------------------

    @Test
    void deletingDraftCascadesInteractionRecords() throws Exception {
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());
        String userToken = login(createUser(UserRole.USER).getUsername());
        long ebookId = createPublishedEbook();

        mockMvc.perform(post("/api/ebooks/{id}/like", ebookId).header("satoken", userToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ebooks/{id}/favorite", ebookId).header("satoken", userToken))
                .andExpect(status().isOk());

        // 已发布书不允许直接删除
        mockMvc.perform(delete("/api/admin/ebooks/{id}", ebookId).header("satoken", adminToken))
                .andExpect(status().isConflict());

        // 撤回后（草稿）删除，likes / favorites 级联清理
        mockMvc.perform(post("/api/admin/ebooks/{id}/unpublish", ebookId).header("satoken", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/ebooks/{id}", ebookId).header("satoken", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE ebook_id = ?", Integer.class, ebookId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE ebook_id = ?", Integer.class, ebookId));
    }

    // ------------------------------------------------------------------
    // 8. 我的收藏排序、分页与参数校验
    // ------------------------------------------------------------------

    @Test
    void favoritesAreSortedByFavoritedAtDescAndPaginated() throws Exception {
        User user = createUser(UserRole.USER);
        String userToken = login(user.getUsername());
        String otherToken = login(createUser(UserRole.USER).getUsername());
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());

        long ebook1 = createPublishedEbook("收藏排序一" + suffix());
        long ebook2 = createPublishedEbook("收藏排序二" + suffix());
        long ebook3 = createPublishedEbook("收藏排序三" + suffix());

        mockMvc.perform(post("/api/ebooks/{id}/favorite", ebook1).header("satoken", userToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ebooks/{id}/favorite", ebook2).header("satoken", userToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ebooks/{id}/favorite", ebook3).header("satoken", userToken))
                .andExpect(status().isOk());

        // 其他用户对 ebook2 的收藏不应混入；撤回 ebook3 也不应出现
        mockMvc.perform(post("/api/ebooks/{id}/favorite", ebook2).header("satoken", otherToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/ebooks/{id}/unpublish", ebook3).header("satoken", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/favorites").header("satoken", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.list[0].id").value(String.valueOf(ebook2)))
                .andExpect(jsonPath("$.data.list[1].id").value(String.valueOf(ebook1)))
                .andExpect(jsonPath("$.data.list[0].favoritedAt").exists())
                .andExpect(jsonPath("$.data.list[0].likeCount").isString());

        // 分页：pageSize=1 翻页
        mockMvc.perform(get("/api/me/favorites").header("satoken", userToken)
                        .param("page", "1").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(String.valueOf(ebook2)));

        // pageSize = 20 合法
        mockMvc.perform(get("/api/me/favorites").header("satoken", userToken)
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // pageSize = 0 与 51 非法
        mockMvc.perform(get("/api/me/favorites").header("satoken", userToken)
                        .param("pageSize", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/me/favorites").header("satoken", userToken)
                        .param("pageSize", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    // ------------------------------------------------------------------
    // 9. 大于 JS 安全整数的 ID 与 likeCount 均为字符串
    // ------------------------------------------------------------------

    @Test
    void bigIdsAndCountsAreSerializedAsStrings() throws Exception {
        String userToken = login(createUser(UserRole.USER).getUsername());
        long ebookId = createPublishedEbook("大整数精度测试" + suffix());

        // MyBatis-Plus 雪花 ID（如 2094773790913531906）已大于 JavaScript 安全整数 2^53。
        // 本断言依赖生成的电子书 ID 超出安全范围，验证序列化为字符串且精度不丢失。
        assertTrue(ebookId > 9_007_199_254_740_991L,
                "雪花 ID 应大于 JS 安全整数，实际为 " + ebookId);

        mockMvc.perform(post("/api/ebooks/{id}/like", ebookId).header("satoken", userToken))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/ebooks/{id}/interaction", ebookId)
                        .header("satoken", userToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertTrue(data.path("likeCount").isTextual(), "likeCount 应为字符串");
        assertEquals("1", data.path("likeCount").asText());
        assertTrue(data.path("liked").asBoolean());

        MvcResult detailResult = mockMvc.perform(get("/api/ebooks/{id}", ebookId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detail = objectMapper.readTree(detailResult.getResponse().getContentAsString()).path("data");
        assertTrue(detail.path("likeCount").isTextual(), "likeCount 应为字符串");
        assertEquals("1", detail.path("likeCount").asText());
        assertTrue(detail.path("id").isTextual(), "id 应为字符串");
        assertEquals(String.valueOf(ebookId), detail.path("id").asText(), "字符串 id 应无精度丢失");

        mockMvc.perform(post("/api/ebooks/{id}/favorite", ebookId).header("satoken", userToken))
                .andExpect(status().isOk());

        MvcResult favoritesResult = mockMvc.perform(get("/api/me/favorites")
                        .header("satoken", userToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode item = objectMapper.readTree(favoritesResult.getResponse().getContentAsString())
                .path("data").path("list").path(0);
        assertTrue(item.path("id").isTextual(), "收藏项 id 应为字符串");
        assertTrue(item.path("likeCount").isTextual(), "收藏项 likeCount 应为字符串");
        assertEquals(String.valueOf(ebookId), item.path("id").asText());
        assertEquals("1", item.path("likeCount").asText());
    }

    // ------------------------------------------------------------------
    // 10. 唯一键冲突被服务层转化为幂等成功（绕过 INSERT IGNORE 的重复插入场景）
    // ------------------------------------------------------------------

    @Test
    void duplicateKeyConflictIsTreatedAsIdempotentSuccess() throws Exception {
        User user = createUser(UserRole.USER);
        String userToken = login(user.getUsername());
        long ebookId = createPublishedEbook();
        long userId = user.getId();

        mockMvc.perform(post("/api/ebooks/{id}/like", ebookId).header("satoken", userToken))
                .andExpect(status().isOk());

        // 绕过服务层直接重复执行与 insertIgnore 相同的 SQL：验证唯一键冲突
        // 被 INSERT IGNORE 语义吞掉（不抛异常、不产生第二行）。
        jdbcTemplate.update("INSERT IGNORE INTO likes (user_id, ebook_id) VALUES (?, ?)", userId, ebookId);
        int rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM likes WHERE user_id = ? AND ebook_id = ?",
                Integer.class, userId, ebookId);
        assertEquals(1, rows);

        // 再次通过 API 点赞：仍然成功且幂等
        mockMvc.perform(post("/api/ebooks/{id}/like", ebookId).header("satoken", userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value("1"));

        // favorites 同样验证
        jdbcTemplate.update("INSERT IGNORE INTO favorites (user_id, ebook_id) VALUES (?, ?)", userId, ebookId);
        jdbcTemplate.update("INSERT IGNORE INTO favorites (user_id, ebook_id) VALUES (?, ?)", userId, ebookId);
        int favoriteRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE user_id = ? AND ebook_id = ?",
                Integer.class, userId, ebookId);
        assertEquals(1, favoriteRows);
    }

    // ------------------------------------------------------------------
    // 11. 超大页码返回空页，而不是 int 溢出产生负 OFFSET 导致 500
    // ------------------------------------------------------------------

    @Test
    void hugePageNumbersReturnEmptyListInsteadOfOverflowing() throws Exception {
        String userToken = login(createUser(UserRole.USER).getUsername());
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());
        String title = "超大页码测试" + suffix();
        long ebookId = createPublishedEbook(title);

        mockMvc.perform(post("/api/ebooks/{id}/favorite", ebookId).header("satoken", userToken))
                .andExpect(status().isOk());

        // page=2147483647、pageSize=50 时 (page-1)*pageSize ≈ 1.07e11，远超 int 范围。
        // 若用 int 计算会溢出为负 OFFSET，导致 SQL 报错 -> 500。
        mockMvc.perform(get("/api/me/favorites").header("satoken", userToken)
                        .param("page", "2147483647").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list.length()").value(0));

        mockMvc.perform(get("/api/ebooks").param("keyword", title)
                        .param("page", "2147483647").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list.length()").value(0));

        mockMvc.perform(get("/api/admin/ebooks").param("keyword", title).header("satoken", adminToken)
                        .param("page", "2147483647").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list.length()").value(0));
    }

    // ------------------------------------------------------------------
    // 12. 管理端接口不暴露 likeCount；公开接口保留
    // ------------------------------------------------------------------

    @Test
    void adminEndpointsDoNotExposeLikeCount() throws Exception {
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());
        String userToken = login(createUser(UserRole.USER).getUsername());
        String title = "管理端点赞隐藏" + suffix();
        long ebookId = createPublishedEbook(title);

        mockMvc.perform(post("/api/ebooks/{id}/like", ebookId).header("satoken", userToken))
                .andExpect(status().isOk());

        // 公开接口包含 likeCount
        mockMvc.perform(get("/api/ebooks/{id}", ebookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value("1"));

        // 管理端详情与列表不包含 likeCount
        mockMvc.perform(get("/api/admin/ebooks/{id}", ebookId).header("satoken", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").doesNotExist());
        mockMvc.perform(get("/api/admin/ebooks").param("keyword", title).header("satoken", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].likeCount").doesNotExist());
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private void assertAllInteractionEndpointsReject(String token, long ebookId) throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get("/api/ebooks/{id}/interaction", ebookId),
                post("/api/ebooks/{id}/like", ebookId),
                delete("/api/ebooks/{id}/like", ebookId),
                post("/api/ebooks/{id}/favorite", ebookId),
                delete("/api/ebooks/{id}/favorite", ebookId)}) {
            mockMvc.perform(request.header("satoken", token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(40401))
                    .andExpect(jsonPath("$.message").value("电子书不存在或尚未发布"));
        }
    }

    private int countRows(String table, long userId, long ebookId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ? AND ebook_id = ?",
                Integer.class, userId, ebookId);
    }

    private long createPublishedEbook() throws Exception {
        return createPublishedEbook("互动测试电子书" + suffix());
    }

    private long createPublishedEbook(String title) throws Exception {
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());
        long ebookId = createDraft(adminToken, title);
        makePublishable(adminToken, ebookId);
        publish(adminToken, ebookId);
        return ebookId;
    }

    /**
     * 补齐发布校验所需条件：封面与至少一篇正文非空章节。
     */
    private void makePublishable(String adminToken, long ebookId) throws Exception {
        uploadCover(adminToken, ebookId);
        jdbcTemplate.update("""
                INSERT INTO chapters (ebook_id, title, content, sort_order, status, source_note)
                VALUES (?, '互动测试章节', '<p>互动集成测试正文。</p>', 1, 'PUBLISHED', '互动测试自制内容。')
                """, ebookId);
    }

    private long createDraft(String adminToken) throws Exception {
        return createDraft(adminToken, "草稿互动测试" + suffix());
    }

    private long createDraft(String adminToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/ebooks").header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(title)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private void uploadCover(String adminToken, long ebookId) throws Exception {
        MockMultipartFile cover = new MockMultipartFile("file", "cover.png", "image/png", createMinimalPng());
        mockMvc.perform(multipart("/api/admin/ebooks/cover").file(cover)
                        .param("ebookId", String.valueOf(ebookId)).header("satoken", adminToken))
                .andExpect(status().isOk());
    }

    private void publish(String adminToken, long ebookId) throws Exception {
        mockMvc.perform(post("/api/admin/ebooks/{id}/publish", ebookId).header("satoken", adminToken))
                .andExpect(status().isOk());
    }

    private String payload(String title) {
        String summary = "这是一段用于发布校验的简介，长度超过二十个字符以满足发布要求。";
        return """
                {"categoryId":%d,"title":"%s","summary":"%s","sourceNote":"互动集成测试自制内容。"}
                """.formatted(secondLevelCategory().getId(), title, summary);
    }

    private Category secondLevelCategory() {
        return categoryMapper.selectList(null).stream()
                .filter(category -> category.getParentId() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("数据库缺少二级分类"));
    }

    private User createUser(UserRole role) {
        String username = "itx" + suffix();
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

    private byte[] createMinimalPng() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF0000FF);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}
