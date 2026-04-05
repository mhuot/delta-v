# Design: PR1 — Phase 2 Reactor Pruning

> **Date:** 2026-04-05
> **Status:** Approved (brainstorming phase complete)
> **Parent spec:** [2026-04-05-horizon-extraction-spec.md](2026-04-05-horizon-extraction-spec.md) — PR1 detail in Section 6
> **Prompt:** [2026-04-05-next-session-pr1-reactor-pruning.md](2026-04-05-next-session-pr1-reactor-pruning.md)
> **Brainstorm transcript:** this document's Q&A originated from the 2026-04-05 brainstorming session

---

## Problem

Delta-V's reactor currently builds ~780 modules on every clean build (~10 minutes). Only ~219 of those modules are actually needed: ~20 Delta-V-authored modules plus ~200 horizon modules in their transitive dependency closure. The remaining ~561 horizon modules (legacy webapp, Karaf features, Vaadin UI, correlator, RPM/DEB assembly, legacy tests) are dead weight.

## Goal

Reduce clean-build time from ~10 min to ~4 min by removing non-closure horizon modules from the Delta-V Maven reactor. Horizon source files remain on disk; only `<module>` entries in aggregator POMs are removed.

## Scope

**In scope (PR1):**
- Remove ~561 `<module>` entries from aggregator POMs across the repo to shrink the reactor from ~780 → ~219 modules
- Edit aggregator POMs in place (preserve directory hierarchy for PR3's directory-level deletions)
- Delete fully-orphaned aggregator POMs (all sub-modules removed)
- Two commits on one feature branch → one PR against `pbrane/delta-v` `develop`

**Out of scope (PR1):**
- Moving or deleting horizon source directories (PR3)
- Changing `<dependency>` declarations in daemon-boot POMs (PR3)
- Publishing to GitHub Packages (PR2)
- Package rebranding to `org.deltav.*` (PR4)

**Success criteria:**
- `make build` succeeds with the pruned reactor
- All 94 E2E tests pass (93 existing E2E + PR0 Minion RPC canary)
- All 12 daemons boot healthy with no `ClassNotFoundException`/`NoClassDefFoundError` in logs
- Clean-build time: ~4 min (median of 3 warm-cache measurements), recorded in PR description

## Architecture

### 1. Entry-Point Seed List (19 modules)

The closure computation takes these 19 Delta-V-authored modules as seeds. All `org.opennms:*` dependencies they transitively pull in (per `mvn dependency:list`) form the reactor closure.

**Daemon-boots (14):**
1. `core/daemon-boot-alarmd`
2. `core/daemon-boot-bsmd`
3. `core/daemon-boot-collectd`
4. `core/daemon-boot-discovery`
5. `core/daemon-boot-enlinkd`
6. `core/daemon-boot-eventtranslator`
7. `core/daemon-boot-minion`
8. `core/daemon-boot-minion-common`
9. `core/daemon-boot-perspectivepollerd`
10. `core/daemon-boot-pollerd`
11. `core/daemon-boot-provisiond`
12. `core/daemon-boot-syslogd`
13. `core/daemon-boot-telemetryd`
14. `core/daemon-boot-trapd`

**Shared infrastructure (5):**
15. `core/db-init`
16. `core/daemon-common`
17. `core/daemon-registry`
18. `core/daemon-sink-kafka`
19. `core/opennms-model-jakarta` (Delta-V-authored modern data layer; explicit seed even though daemon-boots already depend on it)

**Implicit keepers (reactor-resident but not seed inputs):**
- `core/daemon/` (aggregator wrapping daemon-boot modules)
- `core/` and root `pom.xml` aggregators
- `opennms-container/delta-v/` and consumer container-assembly POMs

### 2. Closure Computation Methodology

**Artifact-ID → directory lookup table** (one-time pass, no per-artifact JVM startup):

```bash
find . -name pom.xml \
    -not -path '*/target/*' -not -path '*/.claude/*' -not -path '*/node_modules/*' \
  | while read pom; do
      aid=$(xmllint --xpath \
        'string(/*[local-name()="project"]/*[local-name()="artifactId"])' \
        "$pom" 2>/dev/null)
      [ -n "$aid" ] && echo "$aid $(dirname $pom)"
    done > target/all_modules_lookup.txt
```

The XPath `/*[local-name()="project"]/*[local-name()="artifactId"]` selects only the project's direct-child `<artifactId>`, ignoring `<parent><artifactId>`.

**Union of transitive deps across seeds:**

```bash
ENTRY_POINTS=(
  core/daemon-boot-{alarmd,bsmd,collectd,discovery,enlinkd,eventtranslator}
  core/daemon-boot-{minion,minion-common,perspectivepollerd,pollerd,provisiond}
  core/daemon-boot-{syslogd,telemetryd,trapd}
  core/db-init core/daemon-common core/daemon-registry
  core/daemon-sink-kafka core/opennms-model-jakarta
)

for ep in "${ENTRY_POINTS[@]}"; do
  aid=$(xmllint --xpath 'string(/*[local-name()="project"]/*[local-name()="artifactId"])' $ep/pom.xml)
  ./compile.pl -pl :$aid -Pdocker \
    dependency:list -DincludeGroupIds=org.opennms \
    -DoutputFile=target/deps-$(basename $ep).txt
done

cat target/deps-*.txt | grep -oE 'org\.opennms:[a-zA-Z0-9._-]+' \
  | sort -u > target/closure.txt
```

**Test-scope inclusion:** `dependency:list` includes test-scope deps by default — this is intentional (see Decision #4a). Widens closure by ~20-30 modules, eliminates "build succeeds, `mvn verify` fails" class of surprise.

**Profile activation:** Closure computation runs with `-Pdocker` (and any other production/CI-active profiles Delta-V uses) so profile-guarded deps are included.

**Plugin-scoped dependency gap scan:**

```bash
grep -r 'groupId>org.opennms' core/daemon-boot-*/pom.xml core/db-init/pom.xml \
  core/daemon-{common,registry,sink-kafka}/pom.xml core/opennms-model-jakarta/pom.xml \
  --include='pom.xml' | grep -B2 -A5 '<plugin>'
```

Manually append any `groupId>org.opennms</groupId>` + `artifactId>X</artifactId>` pair found inside a `<plugin>` block to `closure.txt` — but only if `X` is not already present (sort-unique afterward).

**Container-script scan** (catches scripted JAR path expectations):

```bash
grep -rn 'org\.opennms\|target/' opennms-container/delta-v/ \
  --include='*.sh' --include='*.yml' --include='Dockerfile*'
```

**Ancestor-chain preservation:**

```
Algorithm
---
1. leaf_dirs := { directory of each artifactId in closure.txt }
2. keep_dirs := leaf_dirs ∪ { ancestors(d) for d in leaf_dirs } ∪ reserved_allowlist
3. For each aggregator POM P with <modules>:
     For each <module>X</module>:
       path_of_X := join(dir(P), X)
       if path_of_X ∉ keep_dirs: remove this <module> entry
4. Recursively collapse empty aggregators: for any aggregator whose <modules>
   list is now empty, delete the aggregator's pom.xml file (leave the
   directory on disk for PR3) and remove its <module> entry from the
   grandparent's <modules>. Repeat until a fixpoint is reached
   (fully-emptied grandparents chain upward).
```

**Reserved capabilities allow-list** (`tools/development/reserved-capabilities.txt`, user-reviewed before Commit 2): candidate directories for the 6 reserved capabilities from parent-spec Section 7. Initial candidates require verification against actual tree:

| Parent-spec indicative path | Current-tree candidate |
|---|---|
| `features/telemetry/adapter-manager` | `features/telemetry/protocols/adapters/`, `features/telemetry/registry/` |
| `features/telemetry/listener-manager` | `features/telemetry/listeners/`, `features/telemetry/registry/` |
| `features/topology/graph-service` | `features/graph/` (subdir TBD) |
| `features/topology/graph-provider-manager` | `features/graph/`, `features/topologies/` |
| `features/distributed/extensions-api` | `features/distributed/` (subdir TBD) |
| OpenNMS Integration API infrastructure | `features/api-layer/` |

Path resolution happens during Commit 2 preparation. Allow-list entries not present in the tree are removed; any reserved module found to be needed later is added via PR against `pbrane/delta-v-horizon` in PR2+.

### 3. Execution Plan (2 Commits on 1 Branch)

Branch: `pr1/reactor-pruning` off latest `develop`.

#### Commit 1 — Surgical amputation

Remove only aggregators named as dead-weight in the parent spec. No closure computation required — these modules provably cannot contribute to a healthy Delta-V runtime.

**Candidate targets** (verify-then-remove):
- `opennms-webapp`, `opennms-web-api`, `opennms-web-dependencies`, `opennms-taglib` (legacy JSP webapp and supporting modules)
- `opennms-full-assembly`, `opennms-assemblies/*` (Karaf-based legacy assemblies)
- `features/vaadin`, `features/vaadin-*` (all Vaadin UI features)
- `features/topology-map` (legacy topology UI feature)
- `features/karaf-*`, `container/karaf-*`, `container/features/*` (Karaf container assembly)
- `features/correlator*`, `opennms-correlation` (Drools-based legacy correlator)
- `opennms-rpm-*`, `opennms-deb-*`, `opennms-full-*` (RPM/DEB assembly targets)
- `integration-tests/*` (UI/selenium tests not part of Delta-V E2E)
- Other aggregators confirmed dead via parent-spec Problem statement

**Method:** edit affected aggregator POMs (typically repo root `pom.xml` + 2-3 intermediate aggregators) to remove named `<module>` entries. For any aggregator whose `<modules>` list is emptied by these removals, delete its pom.xml file (directory stays on disk for PR3) and remove its entry from the grandparent's `<modules>`. Apply recursively (emptied grandparents chain upward).

**Pre-removal safety check:** before removing any aggregator, cross-reference its physical contents against `reserved-capabilities.txt`. If a reserved-capability submodule lives inside an amputation target (e.g., ALEC's `extensions-api` physically nested under `features/correlator*` or `opennms-correlation`), spare the parent in Commit 1 and handle its granular pruning in Commit 2's data-driven pass.

**Validation gates:**
1. `make build` succeeds (full clean compile)
2. Container image builds: `cd opennms-container/delta-v && ./build.sh deltav`
3. Stack boots: `./deploy.sh up lite`
4. All 12 daemons healthy (per project baseline)
5. No new errors in logs: `docker compose logs | grep -Ei "exception|error|notfound|noclassdef"`
6. Minion RPC canary: `./test-minion-rpc-e2e.sh --pre-clean --verbose` → 11/11
7. Full E2E suite: 93/93
8. Build-time measurement recorded (expect ~6-7 min after Commit 1)

#### Commit 2 — Data-driven closure trim

Run closure-computation script → ancestor-chain pruning → reactor-trim edits.

**Method:**
1. Ensure `tools/development/reserved-capabilities.txt` is reviewed/finalized by user
2. Run `tools/development/compute-reactor-closure.sh` → produces `target/closure.txt` + `target/keep-dirs.txt`
3. Apply ancestor-chain pruning algorithm (Section 2) to every aggregator POM still in the reactor
4. Delete empty aggregators; prune their entries from grandparents
5. Commit the pruned aggregator POMs + closure-computation script + allow-list file

**Validation gates:** all 8 gates from Commit 1, plus:
9. **Phantom-dependency check:** `mvn dependency:resolve -DincludeGroupIds=org.opennms | grep "36.0.0-SNAPSHOT"` — any artifact listed here must appear in `keep_dirs.txt`. Hits not in keep_dirs indicate Maven is pulling a binary from Nexus instead of building from source; those modules get added back to the closure (or documented as PR3-deferred).
10. **Jakarta-model-split check:** verify `closure.txt` contains `org.opennms:opennms-model-jakarta` but NOT the bare `org.opennms:opennms-model` (the legacy javax.persistence model). Cross-check `keep_dirs.txt` for `core/opennms-model/` — absence confirms the legacy model was not pulled into the reactor.
11. Build-time measurement recorded (expect ~4 min)

**Straggler protocol:** if Commit 2 validation fails due to missing transitive dep:
- Diagnose the missing module from the error output
- Manually restore the `<module>` entry in the appropriate aggregator via a new documentary commit: `fix(reactor): restore opennms-icmp-jna — required by pollerd for ICMP monitoring`
- Re-run validation
- Closure script output is a frozen baseline; do not regenerate — manual addbacks provide audit trail

## Testing Strategy

- **Per-commit:** full `make build`, Docker image build, stack boot, log-scrape for errors, Minion RPC canary, full E2E suite (94 tests total)
- **Build-time measurement:** 3 consecutive warm-cache builds per commit; record median in PR description
- **Cache-masking mitigation:** CI (and a local pre-merge check) runs `rm -rf ~/.m2/repository/org/opennms && make build` to ensure no phantom dependencies

## Risks and Mitigations

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Reserved-capability modules pruned in Commit 2 (spec paths indicative) | High | Reserved caps missing from PR2's delta-v-horizon snapshot | User-reviewed `reserved-capabilities.txt` allow-list unioned into `keep_dirs` |
| R2 | Runtime-only classpath deps (META-INF/services, component-scan) | Medium | Daemon fails to start, silent ClassNotFoundException | Log-scrape gate after every Commit; straggler protocol |
| R3 | Plugin-scoped `org.opennms:*` deps missed by `dependency:list` | Medium | "Could not resolve plugin dependency" build failure | Grep sweep for `<plugin>` + `org.opennms` across seed POMs |
| R4 | Ancestor-chain omission | Low | "Could not find module X" at reactor load | Ancestor-chain algorithm computes `keep_dirs` as leaves ∪ all ancestors; deterministic |
| R5 | Jakarta model split (legacy `opennms-model` kept alongside `opennms-model-jakarta`) | Low-Medium | Hibernate 7 duplicate-entity runtime conflict | Explicit gate: grep reactor for `opennms-model/` ≠ jakarta version; must be absent |
| R6 | Phantom dependency — Maven silently pulls `36.0.0-SNAPSHOT` JAR from Nexus instead of building from source | Medium-High | Reactor pruning looks successful but builds against stale binaries | Post-Commit-2 `mvn dependency:resolve` cross-check vs `keep_dirs.txt`; clean `~/.m2/repository/org/opennms/*` before final validation build |
| R7 | E2E flake vs real regression | Low-Medium | Time wasted on wrong cause | Rerun failures; `git stash` → verify on unpruned tree to establish flake baseline |
| R8 | Build-time measurement noise (cold `~/.m2` cache) | Low | Incorrect baseline/target comparison | 3 consecutive warm-cache measurements, record median |
| R9 | Container assembly scripts expect pruned JARs | Medium | `./build.sh deltav` fails with "no such file" | Commit 1's container-script scan catches these; Commit 1's `build.sh deltav` gate is the backstop |
| R10 | Profile-specific modules (platform-conditional JNA wrappers, DB drivers) | Medium | Prod-profile build breaks after merge | Closure script runs with `-Pdocker` and other production/CI profiles |
| R11 | Shared test resources (SNMP MIBs, XML configs in `src/test/resources`) | Low | "Resource Not Found" in tests of modules that remain | Test-scope inclusion (Decision #4a-ii) covers most; monitor test output for resource errors |

## Key Decisions

Summary of decisions made during brainstorming. Full rationale preserved in this document's Q&A appendix (if needed for future reference, reconstruct from the Section-by-Section structure).

| # | Decision | Rationale |
|---|---|---|
| D1 | 19 seed entry points (14 daemon-boots + 5 shared infra) | Captures Delta-V-authored module universe; shared infra modules given explicit-seed status to survive closure arithmetic |
| D2 | Edit aggregators in place (vs flatten) | Preserves directory hierarchy for PR3's clean `rm -rf` extraction; localized diffs for review/rollback |
| D3 | Two commits, one PR: surgical amputation + data-driven closure trim | Stage 1 proves monolith is dead (safe by naming); Stage 2 is risky closure arithmetic with bisectable checkpoint |
| D4a | Include test-scope deps in closure | Negligible build-time cost; eliminates "verify fails" class of surprise |
| D4b | Grep `<plugin>` + `org.opennms` for plugin-dep gaps | Cheap pre-commit check; `dependency:list` is blind to plugin classpaths |
| D4c | Log-scrape gate + E2E for reflective-loading safety | Spring Boot "healthy" boots can hide silent CNFE failures |
| D4d | Closure computed once, frozen; straggler addbacks are manual | Each manual addback is a documentary commit |
| D5 | Use `xmllint` for artifactId→dir lookup (not `mvn help:evaluate`) | 780 JVM starts is unacceptable; one XML sweep is instant |
| D6 | User reviews `reserved-capabilities.txt` allow-list before Commit 2 | Spec paths are indicative, not literal; human judgment required |

## Rollback

Revert the PR. Horizon source was never moved. Reactor returns to 780 modules. No dependency coordinates, no classpath layout, no Docker image format changes occurred in PR1, so rollback is surgical.

## Open Questions

None — all resolved during brainstorming. Implementation may surface additional questions (e.g., which exact features/telemetry subdirectory maps to the spec's "adapter-manager"); those are captured via the user-reviewed `reserved-capabilities.txt` file and the straggler protocol's documentary commits.
