package com.schedulix.common;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CsvImportSupport {

    public static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;
    public static final int MAX_ROWS = 2000;

    public record ParsedRow(int rowNumber, Map<String, String> values) {
        public String get(String columnName) {
            return values.getOrDefault(columnName.toLowerCase(Locale.ROOT), "");
        }
    }

    public List<ParsedRow> parse(MultipartFile file, List<String> requiredColumns) {
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Please choose a CSV file to upload.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "FILE_TOO_LARGE",
                    "This file is larger than 2 MB. Please split it into smaller files."
            );
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "UNREADABLE_FILE",
                    "Could not read the uploaded file. Please save it as UTF-8 CSV and try again."
            );
        }

        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();

        try (CSVParser parser = format.parse(new StringReader(content))) {
            Map<String, String> headerLookup = new LinkedHashMap<>();
            for (String rawHeader : parser.getHeaderNames()) {
                if (rawHeader == null) continue;
                String cleaned = rawHeader.replace("\uFEFF", "").trim();
                if (!cleaned.isEmpty()) {
                    headerLookup.putIfAbsent(cleaned.toLowerCase(Locale.ROOT), rawHeader);
                }
            }

            List<String> missing = new ArrayList<>();
            for (String required : requiredColumns) {
                if (!headerLookup.containsKey(required.toLowerCase(Locale.ROOT))) {
                    missing.add(required);
                }
            }
            if (!missing.isEmpty()) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "MISSING_COLUMNS",
                        "Missing required column" + (missing.size() == 1 ? "" : "s") + ": "
                                + String.join(", ", missing) + ". Download the template to see the exact column names."
                );
            }

            List<ParsedRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                boolean allBlank = true;
                Map<String, String> values = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : headerLookup.entrySet()) {
                    String normalizedName = entry.getKey();
                    String originalHeader = entry.getValue();
                    String cell = record.isMapped(originalHeader) && record.isSet(originalHeader)
                            ? record.get(originalHeader)
                            : "";
                    String trimmed = cell == null ? "" : cell.trim();
                    if (!trimmed.isEmpty()) {
                        allBlank = false;
                    }
                    values.put(normalizedName, trimmed);
                }
                if (allBlank) {
                    continue;
                }
                if (rows.size() >= MAX_ROWS) {
                    throw new AppException(
                            HttpStatus.BAD_REQUEST,
                            "TOO_MANY_ROWS",
                            "This file has more than 2,000 rows. Please split it into smaller files."
                    );
                }
                int rowNumber = (int) record.getRecordNumber() + 1;
                rows.add(new ParsedRow(rowNumber, values));
            }

            if (rows.isEmpty()) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "NO_DATA_ROWS",
                        "This file has no data rows. Add at least one row below the header."
                );
            }
            return rows;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_CSV",
                    "Could not read this CSV file. Please check for unclosed quotes or broken lines."
            );
        }
    }
}