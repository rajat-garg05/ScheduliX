# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start, Spring Boot 4.1.1 backend skeleton, Docker Compose PostgreSQL 16 + Flyway `V1__baseline.sql`, React + Vite (JavaScript) frontend skeleton, role-based `AppShell`, public `HomePage` + `SampleTimetable`, PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`).
- Prompt 2 (All 7 milestones): Accounts, Notifications, and History (`V2__accounts_notifications_history.sql`, JWT + refresh cookie rotation, email verification, password reset, `NotificationsPage`, `HistoryPage`, and shared backend helpers).
- Prompt 3 (All 7 milestones): Teachers, Subjects, and Who Can Teach What (`V3__teachers_subjects_who_can_teach.sql`, `UsageChecker`, `SubjectGroupValidator`, `CsvImportSupport`, CRUD + CSV import + React screens).
- Prompt 4 (All 6 milestones): Semesters, Sections, and Semester Plans (`V4__semesters_sections_plans.sql`, `SavedTimetableLookup`, `PlanValidator`, CSV multi-section import, manual plan editing, `SemesterPlansPage`).
- Prompt 5 (All 6 milestones): Pure Java Timetable Engine (`com.schedulix.engine`, zero Spring/JPA dependencies).
  - Contract records + `TimeGrid` stretch/block math (`TimeGridTest`).
  - `EngineChecks.check(EngineInput)` for all 7 pre-checks (`EngineChecksTest`).
  - `TeacherSuggester.suggest(EngineInput)` with workload balancing & paired-lab separation (`TeacherSuggesterTest`).
  - `TimetableEngine.make(EngineInput, int variation)` with MRV, forward checking, compact-day gap scoring, 45-second time limit, deterministic node-limit restarts, and bottleneck messages (`TimetableEngineTest`).
  - Independent `EngineChecks.verify(EngineInput, List<PlacedLecture>)` wired into `make`, plus brute-force comparison test (`EngineVerifyAndBruteForceTest`).
  - `variation` repeatability/diversity test and 12-class (~250-lecture) speed check (`EngineVariationAndScaleTest`).

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

## Engine contract (`com.schedulix.engine` — final signatures)
- `record EngineSettings(int workingDays, int lecturesPerDay, List<Integer> breakAfterLecture, int maxPerTeacherPerDay)`
- `record EngineClass(String id, String name)`
- `record EngineSubject(String id, String name, boolean lab, int periodsInARow, String groupName, String placeName)`
- `record PlanLine(String classId, String subjectId, int lecturesPerWeek)`
- `record EngineTeacher(String id, String name, Set<String> ableSubjectIds)`
- `record TeacherChoice(String classId, String subjectId, String teacherId)`
- `record FixedLecture(String classId, String subjectId, int day, int startLecture)` (day 0-based; startLecture 1-based)
- `record BusyCell(String owner, int day, int lecture)` (owner = teacher id or place name; lecture 1-based)
- `record EngineInput(EngineSettings settings, List<EngineClass> classes, List<EngineSubject> subjects, List<PlanLine> plan, List<EngineTeacher> teachers, List<TeacherChoice> choices, List<FixedLecture> fixed, List<BusyCell> busyTeachers, List<BusyCell> busyPlaces)`
- `record PlacedLecture(String classId, String subjectId, String teacherId, int day, int startLecture, int length, String groupKey)`
- `record EngineResult(boolean ok, List<PlacedLecture> lectures, List<String> messages)`
- `record Suggestion(List<TeacherChoice> choices, List<String> messages)`
- `TimetableEngine.make(EngineInput in, int variation) -> EngineResult`
- `EngineChecks.check(EngineInput in) -> List<String>`
- `EngineChecks.verify(EngineInput in, List<PlacedLecture> lectures) -> List<String>`
- `TeacherSuggester.suggest(EngineInput in) -> Suggestion`
- `TimeGrid.from(EngineSettings settings) -> TimeGrid`

## Database migrations
- `V1__baseline.sql`
- `V2__accounts_notifications_history.sql`
- `V3__teachers_subjects_who_can_teach.sql`
- `V4__semesters_sections_plans.sql`
- Next migration number: `V5__...sql`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run engine tests only (no Docker): `cd backend; mvn test -Dtest="com.schedulix.engine.*Test"; cd ..`
- Run targeted backend test: `cd backend; mvn test -Dtest=ClassName; cd ..`
- Run all tests: `.\scripts\test.ps1`

## Open items
- Ready for Prompt 6: Making & Saving Timetables (`V5` migration for `timetable`, `timetable_class`, `teacher_choice`, `fixed_lecture`, `lecture`).