package com.schedulix.timetable.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import com.schedulix.timetable.entity.TeacherChoiceEntity;
import com.schedulix.timetable.entity.TeacherChoiceId;

public interface TeacherChoiceRepository extends JpaRepository<TeacherChoiceEntity, TeacherChoiceId> {

    List<TeacherChoiceEntity> findByTimetableId(UUID timetableId);

    void deleteByTimetableId(UUID timetableId);

    boolean existsByTeacherId(UUID teacherId);

    boolean existsBySubjectId(UUID subjectId);
}