package com.schedulix.leave.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class LeaveLectureId implements Serializable {

    private UUID leaveId;
    private UUID lectureId;
    private LocalDate onDate;

    public LeaveLectureId() {
    }

    public LeaveLectureId(UUID leaveId, UUID lectureId, LocalDate onDate) {
        this.leaveId = leaveId;
        this.lectureId = lectureId;
        this.onDate = onDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeaveLectureId that)) return false;
        return Objects.equals(leaveId, that.leaveId)
                && Objects.equals(lectureId, that.lectureId)
                && Objects.equals(onDate, that.onDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(leaveId, lectureId, onDate);
    }
}