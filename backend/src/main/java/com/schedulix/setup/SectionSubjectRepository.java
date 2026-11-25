package com.schedulix.setup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SectionSubjectRepository extends JpaRepository<SectionSubject, SectionSubjectId> {

    List<SectionSubject> findBySectionId(UUID sectionId);

    void deleteBySectionId(UUID sectionId);

    boolean existsBySubjectId(UUID subjectId);
}