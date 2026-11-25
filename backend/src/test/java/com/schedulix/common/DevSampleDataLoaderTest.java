package com.schedulix.common;

import com.schedulix.auth.EmailSender;
import com.schedulix.auth.UserAccount;
import com.schedulix.auth.UserAccountRepository;
import com.schedulix.setup.SemesterRepository;
import com.schedulix.setup.SubjectRepository;
import com.schedulix.setup.TeacherRepository;
import com.schedulix.timetable.TimetableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Testcontainers
class DevSampleDataLoaderTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    TeacherRepository teacherRepository;

    @Autowired
    SubjectRepository subjectRepository;

    @Autowired
    SemesterRepository semesterRepository;

    @Autowired
    TimetableRepository timetableRepository;

    @MockitoBean
    EmailSender emailSender;

    @Test
    void loadsSampleDepartmentInDevProfileWithoutMakingTimetablesAndRefusesNonDevProfile() {
        UserAccount hod = userAccountRepository.findByEmail(DevSampleDataLoader.DEMO_HOD_EMAIL).orElseThrow();
        UUID deptId = hod.getDepartmentId();

        assertThat(teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId)).hasSize(10);
        assertThat(subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)).hasSize(15);
        assertThat(semesterRepository.findByDepartmentIdOrderByNumberAsc(deptId)).hasSize(2);
        assertThat(timetableRepository.findByDepartmentIdOrderByCreatedAtDesc(deptId)).isEmpty();

        // Guard check: refuses to run when 'prod' profile is active
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        DevSampleDataLoader prodLoader = new DevSampleDataLoader(prodEnv, null, null, null);
        assertThatThrownBy(prodLoader::assertDevProfileActive)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev");
    }
}