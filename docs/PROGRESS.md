# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What (`V3__teachers_subjects_who_can_teach.sql`, `UsageChecker`, `SubjectGroupValidator`, `CsvImportSupport`, CRUD + CSV import + React screens).
- Prompt 4 (All 6 milestones): Semesters, Sections, and Semester Plans (`V4__semesters_sections_plans.sql`, `SavedTimetableLookup`, `PlanValidator`, CSV multi-section import, manual plan editing, `SemesterPlansPage`).
- Prompt 5 (All 6 milestones): Pure Java Timetable Engine (`com.schedulix.engine`).
- Prompt 6 (All 9 milestones): Make Timetable, Steps 1 to 5.
- Prompt 7 (All 7 milestones): Make, Keep, Save, and the Classes and Timetables Tree.
- Prompt 8, Milestones 1–4: Backend `V6__leave_substitutes.sql`, `LeaveService`, `SubstituteService`, `TodayAndMyTimetableService`, and all leave/substitute/today/my-timetable endpoints.
- Prompt 8, Milestone 5: Built `LeavePage.jsx` (`/leave`) for both HOD and Teacher.
- Prompt 8, Milestone 6: Built `TodayPage.jsx` (`/today`) with date picker, HOD review banner, Check again, Accept all, one-by-one Accept, manual substitute dropdown, and teacher highlight.

## Implementation notes for later prompts
- Do **not** put `@ConditionalOnMissingBean` on `@Component`-scanned classes. Real hook overrides (`TimetableSavedLookup`, `TimetableUsageChecker`) use `@Primary @Component`.
- `UserAccountRepository` queries by `findByEmail(String email)` (not `findByEmailIgnoreCase`).
- Request DTO for linking teacher subjects in `com.schedulix.setup` is `TeacherSubjectsRequest`.
- `com.schedulix.engine` is strictly plain Java (no Spring, no JPA, no JSON libraries).

## Database migrations
- `V1__baseline.sql`
- `V2__accounts_notifications_history.sql`
- `V3__teachers_subjects_who_can_teach.sql`
- `V4__semesters_sections_plans.sql`
- `V5__timetables.sql`
- `V6__leave_substitutes.sql`
- Next migration number: `V7__...sql`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run targeted backend test: `cd backend; mvn test -Dtest=ClassName; cd ..`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Prompt 8, Milestone 7: React — Teacher views (`AllTimetablesPage`, `MyTimetablePage`), extend `UsageChecker` with leave and substitutes, and finalize Prompt 8 `docs/PROGRESS.md`.