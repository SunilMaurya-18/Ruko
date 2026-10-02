# Submission pack

Everything Phase 7 asks for, in one place. Generated files say so at the top and are rebuilt by the command shown. Do not edit them by hand.

| | |
| --- | --- |
| Demo link | **Not deployed yet.** Follow [deploy.md](../deploy.md) (Render free tier, about 10 minutes), then put the URL here and in the deck. |
| API page | `<demo link>/swagger-ui.html` (springdoc). The same document is in [openapi.json](./openapi.json). |
| Video | 3:00, English (`brag.mp4`) and Hindi narration (`brag-hi.mp4`). Kept outside the repo; upload them and link here. They do not show the SCORES path yet (video beat 5 in TRD §9). |
| Deck | [deck.md](./deck.md) (Marp: `npx @marp-team/marp-cli docs/submission/deck.md --pdf`) |

## Pack

| Item | Where | How it is produced |
| --- | --- | --- |
| OpenAPI page | `/swagger-ui.html` on the live URL; [openapi.json](./openapi.json) | `cd server && ./mvnw -Psubmission test` |
| Linter report | [linter-report.md](./linter-report.md) | same command |
| Permission list | [permissions.md](./permissions.md) | checked against `pwa/src` and the manifest |
| Link audit | [link-audit.md](./link-audit.md) | same command; fails if a user-facing link is off the allowlist |
| Evaluation report | [../eval-report.md](../eval-report.md) | `cd server && ./mvnw -Peval test` |
| Study sheet with sample size | [../study/README.md](../study/README.md), [results-template.csv](../study/results-template.csv) | **n = 0**: no sessions have been run. Fill `docs/study/results.csv`, then `node scripts/score-study.mjs docs/study/results.csv --write` |
| Architecture slide | [architecture.md](./architecture.md) | TRD §1 diagram plus the deployment |
| Smoke test | [smoke-report.md](./smoke-report.md) | `RUKO_URL=… npm run test:smoke` in `pwa/`. The current file is from the deploy image run locally in 512 MB; rerun it against the live URL after deploying. |
| Snapshot | see below | `shared/snapshot/sebi-intermediaries.json` |
| Disclaimer | see below | |

## SEBI registration snapshot

**No public list, deep link only.** SEBI publishes no downloadable list of registration numbers, so Ruko ships an empty snapshot (`snapshot_date: null`). A registration number in a message is always shown as "couldn't verify" with a one-tap link to [SEBI Check](https://siportal.sebi.gov.in/intermediary/sebi-check). Ruko never says a number is verified, and never says a message is safe.

## Disclaimer

Ruko is an independent prototype built for the SANGYAN Investor Resilience Hackathon. It is not an official SEBI or NSDL product and is not endorsed by them. It gives no investment advice: it never says buy, sell, or hold, and never names a share. A result with no flags does not mean a message is safe. DPDP principles are applied (notice, consent, purpose limitation, minimisation); this is not legal advice.
