# ScheduliX

Timetable management for a college department.

The HOD sets up teachers, subjects, and semester plans, builds timetables for classes in one go, keeps preferred lectures, and handles daily teacher leave with substitutes. Teachers can view department timetables, their own weekly schedule, today's substitutes, and submit leave requests.

## Requirements
- JDK 21
- Maven 3.9+
- Node.js 20+ and npm
- Docker Desktop (running)

## Project layout
- `backend/` - Single-module Spring Boot API and timetable engine
- `frontend/` - React + Vite (JavaScript) + Tailwind CSS web app
- `docs/` - Progress notes (`docs/PROGRESS.md`)
- `scripts/` - Local PowerShell helpers (`start.ps1`, `test.ps1`)
- `docker-compose.yml` - Local PostgreSQL 16 database

## Quick start (Windows PowerShell)
Make sure Docker Desktop is running, then from the `schedulix` folder:

```powershell
# First time only: install frontend packages
cd frontend
npm install
cd ..

# Start PostgreSQL, backend, and frontend
.\scripts\start.ps1