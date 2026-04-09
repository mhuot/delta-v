# Next Session: Flow Enricher Phase 1 Implementation

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Branch: `feature/elasticsearch-infrastructure` (PR #138, not yet merged — contains ES compose service + spec + plan)

We designed and specced a Nephron (Apache Beam/Flink) replacement using two Spring Cloud Stream services:
- **flow-enricher** — consumes raw Sink topics, parses/enriches flows, publishes to `deltav-flows` protobuf topic
- **flow-aggregator** — consumes `deltav-flows`, Kafka Streams windowed aggregation → Elasticsearch

This session implements **Phase 1: flow-enricher MVP** only.

## Key Files

- **Spec:** `docs/superpowers/specs/2026-04-09-flow-processor-design.md`
- **Plan:** `docs/superpowers/plans/2026-04-09-flow-enricher-phase1.md`
- **ES compose service:** `opennms-container/delta-v/docker-compose.yml` (already has Elasticsearch 8.18.2)

## What the Plan Covers

10 tasks, TDD approach:
1. Add Spring Cloud Stream BOM to parent POM
2. Create `core/flow-enricher/` Maven module
3. Create `deltav-flows.proto` protobuf definition
4. Implement `FlowLocalityCalculator` (private/public IP classification)
5. Implement `InterfaceMarkingCache` (TTL cache for hasFlows DB marking)
6. Implement `SinkMessageDeserializer` (SinkMessage → TelemetryMessageLog)
7. Implement `JdbcNodeInfoLookup` (JDBC node resolution)
8. Wire `FlowEnrichmentFunction` + Application class + application.yml
9. Docker Compose integration
10. Verify end-to-end message flow

## Review Feedback to Incorporate (not yet in the plan file)

These items came from review after the plan was written:

1. **Spring Cloud version** — verify `2025.0.0` is GA for Spring Boot 4.0.3. Fall back to latest stable if needed.

2. **Caffeine cache on JdbcNodeInfoLookup** — add IP→NodeInfo cache (~5 min TTL) to avoid per-flow DB queries. Critical for high-volume deployments.

3. **Multi-topic binding** — use comma-separated destinations instead of CompositeMessageChannelFactory:
   ```yaml
   destination: DeltaV.Sink.Telemetry-Netflow-5,DeltaV.Sink.Telemetry-Netflow-9,DeltaV.Sink.Telemetry-IPFIX,DeltaV.Sink.Telemetry-SFlow
   ```

4. **Verify horizon 1.0.7 artifact IDs** — confirm these exist in GitHub Packages before adding to pom.xml:
   ```bash
   find ~/.m2/repository/org/opennms -name '*flow*' -name '*.jar' | grep '1.0.7' | head -20
   find ~/.m2/repository/org/opennms -name '*netflow*' -name '*.jar' | grep '1.0.7' | head -20
   find ~/.m2/repository/org/opennms -name '*telemetry*' -name '*.jar' | grep '1.0.7' | head -20
   ```

5. **`hasflows` column name** — verify exact column name in Liquibase schema (`core/schema/`) before Task 5.

## Prerequisites (Not Done Yet)

- **Sink topic prefix rename** (`OpenNMS.Sink.*` → `DeltaV.Sink.*`) — separate PR, not yet started. For MVP testing, use the current `OpenNMS.Sink.*` prefix and make the topic names configurable via `DELTAV_FLOWS_SINK_TOPIC` env var.
- **Merge PR #138** — Elasticsearch compose service + spec + plan need to be on develop first.

## Architecture Recap

```
Minion → DeltaV.Sink.Telemetry-* topics (raw protocol bytes)
           ↓
flow-enricher (Spring Cloud Stream, Kafka binder)
  • Parse: horizon adapter JARs (Netflow5/9, IPFIX, sFlow)
  • Enrich: JDBC node lookup (with Caffeine cache), classification, locality
  • Mark: interface hasFlows (with TTL cache)
  • Publish: deltav-flows topic (protobuf FlowDocument)
           ↓
deltav-flows topic (PUBLIC CONTRACT — community API)
           ↓
    ┌──────┴──────────────┐
    ↓                     ↓
flow-aggregator           Community consumers
(Phase 2 — future)       (Prometheus, ClickHouse, etc.)
```

## Important Reminders

- **Never create PRs against OpenNMS/opennms** — always `--repo pbrane/delta-v`
- **Always git pull develop** before creating feature branches
- **Package policy:** `org.deltav.flows.enricher` for new code
- **Copyright:** BeaconStrategists for `org.deltav` packages
- **Spring Cloud Stream is new to this project** — no existing usage patterns to follow
- **The protobuf `.proto` file is the community API contract** — design it carefully
