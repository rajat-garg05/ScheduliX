package com.schedulix.setup.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.schedulix.setup.entity.Subject;
import com.schedulix.setup.entity.SubjectKind;

@Component
public class PlanValidator {

    public record PlanSubjectInfo(
            UUID id,
            String code,
            String name,
            SubjectKind kind,
            int periodsInARow,
            String runTogetherGroup,
            boolean active
    ) {
        public static PlanSubjectInfo from(Subject s) {
            return new PlanSubjectInfo(
                    s.getId(),
                    s.getCode(),
                    s.getName(),
                    s.getKind(),
                    s.getPeriodsInARow(),
                    s.getRunTogetherGroup(),
                    s.isActive()
            );
        }
    }

    public record PlanEntry(UUID subjectId, int lecturesPerWeek) {
    }

    public record ValidationResult(
            List<String> errors,
            List<String> warnings,
            Map<UUID, String> errorBySubjectId
    ) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    public ValidationResult validate(
            List<PlanEntry> entries,
            Map<UUID, PlanSubjectInfo> departmentSubjectsById,
            Set<UUID> subjectIdsWithTeacher
    ) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<UUID, String> errorBySubjectId = new LinkedHashMap<>();

        if (entries == null || entries.isEmpty()) {
            return new ValidationResult(errors, warnings, errorBySubjectId);
        }

        Map<UUID, Integer> lecturesBySubjectId = new LinkedHashMap<>();

        for (PlanEntry entry : entries) {
            if (entry == null || entry.subjectId() == null) {
                errors.add("One of the rows is missing a subject.");
                continue;
            }

            UUID subjectId = entry.subjectId();
            PlanSubjectInfo subject = departmentSubjectsById.get(subjectId);
            if (subject == null) {
                errors.add("One of the chosen subjects was not found.");
                continue;
            }

            if (lecturesBySubjectId.containsKey(subjectId)) {
                String msg = subject.name() + " (" + subject.code() + ") is listed more than once in this plan.";
                errors.add(msg);
                errorBySubjectId.putIfAbsent(subjectId, msg);
                continue;
            }

            if (!subject.active()) {
                String msg = subject.name() + " (" + subject.code() + ") is inactive and cannot be used in a plan.";
                errors.add(msg);
                errorBySubjectId.putIfAbsent(subjectId, msg);
                continue;
            }

            int lectures = entry.lecturesPerWeek();
            if (lectures <= 0) {
                String msg = subject.name() + " must have at least 1 lecture per week.";
                errors.add(msg);
                errorBySubjectId.putIfAbsent(subjectId, msg);
                continue;
            }

            int blockSize = Math.max(1, subject.periodsInARow());
            if (lectures % blockSize != 0) {
                String msg = subject.name() + " needs " + blockSize + " lectures in a row, so use "
                        + multiplesHint(blockSize) + ".";
                errors.add(msg);
                errorBySubjectId.putIfAbsent(subjectId, msg);
                continue;
            }

            lecturesBySubjectId.put(subjectId, lectures);
        }

        // Index active LAB subjects by run-together group
        Map<String, List<PlanSubjectInfo>> activeLabsByGroup = new LinkedHashMap<>();
        for (PlanSubjectInfo subject : departmentSubjectsById.values()) {
            if (subject.active() && subject.kind() == SubjectKind.LAB && subject.runTogetherGroup() != null) {
                String key = subject.runTogetherGroup().toLowerCase(Locale.ROOT);
                activeLabsByGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(subject);
            }
        }
        for (List<PlanSubjectInfo> members : activeLabsByGroup.values()) {
            members.sort(Comparator.comparing(PlanSubjectInfo::code, String.CASE_INSENSITIVE_ORDER));
        }

        // Check every paired-lab group that appears in this plan
        Set<String> checkedGroups = new LinkedHashSet<>();
        for (UUID subjectId : lecturesBySubjectId.keySet()) {
            PlanSubjectInfo subject = departmentSubjectsById.get(subjectId);
            if (subject.kind() != SubjectKind.LAB || subject.runTogetherGroup() == null) {
                continue;
            }

            String groupKey = subject.runTogetherGroup().toLowerCase(Locale.ROOT);
            if (!checkedGroups.add(groupKey)) {
                continue;
            }

            List<PlanSubjectInfo> groupMembers = activeLabsByGroup.getOrDefault(groupKey, List.of());
            if (groupMembers.size() < 2) {
                String msg = "Group " + subject.runTogetherGroup()
                        + " has fewer than two active labs. Add another lab to the group before using it in a plan.";
                errors.add(msg);
                errorBySubjectId.putIfAbsent(subjectId, msg);
                continue;
            }

            List<PlanSubjectInfo> present = new ArrayList<>();
            List<PlanSubjectInfo> missing = new ArrayList<>();
            for (PlanSubjectInfo member : groupMembers) {
                if (lecturesBySubjectId.containsKey(member.id())) {
                    present.add(member);
                } else {
                    missing.add(member);
                }
            }

            if (!missing.isEmpty()) {
                PlanSubjectInfo anchor = present.get(0);
                for (PlanSubjectInfo absent : missing) {
                    String msg = anchor.name() + " runs at the same time as " + absent.name()
                            + ", but " + absent.name() + " is missing from this plan.";
                    errors.add(msg);
                    for (PlanSubjectInfo p : present) {
                        errorBySubjectId.putIfAbsent(p.id(), msg);
                    }
                }
                continue;
            }

            PlanSubjectInfo first = present.get(0);
            int expectedLectures = lecturesBySubjectId.get(first.id());
            for (int i = 1; i < present.size(); i++) {
                PlanSubjectInfo other = present.get(i);
                int otherLectures = lecturesBySubjectId.get(other.id());
                if (otherLectures != expectedLectures) {
                    String msg = first.name() + " has " + expectedLectures + " lectures but "
                            + other.name() + " has " + otherLectures + ". They must be equal.";
                    errors.add(msg);
                    errorBySubjectId.putIfAbsent(first.id(), msg);
                    errorBySubjectId.putIfAbsent(other.id(), msg);
                }
            }
        }

        // Warnings for subjects that no active teacher can teach yet
        for (UUID subjectId : lecturesBySubjectId.keySet()) {
            if (!subjectIdsWithTeacher.contains(subjectId)) {
                PlanSubjectInfo subject = departmentSubjectsById.get(subjectId);
                warnings.add("Nobody is listed as able to teach " + subject.name() + ".");
            }
        }

        return new ValidationResult(errors, warnings, errorBySubjectId);
    }

    private String multiplesHint(int blockSize) {
        return blockSize + ", " + (blockSize * 2) + ", " + (blockSize * 3) + " ...";
    }
}