package com.schedulix.timetable;

import com.schedulix.setup.SavedTimetableLookup;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

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