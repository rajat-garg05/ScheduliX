package com.schedulix.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TimetableEngine {

    private TimetableEngine() {
    }

    public static EngineResult make(EngineInput in, int variation) {
        List<String> preCheckProblems = EngineChecks.check(in);
        if (!preCheckProblems.isEmpty()) {
            return new EngineResult(false, List.of(), preCheckProblems);
        }
        if (in.plan().isEmpty()) {
            return new EngineResult(true, List.of(), List.of());
        }

        SearchContext ctx = SearchContext.build(in, variation);
        if (!ctx.placeFixedPieces()) {
            return new EngineResult(false, List.of(), List.of(ctx.buildFailureMessage()));
        }

        boolean solved = ctx.solve();
        if (!solved) {
            return new EngineResult(false, List.of(), List.of(ctx.buildFailureMessage()));
        }

        List<PlacedLecture> placed = ctx.toPlacedLectures();
        List<String> verifyProblems = EngineChecks.verify(in, placed);
        if (!verifyProblems.isEmpty()) {
            return new EngineResult(false, List.of(), verifyProblems);
        }
        return new EngineResult(true, placed, List.of());
    }

    private record PieceMember(
            String subjectId,
            String teacherId,
            int teacherIdx,
            int placeIdx
    ) {
    }

    private static final class Piece {
        final int id;
        final String classId;
        final int classIdx;
        final int subjectGroupIdx;
        final String label;
        final int length;
        final int maxPerDay;
        final boolean grouped;
        final String groupKey;
        final List<PieceMember> members;
        final int[] teacherIndices;
        final int[] placeIndices;
        final boolean usesSharedPlace;
        final int[] allowedStarts;

        boolean fixed;
        int fixedDay = -1;
        int fixedStart = -1;

        boolean placed;
        int placedDay = -1;
        int placedStart = -1;

        int[] neighborIds = new int[0];

        Piece(
                int id,
                String classId,
                int classIdx,
                int subjectGroupIdx,
                String label,
                int length,
                int maxPerDay,
                boolean grouped,
                String groupKey,
                List<PieceMember> members,
                int[] allowedStarts
        ) {
            this.id = id;
            this.classId = classId;
            this.classIdx = classIdx;
            this.subjectGroupIdx = subjectGroupIdx;
            this.label = label;
            this.length = length;
            this.maxPerDay = maxPerDay;
            this.grouped = grouped;
            this.groupKey = groupKey;
            this.members = members;
            this.allowedStarts = allowedStarts;

            Set<Integer> tSet = new LinkedHashSet<>();
            Set<Integer> pSet = new LinkedHashSet<>();
            for (PieceMember m : members) {
                tSet.add(m.teacherIdx());
                if (m.placeIdx() >= 0) {
                    pSet.add(m.placeIdx());
                }
            }
            this.teacherIndices = tSet.stream().mapToInt(Integer::intValue).toArray();
            this.placeIndices = pSet.stream().mapToInt(Integer::intValue).toArray();
            this.usesSharedPlace = this.placeIndices.length > 0;
        }
    }

    private record SlotCandidate(int day, int startLecture, int sameSubjectDayCount, int gapScore, long tieHash) {
    }

    private static final class SearchContext {
        final int days;
        final int lecturesPerDay;
        final int maxTeacherPerDay;
        final int variation;

        final List<Piece> pieces;
        final boolean[][][] classBusy;     // [classIdx][day][lecture (1..N)]
        final boolean[][][] teacherBusy;   // [teacherIdx][day][lecture (1..N)]
        final boolean[][][] placeBusy;     // [placeIdx][day][lecture (1..N)]
        final int[][] teacherDayLoad;      // [teacherIdx][day] (includes busy cells)
        final int[][] subjectDayCount;     // [subjectGroupIdx][day]

        final int[] failCounts;
        int unplacedCount;

        private SearchContext(
                int days,
                int lecturesPerDay,
                int maxTeacherPerDay,
                int variation,
                List<Piece> pieces,
                boolean[][][] classBusy,
                boolean[][][] teacherBusy,
                boolean[][][] placeBusy,
                int[][] teacherDayLoad,
                int[][] subjectDayCount
        ) {
            this.days = days;
            this.lecturesPerDay = lecturesPerDay;
            this.maxTeacherPerDay = maxTeacherPerDay;
            this.variation = variation;
            this.pieces = pieces;
            this.classBusy = classBusy;
            this.teacherBusy = teacherBusy;
            this.placeBusy = placeBusy;
            this.teacherDayLoad = teacherDayLoad;
            this.subjectDayCount = subjectDayCount;
            this.failCounts = new int[pieces.size()];
            this.unplacedCount = pieces.size();
        }

        static SearchContext build(EngineInput in, int variation) {
            EngineSettings settings = in.settings();
            TimeGrid grid = TimeGrid.from(settings);
            int days = grid.workingDays();
            int perDay = grid.lecturesPerDay();

            Map<String, EngineClass> classById = new LinkedHashMap<>();
            Map<String, Integer> classIdxMap = new HashMap<>();
            for (EngineClass c : in.classes()) {
                classIdxMap.put(c.id(), classById.size());
                classById.put(c.id(), c);
            }

            Map<String, EngineSubject> subjectById = new LinkedHashMap<>();
            for (EngineSubject s : in.subjects()) {
                subjectById.put(s.id(), s);
            }

            Map<String, Integer> teacherIdxMap = new HashMap<>();
            for (EngineTeacher t : in.teachers()) {
                teacherIdxMap.put(t.id(), teacherIdxMap.size());
            }

            Map<String, Integer> placeIdxMap = new HashMap<>();
            for (EngineSubject s : in.subjects()) {
                if (hasText(s.placeName())) {
                    placeIdxMap.putIfAbsent(normalizeKey(s.placeName()), placeIdxMap.size());
                }
            }
            for (BusyCell cell : in.busyPlaces()) {
                if (hasText(cell.owner())) {
                    placeIdxMap.putIfAbsent(normalizeKey(cell.owner()), placeIdxMap.size());
                }
            }

            Map<String, String> choiceByPair = new HashMap<>();
            for (TeacherChoice c : in.choices()) {
                choiceByPair.put(pairKey(c.classId(), c.subjectId()), c.teacherId());
            }

            // Group plan lines into non-grouped subjects and paired-lab groups per class
            Map<String, List<PlanLine>> groupedPlan = new LinkedHashMap<>();
            List<PlanLine> singlePlans = new ArrayList<>();

            for (PlanLine line : in.plan()) {
                EngineSubject sub = subjectById.get(line.subjectId());
                if (sub.lab() && hasText(sub.groupName())) {
                    String key = line.classId() + "|" + normalizeKey(sub.groupName());
                    groupedPlan.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
                } else {
                    singlePlans.add(line);
                }
            }

            List<Piece> pieces = new ArrayList<>();
            Map<String, List<Piece>> piecesByLookupKey = new HashMap<>();
            int nextSubjectGroupIdx = 0;

            for (PlanLine line : singlePlans) {
                EngineClass cls = classById.get(line.classId());
                EngineSubject sub = subjectById.get(line.subjectId());
                int classIdx = classIdxMap.get(cls.id());
                String teacherId = choiceByPair.get(pairKey(cls.id(), sub.id()));
                int teacherIdx = teacherIdxMap.get(teacherId);
                int placeIdx = hasText(sub.placeName()) ? placeIdxMap.get(normalizeKey(sub.placeName())) : -1;

                int length = Math.max(1, sub.periodsInARow());
                int count = line.lecturesPerWeek() / length;
                int maxPerDay = (int) Math.ceil((double) count / days);
                int[] starts = grid.allowedStartLectures(length).stream().mapToInt(Integer::intValue).toArray();
                int subjectGroupIdx = nextSubjectGroupIdx++;

                List<PieceMember> members = List.of(new PieceMember(sub.id(), teacherId, teacherIdx, placeIdx));
                String lookupKey = cls.id() + "|S|" + sub.id();

                for (int i = 0; i < count; i++) {
                    Piece piece = new Piece(
                            pieces.size(),
                            cls.id(),
                            classIdx,
                            subjectGroupIdx,
                            sub.name() + " in " + cls.name(),
                            length,
                            maxPerDay,
                            false,
                            null,
                            members,
                            starts
                    );
                    pieces.add(piece);
                    piecesByLookupKey.computeIfAbsent(lookupKey, k -> new ArrayList<>()).add(piece);
                }
            }

            for (List<PlanLine> groupLines : groupedPlan.values()) {
                PlanLine first = groupLines.get(0);
                EngineClass cls = classById.get(first.classId());
                EngineSubject firstSub = subjectById.get(first.subjectId());
                int classIdx = classIdxMap.get(cls.id());

                int length = Math.max(1, firstSub.periodsInARow());
                int count = first.lecturesPerWeek() / length;
                int maxPerDay = (int) Math.ceil((double) count / days);
                int[] starts = grid.allowedStartLectures(length).stream().mapToInt(Integer::intValue).toArray();
                int subjectGroupIdx = nextSubjectGroupIdx++;

                List<PieceMember> members = new ArrayList<>();
                List<String> subNames = new ArrayList<>();
                for (PlanLine line : groupLines) {
                    EngineSubject sub = subjectById.get(line.subjectId());
                    String teacherId = choiceByPair.get(pairKey(cls.id(), sub.id()));
                    int teacherIdx = teacherIdxMap.get(teacherId);
                    int placeIdx = hasText(sub.placeName()) ? placeIdxMap.get(normalizeKey(sub.placeName())) : -1;
                    members.add(new PieceMember(sub.id(), teacherId, teacherIdx, placeIdx));
                    subNames.add(sub.name());
                }

                String groupKey = firstSub.groupName().trim();
                String label = String.join(" / ", subNames) + " in " + cls.name();
                String lookupKey = cls.id() + "|G|" + normalizeKey(groupKey);

                for (int i = 0; i < count; i++) {
                    Piece piece = new Piece(
                            pieces.size(),
                            cls.id(),
                            classIdx,
                            subjectGroupIdx,
                            label,
                            length,
                            maxPerDay,
                            true,
                            groupKey,
                            members,
                            starts
                    );
                    pieces.add(piece);
                    piecesByLookupKey.computeIfAbsent(lookupKey, k -> new ArrayList<>()).add(piece);
                }
            }

            // Attach fixed lectures to available pieces (deduplicated per piece group + slot)
            Set<String> seenFixedSlots = new HashSet<>();
            for (FixedLecture fl : in.fixed()) {
                EngineSubject sub = subjectById.get(fl.subjectId());
                if (sub == null) continue;
                boolean isGrouped = sub.lab() && hasText(sub.groupName());
                String lookupKey = isGrouped
                        ? fl.classId() + "|G|" + normalizeKey(sub.groupName())
                        : fl.classId() + "|S|" + sub.id();

                if (!seenFixedSlots.add(lookupKey + "@" + fl.day() + ":" + fl.startLecture())) {
                    continue;
                }

                List<Piece> candidates = piecesByLookupKey.getOrDefault(lookupKey, List.of());
                for (Piece p : candidates) {
                    if (!p.fixed) {
                        p.fixed = true;
                        p.fixedDay = fl.day();
                        p.fixedStart = fl.startLecture();
                        break;
                    }
                }
            }

            // Build conflict neighbor lists for fast forward checking
            for (int i = 0; i < pieces.size(); i++) {
                Piece a = pieces.get(i);
                List<Integer> neighbors = new ArrayList<>();
                for (int j = 0; j < pieces.size(); j++) {
                    if (i == j) continue;
                    Piece b = pieces.get(j);
                    if (sharesResource(a, b)) {
                        neighbors.add(j);
                    }
                }
                a.neighborIds = neighbors.stream().mapToInt(Integer::intValue).toArray();
            }

            boolean[][][] classBusy = new boolean[classIdxMap.size()][days][perDay + 1];
            boolean[][][] teacherBusy = new boolean[teacherIdxMap.size()][days][perDay + 1];
            boolean[][][] placeBusy = new boolean[placeIdxMap.size()][days][perDay + 1];
            int[][] teacherDayLoad = new int[teacherIdxMap.size()][days];
            int[][] subjectDayCount = new int[nextSubjectGroupIdx][days];

            for (BusyCell cell : in.busyTeachers()) {
                Integer tIdx = teacherIdxMap.get(cell.owner());
                if (tIdx != null && grid.isValidDay(cell.day()) && grid.isValidLecture(cell.lecture())) {
                    if (!teacherBusy[tIdx][cell.day()][cell.lecture()]) {
                        teacherBusy[tIdx][cell.day()][cell.lecture()] = true;
                        teacherDayLoad[tIdx][cell.day()]++;
                    }
                }
            }

            for (BusyCell cell : in.busyPlaces()) {
                if (!hasText(cell.owner())) continue;
                Integer pIdx = placeIdxMap.get(normalizeKey(cell.owner()));
                if (pIdx != null && grid.isValidDay(cell.day()) && grid.isValidLecture(cell.lecture())) {
                    placeBusy[pIdx][cell.day()][cell.lecture()] = true;
                }
            }

            return new SearchContext(
                    days,
                    perDay,
                    settings.maxPerTeacherPerDay(),
                    variation,
                    pieces,
                    classBusy,
                    teacherBusy,
                    placeBusy,
                    teacherDayLoad,
                    subjectDayCount
            );
        }

        private static boolean sharesResource(Piece a, Piece b) {
            if (a.classIdx == b.classIdx) return true;
            for (int ta : a.teacherIndices) {
                for (int tb : b.teacherIndices) {
                    if (ta == tb) return true;
                }
            }
            for (int pa : a.placeIndices) {
                for (int pb : b.placeIndices) {
                    if (pa == pb) return true;
                }
            }
            return false;
        }

        boolean placeFixedPieces() {
            for (Piece piece : pieces) {
                if (!piece.fixed) continue;
                if (!canPlace(piece, piece.fixedDay, piece.fixedStart)) {
                    failCounts[piece.id]++;
                    return false;
                }
                place(piece, piece.fixedDay, piece.fixedStart);
            }
            return true;
        }

        boolean solve() {
            if (unplacedCount == 0) {
                return true;
            }

            Piece next = pickHardestPiece();
            if (next == null) {
                return false;
            }

            List<SlotCandidate> candidates = collectOrderedSlots(next);
            if (candidates.isEmpty()) {
                failCounts[next.id]++;
                return false;
            }

            for (SlotCandidate slot : candidates) {
                place(next, slot.day(), slot.startLecture());
                if (forwardCheck(next) && solve()) {
                    return true;
                }
                unplace(next);
            }

            failCounts[next.id]++;
            return false;
        }

        private Piece pickHardestPiece() {
            Piece best = null;
            int bestDomain = Integer.MAX_VALUE;

            for (Piece piece : pieces) {
                if (piece.placed) continue;

                int domain = countValidSlots(piece, bestDomain + 1);
                if (domain == 0) {
                    failCounts[piece.id]++;
                    return null;
                }

                if (best == null || isHarder(piece, domain, best, bestDomain)) {
                    best = piece;
                    bestDomain = domain;
                }
            }
            return best;
        }

        private boolean isHarder(Piece a, int domainA, Piece b, int domainB) {
            if (domainA != domainB) return domainA < domainB;
            if (a.length != b.length) return a.length > b.length;
            if (a.grouped != b.grouped) return a.grouped;
            if (a.usesSharedPlace != b.usesSharedPlace) return a.usesSharedPlace;
            return a.id < b.id;
        }

        private int countValidSlots(Piece piece, int stopAt) {
            int count = 0;
            for (int d = 0; d < days; d++) {
                if (subjectDayCount[piece.subjectGroupIdx][d] >= piece.maxPerDay) {
                    continue;
                }
                if (!teachersHaveDailyRoom(piece, d)) {
                    continue;
                }
                for (int start : piece.allowedStarts) {
                    if (slotsFree(piece, d, start)) {
                        count++;
                        if (count >= stopAt) {
                            return count;
                        }
                    }
                }
            }
            return count;
        }

        private boolean forwardCheck(Piece justPlaced) {
            for (int neighborId : justPlaced.neighborIds) {
                Piece neighbor = pieces.get(neighborId);
                if (neighbor.placed) continue;
                if (countValidSlots(neighbor, 1) == 0) {
                    failCounts[neighbor.id]++;
                    return false;
                }
            }
            return true;
        }

        private List<SlotCandidate> collectOrderedSlots(Piece piece) {
            List<SlotCandidate> slots = new ArrayList<>();
            for (int d = 0; d < days; d++) {
                int sameSubCount = subjectDayCount[piece.subjectGroupIdx][d];
                if (sameSubCount >= piece.maxPerDay) {
                    continue;
                }
                if (!teachersHaveDailyRoom(piece, d)) {
                    continue;
                }
                for (int start : piece.allowedStarts) {
                    if (!slotsFree(piece, d, start)) {
                        continue;
                    }
                    int gapScore = computeClassDayGapScore(piece.classIdx, d, start, piece.length);
                    long hash = mixHash(variation, piece.id, d, start);
                    slots.add(new SlotCandidate(d, start, sameSubCount, gapScore, hash));
                }
            }

            slots.sort(Comparator
                    .comparingInt(SlotCandidate::sameSubjectDayCount)
                    .thenComparingInt(SlotCandidate::gapScore)
                    .thenComparingLong(SlotCandidate::tieHash));
            return slots;
        }

        private int computeClassDayGapScore(int classIdx, int day, int start, int length) {
            boolean[] row = classBusy[classIdx][day];
            int first = Integer.MAX_VALUE;
            int last = -1;
            int occupied = 0;

            for (int lec = 1; lec <= lecturesPerDay; lec++) {
                boolean busy = row[lec] || (lec >= start && lec < start + length);
                if (busy) {
                    if (lec < first) first = lec;
                    if (lec > last) last = lec;
                    occupied++;
                }
            }
            if (last < first) return 0;
            return (last - first + 1) - occupied;
        }

        private boolean canPlace(Piece piece, int day, int start) {
            if (day < 0 || day >= days) return false;
            if (subjectDayCount[piece.subjectGroupIdx][day] >= piece.maxPerDay) return false;
            if (!teachersHaveDailyRoom(piece, day)) return false;
            return slotsFree(piece, day, start);
        }

        private boolean teachersHaveDailyRoom(Piece piece, int day) {
            for (int tIdx : piece.teacherIndices) {
                if (teacherDayLoad[tIdx][day] + piece.length > maxTeacherPerDay) {
                    return false;
                }
            }
            return true;
        }

        private boolean slotsFree(Piece piece, int day, int start) {
            for (int offset = 0; offset < piece.length; offset++) {
                int lec = start + offset;
                if (lec < 1 || lec > lecturesPerDay) return false;
                if (classBusy[piece.classIdx][day][lec]) return false;
                for (int tIdx : piece.teacherIndices) {
                    if (teacherBusy[tIdx][day][lec]) return false;
                }
                for (int pIdx : piece.placeIndices) {
                    if (placeBusy[pIdx][day][lec]) return false;
                }
            }
            return true;
        }

        private void place(Piece piece, int day, int start) {
            for (int offset = 0; offset < piece.length; offset++) {
                int lec = start + offset;
                classBusy[piece.classIdx][day][lec] = true;
                for (int tIdx : piece.teacherIndices) {
                    teacherBusy[tIdx][day][lec] = true;
                }
                for (int pIdx : piece.placeIndices) {
                    placeBusy[pIdx][day][lec] = true;
                }
            }
            for (int tIdx : piece.teacherIndices) {
                teacherDayLoad[tIdx][day] += piece.length;
            }
            subjectDayCount[piece.subjectGroupIdx][day]++;
            piece.placed = true;
            piece.placedDay = day;
            piece.placedStart = start;
            unplacedCount--;
        }

        private void unplace(Piece piece) {
            int day = piece.placedDay;
            int start = piece.placedStart;
            for (int offset = 0; offset < piece.length; offset++) {
                int lec = start + offset;
                classBusy[piece.classIdx][day][lec] = false;
                for (int tIdx : piece.teacherIndices) {
                    teacherBusy[tIdx][day][lec] = false;
                }
                for (int pIdx : piece.placeIndices) {
                    placeBusy[pIdx][day][lec] = false;
                }
            }
            for (int tIdx : piece.teacherIndices) {
                teacherDayLoad[tIdx][day] -= piece.length;
            }
            subjectDayCount[piece.subjectGroupIdx][day]--;
            piece.placed = false;
            piece.placedDay = -1;
            piece.placedStart = -1;
            unplacedCount++;
        }

        private String buildFailureMessage() {
            Piece bottleneck = pieces.get(0);
            int maxFails = -1;
            for (Piece p : pieces) {
                if (failCounts[p.id] > maxFails) {
                    maxFails = failCounts[p.id];
                    bottleneck = p;
                }
            }
            return "Could not find a valid slot for " + bottleneck.label
                    + " without clashing with another class, teacher, or shared lab. "
                    + "Try removing a fixed lecture, raising the daily teacher limit, or choosing a different teacher.";
        }

        List<PlacedLecture> toPlacedLectures() {
            List<PlacedLecture> output = new ArrayList<>();
            for (Piece piece : pieces) {
                for (PieceMember member : piece.members) {
                    output.add(new PlacedLecture(
                            piece.classId,
                            member.subjectId(),
                            member.teacherId(),
                            piece.placedDay,
                            piece.placedStart,
                            piece.length,
                            piece.groupKey
                    ));
                }
            }
            output.sort(Comparator
                    .comparing(PlacedLecture::classId)
                    .thenComparingInt(PlacedLecture::day)
                    .thenComparingInt(PlacedLecture::startLecture)
                    .thenComparing(PlacedLecture::subjectId));
            return output;
        }
    }

    private static long mixHash(int variation, int pieceId, int day, int start) {
        long h = 0x9E3779B97F4A7C15L;
        h ^= (variation + 1L) * 0xBF58476D1CE4E5B9L;
        h = Long.rotateLeft(h, 27) ^ (pieceId * 0x94D049BB133111EBL);
        h = Long.rotateLeft(h, 31) ^ ((day * 31L + start) * 0xD6E8FEB86659FD93L);
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        return h;
    }

    private static String pairKey(String classId, String subjectId) {
        return classId + "|" + subjectId;
    }

    private static String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}