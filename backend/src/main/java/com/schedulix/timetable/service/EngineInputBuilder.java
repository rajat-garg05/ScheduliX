package com.schedulix.timetable.service;

import com.schedulix.engine.EngineClass;
import com.schedulix.engine.EngineInput;
import com.schedulix.engine.EngineSettings;
import com.schedulix.engine.EngineSubject;
import com.schedulix.engine.EngineTeacher;
import com.schedulix.engine.FixedLecture;
import com.schedulix.engine.PlanLine;
import com.schedulix.engine.TeacherChoice;
import com.schedulix.setup.entity.Section;
import com.schedulix.setup.repository.SectionRepository;
import com.schedulix.setup.entity.SectionSubject;
import com.schedulix.setup.repository.SectionSubjectRepository;
import com.schedulix.setup.entity.Subject;
import com.schedulix.setup.entity.SubjectKind;
import com.schedulix.setup.repository.SubjectRepository;
import com.schedulix.setup.entity.Teacher;
import com.schedulix.setup.repository.TeacherRepository;
import com.schedulix.setup.entity.TeacherSubject;
import com.schedulix.setup.repository.TeacherSubjectRepository;
import com.schedulix.timetable.service.BusyCellService.BusyCells;
import com.schedulix.timetable.dto.TimetableSettings.BreakSetting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.schedulix.timetable.entity.FixedLectureEntity;
import com.schedulix.timetable.repository.FixedLectureRepository;
import com.schedulix.timetable.entity.TeacherChoiceEntity;
import com.schedulix.timetable.repository.TeacherChoiceRepository;
import com.schedulix.timetable.entity.Timetable;
import com.schedulix.timetable.entity.TimetableClass;
import com.schedulix.timetable.repository.TimetableClassRepository;
import com.schedulix.timetable.dto.TimetableSettings;

@Service
public class EngineInputBuilder {

    private final TimetableClassRepository timetableClassRepository;
    private final TeacherChoiceRepository teacherChoiceRepository;
    private final FixedLectureRepository fixedLectureRepository;
    private final SectionRepository sectionRepository;
    private final SectionSubjectRepository sectionSubjectRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherRepository teacherRepository;
    private final TeacherSubjectRepository teacherSubjectRepository;
    private final TimetableSettingsSupport settingsSupport;
    private final BusyCellService busyCellService;

    public EngineInputBuilder(
            TimetableClassRepository timetableClassRepository,
            TeacherChoiceRepository teacherChoiceRepository,
            FixedLectureRepository fixedLectureRepository,
            SectionRepository sectionRepository,
            SectionSubjectRepository sectionSubjectRepository,
            SubjectRepository subjectRepository,
            TeacherRepository teacherRepository,
            TeacherSubjectRepository teacherSubjectRepository,
            TimetableSettingsSupport settingsSupport,
            BusyCellService busyCellService
    ) {
        this.timetableClassRepository = timetableClassRepository;
        this.teacherChoiceRepository = teacherChoiceRepository;
        this.fixedLectureRepository = fixedLectureRepository;
        this.sectionRepository = sectionRepository;
        this.sectionSubjectRepository = sectionSubjectRepository;
        this.subjectRepository = subjectRepository;
        this.teacherRepository = teacherRepository;
        this.teacherSubjectRepository = teacherSubjectRepository;
        this.settingsSupport = settingsSupport;
        this.busyCellService = busyCellService;
    }

    @Transactional(readOnly = true)
    public EngineInput build(Timetable timetable, boolean includeManualOverrides) {
        UUID deptId = timetable.getDepartmentId();
        TimetableSettings settings = settingsSupport.fromJson(timetable.getSettings());

        List<Integer> breakPositions = settings.breaks() == null
                ? List.of()
                : settings.breaks().stream().map(BreakSetting::afterLecture).toList();

        EngineSettings engineSettings = new EngineSettings(
                settings.workingDays().size(),
                settings.lecturesPerDay(),
                breakPositions,
                settings.maxPerTeacherPerDay()
        );

        Map<DayOfWeek, Integer> dayIndexByDay = new HashMap<>();
        for (int i = 0; i < settings.workingDays().size(); i++) {
            dayIndexByDay.put(
                    DayOfWeek.valueOf(settings.workingDays().get(i).trim().toUpperCase(Locale.ROOT)),
                    i
            );
        }

        // Load sections belonging to this timetable
        Map<UUID, Section> allSemSections = new LinkedHashMap<>();
        for (Section sec : sectionRepository.findBySemesterIdOrderByNameAsc(timetable.getSemesterId())) {
            allSemSections.put(sec.getId(), sec);
        }

        List<Section> sections = new ArrayList<>();
        for (TimetableClass tc : timetableClassRepository.findByTimetableId(timetable.getId())) {
            Section sec = allSemSections.get(tc.getSectionId());
            if (sec != null) {
                sections.add(sec);
            }
        }
        sections.sort(Comparator.comparing(Section::getName, String.CASE_INSENSITIVE_ORDER));

        List<EngineClass> engineClasses = sections.stream()
                .map(s -> new EngineClass(s.getId().toString(), "Section " + s.getName()))
                .toList();

        // Load plan lines
        List<PlanLine> planLines = new ArrayList<>();
        Set<UUID> referencedSubjectIds = new HashSet<>();
        for (Section sec : sections) {
            for (SectionSubject ss : sectionSubjectRepository.findBySectionId(sec.getId())) {
                planLines.add(new PlanLine(
                        sec.getId().toString(),
                        ss.getSubjectId().toString(),
                        ss.getLecturesPerWeek()
                ));
                referencedSubjectIds.add(ss.getSubjectId());
            }
        }

        // Load department subjects
        List<EngineSubject> engineSubjects = new ArrayList<>();
        for (Subject sub : subjectRepository.findByDepartmentIdOrderByCodeAsc(deptId)) {
            if (sub.isActive() || referencedSubjectIds.contains(sub.getId())) {
                engineSubjects.add(new EngineSubject(
                        sub.getId().toString(),
                        sub.getName(),
                        sub.getKind() == SubjectKind.LAB,
                        sub.getPeriodsInARow(),
                        sub.getRunTogetherGroup(),
                        sub.getSharedPlace()
                ));
            }
        }

        // Load teacher choices
        List<TeacherChoiceEntity> storedChoices = teacherChoiceRepository.findByTimetableId(timetable.getId());
        List<TeacherChoice> engineChoices = storedChoices.stream()
                .map(c -> new TeacherChoice(
                        c.getSectionId().toString(),
                        c.getSubjectId().toString(),
                        c.getTeacherId().toString()
                ))
                .toList();

        // Load active teachers + who can teach what
        List<Teacher> teachers = teacherRepository.findByDepartmentIdOrderByCodeAsc(deptId);
        Map<UUID, Set<String>> ableByTeacherId = new HashMap<>();
        for (Teacher t : teachers) {
            if (!t.isActive()) continue;
            Set<String> able = new HashSet<>();
            for (TeacherSubject ts : teacherSubjectRepository.findByTeacherId(t.getId())) {
                able.add(ts.getSubjectId().toString());
            }
            ableByTeacherId.put(t.getId(), able);
        }

        if (includeManualOverrides) {
            for (TeacherChoiceEntity c : storedChoices) {
                Set<String> able = ableByTeacherId.get(c.getTeacherId());
                if (able != null) {
                    able.add(c.getSubjectId().toString());
                }
            }
        }

        List<EngineTeacher> engineTeachers = new ArrayList<>();
        for (Teacher t : teachers) {
            if (!t.isActive()) continue;
            engineTeachers.add(new EngineTeacher(
                    t.getId().toString(),
                    t.getFullName(),
                    ableByTeacherId.getOrDefault(t.getId(), Set.of())
            ));
        }

        // Load fixed lectures
        List<FixedLecture> engineFixed = new ArrayList<>();
        for (FixedLectureEntity fl : fixedLectureRepository.findByTimetableId(timetable.getId())) {
            Integer dayIdx = dayIndexByDay.get(fl.getDayOfWeek());
            if (dayIdx != null) {
                engineFixed.add(new FixedLecture(
                        fl.getSectionId().toString(),
                        fl.getSubjectId().toString(),
                        dayIdx,
                        fl.getStartLecture()
                ));
            }
        }

        BusyCells busyCells = busyCellService.forDepartment(deptId, settings);

        return new EngineInput(
                engineSettings,
                engineClasses,
                engineSubjects,
                planLines,
                engineTeachers,
                engineChoices,
                engineFixed,
                busyCells.busyTeachers(),
                busyCells.busyPlaces()
        );
    }
}