package com.schedulix.setup;

import java.util.UUID;

public interface SavedTimetableLookup {

    boolean hasSavedTimetable(UUID sectionId);
}