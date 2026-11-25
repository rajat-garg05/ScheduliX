# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What (`V3__teachers_subjects_who_can_teach.sql`, `UsageChecker`, `SubjectGroupValidator`, `CsvImportSupport`, CRUD + CSV import + React screens).
- Prompt 4 (All 6 milestones): Semesters, Sections, and Semester Plans (`V4__semesters_sections_plans.sql`, `SavedTimetableLookup`, `PlanValidator`, CSV multi-section import, manual plan editing, `SemesterPlansPage`).
- Prompt 5 (All 6 milestones): Pure Java Timetable Engine (`com.schedulix.engine`: contract records, `TimeGrid`, `EngineChecks.check`/`verify`, `TeacherSuggester.suggest`, `TimetableEngine.make` with 45s limit, restarts, `variation`, brute-force & scale tests).
- Prompt 6, Milestone 1: Added `V5__timetables.sql` (`timetable`, `timetable_class`, `teacher_choice`, `fixed_lecture`, `lecture`), JPA entities and repositories in `com.schedulix.timetable`, real `@Primary` `TimetableSavedLookup` and `TimetableUsageChecker`, draft CRUD (`GET/POST /api/timetables`, `GET/DELETE /api/timetables/{id}`), and `TimetableDraftIntegrationTest`.

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
- `com.schedulix.common.CsvImportSupport`
- `com.schedulix.common.PageResponse<T>`

## Timetable entities & repositories (`com.schedulix.timetable`)
- `Timetable`, `TimetableStatus`, `TimetableRepository`
- `TimetableClass`, `TimetableClassId`, `TimetableClassRepository`
- `TeacherChoiceEntity`, `TeacherChoiceId`, `TeacherChoiceRepository`
- `FixedLectureEntity`, `FixedLectureRepository`
- `Lecture`, `LectureRepository`

## Engine contract (`com.schedulix.engine`)
- Records: `EngineSettings`, `EngineClass`, `EngineSubject`, `PlanLine`, `EngineTeacher`, `TeacherChoice`, `FixedLecture`, `BusyCell`, `EngineInput`, `PlacedLecture`, `EngineResult`, `Suggestion`
- Methods: `TimetableEngine.make(EngineInput, int)`, `EngineChecks.check(EngineInput)`, `EngineChecks.verify(EngineInput, List<PlacedLecture>)`, `TeacherSuggester.suggest(EngineInput)`, `TimeGrid.from(EngineSettings)`

## Database migrations
- `V1__baseline.sql`
- `V2__accounts_notifications_history.sql`
- `V3__teachers_subjects_who_can_teach.sql`
- `V4__semesters_sections_plans.sql`
- `V5__timetables.sql`
- Next migration number: `V6__...sql`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run engine tests only (no Docker): `cd backend; mvn test -Dtest="com.schedulix.engine.*Test"; cd ..`
- Run targeted backend test: `cd backend; mvn test -Dtest=ClassName; cd ..`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Prompt 6, Milestone 2: Settings step (`PUT /api/timetables/{id}/settings`) — validation, clock times, and live day preview + tests.