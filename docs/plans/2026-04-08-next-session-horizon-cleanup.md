# Next Session: Fix delta-v-horizon Dependencies at the Source

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Delta-V has been fighting transitive dependency problems by adding Maven `<exclusion>` tags in consumer modules (daemon-common, daemon-boot-*, daemon-registry, event-forwarder-kafka). This is **treating symptoms, not causes**. The real problem is that delta-v-horizon (1.0.4) publishes JARs that export ActiveMQ, ServiceMix, C3P0, and broken interface references as transitive dependencies.

**Current state:** All JPA daemons crash with `NoClassDefFoundError: org/opennms/netmgt/model/OnmsCriteria` after a clean `.m2` cache. This was hidden by stale cached artifacts until the 1.0.4 bump forced a cache purge. The `OnmsCriteria` class only exists in `opennms-model` (excluded in PR #127), but `OnmsDao` (in `opennms-dao-api`) has default methods that reference it.

**Architectural principle:** Exclusions in delta-v are a code smell. If a horizon JAR exports something delta-v doesn't want, fix the horizon JAR — don't patch every consumer. The enforcer rule in the parent POM should be a safety net that never fires, not a whack-a-mole tool.

## What Needs To Be Done

### 1. Fix OnmsCriteria in delta-v-horizon (BLOCKER)

`opennms-dao-api`'s `OnmsDao<T,K>` interface has two default methods:
```java
public default List<T> findMatching(OnmsCriteria criteria);
public default int countMatching(OnmsCriteria criteria);
```
These reference `org.opennms.netmgt.model.OnmsCriteria` from `opennms-model`. When Spring introspects any class implementing `OnmsDao` (JdbcDistPollerDao, AlarmDaoJpa, etc.), it loads all method signatures including these defaults → `ClassNotFoundException`.

**Fix options:**
- **A (preferred):** Remove these default methods from `OnmsDao` in horizon. They're legacy Hibernate 3 wrappers. All delta-v code uses `findMatching(org.opennms.core.criteria.Criteria)` — the modern version. Check that no Horizon JAR code calls `findMatching(OnmsCriteria)` first.
- **B (fallback):** Add `OnmsCriteria` as a stub class to `model-jakarta` or `dao-jpa-support` — but this perpetuates the dependency on a dead API.

### 2. Remove ActiveMQ from horizon JARs

`core/daemon` (org.opennms.core:org.opennms.core.daemon) declares direct dependencies on:
- `org.opennms.dependencies:activemq-dependencies`
- `org.apache.activemq:activemq-camel`
- `org.opennms.features.activemq:*`

Delta-V daemons use Kafka exclusively. Remove these dependencies from `core/daemon`'s POM in horizon. The only classes delta-v uses from `core/daemon` are `SpringServiceDaemon` and `AbstractServiceDaemon` (confirmed by bytecode analysis — zero ActiveMQ references).

### 3. Remove C3P0 from horizon JARs

`core/db` (org.opennms.core:org.opennms.core.db) depends on `com.mchange:c3p0`. Delta-V uses HikariCP via Spring Boot. Also check `quartz-dependencies` which pulls C3P0 via a different path.

### 4. Remove ServiceMix Kafka wrappers from horizon JARs

`ipc/common/kafka` (org.opennms.core.ipc.common:org.opennms.core.ipc.common.kafka) depends on ServiceMix Kafka wrappers (`org.apache.servicemix.bundles:org.apache.servicemix.bundles.kafka-clients`). These are OSGi-wrapped versions of the standard `kafka-clients` JAR. Delta-V uses the standard `kafka-clients` from Spring Boot.

### 5. Remove ALL exclusions from delta-v

After fixing horizon, **remove every ActiveMQ, ServiceMix Kafka, C3P0, and OnmsCriteria exclusion from ALL delta-v POMs**. The enforcer rule in the parent POM will catch any remaining violations. If the enforcer fires, it means the horizon fix is incomplete — go back and fix horizon, don't add another exclusion.

Files with exclusions to audit and clean:
- `core/daemon-common/pom.xml` — ActiveMQ, C3P0, opennms-model exclusions on core.daemon
- `core/daemon-registry/pom.xml` — ActiveMQ, C3P0 on poller.monitors.core and snmp-collector
- `core/daemon-boot-minion/pom.xml` — ActiveMQ, ServiceMix Kafka, C3P0 on events.traps
- `core/daemon-boot-minion-common/pom.xml` — ServiceMix Kafka on ipc.rpc.kafka
- `core/daemon-boot-eventtranslator/pom.xml` — C3P0 on event-translator
- `core/daemon-boot-syslogd/pom.xml` — C3P0 on events.syslog
- `core/daemon-boot-collectd/pom.xml` — C3P0 on collection.impl
- `core/daemon-boot-perspectivepollerd/pom.xml` — C3P0 on perspectivepoller
- `core/daemon-boot-pollerd/pom.xml` — C3P0 on poller.impl
- `core/daemon-boot-provisiond/pom.xml` — C3P0 on provisiond, provision-persistence
- `core/event-forwarder-kafka/pom.xml` — ActiveMQ, C3P0, ServiceMix Kafka on events.daemon, opennms-config

**NOTE:** Keep the `opennms-model` exclusions — those are structural (javax vs jakarta persistence split-package), not horizon dependency bugs.

## Steps

1. In delta-v-horizon repo:
   - Remove `OnmsCriteria` default methods from `OnmsDao` interface
   - Remove ActiveMQ deps from `core/daemon`
   - Remove C3P0 from `core/db` (or exclude it in core/db's POM)
   - Remove ServiceMix Kafka wrappers from `ipc/common/kafka`
   - Bump to 1.0.5, tag, push
   - Wait for CI publish

2. In delta-v repo:
   - Bump `deltav.horizon.version` to 1.0.5
   - Purge `.m2` cache: `find ~/.m2/repository/org/opennms -name "*1.0.4*" -delete`
   - Remove ALL ActiveMQ/C3P0/ServiceMix Kafka exclusions from all POMs (keep opennms-model exclusions)
   - Build: `make build` — enforcer should pass with zero violations
   - Rebuild Docker: `build.sh deltav`
   - Deploy and verify all daemons start: `deploy.sh up full`
   - Run all 8 E2E suites: expect 119/119

## Key Files in delta-v-horizon

- `opennms-dao-api/src/main/java/org/opennms/netmgt/dao/api/OnmsDao.java` — remove OnmsCriteria default methods
- `core/daemon/pom.xml` — remove ActiveMQ deps
- `core/db/pom.xml` — remove or exclude C3P0
- `core/ipc/common/kafka/pom.xml` — remove ServiceMix kafka-clients
- `dependencies/quartz/pom.xml` — check for C3P0

## Current Branch State

- `fix/centralize-banned-deps` on delta-v has the enforcer rule + scattered exclusions (DO NOT merge — discard or rebase after horizon fix)
- `develop` on delta-v has the OnmsCriteria crash (all JPA daemons fail after clean .m2 cache)
- delta-v-horizon is at 1.0.4 on main

## Important Reminders

- **Never create PRs against OpenNMS/opennms** — always `--repo pbrane/delta-v`
- **Exclusions are symptoms** — if the enforcer fires, fix horizon, don't add exclusions
- **Always purge .m2 cache** when testing horizon version bumps: stale artifacts mask real problems
- **`mvn versions:set`** for horizon version bumps — never manually edit just the root POM
- The `.m2 cache hiding regressions` problem has bitten us twice now (reactor pruning test-scope gap, and this OnmsCriteria crash)

## E2E Suite Count (target after fix)

| Suite | Expected |
|-------|----------|
| test-e2e.sh | 12 |
| test-syslog-e2e.sh | 15 |
| test-passive-e2e.sh | 17 |
| test-minion-rpc-e2e.sh | 18 |
| test-collectd-e2e.sh | 4 |
| test-minion-e2e.sh | 13 |
| test-perspective-e2e.sh | 22 |
| test-enlinkd-e2e.sh | 18 |
| **Total** | **119** |
