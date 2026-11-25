package com.schedulix.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TeacherSuggesterTest {

    private final EngineSettings settings = new EngineSettings(5, 6, List.of(3), 4);

    @Test
    void assignsFewestAbleTeachersFirstAndBalancesWorkloadDeterministically() {
        // s1 can only be taught by t1; s2 can be taught by t1 or t2.
        // t1 must get s1 first, so t2 (lower load = 0) gets s2.
        EngineInput in = new EngineInput(
                settings,
                List.of(new EngineClass("c1", "Section A")),
                List.of(
                        new EngineSubject("s2", "Operating Systems", false, 1, null, null),
                        new EngineSubject("s1", "Compiler Design", false, 1, null, null)
                ),
                List.of(
                        new PlanLine("c1", "s2", 4),
                        new PlanLine("c1", "s1", 4)
                ),
                List.of(
                        new EngineTeacher("t1", "Prof. Arjun", Set.of("s1", "s2")),
                        new EngineTeacher("t2", "Prof. Neha", Set.of("s2"))
                ),
                List.of(), List.of(), List.of(), List.of()
        );

        Suggestion first = TeacherSuggester.suggest(in);
        Suggestion second = TeacherSuggester.suggest(in);

        assertThat(first.messages()).isEmpty();
        assertThat(first.choices()).isEqualTo(second.choices());

        Map<String, String> pickedBySubject = first.choices().stream()
                .collect(Collectors.toMap(TeacherChoice::subjectId, TeacherChoice::teacherId));
        assertThat(pickedBySubject.get("s1")).isEqualTo("t1");
        assertThat(pickedBySubject.get("s2")).isEqualTo("t2");
    }

    @Test
    void givesDifferentTeachersToLabsInSameRunTogetherGroup() {
        EngineInput in = new EngineInput(
                settings,
                List.of(new EngineClass("c1", "Section A")),
                List.of(
                        new EngineSubject("l1", "DSA Lab", true, 2, "LABGROUP-S3", "Computer Lab 1"),
                        new EngineSubject("l2", "Java Lab", true, 2, "LABGROUP-S3", "Computer Lab 2")
                ),
                List.of(
                        new PlanLine("c1", "l1", 2),
                        new PlanLine("c1", "l2", 2)
                ),
                List.of(
                        new EngineTeacher("t1", "Prof. A", Set.of("l1", "l2")),
                        new EngineTeacher("t2", "Prof. B", Set.of("l1", "l2"))
                ),
                List.of(), List.of(), List.of(), List.of()
        );

        Suggestion suggestion = TeacherSuggester.suggest(in);
        assertThat(suggestion.messages()).isEmpty();
        assertThat(suggestion.choices()).hasSize(2);

        String tForL1 = suggestion.choices().stream().filter(c -> c.subjectId().equals("l1")).findFirst().orElseThrow().teacherId();
        String tForL2 = suggestion.choices().stream().filter(c -> c.subjectId().equals("l2")).findFirst().orElseThrow().teacherId();
        assertThat(tForL1).isNotEqualTo(tForL2);
    }

    @Test
    void leavesEmptyWithPlainReasonWhenNobodyFitsCapacityOrGroup() {
        // 1 day, 4 lectures, max 2/day -> t1 can only take 2 lectures total.
        // s1 needs 2 (takes t1), s2 needs 2 (t1 is full), s3 has no able teachers.
        EngineSettings tiny = new EngineSettings(1, 4, List.of(2), 2);
        EngineInput in = new EngineInput(
                tiny,
                List.of(new EngineClass("c1", "Section A")),
                List.of(
                        new EngineSubject("s1", "Data Structures", false, 1, null, null),
                        new EngineSubject("s2", "Algorithms", false, 1, null, null),
                        new EngineSubject("s3", "Human Values", false, 1, null, null)
                ),
                List.of(
                        new PlanLine("c1", "s1", 2),
                        new PlanLine("c1", "s2", 2),
                        new PlanLine("c1", "s3", 1)
                ),
                List.of(new EngineTeacher("t1", "Prof. Rao", Set.of("s1", "s2"))),
                List.of(), List.of(), List.of(), List.of()
        );

        Suggestion suggestion = TeacherSuggester.suggest(in);
        assertThat(suggestion.choices()).hasSize(1);
        assertThat(suggestion.messages()).anySatisfy(m -> assertThat(m).contains("Human Values").contains("Nobody is listed"));
        assertThat(suggestion.messages()).anySatisfy(m -> assertThat(m).contains("enough free slots"));
    }

    @Test
    void reportsGroupConflictWhenOnlyOneTeacherCanTeachBothPairedLabs() {
        EngineInput in = new EngineInput(
                settings,
                List.of(new EngineClass("c1", "Section A")),
                List.of(
                        new EngineSubject("l1", "DBMS Lab", true, 2, "LABGROUP-S5", "Computer Lab 1"),
                        new EngineSubject("l2", "OS Lab", true, 2, "LABGROUP-S5", "Computer Lab 2")
                ),
                List.of(
                        new PlanLine("c1", "l1", 2),
                        new PlanLine("c1", "l2", 2)
                ),
                List.of(new EngineTeacher("t1", "Prof. Only", Set.of("l1", "l2"))),
                List.of(), List.of(), List.of(), List.of()
        );

        Suggestion suggestion = TeacherSuggester.suggest(in);
        assertThat(suggestion.choices()).hasSize(1);
        assertThat(suggestion.messages()).hasSize(1);
        assertThat(suggestion.messages().get(0)).contains("already assigned to another lab");
    }
}