package com.schedulix.timetable;

import com.schedulix.common.AppException;
import com.schedulix.timetable.dto.TimetableSettings.BreakSetting;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.schedulix.timetable.dto.DayPreviewSlot;
import com.schedulix.timetable.dto.TimetableSettings;
import com.schedulix.timetable.service.TimetableSettingsSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimetableSettingsSupportTest {

    private final TimetableSettingsSupport support = new TimetableSettingsSupport();

    @Test
    void buildsAccurateClockTimesForLecturesAndBreaksAndRoundTripsJson() {
        TimetableSettings raw = new TimetableSettings(
                List.of("FRIDAY", "MONDAY", "WEDNESDAY", "TUESDAY", "THURSDAY"),
                "09:00",
                50,
                6,
                List.of(
                        new BreakSetting(3, 45, "Lunch"),
                        new BreakSetting(1, 15, "Short Break")
                ),
                4
        );

        TimetableSettings normalized = support.validateAndNormalize(raw);
        assertThat(normalized.workingDays()).containsExactly("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY");
        assertThat(normalized.breaks()).extracting(BreakSetting::afterLecture).containsExactly(1, 3);

        List<DayPreviewSlot> preview = support.buildDayPreview(normalized);
        assertThat(preview).hasSize(8); // 6 lectures + 2 breaks
        assertThat(preview.get(0)).isEqualTo(new DayPreviewSlot("LECTURE", 1, "Lecture 1", "09:00", "09:50", 50));
        assertThat(preview.get(1)).isEqualTo(new DayPreviewSlot("BREAK", null, "Short Break", "09:50", "10:05", 15));
        assertThat(preview.get(2)).isEqualTo(new DayPreviewSlot("LECTURE", 2, "Lecture 2", "10:05", "10:55", 50));
        assertThat(preview.get(3)).isEqualTo(new DayPreviewSlot("LECTURE", 3, "Lecture 3", "10:55", "11:45", 50));
        assertThat(preview.get(4)).isEqualTo(new DayPreviewSlot("BREAK", null, "Lunch", "11:45", "12:30", 45));
        assertThat(preview.get(7)).isEqualTo(new DayPreviewSlot("LECTURE", 6, "Lecture 6", "14:10", "15:00", 50));

        String json = support.toJson(normalized);
        TimetableSettings parsed = support.fromJson(json);
        assertThat(parsed).isEqualTo(normalized);
    }

    @Test
    void rejectsDuplicateBreaksOutOfRangeBreaksAndInvalidBounds() {
        // Duplicate break after lecture 3
        TimetableSettings dupBreak = new TimetableSettings(
                List.of("MONDAY"), "09:00", 50, 6,
                List.of(new BreakSetting(3, 15, "Tea"), new BreakSetting(3, 45, "Lunch")),
                4
        );
        assertThatThrownBy(() -> support.validateAndNormalize(dupBreak))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("two breaks after lecture 3");

        // Break after last lecture
        TimetableSettings endBreak = new TimetableSettings(
                List.of("MONDAY"), "09:00", 50, 6,
                List.of(new BreakSetting(6, 30, "End")),
                4
        );
        assertThatThrownBy(() -> support.validateAndNormalize(endBreak))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("between 1 and 5");

        // Max per teacher per day > lecturesPerDay
        TimetableSettings highMax = new TimetableSettings(
                List.of("MONDAY"), "09:00", 50, 5, List.of(), 6
        );
        assertThatThrownBy(() -> support.validateAndNormalize(highMax))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("between 1 and 5");

        // Lecture length outside 20..180
        TimetableSettings shortLec = new TimetableSettings(
                List.of("MONDAY"), "09:00", 15, 5, List.of(), 4
        );
        assertThatThrownBy(() -> support.validateAndNormalize(shortLec))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("between 20 and 180");
    }
}