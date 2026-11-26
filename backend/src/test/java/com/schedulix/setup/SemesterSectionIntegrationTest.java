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
import com.schedulix.common.ApiError;
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
import com.schedulix.setup.service.SavedTimetableLookup;
import com.schedulix.setup.dto.SectionRequest;
import com.schedulix.setup.dto.SectionResponse;
import com.schedulix.setup.dto.SemesterRequest;
import com.schedulix.setup.dto.SemesterResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SemesterSectionIntegrationTest {

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

    @MockitoBean
    SavedTimetableLookup savedTimetableLookup;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
        when(savedTimetableLookup.hasSavedTimetable(any())).thenReturn(false);
    }

    @Test
    void createRenameAndEnforceDeleteRulesForSemestersAndSections() {
        String token = createHodAndLogin("CSE Sem " + UUID.randomUUID(), "hod.sem1@college.edu");

        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        assertThat(sem3).isNotNull();
        assertThat(sem3.number()).isEqualTo(3);

        // Rename semester
        SemesterResponse renamedSem = client.put()
                .uri("/api/semesters/" + sem3.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SemesterRequest(3, "Third Semester"))
                .retrieve()
                .body(SemesterResponse.class);
        assertThat(renamedSem).isNotNull();
        assertThat(renamedSem.name()).isEqualTo("Third Semester");

        // Create Section A
        SectionResponse secA = client.post()
                .uri("/api/semesters/" + sem3.id() + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SectionRequest("A"))
                .retrieve()
                .body(SectionResponse.class);
        assertThat(secA).isNotNull();
        assertThat(secA.name()).isEqualTo("A");
        assertThat(secA.locked()).isFalse();

        // Rename Section A -> A1
        SectionResponse renamedSec = client.put()
                .uri("/api/sections/" + secA.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SectionRequest("A1"))
                .retrieve()
                .body(SectionResponse.class);
        assertThat(renamedSec).isNotNull();
        assertThat(renamedSec.name()).isEqualTo("A1");

        // Cannot delete semester while sections remain
        assertThatThrownBy(() -> client.delete()
                .uri("/api/semesters/" + sem3.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("SEMESTER_HAS_SECTIONS");
                });

        // Cannot delete section while it has a saved timetable
        when(savedTimetableLookup.hasSavedTimetable(secA.id())).thenReturn(true);
        assertThatThrownBy(() -> client.delete()
                .uri("/api/sections/" + secA.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("SECTION_HAS_SAVED_TIMETABLE");
                });

        // Once timetable is removed, section and then semester can be deleted
        when(savedTimetableLookup.hasSavedTimetable(secA.id())).thenReturn(false);
        client.delete()
                .uri("/api/sections/" + secA.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();

        client.delete()
                .uri("/api/semesters/" + sem3.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();

        List<SemesterResponse> remaining = client.get()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(remaining).isEmpty();
    }

    @Test
    void anotherDepartmentCannotSeeOrModifySemestersOrSections() {
        String tokenA = createHodAndLogin("Dept Sem A " + UUID.randomUUID(), "hod.sema@college.edu");
        String tokenB = createHodAndLogin("Dept Sem B " + UUID.randomUUID(), "hod.semb@college.edu");

        SemesterResponse semA = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .body(new SemesterRequest(5, "Semester 5"))
                .retrieve()
                .body(SemesterResponse.class);
        assertThat(semA).isNotNull();

        SectionResponse secA = client.post()
                .uri("/api/semesters/" + semA.id() + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .body(new SectionRequest("A"))
                .retrieve()
                .body(SectionResponse.class);
        assertThat(secA).isNotNull();

        List<SemesterResponse> listB = client.get()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(listB).isEmpty();

        assertThatThrownBy(() -> client.get()
                .uri("/api/semesters/" + semA.id() + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        assertThatThrownBy(() -> client.delete()
                .uri("/api/sections/" + secA.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);
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