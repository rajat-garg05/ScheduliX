package com.schedulix.auth;

import com.schedulix.common.ApiError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.dto.EmailRequest;
import com.schedulix.auth.service.EmailSender;
import com.schedulix.auth.dto.MessageResponse;
import com.schedulix.auth.service.RateLimiter;
import com.schedulix.auth.dto.RegisterRequest;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;
import com.schedulix.auth.dto.VerifyEmailRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RegistrationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    RateLimiter rateLimiter;

    @MockitoBean
    EmailSender emailSender;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        rateLimiter.clearAll();
        reset(emailSender);
    }

    @Test
    void registerSendsLinkAndVerifyActivatesAccountAndLinkWorksOnce() {
        RegisterRequest req = new RegisterRequest(
                "Computer Science",
                "HOD.CS@College.EDU",
                "StrongPass123!"
        );

        ResponseEntity<MessageResponse> res = client.post()
                .uri("/api/auth/register")
                .body(req)
                .retrieve()
                .toEntity(MessageResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UserAccount saved = userAccountRepository.findByEmail("hod.cs@college.edu").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(saved.getDisplayName()).isEqualTo("HOD of Computer Science");
        assertThat(saved.getEmailVerifiedAt()).isNull();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("hod.cs@college.edu"), anyString(), bodyCaptor.capture());
        String token = extractToken(bodyCaptor.getValue());

        // Confirm email
        ResponseEntity<MessageResponse> verifyRes = client.post()
                .uri("/api/auth/verify-email")
                .body(new VerifyEmailRequest(token))
                .retrieve()
                .toEntity(MessageResponse.class);
        assertThat(verifyRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        UserAccount verified = userAccountRepository.findByEmail("hod.cs@college.edu").orElseThrow();
        assertThat(verified.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(verified.getEmailVerifiedAt()).isNotNull();

        // Using the same link a second time fails
        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/verify-email")
                .body(new VerifyEmailRequest(token))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void resendVerificationCancelsPreviousLink() {
        client.post()
                .uri("/api/auth/register")
                .body(new RegisterRequest("Mechanical", "mech.hod@college.edu", "StrongPass123!"))
                .retrieve()
                .toBodilessEntity();

        client.post()
                .uri("/api/auth/resend-verification")
                .body(new EmailRequest("mech.hod@college.edu"))
                .retrieve()
                .toBodilessEntity();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(2)).send(eq("mech.hod@college.edu"), anyString(), bodyCaptor.capture());

        String firstToken = extractToken(bodyCaptor.getAllValues().get(0));
        String secondToken = extractToken(bodyCaptor.getAllValues().get(1));

        // First link is now invalid
        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/verify-email")
                .body(new VerifyEmailRequest(firstToken))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class);

        // Second link works
        ResponseEntity<MessageResponse> okRes = client.post()
                .uri("/api/auth/verify-email")
                .body(new VerifyEmailRequest(secondToken))
                .retrieve()
                .toEntity(MessageResponse.class);
        assertThat(okRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void duplicateEmailReturnsPlainConflictMessage() {
        RegisterRequest req = new RegisterRequest("Electrical", "eee.hod@college.edu", "StrongPass123!");
        client.post().uri("/api/auth/register").body(req).retrieve().toBodilessEntity();

        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/register")
                .body(req)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    ApiError err = httpEx.getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("EMAIL_ALREADY_USED");
                });
    }

    @Test
    void registerRateLimitsAfterFiveAttemptsPerIp() {
        for (int i = 1; i <= 5; i++) {
            client.post()
                    .uri("/api/auth/register")
                    .header("X-Forwarded-For", "198.51.100.42")
                    .body(new RegisterRequest("Dept " + i, "dept" + i + "@college.edu", "StrongPass123!"))
                    .retrieve()
                    .toBodilessEntity();
        }

        assertThatThrownBy(() -> client.post()
                .uri("/api/auth/register")
                .header("X-Forwarded-For", "198.51.100.42")
                .body(new RegisterRequest("Dept 6", "dept6@college.edu", "StrongPass123!"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);
    }

    private String extractToken(String emailBody) {
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(emailBody);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}