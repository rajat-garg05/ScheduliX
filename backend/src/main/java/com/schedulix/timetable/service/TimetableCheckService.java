package com.schedulix.timetable.service;

import com.schedulix.auth.security.CurrentUser;
import com.schedulix.engine.EngineChecks;
import com.schedulix.engine.EngineInput;
import com.schedulix.engine.Suggestion;
import com.schedulix.engine.TeacherChoice;
import com.schedulix.engine.TeacherSuggester;
import com.schedulix.timetable.dto.TimetableCheckResponse.CheckProblem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.schedulix.timetable.entity.Timetable;
import com.schedulix.timetable.dto.TimetableCheckResponse;

@Service
public class TimetableCheckService {

    private final TimetableDraftService draftService;
    private final EngineInputBuilder engineInputBuilder;
    private final CurrentUser currentUser;

    public TimetableCheckService(
            TimetableDraftService draftService,
            EngineInputBuilder engineInputBuilder,
            CurrentUser currentUser
    ) {
        this.draftService = draftService;
        this.engineInputBuilder = engineInputBuilder;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public TimetableCheckResponse check(UUID timetableId) {
        Timetable tt = draftService.loadOwnedDraft(timetableId, currentUser.departmentId());
        EngineInput baseInput = engineInputBuilder.build(tt, true);

        Set<String> rawProblems = new LinkedHashSet<>();

        // If some (class, subject) pairs do not have a chosen teacher yet (normal in Step 3),
        // fill them temporarily with TeacherSuggester so we can validate teacher capacity and coverage.
        EngineInput inputForCheck = baseInput;
        if (baseInput.choices().size() < baseInput.plan().size()) {
            Suggestion suggestion = TeacherSuggester.suggest(baseInput);
            rawProblems.addAll(suggestion.messages());

            Map<String, TeacherChoice> merged = new HashMap<>();
            for (TeacherChoice c : suggestion.choices()) {
                merged.put(c.classId() + "|" + c.subjectId(), c);
            }
            for (TeacherChoice c : baseInput.choices()) {
                merged.put(c.classId() + "|" + c.subjectId(), c);
            }

            inputForCheck = new EngineInput(
                    baseInput.settings(),
                    baseInput.classes(),
                    baseInput.subjects(),
                    baseInput.plan(),
                    baseInput.teachers(),
                    new ArrayList<>(merged.values()),
                    baseInput.fixed(),
                    baseInput.busyTeachers(),
                    baseInput.busyPlaces()
            );
        }

        List<String> engineProblems = EngineChecks.check(inputForCheck);
        for (String problem : engineProblems) {
            // Avoid duplicating "Nobody is chosen..." when TeacherSuggester already gave the specific reason.
            if (problem.startsWith("Nobody is chosen for ") && !rawProblems.isEmpty()) {
                continue;
            }
            rawProblems.add(problem);
        }

        List<CheckProblem> problems = rawProblems.stream()
                .map(text -> new CheckProblem(text, resolveFixLink(text)))
                .toList();

        return new TimetableCheckResponse(problems.isEmpty(), problems);
    }

    private String resolveFixLink(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("saved timetable")) {
            return "/timetables";
        }
        if (lower.contains("between breaks") || lower.contains("longest stretch")) {
            return null;
        }
        if (lower.contains("able to teach") || lower.contains("free slots")
                || lower.contains("daily limit") || lower.contains("cannot teach both")
                || lower.contains("already assigned to another lab")) {
            return "/setup/teacher-subjects";
        }
        if (lower.contains("different places") || lower.contains("shared lab")
                || lower.contains("place")) {
            return "/setup/subjects";
        }
        if (lower.contains("lecture slots") || lower.contains("semester plan")
                || lower.contains("multiple of") || lower.contains("runs at the same time")) {
            return "/setup/semester-plans";
        }
        return null;
    }
}