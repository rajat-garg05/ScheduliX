package com.schedulix.auth.service;

import com.schedulix.common.AppException;
import com.schedulix.history.HistoryEntry;
import com.schedulix.history.HistoryEntryRepository;
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
import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.entity.Department;
import com.schedulix.auth.repository.DepartmentRepository;
import com.schedulix.auth.entity.EmailToken;
import com.schedulix.auth.repository.EmailTokenRepository;
import com.schedulix.auth.dto.RegisterRequest;
import com.schedulix.auth.security.TokenHelper;
import com.schedulix.auth.entity.TokenPurpose;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;
import com.schedulix.auth.entity.UserRole;

@Service
public class RegistrationService {

    private static final int MAX_REGISTRATIONS_PER_IP_PER_HOUR = 5;
    private static final int MAX_RESENDS_PER_EMAIL_PER_HOUR = 3;
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final DepartmentRepository departmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final EmailTokenRepository emailTokenRepository;
    private final HistoryEntryRepository historyEntryRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final RateLimiter rateLimiter;
    private final String publicUrl;

    public RegistrationService(
            DepartmentRepository departmentRepository,
            UserAccountRepository userAccountRepository,
            EmailTokenRepository emailTokenRepository,
            HistoryEntryRepository historyEntryRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            RateLimiter rateLimiter,
            @Value("${app.public-url}") String publicUrl
    ) {
        this.departmentRepository = departmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.emailTokenRepository = emailTokenRepository;
        this.historyEntryRepository = historyEntryRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @Transactional
    public void register(RegisterRequest request, String clientIp) {
        if (!rateLimiter.tryAcquire("register:" + clientIp, MAX_REGISTRATIONS_PER_IP_PER_HOUR, ONE_HOUR)) {
            throw new AppException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "TOO_MANY_REQUESTS",
                    "Too many registration attempts from this network. Please try again in an hour."
            );
        }

        String cleanDepartment = request.departmentName().trim();
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "EMAIL_ALREADY_USED",
                    "This email address is already registered. Please log in or use a different email."
            );
        }

        Department department = departmentRepository.save(new Department(cleanDepartment));
        String displayName = "HOD of " + cleanDepartment;
        String passwordHash = passwordEncoder.encode(request.password());

        UserAccount hod = userAccountRepository.save(new UserAccount(
                department.getId(),
                normalizedEmail,
                passwordHash,
                displayName,
                UserRole.HOD,
                AccountStatus.PENDING,
                false,
                null
        ));

        String rawToken = issueEmailToken(hod, TokenPurpose.VERIFY, 24, ChronoUnit.HOURS);

        historyEntryRepository.save(new HistoryEntry(
                department.getId(),
                hod.getId(),
                displayName,
                "ACCOUNT",
                displayName + " registered the department."
        ));

        sendVerificationEmail(normalizedEmail, cleanDepartment, rawToken);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String tokenHash = TokenHelper.sha256Hex(rawToken.trim());
        EmailToken emailToken = emailTokenRepository
                .findByTokenHashAndPurpose(tokenHash, TokenPurpose.VERIFY)
                .orElseThrow(() -> new AppException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_LINK",
                        "This confirmation link is not valid. Please request a new link."
                ));

        Instant now = Instant.now();
        if (emailToken.getUsedAt() != null || emailToken.getExpiresAt().isBefore(now)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "LINK_EXPIRED",
                    "This confirmation link has already been used or has expired. Please request a new link."
            );
        }

        UserAccount user = userAccountRepository.findById(emailToken.getUserAccountId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_LINK",
                        "This confirmation link is not valid."
                ));

        emailToken.markUsed(now);
        if (user.getStatus() == AccountStatus.PENDING) {
            user.setStatus(AccountStatus.ACTIVE);
        }
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
        }

        historyEntryRepository.save(new HistoryEntry(
                user.getDepartmentId(),
                user.getId(),
                user.getDisplayName(),
                "ACCOUNT",
                user.getDisplayName() + " confirmed the email address."
        ));
    }

    @Transactional
    public void resendVerification(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!rateLimiter.tryAcquire("resend-verify:" + normalizedEmail, MAX_RESENDS_PER_EMAIL_PER_HOUR, ONE_HOUR)) {
            return;
        }

        userAccountRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            if (user.getStatus() != AccountStatus.PENDING) {
                return;
            }
            String departmentName = departmentRepository.findById(user.getDepartmentId())
                    .map(Department::getName)
                    .orElse("your department");
            String rawToken = issueEmailToken(user, TokenPurpose.VERIFY, 24, ChronoUnit.HOURS);
            sendVerificationEmail(normalizedEmail, departmentName, rawToken);
        });
    }

    private String issueEmailToken(UserAccount user, TokenPurpose purpose, long amount, ChronoUnit unit) {
        Instant now = Instant.now();
        List<EmailToken> activeTokens = emailTokenRepository
                .findByUserAccountIdAndPurposeAndUsedAtIsNull(user.getId(), purpose);
        for (EmailToken oldToken : activeTokens) {
            oldToken.markUsed(now);
        }

        String rawToken = TokenHelper.generateRawToken();
        String hash = TokenHelper.sha256Hex(rawToken);
        emailTokenRepository.save(new EmailToken(user.getId(), hash, purpose, now.plus(amount, unit)));
        return rawToken;
    }

    private void sendVerificationEmail(String to, String departmentName, String rawToken) {
        String link = publicUrl + "/verify-email?token=" + rawToken;
        String subject = "Confirm your email for ScheduliX";
        String body = "Hello,\n\n"
                + "Please confirm your email address for " + departmentName + " by opening the link below:\n"
                + link + "\n\n"
                + "This link works for 24 hours.\n"
                + "If you did not ask for this, ignore this email.\n";
        emailSender.send(to, subject, body);
    }
}