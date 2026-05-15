package com.kama.jchatmind.controller;

import com.kama.jchatmind.model.common.ApiResponse;
import com.kama.jchatmind.model.request.LoginRequest;
import com.kama.jchatmind.model.request.RefreshTokenRequest;
import com.kama.jchatmind.model.request.RegisterRequest;
import com.kama.jchatmind.model.response.LoginResponse;
import com.kama.jchatmind.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success();
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 刷新 Token
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refreshToken(request));
    }

    /**
     * 获取当前登录用户信息（需认证）
     */
    @GetMapping("/me")
    public ApiResponse<LoginResponse> getCurrentUser() {
        return ApiResponse.success(authService.getCurrentUser());
    }
}
