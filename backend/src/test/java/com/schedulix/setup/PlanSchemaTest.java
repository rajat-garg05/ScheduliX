package com.schedulix.setup;

import com.schedulix.auth.entity.Department;
import com.schedulix.auth.repository.DepartmentRepository;
import com.schedulix.auth.service.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.schedulix.setup.entity.Section;
import com.schedulix.setup.repository.SectionRepository;
import com.schedulix.setup.entity.SectionSubject;
import com.schedulix.setup.repository.SectionSubjectRepository;
import com.schedulix.setup.entity.Semester;
import com.schedulix.setup.repository.SemesterRepository;
import com.schedulix.setup.entity.Subject;
import com.schedulix.setup.entity.SubjectKind;
import com.schedulix.setup.repository.SubjectRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class PlanSchemaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    SubjectRepository subjectRepository;

    @Autowired
    SemesterRepository semesterRepository;

    @Autowired
    SectionRepository sectionRepository;

    @Autowired
    SectionSubjectRepository sectionSubjectRepository;

    @MockitoBean
    EmailSender emailSender;

    @Test
    void enforcesSemesterSectionAndPlanConstraints() {
        Department dept = departmentRepository.save(new Department("Computer Science"));
        Subject ds = subjectRepository.saveAndFlush(new Subject(
                dept.getId(), "CS301", "Data Structures", SubjectKind.THEORY, (short) 1, null, null
        ));

        Semester sem3 = semesterRepository.saveAndFlush(new Semester(dept.getId(), (short) 3, "Semester 3"));
        Section secA = sectionRepository.saveAndFlush(new Section(sem3.getId(), "A"));

        sectionSubjectRepository.saveAndFlush(new SectionSubject(secA.getId(), ds.getId(), (short) 4));
        assertThat(sectionSubjectRepository.findBySectionId(secA.getId())).hasSize(1);
        assertThat(sectionSubjectRepository.existsBySubjectId(ds.getId())).isTrue();

        // Semester number outside 1..12 is rejected
        Semester invalidSem = new Semester(dept.getId(), (short) 13, "Semester 13");
        assertThatThrownBy(() -> semesterRepository.saveAndFlush(invalidSem))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Duplicate semester number in same department is rejected
        Semester duplicateSem = new Semester(dept.getId(), (short) 3, "Third Sem");
        assertThatThrownBy(() -> semesterRepository.saveAndFlush(duplicateSem))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Duplicate section name in same semester is rejected
        Section duplicateSec = new Section(sem3.getId(), "A");
        assertThatThrownBy(() -> sectionRepository.saveAndFlush(duplicateSec))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Zero lectures_per_week is rejected
        Section secB = sectionRepository.saveAndFlush(new Section(sem3.getId(), "B"));
        SectionSubject zeroLectures = new SectionSubject(secB.getId(), ds.getId(), (short) 0);
        assertThatThrownBy(() -> sectionSubjectRepository.saveAndFlush(zeroLectures))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Deleting a section cascades to its plan rows
        sectionRepository.delete(secA);
        sectionRepository.flush();
        assertThat(sectionSubjectRepository.findBySectionId(secA.getId())).isEmpty();
    }
}