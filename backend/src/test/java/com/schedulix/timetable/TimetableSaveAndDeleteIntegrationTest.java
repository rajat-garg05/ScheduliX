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
import com.schedulix.setup.dto.TeacherSubjectsRequest;
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
import java.util.List;
import java.util.UUID;
import com.schedulix.timetable.dto.AddFixedLectureRequest;
import com.schedulix.timetable.dto.CreateDraftRequest;
import com.schedulix.timetable.repository.LectureRepository;
import com.schedulix.timetable.dto.TimetableDeletePreviewResponse;
import com.schedulix.timetable.dto.TimetableDraftResponse;
import com.schedulix.timetable.dto.TimetableMakeResponse;
import com.schedulix.timetable.entity.TimetableStatus;
import com.schedulix.timetable.dto.TimetableViewResponse;
import com.schedulix.timetable.dto.UpdateTeacherChoiceRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TimetableSaveAndDeleteIntegrationTest {

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
    LectureRepository lectureRepository;

    @Autowired
    SavedTimetableLookup savedTimetableLookup;

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
    void saveCatchesFreshBusyCellClashBlocksDuplicateSaveAndDeleteFreesTeacher() {
        Department dept = departmentRepository.save(new Department("CSE SaveDel " + UUID.randomUUID()));
        String token = createHodAndLogin(dept, "hod.sd1@college.edu");

        TeacherResponse rao = client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherRequest("T01", "Prof. Rao", "rao.sd@college.edu", "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
        SubjectResponse ds = client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SubjectRequest("CS301", "Data Structures", SubjectKind.THEORY, 1, null, null))
                .retrieve()
                .body(SubjectResponse.class);
        client.put()
                .uri("/api/teachers/" + rao.id() + "/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherSubjectsRequest(List.of(ds.id())))
                .retrieve()
                .toBodilessEntity();

        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        SectionResponse secA = createSection(token, sem3.id(), "A");
        SectionResponse secB = createSection(token, sem3.id(), "B");

        setPlan(token, secA.id(), ds.id(), 1);
        setPlan(token, secB.id(), ds.id(), 1);

        // Create TWO separate drafts that both pin Prof. Rao to Monday Lecture 1
        TimetableDraftResponse draftA = createDraftWithPin(token, sem3.id(), secA.id(), ds.id(), rao.id());
        TimetableDraftResponse draftB = createDraftWithPin(token, sem3.id(), secB.id(), ds.id(), rao.id());
        TimetableDraftResponse secondDraftForA = createDraftWithPin(token, sem3.id(), secA.id(), ds.id(), rao.id());

        // Make all drafts while nothing is saved yet
        makeDraft(token, draftA.id());
        makeDraft(token, draftB.id());
        makeDraft(token, secondDraftForA.id());

        // Save Draft A -> succeeds
        TimetableViewResponse savedA = client.post()
                .uri("/api/timetables/" + draftA.id() + "/save")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(TimetableViewResponse.class);
        assertThat(savedA).isNotNull();
        assertThat(savedA.status()).isEqualTo(TimetableStatus.SAVED);
        assertThat(savedA.savedAt()).isNotNull();
        assertThat(savedTimetableLookup.hasSavedTimetable(secA.id())).isTrue();

        // Saving a second draft for Section A is refused (one saved timetable per class)
        assertThatThrownBy(() -> client.post()
                .uri("/api/timetables/" + secondDraftForA.id() + "/save")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("SECTION_ALREADY_SAVED");
                });

        // Saving Draft B is refused because Prof. Rao is now busy on Monday Lecture 1 in Saved A
        assertThatThrownBy(() -> client.post()
                .uri("/api/timetables/" + draftB.id() + "/save")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("SAVE_CONFLICT");
                    assertThat(err.message()).contains("Prof. Rao").contains("make the timetable again");
                });

        // Delete preview on Saved A lists Prof. Rao and Section A
        TimetableDeletePreviewResponse preview = client.get()
                .uri("/api/timetables/" + draftA.id() + "/delete-preview")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(TimetableDeletePreviewResponse.class);
        assertThat(preview).isNotNull();
        assertThat(preview.teacherNames()).containsExactly("Prof. Rao");
        assertThat(preview.sectionNames()).containsExactly("A");
        assertThat(preview.summaryMessage()).contains("Prof. Rao will be free in these slots again");

        // Delete Saved A -> removes lectures and frees Prof. Rao
        client.delete()
                .uri("/api/timetables/" + draftA.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();

        assertThat(lectureRepository.findByTimetableId(draftA.id())).isEmpty();
        assertThat(savedTimetableLookup.hasSavedTimetable(secA.id())).isFalse();

        // Now Draft B can be saved cleanly using Monday Lecture 1 with Prof. Rao
        TimetableViewResponse savedB = client.post()
                .uri("/api/timetables/" + draftB.id() + "/save")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(TimetableViewResponse.class);
        assertThat(savedB).isNotNull();
        assertThat(savedB.status()).isEqualTo(TimetableStatus.SAVED);
    }

    private TimetableDraftResponse createDraftWithPin(String token, UUID semId, UUID secId, UUID subId, UUID teacherId) {
        TimetableDraftResponse draft = client.post()
                .uri("/api/timetables")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new CreateDraftRequest(semId, List.of(secId)))
                .retrieve()
                .body(TimetableDraftResponse.class);
        client.put()
                .uri("/api/timetables/" + draft.id() + "/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new UpdateTeacherChoiceRequest(secId, subId, teacherId))
                .retrieve()
                .toBodilessEntity();
        client.post()
                .uri("/api/timetables/" + draft.id() + "/fixed-lectures")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new AddFixedLectureRequest(secId, subId, "MONDAY", 1))
                .retrieve()
                .toBodilessEntity();
        return draft;
    }

    private void makeDraft(String token, UUID draftId) {
        TimetableMakeResponse res = client.post()
                .uri("/api/timetables/" + draftId + "/make")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(TimetableMakeResponse.class);
        assertThat(res).isNotNull();
        assertThat(res.ok()).isTrue();
    }

    private SectionResponse createSection(String token, UUID semesterId, String name) {
        return client.post()
                .uri("/api/semesters/" + semesterId + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SectionRequest(name))
                .retrieve()
                .body(SectionResponse.class);
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