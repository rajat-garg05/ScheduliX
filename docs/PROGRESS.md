# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What.
  - `V3__teachers_subjects_who_can_teach.sql` (`teacher`, `subject`, `teacher_subject`).
  - Teachers CRUD + login account creation (`ScheduliX@123`) + email/name sync + activate/deactivate + reset password + `UsageChecker` delete guard.
  - Subjects CRUD + paired-lab group & shared-place validation (`SubjectGroupValidator`) + used-subject rename-only rule.
  - Who can teach what bidirectional API (`GET/PUT /api/teachers/{id}/subjects`, `GET/PUT /api/subjects/{id}/teachers`).
  - Reusable `CsvImportSupport` (Apache Commons CSV, UTF-8 BOM support, column-by-name lookup, 2,000-row / 2 MB limits, stateless preview & transactional confirm) + template downloads.
  - React screens: `TeachersPage`, `SubjectsPage`, `TeacherSubjectsPage`, reusable `CsvImportModal`, and `ConfirmDialog`.

## Shared backend helpers (use these exact names in later prompts)
- `com.schedulix.auth.CurrentUser`
- `com.schedulix.history.HistoryService` (`record(String kind, String sentence)`)
- `com.schedulix.notice.NotificationService` (`send(UUID userId, String text, String link)`)
- `com.schedulix.auth.AccountService` (`createTeacherAccount`, `resetToDefaultPassword`, `disableAccount`, `enableAccount`, `changeEmail`, `changeDisplayName`, `DEFAULT_TEACHER_PASSWORD`)
- `com.schedulix.auth.EmailSender` (`SmtpEmailSender`, `ConsoleEmailSender`)
- `com.schedulix.setup.UsageChecker` (`isTeacherUsed`, `isSubjectUsed`, `isSubjectUsedInSavedTimetable`)
- `com.schedulix.setup.SubjectGroupValidator`
- `com.schedulix.common.CsvImportSupport`, `CsvImportMode`, `CsvRowStatus`, `CsvPreviewRow`, `CsvImportPreviewResponse`, `CsvImportResultResponse`
- `com.schedulix.common.PageResponse<T>`

## Setup entities & repositories (`com.schedulix.setup`)
- `Teacher`, `TeacherRepository`, `TeacherService`, `TeacherController`
- `Subject`, `SubjectKind`, `SubjectRepository`, `SubjectService`, `SubjectController`
- `TeacherSubject`, `TeacherSubjectId`, `TeacherSubjectRepository`, `TeacherSubjectService`, `TeacherSubjectController`
- `SetupCsvImportService`, `SetupCsvImportController`

## Database migrations
- `V1__baseline.sql`
- `V2__accounts_notifications_history.sql`
- `V3__teachers_subjects_who_can_teach.sql`
- Next migration number: `V4__...sql`

## Frontend reusable components & pages
- `frontend/src/components/`: `AppShell`, `ConfirmDialog`, `SampleTimetable`, `menuItems`
- `frontend/src/features/setup/CsvImportModal.jsx`
- `frontend/src/pages/`: `HomePage`, `RegisterPage`, `CheckEmailPage`, `VerifyEmailPage`, `LoginPage`, `ForgotPasswordPage`, `ResetPasswordPage`, `ChangePasswordPage`, `DashboardPage`, `NotificationsPage`, `HistoryPage`, `TeachersPage`, `SubjectsPage`, `TeacherSubjectsPage`, `ComingSoonPage`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Ready for Prompt 4: Semesters, Sections, and Semester Plans (`V4` migration).