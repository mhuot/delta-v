# Next Session: Phase 5a — Exclude opennms-dao from daemon-boot fat JARs

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Design plan: `docs/plans/2026-04-01-legacy-module-elimination.md`

Phase status:
- Phase 1: DONE — Dead Karaf infrastructure deleted
- Phase 2: DONE — Bundle→jar packaging converted
- Phase 3: DONE (PR #102) — opennms-model eliminated from daemon-common, cycle broken
- Phase 4: DONE (PRs #91–#101) — All 13 daemons free of opennms-config
- Phase 5a: THIS SESSION — Exclude opennms-dao from daemon-boot fat JARs

## Goal

Remove `opennms-dao` (37 Hibernate 3.x DAO classes) from the runtime classpath of all
daemon-boot fat JARs. At runtime, daemon-boots use model-jakarta's `@Repository` DaoJpa
classes — the Hibernate DAOs are dead weight that leak in through feature module
transitive dependencies.

## Current State

9 of 13 daemon-boots pull `opennms-dao` transitively:

| daemon-boot | opennms-dao comes through |
|-------------|--------------------------|
| alarmd | opennms-alarmd |
| bsmd | bsm-service-impl |
| collectd | collection-impl |
| discovery | features/discovery |
| enlinkd | enlinkd-daemon or enlinkd-persistence-impl |
| perspectivepollerd | perspectivepoller |
| pollerd | poller-impl |
| provisiond | opennms-provision |
| syslogd | (runtime scope — check if actually present) |

4 daemon-boots (eventtranslator, minion, telemetryd, trapd) do NOT pull opennms-dao.

## Approach

For each affected daemon-boot POM, add `<exclusion>` for `opennms-dao` on the feature
module dependency that causes it. This is the same pattern used throughout Phases 3-4.

### Step 1: Verify the transitive paths

For each daemon-boot, confirm which dependency brings opennms-dao:
```bash
mvn dependency:tree --projects :org.opennms.core.daemon-boot-alarmd \
  -Dincludes=org.opennms:opennms-dao 2>&1 | grep "opennms-dao"
```

### Step 2: Add exclusions

Example pattern:
```xml
<dependency>
    <groupId>org.opennms</groupId>
    <artifactId>opennms-alarmd</artifactId>
    <exclusions>
        <exclusion><groupId>org.opennms</groupId><artifactId>opennms-dao</artifactId></exclusion>
    </exclusions>
</dependency>
```

### Step 3: Build all 13 daemon-boots

```bash
mvn -DskipTests --projects :org.opennms.core.daemon-boot-alarmd,...(all 13) -am install
```

### Step 4: E2E smoke test on labbox

Deploy updated fat JARs to labbox and verify daemons start and function. If a daemon
fails at runtime with a missing DAO class, that DAO needs to be ported to model-jakarta
(see Phase 5b in the elimination plan).

## What NOT To Do

- **Don't modify opennms-dao itself.** We're excluding it, not fixing it.
- **Don't port DAOs preemptively.** Let runtime failures tell you what's needed.
- **Don't touch opennms-dao-api.** The DAO interfaces are shared and stay as-is.

## Currently Ported DAOs (15 in model-jakarta)

AlarmDao, ApplicationDao, CategoryDao, DistPollerDao, HwEntityAttributeTypeDao,
HwEntityDao, IpInterfaceDao, MonitoredServiceDao, MonitoringLocationDao,
MonitoringSystemDao, NodeDao, OutageDao, RequisitionedCategoryAssociationDao,
ServiceTypeDao, SnmpInterfaceDao

## Potentially Needed (port only if E2E tests fail)

- AcknowledgmentDao — Alarmd alarm acknowledgments
- AlarmAssociationDao — Alarmd alarm situations
- MemoDao — Alarmd alarm memos
- AssetRecordDao — Provisiond
- MinionDao — Minion health checks
- PathOutageDao — Pollerd path outage logic
- NodeLabelDao — Provisiond
