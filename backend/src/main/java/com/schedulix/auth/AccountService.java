package com.schedulix.auth;

import com.schedulix.common.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountService {

    public static final String DEFAULT_TEACHER_PASSWORD = "ScheduliX@123";

    private final UserAccountRepository userAccountRepository;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;

    public AccountService(
            UserAccountRepository userAccountRepository,
            SessionService sessionService,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount createTeacherAccount(UUID departmentId, String email, String displayName) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_USED",
                    "This email address is already used by another account."
            );
        }

        UserAccount teacher = new UserAccount(
                departmentId,
                normalizedEmail,
                passwordEncoder.encode(DEFAULT_TEACHER_PASSWORD),
                displayName.trim(),
                UserRole.TEACHER,
                AccountStatus.ACTIVE,
                true,
                Instant.now()
        );
        return userAccountRepository.save(teacher);
    }

    @Transactional
    public void resetToDefaultPassword(UUID userId) {
        UserAccount user = loadAccount(userId);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_TEACHER_PASSWORD));
        user.setMustChangePassword(true);
        sessionService.revokeAllSessions(userId);
    }

    @Transactional
    public void disableAccount(UUID userId) {
        UserAccount user = loadAccount(userId);
        user.setStatus(AccountStatus.DISABLED);
        sessionService.revokeAllSessions(userId);
    }

    @Transactional
    public void enableAccount(UUID userId) {
        UserAccount user = loadAccount(userId);
        user.setStatus(AccountStatus.ACTIVE);
    }

    @Transactional
    public void changeEmail(UUID userId, String newEmail) {
        UserAccount user = loadAccount(userId);
        String normalizedEmail = newEmail.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.equals(user.getEmail())) {
            return;
        }
        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_USED",
                    "This email address is already used by another account."
            );
        }
        user.setEmail(normalizedEmail);
    }

    @Transactional
    public void changeDisplayName(UUID userId, String name) {
        UserAccount user = loadAccount(userId);
        user.setDisplayName(name.trim());
    }

    private UserAccount loadAccount(UUID userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "ACCOUNT_NOT_FOUND",
                        "Account not found."
                ));
    }
}