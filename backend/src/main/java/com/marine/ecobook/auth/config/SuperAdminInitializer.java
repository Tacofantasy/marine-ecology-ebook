package com.marine.ecobook.auth.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marine.ecobook.auth.mapper.UserMapper;
import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SuperAdminInitializer implements ApplicationRunner {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,64}");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public SuperAdminInitializer(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.initial-super-admin.username}") String username,
            @Value("${app.bootstrap.initial-super-admin.password}") String password) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        User existingSuperAdmin = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.SUPER_ADMIN)
                .eq(User::getStatus, 1)
                .isNull(User::getDeletedAt)
                .last("LIMIT 1"));
        if (existingSuperAdmin != null) {
            replaceDefaultDevelopmentCredentials(existingSuperAdmin);
            return;
        }
        if (!USERNAME_PATTERN.matcher(username).matches() || !StringUtils.hasText(password) || password.length() < 8) {
            throw new IllegalStateException("未配置有效的首次总管理员账号");
        }

        User superAdmin = new User();
        superAdmin.setUsername(username);
        superAdmin.setDisplayName("总管理员");
        superAdmin.setPasswordHash(passwordEncoder.encode(password));
        superAdmin.setRole(UserRole.SUPER_ADMIN);
        superAdmin.setStatus(1);
        userMapper.insert(superAdmin);
    }

    private void replaceDefaultDevelopmentCredentials(User existingSuperAdmin) {
        if (!"admin".equals(existingSuperAdmin.getUsername())
                || !passwordEncoder.matches("password", existingSuperAdmin.getPasswordHash())
                || ("admin".equals(username) && "password".equals(password))) {
            return;
        }
        if (!USERNAME_PATTERN.matcher(username).matches() || !StringUtils.hasText(password) || password.length() < 8) {
            throw new IllegalStateException("未配置有效的首次总管理员账号");
        }
        existingSuperAdmin.setUsername(username);
        existingSuperAdmin.setDisplayName("总管理员");
        existingSuperAdmin.setPasswordHash(passwordEncoder.encode(password));
        userMapper.updateById(existingSuperAdmin);
    }
}
