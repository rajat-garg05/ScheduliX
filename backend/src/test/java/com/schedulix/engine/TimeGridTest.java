package com.schedulix.engine;

import com.schedulix.engine.TimeGrid.Stretch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimeGridTest {

    @Test
    void splitsDayWithSingleLunchBreakIntoTwoStretches() {
        TimeGrid grid = TimeGrid.from(new EngineSettings(5, 6, List.of(3), 4));

        assertThat(grid.totalWeeklySlots()).isEqualTo(30);
        assertThat(grid.stretches()).containsExactly(
                new Stretch(1, 3),
                new Stretch(4, 6)
        );
        assertThat(grid.longestStretchLength()).isEqualTo(3);

        assertThat(grid.allowedStartLectures(1)).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(grid.allowedStartLectures(2)).containsExactly(1, 2, 4, 5);
        assertThat(grid.allowedStartLectures(3)).containsExactly(1, 4);
        assertThat(grid.isValidStart(3, 2)).isFalse(); // would cross lunch between 3 and 4
    }

    @Test
    void handlesMorningTeaBreakAndLunchBreak() {
        // 7 lectures with tea after 2 and lunch after 4 -> stretches 1..2, 3..4, 5..7
        TimeGrid grid = TimeGrid.from(new EngineSettings(5, 7, List.of(4, 2, 2), 5));

        assertThat(grid.stretches()).containsExactly(
                new Stretch(1, 2),
                new Stretch(3, 4),
                new Stretch(5, 7)
        );
        assertThat(grid.longestStretchLength()).isEqualTo(3);

        assertThat(grid.allowedStartLectures(2)).containsExactly(1, 3, 5, 6);
        assertThat(grid.allowedStartLectures(3)).containsExactly(5);
    }

    @Test
    void handlesShortDaysAndOutOfRangeBreaks() {
        // 4 lectures with break after 2 -> max stretch is 2, so 3-in-a-row cannot fit
        TimeGrid shortDay = TimeGrid.from(new EngineSettings(6, 4, List.of(0, 2, 4, 9), 4));

        assertThat(shortDay.stretches()).containsExactly(
                new Stretch(1, 2),
                new Stretch(3, 4)
        );
        assertThat(shortDay.longestStretchLength()).isEqualTo(2);
        assertThat(shortDay.allowedStartLectures(2)).containsExactly(1, 3);
        assertThat(shortDay.allowedStartLectures(3)).isEmpty();
        assertThat(shortDay.isValidStart(1, 3)).isFalse();
    }

    @Test
    void handlesDayWithNoBreaks() {
        TimeGrid noBreaks = TimeGrid.from(new EngineSettings(5, 5, List.of(), 4));

        assertThat(noBreaks.stretches()).containsExactly(new Stretch(1, 5));
        assertThat(noBreaks.longestStretchLength()).isEqualTo(5);
        assertThat(noBreaks.allowedStartLectures(2)).containsExactly(1, 2, 3, 4);
        assertThat(noBreaks.allowedStartLectures(3)).containsExactly(1, 2, 3);
    }
}