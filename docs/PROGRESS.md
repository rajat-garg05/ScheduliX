# ScheduliX Progress

## Completed
- Prompt 1 (All 6 milestones): Project start and public Home page.
  - Milestone 1: Initialized `schedulix` repo, base folders (`backend/`, `frontend/`, `docs/`, `scripts/`), `.gitignore`, `README.md`.
  - Milestone 2: Spring Boot 4.1.1 backend skeleton (`SchedulixApplication`, `HealthController`, `ApiError`, `AppException`, `GlobalExceptionHandler`, `application.yml` with `dev`/`prod` profiles, `HealthControllerTest`).
  - Milestone 3: Docker Compose PostgreSQL 16 (`docker-compose.yml`, `.env.example`), JPA + Flyway (`V1__baseline.sql`), and Testcontainers integration test (`HealthIntegrationTest`).
  - Milestone 4: React + Vite (JavaScript) frontend with Tailwind CSS v4, React Router, TanStack Query, Axios `/api` client + Vite proxy, role-based `AppShell` (`MENU_BY_ROLE`), `ProtectedRoute` placeholder + unit tests, `LoginPage` placeholder, `DashboardPage`, and `ComingSoonPage`.
  - Milestone 5: Public `HomePage` and `SampleTimetable` component (static fake data labelled "Sample"), plus `HomePage.test.jsx`.
  - Milestone 6: PowerShell scripts (`scripts/start.ps1`, `scripts/test.ps1`), complete `README.md`, and final Prompt 1 check.

## Key files and classes
- Backend (`com.schedulix`):
  - `SchedulixApplication`
  - `common.HealthController` (`GET /api/health`)
  - `common.ApiError`, `common.AppException`, `common.GlobalExceptionHandler`
  - `backend/src/main/resources/application.yml` (`dev` default, `prod` profile, `APP_TIMEZONE` default `Asia/Kolkata`)
  - `backend/src/main/resources/db/migration/V1__baseline.sql` (next migration: `V2__...sql`)
- Frontend (`frontend/src/`):
  - `api/client.js` (Axios baseURL `/api`, `withCredentials: true`)
  - `features/auth/authContext.jsx` (placeholder auth — to be replaced with real JWT login in Prompt 2)
  - `routes/ProtectedRoute.jsx` (redirects guests to `/login?next=...`)
  - `components/menuItems.js` (`MENU_BY_ROLE`), `components/AppShell.jsx`, `components/SampleTimetable.jsx`
  - `pages/HomePage.jsx`, `pages/LoginPage.jsx`, `pages/DashboardPage.jsx`, `pages/ComingSoonPage.jsx`
- Scripts & infra:
  - `docker-compose.yml`, `.env.example`, `scripts/start.ps1`, `scripts/test.ps1`

## How to run
- Start everything: `.\scripts\start.ps1`
- Run all tests: `.\scripts\test.ps1`
- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8080/api/health`

## Open items
- Ready for Prompt 2: Accounts, Notifications, and History (will replace placeholder auth and add `V2` migration).