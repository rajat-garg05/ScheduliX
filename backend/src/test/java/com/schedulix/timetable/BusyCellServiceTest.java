package com.schedulix.timetable;

import com.schedulix.engine.BusyCell;
import com.schedulix.timetable.service.BusyCellService.BusyCells;
import com.schedulix.timetable.service.BusyCellService.SavedLectureSlot;
import com.schedulix.timetable.dto.TimetableSettings.BreakSetting;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import com.schedulix.timetable.service.BusyCellService;
import com.schedulix.timetable.dto.TimetableSettings;
import com.schedulix.timetable.service.TimetableSettingsSupport;

import static org.assertj.core.api.Assertions.assertThat;

class BusyCellServiceTest {

    private final TimetableSettingsSupport settingsSupport = new TimetableSettingsSupport();
    private final BusyCellService busyCellService = new BusyCellService(null, null, settingsSupport);

    @Test
    void mapsSavedLecturesWithDifferentStartTimesToOverlappingGridCellsOnly() {
        // New timetable: Mon-Fri, starts 09:00, 50-min lectures, lunch after 3 (45 min)
        // L1: 09:00-09:50, L2: 09:50-10:40, L3: 10:40-11:30, Lunch: 11:30-12:15, L4: 12:15-13:05
        TimetableSettings target = new TimetableSettings(
                List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"),
                "09:00",
                50,
                4,
                List.of(new BreakSetting(3, 45, "Lunch")),
                4
        );

        UUID teacherId = UUID.randomUUID();
        List<SavedLectureSlot> saved = List.of(
                // Saved lecture from another timetable starting at 09:30-10:30 on MONDAY
                // -> overlaps BOTH L1 (09:00-09:50) and L2 (09:50-10:40), not L3
                new SavedLectureSlot(teacherId, "Computer Lab 1", DayOfWeek.MONDAY, LocalTime.of(9, 30), LocalTime.of(10, 30)),

                // Touching boundary: 10:40-11:30 on TUESDAY -> overlaps L3 only (not L2 ending at 10:40)
                new SavedLectureSlot(teacherId, null, DayOfWeek.TUESDAY, LocalTime.of(10, 40), LocalTime.of(11, 30)),

                // Falls entirely inside Lunch (11:30-12:15) on WEDNESDAY -> overlaps NO lectures
                new SavedLectureSlot(teacherId, "Computer Lab 1", DayOfWeek.WEDNESDAY, LocalTime.of(11, 35), LocalTime.of(12, 10)),

                // Saturday is not a working day in target -> ignored
                new SavedLectureSlot(teacherId, "Computer Lab 1", DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))
        );

        BusyCells result = busyCellService.mapToBusyCells(saved, target);

        assertThat(result.busyTeachers()).containsExactlyInAnyOrder(
                new BusyCell(teacherId.toString(), 0, 1),
                new BusyCell(teacherId.toString(), 0, 2),
                new BusyCell(teacherId.toString(), 1, 3)
        );
        assertThat(result.busyPlaces()).containsExactlyInAnyOrder(
                new BusyCell("Computer Lab 1", 0, 1),
                new BusyCell("Computer Lab 1", 0, 2)
        );
    }
}