# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

A quiz/test-taking system ("Test tizimi"): users pick a subject, one or more topics and a question count, then take the test in **LEARNING** (instant feedback) or **EXAM** (results only at the end) mode. The README, code comments and UI strings are in Uzbek — keep new user-facing text and comments in Uzbek to match.

Three parts:
- `backend/` — Java 21, Spring Boot 3.5, Spring Security (JWT), JPA, Flyway, PostgreSQL 16. Package `uz.testsystem`, organized by feature (`auth`, `subject`, `topic`, `question`, `test`, `stats`, `importer`, `admin`, `security`, `common`, `config`).
- `frontend/` — Vue 3 + TypeScript (`<script setup>`), Vite, Naive UI, Pinia, Vue Router. Served by nginx in Docker, which proxies `/api/*` to the backend.
- `mobile/` — offline Android APK (Capacitor 7) with no server or login. Independent of the web deployment.

## Commands

```bash
# Full stack (postgres + backend + nginx/frontend) at http://localhost:3000
cp .env.example .env && docker compose up -d --build
docker compose up -d --build backend     # rebuild only backend (also reloads seed files)

# Local dev
docker compose -f docker-compose.dev.yml up -d       # Postgres only, on 127.0.0.1:5433
cd backend && DB_URL=jdbc:postgresql://localhost:5433/test_system mvn spring-boot:run   # :8080
cd frontend && npm install && VITE_API_TARGET=http://localhost:8080 npm run dev        # :5173 (proxy default is :8081)

# Backend tests (integration tests use Testcontainers → Docker must be running)
cd backend && mvn test
cd backend && mvn test -Dtest=QuestionPickerTest              # single class
cd backend && mvn test -Dtest=TestFlowIntegrationTest#learningModeFlow   # single method

# Frontend: no unit tests; typecheck is the check
cd frontend && npm run typecheck        # build: npm run build

# Mobile
cd mobile && npm test                   # Vitest + fake-indexeddb (src/api/local/service.test.ts)
cd mobile && npx vitest run -t "<name>" # single test
cd mobile && ./build-apk.sh             # bumps versionCode, builds bundle, Gradle in Docker, signs with keystore/
```

Swagger UI: `/api/swagger-ui/index.html`. Default admin comes from `ADMIN_USERNAME`/`ADMIN_PASSWORD`, created on first start by `AdminInitializer`.

## Architecture notes

**Subject content comes from seed JSON, not migrations.** Flyway (`db/migration`) manages schema only. On startup `SeedDataLoader` imports every `backend/src/main/resources/seed/*.json` whose subject name doesn't exist yet (skips existing; disable with `SEED_DATA=false`). The same format is used by the admin JSON import (`ImportService`) — see README "Import formati" for the schema and validation rules (2–6 options, exactly one correct, no duplicate option texts). Integration tests override `app.seed.location=classpath:demo/*.json` to use `src/test/resources/demo/` instead of real content.

**Question picking (`test/QuestionPicker`)** round-robins random questions across selected topics so topics are balanced (diff ≤ 1), backfills from other topics when one runs out, and shuffles both question and option order. Option order is persisted per session (`option_order` on `SessionQuestion`) so reloads are stable. Max 500 questions per test (frontend enforces too).

**Answer leakage rules:** while a test is in progress, `correctOptionId`, `correct` and `explanation` must not be sent. In LEARNING mode they're returned only for the just-answered question (answer is then locked); in EXAM mode only after finish (answers changeable until then). Stats exclude answers from in-progress exams for the same reason. Keep these invariants when touching `TestService`/`TestDtos`/`StatsService`.

**Soft delete everywhere:** subjects, topics, questions and options are deactivated, never deleted. Editing a question deactivates removed options so historical results remain valid. "Retry wrong" builds a new test from wrong + unanswered questions, excluding inactive ones. Unanswered counts as wrong in scoring but is shown separately.

**Backend conventions:** DTOs are Java `record`s grouped in a per-feature `*Dtos.java` with small `from()` mappers (no MapStruct). Errors are thrown as `ApiException` and rendered by `GlobalExceptionHandler` in a uniform `{timestamp, status, message, errors[]}` shape. Admin endpoints live under `/api/admin/**` (role-protected in `SecurityConfig`). Testcontainers is pinned to 1.21.4 deliberately (works with Docker 29).

**Mobile reuses the web frontend via Vite aliases.** `mobile/vite.config.ts` maps `@/` to `../frontend/src`, so web views/components are used unchanged, but swaps only the server-bound modules: `@/api` → `mobile/src/api/index.ts`, `@/api/http` → `mobile/src/api/errors.ts`, `@/stores/auth` → `mobile/src/stores/auth.ts`. `@m/` refers to mobile's own `src`. Consequences:
- Changes to `frontend/src` (views, components, `types`, the `api` interface) affect the mobile build too — keep the mobile `api/index.ts` signature in sync with `frontend/src/api/index.ts`.
- `mobile/src/api/local/` is a TypeScript port of the backend logic (picker, learning/exam modes, results, retry-wrong, history, stats) over IndexedDB (Dexie). Behavior changes in the backend `test`/`stats` services usually need a matching change here.
- Mobile bundles subjects directly from `backend/src/main/resources/seed/*.json` via `import.meta.glob`; adding a seed file and rebuilding the APK ships it.

`mobile/keystore/` (signing key) is not in git and must not be lost — without it new APKs can't upgrade existing installs. `books/` holds copyrighted textbook PDFs used to author seed content; it's gitignored.
