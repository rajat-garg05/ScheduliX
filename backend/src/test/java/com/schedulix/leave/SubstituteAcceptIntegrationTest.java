package com.schedulix.leave;

import com.schedulix.auth.AccountStatus;
import com.schedulix.auth.Department;
import com.schedulix.auth.DepartmentRepository;
import com.schedulix.auth.EmailSender;
import com.schedulix.auth.LoginRequest;
import com.schedulix.auth.LoginResponse;
import com.schedulix.auth.UserAccount;
import com.schedulix.auth.UserAccountRepository;
import com.schedulix.auth.UserRole;
import com.schedulix.common.ApiError;
import com.schedulix.setup.SectionRequest;
import com.schedulix.setup.SectionResponse;
import com.schedulix.setup.SemesterRequest;
import com.schedulix.setup.SemesterResponse;
import com.schedulix.setup.SubjectKind;
import com.schedulix.setup.SubjectRequest;
import com.schedulix.setup.SubjectResponse;
import com.schedulix.setup.TeacherRequest;
import com.schedulix.setup.TeacherResponse;
import com.schedulix.setup.TeacherSubjectsRequest;
import com.schedulix.timetable.Lecture;
import com.schedulix.timetable.LectureRepository;
import com.schedulix.timetable.Timetable;
import com.schedulix.timetable.TimetableClass;
import com.schedulix.timetable.TimetableClassRepository;
import com.schedulix.timetable.TimetableDraftService;
import com.schedulix.timetable.TimetableRepository;
import com.schedulix.timetable.TimetableStatus;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SubstituteAcceptIntegrationTest {

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
    SubstituteRepository substituteRepository;

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
    void manualChoiceOptionsReCheckAcceptAndAcceptAllLeaveSavedLectureUnchanged() {
        Department dept = departmentRepository.save(new Department("CSE SubAcc " + UUID.randomUUID()));
        String hodToken = createHodAndLogin(dept, "hod.sa1@college.edu");

        TeacherResponse rao = createTeacher(hodToken, "T01", "Prof. Rao", "rao.sa@college.edu");
        TeacherResponse das = createTeacher(hodToken, "T02", "Prof. Das", "das.sa@college.edu");
        TeacherResponse guest = createTeacher(hodToken, "T03", "Prof. Guest", "guest.sa@college.edu");
        TeacherResponse busy = createTeacher(hodToken, "T04", "Prof. Busy", "busy.sa@college.edu");

        SubjectResponse dbms = createSubject(hodToken, "CS304", "DBMS");
        linkTeacherSubjects(hodToken, rao.id(), List.of(dbms.id()));
        linkTeacherSubjects(hodToken, das.id(), List.of(dbms.id()));

        SemesterResponse sem3 = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new SemesterRequest(3, "Semester 3"))
                .retrieve()
                .body(SemesterResponse.class);
        SectionResponse secA = createSection(hodToken, sem3.id(), "A");
        SectionResponse secB = createSection(hodToken, sem3.id(), "B");

        Timetable tt = timetableRepository.save(new Timetable(
                dept.getId(), sem3.id(), "Semester 3 - Sections A, B", TimetableStatus.SAVED,
                TimetableDraftService.DEFAULT_SETTINGS_JSON, null, Instant.now()
        ));
        tt.setSavedAt(Instant.now());
        timetableRepository.save(tt);
        timetableClassRepository.save(new TimetableClass(tt.getId(), secA.id(), true));
        timetableClassRepository.save(new TimetableClass(tt.getId(), secB.id(), true));

        Lecture raoLec = lectureRepository.save(new Lecture(
                tt.getId(), secA.id(), dbms.id(), rao.id(),
                DayOfWeek.TUESDAY, (short) 1, (short) 1,
                LocalTime.of(9, 0), LocalTime.of(9, 50), false, null
        ));
        lectureRepository.save(new Lecture(
                tt.getId(), secB.id(), dbms.id(), busy.id(),
                DayOfWeek.TUESDAY, (short) 1, (short) 1,
                LocalTime.of(9, 0), LocalTime.of(9, 50), false, null
        ));

        LocalDate nextTuesday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY));
        client.post()
                .uri("/api/leaves")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new CreateLeaveRequest(rao.id(), nextTuesday, nextTuesday, LeaveScope.WHOLE_DAYS, "Medical", null))
                .retrieve()
                .toBodilessEntity();

        Substitute sub = substituteRepository.findByLectureIdAndOnDate(raoLec.getId(), nextTuesday).orElseThrow();
        assertThat(sub.getSubstituteTeacherId()).isEqualTo(das.id());
        assertThat(sub.getStatus()).isEqualTo(SubstituteStatus.PROPOSED);

        // 1. Options list shows das (FREE_ABLE), guest (FREE_NOT_ABLE), busy (BUSY)
        List<SubstituteOptionResponse> options = client.get()
                .uri("/api/substitutes/" + sub.getId() + "/options")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(options).hasSize(3);
        assertThat(options.get(0).teacherId()).isEqualTo(das.id());
        assertThat(options.get(0).status()).isEqualTo("FREE_ABLE");
        assertThat(options.get(1).teacherId()).isEqualTo(guest.id());
        assertThat(options.get(1).status()).isEqualTo("FREE_NOT_ABLE");
        assertThat(options.get(1).warning()).isTrue();
        assertThat(options.get(2).teacherId()).isEqualTo(busy.id());
        assertThat(options.get(2).selectable()).isFalse();

        // 2. Trying to manually assign busy teacher is rejected by server re-check
        assertThatThrownBy(() -> client.put()
                .uri("/api/substitutes/" + sub.getId() + "/teacher")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new UpdateSubstituteTeacherRequest(busy.id()))
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Conflict.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("TEACHER_BUSY");
                });

        // 3. Manually assigning guest (free but not able) succeeds with warning
        SubstituteArrangementResponse updated = client.put()
                .uri("/api/substitutes/" + sub.getId() + "/teacher")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .body(new UpdateSubstituteTeacherRequest(guest.id()))
                .retrieve()
                .body(SubstituteArrangementResponse.class);
        assertThat(updated).isNotNull();
        assertThat(updated.substituteTeacherId()).isEqualTo(guest.id());
        assertThat(updated.warning()).contains("not listed as able");

        // 4. Accept all for that date accepts the arrangement
        AcceptAllSubstitutesResponse acceptAll = client.post()
                .uri("/api/substitutes/accept-all?date=" + nextTuesday)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodToken)
                .retrieve()
                .body(AcceptAllSubstitutesResponse.class);
        assertThat(acceptAll).isNotNull();
        assertThat(acceptAll.acceptedCount()).isEqualTo(1);
        assertThat(substituteRepository.findById(sub.getId()).orElseThrow().getStatus())
                .isEqualTo(SubstituteStatus.ACCEPTED);

        // 5. Saved timetable lecture row is completely unchanged (still Prof. Rao)
        assertThat(lectureRepository.findById(raoLec.getId()).orElseThrow().getTeacherId())
                .isEqualTo(rao.id());
    }

    private TeacherResponse createTeacher(String token, String code, String name, String email) {
        return client.post()
                .uri("/api/teachers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new TeacherRequest(code, name, email, "Professor"))
                .retrieve()
                .body(TeacherResponse.class);
    }

    private SubjectResponse createSubject(String token, String code, String name) {
        return client.post()
                .uri("/api/subjects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SubjectRequest(code, name, SubjectKind.THEORY, 1, null, null))
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

    private SectionResponse createSection(String token, UUID semesterId, String name) {
        return client.post()
                .uri("/api/semesters/" + semesterId + "/sections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new SectionRequest(name))
                .retrieve()
                .body(SectionResponse.class);
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