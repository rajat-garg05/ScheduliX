package com.schedulix.setup.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.schedulix.setup.entity.TeacherSubject;
import com.schedulix.setup.entity.TeacherSubjectId;

public interface TeacherSubjectRepository extends JpaRepository<TeacherSubject, TeacherSubjectId> {

    List<TeacherSubject> findByTeacherId(UUID teacherId);

    List<TeacherSubject> findBySubjectId(UUID subjectId);

    void deleteByTeacherId(UUID teacherId);

    void deleteBySubjectId(UUID subjectId);

    boolean existsByTeacherIdAndSubjectId(UUID teacherId, UUID subjectId);

    @Query("""
            SELECT DISTINCT ts.subjectId FROM TeacherSubject ts
            JOIN Teacher t ON t.id = ts.teacherId
            WHERE t.departmentId = :departmentId AND t.active = true
            """)
    Set<UUID> findSubjectIdsWithActiveTeacher(@Param("departmentId") UUID departmentId);
}