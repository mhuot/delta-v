# Brainstorm Transcript: Extract Delta-V from Horizon (Phase 3)

> **Date:** 2026-04-05
> **Spec:** [2026-04-05-horizon-extraction-spec.md](2026-04-05-horizon-extraction-spec.md)
> **Format:** Question-by-question summary with options presented, decision made, and reasoning captured for future reference.

This document is the reasoning archaeology artifact for the Phase 3 extraction spec. Each section records a brainstorming question, the options considered, the decision made, and the insights surfaced during discussion. Future contributors reading this can understand *why* the spec took the shape it did, not just *what* was decided.

---

## Q1: Upstream Sync Relationship

**Question:** How will Delta-V relate to upstream OpenNMS/Horizon going forward?

**Context:** The 2026-03-25 upstream sync examined 46 commits and cherry-picked 1. That datapoint suggests architectural divergence — 98% of upstream changes land on code paths Delta-V doesn't use (Karaf, webapp, correlator, Vaadin). The sync relationship shapes every downstream decision (versioning, repo organization, CI complexity).

**Options presented:**
- **A. Continuous selective sync** — monitor upstream monthly, cherry-pick fitting bugfixes (similar to current practice but formalized)
- **B. Release-anchored sync** — pin to OpenNMS release tags, sync quarterly
- **C. Opportunistic / as-needed** — no regular cadence; pull only when needed
- **D. Hard fork / snapshot** — take today's snapshot, never sync again

**Decision: D (Hard fork)**

**Clarification surfaced during discussion:** User described behavior that sounded between C and D — "learn of a bug, analyze in Delta-V, implement the fix ourselves but doesn't necessarily need to be a cherry-pick." This is structurally D with an upstream-as-reference nuance: OpenNMS GitHub is consulted for bug *intelligence*, but fixes are Delta-V-authored commits with no upstream ancestry.

**Physical distinction between C and D:**
- Under **C**, the horizon-fork repo has `upstream` as a git remote; cherry-picks preserve upstream commit ancestry.
- Under **D**, the horizon-fork has no `upstream` remote; all commits are Delta-V-authored; the repo is philosophically separate from OpenNMS.

**Consequences of D locked in:**
- No sync cadence, no sync tooling, no cherry-pick workflow
- Version numbers are ours to assign (no implicit alignment with OpenNMS releases)
- Horizon-fork repo git history is linear and Delta-V-owned
- Groupwise, the `org.opennms.*` package decision becomes orthogonal to C/D (same answer either way)

**Key insight:** The single configuration choice "does `upstream` exist as a git remote?" encodes the entire philosophy. Without upstream as a remote, the repo can't accidentally drift into being a live fork.

---

## Q2: Repo Organization

**Question:** How should the extracted code be organized across repositories?

**Options presented:**
- **A. Two repos** — `pbrane/delta-v-horizon` (publishes JARs) + `pbrane/delta-v` (consumes)
- **B. Single repo with subtree** — `horizon-src/` subdirectory inside `pbrane/delta-v`
- **C. Git submodule** — rejected upfront by user ("submodules are painful")

**Decision: A (Two repos)**

**Rationale:**
1. Under D (hard fork), horizon changes are rare. Two-repo setup optimizes for the common case (Delta-V changes only) where horizon JARs are stable cached artifacts.
2. Conceptual separation matches reality: Delta-V-Horizon is a distinct product (frozen snapshot + Delta-V-owned patches) with its own version cadence.
3. The `pbrane/delta-v` repo stays small (~20 modules) with a comprehensible POM tree.
4. The two CI pipelines become genuinely independent — not "independent but coordinated," which is B's hidden cost.
5. The "API contract" between the repos (published Maven coordinates + version) enforces discipline: horizon changes can't sneak into Delta-V without an explicit publish + version bump.

**Key insights:**
- Monorepo benefits (coordinated atomic changes) only pay off when you're frequently changing both sides together. Under D, horizon touches are the exception.
- Two-speed CI economics: delta-v builds in <2 min against cached JARs; horizon CI can take 15 min to produce those JARs, runs maybe once a month.

**Follow-up concern raised by user:** *"How will we know when delta-v-horizon can be pruned?"* Since Delta-V expects to extract functionality rapidly, the set of needed horizon modules will shrink. User wanted to know how two-repo approach handles ongoing pruning.

**Answer — automated pruning-report CI:**

A GitHub Action in `pbrane/delta-v` that runs on every PR and weekly:
1. Computes `org.opennms:*` transitive closure across Delta-V's daemon-boot modules
2. Fetches Delta-V-Horizon's module manifest
3. Classifies each module: **in-use** / **reserved** / **prunable**
4. Posts results as PR comment / issue update

When Delta-V drops a dependency → pruning report flags modules as newly prunable → human opens delta-v-horizon PR to delete → next delta-v-horizon release bumps major version → automation propagates back to Delta-V.

**Key insight:** The pruning feedback loop is a virtue of two repos, not a cost. In Option B (monorepo), unused horizon modules would silently sit in the reactor forever with no external boundary forcing their notice. Option A makes pruning visible and actionable by construction.

---

## Q3: Publishing Destination

**Question:** Where do the horizon JARs get published?

**Options presented:**
- **A. GitHub Packages (Maven)**
- **B. Self-hosted Nexus / Artifactory**
- **C. Static Maven repo via git branch** (commit JARs to `mvn-repo` branch, served via `raw.githubusercontent.com`)
- **D. Cloudflare R2 / S3 as Maven repo**

**Decision: A (GitHub Packages)**

**Rationale:**
1. Unified platform with existing GHCR usage for Delta-V Docker images (`delta-v-build-images.yml`)
2. CI auth is native: `${{ secrets.GITHUB_TOKEN }}` has both `packages:write` (publisher) and `packages:read` (consumer) out of the box
3. Free for public repositories with unlimited data transfer
4. Standard Maven `<repository>` + `<distributionManagement>` configuration

**Known gotcha surfaced:** GitHub Packages Maven requires authentication even for public packages (historical quirk). Mitigation: one-time `~/.m2/settings.xml` + PAT setup, documented in README. Well-understood Maven idiom.

**Fallback kept in pocket:** Option C (static git branch) is available as an emergency exit if GHP auth friction proves excessive. Switching requires only changing the `<repository><url>` value.

**Key insight:** Generalizing an existing pattern, not inventing a new one. Delta-V already consumes `org.opennms:jicmp-api:3.0.4`, `org.opennms.integration.api:api:2.0.0`, etc. as external Maven artifacts from `maven.opennms.org`. Phase 3 extends the same external-artifact pattern to the bulk of horizon.

---

## Q4: Versioning Scheme

**Question:** What version scheme applies to published delta-v-horizon JARs?

**Options presented:**
- **A. Delta-V semver** — `1.0.0`, `1.0.1`, `2.0.0` (clean, conventional)
- **B. Lineage-prefixed** — `36.0.0-deltav-1`, `36.0.0-deltav-2` (encodes OpenNMS 36 fork)
- **C. Date-stamped** — `2026.04.05`, `2026.05.12`

**Decision: A (Delta-V semver)**

**Rationale:**
1. Simple, standard, every Maven tool understands it
2. Avoids Maven `ComparableVersion` edge cases (e.g. whether `36.0.0-deltav-10 > 36.0.0-deltav-2` parses correctly)
3. Semver discipline doubles as change documentation
4. Lineage doesn't need to live in the version string — README and `<description>` carry it for humans

**Bump semantics:**
- Patch (`1.0.0 → 1.0.1`): horizon bugfix, no API change
- Minor (`1.0.0 → 1.1.0`): added/exposed new API, backward compatible
- Major (`1.0.0 → 2.0.0`): module pruned, API removed

**Downstream mechanics:** Delta-V's root POM declares `<deltav.horizon.version>1.0.0</deltav.horizon.version>` as a property. Every Delta-V `<dependency>` references `${deltav.horizon.version}`. Single line to bump, automatable via post-publish PR from Delta-V-Horizon's CI.

**Key insight:** Pruning-as-major-bump is a feature, not a bug. Delta-V expects rapid horizon shrinkage (~200 → maybe 80 modules). The major version will climb visibly (1.x → 5.x → 10.x), and that climb accurately tracks pruning milestones. Version numbers are free.

---

## Q5: GroupId / Rebrand Strategy

**Question:** Do the published artifacts keep `org.opennms.*` coordinates, or rebrand?

**Initial options presented:**
- **A. Keep `org.opennms.*` everywhere** — pragmatic, zero refactoring
- **B. Full rebrand** — `org.deltav.*` across both repos

**Initial recommendation was A**, but user raised a **trademark concern**: continuing to use package names from a trademarked organization can be troublesome.

**Expanded options after trademark concern:**
- **Strategy 1 (full rebrand)**: rename `org.opennms.*` → `org.deltav.*` in both repos. Weeks-long refactor touching thousands of files, XML configs, META-INF/services descriptors, Protobuf generated code, potentially breaking Kafka serialized data and DB discriminator columns.
- **Strategy 2 (groupId-only)**: change only Maven `<groupId>` to `org.deltav` while keeping `package org.opennms.*;` Java declarations. Mixed signal — signals awareness of trademark without providing meaningful distance. Not recommended.
- **Strategy 3 (NOTICE file only)**: keep `org.opennms.*` everywhere, add `NOTICE.md` with trademark attribution. Zero refactor. Precedent: many long-standing forks. Relies on explicit attribution.
- **Strategy 4 (partial rebrand)**: rename only Delta-V's ~20 modules to `org.deltav.*`, keep delta-v-horizon's ~200 modules as `org.opennms.*`. Spring Boot / Spring precedent.

**User clarification:** *"I was assuming we'd only rebrand the packages in the deltav repo and not the horizon repo."* This is Strategy 4.

**Decision: Strategy 4 (Partial rebrand) + NOTICE.md in both repos**

**What partial rebrand accomplishes:**
- Asserts Delta-V identity in Delta-V's own work product (bootstrappers, registries, config classes — everything Delta-V wrote)
- Honest about derivation: horizon code stays in its original namespace as a derivation marker
- Spring Boot pattern: `org.springframework.boot.*` over `org.springframework.*`

**What partial rebrand does NOT accomplish:**
- Horizon JARs still publish as `org.opennms:*:1.0.0`
- Delta-V's Docker images still bundle `org.opennms.*` bytecode (~95% of runtime classes)
- Strict "zero opennms bytecode" would require full rebrand (deferred to possible future phase)

**Scope of partial rebrand (days, not weeks):**
- IntelliJ "Rename Package" across ~20 modules
- Grep verification for Dockerfile ENTRYPOINTs, Spring XML, META-INF/services, CI main-class extraction
- No runtime serialization risks (Delta-V's main classes aren't over-the-wire)

**Key insights:**
- Spring Boot analogy: layered-ownership is visible in package names. `org.deltav.*` for bootstrappers + wiring; `org.opennms.*` preserved in horizon-fork as derivation marker.
- Package namespace as "authorship attribution" is a cleaner mental model than package namespace as "trademark protection." Keeping horizon as `org.opennms.*` is honest: that code came from upstream.
- Partial rebrand doesn't foreclose full rebrand later. It's an additive change that can be upgraded in a future phase if legal posture tightens.

---

## Q6: Migration Path

**Question:** How do we actually migrate from current state to the target state?

**Options presented:**
- **Path 1 (Sequential)**: Phase 2 (reactor pruning) delivered as a distinct PR before Phase 3 extraction. 5+ PRs, validation checkpoints at each stage.
- **Path 2 (Direct)**: Skip Phase 2 as a distinct step; extract straight to delta-v-horizon. 4 PRs, one of which is large.

**Decision: Path 1 (Sequential)**

**Rationale:**
1. Phase 2 (reactor pruning) is exactly the closure-validation step that Phase 3 extraction depends on. Path 1 makes that proof explicit and shippable.
2. Phase 2 independently delivers ~4-min builds in roughly a day's work, even if Phase 3 stalls on unforeseen issues (GHP Docker auth, serialization edge cases).
3. Clean rollback boundaries per PR.
4. The "redundant work" cost (Phase 2 keeps horizon in-tree briefly) is overstated: Phase 3's "delete horizon source" is a one-command operation.

**Two concerns raised by user that modified the migration plan:**

### Concern 1: Minion RPC E2E Coverage Gap

User observation: All current E2E tests rely on passive monitoring (syslogs, traps, flows). No E2E test exercises active detector/monitor execution via Minion RPC. The user recalled a PerspectivePollerd + PageSequenceMonitor E2E test that existed at one point (introduced alongside `${nodelabel}` hostname resolution), but was removed.

**Impact on plan:** Added **PR0 (NEW)** before Phase 2. PR0 adds one E2E test that provisions a node via Minion RPC exercising a real detector + real monitor. This becomes the canary for every subsequent PR. Without it, Phase 3 validation is flying blind on RPC-execution paths.

**Memory linkage:** `project_e2e_detectors_monitors_minion.md` documented this as a future followup. Phase 3 elevates it from future to prerequisite.

### Concern 2: Capability Recovery for OSGi Services

User observation: Some horizon capabilities are provided via OSGi services that were never formal ServiceDaemons and are not currently consumed by Delta-V daemon-boots. Examples:
- Telemetry AdapterManager + ListenerManager (Telemetryd migration)
- Topology GraphProviderManager + GraphService (future UI)
- Extensions API / AlarmLifecycleListener (ALEC integration)

These would be absent from the transitive closure → pruned from delta-v-horizon under aggressive pruning.

**Question:** If pruned, can they be recovered?

**Answer:** Yes, always. Git history is permanent; published Maven versions are immutable. Recovery path: `git checkout 1.0.0 -- path/to/module/`, restore to reactor, publish next version. Published `1.0.0` JARs don't disappear when `2.0.0` is published — Delta-V could even pin specific old-version modules while using newer versions for everything else.

**But friction matters.** Aggressive initial pruning means every capability addition becomes an archaeology expedition. Asymmetric economics: recovery costs ~1 hour, keeping an unused module costs ~30 seconds build time. Asymmetry favors conservative initial inclusion.

**Impact on plan:** Introduced **Reserved Capabilities allow-list** (spec section 7) for the initial `1.0.0` snapshot:
1. Telemetry AdapterManager + ListenerManager
2. Topology GraphProviderManager + GraphService
3. Extensions API / AlarmLifecycleListener
4. OpenNMS Integration API infrastructure (added by assistant; confirmed by user)

The pruning-report CI treats reserved modules as an allow-list, not as prunable orphans. Each is documented in delta-v-horizon's README with rationale.

**Key insights:**
- Reserved modules are "public domain API contracts for future work." Documenting them in README turns tribal knowledge into a repo artifact — a contributor wondering why `graph-provider-manager` is still there gets an immediate answer.
- Archaeology friction is bounded but real. The failure mode to guard against is: contributor needs a capability, doesn't know it was pruned, reinvents from scratch. The allow-list is defense against that.

**Final migration plan after concerns:**

| PR | Change | Build Time |
|---|---|---|
| PR0 | Restore Minion RPC E2E coverage | — |
| PR1 (Phase 2) | Prune delta-v reactor (780 → ~219) | ~4 min |
| PR2 | Bootstrap `pbrane/delta-v-horizon`, publish 1.0.0 | — |
| PR3 (Phase 3a) | Switch delta-v to external JARs; delete horizon source | <2 min |
| PR4 | Rebrand delta-v modules to `org.deltav.*` | <2 min |
| PR5 | NOTICE.md + pruning-report CI | — |

---

## Cross-Cutting Insights

**On fork economics:** 97% of the 780 `pom.xml` files in Delta-V are horizon plumbing Delta-V never touches. The JVM equivalent of shipping `node_modules` in every deploy. Phase 3 is structurally about stopping that.

**On trademark mitigation asymmetry:** The cost of the four trademark strategies (NOTICE only / partial rebrand / groupId-only / full rebrand) varies by ~100x. Partial rebrand is the inflection point — meaningful trademark distance at days-of-work cost.

**On what two repos buy you:** Not simplicity (monorepos are simpler per se) but **visible coupling**. The published Maven coordinate is an enforced API boundary. You can't accidentally modify horizon and ship it in delta-v — you have to publish and version-bump.

**On recoverability:** Git history + immutable published versions means no architecture decision is irreversible. The spec can be aggressive about pruning because the recovery path is always `git checkout <tag> -- path/`. This reduces the cost of wrong decisions.

**On sequencing:** Phase 2 before Phase 3 looks like "extra work" but is actually free validation. The closure computation Phase 2 performs is exactly what Phase 3 depends on. Doing it separately makes the proof shippable and rollback-able.

---

## What the Spec Captures (That This Transcript Doesn't)

The spec document adds implementation detail derived from these decisions but not separately brainstormed:

- **Bootstrap mechanism**: directory copy vs `git subtree split` for populating `pbrane/delta-v-horizon`
- **CI workflow YAML structure**: publish-on-tag-push pattern for delta-v-horizon
- **Docker build auth**: `--secret` mount vs env-based settings.xml in `delta-v-build-images.yml`
- **Local dev onboarding**: `~/.m2/settings.xml` + PAT setup documentation
- **Per-PR rollback procedures**: explicit revert paths for each of PR0–PR5
- **Risk table**: likelihood + mitigation for eight identified risks

These were treated as implementation details that follow from the architecture, not decisions requiring separate brainstorming.
