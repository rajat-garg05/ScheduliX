# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What (`V3__teachers_subjects_who_can_teach.sql`, `UsageChecker`, `SubjectGroupValidator`, `CsvImportSupport`, CRUD + CSV import + React screens).
- Prompt 4 (All 6 milestones): Semesters, Sections, and Semester Plans (`V4__semesters_sections_plans.sql`, `SavedTimetableLookup`, `PlanValidator`, CSV multi-section import, manual plan editing, `SemesterPlansPage`).
- Prompt 5 (All 6 milestones): Pure Java Timetable Engine (`com.schedulix.engine`).
- Prompt 6, Milestone 1: Added `V5__timetables.sql`, JPA entities & repositories, `@Primary` `TimetableSavedLookup` & `TimetableUsageChecker`, draft CRUD (`TimetableDraftService`, `TimetableController`), and `TimetableDraftIntegrationTest`.
- Prompt 6, Milestone 2: Built `TimetableSettings`, `DayPreviewSlot`, `TimetableSettingsResponse`, `TimetableSettingsSupport`, `PUT /api/timetables/{id}/settings`, and `TimetableSettingsSupportTest`.
- Prompt 6, Milestone 3: Added `UpdateClassesRequest`, `PUT /api/timetables/{id}/classes`, and `TimetableClassesIntegrationTest`.
- Prompt 6, Milestone 4: Built `BusyCellService` (real clock-time overlap mapping across different start times) and `EngineInputBuilder` + `BusyCellServiceTest`.

## Implementation notes for later prompts
- Do **not** put `@ConditionalOnMissingBean` on `@Component`-scanned classes. Real hook overrides (`TimetableSavedLookup`, `TimetableUsageChecker`) use `@Primary @Component`.
- `com.schedulix.engine` is strictly plain Java (no Spring, no JPA, no JSON libraries).

## Shared backend helpers (use these exact names in later prompts)
- `com.schedulix.auth.CurrentUser`
- `com.schedulix.history.HistoryService` (`record(String kind, String sentence)`)
- `com.schedulix.notice.NotificationService` (`send(UUID userId, String text, String link)`)
- `com.schedulix.auth.AccountService`
- `com.schedulix.auth.EmailSender`
- `com.schedulix.setup.UsageChecker` (backed by `@Primary TimetableUsageChecker`)
- `com.schedulix.setup.SavedTimetableLookup` (backed by `@Primary TimetableSavedLookup`)
- `com.schedulix.setup.SubjectGroupValidator`
- `com.schedulix.setup.PlanValidator`
- `com.schedulix.timetable.TimetableSettingsSupport`
- `com.schedulix.timetable.BusyCellService`
- `com.schedulix.timetable.EngineInputBuilder`
- `com.schedulix.common.CsvImportSupport`
- `com.schedulix.common.PageResponse<T>`

## Database migrations
- `V1__baseline.sql`
- `V2__accounts_notifications_history.sql`
- `V3__teachers_subjects_who_can_teach.sql`
- `V4__semesters_sections_plans.sql`
- `V5__timetables.sql`
- Next migration number: `V6__...sql`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run targeted backend test: `cd backend; mvn test -Dtest=ClassName; cd ..`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Prompt 6, Milestone 5: Check step (`POST /api/timetables/{id}/check`) returning plain problems with fix links + tests.