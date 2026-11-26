package com.schedulix.timetable;

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
import com.schedulix.setup.dto.PlanItemRequest;
import com.schedulix.setup.service.SavedTimetableLookup;
import com.schedulix.setup.dto.SectionRequest;
import com.schedulix.setup.dto.SectionResponse;
import com.schedulix.setup.dto.SemesterRequest;
import com.schedulix.setup.dto.SemesterResponse;
import com.schedulix.setup.entity.SubjectKind;
import com.schedulix.setup.dto.SubjectRequest;
import com.schedulix.setup.dto.SubjectResponse;
import com.schedulix.setup.dto.TeacherRequest;
import com.schedulix.setup.dto.TeacherResponse;
import com.schedulix.setup.service.UsageChecker;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import com.schedulix.timetable.dto.CreateDraftRequest;
import com.schedulix.timetable.entity.Lecture;
import com.schedulix.timetable.repository.LectureRepository;
import com.schedulix.timetable.entity.Timetable;
import com.schedulix.timetable.entity.TimetableClass;
import com.schedulix.timetable.repository.TimetableClassRepository;
import com.schedulix.timetable.dto.TimetableDraftResponse;
import com.schedulix.timetable.service.TimetableDraftService;
import com.schedulix.timetable.repository.TimetableRepository;
import com.schedulix.timetable.entity.TimetableStatus;
import com.schedulix.timetable.dto.TimetableSummaryResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TimetableDraftIntegrationTest {

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
    TimetableRepository timetableRepository;

    @Autowired
    TimetableClassRepository timetableClassRepository;

    @Autowired
    LectureRepository lectureRepository;

    @Autowired
    SavedTimetableLookup savedTimetableLookup;

    @Autowired
    UsageChecker usageChecker;

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
    void createsListsDeletesDraftAndActivatesRealSavedLookupAndUsageChecker() {
        Department dept = departmentRepository.save(new Department("CSE Draft " + UUID.randomUUID()));
        String token = createHodAndLogin(dept, "hod.draft1@college.edu");

        TeacherResponse teacher = client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherRequest("T01", "Prof. Rao", "rao.draft@college.edu", "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
        SubjectResponse ds = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SubjectRequest("CS301", "Data Structures", SubjectKind.THEORY, 1, null, null))
                .retrieve()
                .body(SubjectResponse.class);
        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        SectionResponse secA = createSection(token, sem3.id(), "A");
        SectionResponse secB = createSection(token, sem3.id(), "B");

        // Creating a draft for a section with no plan is rejected
        assertThatThrownBy(() -> client.post()
                .uri("/api/timetables")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new CreateDraftRequest(sem3.id(), List.of(secA.id())))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("SECTION_NO_PLAN");
                });

        // Add plans for Section A and Section B
        setPlan(token, secA.id(), ds.id(), 4);
        setPlan(token, secB.id(), ds.id(), 4);

        // Create draft for Sections A and B
        TimetableDraftResponse draft = client.post()
                .uri("/api/timetables")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new CreateDraftRequest(sem3.id(), List.of(secB.id(), secA.id())))
                .retrieve()
                .body(TimetableDraftResponse.class);

        assertThat(draft).isNotNull();
        assertThat(draft.name()).isEqualTo("Semester 3 - Sections A, B");
        assertThat(draft.status()).isEqualTo(TimetableStatus.DRAFT);
        assertThat(draft.sections()).hasSize(2);

        // List drafts
        List<TimetableSummaryResponse> drafts = client.get()
                .uri("/api/timetables?status=DRAFT")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).sectionNames()).containsExactly("A", "B");

        // Another department cannot view this draft
        Department otherDept = departmentRepository.save(new Department("Other Dept " + UUID.randomUUID()));
        String otherToken = createHodAndLogin(otherDept, "hod.draftother@college.edu");
        assertThatThrownBy(() -> client.get()
                .uri("/api/timetables/" + draft.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        // Delete draft
        client.delete()
                .uri("/api/timetables/" + draft.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();

        List<TimetableSummaryResponse> afterDelete = client.get()
                .uri("/api/timetables?status=DRAFT")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(afterDelete).isEmpty();

        // Real SavedTimetableLookup & UsageChecker reflect saved timetable + lectures
        assertThat(savedTimetableLookup.hasSavedTimetable(secA.id())).isFalse();
        assertThat(usageChecker.isTeacherUsed(teacher.id())).isFalse();
        assertThat(usageChecker.isSubjectUsedInSavedTimetable(ds.id())).isFalse();

        Timetable savedTt = timetableRepository.save(new Timetable(
                dept.getId(), sem3.id(), "Semester 3 - Section A",
                TimetableStatus.SAVED, TimetableDraftService.DEFAULT_SETTINGS_JSON, null, Instant.now()
        ));
        timetableClassRepository.save(new TimetableClass(savedTt.getId(), secA.id(), true));
        lectureRepository.save(new Lecture(
                savedTt.getId(), secA.id(), ds.id(), teacher.id(),
                DayOfWeek.MONDAY, (short) 1, (short) 1,
                LocalTime.of(9, 0), LocalTime.of(9, 50), false, null
        ));

        assertThat(savedTimetableLookup.hasSavedTimetable(secA.id())).isTrue();
        assertThat(usageChecker.isTeacherUsed(teacher.id())).isTrue();
        assertThat(usageChecker.isSubjectUsedInSavedTimetable(ds.id())).isTrue();

        // Creating a draft for Section A is now blocked
        assertThatThrownBy(() -> client.post()
                .uri("/api/timetables")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new CreateDraftRequest(sem3.id(), List.of(secA.id())))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("SECTION_HAS_SAVED_TIMETABLE");
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

    private void setPlan(String token, UUID sectionId, UUID subjectId, int lectures) {
        client.put()
                .uri("/api/sections/" + sectionId + "/plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(List.of(new PlanItemRequest(subjectId, lectures)))
                .retrieve()
                .toBodilessEntity();
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