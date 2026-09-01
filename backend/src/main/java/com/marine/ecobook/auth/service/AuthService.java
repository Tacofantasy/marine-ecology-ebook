package com.marine.ecobook.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marine.ecobook.auth.dto.LoginRequest;
import com.marine.ecobook.auth.dto.LoginResponse;
import com.marine.ecobook.auth.dto.RegisterRequest;
import com.marine.ecobook.auth.dto.UserProfile;
import com.marine.ecobook.auth.mapper.UserMapper;
import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;
import com.marine.ecobook.common.api.ResultCode;
import com.marine.ecobook.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final String RESERVED_ADMIN_USERNAME = "admin";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserProfile register(RegisterRequest request) {
        String username = request.username().trim();
        String email = StringUtils.hasText(request.email()) ? request.email().trim() : null;
        if (RESERVED_ADMIN_USERNAME.equalsIgnoreCase(username)) {
            throw new BusinessException(ResultCode.CONFLICT, "该用户名不可注册");
        }
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username))) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名已存在");
        }
        if (email != null && userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, email))) {
            throw new BusinessException(ResultCode.CONFLICT, "邮箱已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名或邮箱已存在");
        }
        return UserProfile.from(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = findByAccount(request.account().trim());
        if (!isActive(user)
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "账号或密码错误");
        }
        StpUtil.login(user.getId());
        return new LoginResponse(StpUtil.getTokenValue(), UserProfile.from(user));
    }

    public UserProfile currentUser() {
        long userId = Long.parseLong(StpUtil.getLoginId().toString());
        User user = userMapper.selectById(userId);
        if (!isActive(user)) {
            StpUtil.logout(userId);
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return UserProfile.from(user);
    }

    public UserProfile currentContentAdministrator() {
        UserProfile profile = currentUser();
        if (!profile.role().isContentAdministrator()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return profile;
    }

    public UserProfile currentSuperAdministrator() {
        UserProfile profile = currentUser();
        if (profile.role() != UserRole.SUPER_ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return profile;
    }

    public void logout() {
        StpUtil.logout();
    }

    private User findByAccount(String account) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or()
                .eq(User::getEmail, account));
    }

    private boolean isActive(User user) {
        return user != null && Integer.valueOf(1).equals(user.getStatus()) && user.getDeletedAt() == null;
    }
}
