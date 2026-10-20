# automockit

A local, standalone WireMock container for mocking HTTP services, plus a Bruno collection for
calling the mocks and adding stubs at runtime. Also a multi-project Gradle build: a Spring Boot
router (`main-app`) that calls up to three partner services (`partner-a/b/c`), each with its own
seeded Postgres database. The router is where the design doc's flow-doc framework will go.

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
  search/         POST /search against the router, one request per routing case
  environments/   local.bru -> baseUrl http://localhost:8080 (WireMock), appUrl http://localhost:8090
compose.yaml      Postgres 17 on localhost:5442 (5432 is taken by another project here)
docker/           init script creating partner_a, partner_b, partner_c databases
main-app/         router, com.example.router, port 8090; flow docs in main-app/docs/
partner-a/        com.example.partner.a, port 8091, POST /partner-a/matches
partner-b/        com.example.partner.b, port 8092, POST /partner-b/matches
partner-c/        com.example.partner.c, port 8093, POST /partner-c/matches
```

## Router and partners

`POST /search` takes `{header, documents[], person}`. `SearchController` creates a `ResponseDto`,
and `Router` runs A, B, then C. For each partner, its `PartnerXService.shouldCall` decides whether
to call it, and `PartnerXClient` makes the call.

| Partner | Called when | Sent |
| --- | --- | --- |
| A | SSN and a valid `PASSPORT` | firstName, lastName, documentNumber, personId (=SSN) |
| B | PNumber and a valid `DRIVER_LICENSE` | firstName, lastName, birthDate, documentNumber, pNumber |
| C | SSN | lastName, birthDate, personIdentifier (=SSN) |

- "Valid" means the number matches the regex on `DocumentType`. The patterns are placeholders.
- `results` has one entry per partner that was called. If a partner call fails, the router
  returns 502.
- Partners find candidates by identifier or document number and score them with weighted field
  matches, highest first. Ties are broken by name so the order is deterministic.
- Seed data lives in each partner's `db/migration/V2__seed.sql` (Flyway).
- There is deliberately no shared module, because each partner owns its contract.

## Running

```bash
docker build -t wiremock-local .
docker run -d --rm --name wiremock -p 8080:8080 wiremock-local
docker stop wiremock          # container removes itself
```

Router and partners (Java 21; Gradle comes from the wrapper):

```bash
docker compose up -d                 # Postgres; Flyway seeds each partner DB on first start
./gradlew build                      # compiles and runs unit tests
./gradlew :partner-a:bootRun         # likewise partner-b, partner-c, main-app (separate terminals)
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

## Flow docs (main-app)

This is the design doc's framework, applied to the router.

- **Steps.** `@FlowStep` sits on:
  - `SearchController.search` and `RequestValidator.validate`
  - `Router.route`
  - each `PartnerXClient.match`
  - both `SearchExceptionHandler` methods (the 400 and 502 mappings)

  It lives in `com.example.router.docs`, along with `Participant` and `FlowStepObservationConfig`.
- **Decisions.** `@FlowDecision(question, participant, gates)` sits on each
  `PartnerXService.shouldCall`, which returns `Decision.yes(reason)` or `Decision.no(reason)`.
  - Static graph: a diamond, chained A → B → C in call-site order, with `yes` leading to the
    gated step.
  - Scenario diagrams: a note with the reason.
- **Notes.** `FlowNotes.note(text)` adds a note to the step currently running, for example which
  document was sent.
- **Off by default.** The annotations do nothing unless `flow.observations.enabled=true`.
- **Validation.** It's explicit (`RequestValidator`), not `@Valid`, so the 400 path is a step.
  Malformed JSON still gets Spring's default 400 and isn't a step.
- **Generated docs** in `main-app/docs/`:

  | File | Contents |
  | --- | --- |
  | `flow.mmd` | Every possible path |
  | `README.md` | The graph, plus tables of steps, decisions and scenarios |
  | `flows/<scenario>.mmd` | One diagram per scenario. It ends with the status the caller got. |
  | `flows/<scenario>.md` | Request, decisions table, diagram, partner calls (from WireMock's request log), router response |
- **Snapshot rule.** `./gradlew build` fails if a committed diagram is stale. To regenerate after an
  intentional change, run `./gradlew :main-app:test -PupdateFlowDocs` and commit `docs/`.
- **Scenarios.** They live in `main-app/src/test/resources/flow-scenarios/`. The test runs each one
  against an in-process WireMock that serves all three `/partner-x` paths.
  - Give each scenario its own SSN or PNumber, and match on it in `bodyPatterns`: `$.personId`
    (A), `$.pNumber` (B), `$.personIdentifier` (C).
  - Stub responses need a `Content-Type: application/json` header, or RestClient can't read them.
  - Don't use random delays, so the diagrams stay the same between runs.
- **Rules.** `FlowStepCoverageTest` checks that:
  - every public method on a `*Client` in `..client..` is a step
  - actions and questions are unique together
  - steps and decisions aren't private or final, and aren't called from their own class
  - every method returning `Decision` has `@FlowDecision`
  - every `gates` names an existing step
  - decisions are called only from steps
- **Metrics.** Every flow observation carries `flow.kind` (`step` or `decision`) and
  `flow.outcome` (`yes`, `no` or `none`), so the `flow.step` timer counts decision outcomes under
  load.
- **HTTP version.** `PartnerHttpConfig` pins RestClient to HTTP/1.1. Without it, the JDK client
  tries an h2c upgrade (HTTP/2 over plain HTTP), and WireMock's Jetty resets the stream.

Gatling load test (`main-app/src/gatling/java/.../FlowScenariosSimulation.java`):

```bash
docker run -d --rm --name wiremock -p 8080:8080 wiremock-local
java -jar main-app/build/libs/main-app.jar --spring.profiles.active=wiremock --flow.observations.enabled=true
./gradlew :main-app:gatlingRun        # env: APP_URL, WIREMOCK_URL, PARTNER_DELAY_MS, PEAK_USERS_PER_SEC, RAMP_SECONDS
curl 'localhost:8090/actuator/metrics/flow.step?tag=flow.participant:PARTNER_A'
```

- `before()` resets WireMock and loads every scenario's stubs at once. The baked-in `/api/*`
  mappings don't overlap with them.
- The `wiremock` profile points all three partner base URLs at `localhost:8080`.

## Open questions

- **Decryption in WireMock.** Proposed approach: a Java extension jar, either a template helper
  (`{{decrypt request.body}}`) to put plain values in responses, or a request matcher to choose a
  response by decrypted content. Waiting on the user: which of the two is needed, and the
  algorithm and key source.
- **`bruno/mocks/` vs `bruno/calls/`.** Undecided whether to move Get user / Get order into
  `calls/` and drop `mocks/`.
