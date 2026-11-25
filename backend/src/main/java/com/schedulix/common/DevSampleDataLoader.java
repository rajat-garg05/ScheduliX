package com.schedulix.common;

import com.schedulix.auth.AccountStatus;
import com.schedulix.auth.Department;
import com.schedulix.auth.DepartmentRepository;
import com.schedulix.auth.LoginRequest;
import com.schedulix.auth.LoginResponse;
import com.schedulix.auth.UserAccount;
import com.schedulix.auth.UserAccountRepository;
import com.schedulix.auth.UserRole;
import com.schedulix.setup.PlanItemRequest;
import com.schedulix.setup.SectionRequest;
import com.schedulix.setup.SectionResponse;
import com.schedulix.setup.SemesterRequest;
import com.schedulix.setup.SemesterResponse;
import com.schedulix.setup.SubjectKind;
import com.schedulix.setup.SubjectRequest;
import com.schedulix.setup.SubjectResponse;
import com.schedulix.setup.TeacherRequest;
import com.schedulix.setup.TeacherResponse;
import com.schedulix.setup.TeacherSubjectsRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Profile("dev")
public class DevSampleDataLoader {

    private static final Logger log = LoggerFactory.getLogger(DevSampleDataLoader.class);

    public static final String DEMO_DEPARTMENT_NAME = "Computer Science & Engineering (Demo)";
    public static final String DEMO_HOD_EMAIL = "hod@cse-demo.edu";
    public static final String DEMO_HOD_PASSWORD = "HodDemo@123";

    private final Environment environment;
    private final DepartmentRepository departmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public DevSampleDataLoader(
            Environment environment,
            DepartmentRepository departmentRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.environment = environment;
        this.departmentRepository = departmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        boolean enabled = environment.getProperty("app.sample-data.enabled", Boolean.class, true);
        if (!enabled) {
            return;
        }
        int port = event.getApplicationContext().getEnvironment()
                .getProperty("local.server.port", Integer.class,
                        environment.getProperty("server.port", Integer.class, 8080));
        loadIfNeeded(port);
    }

    public void assertDevProfileActive() {
        if (!environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new IllegalStateException("Sample data loader refuses to run unless the 'dev' profile is active.");
        }
    }

    public boolean loadIfNeeded(int port) {
        assertDevProfileActive();

        if (userAccountRepository.findByEmail(DEMO_HOD_EMAIL).isPresent()) {
            log.info("Sample department already loaded. HOD login: {} / {} (teachers: ScheduliX@123)",
                    DEMO_HOD_EMAIL, DEMO_HOD_PASSWORD);
            return false;
        }

        Department dept = departmentRepository.save(new Department(DEMO_DEPARTMENT_NAME));
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                DEMO_HOD_EMAIL,
                passwordEncoder.encode(DEMO_HOD_PASSWORD),
                "Dr. Vikram Deshmukh (HOD)",
                UserRole.HOD,
                AccountStatus.ACTIVE,
                false,
                Instant.now()
        ));

        RestClient client = RestClient.create("http://localhost:" + port);
        LoginResponse login = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest(DEMO_HOD_EMAIL, DEMO_HOD_PASSWORD))
                .retrieve()
                .body(LoginResponse.class);
        if (login == null || login.accessToken() == null) {
            throw new IllegalStateException("Failed to log in as demo HOD while loading sample data.");
        }
        String auth = "Bearer " + login.accessToken();

        // 1. Teachers
        Map<String, UUID> teacherIdByCode = new HashMap<>();
        for (CSVRecord row : readCsv("sample/teachers.csv")) {
            TeacherResponse created = client.post()
                    .uri("/api/teachers")
                    .header(HttpHeaders.AUTHORIZATION, auth)
                    .body(new TeacherRequest(
                            row.get("facultyCode").trim(),
                            row.get("fullName").trim(),
                            row.get("officialEmail").trim(),
                            row.get("designation").trim()
                    ))
                    .retrieve()
                    .body(TeacherResponse.class);
            if (created != null) {
                teacherIdByCode.put(created.code(), created.id());
            }
        }

        // 2. Subjects
        Map<String, UUID> subjectIdByCode = new HashMap<>();
        for (CSVRecord row : readCsv("sample/subjects.csv")) {
            String group = blankToNull(row.get("runsTogetherGroup"));
            String place = blankToNull(row.get("sharedPlace"));
            SubjectResponse created = client.post()
                    .uri("/api/subjects")
                    .header(HttpHeaders.AUTHORIZATION, auth)
                    .body(new SubjectRequest(
                            row.get("subjectCode").trim(),
                            row.get("name").trim(),
                            SubjectKind.valueOf(row.get("classification").trim().toUpperCase()),
                            Integer.parseInt(row.get("periodsInARow").trim()),
                            group,
                            place
                    ))
                    .retrieve()
                    .body(SubjectResponse.class);
            if (created != null) {
                subjectIdByCode.put(created.code(), created.id());
            }
        }

        // 3. Who can teach what
        Map<UUID, List<UUID>> subjectsByTeacher = new LinkedHashMap<>();
        for (CSVRecord row : readCsv("sample/who_can_teach_what.csv")) {
            UUID teacherId = teacherIdByCode.get(row.get("facultyCode").trim());
            UUID subjectId = subjectIdByCode.get(row.get("subjectCode").trim());
            if (teacherId != null && subjectId != null) {
                subjectsByTeacher.computeIfAbsent(teacherId, k -> new ArrayList<>()).add(subjectId);
            }
        }
        for (Map.Entry<UUID, List<UUID>> entry : subjectsByTeacher.entrySet()) {
            client.put()
                    .uri("/api/teachers/" + entry.getKey() + "/subjects")
                    .header(HttpHeaders.AUTHORIZATION, auth)
                    .body(new TeacherSubjectsRequest(entry.getValue()))
                    .retrieve()
                    .toBodilessEntity();
        }

        // 4. Semester 3 (Sections A & B) and Semester 5 (Sections A & B)
        loadSemesterWithPlans(client, auth, 3, "Semester 3", List.of("A", "B"), "sample/semester3_plan.csv", subjectIdByCode);
        loadSemesterWithPlans(client, auth, 5, "Semester 5", List.of("A", "B"), "sample/semester5_plan.csv", subjectIdByCode);

        log.info("============================================================");
        log.info("ScheduliX DEV sample data loaded (no timetables created yet)");
        log.info("  HOD login      : {} / {}", DEMO_HOD_EMAIL, DEMO_HOD_PASSWORD);
        log.info("  Teacher default: ScheduliX@123 (e.g. arjun.mehta@cse-demo.edu)");
        log.info("============================================================");
        return true;
    }

    private void loadSemesterWithPlans(
            RestClient client,
            String auth,
            int number,
            String name,
            List<String> sectionNames,
            String planCsvPath,
            Map<String, UUID> subjectIdByCode
    ) {
        SemesterResponse sem = client.post()
                .uri("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .body(new SemesterRequest(number, name))
                .retrieve()
                .body(SemesterResponse.class);
        if (sem == null) return;

        List<PlanItemRequest> planItems = new ArrayList<>();
        for (CSVRecord row : readCsv(planCsvPath)) {
            UUID subId = subjectIdByCode.get(row.get("subjectCode").trim());
            int count = Integer.parseInt(row.get("lecturesPerWeek").trim());
            if (subId != null) {
                planItems.add(new PlanItemRequest(subId, count));
            }
        }

        for (String secName : sectionNames) {
            SectionResponse sec = client.post()
                    .uri("/api/semesters/" + sem.id() + "/sections")
                    .header(HttpHeaders.AUTHORIZATION, auth)
                    .body(new SectionRequest(secName))
                    .retrieve()
                    .body(SectionResponse.class);
            if (sec != null) {
                client.put()
                        .uri("/api/sections/" + sec.id() + "/plan")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .body(planItems)
                        .retrieve()
                        .toBodilessEntity();
            }
        }
    }

    private List<CSVRecord> readCsv(String classpathLocation) {
        ClassPathResource res = new ClassPathResource(classpathLocation);
        try (InputStreamReader reader = new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            return parser.getRecords();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read sample CSV: " + classpathLocation, ex);
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}