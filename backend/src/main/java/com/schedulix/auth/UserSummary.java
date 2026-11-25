package com.schedulix.auth;

public record UserSummary(String displayName, String role, boolean mustChangePassword) {

    public static UserSummary from(UserAccount user) {
        return new UserSummary(user.getDisplayName(), user.getRole().name(), user.isMustChangePassword());
    }

    public static UserSummary from(AuthPrincipal principal) {
        return new UserSummary(principal.displayName(), principal.role().name(), principal.mustChangePassword());
    }
}