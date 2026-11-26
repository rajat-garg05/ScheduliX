package com.schedulix.auth;

import com.schedulix.common.ApiError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import com.schedulix.auth.service.AccountService;
import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.controller.AuthController;
import com.schedulix.auth.dto.ChangePasswordRequest;
import com.schedulix.auth.entity.Department;
import com.schedulix.auth.repository.DepartmentRepository;
import com.schedulix.auth.service.EmailSender;
import com.schedulix.auth.dto.LoginRequest;
import com.schedulix.auth.dto.LoginResponse;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AccountServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    AccountService accountService;

    @Autowired
    UserAccountRepository userAccountRepository;

    @MockitoBean
    EmailSender emailSender;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
    }

    @Test
    void createTeacherAccountAndLifecycleHelpersWorkEndToEnd() {
        Department dept = departmentRepository.save(new Department("Mathematics"));

        UserAccount teacher = accountService.createTeacherAccount(
                dept.getId(),
                "Prof.Mehta@College.edu",
                "Prof. Mehta"
        );
        assertThat(teacher.getEmail()).isEqualTo("prof.mehta@college.edu");
        assertThat(teacher.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(teacher.getEmailVerifiedAt()).isNotNull();
        assertThat(teacher.isMustChangePassword()).isTrue();

        // Can log in with default password immediately
        ResponseEntity<LoginResponse> firstLogin = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("prof.mehta@college.edu", AccountService.DEFAULT_TEACHER_PASSWORD))
                .retrieve()
                .toEntity(LoginResponse.class);
        assertThat(firstLogin.getBody()).isNotNull();
        assertThat(firstLogin.getBody().user().mustChangePassword()).isTrue();
        String cookiePair = extractRefreshCookiePair(firstLogin.getHeaders());

        // Change password unlocks normal endpoints
        client.post()
                .uri("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstLogin.getBody().accessToken())
                .body(new ChangePasswordRequest(AccountService.DEFAULT_TEACHER_PASSWORD, "TeacherPass123!"))
                .retrieve()
                .toBodilessEntity();

        // Reset back to default password ends existing sessions and sets mustChangePassword again
        accountService.resetToDefaultPassword(teacher.getId());

        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/refresh")
                .header(HttpHeaders.COOKIE, cookiePair)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);

        LoginResponse afterReset = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("prof.mehta@college.edu", AccountService.DEFAULT_TEACHER_PASSWORD))
                .retrieve()
                .body(LoginResponse.class);
        assertThat(afterReset).isNotNull();
        assertThat(afterReset.user().mustChangePassword()).isTrue();

        // Disable and re-enable account
        accountService.disableAccount(teacher.getId());
        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("prof.mehta@college.edu", AccountService.DEFAULT_TEACHER_PASSWORD))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Forbidden.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err.code()).isEqualTo("ACCOUNT_DISABLED");
                });

        accountService.enableAccount(teacher.getId());
        accountService.changeEmail(teacher.getId(), "mehta.new@college.edu");
        accountService.changeDisplayName(teacher.getId(), "Dr. Mehta");

        UserAccount updated = userAccountRepository.findById(teacher.getId()).orElseThrow();
        assertThat(updated.getEmail()).isEqualTo("mehta.new@college.edu");
        assertThat(updated.getDisplayName()).isEqualTo("Dr. Mehta");
        assertThat(updated.getStatus()).isEqualTo(AccountStatus.ACTIVE);
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
}