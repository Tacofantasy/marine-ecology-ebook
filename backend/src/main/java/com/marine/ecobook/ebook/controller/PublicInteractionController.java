package com.marine.ecobook.ebook.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.marine.ecobook.common.api.ApiResponse;
import com.marine.ecobook.ebook.dto.InteractionState;
import com.marine.ecobook.ebook.service.InteractionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 已发布电子书的点赞与收藏互动接口，仅 USER 角色可调用。
 * 所有写操作幂等：重复创建、重复删除均返回成功及真实当前状态。
 */
@RestController
@SaCheckRole("USER")
@ConditionalOnProperty(name = "app.ebook.enabled", havingValue = "true", matchIfMissing = true)
public class PublicInteractionController {

    private final InteractionService interactionService;

    public PublicInteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @GetMapping("/api/ebooks/{ebookId}/interaction")
    public ApiResponse<InteractionState> getState(@PathVariable long ebookId) {
        return ApiResponse.success(interactionService.getState(ebookId, StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/api/ebooks/{ebookId}/like")
    public ApiResponse<InteractionState> like(@PathVariable long ebookId) {
        return ApiResponse.success(interactionService.like(ebookId, StpUtil.getLoginIdAsLong()));
    }

    @DeleteMapping("/api/ebooks/{ebookId}/like")
    public ApiResponse<InteractionState> unlike(@PathVariable long ebookId) {
        return ApiResponse.success(interactionService.unlike(ebookId, StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/api/ebooks/{ebookId}/favorite")
    public ApiResponse<InteractionState> favorite(@PathVariable long ebookId) {
        return ApiResponse.success(interactionService.favorite(ebookId, StpUtil.getLoginIdAsLong()));
    }

    @DeleteMapping("/api/ebooks/{ebookId}/favorite")
    public ApiResponse<InteractionState> unfavorite(@PathVariable long ebookId) {
        return ApiResponse.success(interactionService.unfavorite(ebookId, StpUtil.getLoginIdAsLong()));
    }
}
