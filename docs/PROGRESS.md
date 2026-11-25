# ScheduliX Progress

## Completed
- Milestone 1 (Prompt 1): Initialized `schedulix` repository, base folder structure (`backend/`, `frontend/`, `docs/`, `scripts/`), `.gitignore`, and `README.md`.
- Milestone 2 (Prompt 1): Created Spring Boot 4.1.1 backend skeleton (`backend/pom.xml`, `SchedulixApplication`, `HealthController`, `ApiError`, `AppException`, `GlobalExceptionHandler`, `application.yml` with `dev`/`prod` profiles, and `HealthControllerTest`).
- Milestone 3 (Prompt 1): Added Docker Compose PostgreSQL 16 (`docker-compose.yml`, `.env.example`), JPA + Flyway (`V1__baseline.sql`), and Testcontainers integration test (`HealthIntegrationTest`).
- Milestone 4 (Prompt 1): Built React + Vite (JavaScript) frontend skeleton with Tailwind CSS, React Router, TanStack Query, Axios `/api` client + Vite proxy, role-based `AppShell` (`MENU_BY_ROLE`), `ProtectedRoute` placeholder + unit tests, `LoginPage` placeholder, `DashboardPage`, and `ComingSoonPage`.

## Key files
- `frontend/package.json`
- `frontend/vite.config.js`
- `frontend/src/api/client.js`
- `frontend/src/features/auth/authContext.jsx`
- `frontend/src/routes/ProtectedRoute.jsx`
- `frontend/src/routes/ProtectedRoute.test.jsx`
- `frontend/src/components/menuItems.js`
- `frontend/src/components/AppShell.jsx`
- `frontend/src/pages/DashboardPage.jsx`
- `frontend/src/pages/LoginPage.jsx`
- `frontend/src/App.jsx`

## How to run
- Start PostgreSQL: `docker compose up -d`
- Backend tests: `cd backend; mvn test`
- Start backend: `cd backend; mvn spring-boot:run`
- Frontend tests: `cd frontend; npm test`
- Start frontend: `cd frontend; npm run dev` (opens at `http://localhost:5173`)

## Open items
- Build the complete public Home page in Milestone 5.