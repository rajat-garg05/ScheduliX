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
import com.schedulix.timetable.dto.TimetableViewResponse.ViewLecture;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.schedulix.timetable.dto.AddFixedLectureRequest;
import com.schedulix.timetable.dto.CreateDraftRequest;
import com.schedulix.timetable.dto.KeepLectureRequest;
import com.schedulix.timetable.dto.TimetableDraftResponse;
import com.schedulix.timetable.dto.TimetableMakeResponse;
import com.schedulix.timetable.repository.TimetableRepository;
import com.schedulix.timetable.dto.TimetableViewResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TimetableKeepAndMakeAgainIntegrationTest {

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
    PasswordEncoder passwordEncoder;

    @MockitoBean
    EmailSender emailSender;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
    }

    @Test
    void keptLecturesStayPutOnMakeAgainWhileUnkeptLecturesChangeAndKeepAllPinsEverything() {
        Department dept = departmentRepository.save(new Department("CSE Keep " + UUID.randomUUID()));
        String token = createHodAndLogin(dept, "hod.keep1@college.edu");

        TeacherResponse t1 = createTeacher(token, "T01", "Prof. Arjun", "arjun.kp@college.edu");
        TeacherResponse t2 = createTeacher(token, "T02", "Prof. Meera", "meera.kp@college.edu");

        SubjectResponse ds = createSubject(token, "CS301", "Data Structures", SubjectKind.THEORY, 1);
        SubjectResponse dm = createSubject(token, "CS302", "Discrete Maths", SubjectKind.THEORY, 1);
        SubjectResponse os = createSubject(token, "CS303", "Operating Systems", SubjectKind.THEORY, 1);

        linkTeacherSubjects(token, t1.id(), List.of(ds.id(), os.id()));
        linkTeacherSubjects(token, t2.id(), List.of(dm.id()));

        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        SectionResponse secA = client.post()
                .uri("/api/semesters/" + sem3.id() + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SectionRequest("A"))
                .retrieve()
                .body(SectionResponse.class);

        client.put()
                .uri("/api/sections/" + secA.id() + "/plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(List.of(
                        new PlanItemRequest(ds.id(), 4),
                        new PlanItemRequest(dm.id(), 4),
                        new PlanItemRequest(os.id(), 4)
                ))
                .retrieve()
                .toBodilessEntity();

        TimetableDraftResponse draft = client.post()
                .uri("/api/timetables")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new CreateDraftRequest(sem3.id(), List.of(secA.id())))
                .retrieve()
                .body(TimetableDraftResponse.class);

        client.post()
                .uri("/api/timetables/" + draft.id() + "/teachers/suggest")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity();

        // Pin one fixed lecture in Step 5
        client.post()
                .uri("/api/timetables/" + draft.id() + "/fixed-lectures")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new AddFixedLectureRequest(secA.id(), ds.id(), "MONDAY", 1))
                .retrieve()
                .toBodilessEntity();

        // First make (variation = 0)
        TimetableMakeResponse first = client.post()
                .uri("/api/timetables/" + draft.id() + "/make")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(TimetableMakeResponse.class);
        assertThat(first).isNotNull();
        assertThat(first.ok()).isTrue();

        // Trying to unpin the Step 5 fixed lecture is refused
        ViewLecture fixedLec = first.view().lectures().stream()
                .filter(ViewLecture::fixed)
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> client.put()
                .uri("/api/timetables/" + draft.id() + "/lectures/" + fixedLec.id() + "/keep")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new KeepLectureRequest(false))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("FIXED_LECTURE_LOCKED");
                });

        // Keep one non-fixed lecture
        ViewLecture chosenToKeep = first.view().lectures().stream()
                .filter(l -> !l.fixed())
                .findFirst()
                .orElseThrow();

        TimetableViewResponse afterKeep = client.put()
                .uri("/api/timetables/" + draft.id() + "/lectures/" + chosenToKeep.id() + "/keep")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new KeepLectureRequest(true))
                .retrieve()
                .body(TimetableViewResponse.class);
        assertThat(afterKeep).isNotNull();
        assertThat(afterKeep.lectures()).anySatisfy(l -> {
            if (l.id().equals(chosenToKeep.id())) {
                assertThat(l.kept()).isTrue();
            }
        });

        // Make again (variation increments to 1)
        TimetableMakeResponse second = client.post()
                .uri("/api/timetables/" + draft.id() + "/make-again")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(TimetableMakeResponse.class);
        assertThat(second).isNotNull();
        assertThat(second.ok()).isTrue();
        assertThat(timetableRepository.findById(draft.id()).orElseThrow().getVariation()).isEqualTo(1);

        // The kept lecture stayed at the exact same day & startLecture and remains kept
        assertThat(second.view().lectures()).anySatisfy(l -> {
            assertThat(l.sectionId()).isEqualTo(chosenToKeep.sectionId());
            assertThat(l.subjectId()).isEqualTo(chosenToKeep.subjectId());
            assertThat(l.dayOfWeek()).isEqualTo(chosenToKeep.dayOfWeek());
            assertThat(l.startLecture()).isEqualTo(chosenToKeep.startLecture());
            assertThat(l.kept()).isTrue();
        });

        // At least one unkept lecture moved compared to the first timetable
        Set<String> firstSlots = first.view().lectures().stream()
                .map(l -> l.subjectId() + "@" + l.dayOfWeek() + ":" + l.startLecture())
                .collect(Collectors.toSet());
        Set<String> secondSlots = second.view().lectures().stream()
                .map(l -> l.subjectId() + "@" + l.dayOfWeek() + ":" + l.startLecture())
                .collect(Collectors.toSet());
        assertThat(secondSlots).isNotEqualTo(firstSlots);

        // Keep all pins every lecture
        TimetableViewResponse allKept = client.post()
                .uri("/api/timetables/" + draft.id() + "/keep-all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(TimetableViewResponse.class);
        assertThat(allKept).isNotNull();
        assertThat(allKept.lectures()).allSatisfy(l -> assertThat(l.kept()).isTrue());
    }

    private TeacherResponse createTeacher(String token, String code, String name, String email) {
        return client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherRequest(code, name, email, "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
    }

    private SubjectResponse createSubject(String token, String code, String name, SubjectKind kind, int periods) {
        return client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SubjectRequest(code, name, kind, periods, null, null))
                .retrieve()
                .body(SubjectResponse.class);
    }

    private void linkTeacherSubjects(String token, UUID teacherId, List<UUID> subjectIds) {
        client.put()
                .uri("/api/teachers/" + teacherId + "/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherSubjectsRequest(subjectIds))
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