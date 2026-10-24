-- Initial schema for Open Idea Studio. See docs/data-model.md.
--
-- Current state lives in normalized rows (project, artifact, relationship).
-- Every committed change also writes an immutable project_revision snapshot,
-- so history can be inspected and restored without event sourcing.

-- Also created by infra/postgres/init; repeated so Flyway works against any
-- PostgreSQL 17 (e.g. Testcontainers). gen_random_uuid() is built in since PG 13.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE project (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                varchar(200) NOT NULL,
    description         text NOT NULL DEFAULT '',
    current_revision_id uuid,
    schema_version      integer NOT NULL DEFAULT 1,
    -- Optimistic concurrency token; incremented on every committed change.
    version             bigint NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX project_updated_at_idx ON project (updated_at DESC);

CREATE TABLE project_revision (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id         uuid NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    parent_revision_id uuid REFERENCES project_revision (id),
    -- Monotonic per project; easier to read than UUIDs in the UI ("rev 12").
    number             integer NOT NULL,
    summary            text NOT NULL,
    -- 'user' for direct edits, 'ai-proposal' for applied change sets, 'restore', 'system'.
    created_by         varchar(32) NOT NULL,
    affected_artifacts jsonb NOT NULL DEFAULT '[]'::jsonb,
    -- Full, immutable copy of project + artifacts + relationships at this revision.
    snapshot           jsonb NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (project_id, number)
);

ALTER TABLE project
    ADD CONSTRAINT project_current_revision_fk
    FOREIGN KEY (current_revision_id) REFERENCES project_revision (id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE conversation_message (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    role       varchar(16) NOT NULL CHECK (role IN ('user', 'assistant', 'system', 'tool')),
    content    text NOT NULL,
    metadata   jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX conversation_message_project_idx ON conversation_message (project_id, created_at);

CREATE TABLE artifact (
    -- Surrogate key for the row.
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     uuid NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    -- Stable, human-readable ID (REQ-BOOKING, FLOW-BOOKING) that survives edits,
    -- reordering, and AI regeneration. Traceability uses this key.
    key            varchar(100) NOT NULL,
    type           varchar(32) NOT NULL CHECK (type IN (
                       'requirement', 'decision', 'assumption', 'question',
                       'risk', 'suggestion', 'flow', 'screen', 'summary')),
    title          varchar(300) NOT NULL,
    status         varchar(32) NOT NULL,
    data           jsonb NOT NULL DEFAULT '{}'::jsonb,
    schema_version integer NOT NULL DEFAULT 1,
    version        bigint NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (project_id, key)
);

CREATE INDEX artifact_project_type_idx ON artifact (project_id, type);
CREATE INDEX artifact_title_trgm_idx ON artifact USING gin (title gin_trgm_ops);

CREATE TABLE artifact_relationship (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        uuid NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    source_artifact_id uuid NOT NULL REFERENCES artifact (id) ON DELETE CASCADE,
    target_artifact_id uuid NOT NULL REFERENCES artifact (id) ON DELETE CASCADE,
    relationship_type varchar(32) NOT NULL CHECK (relationship_type IN (
                          'derived-from', 'implements', 'affects', 'references')),
    created_at        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (source_artifact_id, target_artifact_id, relationship_type),
    CHECK (source_artifact_id <> target_artifact_id)
);

CREATE INDEX artifact_relationship_target_idx ON artifact_relationship (target_artifact_id);

CREATE TABLE proposed_change_set (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        uuid NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    -- A proposal is only applicable to the revision it was computed against.
    base_revision_id  uuid NOT NULL REFERENCES project_revision (id),
    summary           text NOT NULL,
    operations        jsonb NOT NULL,
    warnings          jsonb NOT NULL DEFAULT '[]'::jsonb,
    validation_status varchar(16) NOT NULL CHECK (validation_status IN ('valid', 'invalid')),
    validation_errors jsonb NOT NULL DEFAULT '[]'::jsonb,
    state             varchar(16) NOT NULL DEFAULT 'pending'
                          CHECK (state IN ('pending', 'applied', 'rejected', 'stale')),
    applied_revision_id uuid REFERENCES project_revision (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    applied_at        timestamptz
);

CREATE INDEX proposed_change_set_project_idx ON proposed_change_set (project_id, created_at DESC);
