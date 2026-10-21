# Flow Docs: Adoption Guide

How to add the flow-docs framework from this repo to another Spring Boot 4 / Java / Gradle (Groovy)
project. You copy one Gradle project and add annotations, a few test classes and some config. The
app's functional code doesn't change.

**Requirements**
- Spring Boot 4.x, Spring MVC, Java 21 (or change the toolchain lines)
- Gradle with the Spring Boot plugin
- WireMock 3.13.x, which the `flowdocs` test fixtures bring in

## 1. Copy the library

Copy the `flowdocs/` directory from this repo to the root of the target project, unchanged. Leave out
its `build/` directory.

| Folder | Contents | Reaches the app's |
| --- | --- | --- |
| `flowdocs/src/main/` | Annotations (`com.example.flowdocs`) and runtime (`.boot`), plus the auto-configuration registration in `resources/META-INF/spring/` | main classpath. Inert unless enabled. |
| `flowdocs/src/testFixtures/` | Test support (`.test`): recorder, renderers, static graph, scenario runner, rules | test classpath only |
| `flowdocs/src/test/` | The library's own tests | nothing (optional to copy) |

**Renaming the package.** If you rename `com.example.flowdocs`, also update the single line in
`flowdocs/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

## 2. Gradle wiring

**`settings.gradle`:**

```groovy
include 'flowdocs'
```

**Root `build.gradle`.** `flowdocs/build.gradle` references the Boot BOM through the Boot plugin
class, and it relies on the root build for repositories and JUnit. If the root project isn't
already applying the Boot plugin, add this:

```groovy
plugins {
    id 'org.springframework.boot' version '4.0.8' apply false   // match your Boot version
}

subprojects {
    repositories { mavenCentral() }
    tasks.withType(Test).configureEach { useJUnitPlatform() }
}
```

If you'd rather not touch the root build, add `repositories { mavenCentral() }` and
`tasks.withType(Test).configureEach { useJUnitPlatform() }` to `flowdocs/build.gradle` instead.

**App module `build.gradle`:**

```groovy
dependencies {
    implementation project(':flowdocs')
    implementation 'org.springframework.boot:spring-boot-starter-actuator'      // provides ObservationRegistry; see below

    testImplementation testFixtures(project(':flowdocs'))                        // test support only
    testImplementation 'org.springframework.boot:spring-boot-webmvc-test'       // @AutoConfigureMockMvc
}

tasks.named('test') {
    systemProperty 'updateFlowDocs', providers.gradleProperty('updateFlowDocs').isPresent()
    inputs.files(fileTree('docs') { include '**/*.mmd' })
}
```

- **Actuator.** When flow observations are enabled, the library needs an `ObservationRegistry`
  bean, and startup fails without one. Actuator provides it, and so does
  `org.springframework.boot:spring-boot-micrometer-observation` on its own. The library
  deliberately doesn't pull either in.
- **`-parameters`.** The Boot Gradle plugin compiles with `-parameters`, which lets expressions
  refer to arguments as `#name`. Without the plugin, add
  `tasks.withType(JavaCompile).configureEach { options.compilerArgs.add('-parameters') }`.

**Repo root `.gitattributes`:** add `*.mmd text eol=lf`.

## 3. Turning it on and off

| Property | Default | Effect |
| --- | --- | --- |
| `flow.observations.enabled` | unset (off) | Off: the library registers no beans, proxies or expression parsers, so the annotations are inert metadata. On: annotated methods are recorded as `flow.step` observations. |
| `flow.observations.strict` | `false` | `true`: a decision or branch whose conditions disagree with what happened, a branch whose both paths ran, or a broken expression, throws. Use this in tests only. `false`: logs a warning instead. |

Where to set them:

| Environment | Setting |
| --- | --- |
| **Production** | Set nothing. |
| **Tests** | In the scenario test's `@SpringBootTest(properties = ...)`, as in section 6. Nothing goes in `application.yml`. |
| **Perf/load environment** (optional) | `flow.observations.enabled=true`, plus `management.endpoints.web.exposure.include=health,metrics` to read the timers. |

**Removing it later:** drop the two `flowdocs` dependencies and the annotations. Nothing else depends on it.

## 4. Declare participants (app main code)

Participants are the systems that appear in the diagrams, such as your app and each downstream
service. Declare each one as a constant with a label, in a class anywhere under the app's base
package:

```java
public final class Participants {
    @FlowParticipant(label = "Router")    public static final String ROUTER = "ROUTER";
    @FlowParticipant(label = "Partner A") public static final String PARTNER_A = "PARTNER_A";
    private Participants() {}
}
```

## 5. Annotate (app main code: add only)

```java
// Steps: the controller method, orchestration methods, each downstream call
@FlowStep(action = "Call Partner A", participant = Participants.PARTNER_A,
          note = "Using passport #{#request.documentNumber()}")          // note is optional (SpEL template)

// Decisions: an existing boolean method, body unchanged
@FlowDecision(question = "Partner A eligible?", participant = Participants.ROUTER, gates = "Call Partner A",
        conditions = {
            @Condition(label = "SSN present", test = "#hasText(#request.person().ssn())"),
            @Condition(label = "valid passport",
                       test = "#request.firstValidNumber(T(com.acme.DocumentType).PASSPORT).isPresent()")})
public boolean shouldCall(RequestDto request) { ... }

// Error mappings: annotate existing @ExceptionHandler methods in a @RestControllerAdvice
@FlowStep(action = "Map partner failure to 502", participant = Participants.ROUTER)

// Inline if/else with no method of its own: declare it on the step that contains it (repeatable)
@FlowStep(action = "Determine downstream partners", participant = Participants.ROUTER)
@FlowBranch(question = "Use new ID provider?", participant = Participants.ROUTER,
        gates = "Call New ID Provider", otherwise = "Call Legacy ID Provider",
        conditions = @Condition(label = "new provider enabled", test = "#property('id-provider.use-new') == 'true'"))
public void route(RequestDto request, ResponseDto response) { if (useNewIdProvider) { ... } else { ... } }
```

**Decision or branch?**
- **`@FlowDecision`:** the `if` calls a boolean method, as in `if (partnerA.shouldCall(request))`.
  Annotate that method.
- **`@FlowBranch`:** the condition is written inline, as in `if (useNewIdProvider)`. Declare it on
  the `@FlowStep` method that contains it.

**How `@FlowBranch` works**
- **Outcome.** The outcome is observed, not declared. It's "yes" if the `gates` step runs during
  the method call, and "no" if the `otherwise` step does. Both steps must be `@FlowStep`s, called
  from the method directly or through helpers.
- **Placement.** The branch is recorded just before whichever of those steps runs, so the note sits
  in the right place in the sequence diagram.
- **`otherwise` is optional.** Leave it out for an `if` with no `else` step. A branch that never
  fires is then recorded as "no" when the method returns.
- **Repeatable.** Put one `@FlowBranch` per inline branch on the same method. The static graph
  orders them with any decisions, by where their steps are called in the method.
- **Conditions** are optional and evaluated when the method starts. They can use the method's
  arguments (`#name`), and `#property('key')` to read the app's configuration.
  - A condition can't see local variables computed inside the method. For a branch that tests one,
    leave `conditions` out: you still get the observed yes/no and the graph, just not the ✓/✗.
  - With `#property`, an unset key is `null`. Write the condition so that matches the code's
    default, for example `== 'true'` for `@Value("${flag:false}")`. The cross-check catches a
    mismatch.
- **Cross-check.** In strict mode, the call fails if the conditions disagree with which step ran,
  or if both steps ran in one call.

These rules are enforced at build time by `FlowDocsRules`:

- **Downstream calls are steps.** Any method that calls `RestClient`, `RestTemplate` or `WebClient`
  must be a `@FlowStep`. Matching nothing is fine.
- **Proxyable.** Steps and decisions are public or package-visible, not `final` or `static`, and
  not called from their own class.
- **Decisions:**
  - return `boolean`
  - `gates` names an existing step action
  - are called directly from a `@FlowStep` method, which is how the graph orders them
  - the calling step can reach the gated step
- **Branches:**
  - sit on a `@FlowStep` method
  - `gates` and `otherwise` name existing steps
  - the annotated step can reach both
- **Unique names.** Actions and questions are unique.
- **Declared participants.** Every participant id is declared.
- **Valid expressions.** Every expression parses.

Not enforced, but required: conditions are ANDed and all of them are evaluated, so each one must be
safe to run when an earlier one is false (null-safe, no side effects).

At test time, each scenario also fails if a gated step ran against its decision or branch, or
without its decision or branch being made:
- a `gates` step needs a "yes"
- a branch's `otherwise` step needs a "no"

### Downstream calls through a client library

If partner calls go through client interfaces from a separate repo or jar that you don't
annotate, put the step on the app method that calls the client:

```java
@FlowStep(action = "Call Partner A", participant = Participants.PARTNER_A)
public PartnerResult call(RequestDto request) {
    ... partnerAClient.match(requestId, partnerRequest) ...
}
```

- Leave the client library alone. It never needs a flowdocs dependency.
- Add `downstreamCallsAreSteps` with the client packages to the rules test (section 6). It requires
  every app method that calls a client interface to be a step. Reading the client's data types
  doesn't count.
- The step's timing includes the app's request mapping, which is normally negligible.
- Point `gates` at this step's action.

## 6. Test wiring (app `src/test/java`)

Create these classes in any test package. Replace the `com.acme.app` package, the WireMock property
names and the paths with your own.

```java
// Settings shared by the docs tests. Defaults: docs/ and src/test/resources/flow-scenarios/
final class AppFlowDocs {
    static final FlowDocsSettings SETTINGS = FlowDocsSettings.of("com.acme.app", Participants.ROUTER);
}

// Static graph + index: writes docs/flow.mmd and docs/README.md
class FlowDocsTest {
    @Test void flowDocsAreCurrent() throws Exception { StaticFlowDocs.verify(AppFlowDocs.SETTINGS); }
}

// Annotation rules
@AnalyzeClasses(packages = "com.acme.app", importOptions = ImportOption.DoNotIncludeTests.class)
class FlowDocsRulesTest {
    @ArchTest static final ArchTests flowDocs = ArchTests.in(FlowDocsRules.class);

    // Only if downstream calls go through client interfaces you don't annotate (see section 5)
    @ArchTest static final ArchRule clients = FlowDocsRules.downstreamCallsAreSteps("com.acme.clients..");
}

// Scenarios: writes docs/flows/<name>.mmd and .md
@SpringBootTest(properties = {"flow.observations.enabled=true", "flow.observations.strict=true"})
@AutoConfigureMockMvc
@Import(FlowRecorderConfiguration.class)
class FlowScenarioDocsTest {

    @RegisterExtension
    static WireMockExtension downstream = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))   // required, see below
            .build();

    private static final ScenarioRunner RUNNER = new ScenarioRunner(AppFlowDocs.SETTINGS, downstream);

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("partners.a.base-url", () -> downstream.baseUrl() + "/partner-a");   // your properties
    }

    @Autowired MockMvc mvc;
    @Autowired FlowRecorder recorder;

    // Only the scenarios whose "properties" match this context's; Map.of() for the defaults
    static Stream<String> scenarios() throws IOException { return Scenario.names(AppFlowDocs.SETTINGS.scenariosDir(), Map.of()); }

    @ParameterizedTest(name = "{0}") @MethodSource("scenarios")
    void scenario(String name) throws Exception { RUNNER.run(name, mvc, recorder); }
}
```

- **One test class per configuration.** Configuration is fixed per application context, so a
  branch on a property needs a scenario test class per property value. Give each class those
  properties in its `@SpringBootTest`, and pass the same map to `Scenario.names`:

  ```java
  @SpringBootTest(properties = {
          "flow.observations.enabled=true", "flow.observations.strict=true", "id-provider.use-new=true"})
  class NewIdProviderScenarioDocsTest extends ScenarioDocsTestSupport {   // shared wiring, as above
      static Stream<String> scenarios() throws IOException {
          return Scenario.names(AppFlowDocs.SETTINGS.scenariosDir(), Map.of("id-provider.use-new", "true"));
      }
      @ParameterizedTest(name = "{0}") @MethodSource("scenarios")
      void scenario(String name) throws Exception { run(name); }
  }
  ```

  The shared wiring (the WireMock extension, `@DynamicPropertySource`, `MockMvc`, `FlowRecorder`
  and the runner) can live in an abstract base class, like `ScenarioDocsTestSupport` in this repo.
  Two guards apply:
  - a scenario fails if its context lacks its properties
  - the build fails if a scenario has no generated page, meaning no class runs its properties
- **`http2PlainDisabled(true)` is required.** Spring's `RestClient` uses the JDK HttpClient, which
  tries an h2c upgrade (HTTP/2 over plain HTTP). WireMock's Jetty resets that connection.
- **Optional prod-neutral check.** A test that boots without the property and asserts that a
  `@FlowStep` bean isn't proxied:
  `assertThat(AopUtils.isAopProxy(someClient)).isFalse()`. See `FlowObservationsOffTest` in this
  repo.

## 7. Scenarios (app `src/test/resources/flow-scenarios/<name>.json`)

```json
{
  "description": "One line, shown on the page and in the diagram",
  "method": "POST",
  "path": "/search",
  "properties": { "id-provider.use-new": "true" },
  "request": { "...": "body sent to the app" },
  "expectedStatus": 200,
  "stubs": [ { "request": { "...": "standard WireMock mapping" },
               "response": { "status": 200, "headers": { "Content-Type": "application/json" }, "jsonBody": {} } } ]
}
```

- `properties` is optional. Leave it out for scenarios that use the default configuration.
- **Unique keys.** Give each scenario a unique key, such as an id in the downstream request body,
  and match on it with `bodyPatterns`. This keeps stubs from overlapping when Gatling loads them
  all at once.
- **Content type.** Stub responses need `Content-Type: application/json`, or `RestClient` can't
  read them.
- **No random delays,** so the diagrams stay the same on every run.

## 8. Generate and commit docs

```bash
./gradlew test -PupdateFlowDocs     # writes <app>/docs/; the first plain build fails until this has run
git add <app>/docs && git commit
```

After that:
- `./gradlew build` fails whenever a committed doc is stale.
- Regenerate after intentional flow changes, and review the diffs in the PR.

Output in `<app>/docs/`:

| File | Contents |
| --- | --- |
| `flow.mmd` | Static graph, with decisions and branches as condition diamonds |
| `README.md` | Graph, then steps, decisions and branches, then scenarios with their properties |
| `flows/<name>.mmd` | Sequence diagram |
| `flows/<name>.md` | Request, decisions, diagram, downstream calls, response |

## 9. Optional: Gatling load test

Copy these from `main-app`:

| From | To |
| --- | --- |
| `src/gatling/java/com/example/router/FlowScenariosSimulation.java` | `<app>/src/gatling/java/<your package>/` |
| `src/gatling/resources/logback.xml` | `<app>/src/gatling/resources/` |
| `src/main/resources/application-wiremock.yml` | `<app>/src/main/resources/`, pointing your downstream base URLs at the WireMock host |

Then:

- Add `id 'io.gatling.gradle' version '3.16.0'` to the app's `plugins` block.
- Run WireMock with `--disable-http2-plain`. In a container, that's `CMD ["--disable-http2-plain"]`
  in its Dockerfile.
- Run the load test:
  1. Start the app with `--spring.profiles.active=wiremock --flow.observations.enabled=true`.
  2. Run `./gradlew :<app>:gatlingRun`, setting `APP_URL`, `WIREMOCK_URL`, `PARTNER_DELAY_MS`,
     `PEAK_USERS_PER_SEC` and `RAMP_SECONDS` as needed. `GATLING_PROPERTIES=key=value,...` selects the
     scenarios for that configuration. Start the app with the same settings. The default is the
     scenarios without `properties`.
  3. Read per-step timings from `/actuator/metrics/flow.step`. It's tagged with `flow.step`,
     `flow.participant`, `flow.kind` and `flow.outcome`.

## Limits

- **Spring MVC only.** WebFlux would need context propagation to keep steps nested across threads.
- **Branches are tracked per thread.** A `gates` or `otherwise` step that runs on another thread
  isn't seen, and the branch is recorded as "no".
- **A branch inside a loop** records only its first pass in each call. If a later pass takes the
  other path, that counts as "both paths ran", which fails in strict mode. Declare such a branch on
  a step called once per pass instead.
- **Multi-way `switch` statements** aren't supported yet. `@FlowBranch` covers yes/no, with one
  `gates` step and one `otherwise` step.
- **MockMvc doesn't render the servlet container's error page,** so a request rejected before the
  controller shows an empty body on its scenario page.
- **Boot 4 / Jackson 3.** The test module reads scenarios with `tools.jackson`.
