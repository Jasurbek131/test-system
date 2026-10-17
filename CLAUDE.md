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

**Answer leakage rules:** while a test is in progress, `correctOptionId`, `correct` and `explanation` must not be sent. In LEARNING and BLITZ mode they're returned only for the just-answered question (answer is then locked, `TestMode.revealsInstantly()`); in EXAM/BLOCK mode only after finish (answers changeable until then). FLASHCARD is the exception: the answer and explanation are shown up front (the learner self-grades), so the answer request carries `known` instead of `optionId` and the server stores the correct option as `selected_option` with `is_correct = known`. Stats and progress exclude answers from in-progress exams for the same reason; accuracy stats (`StatsService`) also exclude FLASHCARD answers. Keep these invariants when touching `TestService`/`TestDtos`/`StatsService`/`ProgressService`.

**Learning modes:** FLASHCARD (self-graded cards) and BLITZ (60 s timer via `time_limit_seconds` + the same lazy-expiry machinery as block tests; on finish unanswered questions are deleted and `question_count` shrinks; retry-wrong of a blitz becomes LEARNING). In LEARNING/FLASHCARD questions are ordered easy → hard. `QuestionPicker.pick` takes a priority function: within a topic, unseen and due-for-review questions come first; outside EXAM the difficulty also follows the topic's mastery level (NEW → EASY, LEARNING → MEDIUM, MASTERED → HARD). Topic balance is unchanged.

**Progress (`progress/`) has no tables — everything is replayed from `session_questions`:** spaced repetition (`Leitner`: wrong → due now, correct → next box with 1/3/7/14/30-day intervals), topic mastery (last 10 answers, ≥8 answered and ≥80% → MASTERED), XP (correct 1, wrong 0, flashcard 1, a question earns XP at most once per 24 h; finished test with ≥5 questions +5, 100% with ≥10 questions +10 more; per-session XP is returned as `ResultView.xp`, the leaderboard replays each active user's full history so the 24 h rule matches), daily activity by the client's `tz`, mistakes notebook (latest answer wrong), weekly leaderboard (web only). `POST /api/tests/review` builds a test from due (`DUE`) or mistaken (`MISTAKES`) questions. Clearing history resets progress. Levels, day streak, daily goal and badges are computed client-side in the shared `frontend/src/utils/gamification.ts`. The rules are duplicated in `mobile/src/api/local/progress.ts` + `service.ts` — change both.

**Block test (`TestMode.BLOCK`, DTM-style):** works on **subject groups** (`subject_groups`, e.g. "1-sinf Matematika" + "2-sinf Matematika" → "Matematika"), not subjects; learning/exam modes still use individual subjects. A subject's group defaults to its name without the `N-sinf` prefix (`SubjectGroupService.defaultGroupName`, same regex in V4 migration and mobile `defaultGroupName`). `score_weight` lives on the group. A block session has `subject_id = null`; `test_session_groups` (`SessionGroup`) holds per-group question count + weight snapshot, `test_session_subjects` (`SessionSubject`) holds the chosen grades with their group snapshot. Question count per group is split evenly across chosen grades (`QuestionPicker.allocate`), then by topic (`pick`). Questions are ordered in per-group sections (group order, shuffled within a section), not mixed; `QuestionView.groupId/groupName` drive the section tabs and sectioned nav in both test views. Seed files with `scoreWeight` are loaded first so a group gets its weight regardless of file order. Questions from all subjects are shuffled together; reveal rules are the same as EXAM. Score = Σ correct × weight, stored on finish (`score`, `max_score`; `score_percent` = score/max). Code that reads `session.subject` must handle null — use the `subjectId()`/`subjectName()` helpers in `TestService`. Time limit is 1 hour per group (`time_limit_seconds`, `BLOCK_SECONDS_PER_SUBJECT`), enforced server-side: expired sessions are lazily finished (with `finished_at = deadline`) on get/answer/result/history/stats via `finishExpired()`/`isExpired()`; the mobile local service mirrors this. Default weights (biologiya 3.1, matematika 1.1) come from `V2__block_exam.sql` for existing DBs and from `scoreWeight` in seed JSON for fresh ones. On mobile there is no groups table: group id = smallest subject id in the group, weight = max seed weight in the group.

**Soft delete everywhere:** subjects, topics, questions and options are deactivated, never deleted. Editing a question deactivates removed options so historical results remain valid. "Retry wrong" builds a new test from wrong + unanswered questions, excluding inactive ones. Unanswered counts as wrong in scoring but is shown separately.

**Backend conventions:** DTOs are Java `record`s grouped in a per-feature `*Dtos.java` with small `from()` mappers (no MapStruct). Errors are thrown as `ApiException` and rendered by `GlobalExceptionHandler` in a uniform `{timestamp, status, message, errors[]}` shape. Admin endpoints live under `/api/admin/**` (role-protected in `SecurityConfig`). Testcontainers is pinned to 1.21.4 deliberately (works with Docker 29).

**Mobile shares code with the web frontend via Vite aliases, but has its own UI.** `mobile/vite.config.ts` maps `@/` to `../frontend/src` (so `types`, `utils`, `stores/theme` are shared) and swaps the server-bound modules: `@/api` → `mobile/src/api/index.ts`, `@/api/http` → `mobile/src/api/errors.ts`, `@/stores/auth` → `mobile/src/stores/auth.ts`. `@m/` refers to mobile's own `src`. Consequences:
- **Views are NOT shared:** mobile has its own router and pages in `mobile/src/views/` (HomeView, SubjectView, TestView, ResultView, …). A UI change in `frontend/src/views` must be ported to the matching mobile view separately.
- Changes to shared `frontend/src` modules (`types`, `utils`, the `api` interface) affect the mobile build — keep the mobile `api/index.ts` signature in sync with `frontend/src/api/index.ts`. Global CSS is separate too (`frontend/src/styles.css` vs `mobile/src/theme.css`).
- `mobile/src/api/local/` is a TypeScript port of the backend logic (picker, learning/exam modes, results, retry-wrong, history, stats) over IndexedDB (Dexie). Behavior changes in the backend `test`/`stats` services usually need a matching change here.
- Mobile bundles subjects directly from `backend/src/main/resources/seed/*.json` via `import.meta.glob`; adding a seed file and rebuilding the APK ships it.

`mobile/keystore/` (signing key) is not in git and must not be lost — without it new APKs can't upgrade existing installs. `books/` holds copyrighted textbook PDFs used to author seed content; it's gitignored.
