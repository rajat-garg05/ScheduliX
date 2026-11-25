package com.schedulix.common;

import com.schedulix.auth.AccountStatus;
import com.schedulix.auth.Department;
import com.schedulix.auth.DepartmentRepository;
import com.schedulix.auth.EmailSender;
import com.schedulix.auth.LoginRequest;
import com.schedulix.auth.LoginResponse;
import com.schedulix.auth.UserAccount;
import com.schedulix.auth.UserAccountRepository;
import com.schedulix.auth.UserRole;
import com.schedulix.leave.CreateLeaveRequest;
import com.schedulix.leave.LeaveScope;
import com.schedulix.setup.SectionRequest;
import com.schedulix.setup.SectionResponse;
import com.schedulix.setup.SemesterRequest;
import com.schedulix.setup.SemesterResponse;
import com.schedulix.setup.SubjectKind;
import com.schedulix.setup.SubjectRequest;
import com.schedulix.setup.SubjectResponse;
import com.schedulix.setup.TeacherRequest;
import com.schedulix.setup.TeacherResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DashboardIntegrationTest {

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
    void returnsRoleSpecificDashboardNumbersForHodAndTeacher() {
        Department dept = departmentRepository.save(new Department("CSE Dash " + UUID.randomUUID()));
        String hodToken = createHodAndLogin(dept, "hod.dash@college.edu");

        TeacherResponse rao = client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new TeacherRequest("T01", "Prof. Rao", "rao.dash@college.edu", "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
        client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SubjectRequest("CS301", "Data Structures", SubjectKind.THEORY, 1, null, null))
                .retrieve()
                .body(SubjectResponse.class);

        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        client.post()
                .uri("/api/semesters/" + sem3.id() + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SectionRequest("A"))
                .retrieve()
                .body(SectionResponse.class);

        String raoToken = activateTeacherAndLogin("rao.dash@college.edu");

        // Teacher asks for leave -> pendingLeaveCount becomes 1
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        client.post()
                .uri("/api/leaves")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + raoToken)
                .body(new CreateLeaveRequest(null, tomorrow, tomorrow, LeaveScope.WHOLE_DAYS, "Personal", null))
                .retrieve()
                .toBodilessEntity();

        DashboardResponse hodDash = client.get()
                .uri("/api/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .body(DashboardResponse.class);

        assertThat(hodDash).isNotNull();
        assertThat(hodDash.role()).isEqualTo("HOD");
        assertThat(hodDash.hod()).isNotNull();
        assertThat(hodDash.teacher()).isNull();
        assertThat(hodDash.hod().activeTeacherCount()).isEqualTo(1);
        assertThat(hodDash.hod().activeSubjectCount()).isEqualTo(1);
        assertThat(hodDash.hod().totalSectionCount()).isEqualTo(1);
        assertThat(hodDash.hod().savedTimetableSectionCount()).isEqualTo(0);
        assertThat(hodDash.hod().pendingLeaveCount()).isEqualTo(1);

        DashboardResponse teacherDash = client.get()
                .uri("/api/dashboard")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + raoToken)
                .retrieve()
                .body(DashboardResponse.class);

        assertThat(teacherDash).isNotNull();
        assertThat(teacherDash.role()).isEqualTo("TEACHER");
        assertThat(teacherDash.hod()).isNull();
        assertThat(teacherDash.teacher()).isNotNull();
        assertThat(teacherDash.teacher().teacherId()).isEqualTo(rao.id());
        assertThat(teacherDash.teacher().todayLectures()).isEmpty();
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