package com.schedulix.leave;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HOD', 'TEACHER')")
    public LeaveResponse create(@Valid @RequestBody CreateLeaveRequest request) {
        return leaveService.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOD', 'TEACHER')")
    public List<LeaveResponse> list(
            @RequestParam(value = "status", required = false) LeaveStatus status,
            @RequestParam(value = "teacherId", required = false) UUID teacherId
    ) {
        return leaveService.list(status, teacherId);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('HOD')")
    public LeaveResponse accept(
            @PathVariable UUID id,
            @RequestBody(required = false) LeaveDecisionRequest request
    ) {
        return leaveService.accept(id, request);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('HOD')")
    public LeaveResponse reject(
            @PathVariable UUID id,
            @RequestBody(required = false) LeaveDecisionRequest request
    ) {
        return leaveService.reject(id, request);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('HOD', 'TEACHER')")
    public void cancel(@PathVariable UUID id) {
        leaveService.cancel(id);
    }
}