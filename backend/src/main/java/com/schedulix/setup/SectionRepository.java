package com.schedulix.setup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SectionRepository extends JpaRepository<Section, UUID> {

    List<Section> findBySemesterIdOrderByNameAsc(UUID semesterId);

    boolean existsBySemesterId(UUID semesterId);

    boolean existsBySemesterIdAndNameIgnoreCase(UUID semesterId, String name);

    Optional<Section> findBySemesterIdAndNameIgnoreCase(UUID semesterId, String name);

    @Query("""
            SELECT s FROM Section s
            JOIN Semester sem ON sem.id = s.semesterId
            WHERE s.id = :sectionId AND sem.departmentId = :departmentId
            """)
    Optional<Section> findByIdAndDepartmentId(
            @Param("sectionId") UUID sectionId,
            @Param("departmentId") UUID departmentId
    );
}