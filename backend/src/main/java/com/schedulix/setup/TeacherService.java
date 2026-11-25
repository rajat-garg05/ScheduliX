package com.schedulix.setup;

import com.schedulix.auth.AccountService;
import com.schedulix.auth.CurrentUser;
import com.schedulix.auth.UserAccount;
import com.schedulix.auth.UserAccountRepository;
import com.schedulix.common.AppException;
import com.schedulix.common.PageResponse;
import com.schedulix.history.HistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountService accountService;
    private final CurrentUser currentUser;
    private final HistoryService historyService;
    private final UsageChecker usageChecker;

    public TeacherService(
            TeacherRepository teacherRepository,
            UserAccountRepository userAccountRepository,
            AccountService accountService,
            CurrentUser currentUser,
            HistoryService historyService,
            UsageChecker usageChecker
    ) {
        this.teacherRepository = teacherRepository;
        this.userAccountRepository = userAccountRepository;
        this.accountService = accountService;
        this.currentUser = currentUser;
        this.historyService = historyService;
        this.usageChecker = usageChecker;
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherResponse> list(String search, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String cleanSearch = search == null ? "" : search.trim();

        Page<TeacherResponse> result = teacherRepository
                .searchByDepartment(currentUser.departmentId(), cleanSearch, PageRequest.of(safePage, safeSize))
                .map(TeacherResponse::from);
        return PageResponse.from(result);
    }

    @Transactional
    public TeacherResponse create(TeacherRequest request) {
        UUID deptId = currentUser.departmentId();
        String code = request.code().trim();
        String fullName = request.fullName().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String designation = request.designation() == null ? "" : request.designation().trim();

        if (teacherRepository.existsByDepartmentIdAndCodeIgnoreCase(deptId, code)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "TEACHER_CODE_EXISTS",
                    "A teacher with code " + code + " already exists in your department."
            );
        }

        UserAccount account = accountService.createTeacherAccount(deptId, email, fullName);
        Teacher teacher = teacherRepository.save(
                new Teacher(deptId, code, fullName, email, designation, account.getId())
        );

        historyService.record("TEACHER", "Added teacher " + fullName + " (" + code + ").");
        return TeacherResponse.from(teacher);
    }

    @Transactional
    public TeacherResponse update(UUID teacherId, TeacherRequest request) {
        UUID deptId = currentUser.departmentId();
        Teacher teacher = loadOwnedTeacher(teacherId, deptId);

        String code = request.code().trim();
        String fullName = request.fullName().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String designation = request.designation() == null ? "" : request.designation().trim();

        teacherRepository.findByDepartmentIdAndCodeIgnoreCase(deptId, code)
                .filter(existing -> !existing.getId().equals(teacherId))
                .ifPresent(existing -> {
                    throw new AppException(
                            HttpStatus.CONFLICT,
                            "TEACHER_CODE_EXISTS",
                            "A teacher with code " + code + " already exists in your department."
                    );
                });

        if (!teacher.getEmail().equals(email)) {
            accountService.changeEmail(teacher.getUserAccountId(), email);
            teacher.setEmail(email);
        }
        if (!teacher.getFullName().equals(fullName)) {
            accountService.changeDisplayName(teacher.getUserAccountId(), fullName);
            teacher.setFullName(fullName);
        }

        teacher.setCode(code);
        teacher.setDesignation(designation);

        historyService.record("TEACHER", "Updated teacher " + fullName + " (" + code + ").");
        return TeacherResponse.from(teacher);
    }

    @Transactional
    public TeacherResponse deactivate(UUID teacherId) {
        Teacher teacher = loadOwnedTeacher(teacherId, currentUser.departmentId());
        if (teacher.isActive()) {
            teacher.setActive(false);
            accountService.disableAccount(teacher.getUserAccountId());
            historyService.record(
                    "TEACHER",
                    "Deactivated teacher " + teacher.getFullName() + " (" + teacher.getCode() + ")."
            );
        }
        return TeacherResponse.from(teacher);
    }

    @Transactional
    public TeacherResponse activate(UUID teacherId) {
        Teacher teacher = loadOwnedTeacher(teacherId, currentUser.departmentId());
        if (!teacher.isActive()) {
            teacher.setActive(true);
            accountService.enableAccount(teacher.getUserAccountId());
            historyService.record(
                    "TEACHER",
                    "Activated teacher " + teacher.getFullName() + " (" + teacher.getCode() + ")."
            );
        }
        return TeacherResponse.from(teacher);
    }

    @Transactional
    public void resetPassword(UUID teacherId) {
        Teacher teacher = loadOwnedTeacher(teacherId, currentUser.departmentId());
        accountService.resetToDefaultPassword(teacher.getUserAccountId());
        historyService.record(
                "TEACHER",
                "Reset password to default for " + teacher.getFullName() + " (" + teacher.getCode() + ")."
        );
    }

    @Transactional
    public void delete(UUID teacherId) {
        Teacher teacher = loadOwnedTeacher(teacherId, currentUser.departmentId());
        if (usageChecker.isTeacherUsed(teacherId)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "TEACHER_IN_USE",
                    "This teacher is used in a timetable or leave record and cannot be deleted. Deactivate them instead."
            );
        }

        String name = teacher.getFullName();
        String code = teacher.getCode();
        UUID accountId = teacher.getUserAccountId();

        teacherRepository.delete(teacher);
        teacherRepository.flush();
        userAccountRepository.deleteById(accountId);

        historyService.record("TEACHER", "Deleted teacher " + name + " (" + code + ").");
    }

    private Teacher loadOwnedTeacher(UUID teacherId, UUID departmentId) {
        return teacherRepository.findByIdAndDepartmentId(teacherId, departmentId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "TEACHER_NOT_FOUND",
                        "Teacher not found."
                ));
    }
}