package com.schedulix.timetable;

import com.schedulix.common.AppException;
import com.schedulix.timetable.TimetableSettings.BreakSetting;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TimetableSettingsSupport {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public record LectureInterval(int lectureNumber, LocalTime startTime, LocalTime endTime) {
    }

    public TimetableSettings validateAndNormalize(TimetableSettings raw) {
        if (raw == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_SETTINGS", "Time settings are missing.");
        }

        if (raw.workingDays() == null || raw.workingDays().isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_SETTINGS", "Pick at least one working day.");
        }

        TreeSet<DayOfWeek> days = new TreeSet<>(Comparator.comparingInt(DayOfWeek::getValue));
        for (String dayStr : raw.workingDays()) {
            if (dayStr == null || dayStr.isBlank()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_SETTINGS", "One of the working days is blank.");
            }
            try {
                days.add(DayOfWeek.valueOf(dayStr.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_SETTINGS",
                        "Unknown day of week: " + dayStr.trim() + "."
                );
            }
        }

        LocalTime startTime = parseTime(raw.startTime());

        if (raw.lectureMinutes() < 20 || raw.lectureMinutes() > 180) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SETTINGS",
                    "Lecture length must be between 20 and 180 minutes."
            );
        }

        if (raw.lecturesPerDay() < 1 || raw.lecturesPerDay() > 12) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SETTINGS",
                    "Lectures per day must be between 1 and 12."
            );
        }

        if (raw.maxPerTeacherPerDay() < 1 || raw.maxPerTeacherPerDay() > raw.lecturesPerDay()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SETTINGS",
                    "Most lectures one teacher may take in a day must be between 1 and " + raw.lecturesPerDay() + "."
            );
        }

        List<BreakSetting> normalizedBreaks = new ArrayList<>();
        Set<Integer> seenBreakPositions = new HashSet<>();
        if (raw.breaks() != null) {
            for (BreakSetting b : raw.breaks()) {
                if (b == null) continue;
                if (raw.lecturesPerDay() == 1 || b.afterLecture() < 1 || b.afterLecture() >= raw.lecturesPerDay()) {
                    throw new AppException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_SETTINGS",
                            "A break must come after a lecture between 1 and " + Math.max(1, raw.lecturesPerDay() - 1) + "."
                    );
                }
                if (!seenBreakPositions.add(b.afterLecture())) {
                    throw new AppException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_SETTINGS",
                            "There cannot be two breaks after lecture " + b.afterLecture() + "."
                    );
                }
                if (b.minutes() < 5 || b.minutes() > 180) {
                    throw new AppException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_SETTINGS",
                            "Break length must be between 5 and 180 minutes."
                    );
                }
                String label = (b.label() == null || b.label().isBlank()) ? "Break" : b.label().trim();
                if (label.length() > 40) {
                    throw new AppException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_SETTINGS",
                            "Break name must be 40 characters or fewer."
                    );
                }
                normalizedBreaks.add(new BreakSetting(b.afterLecture(), b.minutes(), label));
            }
        }
        normalizedBreaks.sort(Comparator.comparingInt(BreakSetting::afterLecture));

        // Verify the day does not spill past midnight (23:59)
        int totalMinutes = raw.lecturesPerDay() * raw.lectureMinutes()
                + normalizedBreaks.stream().mapToInt(BreakSetting::minutes).sum();
        int startMinuteOfDay = startTime.getHour() * 60 + startTime.getMinute();
        if (startMinuteOfDay + totalMinutes > 23 * 60 + 59) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SETTINGS",
                    "The day runs past midnight. Use an earlier start time, fewer lectures, or shorter lengths."
            );
        }

        List<String> orderedDays = days.stream().map(DayOfWeek::name).toList();
        return new TimetableSettings(
                orderedDays,
                startTime.format(HH_MM),
                raw.lectureMinutes(),
                raw.lecturesPerDay(),
                normalizedBreaks,
                raw.maxPerTeacherPerDay()
        );
    }

    public List<DayPreviewSlot> buildDayPreview(TimetableSettings settings) {
        LocalTime cursor = parseTime(settings.startTime());
        Map<Integer, BreakSetting> breakAfter = new HashMap<>();
        if (settings.breaks() != null) {
            for (BreakSetting b : settings.breaks()) {
                breakAfter.put(b.afterLecture(), b);
            }
        }

        List<DayPreviewSlot> preview = new ArrayList<>();
        for (int lec = 1; lec <= settings.lecturesPerDay(); lec++) {
            LocalTime end = cursor.plusMinutes(settings.lectureMinutes());
            preview.add(new DayPreviewSlot(
                    "LECTURE",
                    lec,
                    "Lecture " + lec,
                    cursor.format(HH_MM),
                    end.format(HH_MM),
                    settings.lectureMinutes()
            ));
            cursor = end;

            BreakSetting brk = breakAfter.get(lec);
            if (brk != null) {
                LocalTime breakEnd = cursor.plusMinutes(brk.minutes());
                preview.add(new DayPreviewSlot(
                        "BREAK",
                        null,
                        brk.label(),
                        cursor.format(HH_MM),
                        breakEnd.format(HH_MM),
                        brk.minutes()
                ));
                cursor = breakEnd;
            }
        }
        return preview;
    }

    public Map<Integer, LectureInterval> lectureIntervals(TimetableSettings settings) {
        Map<Integer, LectureInterval> map = new LinkedHashMap<>();
        for (DayPreviewSlot slot : buildDayPreview(settings)) {
            if ("LECTURE".equals(slot.type()) && slot.lectureNumber() != null) {
                map.put(
                        slot.lectureNumber(),
                        new LectureInterval(
                                slot.lectureNumber(),
                                LocalTime.parse(slot.startTime(), HH_MM),
                                LocalTime.parse(slot.endTime(), HH_MM)
                        )
                );
            }
        }
        return map;
    }

    public String toJson(TimetableSettings s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"workingDays\":[");
        for (int i = 0; i < s.workingDays().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escapeJson(s.workingDays().get(i))).append('"');
        }
        sb.append("],\"startTime\":\"").append(escapeJson(s.startTime())).append('"');
        sb.append(",\"lectureMinutes\":").append(s.lectureMinutes());
        sb.append(",\"lecturesPerDay\":").append(s.lecturesPerDay());
        sb.append(",\"breaks\":[");
        List<BreakSetting> breaks = s.breaks() == null ? List.of() : s.breaks();
        for (int i = 0; i < breaks.size(); i++) {
            BreakSetting b = breaks.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"afterLecture\":").append(b.afterLecture())
                    .append(",\"minutes\":").append(b.minutes())
                    .append(",\"label\":\"").append(escapeJson(b.label())).append("\"}");
        }
        sb.append("],\"maxPerTeacherPerDay\":").append(s.maxPerTeacherPerDay()).append('}');
        return sb.toString();
    }

    public TimetableSettings fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_SETTINGS", "Saved settings are empty.");
        }

        List<String> workingDays = extractStringArray(json, "workingDays");
        String startTime = extractString(json, "startTime");
        int lectureMinutes = extractInt(json, "lectureMinutes");
        int lecturesPerDay = extractInt(json, "lecturesPerDay");
        int maxPerTeacherPerDay = extractInt(json, "maxPerTeacherPerDay");
        List<BreakSetting> breaks = extractBreaks(json);

        return new TimetableSettings(
                workingDays,
                startTime,
                lectureMinutes,
                lecturesPerDay,
                breaks,
                maxPerTeacherPerDay
        );
    }

    private LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_SETTINGS", "Please enter a start time (HH:mm).");
        }
        try {
            return LocalTime.parse(raw.trim(), HH_MM);
        } catch (DateTimeParseException ex) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SETTINGS",
                    "Start time must be in 24-hour HH:mm format, for example 09:00."
            );
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescapeJson(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private List<String> extractStringArray(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
        if (!m.find()) return List.of();
        String inside = m.group(1);
        List<String> items = new ArrayList<>();
        Matcher itemMatcher = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(inside);
        while (itemMatcher.find()) {
            items.add(unescapeJson(itemMatcher.group(1)));
        }
        return items;
    }

    private String extractString(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        return m.find() ? unescapeJson(m.group(1)) : "";
    }

    private int extractInt(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private List<BreakSetting> extractBreaks(String json) {
        Matcher arr = Pattern.compile("\"breaks\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(json);
        if (!arr.find()) return List.of();
        String inside = arr.group(1);
        List<BreakSetting> breaks = new ArrayList<>();
        Matcher obj = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL).matcher(inside);
        while (obj.find()) {
            String body = obj.group(1);
            int afterLecture = extractInt(body, "afterLecture");
            int minutes = extractInt(body, "minutes");
            String label = extractString(body, "label");
            breaks.add(new BreakSetting(afterLecture, minutes, label));
        }
        return breaks;
    }
}