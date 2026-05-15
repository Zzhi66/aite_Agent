package com.kama.jchatmind.model.entity;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * @TableName app_user
 */
@Data
@Builder
public class AppUser {
    private String id;

    private String username;

    private String password;

    private String nickname;

    private String avatarUrl;

    private String role;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
