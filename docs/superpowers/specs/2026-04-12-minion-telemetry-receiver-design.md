# Minion Telemetry Receiver Design

> Unblocks full E2E flow testing by adding flow protocol UDP listeners to the
> Boot4 Minion, making Minion the sole network ingress point for flow data.

## Problem Statement

The Boot4 Minion (`core/daemon-boot-minion`) lacks telemetry UDP listeners.
The Karaf `minion-telemetryd-receivers` feature listed in `features.boot` is
inert in the Spring Boot runtime. Flow traffic currently bypasses the Minion
entirely:

```
softflowd -> Telemetryd container (UDP 4729) -> Kafka -> flow-enricher -> ClickHouse
```

This violates the architectural rule "Minion is sole network ingress" and
means the Minion is not exercised in the flow data path during E2E testing.

## Target Architecture

```
softflowd -> Minion (UDP 4729) -> Kafka -> flow-enricher -> ClickHouse
```

The Minion receives raw UDP datagrams, detects the flow protocol from header
bytes, wraps the raw payload in a `TelemetryMessageLog` protobuf, and
dispatches via the existing Sink API to protocol-specific Kafka topics. The
flow-enricher consumes these topics unchanged -- it cannot distinguish
Minion-produced messages from Telemetryd-produced ones because the wire
format is identical.

## Design Decisions

### Approach: Lightweight Custom UDP Listener (Approach B)

Instead of wiring horizon's `UdpListener` + parser classes (Approach A) or
reusing the full `Telemetryd` daemon (Approach C), the Minion gets a custom
Netty-based UDP listener that acts as a thin UDP-to-Kafka relay.

**Rationale:**
- Keeps the Minion completely free of horizon telemetry dependencies
- No new entries in `daemon-boot-minion/pom.xml`'s dependency section
  (except `protobuf-maven-plugin` in the build)
- The Minion's parsers in the legacy architecture don't do deep protocol
  parsing anyway -- they wrap raw bytes in `TelemetryMessageLog` with
  metadata. The heavy parsing happens server-side in the flow-enricher's
  horizon adapters.
- Lighter Minion image -- no parser JARs, no template management, no
  protocol-specific code

### Protocols: Four Flow Protocols

Netflow v5, Netflow v9, IPFIX, and sFlow. These are the four protocols
the flow-enricher can process. Other protocols (JTI, BMP, NXOS, OpenConfig,
Graphite) have no active consumer in delta-v and are deferred.

### Configuration: Spring Boot Properties

Follows the established `TrapListenerConfiguration` / `SyslogListenerConfiguration`
pattern -- `@Value` properties in `application.yml`, `@ConditionalOnProperty`
toggle, no XML config parsing.

### Port: Single Shared Port

All four protocols share a single UDP port (default 4729). The listener
detects the protocol from version header bytes in each datagram. This
matches real-world deployments where network devices export all flow types
to a single collector address.

### Protobuf: Locally Generated

The `telemetry.proto` definition (~15 lines) is copied into the Minion
module at `src/main/proto/telemetry.proto`. The `protobuf-maven-plugin`
generates classes in the `org.deltav.minion.telemetry.proto` package. The
wire format is byte-identical to horizon's
`org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos` -- protobuf
deserialization is based on field tags, not Java class names.

## Component Design

### FlowProtocol Enum

Maps version header bytes to Kafka Sink topic suffix:

| Protocol   | Version Field             | Sink Module ID           |
|------------|---------------------------|--------------------------|
| NETFLOW_5  | uint16 `0x0005` at offset 0 | `Telemetry-Netflow-5`  |
| NETFLOW_9  | uint16 `0x0009` at offset 0 | `Telemetry-Netflow-9`  |
| IPFIX      | uint16 `0x000A` at offset 0 | `Telemetry-IPFIX`      |
| SFLOW      | uint32 `0x00000005` at offset 0 | `Telemetry-SFlow`  |

**Detection logic:**
```
detect(bytes):
  if length < 4: return null (too small)
  version16 = bytes[0..1] as uint16 big-endian
  switch version16:
    0x0005 -> NETFLOW_5
    0x0009 -> NETFLOW_9
    0x000A -> IPFIX
    default:
      version32 = bytes[0..3] as uint32 big-endian
      if version32 == 5 -> SFLOW
      else -> null (unknown)
```

No collision between sFlow and Netflow v5: a 2-byte read of an sFlow packet
at offset 0 yields `0x0000` (high bytes of the 4-byte version field), not
`0x0005`.

### FlowTelemetryBatch (Sink Message Wrapper)

The Sink API's `SinkModule<S, T>` requires S and T to extend
`org.opennms.core.ipc.sink.api.Message` (a marker interface). The
protobuf-generated `TelemetryMessageLog` doesn't implement this marker, so
a thin wrapper is needed:

```java
public class FlowTelemetryBatch implements Message {
    private final byte[] serializedLog;

    public FlowTelemetryBatch(TelemetryMessageLog log) {
        this.serializedLog = log.toByteArray();
    }

    public byte[] getBytes() { return serializedLog; }
}
```

The `FlowUdpListener` builds a `TelemetryMessageLog` from each datagram,
wraps it in a `FlowTelemetryBatch`, and passes that to the dispatcher.

### FlowSinkModule

Implements `SinkModule<FlowTelemetryBatch, FlowTelemetryBatch>` from the
Sink API (`org.opennms.core.ipc.sink.api`).

- `getId()` returns `"Telemetry-{protocol}"` (e.g., `"Telemetry-Netflow-9"`)
- `marshal(FlowTelemetryBatch)` returns `batch.getBytes()`
- `unmarshal(byte[])` returns `new FlowTelemetryBatch(TelemetryMessageLog.parseFrom(bytes))`
- Batching config: batch size 100, interval 500ms, queue size 10000
  (configurable via application.yml)

The `TelemetryMessageLog` used inside `FlowTelemetryBatch` is the
locally-generated class from `org.deltav.minion.telemetry.proto`, not
horizon's version. Both produce identical wire bytes.

Four instances are created at startup, one per `FlowProtocol`.

### FlowUdpListener

A Netty `NioDatagramChannel` listener that:

1. Binds to a configurable UDP port (default 4729)
2. On each inbound datagram:
   a. Extracts `sourceAddress` and `sourcePort` from the sender
   b. Calls `FlowProtocol.detect(payload)` to identify the protocol
   c. If unknown, drops with rate-limited WARN log
   d. Builds a `TelemetryMessageLog`:
      - `location` = Minion location (from `DistPollerDao`)
      - `system_id` = Minion ID (from `DistPollerDao`)
      - `source_address` = exporter IP from datagram
      - `source_port` = exporter port from datagram
      - One `TelemetryMessage` entry: timestamp = now, bytes = raw payload
   e. Wraps in `FlowTelemetryBatch` and dispatches via
      `asyncDispatchers.get(protocol).send(batch)`
3. Holds a `Map<FlowProtocol, AsyncDispatcher<FlowTelemetryBatch>>`
   created at construction time

**Constructor parameters:**
- `int port`
- `String bindAddress`
- `Map<FlowProtocol, AsyncDispatcher<FlowTelemetryBatch>> dispatchers`
- `String location`
- `String systemId`

**Lifecycle:**
- `start()` -- binds Netty channel, starts event loop
- `stop()` -- closes channel, closes all dispatchers (flushes pending batches)

### TelemetryListenerConfiguration

Spring `@Configuration` in `org.deltav.minion.boot`:

```java
@Configuration
@ConditionalOnProperty(
    name = "opennms.minion.telemetry.enabled",
    havingValue = "true",
    matchIfMissing = true)
```

**Properties:**
```yaml
opennms:
  minion:
    telemetry:
      enabled: true
      port: 4729
      address: "*"
      batch-size: 100
      batch-interval-ms: 500
      queue-size: 10000
```

**Beans:**
- `FlowSinkModule` x4 (one per `FlowProtocol`)
- `AsyncDispatcher` x4 (via `messageDispatcherFactory.createAsyncDispatcher(sinkModule)`)
- `FlowUdpListener` (single instance, all 4 dispatchers)
- `SmartLifecycle` at phase 400 (after Sink client phase 200 and RPC server phase 300)

**Dependencies injected:**
- `MessageDispatcherFactory` -- from `KafkaSinkClientConfiguration` (already wired)
- `DistPollerDao` -- already available (provides location and systemId)

## File Inventory

### New Files in `core/daemon-boot-minion`

| File | Package | Purpose | ~Lines |
|------|---------|---------|--------|
| `src/main/proto/telemetry.proto` | -- | Protobuf definition for TelemetryMessage + TelemetryMessageLog | ~15 |
| `src/main/java/.../telemetry/FlowProtocol.java` | `o.d.minion.telemetry` | Protocol enum with version-byte detection | ~50 |
| `src/main/java/.../telemetry/FlowTelemetryBatch.java` | `o.d.minion.telemetry` | Thin wrapper: TelemetryMessageLog -> Sink Message marker | ~20 |
| `src/main/java/.../telemetry/FlowSinkModule.java` | `o.d.minion.telemetry` | Local SinkModule impl for telemetry dispatch | ~60 |
| `src/main/java/.../telemetry/FlowUdpListener.java` | `o.d.minion.telemetry` | Netty UDP channel handler | ~120 |
| `src/main/java/.../boot/TelemetryListenerConfiguration.java` | `o.d.minion.boot` | Spring Configuration: beans + lifecycle | ~100 |
| `src/test/java/.../telemetry/FlowProtocolTest.java` | `o.d.minion.telemetry` | Protocol detection unit tests | ~60 |
| `src/test/java/.../telemetry/FlowSinkModuleTest.java` | `o.d.minion.telemetry` | Module ID + marshal round-trip tests | ~40 |
| `src/test/java/.../telemetry/FlowUdpListenerTest.java` | `o.d.minion.telemetry` | Synthetic datagram -> dispatcher verification | ~80 |

### Modified Files

| File | Change |
|------|--------|
| `core/daemon-boot-minion/pom.xml` | Add `protobuf-maven-plugin` to build plugins |
| `core/daemon-boot-minion/src/main/resources/application.yml` | Add `telemetry.port`, `telemetry.address`, batch properties |
| `opennms-container/delta-v/docker-compose.yml` | Add `4729:4729/udp` to minion ports; change flow-exporter `NETFLOW_COLLECTOR` to `minion-default-01:4729`; change flow-exporter `depends_on` from telemetryd to minion; remove `4729:4729/udp` from telemetryd ports |
| `opennms-container/delta-v/telemetryd-overlay/etc/telemetryd-configuration.xml` | Remove `Netflow-9-UDP-4729` listener block |
| `opennms-container/delta-v/test-flows-e2e.sh` | Add `minion` to `REQUIRED_SERVICES` |

### No New Dependencies

The Minion POM gains zero new `<dependency>` entries. Everything needed is
already on the classpath:
- `io.netty:netty-transport` -- via Spring Boot starter
- `com.google.protobuf:protobuf-java` -- transitive via Sink API
- `org.opennms.core.ipc.sink:*.sink.api` -- via daemon-boot-minion-common
- `org.opennms.core.ipc.sink:*.sink.common` -- via daemon-boot-minion-common

Only a `protobuf-maven-plugin` build plugin is added to generate from the
local `telemetry.proto`.

## Error Handling

**Startup:** `BindException` if port in use -- Spring Boot fails fast. Same
behavior as Trap/Syslog listeners.

**Malformed datagrams:** Packets < 4 bytes or with unrecognized version
headers are dropped with rate-limited WARN log. No exception, no retry.

**Kafka unavailability:** Handled by the Sink API's `AsyncDispatcher`. It
queues up to `queue-size` messages and drops when full. No custom resilience
logic -- UDP flow telemetry already tolerates packet loss.

**Backpressure:** Netty event loop calls `asyncDispatcher.send()` which is
non-blocking (enqueue). If the Sink buffer fills, messages are dropped.
Acceptable for UDP-based protocols.

**Shutdown:** SmartLifecycle `stop()` closes the Netty channel, then closes
each `AsyncDispatcher` (flushes pending batches). Graceful drain.

**Logging:**
- INFO on startup: listener port and enabled protocols
- WARN rate-limited: unrecognized protocol version
- DEBUG: per-datagram protocol detection (disabled by default)

## Testing

### Unit Tests

| Test Class | Verifies |
|------------|----------|
| `FlowProtocolTest` | Version-byte detection for all 4 protocols, unknown versions return null, edge cases (empty/tiny buffers) |
| `FlowSinkModuleTest` | Module ID format, marshal/unmarshal round-trip, batching config values |
| `FlowUdpListenerTest` | Synthetic Netflow v9 datagram to ephemeral port -> correct AsyncDispatcher receives TelemetryMessageLog with expected metadata (location, systemId, sourceAddress, payload bytes). Uses mock MessageDispatcherFactory. |

### E2E Validation

No new integration test module. The existing `test-flows-e2e.sh` validates
the full pipeline after Docker Compose changes:

1. flow-exporter sends Netflow v9 to `minion-default-01:4729`
2. Minion dispatches to `OpenNMS.Sink.Telemetry-Netflow-9`
3. flow-enricher enriches and produces to `deltav-flows`
4. ClickHouse ingests via Kafka engine
5. Test asserts non-zero rows in `flows_raw` and all 4 dimension MVs

This proves wire-format compatibility between the Minion's locally-generated
`TelemetryMessageLog` and the flow-enricher's horizon-based deserializer.

## Telemetryd Container Changes

The `Netflow-9-UDP-4729` listener is removed from
`telemetryd-overlay/etc/telemetryd-configuration.xml`. The Telemetryd
container remains in docker-compose for its Kafka consumer role (future
non-flow adapters: JTI, BMP, etc.), but no longer opens any UDP ports for
flow collection. The config header comment "No listeners" now matches
reality.

## Future Work (Not in Scope)

- **Additional protocols** (BMP, JTI, NXOS, etc.) -- add when consumers exist
- **Per-protocol enable/disable** -- currently all-or-nothing via `telemetry.enabled`
- **Metrics counters** -- datagrams received/dropped per protocol
- **Multi-port support** -- separate ports per protocol for operators who need it
- **DeltaV.Sink.* topic rename** -- prerequisite tracked in `project_sink_topic_rename.md`
