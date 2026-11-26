package com.schedulix.setup;

import com.schedulix.setup.service.PlanValidator.PlanEntry;
import com.schedulix.setup.service.PlanValidator.PlanSubjectInfo;
import com.schedulix.setup.service.PlanValidator.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.schedulix.setup.service.PlanValidator;
import com.schedulix.setup.entity.SubjectKind;

import static org.assertj.core.api.Assertions.assertThat;

class PlanValidatorTest {

    private final PlanValidator validator = new PlanValidator();

    @Test
    void acceptsValidPlanWithPairedLabsAndEmitsNoWarningsWhenAllHaveTeachers() {
        PlanSubjectInfo ds = theory("CS301", "Data Structures");
        PlanSubjectInfo dsaLab = lab("CS351", "DSA Lab", 2, "LABGROUP-S3");
        PlanSubjectInfo javaLab = lab("CS352", "Java Lab", 2, "LABGROUP-S3");

        ValidationResult result = validator.validate(
                List.of(
                        new PlanEntry(ds.id(), 4),
                        new PlanEntry(dsaLab.id(), 2),
                        new PlanEntry(javaLab.id(), 2)
                ),
                mapOf(ds, dsaLab, javaLab),
                Set.of(ds.id(), dsaLab.id(), javaLab.id())
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void rejectsLecturesNotAMultipleOfPeriodsInARow() {
        PlanSubjectInfo dsaLab = lab("CS351", "DSA Lab", 2, null);
        PlanSubjectInfo studioLab = lab("AR301", "Design Studio", 3, null);

        ValidationResult result = validator.validate(
                List.of(
                        new PlanEntry(dsaLab.id(), 3),
                        new PlanEntry(studioLab.id(), 4)
                ),
                mapOf(dsaLab, studioLab),
                Set.of(dsaLab.id(), studioLab.id())
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).containsExactly(
                "DSA Lab needs 2 lectures in a row, so use 2, 4, 6 ....",
                "Design Studio needs 3 lectures in a row, so use 3, 6, 9 ...."
        );
    }

    @Test
    void rejectsMissingPeerInPairedLabGroup() {
        PlanSubjectInfo dbmsLab = lab("CS551", "DBMS Lab", 2, "LABGROUP-S5");
        PlanSubjectInfo osLab = lab("CS552", "OS Lab", 2, "LABGROUP-S5");

        ValidationResult result = validator.validate(
                List.of(new PlanEntry(dbmsLab.id(), 2)),
                mapOf(dbmsLab, osLab),
                Set.of(dbmsLab.id(), osLab.id())
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).containsExactly(
                "DBMS Lab runs at the same time as OS Lab, but OS Lab is missing from this plan."
        );
    }

    @Test
    void rejectsUnequalLecturesInPairedLabGroup() {
        PlanSubjectInfo dsaLab = lab("CS351", "DSA Lab", 2, "LABGROUP-S3");
        PlanSubjectInfo javaLab = lab("CS352", "Java Lab", 2, "LABGROUP-S3");

        ValidationResult result = validator.validate(
                List.of(
                        new PlanEntry(dsaLab.id(), 2),
                        new PlanEntry(javaLab.id(), 4)
                ),
                mapOf(dsaLab, javaLab),
                Set.of(dsaLab.id(), javaLab.id())
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).containsExactly(
                "DSA Lab has 2 lectures but Java Lab has 4. They must be equal."
        );
    }

    @Test
    void rejectsInactiveSubjectAndDuplicateEntries() {
        PlanSubjectInfo inactive = new PlanSubjectInfo(
                UUID.randomUUID(), "OLD101", "Old Subject", SubjectKind.THEORY, 1, null, false
        );
        PlanSubjectInfo ds = theory("CS301", "Data Structures");

        ValidationResult result = validator.validate(
                List.of(
                        new PlanEntry(inactive.id(), 2),
                        new PlanEntry(ds.id(), 3),
                        new PlanEntry(ds.id(), 4)
                ),
                mapOf(inactive, ds),
                Set.of(ds.id())
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("Old Subject").contains("inactive"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("Data Structures").contains("more than once"));
    }

    @Test
    void returnsWarningWhenNoTeacherCanTeachASubject() {
        PlanSubjectInfo hv = theory("HS306", "Human Values");

        ValidationResult result = validator.validate(
                List.of(new PlanEntry(hv.id(), 2)),
                mapOf(hv),
                Set.of()
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.warnings()).containsExactly("Nobody is listed as able to teach Human Values.");
    }

    private PlanSubjectInfo theory(String code, String name) {
        return new PlanSubjectInfo(UUID.randomUUID(), code, name, SubjectKind.THEORY, 1, null, true);
    }

    private PlanSubjectInfo lab(String code, String name, int periods, String group) {
        return new PlanSubjectInfo(UUID.randomUUID(), code, name, SubjectKind.LAB, periods, group, true);
    }

    private Map<UUID, PlanSubjectInfo> mapOf(PlanSubjectInfo... subjects) {
        Map<UUID, PlanSubjectInfo> map = new LinkedHashMap<>();
        for (PlanSubjectInfo s : subjects) {
            map.put(s.id(), s);
        }
        return map;
    }
}