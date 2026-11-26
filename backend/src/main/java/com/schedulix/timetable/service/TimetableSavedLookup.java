package com.schedulix.timetable.service;

import com.schedulix.setup.service.SavedTimetableLookup;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;
import com.schedulix.timetable.repository.TimetableClassRepository;

@Primary
@Component
public class TimetableSavedLookup implements SavedTimetableLookup {

    private final TimetableClassRepository timetableClassRepository;

    public TimetableSavedLookup(TimetableClassRepository timetableClassRepository) {
        this.timetableClassRepository = timetableClassRepository;
    }

    @Override
    public boolean hasSavedTimetable(UUID sectionId) {
        return timetableClassRepository.existsBySectionIdAndSavedTrue(sectionId);
    }
}