# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What (`V3__teachers_subjects_who_can_teach.sql`, `UsageChecker`, `SubjectGroupValidator`, `CsvImportSupport`, CRUD + CSV import + React screens).
- Prompt 4, Milestone 1: Added Flyway migration `V4__semesters_sections_plans.sql` (`semester`, `section`, `section_subject`), entities, repositories, and `PlanSchemaTest`.
- Prompt 4, Milestone 2: Built Semesters and Sections backend (`SemesterSectionService`, `SemesterSectionController`, `SavedTimetableLookup`) + `SemesterSectionIntegrationTest`.
- Prompt 4, Milestone 3: Built pure Java `PlanValidator` + `PlanValidatorTest`.
- Prompt 4, Milestone 4: Built semester plan CSV import (`PlanCsvImportService`, `PlanCsvImportController`, multi-section apply) + `PlanCsvImportIntegrationTest`.
- Prompt 4, Milestone 5: Built manual plan editing (`GET/PUT /api/sections/{id}/plan`, `SectionPlanService`, `SectionPlanController`), read-only lock guard via `SavedTimetableLookup`, extended `DefaultUsageChecker.isSubjectUsed` to protect subjects used in a plan, and added `SectionPlanIntegrationTest`.

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

## Database migrations
- `V1__baseline.sql`
- `V2__accounts_notifications_history.sql`
- `V3__teachers_subjects_who_can_teach.sql`
- `V4__semesters_sections_plans.sql`
- Next migration number: `V5__...sql`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Prompt 4, Milestone 6: React **Semester plans** page (semesters list, sections list, manual plan table, and CSV upload with multi-section tick list) + final Prompt 4 `docs/PROGRESS.md` update.