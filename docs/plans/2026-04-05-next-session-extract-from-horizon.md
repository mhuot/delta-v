# Next Session: Brainstorm Phase 3 — Extract Delta-V from Horizon

> Copy everything below the line into the next Claude Code conversation.

---

## Goal

Brainstorm and write a spec for extracting Delta-V completely from the OpenNMS/Horizon codebase, publishing our own Horizon-derived artifacts to GitHub Packages (or similar), so that the `pbrane/delta-v` repo only contains Delta-V-specific code and depends on Horizon JARs as external Maven artifacts.

## Why This Matters

Phase 1 (mvnd + skip flags) made single-module rebuilds take ~3 seconds, but a full build still takes ~10 minutes because we're compiling 219 Horizon modules from source every time. The repo has **780 pom.xml files** — 561 of them are Horizon modules we don't actually need (webapp, Karaf features, RPM/DEB assembly, tests, correlator, vaadin UI, etc.).

Phase 2 (reactor pruning) would drop this to ~220 modules and probably ~4 min build. Phase 3 goes further: extract all Horizon code entirely, depending on it as pre-built JARs, leaving Delta-V with only ~20-30 Delta-V-specific modules. Build time drops to **under 2 minutes**.

## Current State (verified 2026-04-05)

**What Delta-V depends on from Horizon:** ~219 `org.opennms.*:36.0.0-SNAPSHOT` artifacts (all built locally).

**What Delta-V already gets from external repos (maven.opennms.org):**
- `org.opennms:jicmp-api:3.0.4` — JNI ICMP wrapper (separate project, native libs)
- `org.opennms:jicmp6-api:3.0.4` — JNI ICMPv6 wrapper
- `org.opennms.integration.api:api:2.0.0` — Plugin API
- `org.opennms.integration.api:common:2.0.0` — Plugin API common
- `org.opennms.newts:newts-*:3.0.0` — Newts timeseries library

**Delta-V-specific modules we'd keep in the repo:**
- `core/daemon-registry` (new in PR #116)
- `core/daemon-common`
- `core/daemon-boot-minion-common`
- `core/daemon-boot-*` (14 boot JARs)
- `core/db-init`
- `opennms-container/delta-v/` (Docker + compose + E2E tests)
- `docs/plans/` (our planning docs)

That's ~20 Delta-V-specific modules.

## Brainstorming Questions

Before writing the spec, we need to answer:

### Publishing strategy
1. **Where do we publish the 219 Horizon JARs?**
   - GitHub Packages (ghcr.io / npm.pkg.github.com for Maven)
   - GitHub Actions producing a Maven repo site
   - Self-hosted Nexus / Artifactory
   - Contribute back to `maven.opennms.org` (unlikely, we're a fork)
2. **How do we version them?**
   - Keep `36.0.0-SNAPSHOT` and publish snapshots?
   - Use semver like `deltav-36.0.0-20260405`?
   - Tag-driven releases (e.g., `delta-v-horizon-1.0.0`)?
3. **How do we sync with upstream Horizon?**
   - When Horizon/OpenNMS releases a new version, we cherry-pick/sync into a separate **horizon-fork** repo, build, and re-publish?
   - Or we pin to specific Horizon release tags and sync periodically?

### Repo organization options

**Option A: Two repos**
- `pbrane/delta-v-horizon` — fork of OpenNMS/opennms, pruned to 219 needed modules, publishes JARs via CI
- `pbrane/delta-v` — Delta-V-specific code only, depends on delta-v-horizon JARs

**Option B: Single repo with separate subtree**
- `pbrane/delta-v` with two top-level directories: `horizon/` (synced from upstream) and `delta-v/` (our code)
- Build pipeline publishes horizon JARs on push to master, then builds delta-v using them

**Option C: Submodule / subtree**
- `pbrane/delta-v` with `pbrane/horizon-fork` as a git submodule
- Less favored — submodules are painful

### Sync strategy for upstream Horizon updates

When OpenNMS releases a new version (or we want a bugfix from upstream):
1. How do we detect what changed?
2. How do we apply it to our Horizon fork (if separate repo)?
3. How do we test that Delta-V still works against the new JARs?
4. Do we need a staging area / compatibility matrix?

### CI/CD implications
- New workflow to publish Horizon JARs (triggered by what?)
- Existing `delta-v-build-images.yml` workflow (PR #113) would consume these JARs
- How do we handle auth for private GitHub Packages from Docker builds?

### Migration approach
- Big-bang vs incremental?
- Can we test Phase 3 alongside Phase 2 (pruned reactor) before committing?
- What's the rollback plan if it doesn't work?

## Reference Material

- **Spec for PR #116 (precedent for this kind of refactor):** `docs/plans/2026-04-04-spring-native-registry-spec.md`
- **Plan for PR #116:** `docs/plans/2026-04-04-spring-native-registry-plan.md`
- **Current dependency analysis from 2026-04-05 session:** 219 local org.opennms SNAPSHOT modules in daemon-boot transitive closure, 5 external OpenNMS artifact families.

## Suggested Approach for the Session

1. Use `superpowers:brainstorming` skill to work through the questions above
2. Decide on publishing strategy + repo organization
3. Write a spec to `docs/plans/2026-04-05-horizon-extraction-spec.md`
4. Based on user feedback, consider whether to also write an implementation plan, or whether Phase 2 (reactor pruning) should happen first as a stepping stone

## Important Reminders

- **Never create PRs against OpenNMS/opennms** — always `--repo pbrane/delta-v`
- **Always pull develop before branching**
- **Feature branches for all work; merge via PR**
- **Memory index (`MEMORY.md`) must be updated if we add long-lived architectural decisions**
