package com.marine.ecobook.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marine.ecobook.auth.dto.AdminCreateRequest;
import com.marine.ecobook.auth.dto.AdminUserItem;
import com.marine.ecobook.auth.mapper.UserMapper;
import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;
import com.marine.ecobook.common.api.ResultCode;
import com.marine.ecobook.common.exception.BusinessException;
import com.marine.ecobook.ebook.dto.PageData;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageData<AdminUserItem> list(long operatorId, String keyword, Integer status, UserRole requestedRole, int page, int pageSize) {
        User operator = requireActiveOperator(operatorId);
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.isEmpty()) {
            normalizedKeyword = null;
        }
        UserRole role = operator.getRole() == UserRole.SUPER_ADMIN ? requestedRole : UserRole.USER;
        long total = userMapper.countAdminPage(normalizedKeyword, status, role);
        if (total == 0) {
            return new PageData<>(0, List.of());
        }
        long offset = (long) (page - 1) * pageSize;
        if (offset >= total) {
            return new PageData<>(total, List.of());
        }
        List<AdminUserItem> items = userMapper.selectAdminPage(normalizedKeyword, status, role, pageSize, offset)
                .stream()
                .map(this::toItem)
                .toList();
        return new PageData<>(total, items);
    }

    @Transactional
    public AdminUserItem createSubAdministrator(long operatorId, AdminCreateRequest request) {
        requireSuperAdministrator(operatorId);
        String username = request.username().trim();
        String email = request.email() == null || request.email().isBlank() ? null : request.email().trim();
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username))
                || email != null && userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, email))) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名或邮箱已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.ADMIN);
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.CONFLICT, "用户名或邮箱已存在");
        }
        return toItem(user);
    }

    @Transactional
    public void updateStatus(long operatorId, long targetUserId, int status) {
        User target = requireManageableTarget(requireActiveOperator(operatorId), targetUserId);
        target.setStatus(status);
        userMapper.updateById(target);
        if (status == 0) {
            StpUtil.logout(targetUserId);
        }
    }

    @Transactional
    public void resetPassword(long operatorId, long targetUserId, String password) {
        User operator = requireSuperAdministrator(operatorId);
        User target = requireManageableTarget(operator, targetUserId);
        target.setPasswordHash(passwordEncoder.encode(password));
        userMapper.updateById(target);
        StpUtil.logout(targetUserId);
    }

    @Transactional
    public void deactivate(long operatorId, long targetUserId) {
        User target = requireManageableTarget(requireActiveOperator(operatorId), targetUserId, true);
        if (target.getDeletedAt() != null) return;
        target.setStatus(0);
        target.setDeletedAt(LocalDateTime.now());
        userMapper.updateById(target);
        StpUtil.logout(targetUserId);
    }

    private User requireActiveOperator(long operatorId) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null || operator.getDeletedAt() != null || !Integer.valueOf(1).equals(operator.getStatus())) {
            StpUtil.logout(operatorId);
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (operator.getRole() == null || !operator.getRole().isContentAdministrator()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return operator;
    }

    private User requireSuperAdministrator(long operatorId) {
        User operator = requireActiveOperator(operatorId);
        if (operator.getRole() != UserRole.SUPER_ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅总管理员可执行此操作");
        }
        return operator;
    }

    private User requireManageableTarget(User operator, long targetUserId) {
        return requireManageableTarget(operator, targetUserId, false);
    }

    private User requireManageableTarget(User operator, long targetUserId, boolean allowDeleted) {
        if (operator.getId() == targetUserId) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能管理当前登录账号");
        }
        User target = userMapper.selectById(targetUserId);
        if (target == null || !allowDeleted && target.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "账号不存在或已注销");
        }
        if (target.getRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能管理总管理员账号");
        }
        if (operator.getRole() != UserRole.SUPER_ADMIN && target.getRole() != UserRole.USER) {
            throw new BusinessException(ResultCode.FORBIDDEN, "子管理员仅可管理注册用户账号");
        }
        return target;
    }

    private AdminUserItem toItem(User user) {
        return new AdminUserItem(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getStatus(),
                user.getCreatedAt() == null ? null : DATE_FORMAT.format(user.getCreatedAt()));
    }
}
