# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts.
- Prompt 2, Milestone 1: Added Flyway migration `V2__accounts_notifications_history.sql`, JPA entities, repositories, and `AccountsSchemaTest`.
- Prompt 2, Milestone 2: Built registration, email verification, resend confirmation, `EmailSender`, `TokenHelper`, `RateLimiter`, and `RegistrationIntegrationTest`.
- Prompt 2, Milestone 3: Built login, refresh-token cookie rotation, logout, `GET /api/me`, `JwtService`, `JwtAuthenticationFilter`, `CurrentUser`, `LoginAttemptTracker`, `must_change_password` guard, and `LoginSessionIntegrationTest`.
- Prompt 2, Milestone 4: Built forgot password, reset password, change password (`PasswordService`), and `PasswordIntegrationTest`.
- Prompt 2, Milestone 5: Built Notifications, History, `PageResponse`, and `AccountService` helpers + integration tests.
- Prompt 2, Milestone 6: Replaced placeholder auth with real JWT + silent refresh (`client.js`, `authContext.jsx`), enforced `mustChangePassword` in `ProtectedRoute`, and built React pages for Register, Check your email, Verify email, Login (with resend on unverified email), Forgot password, Reset password, and Change password.

## Key files and classes
- Migrations:
  - `V1__baseline.sql`
  - `V2__accounts_notifications_history.sql` (next migration: `V3__...sql`)
- Backend shared helpers:
  - `com.schedulix.auth.CurrentUser`
  - `com.schedulix.history.HistoryService`
  - `com.schedulix.notice.NotificationService`
  - `com.schedulix.auth.AccountService`
  - `com.schedulix.auth.EmailSender`
  - `com.schedulix.common.PageResponse`
- Frontend auth:
  - `frontend/src/api/client.js` (in-memory access token + silent 401 refresh)
  - `frontend/src/features/auth/authContext.jsx`, `AuthLayout.jsx`
  - `frontend/src/routes/ProtectedRoute.jsx`
  - `frontend/src/pages/RegisterPage.jsx`, `CheckEmailPage.jsx`, `VerifyEmailPage.jsx`, `LoginPage.jsx`, `ForgotPasswordPage.jsx`, `ResetPasswordPage.jsx`, `ChangePasswordPage.jsx`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Prompt 2, Milestone 7: React notification bell + Notifications list page, History page, and final Prompt 2 documentation in `docs/PROGRESS.md`.