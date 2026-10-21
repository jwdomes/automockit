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
flowdocs/         flow-docs library, one Gradle project:
  src/main/            annotations (com.example.flowdocs) + runtime (.boot): inert unless enabled
  src/testFixtures/    test support (.test): recorder, renderers, graph, scenario runner, rules
  src/test/            the library's own tests
partner-clients/  stand-in for the separate client repo: PartnerXClient and New/LegacyIdProviderClient
                  interfaces + RestClient implementations (com.example.partnerclients), wired by
                  auto-configuration
main-app/         router, com.example.router, port 8090; flow docs in main-app/docs/
partner-a/        com.example.partner.a, port 8091, POST /partner-a/matches
partner-b/        com.example.partner.b, port 8092, POST /partner-b/matches
partner-c/        com.example.partner.c, port 8093, POST /partner-c/matches
```

## Router and partners

`POST /search` takes `{header, documents[], person}`. `SearchController` creates a `ResponseDto`.

`Router` first resolves a canonical `personId` through `IdProviderService`. It uses the new ID
provider if `id-provider.use-new` is true, and the legacy one otherwise (the default). This is an
inline if/else in `route()`.

It then runs A, B, then C. For each partner, its `PartnerXService.shouldCall` decides whether
to call it, and `PartnerXService.call` makes the call through the `PartnerXClient` interface.

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
- **Client code.** The clients live in `partner-clients`, mirroring the enterprise setup:
  - Client interfaces and implementations sit together in a separate repo the team owns. The
    partners own their APIs.
  - main-app only sees the interfaces and converts `PartnerMatch` into its own `PersonMatch`.
  - `PartnerClientsAutoConfiguration` registers one client per partner at `partners.x.base-url`.
  - The module has no flowdocs dependency.

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
| `id-providers.json` | `POST /new-id-provider/lookup`, `POST /legacy-id-provider/lookup` | Stand-ins for the two ID providers; templated `personId`; priority 10 so scenario stubs win |

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

## Flow docs (flowdocs/, used by main-app)

A library, kept in this repo for now, that generates flow docs from annotations. It's written to be
added to any Spring Boot 4 app; `Flow Docs Adoption Guide.md` says how.

Apps depend on it with `implementation project(':flowdocs')` and
`testImplementation testFixtures(project(':flowdocs'))`. The test support uses Gradle's
`java-test-fixtures` plugin, so its dependencies (ArchUnit, WireMock, JUnit) never reach an app's
main classpath.

Two rules shape it:

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
- `@FlowBranch(question, participant, gates, otherwise, conditions)` covers an inline if/else, which
  has no method of its own. It's repeatable, and goes on the `@FlowStep` method containing the
  branch: see `Router.route`.
  - The outcome is observed: "yes" if the `gates` step ran during the call, "no" if the
    `otherwise` step did.
  - The branch is recorded as a decision just before that step.
  - Conditions are evaluated on entry. They can use `#property('key')` to read configuration.
  - The same strict cross-check applies, plus "both paths ran". Tracking is per thread, and a
    branch in a loop records its first pass only.
- `#property('key')` is available to every condition and note.
- **Participants** are string ids declared as constants with `@FlowParticipant(label)`. See
  `main-app/.../Participants.java`.
- **Step actions** are constants in `main-app/.../Steps.java`. They're used for `action`, and for
  `gates`/`otherwise`, which must match an action exactly. A rename there renames the step in every
  annotation and every generated doc.

**Generated docs** in `main-app/docs/`. `StaticFlowDocs` writes the first two, and `ScenarioRunner`
writes the per-scenario files:

| File | Contents |
| --- | --- |
| `flow.mmd` | Static graph. Each decision or branch is a group of condition diamonds, chained in code order. Every "no" leads to the branch's `otherwise` step, or else the next decision. Exception handlers hang off the controller as dashed edges. |
| `README.md` | The graph, plus tables of steps, decisions and scenarios |
| `flows/<scenario>.mmd` | The sequence diagram. Decisions show each condition ✓/✗, and step notes appear before the call. It ends with the status the caller got. |
| `flows/<scenario>.md` | Request, decisions table, diagram, downstream calls (from WireMock's request log), response |

A request rejected before the controller (for example by `@Valid`) is drawn from MockMvc's resolved
exception. MockMvc doesn't render the servlet container's error page, so the 400 body there is
empty.

**Snapshot rule.** `./gradlew build` fails if a committed doc is stale. After an intentional
change, run `./gradlew :main-app:test -PupdateFlowDocs` and commit `docs/`.

**Scenarios** (`main-app/src/test/resources/flow-scenarios/*.json`): `description`, `method`,
`path`, optional `properties`, `request`, `expectedStatus`, `stubs`.
- **One test class per property set.** Configuration is per application context, so each class
  runs the scenarios whose `properties` match its own `@SpringBootTest` properties, via
  `Scenario.names(dir, properties)`. Shared wiring is in `ScenarioDocsTestSupport`.
  - `FlowScenarioDocsTest` runs the defaults (`Map.of()`).
  - `NewIdProviderScenarioDocsTest` runs `id-provider.use-new=true`.
- **Guards.** A scenario fails if its context lacks its properties. `FlowDocsTest` fails if any
  scenario has no generated page, which is what happens when no test class runs its properties.
- The test uses one in-process WireMock for all three `/partner-x` paths.
- Give each scenario its own SSN or PNumber, and match on it in `bodyPatterns`: `$.personId` (A),
  `$.pNumber` (B), `$.personIdentifier` (C).
- **ID provider stub.** Every scenario that reaches `route()` needs a legacy ID provider stub,
  because tests run with the default flag. It matches on the scenario's `$.lastName`.
- **New-provider path.** `new-id-provider.json` covers it, with a new-provider stub instead.
- Stub responses need `Content-Type: application/json`.
- Don't use random delays.

**Rules** (`FlowDocsRules`, included by `FlowDocsRulesTest`):
- methods calling `RestClient`, `RestTemplate` or `WebClient` must be steps. It's allowed to match
  nothing, which is the case in main-app: its calls go through `partner-clients`.
- `downstreamCallsAreSteps("com.example.partnerclients..")` is added by `FlowDocsRulesTest`. App
  methods that call a client interface must be steps. That's why "Call Partner X" sits on
  `PartnerXService.call`: the client repo isn't annotated.
- names are unique
- steps and decisions aren't private, final or static, and aren't called from their own class
- decisions return boolean, `gates` an existing step, and are called only from steps
- the step that calls a decision can reach the step it gates (`gatedStepsAreReachable`)

**Gate check.** `ScenarioRunner` also fails a scenario if a gated step ran after its decision said
no, or ran without its decision being made. It checks this before the status, so the cause is
reported rather than the resulting 502. The static graph's yes-edges come from `gates` and are
taken on trust, so this check is what holds them to what the code actually does, on every path a
scenario exercises.
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
./gradlew :main-app:gatlingRun        # env: APP_URL, WIREMOCK_URL, PARTNER_DELAY_MS, PEAK_USERS_PER_SEC, RAMP_SECONDS, GATLING_PROPERTIES
curl 'localhost:8090/actuator/metrics/flow.step?tag=flow.participant:PARTNER_A'
```

- `before()` resets WireMock and loads every scenario's stubs at once. The baked-in `/api/*`
  mappings don't overlap with them.
- The `wiremock` profile points all three partner base URLs at `localhost:8080`.
- **`GATLING_PROPERTIES`** (for example `id-provider.use-new=true`) selects the scenarios for that
  property set. The default is the scenarios without `properties`. Start the app with the same
  settings.

## Open questions

- **Decryption in WireMock.** Proposed approach: a Java extension jar, either a template helper
  (`{{decrypt request.body}}`) to put plain values in responses, or a request matcher to choose a
  response by decrypted content. Waiting on the user: which of the two is needed, and the
  algorithm and key source.
- **`bruno/mocks/` vs `bruno/calls/`.** Undecided whether to move Get user / Get order into
  `calls/` and drop `mocks/`.
