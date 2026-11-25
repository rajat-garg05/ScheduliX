package com.schedulix.auth;

import com.schedulix.common.ApiError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LoginSessionIntegrationTest {

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
    PasswordEncoder passwordEncoder;

    @Autowired
    LoginAttemptTracker attemptTracker;

    @MockitoBean
    EmailSender emailSender;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        attemptTracker.clearAll();
    }

    @Test
    void pendingAccountShowsEmailNotVerifiedOnlyWhenPasswordMatches() {
        Department dept = departmentRepository.save(new Department("Civil"));
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                "pending.hod@college.edu",
                passwordEncoder.encode("CorrectPass123!"),
                "HOD of Civil",
                UserRole.HOD,
                AccountStatus.PENDING,
                false,
                null
        ));

        // Wrong password gives generic invalid credentials
        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("pending.hod@college.edu", "WrongPassword123"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("INVALID_CREDENTIALS");
                });

        // Right password tells user to confirm email
        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("pending.hod@college.edu", "CorrectPass123!"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Forbidden.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("EMAIL_NOT_VERIFIED");
                });
    }

    @Test
    void loginSetsRefreshCookieRotatesOnRefreshAndRevokesOnLogout() {
        Department dept = departmentRepository.save(new Department("IT"));
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                "it.hod@college.edu",
                passwordEncoder.encode("StrongPass123!"),
                "HOD of IT",
                UserRole.HOD,
                AccountStatus.ACTIVE,
                false,
                Instant.now()
        ));

        ResponseEntity<LoginResponse> loginRes = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("it.hod@college.edu", "StrongPass123!"))
                .retrieve()
                .toEntity(LoginResponse.class);

        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginRes.getBody()).isNotNull();
        assertThat(loginRes.getBody().accessToken()).isNotBlank();
        assertThat(loginRes.getBody().user().role()).isEqualTo("HOD");

        String firstCookieHeader = extractRefreshCookie(loginRes.getHeaders());
        assertThat(firstCookieHeader).contains("HttpOnly");
        assertThat(firstCookieHeader).contains("SameSite=Strict");
        assertThat(firstCookieHeader).contains("Path=/api/auth");

        String firstCookiePair = firstCookieHeader.split(";")[0];

        // /api/me works with access token
        UserSummary me = client.get()
                .uri("/api/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginRes.getBody().accessToken())
                .retrieve()
                .body(UserSummary.class);
        assertThat(me).isNotNull();
        assertThat(me.displayName()).isEqualTo("HOD of IT");

        // Refresh rotates the cookie
        ResponseEntity<LoginResponse> refreshRes = client.post()
                .uri("/api/auth/refresh")
                .header(HttpHeaders.COOKIE, firstCookiePair)
                .retrieve()
                .toEntity(LoginResponse.class);
        assertThat(refreshRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        String secondCookiePair = extractRefreshCookie(refreshRes.getHeaders()).split(";")[0];
        assertThat(secondCookiePair).isNotEqualTo(firstCookiePair);

        // Old refresh cookie no longer works
        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/refresh")
                .header(HttpHeaders.COOKIE, firstCookiePair)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);

        // Logout revokes the active refresh cookie
        client.post()
                .uri("/api/auth/logout")
                .header(HttpHeaders.COOKIE, secondCookiePair)
                .retrieve()
                .toBodilessEntity();

        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/refresh")
                .header(HttpHeaders.COOKIE, secondCookiePair)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    void fiveWrongPasswordsLockOutSameEmailAndIp() {
        Department dept = departmentRepository.save(new Department("ECE"));
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                "ece.hod@college.edu",
                passwordEncoder.encode("CorrectPass123!"),
                "HOD of ECE",
                UserRole.HOD,
                AccountStatus.ACTIVE,
                false,
                Instant.now()
        ));

        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> client.post()
                    .uri("/api/auth/login")
                    .header("X-Forwarded-For", "203.0.113.10")
                    .body(new LoginRequest("ece.hod@college.edu", "BadPass"))
                    .retrieve()
                    .toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        }

        // 5th wrong try triggers 429 lockout
        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/login")
                .header("X-Forwarded-For", "203.0.113.10")
                .body(new LoginRequest("ece.hod@college.edu", "BadPass"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);

        // Even the right password is blocked during lockout
        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/login")
                .header("X-Forwarded-For", "203.0.113.10")
                .body(new LoginRequest("ece.hod@college.edu", "CorrectPass123!"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);
    }

    @Test
    void teacherWithMustChangePasswordCanOnlyCallAllowedEndpoints() {
        Department dept = departmentRepository.save(new Department("Maths"));
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                "teacher1@college.edu",
                passwordEncoder.encode("ScheduliX@123"),
                "Prof. Rao",
                UserRole.TEACHER,
                AccountStatus.ACTIVE,
                true,
                Instant.now()
        ));

        LoginResponse login = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("teacher1@college.edu", "ScheduliX@123"))
                .retrieve()
                .body(LoginResponse.class);

        assertThat(login).isNotNull();
        assertThat(login.user().mustChangePassword()).isTrue();

        // /api/me is allowed
        UserSummary me = client.get()
                .uri("/api/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                .retrieve()
                .body(UserSummary.class);
        assertThat(me).isNotNull();
        assertThat(me.mustChangePassword()).isTrue();

        // Any other protected endpoint is blocked with MUST_CHANGE_PASSWORD
        assertThatThrownBy(() -> client.get()
                .uri("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.accessToken())
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Forbidden.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("MUST_CHANGE_PASSWORD");
                });
    }

    @Test
    void disabledAccountCannotLogIn() {
        Department dept = departmentRepository.save(new Department("Physics"));
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                "disabled.teacher@college.edu",
                passwordEncoder.encode("StrongPass123!"),
                "Prof. Sen",
                UserRole.TEACHER,
                AccountStatus.DISABLED,
                false,
                Instant.now()
        ));

        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("disabled.teacher@college.edu", "StrongPass123!"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Forbidden.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("ACCOUNT_DISABLED");
                });
    }

    private String extractRefreshCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        return cookies.stream()
                .filter(c -> c.startsWith(AuthController.REFRESH_COOKIE_NAME + "="))
                .findFirst()
                .orElseThrow();
    }
}