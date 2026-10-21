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
Dockerfile        wiremock/wiremock:3.13.1 with mappings/ baked in, --verbose, plain HTTP/2 off
mappings/         stub mappings loaded at startup, one feature per file
bruno/            Bruno collection (open this folder in Bruno, pick the "local" environment)
  calls/          one request per mapping behaviour, each with a docs block saying what to expect
  admin/          WireMock admin API: list, create, reset mappings; request journal
  mocks/          original examples (Get user, Get order)
  search/         POST /search against the router, one request per routing case
  environments/   local.bru -> baseUrl http://localhost:8080 (WireMock), appUrl http://localhost:8090
compose.yaml      Postgres 17 on localhost:5442 (5432 is taken by another project here)
docker/           init script creating partner_a, partner_b, partner_c databases
flowdocs-annotations/   @FlowStep, @FlowDecision, @Condition, @FlowParticipant (no dependencies)
flowdocs-spring-boot/   runtime: auto-configuration + interceptor, inert unless enabled
flowdocs-test/          test support: recorder, renderers, static graph, scenario runner, rules
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

## Flow docs (flowdocs-* modules, used by main-app)

A library, kept in this repo for now, that generates flow docs from annotations. It's written to be
added to any Spring Boot 4 app. Two rules shape it:

- **Add-only.** Apps gain annotations and nothing else. Functional code is never changed for the
  docs. `git diff 3525edb -- main-app/src/main` shows only annotations, imports, `Participants`
  and config.
- **Performance-neutral in prod.** Unless `flow.observations.enabled=true`,
  `FlowDocsAutoConfiguration` registers nothing: no beans, no proxies, no expression parsing.
  `FlowDocsAutoConfigurationTest` and `FlowObservationsOffTest` check this.

When it's enabled, the app needs an `ObservationRegistry` bean. Actuator provides one, and startup
fails loudly without it. The library deliberately doesn't pull one in, because that would also
switch on Boot's HTTP observations.

**Annotations** (`com.example.flowdocs`):
- `@FlowStep(action, participant, note)`. `note` is an optional SpEL template over the method's
  arguments, for example `"Using passport #{#request.documentNumber()}"`. It's evaluated before the
  call.
- `@FlowDecision(question, participant, gates, conditions)` goes on an unchanged boolean method.
  - Its return value is the outcome.
  - Each `@Condition(label, test)` is a SpEL test over the arguments. `#hasText(...)` is available.
    Conditions are evaluated after the call and ANDed.
  - All conditions are evaluated, not short-circuited, so write them to be safe when earlier ones
    are false.
  - **Strict mode** (`flow.observations.strict=true`, which the scenario test sets): conditions
    that disagree with the real return value, or an expression error, throw.
  - **Otherwise:** it logs a warning, and the real value always flows through.
- **Participants** are string ids declared as constants with `@FlowParticipant(label)`. See
  `main-app/.../Participants.java`.

**Generated docs** in `main-app/docs/`. `StaticFlowDocs` writes the first two, and `ScenarioRunner`
writes the per-scenario files:

| File | Contents |
| --- | --- |
| `flow.mmd` | Static graph. Each decision is a group of condition diamonds, chained in call-site order, with every "no" leading to the next decision. Exception handlers hang off the controller as dashed edges. |
| `README.md` | The graph, plus tables of steps, decisions and scenarios |
| `flows/<scenario>.mmd` | The sequence diagram. Decisions show each condition ✓/✗, and step notes appear before the call. It ends with the status the caller got. |
| `flows/<scenario>.md` | Request, decisions table, diagram, downstream calls (from WireMock's request log), response |

A request rejected before the controller (for example by `@Valid`) is drawn from MockMvc's resolved
exception. MockMvc doesn't render the servlet container's error page, so the 400 body there is
empty.

**Snapshot rule.** `./gradlew build` fails if a committed doc is stale. After an intentional
change, run `./gradlew :main-app:test -PupdateFlowDocs` and commit `docs/`.

**Scenarios** (`main-app/src/test/resources/flow-scenarios/*.json`): `description`, `method`,
`path`, `request`, `expectedStatus`, `stubs`.
- The test uses one in-process WireMock for all three `/partner-x` paths.
- Give each scenario its own SSN or PNumber, and match on it in `bodyPatterns`: `$.personId` (A),
  `$.pNumber` (B), `$.personIdentifier` (C).
- Stub responses need `Content-Type: application/json`.
- Don't use random delays.

**Rules** (`FlowDocsRules`, included by `FlowDocsRulesTest`):
- methods calling `RestClient`, `RestTemplate` or `WebClient` must be steps
- names are unique
- steps and decisions aren't private, final or static, and aren't called from their own class
- decisions return boolean, `gates` an existing step, and are called only from steps
- every participant id is declared
- every note and condition expression parses

**Metrics.** The `flow.step` timer is tagged with `flow.kind` (`step`/`decision`) and `flow.outcome`
(`yes`/`no`/`none`), so decision outcomes are countable under load.

**WireMock and HTTP/2.** Spring's RestClient uses the JDK HttpClient, which tries an h2c upgrade
(HTTP/2 over plain HTTP) that WireMock's Jetty resets. It's turned off on the WireMock side, never
in the app:
- the test uses `http2PlainDisabled(true)`
- the Dockerfile passes `--disable-http2-plain`

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
