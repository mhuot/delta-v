# Next Session: Fix Daemon Startup on Current Develop

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Branch: start from `develop` (clean, pulled). All 12 daemon boot JARs built from develop fail to start in Docker. The root causes are fully diagnosed.

**CRITICAL: Before building, clean stale .m2 artifacts:**
```bash
find ~/.m2/repository/org/opennms -name "36.0.0-SNAPSHOT" -type d -exec rm -rf {} + 2>/dev/null
```

## Root Causes (all on develop, not stale artifacts)

### Issue 1: EventConfEnrichmentService pulls opennms-model types
`core/daemon-common/src/main/java/org/opennms/core/daemon/common/EventConfEnrichmentService.java` uses `DefaultEventConfDao` from `opennms-config`. `DefaultEventConfDao` internally calls code paths that load `ResourceTypeUtils` and `OnmsEventParameter` from `opennms-model` — but opennms-model is NOT on the daemon classpath (PR #73 removed it intentionally).

**Error:** `ClassNotFoundException: org.opennms.netmgt.model.ResourceTypeUtils` (trapd, pollerd, collectd, enlinkd, telemetryd, syslogd, eventtranslator, discovery) and `ClassNotFoundException: org.opennms.netmgt.model.OnmsEventParameter` (alarmd, bsmd, provisiond).

**Fix options:**
- A) Rewrite `EventConfEnrichmentService` to use JDBC directly instead of `DefaultEventConfDao` (which drags in opennms-model transitively). The `JdbcEventConfLoader` in daemon-boot-trapd already does this pattern.
- B) Move `ResourceTypeUtils` and `OnmsEventParameter` to model-api. But `ResourceTypeUtils` has many dependencies (Spring, collection API) — it's not lightweight enough for model-api.
- C) Make `DefaultEventConfDao.loadEventsFromDB()` not call the code path that needs `ResourceTypeUtils`. Requires understanding what method chain triggers it.

**Recommended: Option A** — the cleanest. `EventConfEnrichmentService` should load event conf directly from JDBC (like `JdbcEventConfLoader` already does in daemon-boot-trapd), not through `DefaultEventConfDao`.

### Issue 2: PrimaryType not in model-api
`PrimaryTypeConverter` in model-jakarta references `org.opennms.netmgt.model.PrimaryType` which is only in opennms-model. Since opennms-model isn't on the classpath, this fails.

**Fix:** Copy `PrimaryType` to `core/opennms-model-api/` (strip `@Embeddable`/`@Transient` javax.persistence annotations — the converter handles persistence).

### Issue 3: OnmsAssetRecord missing from PersistenceManagedTypes
`OnmsNode` has `@OneToOne(mappedBy="node")` to `OnmsAssetRecord`. Hibernate 7 requires all association targets to be in the same persistence unit.

**Fix:** Add `OnmsAssetRecord.class.getName()` to `PersistenceManagedTypes` in all 7 daemon JPA configurations that include `OnmsNode`: alarmd, bsmd, collectd, enlinkd, perspectivepollerd, pollerd, provisiond.

### Issue 4: LldpLinkDaoJpa InetAddress type mismatch
`LldpLinkDaoJpa.getIfIndex()` passes `String portId` to a query parameter that expects `InetAddress`. Hibernate 7 enforces strict type checking.

**Fix:** Parse `portId` to `InetAddress` before the query: `InetAddress.getByName(portId)`, catch `UnknownHostException` and return -1.

### Issue 5: Collectd E2E test missing provisioning config
`test-collectd-e2e.sh` creates the requisition XML but doesn't add a `requisition-def` to `provisiond-configuration.xml`, so Provisiond never imports it.

**Fix:** Add the same provisiond-configuration.xml overlay pattern used by test-passive-e2e.sh.

## What's Already Working on Develop
- Minion Spring Boot (PR #76) — deployed, 10 protocol handlers
- BSM jakarta port (PR #76) — 17 entity classes ported
- model-api + model-jakarta split (PR #73) — clean separation
- No opennms-model on daemon classpath (PR #73) — no split-package issues
- Labbox Spring Boot Minion deployed at `pbrane/minion-boot:latest`

## Verification Plan
After fixes, build → Docker → verify all 12 daemons healthy → run all 6 E2E tests.
