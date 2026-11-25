package com.schedulix.setup;

import com.schedulix.auth.AccountService;
import com.schedulix.auth.CurrentUser;
import com.schedulix.auth.UserAccount;
import com.schedulix.auth.UserAccountRepository;
import com.schedulix.common.CsvImportMode;
import com.schedulix.common.CsvImportPreviewResponse;
import com.schedulix.common.CsvImportResultResponse;
import com.schedulix.common.CsvImportSupport;
import com.schedulix.common.CsvPreviewRow;
import com.schedulix.common.CsvRowStatus;
import com.schedulix.history.HistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SetupCsvImportService {

    private static final List<String> TEACHER_COLUMNS = List.of("facultyCode", "fullName", "officialEmail");
    private static final List<String> SUBJECT_COLUMNS = List.of("subjectCode", "name", "classification");
    private static final List<String> LINK_COLUMNS = List.of("facultyCode", "subjectCode");

    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CsvImportSupport csvSupport;
    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherSubjectRepository teacherSubjectRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountService accountService;
    private final CurrentUser currentUser;
    private final HistoryService historyService;
    private final UsageChecker usageChecker;
    private final SubjectGroupValidator groupValidator;

    public SetupCsvImportService(
            CsvImportSupport csvSupport,
            TeacherRepository teacherRepository,
            SubjectRepository subjectRepository,
            TeacherSubjectRepository teacherSubjectRepository,
            UserAccountRepository userAccountRepository,
            AccountService accountService,
            CurrentUser currentUser,
            HistoryService historyService,
            UsageChecker usageChecker,
            SubjectGroupValidator groupValidator
    ) {
        this.csvSupport = csvSupport;
        this.teacherRepository = teacherRepository;
        this.subjectRepository = subjectRepository;
        this.teacherSubjectRepository = teacherSubjectRepository;
        this.userAccountRepository = userAccountRepository;
        this.accountService = accountService;
        this.currentUser = currentUser;
        this.historyService = historyService;
        this.usageChecker = usageChecker;
        this.groupValidator = groupValidator;
    }

    // ---- Templates ----

    public String teachersTemplate() {
        return "facultyCode,fullName,officialEmail,designation\n"
                + "T001,Dr. Rajesh Kulkarni,rajesh.kulkarni@college.edu,Professor\n";
    }

    public String subjectsTemplate() {
        return "subjectCode,name,classification,periodsInARow,runsTogetherGroup,sharedPlace\n"
                + "CS301,Data Structures,THEORY,1,,\n";
    }

    public String teacherSubjectsTemplate() {
        return "facultyCode,subjectCode\n"
                + "T001,CS301\n";
    }

    // ---- Teachers Import ----

    private record TeacherEvalRow(
            CsvPreviewRow preview,
            Teacher existing,
            String code,
            String fullName,
            String email,
            String designation
    ) {
    }

    @Transactional(readOnly = true)
    public CsvImportPreviewResponse previewTeachers(MultipartFile file, CsvImportMode mode) {
        List<TeacherEvalRow> evaluated = evaluateTeachers(file, mode == null ? CsvImportMode.ADD_ONLY : mode);
        return CsvImportPreviewResponse.from(evaluated.stream().map(TeacherEvalRow::preview).toList());
    }

    @Transactional
    public CsvImportResultResponse confirmTeachers(MultipartFile file, CsvImportMode mode) {
        CsvImportMode effectiveMode = mode == null ? CsvImportMode.ADD_ONLY : mode;
        UUID deptId = currentUser.departmentId();
        List<TeacherEvalRow> evaluated = evaluateTeachers(file, effectiveMode);

        int added = 0;
        int updated = 0;
        int skipped = 0;
        int problems = 0;

        for (TeacherEvalRow row : evaluated) {
            switch (row.preview().status()) {
                case NEW -> {
                    UserAccount account = accountService.createTeacherAccount(deptId, row.email(), row.fullName());
                    teacherRepository.save(new Teacher(
                            deptId,
                            row.code(),
                            row.fullName(),
                            row.email(),
                            row.designation(),
                            account.getId()
                    ));
                    added++;
                }
                case UPDATE -> {
                    Teacher existing = row.existing();
                    if (!existing.getEmail().equals(row.email())) {
                        accountService.changeEmail(existing.getUserAccountId(), row.email());
                        existing.setEmail(row.email());
                    }
                    if (!existing.getFullName().equals(row.fullName())) {
                        accountService.changeDisplayName(existing.getUserAccountId(), row.fullName());
                        existing.setFullName(row.fullName());
                    }
                    existing.setDesignation(row.designation());
                    updated++;
                }
                case UNCHANGED -> skipped++;
                case PROBLEM -> {
                    skipped++;
                    problems++;
                }
            }
        }

        int totalProcessed = evaluated.size();
        historyService.record(
                "IMPORT",
                currentUser.displayName() + " imported " + totalProcessed + " teacher row"
                        + (totalProcessed == 1 ? "" : "s") + ": "
                        + added + " added, " + updated + " updated, " + skipped + " skipped."
        );

        String message = "Import complete: " + added + " added, " + updated + " updated, " + skipped + " skipped.";
        return new CsvImportResultResponse(added, updated, skipped, problems, message);
    }

    private List<TeacherEvalRow> evaluateTeachers(MultipartFile file, CsvImportMode mode) {
        UUID deptId = currentUser.departmentId();
        List<CsvImportSupport.ParsedRow> parsedRows = csvSupport.parse(file, TEACHER_COLUMNS);

        Map<String, Teacher> existingByCode = new HashMap<>();
        for (Teacher t : teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            existingByCode.put(t.getCode().toLowerCase(Locale.ROOT), t);
        }

        Set<String> seenCodes = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        List<TeacherEvalRow> results = new ArrayList<>();

        for (CsvImportSupport.ParsedRow row : parsedRows) {
            String code = row.get("facultyCode");
            String fullName = row.get("fullName");
            String rawEmail = row.get("officialEmail");
            String email = rawEmail.toLowerCase(Locale.ROOT);
            String designation = row.get("designation");

            Map<String, String> display = new LinkedHashMap<>();
            display.put("facultyCode", code);
            display.put("fullName", fullName);
            display.put("officialEmail", email);
            display.put("designation", designation);

            String problem = validateTeacherFields(row.rowNumber(), code, fullName, email, designation);
            if (problem != null) {
                results.add(problemTeacher(row.rowNumber(), problem, display));
                continue;
            }

            String codeKey = code.toLowerCase(Locale.ROOT);
            if (!seenCodes.add(codeKey)) {
                results.add(problemTeacher(
                        row.rowNumber(),
                        "Row " + row.rowNumber() + ": teacher code " + code + " appears more than once in this file.",
                        display
                ));
                continue;
            }
            if (!seenEmails.add(email)) {
                results.add(problemTeacher(
                        row.rowNumber(),
                        "Row " + row.rowNumber() + ": email " + email + " appears more than once in this file.",
                        display
                ));
                continue;
            }

            Teacher existing = existingByCode.get(codeKey);
            if (existing != null) {
                if (mode == CsvImportMode.ADD_ONLY) {
                    results.add(new TeacherEvalRow(
                            new CsvPreviewRow(row.rowNumber(), CsvRowStatus.UNCHANGED, "Already exists, skipped.", display),
                            existing, code, fullName, email, designation
                    ));
                    continue;
                }

                if (!existing.getEmail().equals(email) && userAccountRepository.existsByEmail(email)) {
                    results.add(problemTeacher(
                            row.rowNumber(),
                            "Row " + row.rowNumber() + ": this email is already used by another account.",
                            display
                    ));
                    continue;
                }

                boolean unchanged = existing.getFullName().equals(fullName)
                        && existing.getEmail().equals(email)
                        && existing.getDesignation().equals(designation);
                if (unchanged) {
                    results.add(new TeacherEvalRow(
                            new CsvPreviewRow(row.rowNumber(), CsvRowStatus.UNCHANGED, "No changes.", display),
                            existing, code, fullName, email, designation
                    ));
                } else {
                    results.add(new TeacherEvalRow(
                            new CsvPreviewRow(row.rowNumber(), CsvRowStatus.UPDATE, "Will update teacher details.", display),
                            existing, code, fullName, email, designation
                    ));
                }
            } else {
                if (userAccountRepository.existsByEmail(email)) {
                    results.add(problemTeacher(
                            row.rowNumber(),
                            "Row " + row.rowNumber() + ": this email is already used by another account.",
                            display
                    ));
                    continue;
                }
                results.add(new TeacherEvalRow(
                        new CsvPreviewRow(row.rowNumber(), CsvRowStatus.NEW, "Will be added.", display),
                        null, code, fullName, email, designation
                ));
            }
        }

        return results;
    }

    private String validateTeacherFields(int rowNum, String code, String fullName, String email, String designation) {
        if (code.isEmpty()) return "Row " + rowNum + ": the teacher code is missing.";
        if (code.length() > 30) return "Row " + rowNum + ": the teacher code is longer than 30 characters.";
        if (fullName.isEmpty()) return "Row " + rowNum + ": the full name is missing.";
        if (fullName.length() > 150) return "Row " + rowNum + ": the full name is too long.";
        if (email.isEmpty()) return "Row " + rowNum + ": the email is missing.";
        if (email.length() > 254 || !SIMPLE_EMAIL.matcher(email).matches()) {
            return "Row " + rowNum + ": the email address is not valid.";
        }
        if (designation.length() > 100) return "Row " + rowNum + ": the designation is longer than 100 characters.";
        return null;
    }

    private TeacherEvalRow problemTeacher(int rowNumber, String message, Map<String, String> display) {
        return new TeacherEvalRow(new CsvPreviewRow(rowNumber, CsvRowStatus.PROBLEM, message, display), null, null, null, null, null);
    }

    // ---- Subjects Import ----

    private static class SubjectEvalRow {
        final int rowNumber;
        CsvRowStatus status;
        String message;
        final Map<String, String> display;
        final Subject existing;
        final String code;
        final String name;
        final SubjectKind kind;
        final short periodsInARow;
        final String runTogetherGroup;
        final String sharedPlace;

        SubjectEvalRow(
                int rowNumber,
                CsvRowStatus status,
                String message,
                Map<String, String> display,
                Subject existing,
                String code,
                String name,
                SubjectKind kind,
                short periodsInARow,
                String runTogetherGroup,
                String sharedPlace
        ) {
            this.rowNumber = rowNumber;
            this.status = status;
            this.message = message;
            this.display = display;
            this.existing = existing;
            this.code = code;
            this.name = name;
            this.kind = kind;
            this.periodsInARow = periodsInARow;
            this.runTogetherGroup = runTogetherGroup;
            this.sharedPlace = sharedPlace;
        }

        CsvPreviewRow toPreview() {
            return new CsvPreviewRow(rowNumber, status, message, display);
        }
    }

    @Transactional(readOnly = true)
    public CsvImportPreviewResponse previewSubjects(MultipartFile file, CsvImportMode mode) {
        List<SubjectEvalRow> evaluated = evaluateSubjects(file, mode == null ? CsvImportMode.ADD_ONLY : mode);
        return CsvImportPreviewResponse.from(evaluated.stream().map(SubjectEvalRow::toPreview).toList());
    }

    @Transactional
    public CsvImportResultResponse confirmSubjects(MultipartFile file, CsvImportMode mode) {
        CsvImportMode effectiveMode = mode == null ? CsvImportMode.ADD_ONLY : mode;
        UUID deptId = currentUser.departmentId();
        List<SubjectEvalRow> evaluated = evaluateSubjects(file, effectiveMode);

        int added = 0;
        int updated = 0;
        int skipped = 0;
        int problems = 0;

        for (SubjectEvalRow row : evaluated) {
            switch (row.status) {
                case NEW -> {
                    subjectRepository.save(new Subject(
                            deptId,
                            row.code,
                            row.name,
                            row.kind,
                            row.periodsInARow,
                            row.runTogetherGroup,
                            row.sharedPlace
                    ));
                    added++;
                }
                case UPDATE -> {
                    Subject existing = row.existing;
                    existing.setName(row.name);
                    existing.setKind(row.kind);
                    existing.setPeriodsInARow(row.periodsInARow);
                    existing.setRunTogetherGroup(row.runTogetherGroup);
                    existing.setSharedPlace(row.sharedPlace);
                    updated++;
                }
                case UNCHANGED -> skipped++;
                case PROBLEM -> {
                    skipped++;
                    problems++;
                }
            }
        }

        int total = evaluated.size();
        historyService.record(
                "IMPORT",
                currentUser.displayName() + " imported " + total + " subject row"
                        + (total == 1 ? "" : "s") + ": "
                        + added + " added, " + updated + " updated, " + skipped + " skipped."
        );

        String message = "Import complete: " + added + " added, " + updated + " updated, " + skipped + " skipped.";
        return new CsvImportResultResponse(added, updated, skipped, problems, message);
    }

    private List<SubjectEvalRow> evaluateSubjects(MultipartFile file, CsvImportMode mode) {
        UUID deptId = currentUser.departmentId();
        List<CsvImportSupport.ParsedRow> parsedRows = csvSupport.parse(file, SUBJECT_COLUMNS);

        Map<String, Subject> existingByCode = new HashMap<>();
        for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            existingByCode.put(s.getCode().toLowerCase(Locale.ROOT), s);
        }

        Set<String> seenCodes = new HashSet<>();
        List<SubjectEvalRow> results = new ArrayList<>();

        for (CsvImportSupport.ParsedRow row : parsedRows) {
            String code = row.get("subjectCode");
            String name = row.get("name");
            String rawKind = row.get("classification").toUpperCase(Locale.ROOT);
            String rawPeriods = row.get("periodsInARow");
            String rawGroup = row.get("runsTogetherGroup");
            String rawPlace = row.get("sharedPlace");

            Map<String, String> display = new LinkedHashMap<>();
            display.put("subjectCode", code);
            display.put("name", name);
            display.put("classification", rawKind);
            display.put("periodsInARow", rawPeriods.isEmpty() ? "1" : rawPeriods);
            display.put("runsTogetherGroup", rawGroup);
            display.put("sharedPlace", rawPlace);

            if (code.isEmpty()) {
                results.add(problemSubject(row.rowNumber(), "Row " + row.rowNumber() + ": the subject code is missing.", display));
                continue;
            }
            if (code.length() > 30) {
                results.add(problemSubject(row.rowNumber(), "Row " + row.rowNumber() + ": the subject code is longer than 30 characters.", display));
                continue;
            }
            if (name.isEmpty()) {
                results.add(problemSubject(row.rowNumber(), "Row " + row.rowNumber() + ": the subject name is missing.", display));
                continue;
            }
            if (name.length() > 150) {
                results.add(problemSubject(row.rowNumber(), "Row " + row.rowNumber() + ": the subject name is too long.", display));
                continue;
            }

            SubjectKind kind;
            if ("THEORY".equals(rawKind)) {
                kind = SubjectKind.THEORY;
            } else if ("LAB".equals(rawKind)) {
                kind = SubjectKind.LAB;
            } else {
                results.add(problemSubject(
                        row.rowNumber(),
                        "Row " + row.rowNumber() + ": classification must be THEORY or LAB.",
                        display
                ));
                continue;
            }

            short periods = 1;
            if (!rawPeriods.isEmpty()) {
                try {
                    int parsed = Integer.parseInt(rawPeriods);
                    if (parsed < 1 || parsed > 3) {
                        results.add(problemSubject(
                                row.rowNumber(),
                                "Row " + row.rowNumber() + ": lectures in a row must be 1, 2, or 3.",
                                display
                        ));
                        continue;
                    }
                    periods = (short) parsed;
                } catch (NumberFormatException ex) {
                    results.add(problemSubject(
                            row.rowNumber(),
                            "Row " + row.rowNumber() + ": lectures in a row must be 1, 2, or 3.",
                            display
                    ));
                    continue;
                }
            }

            String group = rawGroup.isEmpty() ? null : rawGroup;
            String place = rawPlace.isEmpty() ? null : rawPlace;
            String warningSuffix = "";

            if (kind == SubjectKind.THEORY) {
                if (group != null || place != null) {
                    warningSuffix = " (Lab group and shared place ignored for a theory subject.)";
                }
                group = null;
                place = null;
            } else {
                if (group != null && group.length() > 60) {
                    results.add(problemSubject(row.rowNumber(), "Row " + row.rowNumber() + ": group name is too long.", display));
                    continue;
                }
                if (place != null && place.length() > 80) {
                    results.add(problemSubject(row.rowNumber(), "Row " + row.rowNumber() + ": shared place name is too long.", display));
                    continue;
                }
            }

            String codeKey = code.toLowerCase(Locale.ROOT);
            if (!seenCodes.add(codeKey)) {
                results.add(problemSubject(
                        row.rowNumber(),
                        "Row " + row.rowNumber() + ": subject code " + code + " appears more than once in this file.",
                        display
                ));
                continue;
            }

            Subject existing = existingByCode.get(codeKey);
            if (existing != null) {
                if (mode == CsvImportMode.ADD_ONLY) {
                    results.add(new SubjectEvalRow(
                            row.rowNumber(), CsvRowStatus.UNCHANGED, "Already exists, skipped.", display,
                            existing, code, name, kind, periods, group, place
                    ));
                    continue;
                }

                boolean onlyNameChanged = existing.getKind() == kind
                        && existing.getPeriodsInARow() == periods
                        && equalsIgnoreCaseNullable(existing.getRunTogetherGroup(), group)
                        && equalsIgnoreCaseNullable(existing.getSharedPlace(), place);

                if (usageChecker.isSubjectUsedInSavedTimetable(existing.getId()) && !onlyNameChanged) {
                    results.add(problemSubject(
                            row.rowNumber(),
                            "Row " + row.rowNumber() + ": this subject is used in a saved timetable. Only its name can be changed.",
                            display
                    ));
                    continue;
                }

                boolean unchanged = existing.getName().equals(name) && onlyNameChanged;
                if (unchanged) {
                    results.add(new SubjectEvalRow(
                            row.rowNumber(), CsvRowStatus.UNCHANGED, "No changes.", display,
                            existing, code, name, kind, periods, group, place
                    ));
                } else {
                    results.add(new SubjectEvalRow(
                            row.rowNumber(), CsvRowStatus.UPDATE, "Will update subject." + warningSuffix, display,
                            existing, code, name, kind, periods, group, place
                    ));
                }
            } else {
                results.add(new SubjectEvalRow(
                        row.rowNumber(), CsvRowStatus.NEW, "Will be added." + warningSuffix, display,
                        null, code, name, kind, periods, group, place
                ));
            }
        }

        // Validate paired-lab groups across the resulting department state
        Map<String, Subject> resultingByCode = new LinkedHashMap<>();
        for (Map.Entry<String, Subject> entry : existingByCode.entrySet()) {
            if (entry.getValue().isActive()) {
                resultingByCode.put(entry.getKey(), entry.getValue());
            }
        }
        for (SubjectEvalRow row : results) {
            if (row.status == CsvRowStatus.NEW || row.status == CsvRowStatus.UPDATE) {
                Subject projected = new Subject(deptId, row.code, row.name, row.kind, row.periodsInARow, row.runTogetherGroup, row.sharedPlace);
                resultingByCode.put(row.code.toLowerCase(Locale.ROOT), projected);
            }
        }

        Map<String, List<Subject>> groups = new LinkedHashMap<>();
        for (Subject subject : resultingByCode.values()) {
            if (subject.getKind() == SubjectKind.LAB && subject.getRunTogetherGroup() != null) {
                String gKey = subject.getRunTogetherGroup().toLowerCase(Locale.ROOT);
                groups.computeIfAbsent(gKey, k -> new ArrayList<>()).add(subject);
            }
        }

        Map<String, String> groupProblemByKey = new HashMap<>();
        for (Map.Entry<String, List<Subject>> entry : groups.entrySet()) {
            List<Subject> members = entry.getValue();
            String displayGroupName = members.get(0).getRunTogetherGroup();
            List<String> problems = groupValidator.validateCompleteGroup(displayGroupName, members);
            if (!problems.isEmpty()) {
                groupProblemByKey.put(entry.getKey(), problems.get(0));
            }
        }

        for (SubjectEvalRow row : results) {
            if ((row.status == CsvRowStatus.NEW || row.status == CsvRowStatus.UPDATE) && row.runTogetherGroup != null) {
                String groupError = groupProblemByKey.get(row.runTogetherGroup.toLowerCase(Locale.ROOT));
                if (groupError != null) {
                    row.status = CsvRowStatus.PROBLEM;
                    row.message = "Row " + row.rowNumber + ": " + groupError;
                }
            }
        }

        return results;
    }

    private SubjectEvalRow problemSubject(int rowNumber, String message, Map<String, String> display) {
        return new SubjectEvalRow(rowNumber, CsvRowStatus.PROBLEM, message, display, null, null, null, null, (short) 1, null, null);
    }

    // ---- Who Can Teach What Import ----

    private record LinkEvalRow(CsvPreviewRow preview, UUID teacherId, UUID subjectId) {
    }

    @Transactional(readOnly = true)
    public CsvImportPreviewResponse previewTeacherSubjects(MultipartFile file) {
        List<LinkEvalRow> evaluated = evaluateTeacherSubjects(file);
        return CsvImportPreviewResponse.from(evaluated.stream().map(LinkEvalRow::preview).toList());
    }

    @Transactional
    public CsvImportResultResponse confirmTeacherSubjects(MultipartFile file) {
        List<LinkEvalRow> evaluated = evaluateTeacherSubjects(file);

        int added = 0;
        int skipped = 0;
        int problems = 0;

        for (LinkEvalRow row : evaluated) {
            switch (row.preview().status()) {
                case NEW -> {
                    teacherSubjectRepository.save(new TeacherSubject(row.teacherId(), row.subjectId()));
                    added++;
                }
                case UNCHANGED, UPDATE -> skipped++;
                case PROBLEM -> {
                    skipped++;
                    problems++;
                }
            }
        }

        int total = evaluated.size();
        historyService.record(
                "IMPORT",
                currentUser.displayName() + " imported " + total + " teacher-subject link"
                        + (total == 1 ? "" : "s") + ": "
                        + added + " added, " + skipped + " skipped."
        );

        String message = "Import complete: " + added + " added, " + skipped + " skipped.";
        return new CsvImportResultResponse(added, 0, skipped, problems, message);
    }

    private List<LinkEvalRow> evaluateTeacherSubjects(MultipartFile file) {
        UUID deptId = currentUser.departmentId();
        List<CsvImportSupport.ParsedRow> parsedRows = csvSupport.parse(file, LINK_COLUMNS);

        Map<String, Teacher> teachersByCode = new HashMap<>();
        for (Teacher t : teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            teachersByCode.put(t.getCode().toLowerCase(Locale.ROOT), t);
        }

        Map<String, Subject> subjectsByCode = new HashMap<>();
        for (Subject s : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            subjectsByCode.put(s.getCode().toLowerCase(Locale.ROOT), s);
        }

        Set<String> seenPairs = new HashSet<>();
        List<LinkEvalRow> results = new ArrayList<>();

        for (CsvImportSupport.ParsedRow row : parsedRows) {
            String facultyCode = row.get("facultyCode");
            String subjectCode = row.get("subjectCode");

            Map<String, String> display = new LinkedHashMap<>();
            display.put("facultyCode", facultyCode);
            display.put("subjectCode", subjectCode);

            if (facultyCode.isEmpty()) {
                results.add(new LinkEvalRow(
                        new CsvPreviewRow(row.rowNumber(), CsvRowStatus.PROBLEM,
                                "Row " + row.rowNumber() + ": the teacher code is missing.", display),
                        null, null
                ));
                continue;
            }
            if (subjectCode.isEmpty()) {
                results.add(new LinkEvalRow(
                        new CsvPreviewRow(row.rowNumber(), CsvRowStatus.PROBLEM,
                                "Row " + row.rowNumber() + ": the subject code is missing.", display),
                        null, null
                ));
                continue;
            }

            Teacher teacher = teachersByCode.get(facultyCode.toLowerCase(Locale.ROOT));
            if (teacher == null) {
                results.add(new LinkEvalRow(
                        new CsvPreviewRow(row.rowNumber(), CsvRowStatus.PROBLEM,
                                "Row " + row.rowNumber() + ": teacher code " + facultyCode + " was not found.", display),
                        null, null
                ));
                continue;
            }

            Subject subject = subjectsByCode.get(subjectCode.toLowerCase(Locale.ROOT));
            if (subject == null) {
                results.add(new LinkEvalRow(
                        new CsvPreviewRow(row.rowNumber(), CsvRowStatus.PROBLEM,
                                "Row " + row.rowNumber() + ": subject code " + subjectCode + " was not found.", display),
                        null, null
                ));
                continue;
            }

            String pairKey = teacher.getId() + "|" + subject.getId();
            if (!seenPairs.add(pairKey)) {
                results.add(new LinkEvalRow(
                        new CsvPreviewRow(row.rowNumber(), CsvRowStatus.UNCHANGED, "Duplicate row in file, skipped.", display),
                        teacher.getId(), subject.getId()
                ));
                continue;
            }

            if (teacherSubjectRepository.existsByTeacherIdAndSubjectId(teacher.getId(), subject.getId())) {
                results.add(new LinkEvalRow(
                        new CsvPreviewRow(row.rowNumber(), CsvRowStatus.UNCHANGED, "Already linked, skipped.", display),
                        teacher.getId(), subject.getId()
                ));
                continue;
            }

            results.add(new LinkEvalRow(
                    new CsvPreviewRow(row.rowNumber(), CsvRowStatus.NEW, "Will be added.", display),
                    teacher.getId(), subject.getId()
            ));
        }

        return results;
    }

    private boolean equalsIgnoreCaseNullable(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.toLowerCase(Locale.ROOT), b.toLowerCase(Locale.ROOT));
    }
}