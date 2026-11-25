package com.schedulix.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String REFRESH_COOKIE_NAME = "schedulix_refresh";

    private final RegistrationService registrationService;
    private final SessionService sessionService;
    private final boolean cookieSecure;
    private final long refreshDays;

    public AuthController(
            RegistrationService registrationService,
            SessionService sessionService,
            @Value("${app.cookie.secure:false}") boolean cookieSecure,
            @Value("${app.jwt.refresh-days:7}") long refreshDays
    ) {
        this.registrationService = registrationService;
        this.sessionService = sessionService;
        this.cookieSecure = cookieSecure;
        this.refreshDays = refreshDays;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        registrationService.register(request, extractClientIp(httpRequest));
        return new MessageResponse("Department registered. Please check your email to confirm your account.");
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        registrationService.verifyEmail(request.token());
        return new MessageResponse("Your email is confirmed. You can now log in.");
    }

    @PostMapping("/resend-verification")
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest request) {
        registrationService.resendVerification(request.email());
        return new MessageResponse("If this email is waiting for confirmation, we sent a new link.");
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        SessionService.SessionResult result = sessionService.login(request, extractClientIp(httpRequest));
        addRefreshCookie(httpResponse, result.rawRefreshToken(), Duration.ofDays(refreshDays));
        return result.body();
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse httpResponse
    ) {
        SessionService.SessionResult result = sessionService.refresh(refreshToken);
        addRefreshCookie(httpResponse, result.rawRefreshToken(), Duration.ofDays(refreshDays));
        return result.body();
    }

    @PostMapping("/logout")
    public MessageResponse logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse httpResponse
    ) {
        sessionService.logout(refreshToken);
        addRefreshCookie(httpResponse, "", Duration.ZERO);
        return new MessageResponse("You have been logged out.");
    }

    private void addRefreshCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}