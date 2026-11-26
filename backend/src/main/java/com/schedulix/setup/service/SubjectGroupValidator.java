package com.schedulix.setup.service;

import com.schedulix.common.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.schedulix.setup.entity.Subject;

@Component
public class SubjectGroupValidator {

    public List<String> validateMemberAgainstPeers(
            UUID editingId,
            String name,
            short periodsInARow,
            String groupName,
            String sharedPlace,
            List<Subject> existingPeers
    ) {
        List<String> warnings = new ArrayList<>();
        if (groupName == null) {
            return warnings;
        }

        List<Subject> others = existingPeers.stream()
                .filter(s -> editingId == null || !s.getId().equals(editingId))
                .filter(Subject::isActive)
                .toList();

        for (Subject other : others) {
            if (other.getPeriodsInARow() != periodsInARow) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "GROUP_PERIOD_MISMATCH",
                        name + " has " + periodsInARow + " lectures in a row, but " + other.getName()
                                + " in group " + groupName + " has " + other.getPeriodsInARow()
                                + ". Labs that run at the same time must have the same lectures in a row."
                );
            }

            if (sharedPlace != null && other.getSharedPlace() != null
                    && sharedPlace.equalsIgnoreCase(other.getSharedPlace())) {
                throw new AppException(
                        HttpStatus.BAD_REQUEST,
                        "GROUP_SAME_PLACE",
                        name + " and " + other.getName() + " both use " + sharedPlace
                                + ", but they run at the same time. Give them different places."
                );
            }
        }

        if (others.isEmpty()) {
            warnings.add("Group " + groupName
                    + " has only 1 lab right now. Add at least one more lab to this group before using it in a semester plan.");
        }

        return warnings;
    }

    public List<String> validateCompleteGroup(String groupName, List<Subject> members) {
        List<String> problems = new ArrayList<>();
        if (members.size() < 2) {
            problems.add("Group " + groupName + " needs at least two labs.");
            return problems;
        }

        short expectedPeriods = members.get(0).getPeriodsInARow();
        for (int i = 1; i < members.size(); i++) {
            Subject current = members.get(i);
            if (current.getPeriodsInARow() != expectedPeriods) {
                problems.add(current.getName() + " and " + members.get(0).getName()
                        + " are in group " + groupName
                        + " but have different lectures in a row. Give them the same number.");
                break;
            }
        }

        for (int i = 0; i < members.size(); i++) {
            String placeA = members.get(i).getSharedPlace();
            if (placeA == null) continue;
            for (int j = i + 1; j < members.size(); j++) {
                String placeB = members.get(j).getSharedPlace();
                if (placeB != null && placeA.toLowerCase(Locale.ROOT).equals(placeB.toLowerCase(Locale.ROOT))) {
                    problems.add(members.get(i).getName() + " and " + members.get(j).getName()
                            + " both use " + placeA + ", but they run at the same time. Give them different places.");
                }
            }
        }

        return problems;
    }
}