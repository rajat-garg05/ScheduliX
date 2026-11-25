# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts.
- Prompt 2, Milestone 1: Added Flyway migration `V2__accounts_notifications_history.sql`, JPA entities, repositories, and `AccountsSchemaTest`.
- Prompt 2, Milestone 2: Built registration, email verification, resend confirmation, `EmailSender`, `TokenHelper`, `RateLimiter`, and `RegistrationIntegrationTest`.
- Prompt 2, Milestone 3: Built login, refresh-token cookie rotation, logout, `GET /api/me`, `JwtService`, `JwtAuthenticationFilter`, `CurrentUser`, `LoginAttemptTracker`, `must_change_password` guard, and `LoginSessionIntegrationTest`.
- Prompt 2, Milestone 4: Built forgot password, reset password, change password (`PasswordService`), and `PasswordIntegrationTest`.
- Prompt 2, Milestone 5: Built Notifications (`NotificationService`, `NotificationController`), History (`HistoryService`, `HistoryController`), `PageResponse`, and `AccountService` teacher-account helpers, plus `NotificationsHistoryIntegrationTest` and `AccountServiceIntegrationTest`.

## Key files and classes
- Migrations:
  - `V1__baseline.sql`
  - `V2__accounts_notifications_history.sql` (next migration: `V3__...sql`)
- Shared helpers for later prompts:
  - `com.schedulix.auth.CurrentUser` (`userId()`, `departmentId()`, `role()`, `displayName()`, `mustChangePassword()`)
  - `com.schedulix.history.HistoryService` (`record(String kind, String sentence)`)
  - `com.schedulix.notice.NotificationService` (`send(UUID userId, String text, String link)`)
  - `com.schedulix.auth.AccountService` (`createTeacherAccount`, `resetToDefaultPassword`, `disableAccount`, `enableAccount`, `changeEmail`, `changeDisplayName`, `DEFAULT_TEACHER_PASSWORD`)
  - `com.schedulix.auth.EmailSender` (`SmtpEmailSender`, `ConsoleEmailSender`)
  - `com.schedulix.common.PageResponse`
- Controllers:
  - `AuthController`, `MeController`, `NotificationController`, `HistoryController`, `HealthController`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Prompt 2, Milestone 6: React auth screens (Register, Check your email, Verify email, Login, Forgot, Reset, Change password) and replace placeholder auth in `ProtectedRoute`.