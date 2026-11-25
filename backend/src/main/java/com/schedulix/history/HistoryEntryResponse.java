package com.schedulix.history;

import java.time.Instant;
import java.util.UUID;

public record HistoryEntryResponse(
        UUID id,
        String userName,
        String kind,
        String text,
        Instant createdAt
) {
    public static HistoryEntryResponse from(HistoryEntry entry) {
        return new HistoryEntryResponse(
                entry.getId(),
                entry.getUserName(),
                entry.getKind(),
                entry.getText(),
                entry.getCreatedAt()
        );
    }
}