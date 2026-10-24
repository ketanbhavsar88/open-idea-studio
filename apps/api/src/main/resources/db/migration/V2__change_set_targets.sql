-- Phase 2: change sets target one artifact (e.g. a flow) at a known version, so a
-- proposal computed against an older graph is detected as stale before applying.
ALTER TABLE proposed_change_set
    ADD COLUMN kind                    varchar(32) NOT NULL DEFAULT 'flow-edit',
    ADD COLUMN target_artifact_key     varchar(100),
    ADD COLUMN target_artifact_version bigint,
    ADD COLUMN instruction             text;
