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
