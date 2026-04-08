# RPC Timeout Handling Audit — 2026-04-08

## Policy

**RPC timeouts must NEVER create outages or fault events.**

RPC timeout = infrastructure problem (Minion unreachable, Kafka consumer rebalance, SSH tunnel flap). Only actual service failures (monitor executed on Minion and returned Unavailable) should create outages.

| RPC Callback | Should create outage? | Reasoning |
|--------------|----------------------|-----------|
| `onTimedOut()` | **NO** — log only | Minion unreachable, not service down |
| `onRejected()` | **YES** | Minion explicitly refused — processing failure |
| `onUnknown()` | **YES** | Exception during processing — real error |
| Normal result with Unavailable | **YES** | Monitor executed, service actually down |

## Audit Results

All `onTimedOut()` implementations in delta-v-horizon (1.0.4) are **compliant**.

### Active RPC Daemons

| Daemon | Class | File | onTimedOut() behavior | Status |
|--------|-------|------|----------------------|--------|
| Pollerd | `PollableServiceConfig` | `features/poller/impl/.../PollableServiceConfig.java:155` | Returns `PollStatus.unknown()` — no outage | Compliant |
| Pollerd (path) | `DefaultPollContext` | `features/poller/impl/.../DefaultPollContext.java:298` | Marks path AVAILABLE on timeout | Compliant |
| PerspectivePollerd | `PerspectivePollJob` | `features/perspectivepoller/.../PerspectivePollJob.java:92` | Log-only with explicit policy comment | Exemplary |
| Collectd (SNMP) | `SnmpCollectionSet` | `features/collection/snmp-collector/.../SnmpCollectionSet.java:397` | Returns `CollectionUnknown` (caught, logged only) | Compliant |
| Collectd (core) | `CollectionSpecification` | `features/collection/core/.../CollectionSpecification.java:310` | Returns `CollectionUnknown` (caught, logged only) | Compliant |
| Collectd (TCA) | `TcaCollectionHandler` | `features/juniper-tca-collector/.../TcaCollectionHandler.java:146` | Manual `CollectionUnknown` via `RequestTimedOutException` catch | Compliant |

### Passive/Lookup Daemons

| Daemon | RPC Usage | Timeout behavior | Status |
|--------|-----------|-----------------|--------|
| Trapd | `InterfaceToNodeCache` RPC lookup | Lookup failure logged, trap continues processing | Compliant |
| Syslogd | `InterfaceToNodeCache` RPC lookup | Same as Trapd | Compliant |
| Provisiond | Detector RPCs | No `onTimedOut()` override — uses framework default (safe) | Compliant |
| Discovery | Ping sweep RPC | No `onTimedOut()` override — uses framework default (safe) | Compliant |
| Enlinkd | SNMP proxy RPC | No `onTimedOut()` override — uses underlying collection handlers | Compliant |

## Automated Verification

E2E test `test-minion-rpc-e2e.sh` Phase 4 verifies this policy at runtime:
- Pauses Minion container for 120s (forces all RPC timeouts)
- Asserts zero new outages created during pause
- Asserts zero new problem alarms created during pause
- Asserts zero `nodeLostService` or `dataCollectionFailed` events on Kafka during pause

## Revision History

- 2026-04-08: Initial audit against delta-v-horizon 1.0.4 — all handlers compliant
