# ScheduliX — Verification Checklist

Checklist mapped from Prompts 1–9 **Done when** criteria to verified files, migrations, and automated tests in this repository.

## Prompt 1 — Project Start and Home Page
- [x] `docker compose up -d`, backend, and frontend start cleanly — verified via [docker-compose.yml](file:///d:/project/ScheduliX/schedulix/docker-compose.yml), [scripts/start.ps1](file:///d:/project/ScheduliX/schedulix/scripts/start.ps1), `V1__baseline.sql`, `HealthController.java`, `HealthIntegrationTest.java`, and `HealthControllerTest.java`.
- [x] `/` shows the Home page and protected routes redirect unauthenticated visitors to `/login` (preserving `?next=...`) — verified via [HomePage.jsx](file:///d:/project/ScheduliX/schedulix/frontend/src/pages/HomePage.jsx), [ProtectedRoute.jsx](file:///d:/project/ScheduliX/schedulix/frontend/src/routes/ProtectedRoute.jsx), and `ProtectedRoute.test.jsx`.
- [x] Home page renders static illustrative schedule only (no real tenant data) — verified via [HomePage.test.jsx](file:///d:/project/ScheduliX/schedulix/frontend/src/pages/HomePage.test.jsx) and [SampleTimetable.jsx](file:///d:/project/ScheduliX/schedulix/frontend/src/components/SampleTimetable.jsx).

## Prompt 2 — Accounts, Notifications, and History
- [x] Register, email confirmation link, login, refresh cookie rotation, logout, and forgot/reset password — verified via `V2__accounts_notifications_history.sql`, `RegistrationService.java`, `SessionService.java`, `PasswordService.java`, `RegistrationIntegrationTest.java`, `LoginSessionIntegrationTest.java`, `PasswordIntegrationTest.java`, and Playwright `journey.spec.js`.
- [x] Notification bell unread count and department History log work — verified via `NotificationService.java`, `HistoryService.java`, `NotificationsHistoryIntegrationTest.java`, `NotificationsPage.jsx`, and `HistoryPage.jsx`.
- [x] Implementation notes and helper names recorded in [PROGRESS.md](file:///d:/project/ScheduliX/schedulix/docs/PROGRESS.md).

## Prompt 3 — Teachers, Subjects, and Who Can Teach What
- [x] Loading `teachers.csv` (10), `subjects.csv` (15), and `who_can_teach_what.csv` (32 links) works via CSV import and manual CRUD — verified via `V3__teachers_subjects_who_can_teach.sql`, `SetupCsvImportService.java`, `CsvImportIntegrationTest.java`, `TeacherIntegrationTest.java`, `SubjectIntegrationTest.java`, `TeacherSubjectIntegrationTest.java`, and `DevSampleDataLoaderTest.java`.
- [x] Invalid CSV rows report exact row number and plain-English reason in preview before saving — verified via `CsvImportIntegrationTest.java` and `CsvImportModal.jsx`.

## Prompt 4 — Semesters, Sections, and Semester Plans
- [x] Creating Semester 3, Sections A and B, and uploading `semester3_plan.csv` to multiple sections in one upload works with totals — verified via `V4__semesters_sections_plans.sql`, `PlanCsvImportService.java`, `PlanCsvImportIntegrationTest.java`, `SectionPlanIntegrationTest.java`, and `SemesterPlansPage.jsx`.
- [x] Invalid lab lecture multiples show clear validation messages — verified via `PlanValidator.java`, `PlanValidatorTest.java`, and `PlanCsvImportIntegrationTest.java`.

## Prompt 5 — Pure Java Timetable Engine
- [x] `com.schedulix.engine` uses zero Spring/JPA/JSON libraries — verified via package imports and `TimetableEngineTest.java`, `TimeGridTest.java`, `TeacherSuggesterTest.java`, `EngineChecksTest.java`, `EngineVerifyAndBruteForceTest.java`, and `EngineVariationAndScaleTest.java`.
- [x] 12-class (~250+ lecture-slot) department schedule benchmarked cleanly — verified via `EngineVariationAndScaleTest.realisticDepartmentSpeedCheckLogsElapsedTimeWithoutFailingBuild`.

## Prompt 6 — Make Timetable (Steps 1 to 5)
- [x] Choose semester/sections, configure time settings, run Check with plain problems & fix links, review/override teacher choices, and add fixed lectures with instant break/clash checks — verified via `V5__timetables.sql`, `BusyCellService.java`, `TimetableCheckService.java`, `TimetableTeachersService.java`, `FixedLectureService.java`, `TimetableClassesIntegrationTest.java`, `TimetableCheckIntegrationTest.java`, `TimetableTeachersIntegrationTest.java`, `FixedLecturesIntegrationTest.java`, and Steps 1–5 components.

## Prompt 7 — Make, Keep, Save, and Classes Tree
- [x] Generate timetable, keep lectures, make again for another valid arrangement, save timetable, browse tree, and delete saved timetable freeing teachers/shared places — verified via `TimetableMakeService.java`, `TimetableLifecycleService.java`, `TimetableViewService.java`, `TimetableMakeIntegrationTest.java`, `TimetableKeepAndMakeAgainIntegrationTest.java`, `TimetableSaveAndDeleteIntegrationTest.java`, `TimetableViewAndTreeIntegrationTest.java`, `Step6Result.jsx`, and `ClassesTimetablesPage.jsx`.

## Prompt 8 — Leave, Substitutes, Today, and Teacher Views
- [x] Teacher leave requests, HOD record/accept/reject, automatic substitute proposals, accept/accept-all, and substitute notifications work — verified via `V6__leave_substitutes.sql`, `LeaveService.java`, `SubstituteService.java`, `LeaveIntegrationTest.java`, `SubstituteProposalIntegrationTest.java`, and `SubstituteAcceptIntegrationTest.java`.
- [x] `Today`, `All timetables`, and `My timetable` reflect accepted substitutes for the leave date without modifying saved timetables — verified via `TodayAndMyTimetableService.java`, `TodayAndMyTimetableIntegrationTest.java`, `TodayPage.jsx`, `AllTimetablesPage.jsx`, and `MyTimetablePage.jsx`.

## Prompt 9 — Polish, Security, Speed, and Deployment Readiness
- [x] Role-based dashboard (`GET /api/dashboard`), plain-wording audit, look-and-feel pass, and dev-only sample loader (`@Profile("dev")`) — verified via `DashboardIntegrationTest.java`, `DevSampleDataLoaderTest.java`.
- [x] Performance indexes and security audit (401 unauthenticated, 403 role boundary, 403 default-password enforcement, cross-tenant 404 isolation) — verified via `V7__performance_indexes.sql` and `SecurityAuditIntegrationTest.java`.
- [x] Full end-to-end user journey from registration through teacher `/today` view — verified via Playwright `frontend/e2e/journey.spec.js`.
- [x] Production deployment files prepared and audited (`backend/Dockerfile`, `backend/.dockerignore`, `application-prod.yml`, `frontend/netlify.toml`, `docs/DEPLOY.md`, `docs/HOD_GUIDE.md`, `docs/TEACHER_GUIDE.md`).
- [ ] Live deployment check on free-tier Neon + Render + Netlify + Brevo verified from public URL *(run once pushed and configured in Render/Netlify)*.
