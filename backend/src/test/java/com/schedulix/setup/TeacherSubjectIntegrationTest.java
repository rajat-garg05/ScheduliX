package com.schedulix.setup;

import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.entity.Department;
import com.schedulix.auth.repository.DepartmentRepository;
import com.schedulix.auth.service.EmailSender;
import com.schedulix.auth.dto.LoginRequest;
import com.schedulix.auth.dto.LoginResponse;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;
import com.schedulix.auth.entity.UserRole;
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
import java.util.List;
import java.util.UUID;
import com.schedulix.setup.entity.SubjectKind;
import com.schedulix.setup.dto.SubjectRequest;
import com.schedulix.setup.dto.SubjectResponse;
import com.schedulix.setup.dto.SubjectTeachersRequest;
import com.schedulix.setup.dto.TeacherRequest;
import com.schedulix.setup.dto.TeacherResponse;
import com.schedulix.setup.dto.TeacherSubjectsRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TeacherSubjectIntegrationTest {

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
    void assignsAndReplacesLinksFromBothTeacherAndSubjectSidesAndIgnoresDuplicates() {
        String token = createHodAndLogin("CSE Links " + UUID.randomUUID(), "hod.links1@college.edu");

        TeacherResponse t1 = createTeacher(token, "T001", "Prof. A", "link.t1@college.edu");
        TeacherResponse t2 = createTeacher(token, "T002", "Prof. B", "link.t2@college.edu");
        SubjectResponse s1 = createSubject(token, "CS301", "Data Structures");
        SubjectResponse s2 = createSubject(token, "CS304", "Object Oriented Programming");

        // Assign s1 and s2 to t1 (with duplicate s1 ignored)
        List<SubjectResponse> assigned = client.put()
                .uri("/api/teachers/" + t1.id() + "/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherSubjectsRequest(List.of(s1.id(), s2.id(), s1.id())))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(assigned).extracting(SubjectResponse::code).containsExactly("CS301", "CS304");

        // Visible from subject side
        List<TeacherResponse> teachersForS1 = client.get()
                .uri("/api/subjects/" + s1.id() + "/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(teachersForS1).extracting(TeacherResponse::code).containsExactly("T001");

        // Replace teachers for s1: remove t1, add t2
        List<TeacherResponse> updatedTeachers = client.put()
                .uri("/api/subjects/" + s1.id() + "/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SubjectTeachersRequest(List.of(t2.id())))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(updatedTeachers).extracting(TeacherResponse::code).containsExactly("T002");

        // t1 now only has s2
        List<SubjectResponse> remainingForT1 = client.get()
                .uri("/api/teachers/" + t1.id() + "/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(remainingForT1).extracting(SubjectResponse::code).containsExactly("CS304");
    }

    @Test
    void rejectsCrossDepartmentTeacherOrSubjectIds() {
        String tokenA = createHodAndLogin("Dept Link A " + UUID.randomUUID(), "hod.la@college.edu");
        String tokenB = createHodAndLogin("Dept Link B " + UUID.randomUUID(), "hod.lb@college.edu");

        TeacherResponse teacherA = createTeacher(tokenA, "T01", "Prof. Alpha", "alpha.t@college.edu");
        SubjectResponse subjectB = createSubject(tokenB, "ME101", "Thermodynamics");

        // HOD A cannot link Dept B's subject
        assertThatThrownBy(() -> client.put()
                .uri("/api/teachers/" + teacherA.id() + "/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .body(new TeacherSubjectsRequest(List.of(subjectB.id())))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        // HOD B cannot read Dept A's teacher links
        assertThatThrownBy(() -> client.get()
                .uri("/api/teachers/" + teacherA.id() + "/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    private TeacherResponse createTeacher(String token, String code, String name, String email) {
        TeacherResponse res = client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherRequest(code, name, email, "Assistant Professor"))
                .retrieve()
                .body(TeacherResponse.class);
        assertThat(res).isNotNull();
        return res;
    }

    private SubjectResponse createSubject(String token, String code, String name) {
        SubjectResponse res = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SubjectRequest(code, name, SubjectKind.THEORY, 1, null, null))
                .retrieve()
                .body(SubjectResponse.class);
        assertThat(res).isNotNull();
        return res;
    }

    private String createHodAndLogin(String deptName, String email) {
        Department dept = departmentRepository.save(new Department(deptName));
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                email,
                passwordEncoder.encode("StrongPass123!"),
                "HOD of " + deptName,
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