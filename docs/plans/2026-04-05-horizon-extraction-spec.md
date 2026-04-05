# Spec: Extract Delta-V from Horizon (Phase 3)

> **Date:** 2026-04-05
> **Status:** Draft
> **Precedes:** PR0 (RPC E2E coverage) through PR5 (NOTICE + pruning-report CI)
> **Companion:** [2026-04-05-horizon-extraction-brainstorm.md](2026-04-05-horizon-extraction-brainstorm.md) (full Q&A transcript + rationale)
> **Precedent:** [2026-04-04-spring-native-registry-spec.md](2026-04-04-spring-native-registry-spec.md) (PR #116 pattern)

---

## Problem

Delta-V's repository contains 780 `pom.xml` files. Only ~219 of them are actually built: ~20 Delta-V-specific modules (daemon-boot, daemon-registry, db-init, etc.) plus ~200 Horizon modules transitively referenced by Delta-V's daemon-boots. The other ~561 (webapp, Karaf features, RPM/DEB assembly, correlator, Vaadin UI, legacy tests) are dead weight compiled from source on every build.

Phase 1 (mvnd + skip flags, PR #117) brought single-module rebuilds to ~3 seconds, but a clean full build still takes ~10 minutes because all ~219 modules in the current reactor closure are compiled from source every time.

**Goal:** Reduce a clean Delta-V full build to under 2 minutes by consuming Horizon as pre-built external Maven artifacts, leaving only Delta-V-specific modules in the `pbrane/delta-v` repository.

## Solution

Extract the ~200 needed Horizon modules into a new `pbrane/delta-v-horizon` repository. Publish them as versioned Maven artifacts to GitHub Packages. Update `pbrane/delta-v`'s POMs to consume those published artifacts as normal Maven dependencies. Rebrand Delta-V's own modules to `org.deltav.*` to assert authorship while preserving the horizon-derived code in its original `org.opennms.*` namespace.

## Architecture

### 1. Upstream Relationship: Hard Fork

Delta-V-Horizon is a **frozen snapshot** of OpenNMS Horizon 36, owned entirely by Delta-V going forward. No `upstream` remote is configured on `pbrane/delta-v-horizon`. OpenNMS GitHub remains a read-only reference consulted for bug intelligence — when a Horizon bug is relevant to Delta-V, the fix is reimplemented as a new Delta-V commit, not cherry-picked.

**Consequences:**
- No sync cadence, no sync tooling, no cherry-pick workflow
- Delta-V-Horizon's git history is linear and Delta-V-owned from day one
- Version numbers are ours to assign; they carry no implicit alignment with OpenNMS releases
- Recovery of pruned functionality happens from Delta-V-Horizon's own git history, not from upstream

### 2. Repo Organization: Two Repos

| Repo | Contents | Role |
|---|---|---|
| `pbrane/delta-v-horizon` | ~200 horizon modules + reserved capabilities | Publishes JARs to GitHub Packages |
| `pbrane/delta-v` | ~20 Delta-V modules (daemon-boot, daemon-registry, db-init, container) | Consumes horizon JARs as Maven deps |

**Why two repos rather than a monorepo:**
Under a hard fork, horizon-source changes are rare (only when Delta-V fixes a bug). A two-repo setup optimizes for the common case — Delta-V changes only — where horizon JARs are stable artifacts pulled from a registry. The `pbrane/delta-v` repo stays small (~20 modules), with fast clones and a comprehensible POM tree. The two CI pipelines are genuinely independent (not "independent-but-coordinated"), which simplifies each pipeline.

The "API contract" between the two repos — a published Maven coordinate + version — enforces discipline. A horizon change can't sneak into Delta-V without an explicit publish + version bump.

### 3. Publishing Destination: GitHub Packages

Registry URL: `maven.pkg.github.com/pbrane/delta-v-horizon/*`

**Rationale:**
- Unified platform with GHCR (already used for Delta-V Docker images in `delta-v-build-images.yml`)
- CI auth is native: `${{ secrets.GITHUB_TOKEN }}` has `packages:write` and `packages:read` out of the box
- Free for public repositories, unlimited data transfer
- Standard Maven `<repository>` + `<distributionManagement>` configuration

**Known limitation (mitigated):** GitHub Packages Maven requires authentication even for public packages. Mitigation: document one-time `~/.m2/settings.xml` + PAT setup in Delta-V-Horizon's README.

**Fallback option (not chosen initially):** A static Maven repo served from an `mvn-repo` branch via `raw.githubusercontent.com` would require no auth but is unconventional. Available as an emergency exit if GHP auth friction proves excessive — switching requires only changing the `<repository><url>` value.

### 4. Versioning: Delta-V Semver

Scheme: `1.0.0`, `1.0.1`, `2.0.0`. No lineage prefix in the version string.

| Change type | Bump | Examples |
|---|---|---|
| Bugfix, no API change | Patch | `1.0.0` → `1.0.1` |
| New API, backward compatible | Minor | `1.0.0` → `1.1.0` |
| Module pruning, API removal | Major | `1.0.0` → `2.0.0` |

Lineage to OpenNMS 36 lives in `delta-v-horizon/README.md` and each `pom.xml`'s `<description>`. The version string is for Maven resolution and ordering; lineage is for humans.

**Downstream mechanics:** Delta-V's root `pom.xml` declares:

```xml
<properties>
  <deltav.horizon.version>1.0.0</deltav.horizon.version>
</properties>
```

Every Delta-V dependency on `org.opennms:*` references `${deltav.horizon.version}`. A single property edit propagates the bump. Delta-V-Horizon's publish CI opens an automated PR in Delta-V to bump this property after each release.

**Pruning as major bump is intentional:** over the coming weeks as Delta-V extracts functionality natively, Delta-V-Horizon is expected to shrink rapidly (~200 → maybe 80 modules). The major-version climbs will track that progress visibly.

### 5. GroupId Strategy: Partial Rebrand

| Repo | Java Packages | Maven groupIds | Rationale |
|---|---|---|---|
| `pbrane/delta-v` (~20 modules) | `org.deltav.*` (rebranded) | `org.deltav:*` | Delta-V-authored code; asserts Delta-V identity |
| `pbrane/delta-v-horizon` (~200 modules) | `org.opennms.*` (unchanged) | `org.opennms:*` | Honest derivation marker — this code came from OpenNMS |

Both repos ship `NOTICE.md` files with:
- Trademark attribution: *"'OpenNMS' is a trademark of The OpenNMS Group, Inc."*
- Non-affiliation: *"Delta-V is not affiliated with, endorsed by, or sponsored by The OpenNMS Group, Inc."*
- Derivation: *"Delta-V is a fork of OpenNMS Horizon, snapshotted 2026-04-05 from OpenNMS 36.0.0-SNAPSHOT."*

**Precedent:** This is the Spring Boot / Spring pattern. Spring Boot lives in `org.springframework.boot.*` while Spring stays `org.springframework.*`. Layered-ownership is visible in package names.

**Why not full rebrand:** Rebranding all ~200 horizon modules' Java packages is a weeks-long refactor touching thousands of files, XML configs, META-INF/services descriptors, Protobuf generated code, and potentially serialized data on the wire. Partial rebrand asserts Delta-V identity where it matters (Delta-V's own code) and can be upgraded to full rebrand in a future phase if legal posture tightens.

**Why not groupId-only rebrand:** Changing only the Maven groupId while keeping `package org.opennms.netmgt.*;` declarations creates a confusing split personality and provides weak trademark coverage. Either commit to package rebrand or don't — not halfway.

### 6. Migration Path: Sequential (Path 1)

Six PRs (PR0 through PR5), each individually testable against the full E2E suite. The sequential path optimizes for safety: each PR has a clear rollback boundary, and PR1's reactor pruning validates the module closure that PR3's extraction depends on.

### 7. Reserved Capabilities

These modules are included in the initial `1.0.0` snapshot even though no current Delta-V daemon-boot references them. They provide capabilities Delta-V expects to adopt in the near-to-medium term.

| Module (indicative path) | Reserved for | Rationale |
|---|---|---|
| `features/telemetry/adapter-manager` | Telemetryd migration | OSGi→Spring conversion of AdapterManager |
| `features/telemetry/listener-manager` | Telemetryd migration | OSGi→Spring conversion of ListenerManager |
| `features/topology/graph-service` | Future UI work | Topology Graph API surface |
| `features/topology/graph-provider-manager` | Future UI work | Topology Graph plugin registration |
| `features/distributed/extensions-api` (or equivalent) | ALEC integration | AlarmLifecycleListener plugin SPI |
| OpenNMS Integration API infrastructure | Plugin registration | Bundled-plugin support beyond the external `org.opennms.integration.api:api:2.0.0` artifact |

The exact module paths will be confirmed during PR2's bootstrap work; the table above captures intent. Each entry is documented in `delta-v-horizon/README.md` with the same rationale.

**Effect on pruning-report CI:** Reserved modules are treated as an allow-list, not as orphans. They do not appear in the "prunable" category even when no Delta-V module references them.

### 8. Pruning-Report CI

A GitHub Action in `pbrane/delta-v` runs on every PR and weekly on schedule. It performs reachability analysis from Delta-V's daemon-boot modules into the Delta-V-Horizon module set, producing a trinary classification:

- **In use** — transitively referenced by a Delta-V daemon-boot (via `mvn dependency:list -DincludeGroupIds=org.opennms`)
- **Reserved** — in the capabilities allow-list
- **Prunable** — neither; flagged for human review

Output: a PR comment (or issue update on schedule runs) summarizing the diff since the previous report, e.g. *"2 modules newly prunable: `features/legacy-foo`, `opennms-bar`"*.

**Workflow integration:** when the report surfaces prunable modules, a human opens a PR in `pbrane/delta-v-horizon` to delete the modules, which produces a new major version bump (e.g., `2.0.0`), which automation propagates back to `pbrane/delta-v` via a version-bump PR.

## Migration Plan

All PRs run the full E2E suite (93 tests) as baseline validation plus PR-specific additional checks.

### PR0: Restore Minion RPC E2E Coverage

**Problem:** Current E2E suites exercise passive monitoring (syslog, traps, flows), enlinkd topology, collection, and passive outages. No E2E test exercises *active* detector or monitor execution via Minion RPC. This gap existed before Phase 3 and is not introduced by it, but Phase 3 cannot be safely validated without closing it.

Historical note: a PerspectivePollerd + PageSequenceMonitor E2E test existed at one point (during the introduction of `${nodelabel}` hostname resolution). It was removed at some point and the coverage has not been restored.

**Change:** Add one E2E test that provisions a node via Minion RPC exercising:
- A real detector (e.g., `SnmpDetector` against labbox cEOS)
- A real monitor (e.g., `IcmpMonitor` or `SnmpMonitor`)

The test runs on the existing labbox Containerlab cEOS topology (see `reference_labbox_setup.md`).

**Validation:** test passes against the current `develop` baseline *before* any Phase 3 work begins. This establishes the canary — every subsequent PR re-runs this test to catch RPC path regressions.

**Rollback:** N/A (purely additive test).

### PR1 (Phase 2): Prune Delta-V Reactor

**Change:** In `pbrane/delta-v`'s root `pom.xml`, remove the ~561 `<module>` entries for horizon modules Delta-V does not transitively reference. Horizon source remains on the filesystem but is no longer built.

**Identifying the closure:** Run `mvn dependency:list -DincludeGroupIds=org.opennms` on each of the 16 daemon-boot modules (+ db-init + minion-boot). Union the results. Any `org.opennms:*` artifact in that union is in the closure and stays in the reactor. Everything else is prunable from the reactor.

**Validation:**
- `make build` succeeds with the pruned reactor
- Full E2E suite passes (93/93)
- PR0's new Minion RPC test passes
- Build time measured and recorded (~4 min expected)

**Rollback:** Revert the PR. Horizon source was never moved.

### PR2: Bootstrap `pbrane/delta-v-horizon`

**Change:** Create `pbrane/delta-v-horizon`. Seed with:
- The ~200 horizon module directories copied from `pbrane/delta-v` (post-PR1, preserving any Delta-V-side fixes already applied)
- Reserved capability modules from section 7
- Root `pom.xml` with `<distributionManagement>` pointing to GitHub Packages
- `.github/workflows/publish.yml` triggered on tag push (`delta-v-horizon-*`)
- `README.md` with lineage declaration
- `NOTICE.md` with trademark attribution
- First tag: `1.0.0`

**Publish CI workflow:** pattern-matched to `delta-v-build-images.yml`. On tag push:
1. Checkout with full history
2. Set up JDK 17
3. Authenticate to GitHub Packages via `GITHUB_TOKEN`
4. `mvn deploy -DskipTests` for all modules
5. Open automated PR in `pbrane/delta-v` bumping `deltav.horizon.version`

**Validation:**
- `mvn install -DskipTests` succeeds in the new repo (local build sanity)
- Publish workflow runs successfully on `1.0.0` tag
- `mvn dependency:get -Dartifact=org.opennms:opennms-dao:1.0.0` resolves from a clean `~/.m2/repository`
- Published artifact list matches expectation (~200 + reserved)

**Rollback:** Delete the repo (or leave unpublished). Delta-V is unaffected.

### PR3 (Phase 3a): Switch Delta-V to External Horizon JARs

**Change:** In `pbrane/delta-v`:
- Add `<repositories>` entry for GitHub Packages Maven
- Add `<properties><deltav.horizon.version>1.0.0</deltav.horizon.version></properties>` to root POM
- Update all `org.opennms:*` dependencies in daemon-boot POMs to use `${deltav.horizon.version}` (currently `36.0.0-SNAPSHOT`)
- Delete the ~200 horizon module directories from the repo (now dead code, not in reactor since PR1)
- Update `.github/workflows/delta-v-build-images.yml` to authenticate to GitHub Packages during dependency resolution
- Update local dev documentation (`README.md`): `~/.m2/settings.xml` PAT setup instructions

**Validation:**
- `mvn -o clean install -DskipTests` fails from a clean `~/.m2/repository` (proves we're not accidentally using cached local artifacts)
- `mvn clean install -DskipTests` succeeds when online (proves JAR resolution from GHP)
- Full E2E suite passes
- PR0's Minion RPC test passes
- Docker images build successfully (including GHP auth during build)
- Docker images run successfully and daemons start
- Build time measured and recorded (<2 min expected)

**Rollback:** Revert the PR. Delta-V-Horizon JARs remain published but unused.

### PR4: Rebrand Delta-V Modules to `org.deltav.*`

**Change:** IntelliJ "Rename Package" across all ~20 Delta-V modules:
- `org.opennms.core.daemon.boot.*` → `org.deltav.core.daemon.boot.*`
- `org.opennms.core.daemon.registry` → `org.deltav.core.daemon.registry`
- `org.opennms.core.daemon.common` → `org.deltav.core.daemon.common`
- All other Delta-V-authored packages under `core/`

**Also updated:**
- `<groupId>org.opennms</groupId>` → `<groupId>org.deltav</groupId>` in Delta-V's own POMs
- `@SpringBootApplication` main class references in:
  - Dockerfile ENTRYPOINTs (`opennms-container/delta-v/Dockerfile.daemon-per` etc.)
  - `delta-v-build-images.yml` workflow (MAIN_CLASS extraction)
  - `compute-shared-libs.sh` (if it references classnames)
- Spring XML `class="..."` references to Delta-V classes
- `META-INF/services/` filenames where Delta-V registers its own SPI
- Logback `<logger name="org.opennms.core.daemon.*"/>` patterns

**Unchanged (intentionally):**
- `import org.opennms.*` statements referencing horizon classes (unavoidable — Delta-V daemons extend horizon base classes)
- `extends` / `implements` clauses pointing at horizon types

**Validation:**
- All modules compile after the rename
- Full E2E suite passes
- PR0's Minion RPC test passes
- Docker images build and daemons start (validates ENTRYPOINT + main class updates)
- `grep -r "org.opennms.core.daemon" core/` returns only unavoidable import statements (no residual package declarations)

**Rollback:** Revert the PR. Package rename is idempotent.

### PR5: NOTICE Files + Pruning-Report CI

**Change:**
- Add `NOTICE.md` to both `pbrane/delta-v` and `pbrane/delta-v-horizon` with trademark attribution and non-affiliation statement
- Update root `README.md` in both repos to reference `NOTICE.md`
- Add `.github/workflows/pruning-report.yml` to `pbrane/delta-v`:
  - Triggers: pull_request, schedule (weekly)
  - Computes closure of `org.opennms:*` transitive deps across daemon-boot modules
  - Fetches Delta-V-Horizon's module manifest
  - Classifies each Delta-V-Horizon module: in-use / reserved / prunable
  - Posts PR comment (or creates/updates tracking issue on schedule)

**Validation:**
- Workflow runs successfully on a test PR
- Output format is readable (markdown comment with clear classification table)
- Reserved modules from section 7 appear as reserved, not prunable

**Rollback:** Revert. Workflow removal is clean.

## Testing Strategy

### Per-PR

Every PR runs:
- Unit tests (JUnit) for changed modules
- Integration tests (IT) for changed modules
- Full E2E suite: test-e2e, test-minion-e2e, test-syslog-e2e, test-passive-e2e, test-collectd-e2e, test-enlinkd-e2e (+ new Minion RPC test from PR0)
- Docker image build and smoke-test startup

### Build-Time Regression Tracking

Each PR records the measured clean-build time in its description:
- Baseline (pre-PR0): ~10 min
- PR1 target: ~4 min
- PR3 target: <2 min
- PR4/PR5: <2 min maintained

### Cross-Repo Validation (PR3 only)

Before merging PR3, validate the published-JAR path works end-to-end:
1. Clear `~/.m2/repository/org/opennms/`
2. `mvn clean install -DskipTests` (forces fetch from GHP)
3. All JARs resolve successfully
4. Full E2E suite passes
5. Docker images build successfully *from CI* (validates GHP auth in CI context)

## Risks and Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| GitHub Packages auth friction in Docker builds | Medium | Mirror existing GHCR auth pattern in `delta-v-build-images.yml`; fallback to static `mvn-repo` branch if unworkable |
| Module closure missing a transitive dep → build fails at PR1 | Medium | `dependency:list` closure computation is deterministic; PR1 catches this before PR3 |
| PR3 Docker build fails on private package auth | Medium | `docker buildx --secret` pattern for `GITHUB_TOKEN` injection; documented in workflow |
| Rebrand (PR4) misses a Spring XML / META-INF reference | Low-Medium | Grep verification step + E2E tests catch runtime failures; rollback is a single revert |
| `ComparableVersion` surprises with semver | Low | Plain semver (1.0.0) dodges Maven version-parsing edge cases |
| Reserved capability missed in `1.0.0` | Medium | Allow-list lives in README; adding a module later is a routine PR bumping to next minor version |
| Published `1.0.0` has a bug, needs immediate replacement | Low | Publish `1.0.1`; previous version immutably remains on GHP for reproducibility |

## Rollback Procedures

Path 1 (sequential) provides clean rollback at each PR boundary:

- **PR0**: purely additive test — no rollback needed
- **PR1**: revert restores all 561 modules to the reactor; horizon source was never moved
- **PR2**: delete or ignore `pbrane/delta-v-horizon`; Delta-V is unaffected
- **PR3**: revert restores local horizon source dependencies; `deltav.horizon.version` property reverts to `36.0.0-SNAPSHOT`
- **PR4**: revert restores `org.opennms.core.daemon.*` package names; rebrand is idempotent
- **PR5**: revert removes NOTICE and CI workflow; no runtime impact

## Open Questions (for Implementation Plan)

These will be resolved during the implementation plan phase, not the spec phase:

1. **Exact module paths for reserved capabilities** — indicative paths in section 7 need verification against current horizon source layout
2. **Docker build auth pattern** — whether `--secret` mount or env-based `settings.xml` is cleaner for `delta-v-build-images.yml`
3. **Automated version-bump PR mechanics** — whether to use `peter-evans/create-pull-request` GitHub Action or a custom script
4. **PR0 test location** — which existing E2E suite directory should host the new Minion RPC test (likely `test-minion-e2e` or `test-e2e`)
5. **Initial bootstrap mechanism** — `git subtree split` vs. directory copy for populating `pbrane/delta-v-horizon` (directory copy is simpler; subtree split preserves Delta-V-side commit attribution)

## Decision Journal

Summary of the brainstorming decisions that produced this spec. Full transcript with reasoning is in the companion brainstorm document.

| # | Decision | Rejected Alternatives | Why |
|---|---|---|---|
| 1 | Hard fork (no upstream remote) | Continuous sync (A), Release-anchored (B), Opportunistic cherry-pick (C) | 2026-03-25 sync showed 1/46 commits were picked; Delta-V has architecturally diverged from upstream |
| 2 | Two repos | Single repo with subtree; Git submodule | Horizon changes are rare under hard fork; two-repo separation reflects reality and keeps delta-v surgical |
| 3 | GitHub Packages (Maven) | Self-hosted Nexus; Static git branch; Cloudflare R2 | Unified platform with GHCR; native CI auth via `GITHUB_TOKEN`; free for public repos |
| 4 | Delta-V semver (`1.0.0`) | Lineage-prefixed (`36.0.0-deltav-N`); Date-stamped | Simple, conventional, dodges Maven `ComparableVersion` edge cases; lineage lives in README |
| 5 | Partial rebrand (delta-v only) | Full rebrand (both repos); groupId-only rebrand; keep `org.opennms.*` everywhere | Full rebrand is a weeks-long refactor; groupId-only is mixed-signal; partial rebrand asserts identity where it matters and defers larger scope to future phase |
| 6 | Sequential migration (Path 1) | Direct (Path 2) — single large PR | Phase 2 independently delivers 4-min builds; PR1's closure validation is prerequisite to PR3's extraction; clean per-PR rollback boundaries |
| 6a | PR0 added (Minion RPC E2E) | Deferred to future task | Phase 3 extraction cannot be safely validated without Minion RPC execution coverage; gap existed before Phase 3 but becomes blocking |
| 7 | Reserved capabilities allow-list | Prune aggressively, recover from git | Aggressive pruning creates recovery friction; asymmetric economics favor conservative initial inclusion (~1 hour recovery cost vs. ~30 seconds build cost) |
| 8 | Automated pruning-report CI | Manual inspection per refactor | Operational sustainability of two-repo approach depends on visible pruning signals; CI comment on every PR makes it automatic |
