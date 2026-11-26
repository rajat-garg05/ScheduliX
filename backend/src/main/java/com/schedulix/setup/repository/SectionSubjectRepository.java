package com.schedulix.setup.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import com.schedulix.setup.entity.SectionSubject;
import com.schedulix.setup.entity.SectionSubjectId;

public interface SectionSubjectRepository extends JpaRepository<SectionSubject, SectionSubjectId> {

    List<SectionSubject> findBySectionId(UUID sectionId);

    void deleteBySectionId(UUID sectionId);

    boolean existsBySubjectId(UUID subjectId);
}