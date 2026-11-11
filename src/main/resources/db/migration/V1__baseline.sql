-- Stage 1 baseline: establishes Flyway history against PostgreSQL.
-- Article tables are intentionally deferred to later stages.

CREATE TABLE IF NOT EXISTS app_meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO app_meta (key, value)
VALUES ('schema_stage', '1')
ON CONFLICT (key) DO NOTHING;
