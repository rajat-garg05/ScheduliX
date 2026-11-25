package com.schedulix.setup;

import com.schedulix.auth.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.common.PageResponse;
import com.schedulix.history.HistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final CurrentUser currentUser;
    private final HistoryService historyService;
    private final UsageChecker usageChecker;
    private final SubjectGroupValidator groupValidator;

    public SubjectService(
            SubjectRepository subjectRepository,
            CurrentUser currentUser,
            HistoryService historyService,
            UsageChecker usageChecker,
            SubjectGroupValidator groupValidator
    ) {
        this.subjectRepository = subjectRepository;
        this.currentUser = currentUser;
        this.historyService = historyService;
        this.usageChecker = usageChecker;
        this.groupValidator = groupValidator;
    }

    @Transactional(readOnly = true)
    public PageResponse<SubjectResponse> list(String search, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String cleanSearch = search == null ? "" : search.trim();

        Page<SubjectResponse> result = subjectRepository
                .searchByDepartment(currentUser.departmentId(), cleanSearch, PageRequest.of(safePage, safeSize))
                .map(SubjectResponse::from);
        return PageResponse.from(result);
    }

    @Transactional
    public SubjectResponse create(SubjectRequest request) {
        UUID deptId = currentUser.departmentId();
        String code = request.code().trim();
        String name = request.name().trim();
        SubjectKind kind = request.kind();
        short periods = normalizePeriods(request.periodsInARow());

        if (subjectRepository.existsByDepartmentIdAndCodeIgnoreCase(deptId, code)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SUBJECT_CODE_EXISTS",
                    "A subject with code " + code + " already exists in your department."
            );
        }

        List<String> warnings = new ArrayList<>();
        String group = cleanOptional(request.runTogetherGroup());
        String place = cleanOptional(request.sharedPlace());

        if (kind == SubjectKind.THEORY) {
            if (group != null || place != null) {
                warnings.add("Group name and shared lab or place apply to labs only, so they were ignored for this theory subject.");
            }
            group = null;
            place = null;
        } else if (group != null) {
            List<Subject> peers = subjectRepository.findByDepartmentIdAndRunTogetherGroupIgnoreCase(deptId, group);
            warnings.addAll(groupValidator.validateMemberAgainstPeers(null, name, periods, group, place, peers));
        }

        Subject subject = subjectRepository.save(new Subject(deptId, code, name, kind, periods, group, place));
        historyService.record("SUBJECT", "Added subject " + name + " (" + code + ").");
        return SubjectResponse.from(subject, warnings);
    }

    @Transactional
    public SubjectResponse update(UUID subjectId, SubjectRequest request) {
        UUID deptId = currentUser.departmentId();
        Subject subject = loadOwnedSubject(subjectId, deptId);

        String code = request.code().trim();
        String name = request.name().trim();
        SubjectKind kind = request.kind();
        short periods = normalizePeriods(request.periodsInARow());

        List<String> warnings = new ArrayList<>();
        String group = cleanOptional(request.runTogetherGroup());
        String place = cleanOptional(request.sharedPlace());

        if (kind == SubjectKind.THEORY) {
            if (group != null || place != null) {
                warnings.add("Group name and shared lab or place apply to labs only, so they were ignored for this theory subject.");
            }
            group = null;
            place = null;
        }

        if (usageChecker.isSubjectUsedInSavedTimetable(subjectId)) {
            boolean onlyNameChanged = subject.getCode().equalsIgnoreCase(code)
                    && subject.getKind() == kind
                    && subject.getPeriodsInARow() == periods
                    && equalsIgnoreCaseNullable(subject.getRunTogetherGroup(), group)
                    && equalsIgnoreCaseNullable(subject.getSharedPlace(), place);

            if (!onlyNameChanged) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "SUBJECT_IN_SAVED_TIMETABLE",
                        "This subject is used in a saved timetable. Only its name can be changed."
                );
            }

            subject.setName(name);
            historyService.record("SUBJECT", "Renamed subject " + subject.getCode() + " to " + name + ".");
            return SubjectResponse.from(subject, warnings);
        }

        subjectRepository.findByDepartmentIdAndCodeIgnoreCase(deptId, code)
                .filter(existing -> !existing.getId().equals(subjectId))
                .ifPresent(existing -> {
                    throw new AppException(
                            HttpStatus.CONFLICT,
                            "SUBJECT_CODE_EXISTS",
                            "A subject with code " + code + " already exists in your department."
                    );
                });

        if (kind == SubjectKind.LAB && group != null) {
            List<Subject> peers = subjectRepository.findByDepartmentIdAndRunTogetherGroupIgnoreCase(deptId, group);
            warnings.addAll(groupValidator.validateMemberAgainstPeers(subjectId, name, periods, group, place, peers));
        }

        subject.setCode(code);
        subject.setName(name);
        subject.setKind(kind);
        subject.setPeriodsInARow(periods);
        subject.setRunTogetherGroup(group);
        subject.setSharedPlace(place);

        historyService.record("SUBJECT", "Updated subject " + name + " (" + code + ").");
        return SubjectResponse.from(subject, warnings);
    }

    @Transactional
    public SubjectResponse deactivate(UUID subjectId) {
        Subject subject = loadOwnedSubject(subjectId, currentUser.departmentId());
        if (subject.isActive()) {
            subject.setActive(false);
            historyService.record("SUBJECT", "Deactivated subject " + subject.getName() + " (" + subject.getCode() + ").");
        }
        return SubjectResponse.from(subject);
    }

    @Transactional
    public SubjectResponse activate(UUID subjectId) {
        UUID deptId = currentUser.departmentId();
        Subject subject = loadOwnedSubject(subjectId, deptId);
        List<String> warnings = new ArrayList<>();

        if (!subject.isActive()) {
            if (subject.getKind() == SubjectKind.LAB && subject.getRunTogetherGroup() != null) {
                List<Subject> peers = subjectRepository.findByDepartmentIdAndRunTogetherGroupIgnoreCase(
                        deptId, subject.getRunTogetherGroup()
                );
                warnings.addAll(groupValidator.validateMemberAgainstPeers(
                        subject.getId(),
                        subject.getName(),
                        subject.getPeriodsInARow(),
                        subject.getRunTogetherGroup(),
                        subject.getSharedPlace(),
                        peers
                ));
            }
            subject.setActive(true);
            historyService.record("SUBJECT", "Activated subject " + subject.getName() + " (" + subject.getCode() + ").");
        }
        return SubjectResponse.from(subject, warnings);
    }

    @Transactional
    public void delete(UUID subjectId) {
        Subject subject = loadOwnedSubject(subjectId, currentUser.departmentId());
        if (usageChecker.isSubjectUsed(subjectId) || usageChecker.isSubjectUsedInSavedTimetable(subjectId)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SUBJECT_IN_USE",
                    "This subject is used in a semester plan or timetable and cannot be deleted. Deactivate it instead."
            );
        }

        String name = subject.getName();
        String code = subject.getCode();
        subjectRepository.delete(subject);
        historyService.record("SUBJECT", "Deleted subject " + name + " (" + code + ").");
    }

    private Subject loadOwnedSubject(UUID subjectId, UUID departmentId) {
        return subjectRepository.findByIdAndDepartmentId(subjectId, departmentId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SUBJECT_NOT_FOUND",
                        "Subject not found."
                ));
    }

    private short normalizePeriods(Integer periodsInARow) {
        int value = periodsInARow == null ? 1 : periodsInARow;
        if (value < 1 || value > 3) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PERIODS",
                    "Lectures in a row must be 1, 2, or 3."
            );
        }
        return (short) value;
    }

    private String cleanOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean equalsIgnoreCaseNullable(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.toLowerCase(), b.toLowerCase());
    }
}