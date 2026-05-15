package com.kama.jchatmind.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：从请求 Header 或 Query Param 中提取 Token，
 * 验证后将用户信息注入 SecurityContext 和 UserContext。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);

            if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
                // 只接受 access token，拒绝 refresh token 用于普通请求
                String tokenType = tokenProvider.getTypeFromToken(token);
                if (!"access".equals(tokenType)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String userId = tokenProvider.getUserIdFromToken(token);
                String role = tokenProvider.getRoleFromToken(token);

                // 构建 Spring Security 认证对象
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 设置 UserContext，供 Service 层使用
                UserContext.setUserId(userId);
            }

            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后清理 ThreadLocal，防止内存泄漏
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 从请求中提取 Token：
     * 1. 优先从 Authorization Header（Bearer xxx）中提取
     * 2. 如果没有，从 Query Param（token=xxx）中提取（用于 SSE 连接）
     */
    private String resolveToken(HttpServletRequest request) {
        // 从 Header 中提取
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // 从 Query Param 中提取（SSE 场景，EventSource 不支持自定义 Header）
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }

        return null;
    }
}
