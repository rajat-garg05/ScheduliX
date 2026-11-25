package com.schedulix.engine;

import java.util.List;

public record Suggestion(
        List<TeacherChoice> choices,
        List<String> messages
) {
    public Suggestion {
        choices = choices == null ? List.of() : List.copyOf(choices);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}