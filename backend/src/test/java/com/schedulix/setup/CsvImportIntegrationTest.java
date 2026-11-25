package com.schedulix.setup;

import com.schedulix.auth.AccountService;
import com.schedulix.auth.AccountStatus;
import com.schedulix.auth.ChangePasswordRequest;
import com.schedulix.auth.Department;
import com.schedulix.auth.DepartmentRepository;
import com.schedulix.auth.EmailSender;
import com.schedulix.auth.LoginRequest;
import com.schedulix.auth.LoginResponse;
import com.schedulix.auth.UserAccount;
import com.schedulix.auth.UserAccountRepository;
import com.schedulix.auth.UserRole;
import com.schedulix.common.ApiError;
import com.schedulix.common.CsvImportPreviewResponse;
import com.schedulix.common.CsvImportResultResponse;
import com.schedulix.common.CsvRowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CsvImportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    TeacherRepository teacherRepository;

    @Autowired
    SubjectRepository subjectRepository;

    @Autowired
    TeacherSubjectRepository teacherSubjectRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    EmailSender emailSender;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.create("http://localhost:" + port);
    }

    @Test
    void loadsFullSampleDepartmentWith10Teachers15SubjectsAnd32Links() {
        Department dept = departmentRepository.save(new Department("CSE Sample " + UUID.randomUUID()));
        String token = createHodAndLogin(dept, "hod.sample@college.edu");

        // Add UTF-8 BOM to verify BOM stripping works
        String teachersCsv = "\uFEFF" + """
                facultyCode,fullName,officialEmail,designation
                T001,Dr. Rajesh Kulkarni,rajesh.kulkarni@cse-demo.edu,Professor
                T002,Dr. Meera Iyer,meera.iyer@cse-demo.edu,Professor
                T003,Prof. Arjun Mehta,arjun.mehta@cse-demo.edu,Associate Professor
                T004,Prof. Pooja Joshi,pooja.joshi@cse-demo.edu,Assistant Professor
                T005,Prof. Rohit Sharma,rohit.sharma@cse-demo.edu,Assistant Professor
                T006,Prof. Neha Gupta,neha.gupta@cse-demo.edu,Assistant Professor
                T007,Prof. Sandeep Patil,sandeep.patil@cse-demo.edu,Assistant Professor
                T008,Prof. Ananya Das,ananya.das@cse-demo.edu,Assistant Professor
                T009,Prof. Farah Khan,farah.khan@cse-demo.edu,Visiting Faculty
                T010,Prof. Imran Sheikh,imran.sheikh@cse-demo.edu,Assistant Professor
                """;

        // Preview saves nothing
        CsvImportPreviewResponse tPreview = postCsv(
                token, "/api/teachers/import/preview", teachersCsv, "ADD_ONLY", CsvImportPreviewResponse.class
        );
        assertThat(tPreview.toAdd()).isEqualTo(10);
        assertThat(tPreview.problemCount()).isEqualTo(0);
        assertThat(teacherRepository.findByDepartmentIdOrderByCodeAsc(dept.getId())).isEmpty();

        // Confirm saves all 10
        CsvImportResultResponse tResult = postCsv(
                token, "/api/teachers/import/confirm", teachersCsv, "ADD_ONLY", CsvImportResultResponse.class
        );
        assertThat(tResult.added()).isEqualTo(10);
        assertThat(teacherRepository.findByDepartmentIdOrderByCodeAsc(dept.getId())).hasSize(10);

        // Subjects (15 rows, including two paired lab groups)
        String subjectsCsv = """
                subjectCode,name,classification,periodsInARow,runsTogetherGroup,sharedPlace
                CS301,Data Structures,THEORY,1,,
                MA301,Discrete Mathematics,THEORY,1,,
                CS303,Computer Organization,THEORY,1,,
                CS304,Object Oriented Programming,THEORY,1,,
                CS305,Digital Logic Design,THEORY,1,,
                HS306,Universal Human Values,THEORY,1,,
                CS351,Data Structures Lab,LAB,2,LABGROUP-S3,Computer Lab 1
                CS352,Java Programming Lab,LAB,2,LABGROUP-S3,Computer Lab 2
                CS501,Database Management Systems,THEORY,1,,
                CS502,Operating Systems,THEORY,1,,
                CS503,Computer Networks,THEORY,1,,
                CS504,Design and Analysis of Algorithms,THEORY,1,,
                CS551,DBMS Lab,LAB,2,LABGROUP-S5,Computer Lab 1
                CS552,Operating Systems Lab,LAB,2,LABGROUP-S5,Computer Lab 2
                CS553,Computer Networks Lab,LAB,2,,Networks Lab
                """;

        CsvImportResultResponse sResult = postCsv(
                token, "/api/subjects/import/confirm", subjectsCsv, "ADD_ONLY", CsvImportResultResponse.class
        );
        assertThat(sResult.added()).isEqualTo(15);
        assertThat(sResult.problems()).isEqualTo(0);

        // Who can teach what (32 links)
        String linksCsv = """
                facultyCode,subjectCode
                T003,CS301
                T004,CS301
                T008,CS301
                T002,MA301
                T010,MA301
                T005,CS303
                T006,CS303
                T004,CS304
                T008,CS304
                T007,CS304
                T005,CS305
                T006,CS305
                T009,HS306
                T003,CS351
                T004,CS351
                T004,CS352
                T008,CS352
                T007,CS352
                T001,CS501
                T007,CS501
                T005,CS502
                T006,CS502
                T006,CS503
                T010,CS503
                T002,CS504
                T003,CS504
                T001,CS551
                T007,CS551
                T005,CS552
                T006,CS552
                T006,CS553
                T010,CS553
                """;

        CsvImportResultResponse lResult = postCsv(
                token, "/api/teacher-subjects/import/confirm", linksCsv, null, CsvImportResultResponse.class
        );
        assertThat(lResult.added()).isEqualTo(32);
        assertThat(lResult.problems()).isEqualTo(0);
    }

    @Test
    void teacherUpdateModeNeverResetsPasswordAndReportsBadRowsClearly() {
        Department dept = departmentRepository.save(new Department("Update Dept " + UUID.randomUUID()));
        String token = createHodAndLogin(dept, "hod.upd@college.edu");

        String initialCsv = """
                facultyCode,fullName,officialEmail,designation
                T10,Prof. Anita Roy,anita.roy@upd-demo.edu,Assistant Professor
                """;
        postCsv(token, "/api/teachers/import/confirm", initialCsv, "ADD_ONLY", CsvImportResultResponse.class);

        // Teacher changes default password
        LoginResponse firstLogin = login("anita.roy@upd-demo.edu", AccountService.DEFAULT_TEACHER_PASSWORD);
        client.post()
                .uri("/api/auth/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstLogin.accessToken())
                .body(new ChangePasswordRequest(AccountService.DEFAULT_TEACHER_PASSWORD, "AnitaPersonal123!"))
                .retrieve()
                .toBodilessEntity();

        // Re-import in ADD_AND_UPDATE with renamed teacher, new teacher, and one bad row (missing email)
        String updateCsv = """
                officialEmail,facultyCode,fullName,designation
                anita.new@upd-demo.edu,T10,Dr. Anita Roy,Associate Professor
                vikram@upd-demo.edu,T11,Prof. Vikram Sen,Assistant Professor
                ,T12,Prof. Missing Email,Professor
                """;

        CsvImportPreviewResponse preview = postCsv(
                token, "/api/teachers/import/preview", updateCsv, "ADD_AND_UPDATE", CsvImportPreviewResponse.class
        );
        assertThat(preview.toAdd()).isEqualTo(1);
        assertThat(preview.toUpdate()).isEqualTo(1);
        assertThat(preview.problemCount()).isEqualTo(1);
        assertThat(preview.rows().get(2).status()).isEqualTo(CsvRowStatus.PROBLEM);
        assertThat(preview.rows().get(2).message()).contains("Row 4").contains("email is missing");

        CsvImportResultResponse result = postCsv(
                token, "/api/teachers/import/confirm", updateCsv, "ADD_AND_UPDATE", CsvImportResultResponse.class
        );
        assertThat(result.added()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);

        // Updated teacher keeps her chosen password and logs in with new email
        LoginResponse afterUpdate = login("anita.new@upd-demo.edu", "AnitaPersonal123!");
        assertThat(afterUpdate.user().displayName()).isEqualTo("Dr. Anita Roy");
        assertThat(afterUpdate.user().mustChangePassword()).isFalse();
    }

    @Test
    void subjectCsvRejectsMissingColumnsAndInvalidLabGroups() {
        Department dept = departmentRepository.save(new Department("Bad Sub Dept " + UUID.randomUUID()));
        String token = createHodAndLogin(dept, "hod.badsub@college.edu");

        // Missing required column
        String missingColCsv = """
                subjectCode,name
                CS101,Intro
                """;
        assertThatThrownBy(() -> postCsv(
                token, "/api/subjects/import/preview", missingColCsv, "ADD_ONLY", CsvImportPreviewResponse.class
        ))
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .satisfies(ex -> {
                    ApiError err = ((HttpClientErrorException) ex).getResponseBodyAs(ApiError.class);
                    assertThat(err).isNotNull();
                    assertThat(err.code()).isEqualTo("MISSING_COLUMNS");
                    assertThat(err.message()).contains("classification");
                });

        // Two labs in same group sharing the same place + one lonely lab group
        String badGroupCsv = """
                subjectCode,name,classification,periodsInARow,runsTogetherGroup,sharedPlace
                CS351,DSA Lab,LAB,2,GRP-CLASH,Computer Lab 1
                CS352,Java Lab,LAB,2,GRP-CLASH,Computer Lab 1
                CS353,Lonely Lab,LAB,2,GRP-ONE,Lab 3
                """;
        CsvImportPreviewResponse preview = postCsv(
                token, "/api/subjects/import/preview", badGroupCsv, "ADD_ONLY", CsvImportPreviewResponse.class
        );
        assertThat(preview.problemCount()).isEqualTo(3);
        assertThat(preview.rows().get(0).message()).contains("Computer Lab 1").contains("different places");
        assertThat(preview.rows().get(2).message()).contains("at least two labs");
    }

    private <T> T postCsv(String token, String path, String csvContent, String mode, Class<T> responseType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(csvContent.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "upload.csv";
            }
        };
        body.add("file", resource);
        if (mode != null) {
            body.add("mode", mode);
        }

        return client.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(responseType);
    }

    private String createHodAndLogin(Department dept, String email) {
        userAccountRepository.save(new UserAccount(
                dept.getId(),
                email,
                passwordEncoder.encode("StrongPass123!"),
                "HOD of " + dept.getName(),
                UserRole.HOD,
                AccountStatus.ACTIVE,
                false,
                Instant.now()
        ));
        return login(email, "StrongPass123!").accessToken();
    }

    private LoginResponse login(String email, String password) {
        LoginResponse res = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email, password))
                .retrieve()
                .body(LoginResponse.class);
        assertThat(res).isNotNull();
        return res;
    }
}