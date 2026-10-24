# Open Idea Studio

## Product Vision, Functional Design, and Technical Implementation Brief

**Document type:** Product + solution design\
**Status:** Initial MVP specification\
**Intended audience:** Coding agent, product designer, and engineering
team\
**Working name:** Open Idea Studio (name is provisional)

------------------------------------------------------------------------

## 1. Executive Summary

Open Idea Studio is an open-source, AI-assisted workspace that helps
people turn an early-stage idea into a structured, editable, and
testable product concept.

A user starts by describing an idea in ordinary language. The
application guides them through discovery and clarification, records
requirements and decisions, creates visual flows, and generates a
clickable UI prototype. Users can continue refining the project through
conversation or direct editing.

The product is **not merely an AI chat interface** and **not initially a
production-code generator**. Its core value is maintaining consistency
between four connected artifacts:

1.  Conversation and discovery notes
2.  Structured requirements and decisions
3.  Visual user journeys and system/product flows
4.  An interactive prototype

When a user changes a requirement, the system should identify affected
artifacts, propose specific updates, and let the user review and apply
those changes. It must avoid silently rewriting unrelated parts of the
project.

### Product promise

> Turn an idea into a clear product plan, visual flows, and a clickable
> prototype --- by thinking it through with AI.

------------------------------------------------------------------------

## 2. Problem Statement

People with promising ideas often struggle to move from a vague concept
to a concrete design. They may not know which questions to answer, how
to define the initial scope, what screens or workflows are needed, or
how to communicate their idea to a developer.

Current workflows commonly involve separate tools for brainstorming,
documents, diagrams, and UI prototypes. This creates duplicated work and
inconsistencies: a requirement changes in a document, but the flowchart
and prototype do not.

Open Idea Studio should provide one connected workspace in which users
can explore an idea, make decisions, inspect the resulting design, and
iterate without losing the reasoning behind earlier choices.

### Target users

-   Founders and solo builders exploring a product idea
-   Product managers preparing an initial product specification
-   Designers exploring user journeys and interface concepts
-   Developers validating a concept before implementation
-   Students and hackathon participants turning ideas into demos
-   Non-technical users who need help expressing an idea clearly

### Initial user persona

A person has a rough idea but does not yet have complete requirements, a
defined user journey, or a UI design. They want guided questions and
tangible outputs, not just a long answer from a chatbot.

------------------------------------------------------------------------

## 3. Product Goals and Non-Goals

### Goals for the MVP

-   Make it easy to start with an incomplete idea.
-   Guide the user through discovery without overwhelming them.
-   Distinguish confirmed decisions, assumptions, open questions, and AI
    suggestions.
-   Generate structured requirements that the user can edit.
-   Generate editable user-flow diagrams.
-   Generate a small, clickable UI prototype from approved requirements
    and flows.
-   Keep artifacts linked and detect changes that may affect other
    artifacts.
-   Preserve project versions and allow users to inspect or restore an
    earlier version.
-   Use an open-weight Gemma model, served through the hosted Google
    Gemini API by default, with an optional local Ollama fallback,
    subject to the model's capabilities and license.
-   Keep the core application open-source and practical to run locally.

### Explicit non-goals for the first release

-   Generating a complete production-ready application with backend and
    database logic
-   Automatically deploying generated applications to the public
    internet
-   Replacing a full professional design suite
-   Autonomous execution of arbitrary shell commands or repository
    changes
-   Real-time multi-user collaborative editing
-   Billing, subscriptions, enterprise administration, and marketplace
    features
-   Guaranteeing that AI-generated requirements or designs are correct

These may be considered after the MVP proves the main workflow.

------------------------------------------------------------------------

## 4. Product Principles

1.  **Structure over chat history.** The conversation helps create the
    project, but structured artifacts are the source of truth.
2.  **User control.** AI suggestions are reviewable. Significant changes
    should not be silently applied.
3.  **Traceability.** Important requirements and decisions should link
    to their origin and downstream artifacts.
4.  **Incremental change.** Update the smallest reasonable set of
    artifacts rather than regenerate everything.
5.  **Visible uncertainty.** Label assumptions and unresolved questions
    rather than presenting guesses as facts.
6.  **Model independence.** Keep orchestration separate from any single
    model provider.
7.  **Safe preview.** Treat generated UI code as untrusted and isolate
    it from the main application.
8.  **Useful without AI.** Users should be able to edit, save, and
    inspect existing artifacts even if the model is unavailable.

------------------------------------------------------------------------

## 5. Core User Experience

### 5.1 Create a project

The user selects **New project**, enters a project name, and describes
the idea in free text. The description may be incomplete or ambiguous.

Example:

> I want an application where people can rent unused private vehicles
> from owners for a few hours.

The system creates a project with the initial idea and offers to begin
discovery. It must not require a fully completed form before the user
can start.

### 5.2 AI-guided discovery

The AI behaves like a product discovery facilitator. It should:

-   Summarize the idea in a few sentences.
-   Identify the likely users and the problem being addressed.
-   Ask a small number of high-value questions, preferably one focused
    question at a time.
-   Explain why a question matters when that would help the user decide.
-   Offer options when the user may not know how to answer.
-   Identify risks, constraints, dependencies, and missing decisions.
-   Distinguish user-confirmed facts from assumptions and suggestions.
-   Allow the user to skip questions and revisit them later.

The AI should not interrogate the user with a long questionnaire or
prematurely decide the business model.

Example questions for the vehicle-sharing idea:

-   Who can list a vehicle, and who can rent it?
-   Does the owner approve each booking?
-   How is identity and driving eligibility verified?
-   Who handles insurance, deposits, damage, and disputes?
-   Is pickup arranged by the owner or the renter?

The questions should be selected dynamically based on the current
project, not hard-coded to this example.

### 5.3 Requirements workspace

The system generates a structured requirements document. Each
requirement should have:

-   Stable ID
-   Title
-   Description
-   Category
-   Priority
-   Status
-   Acceptance criteria
-   Origin or rationale
-   Links to relevant decisions, flows, and screens

Suggested requirement categories:

-   Functional
-   Non-functional
-   Business rule
-   Security and privacy
-   Operational constraint
-   Out of scope

Suggested statuses:

-   Proposed
-   Confirmed
-   Needs clarification
-   Deferred
-   Rejected

Users can edit requirements directly. They can also ask the AI to
propose changes, but those changes should be presented as a reviewable
diff.

### 5.4 Decision and assumption log

The application maintains separate lists of:

-   **Confirmed decisions:** explicitly accepted by the user
-   **Assumptions:** plausible but unconfirmed statements
-   **Open questions:** decisions still needed
-   **AI suggestions:** options not yet accepted
-   **Risks:** potential issues and their possible impact

Do not convert an AI suggestion into a confirmed decision without
explicit user approval. A user may correct or reverse a decision later;
preserve the history.

### 5.5 Visual flow canvas

Generate editable flow diagrams for workflows such as registration,
onboarding, booking, approval, payment, and cancellation.

The canvas should support:

-   Nodes and directed connections
-   Start/end nodes, actions, decisions, and optional sub-processes
-   Node labels and descriptions
-   Links to requirements
-   Adding, editing, moving, and deleting nodes
-   Connecting and disconnecting nodes
-   Undo/redo for direct edits where practical
-   Zoom, pan, fit-to-view, and readable labels
-   AI requests such as "Add a manual approval step before payment"
-   Review of AI-proposed changes before applying them

Use a stable, documented graph data model. Do not store only a rendered
image of the diagram.

### 5.6 Interactive prototype

The application generates a low-fidelity but usable UI prototype based
on approved requirements and flows.

For the MVP, target approximately 5--8 screens for a typical small
product, while allowing the actual number to vary with the concept.

Expected capabilities:

-   Basic navigation between screens
-   Buttons, links, forms, tabs, dialogs, and simple state changes
-   Mock/sample data
-   Clear distinction between simulated behavior and real integrations
-   Responsive layout suitable for desktop and a basic mobile viewport
-   Regeneration or modification of selected screens
-   Preview mode separate from editing mode

The prototype does not need real authentication, payment processing,
persistent business data, or production APIs. Do not imply that
simulated operations are real.

### 5.7 Change impact and synchronization

This is a defining feature.

When a requirement changes, the system should:

1.  Identify the changed requirement and its previous version.
2.  Find directly linked flows, decisions, and screens.
3.  Ask the model to assess likely downstream effects.
4.  Show the user a proposed impact summary.
5.  Generate a minimal change set.
6.  Present a diff or preview.
7.  Apply the changes only after user approval, unless the user
    explicitly invokes a clearly defined low-risk automatic mode.
8.  Save the change as a new project revision.

Example:

A requirement changes from "All bookings are automatically accepted" to
"Owners must approve each booking."

Potential impacts: - Booking flow gains a pending-approval state and an
owner decision step. - Booking status requirements need to include
pending, approved, and rejected. - The renter-facing prototype needs a
pending status. - The owner-facing prototype needs an approval screen or
action.

The system must not claim that an artifact is synchronized simply
because the AI regenerated a document. It should track the artifacts
actually changed and identify unresolved inconsistencies.

------------------------------------------------------------------------

## 6. Information Architecture and Main Screens

### 6.1 Projects dashboard

Purpose: find and create projects.

Elements: - Create project - Recent projects - Project title and short
summary - Last updated timestamp - Current stage or completeness
indicators - Delete/archive action with confirmation

Avoid a misleading single "AI completeness score" in the MVP. Prefer
transparent counts such as open questions, confirmed requirements, and
available flows.

### 6.2 Project workspace

Suggested desktop layout:

-   **Left sidebar:** project navigation and artifact list
-   **Center workspace:** selected artifact, diagram canvas, or
    prototype
-   **Right panel:** AI conversation and contextual actions
-   **Top bar:** project title, save state, undo/redo where relevant,
    version history, and preview action

On smaller screens, panels should collapse into tabs or drawers rather
than becoming unusably narrow.

Main workspace sections:

1.  Overview
2.  Discuss
3.  Requirements
4.  Decisions and questions
5.  Flows
6.  Prototype
7.  Version history

### 6.3 Overview

Show: - Idea summary - Target users - Problem statement - Proposed
solution - Current stage - Important open questions - Recent changes -
Next recommended action

Recommendations should be explainable and dismissible.

### 6.4 Requirements editor

Support viewing, filtering, editing, and reviewing proposed updates.
Stable requirement IDs must survive ordinary edits and reordering.

### 6.5 Flow editor

Provide the editable graph canvas and a contextual AI action panel.
Direct canvas edits and AI edits must update the same underlying graph
representation.

### 6.6 Prototype preview

Render the generated UI in an isolated preview. Provide a screen list,
viewport selection, and an option to return to the relevant requirement
or flow.

### 6.7 Revision history

Show revision time, summary, changed artifacts, and the user action that
initiated the change. Support comparing revisions and restoring a
previous revision as a new current revision. Do not erase later history
when restoring.

------------------------------------------------------------------------

## 7. Functional Requirements

### P0 --- Required for MVP

-   FR-001: A user can create, open, rename, and delete a project.
-   FR-002: A user can describe an idea in free text.
-   FR-003: The AI can summarize the idea and ask contextual discovery
    questions.
-   FR-004: The application stores conversation messages by project.
-   FR-005: The AI can propose structured requirements, decisions,
    assumptions, risks, and open questions.
-   FR-006: Users can edit and confirm or reject proposed artifacts.
-   FR-007: The application generates an editable flow graph from
    approved requirements.
-   FR-008: Users can modify graph nodes and connections directly.
-   FR-009: The application can propose graph changes from
    natural-language requests.
-   FR-010: The application generates a clickable prototype from project
    artifacts.
-   FR-011: Prototype interactions can be simulated without requiring a
    production backend.
-   FR-012: The application records revisions and can restore a previous
    state as a new revision.
-   FR-013: AI-generated changes can be reviewed before application.
-   FR-014: The application shows loading, success, validation, and
    failure states for AI operations.
-   FR-015: Users can continue viewing and manually editing saved
    artifacts when AI is unavailable.
-   FR-016: The application can run locally with documented setup
    instructions; dependencies start with `docker compose up` in
    `infra/`.

### P1 --- Important after the core workflow works

-   PR-001: Change-impact analysis across requirements, flows, and
    screens.
-   PR-002: Traceability links from requirements to flows and screens.
-   PR-003: Export project requirements and flow data to portable
    formats.
-   PR-004: Export a prototype as a portable artifact where technically
    safe.
-   PR-005: Search across a project's artifacts and discussion.
-   PR-006: Compare artifact versions.
-   PR-007: Configurable model/provider settings.
-   PR-008: Project import/export with schema versioning.

### P2 --- Future possibilities

-   Collaborative editing and sharing
-   Comments and review workflows
-   Multiple prototype themes or design systems
-   More sophisticated system architecture diagrams
-   Code generation for selected frontend components
-   Optional integrations with design and issue-tracking tools
-   Optional generated backend/API scaffolding
-   Team workspaces and access controls

------------------------------------------------------------------------

## 8. Acceptance Criteria for the MVP

The MVP is considered demonstrable when all the following can be
completed in a clean local setup:

1.  Create a new project from a one-paragraph idea.
2.  Receive an idea summary and relevant discovery questions.
3.  Answer questions and see the structured project state evolve.
4.  Review and edit generated requirements.
5.  Confirm a requirement and generate a user flow.
6.  Edit a node directly on the canvas and save the change.
7.  Ask the AI to modify the flow and review its proposed change.
8.  Generate a clickable prototype with several connected screens.
9.  Change a confirmed requirement and receive an impact summary for
    linked artifacts.
10. Apply a proposed update and see the changed artifacts reflected in
    the workspace.
11. Reopen the project and retain its saved state.
12. Restore a previous revision without losing the later revision
    history.
13. Stop or disconnect the model and still view and edit saved project
    data.
14. Run the project using the README instructions on a supported
    development environment.

For any feature that is not implemented, show an explicit limitation
rather than a fake success state.

------------------------------------------------------------------------

## 9. Technical Architecture

### 9.1 Recommended initial stack

#### Frontend

-   React
-   TypeScript
-   Vite
-   Material UI (MUI) for common UI components
-   React Flow / `@xyflow/react` for editable diagrams
-   A code or component preview mechanism selected with sandboxing as a
    primary requirement

#### Backend

-   Java and Spring Boot
-   Spring Web for REST APIs
-   Spring AI or a small provider abstraction for model integration
-   Bean Validation for request and artifact validation
-   Flyway for database migrations

#### Persistence

-   PostgreSQL
-   JSONB for versioned structured artifact payloads where appropriate
-   Relational tables for projects, messages, artifact identity,
    revisions, and relationships

#### Model runtime

-   Gemma (instruction-tuned, for example `gemma-4-26b-a4b-it`) called
    through the hosted Google Gemini API as the default provider
-   Google Gen AI Java SDK (`com.google.genai:google-genai`) wrapped by a
    thin adapter
-   A configurable model name, optionally per AI operation
-   Optional Ollama provider for offline/local inference
-   Provider interface to avoid coupling business logic to
    Gemini- or Ollama-specific request formats

Choose exact dependency versions compatible with one another during
implementation and document them. Do not assume a model supports every
structured-output or tool-calling feature. Detect and handle model
capability limitations.

### 9.2 Logical components

1.  **Web UI:** workspace, conversation, editors, flow canvas, and
    prototype preview.
2.  **Project API:** project CRUD, artifact reads/writes, revision
    history, and exports.
3.  **Conversation orchestrator:** builds relevant context and calls the
    selected model.
4.  **Artifact service:** validates and persists structured
    requirements, decisions, and graph data.
5.  **Change planner:** translates an AI proposal into a list of
    targeted artifact operations.
6.  **Validation service:** checks schemas, IDs, references, graph
    integrity, and permitted operations.
7.  **Revision service:** stores revision snapshots and change
    summaries.
8.  **Prototype generator:** converts approved artifacts into
    constrained prototype components or a validated prototype
    specification.
9.  **Model adapter:** communicates with Gemma through the Gemini API,
    with an optional Ollama adapter and a fake adapter for tests.
10. **Preview sandbox:** renders prototype content separately from the
    trusted application.

### 9.3 Architecture rule

The model should not write directly to the database or execute arbitrary
operations. It should return a typed proposal. The backend validates the
proposal, checks references and permissions, and commits the accepted
changes in a controlled transaction.

### 9.4 Suggested repository structure

Use a simple monorepo initially:

``` text
open-idea-studio/
├── apps/
│   ├── web/                       # React + TypeScript frontend
│   └── api/                       # Spring Boot application
├── docs/
│   ├── product-design.md
│   ├── architecture.md
│   ├── data-model.md
│   └── local-development.md
├── infra/
│   ├── docker-compose.yml          # Local dependencies (Postgres, optional Ollama)
│   ├── .env.example                # Placeholder configuration
│   └── postgres/
│       ├── Dockerfile              # Postgres image + healthcheck
│       └── init/01-extensions.sql  # Extensions only; schema is Flyway's
├── examples/
│   └── vehicle-sharing-project/    # Example/demo project
├── scripts/
├── .env.example
├── LICENSE
├── README.md
└── CONTRIBUTING.md
```

This is a proposed structure, not a reason to create empty placeholder
files. Add modules only when there is working functionality to put in
them.

### 9.5 AI provider integration (Gemma via Gemini API)

Business logic depends only on a provider interface:

``` java
public interface ModelProvider {
    ModelCapabilities capabilities();          // systemPrompt, jsonSchema, tools, contextTokens
    ModelResponse generate(ModelRequest req);  // system text, messages, optional schema, timeout
}
```

Implementations:

-   `GeminiApiProvider` --- default; calls a Gemma model on the Gemini API.
-   `OllamaProvider` --- optional offline path (Ollama can also run Gemma,
    so prompts stay portable).
-   `FakeProvider` --- deterministic responses for automated tests.

Configuration (backend only; see `infra/.env.example`):

``` properties
ai.provider=${AI_PROVIDER:gemini}
ai.gemini.api-key=${GEMINI_API_KEY}
ai.gemini.model=${GEMINI_MODEL:gemma-4-26b-a4b-it}
ai.timeout-seconds=60
ai.max-retries=2
```

Integration rules:

-   **System prompt:** Gemma 4 on the Gemini API accepts a system
    instruction. Older Gemma versions did not; when
    `supportsSystemPrompt` is false, send the instructions as the first
    user turn.
-   **Structured output:** native JSON-schema output is not confirmed for
    Gemma model IDs. Default to schema-in-prompt plus JSON-only
    instructions, strip code fences, parse, validate against the DTO
    schema, and retry with validation errors in the prompt. Enable native
    schema output through the capability flag only after testing it.
-   **Tool calling:** not required. The model returns typed proposals
    (§9.3, §11.3) rather than invoking tools.
-   **Errors:** map HTTP 429 (rate limit/quota), timeouts, and 5xx to the
    model-unavailable and invalid-output states (FR-014). Throttle
    requests on the backend; the free tier has tight per-minute limits.
-   **Model per operation:** allow overriding the model for demanding
    operations (`analyzeChangeImpact`, `proposeArtifactChanges`), so they
    can move to a larger Gemma or a Gemini model without code changes.

### 9.6 Local dependencies

All infrastructure dependencies live in `infra/` and run with Docker
Compose, separate from application code:

-   `postgres` --- built from `infra/postgres/Dockerfile`
    (PostgreSQL 17), bound to `127.0.0.1`, with a healthcheck and a named
    data volume. The init script only enables extensions (`pgcrypto`,
    `pg_trgm`); all tables are created by Flyway.
-   `ollama` --- optional, enabled with the `local-llm` profile.
-   The Gemini API is external and needs no container; only
    `GEMINI_API_KEY` is required.

``` bash
cd infra
cp .env.example .env               # set POSTGRES_PASSWORD and GEMINI_API_KEY
docker compose up -d               # Postgres
docker compose --profile local-llm up -d   # + Ollama fallback
```

------------------------------------------------------------------------

## 10. Data Model

The data model should support structured editing, traceability, and
revisions. The following is a logical starting point; the implementation
agent should refine the relational schema and migrations.

### Project

-   `id`
-   `name`
-   `description`
-   `created_at`
-   `updated_at`
-   `current_revision_id`
-   `schema_version`

### ConversationMessage

-   `id`
-   `project_id`
-   `role` (user, assistant, or system/tool representation as
    appropriate)
-   `content`
-   `created_at`
-   `metadata` (optional structured metadata)

Do not expose hidden model reasoning. Persist useful user-facing
summaries, decisions, tool outcomes, and explanations instead.

### Artifact

-   `id`
-   `project_id`
-   `type` (requirement, decision, assumption, question, risk, flow,
    screen, or other defined artifact)
-   `title`
-   `status`
-   `data` (typed JSON payload)
-   `created_at`
-   `updated_at`
-   `schema_version`

### ArtifactRelationship

-   `id`
-   `project_id`
-   `source_artifact_id`
-   `target_artifact_id`
-   `relationship_type` (for example, derived-from, implements, affects,
    or references)

### ProjectRevision

-   `id`
-   `project_id`
-   `parent_revision_id`
-   `summary`
-   `created_at`
-   `created_by` (user or system action)
-   `snapshot` or a well-defined immutable revision reference

### ProposedChangeSet

-   `id`
-   `project_id`
-   `base_revision_id`
-   `summary`
-   `operations`
-   `validation_status`
-   `created_at`
-   `applied_at`

A proposed change set should refer to the revision it was based on.
Reject or re-evaluate it if the project has changed since that revision,
rather than blindly overwriting newer work.

### Example flow node

``` json
{
  "id": "node-booking-approval",
  "type": "decision",
  "label": "Owner approves booking?",
  "description": "The owner decides whether to accept the request.",
  "position": { "x": 360, "y": 220 },
  "requirementIds": ["REQ-BOOKING-APPROVAL"],
  "metadata": {}
}
```

### Example flow graph

``` json
{
  "id": "FLOW-BOOKING",
  "title": "Booking flow",
  "nodes": [],
  "edges": [],
  "schemaVersion": 1
}
```

Stable IDs are required. AI-generated updates must not replace every ID
on each generation, because that would break traceability and make diffs
noisy.

------------------------------------------------------------------------

## 11. AI Orchestration and Structured Outputs

### 11.1 Avoid one giant prompt

Use separate task-specific operations such as:

-   `summarizeIdea`
-   `generateDiscoveryQuestions`
-   `proposeRequirements`
-   `extractDecisions`
-   `generateFlow`
-   `proposeArtifactChanges`
-   `analyzeChangeImpact`
-   `generatePrototypeSpecification`
-   `validateProjectConsistency`

These can share a model adapter and common context builder. They do not
necessarily need separate agents or separate model instances.

### 11.2 Context selection

For each AI operation, include only the context it needs:

-   Current idea summary
-   Relevant confirmed requirements and decisions
-   Relevant assumptions and open questions
-   Target artifacts and their IDs
-   Current revision identifier
-   User's latest instruction
-   Output schema and constraints

Use the full conversation only when necessary. Summarize older
discussion into explicit project artifacts instead of relying on a
large, ever-growing prompt.

### 11.3 Structured proposal format

The model should return a validated, machine-readable proposal. For
example:

``` json
{
  "baseRevisionId": "rev-12",
  "summary": "Add owner approval before a booking is confirmed.",
  "operations": [
    {
      "operation": "update",
      "artifactId": "REQ-BOOKING",
      "patch": {
        "description": "The owner must approve or reject each booking request."
      }
    },
    {
      "operation": "create",
      "artifactType": "requirement",
      "data": {
        "id": "REQ-BOOKING-APPROVAL",
        "title": "Owner booking approval",
        "status": "proposed"
      }
    }
  ],
  "warnings": []
}
```

This is illustrative. Define real schemas and patch semantics in code.
Do not trust the model to invent valid IDs, silently change artifact
types, or return unrestricted database updates.

### 11.4 Validation rules

-   Validate every proposal against a schema.
-   Confirm that referenced artifact IDs exist.
-   Prevent duplicate IDs and broken relationships.
-   Reject unknown operation types and disallowed fields.
-   Validate graph nodes, edges, and target references.
-   Keep proposals separate from committed state until approved.
-   Handle invalid JSON and model timeouts with bounded retries.
-   Record actionable error details without logging secrets or
    unnecessarily sensitive project content.
-   Make applying a change set atomic where possible.
-   Re-check the base revision before applying.
-   Do not claim successful updates until persistence succeeds.

### 11.5 Model limitations

Models vary in instruction following, context length, structured
output, and tool support, and Gemma's capabilities on the Gemini API
differ from Gemini models'. Record them per model in
`ModelCapabilities`. The application should display actionable
errors, use constrained output formats where supported, and validate
every result. A smaller model may be adequate for summarization but
weaker at complex cross-artifact change planning.

------------------------------------------------------------------------

## 12. Prototype Generation and Safety

Prototype generation is a major technical risk and should be
deliberately constrained.

### Recommended MVP approach

Generate a structured UI specification and render it using a set of
trusted, predefined React components. This is safer and more predictable
than executing arbitrary model-generated JavaScript.

Example component types:

-   Page
-   Header
-   Sidebar
-   Card
-   Table
-   Form
-   Input
-   Button
-   Dialog
-   Tabs
-   Navigation link
-   Status badge

Define an explicit schema for these components, their allowed
properties, and supported interactions. Validate the specification
before rendering it.

### Preview safety requirements

-   Treat all model output as untrusted input.
-   Do not run generated code in the main application's JavaScript
    context.
-   Prefer a constrained renderer and mock actions over arbitrary
    executable code.
-   If dynamic code execution is later added, isolate it in a sandboxed
    iframe or separate origin with a restrictive Content Security
    Policy.
-   Do not expose application credentials, cookies, local storage,
    backend tokens, or privileged APIs to the preview.
-   Do not allow generated prototypes to execute arbitrary shell
    commands or access the host filesystem.
-   Clearly label mock integrations and simulated transactions.

The goal is to produce a convincing interactive prototype without
creating an unrestricted code execution service.

------------------------------------------------------------------------

## 13. API Design --- Initial Proposal

Use versioned REST endpoints and consistent error responses. The exact
API may evolve during implementation.

### Projects

-   `POST /api/v1/projects`
-   `GET /api/v1/projects`
-   `GET /api/v1/projects/{projectId}`
-   `PATCH /api/v1/projects/{projectId}`
-   `DELETE /api/v1/projects/{projectId}`

### Conversation

-   `GET /api/v1/projects/{projectId}/messages`
-   `POST /api/v1/projects/{projectId}/messages`

The message endpoint may return an operation ID or stream progress if
the chosen model and implementation support it reliably. Streaming is
optional for the first vertical slice.

### Artifacts

-   `GET /api/v1/projects/{projectId}/artifacts`
-   `GET /api/v1/projects/{projectId}/artifacts/{artifactId}`
-   `PATCH /api/v1/projects/{projectId}/artifacts/{artifactId}`
-   `POST /api/v1/projects/{projectId}/artifacts/proposals`
-   `POST /api/v1/projects/{projectId}/changesets/{changeSetId}/apply`
-   `POST /api/v1/projects/{projectId}/changesets/{changeSetId}/reject`

### Flows and prototype

-   `GET /api/v1/projects/{projectId}/flows`
-   `PUT /api/v1/projects/{projectId}/flows/{flowId}`
-   `POST /api/v1/projects/{projectId}/flows/{flowId}/proposals`
-   `POST /api/v1/projects/{projectId}/prototype/generate`
-   `GET /api/v1/projects/{projectId}/prototype`

### Revisions

-   `GET /api/v1/projects/{projectId}/revisions`
-   `GET /api/v1/projects/{projectId}/revisions/{revisionId}`
-   `POST /api/v1/projects/{projectId}/revisions/{revisionId}/restore`

For all write operations, validate the project, artifact ownership,
payload schema, and expected revision. Use appropriate HTTP status codes
and return actionable, non-sensitive errors.

------------------------------------------------------------------------

## 14. Persistence, Versioning, and Consistency

The revision model must be clear before implementing cross-artifact
changes.

### Minimum viable strategy

-   Store current project artifacts in normalized records or
    well-defined JSONB payloads.
-   Keep immutable revision snapshots or equivalent reconstructable
    revision records.
-   Store a concise summary and list of affected artifacts for each
    revision.
-   Link each proposed change set to its base revision.
-   Apply approved operations transactionally when possible.
-   Use optimistic concurrency control to prevent stale updates.
-   Restore old versions by creating a new revision from the selected
    historical state.
-   Keep stable artifact IDs across revisions.
-   Validate relationships after applying changes.

For a small MVP, snapshot-based revisions are acceptable and simpler
than event sourcing. Avoid introducing event sourcing or a distributed
microservice architecture without a demonstrated need.

------------------------------------------------------------------------

## 15. Security, Privacy, and Reliability

-   Keep model credentials and provider secrets on the backend.
-   Do not commit real secrets to the repository.
-   Provide `.env.example` with placeholder values only.
-   Clearly disclose that project content is sent to the Google Gemini
    API by default, and document that data flow.
-   Review the Gemini API data-use terms, which differ between free and
    paid tiers, before handling sensitive content.
-   Read `GEMINI_API_KEY` only on the backend; never expose it to the
    browser or the prototype preview.
-   Offer the Ollama provider when privacy and self-hosting are
    important.
-   Add reasonable request-size and rate limits to expensive AI
    operations.
-   Validate all user and model inputs.
-   Escape rendered text and guard against XSS.
-   Protect project data using proper authorization if user accounts are
    introduced.
-   Use restrictive preview isolation.
-   Avoid logging full conversations or documents by default.
-   Provide clear model-unavailable, invalid-output, timeout, and
    persistence-error states.
-   Use database migrations and a documented backup/export path.
-   Do not claim that local inference guarantees privacy if the
    application sends telemetry or data to other services; document
    actual data flows.

Authentication is not a reason to delay the first local-only vertical
slice. However, before exposing the service to a network or multiple
users, implement authentication, authorization, and appropriate
isolation.

------------------------------------------------------------------------

## 16. Testing Strategy

### Unit tests

-   Requirement and decision validation
-   Artifact ID and relationship validation
-   Flow graph integrity
-   Change-set validation
-   Revision creation and restoration
-   Optimistic concurrency handling
-   Prototype specification validation

### Integration tests

-   Project creation and persistence
-   Conversation request and model adapter behavior
-   Invalid model output handling
-   Applying and rejecting a change set
-   Revision restoration
-   Database migration execution

Use a fake or stub model in most automated tests so tests remain
deterministic and do not require a network model. Keep a smaller set of
opt-in integration tests against the Gemini API that run only when
`GEMINI_API_KEY` is set, plus optional Ollama tests. Run database
integration tests against the `infra/` Postgres container or
Testcontainers.

### End-to-end tests

Test the main journey:

1.  Create a project.
2.  Enter an idea.
3.  Complete a discovery exchange.
4.  Review and confirm requirements.
5.  Generate and edit a flow.
6.  Generate a prototype.
7.  Change a requirement.
8.  Review and apply the impact proposal.
9.  Reload the project and verify persistence.
10. Restore a previous revision.

### Evaluation scenarios

Create a small, version-controlled set of test ideas with expected
outcomes. Evaluate whether the AI:

-   Asks relevant questions
-   Distinguishes assumptions from confirmed decisions
-   Produces valid structured artifacts
-   Preserves stable IDs
-   Proposes changes only to relevant artifacts
-   Identifies obvious downstream impacts
-   Avoids claiming that simulated functionality is real

Use quality checks and human review rather than relying on a single
model-generated score.

------------------------------------------------------------------------

## 17. Development Plan

Build one working vertical slice at a time. Avoid creating all layers as
empty scaffolding before proving the workflow.

### Phase 0 --- Repository and project foundation

-   Establish the monorepo, frontend, backend, and the `infra/` Docker
    Compose setup for PostgreSQL.
-   Add health checks, configuration, database migrations, linting, and
    basic tests.
-   Create a project and reload it successfully.
-   Document local startup instructions.

**Exit criteria:** A developer can run the application locally and
create a persisted project.

### Phase 1 --- Idea discussion and structured discovery

-   Add the conversation UI and API.
-   Integrate the model adapter with Gemma through the Gemini API.
-   Generate idea summaries and contextual discovery questions.
-   Persist messages and extract proposed requirements, assumptions, and
    decisions.
-   Allow users to confirm or reject proposed artifacts.

**Exit criteria:** A short discussion produces editable, persisted
project artifacts.

### Phase 2 --- Editable flow canvas

-   Define the flow graph schema.
-   Render nodes and connections.
-   Support direct graph editing.
-   Generate a flow from confirmed requirements.
-   Propose and review AI-generated graph changes.

**Exit criteria:** Direct edits and AI proposals modify the same graph
model and survive reloads.

### Phase 3 --- Interactive prototype

-   Define the constrained UI specification schema.
-   Build the trusted component renderer.
-   Generate a small multi-screen prototype from project artifacts.
-   Add navigation and basic mock interactions.
-   Validate and safely render the output.

**Exit criteria:** A user can click through a generated prototype
without a production backend.

### Phase 4 --- Change impact and revisions

-   Introduce explicit artifact relationships.
-   Add change sets and base-revision checks.
-   Generate impact summaries.
-   Review, apply, and reject proposed changes.
-   Add revision history and restore.

**Exit criteria:** A requirement change can update relevant linked
artifacts without overwriting unrelated work.

### Phase 5 --- Demo readiness and open-source release

-   Add a sample project.
-   Improve error states and empty states.
-   Add unit, integration, and end-to-end coverage.
-   Document architecture, limitations, security, model setup, and
    contribution workflow.
-   Add an appropriate open-source license.
-   Prepare a short demo script and screenshots or recording.

**Exit criteria:** A new contributor can run the project from the README
and reproduce the central demo.

------------------------------------------------------------------------

## 18. Demo Scenario

Use one consistent example throughout implementation and the final
demonstration.

**Example project:** A marketplace for short-term sharing of privately
owned vehicles.

Demonstration sequence:

1.  Enter the one-paragraph idea.
2.  Show the AI summarizing the concept and asking a useful question
    about booking approval.
3.  Answer that owners must approve booking requests.
4.  Show the decision and requirement created from the answer.
5.  Generate a booking flow with pending, approved, and rejected
    outcomes.
6.  Edit a node on the canvas.
7.  Generate a clickable prototype with vehicle listings, a booking
    request, and booking status.
8.  Change a requirement and show the proposed downstream impacts.
9.  Review and apply the change set.
10. Show the updated flow and prototype, then restore an earlier
    revision.

The most important demonstration is not the initial generation. It is
the controlled change across linked artifacts, with the user retaining
final approval.

------------------------------------------------------------------------

## 19. Open-Source Release Requirements

-   Choose a license after checking all dependencies, model licenses,
    and intended use.
-   Include the license in the repository.
-   Document Gemini API key setup, Gemma model choice, optional Ollama
    hardware expectations, and known limitations.
-   Provide an example `.env` file without secrets.
-   Include a reproducible local development setup.
-   Document architecture and key data schemas.
-   Include contribution guidelines and issue templates where useful.
-   Make sample data safe to publish.
-   Document third-party services and any outbound data transmission.
-   Do not bundle model weights unless their licenses and distribution
    terms explicitly permit it.
-   Keep the core functionality usable on the Gemini API free tier or
    through the Ollama fallback, without requiring paid services.

An MIT or Apache-2.0 license may be suitable for the code, but model
weights and third-party components have separate terms that must be
reviewed.

------------------------------------------------------------------------

## 20. Risks and Mitigations

  -----------------------------------------------------------------------
  Risk                                Mitigation
  ----------------------------------- -----------------------------------
  The product becomes a generic       Make structured artifacts and
  chatbot                             direct editing central to the UX.

  AI invents or misinterprets         Track provenance and uncertainty;
  requirements                        require confirmation for important
                                      decisions.

  Regeneration destroys user edits    Use stable IDs, targeted patches,
                                      diffs, and revisions.

  Model output is malformed           Validate schemas and graph
                                      references; show recoverable
                                      errors.

  Local models are too weak for some  Keep tasks narrow, test supported
  tasks                               models, and expose provider
                                      configuration.

  Prototype execution creates         Prefer a constrained component
  security risks                      renderer; isolate any future
                                      dynamic code.

  Scope expands too quickly           Finish the
                                      discovery-to-flow-to-prototype
                                      journey before adding integrations.

  Cross-artifact synchronization      Maintain explicit relationships,
  becomes unreliable                  change sets, and consistency
                                      checks.

  AI latency harms usability          Show progress, support cancellation
                                      where possible, and keep model
                                      operations bounded.

  The application implies a prototype Label mock data and simulated
  is production-ready                 integrations clearly.
  Gemini API rate limits, quota,      Throttle and queue requests, show
  or outage block the demo            clear quota errors, allow per-
                                      operation models and the Ollama
                                      fallback.

  -----------------------------------------------------------------------

------------------------------------------------------------------------

## 21. Definition of Done for Every Feature

A feature is not complete just because the happy path works once.

Each feature should include:

-   User-facing behavior and clear loading/empty/error states
-   Input and output validation
-   Persistence where appropriate
-   Unit tests for core logic
-   Integration tests for important backend interactions
-   No hard-coded secrets
-   Documentation for configuration and use
-   A clear boundary between real functionality and simulation
-   Accessibility and responsive behavior for the main interface
-   No regressions in existing project data or revision history

The agent should run relevant tests and report actual results. It must
not claim tests passed unless it executed them successfully.

------------------------------------------------------------------------

## 22. Instructions to the Coding Agent

Implement this design incrementally and prioritize a working, coherent
MVP over breadth.

### Required working approach

1.  Inspect the existing repository before changing files. If it is
    empty, initialize the proposed structure.
2.  Summarize the implementation plan and identify major technical
    uncertainties.
3.  Establish a working local environment and a minimal end-to-end
    vertical slice first.
4.  Implement the database schema and migrations before relying on
    persisted features.
5.  Keep API contracts and frontend types aligned.
6.  Use typed DTOs and explicit schemas for AI-generated proposals.
7.  Keep model calls behind an abstraction and provide a fake
    implementation for tests.
8.  Treat model output as untrusted input.
9.  Do not silently replace user-confirmed decisions or unrelated
    artifacts.
10. Avoid unnecessary microservices, premature abstractions, and
    placeholder features.
11. Add tests as each phase is implemented.
12. Update the README and architecture documentation as the
    implementation changes.
13. After each phase, report files changed, behavior implemented, tests
    executed and their actual outcomes, known gaps, and the next
    recommended phase.

### Implementation priorities

**First:** project persistence and local startup.\
**Second:** idea discussion that creates structured artifacts.\
**Third:** editable flows.\
**Fourth:** a safe clickable prototype.\
**Fifth:** change-impact analysis and revision history.

If the repository contains existing conventions, preserve them where
reasonable. If a design decision is ambiguous, choose a simple,
maintainable default, document it, and continue unless the decision
would cause significant rework or introduce a security risk.

Do not implement every future possibility before the core workflow is
usable.

------------------------------------------------------------------------

## 23. Initial Product Success Measures

For the MVP, measure observable behavior rather than vanity metrics:

-   A new user can create a project without instructions.
-   A user can turn an idea into a first set of requirements in one
    guided session.
-   A generated flow can be edited without losing its structure.
-   A user can navigate a generated prototype.
-   A requirement change produces a reviewable and relevant impact
    proposal.
-   Project data survives reloads and revisions can be restored.
-   A developer can run the project using the documented setup.
-   The demonstration works on the Gemini API free tier, or with the
    Ollama fallback when no network model is available.

These are initial validation goals, not claims of achieved performance.

------------------------------------------------------------------------

## 24. Final Product Definition

Open Idea Studio is an open-source workspace for turning early ideas
into structured product designs and interactive prototypes through
guided AI collaboration.

Its distinguishing capability is not simply that it can generate
requirements, diagrams, or screens. It is that these artifacts share a
structured project model, can be edited by the user, preserve their
history, and can be updated through controlled, traceable changes.

**Build the smallest version that proves this loop:**

**Discuss → Structure → Visualize → Prototype → Change → Review →
Synchronize.**

Everything else is secondary until that loop works reliably.