-- ============================================================
-- V5__drop_notifications.sql — remove the Notifications feature
-- ============================================================
-- Notifications is being replaced by a News feature (separate, future
-- work). No code writes to or reads from this table anymore — see
-- DECISIONS.md.

DROP TABLE IF EXISTS notifications;
