# Next Session: Fix PSM RPC Serialization + Pollerd E2E

> Copy everything below the line into the next Claude Code conversation.

---

## Context

**Pollerd → Minion RPC is silently broken for PageSequenceMonitor services.** All PSM poll requests fail on the Minion with XML unmarshalling errors, and the volume of failures triggers Kafka consumer rebalances that block ALL other monitor types (ICMP, SNMP, TCP, etc.) from being polled. This affects both Pollerd and PerspectivePollerd.

Discovered while building the PerspectivePollerd E2E test (PR #124, merged). The test-minion-rpc-e2e.sh was passing by only checking for poll DISPATCH evidence in logs, not for successful poll RESULTS.

## The Bug

### Symptoms
- Pollerd: 136/136 RPC polls timeout, 0 successful
- PerspectivePollerd: 107/107 RPC polls timeout, 0 successful
- Minion log: 1098 `PageSequenceMonitor` XML parse errors
- Minion log: 646 `ttl already expired` (requests expire before processing)
- Minion Kafka consumer rebalances due to `max.poll.interval.ms` exceeded

### Root Cause
The PSM `page-sequence` parameter contains embedded XML:
```xml
<parameter key="page-sequence">
  <page-sequence>
    <page host="${nodelabel}" path="/" port="443" scheme="https" response-range="200-399"/>
  </page-sequence>
</parameter>
```

When Pollerd serializes the `PollerRequestDTO` for the Kafka RPC request, this embedded XML is not properly escaped or CDATA-wrapped. The Minion's JAXB unmarshaller (EclipseLink MOXy) receives truncated/malformed XML and throws:
```
MarshallingResourceFailureException: Premature end of file
```

This causes every PSM poll to fail on the Minion. The failures take long enough to exceed `max.poll.interval.ms`, triggering Kafka consumer group rebalances that prevent ALL other RPC requests from being processed.

### Key Files
- `features/poller/client-rpc/src/main/java/org/opennms/netmgt/poller/client/rpc/PollerRequestDTO.java` — RPC request serialization
- `features/poller/client-rpc/src/main/java/org/opennms/netmgt/poller/client/rpc/PollerAttributeDTO.java` — Attribute/parameter serialization (likely where XML-in-XML breaks)
- `features/poller/client-rpc/src/main/java/org/opennms/netmgt/poller/client/rpc/PollerClientRpcModule.java` — Minion-side execution
- `opennms-container/delta-v/pollerd-overlay/etc/poller-configuration.xml` — PSM service definitions with embedded XML
- `opennms-container/delta-v/perspectivepollerd-overlay/etc/poller-configuration.xml` — Same

### Fix Direction
Check how `PollerAttributeDTO` handles the `page-sequence` parameter value. The parameter value is a complex XML element (not a simple string). Options:
1. CDATA-wrap XML parameter values in the DTO serialization
2. Base64-encode complex parameter values
3. Use a different serialization strategy for nested XML parameters
4. Check if upstream OpenNMS handles this correctly and what changed in the Delta-V migration

## What Else to Fix in This Session

### 1. Revert PerspectivePollJob timeout → Unavailable reporting
The fix committed to delta-v-horizon (`62efef44`) was wrong. RPC timeouts should NOT create service outages — they're infrastructure problems (Minion unreachable), not service problems (google.com down). Revert `onTimedOut()` to log-only (no `reportResult(Unavailable)`). Keep `onRejected()` and `onUnknown()` as-is since those indicate actual processing failures.

### 2. Fix test-minion-rpc-e2e.sh to verify actual poll success
Current test only checks for poll DISPATCH in logs (grep for canary IP), not poll RESULTS. Should verify either:
- No open outages for canary services (the current "stuck outages" check is correct but doesn't fail if polls never run)
- Or verify `lastpoll` timestamp on canary services is recent

### 3. No-detector foreign sources for all E2E tests
Several E2E tests provision nodes that trigger 18-detector scans against unreachable IPs, wasting minutes on timeouts. Create no-detector foreign source definitions (like we did for `perspective-test`) for:
- `test-e2e.sh` — sends coldStart trap, provisions via Provisiond
- `test-minion-rpc-e2e.sh` — provisions rpc-canary node
- Any other test that provisions nodes without testing detection

### 4. Startup time audit
"9 minutes to healthy" is too long. Measure actual startup times per daemon and identify bottlenecks.

## What Changed in This Session (2026-04-07)

### Merged
- **PR #123**: Discovery registry wiring + ServiceMix/ActiveMQ purge from all Horizon daemons
- **PR #124**: PerspectivePollerd E2E test + ApplicationDaoJpa fix

### Direct commits to develop
- OutageDaoJpa: merge detached entities in save(), implement perspective outage methods
- Correct outage table queries (no nodeid column — join through ifserviceid)
- Next-session prompts

### Committed to delta-v-horizon
- PerspectivePollJob.onTimedOut() reports Unavailable (NEEDS REVERT — see above)

### Bugs found: 7 fixed, 1 remaining
1. ApplicationDaoJpa.getServicePerspectives() — empty stub → implemented
2. Discovery missing ServiceDetectorRegistry → @Import fix
3. ServiceMix Spring 4.2 leaking → spring-dependencies exclusions
4. ActiveMQ leaking → activemq-dependencies exclusions
5. PerspectivePollJob swallowing timeouts → now reports (NEEDS REVERT)
6. OutageDaoJpa null ifserviceid → merge detached entities
7. Outage table queries using nonexistent nodeid column → fixed joins
8. **PSM RPC serialization** — XML-in-XML breaks on Minion JAXB (THIS SESSION'S TARGET)

## Important Reminders

- **Never create PRs against OpenNMS/opennms** — always `--repo pbrane/delta-v`
- **Always `git pull develop` before creating feature branches**
- **Delta-V version**: `0.0.1-SNAPSHOT`, Horizon JARs: `1.0.3`
- **Hibernate 7 InetAddress**: autoApply converter works for JPQL bind params (don't convert to String)
- **outages table**: has NO nodeid column — must join via ifserviceid → ifservices → ipinterface → node
- **RPC timeouts should NOT create outages** — infrastructure problem, not service problem
