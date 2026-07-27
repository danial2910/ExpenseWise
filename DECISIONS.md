# Decisions

Non-obvious decisions and their rationale, logged as they're made.

## 2026-07-25 — Foundation phase

- **Single-module Maven, not multi-module.** One deployable JAR; package-by-feature
  already gives isolation at the package level. Multi-module would add reactor/wiring
  overhead with no payoff for a solo project.
- **No empty feature-module packages created this phase.** Only `config`, `common`,
  `exception`, `health` exist. `auth`, `category`, `transaction`, etc. get built fully
  (controller/service/repository/entity/dto/mapper) in their own dedicated session.
- **`health` is its own controller/service/dto package**, not folded into `config`,
  since it's a permanent endpoint following the standard layered pattern.
- **Two partial unique indexes** added (`categories` system-row uniqueness,
  `budgets` overall-vs-category uniqueness) because Postgres treats NULL as distinct
  in a plain `UNIQUE` constraint — a naive `UNIQUE(user_id, category_id, period_month)`
  would not stop duplicate NULL-category rows for the same user+month.
- **FK `ON DELETE` behavior**: CASCADE for strictly personal data with no standalone
  value; RESTRICT wherever deleting a category would silently touch financial history
  (transactions, recurring_rules); SET NULL where the child must independently survive
  and losing the link is harmless (transactions.recurring_rule_id, activity_logs.user_id).
- **`updated_at` will be handled by JPA `@UpdateTimestamp`** (future entity work), not a
  Postgres trigger — more transparent for a solo project that needs every line explained
  in a demo. DB-level `DEFAULT now()` on `created_at`/`updated_at` remains as a safety net
  for any raw SQL writes.
- **CORS origin hardcoded to `localhost:5173`** for this phase, not env-configurable yet.
- **Vite uses a full `VITE_API_BASE_URL`**, not a dev-server proxy, so the CORS config is
  actually exercised in dev and the setup matches prod's cross-origin reality (separately
  hosted static frontend vs. Supabase-backed API).
- **`amount > 0` CHECK constraints** added on transaction/recurring_rule/budget amounts
  (zero-value entries treated as a data-entry mistake by default; easy to relax later).
- **Extra CHECK constraints** added beyond the literal schema spec: positive amounts,
  `recurring_rules.end_date >= start_date`, `budgets.period_month` must be first-of-month.
  Confirmed with the project owner as a DB-level safety net consistent with the project's
  "boring, explicit" ethos.

## 2026-07-26 — Jenkins CI/CD (replaced GitHub Actions)

- **Switched CI from GitHub Actions to a self-hosted Jenkins + SonarQube stack**
  running in Docker (`ci/docker-compose.yml` documents the intended stack). The
  committed `.github/workflows/ci.yml` is now superseded and kept only as
  reference — Jenkins is the real pipeline. `Jenkinsfile` at repo root defines it.
- **Trigger is SCM polling (`H/2 * * * *`), not a webhook.** Local Jenkins isn't
  publicly reachable, so GitHub can't push events in; Jenkins polls GitHub instead
  (outbound, always works). Behaves the same for a demo, ~2 min latency.
- **Pipeline stages:** Checkout -> Backend build+test -> Frontend build -> Sonar
  analysis -> Quality Gate -> Archive. Backend and frontend both run on the
  built-in Jenkins node (JDK 21, Maven, Node via Global Tools).
- **Integration-test DB is a dedicated `ci-postgres` container on the `devops-net`
  network** that Jenkins shares. The app's dev Postgres sits on a different network
  (`code_default`) Jenkins can't reach, so a throwaway CI DB was added and the
  build points `DB_URL` at `ci-postgres:5432`. Keeps dev data untouched.
- **Sonar runs via `withSonarQubeEnv('SonarQube')`**; the token lives in the global
  SonarQube server config (credential `sonar-token-expensewise`, a Global Analysis
  token), never in the Jenkinsfile. Sonar host from inside CI is `sonarqube:9000`
  (container name), not `localhost` — the build runs inside the Jenkins container.
- **Quality Gate uses `waitForQualityGate abortPipeline:false`** for now: it
  reports Sonar's verdict but does not fail the build. A Sonar->Jenkins webhook
  (`http://jenkins:8080/sonarqube-webhook/`) lets `waitForQualityGate` get its
  answer. Flip `abortPipeline` to true once coverage clears the gate's threshold.
- **JaCoCo now wired into `backend/pom.xml`** (`prepare-agent` + `report` bound to
  `verify`), producing `target/site/jacoco/jacoco.xml` — a default Sonar scan path,
  so coverage imports automatically with no extra property. Before this, Sonar
  showed 0% for everything (no report imported) and the gate failed on coverage;
  VS Code showed real coverage because it runs its own JaCoCo. They weren't
  disagreeing — Sonar simply had no data.
- **Environment gotchas hit during setup (all fixed):** (1) the bundled Node from
  the NodeJS plugin needs `libatomic1`, missing from `jenkins/jenkins:lts` —
  installed via apt into the container. (2) A 401 from Sonar was a stale/mismatched
  token value in the Jenkins credential; regenerating the token and re-entering the
  credential secret fixed it. (3) After a host reboot, integration tests failed with
  `Failed to load ApplicationContext` because `ci-postgres` was a loose `docker run`
  with no restart policy and didn't come back. Recreated with
  `--restart unless-stopped`. All three are container/ops issues, not code.
- **Pipeline depends on three containers being `Up`: `jenkins`, `sonarqube`,
  `ci-postgres`** (plus `sonar-db` for SonarQube itself). All carry restart policies,
  so they self-start with Docker Desktop — no manual `docker start` needed normally.
  A build failing with `Failed to load ApplicationContext` on the integration tests
  is the signature of `ci-postgres` being down: check `docker ps` first.

## 2026-07-26 — CI setup phase

- **GitHub Actions, two parallel jobs (`backend`, `frontend`)** in a single
  `ci.yml`, triggered on push to `main` and every pull request. Kept as one
  workflow for a solo project; split later only if job counts grow.
- **Integration tests run against a Postgres 16 service container**, mapped to
  host port `5433` so `application-local.yml`'s default `DB_URL` works with zero
  extra config. This is the Linux-CI environment DECISIONS.md always intended
  the integration tests for — no Windows/Testcontainers npipe bug here.
- **`JWT_SECRET` supplied as a fixed dummy env var in CI** (>=32 bytes for
  HS256). `jwt.secret` has no default in `application.yml`, so the context won't
  start without it; the value signs test tokens only and is never a real secret.
- **`mvn -B clean verify`** runs both unit and integration tests — the
  integration classes end in `IntegrationTest`, matched by Surefire's default
  `*Test` pattern, so they run in the normal test phase (no Failsafe config).
- **Frontend job runs `npm ci` + `npm run build`.** `build` is
  `vue-tsc -b && vite build`, so type errors fail CI. No separate lint step —
  there's no lint script in `package.json` yet.
- **Playwright E2E deliberately deferred to a follow-up CI stage.** It needs
  backend + frontend + Postgres running together (plus a browser); the first
  pipeline stays fast and green with build + unit + integration coverage.
- **Playwright uses system Google Chrome (`channel: 'chrome'`)** locally, not
  the bundled Chrome-for-Testing build, which Windows Smart App Control blocks
  (unsigned `chrome_elf.dll` → browser killed on launch).

## 2026-07-25 — Auth & user management phase

- **Refresh-token "family" = all of a user's active tokens**, not a per-device chain.
  The given `V2__refresh_tokens.sql` schema has no chain/family column. Reusing a
  revoked token is treated as a compromise signal: every other active token for that
  `user_id` is revoked too. Coarser than per-device tracking (kills all sessions, not
  just the stolen chain) but matches the given schema exactly and is the more
  conservative default.
- **`is_active` cache and login rate limiter are plain in-memory `ConcurrentHashMap`s**
  (`UserStatusCache`, `RateLimiterService`), not Caffeine/Redis. Gives immediate
  consistency (evicted synchronously on admin enable/disable) at zero DB cost per
  request, for this project's single-instance deployment. Would not stay consistent
  across multiple app instances — that needs a shared cache, explicitly out of scope.
- **Lombok and MapStruct introduced** in this phase (`pom.xml`) — first real use in the
  codebase. Entities use `@Getter @Setter @NoArgsConstructor` only, never `@Data`/
  `@EqualsAndHashCode`, per the hard rule.
- **`MailService` is a single implementation with an internal blank-host check**, not
  two conditionally-registered beans. `@ConditionalOnProperty` can't reliably
  distinguish "env var unset" from "property present but empty" via Spring's relaxed
  binding, so the safer, more boring choice is one bean that logs the reset link when
  `brevo.smtp-host` is blank and actually sends otherwise.
- **Refresh cookie**: `HttpOnly`, `Path=/api/v1/auth` (never sent outside auth
  endpoints), `SameSite=Lax`, `Secure` driven by `cookie.secure` (false in
  `application-local.yml`, true in `application-prod.yml`). No separate CSRF token —
  the Path/HttpOnly/SameSite combination is the mitigation for this endpoint, which has
  no other cookie-authenticated side-effecting route.
- **Register logs the user straight in** (per the imported design) — only
  forgot-password uses the "check your email" screen.
- **Nested `@Transactional` pitfall**: `RefreshTokenService.rotate()` uses
  `noRollbackFor = InvalidTokenException` so the reuse-detection revocation commits
  even though the method then throws. This only works if `rotate()` is the outermost
  transaction boundary — `AuthService.refreshAccessToken()` is deliberately **not**
  `@Transactional`, since wrapping it too would make it the actual transaction owner
  and its default rollback rules would override `rotate()`'s.
- **`ActivityLogger` runs in `Propagation.REQUIRES_NEW`.** Several callers (a failed
  login, a rejected refresh) log an event and then deliberately throw; without its own
  transaction, that audit row would roll back along with the rest of the request.
- **Admin endpoints reuse `UserResponse`**, no separate `AdminUserResponse` — the shape
  is identical and a parallel DTO would be pure duplication.
- **Integration tests run against the project's own docker-compose Postgres**
  (port 5433), not Testcontainers. Testcontainers was the original plan, but its npipe
  client hits a Docker-Desktop-on-Windows compatibility bug in this dev environment
  (the `docker` CLI talks to the daemon fine; docker-java's raw npipe requests get a
  malformed response). Testcontainers works on Linux CI; this is a local-dev-only
  adaptation. Tests use uniquely generated emails to avoid colliding with real data in
  the same database.
- **PrimeVue's `cssLayer.order` must be declared in the `PrimeVue` plugin config
  itself**, not only via a `@layer name-list;` statement in `style.css`. PrimeVue
  injects its own `@layer` order-declaration `<style>` tag before the app's own CSS
  loads, so it wins the "first mention establishes cascade layer order" race unless
  told the full order (`tailwind-base, primevue, tailwind-components,
  tailwind-utilities`) up front.
- **`/actuator/health` was never real** — Phase 1 built its own `/api/v1/health`
  instead of adding the actuator starter. `SecurityConfig`'s public-path list points at
  the real endpoint.

## 2026-07-27 — Transaction module phase

- **DATE column money-arithmetic is unaffected by TZ.**
  `transactions.transaction_date` is a plain DATE with no time component, so every
  explicit `from`/`to` filter compares dates directly with no conversion.
- **Correction (same day): the summary endpoint no longer defaults to "this
  calendar month" when `from`/`to` are both omitted.** The first version added a
  KL-anchored (`Asia/Kuala_Lumpur`) implicit "this month" default to
  `TransactionService.getSummary`, reasoning that *some* endpoint should exercise
  the this-month-boundary rule from CLAUDE.md. In practice this silently
  diverged from `listTransactions`, which has no such default and shows all-time
  by default — the summary strip would show RM 0.00 for real data outside the
  current month while the list right below it showed those same rows, which read
  as a bug in a demo. Fixed by having the summary sum exactly the same filter set
  the list uses (no date filter = all-time), so the two always agree. The
  KL-anchored "this month" boundary rule from CLAUDE.md still applies wherever a
  screen genuinely needs a "this month" default (e.g. a future Dashboard widget)
  — it just isn't this endpoint, since it has to mirror the list it summarizes
  instead of picking its own default.
- **`categoryName`/`categoryIcon` enrichment uses a second boring lookup, not a
  JPA join/association.** `Transaction` stores a bare `categoryId` Long (matching
  the FK), not a `@ManyToOne Category`, keeping the entity a flat mirror of its
  table like `Category` already is. `TransactionMapper.toResponse` takes both the
  transaction and its `Category` as separate arguments; the service resolves the
  category itself — a single `findById` for get/create/update, one batched
  `findAllById` across the page's distinct category ids for the paginated list (to
  avoid N+1 queries). Category volumes are tiny, so this is simpler to explain in a
  demo than configuring a fetch join or `@EntityGraph`.
- **Category coherence errors (type mismatch, or a category outside the caller's
  visibility) both collapse into one `InvalidTransactionCategoryException`**,
  mapped to a 400 `VALIDATION_FAILED` on the `categoryId` field — mirrors how
  `CategoryService` collapses "system category" and "someone else's category" into
  the same 404 for the same "don't leak details, give one clean answer" reason.
- **The Add/Edit transaction dialog omits the receipt upload and "make this
  recurring" controls that appear in the imported Figma design.** Those belong to
  the receipt and recurring modules, both explicitly out of scope for this
  session (CLAUDE.md: "Do not touch budgets/dashboard/recurring/receipt
  modules"). The dialog matches the design's type toggle, amount field, date,
  category select, and description field exactly; the two extra sections will
  slot in when those modules are built.
- **Two Figma designs were found and used**: "ExpenseWise Transactions" (list +
  summary + filters) and "ExpenseWise Add Transaction" (dialog), both in the
  existing `ExpenseWise` Claude Design project. Built to the desktop (1440px)
  frame only, consistent with the Category module's precedent of deferring the
  separate mobile/tablet shell (different sidebar pattern, no responsive
  treatment on `AppLayout` yet) to its own session.
- **Only one Playwright E2E test added for this module** (`transactions.spec.ts`):
  record an expense and an income, verify both appear with the balance updating
  correctly, edit one, delete it. Matches CLAUDE.md's "exactly one E2E test per
  module" rule going forward — `categories.spec.ts`'s four tests predate this
  being written down explicitly as a hard rule.

## 2026-07-26 — Category module phase

- **No `V3__seed_system_categories.sql` added.** V1__baseline.sql already seeds the
  13 system categories plus the partial unique index — writing a second seed
  migration would either duplicate that data or need `ON CONFLICT` gymnastics for
  no benefit. Confirmed with the project owner; V1's seed is the system category set.
- **Ownership checks collapse "system category" and "someone else's category" into
  the same 404.** `findOwnedOrThrow` treats any category where `userId` isn't the
  caller's as not-found, whether `userId` is null (system) or another user's id —
  matches the "don't leak existence of other users' records" rule and means system
  categories don't need a separate 403/read-only error path.
- **Uniqueness check follows the DB constraint exactly**: `(user_id, name, type)`.
  A custom category is allowed to share a name with a system category (the unique
  index is per-user_id, and NULL user_id is its own bucket) — not checked against
  system names, even though the imported design mockup's placeholder JS logic
  checks against all names. The DB schema is the source of truth here, not the
  mockup's local-state approximation.
- **`CategoryRequest` (create) doubles as the PUT body** — full update requires the
  same three fields as create, so a second near-identical DTO would be pure
  duplication. `PatchCategoryRequest` is separate because its fields are genuinely
  optional (null = unchanged), which needs different validation annotations.
- **Delete-in-use detection catches `DataIntegrityViolationException` around
  `delete()` + `flush()`**, rather than pre-checking for referencing transactions.
  The DB's `ON DELETE RESTRICT` on `transactions.category_id` is already the
  source of truth; catching its violation avoids a redundant existence query and
  a TOCTOU gap between check and delete.
- **Category icons reuse PrimeIcons** (already a project dependency) instead of
  inventing SVG assets. The V1 seed's icon keys (`utensils`, `film`, `laptop`,
  `trending-up`, `ellipsis`) predate PrimeIcons naming and are aliased to the
  closest available icon (`frontend/src/lib/categoryIcons.ts`) rather than
  changing seeded DB values.
- **Mobile app shell intentionally not built this phase.** The imported Categories
  design's 390px frame uses a different shell (bottom tab bar, FAB, no sidebar)
  than the current `AppLayout`, which has no responsive treatment yet and is
  shared by every screen (Dashboard included). Redesigning it is cross-cutting,
  out of scope for a single-module session, and confirmed with the project owner
  to defer to a dedicated pass. Categories ships with the existing desktop-first
  `AppLayout`; its content grids do reflow at narrower widths.
- **`AppLayout` gained a `title` prop and route-aware nav highlighting** (was
  hardcoded to "Dashboard" with one static nav item) — the minimum shared-layout
  change needed to host a second real screen, not a mobile redesign.
