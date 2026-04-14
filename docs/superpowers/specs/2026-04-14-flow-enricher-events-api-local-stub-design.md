# Flow-enricher `events.api` Exclusion Cleanup — Design

**Date:** 2026-04-14
**Status:** Approved (pending implementation plan)
**Owner:** David Hustace
**Related memory:** `project_flow_enricher_events_api_local_stub.md`, `feedback_fix_horizon_not_exclusions.md`, `project_flows_processing_classpath_regression.md`

## Problem

`core/flow-enricher/pom.xml` declares 16 `<exclusion>` entries on its `org.opennms.features.events:org.opennms.features.events.api` dependency. The exclusions exist to cut two legacy transitive chains out of the Spring Boot 4 classpath:

- `events.api → core.model-api → jaxb-dependencies → eclipselink 2.5.1` (2014-era JPA provider)
- `events.api → spring-dependencies → hibernate-dependencies → hibernate-core 3.6.11.ONMS_RELEASE_1`

The exclusions are functionally correct and leave the dependency tree clean at HEAD, but they violate `feedback_fix_horizon_not_exclusions`: exclusions in a consumer pom are symptoms. The root cause belongs upstream, in the module whose transitive closure is wider than its load-bearing public API needs.

The memory file `project_flow_enricher_events_api_local_stub.md` originally framed the fix as a "delta-v-local stub" — copy `EventForwarder`, `Event`, `Log` into delta-v as trimmed POJOs and drop the events.api dep entirely. Investigation during brainstorming showed that framing was optimistic:

- `ParserBase.java:319` and `:368` (the two real call sites) construct `new EventBuilder().setUei(...).setTime(...)....getEvent()` chains.
- `EventBuilder` (655 LOC) was moved into `events.api` in PR #90, so a shim would have to carry it too.
- `Event.java` is 1,955 LOC of JAXB-generated code with ~20 sibling XML schema classes (Parm, Parms, Snmp, AlarmData, Mask, etc.) that its getter/setter signatures reference.
- A true classpath shim would be roughly 2,600 LOC and brittle against any future horizon upgrade that touches `EventBuilder`'s chained API or adds new call sites in `ParserBase`.

A smaller and more durable fix exists: the `events.api` *pom* declares dependencies that its *source* doesn't actually need. Trimming the pom at the horizon source eliminates the heavy transitives for every consumer at once, without any delta-v-local Java code.

## Architecture

Two-repo change, sequenced through a horizon release:

1. **`delta-v-horizon` (upstream):** Edit `features/events/api/pom.xml`. Mark `spring-dependencies` and `swagger-annotations` as `<optional>true</optional>`. Delete `camel-dependencies` and `org.opennms.core.model-api`. Verify the full horizon reactor still compiles. Release as horizon `1.0.9`.

2. **`delta-v` (consumer):** Bump `deltav.horizon.version` from `1.0.8` to `1.0.9`. Delete the 16 `<exclusion>` entries from the `events.api` dependency block in `core/flow-enricher/pom.xml`. No new Java code, no source changes, no stubs. `LoggingEventForwarder` stays exactly as written.

### Explicit non-goals

- **No delta-v-local shim of `EventForwarder`/`Event`/`Log`/`EventBuilder`.** The pom diet makes it unnecessary.
- **No new horizon module extraction.** A pom edit on the existing `events.api` module is structurally sufficient.
- **No touching the other 10 exclusion blocks in `core/flow-enricher/pom.xml`.** Narrow scope per the scope decision in brainstorming (Option a). A followup memory will track batching those after the next horizon release.

## Horizon pom edit

File: `delta-v-horizon/features/events/api/pom.xml`

```xml
<!-- BEFORE -->
<dependency>
  <groupId>org.opennms.core</groupId>
  <artifactId>org.opennms.core.model-api</artifactId>
</dependency>
<dependency>
  <groupId>org.opennms.dependencies</groupId>
  <artifactId>camel-dependencies</artifactId>
  <type>pom</type>
</dependency>
<dependency>
  <groupId>org.opennms.dependencies</groupId>
  <artifactId>spring-dependencies</artifactId>
  <type>pom</type>
</dependency>
<dependency>
  <groupId>io.swagger.core.v3</groupId>
  <artifactId>swagger-annotations</artifactId>
  <version>${swaggerVersion}</version>
</dependency>

<!-- AFTER -->
<dependency>
  <groupId>org.opennms.dependencies</groupId>
  <artifactId>spring-dependencies</artifactId>
  <type>pom</type>
  <optional>true</optional>
</dependency>
<dependency>
  <groupId>io.swagger.core.v3</groupId>
  <artifactId>swagger-annotations</artifactId>
  <version>${swaggerVersion}</version>
  <optional>true</optional>
</dependency>
<!-- model-api removed: zero source imports -->
<!-- camel-dependencies removed: zero source imports -->
```

### Why each change is safe

- **`model-api` — delete.** Grep across the entire `features/events/api/src/` tree returns zero imports of `org.opennms.core.model.*`. The dep was inherited cruft from an older layout.
- **`camel-dependencies` — delete.** Grep across the same tree returns zero imports of `org.apache.camel.*`. Same story.
- **`spring-dependencies` — optional.** Only 2 classes in the entire `events.api` source tree import Spring:
  - `AnnotationBasedEventListenerAdapter.java` — uses `org.springframework.beans.factory.{DisposableBean, InitializingBean}`, `AnnotationUtils`, `Assert`, `ClassUtils`.
  - `EventIpcManagerFactory.java` — uses `org.springframework.util.Assert`.
  Both are Spring framework utility consumers, not Spring annotation targets. `<optional>true</optional>` keeps Spring visible to the owner module's compile but stops it from propagating transitively. Consumers who instantiate either class must declare Spring themselves — and virtually every horizon core consumer already does, since Spring is the framework the core runs on.
- **`swagger-annotations` — optional.** Only `Event.java` uses `io.swagger.v3.oas.annotations.Hidden`, on a single helper getter. Swagger-annotations has no heavy transitives of its own, but marking it optional costs nothing and keeps the dep graph clean for consumers who don't serialize `Event` as an OpenAPI schema. Annotation references in bytecode are resolved lazily — the absence of the annotation class at runtime does not prevent class loading unless reflection enumerates annotations on the affected member.

### What explicitly stays

- `hibernate-jpa-2.0-api` is already `<scope>provided</scope>` and harmless. Leave alone.
- `javax.validation:validation-api` is load-bearing for `Event.java`'s `@NotNull`/`@Min`/`@Valid` annotations — keep.
- `commons-lang3`, `core.lib`, `core.logging`, `core.snmp.api`, `core.xml` — all used by source. Keep.

### Horizon release

Bump horizon parent version to `1.0.9`. Same workflow used for the `1.0.7 → 1.0.8` FlowRecord.visit NPE fix (memory: `project_horizon_flowrecord_visit_npe.md`).

## Delta-v consumption

### File 1 — `pom.xml` (delta-v reactor root)

```xml
<!-- BEFORE -->
<deltav.horizon.version>1.0.8</deltav.horizon.version>
<!-- AFTER -->
<deltav.horizon.version>1.0.9</deltav.horizon.version>
```

All horizon deps in the delta-v reactor use `${deltav.horizon.version}`, so this single bump propagates everywhere.

### File 2 — `core/flow-enricher/pom.xml`

Replace the `events.api` `<dependency>` block (currently lines 237-262, 20 lines of exclusions plus the explanatory comment) with the bare declaration:

```xml
<!-- Horizon: Events API (EventForwarder interface required by Netflow parsers). -->
<dependency>
    <groupId>org.opennms.features.events</groupId>
    <artifactId>org.opennms.features.events.api</artifactId>
</dependency>
```

### What stays untouched in delta-v

- `LoggingEventForwarder.java` — still implements `org.opennms.netmgt.events.api.EventForwarder` with the same 4 method overrides. The interface still exists on the classpath from the events.api jar; only the transitive closure around it has slimmed down.
- `LoggingEventForwarderTest.java` — unchanged.
- `FlowEnricherConfiguration.java` — unchanged. The parser bean wiring still imports `org.opennms.netmgt.events.api.EventForwarder`.
- The other 10 exclusion blocks in `core/flow-enricher/pom.xml` — out of scope per Option (a). Tracked in a followup memory.
- Four other delta-v modules that reference `events.api` (`core/opennms-model-jakarta`, `core/event-forwarder-kafka`, `core/daemon-common`, plus the reactor root `pom.xml`) — out of scope. They also benefit automatically from the horizon 1.0.9 diet; any exclusion lists they carry on `events.api` can drop in a followup cleanup batch.

## Sequencing

1. Open horizon PR against `pbrane/delta-v-horizon`. Land and merge.
2. Run the horizon release job to publish `1.0.9` to GitHub Packages.
3. Open delta-v PR against `pbrane/delta-v` (NEVER `OpenNMS/opennms`, per `feedback_never_pr_opennms`). Use `gh pr create --repo pbrane/delta-v --base develop ...`. Feature branch `feature/flow-enricher-events-api-cleanup` off the latest `develop` (per `feedback_pull_before_branching`).
4. CI pulls `1.0.9` from GitHub Packages, builds, tests green, merge.

## Verification

Three layers of verification, each gating the next.

### Layer 1 — Horizon side, before releasing 1.0.9

In `delta-v-horizon` after the pom edit:

1. `./mvnw -pl features/events/api clean install -DskipTests` — must succeed. Proves the module itself still compiles with Spring now local-only.
2. `./mvnw -pl features/events/api test` — must stay green. Proves the 2 Spring-using classes are still wired correctly within the module.
3. `./mvnw clean install -DskipTests` from horizon root — the critical gate. If any downstream horizon module was riding on `events.api`'s transitive Spring/camel/model-api, the compile fails here with a clear "cannot find symbol" or "package does not exist" error. Fix each broken module by adding the dep it was implicitly getting. Iterate until the full reactor is green.
4. Release horizon 1.0.9 via the GitHub Packages release job.

### Layer 2 — Delta-v, after horizon 1.0.9 publish

On feature branch `feature/flow-enricher-events-api-cleanup`:

1. Clean stale horizon SNAPSHOT artifacts from `~/.m2` per `feedback_clean_m2_between_branches`.
2. Bump `deltav.horizon.version` to `1.0.9` and drop the events.api exclusion block in `core/flow-enricher/pom.xml`.
3. `./mvnw -pl core/flow-enricher clean install` — must succeed.
4. **Classpath verification (the money shot):**
   ```
   ./mvnw -pl core/flow-enricher dependency:tree | grep -E "(eclipselink|hibernate-core|hibernate-jpa|camel-core|opennms-model-api|spring-context)"
   ```
   Expected output: empty. If any of those appear, the root cause isn't fully fixed and we stop.
5. Unit tests: `./mvnw -pl core/flow-enricher test` — `LoggingEventForwarderTest` and all Phase 2 parser bridge tests must stay green.
6. Integration tests: run in the same phase because flow-enricher surefire includes `*IT` per `pom.xml` line 407. Parser bridge IT tests, splitter, enricher — all must stay green.

### Layer 3 — End-to-end runtime (recommended, not strictly blocking)

Compile-time and dependency-tree checks guarantee nothing statically depends on eclipselink/hibernate-3. But the parser path only exercises `EventForwarder.sendNow()` when a real `EventBuilder` is constructed — which happens on clock-skew or illegal-flow detection. To get runtime coverage:

1. Rebuild all 12 daemon boot jars per `feedback_rebuild_all_daemons`, then rebuild the delta-v Docker image: `cd opennms-container/delta-v && ./build.sh deltav`.
2. Start the E2E Docker Compose stack including hsflowd (the test sflow exporter from PR #153).
3. Tail flow-enricher logs while flows are generated. Confirm the service parses flows without `NoClassDefFoundError`, `ClassNotFoundException`, or any other classpath-induced runtime fault.
4. Optional trigger: advance a container clock by >10s and wait for a netflow packet. Confirm the log shows `Parser event dropped (logging-only forwarder): uei=uei.opennms.org/internal/telemetry/clockSkewDetected` — the only way to prove `EventForwarder.sendNow()` actually fires through the new classpath.

## Rollback

- **Horizon:** revert the pom edit. Cut horizon 1.0.10 containing only the revert.
- **Delta-v:** already on a feature branch, so just don't merge. If 1.0.9 was already pulled into `develop`, revert the delta-v PR and pin `deltav.horizon.version` back to 1.0.8 until horizon 1.0.10 is ready.

Same model used for every prior horizon release in the `delta-v-horizon` repo — clean provenance, each version tag a single logical change.

## Risks

### R1 — Horizon reactor compile cascade (moderate likelihood, low blast radius)

Likely. When `events.api` stops transitively leaking Spring/camel/model-api, some horizon modules that implicitly relied on the transit will fail to compile. The risk isn't that we can't fix these — it's that the horizon PR grows beyond "one pom edit" into "one pom edit plus N downstream modules each needing a direct dep declaration."

**Mitigation:** the full-reactor compile in Verification Layer 1 flushes all such modules in a single run. Each fix is mechanical (add the dep to the broken module's pom).

**Acceptance threshold:** if the cascade exceeds ~10 downstream modules, stop and reconsider extracting a new `events/api-minimal` module instead (Option 3 from earlier brainstorming). That keeps the blast radius on consumers who opt in rather than on every downstream module.

### R2 — Swagger optional may surprise OpenAPI tooling (low likelihood, low blast radius)

`@Hidden` on `Event.java` is load-bearing for hiding a helper getter from OpenAPI schema generation. If a downstream horizon module serializes `Event` through swagger tooling without declaring swagger directly, its OpenAPI schema output could change — not break, but change. Since delta-v no longer ships the legacy webapp (`project_webapp_removed`), this does not affect delta-v.

**Mitigation:** horizon full-reactor compile catches modules that need swagger at compile time. Runtime OpenAPI drift is horizon's responsibility to catch in its own consumer-level smoke tests.

### R3 — Horizon release cycle overhead (scheduling, not technical)

Two-repo sequencing requires one horizon release between the horizon PR merge and the delta-v PR. Roughly a half-day of operator time based on the `1.0.7 → 1.0.8` cadence. Not a technical blocker.

**Mitigation:** none needed — the "1-2 days across two repos" budget on the queued task already accounts for it.

### R4 — Undiscovered runtime reflection (low likelihood, high blast radius)

Compile-time and dependency-tree checks cannot rule out runtime reflection that enumerates annotations on `Event`. If some horizon code tries to load `io.swagger.v3.oas.annotations.Hidden` via reflection and swagger-annotations isn't on the classpath, the reflection call throws `TypeNotPresentException`. For delta-v flow-enricher, the only code that touches `Event` is horizon's `EventBuilder.setUei().setTime()...getEvent()` chain in `ParserBase.java:319`, which does not reflect on annotations — delta-v is safe.

**Mitigation:** Verification Layer 3 (runtime E2E with hsflowd + triggered clock skew) is the definitive proof for the delta-v path. Horizon's own smoke tests cover horizon's arbitrary consumers.

## Success Criteria

- [ ] Horizon `features/events/api/pom.xml` diet merged and released as 1.0.9.
- [ ] Horizon full-reactor build green after the diet, either unchanged or with any cascade-fix dep declarations added.
- [ ] Delta-v `deltav.horizon.version` bumped to 1.0.9.
- [ ] `core/flow-enricher/pom.xml` `events.api` dep block has zero exclusions.
- [ ] `./mvnw -pl core/flow-enricher dependency:tree` grep for `eclipselink|hibernate-core|hibernate-jpa|camel-core|opennms-model-api|spring-context` returns empty.
- [ ] `LoggingEventForwarderTest` and all flow-enricher unit + IT tests pass.
- [ ] (Recommended) E2E runtime verification with hsflowd shows no classpath-induced runtime errors.
- [ ] Followup memory opened tracking cleanup of the remaining 10 exclusion blocks in `core/flow-enricher/pom.xml` plus the 4 other delta-v modules that reference `events.api`.

## Followup work (out of scope for this PR)

- Batched "flow-enricher classpath hygiene" cleanup: audit the other 10 exclusion blocks in `core/flow-enricher/pom.xml` and the `events.api` references in `core/opennms-model-jakarta`, `core/event-forwarder-kafka`, `core/daemon-common`. Any block whose transitive closure is naturally clean after horizon 1.0.9 gets its exclusions dropped.
- Same batched cleanup can fold in any additional horizon pom diets discovered during Layer 1's full-reactor build (if other horizon modules show equally over-specified dep lists).
