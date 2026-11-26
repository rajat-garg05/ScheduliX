package com.schedulix.leave.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.schedulix.leave.entity.Substitute;

public interface SubstituteRepository extends JpaRepository<Substitute, UUID> {

    List<Substitute> findByLeaveId(UUID leaveId);

    List<Substitute> findByOnDate(LocalDate onDate);

    List<Substitute> findByOnDateBetween(LocalDate fromDate, LocalDate toDate);

    List<Substitute> findByOnDateGreaterThanEqualOrderByOnDateAsc(LocalDate fromDate);

    Optional<Substitute> findByLectureIdAndOnDate(UUID lectureId, LocalDate onDate);

    boolean existsBySubstituteTeacherId(UUID teacherId);
}