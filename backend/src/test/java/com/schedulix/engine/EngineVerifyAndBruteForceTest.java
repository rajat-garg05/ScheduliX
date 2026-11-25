package com.schedulix.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EngineVerifyAndBruteForceTest {

    @Test
    void verifyCatchesDeliberatelyBrokenTimetables() {
        EngineSettings settings = new EngineSettings(3, 4, List.of(2), 2);
        EngineInput in = new EngineInput(
                settings,
                List.of(new EngineClass("A", "Section A"), new EngineClass("B", "Section B")),
                List.of(
                        new EngineSubject("S1", "Maths", false, 1, null, null),
                        new EngineSubject("L1", "DSA Lab", true, 2, null, "Lab 1")
                ),
                List.of(
                        new PlanLine("A", "S1", 2),
                        new PlanLine("A", "L1", 2),
                        new PlanLine("B", "L1", 2)
                ),
                List.of(
                        new EngineTeacher("T1", "Prof. Rao", Set.of("S1", "L1")),
                        new EngineTeacher("T2", "Prof. Sen", Set.of("L1"))
                ),
                List.of(
                        new TeacherChoice("A", "S1", "T1"),
                        new TeacherChoice("A", "L1", "T1"),
                        new TeacherChoice("B", "L1", "T2")
                ),
                List.of(new FixedLecture("A", "S1", 0, 1)),
                List.of(new BusyCell("T1", 1, 1)),
                List.of(new BusyCell("Lab 1", 2, 1))
        );

        // 1. Break crossing (L1 starting at lecture 2 crosses break after 2) + missing fixed lecture
        List<PlacedLecture> broken1 = List.of(
                new PlacedLecture("A", "S1", "T1", 0, 2, 1, null),
                new PlacedLecture("A", "S1", "T1", 1, 2, 1, null),
                new PlacedLecture("A", "L1", "T1", 0, 2, 2, null),
                new PlacedLecture("B", "L1", "T2", 1, 3, 2, null)
        );
        List<String> err1 = EngineChecks.verify(in, broken1);
        assertThat(err1).anySatisfy(e -> assertThat(e).contains("crosses a break"));
        assertThat(err1).anySatisfy(e -> assertThat(e).contains("Fixed lecture"));

        // 2. Shared place double-booked + busy teacher cell + daily max exceeded
        List<PlacedLecture> broken2 = List.of(
                new PlacedLecture("A", "S1", "T1", 0, 1, 1, null),
                new PlacedLecture("A", "S1", "T1", 1, 1, 1, null), // T1 busy here AND makes T1 have 2 on Day 1
                new PlacedLecture("A", "L1", "T1", 0, 3, 2, null), // T1 now has 3 on Day 0 (> max 2)
                new PlacedLecture("B", "L1", "T2", 0, 3, 2, null)  // Lab 1 double-booked with A
        );
        List<String> err2 = EngineChecks.verify(in, broken2);
        assertThat(err2).anySatisfy(e -> assertThat(e).contains("Prof. Rao is already busy"));
        assertThat(err2).anySatisfy(e -> assertThat(e).contains("exceeding the daily maximum of 2"));
        assertThat(err2).anySatisfy(e -> assertThat(e).contains("Lab 1 is double-booked"));
    }

    @Test
    void matchesIndependentBruteForceSolverOnRandomTinyProblems() {
        Random rng = new Random(20261125L);
        EngineSettings settings = new EngineSettings(3, 4, List.of(2), 2);
        TimeGrid grid = TimeGrid.from(settings);

        int solvableCount = 0;
        int unsolvableCount = 0;

        for (int trial = 0; trial < 30; trial++) {
            EngineInput in = randomTinyProblem(rng, settings);

            EngineResult engineResult = TimetableEngine.make(in, 0);
            boolean bruteSolvable = solveBruteForce(in, grid);

            assertThat(engineResult.ok())
                    .as("Trial %d: engine ok=%s must match bruteForce=%s", trial, engineResult.ok(), bruteSolvable)
                    .isEqualTo(bruteSolvable);

            if (engineResult.ok()) {
                solvableCount++;
                assertThat(EngineChecks.verify(in, engineResult.lectures())).isEmpty();
            } else {
                unsolvableCount++;
                assertThat(engineResult.messages()).isNotEmpty();
            }
        }

        // Confirm our random generator actually tested both solvable and unsolvable cases
        assertThat(solvableCount).isGreaterThan(0);
        assertThat(unsolvableCount).isGreaterThan(0);
    }

    private EngineInput randomTinyProblem(Random rng, EngineSettings settings) {
        List<EngineClass> classes = List.of(
                new EngineClass("C1", "Section A"),
                new EngineClass("C2", "Section B")
        );
        List<EngineSubject> subjects = List.of(
                new EngineSubject("S1", "Maths", false, 1, null, null),
                new EngineSubject("S2", "Physics", false, 1, null, null),
                new EngineSubject("L1", "Lab", true, 2, null, "Shared Lab")
        );
        List<EngineTeacher> teachers = List.of(
                new EngineTeacher("T1", "Prof. One", Set.of("S1", "S2", "L1")),
                new EngineTeacher("T2", "Prof. Two", Set.of("S1", "S2", "L1"))
        );

        // 6 pieces total: C1 gets S1(2), L1(1 piece=2 lec); C2 gets S2(2), L1(1 piece=2 lec)
        List<PlanLine> plan = List.of(
                new PlanLine("C1", "S1", 2),
                new PlanLine("C1", "L1", 2),
                new PlanLine("C2", "S2", 2),
                new PlanLine("C2", "L1", 2)
        );

        List<TeacherChoice> choices = List.of(
                new TeacherChoice("C1", "S1", rng.nextBoolean() ? "T1" : "T2"),
                new TeacherChoice("C1", "L1", rng.nextBoolean() ? "T1" : "T2"),
                new TeacherChoice("C2", "S2", rng.nextBoolean() ? "T1" : "T2"),
                new TeacherChoice("C2", "L1", rng.nextBoolean() ? "T1" : "T2")
        );

        List<BusyCell> busyTeachers = new ArrayList<>();
        int busyTCount = rng.nextInt(4);
        for (int i = 0; i < busyTCount; i++) {
            busyTeachers.add(new BusyCell(rng.nextBoolean() ? "T1" : "T2", rng.nextInt(3), 1 + rng.nextInt(4)));
        }

        List<BusyCell> busyPlaces = new ArrayList<>();
        int busyPCount = rng.nextInt(4);
        for (int i = 0; i < busyPCount; i++) {
            busyPlaces.add(new BusyCell("Shared Lab", rng.nextInt(3), 1 + rng.nextInt(4)));
        }

        List<FixedLecture> fixed = new ArrayList<>();
        if (rng.nextInt(3) == 0) {
            fixed.add(new FixedLecture("C1", "S1", rng.nextInt(3), 1 + rng.nextInt(4)));
        }

        return new EngineInput(settings, classes, subjects, plan, teachers, choices, fixed, busyTeachers, busyPlaces);
    }

    // Plain recursive brute-force solver for tiny instances (shares no search code with TimetableEngine)
    private boolean solveBruteForce(EngineInput in, TimeGrid grid) {
        if (!EngineChecks.check(in).isEmpty()) {
            return false;
        }

        record MiniPiece(String classId, String subjectId, String teacherId, int len, String place, int fixedDay, int fixedStart) {}

        List<MiniPiece> pieces = new ArrayList<>();
        for (PlanLine line : in.plan()) {
            EngineSubject sub = in.subjects().stream().filter(s -> s.id().equals(line.subjectId())).findFirst().orElseThrow();
            String teacherId = in.choices().stream()
                    .filter(c -> c.classId().equals(line.classId()) && c.subjectId().equals(line.subjectId()))
                    .findFirst().orElseThrow().teacherId();
            int count = line.lecturesPerWeek() / sub.periodsInARow();
            List<FixedLecture> matchingFixed = in.fixed().stream()
                    .filter(f -> f.classId().equals(line.classId()) && f.subjectId().equals(line.subjectId()))
                    .toList();
            for (int i = 0; i < count; i++) {
                int fDay = i < matchingFixed.size() ? matchingFixed.get(i).day() : -1;
                int fStart = i < matchingFixed.size() ? matchingFixed.get(i).startLecture() : -1;
                pieces.add(new MiniPiece(line.classId(), line.subjectId(), teacherId, sub.periodsInARow(), sub.placeName(), fDay, fStart));
            }
        }

        List<PlacedLecture> current = new ArrayList<>();
        return bruteRecurse(in, grid, pieces, 0, current);
    }

    private boolean bruteRecurse(
            EngineInput in,
            TimeGrid grid,
            List<? extends Object> rawPieces,
            int idx,
            List<PlacedLecture> current
    ) {
        if (idx == rawPieces.size()) {
            return EngineChecks.verify(in, current).isEmpty();
        }

        // Read fields via reflection-free record access by casting through helper
        var p = rawPieces.get(idx);
        try {
            String classId = (String) p.getClass().getMethod("classId").invoke(p);
            String subjectId = (String) p.getClass().getMethod("subjectId").invoke(p);
            String teacherId = (String) p.getClass().getMethod("teacherId").invoke(p);
            int len = (int) p.getClass().getMethod("len").invoke(p);
            int fixedDay = (int) p.getClass().getMethod("fixedDay").invoke(p);
            int fixedStart = (int) p.getClass().getMethod("fixedStart").invoke(p);

            if (fixedDay >= 0) {
                PlacedLecture cand = new PlacedLecture(classId, subjectId, teacherId, fixedDay, fixedStart, len, null);
                if (!quickClash(cand, current)) {
                    current.add(cand);
                    if (bruteRecurse(in, grid, rawPieces, idx + 1, current)) return true;
                    current.remove(current.size() - 1);
                }
                return false;
            }

            List<Integer> starts = grid.allowedStartLectures(len);
            for (int d = 0; d < grid.workingDays(); d++) {
                for (int s : starts) {
                    PlacedLecture cand = new PlacedLecture(classId, subjectId, teacherId, d, s, len, null);
                    if (quickClash(cand, current)) continue;
                    current.add(cand);
                    if (bruteRecurse(in, grid, rawPieces, idx + 1, current)) return true;
                    current.remove(current.size() - 1);
                }
            }
            return false;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private boolean quickClash(PlacedLecture a, List<PlacedLecture> placed) {
        for (PlacedLecture b : placed) {
            if (a.day() != b.day()) continue;
            boolean overlap = a.startLecture() < b.startLecture() + b.length()
                    && b.startLecture() < a.startLecture() + a.length();
            if (!overlap) continue;
            if (a.classId().equals(b.classId())) return true;
            if (a.teacherId().equals(b.teacherId())) return true;
        }
        return false;
    }
}