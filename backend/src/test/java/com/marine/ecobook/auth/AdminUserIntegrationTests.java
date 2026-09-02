package com.marine.ecobook.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marine.ecobook.auth.mapper.UserMapper;
import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端用户运营集成测试：分页、搜索、注销幂等与踢下线、权限边界。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("integration")
class AdminUserIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------
    // 1. 列表分页：按 pageSize 切页，总数正确
    // ------------------------------------------------------------------

    @Test
    void listUsersIsPaginated() throws Exception {
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());
        String keyword = "ump" + suffix();
        for (int i = 0; i < 3; i++) {
            createUser(UserRole.USER, keyword + "user" + i);
        }

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/users").header("satoken", adminToken)
                        .param("keyword", keyword).param("page", "1").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list.length()").value(2));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/users").header("satoken", adminToken)
                        .param("keyword", keyword).param("page", "2").param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list.length()").value(1));
    }

    // ------------------------------------------------------------------
    // 2. 关键词搜索：用户名与昵称均可命中，不匹配则空
    // ------------------------------------------------------------------

    @Test
    void listUsersSearchesByUsernameAndDisplayName() throws Exception {
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());
        String marker = "usk" + suffix();
        User user = createUser(UserRole.USER, marker + "alpha");
        // 昵称与用户名不同，验证昵称搜索
        user.setDisplayName("海洋爱好者" + marker);
        userMapper.updateById(user);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/users").header("satoken", adminToken)
                        .param("keyword", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].username").value(user.getUsername()));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/users").header("satoken", adminToken)
                        .param("keyword", "海洋爱好者" + marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(String.valueOf(user.getId())));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/users").header("satoken", adminToken)
                        .param("keyword", "不存在的用户" + marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list.length()").value(0));
    }

    // ------------------------------------------------------------------
    // 3. 注销：状态置 0、deleted_at 写入、会话立即失效、重复注销幂等
    // ------------------------------------------------------------------

    @Test
    void deactivateUserKicksSessionAndIsIdempotent() throws Exception {
        String adminToken = login(createUser(UserRole.ADMIN).getUsername());
        User target = createUser(UserRole.USER);
        String targetToken = login(target.getUsername());

        // 注销前目标用户可访问个人资料
        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("satoken", targetToken))
                .andExpect(status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/users/{id}", target.getId())
                        .header("satoken", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 会话已失效
        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("satoken", targetToken))
                .andExpect(status().isUnauthorized());
        // 再次登录被拒
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"%s\",\"password\":\"password123\"}".formatted(target.getUsername())))
                .andExpect(status().isBadRequest());

        User after = userMapper.selectById(target.getId());
        assertEquals(0, after.getStatus());
        assertNotNull(after.getDeletedAt());

        // 幂等：重复注销仍成功
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/users/{id}", target.getId())
                        .header("satoken", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 已注销用户不出现在默认列表
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/users").header("satoken", adminToken)
                        .param("keyword", target.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ------------------------------------------------------------------
    // 4. 权限边界：不能注销自己；子管理员不能注销管理员；总管理员可以
    // ------------------------------------------------------------------

    @Test
    void deactivationRespectsRoleBoundaries() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        String adminToken = login(admin.getUsername());
        User anotherAdmin = createUser(UserRole.ADMIN);
        User superAdmin = createUser(UserRole.SUPER_ADMIN);
        String superToken = login(superAdmin.getUsername());

        // 子管理员不能注销自己
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/users/{id}", admin.getId())
                        .header("satoken", adminToken))
                .andExpect(status().isBadRequest());

        // 子管理员不能注销另一个管理员
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/users/{id}", anotherAdmin.getId())
                        .header("satoken", adminToken))
                .andExpect(status().isForbidden());

        // 任何人（含总管理员）不能注销总管理员
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/users/{id}", superAdmin.getId())
                        .header("satoken", superToken))
                .andExpect(status().isBadRequest());

        // 总管理员可以注销子管理员
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/users/{id}", anotherAdmin.getId())
                        .header("satoken", superToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(0, userMapper.selectById(anotherAdmin.getId()).getStatus());
    }

    // ------------------------------------------------------------------
    // 5. 访问控制：访客 40101；普通用户 40301；ID 为字符串
    // ------------------------------------------------------------------

    @Test
    void guestAndRegularUserAreRejected() throws Exception {
        User user = createUser(UserRole.USER);
        String userToken = login(user.getUsername());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/users").header("satoken", userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        String adminToken = login(createUser(UserRole.ADMIN).getUsername());
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/users")
                        .header("satoken", adminToken)
                        .param("keyword", user.getUsername()))
                .andExpect(status().isOk())
                .andReturn();
        String idText = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("list").path(0).path("id").asText();
        assertEquals(String.valueOf(user.getId()), idText, "用户 id 应序列化为字符串");
        assertTrue(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("list").path(0).path("id").isTextual());
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private User createUser(UserRole role) {
        return createUser(role, "aux" + suffix());
    }

    private User createUser(UserRole role, String username) {
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
