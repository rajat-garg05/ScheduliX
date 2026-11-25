package com.schedulix.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EngineChecksTest {

    private final EngineSettings defaultSettings = new EngineSettings(5, 6, List.of(3), 4);

    @Test
    void returnsEmptyListForValidSetup() {
        EngineInput in = new EngineInput(
                defaultSettings,
                List.of(new EngineClass("c1", "Section A")),
                List.of(
                        new EngineSubject("s1", "Data Structures", false, 1, null, null),
                        new EngineSubject("l1", "DSA Lab", true, 2, "GRP-S3", "Computer Lab 1"),
                        new EngineSubject("l2", "Java Lab", true, 2, "GRP-S3", "Computer Lab 2")
                ),
                List.of(
                        new PlanLine("c1", "s1", 4),
                        new PlanLine("c1", "l1", 2),
                        new PlanLine("c1", "l2", 2)
                ),
                List.of(
                        new EngineTeacher("t1", "Prof. Rao", Set.of("s1", "l1")),
                        new EngineTeacher("t2", "Prof. Sen", Set.of("l2"))
                ),
                List.of(
                        new TeacherChoice("c1", "s1", "t1"),
                        new TeacherChoice("c1", "l1", "t1"),
                        new TeacherChoice("c1", "l2", "t2")
                ),
                List.of(new FixedLecture("c1", "l1", 0, 1)),
                List.of(),
                List.of()
        );

        assertThat(EngineChecks.check(in)).isEmpty();
    }

    @Test
    void check1_catchesClassLecturesExceedingWeeklySlots() {
        EngineSettings smallWeek = new EngineSettings(5, 2, List.of(), 4); // 10 slots
        EngineInput in = new EngineInput(
                smallWeek,
                List.of(new EngineClass("c1", "Section A")),
                List.of(
                        new EngineSubject("s1", "Maths", false, 1, null, null),
                        new EngineSubject("s2", "Physics", false, 1, null, null)
                ),
                List.of(
                        new PlanLine("c1", "s1", 6),
                        new PlanLine("c1", "s2", 6)
                ),
                List.of(
                        new EngineTeacher("t1", "Prof. A", Set.of("s1")),
                        new EngineTeacher("t2", "Prof. B", Set.of("s2"))
                ),
                List.of(
                        new TeacherChoice("c1", "s1", "t1"),
                        new TeacherChoice("c1", "s2", "t2")
                ),
                List.of(), List.of(), List.of()
        );

        List<String> problems = EngineChecks.check(in);
        assertThat(problems).anySatisfy(p ->
                assertThat(p).contains("Section A has 12 lectures a week").contains("10 lecture slots"));
    }

    @Test
    void check2_catchesMissingTeacherChoice() {
        EngineInput in = new EngineInput(
                defaultSettings,
                List.of(new EngineClass("c1", "Section A")),
                List.of(new EngineSubject("s1", "Data Structures", false, 1, null, null)),
                List.of(new PlanLine("c1", "s1", 4)),
                List.of(new EngineTeacher("t1", "Prof. Rao", Set.of("s1"))),
                List.of(),
                List.of(), List.of(), List.of()
        );

        assertThat(EngineChecks.check(in))
                .containsExactly("Nobody is chosen for Data Structures in Section A.");
    }

    @Test
    void check3_catchesTeacherExceedingFreeSlotsAndDailyMax() {
        // 2 days * 4 lectures = 8 slots, max 3/day. Teacher has 2 busy cells on Day 0 -> max 1 on Day 0 + 3 on Day 1 = 4.
        EngineSettings settings = new EngineSettings(2, 4, List.of(2), 3);
        EngineInput in = new EngineInput(
                settings,
                List.of(new EngineClass("c1", "Section A")),
                List.of(new EngineSubject("s1", "OS", false, 1, null, null)),
                List.of(new PlanLine("c1", "s1", 5)),
                List.of(new EngineTeacher("t1", "Prof. Rao", Set.of("s1"))),
                List.of(new TeacherChoice("c1", "s1", "t1")),
                List.of(),
                List.of(new BusyCell("t1", 0, 1), new BusyCell("t1", 0, 2)),
                List.of()
        );

        assertThat(EngineChecks.check(in)).anySatisfy(p ->
                assertThat(p).contains("Prof. Rao").contains("daily limit of 3"));
    }

    @Test
    void check4_catchesBlockLongerThanAnyStretch() {
        // 4 lectures with break after 2 -> longest stretch is 2
        EngineSettings shortStretches = new EngineSettings(5, 4, List.of(2), 4);
        EngineInput in = new EngineInput(
                shortStretches,
                List.of(new EngineClass("c1", "Section A")),
                List.of(new EngineSubject("l1", "Hardware Lab", true, 3, null, null)),
                List.of(new PlanLine("c1", "l1", 3)),
                List.of(new EngineTeacher("t1", "Prof. Rao", Set.of("l1"))),
                List.of(new TeacherChoice("c1", "l1", "t1")),
                List.of(), List.of(), List.of()
        );

        assertThat(EngineChecks.check(in)).anySatisfy(p ->
                assertThat(p).contains("A lab of 3 in a row does not fit between breaks")
                        .contains("longest stretch is 2"));
    }

    @Test
    void check5_catchesRunTogetherGroupSameTeacherSamePlaceAndUnequalLectures() {
        EngineInput in = new EngineInput(
                defaultSettings,
                List.of(new EngineClass("c1", "Section A")),
                List.of(
                        new EngineSubject("l1", "DSA Lab", true, 2, "GRP-S3", "Computer Lab 1"),
                        new EngineSubject("l2", "Java Lab", true, 2, "GRP-S3", "Computer Lab 1")
                ),
                List.of(
                        new PlanLine("c1", "l1", 2),
                        new PlanLine("c1", "l2", 4)
                ),
                List.of(new EngineTeacher("t1", "Prof. Rao", Set.of("l1", "l2"))),
                List.of(
                        new TeacherChoice("c1", "l1", "t1"),
                        new TeacherChoice("c1", "l2", "t1")
                ),
                List.of(), List.of(), List.of()
        );

        List<String> problems = EngineChecks.check(in);
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("DSA Lab has 2 lectures").contains("Java Lab has 4"));
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("Prof. Rao cannot teach both"));
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("both use Computer Lab 1"));
    }

    @Test
    void check6_catchesSharedPlaceOverbooked() {
        // 1 day * 4 lectures = 4 slots; 2 busy cells leave 2 free slots, but 2 classes need 4 total lectures
        EngineSettings oneDay = new EngineSettings(1, 4, List.of(2), 4);
        EngineInput in = new EngineInput(
                oneDay,
                List.of(new EngineClass("c1", "Section A"), new EngineClass("c2", "Section B")),
                List.of(new EngineSubject("l1", "Networks Lab", true, 2, null, "Networks Lab")),
                List.of(
                        new PlanLine("c1", "l1", 2),
                        new PlanLine("c2", "l1", 2)
                ),
                List.of(
                        new EngineTeacher("t1", "Prof. A", Set.of("l1")),
                        new EngineTeacher("t2", "Prof. B", Set.of("l1"))
                ),
                List.of(
                        new TeacherChoice("c1", "l1", "t1"),
                        new TeacherChoice("c2", "l1", "t2")
                ),
                List.of(),
                List.of(),
                List.of(new BusyCell("Networks Lab", 0, 1), new BusyCell("Networks Lab", 0, 2))
        );

        assertThat(EngineChecks.check(in)).anySatisfy(p ->
                assertThat(p).contains("Networks Lab needs 4 lectures").contains("only 2 slots are free"));
    }

    @Test
    void check7_catchesFixedLectureBreakCrossingBusyClashAndExcessCount() {
        EngineInput in = new EngineInput(
                defaultSettings,
                List.of(new EngineClass("c1", "Section A")),
                List.of(
                        new EngineSubject("s1", "Maths", false, 1, null, null),
                        new EngineSubject("l1", "DSA Lab", true, 2, null, "Lab 1")
                ),
                List.of(
                        new PlanLine("c1", "s1", 1),
                        new PlanLine("c1", "l1", 2)
                ),
                List.of(new EngineTeacher("t1", "Prof. Rao", Set.of("s1", "l1"))),
                List.of(
                        new TeacherChoice("c1", "s1", "t1"),
                        new TeacherChoice("c1", "l1", "t1")
                ),
                List.of(
                        new FixedLecture("c1", "l1", 0, 3), // crosses lunch break between 3 and 4
                        new FixedLecture("c1", "s1", 1, 1), // teacher busy here
                        new FixedLecture("c1", "s1", 2, 1)  // exceeds 1 piece allowed
                ),
                List.of(new BusyCell("t1", 1, 1)),
                List.of()
        );

        List<String> problems = EngineChecks.check(in);
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("DSA Lab").contains("between breaks"));
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("Prof. Rao is already busy"));
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("more fixed lectures for Maths"));
    }
}