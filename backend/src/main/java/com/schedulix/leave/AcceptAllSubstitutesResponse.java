package com.schedulix.leave;

import java.time.LocalDate;

public record AcceptAllSubstitutesResponse(
        LocalDate date,
        int acceptedCount,
        int skippedNeededCount
) {
}