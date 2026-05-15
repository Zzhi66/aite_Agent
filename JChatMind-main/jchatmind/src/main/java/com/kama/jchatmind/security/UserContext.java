package com.kama.jchatmind.security;

/**
 * 基于 ThreadLocal 的用户上下文，在 JwtAuthFilter 中设置，在 Service 层获取当前用户 ID。
 * 使用 ThreadLocal 避免侵入现有 Service 方法签名。
 */
public class UserContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    /**
     * 获取当前用户 ID，如果未登录则抛出异常
     */
    public static String requireUserId() {
        String id = USER_ID.get();
        if (id == null) {
            throw new IllegalStateException("用户未登录");
        }
        return id;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
