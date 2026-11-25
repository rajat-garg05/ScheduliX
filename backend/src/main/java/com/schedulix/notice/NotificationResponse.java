package com.schedulix.notice;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String text,
        String link,
        Instant createdAt,
        Instant readAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getText(),
                n.getLink(),
                n.getCreatedAt(),
                n.getReadAt()
        );
    }
}