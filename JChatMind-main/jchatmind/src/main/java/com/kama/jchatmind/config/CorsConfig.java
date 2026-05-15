package com.kama.jchatmind.config;

/**
 * CORS 跨域配置 — 已迁移到 SecurityConfig。
 *
 * 引入 Spring Security 后，CORS 必须在 SecurityFilterChain 中通过 http.cors() 配置，
 * 否则预检请求（OPTIONS）会被 Security 拦截返回 403。
 *
 * 相关配置请查看：com.kama.jchatmind.security.SecurityConfig#corsConfigurationSource()
 */
// @Configuration  ← 已禁用，避免与 SecurityConfig 中的 CORS 配置冲突
public class CorsConfig {
}
