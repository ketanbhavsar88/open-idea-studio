# Open Idea Studio

An open-source, AI-assisted workspace for turning an early idea into structured requirements, editable flows, and a clickable prototype. See [docs/product-design.md](docs/product-design.md).

## Local dependencies

```bash
cd infra
cp .env.example .env    # set POSTGRES_PASSWORD and GEMINI_API_KEY
docker compose up -d                       # PostgreSQL
docker compose --profile local-llm up -d   # optional Ollama fallback
```

The AI model (Gemma) is called through the hosted Google Gemini API, so it needs no container.
