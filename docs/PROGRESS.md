# ScheduliX Progress

## Completed
- Milestone 1 (Prompt 1): Initialized `schedulix` repository, base folder structure (`backend/`, `frontend/`, `docs/`, `scripts/`), `.gitignore`, and `README.md`.
- Milestone 2 (Prompt 1): Created Spring Boot 4.1.1 backend skeleton (`backend/pom.xml`, `SchedulixApplication`, `HealthController`, `ApiError`, `AppException`, `GlobalExceptionHandler`, `application.yml` with `dev`/`prod` profiles, and `HealthControllerTest`).
- Milestone 3 (Prompt 1): Added Docker Compose PostgreSQL 16 (`docker-compose.yml`, `.env.example`), JPA + Flyway (`V1__baseline.sql`), and Testcontainers integration test (`HealthIntegrationTest`).
- Milestone 4 (Prompt 1): Built React + Vite (JavaScript) frontend skeleton with Tailwind CSS, React Router, TanStack Query, Axios `/api` client + Vite proxy, role-based `AppShell` (`MENU_BY_ROLE`), `ProtectedRoute` placeholder + unit tests, `LoginPage` placeholder, `DashboardPage`, and `ComingSoonPage`.
- Milestone 5 (Prompt 1): Built the public `HomePage` and `SampleTimetable` component (fake data labelled "Sample", breaks, paired lab block, pinned lecture, and substitute example), plus `HomePage.test.jsx`.

## Key files
- `frontend/src/components/SampleTimetable.jsx`
- `frontend/src/pages/HomePage.jsx`
- `frontend/src/pages/HomePage.test.jsx`

## How to run
- Start PostgreSQL: `docker compose up -d`
- Backend tests: `cd backend; mvn test`
- Start backend: `cd backend; mvn spring-boot:run`
- Frontend tests: `cd frontend; npm test`
- Start frontend: `cd frontend; npm run dev` (opens at `http://localhost:5173`)

## Open items
- Add PowerShell helper scripts (`scripts/start.ps1`, `scripts/test.ps1`) and finalize `README.md` in Milestone 6.