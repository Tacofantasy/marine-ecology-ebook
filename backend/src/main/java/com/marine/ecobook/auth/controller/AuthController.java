package com.marine.ecobook.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.marine.ecobook.auth.dto.LoginRequest;
import com.marine.ecobook.auth.dto.LoginResponse;
import com.marine.ecobook.auth.dto.RegisterRequest;
import com.marine.ecobook.auth.dto.UserProfile;
import com.marine.ecobook.auth.service.AuthService;
import com.marine.ecobook.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<UserProfile> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @SaCheckLogin
    @GetMapping("/me")
    public ApiResponse<UserProfile> currentUser() {
        return ApiResponse.success(authService.currentUser());
    }
}
