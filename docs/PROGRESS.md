# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What (`V3__teachers_subjects_who_can_teach.sql`, `UsageChecker`, `SubjectGroupValidator`, `CsvImportSupport`, CRUD + CSV import + React screens).
- Prompt 4 (All 6 milestones): Semesters, Sections, and Semester Plans.
  - `V4__semesters_sections_plans.sql` (`semester`, `section`, `section_subject`).
  - Semesters & Sections CRUD (`SemesterSectionService`, `SemesterSectionController`) with delete guards and `SavedTimetableLookup` hook.
  - Pure Java `PlanValidator` (block multiples, paired-lab completeness and equal lectures, inactive/duplicate checks, missing-teacher warnings) + `PlanValidatorTest`.
  - Plan CSV import (`PlanCsvImportService`, `PlanCsvImportController`, `GET /api/plan/template`, multi-section apply via `otherSectionIds`).
  - Manual plan editing (`SectionPlanService`, `SectionPlanController`), read-only lock rule, and `DefaultUsageChecker.isSubjectUsed` checking `section_subject`.
  - React `SemesterPlansPage` and `PlanImportModal` wired at `/setup/semester-plans`.

## Implementation notes for later prompts
- Do **not** put `@ConditionalOnMissingBean` on `@Component`-scanned classes (`DefaultUsageChecker` and `DefaultSavedTimetableLookup` use plain `@Component`). When Prompt 6/8 provides real implementations of `UsageChecker` and `SavedTimetableLookup`, replace the default class or mark the real bean `@Primary`.

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

## Setup entities & repositories (`com.schedulix.setup`)
- `Teacher`, `TeacherRepository`, `Subject`, `SubjectKind`, `SubjectRepository`, `TeacherSubject`, `TeacherSubjectRepository`
- `Semester`, `SemesterRepository`, `Section`, `SectionRepository`, `SectionSubject`, `SectionSubjectId`, `SectionSubjectRepository`
- Services/Controllers: `SemesterSectionService`, `SemesterSectionController`, `SectionPlanService`, `SectionPlanController`, `PlanCsvImportService`, `PlanCsvImportController`

## Database migrations
- `V1__baseline.sql`
- `V2__accounts_notifications_history.sql`
- `V3__teachers_subjects_who_can_teach.sql`
- `V4__semesters_sections_plans.sql`
- Next migration number: `V5__...sql`

## Frontend pages
- `frontend/src/pages/`: `HomePage`, `RegisterPage`, `CheckEmailPage`, `VerifyEmailPage`, `LoginPage`, `ForgotPasswordPage`, `ResetPasswordPage`, `ChangePasswordPage`, `DashboardPage`, `NotificationsPage`, `HistoryPage`, `TeachersPage`, `SubjectsPage`, `TeacherSubjectsPage`, `SemesterPlansPage`, `ComingSoonPage`
- `frontend/src/features/setup/`: `CsvImportModal.jsx`, `PlanImportModal.jsx`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run targeted backend test: `cd backend; mvn test -Dtest=ClassName; cd ..`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Ready for Prompt 5: Timetable Engine (pure Java under `com.schedulix.engine`).