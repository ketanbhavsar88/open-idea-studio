-- Runs once, on first initialisation of an empty data volume.
-- Tables are created by Flyway migrations, not here.
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- text search across artifacts (PR-005)
