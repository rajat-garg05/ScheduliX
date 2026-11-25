package com.schedulix.common;

import java.util.List;

public record CsvImportPreviewResponse(
        int totalRows,
        int toAdd,
        int toUpdate,
        int toSkip,
        int problemCount,
        String summary,
        List<CsvPreviewRow> rows
) {
    public static CsvImportPreviewResponse from(List<CsvPreviewRow> rows) {
        int toAdd = 0;
        int toUpdate = 0;
        int unchanged = 0;
        int problems = 0;

        for (CsvPreviewRow row : rows) {
            switch (row.status()) {
                case NEW -> toAdd++;
                case UPDATE -> toUpdate++;
                case UNCHANGED -> unchanged++;
                case PROBLEM -> problems++;
            }
        }

        int toSkip = unchanged + problems;
        String summary = toAdd + " will be added, " + toUpdate + " updated, " + toSkip + " skipped.";
        return new CsvImportPreviewResponse(rows.size(), toAdd, toUpdate, toSkip, problems, summary, rows);
    }
}