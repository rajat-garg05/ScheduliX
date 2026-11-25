# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What (`V3__teachers_subjects_who_can_teach.sql`, `UsageChecker`, `SubjectGroupValidator`, `CsvImportSupport`, CRUD + CSV import + React screens).
- Prompt 4 (All 6 milestones): Semesters, Sections, and Semester Plans (`V4__semesters_sections_plans.sql`, `SavedTimetableLookup`, `PlanValidator`, CSV multi-section import, manual plan editing, `SemesterPlansPage`).
- Prompt 5 (All 6 milestones): Pure Java Timetable Engine (`com.schedulix.engine`).
- Prompt 6 (All 9 milestones): Make Timetable, Steps 1 to 5 (`V5__timetables.sql`, real `TimetableSavedLookup` & `TimetableUsageChecker`, draft CRUD, settings + day preview, `BusyCellService`, `EngineInputBuilder`, `/check`, `/teachers`, `/fixed-lectures`, and React wizard Steps 1–5).
- Prompt 7 (All 7 milestones): Make, Keep, Save, and the Classes and Timetables Tree.
  - Backend (`com.schedulix.timetable`): `TimetableMakeService` (`POST /make`, `POST /make-again`, `PUT .../keep`, `POST /keep-all`, per-department lock), `TimetableLifecycleService` (`POST /save` with fresh busy-cell verify, `GET /delete-preview`, unified `DELETE /{id}`), `TimetableViewService` & `TimetableViewController` (`GET /{id}/view`, `GET /api/tree`, `GET /api/timetables/saved`, `GET /api/sections/{id}/timetable`).
  - Frontend: reusable `TimetableGrid.jsx`, `Step6Result.jsx` (Make progress, class & Teachers tabs, Keep, Keep all, Make again, Save), and `ClassesTimetablesPage.jsx` (`/timetables` tree with View and Delete preview).

## Implementation notes for later prompts
- Do **not** put `@ConditionalOnMissingBean` on `@Component`-scanned classes. Real hook overrides (`TimetableSavedLookup`, `TimetableUsageChecker`) use `@Primary @Component`.
- Request DTO for linking teacher subjects in `com.schedulix.setup` is `TeacherSubjectsRequest`.
- `com.schedulix.engine` is strictly plain Java (no Spring, no JPA, no JSON libraries).
- `TimetableGrid.jsx` is reusable across HOD views, Teacher views, and Today views.

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
- Ready for Prompt 8: Teacher Leave, Substitutes, Today view, and Teacher read-only pages (`V6` migration for `leave`, `leave_lecture`, `substitute`).