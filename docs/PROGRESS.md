# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What (`V3__teachers_subjects_who_can_teach.sql`, `UsageChecker`, `SubjectGroupValidator`, `CsvImportSupport`, CRUD + CSV import + React screens).
- Prompt 4 (All 6 milestones): Semesters, Sections, and Semester Plans (`V4__semesters_sections_plans.sql`, `SavedTimetableLookup`, `PlanValidator`, CSV multi-section import, manual plan editing, `SemesterPlansPage`).
- Prompt 5 (All 6 milestones): Pure Java Timetable Engine (`com.schedulix.engine`).
- Prompt 6 (All 9 milestones): Make Timetable, Steps 1 to 5.
  - `V5__timetables.sql`: `timetable`, `timetable_class` (with partial unique index on `section_id WHERE is_saved`), `teacher_choice`, `fixed_lecture`, `lecture`.
  - Real `@Primary` hooks: `TimetableSavedLookup` and `TimetableUsageChecker`.
  - Backend services & controller (`com.schedulix.timetable`): `TimetableDraftService`, `TimetableSettingsSupport`, `BusyCellService` (real clock-time overlap), `EngineInputBuilder`, `TimetableCheckService`, `TimetableTeachersService`, `FixedLectureService`, `TimetableController`.
  - React wizard (`MakeTimetablePage.jsx`): Unfinished timetables list + `Step1Classes`, `Step2Settings` (live day preview), `Step3Check`, `Step4Teachers` (suggestions, able-first dropdowns, override warnings, workload bars), `Step5FixedLectures` (quick-add form + clickable preview grid).

## Implementation notes for later prompts
- Do **not** put `@ConditionalOnMissingBean` on `@Component`-scanned classes. Real hook overrides (`TimetableSavedLookup`, `TimetableUsageChecker`) use `@Primary @Component`.
- Request DTO for linking teacher subjects in `com.schedulix.setup` is `TeacherSubjectsRequest`.
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

## Timetable APIs implemented (`com.schedulix.timetable`)
- `GET/POST /api/timetables`, `GET/DELETE /api/timetables/{id}`
- `PUT /api/timetables/{id}/classes`
- `PUT /api/timetables/{id}/settings`
- `POST /api/timetables/{id}/check`
- `GET/PUT /api/timetables/{id}/teachers`, `POST /api/timetables/{id}/teachers/suggest`
- `GET/POST/DELETE /api/timetables/{id}/fixed-lectures`

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
- Ready for Prompt 7: Making, Keeping, Saving, Viewing, and Deleting Timetables.