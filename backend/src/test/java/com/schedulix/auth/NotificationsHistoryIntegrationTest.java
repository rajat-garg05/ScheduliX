package com.schedulix.auth;

import com.schedulix.common.PageResponse;
import com.schedulix.history.HistoryEntry;
import com.schedulix.history.HistoryEntryRepository;
import com.schedulix.history.HistoryEntryResponse;
import com.schedulix.notice.Notification;
import com.schedulix.notice.NotificationRepository;
import com.schedulix.notice.NotificationResponse;
import com.schedulix.notice.NotificationService;
import com.schedulix.notice.UnreadCountResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class NotificationsHistoryIntegrationTest {

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
    NotificationRepository notificationRepository;

    @Autowired
    NotificationService notificationService;

    @Autowired
    HistoryEntryRepository historyEntryRepository;

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
    void notificationsAreScopedToOwnAccountAndSupportReadActions() {
        Department dept = departmentRepository.save(new Department("Dept Notice " + UUID.randomUUID()));
        UserAccount userA = saveActiveUser(dept.getId(), "notice.a@college.edu", "HOD A", UserRole.HOD);
        UserAccount userB = saveActiveUser(dept.getId(), "notice.b@college.edu", "Prof. B", UserRole.TEACHER);

        notificationService.send(userA.getId(), "Leave request from Prof. B", "/leave");
        notificationService.send(userA.getId(), "Timetable saved", "/timetables");
        Notification otherNotice = notificationRepository.save(
                new Notification(userB.getId(), "Only for Prof. B", "/today")
        );

        String tokenA = login("notice.a@college.edu");

        UnreadCountResponse unread = client.get()
                .uri("/api/notifications/unread-count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .retrieve()
                .body(UnreadCountResponse.class);
        assertThat(unread).isNotNull();
        assertThat(unread.count()).isEqualTo(2);

        PageResponse<NotificationResponse> list = client.get()
                .uri("/api/notifications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(list).isNotNull();
        assertThat(list.items()).hasSize(2);
        assertThat(list.items()).extracting(NotificationResponse::text)
                .doesNotContain("Only for Prof. B");

        // Cannot mark another user's notification as read
        assertThatThrownBy(() -> client.post()
                .uri("/api/notifications/" + otherNotice.getId() + "/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.NotFound.class);

        // Mark one read
        UUID firstId = list.items().get(0).id();
        client.post()
                .uri("/api/notifications/" + firstId + "/read")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .retrieve()
                .toBodilessEntity();

        UnreadCountResponse afterOne = client.get()
                .uri("/api/notifications/unread-count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .retrieve()
                .body(UnreadCountResponse.class);
        assertThat(afterOne.count()).isEqualTo(1);

        // Mark all read
        client.post()
                .uri("/api/notifications/read-all")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .retrieve()
                .toBodilessEntity();

        UnreadCountResponse afterAll = client.get()
                .uri("/api/notifications/unread-count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                .retrieve()
                .body(UnreadCountResponse.class);
        assertThat(afterAll.count()).isEqualTo(0);
    }

    @Test
    void historyIsHodOnlyAndIsolatedByDepartment() {
        Department deptA = departmentRepository.save(new Department("Dept Alpha"));
        Department deptB = departmentRepository.save(new Department("Dept Beta"));

        UserAccount hodA = saveActiveUser(deptA.getId(), "hod.alpha@college.edu", "HOD of Alpha", UserRole.HOD);
        saveActiveUser(deptA.getId(), "teacher.alpha@college.edu", "Prof. Alpha", UserRole.TEACHER);
        UserAccount hodB = saveActiveUser(deptB.getId(), "hod.beta@college.edu", "HOD of Beta", UserRole.HOD);

        historyEntryRepository.save(new HistoryEntry(
                deptA.getId(), hodA.getId(), hodA.getDisplayName(), "ACCOUNT", "HOD of Alpha registered the department."
        ));
        historyEntryRepository.save(new HistoryEntry(
                deptA.getId(), hodA.getId(), hodA.getDisplayName(), "TEACHER", "Added teacher Prof. Alpha."
        ));
        historyEntryRepository.save(new HistoryEntry(
                deptB.getId(), hodB.getId(), hodB.getDisplayName(), "ACCOUNT", "HOD of Beta registered the department."
        ));

        String hodAToken = login("hod.alpha@college.edu");
        String teacherAToken = login("teacher.alpha@college.edu");

        // Teacher cannot view History
        assertThatThrownBy(() -> client.get()
                .uri("/api/history")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherAToken)
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.Forbidden.class);

        // HOD A sees only Dept A entries
        PageResponse<HistoryEntryResponse> allA = client.get()
                .uri("/api/history")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodAToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(allA).isNotNull();
        assertThat(allA.items()).hasSize(2);
        assertThat(allA.items()).extracting(HistoryEntryResponse::text)
                .doesNotContain("HOD of Beta registered the department.");

        // Filter by kind and search
        PageResponse<HistoryEntryResponse> filtered = client.get()
                .uri("/api/history?kind=TEACHER&search=Alpha")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hodAToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        assertThat(filtered).isNotNull();
        assertThat(filtered.items()).hasSize(1);
        assertThat(filtered.items().get(0).text()).isEqualTo("Added teacher Prof. Alpha.");
    }

    private UserAccount saveActiveUser(UUID deptId, String email, String name, UserRole role) {
        return userAccountRepository.save(new UserAccount(
                deptId,
                email,
                passwordEncoder.encode("StrongPass123!"),
                name,
                role,
                AccountStatus.ACTIVE,
                false,
                Instant.now()
        ));
    }

    private String login(String email) {
        LoginResponse res = client.post()
                .uri("/api/auth/login")
                .body(new LoginRequest(email, "StrongPass123!"))
                .retrieve()
                .body(LoginResponse.class);
        assertThat(res).isNotNull();
        return res.accessToken();
    }
}