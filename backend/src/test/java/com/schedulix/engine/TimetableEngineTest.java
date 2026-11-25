package com.schedulix.engine;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TimetableEngineTest {

    @Test
    void buildsValidMultiClassTimetableWithPairedLabsSharedPlacesBusyCellsAndFixedLectures() {
        EngineSettings settings = new EngineSettings(5, 6, List.of(3), 4);
        List<EngineClass> classes = List.of(
                new EngineClass("A", "Sem 3 - A"),
                new EngineClass("B", "Sem 3 - B")
        );
        List<EngineSubject> subjects = List.of(
                new EngineSubject("DS", "Data Structures", false, 1, null, null),
                new EngineSubject("DM", "Discrete Maths", false, 1, null, null),
                new EngineSubject("COA", "Computer Org", false, 1, null, null),
                new EngineSubject("DSA_LAB", "DSA Lab", true, 2, "LABGROUP-S3", "Computer Lab 1"),
                new EngineSubject("JAVA_LAB", "Java Lab", true, 2, "LABGROUP-S3", "Computer Lab 2")
        );
        List<PlanLine> plan = List.of(
                new PlanLine("A", "DS", 4),
                new PlanLine("A", "DM", 4),
                new PlanLine("A", "COA", 3),
                new PlanLine("A", "DSA_LAB", 2),
                new PlanLine("A", "JAVA_LAB", 2),
                new PlanLine("B", "DS", 4),
                new PlanLine("B", "DM", 4),
                new PlanLine("B", "COA", 3),
                new PlanLine("B", "DSA_LAB", 2),
                new PlanLine("B", "JAVA_LAB", 2)
        );
        List<EngineTeacher> teachers = List.of(
                new EngineTeacher("T1", "Prof. Arjun", Set.of("DS", "DSA_LAB")),
                new EngineTeacher("T2", "Prof. Meera", Set.of("DM")),
                new EngineTeacher("T3", "Prof. Rohit", Set.of("COA")),
                new EngineTeacher("T4", "Prof. Pooja", Set.of("JAVA_LAB", "DS"))
        );
        List<TeacherChoice> choices = List.of(
                new TeacherChoice("A", "DS", "T1"),
                new TeacherChoice("A", "DM", "T2"),
                new TeacherChoice("A", "COA", "T3"),
                new TeacherChoice("A", "DSA_LAB", "T1"),
                new TeacherChoice("A", "JAVA_LAB", "T4"),
                new TeacherChoice("B", "DS", "T4"),
                new TeacherChoice("B", "DM", "T2"),
                new TeacherChoice("B", "COA", "T3"),
                new TeacherChoice("B", "DSA_LAB", "T1"),
                new TeacherChoice("B", "JAVA_LAB", "T4")
        );
        // Pin Section A's paired lab to Day 0, lectures 1-2
        List<FixedLecture> fixed = List.of(new FixedLecture("A", "DSA_LAB", 0, 1));
        // T1 is busy on Day 1 lectures 1-2 in another saved timetable
        List<BusyCell> busyTeachers = List.of(new BusyCell("T1", 1, 1), new BusyCell("T1", 1, 2));
        // Computer Lab 1 is busy on Day 2 lectures 4-5 in another saved timetable
        List<BusyCell> busyPlaces = List.of(
                new BusyCell("Computer Lab 1", 2, 4),
                new BusyCell("Computer Lab 1", 2, 5)
        );

        EngineInput in = new EngineInput(settings, classes, subjects, plan, teachers, choices, fixed, busyTeachers, busyPlaces);
        EngineResult result = TimetableEngine.make(in, 0);

        assertThat(result.ok()).isTrue();
        assertThat(result.messages()).isEmpty();

        // Fixed paired lab for Section A is at Day 0, start 1, for both DSA_LAB and JAVA_LAB
        List<PlacedLecture> secALabs = result.lectures().stream()
                .filter(l -> l.classId().equals("A") && l.groupKey() != null)
                .toList();
        assertThat(secALabs).hasSize(2);
        assertThat(secALabs).allSatisfy(l -> {
            assertThat(l.day()).isEqualTo(0);
            assertThat(l.startLecture()).isEqualTo(1);
            assertThat(l.length()).isEqualTo(2);
            assertThat(l.groupKey()).isEqualTo("LABGROUP-S3");
        });

        // Section B's paired lab must NOT overlap Section A's shared labs (Day 0, 1-2) or busy Computer Lab 1 (Day 2, 4-5)
        List<PlacedLecture> secBLabs = result.lectures().stream()
                .filter(l -> l.classId().equals("B") && l.groupKey() != null)
                .toList();
        assertThat(secBLabs).hasSize(2);
        assertThat(secBLabs.get(0).day()).isEqualTo(secBLabs.get(1).day());
        assertThat(secBLabs.get(0).startLecture()).isEqualTo(secBLabs.get(1).startLecture());
        assertThat(secBLabs.get(0).day() == 0 && secBLabs.get(0).startLecture() == 1).isFalse();
        assertThat(secBLabs.get(0).day() == 2 && secBLabs.get(0).startLecture() == 4).isFalse();

        // Check no teacher clash, no busy-cell clash, no break crossing, and daily max <= 4 (including busy cells)
        TimeGrid grid = TimeGrid.from(settings);
        Set<String> teacherSlots = new HashSet<>();
        Map<String, Integer> teacherDayCounts = new HashMap<>();
        teacherDayCounts.put("T1|1", 2); // 2 busy cells on Day 1

        for (PlacedLecture lec : result.lectures()) {
            assertThat(grid.isValidStart(lec.startLecture(), lec.length())).isTrue();
            for (int off = 0; off < lec.length(); off++) {
                int slot = lec.startLecture() + off;
                assertThat(teacherSlots.add(lec.teacherId() + "|" + lec.day() + ":" + slot)).isTrue();
                assertThat(lec.teacherId().equals("T1") && lec.day() == 1 && (slot == 1 || slot == 2)).isFalse();
            }
            int updated = teacherDayCounts.merge(lec.teacherId() + "|" + lec.day(), lec.length(), Integer::sum);
            assertThat(updated).isLessThanOrEqualTo(4);
        }
    }

    @Test
    void enforcesPerDaySubjectLimit() {
        // 4 lectures over 5 days -> at most ceil(4/5) = 1 per day
        EngineSettings settings = new EngineSettings(5, 4, List.of(2), 4);
        EngineInput in = new EngineInput(
                settings,
                List.of(new EngineClass("c1", "Section A")),
                List.of(new EngineSubject("s1", "Maths", false, 1, null, null)),
                List.of(new PlanLine("c1", "s1", 4)),
                List.of(new EngineTeacher("t1", "Prof. Rao", Set.of("s1"))),
                List.of(new TeacherChoice("c1", "s1", "t1")),
                List.of(), List.of(), List.of()
        );

        EngineResult result = TimetableEngine.make(in, 0);
        assertThat(result.ok()).isTrue();
        assertThat(result.lectures()).hasSize(4);

        long distinctDays = result.lectures().stream().map(PlacedLecture::day).distinct().count();
        assertThat(distinctDays).isEqualTo(4);
    }

    @Test
    void impossibleCaseThatPassesPreChecksReturnsPlainExplanation() {
        // 1 day, 4 lectures with break after 2 -> only 2 valid 2-period lab starts (1 and 3).
        // 3 classes each need a 2-period lab in the same shared lab? Pre-check catches 6 > 4.
        // Instead: 2 classes each need a 2-period lab in Shared Lab 1, AND 1 busy cell at lecture 2
        // blocks the 1..2 stretch, leaving only stretch 3..4 (1 start) for 2 labs!
        // Weekly free slots = 4 - 1 = 3, and wait: 2 labs = 4 lectures > 3 (pre-check catches).
        // Use 2 days, 4 lectures (break after 2). Busy cell at Day 0 lecture 1 and Day 1 lecture 1
        // blocks the first stretch of BOTH days, leaving only 2 valid 2-period starts (Day 0 lec 3, Day 1 lec 3)
        // for 3 classes (6 lectures <= 6 free slots, so pre-check passes, search proves impossible).
        EngineSettings settings = new EngineSettings(2, 4, List.of(2), 4);
        EngineInput in = new EngineInput(
                settings,
                List.of(
                        new EngineClass("c1", "Section A"),
                        new EngineClass("c2", "Section B"),
                        new EngineClass("c3", "Section C")
                ),
                List.of(new EngineSubject("l1", "Networks Lab", true, 2, null, "Networks Lab")),
                List.of(
                        new PlanLine("c1", "l1", 2),
                        new PlanLine("c2", "l1", 2),
                        new PlanLine("c3", "l1", 2)
                ),
                List.of(
                        new EngineTeacher("t1", "Prof. A", Set.of("l1")),
                        new EngineTeacher("t2", "Prof. B", Set.of("l1")),
                        new EngineTeacher("t3", "Prof. C", Set.of("l1"))
                ),
                List.of(
                        new TeacherChoice("c1", "l1", "t1"),
                        new TeacherChoice("c2", "l1", "t2"),
                        new TeacherChoice("c3", "l1", "t3")
                ),
                List.of(),
                List.of(),
                List.of(new BusyCell("Networks Lab", 0, 1), new BusyCell("Networks Lab", 1, 1))
        );

        assertThat(EngineChecks.check(in)).isEmpty();
        EngineResult result = TimetableEngine.make(in, 0);
        assertThat(result.ok()).isFalse();
        assertThat(result.messages()).isNotEmpty();
        assertThat(result.messages().get(0)).contains("Networks Lab");
    }
}