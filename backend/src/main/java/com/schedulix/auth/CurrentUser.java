package com.schedulix.auth;

import com.schedulix.common.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUser {

    public AuthPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "NOT_LOGGED_IN", "Please log in to continue.");
        }
        return principal;
    }

    public UUID userId() {
        return principal().userId();
    }

    public UUID departmentId() {
        return principal().departmentId();
    }

    public UserRole role() {
        return principal().role();
    }

    public String displayName() {
        return principal().displayName();
    }

    public boolean mustChangePassword() {
        return principal().mustChangePassword();
    }
}