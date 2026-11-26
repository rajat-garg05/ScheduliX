package com.schedulix.leave.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.schedulix.leave.entity.LeaveLecture;
import com.schedulix.leave.entity.LeaveLectureId;

public interface LeaveLectureRepository extends JpaRepository<LeaveLecture, LeaveLectureId> {

    List<LeaveLecture> findByLeaveId(UUID leaveId);

    List<LeaveLecture> findByLeaveIdAndOnDate(UUID leaveId, LocalDate onDate);
}