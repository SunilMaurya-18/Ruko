# Ruko (रुको) — Phase-by-Phase Implementation Plan

*Derived from "Ruko — Final TRD" v1.0 (1 Oct 2026). Four build days, eight phases (0–7). Each phase lists goal, tasks by area, tests, exit gate, and dependencies. TRD section references are given as §n.*

---

## How to read this plan

- **Tracks** (from §0): A Fraud, B Rights, C Education, D Habits, E Misinformation, Open (accessibility/voice/offline).
- **Areas:** `BE` = Spring Boot backend (`in.ruko`), `PWA` = Vite + React client, `DATA` = JSON/properties/fixtures, `OPS` = CI, deploy, research.
- **Gate rule:** a phase is done only when its exit gate passes in CI, not just locally.
- **Depth rule (§0):** anything that needs a second persona or second demo is rejected during the build.

### Day map

| Day | Phases | Theme |
| --- | --- | --- |
| 1 | 0 Foundation, 1 Intake and extraction | Skeleton, guardrails-first infra, text in, entities out |
| 2 | 2 Rules/band/snapshot, 3 Explain/linter, 4 Voice | The deterministic core and what the user hears |
| 3 | 5 Recovery/journal/on-device, 6 Study and eval | Client tracks, offline parity, evidence |
| 4 | 7 Deploy and submit | Ship, record, pack |

### Workstreams (if 2–3 people)

| Stream | Owns |
| --- | --- |
| Backend | `pipeline`, `extract`, `rules`, `explain`, `guardrail`, `snapshot`, `voice`, `infra` |
| Client | PWA shell, share target, OCR, masking, result screen, journal, recovery, on-device engine |
| Content and research | Fixtures, Hindi copy (Class 6 level), analogies, prefix map, study protocol, deck |

---

## Pre-flight (Day 1, first hour): resolve open decisions (§10)

Each decision has a default so the build is never blocked.

| # | Decision | Default if unresolved by end of Day 1 |
| --- | --- | --- |
| 1 | LLM provider with strict JSON mode | Build against `LlmPort`; ship with `ruko.llm.enabled=false` |
| 2 | Bhashini credentials and rate limits | Apply on Day 1; until approved, `VoicePort` returns 503 and PWA uses `speechSynthesis` |
| 3 | NSE/BSE symbol file date; prefix map vs SEBI page | Download dated symbol CSVs Day 1; record source URL and date in `resources/` |
| 4 | Public SEBI registration-number file | Ship empty `sebi-intermediaries.json` and the "no public list, deep link only" line |
| 5 | RDAP for S10 | Off (`s10-domain-age: false`); first item in cut order |
| 6 | Host free-tier limits | Pick host that fits a 512 MB JRE 21 container; plan keep-alive ping |
| 7 | Study participants (8–10 non-metro adults) | Recruit Day 1; publish actual n whatever it is |

**Pin versions on Day 1** (Java 21, Spring Boot 3.5.x patch, Maven plugins, Node LTS, Vite, React, Tesseract.js, resilience library, ArchUnit, JSON-schema validator) and record them in the README.

---

## Phase 0 — Foundation (Day 1)

**Goal:** an empty but correctly-shaped system where privacy and error guardrails exist before any feature code.

### BE
- [ ] Maven project, Java 21, Spring Boot 3.5.x, package root `in.ruko` with the full §1 package layout (empty classes are fine).
- [ ] `application.yml` exactly as §6, plus sane defaults for every `${...}` placeholder (e.g. `${RATE_LIMIT:30}`) so a missing env var does not break boot.
- [ ] Typed `@ConfigurationProperties` records: `AnalyzeProps`, `LlmProps`, `BhashiniProps`, `LinksProps`, `RateLimitProps`, `FeatureFlags`.
- [ ] Virtual threads on; Jackson `SNAKE_CASE`.
- [ ] `infra/SafeLog`: the only logging facade. Accepts enums, ids, numbers, durations only — no `String` free-text parameter.
- [ ] ArchUnit rule: no `org.slf4j` / `java.util.logging` / `System.out` outside `SafeLog`.
- [ ] `api/error`: global `@ControllerAdvice` returning RFC 9457 `ProblemDetail` with fixed `type`/`title`/`detail` strings that never echo request content.
- [ ] `infra/SecurityHeadersFilter` (CSP, `X-Content-Type-Options`, `Referrer-Policy: no-referrer`, HSTS, frame-deny).
- [ ] `infra/RateLimitFilter` (per-IP per minute, in-memory bucket; returns RFC 9457 429).
- [ ] Disable request/access logging of bodies; Tomcat access log off or path-only.
- [ ] Actuator: `health` only exposed; Micrometer registry wired (`MetricsConfig`).

### PWA
- [ ] Vite + React shell, service worker, web app manifest with `share_target` (GET or POST text/url/title).
- [ ] Routes: Home, Result, Recovery, Journal, How Ruko decides.
- [ ] Bundle budget check in CI: shell ≤200 KB gzipped.
- [ ] Base accessibility: large touch targets (≥48 dp), semantic landmarks, `lang` attribute per screen.

### DATA / OPS
- [ ] Repo layout: `/server`, `/pwa`, `/shared` (rules JSON, i18n, schemas, fixtures — single source of truth for both sides).
- [ ] CI pipeline (GitHub Actions or equivalent): `mvn verify`, PWA build + bundle-size check, ArchUnit.
- [ ] Persona doc (Ramesh) and the single demo journey written down.
- [ ] Start fixture collection: target ≥40 messages by end of Day 1 (Hindi, English, Hinglish; scam, education, ambiguous), each with expected band, class, signal ids.
- [ ] Submit Bhashini application.

### Tests
- `ErrorEchoTest`: malformed/oversized inputs containing a canary string; assert canary absent from response body and headers.
- `NoContentLoggingTest` scaffold: capture all log output while posting fixtures (no fixtures yet beyond a canary), assert canary never appears.
- `ArchitectureTest` (ArchUnit) for the logging rule.

### Exit gate
CI green; log-audit test exists; RFC 9457 errors that do not echo input.

---

## Phase 1 — Intake and extraction (Day 1)

**Goal:** text gets from WhatsApp/Telegram into the app and comes out normalised, masked, and with entities extracted.

### BE — `pipeline/`, `extract/`
- [ ] `api/dto/AnalyzeRequest` `{text, lang, source, ocr_confidence?}` with bean validation; `source ∈ share|paste|ocr|asr|video|audio`, `lang ∈ hi|en`.
- [ ] `InputGuard`: ≤4000 chars (`max-chars`), valid UTF-8, reject binary/NUL/high control-char ratio.
- [ ] `TextNormalizer`: NFKC → strip zero-width and control chars → collapse dotted/spaced obfuscation (`a.n.y.d.e.s.k`, `g u a r a n t e e d`) → Devanagari digits to ASCII → case-fold. Keep an offset map if evidence spans must be shown against the original.
- [ ] `PiiMasker`: phone → `[PHONE]`, 9–18 digit account → `[ACCT]`, 12-digit Aadhaar-like → `[AADHAAR]`, PAN → `[PAN]`, OTP after keyword → `[OTP]`. Order matters: Aadhaar before account, so 12-digit numbers are classified correctly.
- [ ] `AnalysisContext` record: normalised text, masked text, entities, lang, source, `unreadable` flag (under `min-chars`, low `ocr_confidence`, or <50% letters).
- [ ] `EntityExtractor` + `extract/patterns/`: registration `\bIN[A-Z]\d{9}\b`, UPI ids, IFSC, URLs, phones, return claims (`x% per day/month`, `double in`), app names, QR/scan-to-pay phrases.
- [ ] Keep all regexes in the JS-compatible subset (no possessive quantifiers, no lookbehind variants that differ) — they will be ported to the PWA in Phase 5.
- [ ] Stub `POST /api/v1/analyze` that returns entities and an empty `signals` list in the final `AnalyzeResponse` shape (§5).

### PWA
- [ ] Share-target handler: receive shared text, prefill editable transcript.
- [ ] Paste input path.
- [ ] Image path: Tesseract.js (`hin` + `eng`) on-device, editable transcript, expose OCR confidence. Images never leave the device.
- [ ] Client-side PII masker (same rules as server) — only masked text is sent; pre-mask text kept in memory for "copy UPI/account for SEBI Check".
- [ ] Call `/analyze`, render raw entities on a debug result screen.

### DATA
- [ ] Fixture format: `{id, text, lang, source, expected: {entities, signals, band, content_class}}` in `/shared/fixtures/`.
- [ ] Label entities on all Day-1 fixtures.

### Tests
- `TextNormalizerTest` (obfuscation, zero-width, Devanagari digits).
- `PiiMaskerTest` (each PII type, overlaps, false-positive guard on registration numbers and amounts).
- `EntityExtractionFixtureTest`: precision/recall per entity type over fixtures.
- `InputGuardTest` (4000/4001 chars, invalid UTF-8, binary).

### Exit gate
≥95% entity extraction on fixtures; WhatsApp share shows text in the PWA.

---

## Phase 2 — Rules, content class, band, snapshot (Day 2)

**Goal:** the deterministic core. Rules alone decide the band.

### BE — `rules/`, `snapshot/`
- [ ] `/shared/rules/signals.v0.json` with every catalogue row from §2: C1, C2, C3, C4, C15, C16, C17, S5, S6, S7, S8, S9, S10 (`enabled: false`), M11, M12, M13, U14, R1, R2, **plus S19** (snapshot-absent, strong; only emitted by snapshot lookup).
- [ ] JSON schema `schemas/signals.v0.json`; `RuleLoader` validates at startup and **aborts boot** on any error (unknown type, missing i18n key, bad regex, duplicate id).
- [ ] `Severity` enum: `CRITICAL, STRONG, MODERATE, UNVERIFIED, REASSURANCE`. Only the first three count toward the band.
- [ ] `SignalRule` implementations per type:
  - `TERMS` — term list matching on normalised text (C3, C15, S6, M11).
  - `REGEX` — compiled patterns (C4, S8, S9 pattern part).
  - `PREFIX_MAP` — registration prefix vs claimed role (S5): INH research analyst, INA investment adviser, INZ stock broker, INP portfolio manager (confirm against a SEBI page and record source/date).
  - `ENTITY` — rules over extracted entities with same-sentence constraints (C2: UPI/account + pay verb, handle not `@valid`; C16: fee/tax/penalty phrase + amount or pay verb; C17: QR/scan + invest/fee/membership; S7: VIP/premium keyword + amount; R1: `@valid` handle; U14: any registration number).
  - `LLM_TAG` — placeholders for C1, S9, M12, R2 (filled in Phase 3); C1 and R2 also have rule-based detection.
- [ ] `SignalEngine.evaluate(ctx)` → `List<SignalHit>`; every hit carries a verbatim evidence span from the input.
- [ ] M13: name list **or** `source ∈ video|audio`; fixed card "face and voice were not checked".
- [ ] `BandCalculator.band(hits, unreadable)` exactly as §2 (distinct by id, counts not percentages). R1/R2 never lower the band. `footer_key` always `no_flags_not_safe`.
- [ ] `ContentClassifier.classify(ctx, hits)`: `promotion` / `education` / `mixed` / `unknown` per §2 rules; never decided by LLM alone.
- [ ] Analogy key selection: highest-severity hit with an `analogyKey`; none if missing.
- [ ] `SebiSnapshotIndex`: loads `snapshot/sebi-intermediaries.json` at startup (registration numbers + `snapshot_date` only).
  - Found → U14 `snapshot: "listed"`, band unchanged.
  - Not found and file non-empty → U14 `snapshot: "not_listed"` + S19 "absent from snapshot dated {date}".
  - Empty file / no date → U14 only, no S19, no "real-time" wording.
  - Never fetch the suspect URL.
- [ ] Wire `/analyze` end-to-end with rules: response has signals, unverified, reassuring, band, content_class, counts, analogy_key, footer_key, `engine: "template"` (cards may be placeholders until Phase 3).

### PWA
- [ ] Result screen v1: band (plain Hindi, no percentage), counts, flags with evidence, content class in plain Hindi (no true/false), "Verify on SEBI Check" action for U14, footer.

### Tests
- `BandCalculatorTest` (table-driven: all severity combinations, duplicates, unreadable, R1/R2 present).
- `ContentClassifierTest`.
- Per-rule tests; `EverySignalHasSpanTest` (span ⊂ input for every fixture hit).
- `RuleLoaderTest`: bad JSON / unknown key / bad regex aborts.
- `SnapshotTest`: listed, not listed, **empty file**, missing date.
- `OnDeviceParityTest` **scaffold**: shared fixture runner that both engines will use; server side passes now, client side marked pending.

### Exit gate
Every flag has a span; parity test scaffolded; snapshot empty-file path tested.

---

## Phase 3 — Explain, analogy, linter (Day 2)

**Goal:** turn hits into short, safe, spoken-ready cards. Templates are the product; the LLM is optional garnish.

### BE — `explain/`, `guardrail/`, `content/I18nBundle`
- [ ] `i18n/messages_{hi,en}.properties`: `sig.*.reason`, `sig.*.spoken`, band labels, class labels, footer, analogy texts (≤40 words), card texts (≤25 words). Hindi reviewed to ~Class 6.
- [ ] `AnalogyCatalog`: guaranteed return, personal UPI, remote access, fee to unlock profit, F&O/leverage (only if those words appear).
- [ ] `ExplainerPort` + `TemplateExplainer`: builds cards purely from catalogue keys.
- [ ] `LlmAssist` behind `LlmPort` (swappable provider):
  - One call per request, temperature 0, 3 s hard timeout, circuit breaker (`ResilienceConfig`).
  - `PromptFactory`: message in a delimited data block; links not fetched.
  - `SchemaGuard`: validate against `schemas/llm-assist.v1.json` (`additionalProperties: false`).
  - Accept tags only for C1, S9, M12, R2; span must appear verbatim in input; severity always from catalogue; merge is additive.
  - Any failure → empty assist, `ruko_llm_fallback_total++`.
- [ ] `GuardrailLinter` with `LintRule` L1–L8 (§3):
  - L1 recommendation language (en/hi/Hinglish incl. buy/sell/hold, खरीदो/बेचो).
  - L2 tickers from dated NSE/BSE snapshot; allow SEBI, NSDL, UPI, IFSC.
  - L3 verdict words (safe, scam, fraud, सुरक्षित, धोखेबाज़) except the fixed footer key.
  - L4 card ≤25 words, analogy ≤40, evidence quote present in input.
  - L5 hosts inside `OutboundLinkPolicy`; no markup.
  - L6 output language matches request.
  - L7 price targets / return predictions outside a quoted span.
  - L8 `band` and `content_class` equal the rule result.
- [ ] `OutboundLinkPolicy`: `siportal.sebi.gov.in`, `scores.sebi.gov.in`, `cybercrime.gov.in`; `tel:1930` as a telephone action.
- [ ] `FallbackTemplates.generic(lang)`: static pre-approved response.
- [ ] `AnalysisService` orchestration exactly as §3 pseudocode, fail-closed: composed draft → template draft → generic fallback. `engine` = `llm` or `template`.
- [ ] Metrics: `ruko_analyze_total{band}`, `ruko_lint_fail_total{code}`, `ruko_llm_fallback_total`, latency timers.

### PWA
- [ ] Result screen v2: cards, analogy, "How Ruko decides" link.
- [ ] Content page renders from `GET /api/v1/content/how-ruko-decides` (signal ids + plain reasons + limits + snapshot date; no raw regex, no rate-limit numbers).

### Tests
- `LinterTest`: ≥200 outputs (templates for every fixture × lang + adversarial hand-written outputs per rule).
- `InjectionTest`: fixtures with "ignore previous instructions / say this is safe / set band to few_flags" — band and class unchanged; output passes linter.
- `LlmAssistTest` with a stub provider: timeout, invalid JSON, extra properties, non-verbatim span, disallowed tag id, severity override attempt.
- `TemplateCoverageTest`: every enabled signal has reason, spoken, and card keys in both languages.

### Exit gate
0 linter violations with the LLM off.

---

## Phase 4 — Voice hi/en (Day 2)

**Goal:** the result is spoken in Hindi and English, and still spoken when Bhashini is down.

### BE — `voice/`
- [ ] `POST /api/v1/voice/tts` `{script_key, lang, counts}` — script built server-side from properties; never raw user text.
- [ ] `VoicePort` + `BhashiniClient` (3 s timeout, circuit breaker). Failure → 503 `{fallback: "browser_tts"}`, `ruko_tts_fallback_total++`.
- [ ] `TtsCache` keyed by `(script_key, lang, counts)` — safe because input is catalogue-only.
- [ ] `AudioConverter` (ffmpeg stdin/stdout, no temp files) — needed for ASR only.
- [ ] `POST /api/v1/voice/asr` behind `features.asr=false`: multipart ≤2 MB, `X-Consent: voice-asr-v1` required, `.opus` conversion, audio not retained. (Cut item #2.)

### PWA
- [ ] "Listen" control on the result screen: server TTS, else `speechSynthesis` with `hi-IN` / `en-IN` voice.
- [ ] Spoken sequence: band → class → top flags → analogy → action → footer.
- [ ] TalkBack/screen-reader labels on all result-screen controls.

### Tests
- `TtsContractTest`: request with arbitrary text field is rejected; only known keys accepted.
- `VoiceFallbackTest`: Bhashini stub down/slow → 503 with fallback body within ~3 s.
- `AsrGuardTest`: flag off → 404/403; missing consent → 400; >2 MB → 413.

### Exit gate
Spoken result with Bhashini down.

---

## Phase 5 — Recovery, journal, on-device engine (Day 3)

**Goal:** Tracks B and D on the client, and a fully offline result that matches the server.

### BE — `content/`, `api/ComplaintController`
- [ ] `GET /api/v1/content/recovery?lang=` → `{cyber_fraud: [...], scores: [...]}`.
- [ ] `GET /api/v1/content/links` → allowlisted links.
- [ ] `POST /api/v1/complaint/draft` (`features.complaint=true`): structured facts only (date, amount, channel, payee id type, platform), no free-form essay; returns ≤200-word template draft; output passes linter L1/L3/L5. (Cut item #5.)

### PWA — Recovery (Track B)
- [ ] Reachable from Home and Result.
- [ ] "Money already sent": tap-to-call `tel:1930` → contact your bank → cybercrime.gov.in.
- [ ] "Problem with a registered broker or DP": plain-Hindi SCORES steps + `https://scores.sebi.gov.in`.
- [ ] Client complaint template from on-device fields, ≤200 words, always editable, copy/share; server route used only when online.

### PWA — Pause journal (Track D)
- [ ] Shown after `high_concern` or `some_concern`.
- [ ] Three fields: why send money, time horizon, can you afford to lose it — `localStorage` only.
- [ ] "Wait 24 hours" stores local timestamp; optional local notification if permission granted.
- [ ] No journal API route exists (assert in test).

### PWA — On-device engine (Open track)
- [ ] Generate from `/shared` in CI: rules JSON, i18n template bundle, analogy catalogue, prefix map → TypeScript modules.
- [ ] TypeScript port of `TextNormalizer`, `EntityExtractor`, `SignalEngine`, `BandCalculator`, `ContentClassifier`, template composer. (Logic is hand-ported once; data is generated, so rules can't drift.)
- [ ] Service worker: if `/analyze` fails or offline, run the on-device pipeline on already-masked text, return same schema with `engine: "on_device"`; increment a local counter reported later as `ruko_on_device_total` (count only).
- [ ] Snapshot on device: ship the same `sebi-intermediaries.json` if small; otherwise U14 only.

### Tests
- `OnDeviceParityTest` (full): every fixture through server and TS engine → identical band, content class, signal id set.
- `JournalNetworkTest` (Playwright or similar): fill journal, assert zero network requests carry journal data.
- `RecoveryLinksTest`: every link passes `OutboundLinkPolicy`.
- `ComplaintDraftTest`: ≤200 words, linter-clean, no echo of free text.

### Exit gate
Both recovery paths work; journal stays off the network; offline band matches the server on fixtures.

---

## Phase 6 — Study and evaluation (Day 3)

**Goal:** numbers for the deck and permanent CI gates.

### DATA / OPS
- [ ] Grow fixtures to ≥80 labelled messages: ~40 scam, ~25 education, ~15 ambiguous; label band, class, signal ids.
- [ ] `eval` runner (Maven profile or script) producing `eval-report.md`:
  - Detection: % scams at `some_concern` or higher (target ≥90%); % genuine education flagged (target ≤10%).
  - Content class: education → `education` or `few_flags_still_verify`, never `high_concern`.
  - Parity: server vs on-device agreement (target 100%).
  - Linter pass rate (target 100%), log audit (target 0 leaks).
- [ ] Performance: server p95 ≤1.5 s with LLM off (k6/JMeter); text-path p95 ≤8 s on throttled 3G low-end Android; result payload ≤6 KB gzipped; shell ≤200 KB gzipped.
- [ ] Accessibility pass: TalkBack walk-through of result screen.
- [ ] Behaviour study: 8–10 consenting adults, 10 messages, with vs without Ruko; record correct identification (+30 pp target) and % who can restate main reason (≥80% target). Consent form, no PII retained. Report actual n.
- [ ] Make `LinterTest`, `NoContentLoggingTest` (now over all fixtures), `OnDeviceParityTest`, and bundle-size checks **required CI gates**.

### Exit gate
≥80 labelled fixtures; study sheet filled; linter and log audit are CI gates.

---

## Phase 7 — Deploy and submit (Day 4)

**Goal:** a live demo link, video, and the submission pack.

### OPS
- [ ] Multi-stage Dockerfile on JRE 21; `-XX:MaxRAMPercentage=75 -XX:+UseSerialGC`; fits ~512 MB.
- [ ] Readiness probe: re-validate rules and run three fixtures; fail readiness if bands differ from expected.
- [ ] PWA and API on one domain (static PWA served by Spring or reverse proxy) — avoids CORS, keeps CSP tight.
- [ ] Secrets via env (`LLM_*`, `BHASHINI_*`, `RATE_LIMIT`); never in repo.
- [ ] Keep-alive ping before demo if the free tier sleeps.
- [ ] Smoke test on the live URL: online path, LLM-off path, Bhashini-off path, airplane-mode path.

### Submission pack
- [ ] OpenAPI page (springdoc).
- [ ] Linter report.
- [ ] Permission list (share target, microphone on tap, optional notifications).
- [ ] Link audit (every outbound link vs allowlist).
- [ ] `eval-report.md`.
- [ ] Study sheet with sample size.
- [ ] Architecture slide (from §1 diagram).
- [ ] Snapshot date, or explicit "no public list, deep link only" line.
- [ ] Disclaimer: independent prototype, not an official SEBI/NSDL product, no investment advice; DPDP principles applied, not legal advice.

### Video (3–5 min), beats from §9
1. The message arrives.
2. Share → spoken result with class and analogy.
3. SEBI Check handoff and the pause journal.
4. Already-paid → 1930 path.
5. SCORES path.
6. Study numbers and guardrails.

### Exit gate
Demo link, 3–5 min video, deck.

---

## Cross-cutting checklist (verify every phase)

| Rule (§1 design rules) | How it's enforced |
| --- | --- |
| Request text lives for one request | No persistence layer; `NoContentLoggingTest`; TTS takes keys only |
| Rules decide the band | `BandCalculator` takes only hits; L8; `InjectionTest` |
| Deterministic fallback for every port | LLM → templates; Bhashini → `speechSynthesis`; API → on-device engine |
| Linter fails closed | Template retry → `FallbackTemplates.generic` |
| One LLM call, 3 s, circuit breaker | `ResilienceConfig`; `LlmAssistTest` |
| TTS gets catalogue text only | `TtsContractTest` |
| Public page hides regex and rate limits | Content endpoint built from reason keys only |
| Snapshot never says "verified"/"safe" | L3 + snapshot copy review |

## Cut order if late (§7)

1. S10 domain-age → 2. ASR → 3. any third language → 4. family card → 5. server complaint route.

**Never cut:** client complaint template, journal, both recovery paths, content class, analogy, on-device rules. The LLM is the last thing to drop; templates are the product.

## P1 (only after all gates pass)

- Family card: image drawn on device; server supplies card copy and TTS only.
- Evidence pack: client-side PDF.

## Gaps and ambiguities in the TRD to settle early

1. **S19 is missing from the catalogue table** in §2 but is defined in the snapshot section. Add it to `signals.v0.json` as STRONG, emitted only by `SebiSnapshotIndex`.
2. **`ExplainerPort` vs `LlmPort`:** §1 layout lists `ExplainerPort`/`LlmAssist`, §10 refers to `LlmPort`. Plan uses `ExplainerPort` for card composition and `LlmPort` for the provider adapter.
3. **Pipeline order:** prose says classifier then band; pseudocode says band then classifier. Both read only hits, so order doesn't matter — keep pseudocode order.
4. **"Generated TypeScript port"**: generating logic is impractical in a 4-day sprint. Generate the data (rules, templates, prefix map) and hand-port the small engine once, with `OnDeviceParityTest` as the guard.
5. **Regex portability:** Java and JS regex engines differ. Restrict `signals.v0.json` regexes to a common subset and validate them in both runtimes in CI.
6. **`${RATE_LIMIT}` has no default** in §6 YAML; add one so boot doesn't fail locally.
7. **Phase 2–4 all land on Day 2.** If Day 2 slips, Phase 4 voice can fall back to browser TTS only and Bhashini wiring moves to Day 3 morning.
