package com.schedulix.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_WHILE_MUST_CHANGE_PASSWORD = Set.of(
            "/api/me",
            "/api/auth/change-password",
            "/api/auth/refresh",
            "/api/auth/logout"
    );

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository userAccountRepository) {
        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawJwt = header.substring(7).trim();
        Optional<UUID> userIdOpt = jwtService.parseValidUserId(rawJwt);
        if (userIdOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<UserAccount> userOpt = userAccountRepository.findById(userIdOpt.get());
        if (userOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        UserAccount user = userOpt.get();
        if (user.getStatus() == AccountStatus.DISABLED) {
            writeError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "ACCOUNT_DISABLED",
                    "Your account is switched off. Please contact your HOD."
            );
            return;
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (user.isMustChangePassword() && !ALLOWED_WHILE_MUST_CHANGE_PASSWORD.contains(path)) {
            writeError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "MUST_CHANGE_PASSWORD",
                    "Please change your password before using ScheduliX."
            );
            return;
        }

        AuthPrincipal principal = new AuthPrincipal(
                user.getId(),
                user.getDepartmentId(),
                user.getRole(),
                user.getDisplayName(),
                user.isMustChangePassword()
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}