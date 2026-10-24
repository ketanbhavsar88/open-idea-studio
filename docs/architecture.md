# Architecture

See [product-design.md](product-design.md) §9 for the target design. This page describes what exists **now**.

## Overview

```text
Browser ──► Vite dev server (apps/web) ──/api──► Spring Boot API (apps/api) ──► PostgreSQL 17 (infra/)
                                                        │
                                                        └──► Gemini API / Ollama / Fake  (ModelProvider)
```

- **apps/web**: React 19, TypeScript, Vite, MUI, TanStack Query, React Router. Types in `src/api/types.ts` mirror the API DTOs and must be kept in sync by hand.
- **apps/api**: Spring Boot 4.1 on Java 25. Spring MVC, Bean Validation, `JdbcClient` with plain SQL, Flyway. No ORM: the schema mixes relational columns and JSONB, and explicit SQL keeps locking and snapshots obvious.
- **infra/**: Docker Compose for PostgreSQL (and optional Ollama).

## Backend packages (`org.openideastudio`)

| Package | Responsibility |
| --- | --- |
| `project` | Project CRUD, dashboard summaries, project lock + revision commit helpers |
| `artifact` | Requirements, decisions, assumptions, questions, risks, idea summary; stable key generation; status rules |
| `conversation` | Discovery orchestrator: context building, the `discovery-turn` operation, applying validated output |
| `flow` | Flow graph schema, validator, pure operation engine, layout; flow service (save, generate, propose, apply) |
| `prototype` | Prototype spec (sealed component types), validator, AI drafts with stable screen IDs |
| `changeset` | Pending/applied/rejected/stale proposals tied to an artifact version |
| `ai` | `ModelProvider` + Gemini/Ollama/Fake, `ModelGateway` (throttle, JSON extraction, validation, retries) |
| `revision` | Snapshot-based revisions (`RevisionService.record`), revision read API |
| `common` | Error types and RFC 9457 problem responses |

## Key rules already enforced

- **Every committed change creates a revision.** Writers lock the project row (`SELECT … FOR UPDATE`), apply the change, then call `RevisionService.record` in the same transaction (`Propagation.MANDATORY`), so state and snapshot commit together.
- **Optimistic concurrency.** `project.version` increments on every change. Updates must send the version they were based on; a mismatch returns `409` with `code: "stale-version"`.
- **Errors** are `application/problem+json` with a stable `code` (`not-found`, `stale-version`, `validation-failed`) and, for validation, a `fields` map. Messages never echo project content.

## API (implemented)

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/api/v1/projects` | Creates project + revision 1 |
| `GET` | `/api/v1/projects` | Summaries with transparent counts |
| `GET` | `/api/v1/projects/{id}` | |
| `PATCH` | `/api/v1/projects/{id}` | Partial update; requires `version` |
| `DELETE` | `/api/v1/projects/{id}` | Cascades to all project data |
| `GET` | `/api/v1/projects/{id}/revisions` | Newest first, no snapshot |
| `GET` | `/api/v1/projects/{id}/revisions/{revisionId}` | Includes snapshot |
| `GET` | `/api/v1/projects/{id}/artifacts[?type=]` | |
| `GET` | `/api/v1/projects/{id}/artifacts/{key}` | |
| `POST` | `/api/v1/projects/{id}/artifacts` | Manual create (works without AI) |
| `PATCH` | `/api/v1/projects/{id}/artifacts/{key}` | Edit / confirm / reject; requires `version` |
| `GET` | `/api/v1/projects/{id}/messages` | |
| `POST` | `/api/v1/projects/{id}/messages` | Saves the user message, then runs one discovery turn |
| `POST` | `/api/v1/projects/{id}/messages/start` | First turn from the project idea |
| `POST` | `/api/v1/projects/{id}/messages/retry` | Re-runs the turn for an unanswered user message |
| `GET` | `/api/v1/ai/status` | Provider, model, configured flag |
| `GET` | `/api/v1/projects/{id}/flows` | |
| `GET` | `/api/v1/projects/{id}/flows/{key}` | |
| `POST` | `/api/v1/projects/{id}/flows` | Blank flow (start + end) |
| `PUT` | `/api/v1/projects/{id}/flows/{key}` | Save the canvas graph; requires `version`; returns warnings |
| `POST` | `/api/v1/projects/{id}/flows/generate` | AI flow from confirmed requirements → new draft |
| `POST` | `/api/v1/projects/{id}/flows/{key}/proposals` | AI change request → pending change set + preview |
| `GET` | `/api/v1/projects/{id}/flows/{key}/proposals` | Pending proposals for a flow |
| `GET` | `/api/v1/projects/{id}/changesets/{csId}` | Change set + preview graph |
| `POST` | `/api/v1/projects/{id}/changesets/{csId}/apply` | 409 `stale-version` if the flow changed since |
| `POST` | `/api/v1/projects/{id}/changesets/{csId}/reject` | |
| `GET` | `/api/v1/projects/{id}/prototype` | `{ "prototype": artifact or null }` |
| `PUT` | `/api/v1/projects/{id}/prototype` | Create (`version: null`) or replace; `fromAi` labels the revision |
| `POST` | `/api/v1/projects/{id}/prototype/generate` | AI draft of the whole prototype; **not saved** |
| `POST` | `/api/v1/projects/{id}/prototype/screens/{screenId}/propose` | AI draft with one screen redesigned; **not saved** |
| `GET` | `/actuator/health` | Liveness/readiness groups enabled |

## Discovery turn

1. The user message is committed first, so it survives model failures.
2. The model is called **outside** any transaction, with compact context (project, summary, open/answered questions, artifact titles, last 12 messages).
3. Output is parsed and validated (`ModelGateway`), then applied in one transaction with the project row locked: summary upsert, answered questions, new proposals with backend-generated keys, assistant message, and one `ai-proposal` revision.
4. Model failures map to `model-not-configured`, `model-rate-limited` (429), `model-timeout`, `model-unavailable` (503) and `invalid-model-output` (502).

## Flows

- Canvas edits, AI generation and AI proposals all produce the same `FlowGraph` and go through the same `FlowValidator`.
- An AI proposal is a list of typed operations, never a replacement graph, so unrelated nodes keep their IDs, positions and your edits. `FlowOperations.apply` is pure and is used for both preview and apply.
- The frontend canvas (`apps/web/src/flow`) uses React Flow. Undo/redo is local (structural edits and completed drags); nothing persists until **Save**.

## Prototype and preview safety

- The model returns a `PrototypeSpec`, never code. Jackson maps `type` onto a sealed set of component records with unknown fields rejected, so nothing outside the component list can be stored.
- The web app renders specs with its own MUI components. Strings are React text, so they are escaped and never treated as HTML. There are no links, `src` URLs or event handlers taken from the spec. Actions (navigate, simulated message, set state) run only in the player's local state.
- Because no generated code runs, the preview renders in the app rather than in an iframe. If dynamic code is ever added, it must move to a sandboxed iframe on a separate origin with a strict CSP (design doc §12).
- Every preview carries a "PROTOTYPE · simulated data and actions" banner, and every message action shows as "Simulated: …".
- AI output is always a draft you click through before saving.

## Planned, not built

Cross-artifact change impact and revision compare/restore (Phase 4).
