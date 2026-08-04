-- ============================================================
-- V6__news_entitlement.sql — add the NEWS feature entitlement
-- ============================================================
-- Never edit an applied migration (V4's CHECK constraint) — replace it
-- with an equivalent one that also allows 'NEWS'.

ALTER TABLE user_feature_entitlements DROP CONSTRAINT chk_user_feature_entitlements_feature;
ALTER TABLE user_feature_entitlements ADD CONSTRAINT chk_user_feature_entitlements_feature
    CHECK (feature IN ('TRANSACTIONS', 'CATEGORIES', 'BUDGETS', 'REPORTS', 'AI_ASSISTANT', 'NEWS'));

-- Backfill NEWS = enabled for every existing USER account so nobody who
-- already has an account is locked out of the new feature by default.
INSERT INTO user_feature_entitlements (user_id, feature, enabled)
SELECT u.id, 'NEWS', true
FROM users u
WHERE u.role = 'USER'
  AND NOT EXISTS (
      SELECT 1 FROM user_feature_entitlements e
      WHERE e.user_id = u.id AND e.feature = 'NEWS'
  );
