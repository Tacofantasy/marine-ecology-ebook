package com.marine.ecobook.auth.dto;

public record LoginResponse(String token, UserProfile user) {
}
