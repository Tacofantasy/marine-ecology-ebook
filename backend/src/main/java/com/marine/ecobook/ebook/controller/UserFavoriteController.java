package com.marine.ecobook.ebook.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.marine.ecobook.common.api.ApiResponse;
import com.marine.ecobook.ebook.dto.FavoriteEbookItem;
import com.marine.ecobook.ebook.dto.PageData;
import com.marine.ecobook.ebook.service.InteractionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * “我的收藏”分页查询，仅 USER 角色可调用。
 * 只返回当前用户收藏且仍处于已发布状态的电子书，按收藏时间倒序。
 */
@RestController
@Validated
@SaCheckRole("USER")
@RequestMapping("/api/me/favorites")
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class UserFavoriteController {

    private final InteractionService interactionService;

    public UserFavoriteController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @GetMapping
    public ApiResponse<PageData<FavoriteEbookItem>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量必须大于 0")
            @Max(value = 50, message = "每页数量不能超过 50") int pageSize) {
        return ApiResponse.success(interactionService.listFavorites(StpUtil.getLoginIdAsLong(), page, pageSize));
    }
}
