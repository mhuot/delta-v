# Next session: sFlow silent drop in flow-enricher

**Background memory:** `project_sflow_silent_drop_v2` (check before reading this file — authoritative source).

**PR to open:** small, targeted — branch name suggestion `fix/sflow-silent-drop`. Expected size: one adapter-path fix + one E2E assertion tightening.

---

## The bug in one paragraph

`flow-sflow-testnode-1` emits sFlow UDP → Minion → Kafka `OpenNMS.Sink.Telemetry-SFlow` → flow-enricher consumes (consumer-group lag = 0) → zero rows materialize in `deltav.flows_raw` with `netflow_version='SFlow'`. **Ever.** No ERROR logs. `flow_enricher_adapters_SFlow_entriesParsed_total > 0` but `flow_enricher_adapters_SFlow_entriesConverted_total == 0`, so the drop sits in the adapter's convert step — between parse and emit.

This is **not** the PR #156 bug (DNS NPE in `SFlowUdpParser.FlowRecord.visit`). That workaround (`setDnsLookupsEnabled(false)`) is already applied in `FlowEnricherConfiguration.java:342-346` and the drop persists.

## Suspected root cause

The other three parsers (Netflow5/9/IPFIX) produce **FlowMessage protobuf** which flows cleanly through the Phase-2 `AbstractProtocolMessageProcessor` pipeline:

```
raw UDP bytes → horizon UdpParser.parse() → FlowMessage protobuf
             → CapturingDispatcher captures → synthesizeParsedLog()
             → AbstractFlowAdapter.handleMessageLog(synthetic log) → Flow POJOs
```

`SFlowUdpParser` produces **BSON documents**, not FlowMessage protobuf. The `CapturingDispatcher` still captures *something*, but the synthesized `TelemetryMessageLog` entries carry BSON bytes where `SFlowAdapter.handleMessageLog()` expects something else. Adapter decodes → produces no Flow records → pipeline emits empty list → silent drop.

## First five diagnostic steps

Do these before writing any code. The assumption above might be wrong.

1. **Read `SFlowMessageProcessor.java` + `SFlowAdapter` end-to-end** (horizon-side adapter, expect it under the horizon JAR). Specifically: what does `SFlowAdapter.handleMessageLog()` expect in `entry.getBytes()`? FlowMessage protobuf, or raw sFlow UDP, or BSON, or horizon-internal `Packet` bytes?
2. **Add TRACE logging** in `AbstractProtocolMessageProcessor.runParser()` right before and after `parser.parse()` (line 170) to see:
   - entry byte size going in
   - captured message count coming out (`captureForThisCall.getCaptured().size()`)
   - for sFlow specifically: is the captured list empty, or is it non-empty but the downstream adapter drops everything?
3. **Add TRACE logging** in `AbstractProtocolMessageProcessor.process()` around line 133 (`adapter.handleMessageLog(parsedLog)`) to see `pipeline.getCapturedFlows().size()` after the call.
4. **Run one sFlow packet manually.** `docker compose up -d --build` with lite + metrics profiles. `delta-v-flow-enricher` logs at TRACE for `org.deltav.flows.enricher.protocol` + `org.opennms.netmgt.telemetry.protocols.sflow`. Trigger one exporter: `docker exec flow-sflow-testnode-1 …` (check the compose file for how the testnode emits). Grep logs for the sFlow entry.
5. **Compare** to IPFIX on the same run. The delta between what survives Stage 1 + Stage 2 for IPFIX vs sFlow is the bug location.

Only after these five steps should you write a fix. If the BSON-vs-protobuf assumption holds, the fix is likely: sFlow's `AbstractProtocolMessageProcessor` subclass needs a different `createAdapter` / `runParser` path that passes raw parser output to `SFlowAdapter` without the `synthesizeParsedLog` re-wrapping. Or the `SFlowMessageProcessor` needs to override `process()` entirely.

## Key files

| File | Why |
|---|---|
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/AbstractProtocolMessageProcessor.java` | Stage 1 / Stage 2 boundary (lines 110–180). `runParser` + `synthesizeParsedLog` are the two-stage bridge. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/SFlowMessageProcessor.java` | Subclass — thin wrapper, may need to override. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/IpfixMessageProcessor.java` | Reference implementation that works — compare. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/CapturingDispatcher.java` | What the parser dispatches INTO during `parser.parse()`. Check what the sFlow parser sends to it. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java` lines 333–405 | All four processor beans + the DNS-disabled sFlow parser bean. |

Horizon-side (pre-built JARs, read but don't modify — if the fix needs horizon changes, it's a separate PR to `pbrane/delta-v-horizon`):
- `org.opennms.netmgt.telemetry.protocols.sflow.parser.SFlowUdpParser`
- `org.opennms.netmgt.telemetry.protocols.sflow.adapter.SFlowAdapter` (or similar — find via `jar tf` in local `.m2`)

## How to verify the fix

Bring the stack up then query ClickHouse:

```bash
cd opennms-container/delta-v
docker compose --profile lite --profile metrics up -d --build
# wait ~2 minutes for everything to stabilize
docker exec delta-v-clickhouse clickhouse-client --query "
SELECT netflow_version, count() FROM deltav.flows_raw
WHERE timestamp >= now() - INTERVAL 2 MINUTE
GROUP BY netflow_version"
```

**Before fix:** SFlow count = 0. **After fix:** SFlow count > 0.

Metric check:

```bash
docker exec delta-v-flow-enricher wget -q -O- http://127.0.0.1:8080/actuator/prometheus | \
  grep -E "flow_enricher_adapters_SFlow_entries(Parsed|Converted)_total"
```

Both counters should be non-zero AND roughly equal. The bug manifests as parsed > 0 and converted == 0.

## Scope for the PR

- Fix the sFlow path so records land in ClickHouse
- Tighten `test-prometheus-writer-e2e.sh` Step 11 assertion: `count(DISTINCT netflow_version) >= 3` → `== 4` (currently relaxed specifically to tolerate this bug — see `project_sflow_silent_drop_v2.md:48`)
- Remove the apologetic comment block in `FlowEnricherConfiguration.java:340-355` (the "sFlow still drops silently" explanation — no longer true)
- Update `project_sflow_silent_drop_v2.md` memory → status DONE with PR link
- Update `project_flows_visibility_done.md` memory to note sFlow now arrives
- Update Grafana Flows Overview screenshot expectations if user cares (Protocol mix donut should show 4 slices)

## Stack state at session start

- All data-pipeline work is in `develop` (PR #184 merged 2026-04-21). PR #185 (forensic dashboard) is **awaiting merge** with 10 commits on `feat/flows-forensic`. Merge #185 first or work alongside it.
- Stack is expected down at session start. `docker compose --profile lite --profile metrics up -d --build` boots everything needed.
- If the E2E gate fails, check the mock-snmp-agent fix is applied (commit `a5f93495f2b` is in PR #185; if that's not merged yet, cherry-pick it onto the new branch first or the rpc-canary node-scan will flake).

## Estimated session size

Small. Diagnosis is 30–60 minutes if the assumption holds, longer if it doesn't. Fix is likely < 50 lines of Java. PR size target: < 200 lines including the E2E assertion tightening and comment-block removal.
