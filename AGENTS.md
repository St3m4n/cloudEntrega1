# Repository Guidelines

## Project Structure & Module Organization

Pedidos360 uses Java 21 and Spring Boot for its active backend and React/TypeScript with Vite for its frontend.

- `backend/`: Maven reactor with eight `ms-pedidos360-*` services and the shared `common` library. Each module keeps code in `src/main/java`, configuration in `src/main/resources`, and tests in `src/test/java`.
- `demo/entra-demo/`: active frontend; pages and components live in `src/`, assets in `src/assets/` and `public/`.
- `infra/`: local and AWS Compose configurations, CloudFormation, Oracle SQL, and architecture/deployment documents in `infra/docs/`.
- `scripts/`: smoke checks, image publishing, and infrastructure generation.

The other `demo/` services are legacy prototypes. Do not run them alongside the active backend because ports overlap.

## Build, Test, and Development Commands

- From `backend/`, run `.\mvnw.cmd -B -ntp verify` to compile, test, and package all modules. Set `JAVA_HOME` to JDK 21. On Linux, use `./mvnw -B -ntp verify`.
- From `demo/entra-demo/`, run `npm ci`, then `npm run dev` for local development, `npm run build` for TypeScript checking and production assets, and `npm run lint` for Oxlint checks.
- From the repository root, run `docker compose --env-file infra/local/.env -f infra/local/compose.yml up -d --build` to start services and messaging infrastructure after configuring the environment file.

## Coding Style & Naming Conventions

Match surrounding code: Java generally uses four-space indentation; TypeScript/TSX uses two spaces. Use PascalCase for Java classes and React components, camelCase for methods and variables, and lowercase Java packages under `cl.duoc.pedidos360`. Keep service directories named `ms-pedidos360-<role>`. Run Oxlint for frontend edits; no backend formatter is configured.

## Testing Guidelines

Backend tests use JUnit Jupiter, Spring Boot Test, MockMvc, Mockito, and AssertJ. Name classes `*Tests.java`. Add regression cases for authorization, stock transitions, retries, and duplicate events. Tests use H2 in Oracle mode and mocked dependencies; real RDS/broker validation remains separate. No numeric coverage threshold or frontend test script is configured.

## Commit & Pull Request Guidelines

Recent commits use Spanish Conventional Commit subjects, such as `feat: agregar ...`, `fix: corregir ...`, and `chore: actualizar ...`. Follow that pattern. PRs should explain behavior changes, identify affected services, link relevant issues, report validation, and include screenshots for UI changes. Update `infra/docs/CONTRATOS.md` when HTTP or event contracts change.

## Security & Configuration

Copy environment examples before configuring local values. Never commit credentials or access tokens. Preserve JWT role checks, event deduplication, and stock idempotency. Follow `infra/docs/AWS.md` for deployment configuration.
