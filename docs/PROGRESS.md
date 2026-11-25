# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What (`V3__teachers_subjects_who_can_teach.sql`, `UsageChecker`, `SubjectGroupValidator`, `CsvImportSupport`, CRUD + CSV import + React screens).
- Prompt 4 (All 6 milestones): Semesters, Sections, and Semester Plans (`V4__semesters_sections_plans.sql`, `SavedTimetableLookup`, `PlanValidator`, CSV multi-section import, manual plan editing, `SemesterPlansPage`).
- Prompt 5, Milestone 1: Created pure Java `com.schedulix.engine` contract records, `TimeGrid` stretch/block math, and `TimeGridTest`.
- Prompt 5, Milestone 2: Built `EngineChecks.check(EngineInput)` covering all 7 pre-checks with plain-English messages + `EngineChecksTest`.

## Implementation notes for later prompts
- Do **not** put `@ConditionalOnMissingBean` on `@Component`-scanned classes (`DefaultUsageChecker` and `DefaultSavedTimetableLookup` use plain `@Component`). When Prompt 6/8 provides real implementations of `UsageChecker` and `SavedTimetableLookup`, replace the default class or mark the real bean `@Primary`.
- `com.schedulix.engine` is strictly plain Java (no Spring, no JPA, no JSON libraries).

## Shared backend helpers (use these exact names in later prompts)
- `com.schedulix.auth.CurrentUser`
- `com.schedulix.history.HistoryService` (`record(String kind, String sentence)`)
- `com.schedulix.notice.NotificationService` (`send(UUID userId, String text, String link)`)
- `com.schedulix.auth.AccountService`
- `com.schedulix.auth.EmailSender`
- `com.schedulix.setup.UsageChecker` (`isTeacherUsed`, `isSubjectUsed`, `isSubjectUsedInSavedTimetable`)
- `com.schedulix.setup.SavedTimetableLookup` (`hasSavedTimetable(UUID sectionId)`)
- `com.schedulix.setup.SubjectGroupValidator`
- `com.schedulix.setup.PlanValidator`
- `com.schedulix.common.CsvImportSupport`
- `com.schedulix.common.PageResponse<T>`

## Engine contract (`com.schedulix.engine`)
- Records: `EngineSettings`, `EngineClass`, `EngineSubject`, `PlanLine`, `EngineTeacher`, `TeacherChoice`, `FixedLecture`, `BusyCell`, `EngineInput`, `PlacedLecture`, `EngineResult`
- Helpers: `TimeGrid`, `EngineChecks.check(EngineInput)`

## Database migrations
- `V1__baseline.sql`
- `V2__accounts_notifications_history.sql`
- `V3__teachers_subjects_who_can_teach.sql`
- `V4__semesters_sections_plans.sql`
- Next migration number: `V5__...sql`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run targeted backend test: `cd backend; mvn test -Dtest=ClassName; cd ..`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Prompt 5, Milestone 3: `TeacherSuggester.suggest(EngineInput)` + unit tests.