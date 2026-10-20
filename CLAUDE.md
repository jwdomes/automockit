# automockit

A local, standalone WireMock container for mocking HTTP services, plus a Bruno collection for
calling the mocks and adding stubs at runtime.

Design doc: [`Flow Docs from Code Micrometer @Observed + WireMock.md`](Flow%20Docs%20from%20Code%20Micrometer%20@Observed%20+%20WireMock.md)
describes the bigger plan this supports: `@FlowStep` annotations generating Mermaid flow diagrams,
JSON scenario files whose `stubs` are standard WireMock mappings, and Gatling load tests that push
every scenario's stubs into a standalone WireMock through `/__admin/mappings`. Read it before
changing how stubs are structured.

## Layout

```
Dockerfile        wiremock/wiremock:3.13.1 with mappings/ baked in, --verbose logging
mappings/         stub mappings loaded at startup, one feature per file
bruno/            Bruno collection (open this folder in Bruno, pick the "local" environment)
  calls/          one request per mapping behaviour, each with a docs block saying what to expect
  admin/          WireMock admin API: list, create, reset mappings; request journal
  mocks/          original examples (Get user, Get order)
  environments/   local.bru -> baseUrl http://localhost:8080
```

## Running

```bash
docker build -t wiremock-local .
docker run -d --rm --name wiremock -p 8080:8080 wiremock-local
docker stop wiremock          # container removes itself
```

Mappings are baked into the image, so changes under `mappings/` need a rebuild and restart.
Mappings added through the admin API live in memory only and are lost on restart.

Useful admin endpoints: `GET /__admin/mappings`, `POST /__admin/mappings`,
`POST /__admin/mappings/reset` (back to the baked-in set), `POST /__admin/scenarios/reset`,
`GET /__admin/requests`, `GET /__admin/health`.

## Current mappings

| File | Endpoint(s) | Demonstrates |
| --- | --- | --- |
| `get-user.json` | `GET /api/users/1` | Plain static stub |
| `greeting-templated.json` | `GET /api/greeting?name=` | Response templating from the request |
| `create-user.json` | `POST /api/users` | JSON body matching; priority fallback to 400 |
| `secure.json` | `GET /api/secure/profile` | Header matching (`Bearer let-me-in`); fallback 401 |
| `faults.json` | `/api/slow`, `/api/unavailable`, `/api/broken` | Random delay, 503 + Retry-After, connection reset |
| `cart-scenario.json` | `GET /api/cart`, `POST /api/cart/items` | Stateful scenario |

## Conventions

- A file holds either one mapping or `{"mappings": [...]}` for a related group.
- Fallbacks use `priority`: the specific match gets 1, the catch-all gets 10.
- Templated responses need `"transformers": ["response-template"]`.
- Stubs meant for the design doc's scenarios or Gatling must match on a request key no other stub
  uses, since Gatling loads every stub at once.
- Every new mapping gets a matching request in `bruno/calls/` with a `docs` block.
- WireMock can't run arbitrary scripts. Custom logic (e.g. decrypting a request) means a Java
  extension jar copied into `/var/wiremock/extensions/` in the image.

## Verifying changes

Rebuild, start the container, and curl each affected endpoint (and `/__admin/mappings` for the
loaded count) before calling a change done. The Bruno CLI (`bru`) is not installed; the desktop
app is.
