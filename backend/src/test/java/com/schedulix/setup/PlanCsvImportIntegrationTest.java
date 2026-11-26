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
import com.schedulix.setup.dto.PlanImportPreviewResponse.RowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.schedulix.setup.dto.PlanImportPreviewResponse;
import com.schedulix.setup.dto.PlanImportResultResponse;
import com.schedulix.setup.service.SavedTimetableLookup;
import com.schedulix.setup.dto.SectionRequest;
import com.schedulix.setup.dto.SectionResponse;
import com.schedulix.setup.repository.SectionSubjectRepository;
import com.schedulix.setup.dto.SemesterRequest;
import com.schedulix.setup.dto.SemesterResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PlanCsvImportIntegrationTest {

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
    SectionSubjectRepository sectionSubjectRepository;

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
    void previewsAndAppliesSemester3PlanToSectionsAAndBAtOnce() {
        String token = createHodAndLogin("CSE Plan " + UUID.randomUUID(), "hod.plan1@college.edu");

        // Load sample subjects for Semester 3
        String subjectsCsv = """
                subjectCode,name,classification,periodsInARow,runsTogetherGroup,sharedPlace
                CS301,Data Structures,THEORY,1,,
                MA301,Discrete Mathematics,THEORY,1,,
                CS303,Computer Organization,THEORY,1,,
                CS304,Object Oriented Programming,THEORY,1,,
                CS305,Digital Logic Design,THEORY,1,,
                HS306,Universal Human Values,THEORY,1,,
                CS351,Data Structures Lab,LAB,2,LABGROUP-S3,Computer Lab 1
                CS352,Java Programming Lab,LAB,2,LABGROUP-S3,Computer Lab 2
                """;
        postCsv(token, "/api/subjects/import/confirm", subjectsCsv, List.of(), Object.class);

        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        SectionResponse secA = createSection(token, sem3.id(), "A");
        SectionResponse secB = createSection(token, sem3.id(), "B");

        String planCsv = """
                subjectCode,lecturesPerWeek
                CS301,4
                MA301,4
                CS303,3
                CS304,3
                CS305,3
                HS306,2
                CS351,2
                CS352,2
                """;

        // Preview does not write to database
        PlanImportPreviewResponse preview = postCsv(
                token,
                "/api/sections/" + secA.id() + "/plan/import/preview",
                planCsv,
                List.of(secB.id()),
                PlanImportPreviewResponse.class
        );
        assertThat(preview).isNotNull();
        assertThat(preview.canConfirm()).isTrue();
        assertThat(preview.subjectCount()).isEqualTo(8);
        assertThat(preview.totalLecturesPerWeek()).isEqualTo(23);
        assertThat(preview.targetSections()).hasSize(2);
        assertThat(preview.warnings()).hasSize(8); // no teachers linked yet
        assertThat(sectionSubjectRepository.findBySectionId(secA.id())).isEmpty();
        assertThat(sectionSubjectRepository.findBySectionId(secB.id())).isEmpty();

        // Confirm saves plan for both Section A and Section B
        PlanImportResultResponse result = postCsv(
                token,
                "/api/sections/" + secA.id() + "/plan/import/confirm",
                planCsv,
                List.of(secB.id()),
                PlanImportResultResponse.class
        );
        assertThat(result).isNotNull();
        assertThat(result.appliedSections()).containsExactly("Section A", "Section B");
        assertThat(result.subjectCount()).isEqualTo(8);
        assertThat(result.totalLecturesPerWeek()).isEqualTo(23);
        assertThat(sectionSubjectRepository.findBySectionId(secA.id())).hasSize(8);
        assertThat(sectionSubjectRepository.findBySectionId(secB.id())).hasSize(8);
    }

    @Test
    void rejectsWrongLabLecturesMissingGroupPeerAndLockedTargetSection() {
        String token = createHodAndLogin("CSE Plan Bad " + UUID.randomUUID(), "hod.plan2@college.edu");

        String subjectsCsv = """
                subjectCode,name,classification,periodsInARow,runsTogetherGroup,sharedPlace
                CS351,DSA Lab,LAB,2,LABGROUP-S3,Computer Lab 1
                CS352,Java Lab,LAB,2,LABGROUP-S3,Computer Lab 2
                """;
        postCsv(token, "/api/subjects/import/confirm", subjectsCsv, List.of(), Object.class);

        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        SectionResponse secA = createSection(token, sem3.id(), "A");
        SectionResponse secB = createSection(token, sem3.id(), "B");

        // Wrong lectures (3 for a 2-period lab) + missing peer CS352
        String badPlanCsv = """
                subjectCode,lecturesPerWeek
                CS351,3
                """;
        PlanImportPreviewResponse badPreview = postCsv(
                token,
                "/api/sections/" + secA.id() + "/plan/import/preview",
                badPlanCsv,
                List.of(),
                PlanImportPreviewResponse.class
        );
        assertThat(badPreview.canConfirm()).isFalse();
        assertThat(badPreview.problemCount()).isEqualTo(1);
        assertThat(badPreview.rows().get(0).status()).isEqualTo(RowStatus.PROBLEM);
        assertThat(badPreview.rows().get(0).message()).contains("DSA Lab needs 2 lectures in a row");

        // Confirm refuses bad plan
        assertThatThrownBy(() -> postCsv(
                token,
                "/api/sections/" + secA.id() + "/plan/import/confirm",
                badPlanCsv,
                List.of(),
                PlanImportResultResponse.class
        )).isInstanceOf(HttpClientErrorException.BadRequest.class);

        // Valid plan blocked if one of the ticked sections is locked
        String validPlanCsv = """
                subjectCode,lecturesPerWeek
                CS351,2
                CS352,2
                """;
        when(savedTimetableLookup.hasSavedTimetable(secB.id())).thenReturn(true);

        PlanImportPreviewResponse lockedPreview = postCsv(
                token,
                "/api/sections/" + secA.id() + "/plan/import/preview",
                validPlanCsv,
                List.of(secB.id()),
                PlanImportPreviewResponse.class
        );
        assertThat(lockedPreview.canConfirm()).isFalse();
        assertThat(lockedPreview.lockErrors()).hasSize(1);

        assertThatThrownBy(() -> postCsv(
                token,
                "/api/sections/" + secA.id() + "/plan/import/confirm",
                validPlanCsv,
                List.of(secB.id()),
                PlanImportResultResponse.class
        ))
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("PLAN_LOCKED");
                });
    }

    private SectionResponse createSection(String token, UUID semesterId, String name) {
        SectionResponse res = client.post()
                .uri("/api/semesters/" + semesterId + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SectionRequest(name))
                .retrieve()
                .body(SectionResponse.class);
        assertThat(res).isNotNull();
        return res;
    }

    private <T> T postCsv(String token, String path, String csvContent, List<UUID> otherSectionIds, Class<T> responseType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "plan.csv";
            }
        };
        body.add("file", resource);
        for (UUID otherId : otherSectionIds) {
            body.add("otherSectionIds", otherId.toString());
        }

        return client.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(responseType);
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