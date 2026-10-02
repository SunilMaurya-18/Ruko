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
- Reasons, spoken lines, and cards (at most 25 words) live in `shared/i18n/messages_{hi,en}.properties`, along with analogies (at most 40 words), band, class, and footer labels, and the "How Ruko decides" text. The rule file only holds keys. The PWA bundles the same files.
- `shared/snapshot/sebi-intermediaries.json` ships empty. With no date or no numbers, U14 is reported without a snapshot status and S19 never fires. Nothing is ever fetched at runtime.
- **To do before the demo:** confirm the S5 registration-prefix map (`INH`, `INA`, `INZ`, `INP`) against a SEBI page and fill in its `source.url` / `source.checked`.

### Explain and linter

- Every `/analyze` response is built from catalogue keys (`TemplateExplainer`) and checked by `GuardrailLinter` (L1–L8, TRD §3) before it leaves the server. On any violation it falls back to the pure template draft, then to the fixed `FallbackTemplates.generic` text. Band and class always come from the rules.
- The LLM is optional and off by default (`LLM_ENABLED=false`). When `LLM_ENABLED=true` and `LLM_URL`, `LLM_KEY`, `LLM_MODEL` are set, one call per request goes to an OpenAI-compatible `/chat/completions` endpoint (temperature 0, 3 s timeout, circuit breaker). The reply must match `shared/schemas/llm-assist.v1.json`. It may add C1, S9, M12, or R2 tags with spans copied verbatim from the input, plus card text. A model-added R2 never makes a message "education".
- L2 reads ticker symbols from `shared/snapshot/nse-bse-symbols.json`. **It currently holds a hand-typed NIFTY 50 seed list with no date.** Before the demo, download the NSE `EQUITY_L.csv` (and the BSE equity list) in a browser and run `node scripts/import-symbols.mjs <yyyy-mm-dd> EQUITY_L.csv [bse.csv]`, then re-run `mvnw verify` to check that no template trips L2.
- Metrics (no content in tags): `ruko_analyze_total{band}`, `ruko_lint_fail_total{code}`, `ruko_llm_fallback_total`, `ruko_tts_fallback_total`, and the `ruko_analyze_latency` / `ruko_llm_latency` / `ruko_tts_latency` timers.
- `GET /api/v1/content/how-ruko-decides?lang=hi|en` lists the signals that can fire, with plain reasons, the bands, the limits, and the snapshot date. No patterns, terms, or rate limits.

### Voice

- **Listen** on the result screen speaks band (with counts), class, up to three flags, the unverified number, the analogy, the action line, and the footer. Each part is a catalogue key. The PWA asks the server for each part. On any failure, or offline, it speaks the rest with the phone's own `speechSynthesis` (`hi-IN` / `en-IN`), and skips the server for a minute after a failure.
- `POST /api/v1/voice/tts {script_key, lang, counts}` only accepts keys from `VoiceScripts`' allowlist; any other key, or any extra field such as `text`, is a 400. The server builds the words from the properties files, so Bhashini only ever gets catalogue text, and `TtsCache` (in memory, LRU) can safely keep the audio. `shared/fixtures/voice-scripts.v0.json` checks that the server and the PWA build the same text.
- Bhashini is called only when `BHASHINI_USER` and `BHASHINI_KEY` are set. Each call makes a ULCA config request (cached for 30 minutes), then an inference request, within a 3 s total budget and behind a circuit breaker. Without credentials, or on any failure, the server returns 503 `{"fallback": "browser_tts"}` and counts `ruko_tts_fallback_total`. **The Bhashini request format follows the public ULCA docs; it has only been tested against a local stub, not with real credentials.** `BHASHINI_CONFIG_URL` and `BHASHINI_PIPELINE_ID` override the defaults.
- `POST /api/v1/voice/asr` (multipart `audio`, `?lang=`) is off (`ruko.features.asr=false`), and returns 404 while off. When on:
  - It needs `X-Consent: voice-asr-v1`, checked before the upload is read.
  - The upload must be at most 2 MB.
  - The audio stays in memory: no multipart temp files.
  - ffmpeg converts it to 16 kHz WAV over stdin and stdout (at most 60 s; pipe-only protocols; stderr discarded). Set `FFMPEG_PATH` if ffmpeg is not on the PATH.
  - The audio is not kept. The PWA has no microphone UI yet.

### Recovery, journal, and on-device engine

- **Recovery** (`/recovery`, linked from Home and Result) has two paths from `shared/content/recovery.v0.json`: money already sent (call 1930, then the bank, then cybercrime.gov.in) and broker or adviser problems (SCORES). Every link is an id in `shared/content/links.v0.json`. The server refuses to start if a link's host is not on `ruko.links.allow` or a key is missing. `GET /api/v1/content/recovery?lang=` and `GET /api/v1/content/links?lang=` serve the same content.
- **Complaint draft:** `POST /api/v1/complaint/draft` (on by default; `ruko.features.complaint=false` makes it 404) takes structured facts only: `lang`, `date` (2000-01-01 to today in India), `amount` (1 to 1,000,000,000), `channel`, `platform`, and `payee_id_type`. Any free-text field is a 400. The reply is the `complaint.draft` catalogue template with those facts filled in: at most 200 words, lint-clean (L1, L3, L5). It has `[brackets]` where the user types their own details. The PWA builds the same text locally (`shared/fixtures/complaint-drafts.v0.json` keeps both sides in sync). It asks the server only when online, and the user can edit, copy, or share the draft.
- **Journal** (`/journal`, offered on Result for high or some concern) has three questions, a "wait 24 hours" timestamp, and an optional reminder notification. It is stored in `localStorage` (`ruko.journal.v1`) only. There is no journal API route (`NoJournalRouteTest` pins the whole API surface), and `pwa/e2e/journal-network.test.js` drives a real browser to check that no request carries journal text. The reminder fires only while the PWA is open or when it comes back to the foreground. There is no push server, so a closed app cannot remind.
- **On-device engine** (`pwa/src/engine/`) is a JS port of the server pipeline: normaliser, masker, extractor, rules, bands, class, analogy, and template cards. It uses the same rule file, catalogue, and shipped snapshot. When the phone is offline, or `/analyze` fails (network, 5xx, or rate limit), the result is computed in the page with `engine: "on_device"`, and the Result screen says so. **This differs from the TRD, which puts the fallback in the service worker.** It runs in the page through a lazily loaded chunk (about 12 KB gzipped) that the service worker precaches, so it also works offline. A local counter (`localStorage` `ruko.on_device_total`) is kept for a later `ruko_on_device_total` report. Nothing is sent yet.
- **Parity:** `OnDeviceParityTest` writes the server's full responses for every fixture plus the edge cases in `shared/fixtures/engine-probes.v0.json` to `shared/fixtures/engine-golden.v0.json`. `pwa/src/engine/parity.test.js` requires the JS engine to produce the same output, deep-equal except for `engine`. After changing rules, catalogue text, or fixtures, regenerate the shared files with `./mvnw verify -Druko.updateGolden=true` and commit them.

## Run

```sh
cd server && ./mvnw verify            # tests, ArchUnit, log audit, linter gate (Windows: mvnw.cmd)
cd server && ./mvnw spring-boot:run   # API on :8081 (override with PORT)
cd pwa && npm ci && npm run dev       # PWA on :5173, proxies /api to :8081 (or PORT)
cd pwa && npm test                      # masker and on-device engine parity, recovery, journal, catalogue coverage
cd pwa && npm run build && npm run check:size
cd pwa && npm run test:e2e              # after build: journal network test in headless Edge/Chrome
```

`test:e2e` uses an installed browser through `playwright-core`; no browser is downloaded. Set `PW_CHANNEL` to `msedge` (the default on Windows) or `chrome` (the default elsewhere).

Current counts: 1026 server tests, 201 PWA unit tests, and 1 browser test. The shell bundle is 119.2 KB gzipped against the 200 KB budget.

The server boots with no environment variables. Optional: `PORT` (default 8081; 8080 is often taken by Oracle XE), `LLM_ENABLED`, `LLM_URL`, `LLM_KEY`, `LLM_MODEL`, `BHASHINI_ENABLED`, `BHASHINI_USER`, `BHASHINI_KEY`, `BHASHINI_CONFIG_URL`, `BHASHINI_PIPELINE_ID`, `FFMPEG_PATH` (ASR only), `RATE_LIMIT` (default 30 per IP per minute). Secrets come from the environment only. The voice tests that need ffmpeg skip themselves when it is missing.

## Pinned versions

| Component | Version |
| --- | --- |
| Java (target / CI) | 21 (Temurin) |
| Spring Boot | 3.5.16 (manages Spring Framework, Tomcat, Jackson, Micrometer, and Maven plugin versions) |
| Maven (wrapper) | 3.9.16, wrapper 3.3.4 |
| ArchUnit | 1.5.1 |
| JSON-schema validator (networknt) | 1.5.9 |
| Resilience4j | 2.4.0 (`resilience4j-circuitbreaker`, around the LLM and Bhashini calls) |
| ffmpeg (ASR only, optional) | any recent build on the PATH; tested with 9.0.2 |
| Node | 24 LTS (`pwa/.nvmrc`) |
| Vite | 8.3.2 |
| @vitejs/plugin-react | 6.1.1 |
| React / React DOM | 19.3.0 |
| Tesseract.js | 7.0.0 (core 7.0.0, `hin`/`eng` best_int data), self-hosted under `/ocr/` |
| playwright-core (dev, e2e only) | 1.63.0 |

## Privacy guardrails in place

- All server logging goes through `infra/SafeLog`, which accepts only events, enums, numbers, durations, catalogue ids, and exception types. `ArchitectureTest` forbids SLF4J, JUL, Commons Logging, and `System.out`/`err` anywhere else.
- Errors are RFC 9457 `application/problem+json` with fixed `type`, `title`, and `detail`. `ErrorEchoTest` sends a canary in the path, query, headers, method, and body and asserts it never comes back. `NoContentLoggingTest` asserts it never reaches the logs.
- No access log, no request-detail logging, actuator exposes `health` only, security headers on every response, per-IP rate limit on `/api/**`. Filters match on the container's normalised servlet path, so `/api;x/...` cannot skip them.
- Request JSON is strict (`fail-on-unknown-properties`): a field the API does not define is a 400.
- The service worker answers every navigation from the cached shell, so a share-target URL (which carries the message) never hits the network.
