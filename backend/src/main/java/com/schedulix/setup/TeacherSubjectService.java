package com.schedulix.setup;

import com.schedulix.auth.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.history.HistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TeacherSubjectService {

    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherSubjectRepository teacherSubjectRepository;
    private final CurrentUser currentUser;
    private final HistoryService historyService;

    public TeacherSubjectService(
            TeacherRepository teacherRepository,
            SubjectRepository subjectRepository,
            TeacherSubjectRepository teacherSubjectRepository,
            CurrentUser currentUser,
            HistoryService historyService
    ) {
        this.teacherRepository = teacherRepository;
        this.subjectRepository = subjectRepository;
        this.teacherSubjectRepository = teacherSubjectRepository;
        this.currentUser = currentUser;
        this.historyService = historyService;
    }

    @Transactional(readOnly = true)
    public List<SubjectResponse> getSubjectsForTeacher(UUID teacherId) {
        UUID deptId = currentUser.departmentId();
        loadOwnedTeacher(teacherId, deptId);

        List<UUID> subjectIds = teacherSubjectRepository.findByTeacherId(teacherId).stream()
                .map(TeacherSubject::getSubjectId)
                .toList();
        if (subjectIds.isEmpty()) {
            return List.of();
        }

        return subjectRepository.findAllById(subjectIds).stream()
                .filter(s -> s.getDepartmentId().equals(deptId))
                .sorted(Comparator.comparing(Subject::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(SubjectResponse::from)
                .toList();
    }

    @Transactional
    public List<SubjectResponse> replaceSubjectsForTeacher(UUID teacherId, List<UUID> rawSubjectIds) {
        UUID deptId = currentUser.departmentId();
        Teacher teacher = loadOwnedTeacher(teacherId, deptId);

        Set<UUID> uniqueIds = new LinkedHashSet<>();
        if (rawSubjectIds != null) {
            for (UUID id : rawSubjectIds) {
                if (id != null) {
                    uniqueIds.add(id);
                }
            }
        }

        List<Subject> subjects = uniqueIds.isEmpty()
                ? List.of()
                : subjectRepository.findAllById(uniqueIds).stream()
                        .filter(s -> s.getDepartmentId().equals(deptId))
                        .toList();

        if (subjects.size() != uniqueIds.size()) {
            throw new AppException(
                    HttpStatus.NOT_FOUND,
                    "SUBJECT_NOT_FOUND",
                    "One or more chosen subjects were not found."
            );
        }

        teacherSubjectRepository.deleteByTeacherId(teacherId);
        teacherSubjectRepository.flush();

        if (!uniqueIds.isEmpty()) {
            List<TeacherSubject> links = uniqueIds.stream()
                    .map(subjectId -> new TeacherSubject(teacherId, subjectId))
                    .toList();
            teacherSubjectRepository.saveAll(links);
        }

        historyService.record(
                "TEACHER",
                "Updated subjects for " + teacher.getFullName() + " (" + teacher.getCode() + "): "
                        + uniqueIds.size() + " subject" + (uniqueIds.size() == 1 ? "" : "s") + "."
        );

        return subjects.stream()
                .sorted(Comparator.comparing(Subject::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(SubjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeacherResponse> getTeachersForSubject(UUID subjectId) {
        UUID deptId = currentUser.departmentId();
        loadOwnedSubject(subjectId, deptId);

        List<UUID> teacherIds = teacherSubjectRepository.findBySubjectId(subjectId).stream()
                .map(TeacherSubject::getTeacherId)
                .toList();
        if (teacherIds.isEmpty()) {
            return List.of();
        }

        return teacherRepository.findAllById(teacherIds).stream()
                .filter(t -> t.getDepartmentId().equals(deptId))
                .sorted(Comparator.comparing(Teacher::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(TeacherResponse::from)
                .toList();
    }

    @Transactional
    public List<TeacherResponse> replaceTeachersForSubject(UUID subjectId, List<UUID> rawTeacherIds) {
        UUID deptId = currentUser.departmentId();
        Subject subject = loadOwnedSubject(subjectId, deptId);

        Set<UUID> uniqueIds = new LinkedHashSet<>();
        if (rawTeacherIds != null) {
            for (UUID id : rawTeacherIds) {
                if (id != null) {
                    uniqueIds.add(id);
                }
            }
        }

        List<Teacher> teachers = uniqueIds.isEmpty()
                ? List.of()
                : teacherRepository.findAllById(uniqueIds).stream()
                        .filter(t -> t.getDepartmentId().equals(deptId))
                        .toList();

        if (teachers.size() != uniqueIds.size()) {
            throw new AppException(
                    HttpStatus.NOT_FOUND,
                    "TEACHER_NOT_FOUND",
                    "One or more chosen teachers were not found."
            );
        }

        teacherSubjectRepository.deleteBySubjectId(subjectId);
        teacherSubjectRepository.flush();

        if (!uniqueIds.isEmpty()) {
            List<TeacherSubject> links = uniqueIds.stream()
                    .map(teacherId -> new TeacherSubject(teacherId, subjectId))
                    .toList();
            teacherSubjectRepository.saveAll(links);
        }

        historyService.record(
                "SUBJECT",
                "Updated teachers for " + subject.getName() + " (" + subject.getCode() + "): "
                        + uniqueIds.size() + " teacher" + (uniqueIds.size() == 1 ? "" : "s") + "."
        );

        return teachers.stream()
                .sorted(Comparator.comparing(Teacher::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(TeacherResponse::from)
                .toList();
    }

    private Teacher loadOwnedTeacher(UUID teacherId, UUID departmentId) {
        return teacherRepository.findByIdAndDepartmentId(teacherId, departmentId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "TEACHER_NOT_FOUND",
                        "Teacher not found."
                ));
    }

    private Subject loadOwnedSubject(UUID subjectId, UUID departmentId) {
        return subjectRepository.findByIdAndDepartmentId(subjectId, departmentId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SUBJECT_NOT_FOUND",
                        "Subject not found."
                ));
    }
}