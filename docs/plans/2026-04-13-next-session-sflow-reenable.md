# Next Session: sFlow Re-Enable + Phase 2 Followups

> Copy everything below the line into the next Claude Code conversation.

---

## Context

As of 2026-04-13 end-of-day, **Phase 2 of the flow-enricher parser bridge
has shipped (PR #149, merged into `develop`)**. The flow pipeline is
live: softflowd on `flow-default-testnode-1` → UDP 4729 → Minion →
Kafka Sink → flow-enricher (with server-side Netflow v9 parser) →
enriched `FlowDocument` → ClickHouse `deltav.flows_raw`. Live E2E
verification confirmed **205 real Netflow v9 flows decoded in 10
minutes** with all 4 dimension MVs populated.

Phase 2 shipped with three surprises worth knowing about for this
session's work:

1. **Dispatcher redesign (Task 9):** The original `ThreadLocal`-based
   `CapturingDispatcher` design was wrong — horizon's `ParserBase`
   dispatches flow records on a private background thread pool, so
   `ThreadLocal.get()` returned null on the worker thread. Replaced
   with a `ReentrantLock + AtomicReference<CapturingDispatcher>`
   design. Class is still named `ThreadLocalDispatcher` for history
   but its javadoc explicitly rebrands it as "call-scoped." Serializes
   parse calls per parser instance; consistent with
   `concurrency=1` on the Spring Cloud Stream consumer.

2. **`LogPreservingThreadFactory` production shim (Task 12):** Horizon's
   `ParserBase.start()` constructs
   `org.opennms.core.concurrent.LogPreservingThreadFactory` (from the
   banned `opennms-util` module) for its internal async dispatch pool.
   Flow-enricher startup failed at first real container launch with
   `NoClassDefFoundError`. Fix: shipped a delta-v-local shim class at
   the same FQN
   (`core/flow-enricher/src/main/java/org/opennms/core/concurrent/LogPreservingThreadFactory.java`)
   — JVM class loading resolves by FQN, so horizon's parsers find our
   shim and use it. Minimal daemon-thread factory; MDC preservation is
   intentionally dropped.

3. **sFlow deferred from production Spring context (Task 5/6):** When
   the sFlow classpath gap was first discovered (before Task 12's
   shim), we unwired sFlow from `FlowEnricherConfiguration`. The
   `SFlowMessageProcessor` class and its unit test remain in the
   codebase; the Task 10 `SFlowParserBridgeIT` even instantiates a
   real `SFlowUdpParser` in tests (using test-scoped `opennms-util`).
   The shim that later unblocked production Netflow parsers may also
   unblock sFlow — but this was never verified because we were
   powering through to PR.

## Primary Work for This Session: Re-Enable sFlow in Production

**Hypothesis:** The Task 12 shim covers sFlow too. `SFlowUdpParser`'s
only known reference to `opennms-util` is `LogPreservingThreadFactory`
(same as the Netflow parsers), and we now have a local class with that
FQN on the production classpath. Re-enabling sFlow should be a 30-minute
code change plus a container restart and a verification.

**Estimated effort:** 30-60 minutes end-to-end.

### Step 1: Verify sFlow's classpath cleanliness under the shim

Before touching wiring, confirm that `SFlowUdpParser` doesn't reach
into any OTHER banned class we haven't shimmed. From the repo root:

```bash
grep -rn "import org\.opennms\.\(core\|netmgt\.model\)" \
  .claude/worktrees/provisiond-spring-boot/features/telemetry/protocols/sflow/parser/src/main/java/ \
  | grep -v "telemetry\|dnsresolver\|distributed\.core"
```

Expected: either empty output (meaning only shimmed
`LogPreservingThreadFactory` is pulled) or a short list of classes we
need to write additional shims for. If additional shims are needed,
add them under `core/flow-enricher/src/main/java/org/opennms/` mirroring
the FQN of each missing class.

### Step 2: Re-add sFlow beans to `FlowEnricherConfiguration`

In `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java`,
add back the three things Task 5/6 removed:

1. An `import` for `org.opennms.netmgt.telemetry.protocols.sflow.parser.SFlowUdpParser`
   and `org.deltav.flows.enricher.protocol.SFlowMessageProcessor`.

2. An `sflowUdpParser()` `@Bean` method:
   ```java
   @Bean
   SFlowUdpParser sflowUdpParser(
           ThreadLocalDispatcher threadLocalDispatcher,
           DnsResolver flowParserDnsResolver) {
       return new SFlowUdpParser(
               "SFlow",
               threadLocalDispatcher,
               flowParserDnsResolver);
   }
   ```

3. An `sflowProcessor()` `@Bean` method:
   ```java
   @Bean
   SFlowMessageProcessor sflowProcessor(
           SFlowUdpParser sflowUdpParser,
           MetricRegistry flowEnricherMetricRegistry,
           ThreadLocalDispatcher threadLocalDispatcher) {
       return new SFlowMessageProcessor(
               sflowUdpParser,
               new SimpleAdapterDefinition("SFlow"),
               flowEnricherMetricRegistry,
               threadLocalDispatcher);
   }
   ```

4. Add `SFlowMessageProcessor sflowProcessor` to the
   `flowEnrichmentFunction()` method's parameters, and add
   `"Telemetry-SFlow", sflowProcessor` to the `dispatchMap`.

5. Add `sflowUdpParser` to the `flowParserLifecycle()` bean's parser
   list so its `start()` / `stop()` lifecycle runs.

6. Delete the "Deliberately no SFlow*" comment blocks in the file
   (they're now stale).

### Step 3: Re-add sFlow to the integration test mocks

In `core/flow-enricher/src/test/java/org/deltav/flows/enricher/integration/FlowEnrichmentStreamBinderIT.java`,
add back:

1. `import org.opennms.netmgt.telemetry.protocols.sflow.parser.SFlowUdpParser;`
2. `@MockitoBean private SFlowUdpParser sflowUdpParser;`
3. `configurePassthroughParser(sflowUdpParser);` in `setUp()`
4. Restore the `SFLOW_DESTINATION` constant
5. Optionally: restore the `sflowMessageProducesEnrichedFlowDocuments` test
   method that was removed — the one that feeds a BSON sample into the
   mock and verifies 5 enriched FlowDocuments. The helper methods
   `loadFixtureAsBsonBytes()` and `encodeBsonDocument()` also need
   restoring. Check `git show c30369c5938 -- ...FlowEnrichmentStreamBinderIT.java`
   for the removal diff and reverse it; the test-packets/sflow-sample.json
   fixture should already exist under `src/test/resources/`.

### Step 4: Run unit + integration tests

```bash
cd /Users/david/development/src/opennms/delta-v/core/flow-enricher
../../mvnw test 2>&1 | tail -15
```

Expected: 120+ tests pass. Pre-existing count was 120; adding sFlow
back could raise it to 121 (if the sFlow IT test is restored).

### Step 5: Rebuild + restart + live verify

```bash
cd /Users/david/development/src/opennms/delta-v/opennms-container/delta-v
SKIP_TESTS=true ./build.sh deltav 2>&1 | tail -10
docker compose up -d --force-recreate --no-deps flow-enricher
sleep 10
docker compose logs --tail 30 flow-enricher | grep -E "Started|ERROR|Caused by"
```

Expected: flow-enricher starts cleanly within ~3 seconds. If you see a
new `NoClassDefFoundError` or `ClassNotFoundException`, go back to Step
1 and shim the missing class.

If it starts cleanly, the hypothesis is confirmed — the shim covers
sFlow. Run the E2E to confirm Netflow v9 still works (sFlow has no test
exporter yet, so live sFlow verification is a separate followup):

```bash
./test-flows-e2e.sh 2>&1 | tail -25
```

Expected: same 15-passed/1-failed result as Phase 2 (the 1 FAIL is the
unrelated node-provisioning issue).

### Step 6: Commit and PR

Single commit on a fresh feature branch:

```bash
git checkout develop && git pull origin develop
git checkout -b feature/flow-enricher-sflow-reenable
# ... make edits from Steps 2, 3 ...
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/integration/FlowEnrichmentStreamBinderIT.java
git commit -m "feat(flow-enricher): re-enable sFlow in production Spring context

The Phase 2 LogPreservingThreadFactory shim (commit 12f015b259f) covers
the classpath gap that originally forced sFlow to be unwired.
Verified: flow-enricher starts cleanly with sFlow bean wiring, and
the existing Netflow v9 E2E path continues to work.

Unblocks the sFlow live verification followup: the next step is to
add an sFlow-generating test exporter to docker-compose (sflowtool
container) so test-flows-e2e.sh can verify sFlow rows in flows_raw."
git push -u origin feature/flow-enricher-sflow-reenable
gh pr create --repo pbrane/delta-v --base develop \
    --title "feat(flow-enricher): re-enable sFlow in production" \
    --body "..."
```

**DO NOT:** add `opennms-util` as a production dependency. The shim
is the point. The test-scope `opennms-util` added in Task 9 stays as-is.

## Alternative Work (If sFlow is Already Working or Deprioritized)

Two other Phase 2 followups are ready to pick up. Both are tracked in
`~/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/`:

### Option A: Fix the Dropwizard → Micrometer metrics bridge

**Memory:** `project_flow_enricher_actuator_metrics_bridge.md`

**Problem:** `flow_enricher_*`-prefixed parser metrics don't appear at
`/actuator/prometheus` even though `FlowEnricherMicrometerBridge` is
wired. Kafka consumer metrics (from Spring Cloud Stream's native
binder) work fine; only the Dropwizard-backed parser metrics are
invisible.

**Estimated effort:** 1-3 hours depending on which of the three likely
causes applies. The memory has diagnostic commands and fix sketches.

**Value:** Unblocks ops visibility into parser throughput, template
cache size, and parse errors. Relevant for the first production
incident where Kafka-layer metrics aren't enough.

### Option B: Minion-side flow aggregation

**Memory:** `project_flow_enricher_minion_aggregation.md`

**Problem:** `FlowSinkModule.getAggregationPolicy()` returns `null`, so
each UDP datagram is one Kafka message. Under heavy traffic this is the
producer-side bottleneck. Horizon's canonical batch is 1000 messages /
500 ms per exporter.

**Estimated effort:** 1-2 hours of code + a real traffic measurement
session.

**Value:** Throughput improvement under sustained load. Not needed
until traffic volumes justify the change.

## Phase 2 Followup Backlog (Full List)

Tracked in memory under names starting with `project_flow_enricher_`:

| Memory | Status | Trigger |
|---|---|---|
| `sflow_live_verification` | FUTURE | This session's primary work |
| `actuator_metrics_bridge` | FOLLOWUP | Ops incident or dashboard need |
| `concurrency_raise` | FUTURE | Consumer lag on Sink topics |
| `real_clock_skew` | FUTURE | Timestamp alignment complaint |
| `minion_aggregation` | FUTURE | Kafka producer bottleneck |
| `events_api_local_stub` | FOLLOWUP | Part of horizon cleanup sweep |
| `sflow_classpath_gap` | PARTIALLY UNBLOCKED | Reference / history |
| `call_scoped_dispatcher_fix` | DONE | Reference / history |

Plus the broader `horizon_next_cleanup_batch.md` — a batched sweep to
delete ~145 interim exclusion lines from `core/flow-enricher/pom.xml`
when horizon releases its next cleanup version.

## Important Reminders

- **Never create PRs against `OpenNMS/opennms`** — always use
  `--repo pbrane/delta-v --base develop`.
- **Always `git pull origin develop`** before creating each feature
  branch. Develop now includes PR #149.
- **Feature branches only** — never commit directly to develop.
- **Build tool is `./mvnw`** — not `./compile.pl` or `maven/bin/mvn`
  (those are horizon-only, documented in
  `feedback_delta_v_uses_mvnw_not_compile_pl.md`).
- **Uncommitted provisioning drift** under
  `opennms-container/delta-v/etc/imports/*.xml` and
  `opennms-container/delta-v/provisiond-overlay/etc/imports/*.xml`
  is runtime `last-import=` timestamp pollution. Discard with
  `git checkout --`; do NOT commit. See
  `feedback_provisiond_requisition_drift.md`.
- **New delta-v code goes in `org.deltav.*` packages** with the
  BeaconStrategists 2026 AGPL v3 header. The exception for this session
  is the `LogPreservingThreadFactory` shim at
  `org.opennms.core.concurrent.*` — that FQN is deliberate to shadow
  horizon's class. If you need to add more shim classes, they too go
  under the `org.opennms.*` FQN they're shadowing, and documented with
  an explicit javadoc explaining why the FQN deviates from
  `org.deltav.*`.
- **`opennms-util` is production-banned.** If a new class needs it at
  runtime, shim the specific class locally, don't add the module as a
  dep.

## Quick Start

```bash
cd /Users/david/development/src/opennms/delta-v
git fetch origin && git checkout develop && git pull origin develop
git log --oneline -5  # confirm PR #149 merge commit is present
git checkout -b feature/flow-enricher-sflow-reenable
# ... work from Step 1 ...
```
