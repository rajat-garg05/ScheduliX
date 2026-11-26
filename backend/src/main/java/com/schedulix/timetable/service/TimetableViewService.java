package com.schedulix.timetable.service;

import com.schedulix.auth.security.CurrentUser;
import com.schedulix.common.AppException;
import com.schedulix.setup.entity.Section;
import com.schedulix.setup.repository.SectionRepository;
import com.schedulix.setup.entity.Semester;
import com.schedulix.setup.repository.SemesterRepository;
import com.schedulix.timetable.dto.TimetableTreeResponse.SavedTimetableCard;
import com.schedulix.timetable.dto.TimetableTreeResponse.TreeSection;
import com.schedulix.timetable.dto.TimetableTreeResponse.TreeSemester;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.schedulix.timetable.entity.Lecture;
import com.schedulix.timetable.repository.LectureRepository;
import com.schedulix.timetable.entity.Timetable;
import com.schedulix.timetable.entity.TimetableClass;
import com.schedulix.timetable.repository.TimetableClassRepository;
import com.schedulix.timetable.repository.TimetableRepository;
import com.schedulix.timetable.entity.TimetableStatus;
import com.schedulix.timetable.dto.TimetableTreeResponse;
import com.schedulix.timetable.dto.TimetableViewResponse;

@Service
public class TimetableViewService {

    private final TimetableDraftService draftService;
    private final TimetableMakeService makeService;
    private final TimetableRepository timetableRepository;
    private final TimetableClassRepository timetableClassRepository;
    private final LectureRepository lectureRepository;
    private final SemesterRepository semesterRepository;
    private final SectionRepository sectionRepository;
    private final CurrentUser currentUser;

    public TimetableViewService(
            TimetableDraftService draftService,
            TimetableMakeService makeService,
            TimetableRepository timetableRepository,
            TimetableClassRepository timetableClassRepository,
            LectureRepository lectureRepository,
            SemesterRepository semesterRepository,
            SectionRepository sectionRepository,
            CurrentUser currentUser
    ) {
        this.draftService = draftService;
        this.makeService = makeService;
        this.timetableRepository = timetableRepository;
        this.timetableClassRepository = timetableClassRepository;
        this.lectureRepository = lectureRepository;
        this.semesterRepository = semesterRepository;
        this.sectionRepository = sectionRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public TimetableViewResponse getView(UUID timetableId, UUID classId, UUID teacherId) {
        Timetable tt = draftService.loadOwnedTimetable(timetableId, currentUser.departmentId());
        return makeService.buildView(tt, classId, teacherId);
    }

    @Transactional(readOnly = true)
    public TimetableTreeResponse getTree() {
        return buildTree(false);
    }

    @Transactional(readOnly = true)
    public TimetableTreeResponse listSaved() {
        return buildTree(true);
    }

    @Transactional(readOnly = true)
    public TimetableViewResponse getSectionSavedTimetable(UUID sectionId) {
        UUID deptId = currentUser.departmentId();
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Section not found."));

        // Enforce department isolation via the section's semester
        semesterRepository.findByIdAndDepartmentId(section.getSemesterId(), deptId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Section not found."));

        TimetableClass savedLink = timetableClassRepository.findBySectionIdAndSavedTrue(section.getId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "TIMETABLE_NOT_FOUND",
                        "Section " + section.getName() + " does not have a saved timetable yet."
                ));

        Timetable tt = draftService.loadOwnedTimetable(savedLink.getTimetableId(), deptId);
        return makeService.buildView(tt, section.getId(), null);
    }

    private TimetableTreeResponse buildTree(boolean onlySaved) {
        UUID deptId = currentUser.departmentId();
        List<Semester> semesters = semesterRepository.findByDepartmentIdOrderByNumberAsc(deptId);
        if (semesters.isEmpty()) {
            return new TimetableTreeResponse(List.of());
        }

        // Index saved timetables and their per-section teacher & lecture counts
        List<Timetable> savedTimetables = timetableRepository
                .findByDepartmentIdAndStatusOrderByCreatedAtDesc(deptId, TimetableStatus.SAVED);

        Map<UUID, SavedTimetableCard> cardBySectionId = new HashMap<>();
        for (Timetable tt : savedTimetables) {
            List<Lecture> lectures = lectureRepository.findByTimetableId(tt.getId());
            Map<UUID, Set<UUID>> teachersBySection = new HashMap<>();
            Map<UUID, Integer> lectureCountBySection = new HashMap<>();
            for (Lecture lec : lectures) {
                teachersBySection.computeIfAbsent(lec.getSectionId(), k -> new HashSet<>()).add(lec.getTeacherId());
                lectureCountBySection.merge(lec.getSectionId(), 1, Integer::sum);
            }

            for (TimetableClass tc : timetableClassRepository.findByTimetableId(tt.getId())) {
                if (!tc.isSaved()) continue;
                int teacherCount = teachersBySection.getOrDefault(tc.getSectionId(), Set.of()).size();
                int lectureCount = lectureCountBySection.getOrDefault(tc.getSectionId(), 0);
                cardBySectionId.put(
                        tc.getSectionId(),
                        new SavedTimetableCard(tt.getId(), tt.getName(), tt.getSavedAt(), teacherCount, lectureCount)
                );
            }
        }

        List<TreeSemester> treeSemesters = new ArrayList<>();
        for (Semester sem : semesters) {
            List<Section> sections = sectionRepository.findBySemesterIdOrderByNameAsc(sem.getId());
            List<TreeSection> treeSections = new ArrayList<>();
            for (Section sec : sections) {
                SavedTimetableCard card = cardBySectionId.get(sec.getId());
                if (onlySaved && card == null) {
                    continue;
                }
                treeSections.add(new TreeSection(sec.getId(), sec.getName(), card));
            }
            if (onlySaved && treeSections.isEmpty()) {
                continue;
            }
            treeSemesters.add(new TreeSemester(sem.getId(), sem.getNumber(), sem.getName(), treeSections));
        }

        return new TimetableTreeResponse(treeSemesters);
    }
}