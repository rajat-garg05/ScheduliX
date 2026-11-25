package com.schedulix.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TeacherSuggester {

    private TeacherSuggester() {
    }

    public static Suggestion suggest(EngineInput in) {
        if (in == null || in.settings() == null || in.plan().isEmpty()) {
            return new Suggestion(List.of(), List.of());
        }

        EngineSettings settings = in.settings();
        TimeGrid grid = TimeGrid.from(settings);

        Map<String, EngineClass> classById = new LinkedHashMap<>();
        for (EngineClass c : in.classes()) {
            classById.put(c.id(), c);
        }

        Map<String, EngineSubject> subjectById = new LinkedHashMap<>();
        for (EngineSubject s : in.subjects()) {
            subjectById.put(s.id(), s);
        }

        // Compute each teacher's weekly capacity considering busy cells and daily max
        Map<String, Set<String>> busySlotsByTeacher = new HashMap<>();
        Map<String, int[]> busyPerDayByTeacher = new HashMap<>();
        for (BusyCell cell : in.busyTeachers()) {
            if (cell.owner() == null || !grid.isValidDay(cell.day()) || !grid.isValidLecture(cell.lecture())) {
                continue;
            }
            Set<String> seen = busySlotsByTeacher.computeIfAbsent(cell.owner(), k -> new HashSet<>());
            if (seen.add(cell.day() + ":" + cell.lecture())) {
                busyPerDayByTeacher.computeIfAbsent(cell.owner(), k -> new int[grid.workingDays()])[cell.day()]++;
            }
        }

        Map<String, Integer> teacherCapacity = new HashMap<>();
        Map<String, Integer> teacherLoad = new HashMap<>();
        for (EngineTeacher t : in.teachers()) {
            int[] busyPerDay = busyPerDayByTeacher.getOrDefault(t.id(), new int[grid.workingDays()]);
            int cap = 0;
            for (int d = 0; d < grid.workingDays(); d++) {
                int dayFree = Math.max(0, grid.lecturesPerDay() - busyPerDay[d]);
                int dayCapLeft = Math.max(0, settings.maxPerTeacherPerDay() - busyPerDay[d]);
                cap += Math.min(dayFree, dayCapLeft);
            }
            teacherCapacity.put(t.id(), cap);
            teacherLoad.put(t.id(), 0);
        }

        // Index able teachers per subject
        Map<String, List<EngineTeacher>> ableBySubject = new HashMap<>();
        for (EngineTeacher t : in.teachers()) {
            for (String subId : t.ableSubjectIds()) {
                ableBySubject.computeIfAbsent(subId, k -> new ArrayList<>()).add(t);
            }
        }

        // Build sortable work items from valid plan lines
        List<PlanLine> workList = new ArrayList<>();
        for (PlanLine line : in.plan()) {
            if (classById.containsKey(line.classId()) && subjectById.containsKey(line.subjectId())) {
                workList.add(line);
            }
        }

        workList.sort(Comparator
                .comparingInt((PlanLine line) -> ableBySubject.getOrDefault(line.subjectId(), List.of()).size())
                .thenComparingInt(line -> {
                    EngineSubject s = subjectById.get(line.subjectId());
                    return (s.lab() && hasText(s.groupName())) ? 0 : 1;
                })
                .thenComparing(Comparator.comparingInt(PlanLine::lecturesPerWeek).reversed())
                .thenComparing(line -> classById.get(line.classId()).name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(line -> subjectById.get(line.subjectId()).name(), String.CASE_INSENSITIVE_ORDER));

        Map<String, Set<String>> usedTeachersByClassGroup = new HashMap<>();
        List<TeacherChoice> choices = new ArrayList<>();
        List<String> messages = new ArrayList<>();

        for (PlanLine line : workList) {
            EngineClass cls = classById.get(line.classId());
            EngineSubject sub = subjectById.get(line.subjectId());
            List<EngineTeacher> able = ableBySubject.getOrDefault(sub.id(), List.of());

            if (able.isEmpty()) {
                messages.add("Nobody is listed as able to teach " + sub.name() + " for " + cls.name() + ".");
                continue;
            }

            boolean isGrouped = sub.lab() && hasText(sub.groupName());
            String classGroupKey = isGrouped ? cls.id() + "|" + sub.groupName().trim().toLowerCase(Locale.ROOT) : null;
            Set<String> blockedByGroup = isGrouped
                    ? usedTeachersByClassGroup.getOrDefault(classGroupKey, Set.of())
                    : Set.of();

            List<EngineTeacher> candidates = new ArrayList<>();
            boolean blockedOnlyByGroup = true;

            for (EngineTeacher teacher : able) {
                if (blockedByGroup.contains(teacher.id())) {
                    continue;
                }
                blockedOnlyByGroup = false;
                int current = teacherLoad.getOrDefault(teacher.id(), 0);
                int cap = teacherCapacity.getOrDefault(teacher.id(), 0);
                if (current + line.lecturesPerWeek() <= cap) {
                    candidates.add(teacher);
                }
            }

            if (candidates.isEmpty()) {
                if (blockedOnlyByGroup) {
                    messages.add("All teachers able to teach " + sub.name() + " in " + cls.name()
                            + " are already assigned to another lab that runs at the same time.");
                } else {
                    messages.add("No able teacher has enough free slots for " + sub.name()
                            + " in " + cls.name() + " (" + line.lecturesPerWeek() + " lectures a week).");
                }
                continue;
            }

            candidates.sort(Comparator
                    .comparingInt((EngineTeacher t) -> teacherLoad.getOrDefault(t.id(), 0))
                    .thenComparing(EngineTeacher::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(EngineTeacher::id));

            EngineTeacher picked = candidates.get(0);
            teacherLoad.merge(picked.id(), line.lecturesPerWeek(), Integer::sum);
            if (isGrouped) {
                usedTeachersByClassGroup.computeIfAbsent(classGroupKey, k -> new HashSet<>()).add(picked.id());
            }
            choices.add(new TeacherChoice(cls.id(), sub.id(), picked.id()));
        }

        return new Suggestion(choices, messages);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}