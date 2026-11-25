package com.schedulix.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubstituteRepository extends JpaRepository<Substitute, UUID> {

    List<Substitute> findByLeaveId(UUID leaveId);

    List<Substitute> findByOnDate(LocalDate onDate);

    List<Substitute> findByOnDateBetween(LocalDate fromDate, LocalDate toDate);

    Optional<Substitute> findByLectureIdAndOnDate(UUID lectureId, LocalDate onDate);

    boolean existsBySubstituteTeacherId(UUID teacherId);
}