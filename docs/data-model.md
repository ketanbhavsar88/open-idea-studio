# Data model

Schema source of truth: [`apps/api/src/main/resources/db/migration`](../apps/api/src/main/resources/db/migration). Never edit an applied migration; add a new `V{n}__*.sql`.

## Tables

| Table | Purpose |
| --- | --- |
| `project` | Name, idea description, `current_revision_id`, `version` (optimistic lock), `schema_version` |
| `project_revision` | Immutable snapshot per committed change; `number` is gap-free per project; `parent_revision_id` forms the history chain |
| `conversation_message` | Discussion turns (`user`, `assistant`, `system`, `tool`) with JSONB `metadata`. User-facing content only, never hidden model reasoning |
| `artifact` | Requirements, decisions, assumptions, questions, risks, suggestions, flows, screens, summaries. Typed payload in JSONB `data` |
| `artifact_relationship` | Traceability edges: `derived-from`, `implements`, `affects`, `references` |
| `proposed_change_set` | AI or user proposals tied to `base_revision_id`; `state` is `pending`, `applied`, `rejected` or `stale` |

`artifact_relationship` holds `implements` links from flows to requirements (kept in sync on every flow save). `proposed_change_set` holds AI flow-edit proposals (V2 adds `kind`, `target_artifact_key`, `target_artifact_version`, `instruction`).

## Stable IDs

`artifact.key` (e.g. `REQ-BOOKING-APPROVAL`, `FLOW-BOOKING`) is unique per project and must survive edits, reordering, and AI regeneration. Relationships, flow nodes (`requirementIds`), snapshots, and change-set operations all refer to keys, never to regenerated IDs. The UUID `id` is a row identifier only.

## Revision snapshot (`snapshotSchemaVersion: 1`)

```json
{
  "snapshotSchemaVersion": 1,
  "project": { "name": "…", "description": "…", "schemaVersion": 1 },
  "artifacts": [
    { "key": "REQ-BOOKING", "type": "requirement", "title": "…", "status": "confirmed", "data": {}, "schemaVersion": 1 }
  ],
  "relationships": [
    { "source": "FLOW-BOOKING", "target": "REQ-BOOKING", "type": "implements" }
  ]
}
```

Restoring (Phase 4) will rebuild current rows from a snapshot and record that as a **new** revision, so later history is never erased.

## Artifact statuses (to be validated in code)

Defined in `ArtifactType.java` (mirrored in `apps/web/src/api/types.ts`); the first status is the initial one.

- requirement: `proposed`, `confirmed`, `needs-clarification`, `deferred`, `rejected`
- decision: `proposed`, `confirmed`, `reversed`, `rejected`
- assumption: `unconfirmed`, `confirmed`, `rejected`
- question: `open`, `answered`, `skipped`
- risk: `open`, `mitigated`, `accepted`, `dismissed`
- summary: `ai-generated`, `edited` (single artifact, key `SUMMARY`)
- flow: `draft`, `approved`, `archived`
- prototype: `draft`, `approved` (single artifact, key `PROTOTYPE`)

`data` (`ArtifactData.java`) holds `description`, `category`, `priority`, `acceptanceCriteria`, `rationale`, `impact`, `answer`, `why`, `options`, `targetUsers`, `problem`, `origin` (`user` or `ai`), `graph` (flows only), and `prototype` (the prototype only).

## Flow graph (`data.graph`, `schemaVersion: 1`)

```json
{
  "nodes": [
    {
      "id": "node-owner-approves",
      "type": "decision",
      "label": "Owner approves?",
      "description": "The owner decides whether to accept the request.",
      "position": { "x": 360, "y": 220 },
      "requirementKeys": ["REQ-OWNERS-APPROVE-BOOKINGS"]
    }
  ],
  "edges": [
    { "id": "e-request-booking-owner-approves", "source": "node-request-booking", "target": "node-owner-approves", "label": null }
  ],
  "schemaVersion": 1
}
```

- `type`: `start`, `end`, `action`, `decision`, `subprocess`.
- IDs match `[a-z0-9][a-z0-9-]{0,79}`, are unique within the flow, and never change once created.
- **Errors (block saving):** duplicate IDs, edges to missing nodes, self-loops, links to unknown requirements, >150 nodes or >300 edges.
- **Warnings (shown, saving allowed):** no start or end node, decisions with fewer than two outcomes, unconnected steps, duplicate connections.

## Flow change set operations

Stored in `proposed_change_set.operations`, already resolved (IDs and positions filled in by the backend):

| `op` | Fields |
| --- | --- |
| `addNode` | `nodeId`, `nodeType`, `label`, `description`, `requirementKeys`, `position` (`ref` kept for display) |
| `updateNode` | `nodeId` + any of `label`, `description`, `nodeType`, `requirementKeys` |
| `removeNode` | `nodeId` (its edges are removed too) |
| `addEdge` | `edgeId`, `from`, `to`, `edgeLabel` |
| `removeEdge` | `edgeId` |

A change set applies only if the flow's `version` still equals `target_artifact_version`; otherwise it becomes `stale`.

## Prototype spec (`data.prototype`, `schemaVersion: 1`)

The prototype is plain data rendered by trusted components (`apps/web/src/prototype/renderer.tsx`). It cannot contain HTML, URLs, images, or code.

```json
{
  "title": "Vehicle sharing",
  "startScreen": "browse-vehicles",
  "initialState": { "bookingStatus": "none" },
  "screens": [
    {
      "id": "browse-vehicles",
      "title": "Browse vehicles",
      "description": null,
      "requirementKeys": ["REQ-RENTERS-REQUEST-BOOKINGS"],
      "components": [
        { "type": "list", "items": [
          { "title": "Compact hatchback", "subtitle": "2 km away", "meta": "$8/h",
            "action": { "type": "navigate", "target": "request-booking" } } ] }
      ]
    }
  ],
  "schemaVersion": 1
}
```

**Components:** `header`, `text`, `alert`, `badge`, `button`, `card` (children), `list`, `table`, `form` (fields: text, number, date, email, select, textarea, checkbox), `tabs` (children), `dialog`, `image` (grey placeholder, `alt` only), `conditional` (children shown while `state[key] == equals`). Text may contain `{state.key}`.

**Actions** (all simulated, in the browser only):
- `navigate` with `target`.
- `message` with `message`, shown as "Simulated: …".
- `setState` with `key`, `value` and an optional `target`.

**Errors (block saving):** unknown component or action types, unknown fields, duplicate screen IDs, a missing start screen, navigation to unknown screens, links to unknown requirements, nesting deeper than 4, or more than 80 components per screen. Limits: 12 screens and 30 top-level components per screen.

**Warnings:** screens that no other screen navigates to.

**Stable screen IDs:** when the AI regenerates the prototype, a screen with an existing ID or title keeps its ID, and navigation is rewritten to match. Saving keeps `implements` links from `PROTOTYPE` to every requirement a screen lists.
