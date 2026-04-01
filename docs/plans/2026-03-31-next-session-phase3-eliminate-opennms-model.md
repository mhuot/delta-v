# Next Session: Phase 3 — Eliminate opennms-model Dependency

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Design spec: `docs/superpowers/specs/2026-03-30-karaf-removal-design.md`

Phase status:
- Phase 1: DONE — Dead Karaf infrastructure deleted
- Phase 2: DONE — Bundle→jar packaging converted
- Phase 3: THIS SESSION — Eliminate opennms-model
- Phase 4: DONE (PRs #91–#101) — All 13 daemons free of opennms-config

## Goal: Remove opennms-model from the dependency tree of all daemon-boot modules

All 14 daemon-boot modules already exclude opennms-model via `<exclusion>` elements and
use `core/opennms-model-jakarta` instead. The remaining work is to eliminate opennms-model
as a *compile-time* dependency of `model-jakarta` and `daemon-common`, so the exclusions
become unnecessary and opennms-model can eventually be deleted from the reactor entirely.

## Current Dependency Graph (the problem)

```
daemon-boot-* → model-jakarta → opennms-model (excluded at runtime)
daemon-boot-* → daemon-common → opennms-model (excluded at runtime)
model-jakarta → daemon-common (for AbstractDaoJpa)
```

The daemon-boot POMs exclude `opennms-model` everywhere, but model-jakarta and daemon-common
still list it as a compile dependency. This means opennms-model must stay in the reactor
and continue to compile.

## Blockers — Only Two

### Blocker 1: OnmsCriteria (model-jakarta → opennms-model)

**Location:** `opennms-model/src/main/java/org/opennms/netmgt/model/OnmsCriteria.java`

OnmsCriteria is a deprecated Hibernate 3.x `DetachedCriteria` wrapper (~395 lines). It is
referenced in model-jakarta by 6 DAO JPA files, and **every single usage throws
`UnsupportedOperationException`**:

| File | Methods |
|------|---------|
| `AlarmDaoJpa.java` | `findMatching(OnmsCriteria)`, `countMatching(OnmsCriteria)` — throws UOE |
| `NodeDaoJpa.java` | `findMatching(OnmsCriteria)`, `countMatching(OnmsCriteria)` — throws UOE |
| `OutageDaoJpa.java` | `findMatching(OnmsCriteria)`, `countMatching(OnmsCriteria)` — throws UOE |
| `IpInterfaceDaoJpa.java` | `findMatching(OnmsCriteria)`, `countMatching(OnmsCriteria)` — throws UOE |
| `MonitoredServiceDaoJpa.java` | `findMatching(OnmsCriteria)`, `countMatching(OnmsCriteria)` — throws UOE |
| `SnmpInterfaceDaoJpa.java` | `findMatching(OnmsCriteria)`, `countMatching(OnmsCriteria)` — throws UOE |

These methods exist because the DAOs implement `LegacyOnmsDao<T,K>` from `opennms-dao-api`:

```java
// opennms-dao-api/src/main/java/org/opennms/netmgt/dao/api/LegacyOnmsDao.java
@Deprecated
public interface LegacyOnmsDao<T, K extends Serializable> extends OnmsDao<T,K> {
    List<T> findMatching(OnmsCriteria criteria);
    int countMatching(final OnmsCriteria onmsCrit);
}
```

**No daemon at runtime calls these methods.** They are dead code inherited from the
Karaf-era Hibernate 3.x DAO layer.

### Blocker 2: daemon-common → opennms-model (entity types)

`core/daemon-common` imports these types from `org.opennms.netmgt.model`:

| Class | Used By | Purpose |
|-------|---------|---------|
| `OnmsDistPoller` | `JdbcDistPollerDao` | Thin DAO for `monitoringsystems` table |
| `OnmsMonitoringSystem` | `JdbcDistPollerDao` | Return type |
| `OnmsNode` | `DaemonEntityScopeProvider` | Entity scope resolution |
| `OnmsIpInterface` | `DaemonEntityScopeProvider` | Entity scope resolution |
| `OnmsSnmpInterface` | `DaemonEntityScopeProvider` | Entity scope resolution |
| `OnmsMonitoredService` | `DaemonEntityScopeProvider` | Entity scope resolution |
| `OnmsAssetRecord` | `DaemonEntityScopeProvider` | Entity scope resolution |
| `OnmsGeolocation` | `DaemonEntityScopeProvider` | Entity scope resolution |
| `OnmsMetaData` | `DaemonEntityScopeProvider` | Entity scope resolution |
| `EventConfEvent` | `DaemonEventConfDao`, `EventConfEnrichmentService` | Eventconf loading |
| `EventConfSource` | `EventConfEnrichmentService` | Eventconf source enum |

These same entity/type classes exist in model-jakarta (same package, same class names).
However, switching daemon-common to depend on model-jakarta creates a cycle:
`model-jakarta → daemon-common → model-jakarta`.

## Recommended Approach

### Step 1: Kill OnmsCriteria — Remove LegacyOnmsDao

Since all LegacyOnmsDao methods throw UnsupportedOperationException in model-jakarta DAOs:

1. **In `opennms-dao-api`:** Change the 6 DAO interfaces that extend `LegacyOnmsDao<T,K>`
   to extend `OnmsDao<T,K>` directly instead.

   Find them:
   ```bash
   grep -r "extends LegacyOnmsDao" opennms-dao-api/
   ```

2. **In `model-jakarta`:** Delete the `findMatching(OnmsCriteria)` and
   `countMatching(OnmsCriteria)` stub methods from the 6 DaoJpa files.

3. **Delete `LegacyOnmsDao.java`** from `opennms-dao-api`.

4. **Remove `OnmsCriteria` import** from `opennms-dao-api/pom.xml` (if opennms-model
   was there for this reason).

5. **Remove `opennms-model` dependency from `model-jakarta/pom.xml`** — verify no other
   imports remain. The grep output shows all other `org.opennms.netmgt.model.*` imports
   are either:
   - Intra-module (model-jakarta's own entity classes in the same package)
   - From model-api (enums, DTOs, interfaces — already a dependency)
   - From features/events/api (EventBuilder — already a dependency)

### Step 2: Break daemon-common → opennms-model

Two approaches (pick one):

**Option A: Extract lightweight interfaces to model-api** (preferred)
- The entity types (`OnmsNode`, `OnmsIpInterface`, etc.) used by `DaemonEntityScopeProvider`
  are accessed via getter methods only (read-only).
- Create interfaces in model-api (e.g., `NodeInfo`, `IpInterfaceInfo`) that expose
  the getters needed by `DaemonEntityScopeProvider`.
- Have the entity classes in model-jakarta implement these interfaces.
- daemon-common depends on model-api (no cycle).

**Option B: Move DaemonEntityScopeProvider out of daemon-common**
- Move `DaemonEntityScopeProvider` and its dependencies (`JdbcDistPollerDao`, etc.)
  into model-jakarta or a new `core/daemon-entity-scope` module.
- daemon-common loses its opennms-model dependency entirely.
- daemon-boot modules add the new module.

**Option C: Accept the provided-scope hack**
- Change daemon-common's opennms-model dependency to `<scope>provided</scope>`.
- At runtime, model-jakarta supplies the same classes.
- Least disruptive, but doesn't actually eliminate the compile dependency.

### Step 3: Remove opennms-model from model-jakarta and daemon-common POMs

After Steps 1-2, verify:
```bash
# model-jakarta should compile without opennms-model
./compile.pl -DskipTests --projects :org.opennms.core.model-jakarta -am install

# daemon-common should compile without opennms-model
./compile.pl -DskipTests --projects :org.opennms.core.daemon-common -am install

# All 13 daemon-boots should compile (the real test)
./compile.pl -DskipTests --projects :org.opennms.core.daemon-boot-alarmd,...(all 13) install
```

(Skip `-am` on the second and third builds for speed — deps already installed from first.)

### Step 4: Clean up daemon-boot POMs

Remove all `<exclusion><artifactId>opennms-model</artifactId></exclusion>` entries
from the 14 daemon-boot POMs. They're no longer needed since opennms-model is no
longer in the transitive dependency tree.

### Step 5: Build all 13 daemon-boots

Final verification:
```bash
./compile.pl -DskipTests --projects :org.opennms.core.daemon-boot-alarmd,...(all 13) -am install
```

## What NOT To Do

- **Don't delete `opennms-model/` from the reactor yet.** Legacy modules (`opennms-dao`,
  `opennms-webapp-rest`, `opennms-provision`, etc.) still depend on it. Deletion is a
  future step after those modules are either removed or migrated.
- **Don't touch `opennms-dao-api`'s other interfaces.** Only change the ones that
  extend `LegacyOnmsDao`.
- **Don't refactor `DaemonEntityScopeProvider`'s logic.** Just change where the types
  come from.

## Files To Touch

### Step 1 (OnmsCriteria removal):
- `opennms-dao-api/src/main/java/org/opennms/netmgt/dao/api/LegacyOnmsDao.java` — DELETE
- `opennms-dao-api/src/main/java/org/opennms/netmgt/dao/api/*Dao.java` — 6 interfaces: change extends
- `core/opennms-model-jakarta/src/main/java/org/opennms/netmgt/model/jakarta/dao/*DaoJpa.java` — 6 files: delete stubs
- `core/opennms-model-jakarta/pom.xml` — remove opennms-model dependency
- `opennms-dao-api/pom.xml` — check if opennms-model can be removed

### Step 2 (daemon-common cycle break):
- Depends on chosen option (A, B, or C)
- If Option A: new interfaces in `core/opennms-model-api`, entity impls, daemon-common POM change
- If Option B: new module or move files, daemon-common POM change, daemon-boot POM additions

### Step 4 (exclusion cleanup):
- All 14 `core/daemon-boot-*/pom.xml` files — remove opennms-model exclusions

## Established Patterns

- **Model-api is persistence-free.** No JPA, no Hibernate. Only enums, interfaces, DTOs, value types.
- **Model-jakarta entities use `jakarta.persistence` annotations** with `allocationSize=1` for sequences.
- **Daemon-boot POM pattern:** explicit exclusion of opennms-model, explicit inclusion of model-jakarta before any transitive path.

## Pre-existing Issues (ignore)
- `core/test-api/karaf` directory exists but is removed from reactor
- `daemon-boot-eventtranslator/pom.xml` has a duplicate dependency warning (`org.opennms.core.model-api`)
- The `opennms` assembly module has a pre-existing maven-assembly-plugin failure
