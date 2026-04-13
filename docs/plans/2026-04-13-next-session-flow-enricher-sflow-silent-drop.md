# Next Session: Fix flow-enricher sFlow silent drop + Netflow9 parser NPE

> Copy everything below the line into the next Claude Code conversation.

---

## Context

As of **PR #153 (merged into `develop` on 2026-04-13)**, delta-v has a
live sFlow test exporter (`flow-sflow-testnode-1`) running hsflowd
against the shared Minion UDP 4729 flow port. PR #154 (merged the same
day) added `check_daemon_boot_freshness()` as Phase 0 of
`do_deltav_images`, closing the stale-JAR failure mode that bit us
during the previous session.

When you ran `./test-flows-e2e.sh` at the end of the last session, this
is what the pipeline looked like end-to-end:

| Stage | Netflow v9 (softflowd) | sFlow (hsflowd) |
|---|---|---|
| Minion UDP 4729 bind | ✅ | ✅ |
| `/proc/net/udp6` shows `:1279` | ✅ | ✅ |
| `FlowUdpListener` "Flow telemetry listener started" log | ✅ | ✅ |
| Messages produced to `OpenNMS.Sink.Telemetry-*` Kafka topic | ✅ (~1800 total) | ✅ (~300 total in a few min) |
| `flow-enricher` consumer reaches lag=0 | ✅ | ✅ |
| Rows appear in `deltav.flows_raw` | ✅ (~700/10min) | ❌ **zero rows** |

**sFlow messages are consumed by the enricher and silently dropped.**
No `WARN` or `ERROR` lines mention sFlow. No `FlowDocument`s emerge on
the `deltav-flows` topic with `netflow_version='SFLOW'`. The test
exporter from PR #153 has proven the Minion→Kafka half of the path
works; the gap is strictly inside flow-enricher.

**The `test-flows-e2e.sh` Phase 2 per-protocol loop is strict-failing
on the sFlow assertion today.** That failure is the reproducer for the
work in this session. When this session succeeds, `test-flows-e2e.sh`
should print **all passing** with both V9 and SFLOW row counts > 0.

## Secondary finding: Netflow9 parser is leaking exceptions

While debugging the sFlow issue, two distinct intermittent exceptions
showed up on the **Netflow9** path (sourceAddr=172.18.0.21,
flow-default-testnode-1 / softflowd):

```
WARN  d.f.e.p.AbstractProtocolMessageProcessor : Parser Netflow9UdpParser failed on message log (1 entries, sourceAddr=172.18.0.21):
  Cannot invoke "org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage.getBuffer()" because "msg" is null

java.lang.NullPointerException: Cannot invoke "...TelemetryMessage.getBuffer()" because "msg" is null
    at org.deltav.flows.enricher.protocol.AbstractProtocolMessageProcessor.synthesizeParsedLog(AbstractProtocolMessageProcessor.java:204)
    at org.deltav.flows.enricher.protocol.AbstractProtocolMessageProcessor.runParser(AbstractProtocolMessageProcessor.java:176)
    at org.deltav.flows.enricher.protocol.AbstractProtocolMessageProcessor.process(AbstractProtocolMessageProcessor.java:114)
    at org.deltav.flows.enricher.FlowEnrichmentFunction.processMessage(FlowEnrichmentFunction.java:171)
```

Plus an earlier `java.lang.ArrayIndexOutOfBoundsException: Index 1 out
of bounds for length 0` from thread names like `[flow-9-Thread-*]`
(also Netflow9, not sFlow).

Netflow v9 is still producing hundreds of rows per minute because the
exceptions are intermittent — only some batches hit the broken code
path. But the exceptions are loud log noise and indicate a real defect.
These are the **secondary work** in this session if time allows.

## Primary Work: Diagnose and fix the sFlow silent drop

**Estimated effort:** 1-3 hours, depending on how buried the defect is.

### Step 1: Reproduce

The test bed from PR #153 is the reproducer. From a clean stack:

```bash
cd /Users/david/development/src/opennms/delta-v
git fetch origin && git checkout develop && git pull origin develop
cd opennms-container/delta-v

# Bring up the full stack (skip if already up)
docker compose --profile full up -d

# Verify hsflowd is sending
docker compose logs flow-sflow-testnode-1 --since 1m | tail -20

# Verify Minion received and bridged to Kafka
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group deltav-flow-enricher \
  | grep Telemetry-SFlow

# Verify zero rows in ClickHouse
docker compose exec -T clickhouse clickhouse-client \
  --user deltav --password deltav \
  --query "SELECT count() FROM deltav.flows_raw WHERE netflow_version='SFLOW'"
```

Expected baseline: `Telemetry-SFlow` offsets grow steadily, consumer
lag stays 0, and the ClickHouse query returns `0`.

### Step 2: Find the silent drop point

The flow-enricher sFlow code path is:

1. `AbstractProtocolMessageProcessor.process()` — entry point for each
   Kafka message (file:
   `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/AbstractProtocolMessageProcessor.java`)
2. `synthesizeParsedLog()` and `runParser()` — drive horizon's
   `SFlowUdpParser` on the datagram bytes
3. The parsed sFlow samples flow to `SFlowAdapter` (horizon class) which
   converts them to `FlowDocument` protobufs
4. The splitter fans out one `FlowDocument` per sample into the
   `deltav-flows` Kafka topic

The "silent" part is the most interesting signal. No log output + zero
output usually means one of:

**(a) The parser runs and returns an empty sample collection.** Likely
if hsflowd is sending counter samples only (sampling=1 with ping/curl
traffic should also produce packet samples, but worth confirming). If
`SFlowAdapter` filters counter samples (it should — they're not flow
records), then "0 flow records extracted" is the silent drop.

**(b) The parser runs but throws an exception that `runParser` swallows
into `DEBUG` logging instead of `WARN`.** Check the catch blocks around
line 176.

**(c) The splitter sees a non-empty `List<FlowDocument>` but the
downstream binding fails to serialize or publish.** Less likely given
that V9 works through the same binding.

**Suggested diagnostic strategy:**

1. **Bump `org.deltav.flows.enricher.protocol` and
   `org.opennms.netmgt.telemetry.protocols.sflow` to `DEBUG`** in the
   flow-enricher's `logback-spring.xml` (or via `logging.level.*`
   overrides in `application.yml`). Rebuild, restart, rerun the E2E.
2. **Capture a raw sFlow datagram from the wire** via `tcpdump` on the
   flow-enricher side — no, Minion side — so you can see exactly what
   hsflowd is emitting:
   ```bash
   docker exec -i delta-v-minion sh -c 'apk add --no-cache tcpdump 2>/dev/null || true'
   docker exec -it delta-v-minion tcpdump -i any -w /tmp/sflow.pcap -c 5 udp port 4729 -U
   docker cp delta-v-minion:/tmp/sflow.pcap /tmp/sflow.pcap
   tcpdump -r /tmp/sflow.pcap -xx  # hex dump
   ```
   Then feed those bytes through horizon's `SFlowUdpParser` in a unit
   test to see what comes out.
3. **Check `FlowEnrichmentStreamBinderIT`'s sFlow fixture** —
   `core/flow-enricher/src/test/java/org/deltav/flows/enricher/integration/FlowEnrichmentStreamBinderIT.java`
   is supposed to use the real `SFlowUdpParser`. What bytes does it
   feed? Pre-canned from a fixture file? Does it actually produce
   `FlowDocument`s at the end? If the IT passes today, figure out why
   it diverges from the live path — that's where the gap is.

### Step 3: Hypothesis to check first — hsflowd wire format

Horizon's `SFlowUdpParser` was written against InMon's XDR-encoded
sFlow v5 format (RFC 3176). hsflowd emits exactly this — but there are
options for:

- Sample datagram size (jumbo frames vs IP-MTU-bounded)
- Sample header truncation length (hsflowd defaults to 128 bytes;
  horizon's parser may expect longer)
- Agent address family (IPv4 vs IPv6; `agent = eth0` picks the first
  IP on the interface, which in our Docker network is IPv4)

The quickest way to rule these out: configure horizon's
`sflowtool`-compatible decoder (or just
`https://github.com/sflow/sflowtool` locally) to read the pcap from
Step 2.3 and see if it parses. If sflowtool can parse the datagrams
cleanly but horizon's `SFlowUdpParser` can't, that's the bug.

### Step 4: Implement the fix

Depending on the root cause, one of:

- **If SFlowAdapter silently drops counter samples:** that's correct
  behavior (they aren't flow records), but we need packet samples to
  flow through. The hsflowd config in PR #153 already enables
  `pcap { dev = eth0 }` with `sampling = 1`, which should produce
  packet samples from the container's traffic generator. If they
  aren't being generated, the issue is inside hsflowd's pcap module —
  test with `sflowtool -p 4729` as a local collector to confirm.

- **If `SFlowUdpParser` throws and `runParser()` swallows it:** add a
  `WARN` log with the protocol name so future silent-drop investigations
  aren't silent. Then fix the parser/adapter bug.

- **If the splitter drops empty lists cleanly but we're getting
  non-empty-but-invalid lists:** add a
  `WARN` in the splitter path that logs "produced 0 FlowDocuments from
  X sFlow samples" so the silent path stops being silent.

### Step 5: Prove the fix

```bash
cd opennms-container/delta-v
SKIP_TESTS=true ./build.sh deltav 2>&1 | tail -10
docker compose up -d --force-recreate flow-enricher
./test-flows-e2e.sh 2>&1 | tail -40
```

Expected: all V9 and SFLOW assertions pass. ClickHouse `flows_raw`
contains both `netflow_version='V9'` and `netflow_version='SFLOW'`
rows in the last 10 minutes.

## Secondary Work: Netflow9 NPE/AIOOBE cleanup (if time allows)

Two exceptions to fix:

1. **`NullPointerException` at `AbstractProtocolMessageProcessor.synthesizeParsedLog:204`** —
   `msg` is null. Look at what upstream code paths can pass a null
   `msg` into `synthesizeParsedLog`. Almost certainly a missing null
   check after an empty iteration (e.g., `List<TelemetryMessage>`
   with zero elements → iterator never runs → `msg` stays null →
   deference fails).

2. **`ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0`** —
   also on Netflow9 threads. Probably a message list accessed as
   `list.get(1)` when the list is empty. Read the stack trace from
   flow-enricher logs to pinpoint the file:line.

Both are intermittent and fire on specific batch sizes. Add defensive
null/empty checks at the exact call sites, then ship a reproducer test
if it's cheap. Don't rewrite the `AbstractProtocolMessageProcessor` flow
control — just close the defect.

## Guardrails from this session

- **`./build.sh deltav` is now self-healing** thanks to PR #154. You
  no longer have to remember to rebuild daemon-boot JARs manually; the
  script walks each `core/daemon-boot-*/` module and rebuilds it if any
  `.java` under `src/main/` is newer than the target JAR. Running
  `./build.sh deltav` fresh after source edits should Just Work.
- The flow-enricher build is unaffected by the guardrail because
  `do_flow_enricher_image()` already runs `./mvnw ... package` inline
  every time. So rebuilding the enricher doesn't require any extra
  steps.

## Important Reminders

- **Never create PRs against `OpenNMS/opennms`** — always use
  `--repo pbrane/delta-v --base develop`.
- **Always `git pull origin develop`** before creating each feature
  branch.
- **Feature branches only** — never commit directly to develop.
- **Build tool is `./mvnw`** — not `./compile.pl` or `maven/bin/mvn`.
- **Uncommitted provisiond requisition drift** (`last-import=` timestamp
  pollution under `opennms-container/delta-v/provisiond-overlay/etc/imports/`)
  is runtime state — discard with `git checkout --`; never commit.
- **New delta-v code goes in `org.deltav.*` packages** with the
  BeaconStrategists 2026 AGPL v3 header. This session mostly edits
  Java inside `core/flow-enricher/`, which already uses `org.deltav`.

## Quick Start

```bash
cd /Users/david/development/src/opennms/delta-v
git fetch origin && git checkout develop && git pull origin develop
git log --oneline -5  # confirm PR #153 and #154 merges are present
git checkout -b feature/flow-enricher-sflow-silent-drop-fix
# ... work from Step 1 ...
```
