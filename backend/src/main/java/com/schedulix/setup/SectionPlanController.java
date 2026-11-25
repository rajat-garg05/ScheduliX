package com.schedulix.setup;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sections")
@PreAuthorize("hasRole('HOD')")
public class SectionPlanController {

    private final SectionPlanService sectionPlanService;

    public SectionPlanController(SectionPlanService sectionPlanService) {
        this.sectionPlanService = sectionPlanService;
    }

    @GetMapping("/{id}/plan")
    public SectionPlanResponse getPlan(@PathVariable UUID id) {
        return sectionPlanService.getPlan(id);
    }

    @PutMapping("/{id}/plan")
    public SectionPlanResponse replacePlan(
            @PathVariable UUID id,
            @RequestBody List<PlanItemRequest> items
    ) {
        return sectionPlanService.replacePlan(id, items);
    }
}