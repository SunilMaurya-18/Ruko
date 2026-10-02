# Deploy

One container serves the PWA and the API on one origin. Anything that runs a Docker image with about 512 MB works. The repo ships a Render Blueprint (`render.yaml`) for the free tier.

## Render (free tier)

1. Push the repo to GitHub.
2. Render dashboard: **New > Blueprint**, pick the repo. It reads `render.yaml`: Docker, free plan, Singapore, health check `/actuator/health/readiness`.
3. When asked, leave the `sync: false` values empty to run with rules and templates only. To use them, enter `LLM_URL`, `LLM_KEY`, `LLM_MODEL` (then set `LLM_ENABLED=true`) or `BHASHINI_USER`, `BHASHINI_KEY` (then `BHASHINI_ENABLED=true`). They live in Render's dashboard, never in git.
4. Wait for the deploy to go live (first build about 5 minutes), then open `https://<service>.onrender.com`. If the name is taken, Render adds a suffix; copy the exact URL from the top of the service page. The current deploy is [https://ruko-187c.onrender.com](https://ruko-187c.onrender.com) (`https://ruko.onrender.com` belongs to someone else and returns 404).
5. GitHub repo **Settings > Secrets and variables > Actions > Variables**: add `RUKO_URL` = that origin. This turns on the keep-alive ping and is the smoke test's default target.
6. **Actions > smoke > Run workflow**. Choose `off` for LLM and Bhashini if no secrets were set. Download the `smoke-report` artifact and save it as `docs/submission/smoke-report.md`.

Free instances sleep after 15 idle minutes and take about a minute to wake. `keep-alive.yml` pings readiness every 14 minutes from 09:00 to 21:00 IST; run it by hand (**Actions > keep-alive > Run workflow**) a few minutes before a demo.

## Any Docker host

```sh
docker build -t ruko .
docker run -d -p 8080:8080 -m 512m \
  -e FORWARD_HEADERS_STRATEGY=native \
  -e LLM_ENABLED=false -e BHASHINI_ENABLED=false \
  ruko
```

The host must terminate HTTPS: the service worker, share target, and clipboard need a secure origin. Point the host's health check at `/actuator/health/readiness`. It returns 503 until the rules compile and the three readiness fixtures pass.

## Environment

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8080` in the image | Port to listen on (Render sets it) |
| `FORWARD_HEADERS_STRATEGY` | `none` | `native` behind a proxy that sets `X-Forwarded-For`, so rate limiting sees each client. Only proxies on private addresses are trusted; for others set `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` (a regex of proxy IPs). |
| `RATE_LIMIT` | `30` | `/api` requests per client IP per minute |
| `LLM_ENABLED`, `LLM_URL`, `LLM_KEY`, `LLM_MODEL` | off, empty | Optional OpenAI-compatible endpoint; rules decide the band either way |
| `BHASHINI_ENABLED`, `BHASHINI_USER`, `BHASHINI_KEY` | on, empty | Optional server voice; without it the phone speaks the result itself |
| `RUKO_FEATURES_ASR` | `false` | Spoken input. Also needs ffmpeg: `docker build --build-arg WITH_FFMPEG=true` |
| `JAVA_TOOL_OPTIONS` | empty | Extra JVM flags; the image already sets `-XX:MaxRAMPercentage=75 -XX:+UseSerialGC` |

## Smoke test

```sh
cd pwa && npm ci
RUKO_URL=https://<service>.onrender.com RUKO_EXPECT_LLM=off RUKO_EXPECT_BHASHINI=off \
  RUKO_SMOKE_REPORT=../docs/submission/smoke-report.md npm run test:smoke
```

It sends only fixture texts and checks:

- readiness
- the shell, `/share`, the service worker and manifest, and security headers
- an online check (band decided by the rules)
- the LLM mode (template engine when off, with the labelled bands either way)
- the Bhashini mode (audio, or 503 `browser_tts`)
- the content routes, the OpenAPI page, and that journal routes are 404
- in a real browser, a check online and then one in airplane mode on the installed PWA

CI runs the same test against the image in 512 MB on every push (`ci.yml`, job `image`).
