package com.schedulix.auth.service;

import com.schedulix.common.AppException;
import com.schedulix.history.HistoryEntry;
import com.schedulix.history.HistoryEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.dto.ChangePasswordRequest;
import com.schedulix.auth.security.CurrentUser;
import com.schedulix.auth.entity.EmailToken;
import com.schedulix.auth.repository.EmailTokenRepository;
import com.schedulix.auth.dto.ResetPasswordRequest;
import com.schedulix.auth.security.TokenHelper;
import com.schedulix.auth.entity.TokenPurpose;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;

@Service
public class PasswordService {

    private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

    private static final int MAX_FORGOT_PER_EMAIL_PER_HOUR = 3;
    private static final int MAX_FORGOT_PER_IP_PER_HOUR = 20;
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final UserAccountRepository userAccountRepository;
    private final EmailTokenRepository emailTokenRepository;
    private final HistoryEntryRepository historyEntryRepository;
    private final SessionService sessionService;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final RateLimiter rateLimiter;
    private final String publicUrl;

    public PasswordService(
            UserAccountRepository userAccountRepository,
            EmailTokenRepository emailTokenRepository,
            HistoryEntryRepository historyEntryRepository,
            SessionService sessionService,
            CurrentUser currentUser,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            RateLimiter rateLimiter,
            @Value("${app.public-url}") String publicUrl
    ) {
        this.userAccountRepository = userAccountRepository;
        this.emailTokenRepository = emailTokenRepository;
        this.historyEntryRepository = historyEntryRepository;
        this.sessionService = sessionService;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @Transactional
    public void forgotPassword(String email, String clientIp) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (!rateLimiter.tryAcquire("forgot-ip:" + clientIp, MAX_FORGOT_PER_IP_PER_HOUR, ONE_HOUR)) {
            return;
        }
        if (!rateLimiter.tryAcquire("forgot-email:" + normalizedEmail, MAX_FORGOT_PER_EMAIL_PER_HOUR, ONE_HOUR)) {
            return;
        }

        UserAccount user = userAccountRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || user.getStatus() != AccountStatus.ACTIVE) {
            return;
        }

        Instant now = Instant.now();
        List<EmailToken> previous = emailTokenRepository
                .findByUserAccountIdAndPurposeAndUsedAtIsNull(user.getId(), TokenPurpose.RESET);
        for (EmailToken oldToken : previous) {
            oldToken.markUsed(now);
        }

        String rawToken = TokenHelper.generateRawToken();
        String hash = TokenHelper.sha256Hex(rawToken);
        emailTokenRepository.save(new EmailToken(
                user.getId(),
                hash,
                TokenPurpose.RESET,
                now.plus(30, ChronoUnit.MINUTES)
        ));

        String link = publicUrl + "/reset-password?token=" + rawToken;
        String subject = "Reset your ScheduliX password";
        String body = "Hello " + user.getDisplayName() + ",\n\n"
                + "We received a request to reset your ScheduliX password. Open the link below to choose a new password:\n"
                + link + "\n\n"
                + "This link works once and expires in 30 minutes.\n"
                + "If you did not ask for this, ignore this email.\n";

        CompletableFuture.runAsync(() -> {
            try {
                emailSender.send(normalizedEmail, subject, body);
            } catch (Exception ex) {
                log.error("Failed to send password reset email", ex);
            }
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hash = TokenHelper.sha256Hex(request.token().trim());
        EmailToken emailToken = emailTokenRepository
                .findByTokenHashAndPurpose(hash, TokenPurpose.RESET)
                .orElseThrow(() -> new AppException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_LINK",
                        "This password reset link is not valid. Please request a new link."
                ));

        Instant now = Instant.now();
        if (emailToken.getUsedAt() != null || emailToken.getExpiresAt().isBefore(now)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "LINK_EXPIRED",
                    "This password reset link has expired or has already been used. Please request a new link."
            );
        }

        UserAccount user = userAccountRepository.findById(emailToken.getUserAccountId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_LINK",
                        "This password reset link is not valid."
                ));

        if (user.getStatus() == AccountStatus.DISABLED) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_DISABLED",
                    "Your account is switched off. Please contact your HOD."
            );
        }

        emailToken.markUsed(now);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);

        sessionService.revokeAllSessions(user.getId());

        historyEntryRepository.save(new HistoryEntry(
                user.getDepartmentId(),
                user.getId(),
                user.getDisplayName(),
                "ACCOUNT",
                user.getDisplayName() + " reset the password."
        ));
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        UserAccount user = userAccountRepository.findById(currentUser.userId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.UNAUTHORIZED,
                        "NOT_LOGGED_IN",
                        "Please log in to continue."
                ));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "WRONG_CURRENT_PASSWORD",
                    "Your current password is wrong."
            );
        }

        if (request.currentPassword().equals(request.newPassword())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SAME_PASSWORD",
                    "Please choose a new password that is different from your current password."
            );
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);

        historyEntryRepository.save(new HistoryEntry(
                user.getDepartmentId(),
                user.getId(),
                user.getDisplayName(),
                "ACCOUNT",
                user.getDisplayName() + " changed the password."
        ));
    }
}