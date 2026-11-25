package com.schedulix.engine;

public record EngineSubject(
        String id,
        String name,
        boolean lab,
        int periodsInARow,
        String groupName,
        String placeName
) {
}