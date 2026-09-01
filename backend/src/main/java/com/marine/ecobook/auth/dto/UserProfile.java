package com.marine.ecobook.auth.dto;

import com.marine.ecobook.auth.model.User;
import com.marine.ecobook.auth.model.UserRole;

public record UserProfile(Long id, String username, String displayName, String email, UserRole role, Integer status) {

    public static UserProfile from(User user) {
        return new UserProfile(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getRole(), user.getStatus());
    }
}
