# Next Session: sFlow Test Exporter + E2E Verification

> Copy everything below the line into the next Claude Code conversation.

---

## Context

As of **PR #151 (merged into `develop`)**, sFlow is wired into the
flow-enricher production Spring context. `SFlowUdpParser` instantiates
successfully via the Phase 2 `LogPreservingThreadFactory` shim, the
Spring Cloud Stream consumer group subscribes to
`OpenNMS.Sink.Telemetry-SFlow-0..3`, and `ParserLifecycle` logs
`Started horizon parser SFlow` alongside the three Netflow parsers at
boot. `FlowEnrichmentStreamBinderIT` uses the **real** `SFlowUdpParser`
bean (not a Mockito mock like the three Netflow parsers), so the shim is
exercised at `mvn test` speed on every future change — a regression gets
caught in 3 seconds instead of a 3-minute container rebuild.

**What's missing:** there is no sFlow-generating exporter in the test
bed. The existing `flow-default-testnode-1` service runs `softflowd`,
which emits Netflow v9 only. So `test-flows-e2e.sh` has zero runtime
coverage of the full sFlow path:

- real UDP wire-format bytes hitting Minion
- Minion bridging to `OpenNMS.Sink.Telemetry-SFlow-*`
- flow-enricher's real `SFlowUdpParser.parse()` decoding BSON
- `SFlowAdapter` emitting `FlowDocument`s
- ClickHouse `flows_raw` receiving sFlow rows

The sFlow consumer is live but idle. This session closes that gap.

## Primary Work: Add an sFlow exporter to the E2E stack

**Estimated effort:** half a day (1-2 hours of Dockerfile + compose
wrangling, 30 minutes of E2E script updates, 30-60 minutes of
troubleshooting).

### Step 1: Inspect the existing flow-exporter pattern

The existing Netflow v9 exporter lives at
`opennms-container/delta-v/flow-exporter/`:

- `Dockerfile` — Alpine 3.21 + `softflowd` + `net-snmp` + `bash`
- `entrypoint.sh` — runs `snmpd`, starts `softflowd -v 9 -n minion:4729 -i eth0`, then loops generating synthetic traffic via `ping`/`curl` against `kafka`, `postgres`, `minion-default-01`
- `snmpd.conf` — SNMP v2c community string for SNMP testing

The compose service is `flow-default-testnode-1:` in
`opennms-container/delta-v/docker-compose.yml` (around line 565):
```yaml
  flow-default-testnode-1:
    profiles: [full]
    build: ./flow-exporter
    container_name: flow-default-testnode-1
    hostname: flow-default-testnode-1
    cap_add:
      - NET_RAW
    depends_on:
      minion:
        condition: service_healthy
    environment:
      NETFLOW_COLLECTOR: minion:4729
      NETFLOW_VERSION: "9"
      TRAFFIC_INTERVAL: "5"
```

### Step 2: Create a sibling `sflow-exporter/` directory

Parallel structure to `flow-exporter/`. Three options for the exporter
itself, pick one and stick with it:

**Option A: `hsflowd` on Alpine (preferred)**

Alpine's `host-sflow` package installs `hsflowd`, the InMon reference
implementation. It exports system-level sFlow samples (CPU, memory, NIC
counters) and packet samples if configured. Cleanest match to "production
sFlow exporter" shape.

```dockerfile
FROM alpine:3.21
RUN apk add --no-cache host-sflow bash curl iputils
COPY hsflowd.conf /etc/hsflowd.conf
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
```

`hsflowd.conf` needs to declare:
- `collector { ip = minion port = <SFLOW_PORT> }` — check Minion's UDP
  listener config for the sFlow port number; it may be `6343` (standard
  sFlow) or may share `4729` with Netflow if the listener handles both
- `sampling = 100` and `polling = 20`
- An interface to sample (`eth0`)

**Option B: `sflowtool -g` (generator mode)**

`sflowtool` is primarily a consumer/decoder, but newer versions (2.x)
include a generator mode. Less conventional; prefer Option A unless
hsflowd turns out to be a pain.

**Option C: Extend `flow-exporter` to emit both protocols**

Adds `host-sflow` to the existing container, runs both `softflowd` and
`hsflowd` simultaneously, uses two separate environment variables to
switch protocols. Less isolation but one fewer service.

**Recommendation: Option A.** Sibling directory, separate service,
cleanest to reason about.

### Step 3: Verify Minion's sFlow listener port

Before wiring compose, check where Minion expects sFlow. Search for
`SFlow` listener configuration:

```bash
grep -rn "SFlow\|sflow\|6343" \
  opennms-container/delta-v/opennms-minion-*/etc/ \
  opennms-container/deploy/minion-overlay/ 2>/dev/null
```

If Minion has an sFlow UDP listener already configured (likely on
port `6343`), point `hsflowd.conf` at that port. If Minion is configured
to receive all flow protocols on `4729`, use that — horizon parsers have
a `handles()` method that inspects the first bytes of each datagram, so
a single UDP listener can route by content.

If Minion has NO sFlow listener configured, that's a second piece of
work for this session: add the listener to Minion's telemetryd config
(mirror the existing Netflow-9 listener block but with `parser-class =
SFlowUdpParser`). Note this does NOT affect the flow-enricher — the
enricher has no telemetryd listener itself; Minion is the one holding
the UDP socket.

### Step 4: Add the compose service

Append to `opennms-container/delta-v/docker-compose.yml` right after
`flow-default-testnode-1`:

```yaml
  flow-sflow-testnode-1:
    profiles: [full]
    build: ./sflow-exporter
    container_name: flow-sflow-testnode-1
    hostname: flow-sflow-testnode-1
    cap_add:
      - NET_RAW
    depends_on:
      minion:
        condition: service_healthy
    environment:
      SFLOW_COLLECTOR: minion:<PORT>
      SAMPLING_RATE: "100"
      POLLING_INTERVAL: "20"
```

### Step 5: Extend `test-flows-e2e.sh` to assert sFlow rows

The current script asserts Netflow v9 rows via a total count on
`deltav.flows_raw`. For sFlow, add a protocol-filtered assertion. The
relevant `ClickHouse` column is likely `netflow_version` (check the
`flows_raw` DDL — probably a String enum with values `V5`, `V9`,
`IPFIX`, `SFLOW`).

Add a new assertion block after the existing Phase 2 check:

```bash
log "Phase 2b: Waiting for sFlow data in flows_raw..."
SFLOW_QUERY="SELECT count() FROM deltav.flows_raw WHERE netflow_version = 'SFLOW' AND timestamp > now() - INTERVAL 10 MINUTE"
if wait_for_ch "$SFLOW_QUERY" "$CH_TIMEOUT" "sFlow data in flows_raw"; then
  SFLOW_COUNT=$(ch_query "$SFLOW_QUERY" 2>/dev/null || echo "0")
  ok "flows_raw has ${SFLOW_COUNT} sFlow records in the last 10 minutes"
else
  fail "No recent sFlow rows in flows_raw within ${CH_TIMEOUT}s"
fi
```

Verify the `netflow_version` enum value first (it could also be `sflow`,
`SFlow`, or a numeric code — inspect a sample row once some sFlow data
flows):
```bash
docker compose exec -T clickhouse clickhouse-client \
  --query "SELECT DISTINCT netflow_version FROM deltav.flows_raw LIMIT 10"
```

### Step 6: Run the E2E

```bash
cd /Users/david/development/src/opennms/delta-v/opennms-container/delta-v
SKIP_TESTS=true ./build.sh deltav 2>&1 | tail -10  # only if flow-enricher changed (it shouldn't)
docker compose up -d --build flow-sflow-testnode-1
sleep 30  # let hsflowd send a few samples
docker compose logs flow-sflow-testnode-1 | tail -20  # verify hsflowd started cleanly
./test-flows-e2e.sh 2>&1 | tail -30
```

**Expected result:** 16 passed / 1 failed (the 1 failure is still the
pre-existing `exporter_node_id > 0` node-provisioning issue). Netflow
v9 count should be roughly unchanged (~600+ flows over 10 min); sFlow
count should be >= 1 (hsflowd's counter polls send sFlow datagrams every
20s even with no real traffic).

**If the sFlow count is 0:** check

1. `docker compose logs minion | grep -i sflow` — did Minion accept the
   UDP datagrams? Look for `SFlow parser` references or errors about
   unknown datagram versions on the Netflow listener.
2. `docker compose logs flow-enricher | grep -i sflow` — did the
   enricher see `Telemetry-SFlow` Sink messages? Look for dispatch map
   hits.
3. `docker compose exec clickhouse clickhouse-client --query "SELECT DISTINCT netflow_version FROM deltav.flows_raw"` —
   what enum values are actually in the data?

### Step 7: Commit and PR

```bash
git checkout develop && git pull origin develop
git checkout -b feature/sflow-test-exporter
# ... make edits from Steps 2, 4, 5 ...
git add opennms-container/delta-v/sflow-exporter/ \
        opennms-container/delta-v/docker-compose.yml \
        test-flows-e2e.sh
# plus any Minion listener config if Step 3 required it
git commit -m "feat(e2e): add sFlow test exporter for flow-enricher verification

Closes out the sFlow wiring arc started in PR #151 by adding a live
sFlow exporter (hsflowd on Alpine) to docker-compose and extending
test-flows-e2e.sh to assert sFlow rows in deltav.flows_raw.

Verified: X sFlow records ingested over 10 minutes alongside the
existing Netflow v9 flow from flow-default-testnode-1."
git push -u origin feature/sflow-test-exporter
gh pr create --repo pbrane/delta-v --base develop \
  --title "feat(e2e): add sFlow test exporter" \
  --body "..."
```

## Alternative Work (if sFlow exporter hits a wall)

### Option A: Dropwizard → Micrometer metrics bridge

**Memory:** `project_flow_enricher_actuator_metrics_bridge.md`

`flow_enricher_*`-prefixed parser metrics don't appear at
`/actuator/prometheus` even though `FlowEnricherMicrometerBridge` is
wired. Kafka consumer metrics work fine; only Dropwizard-backed parser
metrics are invisible. 1-3 hours. Unblocks ops visibility into parser
throughput, template cache, parse errors.

### Option B: Raise SCS consumer concurrency

**Memory:** `project_flow_enricher_concurrency_raise.md`

Currently `concurrency=1` because `ThreadLocalDispatcher` is a shared
singleton with a `ReentrantLock`. Raising concurrency needs per-parser
dispatcher instances. Pick up when consumer lag becomes observable.

### Option C: Minion-side flow aggregation

**Memory:** `project_flow_enricher_minion_aggregation.md`

`FlowSinkModule.getAggregationPolicy()` returns `null`, so each UDP
datagram is one Kafka message. Horizon's canonical batch is 1000
messages / 500ms per exporter. Pick up when producer throughput hurts.

## Important Reminders

- **Never create PRs against `OpenNMS/opennms`** — always use
  `--repo pbrane/delta-v --base develop`.
- **Always `git pull origin develop`** before creating each feature
  branch.
- **Feature branches only** — never commit directly to develop.
- **Build tool is `./mvnw`** — not `./compile.pl` or `maven/bin/mvn`
  (those are horizon-only).
- **Uncommitted provisioning drift** under
  `opennms-container/delta-v/etc/imports/*.xml` and
  `opennms-container/delta-v/provisiond-overlay/etc/imports/*.xml`
  is runtime `last-import=` timestamp pollution. Discard with
  `git checkout --`; do NOT commit.
- **New delta-v code goes in `org.deltav.*` packages** with the
  BeaconStrategists 2026 AGPL v3 header. This session primarily edits
  shell scripts, Dockerfiles, and YAML, so the package rule mostly
  doesn't apply — but the license header rule still does for any new
  source files you touch.
- **`opennms-util` is production-banned.** Not relevant to this
  session's shell/YAML work, but keep it in mind if you end up touching
  any Java code.

## Quick Start

```bash
cd /Users/david/development/src/opennms/delta-v
git fetch origin && git checkout develop && git pull origin develop
git log --oneline -5  # confirm PR #151 merge commit is present
git checkout -b feature/sflow-test-exporter
# ... work from Step 1 ...
```
