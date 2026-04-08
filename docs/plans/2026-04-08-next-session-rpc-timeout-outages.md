# Next Session: RPC Timeouts Must Not Create Outages

> Copy everything below the line into the next Claude Code conversation.

---

## Context

**RPC timeouts are infrastructure problems (Minion unreachable), not service problems (target down).** No Delta-V daemon should create outages or fault events when an RPC request times out. Currently, multiple daemons treat RPC timeouts as service failures, creating false outages.

## What Was Fixed This Session

1. **PSM page-sequence fix** — PR #125 (merged to fix/psm-rpc-serialization branch, ready for merge). Jackson XmlMapper silently dropped `@XmlAnyElement` nested XML. `XmlConfigPostProcessor` DOM post-processing workaround.

2. **PerspectivePollJob.onTimedOut() reverted** — Committed to delta-v-horizon (`d5a7e81e`), but NOT yet deployed. The running Docker image still has the old code. Need to rebuild horizon JAR and redeploy.

3. **Health check optimization** — Container startup from ~5-9min → 27 seconds.

4. **E2E validation** — PSM polls working (Google-Search lastgood recent), perspective outages creating and clearing from both Default and mhuot-labs Minions.

## What Needs To Be Done

### 1. Audit ALL daemons for RPC timeout → outage behavior

Every daemon that sends Kafka RPC requests needs its `RpcExceptionHandler` checked. The rule:

| Handler | Should create outage? | Reasoning |
|---------|----------------------|-----------|
| `onTimedOut()` | **NO** — log only | Minion unreachable, not service down |
| `onRejected()` | **YES** | Minion explicitly refused — processing failure |
| `onUnknown()` | **YES** | Exception during processing — real error |
| Normal result with Unavailable | **YES** | Monitor executed, service actually down |

Daemons to audit (all use `LocationAwarePollerClient` or similar RPC clients):
- **Pollerd** — `PollableServiceConfig` or wherever RPC callbacks are handled
- **PerspectivePollerd** — `PerspectivePollJob` (partially fixed in delta-v-horizon, needs deploy)
- **Collectd** — `CollectableService` or RPC callback handler
- **Provisiond** — detector RPC callbacks
- **Discovery** — ping sweep RPC callbacks
- **Enlinkd** — SNMP/bridge RPC callbacks

### 2. Rebuild and deploy PerspectivePollJob fix

The `onTimedOut()` revert was committed to delta-v-horizon (`d5a7e81e`) but the running PerspectivePollerd Docker image hasn't been rebuilt. Steps:
```bash
# In delta-v-horizon repo:
cd /Users/david/development/src/opennms/delta-v-horizon
./mvnw -B -DskipTests -pl features/perspectivepoller install
# Then publish to GitHub Packages or copy JAR locally

# In delta-v repo:
cd /Users/david/development/src/opennms/delta-v
# Rebuild daemon images with updated horizon JAR
./opennms-container/delta-v/build.sh deltav
# Redeploy perspectivepollerd
docker compose up -d --force-recreate perspectivepollerd
```

### 3. Implement Phase 4/5 of perspective E2E test

Phase 4 (outage simulation) was skipped because `docker pause` is an infrastructure failure, not a service failure. The correct approach:

```bash
# Phase 4: Block google.com from Default Minion → real service failure
docker exec delta-v-minion iptables -A OUTPUT -d 142.250.0.0/16 -j REJECT
# Wait for perspective outage from Default (mhuot-labs still succeeds)

# Phase 5: Unblock → outage clears
docker exec delta-v-minion iptables -D OUTPUT -d 142.250.0.0/16 -j REJECT
# Wait for outage to clear
```

This tests real service failure detection, not infrastructure failure.

### 4. Merge PR #125

PR #125 (fix/psm-rpc-serialization) has 3 commits:
- PSM page-sequence XmlConfigPostProcessor fix
- test-minion-rpc-e2e.sh poll verification improvement
- Container health check timing optimization

## Key Files

- `PerspectivePollJob.java` — delta-v-horizon: `features/perspectivepoller/src/main/java/org/opennms/netmgt/perspectivepoller/PerspectivePollJob.java`
- `XmlConfigPostProcessor.java` — delta-v: `core/daemon-common/src/main/java/org/opennms/core/daemon/common/XmlConfigPostProcessor.java`
- `PollerdDaemonConfiguration.java` — delta-v: `core/daemon-boot-pollerd/src/main/java/.../PollerdDaemonConfiguration.java`
- `test-perspective-e2e.sh` — delta-v: `opennms-container/delta-v/test-perspective-e2e.sh`
- `docker-compose.yml` — delta-v: `opennms-container/delta-v/docker-compose.yml`

## Important Reminders

- **Never create PRs against OpenNMS/opennms** — always `--repo pbrane/delta-v`
- **Always `git pull develop` before creating feature branches**
- **RPC timeouts ≠ service outages** — this applies to ALL daemons, not just PerspectivePollerd
- **Pollerd already has this pattern** — check `PollableServiceConfig` / `PollerRequestBuilderImpl` for how Pollerd handles RPC timeouts. It may already have the same bug.
