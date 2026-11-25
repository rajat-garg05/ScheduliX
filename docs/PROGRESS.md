# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History.
  - `V2__accounts_notifications_history.sql` (`department`, `user_account`, `email_token`, `refresh_token`, `notification`, `history_entry`).
  - Registration, email verification (24h single-use link, resend), BCrypt passwords, SHA-256 hashed link tokens, `EmailSender` (SMTP + `dev`-only console fallback), rate limiting.
  - JWT login (15m in-memory access token), HttpOnly `SameSite=Strict` `Path=/api/auth` 7d rotating refresh cookie (`Secure` in `prod`), logout, `/api/me`, 5-attempt 15m login lockout, `must_change_password` guard, Spring method security (`@PreAuthorize`).
  - Forgot password (30m single-use link, background email, ends all sessions), reset password, change password.
  - Notifications API + live bell unread badge + `NotificationsPage`, HOD-only History API + `HistoryPage` (search, type, date range, paging).
  - Full React auth screens replacing placeholder login in `ProtectedRoute`.

## Shared backend helpers (use these exact names in later prompts)
- `com.schedulix.auth.CurrentUser`
  - `userId()`, `departmentId()`, `role()`, `displayName()`, `mustChangePassword()`, `principal()`
- `com.schedulix.history.HistoryService`
  - `record(String kind, String sentence)` (kinds: `ACCOUNT`, `IMPORT`, `TEACHER`, `SUBJECT`, `PLAN`, `TIMETABLE`, `LEAVE`, `SUBSTITUTE`)
- `com.schedulix.notice.NotificationService`
  - `send(UUID userId, String text, String link)`
- `com.schedulix.auth.AccountService`
  - `DEFAULT_TEACHER_PASSWORD` (`"ScheduliX@123"`)
  - `createTeacherAccount(UUID departmentId, String email, String displayName)`
  - `resetToDefaultPassword(UUID userId)`
  - `disableAccount(UUID userId)`
  - `enableAccount(UUID userId)`
  - `changeEmail(UUID userId, String newEmail)`
  - `changeDisplayName(UUID userId, String name)`
- `com.schedulix.auth.EmailSender`
  - Implementations: `SmtpEmailSender` (`app.mail.enabled=true`), `ConsoleEmailSender` (`app.mail.enabled=false`, never prints in `prod`)
- `com.schedulix.common.PageResponse<T>`
  - `PageResponse.from(Page<T>)` → `{ items, page, size, totalItems, totalPages }`
- `com.schedulix.common.AppException` & `com.schedulix.common.ApiError`

## Database migrations
- `V1__baseline.sql`
- `V2__accounts_notifications_history.sql`
- Next migration number: `V3__...sql`

## Frontend helpers and pages
- `frontend/src/api/client.js` (`api` Axios instance, `getErrorMessage`, `getErrorCode`, in-memory JWT + silent 401 refresh)
- `frontend/src/features/auth/authContext.jsx` (`useAuth`, `AuthProvider`)
- `frontend/src/routes/ProtectedRoute.jsx`
- `frontend/src/components/AppShell.jsx`, `menuItems.js`, `SampleTimetable.jsx`
- `frontend/src/pages/`: `HomePage`, `RegisterPage`, `CheckEmailPage`, `VerifyEmailPage`, `LoginPage`, `ForgotPasswordPage`, `ResetPasswordPage`, `ChangePasswordPage`, `DashboardPage`, `NotificationsPage`, `HistoryPage`, `ComingSoonPage`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run all tests: `.\scripts\test.ps1`
- Dev email links print to the backend console when `APP_MAIL_ENABLED=false` (default in `dev`).

## Open items
- Ready for Prompt 3: Teachers, Subjects, and Who Can Teach What (`V3` migration).