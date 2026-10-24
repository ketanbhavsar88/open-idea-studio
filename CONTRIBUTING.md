# Contributing

Thanks for helping build Open Idea Studio.

## Getting set up

Follow [docs/local-development.md](docs/local-development.md).

## Ground rules

- Read [docs/product-design.md](docs/product-design.md) first, especially the principles (§4) and the definition of done (§21).
- Build working vertical slices. Don't add empty placeholder modules or fake success states; show an explicit limitation instead.
- Model output is untrusted input. The model returns typed proposals; the backend validates them and only commits after user approval.
- Every committed change to project state must create a revision in the same transaction.
- Keep `apps/web/src/api/types.ts` aligned with the API DTOs.
- Never commit secrets. Configuration goes in `infra/.env` (gitignored); placeholders go in `infra/.env.example`.
- Schema changes go in a new Flyway migration.

## Before opening a pull request

```bash
scripts/api.sh verify
cd apps/web && npm run lint && npm test && npm run build
```

Describe what changed, how you tested it, and any known gaps.

## License

By contributing you agree that your contributions are licensed under [Apache-2.0](LICENSE).
