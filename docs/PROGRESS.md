# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3, Milestone 1: Added Flyway migration `V3__teachers_subjects_who_can_teach.sql`, entities, repositories, `UsageChecker` hook, and `SetupSchemaTest`.
- Prompt 3, Milestone 2: Built Teachers backend (`TeacherService`, `TeacherController`) + `TeacherIntegrationTest`.
- Prompt 3, Milestone 3: Built Subjects backend (`SubjectService`, `SubjectController`, `SubjectGroupValidator`) + `SubjectIntegrationTest`.
- Prompt 3, Milestone 4: Built Who can teach what backend (`TeacherSubjectService`, `TeacherSubjectController`) + `TeacherSubjectIntegrationTest`.
- Prompt 3, Milestone 5: Built reusable `CsvImportSupport` (Apache Commons CSV, BOM stripping, column-by-name lookup, 2,000-row / 2 MB limits), `SetupCsvImportService`, `SetupCsvImportController` (preview, confirm, templates for Teachers, Subjects, and Who can teach what), and `CsvImportIntegrationTest`.

## Shared backend helpers (use these exact names in later prompts)
- `com.schedulix.auth.CurrentUser`
- `com.schedulix.history.HistoryService` (`record(String kind, String sentence)`)
- `com.schedulix.notice.NotificationService` (`send(UUID userId, String text, String link)`)
- `com.schedulix.auth.AccountService`
- `com.schedulix.auth.EmailSender`
- `com.schedulix.setup.UsageChecker` (`isTeacherUsed`, `isSubjectUsed`, `isSubjectUsedInSavedTimetable`)
- `com.schedulix.setup.SubjectGroupValidator`
- `com.schedulix.common.CsvImportSupport`, `CsvImportMode`, `CsvRowStatus`, `CsvPreviewRow`, `CsvImportPreviewResponse`, `CsvImportResultResponse`
- `com.schedulix.common.PageResponse<T>`

## Database migrations
- `V1__baseline.sql`
- `V2__accounts_notifications_history.sql`
- `V3__teachers_subjects_who_can_teach.sql`
- Next migration number: `V4__...sql`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Prompt 3, Milestone 6: React Teachers and Subjects screens (search, paging, add/edit modals, template download, and CSV import preview/confirm).