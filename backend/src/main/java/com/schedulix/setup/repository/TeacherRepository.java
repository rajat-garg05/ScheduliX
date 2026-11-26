package com.schedulix.setup.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.schedulix.setup.entity.Teacher;

public interface TeacherRepository extends JpaRepository<Teacher, UUID> {

    Optional<Teacher> findByIdAndDepartmentId(UUID id, UUID departmentId);

    Optional<Teacher> findByDepartmentIdAndCodeIgnoreCase(UUID departmentId, String code);

    boolean existsByDepartmentIdAndCodeIgnoreCase(UUID departmentId, String code);

    boolean existsByEmail(String email);

    List<Teacher> findByDepartmentIdOrderByCodeAsc(UUID departmentId);

    @Query("""
            SELECT t FROM Teacher t
            WHERE t.departmentId = :deptId
              AND (:search IS NULL OR :search = ''
                   OR LOWER(t.code) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(t.designation) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY t.code ASC
            """)
    Page<Teacher> searchByDepartment(
            @Param("deptId") UUID departmentId,
            @Param("search") String search,
            Pageable pageable
    );
}