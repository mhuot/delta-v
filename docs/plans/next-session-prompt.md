# Next Session: Consolidate opennms-model into opennms-model-jakarta

> Copy everything below the line into the next Claude Code conversation.

---

## Context

All 12 daemons run as standalone Spring Boot 4 apps with Hibernate 7 / Jakarta Persistence. All 6 E2E test suites pass (78/78). The codebase has a split-package problem between two model modules:

- **`opennms-model`** (~121 classes, `javax.persistence`) — the legacy module pulled transitively by many modules. Contains entity classes AND non-entity types (enums like `OnmsSeverity`, utility types like `TroubleTicketState`). Depends on Hibernate 3.6.
- **`opennms-model-jakarta`** (~30 classes, `jakarta.persistence`) — selective port of entity classes + JPA DAOs for Spring Boot daemons. Uses Hibernate 7.

Both provide classes in the same package `org.opennms.netmgt.model`. This is mitigated by a priority classpath directory (`/opt/libs/priority/`) in Docker containers, but is fragile.

**Goal:** Consolidate into a single model module so we can drop Hibernate 3.6 from the dependency tree entirely.

## What to Implement

### Primary Task: Brainstorm and design the consolidation approach

Two candidate approaches (from prior analysis):

**(A) Complete jakarta migration of all 121 classes** — Port every remaining class in `opennms-model` to jakarta annotations, merge into `opennms-model-jakarta`, drop `opennms-model` from daemon classpaths.

**(B) Split into `opennms-model-api` + `opennms-model-jakarta`** — Move non-entity classes (enums, utility types, ~105 classes with no JPA annotations) to a new `opennms-model-api` module. Make `opennms-model-jakarta` the sole entity provider. Drop `opennms-model` from daemon classpaths.

Option (B) was previously recommended as cleaner long-term — non-entity types don't need JPA annotations at all, so they shouldn't live in a JPA module.

### Key Questions to Resolve

1. Which classes in `opennms-model` are pure enums/types (no JPA) vs. entity classes (need jakarta migration)?
2. How many modules transitively depend on `opennms-model`? What's the blast radius?
3. Can we do this incrementally (move classes in batches) or does it need to be atomic?
4. What about the ~105 non-entity classes — do any have `@Entity`, `@Table`, or other JPA annotations that would need migration?
5. Does anything outside the daemon-boot modules still need `opennms-model` (e.g., Minion, tests)?

### Approach

Use the **brainstorming** skill to explore both approaches, analyze the class inventory, and design the migration path. Then **writing-plans** for implementation.

## Current E2E Baseline (2026-03-28)

| Test | Passed |
|------|--------|
| test-collectd-e2e.sh | 4/4 |
| test-minion-e2e.sh | 13/13 |
| test-syslog-e2e.sh | 15/15 |
| test-passive-e2e.sh | 16/16 |
| test-enlinkd-e2e.sh | 18/18 |
| test-e2e.sh | 12/12 |

## Key Constraints

- Must work on a feature branch with a PR against `pbrane/delta-v` (NEVER against `OpenNMS/opennms`)
- Rebuild all 12 daemon-boot JARs before `./build.sh deltav`
- Run `--pre-clean` on Enlinkd tests to avoid stale node interference
- All 6 E2E suites must pass after consolidation — no regressions
