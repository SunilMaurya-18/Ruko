# Ruko (रुको)

Share a chat investment tip, hear the red flags in Hindi, pause, and either verify on SEBI Check or follow the right recovery path. Independent prototype for the SANGYAN Investor Resilience Hackathon. Not an official SEBI or NSDL product. No investment advice.

Spec: [Final TRD](./Ruko%20—%20Final%20TRD.md) · [Implementation plan](./Ruko%20—%20Implementation%20Plan.md) · [Persona and demo journey](./docs/persona.md)

## Layout

| Path | What |
| --- | --- |
| `server/` | Spring Boot modular monolith, package root `in.ruko` (stateless analysis, voice, content API) |
| `pwa/` | Vite + React PWA: share target, on-device OCR, masking, journal, recovery, on-device rules |
| `shared/` | Single source of truth for both sides: `rules/`, `i18n/`, `schemas/`, `snapshot/`, `fixtures/` |
| `docs/` | Persona, demo journey, study material |

The server build copies `shared/{rules,i18n,schemas,snapshot}` onto the main classpath and `shared/fixtures` onto the test classpath.

### Rules

- `shared/rules/signals.v0.json` is the signal catalogue, validated against `shared/schemas/signals.v0.json`. The server refuses to start on any error: unknown type, missing i18n key, bad or non-portable regex, duplicate id, unknown set or marker, or a detector this build doesn't have (S10 stays disabled until an RDAP detector exists).
- Reasons and spoken lines live in `shared/i18n/messages_{hi,en}.properties`; the rule file only holds keys.
- `shared/snapshot/sebi-intermediaries.json` ships empty. With no date or no numbers, U14 is reported without a snapshot status and S19 never fires. Nothing is ever fetched at runtime.
- **To do before the demo:** confirm the S5 registration-prefix map (`INH`, `INA`, `INZ`, `INP`) against a SEBI page and fill in its `source.url` / `source.checked`.

## Run

```sh
cd server && ./mvnw verify            # tests, ArchUnit, log audit (Windows: mvnw.cmd)
cd server && ./mvnw spring-boot:run   # API on :8081 (override with PORT)
cd pwa && npm ci && npm run dev       # PWA on :5173, proxies /api to :8081 (or PORT)
cd pwa && npm test                      # masker parity with the server + regex portability
cd pwa && npm run build && npm run check:size
```

The server boots with no environment variables. Optional: `PORT` (default 8081; 8080 is often taken by Oracle XE), `LLM_ENABLED`, `LLM_URL`, `LLM_KEY`, `LLM_MODEL`, `BHASHINI_ENABLED`, `BHASHINI_USER`, `BHASHINI_KEY`, `RATE_LIMIT` (default 30 per IP per minute). Secrets come from the environment only.

## Pinned versions

| Component | Version |
| --- | --- |
| Java (target / CI) | 21 (Temurin) |
| Spring Boot | 3.5.16 (manages Spring Framework, Tomcat, Jackson, Micrometer, and Maven plugin versions) |
| Maven (wrapper) | 3.9.16, wrapper 3.3.4 |
| ArchUnit | 1.5.1 |
| JSON-schema validator (networknt) | 1.5.9 |
| Resilience4j | 2.4.0 (pinned in `pom.xml`, wired in Phase 3) |
| Node | 24 LTS (`pwa/.nvmrc`) |
| Vite | 8.3.2 |
| @vitejs/plugin-react | 6.1.1 |
| React / React DOM | 19.3.0 |
| Tesseract.js | 7.0.0 (core 7.0.0, `hin`/`eng` best_int data), self-hosted under `/ocr/` |

## Privacy guardrails in place

- All server logging goes through `infra/SafeLog`, which accepts only events, enums, numbers, durations, catalogue ids, and exception types. `ArchitectureTest` forbids SLF4J, JUL, Commons Logging, and `System.out`/`err` anywhere else.
- Errors are RFC 9457 `application/problem+json` with fixed `type`, `title`, and `detail`. `ErrorEchoTest` sends a canary in the path, query, headers, method, and body and asserts it never comes back. `NoContentLoggingTest` asserts it never reaches the logs.
- No access log, no request-detail logging, actuator exposes `health` only, security headers on every response, per-IP rate limit on `/api/**`.
- The service worker answers every navigation from the cached shell, so a share-target URL (which carries the message) never hits the network.
