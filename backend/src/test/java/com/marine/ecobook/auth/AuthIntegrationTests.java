package com.marine.ecobook.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marine.ecobook.auth.mapper.UserMapper;
import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;
import cn.dev33.satoken.stp.StpUtil;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registerCreatesOnlyRegularUserWithHashedPassword() throws Exception {
        String suffix = uniqueSuffix();
        String username = "user" + suffix;
        String email = username + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"password123\"}"
                                .formatted(username, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.displayName").value(username))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        User stored = findByUsername(username);
        org.junit.jupiter.api.Assertions.assertEquals(UserRole.USER, stored.getRole());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("password123", stored.getPasswordHash()));
    }

    @Test
    void registerRejectsReservedAdminNameAndDuplicateEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"AdMiN\",\"email\":\"reserved@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        User existing = createUser(UserRole.USER, 1);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"another%s\",\"email\":\"%s\",\"password\":\"password123\"}"
                                .formatted(uniqueSuffix(), existing.getEmail())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void loginRejectsWrongPasswordAndDisabledAccount() throws Exception {
        User activeUser = createUser(UserRole.USER, 1);
        User disabledUser = createUser(UserRole.USER, 0);

        mockMvc.perform(loginRequest(activeUser.getUsername(), "wrong-password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(loginRequest(disabledUser.getUsername(), "password123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void currentUserRequiresValidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        User user = createUser(UserRole.USER, 1);
        String token = login(user.getUsername(), "password123");

        mockMvc.perform(get("/api/auth/me").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void loginCreatesTwentyFourHourSessionAndLogoutInvalidatesIt() throws Exception {
        User user = createUser(UserRole.USER, 1);
        String token = login(user.getUsername(), "password123");

        org.junit.jupiter.api.Assertions.assertTrue(StpUtil.getTokenTimeout(token) > 86300);
        org.junit.jupiter.api.Assertions.assertTrue(StpUtil.getTokenTimeout(token) <= 86400);

        mockMvc.perform(post("/api/auth/logout").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/auth/me").header("satoken", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void disabledOrDeletedAccountLosesExistingSession() throws Exception {
        User disabledUser = createUser(UserRole.USER, 1);
        String disabledToken = login(disabledUser.getUsername(), "password123");
        disabledUser.setStatus(0);
        userMapper.updateById(disabledUser);

        mockMvc.perform(get("/api/auth/me").header("satoken", disabledToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        User deletedUser = createUser(UserRole.USER, 1);
        String deletedToken = login(deletedUser.getUsername(), "password123");
        deletedUser.setDeletedAt(LocalDateTime.now());
        userMapper.updateById(deletedUser);

        mockMvc.perform(get("/api/auth/me").header("satoken", deletedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(loginRequest(deletedUser.getUsername(), "password123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void administratorEndpointsEnforceAdminHierarchy() throws Exception {
        User regularUser = createUser(UserRole.USER, 1);
        User administrator = createUser(UserRole.ADMIN, 1);
        User superAdministrator = createUser(UserRole.SUPER_ADMIN, 1);

        mockMvc.perform(get("/api/admin/auth-check").header("satoken", login(regularUser.getUsername(), "password123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/admin/auth-check").header("satoken", login(administrator.getUsername(), "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(administrator.getId()))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        mockMvc.perform(get("/api/admin/auth-check").header("satoken", login(superAdministrator.getUsername(), "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"));

        mockMvc.perform(get("/api/admin/super-admin/auth-check")
                        .header("satoken", login(administrator.getUsername(), "password123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/admin/super-admin/auth-check")
                        .header("satoken", login(superAdministrator.getUsername(), "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"));
    }

    @Test
    void seededAdministratorCanLogInWithDocumentedDevelopmentPassword() throws Exception {
        String token = login("admin", "password");

        mockMvc.perform(get("/api/admin/auth-check").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("SUPER_ADMIN"));
    }

    private User createUser(UserRole role, int status) {
        String username = "test" + uniqueSuffix();
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setRole(role);
        user.setStatus(status);
        userMapper.insert(user);
        return user;
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    private String login(String account, String password) throws Exception {
        MvcResult result = mockMvc.perform(loginRequest(account, password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("data").path("token").asText();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String account, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"%s\",\"password\":\"%s\"}".formatted(account, password));
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
