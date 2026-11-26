package com.schedulix.leave;

import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.entity.Department;
import com.schedulix.auth.repository.DepartmentRepository;
import com.schedulix.auth.service.EmailSender;
import com.schedulix.auth.dto.LoginRequest;
import com.schedulix.auth.dto.LoginResponse;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;
import com.schedulix.auth.entity.UserRole;
import com.schedulix.common.ApiError;
import com.schedulix.setup.dto.TeacherRequest;
import com.schedulix.setup.dto.TeacherResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.schedulix.leave.dto.CreateLeaveRequest;
import com.schedulix.leave.dto.LeaveDecisionRequest;
import com.schedulix.leave.dto.LeaveResponse;
import com.schedulix.leave.entity.LeaveScope;
import com.schedulix.leave.entity.LeaveStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LeaveIntegrationTest {

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

    @MockitoBean
    EmailSender emailSender;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
    }

    @Test
    void teacherAsksHodAcceptsOverlapIsBlockedAndTeacherCannotSeeOthersLeave() {
        Department dept = departmentRepository.save(new Department("CSE Leave " + UUID.randomUUID()));
        String hodToken = createHodAndLogin(dept, "hod.lv1@college.edu");

        TeacherResponse rao = client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new TeacherRequest("T01", "Prof. Rao", "rao.lv@college.edu", "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
        TeacherResponse das = client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new TeacherRequest("T02", "Prof. Das", "das.lv@college.edu", "Professor"))
                .retrieve()
                .body(TeacherResponse.class);

        String raoToken = activateTeacherAndLogin("rao.lv@college.edu");
        String dasToken = activateTeacherAndLogin("das.lv@college.edu");

        LocalDate nextWeek = LocalDate.now().plusDays(7);

        // 1. Teacher Rao asks for leave -> status ASKED
        LeaveResponse asked = client.post()
                .uri("/api/leaves")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + raoToken)
                .body(new CreateLeaveRequest(null, nextWeek, nextWeek.plusDays(1), LeaveScope.WHOLE_DAYS, "Family event", null))
                .retrieve()
                .body(LeaveResponse.class);
        assertThat(asked).isNotNull();
        assertThat(asked.status()).isEqualTo(LeaveStatus.ASKED);
        assertThat(asked.teacherId()).isEqualTo(rao.id());

        // 2. Teacher Das cannot see Rao's leave
        List<LeaveResponse> dasLeaves = client.get()
                .uri("/api/leaves")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dasToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(dasLeaves).isEmpty();

        // 3. Teacher cannot accept their own leave (403)
        assertThatThrownBy(() -> client.post()
                .uri("/api/leaves/" + asked.id() + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + raoToken)
                .body(new LeaveDecisionRequest("Self approve"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Forbidden.class);

        // 4. HOD accepts Rao's leave
        LeaveResponse accepted = client.post()
                .uri("/api/leaves/" + asked.id() + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new LeaveDecisionRequest("Approved"))
                .retrieve()
                .body(LeaveResponse.class);
        assertThat(accepted).isNotNull();
        assertThat(accepted.status()).isEqualTo(LeaveStatus.ACCEPTED);

        // 5. HOD trying to add overlapping accepted leave for Rao is blocked
        assertThatThrownBy(() -> client.post()
                .uri("/api/leaves")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new CreateLeaveRequest(rao.id(), nextWeek.plusDays(1), nextWeek.plusDays(2), LeaveScope.WHOLE_DAYS, "Overlap", null))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("LEAVE_OVERLAP");
                    assertThat(err.message()).contains("Prof. Rao already has leave on these dates");
                });

        // 6. Teacher Rao cannot cancel an already-accepted leave
        assertThatThrownBy(() -> client.post()
                .uri("/api/leaves/" + accepted.id() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + raoToken)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class);

        // 7. HOD cancels Rao's accepted leave -> now Rao has no leaves
        client.post()
                .uri("/api/leaves/" + accepted.id() + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .toBodilessEntity();

        List<LeaveResponse> afterCancel = client.get()
                .uri("/api/leaves?teacherId=" + rao.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(afterCancel).isEmpty();
    }

    private String activateTeacherAndLogin(String email) {
        UserAccount acc = userAccountRepository.findByEmail(email).orElseThrow();
        acc.setMustChangePassword(false);
        userAccountRepository.save(acc);
        LoginResponse res = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email, "ScheduliX@123"))
                .retrieve()
                .body(LoginResponse.class);
        assertThat(res).isNotNull();
        return res.accessToken();
    }

    private String createHodAndLogin(Department dept, String email) {
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                email,
                passwordEncoder.encode("StrongPass123!"),
                "HOD of " + dept.getName(),
                UserRole.HOD,
                AccountStatus.ACTIVE,
                false,
                Instant.now()
        ));
        LoginResponse res = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email, "StrongPass123!"))
                .retrieve()
                .body(LoginResponse.class);
        assertThat(res).isNotNull();
        return res.accessToken();
    }
}