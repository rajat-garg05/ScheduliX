# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What (`V3__teachers_subjects_who_can_teach.sql`, `UsageChecker`, `SubjectGroupValidator`, `CsvImportSupport`, CRUD + CSV import + React screens).
- Prompt 4 (All 6 milestones): Semesters, Sections, and Semester Plans (`V4__semesters_sections_plans.sql`, `SavedTimetableLookup`, `PlanValidator`, CSV multi-section import, manual plan editing, `SemesterPlansPage`).
- Prompt 5 (All 6 milestones): Pure Java Timetable Engine (`com.schedulix.engine`).
- Prompt 6 (All 9 milestones): Make Timetable, Steps 1 to 5.
- Prompt 7 (All 7 milestones): Make, Keep, Save, and the Classes and Timetables Tree.
- Prompt 8, Milestone 1: `V6__leave_substitutes.sql`, `Leave`, `LeaveLecture`, `Substitute` entities & repositories, `LeaveService` & `LeaveController`, `LeaveIntegrationTest`.
- Prompt 8, Milestone 2: `SubstituteService`, `SubstituteController`, `GET /api/teachers/{id}/lectures?date=`, `POST /api/substitutes/prepare?date=`, auto-prepare on leave accept, and `SubstituteProposalIntegrationTest`.
- Prompt 8, Milestone 3: `GET /api/substitutes/{id}/options`, `PUT /api/substitutes/{id}/teacher` (with server re-check), `POST /api/substitutes/{id}/accept`, `POST /api/substitutes/accept-all?date=`, notifications & History, and `SubstituteAcceptIntegrationTest`.

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
- Prompt 8, Milestone 4: `GET /api/today?date=` and `GET /api/my/timetable` + tests.