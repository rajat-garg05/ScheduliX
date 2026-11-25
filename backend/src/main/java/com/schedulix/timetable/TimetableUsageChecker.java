package com.schedulix.timetable;

import com.schedulix.leave.LeaveRepository;
import com.schedulix.leave.SubstituteRepository;
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
    private final LectureRepository lectureRepository;
    private final FixedLectureRepository fixedLectureRepository;
    private final LeaveRepository leaveRepository;
    private final SubstituteRepository substituteRepository;

    public TimetableUsageChecker(
            SectionSubjectRepository sectionSubjectRepository,
            TeacherChoiceRepository teacherChoiceRepository,
            LectureRepository lectureRepository,
            FixedLectureRepository fixedLectureRepository,
            LeaveRepository leaveRepository,
            SubstituteRepository substituteRepository
    ) {
        this.sectionSubjectRepository = sectionSubjectRepository;
        this.teacherChoiceRepository = teacherChoiceRepository;
        this.lectureRepository = lectureRepository;
        this.fixedLectureRepository = fixedLectureRepository;
        this.leaveRepository = leaveRepository;
        this.substituteRepository = substituteRepository;
    }

    @Override
    public boolean isTeacherUsed(UUID teacherId) {
        return teacherChoiceRepository.existsByTeacherId(teacherId)
                || lectureRepository.existsByTeacherId(teacherId)
                || leaveRepository.existsByTeacherId(teacherId)
                || substituteRepository.existsBySubstituteTeacherId(teacherId);
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