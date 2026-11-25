package com.schedulix.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EngineChecks {

    private EngineChecks() {
    }

    public static List<String> check(EngineInput in) {
        List<String> problems = new ArrayList<>();
        if (in == null || in.settings() == null) {
            problems.add("Timetable settings are missing.");
            return problems;
        }

        EngineSettings settings = in.settings();
        if (settings.workingDays() <= 0 || settings.lecturesPerDay() <= 0) {
            problems.add("Working days and lectures per day must both be at least 1.");
            return problems;
        }
        if (settings.maxPerTeacherPerDay() <= 0) {
            problems.add("Maximum lectures per teacher per day must be at least 1.");
            return problems;
        }

        TimeGrid grid = TimeGrid.from(settings);

        Map<String, EngineClass> classById = new LinkedHashMap<>();
        for (EngineClass c : in.classes()) {
            classById.put(c.id(), c);
        }

        Map<String, EngineSubject> subjectById = new LinkedHashMap<>();
        for (EngineSubject s : in.subjects()) {
            subjectById.put(s.id(), s);
        }

        Map<String, EngineTeacher> teacherById = new LinkedHashMap<>();
        for (EngineTeacher t : in.teachers()) {
            teacherById.put(t.id(), t);
        }

        Map<String, String> teacherChoiceByPair = new HashMap<>();
        for (TeacherChoice choice : in.choices()) {
            if (choice.teacherId() != null && !choice.teacherId().isBlank()) {
                teacherChoiceByPair.put(pairKey(choice.classId(), choice.subjectId()), choice.teacherId());
            }
        }

        // Index busy cells (deduplicated per owner/day/lecture)
        Map<String, Set<String>> busyTeacherSlots = new HashMap<>();
        Map<String, int[]> busyTeacherPerDay = new HashMap<>();
        for (BusyCell cell : in.busyTeachers()) {
            if (cell.owner() == null || !grid.isValidDay(cell.day()) || !grid.isValidLecture(cell.lecture())) {
                continue;
            }
            Set<String> slots = busyTeacherSlots.computeIfAbsent(cell.owner(), k -> new HashSet<>());
            if (slots.add(slotKey(cell.day(), cell.lecture()))) {
                busyTeacherPerDay.computeIfAbsent(cell.owner(), k -> new int[grid.workingDays()])[cell.day()]++;
            }
        }

        Map<String, Set<String>> busyPlaceSlots = new HashMap<>();
        for (BusyCell cell : in.busyPlaces()) {
            if (cell.owner() == null || cell.owner().isBlank()
                    || !grid.isValidDay(cell.day()) || !grid.isValidLecture(cell.lecture())) {
                continue;
            }
            String placeKey = normalizeKey(cell.owner());
            busyPlaceSlots.computeIfAbsent(placeKey, k -> new HashSet<>())
                    .add(slotKey(cell.day(), cell.lecture()));
        }

        // Group plan lines by class and validate basic plan / teacher / stretch rules
        Map<String, List<PlanLine>> planByClass = new LinkedHashMap<>();
        Map<String, PlanLine> planByPair = new HashMap<>();
        Map<String, Integer> teacherWeeklyLoad = new LinkedHashMap<>();
        Map<String, Integer> placeWeeklyLoad = new LinkedHashMap<>();
        Map<String, String> placeDisplayName = new HashMap<>();
        Set<Integer> reportedOversizedBlocks = new HashSet<>();

        for (PlanLine line : in.plan()) {
            EngineClass cls = classById.get(line.classId());
            EngineSubject sub = subjectById.get(line.subjectId());
            if (cls == null || sub == null) {
                problems.add("A semester plan row points to an unknown class or subject.");
                continue;
            }

            planByClass.computeIfAbsent(cls.id(), k -> new ArrayList<>()).add(line);
            planByPair.put(pairKey(cls.id(), sub.id()), line);

            int blockSize = Math.max(1, sub.periodsInARow());
            if (line.lecturesPerWeek() <= 0 || line.lecturesPerWeek() % blockSize != 0) {
                problems.add(sub.name() + " in " + cls.name() + " needs a multiple of " + blockSize
                        + " lectures per week.");
            }

            // Check 4: block fits inside a stretch
            if (blockSize > grid.longestStretchLength() && reportedOversizedBlocks.add(blockSize)) {
                String label = sub.lab() ? "A lab of " + blockSize + " in a row" : sub.name() + " (" + blockSize + " in a row)";
                problems.add(label + " does not fit between breaks. The longest stretch is "
                        + grid.longestStretchLength() + " lectures.");
            }

            // Check 2: every (class, subject) has a valid teacher choice
            String chosenTeacherId = teacherChoiceByPair.get(pairKey(cls.id(), sub.id()));
            if (chosenTeacherId == null) {
                problems.add("Nobody is chosen for " + sub.name() + " in " + cls.name() + ".");
            } else {
                EngineTeacher teacher = teacherById.get(chosenTeacherId);
                if (teacher == null) {
                    problems.add("The chosen teacher for " + sub.name() + " in " + cls.name() + " was not found.");
                } else {
                    if (!teacher.ableSubjectIds().contains(sub.id())) {
                        problems.add(teacher.name() + " is not listed as able to teach " + sub.name() + ".");
                    }
                    teacherWeeklyLoad.merge(teacher.id(), line.lecturesPerWeek(), Integer::sum);
                }
            }

            // Accumulate shared place demand
            if (hasText(sub.placeName())) {
                String placeKey = normalizeKey(sub.placeName());
                placeDisplayName.putIfAbsent(placeKey, sub.placeName().trim());
                placeWeeklyLoad.merge(placeKey, line.lecturesPerWeek(), Integer::sum);
            }
        }

        // Check 1 & Check 5: class slot capacity and run-together group rules per class
        Map<String, List<PlanLine>> classGroupMembers = new HashMap<>();
        for (Map.Entry<String, List<PlanLine>> entry : planByClass.entrySet()) {
            EngineClass cls = classById.get(entry.getKey());
            List<PlanLine> lines = entry.getValue();

            Map<String, List<PlanLine>> groupsInClass = new LinkedHashMap<>();
            int classSlotsNeeded = 0;

            for (PlanLine line : lines) {
                EngineSubject sub = subjectById.get(line.subjectId());
                if (sub.lab() && hasText(sub.groupName())) {
                    String gKey = normalizeKey(sub.groupName());
                    groupsInClass.computeIfAbsent(gKey, k -> new ArrayList<>()).add(line);
                } else {
                    classSlotsNeeded += line.lecturesPerWeek();
                }
            }

            for (Map.Entry<String, List<PlanLine>> gEntry : groupsInClass.entrySet()) {
                List<PlanLine> members = gEntry.getValue();
                classGroupMembers.put(cls.id() + "|" + gEntry.getKey(), members);

                // A run-together group occupies one set of class slots, not one per lab
                int groupSlots = members.stream().mapToInt(PlanLine::lecturesPerWeek).max().orElse(0);
                classSlotsNeeded += groupSlots;

                validateClassGroup(cls, members, subjectById, teacherById, teacherChoiceByPair, problems);
            }

            if (classSlotsNeeded > grid.totalWeeklySlots()) {
                problems.add(cls.name() + " has " + classSlotsNeeded
                        + " lectures a week but the week only has " + grid.totalWeeklySlots() + " lecture slots.");
            }
        }

        // Check 3: teacher weekly free slots and daily maximum
        for (Map.Entry<String, Integer> entry : teacherWeeklyLoad.entrySet()) {
            String teacherId = entry.getKey();
            int needed = entry.getValue();
            EngineTeacher teacher = teacherById.get(teacherId);

            int busyCount = busyTeacherSlots.getOrDefault(teacherId, Set.of()).size();
            int freeSlots = Math.max(0, grid.totalWeeklySlots() - busyCount);
            if (needed > freeSlots) {
                problems.add(teacher.name() + " has " + needed + " lectures a week but only "
                        + freeSlots + " slots are free.");
                continue;
            }

            int[] busyPerDay = busyTeacherPerDay.getOrDefault(teacherId, new int[grid.workingDays()]);
            int maxUnderDailyCap = 0;
            for (int d = 0; d < grid.workingDays(); d++) {
                int dayFree = Math.max(0, grid.lecturesPerDay() - busyPerDay[d]);
                int dayCapRemaining = Math.max(0, settings.maxPerTeacherPerDay() - busyPerDay[d]);
                maxUnderDailyCap += Math.min(dayFree, dayCapRemaining);
            }

            if (needed > maxUnderDailyCap) {
                problems.add(teacher.name() + " has " + needed
                        + " lectures a week, which is more than the daily limit of "
                        + settings.maxPerTeacherPerDay() + " allows (" + maxUnderDailyCap + " slots available).");
            }
        }

        // Check 6: shared place capacity
        for (Map.Entry<String, Integer> entry : placeWeeklyLoad.entrySet()) {
            String placeKey = entry.getKey();
            int needed = entry.getValue();
            int busyCount = busyPlaceSlots.getOrDefault(placeKey, Set.of()).size();
            int freeSlots = Math.max(0, grid.totalWeeklySlots() - busyCount);
            if (needed > freeSlots) {
                problems.add(placeDisplayName.get(placeKey) + " needs " + needed
                        + " lectures a week across classes, but only " + freeSlots + " slots are free.");
            }
        }

        // Check 7: fixed lectures
        checkFixedLectures(
                in.fixed(),
                settings,
                grid,
                classById,
                subjectById,
                teacherById,
                planByPair,
                classGroupMembers,
                teacherChoiceByPair,
                busyTeacherSlots,
                busyTeacherPerDay,
                busyPlaceSlots,
                problems
        );

        return problems;
    }

    public static List<String> verify(EngineInput in, List<PlacedLecture> lectures) {
        List<String> problems = new ArrayList<>();
        if (in == null || in.settings() == null) {
            problems.add("Timetable settings are missing.");
            return problems;
        }
        if (lectures == null) {
            problems.add("No lectures were provided to verify.");
            return problems;
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
        Map<String, EngineTeacher> teacherById = new LinkedHashMap<>();
        for (EngineTeacher t : in.teachers()) {
            teacherById.put(t.id(), t);
        }
        Map<String, String> choiceByPair = new HashMap<>();
        for (TeacherChoice c : in.choices()) {
            choiceByPair.put(pairKey(c.classId(), c.subjectId()), c.teacherId());
        }
        Map<String, PlanLine> planByPair = new LinkedHashMap<>();
        Map<String, Set<String>> expectedGroupSubjects = new HashMap<>();
        for (PlanLine line : in.plan()) {
            planByPair.put(pairKey(line.classId(), line.subjectId()), line);
            EngineSubject sub = subjectById.get(line.subjectId());
            if (sub != null && sub.lab() && hasText(sub.groupName())) {
                String key = line.classId() + "|" + normalizeKey(sub.groupName());
                expectedGroupSubjects.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(sub.id());
            }
        }

        // Busy cells
        Set<String> busyTeacherCells = new HashSet<>();
        Map<String, int[]> teacherDayTotal = new HashMap<>();
        for (BusyCell cell : in.busyTeachers()) {
            if (cell.owner() == null || !grid.isValidDay(cell.day()) || !grid.isValidLecture(cell.lecture())) {
                continue;
            }
            if (busyTeacherCells.add(cell.owner() + "|" + slotKey(cell.day(), cell.lecture()))) {
                teacherDayTotal.computeIfAbsent(cell.owner(), k -> new int[grid.workingDays()])[cell.day()]++;
            }
        }

        Set<String> busyPlaceCells = new HashSet<>();
        for (BusyCell cell : in.busyPlaces()) {
            if (hasText(cell.owner()) && grid.isValidDay(cell.day()) && grid.isValidLecture(cell.lecture())) {
                busyPlaceCells.add(normalizeKey(cell.owner()) + "|" + slotKey(cell.day(), cell.lecture()));
            }
        }

        Map<String, Integer> placedLecturesByPair = new HashMap<>();
        Map<String, Map<Integer, Integer>> placedPiecesByPairDay = new HashMap<>();
        Map<String, List<PlacedLecture>> classCellOccupancy = new HashMap<>();
        Set<String> teacherSlotOccupancy = new HashSet<>();
        Set<String> placeSlotOccupancy = new HashSet<>();
        Map<String, Set<String>> groupSlotSubjects = new HashMap<>();
        Set<String> placedExactKeys = new HashSet<>();

        for (PlacedLecture lec : lectures) {
            EngineClass cls = classById.get(lec.classId());
            EngineSubject sub = subjectById.get(lec.subjectId());
            EngineTeacher teacher = teacherById.get(lec.teacherId());

            if (cls == null || sub == null || teacher == null) {
                problems.add("A placed lecture references an unknown class, subject, or teacher.");
                continue;
            }

            String pKey = pairKey(cls.id(), sub.id());
            if (!planByPair.containsKey(pKey)) {
                problems.add(sub.name() + " in " + cls.name() + " is not in the semester plan.");
            }

            String expectedTeacher = choiceByPair.get(pKey);
            if (expectedTeacher == null || !expectedTeacher.equals(teacher.id())) {
                problems.add(sub.name() + " in " + cls.name() + " was assigned to " + teacher.name()
                        + " instead of the chosen teacher.");
            }

            int expectedLen = Math.max(1, sub.periodsInARow());
            if (lec.length() != expectedLen) {
                problems.add(sub.name() + " in " + cls.name() + " has length " + lec.length()
                        + " instead of " + expectedLen + ".");
            }

            if (!grid.isValidDay(lec.day()) || !grid.isValidStart(lec.startLecture(), lec.length())) {
                problems.add(sub.name() + " in " + cls.name() + " on Day " + (lec.day() + 1)
                        + " crosses a break or falls outside working hours.");
                continue;
            }

            placedLecturesByPair.merge(pKey, lec.length(), Integer::sum);
            placedPiecesByPairDay
                    .computeIfAbsent(pKey, k -> new HashMap<>())
                    .merge(lec.day(), 1, Integer::sum);
            placedExactKeys.add(pKey + "@" + lec.day() + ":" + lec.startLecture());

            if (sub.lab() && hasText(sub.groupName())) {
                String groupSlotKey = cls.id() + "|" + normalizeKey(sub.groupName())
                        + "@" + lec.day() + ":" + lec.startLecture();
                groupSlotSubjects.computeIfAbsent(groupSlotKey, k -> new HashSet<>()).add(sub.id());
            }

            for (int off = 0; off < lec.length(); off++) {
                int slot = lec.startLecture() + off;
                String sKey = slotKey(lec.day(), slot);

                classCellOccupancy
                        .computeIfAbsent(cls.id() + "|" + sKey, k -> new ArrayList<>())
                        .add(lec);

                String tCell = teacher.id() + "|" + sKey;
                if (busyTeacherCells.contains(tCell)) {
                    problems.add(teacher.name() + " is already busy in another saved timetable on Day "
                            + (lec.day() + 1) + ", lecture " + slot + ".");
                }
                if (!teacherSlotOccupancy.add(tCell)) {
                    problems.add(teacher.name() + " is scheduled in two classes at once on Day "
                            + (lec.day() + 1) + ", lecture " + slot + ".");
                }
                teacherDayTotal.computeIfAbsent(teacher.id(), k -> new int[grid.workingDays()])[lec.day()]++;

                if (hasText(sub.placeName())) {
                    String pCell = normalizeKey(sub.placeName()) + "|" + sKey;
                    if (busyPlaceCells.contains(pCell)) {
                        problems.add(sub.placeName().trim() + " is already busy in another saved timetable on Day "
                                + (lec.day() + 1) + ", lecture " + slot + ".");
                    }
                    if (!placeSlotOccupancy.add(pCell)) {
                        problems.add(sub.placeName().trim() + " is double-booked on Day "
                                + (lec.day() + 1) + ", lecture " + slot + ".");
                    }
                }
            }
        }

        // Check class cell overlaps (only valid when all items in the cell belong to one paired-lab group)
        for (Map.Entry<String, List<PlacedLecture>> entry : classCellOccupancy.entrySet()) {
            List<PlacedLecture> cellItems = entry.getValue();
            if (cellItems.size() <= 1) continue;

            PlacedLecture first = cellItems.get(0);
            EngineSubject firstSub = subjectById.get(first.subjectId());
            String firstGroup = (firstSub != null && firstSub.lab() && hasText(firstSub.groupName()))
                    ? normalizeKey(firstSub.groupName())
                    : null;

            boolean validGroupOverlap = firstGroup != null;
            Set<String> seenSubIds = new HashSet<>();
            for (PlacedLecture item : cellItems) {
                EngineSubject sub = subjectById.get(item.subjectId());
                if (sub == null || !sub.lab() || !hasText(sub.groupName())
                        || !normalizeKey(sub.groupName()).equals(firstGroup)
                        || item.startLecture() != first.startLecture()
                        || item.length() != first.length()
                        || !seenSubIds.add(sub.id())) {
                    validGroupOverlap = false;
                    break;
                }
            }
            if (!validGroupOverlap) {
                EngineClass cls = classById.get(first.classId());
                problems.add((cls != null ? cls.name() : first.classId())
                        + " has overlapping lectures on Day " + (first.day() + 1) + ".");
            }
        }

        // Check every paired-lab slot contains all subjects of that group together
        for (Map.Entry<String, Set<String>> entry : groupSlotSubjects.entrySet()) {
            String key = entry.getKey();
            String classAndGroup = key.substring(0, key.indexOf('@'));
            Set<String> expected = expectedGroupSubjects.getOrDefault(classAndGroup, Set.of());
            if (!entry.getValue().equals(expected)) {
                problems.add("A paired lab group was split across different times instead of running together.");
            }
        }

        // Check exact weekly totals and per-day subject limits
        for (PlanLine line : in.plan()) {
            EngineClass cls = classById.get(line.classId());
            EngineSubject sub = subjectById.get(line.subjectId());
            if (cls == null || sub == null) continue;

            String pKey = pairKey(cls.id(), sub.id());
            int placedTotal = placedLecturesByPair.getOrDefault(pKey, 0);
            if (placedTotal != line.lecturesPerWeek()) {
                problems.add(sub.name() + " in " + cls.name() + " has " + placedTotal
                        + " placed lectures instead of " + line.lecturesPerWeek() + ".");
            }

            int blockSize = Math.max(1, sub.periodsInARow());
            int totalPieces = line.lecturesPerWeek() / blockSize;
            int maxPerDay = (int) Math.ceil((double) totalPieces / grid.workingDays());
            Map<Integer, Integer> perDay = placedPiecesByPairDay.getOrDefault(pKey, Map.of());
            for (Map.Entry<Integer, Integer> dayEntry : perDay.entrySet()) {
                if (dayEntry.getValue() > maxPerDay) {
                    problems.add(sub.name() + " in " + cls.name() + " appears " + dayEntry.getValue()
                            + " times on Day " + (dayEntry.getKey() + 1)
                            + " (maximum " + maxPerDay + " per day).");
                }
            }
        }

        // Check teacher daily maximums (including busy cells)
        for (Map.Entry<String, int[]> entry : teacherDayTotal.entrySet()) {
            EngineTeacher teacher = teacherById.get(entry.getKey());
            String name = teacher != null ? teacher.name() : entry.getKey();
            int[] perDay = entry.getValue();
            for (int d = 0; d < grid.workingDays(); d++) {
                if (perDay[d] > settings.maxPerTeacherPerDay()) {
                    problems.add(name + " has " + perDay[d] + " lectures on Day " + (d + 1)
                            + ", exceeding the daily maximum of " + settings.maxPerTeacherPerDay() + ".");
                }
            }
        }

        // Check all fixed lectures are honoured
        for (FixedLecture fl : in.fixed()) {
            String exact = pairKey(fl.classId(), fl.subjectId()) + "@" + fl.day() + ":" + fl.startLecture();
            if (!placedExactKeys.contains(exact)) {
                EngineClass cls = classById.get(fl.classId());
                EngineSubject sub = subjectById.get(fl.subjectId());
                String label = (sub != null ? sub.name() : fl.subjectId())
                        + " in " + (cls != null ? cls.name() : fl.classId());
                problems.add("Fixed lecture for " + label + " on Day " + (fl.day() + 1)
                        + ", lecture " + fl.startLecture() + " was moved or missing.");
            }
        }

        return problems;
    }

    private static void validateClassGroup(
            EngineClass cls,
            List<PlanLine> members,
            Map<String, EngineSubject> subjectById,
            Map<String, EngineTeacher> teacherById,
            Map<String, String> teacherChoiceByPair,
            List<String> problems
    ) {
        if (members.size() < 2) {
            EngineSubject only = subjectById.get(members.get(0).subjectId());
            problems.add(only.name() + " in " + cls.name() + " is in group " + only.groupName()
                    + " alone. Add the other lab in that group to the plan.");
            return;
        }

        PlanLine firstLine = members.get(0);
        EngineSubject firstSub = subjectById.get(firstLine.subjectId());

        for (int i = 1; i < members.size(); i++) {
            PlanLine currentLine = members.get(i);
            EngineSubject currentSub = subjectById.get(currentLine.subjectId());

            if (currentLine.lecturesPerWeek() != firstLine.lecturesPerWeek()) {
                problems.add(firstSub.name() + " has " + firstLine.lecturesPerWeek() + " lectures but "
                        + currentSub.name() + " has " + currentLine.lecturesPerWeek()
                        + " in " + cls.name() + ". Labs that run at the same time must be equal.");
            }
            if (currentSub.periodsInARow() != firstSub.periodsInARow()) {
                problems.add(firstSub.name() + " and " + currentSub.name()
                        + " run at the same time in " + cls.name()
                        + " but have different lectures in a row.");
            }
        }

        for (int i = 0; i < members.size(); i++) {
            EngineSubject subA = subjectById.get(members.get(i).subjectId());
            String teacherA = teacherChoiceByPair.get(pairKey(cls.id(), subA.id()));
            String placeA = hasText(subA.placeName()) ? normalizeKey(subA.placeName()) : null;

            for (int j = i + 1; j < members.size(); j++) {
                EngineSubject subB = subjectById.get(members.get(j).subjectId());
                String teacherB = teacherChoiceByPair.get(pairKey(cls.id(), subB.id()));
                String placeB = hasText(subB.placeName()) ? normalizeKey(subB.placeName()) : null;

                if (teacherA != null && teacherA.equals(teacherB)) {
                    EngineTeacher t = teacherById.get(teacherA);
                    String teacherName = t != null ? t.name() : "the same teacher";
                    problems.add(subA.name() + " and " + subB.name() + " run at the same time in "
                            + cls.name() + ", so " + teacherName + " cannot teach both.");
                }

                if (placeA != null && placeA.equals(placeB)) {
                    problems.add(subA.name() + " and " + subB.name() + " both use "
                            + subA.placeName().trim() + ", but they run at the same time in "
                            + cls.name() + ". Give them different places.");
                }
            }
        }
    }

    private static void checkFixedLectures(
            List<FixedLecture> fixedLectures,
            EngineSettings settings,
            TimeGrid grid,
            Map<String, EngineClass> classById,
            Map<String, EngineSubject> subjectById,
            Map<String, EngineTeacher> teacherById,
            Map<String, PlanLine> planByPair,
            Map<String, List<PlanLine>> classGroupMembers,
            Map<String, String> teacherChoiceByPair,
            Map<String, Set<String>> busyTeacherSlots,
            Map<String, int[]> busyTeacherPerDay,
            Map<String, Set<String>> busyPlaceSlots,
            List<String> problems
    ) {
        if (fixedLectures.isEmpty()) {
            return;
        }

        Set<String> seenNormalizedFixed = new LinkedHashSet<>();
        Map<String, Integer> fixedCountByPiece = new HashMap<>();
        Map<String, Map<Integer, Integer>> fixedCountByPieceDay = new HashMap<>();
        Map<String, String> classSlotOwner = new HashMap<>();
        Map<String, String> teacherSlotOwner = new HashMap<>();
        Map<String, String> placeSlotOwner = new HashMap<>();
        Map<String, int[]> fixedTeacherPerDay = new HashMap<>();

        for (FixedLecture fl : fixedLectures) {
            EngineClass cls = classById.get(fl.classId());
            EngineSubject sub = subjectById.get(fl.subjectId());
            if (cls == null || sub == null) {
                problems.add("A fixed lecture points to an unknown class or subject.");
                continue;
            }

            PlanLine planLine = planByPair.get(pairKey(cls.id(), sub.id()));
            if (planLine == null) {
                problems.add("Fixed lecture for " + sub.name() + " in " + cls.name()
                        + " is not in that class's semester plan.");
                continue;
            }

            boolean isGrouped = sub.lab() && hasText(sub.groupName());
            String pieceKey = isGrouped
                    ? cls.id() + "|G|" + normalizeKey(sub.groupName())
                    : cls.id() + "|S|" + sub.id();

            List<EngineSubject> pieceSubjects = new ArrayList<>();
            if (isGrouped) {
                List<PlanLine> members = classGroupMembers.getOrDefault(
                        cls.id() + "|" + normalizeKey(sub.groupName()), List.of(planLine)
                );
                for (PlanLine member : members) {
                    pieceSubjects.add(subjectById.get(member.subjectId()));
                }
            } else {
                pieceSubjects.add(sub);
            }

            int length = Math.max(1, sub.periodsInARow());
            String pieceLabel = isGrouped
                    ? sub.groupName().trim() + " in " + cls.name()
                    : sub.name() + " in " + cls.name();

            if (!seenNormalizedFixed.add(pieceKey + "@" + fl.day() + ":" + fl.startLecture())) {
                continue;
            }

            if (!grid.isValidDay(fl.day()) || !grid.isValidStart(fl.startLecture(), length)) {
                problems.add("Fixed lecture for " + pieceLabel
                        + " does not fit in a single stretch between breaks.");
                continue;
            }

            int totalPieces = planLine.lecturesPerWeek() / length;
            int fixedSoFar = fixedCountByPiece.merge(pieceKey, 1, Integer::sum);
            if (fixedSoFar > totalPieces) {
                problems.add("There are more fixed lectures for " + pieceLabel
                        + " (" + fixedSoFar + ") than its plan allows (" + totalPieces + ").");
            }

            int maxPerDay = (int) Math.ceil((double) totalPieces / grid.workingDays());
            int dayFixedCount = fixedCountByPieceDay
                    .computeIfAbsent(pieceKey, k -> new HashMap<>())
                    .merge(fl.day(), 1, Integer::sum);
            if (dayFixedCount > maxPerDay) {
                problems.add("Too many fixed lectures for " + pieceLabel
                        + " on Day " + (fl.day() + 1) + " (maximum " + maxPerDay + " per day).");
            }

            for (int offset = 0; offset < length; offset++) {
                int lecture = fl.startLecture() + offset;
                String sKey = slotKey(fl.day(), lecture);

                String classCellKey = cls.id() + "|" + sKey;
                String existingClassOwner = classSlotOwner.putIfAbsent(classCellKey, pieceLabel);
                if (existingClassOwner != null) {
                    problems.add("Fixed lectures clash for " + cls.name() + " on Day " + (fl.day() + 1)
                            + ", lecture " + lecture + " (" + existingClassOwner + " and " + pieceLabel + ").");
                }

                for (EngineSubject pieceSub : pieceSubjects) {
                    String teacherId = teacherChoiceByPair.get(pairKey(cls.id(), pieceSub.id()));
                    if (teacherId != null) {
                        EngineTeacher teacher = teacherById.get(teacherId);
                        String teacherName = teacher != null ? teacher.name() : teacherId;

                        if (busyTeacherSlots.getOrDefault(teacherId, Set.of()).contains(sKey)) {
                            problems.add(teacherName + " is already busy in another saved timetable on Day "
                                    + (fl.day() + 1) + ", lecture " + lecture
                                    + ", so the fixed lecture for " + pieceSub.name() + " cannot be placed there.");
                        }

                        String teacherCellKey = teacherId + "|" + sKey;
                        String existingTeacherOwner = teacherSlotOwner.putIfAbsent(teacherCellKey, pieceLabel);
                        if (existingTeacherOwner != null) {
                            problems.add("Fixed lectures put " + teacherName + " in two classes at once on Day "
                                    + (fl.day() + 1) + ", lecture " + lecture + ".");
                        }

                        fixedTeacherPerDay.computeIfAbsent(teacherId, k -> new int[grid.workingDays()])[fl.day()]++;
                    }

                    if (hasText(pieceSub.placeName())) {
                        String placeKey = normalizeKey(pieceSub.placeName());
                        if (busyPlaceSlots.getOrDefault(placeKey, Set.of()).contains(sKey)) {
                            problems.add(pieceSub.placeName().trim() + " is already used by another saved timetable on Day "
                                    + (fl.day() + 1) + ", lecture " + lecture + ".");
                        }

                        String placeCellKey = placeKey + "|" + sKey;
                        String existingPlaceOwner = placeSlotOwner.putIfAbsent(placeCellKey, pieceLabel);
                        if (existingPlaceOwner != null) {
                            problems.add("Fixed lectures double-book " + pieceSub.placeName().trim()
                                    + " on Day " + (fl.day() + 1) + ", lecture " + lecture + ".");
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, int[]> entry : fixedTeacherPerDay.entrySet()) {
            String teacherId = entry.getKey();
            int[] fixedPerDay = entry.getValue();
            int[] busyPerDay = busyTeacherPerDay.getOrDefault(teacherId, new int[grid.workingDays()]);
            EngineTeacher teacher = teacherById.get(teacherId);
            String teacherName = teacher != null ? teacher.name() : teacherId;

            for (int d = 0; d < grid.workingDays(); d++) {
                int total = fixedPerDay[d] + busyPerDay[d];
                if (total > settings.maxPerTeacherPerDay()) {
                    problems.add("Fixed lectures give " + teacherName + " " + total
                            + " lectures on Day " + (d + 1) + ", which exceeds the daily maximum of "
                            + settings.maxPerTeacherPerDay() + ".");
                }
            }
        }
    }

    private static String pairKey(String classId, String subjectId) {
        return classId + "|" + subjectId;
    }

    private static String slotKey(int day, int lecture) {
        return day + ":" + lecture;
    }

    private static String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}