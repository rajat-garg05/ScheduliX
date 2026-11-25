package com.schedulix.engine;

import java.util.List;

public record EngineResult(
        boolean ok,
        List<PlacedLecture> lectures,
        List<String> messages
) {
    public EngineResult {
        lectures = lectures == null ? List.of() : List.copyOf(lectures);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}