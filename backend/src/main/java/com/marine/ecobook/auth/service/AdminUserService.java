package com.marine.ecobook.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.marine.ecobook.auth.dto.AdminUserItem;
import com.marine.ecobook.auth.mapper.UserMapper;
import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;
import com.marine.ecobook.common.api.ResultCode;
import com.marine.ecobook.common.exception.BusinessException;
import com.marine.ecobook.ebook.dto.PageData;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端用户运营服务。
 * <ul>
 *   <li>列表：分页 + 用户名/昵称关键词模糊搜索，只展示未注销（未物理删除）账号；</li>
 *   <li>注销：status 置 0 并写 deleted_at，随后强制下线该账号（幂等：重复注销直接成功）；</li>
 *   <li>权限：子管理员仅可管理 USER；总管理员（SUPER_ADMIN）可管理 USER 与子管理员，任何人都不能注销自己或总管理员。</li>
 * </ul>
 */
@Service
public class AdminUserService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMapper userMapper;

    public AdminUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public PageData<AdminUserItem> list(String keyword, int page, int pageSize) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.isEmpty()) {
            normalizedKeyword = null;
        }
        long total = userMapper.countAdminPage(normalizedKeyword);
        if (total == 0) {
            return new PageData<>(0, List.of());
        }
        long offset = (long) (page - 1) * pageSize;
        if (offset >= total) {
            return new PageData<>(total, List.of());
        }
        List<AdminUserItem> items = userMapper.selectAdminPage(normalizedKeyword, pageSize, offset)
                .stream()
                .map(this::toItem)
                .toList();
        return new PageData<>(total, items);
    }

    /**
     * 注销（逻辑删除）用户：置 status=0、写 deleted_at，并踢下线该用户所有会话。
     * 幂等：对已注销用户重复调用直接成功。
     */
    @Transactional
    public void deactivate(long operatorId, long targetUserId) {
        if (operatorId == targetUserId) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能注销当前登录账号");
        }
        User target = userMapper.selectById(targetUserId);
        if (target == null || target.getDeletedAt() != null) {
            // 不存在或已注销：统一按幂等成功处理（也可能为避免枚举探测）
            return;
        }
        User operator = userMapper.selectById(operatorId);
        if (operator == null || operator.getRole() != UserRole.SUPER_ADMIN
                && target.getRole() != UserRole.USER) {
            throw new BusinessException(ResultCode.FORBIDDEN, "子管理员仅可注销注册用户账号");
        }
        target.setStatus(0);
        target.setDeletedAt(java.time.LocalDateTime.now());
        userMapper.updateById(target);
        // 踢下线：注销后既有会话立即失效（登录校验也会拦截 status!=1）
        StpUtil.logout(targetUserId);
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
