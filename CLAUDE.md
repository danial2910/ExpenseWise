# ExpenseWise

Personal finance / expense tracker. Solo project, academic deliverable.
I have to demo this and explain every line of it, so favour clear,
conventional code over clever code.

---

## Stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3.x, Java 21, Maven |
| Database | PostgreSQL — local Docker for dev/test, Supabase hosted for deploy |
| Migrations | Flyway |
| Frontend | Vue 3 (Composition API) + Vite + Pinia + Vue Router |
| Styling | Tailwind + PrimeVue |
| HTTP client | Axios |
| Auth | Spring Security + JWT (jjwt). **Not** Supabase Auth. |
| File storage | Supabase Storage, private bucket, signed URLs |
| AI | Groq via Spring AI (OpenAI-compatible endpoint) |
| Email | Brevo transactional email HTTP API (Spring `RestClient`) + Thymeleaf templates. **Not** SMTP. |
| News | NewsData.io (`RestClient`), shared cache via Spring's cache abstraction + Caffeine (15–30 min TTL) |
| Reports | Apache POI (Excel), JasperReports (PDF) |
| Unit tests | JUnit 5 + Mockito |
| Coverage | JaCoCo (XML report consumed by SonarQube) |
| E2E tests | Playwright (TypeScript), system Chrome via `channel: 'chrome'` |
| CI/CD | Jenkins (self-hosted in Docker), SCM polling on `main`. See `Jenkinsfile` |
| Code quality | SonarQube (self-hosted in Docker), quality gate in the pipeline |

---

## Hard rules

Violating any of these is a bug, not a style preference.

### Money
- Money is **always** `BigDecimal` in Java and `DECIMAL(12,2)` in Postgres.
- Never `double`, never `float`, never `Double`.
- Never do money arithmetic with `+` on primitives. Use `BigDecimal.add()` etc.
- Always specify `RoundingMode` explicitly when dividing.

### Time
- All timestamps stored as `TIMESTAMPTZ` in **UTC**.
- Convert to Asia/Kuala_Lumpur in the presentation layer only.
- "This month" boundaries are computed in the user's timezone, then converted
  to UTC for the query. Do not compare UTC dates directly against month
  boundaries.

### Security
- Every endpoint touching user data must verify ownership **server-side**.
  Checking in the Vue router is not a security control.
- Never trust a `userId` from the request body or query string. Derive it from
  the authenticated principal.
- The Supabase `service_role` key lives in backend env vars only. It must never
  appear in frontend code, frontend env files, or any committed file.
- Passwords hashed with bcrypt, minimum 10 rounds. Never log a password, a
  token, or a full JWT.
- Password reset tokens are stored **hashed**, single-use, with an expiry.
- Never return stack traces or internal exception messages to the client.

### Database
- Flyway only. `spring.jpa.hibernate.ddl-auto` must be `validate`, never
  `update` or `create`.
- Never edit an applied migration. Add a new `V{n}__description.sql`.
- All monetary write operations are `@Transactional`.

### Frontend
- Every interactive element gets a `data-testid`. Playwright depends on this.
- No business logic in components. Calculations belong on the backend.
- API calls go through the shared axios instance, never bare `fetch`.

---

## Architecture

Strict layering. Dependencies point downward only.

```
controller  ->  service  ->  repository  ->  entity
     |             |
    dto          mapper
```

- Controllers: HTTP concerns only. No business logic, no repository access.
- Services: business logic, transactions, authorisation checks.
- Repositories: Spring Data JPA interfaces.
- **JPA entities never leave the service layer.** Controllers accept and return
  DTOs. Map with MapStruct.
- Validation via Jakarta Bean Validation on request DTOs.

### Package structure

```
com.expensewise
├── config          Security, CORS, OpenAPI, scheduling
├── common          Shared utils, base classes
├── exception       Custom exceptions + @RestControllerAdvice
├── storage         StorageService seam + SupabaseStorageService impl,
                    shared across any module storing a file (Profile's
                    avatar, Receipt later) — mirrors the ai.client seam
└── <module>        auth, user, category, transaction, budget,
                    recurring, receipt, report, ai, news, admin
    ├── controller
    ├── service
    ├── repository
    ├── entity
    ├── dto
    └── mapper
```

Package by feature, not by layer, at the top level.

---

## API conventions

- Base path `/api/v1`
- Plural resource nouns: `/api/v1/transactions`, `/api/v1/budgets`
- Standard verbs. `POST` create, `GET` read, `PUT` full update,
  `PATCH` partial, `DELETE` remove.
- List endpoints are **always paginated**. Default page size 20, max 100.
- Consistent error shape:

```json
{
  "timestamp": "2026-01-15T08:30:00Z",
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Amount must be greater than zero",
  "path": "/api/v1/transactions",
  "fieldErrors": { "amount": "must be greater than zero" }
}
```

- `401` not authenticated, `403` authenticated but not allowed,
  `404` not found or not yours (do not leak existence of other users' records).

---

## Data model

11 tables. Full DDL lives in `V1__baseline.sql`.

**Core financial**
- `users` — id, email UK, password_hash, full_name, role, is_active, phone,
  date_of_birth, gender, address, avatar_path, timestamps (phone/date_of_birth/
  gender/address/avatar_path all nullable — self-service profile fields, added
  in the Profile phase)
- `categories` — id, user_id FK NULL, name, type, icon, is_system
- `transactions` — id, user_id FK, category_id FK, recurring_rule_id FK NULL,
  type, amount, transaction_date, description, timestamps
- `receipts` — id, transaction_id FK UK, storage_path, original_name,
  mime_type, size_bytes, uploaded_at
- `recurring_rules` — id, user_id FK, category_id FK, type, amount,
  description, frequency, start_date, end_date NULL, next_due_date, is_active
- `budgets` — id, user_id FK, category_id FK NULL, amount, period_month,
  created_at

**Supporting**
- `password_reset_tokens` — id, user_id FK, token_hash UK, expires_at, used_at
- `activity_logs` — id, user_id FK NULL, action, entity_type, entity_id,
  ip_address, created_at
- `ai_conversations` — id, user_id FK, title, created_at
- `ai_messages` — id, conversation_id FK, role, content, created_at

### Design decisions that are easy to get wrong

- **Income and expense share one `transactions` table**, distinguished by
  `type`. This keeps balance, recent-transactions, and income-vs-expense
  queries to a single statement. They are recorded on **one unified
  Transactions page** (a type toggle chooses INCOME vs EXPENSE) backed by a
  single `TransactionService` and a single `/api/v1/transactions` resource —
  not separate expense/income pages or services.
- `categories.user_id IS NULL AND is_system = true` means a built-in category
  visible to all users. A non-null `user_id` means a user's custom category.
- `categories.type` is `INCOME` or `EXPENSE`, so income sources need no
  separate table.
- `budgets.category_id IS NULL` means the overall monthly budget. Non-null
  means a category budget. Unique on `(user_id, category_id, period_month)`.
- `budgets.period_month` stores the first day of the month.
- `recurring_rules` are **templates and never appear in balances**. A scheduled
  job creates real `transactions` rows from them and advances `next_due_date`.
  Generated rows keep `recurring_rule_id` for traceability.
- `activity_logs.user_id` is nullable so failed logins against unknown emails
  can still be logged.

### Enums

Stored as strings, never ordinals.

- `role` — `USER`, `ADMIN`
- `type` (transactions, categories, recurring_rules) — `INCOME`, `EXPENSE`
- `frequency` — `WEEKLY`, `MONTHLY`, `YEARLY`
- `ai_messages.role` — `user`, `assistant`

---

## Commands

```bash
docker compose up -d          # Postgres on localhost:5433
mvn spring-boot:run           # API on :8080
mvn test                      # unit tests
npm run dev                   # Vue on :5173
npx playwright test           # e2e
```

Local DB: `expensewise` / user `dev` / password `devpass` / port `5433`.

---

## Environment

All secrets come from env vars. `.env` is gitignored; `.env.example` is
committed with empty values.

Every var carries an `_EXPENSEWISE` suffix, so it can't collide with an
identically-named env var from another project on the same dev machine:

```
DB_URL_EXPENSEWISE, DB_USER_EXPENSEWISE, DB_PASSWORD_EXPENSEWISE
JWT_SECRET_EXPENSEWISE, JWT_EXPIRATION_EXPENSEWISE, REFRESH_TOKEN_EXPIRATION_EXPENSEWISE
SUPABASE_URL_EXPENSEWISE, SUPABASE_SERVICE_KEY_EXPENSEWISE, SUPABASE_BUCKET_EXPENSEWISE
GROQ_API_KEY_EXPENSEWISE, GROQ_MODEL_EXPENSEWISE
NEWSDATA_API_KEY_EXPENSEWISE
BREVO_API_KEY_EXPENSEWISE
MAIL_FROM_EXPENSEWISE, FRONTEND_URL_EXPENSEWISE
```

**Supabase connection:** use the Supavisor pooler (port 6543) in prod, and
append `?prepareThreshold=0` to the JDBC URL. Transaction-mode pooling breaks
JDBC prepared statement caching without it. Keep the Hikari pool small (5–10).

---

## Testing

Test pyramid, not an ice cream cone.

- **Unit tests** for business logic: budget utilisation, balance calculation,
  financial health score, recurring date advancement, report aggregation.
  These are where correctness lives.
- **Integration tests** for repositories and security rules.
- **E2E (Playwright):** each feature module includes all e2e test cases covering for **all possible cases**
  covering that module's single critical user journey (e.g. category: create →
  see it listed → edit → delete). One focused journey per module — never
  E2E-test every field or validation; that belongs in unit/integration tests.
  This keeps a per-phase safety net that catches cross-phase regressions while
  staying disciplined (roughly one per module, not sixty).
- Run E2E against local Docker Postgres. Never against Supabase.
- Do not assert on AI response content — it is non-deterministic. Mock the
  Groq call or assert only that a response rendered.

Security tests worth writing explicitly:
- User A cannot read, update, or delete user B's records (expect 403/404)
- A non-admin cannot reach admin endpoints
- A disabled user cannot authenticate or use an existing token

---

## Working style

- **One module per session.** Build a vertical slice end to end (migration →
  entity → repository → service → controller → DTO → tests → Vue page) before
  starting the next module.
- The transaction module is the **reference pattern**. When building later
  modules, read those files first and follow the same structure.
- Do not scaffold multiple modules at once.
- Prefer boring, explicit code. If a junior developer would need to ask what a
  line does, rewrite it.
- If a requirement here is ambiguous or looks wrong, say so before
  implementing. Do not guess and continue.
- Log non-obvious decisions in `DECISIONS.md` with a one-line rationale.

---

## Out of scope

Do not add these unless explicitly asked:

- Supabase Auth, Row Level Security, Realtime, or the auto-generated REST API.
  Spring Boot is the only thing that talks to the database.
- Multi-currency. Everything is MYR.
- Expense prediction / forecasting. Cut from scope deliberately.
- Redis, message queues, microservices, event sourcing.
- Any dependency not already listed in this file — propose it first.

## Design system

The design system originates in Claude Design and is imported into this
repo. The imported tokens are the source of truth — do not invent values.

- Claude Design MCP is connected. Use it to fetch designs and components
  rather than guessing at layout or spacing.
- Tokens live in [src/assets/main.css @theme block | tailwind.config.js]
  — update this line to match the actual location after import.
- PrimeVue preset in src/theme/preset.js via definePreset over Aura.
  The preset and the Tailwind tokens must always hold the same values.
  Changing one without the other is a bug.
- CSS layer order is configured so Tailwind can override PrimeVue.
  Never use `!` prefixes to force specificity.
- PrimeVue owns component appearance. Tailwind owns layout only
  (flex, grid, gap, padding, margin, width).

## Design fidelity rules

- Never hardcode a colour, spacing value, font size, or radius in a
  component. Tokens only.
- All currency renders through <MoneyDisplay>. No inline toFixed, no
  manual "RM " prefixes anywhere.
- Reuse components in src/components/common. Check there before building
  anything new.
- Every screen needs designed empty, loading, and error states.
- Every interactive element gets a data-testid.
- If a design needs something the tokens don't cover, stop and tell me.
  Do not invent a value.