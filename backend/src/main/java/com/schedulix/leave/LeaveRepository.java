package com.schedulix.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRepository extends JpaRepository<Leave, UUID> {

    List<Leave> findByDepartmentIdOrderByCreatedAtDesc(UUID departmentId);

    Optional<Leave> findByIdAndDepartmentId(UUID id, UUID departmentId);

    boolean existsByTeacherId(UUID teacherId);

    @Query("""
            SELECT COUNT(l) > 0 FROM Leave l
            WHERE l.teacherId = :teacherId
              AND l.status = com.schedulix.leave.LeaveStatus.ACCEPTED
              AND l.fromDate <= :toDate
              AND l.toDate >= :fromDate
              AND (:excludeId IS NULL OR l.id <> :excludeId)
            """)
    boolean existsOverlappingAccepted(
            @Param("teacherId") UUID teacherId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("excludeId") UUID excludeId
    );

    @Query("""
            SELECT l FROM Leave l
            WHERE l.departmentId = :departmentId
              AND l.status = com.schedulix.leave.LeaveStatus.ACCEPTED
              AND l.fromDate <= :date
              AND l.toDate >= :date
            """)
    List<Leave> findAcceptedOnDate(
            @Param("departmentId") UUID departmentId,
            @Param("date") LocalDate date
    );

    @Query("""
            SELECT u.id FROM UserAccount u
            WHERE u.departmentId = :departmentId
              AND u.role = com.schedulix.auth.UserRole.HOD
            """)
    List<UUID> findHodUserIds(@Param("departmentId") UUID departmentId);
}