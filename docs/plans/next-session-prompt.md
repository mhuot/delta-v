# Next Session: Execute opennms-model Consolidation Plan

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Design and implementation plan are complete for consolidating the split-package model modules. All 12 daemons run as standalone Spring Boot 4 apps with Hibernate 7 / Jakarta Persistence. All 6 E2E test suites pass (78/78).

**Spec:** `docs/superpowers/specs/2026-03-28-consolidate-opennms-model-design.md`
**Plan:** `docs/superpowers/plans/2026-03-28-consolidate-opennms-model.md`

## What to Implement

Execute the 21-task implementation plan. Three phases:

1. **Phase 1 (Tasks 2-6):** Create `core/opennms-model-api` with ~30 persistence-free classes extracted from `opennms-model`. Update OSGi bundle metadata for re-export.
2. **Phase 2 (Tasks 7-15):** Port all 17 remaining entity classes to `core/opennms-model-jakarta` (jakarta.persistence). Activate phantom fields. Add EventConf JAXB test.
3. **Phase 3 (Tasks 16-21):** Clean all 12 daemon-boot POMs to depend on model-api + model-jakarta only. Remove priority classpath hack. Full E2E validation.

## Execution Approach

Use subagent-driven development (recommended by the plan) or inline execution. The plan has checkboxes for progress tracking.

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
- Priority classpath hack removal in Phase 3 is the proof-of-cleanliness gate
