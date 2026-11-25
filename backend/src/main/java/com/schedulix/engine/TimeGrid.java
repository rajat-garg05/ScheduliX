package com.schedulix.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class TimeGrid {

    public record Stretch(int startLecture, int endLecture) {
        public int length() {
            return endLecture - startLecture + 1;
        }
    }

    private final int workingDays;
    private final int lecturesPerDay;
    private final List<Stretch> stretches;
    private final int longestStretch;
    private final Map<Integer, List<Integer>> startsByBlockSize;

    private TimeGrid(
            int workingDays,
            int lecturesPerDay,
            List<Stretch> stretches,
            int longestStretch,
            Map<Integer, List<Integer>> startsByBlockSize
    ) {
        this.workingDays = workingDays;
        this.lecturesPerDay = lecturesPerDay;
        this.stretches = stretches;
        this.longestStretch = longestStretch;
        this.startsByBlockSize = startsByBlockSize;
    }

    public static TimeGrid from(EngineSettings settings) {
        int days = settings == null ? 0 : Math.max(0, settings.workingDays());
        int perDay = settings == null ? 0 : Math.max(0, settings.lecturesPerDay());

        if (perDay == 0) {
            return new TimeGrid(days, 0, List.of(), 0, Map.of());
        }

        // Only breaks strictly between 1 and perDay - 1 split the day into stretches.
        TreeSet<Integer> breaks = new TreeSet<>();
        if (settings.breakAfterLecture() != null) {
            for (Integer b : settings.breakAfterLecture()) {
                if (b != null && b >= 1 && b < perDay) {
                    breaks.add(b);
                }
            }
        }

        List<Stretch> stretches = new ArrayList<>();
        int start = 1;
        for (int breakAfter : breaks) {
            stretches.add(new Stretch(start, breakAfter));
            start = breakAfter + 1;
        }
        stretches.add(new Stretch(start, perDay));

        int longest = 0;
        for (Stretch stretch : stretches) {
            if (stretch.length() > longest) {
                longest = stretch.length();
            }
        }

        Map<Integer, List<Integer>> startsMap = new HashMap<>();
        int maxPrecomputed = Math.max(3, longest);
        for (int blockSize = 1; blockSize <= maxPrecomputed; blockSize++) {
            List<Integer> allowed = new ArrayList<>();
            for (Stretch stretch : stretches) {
                int lastStart = stretch.endLecture() - blockSize + 1;
                for (int lecture = stretch.startLecture(); lecture <= lastStart; lecture++) {
                    allowed.add(lecture);
                }
            }
            startsMap.put(blockSize, Collections.unmodifiableList(allowed));
        }

        return new TimeGrid(
                days,
                perDay,
                Collections.unmodifiableList(stretches),
                longest,
                Collections.unmodifiableMap(startsMap)
        );
    }

    public int workingDays() {
        return workingDays;
    }

    public int lecturesPerDay() {
        return lecturesPerDay;
    }

    public int totalWeeklySlots() {
        return workingDays * lecturesPerDay;
    }

    public List<Stretch> stretches() {
        return stretches;
    }

    public int longestStretchLength() {
        return longestStretch;
    }

    public List<Integer> allowedStartLectures(int blockSize) {
        if (blockSize <= 0 || blockSize > longestStretch) {
            return List.of();
        }
        return startsByBlockSize.getOrDefault(blockSize, List.of());
    }

    public boolean isValidStart(int startLecture, int blockSize) {
        return allowedStartLectures(blockSize).contains(startLecture);
    }

    public boolean isValidDay(int day) {
        return day >= 0 && day < workingDays;
    }

    public boolean isValidLecture(int lecture) {
        return lecture >= 1 && lecture <= lecturesPerDay;
    }
}