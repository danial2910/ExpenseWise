-- ============================================================
-- V3__add_profile_fields.sql — profile fields on users
-- ============================================================

ALTER TABLE users
    ADD COLUMN phone         VARCHAR(30),
    ADD COLUMN date_of_birth DATE,
    ADD COLUMN gender        VARCHAR(20),
    ADD COLUMN address       VARCHAR(500),
    ADD COLUMN avatar_path   VARCHAR(500);

ALTER TABLE users
    ADD CONSTRAINT chk_users_gender
        CHECK (gender IS NULL OR gender IN ('FEMALE', 'MALE', 'NON_BINARY', 'SELF_DESCRIBED', 'NOT_SPECIFIED'));
