package com.schedulix.engine;

import java.util.Set;

public record EngineTeacher(String id, String name, Set<String> ableSubjectIds) {
    public EngineTeacher {
        ableSubjectIds = ableSubjectIds == null ? Set.of() : Set.copyOf(ableSubjectIds);
    }
}