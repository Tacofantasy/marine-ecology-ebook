package com.marine.ecobook.auth.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.marine.ecobook.auth.dto.AdminUserItem;
import com.marine.ecobook.auth.service.AdminUserService;
import com.marine.ecobook.common.api.ApiResponse;
import com.marine.ecobook.ebook.dto.PageData;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户运营接口：列表、搜索、注销。
 * 子管理员可管理注册用户；总管理员可管理所有账号（业务规则见 AdminUserService）。
 */
@RestController
@Validated
@RequestMapping("/api/admin/users")
@SaCheckRole(value = {"ADMIN", "SUPER_ADMIN"}, mode = SaMode.OR)
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageData<AdminUserItem>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量必须大于 0")
            @Max(value = 50, message = "每页数量不能超过 50") int pageSize) {
        return ApiResponse.success(adminUserService.list(keyword, page, pageSize));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deactivate(@PathVariable long userId) {
        adminUserService.deactivate(StpUtil.getLoginIdAsLong(), userId);
        return ApiResponse.success(null);
    }
}
