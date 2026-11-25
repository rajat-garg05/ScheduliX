package com.schedulix.timetable;

import com.schedulix.setup.SectionSubjectRepository;
import com.schedulix.setup.UsageChecker;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Primary
@Component
public class TimetableUsageChecker implements UsageChecker {

    private final SectionSubjectRepository sectionSubjectRepository;
    private final TeacherChoiceRepository teacherChoiceRepository;
    private final FixedLectureRepository fixedLectureRepository;
    private final LectureRepository lectureRepository;

    public TimetableUsageChecker(
            SectionSubjectRepository sectionSubjectRepository,
            TeacherChoiceRepository teacherChoiceRepository,
            FixedLectureRepository fixedLectureRepository,
            LectureRepository lectureRepository
    ) {
        this.sectionSubjectRepository = sectionSubjectRepository;
        this.teacherChoiceRepository = teacherChoiceRepository;
        this.fixedLectureRepository = fixedLectureRepository;
        this.lectureRepository = lectureRepository;
    }

    @Override
    public boolean isTeacherUsed(UUID teacherId) {
        return teacherChoiceRepository.existsByTeacherId(teacherId)
                || lectureRepository.existsByTeacherId(teacherId);
    }

    @Override
    public boolean isSubjectUsed(UUID subjectId) {
        return sectionSubjectRepository.existsBySubjectId(subjectId)
                || teacherChoiceRepository.existsBySubjectId(subjectId)
                || fixedLectureRepository.existsBySubjectId(subjectId)
                || lectureRepository.existsBySubjectId(subjectId);
    }

    @Override
    public boolean isSubjectUsedInSavedTimetable(UUID subjectId) {
        return lectureRepository.existsSavedBySubjectId(subjectId);
    }
}