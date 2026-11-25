package com.schedulix.auth;

public record LoginResponse(String accessToken, UserSummary user) {
}