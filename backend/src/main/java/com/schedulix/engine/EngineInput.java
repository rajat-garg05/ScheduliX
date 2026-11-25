package com.schedulix.engine;

import java.util.List;

public record EngineInput(
        EngineSettings settings,
        List<EngineClass> classes,
        List<EngineSubject> subjects,
        List<PlanLine> plan,
        List<EngineTeacher> teachers,
        List<TeacherChoice> choices,
        List<FixedLecture> fixed,
        List<BusyCell> busyTeachers,
        List<BusyCell> busyPlaces
) {
    public EngineInput {
        classes = classes == null ? List.of() : List.copyOf(classes);
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
        plan = plan == null ? List.of() : List.copyOf(plan);
        teachers = teachers == null ? List.of() : List.copyOf(teachers);
        choices = choices == null ? List.of() : List.copyOf(choices);
        fixed = fixed == null ? List.of() : List.copyOf(fixed);
        busyTeachers = busyTeachers == null ? List.of() : List.copyOf(busyTeachers);
        busyPlaces = busyPlaces == null ? List.of() : List.copyOf(busyPlaces);
    }
}