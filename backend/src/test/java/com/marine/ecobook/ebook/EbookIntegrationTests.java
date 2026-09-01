package com.marine.ecobook.ebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marine.ecobook.auth.mapper.UserMapper;
import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;
import com.marine.ecobook.category.mapper.CategoryMapper;
import com.marine.ecobook.category.model.Category;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EbookIntegrationTests {

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

    @Test
    void regularUserCannotManageEbooks() throws Exception {
        String token = login(createUser(UserRole.USER).getUsername());
        mockMvc.perform(get("/api/admin/ebooks").header("satoken", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void administratorCanCreateDraftAndItStaysHiddenFromPublicList() throws Exception {
        String token = login(createUser(UserRole.ADMIN).getUsername());
        Category category = secondLevelCategory();
        String title = "深海生态草稿" + suffix();
        long ebookId = createDraft(token, category.getId(), title);

        mockMvc.perform(get("/api/admin/ebooks").header("satoken", token)
                        .param("keyword", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(ebookId))
                .andExpect(jsonPath("$.data.list[0].status").value("DRAFT"));

        mockMvc.perform(get("/api/ebooks").param("keyword", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void rejectsRootCategoryAndIncompletePublication() throws Exception {
        String token = login(createUser(UserRole.ADMIN).getUsername());
        Category child = secondLevelCategory();
        Category root = categoryMapper.selectById(child.getParentId());

        mockMvc.perform(post("/api/admin/ebooks").header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(root.getId(), "不应创建" + suffix(), null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("电子书必须归属二级分类"));

        long ebookId = createDraft(token, child.getId(), "待发布草稿" + suffix());
        mockMvc.perform(post("/api/admin/ebooks/{id}/publish", ebookId).header("satoken", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("发布前请填写 20–500 字简介"));
    }

    @Test
    void coverEndpointRejectsInvalidContentAndAcceptsPngSignature() throws Exception {
        String token = login(createUser(UserRole.ADMIN).getUsername());
        long ebookId = createDraft(token, secondLevelCategory().getId(), "封面测试书" + suffix());

        MockMultipartFile invalid = new MockMultipartFile("file", "not-image.png", "image/png", "not an image".getBytes());
        mockMvc.perform(multipart("/api/admin/ebooks/cover").file(invalid).param("ebookId", String.valueOf(ebookId)).header("satoken", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("封面仅支持 JPEG、PNG 或 WebP 图片"));

        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0};
        MockMultipartFile valid = new MockMultipartFile("file", "cover.png", "image/png", png);
        mockMvc.perform(multipart("/api/admin/ebooks/cover").file(valid).param("ebookId", String.valueOf(ebookId)).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.startsWith("/uploads/covers/")));
    }

    private long createDraft(String token, long categoryId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/ebooks").header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(categoryId, title, null, null)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String payload(long categoryId, String title, String summary, String sourceNote) {
        return """
                {"categoryId":%d,"title":"%s","summary":%s,"sourceNote":%s}
                """.formatted(categoryId, title, quoted(summary), quoted(sourceNote));
    }

    private String quoted(String value) {
        return value == null ? "null" : "\"%s\"".formatted(value);
    }

    private Category secondLevelCategory() {
        return categoryMapper.selectList(null).stream().filter(category -> category.getParentId() != null).findFirst().orElseThrow();
    }

    private User createUser(UserRole role) {
        String username = "ebook" + suffix();
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
}
