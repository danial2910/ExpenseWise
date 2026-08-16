# Deployment

ExpenseWise deploys as three separate pieces on three separate platforms:

| Piece | Platform | Notes |
|---|---|---|
| Frontend (Vue static build) | Vercel | Static hosting, build-time env var for the API URL |
| Backend (Spring Boot) | Render | Docker web service, built from `backend/Dockerfile` |
| Database + file storage | Supabase | Postgres via the Supavisor pooler, Storage for receipts/avatars |

Vercel and Render are different domains, so this is a cross-origin deployment:
CORS must explicitly allow the Vercel origin, and the refresh-token cookie
needs `SameSite=None; Secure=true` to survive the cross-site round trip.
Local dev, tests, and Jenkins CI are unaffected — every new setting here is
profile/env-driven and defaults to today's local behaviour when unset (see
`DECISIONS.md`).

---

## 1. Supabase (database + storage)

1. Create a Supabase project. Note the project ref, database password, and
   the `service_role` key (Project Settings → API).
2. **Connection string** — use the **Supavisor pooler**, not the direct
   connection, and always in transaction mode on port **6543**:

   ```
   jdbc:postgresql://<project-ref>.pooler.supabase.com:6543/postgres?prepareThreshold=0
   ```

   `?prepareThreshold=0` is required: Supavisor's transaction-mode pooling
   breaks JDBC's server-side prepared statement caching without it (see
   CLAUDE.md). This full URL is `DB_URL_EXPENSEWISE` on Render.
3. Flyway migrations run automatically on backend startup
   (`spring.flyway.enabled: true`), against this same connection — no manual
   migration step.
4. Create a **private** Storage bucket (matches `SUPABASE_BUCKET_EXPENSEWISE`)
   for avatars/receipts. The backend talks to it using the `service_role`
   key and issues signed URLs; the bucket itself stays private.
5. Supabase Auth, RLS, Realtime, and the auto-generated REST API are **not**
   used — out of scope per CLAUDE.md. Spring Boot is the only thing that
   talks to the database.

---

## 2. Backend on Render

Render builds `backend/Dockerfile` as a **Docker web service**.

### Setup

1. New Web Service → connect the repo → root directory `backend` → environment
   **Docker** (Render auto-detects the Dockerfile).
2. Render injects `PORT` itself; `server.port: ${PORT:8080}` in
   `application.yml` already honours it — nothing to configure.
3. Set **Environment Variables** (Render dashboard → Environment):

   | Variable | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DB_URL_EXPENSEWISE` | Supavisor URL from step 1.2 above |
   | `DB_USER_EXPENSEWISE` | `postgres` (or your Supabase DB user) |
   | `DB_PASSWORD_EXPENSEWISE` | Supabase DB password |
   | `JWT_SECRET_EXPENSEWISE` | a strong random secret (not the CI/local one) |
   | `JWT_EXPIRATION_EXPENSEWISE` | `1800` (or your chosen value) |
   | `REFRESH_TOKEN_EXPIRATION_EXPENSEWISE` | `604800` |
   | `FRONTEND_URL_EXPENSEWISE` | your Vercel URL, e.g. `https://expensewise.vercel.app` |
   | `APP_CORS_ALLOWED_ORIGINS_EXPENSEWISE` | same Vercel URL (comma-separate if you have more than one, e.g. a preview domain) |
   | `SUPABASE_URL_EXPENSEWISE` | Supabase project URL |
   | `SUPABASE_SERVICE_KEY_EXPENSEWISE` | Supabase `service_role` key — **backend only, never in frontend config** |
   | `SUPABASE_BUCKET_EXPENSEWISE` | bucket name from step 1.4 |
   | `GROQ_API_KEY_EXPENSEWISE` / `GROQ_MODEL_EXPENSEWISE` | Groq credentials |
   | `NEWSDATA_API_KEY_EXPENSEWISE` | NewsData.io key |
   | `BREVO_API_KEY_EXPENSEWISE` / `MAIL_FROM_EXPENSEWISE` | Brevo transactional email credentials |

   All names keep the project's `_EXPENSEWISE` suffix (see CLAUDE.md) except
   `SPRING_PROFILES_ACTIVE` and `PORT`, which are Spring Boot's/Render's own
   conventional names.

4. `SPRING_PROFILES_ACTIVE=prod` activates `application-prod.yml`:
   `cookie.secure=true`, `cookie.same-site=None` (required for the
   cross-site refresh cookie between Vercel and Render — `None` requires
   `Secure`, which is set alongside it).
5. Deploy. Confirm `GET https://<render-url>/api/v1/health` returns
   `{"status":"UP","database":"UP",...}` — it's on `PUBLIC_PATHS` in
   `SecurityConfig`, so no auth is needed to check it.

### Cold-start mitigation

Render's free/starter web services spin down after a period of inactivity
and take tens of seconds to cold-start on the next request. Point an
external uptime pinger (e.g. UptimeRobot, cron-job.org, or any scheduled
HTTP checker) at `/api/v1/health` on an interval of **~10 minutes** — well
under Render's idle-timeout window — to keep the instance warm. The
Dockerfile also sets `JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1` to shorten
JVM warm-up on whatever cold starts do happen.

---

## 3. Frontend on Vercel

1. New Project → import the repo → root directory `frontend` → framework
   preset **Vite**.
2. Build command `npm run build` (already `vue-tsc -b && vite build`),
   output directory `dist` — Vercel's Vite preset detects both by default.
3. Set one environment variable (Project Settings → Environment Variables):

   | Variable | Value |
   |---|---|
   | `VITE_API_BASE_URL` | `https://<render-url>/api/v1` |

   This is read at **build time** (`import.meta.env.VITE_API_BASE_URL` in
   `frontend/src/api/http.ts`) — never hardcode the backend URL, and set it
   for every Vercel environment (Production, Preview) you use, since each
   gets baked into that environment's own build.
4. The shared axios instance already sets `withCredentials: true`, so the
   `refreshToken` cookie is sent/received cross-site once `APP_CORS_ALLOWED_
   ORIGINS_EXPENSEWISE` on the backend includes this Vercel URL and the
   cookie's `SameSite=None; Secure=true` (step 2.4) are both in place.
5. Deploy. Log in against the deployed backend and confirm the session
   survives a page refresh (proves the refresh cookie round-trips
   cross-site correctly).

---

## Local dev / CI — unchanged

Nothing above changes local or CI behaviour:

- No env var here is set locally or in Jenkins, so every new property falls
  back to its existing default: `APP_CORS_ALLOWED_ORIGINS_EXPENSEWISE` unset
  → `http://localhost:5173`; `SPRING_PROFILES_ACTIVE` unset → `local` →
  `cookie.secure=false`, `cookie.same-site=Lax`, exactly as before.
- `backend/Dockerfile` is only used by Render; local dev still runs via
  `mvn spring-boot:run` against Docker Postgres, and integration tests still
  run with `@ActiveProfiles("local")`.
- The Jenkins pipeline is untouched — `mvn -B clean verify`, `npm run
  build`, and the SonarQube quality gate all run exactly as before.
