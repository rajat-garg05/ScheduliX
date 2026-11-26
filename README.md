# ScheduliX

**Live App:** https://schedulix-tt.netlify.app/  
**Backend Health:** https://schedulix-backend-r6do.onrender.com/api/health  
*(The backend runs on Render's free tier, so the first request after 15 minutes of inactivity can take 30-60 seconds while the server wakes up.)*

ScheduliX is a web application for college departments to build weekly class timetables and manage daily teacher leave and substitute assignments.

An HOD sets up teachers, subjects, eligibility mappings, and weekly semester plans (manually or from CSV), checks feasibility before scheduling, pins fixed slots if needed, generates clash-free timetables across multiple sections together, and handles day-to-day leave requests with substitute suggestions. Teachers sign in to view department schedules, check daily cover on `Today`, view their personal timetable, and submit leave requests.

## Tech Stack

- **Backend:** Java 21, Spring Boot 4.1.1, Spring Security (JWT + HttpOnly refresh cookies), Spring Data JPA, Flyway, PostgreSQL 16
- **Frontend:** React 19, Vite, Tailwind CSS, React Query, React Router
- **Testing:** JUnit 5, Testcontainers, Vitest, React Testing Library, Playwright

## Repository Layout

- `backend/` - Spring Boot API, Flyway database migrations (`V1` to `V7`), and timetable scheduling engine (`com.schedulix.engine`)
- `frontend/` - React + Vite frontend app, unit tests, and end-to-end tests
- `docs/` - User guides:
  - [HOD Guide](docs/HOD_GUIDE.md)
  - [Teacher Guide](docs/TEACHER_GUIDE.md)
- `scripts/` - PowerShell scripts for local startup and testing ([start.ps1](scripts/start.ps1), [test.ps1](scripts/test.ps1))
- `docker-compose.yml` - Local PostgreSQL 16 service on port `5434`

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+ and npm
- Docker Desktop (for local PostgreSQL and Testcontainers backend tests)

## Local Setup (Windows PowerShell)

1. Copy `.env.example` to `.env` if needed and install frontend packages:
   ```powershell
   cd frontend
   npm install
   cd ..
   ```

2. Start PostgreSQL, backend (`http://localhost:8080`), and frontend (`http://localhost:5173`):
   ```powershell
   .\scripts\start.ps1
   ```

   Or start each service individually:
   ```powershell
   docker compose up -d

   cd backend
   $env:SPRING_PROFILES_ACTIVE = "dev"
   mvn spring-boot:run
   ```
   ```powershell
   cd frontend
   npm run dev
   ```

### Local Demo Data

With `SPRING_PROFILES_ACTIVE=dev` and `APP_SAMPLE_DATA_ENABLED=true` (default in `dev`), the backend seeds a sample CSE department from `backend/src/main/resources/sample/`:
- **HOD:** `hod@cse-demo.edu` / `HodDemo@123`
- **Teachers:** e.g. `arjun.mehta@cse-demo.edu` / `ScheduliX@123` (must be changed on first sign-in)

Set `$env:APP_SAMPLE_DATA_ENABLED = "false"` to start with a blank database.

## Tests & Build

- **Run all backend and frontend tests:**
  ```powershell
  .\scripts\test.ps1
  ```

- **Backend tests (requires Docker Desktop):**
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

- **End-to-end test (requires PostgreSQL, backend with `APP_SAMPLE_DATA_ENABLED=false`, and Vite dev server running):**
  ```powershell
  cd frontend
  npm run test:e2e
  ```
