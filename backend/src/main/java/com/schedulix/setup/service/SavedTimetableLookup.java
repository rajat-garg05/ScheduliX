package com.schedulix.setup.service;

import java.util.UUID;

public interface SavedTimetableLookup {

    boolean hasSavedTimetable(UUID sectionId);
}