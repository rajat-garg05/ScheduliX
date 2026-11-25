package com.schedulix.setup;

import com.schedulix.auth.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.history.HistoryService;
import com.schedulix.setup.PlanValidator.PlanEntry;
import com.schedulix.setup.PlanValidator.PlanSubjectInfo;
import com.schedulix.setup.PlanValidator.ValidationResult;
import com.schedulix.setup.SectionPlanResponse.PlanRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SectionPlanService {

    private static final String LOCK_MESSAGE =
            "This section has a saved timetable, so its plan is read-only. Delete the saved timetable in Classes and Timetables to edit it.";

    private final SemesterRepository semesterRepository;
    private final SectionRepository sectionRepository;
    private final SectionSubjectRepository sectionSubjectRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherSubjectRepository teacherSubjectRepository;
    private final PlanValidator planValidator;
    private final SavedTimetableLookup savedTimetableLookup;
    private final CurrentUser currentUser;
    private final HistoryService historyService;

    public SectionPlanService(
            SemesterRepository semesterRepository,
            SectionRepository sectionRepository,
            SectionSubjectRepository sectionSubjectRepository,
            SubjectRepository subjectRepository,
            TeacherSubjectRepository teacherSubjectRepository,
            PlanValidator planValidator,
            SavedTimetableLookup savedTimetableLookup,
            CurrentUser currentUser,
            HistoryService historyService
    ) {
        this.semesterRepository = semesterRepository;
        this.sectionRepository = sectionRepository;
        this.sectionSubjectRepository = sectionSubjectRepository;
        this.subjectRepository = subjectRepository;
        this.teacherSubjectRepository = teacherSubjectRepository;
        this.planValidator = planValidator;
        this.savedTimetableLookup = savedTimetableLookup;
        this.currentUser = currentUser;
        this.historyService = historyService;
    }

    @Transactional(readOnly = true)
    public SectionPlanResponse getPlan(UUID sectionId) {
        UUID deptId = currentUser.departmentId();
        Section section = loadOwnedSection(sectionId, deptId);
        Semester semester = loadOwnedSemester(section.getSemesterId(), deptId);
        return buildResponse(deptId, semester, section);
    }

    @Transactional
    public SectionPlanResponse replacePlan(UUID sectionId, List<PlanItemRequest> items) {
        UUID deptId = currentUser.departmentId();
        Section section = loadOwnedSection(sectionId, deptId);
        Semester semester = loadOwnedSemester(section.getSemesterId(), deptId);

        if (savedTimetableLookup.hasSavedTimetable(sectionId)) {
            throw new AppException(HttpStatus.CONFLICT, "PLAN_LOCKED", LOCK_MESSAGE);
        }

        List<PlanItemRequest> safeItems = items == null ? List.of() : items;
        List<PlanEntry> entries = new ArrayList<>();
        for (PlanItemRequest item : safeItems) {
            if (item == null || item.subjectId() == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_PLAN", "Every row must have a subject.");
            }
            if (item.lecturesPerWeek() <= 0 || item.lecturesPerWeek() > 40) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PLAN",
                        "Lectures per week must be between 1 and 40."
                );
            }
            entries.add(new PlanEntry(item.subjectId(), item.lecturesPerWeek()));
        }

        Map<UUID, PlanSubjectInfo> deptSubjectsById = new LinkedHashMap<>();
        for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            deptSubjectsById.put(s.getId(), PlanSubjectInfo.from(s));
        }
        Set<UUID> subjectsWithTeacher = teacherSubjectRepository.findSubjectIdsWithActiveTeacher(deptId);

        ValidationResult validation = planValidator.validate(entries, deptSubjectsById, subjectsWithTeacher);
        if (!validation.isValid()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_PLAN", validation.errors().get(0));
        }

        sectionSubjectRepository.deleteBySectionId(sectionId);
        sectionSubjectRepository.flush();

        if (!entries.isEmpty()) {
            List<SectionSubject> rowsToSave = entries.stream()
                    .map(e -> new SectionSubject(sectionId, e.subjectId(), (short) e.lecturesPerWeek()))
                    .toList();
            sectionSubjectRepository.saveAll(rowsToSave);
            sectionSubjectRepository.flush();
        }

        int totalLectures = entries.stream().mapToInt(PlanEntry::lecturesPerWeek).sum();
        historyService.record(
                "PLAN",
                "Updated semester plan for " + semester.getName() + " (Section " + section.getName() + "): "
                        + entries.size() + " subject" + (entries.size() == 1 ? "" : "s") + ", "
                        + totalLectures + " lectures per week."
        );

        return buildResponse(deptId, semester, section);
    }

    private SectionPlanResponse buildResponse(UUID deptId, Semester semester, Section section) {
        List<SectionSubject> stored = sectionSubjectRepository.findBySectionId(section.getId());
        Map<UUID, Subject> subjectsById = new LinkedHashMap<>();
        for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            subjectsById.put(s.getId(), s);
        }
        Set<UUID> subjectsWithTeacher = teacherSubjectRepository.findSubjectIdsWithActiveTeacher(deptId);

        List<PlanRow> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int totalLectures = 0;

        for (SectionSubject link : stored) {
            Subject subject = subjectsById.get(link.getSubjectId());
            if (subject == null) {
                continue;
            }
            boolean hasTeacher = subjectsWithTeacher.contains(subject.getId());
            int lectures = link.getLecturesPerWeek();
            totalLectures += lectures;
            rows.add(new PlanRow(
                    subject.getId(),
                    subject.getCode(),
                    subject.getName(),
                    subject.getKind(),
                    subject.getPeriodsInARow(),
                    subject.getRunTogetherGroup(),
                    subject.getSharedPlace(),
                    lectures,
                    hasTeacher
            ));
        }

        rows.sort(Comparator.comparing(PlanRow::subjectCode, String.CASE_INSENSITIVE_ORDER));
        for (PlanRow row : rows) {
            if (!row.hasTeacher()) {
                warnings.add("Nobody is listed as able to teach " + row.subjectName() + ".");
            }
        }

        boolean locked = savedTimetableLookup.hasSavedTimetable(section.getId());
        return new SectionPlanResponse(
                section.getId(),
                section.getName(),
                semester.getId(),
                semester.getName(),
                locked,
                locked ? LOCK_MESSAGE : null,
                rows.size(),
                totalLectures,
                warnings,
                rows
        );
    }

    private Section loadOwnedSection(UUID sectionId, UUID departmentId) {
        return sectionRepository.findByIdAndDepartmentId(sectionId, departmentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Section not found."));
    }

    private Semester loadOwnedSemester(UUID semesterId, UUID departmentId) {
        return semesterRepository.findByIdAndDepartmentId(semesterId, departmentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SEMESTER_NOT_FOUND", "Semester not found."));
    }
}