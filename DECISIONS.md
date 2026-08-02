# Decisions

Non-obvious decisions and their rationale, logged as they're made.

## 2026-08-02 — Profile module phase (personal info, avatar via Supabase Storage, security tab)

- **`StorageService`/`SupabaseStorageService` mirror the `AiChatClient`/`GroqChatClient`
  seam exactly**: a plain interface + one `RestClient`-based implementation calling
  Supabase Storage's raw REST API directly (`/storage/v1/object/...`,
  `/storage/v1/object/sign/...`) — no Supabase SDK dependency, same "boring, explicit
  code" precedent as Brevo/Groq. `SupabaseProperties` binds `SUPABASE_URL_EXPENSEWISE`/
  `SUPABASE_SERVICE_KEY_EXPENSEWISE`/`SUPABASE_BUCKET_EXPENSEWISE` via literal `@Value`
  placeholders (not `@ConfigurationProperties`), same collision-avoidance reasoning as
  `GroqProperties`/`JwtProperties`. Constructor only builds the `RestClient` — no
  network call at startup, so the Spring context boots fine with dummy values (CI) —
  confirmed the same way `GroqChatClient` was confirmed. **None of these three env
  vars have a default**, matching `GROQ_API_KEY_EXPENSEWISE`'s precedent — this means
  `mvn spring-boot:run` will not start locally until they're set (even to dummy
  values) via `setx`, the same way `BREVO_API_KEY_EXPENSEWISE`/`MAIL_FROM_EXPENSEWISE`
  already are on this machine.
- **`/api/v1/auth/logout-others` lives in `AuthController`, not `UserController`**,
  even though "log out of all other sessions" is a Profile/Security-tab feature.
  Forced by an existing, deliberate constraint: the refresh cookie is `Path=/api/v1/auth`
  only (see the Auth phase entry below), so a `/api/v1/users/me/...` endpoint would
  never receive it and couldn't identify which session to spare. `/api/v1/auth/**` is
  otherwise a fully public path (`SecurityConfig.PUBLIC_PATHS`), so the endpoint is
  gated with `@PreAuthorize("isAuthenticated()")` instead of path-level auth — method
  security still applies on top of a path-level `permitAll`. Confirmed with the
  project owner rather than widening the cookie's `Path` (which would reopen a
  security tradeoff closed for a reason) or silently building it in the wrong module.
- **`RefreshTokenRepository.revokeAllActiveForUserExcept`** identifies "the current
  session" by re-hashing the raw `refreshToken` cookie already presented on the
  request — no new schema/column needed. Revoking *all* tokens (reusing the existing
  `revokeAllForUser`) was rejected as the simple option: the design's copy ("Sign out
  everywhere **except this device**") and its confirmation text both promise the
  current device stays logged in, which `revokeAllForUser` would violate.
- **MapStruct pitfall, worth flagging for future mapper work**: a `default` method
  declared inside a `@Mapper` interface with a matching single-arg signature (here,
  `String -> String`) is silently auto-applied by MapStruct to *every* same-typed
  field mapping in that interface — not just the one `@Mapping(expression = ...)` it
  was written for. This produced every `String` field in `UserResponse` (`email`,
  `fullName`, `phone`, `gender`, `address`, ...) coming back as the literal value
  `"Failed"`, since `mapLoginStatus`'s fallback branch is `"Failed"`. Fixed by
  annotating the method `@Named(...)` and referencing it via `qualifiedByName`
  instead of `expression` — `@Named` methods are only applied where explicitly
  requested. No test caught this until a live curl/browser check (Mockito-based
  `UserMapperTest`-style unit tests mock the mapper itself, so they can't catch a
  bug in MapStruct's generated code) — this class of bug specifically needs an
  integration test that hits the real endpoint, which `ProfileIntegrationTest` does.
- **Found and fixed an unrelated, pre-existing bug while building the e2e test**:
  reloading any authenticated page (not Profile-specific) redirected to `/login`
  even with a fully valid refresh cookie. `main.ts` awaited `authStore.bootstrap()`
  before `app.mount()`, on the assumption that delays the router's first navigation
  guard too — but Vue Router 4 starts resolving its initial navigation (and running
  `beforeEach`) as soon as `app.use(router)` runs, independent of `app.mount()`
  timing, so the guard saw `isAuthenticated === false` and redirected well before
  `bootstrap()`'s network round-trip finished. No prior e2e test did a hard
  `page.reload()` on an authenticated route, so this was never exercised. Fixed by
  moving the `await authStore.bootstrap()` into the router's own `beforeEach` guard
  (a no-op after the first call, since `bootstrap()` already early-returns once
  `bootstrapped` is true) and mounting immediately in `main.ts` — also avoids a
  potential double-`bootstrap()` race (two concurrent `/auth/refresh` calls
  fighting over the same single-use rotating cookie) that the old two-call-site
  shape risked. Confirmed with the project owner before fixing, since it's outside
  this session's nominal Profile-module scope but blocks the Profile e2e test's
  reload-based persistence check and affects every authenticated page.
- **Avatar constraints: 2 MB / JPG-PNG-WEBP (task spec) override the imported
  design's copy ("JPG or PNG, up to 5MB")**. The two conflict; the explicit,
  more-recent task instructions were treated as authoritative over a mockup's
  placeholder string, and the UI's helper text was corrected to say "JPG, PNG, or
  WEBP — up to 2 MB" so it doesn't promise a limit the backend won't honor — same
  precedent as the AI Assistant phase's insights-mislabeling fix below.
- **`UpdateProfileRequest`'s `@Pattern` regexes for `phone`/`gender` explicitly also
  accept the empty string** (`^$|...`), not just `null`. The frontend sends `""` for
  an untouched optional text input or the unselected `<option value="">`, not
  `null`/omitted — `UserService.updateProfile` then converts blank to `null` before
  persisting, so the DB never stores an empty string for an "unset" optional field.
- **`avatarUrl` is computed, never stored** — `UserResponse.avatarUrl` is generated
  fresh on every read (`UserService.toResponseWithAvatar`, 15-minute signed-URL TTL)
  from the persisted `avatar_path`, per the task's explicit "regenerate each fetch
  since signed URLs expire" instruction. `UserMapper.toResponse(User)` (used by
  `AuthController` at register/login, where there's never an avatar yet) leaves
  `avatarUrl` `null` via `@Mapping(ignore = true)`; a second overload,
  `toResponse(User, String avatarUrl)`, is what `UserService` uses.
- **Login history reuses `activity_logs`** (`LOGIN_SUCCESS`/`LOGIN_FAILED` actions),
  no new table — `idx_activity_logs_user_created` already supports the scoped,
  paginated query efficiently. Added `PROFILE_UPDATED`/`AVATAR_UPDATED`/
  `AVATAR_REMOVED`/`LOGOUT_OTHERS` to `ActivityAction`, and — filling a gap the
  existing `updateProfile` didn't have — `UserService.updateProfile` now logs
  `PROFILE_UPDATED` (parity with `changePassword`'s existing `PASSWORD_CHANGED` log).
- **Only the desktop (1440px) frame of "ExpenseWise Profile" was built**, same
  precedent as every prior module.
- **Found and fixed a real bug in `SupabaseStorageService` by testing against a
  real Supabase project** (the project owner had already set up
  `SUPABASE_URL_EXPENSEWISE`/`SUPABASE_SERVICE_KEY_EXPENSEWISE`/
  `SUPABASE_BUCKET_EXPENSEWISE` in `.env`) — `@MockBean`-based tests can't catch
  this class of bug since they never make a real HTTP call. `RestClient`'s
  `.uri("/object/sign/{bucket}/{path}", bucket, path)` percent-encodes the `/`
  characters inside a single template-variable value into `%2F`. Supabase's
  Storage API tolerates this for upload/list (decodes it back into a real
  folder hierarchy — confirmed via the bucket's own list-objects API), but its
  sign endpoint embeds the raw, still-encoded path into the signed token's
  `url` claim; the later download request's URL-decoded path then no longer
  matches that claim, failing with `InvalidSignature` (400) even though
  everything upstream reported success. Fixed by building the URI via plain
  string concatenation (`"/object/" + bucket + "/" + path"`) instead of a
  template variable — safe here since `path` is always our own generated
  value (`avatars/{userId}/{uuid}.ext`), never user input. Verified end to end
  against the real bucket: upload, download via the returned signed URL
  (byte-identical content), and delete (including the automatic
  delete-of-previous-object-on-replace) all confirmed working; test objects
  were fully self-cleaned by the app's own replace/remove logic, nothing
  manual to clean up in the bucket afterward.

## 2026-08-02 — Forgot-password test coverage: added a `local`-profile-only token endpoint

- **Added `PasswordResetServiceTest`** (Mockito, no Spring context) covering
  `requestReset`/`completeReset` in isolation: no-op on unknown email, email
  normalization, prior-token invalidation + new-token save/email/log,
  rejecting a missing/used/expired token, and the happy path (password
  updated, token marked used, refresh tokens revoked). Complements the
  existing `PasswordResetIntegrationTest`, which exercises the same rules
  through real HTTP + Postgres but didn't isolate the expired-token branch.
- **New Playwright journey in `auth.spec.ts`**: request a reset, complete it
  with the emailed token, log in with the new password; plus a second test
  asserting an invalid token surfaces `reset-error-banner` instead of
  resetting anything.
- **Problem: the e2e test has no way to read the raw reset token.** Only its
  SHA-256 hash is ever persisted (`PasswordResetToken.tokenHash`); the raw
  value only ever exists in the reset link handed to `MailService`, and in
  local dev (no `BREVO_API_KEY_EXPENSEWISE`) that link is just logged to the
  backend's own stdout — which Playwright can't read, since it doesn't spawn
  the backend process (`playwright.config.ts`'s `webServer` only starts the
  Vite dev server; the backend is expected to already be running).
- **Fix: `DevPasswordResetTokenCache` + `DevResetTokenController`**, both
  under `com.expensewise.devsupport` and both `@Profile("local")` (same
  convention as `DataSourceConfig`'s split beans) — an in-memory
  `email -> lastResetLink` cache that `MailServiceImpl.sendPasswordResetEmail`
  populates via an `Optional<DevPasswordResetTokenCache>` constructor
  dependency (empty outside `local`, so this is a no-op everywhere else), and
  a `GET /api/v1/dev/password-reset-token?email=` endpoint that reads it back.
  Registered in `SecurityConfig.PUBLIC_PATHS` as `/api/v1/dev/**` — harmless
  outside `local` since no controller is ever mapped there, so the path just
  404s. This endpoint does not exist in a prod build; it exists purely so an
  automated test can do what a human tester would do by reading the console.

## 2026-08-02 — Brevo email switched from SMTP to HTTP API

- **`MailServiceImpl` now calls Brevo's transactional email HTTP API
  (`POST https://api.brevo.com/v3/smtp/email`) via Spring's `RestClient`,
  not `JavaMailSender` over SMTP.** Many low-cost hosting platforms block
  outbound SMTP ports (25/465/587) on free/cheap tiers to fight spam, needing
  a paid plan to unblock — a real risk for this project's eventual prod
  deploy. Outbound HTTPS (443) is essentially always allowed, so the HTTP
  API avoids that failure mode entirely. Confirmed with the project owner
  rather than discovering it live in production.
- **Dropped `spring-boot-starter-mail`** — nothing else in the app used
  `JavaMailSender`. `RestClient` ships with `spring-boot-starter-web`
  (already a dependency), so no new dependency was added; used a plain
  `RestClient` call with a `Map`-built JSON body rather than the official
  Brevo Java SDK, keeping with "boring, explicit code" — one small class,
  no generated OpenAPI client to explain in a demo.
- **`BREVO_SMTP_HOST/PORT/USER/PASSWORD_EXPENSEWISE` collapsed into a single
  `BREVO_API_KEY_EXPENSEWISE`.** The blank-check that gates "log the reset
  link instead of sending" (local dev with no real credentials) now checks
  this one var instead of `smtpHost`. `spring.mail.*` blocks removed from
  `application-local.yml`/`application-prod.yml` since nothing binds them
  anymore — also removes the framework-owned `spring.mail.*` env-var
  collision risk flagged as a deferred next step in the entry below.
- **CLAUDE.md's Email stack row and Environment var list updated to match.**
  Thymeleaf stays — it still renders `password-reset-email.html`'s HTML,
  which is now sent as `htmlContent` in the API request body instead of via
  `MimeMessageHelper`.

## 2026-07-28 — AI Assistant module phase

- **Called Groq's REST API directly via Spring's RestClient — no Spring AI
  dependency**, reversing CLAUDE.md's original "Groq via Spring AI"
  stack-table wording. Checked compatibility before adding anything: Spring
  AI's current lines require Spring Boot 3.4.x/3.5.x (1.0.x) or 4.1.x
  (2.0.0); this project is pinned to Spring Boot 3.3.4, which neither
  supports. Bumping the whole app's Spring Boot version for one module was
  rejected as too large a blast radius. Confirmed with the project owner.
  `AiChatClient` is the seam (implemented by `GroqChatClient`); tests
  `@MockBean` this interface, standing in for "the Spring AI
  ChatModel/ChatClient" the task's testing instructions refer to.
- **The "Spending Analysis" insights panel is rule-based, not AI-generated**
  — `AiContextService.buildInsights` derives cards straight from
  `BudgetService`'s existing exceeded/approaching-limit computations (same
  80% "approaching" threshold BudgetsView.vue uses) plus a net-savings
  check, with no model call. The imported design labels these cards "AI
  insight" and closes with "Generated by AI — verify before acting"; since
  the content isn't actually model-generated, the label was changed to
  "Insight" and the footer to "Based on your budgets and transactions this
  month" rather than falsely attributing deterministic output to AI.
  Confirmed with the project owner rather than either building a second
  AI-generated-JSON endpoint or silently mislabeling the deterministic one.
- **`AiContextService.buildSnapshot` reuses `BudgetService.getMonthBudgets`
  for the KL "this month" boundary** rather than re-deriving
  Asia/Kuala_Lumpur month math itself — it calls it with `periodMonth =
  null` and reads back the resolved `periodMonth`, then derives the
  matching `to` date from it. This is the second caller of that boundary
  (after the Budgets screen itself), and reusing the already-resolved value
  keeps the KL-anchoring logic in exactly one place.
- **Top spending categories are aggregated in Java from
  `TransactionService.listTransactions(..., "EXPENSE", ...)`**, one page of
  200 rows, rather than a new repository aggregate query — same "small
  dataset for a solo-user demo app" reasoning `TransactionService.getSummary`
  and `BudgetService` already rely on, and it avoids a second query path to
  keep in sync with category-visibility rules.
- **Conversation delete has no affordance in the imported design** (no
  trash icon anywhere in the conversation rail), same gap the Budgets
  design had for deleting a budget. Applied the same precedent already
  confirmed for that module: a small delete icon next to each conversation
  row (visible on hover), rather than re-asking an already-answered
  question or leaving the required DELETE endpoint unreachable from the UI.
- **The frontend shows the user's own message optimistically, then removes
  it on failure.** Posting a message is a single `@Transactional` unit
  (append user message, build context, call the model, append the reply) —
  if the Groq call throws, the whole exchange rolls back server-side, so an
  optimistically-rendered user bubble that was never actually persisted
  would be misleading if left on screen after an error. The catch handler
  strips it and restores the input text instead.
- **Insight/context body text embeds "RM 123.45" as plain prose, not
  through `<MoneyDisplay>`.** These strings are backend-composed natural-
  language sentences (like assistant chat replies), not a raw numeric field
  the frontend format-controls — the same reasoning that exempts chat
  message content from `<MoneyDisplay>` applies here.
- **Only the desktop (1440px) frame of "ExpenseWise Ai Assistant" was
  built**, same precedent as every prior module (Category, Transaction,
  Budget) deferring the mobile/tablet shell (tab bar, slide-over history) to
  its own session.
- **The E2E test is allowed to hit the real Groq endpoint** (a real
  `GROQ_API_KEY_EXPENSEWISE` is set on this dev machine) rather than forcing
  a mock at the E2E layer — CLAUDE.md's "do not assert on AI response
  content" rule is satisfied by asserting only that a reply renders, never
  its text. Unit/integration tests still mock `AiChatClient` unconditionally
  so CI is deterministic and needs no real key.
- **CLAUDE.md's Testing section currently reads self-contradictory**
  ("all e2e test cases covering ... all possible cases covering that
  module's single critical user journey ... One focused journey per
  module ... not sixty") — looks like a partially-applied edit made outside
  a session. This module's own task instructions explicitly said "Exactly
  ONE Playwright E2E ... one journey only," matching the pre-edit intent, so
  that's what was followed here. Left the file as-is since fixing it wasn't
  asked for; flagged to the project owner.

## 2026-07-28 — Env var rename (`_EXPENSEWISE` suffix)

- **Every app-specific env var name now carries an `_EXPENSEWISE` suffix**
  (`DB_URL_EXPENSEWISE`, `JWT_SECRET_EXPENSEWISE`, `GROQ_API_KEY_EXPENSEWISE`,
  etc. — full list in CLAUDE.md's Environment section), so they can't
  collide with another project's identically-named env vars set globally on
  the same shared dev machine (e.g. a second project also using a plain
  `GROQ_API_KEY`). Confirmed as a deliberate project-wide rename with the
  project owner, not just a personal machine convention — `Jenkinsfile`'s
  environment block, `backend/.env.example`, and CLAUDE.md were all updated
  to match, not just the Spring config.
- **Fixed a bug from the in-progress manual edit this rename was based on**:
  `application-local.yml` had picked up a `groq:` block nested *inside*
  `spring:` (wrong indentation), which would have silently bound to
  `spring.groq.*` instead of `groq.*` — never actually reaching
  `GroqProperties` (`@ConfigurationProperties(prefix = "groq")`). It was
  also a duplicate of the correct top-level block already in
  `application.yml`. Removed the nested duplicate; the one in
  `application.yml` is the only one needed (Groq config doesn't vary
  per-profile).
- **`DB_USER_EXPENSEWISE`/`DB_PASSWORD_EXPENSEWISE` keep their `dev`/`devpass`
  defaults in `application-local.yml`** (the in-progress manual edit had
  dropped them, making local dev require env vars that weren't previously
  needed). Restored the defaults under the new names so CLAUDE.md's
  "zero-config" local dev story (`docker compose up -d && mvn
  spring-boot:run`) still holds — only `JWT_SECRET_EXPENSEWISE` and
  `GROQ_API_KEY_EXPENSEWISE`/`GROQ_MODEL_EXPENSEWISE` remain required with no
  default, matching their pre-existing "must be set, no sane default"
  status (a signing secret and an API key can't have a working default).

## 2026-08-02 — Fixed `GroqProperties` binding a colliding env var

- **`GroqProperties.apiKey`/`model` no longer use `@ConfigurationProperties`
  relaxed binding — switched to explicit `@Value("${GROQ_API_KEY_EXPENSEWISE}")`
  / `@Value("${GROQ_MODEL_EXPENSEWISE}")`.** Root cause of a real bug found
  during manual AI-assistant testing: Spring Boot's config binder resolves
  `@ConfigurationProperties(prefix = "groq")`'s `apiKey` field (canonical
  property `groq.api-key`) by independently checking every property source
  for a *derived* env var name (`GROQ_API_KEY`) — ranked above
  `application.yml` — regardless of what `application.yml` itself says. On
  this dev machine a real, unrelated `GROQ_API_KEY` is set (for a different
  local project), so it silently won over `application.yml`'s own
  `${GROQ_API_KEY_EXPENSEWISE}` placeholder every time, with no error and no
  indication anything was wrong — confirmed by direct log output showing
  `GroqProperties.apiKey()` resolving to the wrong key while
  `System.getenv("GROQ_API_KEY_EXPENSEWISE")` was correct in the same JVM.
  `@Value` placeholder resolution only matches the literal name given, with
  no such derived-name fallback, so it isn't subject to this collision.
  `application.yml`'s `groq:` block now only has `base-url` (a plain, unique
  property with no realistic collision risk); `api-key`/`model` are bound
  directly from the env vars.
- **`JwtProperties` (`jwt.secret` → derived `JWT_SECRET`) has the identical
  latent risk** — not fixed here since no colliding `JWT_SECRET` exists on
  this machine today and it wasn't the reported bug, but flagged to the
  project owner as the same class of issue, worth the same `@Value` fix if
  it ever bites.

## 2026-08-02 — Same env-var collision, three more places

- **The Groq fix above turned out to be incomplete: the collision isn't
  specific to `@ConfigurationProperties`.** Verified directly with an
  isolated `StandardEnvironment` probe — a plain
  `Environment.getProperty("mail.from")` (what a bare `@Value("${mail.from}")`
  uses) also resolved to a fake unsuffixed `MAIL_FROM` env var over a
  higher-precedence `application.yml` value. Spring's
  `SystemEnvironmentPropertySource` does the dotted-property → derived-env-var
  matching for *any* property resolution, not just the Boot config-properties
  binder. The only thing that made the Groq fix actually safe was passing the
  already env-shaped literal name (`GROQ_API_KEY_EXPENSEWISE`) straight into
  `@Value`, not "using `@Value`" per se — any `@Value("${dotted.property}")`
  is equally exposed.
- **Turned out `JWT_SECRET` (unsuffixed) really was set on this dev machine**
  — the "latent, not urgent" risk noted above was actually live: ExpenseWise
  was signing/verifying JWTs with a different project's secret, silently, no
  error. Fixed the same way as Groq: `JwtProperties.secret` /
  `accessTokenExpirationSeconds` / `refreshTokenExpirationSeconds` now bind
  via `@Value` directly to the literal `JWT_SECRET_EXPENSEWISE`/
  `JWT_EXPIRATION_EXPENSEWISE`/`REFRESH_TOKEN_EXPIRATION_EXPENSEWISE` names.
- **`MailServiceImpl` (`mail.from`, `brevo.smtp-host`) and
  `PasswordResetService` (`app.frontend-url`)** had the same dotted-property
  exposure (`MAIL_FROM`, `BREVO_SMTP_HOST`, `APP_FRONTEND_URL` as plausible
  unsuffixed collisions) — switched to `@Value` with the literal
  `MAIL_FROM_EXPENSEWISE`/`BREVO_SMTP_HOST_EXPENSEWISE`/
  `FRONTEND_URL_EXPENSEWISE` names, same pattern.
- **`spring.datasource.*` and `spring.mail.*` are the highest-risk instance
  of this, and framework-owned** — Spring Boot's own `DataSourceProperties`/
  `MailProperties` bind those prefixes via `@ConfigurationProperties`
  internally; we can't add `@Value` inside framework classes. Worse, these
  are Spring Boot's *own documented* override convention
  (`SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`,
  `SPRING_MAIL_HOST`/`_PORT`/`_USERNAME`/`_PASSWORD`), so any other Spring
  Boot project on a shared dev machine that just follows Spring's docs would
  plausibly set exactly these — a likelier collision than the arbitrary
  `GROQ_API_KEY`/`JWT_SECRET` cases were. Fixed by defining the `DataSource`
  bean explicitly in `DataSourceConfig` (`@Value`-bound to the literal
  `DB_URL_EXPENSEWISE`/`DB_USER_EXPENSEWISE`/`DB_PASSWORD_EXPENSEWISE` names,
  split into `@Profile("local")`/`@Profile("!local")` variants to keep each
  profile's own Hikari pool sizing and local-only defaults), which makes
  Spring's `DataSourceAutoConfiguration` back off entirely
  (`@ConditionalOnMissingBean`) — `spring.datasource.*` no longer exists
  anywhere in this app's config, so there's nothing left for the collision to
  attach to. `spring.mail.*` has the identical exposure and is the deferred
  next step (`MailConfig` bean bypassing `MailSenderAutoConfiguration` the
  same way) — not done yet, picking up next session.
- Removed `@ConfigurationPropertiesScan` from `ExpenseWiseApplication` — with
  `GroqProperties` and `JwtProperties` both converted away from
  `@ConfigurationProperties`, nothing in the app uses it anymore.
- All 122 backend tests pass against the real local Docker Postgres through
  the new `DataSourceConfig` bean (integration tests don't set
  `SPRING_PROFILES_ACTIVE`, so they run under `local` same as `mvn
  spring-boot:run` does), confirming the bean-based DataSource behaves
  identically to the autoconfigured one it replaced.

## 2026-07-28 — Budget module phase

- **Correction: category budgets are now capped by the overall budget,
  reversing this phase's original "Option A (independent)" design.** The
  spec initially confirmed overall and per-category budgets as independent
  limits with no sum enforcement; the project owner later asked for the
  opposite — category budgets (Food + Transport + …) must never sum to more
  than the overall monthly budget. Implemented as a two-way cap in
  `BudgetService.requireWithinOverallCap`:
  - A category budget cannot be created/edited unless an overall budget
    already exists for that month (`OverallBudgetRequiredException`), and
    its amount plus every other category budget's amount must not exceed
    the overall amount (`BudgetExceedsOverallException`).
  - The overall budget cannot be created/edited to an amount below the sum
    of category budgets already set for that month (same exception).
  - Deleting the overall budget is rejected while any category budget still
    exists for that month (`OverallBudgetInUseException`, 409 — mirrors
    `CategoryInUseException`'s convention), since deleting it first would
    leave category budgets with no overall limit to be capped against.
  The frontend mirrors this proactively rather than just surfacing the
  resulting 400/409: a category's "Set budget" link is disabled until an
  overall budget exists, and the overall budget's "Clear" link is disabled
  while any category budget exists — both with a `title` tooltip explaining
  why, so the user isn't left to guess from a failed save.
- **Delete is exposed as a "Clear" link, not a design element.** The imported
  "ExpenseWise Budgets" design only shows Edit/Set-budget affordances (an
  amount-only modal) — no delete anywhere. Confirmed with the project owner
  to add a small "Clear" text action next to Edit (and next to the overall
  budget's Edit) calling `DELETE /api/v1/budgets/{id}`, since the module
  requires delete end-to-end (API, tests, and the one E2E journey) but the
  design doesn't cover it.
- **The month view (`GET /api/v1/budgets?month=`) enumerates every EXPENSE
  category visible to the caller**, not just ones with a budget row — a
  `CategoryBudgetLine` is returned even when `budgetId`/`amount` are null,
  with `spent` still computed. This matches the design's "No budget set ·
  RM X spent" row and lets the screen offer "Set budget" inline, at the
  cost of the response no longer being a 1:1 mirror of the `budgets` table.
- **Progress/remaining/exceeded are computed live from `transactions`, never
  stored on `budgets`** (per CLAUDE.md's core design) — reusing
  `TransactionSpecifications` (ownedBy/hasType/hasCategory/dateFrom/dateTo)
  and summing in Java with `BigDecimal`, the same pattern
  `TransactionService.getSummary` already established, rather than a new
  aggregate-query path. `progressPercent` divides with explicit
  `RoundingMode.HALF_UP` at scale 0 (whole-percent, matching the design).
  A budget with no amount set returns `null` for remaining/progressPercent
  (never 0) and is never `exceeded` — an unset limit can't be "over".
- **Uniqueness (one overall + one row per category per user per month) is
  pre-checked in Java against `findByUserIdAndPeriodMonth`**, not a derived
  `existsBy...` repository method, to sidestep the null-parameter-equality
  subtlety of Spring Data derived queries and give a clean 400 instead of a
  raw constraint violation. The DB's two partial unique indexes (see the
  Foundation phase entry) remain the actual source of truth.
- **A `Clock` bean (`ClockConfig`, `Clock.systemUTC()`) was introduced** so
  BudgetService's Asia/Kuala_Lumpur "this month" default is swappable for a
  fixed instant in tests (`BudgetServiceTest`'s KL-boundary test fixes a
  clock at 23:00 UTC on the last day of the month, which is already the
  next day in KL, and asserts the resolved month reflects that) — this is
  the project's first genuine "this month" default (Transaction's summary
  endpoint deliberately has none; see the Transaction phase entry), so it's
  the first place this rule needed real code instead of just a written rule.
- **PATCH cannot turn a category budget into the overall budget or vice
  versa via a null `categoryId`.** `PatchBudgetRequest.categoryId() == null`
  means "unchanged", the same optional-field convention as
  `PatchTransactionRequest`/`PatchCategoryRequest` — there's no separate
  "clear this field" signal. Changing what a budget is scoped to isn't a
  supported edit; delete and recreate instead.
- **Only the desktop (1440px) frame of "ExpenseWise Budgets" was built**,
  consistent with the Category and Transaction phases' precedent of
  deferring the separate mobile/tablet shell (bottom tab bar, FAB, no
  sidebar) to its own dedicated pass.

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
