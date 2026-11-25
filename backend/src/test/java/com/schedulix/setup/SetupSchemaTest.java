package com.schedulix.setup;

import com.schedulix.auth.AccountService;
import com.schedulix.auth.Department;
import com.schedulix.auth.DepartmentRepository;
import com.schedulix.auth.EmailSender;
import com.schedulix.auth.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class SetupSchemaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    AccountService accountService;

    @Autowired
    TeacherRepository teacherRepository;

    @Autowired
    SubjectRepository subjectRepository;

    @Autowired
    TeacherSubjectRepository teacherSubjectRepository;

    @MockitoBean
    EmailSender emailSender;

    @Test
    void enforcesTeacherSubjectConstraintsAndLinks() {
        Department dept = departmentRepository.save(new Department("Computer Science"));
        UserAccount account = accountService.createTeacherAccount(
                dept.getId(),
                "arjun.mehta@college.edu",
                "Prof. Arjun Mehta"
        );

        Teacher teacher = teacherRepository.saveAndFlush(new Teacher(
                dept.getId(),
                "T003",
                "Prof. Arjun Mehta",
                "arjun.mehta@college.edu",
                "Associate Professor",
                account.getId()
        ));

        Subject lab = subjectRepository.saveAndFlush(new Subject(
                dept.getId(),
                "CS351",
                "Data Structures Lab",
                SubjectKind.LAB,
                (short) 2,
                "LABGROUP-S3",
                "Computer Lab 1"
        ));

        teacherSubjectRepository.saveAndFlush(new TeacherSubject(teacher.getId(), lab.getId()));
        assertThat(teacherSubjectRepository.findByTeacherId(teacher.getId())).hasSize(1);

        // Theory subject cannot have a run_together_group
        Subject invalidTheory = new Subject(
                dept.getId(),
                "CS301",
                "Data Structures",
                SubjectKind.THEORY,
                (short) 1,
                "BAD-GROUP",
                null
        );
        assertThatThrownBy(() -> subjectRepository.saveAndFlush(invalidTheory))
                .isInstanceOf(DataIntegrityViolationException.class);

        // periods_in_a_row must be between 1 and 3
        Subject invalidPeriods = new Subject(
                dept.getId(),
                "CS399",
                "Long Lab",
                SubjectKind.LAB,
                (short) 4,
                null,
                null
        );
        assertThatThrownBy(() -> subjectRepository.saveAndFlush(invalidPeriods))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}