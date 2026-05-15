package com.kama.jchatmind.service.impl;

import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.AppUserMapper;
import com.kama.jchatmind.model.entity.AppUser;
import com.kama.jchatmind.model.request.LoginRequest;
import com.kama.jchatmind.model.request.RefreshTokenRequest;
import com.kama.jchatmind.model.request.RegisterRequest;
import com.kama.jchatmind.model.response.LoginResponse;
import com.kama.jchatmind.security.JwtTokenProvider;
import com.kama.jchatmind.security.UserContext;
import com.kama.jchatmind.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void register(RegisterRequest request) {
        // 参数校验
        if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new BizException("用户名和密码不能为空");
        }
        if (request.getUsername().length() < 3 || request.getUsername().length() > 50) {
            throw new BizException("用户名长度需在 3-50 个字符之间");
        }
        if (request.getPassword().length() < 6) {
            throw new BizException("密码长度不能少于 6 个字符");
        }

        // 检查用户名是否已存在
        AppUser existingUser = appUserMapper.selectByUsername(request.getUsername());
        if (existingUser != null) {
            throw new BizException("用户名已存在");
        }

        // 创建用户
        LocalDateTime now = LocalDateTime.now();
        AppUser user = AppUser.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername())
                .role("USER")
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        int result = appUserMapper.insert(user);
        if (result <= 0) {
            throw new BizException("注册失败");
        }

        log.info("用户注册成功: {}", request.getUsername());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new BizException("用户名和密码不能为空");
        }

        // 查找用户
        AppUser user = appUserMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new BizException("用户名或密码错误");
        }

        // 检查账号状态
        if (!user.getEnabled()) {
            throw new BizException("账号已被禁用");
        }

        // 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }

        // 生成 Token
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        log.info("用户登录成功: {}", request.getUsername());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }

    @Override
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        if (!StringUtils.hasText(request.getRefreshToken())) {
            throw new BizException("Refresh Token 不能为空");
        }

        // 验证 refresh token
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new BizException("Refresh Token 已过期或无效");
        }

        // 确认是 refresh 类型
        String tokenType = jwtTokenProvider.getTypeFromToken(request.getRefreshToken());
        if (!"refresh".equals(tokenType)) {
            throw new BizException("Token 类型错误");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
        AppUser user = appUserMapper.selectById(userId);
        if (user == null || !user.getEnabled()) {
            throw new BizException("用户不存在或已被禁用");
        }

        // 生成新的 Token 对
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }

    @Override
    public LoginResponse getCurrentUser() {
        String userId = UserContext.requireUserId();
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }

        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }
}
