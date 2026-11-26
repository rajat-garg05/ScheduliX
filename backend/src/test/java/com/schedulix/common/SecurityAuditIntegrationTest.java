package com.schedulix.common;

import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.entity.Department;
import com.schedulix.auth.repository.DepartmentRepository;
import com.schedulix.auth.service.EmailSender;
import com.schedulix.auth.dto.LoginRequest;
import com.schedulix.auth.dto.LoginResponse;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;
import com.schedulix.auth.entity.UserRole;
import com.schedulix.setup.dto.TeacherRequest;
import com.schedulix.setup.dto.TeacherResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SecurityAuditIntegrationTest {

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
    void enforcesAuthRoleDepartmentIsolationAndDefaultPasswordRules() {
        // 1. Unauthenticated requests to protected routes are rejected (401)
        for (String path : new String[]{"/api/dashboard", "/api/teachers", "/api/leaves", "/api/today"}) {
            assertThatThrownBy(() -> client.get().uri(path).retrieve().toBodilessEntity())
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        }

        Department deptA = departmentRepository.save(new Department("Dept A " + UUID.randomUUID()));
        Department deptB = departmentRepository.save(new Department("Dept B " + UUID.randomUUID()));

        String hodA = createHodAndLogin(deptA, "hod.seca@college.edu");
        String hodB = createHodAndLogin(deptB, "hod.secb@college.edu");

        TeacherResponse teacherA = client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodA)
                .body(new TeacherRequest("T01", "Prof. Alpha", "alpha.sec@college.edu", "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
        assertThat(teacherA).isNotNull();

        // 2. Default-password rule: teacher with mustChangePassword=true cannot access normal APIs
        LoginResponse freshTeacherLogin = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("alpha.sec@college.edu", "ScheduliX@123"))
                .retrieve()
                .body(LoginResponse.class);
        assertThat(freshTeacherLogin).isNotNull();
        assertThat(freshTeacherLogin.user().mustChangePassword()).isTrue();

        assertThatThrownBy(() -> client.get()
                .uri("/api/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshTeacherLogin.accessToken())
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Forbidden.class);

        // Activate teacher (simulate password change)
        UserAccount teacherAcc = userAccountRepository.findByEmail("alpha.sec@college.edu").orElseThrow();
        teacherAcc.setMustChangePassword(false);
        userAccountRepository.save(teacherAcc);

        LoginResponse activeTeacherLogin = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest("alpha.sec@college.edu", "ScheduliX@123"))
                .retrieve()
                .body(LoginResponse.class);
        assertThat(activeTeacherLogin).isNotNull();

        // 3. Teacher cannot call HOD-only actions (403)
        assertThatThrownBy(() -> client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + activeTeacherLogin.accessToken())
                .body(new TeacherRequest("T99", "Unauth", "unauth@college.edu", "Professor"))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Forbidden.class);

        assertThatThrownBy(() -> client.get()
                .uri("/api/tree")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + activeTeacherLogin.accessToken())
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Forbidden.class);

        // 4. Cross-department isolation: Dept B HOD asking for Dept A's teacher gets 404
        assertThatThrownBy(() -> client.get()
                .uri("/api/teachers/" + teacherA.id() + "/lectures?date=2026-11-30")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodB)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);
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