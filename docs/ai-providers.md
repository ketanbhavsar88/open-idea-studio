# AI providers

The API talks to models only through `ModelProvider` (`apps/api/.../ai`). Pick one with `AI_PROVIDER` in `infra/.env`; restart the API after changing it. The current provider is shown by `GET /api/v1/ai/status` (never includes secrets).

| `AI_PROVIDER` | Needs | Data leaves your machine? |
| --- | --- | --- |
| `gemini` (default) | `GEMINI_API_KEY` | **Yes**: prompts, including project content, go to the Google Gemini API |
| `ollama` | Ollama running with the model pulled | No (unless your Ollama is remote) |
| `fake` | nothing | No. Canned, clearly labelled demo replies; for UI work and tests |

## Gemini API (Gemma)

1. Create a key at https://aistudio.google.com/apikey.
2. In `infra/.env`: `GEMINI_API_KEY=...`, optionally `GEMINI_MODEL` (default `gemma-4-26b-a4b-it`).
3. Restart the API.

Notes:
- **Data use:** Google's Gemini API terms differ between the free and paid tiers (the free tier may use content to improve products). Do not put sensitive ideas through the free tier.
- **Rate limits:** the free tier has tight per-minute limits. The API spaces calls by `AI_MIN_INTERVAL_MS` (default 4 s) and reports HTTP 429 as a "rate limited" state in the UI; your message is kept and can be retried.
- **System prompt:** Gemma 4 accepts system instructions. For older Gemma models set `GEMINI_SUPPORTS_SYSTEM_PROMPT=false`; the instructions are then sent as the first user turn.
- **Structured output:** native JSON-schema mode is not used for Gemma. The schema is in the prompt; replies are stripped of code fences, parsed strictly (unknown fields rejected), validated, and retried up to `AI_MAX_RETRIES` times with the errors.
- **Per-operation models:** `AI_MODEL_DISCOVERY_TURN`, `AI_MODEL_GENERATE_FLOW`, `AI_MODEL_PROPOSE_FLOW_CHANGE`, `AI_MODEL_GENERATE_PROTOTYPE` and `AI_MODEL_PROPOSE_SCREEN` override the model for one operation (for example a larger Gemma or a Gemini model for flow edits, which need more careful reasoning).

## Ollama

```bash
cd infra
docker compose --profile local-llm up -d
docker compose exec ollama ollama pull gemma3:4b
```

Then `AI_PROVIDER=ollama` (and `OLLAMA_MODEL` if different). Small local models are slower and weaker at producing valid structured output; expect more retries.

## Operations

| Operation | Output | Applied how |
| --- | --- | --- |
| `discovery-turn` | `DiscoveryTurn`: reply, idea summary, next question, proposals | Saved immediately as **unconfirmed** artifacts |
| `generate-flow` | `GeneratedFlow`: nodes with refs, edges, requirement links | Saved as a new **draft** flow (only when you click Generate) |
| `propose-flow-change` | `FlowChangeProposal`: summary + graph operations | Stored as a **pending change set**; applied only when you approve |
| `generate-prototype` | `PrototypeSpec`: screens of constrained components | Returned as an **unsaved draft** you click through, then save or discard |
| `propose-screen` | One `PrototypeSpec.Screen` | Unsaved draft with only that screen replaced |

With `AI_PROVIDER=fake`, each operation returns a canned, labelled demo response; flow change proposals contain no operations, and prototype drafts are a fixed two-screen demo.

## What the model can and cannot do

- It returns typed output only. It never writes to the database.
- Flow output is checked semantically too (refs exist, requirement keys are real, start/end nodes present, operations apply cleanly to the current graph). Failures are sent back to the model as errors for a retry.
- Node and edge IDs and positions for new items are assigned by the backend; IDs the model invents are discarded.
- The backend generates all artifact keys. The model may only reference keys of existing **open questions**; anything else is ignored.
- AI-created items always start unconfirmed (`proposed`, `unconfirmed`, `open`). Only the user can confirm them.
- A user-edited idea summary is never overwritten by the AI.
- Logs record validation error shapes, not prompts or project content.
