package com.schedulix.setup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeacherSubjectRepository extends JpaRepository<TeacherSubject, TeacherSubjectId> {

    List<TeacherSubject> findByTeacherId(UUID teacherId);

    List<TeacherSubject> findBySubjectId(UUID subjectId);

    void deleteByTeacherId(UUID teacherId);

    void deleteBySubjectId(UUID subjectId);

    boolean existsByTeacherIdAndSubjectId(UUID teacherId, UUID subjectId);
}