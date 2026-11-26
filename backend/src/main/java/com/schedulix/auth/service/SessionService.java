package com.schedulix.auth.service;

import com.schedulix.common.AppException;
import com.schedulix.history.HistoryEntry;
import com.schedulix.history.HistoryEntryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.security.JwtService;
import com.schedulix.auth.security.LoginAttemptTracker;
import com.schedulix.auth.dto.LoginRequest;
import com.schedulix.auth.dto.LoginResponse;
import com.schedulix.auth.entity.RefreshToken;
import com.schedulix.auth.repository.RefreshTokenRepository;
import com.schedulix.auth.security.TokenHelper;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;
import com.schedulix.auth.dto.UserSummary;

@Service
public class SessionService {

    public record SessionResult(LoginResponse body, String rawRefreshToken) {
    }

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final HistoryEntryRepository historyEntryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptTracker attemptTracker;
    private final long refreshDays;

    public SessionService(
            UserAccountRepository userAccountRepository,
            RefreshTokenRepository refreshTokenRepository,
            HistoryEntryRepository historyEntryRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptTracker attemptTracker,
            @Value("${app.jwt.refresh-days:7}") long refreshDays
    ) {
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.historyEntryRepository = historyEntryRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.attemptTracker = attemptTracker;
        this.refreshDays = refreshDays;
    }

    @Transactional
    public SessionResult login(LoginRequest request, String clientIp) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (attemptTracker.isLocked(normalizedEmail, clientIp)) {
            throw new AppException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "TOO_MANY_LOGIN_ATTEMPTS",
                    "Too many wrong login attempts. Please wait 15 minutes and try again."
            );
        }

        UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        boolean passwordValid = user != null && passwordEncoder.matches(request.password(), user.getPasswordHash());

        if (!passwordValid) {
            boolean justLocked = attemptTracker.recordFailure(normalizedEmail, clientIp);
            if (justLocked && user != null) {
                historyEntryRepository.save(new HistoryEntry(
                        user.getDepartmentId(),
                        user.getId(),
                        user.getDisplayName(),
                        "ACCOUNT",
                        "Login was temporarily blocked after too many wrong password attempts for " + user.getDisplayName() + "."
                ));
            }
            if (justLocked) {
                throw new AppException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "TOO_MANY_LOGIN_ATTEMPTS",
                        "Too many wrong login attempts. Please wait 15 minutes and try again."
                );
            }
            throw new AppException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "Email or password is wrong."
            );
        }

        if (user.getStatus() == AccountStatus.PENDING) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "EMAIL_NOT_VERIFIED",
                    "Please confirm your email first."
            );
        }
        if (user.getStatus() == AccountStatus.DISABLED) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_DISABLED",
                    "Your account is switched off. Please contact your HOD."
            );
        }

        attemptTracker.clear(normalizedEmail, clientIp);
        user.setLastLoginAt(Instant.now());

        return issueSession(user);
    }

    @Transactional
    public SessionResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Please log in again.");
        }

        String hash = TokenHelper.sha256Hex(rawRefreshToken.trim());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Please log in again."));

        Instant now = Instant.now();
        if (existing.getRevokedAt() != null || existing.getExpiresAt().isBefore(now)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Please log in again.");
        }

        UserAccount user = userAccountRepository.findById(existing.getUserAccountId())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Please log in again."));

        if (user.getStatus() == AccountStatus.DISABLED) {
            existing.revoke(now);
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_DISABLED",
                    "Your account is switched off. Please contact your HOD."
            );
        }
        if (user.getStatus() != AccountStatus.ACTIVE) {
            existing.revoke(now);
            throw new AppException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Please log in again.");
        }

        // Rotate refresh token on every use
        existing.revoke(now);
        return issueSession(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = TokenHelper.sha256Hex(rawRefreshToken.trim());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.revoke(Instant.now());
            }
        });
    }

    @Transactional
    public void revokeAllSessions(UUID userAccountId) {
        Instant now = Instant.now();
        List<RefreshToken> active = refreshTokenRepository.findByUserAccountIdAndRevokedAtIsNull(userAccountId);
        for (RefreshToken token : active) {
            token.revoke(now);
        }
    }

    private SessionResult issueSession(UserAccount user) {
        String accessToken = jwtService.createAccessToken(user);
        String rawRefreshToken = TokenHelper.generateRawToken();
        String refreshHash = TokenHelper.sha256Hex(rawRefreshToken);

        Instant expiresAt = Instant.now().plus(refreshDays, ChronoUnit.DAYS);
        refreshTokenRepository.save(new RefreshToken(user.getId(), refreshHash, expiresAt));

        return new SessionResult(new LoginResponse(accessToken, UserSummary.from(user)), rawRefreshToken);
    }
}