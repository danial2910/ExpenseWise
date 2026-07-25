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
