package com.schedulix.setup.service;

import com.schedulix.auth.security.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.common.CsvImportSupport;
import com.schedulix.history.HistoryService;
import com.schedulix.setup.dto.PlanImportPreviewResponse.PreviewRow;
import com.schedulix.setup.dto.PlanImportPreviewResponse.RowStatus;
import com.schedulix.setup.dto.PlanImportPreviewResponse.TargetSection;
import com.schedulix.setup.service.PlanValidator.PlanEntry;
import com.schedulix.setup.service.PlanValidator.PlanSubjectInfo;
import com.schedulix.setup.service.PlanValidator.ValidationResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.schedulix.setup.dto.PlanImportPreviewResponse;
import com.schedulix.setup.dto.PlanImportResultResponse;
import com.schedulix.setup.entity.Section;
import com.schedulix.setup.repository.SectionRepository;
import com.schedulix.setup.entity.SectionSubject;
import com.schedulix.setup.repository.SectionSubjectRepository;
import com.schedulix.setup.entity.Semester;
import com.schedulix.setup.repository.SemesterRepository;
import com.schedulix.setup.entity.Subject;
import com.schedulix.setup.repository.SubjectRepository;
import com.schedulix.setup.repository.TeacherSubjectRepository;

@Service
public class PlanCsvImportService {

    private static final List<String> REQUIRED_COLUMNS = List.of("subjectCode", "lecturesPerWeek");

    private final CsvImportSupport csvSupport;
    private final SemesterRepository semesterRepository;
    private final SectionRepository sectionRepository;
    private final SectionSubjectRepository sectionSubjectRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherSubjectRepository teacherSubjectRepository;
    private final PlanValidator planValidator;
    private final SavedTimetableLookup savedTimetableLookup;
    private final CurrentUser currentUser;
    private final HistoryService historyService;

    public PlanCsvImportService(
            CsvImportSupport csvSupport,
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
        this.csvSupport = csvSupport;
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

    public String template() {
        return "subjectCode,lecturesPerWeek\n"
                + "CS301,4\n";
    }

    @Transactional(readOnly = true)
    public PlanImportPreviewResponse preview(UUID sectionId, MultipartFile file, List<UUID> otherSectionIds) {
        Evaluation eval = evaluate(sectionId, file, otherSectionIds);
        return eval.preview();
    }

    @Transactional
    public PlanImportResultResponse confirm(UUID sectionId, MultipartFile file, List<UUID> otherSectionIds) {
        Evaluation eval = evaluate(sectionId, file, otherSectionIds);

        if (!eval.lockErrors().isEmpty()) {
            throw new AppException(HttpStatus.CONFLICT, "PLAN_LOCKED", eval.lockErrors().get(0));
        }
        if (eval.preview().problemCount() > 0) {
            String firstProblem = eval.preview().rows().stream()
                    .filter(r -> r.status() == RowStatus.PROBLEM)
                    .map(PreviewRow::message)
                    .findFirst()
                    .orElse("Fix the highlighted rows in the CSV file before confirming.");
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_PLAN", firstProblem);
        }

        List<String> sectionLabels = new ArrayList<>();
        for (Section target : eval.targets()) {
            sectionSubjectRepository.deleteBySectionId(target.getId());
            sectionSubjectRepository.flush();

            List<SectionSubject> rowsToSave = eval.validEntries().stream()
                    .map(entry -> new SectionSubject(target.getId(), entry.subjectId(), (short) entry.lecturesPerWeek()))
                    .toList();
            sectionSubjectRepository.saveAll(rowsToSave);
            sectionLabels.add("Section " + target.getName());
        }

        String joinedSections = String.join(", ", sectionLabels);
        historyService.record(
                "PLAN",
                "Uploaded semester plan for " + eval.semester().getName() + " (" + joinedSections + "): "
                        + eval.preview().subjectCount() + " subjects, "
                        + eval.preview().totalLecturesPerWeek() + " lectures per week."
        );

        String message = "Saved plan for " + joinedSections + " ("
                + eval.preview().subjectCount() + " subjects, "
                + eval.preview().totalLecturesPerWeek() + " lectures per week).";

        return new PlanImportResultResponse(
                sectionLabels,
                eval.preview().subjectCount(),
                eval.preview().totalLecturesPerWeek(),
                eval.preview().warnings(),
                message
        );
    }

    private record Evaluation(
            Semester semester,
            List<Section> targets,
            List<PlanEntry> validEntries,
            List<String> lockErrors,
            PlanImportPreviewResponse preview
    ) {
    }

    private static class DraftRow {
        final int rowNumber;
        final String rawCode;
        final String rawLectures;
        final Subject subject;
        final Integer lectures;
        RowStatus status;
        String message;

        DraftRow(int rowNumber, String rawCode, String rawLectures, Subject subject, Integer lectures, RowStatus status, String message) {
            this.rowNumber = rowNumber;
            this.rawCode = rawCode;
            this.rawLectures = rawLectures;
            this.subject = subject;
            this.lectures = lectures;
            this.status = status;
            this.message = message;
        }
    }

    private Evaluation evaluate(UUID primarySectionId, MultipartFile file, List<UUID> otherSectionIds) {
        UUID deptId = currentUser.departmentId();
        Section primarySection = sectionRepository.findByIdAndDepartmentId(primarySectionId, deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Section not found."));

        Semester semester = semesterRepository.findByIdAndDepartmentId(primarySection.getSemesterId(), deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SEMESTER_NOT_FOUND", "Semester not found."));

        List<Section> targets = resolveTargets(primarySection, otherSectionIds);
        List<TargetSection> targetSummaries = new ArrayList<>();
        List<String> lockErrors = new ArrayList<>();

        for (Section target : targets) {
            boolean locked = savedTimetableLookup.hasSavedTimetable(target.getId());
            targetSummaries.add(new TargetSection(target.getId(), target.getName(), locked));
            if (locked) {
                lockErrors.add("Section " + target.getName()
                        + " has a saved timetable, so its plan cannot be changed. Delete the saved timetable in Classes and Timetables first.");
            }
        }

        List<CsvImportSupport.ParsedRow> parsedRows = csvSupport.parse(file, REQUIRED_COLUMNS);

        List<Subject> deptSubjects = subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId);
        Map<String, Subject> subjectsByCode = new HashMap<>();
        Map<UUID, PlanSubjectInfo> subjectInfoById = new LinkedHashMap<>();
        for (Subject s : deptSubjects) {
            subjectsByCode.put(s.getCode().toLowerCase(Locale.ROOT), s);
            subjectInfoById.put(s.getId(), PlanSubjectInfo.from(s));
        }

        Map<UUID, Short> existingLecturesBySubjectId = new HashMap<>();
        for (SectionSubject existing : sectionSubjectRepository.findBySectionId(primarySection.getId())) {
            existingLecturesBySubjectId.put(existing.getSubjectId(), existing.getLecturesPerWeek());
        }

        Set<String> seenCodes = new HashSet<>();
        List<DraftRow> drafts = new ArrayList<>();
        List<PlanEntry> candidateEntries = new ArrayList<>();

        for (CsvImportSupport.ParsedRow row : parsedRows) {
            int rowNum = row.rowNumber();
            String code = row.get("subjectCode");
            String rawLectures = row.get("lecturesPerWeek");

            if (code.isEmpty()) {
                drafts.add(new DraftRow(rowNum, code, rawLectures, null, null, RowStatus.PROBLEM,
                        "Row " + rowNum + ": the subject code is missing."));
                continue;
            }

            String codeKey = code.toLowerCase(Locale.ROOT);
            if (!seenCodes.add(codeKey)) {
                Subject dupSub = subjectsByCode.get(codeKey);
                drafts.add(new DraftRow(rowNum, code, rawLectures, dupSub, null, RowStatus.PROBLEM,
                        "Row " + rowNum + ": subject code " + code + " appears more than once in this file."));
                continue;
            }

            Subject subject = subjectsByCode.get(codeKey);
            if (subject == null) {
                drafts.add(new DraftRow(rowNum, code, rawLectures, null, null, RowStatus.PROBLEM,
                        "Row " + rowNum + ": subject code " + code + " was not found."));
                continue;
            }
            if (!subject.isActive()) {
                drafts.add(new DraftRow(rowNum, code, rawLectures, subject, null, RowStatus.PROBLEM,
                        "Row " + rowNum + ": " + subject.getName() + " (" + subject.getCode() + ") is inactive."));
                continue;
            }

            int lectures;
            try {
                lectures = Integer.parseInt(rawLectures);
            } catch (NumberFormatException ex) {
                drafts.add(new DraftRow(rowNum, code, rawLectures, subject, null, RowStatus.PROBLEM,
                        "Row " + rowNum + ": lectures per week must be a whole number above 0."));
                continue;
            }

            if (lectures <= 0 || lectures > 40) {
                drafts.add(new DraftRow(rowNum, code, rawLectures, subject, lectures, RowStatus.PROBLEM,
                        "Row " + rowNum + ": lectures per week must be between 1 and 40."));
                continue;
            }

            Short existingLectures = existingLecturesBySubjectId.get(subject.getId());
            RowStatus initialStatus;
            String initialMessage;
            if (existingLectures == null) {
                initialStatus = RowStatus.NEW;
                initialMessage = "Will be added.";
            } else if (existingLectures.intValue() != lectures) {
                initialStatus = RowStatus.CHANGED;
                initialMessage = "Will change from " + existingLectures + " to " + lectures + " lectures.";
            } else {
                initialStatus = RowStatus.UNCHANGED;
                initialMessage = "No change.";
            }

            drafts.add(new DraftRow(rowNum, code, rawLectures, subject, lectures, initialStatus, initialMessage));
            candidateEntries.add(new PlanEntry(subject.getId(), lectures));
        }

        Set<UUID> subjectsWithTeacher = teacherSubjectRepository.findSubjectIdsWithActiveTeacher(deptId);
        ValidationResult validation = planValidator.validate(candidateEntries, subjectInfoById, subjectsWithTeacher);

        int newCount = 0;
        int changedCount = 0;
        int unchangedCount = 0;
        int problemCount = 0;
        int validSubjectCount = 0;
        int totalLectures = 0;
        List<PlanEntry> validEntries = new ArrayList<>();
        List<PreviewRow> previewRows = new ArrayList<>();

        for (DraftRow draft : drafts) {
            if (draft.status != RowStatus.PROBLEM && draft.subject != null) {
                String ruleError = validation.errorBySubjectId().get(draft.subject.getId());
                if (ruleError != null) {
                    draft.status = RowStatus.PROBLEM;
                    draft.message = "Row " + draft.rowNumber + ": " + ruleError;
                }
            }

            switch (draft.status) {
                case NEW -> {
                    newCount++;
                    validSubjectCount++;
                    totalLectures += draft.lectures;
                    validEntries.add(new PlanEntry(draft.subject.getId(), draft.lectures));
                }
                case CHANGED -> {
                    changedCount++;
                    validSubjectCount++;
                    totalLectures += draft.lectures;
                    validEntries.add(new PlanEntry(draft.subject.getId(), draft.lectures));
                }
                case UNCHANGED -> {
                    unchangedCount++;
                    validSubjectCount++;
                    totalLectures += draft.lectures;
                    validEntries.add(new PlanEntry(draft.subject.getId(), draft.lectures));
                }
                case PROBLEM -> problemCount++;
            }

            previewRows.add(new PreviewRow(
                    draft.rowNumber,
                    draft.status,
                    draft.subject != null ? draft.subject.getCode() : draft.rawCode,
                    draft.subject != null ? draft.subject.getName() : "",
                    draft.rawLectures,
                    draft.message
            ));
        }

        boolean canConfirm = problemCount == 0 && lockErrors.isEmpty() && !validEntries.isEmpty();
        String summary = problemCount > 0
                ? problemCount + " problem row" + (problemCount == 1 ? "" : "s") + " found. Fix the file and upload again."
                : validSubjectCount + " subjects, " + totalLectures + " lectures per week ("
                        + newCount + " new, " + changedCount + " changed, " + unchangedCount + " unchanged).";

        PlanImportPreviewResponse preview = new PlanImportPreviewResponse(
                drafts.size(),
                newCount,
                changedCount,
                unchangedCount,
                problemCount,
                validSubjectCount,
                totalLectures,
                canConfirm,
                summary,
                lockErrors,
                validation.warnings(),
                targetSummaries,
                previewRows
        );

        return new Evaluation(semester, targets, validEntries, lockErrors, preview);
    }

    private List<Section> resolveTargets(Section primarySection, List<UUID> otherSectionIds) {
        List<Section> targets = new ArrayList<>();
        targets.add(primarySection);

        if (otherSectionIds == null || otherSectionIds.isEmpty()) {
            return targets;
        }

        List<Section> semesterSections = sectionRepository.findBySemesterIdOrderByNameAsc(primarySection.getSemesterId());
        Map<UUID, Section> byId = new LinkedHashMap<>();
        for (Section s : semesterSections) {
            byId.put(s.getId(), s);
        }

        Set<UUID> seen = new LinkedHashSet<>();
        seen.add(primarySection.getId());

        for (UUID otherId : otherSectionIds) {
            if (otherId == null || !seen.add(otherId)) {
                continue;
            }
            Section other = byId.get(otherId);
            if (other == null) {
                throw new AppException(
                        HttpStatus.NOT_FOUND,
                        "SECTION_NOT_FOUND",
                        "One of the chosen sections does not belong to this semester."
                );
            }
            targets.add(other);
        }

        return targets;
    }
}