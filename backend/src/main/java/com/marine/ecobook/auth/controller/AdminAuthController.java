package com.marine.ecobook.auth.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.marine.ecobook.auth.dto.UserProfile;
import com.marine.ecobook.auth.service.AuthService;
import com.marine.ecobook.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @SaCheckRole("ADMIN")
    @GetMapping("/auth-check")
    public ApiResponse<UserProfile> authCheck() {
        return ApiResponse.success(authService.currentUser());
    }
}
