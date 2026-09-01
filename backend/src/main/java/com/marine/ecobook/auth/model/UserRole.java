package com.marine.ecobook.auth.model;

public enum UserRole {
    SUPER_ADMIN,
    ADMIN,
    USER;

    public boolean isContentAdministrator() {
        return this == ADMIN || this == SUPER_ADMIN;
    }
}
