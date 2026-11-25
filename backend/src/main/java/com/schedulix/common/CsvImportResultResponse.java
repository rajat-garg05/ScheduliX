package com.schedulix.common;

public record CsvImportResultResponse(
        int added,
        int updated,
        int skipped,
        int problems,
        String message
) {
}