package com.schedulix.setup.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.schedulix.setup.entity.Subject;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    Optional<Subject> findByIdAndDepartmentId(UUID id, UUID departmentId);

    Optional<Subject> findByDepartmentIdAndCodeIgnoreCase(UUID departmentId, String code);

    boolean existsByDepartmentIdAndCodeIgnoreCase(UUID departmentId, String code);

    List<Subject> findByDepartmentIdOrderByCodeAsc(UUID departmentId);

    List<Subject> findByDepartmentIdAndRunTogetherGroupIgnoreCase(UUID departmentId, String runTogetherGroup);

    @Query("""
            SELECT s FROM Subject s
            WHERE s.departmentId = :deptId
              AND (:search IS NULL OR :search = ''
                   OR LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(s.runTogetherGroup, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(s.sharedPlace, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY s.code ASC
            """)
    Page<Subject> searchByDepartment(
            @Param("deptId") UUID departmentId,
            @Param("search") String search,
            Pageable pageable
    );
}