package com.schedulix.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EngineVariationAndScaleTest {

    @Test
    void sameVariationRepeatsAndDifferentVariationGivesDifferentValidTimetable() {
        EngineSettings settings = new EngineSettings(5, 6, List.of(3), 4);
        EngineInput in = new EngineInput(
                settings,
                List.of(new EngineClass("A", "Sem 3 - A"), new EngineClass("B", "Sem 3 - B")),
                List.of(
                        new EngineSubject("DS", "Data Structures", false, 1, null, null),
                        new EngineSubject("DM", "Discrete Maths", false, 1, null, null),
                        new EngineSubject("COA", "Computer Org", false, 1, null, null),
                        new EngineSubject("OOP", "Object Oriented", false, 1, null, null),
                        new EngineSubject("L1", "DSA Lab", true, 2, "GRP-S3", "Computer Lab 1"),
                        new EngineSubject("L2", "Java Lab", true, 2, "GRP-S3", "Computer Lab 2")
                ),
                List.of(
                        new PlanLine("A", "DS", 4), new PlanLine("A", "DM", 4),
                        new PlanLine("A", "COA", 3), new PlanLine("A", "OOP", 3),
                        new PlanLine("A", "L1", 2), new PlanLine("A", "L2", 2),
                        new PlanLine("B", "DS", 4), new PlanLine("B", "DM", 4),
                        new PlanLine("B", "COA", 3), new PlanLine("B", "OOP", 3),
                        new PlanLine("B", "L1", 2), new PlanLine("B", "L2", 2)
                ),
                List.of(
                        new EngineTeacher("T1", "Prof. A", Set.of("DS", "L1")),
                        new EngineTeacher("T2", "Prof. B", Set.of("DM", "OOP")),
                        new EngineTeacher("T3", "Prof. C", Set.of("COA", "L2")),
                        new EngineTeacher("T4", "Prof. D", Set.of("DS", "OOP", "L1", "L2"))
                ),
                List.of(
                        new TeacherChoice("A", "DS", "T1"), new TeacherChoice("A", "DM", "T2"),
                        new TeacherChoice("A", "COA", "T3"), new TeacherChoice("A", "OOP", "T4"),
                        new TeacherChoice("A", "L1", "T1"), new TeacherChoice("A", "L2", "T3"),
                        new TeacherChoice("B", "DS", "T4"), new TeacherChoice("B", "DM", "T2"),
                        new TeacherChoice("B", "COA", "T3"), new TeacherChoice("B", "OOP", "T4"),
                        new TeacherChoice("B", "L1", "T1"), new TeacherChoice("B", "L2", "T3")
                ),
                List.of(), List.of(), List.of()
        );

        EngineResult firstV0 = TimetableEngine.make(in, 0);
        EngineResult secondV0 = TimetableEngine.make(in, 0);
        EngineResult v1 = TimetableEngine.make(in, 1);
        EngineResult v2 = TimetableEngine.make(in, 2);

        assertThat(firstV0.ok()).isTrue();
        assertThat(secondV0.ok()).isTrue();
        assertThat(v1.ok()).isTrue();
        assertThat(v2.ok()).isTrue();

        assertThat(firstV0.lectures()).isEqualTo(secondV0.lectures());
        assertThat(EngineChecks.verify(in, firstV0.lectures())).isEmpty();
        assertThat(EngineChecks.verify(in, v1.lectures())).isEmpty();
        assertThat(EngineChecks.verify(in, v2.lectures())).isEmpty();

        boolean foundDifferent = !v1.lectures().equals(firstV0.lectures())
                || !v2.lectures().equals(firstV0.lectures());
        assertThat(foundDifferent).isTrue();
    }

    @Test
    void realisticDepartmentSpeedCheckLogsElapsedTimeWithoutFailingBuild() {
        // 12 classes (4 semesters x 3 sections), 21 class-slots/week each (252 class-slots, 276 lecture hours)
        EngineSettings settings = new EngineSettings(5, 6, List.of(3), 4);

        List<EngineClass> classes = new ArrayList<>();
        List<EngineSubject> subjects = new ArrayList<>();
        List<PlanLine> plan = new ArrayList<>();

        String[] labPlacesA = {"Computer Lab 1", "Computer Lab 2", "Hardware Lab", "Networks Lab"};
        String[] labPlacesB = {"Computer Lab 3", "Computer Lab 4", "AI Lab", "Systems Lab"};
        int[] semNumbers = {1, 3, 5, 7};
        String[] secNames = {"A", "B", "C"};

        for (int sIdx = 0; sIdx < semNumbers.length; sIdx++) {
            int sem = semNumbers[sIdx];
            String sub1 = "S" + sem + "_T1";
            String sub2 = "S" + sem + "_T2";
            String sub3 = "S" + sem + "_T3";
            String sub4 = "S" + sem + "_T4";
            String sub5 = "S" + sem + "_T5";
            String lab1 = "S" + sem + "_L1";
            String lab2 = "S" + sem + "_L2";
            String grp = "LABGROUP-S" + sem;

            subjects.add(new EngineSubject(sub1, "Sem " + sem + " Core 1", false, 1, null, null));
            subjects.add(new EngineSubject(sub2, "Sem " + sem + " Core 2", false, 1, null, null));
            subjects.add(new EngineSubject(sub3, "Sem " + sem + " Core 3", false, 1, null, null));
            subjects.add(new EngineSubject(sub4, "Sem " + sem + " Core 4", false, 1, null, null));
            subjects.add(new EngineSubject(sub5, "Sem " + sem + " Elective", false, 1, null, null));
            subjects.add(new EngineSubject(lab1, "Sem " + sem + " Lab 1", true, 2, grp, labPlacesA[sIdx]));
            subjects.add(new EngineSubject(lab2, "Sem " + sem + " Lab 2", true, 2, grp, labPlacesB[sIdx]));

            for (String sec : secNames) {
                String classId = "SEM" + sem + "_" + sec;
                classes.add(new EngineClass(classId, "Semester " + sem + " - " + sec));
                plan.add(new PlanLine(classId, sub1, 4));
                plan.add(new PlanLine(classId, sub2, 4));
                plan.add(new PlanLine(classId, sub3, 4));
                plan.add(new PlanLine(classId, sub4, 4));
                plan.add(new PlanLine(classId, sub5, 3));
                plan.add(new PlanLine(classId, lab1, 2));
                plan.add(new PlanLine(classId, lab2, 2));
            }
        }

        // 24 teachers, each able to teach several subjects across semesters
        List<EngineTeacher> teachers = new ArrayList<>();
        for (int t = 0; t < 24; t++) {
            Set<String> able = new HashSet<>();
            for (int i = 0; i < subjects.size(); i++) {
                if ((i + t) % 4 == 0 || (i + t) % 7 == 0) {
                    able.add(subjects.get(i).id());
                }
            }
            teachers.add(new EngineTeacher("T" + String.format("%02d", t + 1), "Prof. Teacher " + (t + 1), able));
        }

        EngineInput base = new EngineInput(settings, classes, subjects, plan, teachers, List.of(), List.of(), List.of(), List.of());
        Suggestion suggestion = TeacherSuggester.suggest(base);

        EngineInput fullInput = new EngineInput(
                settings, classes, subjects, plan, teachers, suggestion.choices(), List.of(), List.of(), List.of()
        );

        long startNanos = System.nanoTime();
        EngineResult result = TimetableEngine.make(fullInput, 0);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

        System.out.printf(
                "[Engine Speed Check] 12 classes, %d plan lines -> ok=%s, placed=%d, time=%d ms%n",
                plan.size(), result.ok(), result.lectures().size(), elapsedMs
        );

        // Log timing without failing the build on slow hardware; if solved, verify it is 100% valid.
        if (result.ok()) {
            assertThat(EngineChecks.verify(fullInput, result.lectures())).isEmpty();
        }
    }
}