package com.schedulix.leave;

import com.schedulix.auth.CurrentUser;
import com.schedulix.auth.UserRole;
import com.schedulix.common.AppException;
import com.schedulix.history.HistoryService;
import com.schedulix.leave.CreateLeaveRequest.SelectedLecture;
import com.schedulix.leave.LeaveResponse.LeaveLectureItem;
import com.schedulix.notice.NotificationService;
import com.schedulix.setup.Teacher;
import com.schedulix.setup.TeacherRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LeaveService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("d MMM");

    private final LeaveRepository leaveRepository;
    private final LeaveLectureRepository leaveLectureRepository;
    private final SubstituteRepository substituteRepository;
    private final TeacherRepository teacherRepository;
    private final SubstituteService substituteService;
    private final CurrentUser currentUser;
    private final NotificationService notificationService;
    private final HistoryService historyService;
    private final ZoneId departmentZone;

    public LeaveService(
            LeaveRepository leaveRepository,
            LeaveLectureRepository leaveLectureRepository,
            SubstituteRepository substituteRepository,
            TeacherRepository teacherRepository,
            SubstituteService substituteService,
            CurrentUser currentUser,
            NotificationService notificationService,
            HistoryService historyService,
            @Value("${app.timezone:Asia/Kolkata}") String timezone
    ) {
        this.leaveRepository = leaveRepository;
        this.leaveLectureRepository = leaveLectureRepository;
        this.substituteRepository = substituteRepository;
        this.teacherRepository = teacherRepository;
        this.substituteService = substituteService;
        this.currentUser = currentUser;
        this.notificationService = notificationService;
        this.historyService = historyService;
        this.departmentZone = ZoneId.of(timezone);
    }

    @Transactional
    public LeaveResponse create(CreateLeaveRequest request) {
        UUID deptId = currentUser.departmentId();
        boolean isHod = currentUser.role() == UserRole.HOD;

        if (request.toDate().isBefore(request.fromDate())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATES",
                    "The end date cannot be before the start date."
            );
        }

        LocalDate today = LocalDate.now(departmentZone);
        if (!isHod && request.fromDate().isBefore(today)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "PAST_DATE",
                    "You can only ask for leave for today or a future date."
            );
        }

        Teacher teacher;
        if (isHod) {
            if (request.teacherId() == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "TEACHER_REQUIRED", "Please pick a teacher.");
            }
            teacher = teacherRepository.findByIdAndDepartmentId(request.teacherId(), deptId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher not found."));
        } else {
            teacher = findTeacherForCurrentUser(deptId);
        }

        if (!teacher.isActive()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "TEACHER_INACTIVE",
                    teacher.getFullName() + " is inactive."
            );
        }

        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.length() > 300) {
            throw new AppException(HttpStatus.BAD_REQUEST, "REASON_TOO_LONG", "Reason must be 300 characters or fewer.");
        }

        List<SelectedLecture> selectedLectures = request.lectures() == null ? List.of() : request.lectures();
        if (request.scope() == LeaveScope.SOME_LECTURES && selectedLectures.isEmpty()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "NO_LECTURES_SELECTED",
                    "Please pick at least one lecture for partial-day leave."
            );
        }
        for (SelectedLecture sl : selectedLectures) {
            if (sl.lectureId() == null || sl.onDate() == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_LECTURE", "Each selected lecture needs a date.");
            }
            if (sl.onDate().isBefore(request.fromDate()) || sl.onDate().isAfter(request.toDate())) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "LECTURE_OUTSIDE_DATES",
                        "Selected lectures must fall within the leave dates."
                );
            }
        }

        LeaveStatus initialStatus = isHod ? LeaveStatus.ACCEPTED : LeaveStatus.ASKED;
        if (initialStatus == LeaveStatus.ACCEPTED) {
            ensureNoOverlappingAccepted(teacher, request.fromDate(), request.toDate(), null);
        }

        Leave saved = leaveRepository.save(new Leave(
                deptId,
                teacher.getId(),
                request.fromDate(),
                request.toDate(),
                request.scope(),
                reason,
                initialStatus,
                isHod ? currentUser.userId() : null,
                null,
                currentUser.userId(),
                Instant.now()
        ));

        List<LeaveLecture> savedLectures = new ArrayList<>();
        if (request.scope() == LeaveScope.SOME_LECTURES) {
            Set<String> seen = new LinkedHashSet<>();
            for (SelectedLecture sl : selectedLectures) {
                if (seen.add(sl.lectureId() + "|" + sl.onDate())) {
                    savedLectures.add(leaveLectureRepository.save(
                            new LeaveLecture(saved.getId(), sl.lectureId(), sl.onDate())
                    ));
                }
            }
        }

        if (saved.getStatus() == LeaveStatus.ACCEPTED) {
            substituteService.prepareForLeave(saved);
        }

        String rangeText = formatDateRange(saved.getFromDate(), saved.getToDate());
        if (isHod) {
            historyService.record("LEAVE", "Added leave for " + teacher.getFullName() + " (" + rangeText + ").");
            if (teacher.getUserAccountId() != null) {
                notificationService.send(
                        teacher.getUserAccountId(),
                        "Leave has been recorded for you (" + rangeText + ").",
                        "/leave"
                );
            }
        } else {
            historyService.record("LEAVE", teacher.getFullName() + " asked for leave (" + rangeText + ").");
            for (UUID hodUserId : leaveRepository.findHodUserIds(deptId)) {
                notificationService.send(
                        hodUserId,
                        teacher.getFullName() + " asked for leave (" + rangeText + ").",
                        "/leave"
                );
            }
        }

        return toResponse(saved, teacher, savedLectures);
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> list(LeaveStatus status, UUID teacherId) {
        UUID deptId = currentUser.departmentId();
        boolean isHod = currentUser.role() == UserRole.HOD;

        UUID effectiveTeacherId = teacherId;
        if (!isHod) {
            effectiveTeacherId = findTeacherForCurrentUser(deptId).getId();
        }

        Map<UUID, Teacher> teacherById = new HashMap<>();
        for (Teacher t : teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            teacherById.put(t.getId(), t);
        }

        List<LeaveResponse> result = new ArrayList<>();
        for (Leave leave : leaveRepository.findByDepartmentIdOrderByCreatedAtDesc(deptId)) {
            if (status != null && leave.getStatus() != status) continue;
            if (effectiveTeacherId != null && !leave.getTeacherId().equals(effectiveTeacherId)) continue;
            Teacher teacher = teacherById.get(leave.getTeacherId());
            if (teacher == null) continue;
            List<LeaveLecture> lectures = leaveLectureRepository.findByLeaveId(leave.getId());
            result.add(toResponse(leave, teacher, lectures));
        }
        return result;
    }

    @Transactional
    public LeaveResponse accept(UUID leaveId, LeaveDecisionRequest request) {
        UUID deptId = currentUser.departmentId();
        Leave leave = loadOwnedLeave(leaveId, deptId);
        if (leave.getStatus() != LeaveStatus.ASKED) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "LEAVE_ALREADY_DECIDED",
                    "This leave request has already been decided."
            );
        }

        Teacher teacher = teacherRepository.findByIdAndDepartmentId(leave.getTeacherId(), deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher not found."));

        ensureNoOverlappingAccepted(teacher, leave.getFromDate(), leave.getToDate(), leave.getId());

        String note = request != null && request.note() != null ? request.note().trim() : null;
        if (note != null && note.length() > 300) {
            throw new AppException(HttpStatus.BAD_REQUEST, "NOTE_TOO_LONG", "Note must be 300 characters or fewer.");
        }

        leave.setStatus(LeaveStatus.ACCEPTED);
        leave.setDecidedBy(currentUser.userId());
        leave.setDecisionNote(note);
        leaveRepository.save(leave);

        substituteService.prepareForLeave(leave);

        String rangeText = formatDateRange(leave.getFromDate(), leave.getToDate());
        historyService.record("LEAVE", "Accepted leave for " + teacher.getFullName() + " (" + rangeText + ").");
        if (teacher.getUserAccountId() != null) {
            String msg = "Your leave request (" + rangeText + ") was accepted."
                    + (note != null && !note.isBlank() ? " Note: " + note : "");
            notificationService.send(teacher.getUserAccountId(), msg, "/leave");
        }

        return toResponse(leave, teacher, leaveLectureRepository.findByLeaveId(leave.getId()));
    }

    @Transactional
    public LeaveResponse reject(UUID leaveId, LeaveDecisionRequest request) {
        UUID deptId = currentUser.departmentId();
        Leave leave = loadOwnedLeave(leaveId, deptId);
        if (leave.getStatus() != LeaveStatus.ASKED) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "LEAVE_ALREADY_DECIDED",
                    "This leave request has already been decided."
            );
        }

        Teacher teacher = teacherRepository.findByIdAndDepartmentId(leave.getTeacherId(), deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher not found."));

        String note = request != null && request.note() != null ? request.note().trim() : null;
        if (note != null && note.length() > 300) {
            throw new AppException(HttpStatus.BAD_REQUEST, "NOTE_TOO_LONG", "Note must be 300 characters or fewer.");
        }

        leave.setStatus(LeaveStatus.REJECTED);
        leave.setDecidedBy(currentUser.userId());
        leave.setDecisionNote(note);
        leaveRepository.save(leave);

        String rangeText = formatDateRange(leave.getFromDate(), leave.getToDate());
        historyService.record("LEAVE", "Rejected leave for " + teacher.getFullName() + " (" + rangeText + ").");
        if (teacher.getUserAccountId() != null) {
            String msg = "Your leave request (" + rangeText + ") was rejected."
                    + (note != null && !note.isBlank() ? " Note: " + note : "");
            notificationService.send(teacher.getUserAccountId(), msg, "/leave");
        }

        return toResponse(leave, teacher, leaveLectureRepository.findByLeaveId(leave.getId()));
    }

    @Transactional
    public void cancel(UUID leaveId) {
        UUID deptId = currentUser.departmentId();
        boolean isHod = currentUser.role() == UserRole.HOD;
        Leave leave = loadOwnedLeave(leaveId, deptId);

        Teacher teacher = teacherRepository.findByIdAndDepartmentId(leave.getTeacherId(), deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TEACHER_NOT_FOUND", "Teacher not found."));

        if (!isHod) {
            Teacher me = findTeacherForCurrentUser(deptId);
            if (!leave.getTeacherId().equals(me.getId())) {
                throw new AppException(HttpStatus.NOT_FOUND, "LEAVE_NOT_FOUND", "Leave not found.");
            }
            if (leave.getStatus() != LeaveStatus.ASKED) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "CANNOT_CANCEL_DECIDED_LEAVE",
                        "You can only cancel a leave request that has not been decided yet."
                );
            }
        }

        Map<UUID, Teacher> teacherById = new HashMap<>();
        for (Teacher t : teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            teacherById.put(t.getId(), t);
        }
        Set<UUID> notifiedSubUsers = new LinkedHashSet<>();
        for (Substitute sub : substituteRepository.findByLeaveId(leave.getId())) {
            if (sub.getStatus() == SubstituteStatus.ACCEPTED && sub.getSubstituteTeacherId() != null) {
                Teacher subTeacher = teacherById.get(sub.getSubstituteTeacherId());
                if (subTeacher != null && subTeacher.getUserAccountId() != null
                        && notifiedSubUsers.add(subTeacher.getUserAccountId())) {
                    notificationService.send(
                            subTeacher.getUserAccountId(),
                            "Your substitute arrangement for " + teacher.getFullName()
                                    + " on " + sub.getOnDate().format(DISPLAY_DATE) + " was cancelled.",
                            "/today"
                    );
                }
            }
        }

        String rangeText = formatDateRange(leave.getFromDate(), leave.getToDate());
        leaveRepository.delete(leave);
        leaveRepository.flush();

        if (isHod) {
            historyService.record("LEAVE", "Cancelled leave for " + teacher.getFullName() + " (" + rangeText + ").");
            if (teacher.getUserAccountId() != null) {
                notificationService.send(
                        teacher.getUserAccountId(),
                        "Your leave (" + rangeText + ") was cancelled by the HOD.",
                        "/leave"
                );
            }
        } else {
            historyService.record("LEAVE", teacher.getFullName() + " cancelled their leave request (" + rangeText + ").");
        }
    }

    Leave loadOwnedLeave(UUID leaveId, UUID deptId) {
        return leaveRepository.findByIdAndDepartmentId(leaveId, deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LEAVE_NOT_FOUND", "Leave not found."));
    }

    Teacher findTeacherForCurrentUser(UUID deptId) {
        UUID userId = currentUser.userId();
        return teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId).stream()
                .filter(t -> userId.equals(t.getUserAccountId()))
                .findFirst()
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "TEACHER_PROFILE_NOT_FOUND",
                        "No teacher profile is linked to your account."
                ));
    }

    private void ensureNoOverlappingAccepted(Teacher teacher, LocalDate fromDate, LocalDate toDate, UUID excludeId) {
        if (leaveRepository.existsOverlappingAccepted(teacher.getId(), fromDate, toDate, excludeId)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "LEAVE_OVERLAP",
                    teacher.getFullName() + " already has leave on these dates."
            );
        }
    }

    private String formatDateRange(LocalDate from, LocalDate to) {
        if (from.equals(to)) {
            return from.format(DISPLAY_DATE);
        }
        return from.format(DISPLAY_DATE) + " to " + to.format(DISPLAY_DATE);
    }

    private LeaveResponse toResponse(Leave leave, Teacher teacher, List<LeaveLecture> lectures) {
        List<LeaveLectureItem> items = lectures.stream()
                .map(ll -> new LeaveLectureItem(ll.getLectureId(), ll.getOnDate()))
                .toList();
        return new LeaveResponse(
                leave.getId(),
                teacher.getId(),
                teacher.getCode(),
                teacher.getFullName(),
                leave.getFromDate(),
                leave.getToDate(),
                leave.getScope(),
                leave.getReason(),
                leave.getStatus(),
                leave.getDecisionNote(),
                items,
                leave.getCreatedAt()
        );
    }
}