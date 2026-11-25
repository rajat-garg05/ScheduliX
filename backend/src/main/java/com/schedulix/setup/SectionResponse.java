package com.schedulix.setup;

import java.util.UUID;

public record SectionResponse(
        UUID id,
        UUID semesterId,
        String name,
        boolean locked
) {
    public static SectionResponse from(Section section, boolean locked) {
        return new SectionResponse(section.getId(), section.getSemesterId(), section.getName(), locked);
    }
}