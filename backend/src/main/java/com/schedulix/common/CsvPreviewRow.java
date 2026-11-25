package com.schedulix.common;

import java.util.Map;

public record CsvPreviewRow(
        int rowNumber,
        CsvRowStatus status,
        String message,
        Map<String, String> values
) {
}