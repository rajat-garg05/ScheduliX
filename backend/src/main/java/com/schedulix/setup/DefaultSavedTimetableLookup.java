package com.schedulix.setup;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DefaultSavedTimetableLookup implements SavedTimetableLookup {

    @Override
    public boolean hasSavedTimetable(UUID sectionId) {
        return false;
    }
}