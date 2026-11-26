package com.schedulix.auth.dto;

public record LoginResponse(String accessToken, UserSummary user) {
}