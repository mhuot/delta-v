# Next Session — Execute Kafka Time Series Phase 1 Implementation Plan

**Created:** 2026-04-16 (end of brainstorming + plan-writing session)
**Predecessors (both DONE):**
- `docs/superpowers/next-session-prompts/2026-04-16-kafka-time-series-phase-1-provisiond.md` — the scaffold for this phase
- `docs/superpowers/specs/2026-04-16-kafka-time-series-phase-1-node-context-design.md` — approved Phase 1 design
- `docs/superpowers/plans/2026-04-16-kafka-time-series-phase-1-node-context.md` — TDD implementation plan (17 tasks, ~3400 lines)

## State at session start

**Branch:** `feature/kafka-ts-phase-1-node-context` (exists locally, 2 commits ahead of `develop`, not yet pushed to origin):

```
0cc7f2bb6e8 docs: Phase 1 node-context producer implementation plan
d83d256a106 docs: Phase 1 node-context producer design spec
```

Both commits are the product of this session's brainstorming and plan-writing. The implementation plan has not been executed — no code or test files yet exist for `org.deltav.netmgt.provision.nodecontext`.

**Working tree:** there may be unstaged changes to `opennms-container/delta-v/provisiond-overlay/etc/imports/*.xml` (requisition `last-import=` drift — per `feedback_provisiond_requisition_drift` memory, these are runtime state and must NOT be committed). A stash entry from the prior session labelled `requisition last-import drift (runtime state, do not commit)` may exist — inspect `git stash list` before starting. Either leave untouched (commits only stage specific files) or restore if desired.

## What to do

**Execute the plan task-by-task via `superpowers:subagent-driven-development`.** That skill is the recommended path — a fresh subagent per task, review between tasks, no context drift. Inline execution via `superpowers:executing-plans` is viable as a fallback if subagent dispatch is unavailable, but prefer the subagent path.

### Boot sequence

1. `git checkout feature/kafka-ts-phase-1-node-context`
2. Read the spec: `docs/superpowers/specs/2026-04-16-kafka-time-series-phase-1-node-context-design.md`
3. Read the plan: `docs/superpowers/plans/2026-04-16-kafka-time-series-phase-1-node-context.md`
4. Invoke `superpowers:subagent-driven-development` and begin with Task 1

### Task summary (full detail in the plan file)

| # | Task | Key deliverable |
|---|---|---|
| 1 | Create `core/deltav-kafka-contracts` Maven module | new module with protobuf-maven-plugin |
| 2 | Move `.proto` files from Collectd to contracts module | single source of truth for wire schemas |
| 3 | Add provisiond deps | SCS kafka, protobuf-java, testcontainers, awaitility |
| 4 | Implement `NodeDao.findByForeignSource` | replace `UnsupportedOperationException` stub |
| 5 | `NodeToProtobufTranslator` (TDD) | pure function, ~14 tests |
| 6 | `NodeContextPublisher` (TDD) | orchestration + error isolation, 9 tests |
| 7 | `NodeContextDebouncer` (TDD) | per-nodeId cancel-and-reschedule, 7 tests |
| 8 | `NodeContextBootstrapRunner` (TDD) | `SmartLifecycle` enumeration, 4 tests |
| 9 | `NodeContextChangeFeedListener` (TDD) | 13 UEI handlers, 7 tests |
| 10 | `NodeContextProducerConfiguration` + Collectd handoff | wire all beans, move NewTopic bean |
| 11 | `scanBasePackages` + `application.yml` | SCS binding + `spring.kafka.bootstrap-servers` |
| 12 | Real-main-class IT (scan-package trap) | mandatory per `feedback_spring_boot_scan_package_trap` |
| 13 | Testcontainers Kafka IT | real broker compaction round-trip, 4 tests |
| 14 | Collectd retention 7 → 1 day | one-line YAML + one-line `@Value` |
| 15 | Operator README update | env vars, metric catalog, limitations |
| 16 | E2E script `test-node-context-e2e.sh` | Docker Compose smoke test |
| 17 | Full-reactor verify + PR | `./mvnw clean install`, rebuild 12 boot jars, E2E, `gh pr create --repo pbrane/delta-v` |

## Critical memories to honor

- `feedback_never_pr_opennms` — `gh pr create --repo pbrane/delta-v` always. Never the OpenNMS upstream.
- `feedback_feature_branches` — stay on `feature/kafka-ts-phase-1-node-context`; never commit to `develop`.
- `feedback_spring_boot_scan_package_trap` — Task 11 (scanBasePackages) + Task 12 (real-main-class IT) are both mandatory. Don't skip either.
- `feedback_delta_v_uses_mvnw_not_compile_pl` — all build commands use `./mvnw`, not `compile.pl`.
- `feedback_delta_v_full_reactor_verify` — Task 17's full-reactor verify is mandatory after the cross-module changes.
- `feedback_rebuild_all_daemons` — before running the E2E Docker Compose, rebuild all 12 daemon boot jars (Task 17 step 3).
- `feedback_deltav_package_namespace` — all new files use `org.deltav.*` + the AGPL v3 Delta-V copyright header (see Phase 0 Collectd files for the boilerplate).
- `feedback_provisiond_requisition_drift` — DO NOT commit the `provisiond-overlay/etc/imports/*.xml` `last-import=` drift. Stage specific files only.
- `feedback_jackson_defaultusewrapper` — not relevant here (no XML config) but a reminder that provisiond config XML uses Jackson XmlMapper, not JAXB.
- `project_provisioning_adapters_as_sidecars` — documents Phase 1's structurally-circular-looking in-process Kafka event pattern. Not blocking; revisit in a future phase.
- `project_kafka_timeseries_producer_next_session` — Phase 0 outcomes and lessons (the #170 cascade — `spring.kafka.bootstrap-servers` missing was the load-bearing bug). Task 11 addresses this explicitly.

## Deviations from the plan that are acceptable without re-brainstorming

- Subagent may adjust test-helper patterns if the OnmsNode/OnmsIpInterface mutator API doesn't match what Task 5's tests use — document the adaptation in the commit message.
- If `spring-cloud-stream-test-binder` exposes a different output-destination API than Phase 0's flow-enricher used, adapt; functional goal (round-trip assertion) is unchanged.
- If Testcontainers `KafkaContainer` import path differs from the `apache/kafka:3.6.1` image assumed in Task 13, use whatever image the existing Phase 0 Testcontainers ITs use (check `core/daemon-boot-collectd/src/test/java` for the precedent).
- Task 12's `@TestConfiguration` stub beans for `NodeDao` / `SessionUtils` / `EventSubscriptionService` may need expanding if the real-main-class context pulls in additional required beans — add stubs as needed; the test's purpose is scan-package coverage, not full wiring.

## Deviations that require re-brainstorming (stop and ask)

- Any change to the `NodeContext` protobuf schema (frozen per Phase 0, and Q3/Phase 1 explicitly rejected schema changes).
- Any change to the feature-flag default (`DELTAV_NODE_CONTEXT_ENABLED=true` on-everywhere was the Q6 decision).
- Any change to the debounce default (250 ms was user-approved; tune via env var is fine, but changing the default needs approval).
- Any move away from the UEI-listener approach (Q1 rejected both ProvisioningAdapter-based and Hibernate-entity-listener approaches).
- Removal of the blocking-bootstrap-on-restart behavior (Q2 accepted republish cost as the Phase 1 trade-off).
- Adding smart-skip / bytes-unchanged cache to bootstrap (bundled with sidecar redesign per `project_provisioning_adapters_as_sidecars`; out of Phase 1 scope).

## After the plan completes

Task 17 opens the PR. Two follow-up items go into queue (don't execute yet):

1. **Phase 2 decision.** Choose Phase 2a (Prometheus Write consumer — simplest, proves GlobalKTable join), Phase 2b (Pollerd/PerspectivePollerd producers), or Phase 2c (Thresholder). Brainstorm before picking.
2. **Memory updates.** Write a `project_kafka_ts_phase_1_done.md` memory with evidence from the E2E run (record count, bootstrap timing, amplification ratio) once the PR merges.

## How to start the next session

```
Execute the Kafka Time Series Phase 1 implementation plan at
docs/superpowers/plans/2026-04-16-kafka-time-series-phase-1-node-context.md.
Use superpowers:subagent-driven-development. Branch is
feature/kafka-ts-phase-1-node-context (spec + plan already committed).
```
