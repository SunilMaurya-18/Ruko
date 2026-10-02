---
marp: true
title: Ruko (रुको)
paginate: true
---

# Ruko (रुको)

**Stop before you pay.**

Share a chat investment tip, hear the red flags in Hindi, pause, then verify on SEBI Check or follow the right recovery path.

SANGYAN Investor Resilience Hackathon · independent prototype

<!-- Not an official SEBI or NSDL product. Not investment advice. -->

---

## The problem

Ramesh, 46, runs a hardware shop in a tier-3 town in UP. Hindi first, low-end Android, patchy data.

Relatives added him to "stock tips" groups on WhatsApp and Telegram. A message promises **"guaranteed 20% a month"**, a **₹4999 VIP fee to a personal UPI ID**, and a **SEBI registration number**, **today only**.

He has never used SEBI Check or SCORES and does not know about 1930. He will not read a long screen, but he will listen to a short answer.

---

## What Ruko does

1. **Share** the message from WhatsApp or Telegram to Ruko. Personal numbers are masked on the phone.
2. **Hear** the result in Hindi: band, class (promotion or education), the exact words that raised each flag, one analogy.
3. **Verify:** registration numbers become "couldn't verify", one tap to SEBI Check.
4. **Pause:** a three-line journal and "wait 24 hours". It stays on the phone.
5. **Get help:** already paid means 1930, then the bank, then cybercrime.gov.in, plus a complaint draft. For a broker problem, the SCORES steps.

It works offline too: the same rules run on the phone.

---

## How Ruko decides

- **Rules decide the band**, from 19 signals in one shared file (`signals.v0.json`) read by the server and the phone.
- An optional LLM may only add tags and card text, behind a 3-second timeout and a circuit breaker.
- **Guardrail linter**, 8 checks on every response: no buy, sell, or hold; no tickers; no "safe" or "scam" verdicts; no predictions; only allowlisted links. It fails closed.
- **Never says "safe".** Every result ends with "No flags does not mean it is safe to send money."

---

## Architecture

```text
 WhatsApp / Telegram --share--> Ruko PWA ----masked text, HTTPS----> Spring Boot (stateless)
                                  |  on-device rules + templates        api -> pipeline -> rules/band
                                  |  journal, OCR, masking                  -> explain (templates + optional LLM)
                                  |                                         -> guardrail linter
                                  '<----------- same JSON shape --------'  voice (Bhashini, optional)
                                                                            content (recovery, how Ruko decides)
```

- One container on JRE 21 serves both the PWA and the API, in 512 MB.
- Every external dependency has a fallback: LLM down means templates, Bhashini down means the phone's own voice, API down means on-device rules.

---

## Results (80 labelled messages)

| | Result | Target |
| --- | --- | --- |
| Scams at some concern or higher | **40 / 40** | ≥ 90% |
| Genuine education flagged | **0 / 25** | ≤ 10% |
| Server and on-device agreement | **80 / 80** | 100% |
| Linter: template outputs clean | **320 / 320** | 100% |
| Server p95, LLM off | **14 ms** | ≤ 1.5 s |
| Largest result, gzipped | **0.8 KB** | ≤ 6 KB |
| Message text found in logs | **0 of 1407 lines** | 0 |

The fixtures are also the development set, so these numbers are optimistic. 9 known gaps are listed in `eval-report.md`.

---

## Privacy and guardrails

- **Message text lives for one request.** It is never written to disk, logs, or the voice cache. A canary log audit runs in CI.
- **The journal never leaves the phone.** There is no journal API route, and a browser test enforces it.
- **Permissions:** share target; clipboard and photo only on tap; notifications optional. No camera, location, or contacts.
- **Links:** only `siportal.sebi.gov.in`, `scores.sebi.gov.in`, `cybercrime.gov.in`, and `tel:1930`.
- **SEBI list:** there is no public registration-number list, so Ruko deep-links to SEBI Check.

DPDP principles applied: notice, consent, purpose limitation, minimisation. This is not legal advice.

---

## Behaviour study

Protocol ready: 8–10 consenting adults, 10 fixed messages, with and without Ruko. It measures correct identification (target +30 points) and restating the reason (target ≥ 80%).

**Sessions run so far: n = 0.** No behaviour numbers are claimed. Results come only from real sessions (`docs/study/results.csv`, scored by `scripts/score-study.mjs`).

---

## Limits and next

- Hindi and English. **Next language: Marathi** (named, not built).
- Spoken input (ASR) is built behind a flag and off.
- Domain-age lookup (S10) is off.
- Known rule gaps: 9 of 80 fixtures, each pinned by a test.
- Text-path speed was measured on an emulated slow 3G phone; a real low-end Android is still needed.

---

# Ruko (रुको)

**Live:** [ruko-187c.onrender.com](https://ruko-187c.onrender.com) · **API:** [ruko-187c.onrender.com/swagger-ui.html](https://ruko-187c.onrender.com/swagger-ui.html) · **Code:** [github.com/SunilMaurya-18/Ruko](https://github.com/SunilMaurya-18/Ruko)

Independent prototype. Not an official SEBI or NSDL product. No investment advice.
