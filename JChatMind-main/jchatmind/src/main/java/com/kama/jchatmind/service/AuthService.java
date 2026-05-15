package com.kama.jchatmind.service;

import com.kama.jchatmind.model.request.LoginRequest;
import com.kama.jchatmind.model.request.RefreshTokenRequest;
import com.kama.jchatmind.model.request.RegisterRequest;
import com.kama.jchatmind.model.response.LoginResponse;

public interface AuthService {

    /**
     * 用户注册
     */
    void register(RegisterRequest request);

    /**
     * 用户登录，返回 accessToken + refreshToken
     */
    LoginResponse login(LoginRequest request);

    /**
     * 刷新 Token
     */
    LoginResponse refreshToken(RefreshTokenRequest request);

    /**
     * 获取当前登录用户信息
     */
    LoginResponse getCurrentUser();
}
