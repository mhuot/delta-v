# Next Session: Minion Boot4 Migration — EventBuilder Port

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Minion-to-Spring-Boot-4 migration is code complete on branch `feature/minion-boot4-clean` (2 commits from develop). Minion boots in 2.4s, passes health checks, 672MB image (down from 1.6GB Karaf). First E2E passed 12/12 with old daemon images.

**BLOCKER:** Model split-package. `model-jakarta` entities import `EventBuilder` from `opennms-model`. JPA daemons need `opennms-model` on classpath for EventBuilder, but Hibernate 7 loads the wrong `javax.persistence` annotations from opennms-model entities — causing entity mapping failures.

## The Problem

`EventBuilder` lives in `opennms-model` (`org.opennms.netmgt.model.events.EventBuilder`). Every daemon uses it. JPA daemons (`model-jakarta` entities + Hibernate 7) cannot have `opennms-model` on the classpath because:

1. `opennms-model` entities use `javax.persistence` annotations
2. `model-jakarta` entities use `jakarta.persistence` annotations
3. Hibernate 7 scans the same package (`org.opennms.netmgt.model`) and finds BOTH
4. Result: mapping failures, wrong annotations, startup crashes

## Resolution: Port EventBuilder to model-api

Move these classes from `opennms-model` to `core/model-api`:
- `EventBuilder` (the main one — used everywhere)
- `NodeLabelChangedEventBuilder` (used by Provisiond)
- `ResourceTypeUtils` (static utility, used by collection daemons)

`model-api` already exists and is on every daemon's classpath. After the port:
- JPA daemons: `model-jakarta` + `model-api` (no opennms-model needed)
- Minion: `model-api` only (no JPA, no opennms-model)
- opennms-model retains its copies for backward compat with horizon JARs

### Steps

1. `git checkout feature/minion-boot4-clean && git pull develop`
2. Copy `EventBuilder.java` from `opennms-model` to `core/model-api/src/main/java/org/opennms/netmgt/model/events/`
3. Copy `NodeLabelChangedEventBuilder.java` and `ResourceTypeUtils.java` similarly
4. Add any missing deps to `model-api/pom.xml` (events-api, config-model for Event/EventBuilder)
5. Verify `model-api` compiles: `./mvnw -DskipTests -pl org.opennms.core:org.opennms.core.model-api install`
6. Remove `opennms-model` dependency from ALL daemon-boot POMs (they already have `model-api`)
7. Full build: `make build`
8. Rebuild Docker images: `build.sh deltav`
9. E2E all 7 suites (including test-perspective-e2e.sh — new since last session)

### Key Files

- `opennms-model/src/main/java/org/opennms/netmgt/model/events/EventBuilder.java` — source
- `core/model-api/pom.xml` — target module
- `core/daemon-boot-*/pom.xml` — remove opennms-model deps
- `core/daemon-common/pom.xml` — may reference EventBuilder

## What Changed Since Last Session

### PR #123 (merged): Discovery registry wiring + classpath cleanup
- Discovery daemon now starts (wired `DetectorRegistryConfiguration` via `@Import`)
- Purged ServiceMix Spring 4.2 (16 JARs), ActiveMQ (~10 JARs) from ALL Horizon daemon fat JARs
- All container images ~60 MB smaller
- 94/94 E2E passing

### PR #124 (merged): PerspectivePollerd E2E test + 4 bug fixes
- New `test-perspective-e2e.sh` (15 tests) — provisions google.com, verifies perspective polling from Default + mhuot-labs Minions via PSM with `${nodelabel}` DNS resolution
- Fixed `ApplicationDaoJpa.getServicePerspectives()` — was returning empty list
- Fixed `MonitoredServiceDaoJpa.get()` — Hibernate 7 autoApply converter doesn't convert InetAddress bind params; pass `InetAddressUtils.str()` instead
- Fixed `OutageDaoJpa.currentOutageForServiceFromPerspective()` — was stubbed
- Added transactional wrapper to PerspectivePollerd event handler

### New E2E suite count: 109 tests across 8 suites
- test-e2e.sh: 14
- test-syslog-e2e.sh: 15
- test-passive-e2e.sh: 16
- test-minion-rpc-e2e.sh: 11
- test-collectd-e2e.sh: 7
- test-minion-e2e.sh: 13
- test-enlinkd-e2e.sh: 18
- test-perspective-e2e.sh: 15

## Outstanding Followups

- **PerspectivePollJob.onTimedOut()** — swallows RPC timeouts in horizon JAR. Needs fix in delta-v-horizon to enable Phase 4/5 of test-perspective-e2e.sh.
- **Minion ServiceMix/ActiveMQ cleanup** — 17 ActiveMQ + 15 ServiceMix bundles still in Minion (own dep paths, not daemon-common).

## Important Reminders

- **Never create PRs against OpenNMS/opennms** — always `--repo pbrane/delta-v`
- **Always `git pull develop` before creating feature branches**
- **BOM precedence**: spring-boot-dependencies imported BEFORE horizon BOM in root POM
- **Delta-V version**: `0.0.1-SNAPSHOT`, Horizon JARs: `1.0.3`
- **Hibernate 7 InetAddress**: always pass `InetAddressUtils.str()` for JPQL bind params
