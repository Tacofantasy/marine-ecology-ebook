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
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class ChapterIntegrationTests {

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

    @Test
    void rejectsNumericAliasesInOrderAndClearsOptionalSource() throws Exception {
        String token = login(createUser(UserRole.ADMIN).getUsername());
        long ebookId = createDraft(token);
        long first = createChapter(token, ebookId, "第一章", "<p>正文</p>");
        createChapter(token, ebookId, "第二章", "<p>正文</p>");
        mockMvc.perform(put("/api/admin/ebooks/{id}/chapters/order", ebookId)
                        .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("chapterIds", List.of("" + first, "0" + first)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/ebooks/{id}/chapters/{chapterId}", ebookId, first)
                        .header("satoken", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"第一章\",\"content\":\"<p>正文</p>\",\"sourceNote\":\"\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/ebooks/{id}/chapters/{chapterId}", ebookId, first).header("satoken", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourceNote").doesNotExist());
    }

    @Test
    void administratorCanManageDraftChaptersAndSanitizesHtml() throws Exception {
        String token = login(createUser(UserRole.ADMIN).getUsername());
        long ebookId = createDraft(token);
        long firstId = createChapter(token, ebookId, "第一章", "<p>安全正文</p><img src=\"/uploads/content/demo.png\" alt=\"示例图片\"><script>alert(1)</script>");
        long secondId = createChapter(token, ebookId, "第二章", "<p>第二段正文</p>");

        mockMvc.perform(get("/api/admin/ebooks/{ebookId}/chapters/{chapterId}", ebookId, firstId)
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(firstId)))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("script"))))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("安全正文")))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("/uploads/content/demo.png")));

        mockMvc.perform(put("/api/admin/ebooks/{ebookId}/chapters/order", ebookId)
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("chapterIds", List.of(String.valueOf(secondId), String.valueOf(firstId))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(secondId)))
                .andExpect(jsonPath("$.data[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data[1].id").value(String.valueOf(firstId)))
                .andExpect(jsonPath("$.data[1].sortOrder").value(2));

        mockMvc.perform(delete("/api/admin/ebooks/{ebookId}/chapters/{chapterId}", ebookId, secondId)
                        .header("satoken", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/ebooks/{ebookId}/chapters", ebookId).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(firstId)))
                .andExpect(jsonPath("$.data[0].sortOrder").value(1));
    }

    @Test
    void publishedChapterCanBeReadAndIsCountedOnlyOncePerSubject() throws Exception {
        String token = login(createUser(UserRole.ADMIN).getUsername());
        long ebookId = createDraft(token);
        uploadCover(token, ebookId);
        long chapterId = createChapter(token, ebookId, "可阅读章节", "<p>完整的公开阅读正文</p>");

        mockMvc.perform(post("/api/admin/ebooks/{ebookId}/publish", ebookId).header("satoken", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ebooks/{ebookId}/chapters", ebookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(chapterId)));
        mockMvc.perform(get("/api/ebooks/{ebookId}/chapters/{chapterId}", ebookId, chapterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("公开阅读正文")));

        mockMvc.perform(post("/api/ebooks/{ebookId}/chapters/{chapterId}/read", ebookId, chapterId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ebooks/{ebookId}/chapters/{chapterId}/read", ebookId, chapterId))
                .andExpect(status().isOk());

        Long chapterViews = jdbcTemplate.queryForObject("SELECT view_count FROM chapters WHERE id = ?", Long.class, chapterId);
        Long ebookViews = jdbcTemplate.queryForObject("SELECT view_count FROM ebooks WHERE id = ?", Long.class, ebookId);
        assertEquals(1L, chapterViews);
        assertEquals(1L, ebookViews);

        mockMvc.perform(put("/api/admin/ebooks/{ebookId}/chapters/{chapterId}", ebookId, chapterId)
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chapterPayload("发布后不能编辑", "<p>正文</p>")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("请先撤回电子书后再修改章节"));
    }

    private long createDraft(String token) throws Exception {
        Category category = categoryMapper.selectList(null).stream()
                .filter(item -> item.getParentId() != null)
                .findFirst()
                .orElseThrow();
        MvcResult result = mockMvc.perform(post("/api/admin/ebooks").header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "categoryId", category.getId(),
                                "title", "章节测试书" + suffix(),
                                "summary", "这是一份用于验证章节管理与阅读流程的完整电子书简介。",
                                "sourceNote", "项目测试内容来源说明。"))))
                .andExpect(status().isOk())
                .andReturn();
        return responseData(result).path("id").asLong();
    }

    private long createChapter(String token, long ebookId, String title, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/ebooks/{ebookId}/chapters", ebookId)
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chapterPayload(title, content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andReturn();
        return responseData(result).path("id").asLong();
    }

    private void uploadCover(String token, long ebookId) throws Exception {
        MockMultipartFile cover = new MockMultipartFile("file", "cover.png", "image/png", minimalPng());
        mockMvc.perform(multipart("/api/admin/ebooks/cover").file(cover)
                        .param("ebookId", String.valueOf(ebookId)).header("satoken", token))
                .andExpect(status().isOk());
    }

    private String chapterPayload(String title, String content) throws Exception {
        return objectMapper.writeValueAsString(Map.of("title", title, "content", content, "sourceNote", "测试章节来源"));
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private User createUser(UserRole role) {
        String username = "chapter" + suffix();
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
        return responseData(result).path("token").asText();
    }

    private byte[] minimalPng() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF005B96);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
