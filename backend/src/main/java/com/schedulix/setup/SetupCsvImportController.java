package com.schedulix.setup;

import com.schedulix.common.CsvImportMode;
import com.schedulix.common.CsvImportPreviewResponse;
import com.schedulix.common.CsvImportResultResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('HOD')")
public class SetupCsvImportController {

    private final SetupCsvImportService importService;

    public SetupCsvImportController(SetupCsvImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/teachers/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportPreviewResponse previewTeachers(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "ADD_ONLY") CsvImportMode mode
    ) {
        return importService.previewTeachers(file, mode);
    }

    @PostMapping(value = "/teachers/import/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportResultResponse confirmTeachers(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "ADD_ONLY") CsvImportMode mode
    ) {
        return importService.confirmTeachers(file, mode);
    }

    @GetMapping("/teachers/template")
    public ResponseEntity<byte[]> teachersTemplate() {
        return csvDownload("teachers_template.csv", importService.teachersTemplate());
    }

    @PostMapping(value = "/subjects/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportPreviewResponse previewSubjects(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "ADD_ONLY") CsvImportMode mode
    ) {
        return importService.previewSubjects(file, mode);
    }

    @PostMapping(value = "/subjects/import/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportResultResponse confirmSubjects(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "ADD_ONLY") CsvImportMode mode
    ) {
        return importService.confirmSubjects(file, mode);
    }

    @GetMapping("/subjects/template")
    public ResponseEntity<byte[]> subjectsTemplate() {
        return csvDownload("subjects_template.csv", importService.subjectsTemplate());
    }

    @PostMapping(value = "/teacher-subjects/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportPreviewResponse previewTeacherSubjects(@RequestParam("file") MultipartFile file) {
        return importService.previewTeacherSubjects(file);
    }

    @PostMapping(value = "/teacher-subjects/import/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CsvImportResultResponse confirmTeacherSubjects(@RequestParam("file") MultipartFile file) {
        return importService.confirmTeacherSubjects(file);
    }

    @GetMapping("/teacher-subjects/template")
    public ResponseEntity<byte[]> teacherSubjectsTemplate() {
        return csvDownload("who_can_teach_what_template.csv", importService.teacherSubjectsTemplate());
    }

    private ResponseEntity<byte[]> csvDownload(String filename, String content) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}