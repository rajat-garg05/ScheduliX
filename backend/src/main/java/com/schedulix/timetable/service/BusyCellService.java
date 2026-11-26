package com.schedulix.timetable.service;

import com.schedulix.engine.BusyCell;
import com.schedulix.setup.entity.Subject;
import com.schedulix.setup.repository.SubjectRepository;
import com.schedulix.timetable.service.TimetableSettingsSupport.LectureInterval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.schedulix.timetable.entity.Lecture;
import com.schedulix.timetable.repository.LectureRepository;
import com.schedulix.timetable.dto.TimetableSettings;

@Service
public class BusyCellService {

    public record BusyCells(List<BusyCell> busyTeachers, List<BusyCell> busyPlaces) {
    }

    public record SavedLectureSlot(
            UUID teacherId,
            String sharedPlace,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime
    ) {
    }

    private final LectureRepository lectureRepository;
    private final SubjectRepository subjectRepository;
    private final TimetableSettingsSupport settingsSupport;

    public BusyCellService(
            LectureRepository lectureRepository,
            SubjectRepository subjectRepository,
            TimetableSettingsSupport settingsSupport
    ) {
        this.lectureRepository = lectureRepository;
        this.subjectRepository = subjectRepository;
        this.settingsSupport = settingsSupport;
    }

    @Transactional(readOnly = true)
    public BusyCells forDepartment(UUID departmentId, TimetableSettings targetSettings) {
        List<Lecture> savedLectures = lectureRepository.findSavedLecturesByDepartment(departmentId);
        if (savedLectures.isEmpty()) {
            return new BusyCells(List.of(), List.of());
        }

        Map<UUID, String> sharedPlaceBySubjectId = new HashMap<>();
        for (Subject subject : subjectRepository.findByDepartmentIdOrderByCodeAsc(departmentId)) {
            if (subject.getSharedPlace() != null && !subject.getSharedPlace().isBlank()) {
                sharedPlaceBySubjectId.put(subject.getId(), subject.getSharedPlace().trim());
            }
        }

        List<SavedLectureSlot> slots = new ArrayList<>(savedLectures.size());
        for (Lecture lec : savedLectures) {
            slots.add(new SavedLectureSlot(
                    lec.getTeacherId(),
                    sharedPlaceBySubjectId.get(lec.getSubjectId()),
                    lec.getDayOfWeek(),
                    lec.getStartTime(),
                    lec.getEndTime()
            ));
        }

        return mapToBusyCells(slots, targetSettings);
    }

    public BusyCells mapToBusyCells(List<SavedLectureSlot> savedSlots, TimetableSettings targetSettings) {
        if (savedSlots == null || savedSlots.isEmpty() || targetSettings == null) {
            return new BusyCells(List.of(), List.of());
        }

        Map<DayOfWeek, Integer> dayIndexByDay = new HashMap<>();
        List<String> workingDays = targetSettings.workingDays();
        for (int i = 0; i < workingDays.size(); i++) {
            dayIndexByDay.put(DayOfWeek.valueOf(workingDays.get(i).trim().toUpperCase(Locale.ROOT)), i);
        }

        Map<Integer, LectureInterval> targetIntervals = settingsSupport.lectureIntervals(targetSettings);
        Set<BusyCell> busyTeachers = new LinkedHashSet<>();
        Set<BusyCell> busyPlaces = new LinkedHashSet<>();

        for (SavedLectureSlot saved : savedSlots) {
            Integer dayIndex = dayIndexByDay.get(saved.dayOfWeek());
            if (dayIndex == null) {
                continue;
            }

            for (LectureInterval interval : targetIntervals.values()) {
                if (overlaps(interval.startTime(), interval.endTime(), saved.startTime(), saved.endTime())) {
                    if (saved.teacherId() != null) {
                        busyTeachers.add(new BusyCell(
                                saved.teacherId().toString(),
                                dayIndex,
                                interval.lectureNumber()
                        ));
                    }
                    if (saved.sharedPlace() != null && !saved.sharedPlace().isBlank()) {
                        busyPlaces.add(new BusyCell(
                                saved.sharedPlace().trim(),
                                dayIndex,
                                interval.lectureNumber()
                        ));
                    }
                }
            }
        }

        return new BusyCells(List.copyOf(busyTeachers), List.copyOf(busyPlaces));
    }

    public static boolean overlaps(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }
}