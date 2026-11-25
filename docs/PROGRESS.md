# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What (`V3__teachers_subjects_who_can_teach.sql`, `UsageChecker`, `SubjectGroupValidator`, `CsvImportSupport`, CRUD + CSV import + React screens).
- Prompt 4 (All 6 milestones): Semesters, Sections, and Semester Plans (`V4__semesters_sections_plans.sql`, `SavedTimetableLookup`, `PlanValidator`, CSV multi-section import, manual plan editing, `SemesterPlansPage`).
- Prompt 5 (All 6 milestones): Pure Java Timetable Engine (`com.schedulix.engine`).
- Prompt 6 (All 9 milestones): Make Timetable, Steps 1 to 5 (`V5__timetables.sql`, real `TimetableSavedLookup` & `TimetableUsageChecker`, draft CRUD, settings + day preview, `BusyCellService`, `EngineInputBuilder`, `/check`, `/teachers`, `/fixed-lectures`, and React wizard Steps 1–5).
- Prompt 7, Milestone 1: Built `TimetableMakeService`, `TimetableViewResponse`, `TimetableMakeResponse`, `POST /api/timetables/{id}/make`, and `TimetableMakeIntegrationTest`.
- Prompt 7, Milestone 2: Added `KeepLectureRequest`, `PUT /api/timetables/{id}/lectures/{lectureId}/keep`, `POST /api/timetables/{id}/keep-all`, `POST /api/timetables/{id}/make-again`, and `TimetableKeepAndMakeAgainIntegrationTest`.

## Implementation notes for later prompts
- Do **not** put `@ConditionalOnMissingBean` on `@Component`-scanned classes. Real hook overrides (`TimetableSavedLookup`, `TimetableUsageChecker`) use `@Primary @Component`.
- Request DTO for linking teacher subjects in `com.schedulix.setup` is `TeacherSubjectsRequest`.
- `com.schedulix.engine` is strictly plain Java (no Spring, no JPA, no JSON libraries).

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
- Prompt 7, Milestone 3: Save (`POST /api/timetables/{id}/save` with fresh busy-cell verify, one transaction, unique index) and Delete with preview (`GET /api/timetables/{id}/delete-preview`, `DELETE /api/timetables/{id}`) + History + tests.