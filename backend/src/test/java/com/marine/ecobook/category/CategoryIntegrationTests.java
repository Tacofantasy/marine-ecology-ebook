package com.marine.ecobook.category;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marine.ecobook.auth.mapper.UserMapper;
import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;
import com.marine.ecobook.category.mapper.CategoryMapper;
import com.marine.ecobook.category.model.Category;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class CategoryIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void publicTreeIsAvailableWithoutLoginAndKeepsTwoLevels() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("海洋生态"))
                .andExpect(jsonPath("$.data[0].children[0].name").value("海洋生态基础"));
    }

    @Test
    void administratorCanCreateRootAndSecondLevelWithAutomaticOrder() throws Exception {
        String token = login(createUser(UserRole.ADMIN).getUsername());
        String suffix = uniqueSuffix();

        JsonNode root = createCategory(token, null, "一级分类" + suffix);
        JsonNode child = createCategory(token, root.path("id").asLong(), "二级分类" + suffix);

        assertEquals(root.path("id").asLong(), findCategory(child.path("id").asLong()).getParentId().longValue());
        assertEquals(1, child.path("sortOrder").asInt());
    }

    @Test
    void regularUserCannotManageCategories() throws Exception {
        String token = login(createUser(UserRole.USER).getUsername());

        mockMvc.perform(post("/api/admin/categories")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不应创建\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void rejectsDuplicateNamesAndThirdLevelCategories() throws Exception {
        String token = login(createUser(UserRole.ADMIN).getUsername());
        String suffix = uniqueSuffix();
        JsonNode root = createCategory(token, null, "唯一一级分类" + suffix);
        JsonNode child = createCategory(token, root.path("id").asLong(), "唯一二级分类" + suffix);

        mockMvc.perform(post("/api/admin/categories")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"唯一一级分类%s\"}".formatted(suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        mockMvc.perform(post("/api/admin/categories")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":%d,\"name\":\"三级分类%s\"}"
                                .formatted(child.path("id").asLong(), suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void rejectsDeletionWhenCategoryStillHasChildrenOrEbooks() throws Exception {
        String token = login(createUser(UserRole.ADMIN).getUsername());
        String suffix = uniqueSuffix();
        JsonNode root = createCategory(token, null, "待删除一级分类" + suffix);
        JsonNode child = createCategory(token, root.path("id").asLong(), "待删除二级分类" + suffix);

        mockMvc.perform(delete("/api/admin/categories/{id}", root.path("id").asLong()).header("satoken", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        jdbcTemplate.update("""
                INSERT INTO ebooks (category_id, title, summary, status, source_note)
                VALUES (?, ?, '用于分类删除保护测试。', 'DRAFT', '项目组测试内容。')
                """, child.path("id").asLong(), "分类删除保护测试书籍" + suffix);

        mockMvc.perform(delete("/api/admin/categories/{id}", child.path("id").asLong()).header("satoken", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void emptySecondLevelCategoryCanBeDeleted() throws Exception {
        String token = login(createUser(UserRole.ADMIN).getUsername());
        String suffix = uniqueSuffix();
        JsonNode root = createCategory(token, null, "空分类一级" + suffix);
        JsonNode child = createCategory(token, root.path("id").asLong(), "空分类二级" + suffix);

        mockMvc.perform(delete("/api/admin/categories/{id}", child.path("id").asLong()).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertNull(categoryMapper.selectById(child.path("id").asLong()));
    }

    @Test
    void updateKeepsTheOriginalParent() throws Exception {
        String token = login(createUser(UserRole.ADMIN).getUsername());
        String suffix = uniqueSuffix();
        JsonNode root = createCategory(token, null, "原一级分类" + suffix);
        JsonNode child = createCategory(token, root.path("id").asLong(), "待修改二级分类" + suffix);

        mockMvc.perform(put("/api/admin/categories/{id}", child.path("id").asLong())
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"修改后二级分类%s\",\"parentId\":null}".formatted(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("修改后二级分类" + suffix));

        assertEquals(root.path("id").asLong(), findCategory(child.path("id").asLong()).getParentId());
    }

    private JsonNode createCategory(String token, Long parentId, String name) throws Exception {
        String payload = parentId == null
                ? "{\"name\":\"%s\"}".formatted(name)
                : "{\"parentId\":%d,\"name\":\"%s\"}".formatted(parentId, name);
        MvcResult result = mockMvc.perform(post("/api/admin/categories")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private User createUser(UserRole role) {
        String username = "category" + uniqueSuffix();
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

    private Category findCategory(long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        assertNotNull(category);
        return category;
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
