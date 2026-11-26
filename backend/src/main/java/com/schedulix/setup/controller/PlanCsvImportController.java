package com.schedulix.setup.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import com.schedulix.setup.service.PlanCsvImportService;
import com.schedulix.setup.dto.PlanImportPreviewResponse;
import com.schedulix.setup.dto.PlanImportResultResponse;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('HOD')")
public class PlanCsvImportController {

    private final PlanCsvImportService importService;

    public PlanCsvImportController(PlanCsvImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/sections/{id}/plan/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlanImportPreviewResponse preview(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "otherSectionIds", required = false) List<UUID> otherSectionIds
    ) {
        return importService.preview(id, file, otherSectionIds);
    }

    @PostMapping(value = "/sections/{id}/plan/import/confirm", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlanImportResultResponse confirm(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "otherSectionIds", required = false) List<UUID> otherSectionIds
    ) {
        return importService.confirm(id, file, otherSectionIds);
    }

    @GetMapping("/plan/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"semester_plan_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(importService.template().getBytes(StandardCharsets.UTF_8));
    }
}