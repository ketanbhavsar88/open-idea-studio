# Open Idea Studio

> Turn an idea into a clear product plan, visual flows, and a clickable prototype — by thinking it through with AI.

Open Idea Studio is an open-source, AI-assisted workspace that helps people take a rough, early-stage idea and turn it into a structured, editable, and testable product concept.

You describe an idea in plain language. The AI acts as a product discovery facilitator: it summarizes the idea, asks a few focused questions, and helps you record requirements and decisions. From those, the studio generates editable user-flow diagrams and a low-fidelity clickable prototype. You keep refining the project through conversation or by editing things directly.

> **Status:** Phase 2 (flows). Discuss the idea with the AI and review what it proposes, then generate user flows from confirmed requirements and edit them on a canvas. AI flow edits arrive as reviewable proposals you apply or reject. Every change is saved as a revision. The prototype and cross-artifact change impact are not built yet. See [docs/product-design.md](docs/product-design.md) for the full specification.

## Why

Moving from a vague concept to a concrete design is hard. People often don't know which questions to answer, what the initial scope should be, or which screens and workflows are needed. Usually brainstorming, documents, diagrams, and prototypes live in separate tools, so a requirement changes in one place while the flowchart and prototype fall out of date.

Open Idea Studio keeps all of that in **one connected workspace**, and it keeps the reasoning behind earlier decisions.

## What makes it different

It is **not just an AI chat** and **not a production-code generator**. Its main value is keeping four linked artifacts consistent with each other:

1. **Conversation and discovery notes**
2. **Structured requirements and decisions**: each requirement has a stable ID, priority, status, acceptance criteria, and links to the flows and screens it affects
3. **Visual flows**: editable node-and-edge diagrams for journeys like onboarding, booking, or approval
4. **An interactive prototype**: a few connected screens with mock data, rendered from a constrained, trusted component set

When a requirement changes, the studio finds the affected artifacts, shows an impact summary, and proposes a minimal, reviewable change set. **Nothing is applied without your approval**, and every applied change is saved as a new revision you can compare against or restore later.

The core loop:

**Discuss → Structure → Visualize → Prototype → Change → Review → Synchronize**

### Example

Say you start with: *"An app where people rent unused private vehicles from owners for a few hours."*

The AI asks whether owners approve each booking. You answer yes. A decision and a requirement are recorded, and the booking flow gets pending, approved, and rejected outcomes. Later you change the rule, and the studio shows exactly which flow steps and prototype screens need updating before it touches anything.

## Who it's for

Founders and solo builders, product managers, designers, developers validating a concept, students and hackathon teams, and non-technical people who need help putting an idea into words.

## Principles

- **Structure over chat history.** Structured artifacts, not the transcript, are the source of truth.
- **User control.** AI suggestions are shown as reviewable diffs; nothing changes silently.
- **Visible uncertainty.** Confirmed decisions, assumptions, open questions, and AI suggestions are kept separate.
- **Traceability.** Requirements link back to where they came from and forward to the flows and screens that implement them.
- **Incremental change.** Update the smallest set of artifacts needed instead of regenerating everything.
- **Safe preview.** Generated UI is treated as untrusted and rendered in isolation from the app.
- **Useful without AI.** Saved projects can still be viewed and edited when the model is unavailable.

## MVP scope

**In scope:** project creation, AI-guided discovery, a requirements editor, a decision/assumption/risk log, an editable flow canvas, a clickable prototype, change-impact review, and revision history with restore.

**Not in scope (yet):** production app or backend generation, deployment, real-time collaboration, billing, and running arbitrary code.

## Stack

| Layer | Technology |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, MUI, TanStack Query, React Flow (`@xyflow/react`) |
| Backend | Java 25, Spring Boot 4.1, Bean Validation, JdbcClient, Flyway |
| Database | PostgreSQL 17 (relational tables + JSONB artifact payloads) |
| AI model | Gemma via the hosted Google Gemini API by default; optional local Ollama fallback ([docs/ai-providers.md](docs/ai-providers.md)) |

The model never writes to the database directly. It returns typed proposals, which the backend validates and commits in a transaction once you approve them.

## Quick start

```bash
cp infra/.env.example infra/.env         # set POSTGRES_PASSWORD and GEMINI_API_KEY
(cd infra && docker compose up -d)       # PostgreSQL
scripts/api.sh spring-boot:run           # API on :8080 (uses Docker if you have no JDK 25)
cd apps/web && npm install && npm run dev  # open http://localhost:5173
```

Optional offline model: `docker compose --profile local-llm up -d` in `infra/`.

More detail, including tests, is in [docs/local-development.md](docs/local-development.md). Architecture: [docs/architecture.md](docs/architecture.md). Schema: [docs/data-model.md](docs/data-model.md). Contributing: [CONTRIBUTING.md](CONTRIBUTING.md).

**Data note:** by default, project content is sent to the Google Gemini API. Use the Ollama profile if you need to keep data local.

## License

[Apache-2.0](LICENSE). Model weights and third-party services have their own terms.
