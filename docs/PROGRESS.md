# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What (`V3__teachers_subjects_who_can_teach.sql`, `UsageChecker`, `SubjectGroupValidator`, `CsvImportSupport`, CRUD + CSV import + React screens).
- Prompt 4 (All 6 milestones): Semesters, Sections, and Semester Plans (`V4__semesters_sections_plans.sql`, `SavedTimetableLookup`, `PlanValidator`, CSV multi-section import, manual plan editing, `SemesterPlansPage`).
- Prompt 5 (All 6 milestones): Pure Java Timetable Engine (`com.schedulix.engine`).
- Prompt 6 (All 9 milestones): Make Timetable, Steps 1 to 5.
- Prompt 7 (All 7 milestones): Make, Keep, Save, and the Classes and Timetables Tree.
- Prompt 8 (All 7 milestones): Leave, Substitutes, Today, and Teacher Views (`V6__leave_substitutes.sql`, `LeaveService`, `SubstituteService`, `TodayAndMyTimetableService`, `LeavePage`, `TodayPage`, `AllTimetablesPage`, `MyTimetablePage`, extended `TimetableUsageChecker`).
- Prompt 9, Milestone 1: Role-based `GET /api/dashboard` (`DashboardController`, `DashboardService`, `DashboardResponse`, `DashboardIntegrationTest`) and HOD/Teacher `DashboardPage.jsx`.
- Prompt 9, Milestone 2: Plain words audit applied across `MakeTimetablePage`, `LeavePage`, `TodayPage`, `AllTimetablesPage`, `ClassesTimetablesPage`, `DashboardPage`, and `TimetableLifecycleService`.

## Implementation notes for later prompts
- Do **not** put `@ConditionalOnMissingBean` on `@Component`-scanned classes. Real hook overrides (`TimetableSavedLookup`, `TimetableUsageChecker`) use `@Primary @Component`.
- `TimetableUsageChecker` implements `isSubjectUsedInSavedTimetable(UUID)` and checks `SectionSubjectRepository`, `TeacherChoiceRepository`, `FixedLectureRepository`, `LectureRepository`, `LeaveRepository`, and `SubstituteRepository`.
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

## How to run
- Start everything: `.\scripts\start.ps1`
- Run targeted backend test: `cd backend; mvn test -Dtest=ClassName; cd ..`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Prompt 9, Milestone 3: Look and feel pass (spacing, focus rings, Today & timetable scannability, mobile timetable view).
- Prompt 9, Milestone 4: Dev-only sample data loader.
- Prompt 9, Milestone 5: Playwright full journey test.
- Prompt 9, Milestone 6: Security and speed checks.
- Prompt 9, Milestone 7: Deployment files and free-hosting guide.
- Prompt 9, Milestone 8: Final docs, checklist, and `v1.0` tag.