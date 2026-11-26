# ScheduliX

Department timetable management for college HODs and teachers.

The HOD registers the department, sets up teachers, subjects, eligibility links, and weekly semester plans (manually or via CSV), generates clash-free timetables across sections in one step, pins preferred lectures, saves final timetables, and manages daily teacher leave with substitute suggestions. Teachers sign in to view department timetables, today's schedule and substitutes, their personal timetable, and submit leave requests.

## Requirements
- JDK 21
- Maven 3.9+
- Node.js 20+ and npm
- Docker Desktop (running PostgreSQL 16 locally and running Testcontainers integration tests)

## Project layout
- `backend/` — Single-module Spring Boot 4.1.1 API, Flyway migrations (`V1`–`V7`), and pure Java timetable engine (`com.schedulix.engine`)
- `frontend/` — React + Vite (JavaScript) + Tailwind CSS web app, Vitest unit tests, and Playwright E2E test (`frontend/e2e/journey.spec.js`)
- `docs/` — Guides, deployment instructions, checklist, and progress notes:
  - [HOD Guide](docs/HOD_GUIDE.md)
  - [Teacher Guide](docs/TEACHER_GUIDE.md)
  - [Deployment Guide (Neon + Render + Netlify + Brevo)](docs/DEPLOY.md)
  - [Verification Checklist](docs/CHECKLIST.md)
  - [Implementation Progress](docs/PROGRESS.md)
- `scripts/` — PowerShell helpers ([start.ps1](scripts/start.ps1), [test.ps1](scripts/test.ps1))
- `docker-compose.yml` — Local PostgreSQL 16 database on port `5434`

## Quick start (Windows PowerShell)

1. Install frontend dependencies (first time only):
   ```powershell
   cd frontend
   npm install
   cd ..
   ```
2. Start PostgreSQL, backend (`http://localhost:8080`), and frontend (`http://localhost:5173`):
   ```powershell
   .\scripts\start.ps1
   ```
   Or start each piece manually:
   ```powershell
   docker compose up -d

   cd backend
   $env:SPRING_PROFILES_ACTIVE="dev"
   mvn spring-boot:run

   # In a separate terminal:
   cd frontend
   npm run dev
   ```

### Dev sample accounts
When the backend starts with `SPRING_PROFILES_ACTIVE=dev` and `APP_SAMPLE_DATA_ENABLED` enabled (default `true` in `dev`), `DevSampleDataLoader` loads the sample department (`Computer Science & Engineering (Demo)`) from `backend/src/main/resources/sample/`:
- **HOD:** `hod@cse-demo.edu` / `HodDemo@123`
- **Teachers:** e.g. `arjun.mehta@cse-demo.edu` / `ScheduliX@123` (must change password on first login)

To start with an empty database (for registering your own department or running the Playwright journey test), set `$env:APP_SAMPLE_DATA_ENABLED="false"`.

## Running tests

- **All backend and frontend tests:**
  ```powershell
  .\scripts\test.ps1
  ```
- **Backend tests (requires Docker Desktop for Testcontainers):**
  ```powershell
  cd backend
  mvn test
  ```
- **Frontend unit tests and production build:**
  ```powershell
  cd frontend
  npm test
  npm run build
  ```
- **Playwright end-to-end journey test** (start PostgreSQL + backend with `$env:APP_SAMPLE_DATA_ENABLED="false"` piped to `dev-run.log` + Vite dev server on an empty DB):
  ```powershell
  cd frontend
  npm run test:e2e
  ```

## Deployment
See [docs/DEPLOY.md](docs/DEPLOY.md) for full instructions on deploying **ScheduliX** on **Neon** (PostgreSQL 16), **Render** (Docker), **Netlify** (React/Vite proxy + SPA), and **Brevo** (SMTP).
