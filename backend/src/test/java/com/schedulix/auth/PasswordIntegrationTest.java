package com.schedulix.auth;

import com.schedulix.common.ApiError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PasswordIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    EmailTokenRepository emailTokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    RateLimiter rateLimiter;

    @Autowired
    LoginAttemptTracker attemptTracker;

    @MockitoBean
    EmailSender emailSender;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        rateLimiter.clearAll();
        attemptTracker.clearAll();
        reset(emailSender);
    }

    @Test
    void resetLinkWorksOnceAndEndsAllSessions() {
        Department dept = departmentRepository.save(new Department("Biotech"));
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                "bio.teacher@college.edu",
                passwordEncoder.encode("OldPassword123!"),
                "Prof. Verma",
                UserRole.TEACHER,
                AccountStatus.ACTIVE,
                false,
                Instant.now()
        ));

        // Log in first to create an active refresh session
        ResponseEntity<LoginResponse> loginRes = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("bio.teacher@college.edu", "OldPassword123!"))
                .retrieve()
                .toEntity(LoginResponse.class);
        String cookiePair = extractRefreshCookiePair(loginRes.getHeaders());

        // Request reset link
        MessageResponse forgotRes = client.post()
                .uri("/api/auth/forgot-password")
                .body(new EmailRequest("bio.teacher@college.edu"))
                .retrieve()
                .body(MessageResponse.class);
        assertThat(forgotRes).isNotNull();
        assertThat(forgotRes.message()).isEqualTo("If this email exists, we sent a link.");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender, timeout(3000)).send(eq("bio.teacher@college.edu"), anyString(), bodyCaptor.capture());
        String resetToken = extractToken(bodyCaptor.getValue());

        // Reset password
        ResponseEntity<MessageResponse> resetRes = client.post()
                .uri("/api/auth/reset-password")
                .body(new ResetPasswordRequest(resetToken, "BrandNewPass123!"))
                .retrieve()
                .toEntity(MessageResponse.class);
        assertThat(resetRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Previous refresh cookie is revoked
        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/refresh")
                .header(HttpHeaders.COOKIE, cookiePair)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);

        // Reset link cannot be reused
        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/reset-password")
                .body(new ResetPasswordRequest(resetToken, "AnotherPass123!"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);

        // Login works with new password
        LoginResponse newLogin = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("bio.teacher@college.edu", "BrandNewPass123!"))
                .retrieve()
                .body(LoginResponse.class);
        assertThat(newLogin).isNotNull();
        assertThat(newLogin.accessToken()).isNotBlank();
    }

    @Test
    void expiredResetLinkIsRejected() {
        Department dept = departmentRepository.save(new Department("Chemistry"));
        UserAccount user = userAccountRepository.save(new UserAccount(
                dept.getId(),
                "chem.hod@college.edu",
                passwordEncoder.encode("OldPassword123!"),
                "HOD of Chemistry",
                UserRole.HOD,
                AccountStatus.ACTIVE,
                false,
                Instant.now()
        ));

        String rawToken = TokenHelper.generateRawToken();
        emailTokenRepository.save(new EmailToken(
                user.getId(),
                TokenHelper.sha256Hex(rawToken),
                TokenPurpose.RESET,
                Instant.now().minus(1, ChronoUnit.MINUTES)
        ));

        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/reset-password")
                .body(new ResetPasswordRequest(rawToken, "BrandNewPass123!"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("LINK_EXPIRED");
                });
    }

    @Test
    void disabledAccountGetsNoResetEmail() {
        Department dept = departmentRepository.save(new Department("Geology"));
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                "off.teacher@college.edu",
                passwordEncoder.encode("OldPassword123!"),
                "Prof. Das",
                UserRole.TEACHER,
                AccountStatus.DISABLED,
                false,
                Instant.now()
        ));

        MessageResponse res = client.post()
                .uri("/api/auth/forgot-password")
                .body(new EmailRequest("off.teacher@college.edu"))
                .retrieve()
                .body(MessageResponse.class);
        assertThat(res).isNotNull();
        assertThat(res.message()).isEqualTo("If this email exists, we sent a link.");

        verify(emailSender, after(300).never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void teacherChangesDefaultPasswordAndClearsMustChangeFlag() {
        Department dept = departmentRepository.save(new Department("Economics"));
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                "new.teacher@college.edu",
                passwordEncoder.encode("ScheduliX@123"),
                "Prof. Kapoor",
                UserRole.TEACHER,
                AccountStatus.ACTIVE,
                true,
                Instant.now()
        ));

        LoginResponse login = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("new.teacher@college.edu", "ScheduliX@123"))
                .retrieve()
                .body(LoginResponse.class);
        assertThat(login).isNotNull();
        assertThat(login.user().mustChangePassword()).isTrue();

        // Wrong current password fails
        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                .body(new ChangePasswordRequest("WrongPass123", "MyNewPassword123!"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);

        // Right current password updates and clears mustChangePassword
        ResponseEntity<MessageResponse> changeRes = client.post()
                .uri("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                .body(new ChangePasswordRequest("ScheduliX@123", "MyNewPassword123!"))
                .retrieve()
                .toEntity(MessageResponse.class);
        assertThat(changeRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        UserSummary me = client.get()
                .uri("/api/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                .retrieve()
                .body(UserSummary.class);
        assertThat(me).isNotNull();
        assertThat(me.mustChangePassword()).isFalse();
    }

    private String extractRefreshCookiePair(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        return cookies.stream()
                .filter(c -> c.startsWith(AuthController.REFRESH_COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow()
                .split(";")[0];
    }

    private String extractToken(String emailBody) {
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(emailBody);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}