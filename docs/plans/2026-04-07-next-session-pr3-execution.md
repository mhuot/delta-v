# Next Session: Execute PR3 — Switch to External Horizon JARs

> Copy everything below the line into the next Claude Code conversation.

---

## Context

PR2 is complete: `pbrane/delta-v-horizon` has been created and 675 modules published as `org.opennms:*:1.0.3` to GitHub Packages.

The PR3 implementation plan is written and approved at `docs/plans/2026-04-06-pr3-horizon-jars-plan.md`. Execute it.

## Prerequisites (verify before starting)

1. **Confirm 1.0.2 published successfully:**
   ```bash
   gh run list --repo pbrane/delta-v-horizon --limit 1
   # Expect: completed success for 1.0.2
   ```

2. **Confirm local GitHub Packages auth works:**
   ```bash
   # Check ~/.m2/settings.xml has server entry for github-deltav-horizon
   grep -A3 "github-deltav-horizon" ~/.m2/settings.xml
   ```
   If missing, add:
   ```xml
   <server>
     <id>github-deltav-horizon</id>
     <username>YOUR_GITHUB_USERNAME</username>
     <password>YOUR_PAT_WITH_PACKAGES_READ</password>
   </server>
   ```

3. **Verify delta-v develop is clean:**
   ```bash
   cd /Users/david/development/src/opennms/delta-v
   git checkout develop && git pull origin develop
   ```

## What PR3 Does

- Replaces ~660 in-reactor horizon modules with pre-built JARs from GitHub Packages
- New slim root POM (~150 lines) inheriting from `spring-boot-starter-parent:4.0.3`
- Imports horizon root POM (`org.opennms:opennms:1.0.2`) as a Maven BOM — one line gets all ~220 `org.opennms:*` managed deps
- Re-parents all 21 Delta-V modules to the new root POM
- Deletes all horizon source directories
- Delta-V version: `0.0.1-SNAPSHOT`
- Target: <2 min clean build, 21 modules

## Key Design Decisions (already approved)

1. **Bottom-up harvest, not top-down prune** — the new POM was designed from effective-POM analysis of actual daemon-boot modules
2. **BOM import strategy** — `org.opennms:opennms:1.0.2` imported as `<type>pom</type><scope>import</scope>` provides all horizon dependency management
3. **Maven precedence** — `spring-boot-starter-parent` (actual parent) > horizon BOM (import). Spring Boot 4.0.3 versions auto-override stale horizon values
4. **No Newts** — Newts/Cassandra eliminated per community decision. Resolves fastutil version conflict
5. **db-init** re-parented to delta-v-parent (was spring-boot-starter-parent, now inherits it transitively)

## Execution

Read the plan at `docs/plans/2026-04-06-pr3-horizon-jars-plan.md` and execute it task by task. The plan has 9 tasks:

1. Create feature branch `pr3/horizon-jars`
2. Write slim root POM (full XML provided in plan)
3. Re-parent 21 modules
4. Delete horizon source directories
5. Update CI workflow for GitHub Packages auth
6. Update Makefile
7. Verify local build (<2 min, 21 modules)
8. Docker build + E2E tests
9. Create PR against develop (`--repo pbrane/delta-v`)

## Important Reminders

- **Never create PRs against OpenNMS/opennms** — always `--repo pbrane/delta-v`
- **Always `git pull develop` before creating feature branches**
- **Feature branches for all work; merge via PR**
- **Grep for Maven artifactId coordinates, not directory names** (lesson from PR2)
- **Never bulk-sed version strings** — clobbers third-party libs
- **delta-v-horizon local repo** is at `/Users/david/development/src/opennms/delta-v-horizon`

## Reference Documents

- **PR3 implementation plan:** `docs/plans/2026-04-06-pr3-horizon-jars-plan.md`
- **Phase 3 spec:** `docs/plans/2026-04-05-horizon-extraction-spec.md`
- **PR2 session plan:** `docs/plans/2026-04-05-next-session-pr2-pr3-horizon-extraction.md`
- **PR1 lessons learned:** `docs/plans/2026-04-05-pr1-reactor-pruning-plan.md` (closure algorithm, test-scope gaps)
