package com.schedulix.auth;

import java.util.UUID;

public record AuthPrincipal(
        UUID userId,
        UUID departmentId,
        UserRole role,
        String displayName,
        boolean mustChangePassword
) {
}