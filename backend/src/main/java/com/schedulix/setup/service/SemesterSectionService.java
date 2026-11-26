package com.schedulix.setup.service;

import com.schedulix.auth.security.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.history.HistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import com.schedulix.setup.entity.Section;
import com.schedulix.setup.repository.SectionRepository;
import com.schedulix.setup.dto.SectionRequest;
import com.schedulix.setup.dto.SectionResponse;
import com.schedulix.setup.entity.Semester;
import com.schedulix.setup.repository.SemesterRepository;
import com.schedulix.setup.dto.SemesterRequest;
import com.schedulix.setup.dto.SemesterResponse;

@Service
public class SemesterSectionService {

    private final SemesterRepository semesterRepository;
    private final SectionRepository sectionRepository;
    private final CurrentUser currentUser;
    private final HistoryService historyService;
    private final SavedTimetableLookup savedTimetableLookup;

    public SemesterSectionService(
            SemesterRepository semesterRepository,
            SectionRepository sectionRepository,
            CurrentUser currentUser,
            HistoryService historyService,
            SavedTimetableLookup savedTimetableLookup
    ) {
        this.semesterRepository = semesterRepository;
        this.sectionRepository = sectionRepository;
        this.currentUser = currentUser;
        this.historyService = historyService;
        this.savedTimetableLookup = savedTimetableLookup;
    }

    @Transactional(readOnly = true)
    public List<SemesterResponse> listSemesters() {
        return semesterRepository.findByDepartmentIdOrderByNumberAsc(currentUser.departmentId()).stream()
                .map(SemesterResponse::from)
                .toList();
    }

    @Transactional
    public SemesterResponse createSemester(SemesterRequest request) {
        UUID deptId = currentUser.departmentId();
        short number = request.number().shortValue();
        String name = request.name().trim();

        if (semesterRepository.existsByDepartmentIdAndNumber(deptId, number)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SEMESTER_EXISTS",
                    "Semester " + number + " already exists in your department."
            );
        }

        Semester semester = semesterRepository.save(new Semester(deptId, number, name));
        historyService.record("PLAN", "Created " + name + ".");
        return SemesterResponse.from(semester);
    }

    @Transactional
    public SemesterResponse updateSemester(UUID semesterId, SemesterRequest request) {
        UUID deptId = currentUser.departmentId();
        Semester semester = loadOwnedSemester(semesterId, deptId);

        short number = request.number().shortValue();
        String name = request.name().trim();

        semesterRepository.findByDepartmentIdAndNumber(deptId, number)
                .filter(existing -> !existing.getId().equals(semesterId))
                .ifPresent(existing -> {
                    throw new AppException(
                            HttpStatus.CONFLICT,
                            "SEMESTER_EXISTS",
                            "Semester " + number + " already exists in your department."
                    );
                });

        semester.setNumber(number);
        semester.setName(name);
        historyService.record("PLAN", "Updated semester to " + name + ".");
        return SemesterResponse.from(semester);
    }

    @Transactional
    public void deleteSemester(UUID semesterId) {
        Semester semester = loadOwnedSemester(semesterId, currentUser.departmentId());
        if (sectionRepository.existsBySemesterId(semesterId)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SEMESTER_HAS_SECTIONS",
                    "Delete all sections of " + semester.getName() + " before deleting the semester."
            );
        }

        String name = semester.getName();
        semesterRepository.delete(semester);
        historyService.record("PLAN", "Deleted " + name + ".");
    }

    @Transactional(readOnly = true)
    public List<SectionResponse> listSections(UUID semesterId) {
        loadOwnedSemester(semesterId, currentUser.departmentId());
        return sectionRepository.findBySemesterIdOrderByNameAsc(semesterId).stream()
                .map(s -> SectionResponse.from(s, savedTimetableLookup.hasSavedTimetable(s.getId())))
                .toList();
    }

    @Transactional
    public SectionResponse createSection(UUID semesterId, SectionRequest request) {
        Semester semester = loadOwnedSemester(semesterId, currentUser.departmentId());
        String name = request.name().trim();

        if (sectionRepository.existsBySemesterIdAndNameIgnoreCase(semesterId, name)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SECTION_EXISTS",
                    "Section " + name + " already exists in " + semester.getName() + "."
            );
        }

        Section section = sectionRepository.save(new Section(semesterId, name));
        historyService.record("PLAN", "Added Section " + name + " in " + semester.getName() + ".");
        return SectionResponse.from(section, false);
    }

    @Transactional
    public SectionResponse updateSection(UUID sectionId, SectionRequest request) {
        UUID deptId = currentUser.departmentId();
        Section section = loadOwnedSection(sectionId, deptId);
        Semester semester = loadOwnedSemester(section.getSemesterId(), deptId);
        String name = request.name().trim();

        sectionRepository.findBySemesterIdAndNameIgnoreCase(section.getSemesterId(), name)
                .filter(existing -> !existing.getId().equals(sectionId))
                .ifPresent(existing -> {
                    throw new AppException(
                            HttpStatus.CONFLICT,
                            "SECTION_EXISTS",
                            "Section " + name + " already exists in " + semester.getName() + "."
                    );
                });

        String oldName = section.getName();
        section.setName(name);
        historyService.record(
                "PLAN",
                "Renamed Section " + oldName + " to Section " + name + " in " + semester.getName() + "."
        );
        return SectionResponse.from(section, savedTimetableLookup.hasSavedTimetable(sectionId));
    }

    @Transactional
    public void deleteSection(UUID sectionId) {
        UUID deptId = currentUser.departmentId();
        Section section = loadOwnedSection(sectionId, deptId);
        if (savedTimetableLookup.hasSavedTimetable(sectionId)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SECTION_HAS_SAVED_TIMETABLE",
                    "Section " + section.getName()
                            + " has a saved timetable. Delete the saved timetable in Classes and Timetables first."
            );
        }

        Semester semester = loadOwnedSemester(section.getSemesterId(), deptId);
        String name = section.getName();
        sectionRepository.delete(section);
        historyService.record("PLAN", "Deleted Section " + name + " from " + semester.getName() + ".");
    }

    private Semester loadOwnedSemester(UUID semesterId, UUID departmentId) {
        return semesterRepository.findByIdAndDepartmentId(semesterId, departmentId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SEMESTER_NOT_FOUND",
                        "Semester not found."
                ));
    }

    private Section loadOwnedSection(UUID sectionId, UUID departmentId) {
        return sectionRepository.findByIdAndDepartmentId(sectionId, departmentId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SECTION_NOT_FOUND",
                        "Section not found."
                ));
    }
}