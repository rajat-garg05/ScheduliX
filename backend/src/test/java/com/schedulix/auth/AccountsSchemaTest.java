package com.schedulix.auth;

import com.schedulix.history.HistoryEntry;
import com.schedulix.history.HistoryEntryRepository;
import com.schedulix.notice.Notification;
import com.schedulix.notice.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import com.schedulix.auth.entity.AccountStatus;
import com.schedulix.auth.entity.Department;
import com.schedulix.auth.repository.DepartmentRepository;
import com.schedulix.auth.entity.EmailToken;
import com.schedulix.auth.repository.EmailTokenRepository;
import com.schedulix.auth.entity.RefreshToken;
import com.schedulix.auth.repository.RefreshTokenRepository;
import com.schedulix.auth.entity.TokenPurpose;
import com.schedulix.auth.entity.UserAccount;
import com.schedulix.auth.repository.UserAccountRepository;
import com.schedulix.auth.entity.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class AccountsSchemaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    EmailTokenRepository emailTokenRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    HistoryEntryRepository historyEntryRepository;

    @Test
    void enforcesOneHodPerDepartmentAndUniqueEmail() {
        Department dept = departmentRepository.save(new Department("Computer Science"));

        UserAccount hod = userAccountRepository.save(new UserAccount(
                dept.getId(),
                "hod@college.edu",
                "$2a$10$dummyhashvaluefortestingonly12345678901234567890123456",
                "HOD of Computer Science",
                UserRole.HOD,
                AccountStatus.ACTIVE,
                false,
                Instant.now()
        ));

        assertThat(userAccountRepository.findByEmail("hod@college.edu")).isPresent();

        UserAccount secondHod = new UserAccount(
                dept.getId(),
                "hod2@college.edu",
                "$2a$10$dummyhashvaluefortestingonly12345678901234567890123456",
                "Another HOD",
                UserRole.HOD,
                AccountStatus.ACTIVE,
                false,
                Instant.now()
        );
        assertThatThrownBy(() -> userAccountRepository.saveAndFlush(secondHod))
                .isInstanceOf(DataIntegrityViolationException.class);

        UserAccount duplicateEmailTeacher = new UserAccount(
                dept.getId(),
                "hod@college.edu",
                "$2a$10$dummyhashvaluefortestingonly12345678901234567890123456",
                "Prof. Mehta",
                UserRole.TEACHER,
                AccountStatus.ACTIVE,
                true,
                Instant.now()
        );
        assertThatThrownBy(() -> userAccountRepository.saveAndFlush(duplicateEmailTeacher))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Tokens, notifications, and history entries save cleanly
        emailTokenRepository.save(new EmailToken(
                hod.getId(),
                "a".repeat(64),
                TokenPurpose.VERIFY,
                Instant.now().plus(24, ChronoUnit.HOURS)
        ));
        refreshTokenRepository.save(new RefreshToken(
                hod.getId(),
                "b".repeat(64),
                Instant.now().plus(7, ChronoUnit.DAYS)
        ));
        notificationRepository.save(new Notification(hod.getId(), "Welcome to ScheduliX", "/dashboard"));
        historyEntryRepository.save(new HistoryEntry(
                dept.getId(),
                hod.getId(),
                hod.getDisplayName(),
                "ACCOUNT",
                "HOD of Computer Science registered the department."
        ));

        assertThat(notificationRepository.countByUserAccountIdAndReadAtIsNull(hod.getId())).isEqualTo(1);
    }
}