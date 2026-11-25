# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts.
- Prompt 2, Milestone 1: Added Flyway migration `V2__accounts_notifications_history.sql`, JPA entities, repositories, and `AccountsSchemaTest`.
- Prompt 2, Milestone 2: Built registration, email verification, resend confirmation, `EmailSender`, `TokenHelper`, `RateLimiter`, and `RegistrationIntegrationTest`.
- Prompt 2, Milestone 3: Built login (`POST /api/auth/login`), refresh-token cookie rotation (`POST /api/auth/refresh`), logout (`POST /api/auth/logout`), `GET /api/me`, `JwtService`, `JwtAuthenticationFilter`, `CurrentUser`, `AuthPrincipal`, `LoginAttemptTracker` (5 tries / 15-min lockout), `must_change_password` guard, method security (`@EnableMethodSecurity`), and `LoginSessionIntegrationTest`.

## Key files and classes
- Migrations:
  - `V1__baseline.sql`
  - `V2__accounts_notifications_history.sql` (next migration: `V3__...sql`)
- `com.schedulix.auth`:
  - Shared helpers: `CurrentUser` (`userId()`, `departmentId()`, `role()`, `displayName()`, `mustChangePassword()`), `EmailSender` (`SmtpEmailSender`, `ConsoleEmailSender`), `JwtService`, `TokenHelper`, `RateLimiter`, `LoginAttemptTracker`
  - Security: `SecurityConfig`, `JwtAuthenticationFilter`, `AuthPrincipal`
  - Services & controllers: `RegistrationService`, `SessionService`, `AuthController`, `MeController`
- `com.schedulix.notice`: `Notification`, `NotificationRepository`
- `com.schedulix.history`: `HistoryEntry`, `HistoryEntryRepository`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Prompt 2, Milestone 4: Forgot password, reset password, change password, and tests.