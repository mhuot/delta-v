# Next Session: Karaf Removal Phase 3 — Eliminate opennms-model

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Design spec: `docs/superpowers/specs/2026-03-30-karaf-removal-design.md`
Phase 1 (delete dead Karaf infrastructure) — PR #84, MERGED.
Phase 2 (convert bundle to jar packaging) — PR #89, MERGED.

**This phase can run in parallel with Phase 4** (eliminate opennms-config). They are independent.

All 12 Spring Boot daemons are running. The daemon-boot POMs explicitly exclude `opennms-model` from their transitive dependencies. They use `opennms-model-jakarta` (JPA entities with jakarta.persistence) + `opennms-model-api` (enums/interfaces). However, `opennms-model` is still a compile dependency of 35 modules — including `core/daemon-common` — and its absence causes `EventBuilder` ClassNotFoundException at runtime in 9 of 12 daemons.

## Goal

Move the 6 remaining classes from `opennms-model` into `core/opennms-model-api`, then delete `opennms-model/` entirely.

## Current State

### What model-api already contains (20 classes)
Enums, interfaces, and utility classes — no entities, no persistence annotations:
- `OnmsSeverity`, `AckAction`, `AckType`, `PrimaryType`, `StatusType`
- `ServiceDaemon`, `ServiceDaemonMBean`, `ServiceInfo`, `ServiceSelector`
- `ResourceId`, `ResourcePath`, `FilterManager`
- `DiscoveryProtocol`, `TroubleTicketState`, `SurveillanceStatus`
- `outage/OutageSummary`, `perspectivepolling/Location`

### What model-jakarta already contains (28 entity classes)
All JPA entities with jakarta.persistence annotations. model-jakarta does NOT import from opennms-model — it's clean.

### The 6 classes that need to move to model-api

All in `opennms-model/src/main/java/org/opennms/netmgt/model/`:

| Class | Subdirectory | Nature | Dependencies |
|-------|-------------|--------|-------------|
| `EventBuilder` | `events/` | Utility builder | Uses `OnmsEvent`, `OnmsSeverity`, event XML model classes |
| `NodeLabelChangedEventBuilder` | `events/` | Utility builder | Uses `EventBuilder` |
| `AlarmSummary` | `alarm/` | DTO/summary | Simple POJO, no JPA |
| `SituationSummary` | `alarm/` | DTO/summary | Simple POJO, no JPA |
| `HeatMapElement` | root | DTO/summary | Simple POJO, no JPA |
| `OnmsCriteria` | root | Hibernate Criteria wrapper | Uses Hibernate 3.x Criteria API |

### Key complexity: EventBuilder

`EventBuilder` is the most complex class. It creates `Event` objects from the eventconf XML model (`org.opennms.netmgt.xml.event.Event`). It does NOT depend on JPA entities — it depends on:
- `org.opennms.netmgt.xml.event.Event` (from `opennms-config-model`)
- `org.opennms.netmgt.model.OnmsSeverity` (already in `model-api`)

This means EventBuilder can move to model-api with a dependency on `opennms-config-model` added to model-api's POM.

### Key complexity: OnmsCriteria

`OnmsCriteria` wraps Hibernate's legacy `org.hibernate.Criteria` API. In Hibernate 7 (used by model-jakarta), the Criteria API is removed. This class may need:
- Deletion (if no daemon uses it at runtime)
- Replacement with jakarta.persistence.criteria equivalent
- Move to a compatibility module

Check who actually calls `OnmsCriteria` before deciding.

### Modules that depend on opennms-model (35 total)

**Critical path (daemon infrastructure):**
- `core/daemon-common` — imports EventBuilder for event processing
- `core/daemon` — legacy daemon base classes
- `core/criteria` — criteria/filter support

**Feature modules (17):**
- `features/event-translator`, `features/perspectivepoller`, `features/discovery`, etc.

**Legacy modules (15):**
- `opennms-config`, `opennms-config-api`, `opennms-dao`, `opennms-dao-api`, `opennms-provision/*`, etc.

## Approach

### Step 1: Move AlarmSummary, SituationSummary, HeatMapElement to model-api

These are simple POJOs with no external dependencies. Straightforward move.

### Step 2: Move EventBuilder and NodeLabelChangedEventBuilder to model-api

Add `opennms-config-model` as a dependency of `model-api` (for the `Event` XML class). Move both builders. Update all imports across the codebase (`org.opennms.netmgt.model.events.EventBuilder` → same package in model-api).

### Step 3: Handle OnmsCriteria

First check usage:
```bash
grep -rl 'OnmsCriteria' --include='*.java' . | grep -v target | grep -v .claude
```
If only used by legacy code that daemon-boot excludes, it can stay in opennms-model until that module is deleted. If daemons need it, create a replacement.

### Step 4: Remove opennms-model dependency from daemon-common

After EventBuilder moves to model-api, daemon-common no longer needs opennms-model. Remove the dependency and verify compilation.

### Step 5: Remove opennms-model dependency from model-jakarta

If model-jakarta still has any reference to opennms-model, remove it.

### Step 6: Verify

1. `make build` succeeds
2. `opennms-container/delta-v/build.sh deltav` produces all 12 daemon images
3. `opennms-container/delta-v/deploy.sh up full` — daemons that previously crashed with `EventBuilder` ClassNotFoundException should now start
4. Run E2E tests

### Step 7: (Stretch) Delete opennms-model entirely

If all 35 consuming modules can be updated to use model-api instead, delete `opennms-model/` and remove it from the root POM's `<modules>`. This may require updating imports in feature modules and legacy modules. If too large for one PR, defer to a follow-up.

## Branch

Create: `chore/karaf-removal-phase3` off latest develop.

## PR

```bash
gh pr create --repo pbrane/delta-v --base develop \
  --title "chore: move EventBuilder et al to model-api — Karaf removal Phase 3"
```

## Pre-existing issues to be aware of

- `integration-tests/config` has a pre-existing test compilation failure (missing `opennms-provision-persistence` transitive dependency) — same on develop, ignore it.
- `opennms-model` has 139 Java files / 19,298 LOC — only 6 classes need to move, the rest are consumed by legacy modules that aren't in the daemon-boot classpath.
