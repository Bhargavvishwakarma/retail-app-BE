package com.retail.dto;

import com.retail.model.AppUser;
import com.retail.model.UserRole;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuthResponse(String token, String tokenType, long expiresIn, UserInfo user) {
    public static AuthResponse from(String token, long expiresIn, AppUser user) {
        return new AuthResponse(token, "Bearer", expiresIn,
                UserInfo.from(user));
    }

    public record UserInfo(UUID id, String name, String email, UserRole role, String workspaceTheme, LocalDateTime createdAt) {
        public static UserInfo from(AppUser user) {
            return new UserInfo(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getWorkspaceTheme(), user.getCreatedAt());
        }
    }
}
