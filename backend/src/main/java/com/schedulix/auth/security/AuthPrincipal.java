package com.schedulix.auth.security;

import java.util.UUID;
import com.schedulix.auth.entity.UserRole;

public record AuthPrincipal(
        UUID userId,
        UUID departmentId,
        UserRole role,
        String displayName,
        boolean mustChangePassword
) {
}