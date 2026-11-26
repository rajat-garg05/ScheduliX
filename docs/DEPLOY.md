# ScheduliX Deployment Guide (Neon + Render + Netlify + Brevo)

This guide covers deploying **ScheduliX** on free hosting tiers:
- **Database:** Neon (PostgreSQL 16)
- **Backend:** Render (Docker web service using [backend/Dockerfile](file:///d:/project/ScheduliX/schedulix/backend/Dockerfile))
- **Frontend:** Netlify (React/Vite app using [frontend/netlify.toml](file:///d:/project/ScheduliX/schedulix/frontend/netlify.toml))
- **Email (SMTP):** Brevo

> **Why Netlify proxies `/api/*`:** Refresh tokens use `HttpOnly; Secure; SameSite=Strict` cookies (`AuthController.java`). Because Netlify forwards `/api/*` to Render with status `200`, the browser communicates strictly with your single Netlify origin, so authentication cookies work reliably without third-party cookie blocking.

---

## 1. Set up Neon (PostgreSQL 16)
1. Create a free PostgreSQL 16 project on [Neon](https://neon.tech).
2. Copy the JDBC connection details:
   - Host, database name (e.g. `schedulix`), username, and password.
   - Build your JDBC URL with SSL required:
     ```text
     jdbc:postgresql://<your-neon-host>.neon.tech/<db-name>?sslmode=require
     ```
3. Flyway runs all schema migrations (`V1` through `V7`) automatically on first backend startup when `ddl-auto: validate` runs.

---

## 2. Set up Brevo (SMTP email)
1. Create a free [Brevo](https://www.brevo.com) account and verify your sender address (used as `MAIL_FROM`).
2. Under **SMTP & API → SMTP**, generate an SMTP key and note:
   - **Host:** `smtp-relay.brevo.com`
   - **Port:** `587`
   - **Username:** your Brevo SMTP login (for example `...@smtp-brevo.com`)
   - **Password:** your Brevo SMTP key

---

## 3. Deploy the Backend on Render (Docker)
1. Push your repository to GitHub (make sure `.env` and `*.log` are not committed).
2. On [Render](https://render.com), create a **New Web Service** connected to your repository:
   - **Runtime:** Docker
   - **Root Directory:** `backend` (so `backend/Dockerfile` and `backend/.dockerignore` are used)
   - **Health Check Path:** `/api/health`
3. Configure these environment variables in Render (matches [application-prod.yml](file:///d:/project/ScheduliX/schedulix/backend/src/main/resources/application-prod.yml)):

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<neon-host>.neon.tech/<db>?sslmode=require` *(or `DB_URL`)* |
| `SPRING_DATASOURCE_USERNAME` | Your Neon DB username *(or `DB_USER`)* |
| `SPRING_DATASOURCE_PASSWORD` | Your Neon DB password *(or `DB_PASSWORD`)* |
| `APP_JWT_SECRET` | Random secret string of 32+ characters *(or `JWT_SECRET`)* |
| `APP_PUBLIC_URL` | `https://<your-site>.netlify.app` |
| `APP_CORS_ALLOWED_ORIGINS` | `https://<your-site>.netlify.app` |
| `APP_TIMEZONE` | `Asia/Kolkata` *(or your department's IANA timezone)* |
| `APP_MAIL_ENABLED` | `true` |
| `MAIL_HOST` | `smtp-relay.brevo.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USERNAME` | Your Brevo SMTP login |
| `MAIL_PASSWORD` | Your Brevo SMTP key |
| `MAIL_FROM` | Your verified sender email address |

4. Deploy and verify `https://<your-render-service>.onrender.com/api/health` returns `{"status":"ok"}`.

---

## 4. Deploy the Frontend on Netlify
1. Edit [frontend/netlify.toml](file:///d:/project/ScheduliX/schedulix/frontend/netlify.toml) and replace `YOUR-BACKEND-SERVICE.onrender.com` with your actual Render backend hostname:
   ```toml
   [[redirects]]
     from = "/api/*"
     to = "https://<your-render-service>.onrender.com/api/:splat"
     status = 200
     force = true
   ```
   *(Make sure the `/api/*` redirect remains above the `/*` SPA fallback rule.)*
2. On [Netlify](https://www.netlify.com), import the same repository:
   - **Base directory:** `frontend`
   - **Build command:** `npm run build`
   - **Publish directory:** `dist` (or `frontend/dist` depending on Netlify UI — matches `netlify.toml`)
3. Deploy and visit `https://<your-site>.netlify.app`. Confirm login, email verification links, and `/api/health` work through the Netlify domain.

---

## 5. Free-plan limits (what to expect)
- **Cold starts:** Render's free web service sleeps after ~15 minutes of inactivity; the first request after idle takes ~30–60 seconds while the JVM boots.
- **Database limits:** Neon's free tier caps storage and compute hours per month (plenty for one college department, not for large multi-tenant campuses).
- **Email quota:** Brevo's free plan caps daily outbound emails (usually 300/day).
- **Backups:** Free plans do not guarantee point-in-time recovery or long-term automated backups—run a weekly manual backup with `pg_dump`.

---

## 6. Weekly database backup (Windows PowerShell)
Install PostgreSQL client tools (`pg_dump`), set your Neon connection string, and save a compressed custom-format dump each week:

```powershell
$stamp = Get-Date -Format "yyyy-MM-dd"
$env:PGSSLMODE = "require"
pg_dump "postgresql://<neon-user>:<neon-password>@<neon-host>.neon.tech/<db-name>?sslmode=require" `
  --format=custom `
  --no-owner `
  --no-privileges `
  --file="schedulix-backup-$stamp.dump"
```
