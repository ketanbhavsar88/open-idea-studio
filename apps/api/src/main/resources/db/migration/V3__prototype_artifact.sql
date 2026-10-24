-- Phase 3: the prototype is one artifact holding a validated UI specification.
ALTER TABLE artifact DROP CONSTRAINT artifact_type_check;
ALTER TABLE artifact ADD CONSTRAINT artifact_type_check CHECK (type IN (
    'requirement', 'decision', 'assumption', 'question', 'risk', 'suggestion',
    'flow', 'screen', 'summary', 'prototype'));
