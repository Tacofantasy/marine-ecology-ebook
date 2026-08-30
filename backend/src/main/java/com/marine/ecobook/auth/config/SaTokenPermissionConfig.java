package com.marine.ecobook.auth.config;

import cn.dev33.satoken.stp.StpInterface;
import com.marine.ecobook.auth.mapper.UserMapper;
import com.marine.ecobook.auth.model.User;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SaTokenPermissionConfig implements StpInterface {

    private final UserMapper userMapper;

    public SaTokenPermissionConfig(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userMapper.selectById(Long.parseLong(loginId.toString()));
        if (user == null || user.getRole() == null) {
            return List.of();
        }
        return List.of(user.getRole().name());
    }
}
