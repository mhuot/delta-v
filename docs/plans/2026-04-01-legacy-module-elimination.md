# Plan: Legacy Module Elimination

> A roadmap for removing `opennms-dao`, `opennms-model`, and `opennms-config` from the
> Delta-V reactor. Written after Phase 3 (PR #102) eliminated `opennms-model` from the
> Spring Boot daemon dependency tree.

---

## Where We Are Today

The Spring Boot daemon stack (`daemon-boot-*` modules) is architecturally free of
legacy modules. Phases 1-4 of Karaf removal established this:

| Module | Direct dep of daemon-boots? | How daemon-boots use it |
|--------|---------------------------|------------------------|
| `opennms-model` | No | Replaced by `model-jakarta` (same package, Jakarta JPA) |
| `opennms-config` | No | Replaced by Jackson XmlMapper + config-api defaults |
| `opennms-dao` | No | Replaced by `model-jakarta` DaoJpa classes |
| `opennms-dao-api` | Yes (intentional) | DAO interfaces — shared between legacy and Jakarta worlds |
| `opennms-config-api` | Yes (intentional) | Config interfaces — shared between legacy and Jakarta worlds |
| `opennms-config-model` | Yes (intentional) | JAXB config model classes (Event, SnmpConfig, etc.) |

**The problem:** These legacy modules still **leak into** daemon-boot fat JARs through
transitive dependencies, bloating the classpath and forcing the CI to compile legacy code
that daemon-boots never execute.

---

## The Dependency Graph (Simplified)

```
daemon-boot-alarmd
  ├── daemon-common ──→ model-jakarta ──→ dao-jpa-support    (CLEAN — no legacy)
  ├── model-jakarta                                          (CLEAN)
  ├── opennms-alarmd ──→ opennms-dao ──→ opennms-model       (LEAKS legacy)
  └── opennms-alarm-api ──→ opennms-model                    (LEAKS legacy)
```

Every daemon-boot module follows this pattern: its direct deps are clean, but the
**feature modules** (alarmd, bsmd, discovery, poller, collectd, enlinkd, etc.) pull in
legacy modules transitively.

### Which daemon-boots pull in which legacy modules?

| daemon-boot | opennms-dao via | opennms-model via | opennms-config via |
|-------------|----------------|-------------------|-------------------|
| alarmd | opennms-alarmd | opennms-alarmd, alarm-api | — |
| bsmd | bsm-service-impl | bsm-service-api | — |
| collectd | collection-impl | opennms-dao-api | — |
| discovery | features/discovery | features/discovery | — |
| enlinkd | enlinkd-daemon | enlinkd-persistence-impl | — |
| eventtranslator | — | event-translator | — |
| minion | (via proto modules) | (via proto modules) | — |
| perspectivepollerd | perspectivepoller | perspectivepoller | — |
| pollerd | poller-impl | opennms-dao-api | — |
| provisiond | opennms-provision | opennms-provision | — |
| syslogd | — | opennms-dao-api | — |
| telemetryd | — | opennms-dao-api | — |
| trapd | — | opennms-dao-api | — |

**Key observation:** `opennms-model` leaks into ALL daemon-boots through `opennms-dao-api`
(which every daemon-boot depends on for DAO interfaces). This is unavoidable without
changing `opennms-dao-api` itself.

---

## What Blocks Deletion

### opennms-dao (37 Hibernate DAO classes)

**Blocked by:** 9 daemon-boots pull it transitively through feature modules.

**At runtime:** daemon-boots never instantiate Hibernate DAOs. Spring Boot's component
scan finds the `@Repository` DaoJpa classes in model-jakarta instead.

**To unblock:** Exclude `opennms-dao` from each feature module dependency in daemon-boot
POMs. This is purely a POM change — no code changes, no porting.

**To delete entirely:** Also remove from legacy consumers:
- `opennms-webapp-rest` (REST API — not in daemon-boot reactor)
- `opennms-provision` (legacy provisioning wiring)
- 14 feature modules that depend on it directly

### opennms-model (181 entity classes)

**Blocked by:** `opennms-dao-api` depends on it. Every DAO interface (NodeDao, AlarmDao,
etc.) uses `opennms-model` entity types as return types and parameters. Since
daemon-boots depend on `opennms-dao-api`, they inherit `opennms-model` transitively.

**At runtime:** The split-package trick works — `model-jakarta` defines the same classes
in the same package (`org.opennms.netmgt.model`). The JVM loads model-jakarta's copies.
opennms-model's copies are on the classpath but unused (and harmless, since their
`javax.persistence` annotations are invisible to Hibernate 7).

**To unblock:** This is the hardest one. Options:
1. **Accept the transitive leak** — opennms-model stays on the classpath as dead weight.
   Harmless at runtime. Ugly but pragmatic.
2. **Extract DAO interfaces to a new module** that doesn't depend on opennms-model —
   massive refactor touching every DAO interface.
3. **Exclude opennms-model from opennms-dao-api in daemon-boot POMs** — risky, since the
   compiler needs the types to resolve DAO interface signatures.

**Recommendation:** Option 1 for now. opennms-model on the classpath is harmless.
Focus effort on removing opennms-dao (which has actual Hibernate 3.x runtime code).

### opennms-config (80 factory classes)

**Blocked by:** 7 feature modules still use it directly (eif-adapter, mib-compiler,
perspectivepoller, vaadin-*, wsman). All 13 daemon-boots are already free of it.

**To unblock:** Continue the Phase 4 pattern — move each factory to its feature module
or replace with Jackson XmlMapper + config-api. This is already done for 8 factories
(Trapd, Syslogd, Discovery, EventTranslator, SnmpPeer, Poller, Collectd, Filter).

---

## Recommended Execution Order

### Phase 5a: Exclude opennms-dao from daemon-boot fat JARs

**Effort:** Small (POM-only changes)
**Impact:** Shrinks fat JARs, removes 37 Hibernate DAO classes from runtime classpath

For each daemon-boot that transitively pulls `opennms-dao`, add an exclusion on the
feature module dependency that causes it:

```xml
<!-- Example: daemon-boot-alarmd/pom.xml -->
<dependency>
    <groupId>org.opennms</groupId>
    <artifactId>opennms-alarmd</artifactId>
    <exclusions>
        <exclusion><groupId>org.opennms</groupId><artifactId>opennms-dao</artifactId></exclusion>
    </exclusions>
</dependency>
```

**Verification:** All 13 daemon-boots compile and produce fat JARs. E2E tests pass.

### Phase 5b: Port remaining DAOs needed by daemon-boots (if any)

After excluding opennms-dao, some daemon-boots may fail at runtime if a feature module
tries to instantiate a DAO that only exists in opennms-dao (not model-jakarta).

**Currently ported (15 Jakarta DAOs in model-jakarta):**
AlarmDao, ApplicationDao, CategoryDao, DistPollerDao, HwEntityAttributeTypeDao,
HwEntityDao, IpInterfaceDao, MonitoredServiceDao, MonitoringLocationDao,
MonitoringSystemDao, NodeDao, OutageDao, RequisitionedCategoryAssociationDao,
ServiceTypeDao, SnmpInterfaceDao

**Potentially needed (check at runtime):**
- AcknowledgmentDao — used by Alarmd for alarm acknowledgments
- AlarmAssociationDao — used by Alarmd for alarm situations
- MemoDao — used by Alarmd for alarm memos
- AssetRecordDao — used by Provisiond
- MinionDao — used by Minion health checks
- PathOutageDao — used by Pollerd for path outage logic
- NodeLabelDao — used by Provisiond

**Not needed by daemon-boots (legacy webapp/REST only):**
AlarmRepository, EventdServiceManager, FilterFavoriteDao, IfLabelDao,
InterfaceToNodeCacheDao (daemon-common has its own JdbcInterfaceToNodeCache),
ResourceReferenceDao

**Approach:** Exclude opennms-dao first, run E2E tests, see what fails. Port only what's
actually needed. Don't guess — let the runtime tell you.

### Phase 5c: Remove opennms-dao from the reactor (future)

Once no daemon-boot module needs opennms-dao at compile time or runtime:
1. Remove opennms-dao from `pom.xml` reactor `<modules>` list
2. Move it to a `legacy/` directory for eventual deletion
3. Legacy consumers (webapp-rest, provision) can be wired differently or deleted

### Phase 6: Clean up opennms-model (future)

opennms-model will remain as a transitive compile dependency through opennms-dao-api.
This is acceptable — it's harmless at runtime and removing it requires refactoring
every DAO interface (not worth the effort until opennms-dao-api itself is replaced).

### Phase 7: Complete opennms-config elimination (ongoing)

Continue the established pattern: for each remaining config factory, either:
- Move it to the feature module that owns the config
- Replace the JAXB factory with Jackson XmlMapper + config-api interface

Remaining factories (7):
- eif-adapter config
- mib-compiler config
- perspectivepoller config
- vaadin-snmp-events-and-metrics config
- vaadin-surveillance-views config
- wsman config
- (plus any transitive references)

---

## Summary

| Phase | What | Effort | Blocks |
|-------|------|--------|--------|
| **5a** | Exclude opennms-dao from daemon-boot POMs | Small | Nothing — do first |
| **5b** | Port DAOs that daemon-boots need at runtime | Medium | E2E test results from 5a |
| **5c** | Remove opennms-dao from reactor | Small | Completion of 5a + 5b |
| **6** | Accept opennms-model as harmless transitive | None | — |
| **7** | Finish opennms-config factory migration | Medium | Feature module owners |

**The immediate next step is Phase 5a** — a POM-only change to exclude opennms-dao
from daemon-boot fat JARs, followed by E2E testing to discover which DAOs (if any)
need porting.
