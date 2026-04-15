# Flow-enricher `events.api` Exclusion Cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the 16-entry `<exclusion>` list on `core/flow-enricher/pom.xml`'s `events.api` dependency by slimming the dependency's transitive closure upstream in `delta-v-horizon`, releasing horizon 1.0.9, and consuming it from delta-v.

**Architecture:** Two-repo change sequenced through a horizon release. (1) In `delta-v-horizon`: mark `spring-dependencies` and `swagger-annotations` optional, delete `camel-dependencies` and `org.opennms.core.model-api` from `features/events/api/pom.xml`. (2) Release horizon 1.0.9. (3) In `delta-v`: bump `deltav.horizon.version` to 1.0.9 and delete the events.api exclusion block.

**Tech Stack:** Maven, `./mvnw` wrapper (both repos), GitHub Packages (horizon artifact registry), `gh` CLI for PRs.

**Design doc:** `docs/superpowers/specs/2026-04-14-flow-enricher-events-api-local-stub-design.md`

**Critical memory references:**
- `feedback_never_pr_opennms` — delta-v PRs always use `gh pr create --repo pbrane/delta-v`. Never `OpenNMS/opennms`.
- `feedback_feature_branches` — never commit directly to `develop` in either repo.
- `feedback_pull_before_branching` — always `git pull` before creating feature branches.
- `feedback_clean_m2_between_branches` — clean stale horizon SNAPSHOTs before switching branches.
- `feedback_no_blind_sed_versions` — use `mvn versions:set`, never bulk-sed.
- `feedback_rebuild_all_daemons` — rebuild all 12 daemon boot JARs before docker E2E.

---

## File Structure

### Horizon changes (`delta-v-horizon` repo at `/Users/david/development/src/opennms/delta-v-horizon`)

- **Modify:** `features/events/api/pom.xml` — the diet itself (mark 2 deps optional, delete 2 deps)
- **Modify (via tool):** every `pom.xml` in the horizon reactor — `./mvnw versions:set` will bump the `<version>` element from `1.0.8` to `1.0.9` in ~700 pom files as a single mechanical change.
- **Conditional:** any downstream horizon module poms that break from the diet (cascade fix) — unknown until Task H4 full-reactor build surfaces them.

### Delta-v changes (`delta-v` repo at `/Users/david/development/src/opennms/delta-v`)

- **Modify:** `pom.xml` (reactor root) — bump `<deltav.horizon.version>1.0.8</deltav.horizon.version>` to `1.0.9`.
- **Modify:** `core/flow-enricher/pom.xml` — delete the 16 `<exclusion>` entries from the `events.api` dep block (currently lines 237-262, including the multi-line explanatory comment).

### Memory updates (`~/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/`)

- **Create:** `project_flow_enricher_classpath_batch_cleanup.md` — followup tracking the remaining 10 exclusion blocks in flow-enricher/pom.xml plus the 4 other delta-v modules that reference `events.api`.
- **Modify:** `project_flow_enricher_events_api_local_stub.md` — mark status DONE and link to the horizon release.
- **Modify:** `MEMORY.md` — update the index entry for the existing memory to reflect DONE status and add the new batch-cleanup entry.

---

## Phase H — Horizon events.api pom diet

### Task H1: Set up horizon feature branch

**Files:** none yet; branch creation only

**Working directory for every Task H*:** `/Users/david/development/src/opennms/delta-v-horizon`

- [ ] **Step 1: Verify clean working tree**

```bash
cd /Users/david/development/src/opennms/delta-v-horizon
git status --short
```

Expected: empty output. If there are local modifications, stop and resolve them before proceeding — do not stash without user confirmation.

- [ ] **Step 2: Switch to develop and pull latest**

```bash
git switch develop
git pull --ff-only origin develop
```

Expected: "Already up to date." or a fast-forward with new commits.

- [ ] **Step 3: Create the feature branch**

```bash
git switch -c chore/events-api-pom-diet
```

Expected: "Switched to a new branch 'chore/events-api-pom-diet'".

### Task H2: Edit `features/events/api/pom.xml`

**Files:**
- Modify: `features/events/api/pom.xml`

- [ ] **Step 1: Read the current dependency block**

```bash
grep -n -A2 "model-api\|camel-dependencies\|spring-dependencies\|swagger-annotations" features/events/api/pom.xml
```

Expected: four `<dependency>` blocks in the file. Confirm their presence before editing. The relevant section is in the main `<dependencies>` list (not the `<dependencyManagement>` or test-scope blocks if any).

- [ ] **Step 2: Apply the diet using Edit tool**

Replace:

```xml
    <dependency>
      <groupId>org.opennms.core</groupId>
      <artifactId>org.opennms.core.model-api</artifactId>
    </dependency>
```

with **nothing** (delete the three lines entirely — they are surrounded by other `<dependency>` entries and removing them does not leave a syntax hole).

Replace:

```xml
    <dependency>
      <groupId>org.opennms.dependencies</groupId>
      <artifactId>camel-dependencies</artifactId>
      <type>pom</type>
    </dependency>
```

with **nothing** (delete the five lines entirely).

Replace:

```xml
    <dependency>
      <groupId>org.opennms.dependencies</groupId>
      <artifactId>spring-dependencies</artifactId>
      <type>pom</type>
    </dependency>
```

with:

```xml
    <dependency>
      <groupId>org.opennms.dependencies</groupId>
      <artifactId>spring-dependencies</artifactId>
      <type>pom</type>
      <optional>true</optional>
    </dependency>
```

Replace:

```xml
    <dependency>
      <groupId>io.swagger.core.v3</groupId>
      <artifactId>swagger-annotations</artifactId>
      <version>${swaggerVersion}</version>
    </dependency>
```

with:

```xml
    <dependency>
      <groupId>io.swagger.core.v3</groupId>
      <artifactId>swagger-annotations</artifactId>
      <version>${swaggerVersion}</version>
      <optional>true</optional>
    </dependency>
```

- [ ] **Step 3: Verify the edits**

```bash
grep -n "model-api\|camel-dependencies\|spring-dependencies\|swagger-annotations\|optional" features/events/api/pom.xml
```

Expected output shows:
- No occurrences of `org.opennms.core.model-api`
- No occurrences of `camel-dependencies`
- One occurrence of `spring-dependencies` with `<optional>true</optional>` immediately after
- One occurrence of `swagger-annotations` with `<optional>true</optional>` immediately after

- [ ] **Step 4: Validate pom XML syntax**

```bash
./mvnw -pl features/events/api help:effective-pom -DskipTests 2>&1 | tail -20
```

Expected: no parse errors. The "effective pom" output at the end is OK; what matters is no "[ERROR]" lines mentioning XML parsing or dependency resolution failures.

### Task H3: Build `events.api` in isolation

**Files:** none modified; build-only verification

- [ ] **Step 1: Clean compile just events.api**

```bash
./mvnw -pl features/events/api clean install -DskipTests
```

Expected: `BUILD SUCCESS`. If this fails with "package org.springframework... does not exist" it means the `<optional>true</optional>` scoping is wrong — spring-dependencies must still be visible to the owner module's compile. Re-read Task H2 Step 2 and confirm you added `<optional>true</optional>` rather than `<scope>provided</scope>` or outright deleting the dep.

- [ ] **Step 2: Run events.api unit tests**

```bash
./mvnw -pl features/events/api test
```

Expected: `BUILD SUCCESS` and all tests green. Confirms `AnnotationBasedEventListenerAdapter` and `EventIpcManagerFactory` (the two Spring-using classes) still wire correctly within the owner module.

### Task H4: Full-reactor compile (cascade check)

**Files:** none modified in this task; this is the critical gate for the horizon PR

- [ ] **Step 1: Full reactor compile without tests**

```bash
./mvnw clean install -DskipTests
```

Expected: `BUILD SUCCESS` across all ~700 modules. Run time: ~20-40 minutes.

If the build fails with compile errors in downstream modules, the cascade has hit. Typical failure symptoms:
- `error: package org.springframework.beans.factory does not exist` in a module that previously got spring transitively through events.api
- `error: cannot find symbol class Event` / `class Log` — unlikely (Event and Log are still shipped by events.api) but possible if a module was referencing `org.opennms.netmgt.xml.event.Event` via a weird path.
- `package org.apache.camel does not exist` — means a downstream module was using camel via events.api transit. Was always wrong; add direct dep.
- `package org.opennms.netmgt.model does not exist` — means a downstream module was using model-api via events.api transit. Was always wrong; add direct dep.

- [ ] **Step 2: If cascade failures occur — fix downstream modules**

For each failing module:

1. Note the failing module's directory (e.g., `features/foo/pom.xml`).
2. Note the missing package/class from the compile error.
3. In that module's `pom.xml`, add a direct `<dependency>` declaration for the dep that supplies the missing package. Examples:
   - Missing `org.springframework.*` → add `spring-dependencies` dep as pom type, or the specific spring module.
   - Missing `org.apache.camel.*` → add `camel-dependencies` dep.
   - Missing `org.opennms.netmgt.model.*` → add `org.opennms.core:org.opennms.core.model-api`.
4. Re-run `./mvnw clean install -DskipTests` from horizon root.
5. Iterate until green.

**Cascade abort threshold:** if more than ~10 downstream modules break, stop and reconsider extracting a new `events/api-minimal` module (Option 3 from the design doc brainstorming). Re-surface the decision to the user before continuing.

- [ ] **Step 3: Confirm full reactor is green**

Re-run the build once after all cascade fixes:

```bash
./mvnw clean install -DskipTests
```

Expected: `BUILD SUCCESS` with no errors in any module.

### Task H5: Commit the diet + any cascade fixes

**Files:**
- `features/events/api/pom.xml` (guaranteed)
- Any downstream `pom.xml` files modified in Task H4 Step 2 (conditional)

- [ ] **Step 1: Stage the changes**

```bash
git add features/events/api/pom.xml
# Stage any cascade fix poms from Task H4 Step 2:
git status --short
# review the list, then git add each touched pom by name
```

Do not use `git add -A` or `git add .` — add each file by explicit path per the global git safety rules.

- [ ] **Step 2: Create the commit**

```bash
git commit -m "$(cat <<'EOF'
chore(events-api): slim pom transitive closure

Mark spring-dependencies and swagger-annotations as <optional>true</optional>
so they stop propagating to downstream consumers. Delete model-api and
camel-dependencies outright — neither is imported anywhere in the
events.api source tree.

The 2 classes that use Spring utility classes
(AnnotationBasedEventListenerAdapter, EventIpcManagerFactory) still
compile because optional deps remain visible to the owner module.
Downstream horizon modules that were implicitly riding on events.api's
transitive Spring/camel/model-api now declare those deps directly.

This unblocks delta-v's flow-enricher module to drop its 16-entry
exclusion block on events.api without losing EventForwarder, Event,
Log, or EventBuilder from the classpath. See delta-v design doc:
docs/superpowers/specs/2026-04-14-flow-enricher-events-api-local-stub-design.md
EOF
)"
```

- [ ] **Step 3: Verify commit**

```bash
git log -1 --stat
```

Expected: commit with the message above and the file list showing `features/events/api/pom.xml` plus any cascade fix poms.

### Task H6: Open horizon PR and merge

**Files:** none modified

- [ ] **Step 1: Push the branch**

```bash
git push -u origin chore/events-api-pom-diet
```

Expected: branch pushed, gh prints a URL hint to create a PR.

- [ ] **Step 2: Open PR via gh**

```bash
gh pr create --repo pbrane/delta-v-horizon --base develop \
  --title "chore(events-api): slim pom transitive closure" \
  --body "$(cat <<'EOF'
## Summary
- Mark spring-dependencies and swagger-annotations as `<optional>true</optional>` in features/events/api/pom.xml so they stop propagating to downstream consumers.
- Delete camel-dependencies and org.opennms.core.model-api — neither is imported anywhere in the events.api source tree.
- [If cascade fixes were made in Task H4 Step 2: list the downstream modules here and explain the dep each now declares directly.]

## Motivation
Unblocks delta-v's flow-enricher module to drop its 16-entry `<exclusion>` block on events.api. The full context is in the delta-v design doc: `docs/superpowers/specs/2026-04-14-flow-enricher-events-api-local-stub-design.md`.

## Test plan
- [x] `./mvnw -pl features/events/api clean install -DskipTests` — BUILD SUCCESS
- [x] `./mvnw -pl features/events/api test` — all events.api unit tests green
- [x] `./mvnw clean install -DskipTests` from root — full reactor green
- [ ] Reviewer: confirm no module is broken at HEAD
EOF
)"
```

- [ ] **Step 3: Wait for user to review and merge**

**User action required.** The plan executor (agent or human) does not merge this PR autonomously. Pause here and request the user confirm the horizon PR has been reviewed and merged.

### Task H7: Bump horizon version to 1.0.9

**Files:**
- Modify (via tool): every `pom.xml` in the horizon reactor — ~700 files changed automatically by `./mvnw versions:set`.

- [ ] **Step 1: Switch back to develop and pull**

```bash
cd /Users/david/development/src/opennms/delta-v-horizon
git switch develop
git pull --ff-only origin develop
```

Expected: develop is now up to date with the merged diet PR.

- [ ] **Step 2: Create version bump branch**

```bash
git switch -c chore/bump-version-1.0.9
```

- [ ] **Step 3: Run versions:set**

```bash
./mvnw versions:set -DnewVersion=1.0.9 -DprocessAllModules=true -DgenerateBackupPoms=false
```

Expected: Maven prints a long list of updated modules and ends with `BUILD SUCCESS`. This is the same command used for the 1.0.7 → 1.0.8 bump (commit `d57b03dd56a` — see its commit message for reference).

- [ ] **Step 4: Verify the version bump**

```bash
head -20 pom.xml | grep -A1 "<version>"
```

Expected: `<version>1.0.9</version>` near the top of the root pom.

```bash
git status --short | wc -l
```

Expected: ~700 modified pom.xml files (exact count depends on the current module graph).

- [ ] **Step 5: Stage and commit**

```bash
git add -u  # safe here because versions:set only touched tracked pom.xml files
git commit -m "$(cat <<'EOF'
chore: bump version to 1.0.9 for events.api classpath cleanup

Publishes the events.api pom diet from chore/events-api-pom-diet so
delta-v can consume it and drop the 16-entry exclusion block on
events.api in core/flow-enricher/pom.xml.

Generated via `./mvnw versions:set -DnewVersion=1.0.9 -DprocessAllModules=true -DgenerateBackupPoms=false`.
EOF
)"
```

Note: `git add -u` is acceptable here because `versions:set` only modifies tracked pom.xml files — no new untracked files are involved. Verify via `git status --short` before committing that nothing unexpected is staged.

- [ ] **Step 6: Push and open PR**

```bash
git push -u origin chore/bump-version-1.0.9
gh pr create --repo pbrane/delta-v-horizon --base develop \
  --title "chore: bump version to 1.0.9" \
  --body "$(cat <<'EOF'
## Summary
Bump version from 1.0.8 to 1.0.9 for the events.api classpath cleanup merged in #[PR number from Task H6].

## Test plan
- [x] Generated via `./mvnw versions:set -DnewVersion=1.0.9 -DprocessAllModules=true -DgenerateBackupPoms=false`
- [ ] Reviewer: confirm the diff is exclusively `<version>1.0.8</version>` → `<version>1.0.9</version>` across ~700 poms
EOF
)"
```

- [ ] **Step 7: Wait for user to review and merge**

**User action required.** Pause for user confirmation that the version bump PR has been merged to `develop`.

### Task H8: Tag and release 1.0.9

**Files:** none

- [ ] **Step 1: Switch to develop, pull, verify version**

```bash
cd /Users/david/development/src/opennms/delta-v-horizon
git switch develop
git pull --ff-only origin develop
head -20 pom.xml | grep -A1 "<version>"
```

Expected: `<version>1.0.9</version>`

- [ ] **Step 2: Create and push the release tag**

```bash
git tag 1.0.9
git push origin 1.0.9
```

Expected: tag push triggers `.github/workflows/publish.yml` (which matches the `[0-9]+.[0-9]+.[0-9]+` tag pattern).

- [ ] **Step 3: Monitor the publish workflow**

```bash
gh run list --repo pbrane/delta-v-horizon --workflow publish.yml --limit 3
```

Expected: the top entry is the 1.0.9 publish run. Wait for it to reach `completed` status with `success` conclusion. Workflow timeout is 60 minutes per the workflow file.

```bash
# Watch the run (optional, streams progress):
gh run watch --repo pbrane/delta-v-horizon <run-id>
```

- [ ] **Step 4: Verify 1.0.9 is published**

```bash
gh api "/orgs/pbrane/packages/maven/org.opennms.features.events.org.opennms.features.events.api/versions" 2>/dev/null | head -30
```

Expected: a version entry for `1.0.9`. Alternative: check the GitHub Packages UI under `pbrane/delta-v-horizon` → Packages.

---

## Phase D — Delta-v consumption

### Task D1: Set up delta-v feature branch

**Files:** none yet; branch creation only

**Working directory for every Task D*:** `/Users/david/development/src/opennms/delta-v`

- [ ] **Step 1: Check working tree state**

```bash
cd /Users/david/development/src/opennms/delta-v
git status --short
```

Expected: shows whatever modified files are present (likely the 5 provisiond-overlay imports/*.xml files from requisition runtime drift — leave them alone per `feedback_provisiond_requisition_drift`).

- [ ] **Step 2: Switch to develop and try to pull**

```bash
git switch develop
git pull --ff-only origin develop
```

If the pull succeeds, proceed to Step 3.

If the pull fails with "cannot pull with rebase: You have unstaged changes" because of the requisition drift, stash them first:

```bash
git stash push -u -m "WIP: provisiond requisition drift" -- opennms-container/delta-v/provisiond-overlay/etc/imports/
git pull --ff-only origin develop
```

Do NOT `git checkout --` those files or `git restore` them — the user may want them later.

- [ ] **Step 3: Create the feature branch**

```bash
git switch -c feature/flow-enricher-events-api-cleanup
```

- [ ] **Step 4: Restore the requisition drift if it was stashed**

```bash
git stash list | head -3
# If the WIP entry exists:
git stash pop
```

Expected: the 5 imports/*.xml files are back in the working tree, unstaged. Confirm via `git status --short`.

### Task D2: Clean stale horizon SNAPSHOTs from local `.m2`

**Files:** none in the repo; modifies `~/.m2/repository`

- [ ] **Step 1: Remove stale horizon SNAPSHOT artifacts**

```bash
find ~/.m2/repository/org/opennms -type d -name "1.0.8-SNAPSHOT" -exec rm -rf {} + 2>/dev/null
find ~/.m2/repository/org/opennms -type d -name "1.0.9-SNAPSHOT" -exec rm -rf {} + 2>/dev/null
```

Note: use `find ... -exec rm -rf` rather than the Bash tool's banned `rm -rf` shortcut style. No SNAPSHOT directory is load-bearing — published releases live under plain version directories like `1.0.8` or `1.0.9`.

- [ ] **Step 2: Verify no SNAPSHOT cache remains**

```bash
find ~/.m2/repository/org/opennms -type d -name "*-SNAPSHOT" 2>/dev/null | head
```

Expected: empty output.

### Task D3: Bump `deltav.horizon.version` to 1.0.9

**Files:**
- Modify: `pom.xml` (reactor root)

- [ ] **Step 1: Locate the property**

```bash
grep -n "deltav.horizon.version" pom.xml
```

Expected: one line like `<deltav.horizon.version>1.0.8</deltav.horizon.version>` in the `<properties>` block.

- [ ] **Step 2: Edit the property using Edit tool**

Replace:

```xml
<deltav.horizon.version>1.0.8</deltav.horizon.version>
```

with:

```xml
<deltav.horizon.version>1.0.9</deltav.horizon.version>
```

- [ ] **Step 3: Verify**

```bash
grep -n "deltav.horizon.version" pom.xml
```

Expected: `<deltav.horizon.version>1.0.9</deltav.horizon.version>`.

### Task D4: Drop exclusions from `core/flow-enricher/pom.xml`

**Files:**
- Modify: `core/flow-enricher/pom.xml`

- [ ] **Step 1: Locate the events.api dependency block**

```bash
grep -n -B1 -A25 "org.opennms.features.events.api" core/flow-enricher/pom.xml
```

Expected: one `<dependency>` block with a multi-line comment above it and 16 `<exclusion>` entries. In the current file this is approximately lines 237-262 (verify by eye before editing).

- [ ] **Step 2: Replace the block using Edit tool**

Replace:

```xml
        <!-- Horizon: Events API (EventForwarder interface required by Netflow parsers).
             Needs a deep exclusion list because events.api transitively pulls in
             legacy core.model-api → jaxb-dependencies → eclipselink and
             spring-dependencies → hibernate-dependencies → hibernate-core:3 chains. -->
        <dependency>
            <groupId>org.opennms.features.events</groupId>
            <artifactId>org.opennms.features.events.api</artifactId>
            <exclusions>
                <exclusion><groupId>org.apache.servicemix.bundles</groupId><artifactId>*</artifactId></exclusion>
                <exclusion><groupId>org.opennms.dependencies</groupId><artifactId>spring-dependencies</artifactId></exclusion>
                <exclusion><groupId>org.opennms.dependencies</groupId><artifactId>spring-security-dependencies</artifactId></exclusion>
                <exclusion><groupId>org.opennms.dependencies</groupId><artifactId>jaxb-dependencies</artifactId></exclusion>
                <exclusion><groupId>org.opennms.dependencies</groupId><artifactId>atomikos-dependencies</artifactId></exclusion>
                <exclusion><groupId>org.hibernate</groupId><artifactId>hibernate-core</artifactId></exclusion>
                <exclusion><groupId>org.hibernate.javax.persistence</groupId><artifactId>hibernate-jpa-2.0-api</artifactId></exclusion>
                <exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion>
                <exclusion><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId></exclusion>
                <exclusion><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId></exclusion>
                <exclusion><groupId>commons-logging</groupId><artifactId>commons-logging</artifactId></exclusion>
                <exclusion><groupId>org.opennms</groupId><artifactId>opennms-model</artifactId></exclusion>
                <exclusion><groupId>org.opennms</groupId><artifactId>opennms-util</artifactId></exclusion>
                <exclusion><groupId>org.opennms</groupId><artifactId>opennms-config</artifactId></exclusion>
                <exclusion><groupId>org.opennms.features.config</groupId><artifactId>*</artifactId></exclusion>
                <exclusion><groupId>org.opennms.core</groupId><artifactId>org.opennms.core.model-api</artifactId></exclusion>
            </exclusions>
        </dependency>
```

with:

```xml
        <!-- Horizon: Events API (EventForwarder interface required by Netflow parsers). -->
        <dependency>
            <groupId>org.opennms.features.events</groupId>
            <artifactId>org.opennms.features.events.api</artifactId>
        </dependency>
```

- [ ] **Step 3: Verify**

```bash
grep -n -B1 -A5 "org.opennms.features.events.api" core/flow-enricher/pom.xml
```

Expected: exactly 5 lines (comment + 4-line `<dependency>` block) with no `<exclusions>` or `<exclusion>` elements.

```bash
grep -c "<exclusion>" core/flow-enricher/pom.xml
```

Expected: a number 16 lower than before the edit. Record the before/after count for the PR description.

### Task D5: Build flow-enricher

**Files:** none modified

- [ ] **Step 1: Clean compile**

```bash
./mvnw -pl core/flow-enricher clean install -DskipTests
```

Expected: `BUILD SUCCESS`. Run time: ~1-2 minutes.

If this fails with `NoClassDefFoundError: org/opennms/netmgt/events/api/EventForwarder` or similar, something went wrong with Step D4 — the dep block may have been accidentally removed instead of just the exclusions. Re-check the file.

If it fails with a dep resolution error on horizon 1.0.9, the horizon publish workflow may not have completed. Re-check Task H8 Step 3.

### Task D6: Classpath verification (the money shot)

**Files:** none modified

This is the definitive check that the root cause is fixed, not just the symptom.

- [ ] **Step 1: Run dependency:tree and grep for banned transitives**

```bash
./mvnw -pl core/flow-enricher dependency:tree -DoutputFile=/tmp/flow-enricher-deps.txt
grep -E "(eclipselink|hibernate-core|hibernate-jpa|camel-core|opennms-model-api|spring-context)" /tmp/flow-enricher-deps.txt
```

Expected: **empty output.** Every listed token must be absent from the dependency tree.

If any appear, do not proceed. Diagnose which source dep is pulling them in:

```bash
./mvnw -pl core/flow-enricher dependency:tree -Dincludes="*:<banned-artifact>" | tail -40
```

Replace `<banned-artifact>` with the specific artifact that leaked (e.g., `hibernate-core`). The output shows the transitive path. If the leak comes from a horizon module other than events.api, that's a new finding — stop and re-scope.

- [ ] **Step 2: Confirm events.api is still on the classpath**

```bash
grep "events.api" /tmp/flow-enricher-deps.txt
```

Expected: at least one line showing `org.opennms.features.events:org.opennms.features.events.api:jar:1.0.9:compile`. Confirms we didn't accidentally remove the dep.

- [ ] **Step 3: Confirm version is 1.0.9 everywhere**

```bash
grep "1.0.8" /tmp/flow-enricher-deps.txt
```

Expected: empty output. No stale 1.0.8 references.

### Task D7: Run flow-enricher unit and integration tests

**Files:** none modified

- [ ] **Step 1: Run tests**

```bash
./mvnw -pl core/flow-enricher test
```

Expected: `BUILD SUCCESS`. Surefire includes both `*Test` and `*IT` patterns per `core/flow-enricher/pom.xml` lines 403-408, so integration tests run in the same phase. Critical tests to watch for:

- `LoggingEventForwarderTest` — must pass (proves `EventForwarder` interface is still loadable and the logging implementation compiles)
- Parser bridge IT tests (Netflow5/9/IPFIX/sFlow) — must pass (proves the parsers still construct with the `EventForwarder` bean)
- Enricher and splitter tests — must pass (unrelated to this change but should not regress)

If `LoggingEventForwarderTest` fails with `ClassNotFoundException: org.opennms.netmgt.events.api.EventForwarder`, the events.api jar is not on the classpath. Re-check Task D4 Step 2.

### Task D8: (Recommended) End-to-end runtime verification

**Files:** none modified

This task is labeled recommended, not strictly required. Run it if the user wants high-confidence verification beyond compile-time and dependency-tree checks. Skip only if time-boxed and D5–D7 all passed cleanly.

- [ ] **Step 1: Rebuild the delta-v Docker image**

Per `feedback_rebuild_all_daemons` (updated 2026-04-13): `build.sh deltav` is self-healing as of PR #154. Phase 0 of the script walks `core/daemon-boot-*/` module mtimes and rebuilds any stale ones before staging, so a single invocation is sufficient — no separate manual daemon rebuild step is needed.

```bash
cd opennms-container/delta-v
./build.sh deltav
cd -
```

Expected: image rebuilt with the new flow-enricher jar. First run after a source change may take an extra ~15s for the freshness check to fire `./mvnw -B -DskipTests -pl <stale modules> -am install`.

- [ ] **Step 2: Start the E2E Compose stack**

```bash
cd opennms-container/delta-v
docker compose up -d
```

Expected: all containers reach `Up (healthy)` within 2-3 minutes.

- [ ] **Step 3: Tail flow-enricher logs**

```bash
docker compose logs -f flow-enricher &
```

Expected: the flow-enricher startup banner, followed by Spring Cloud Stream binder connection messages, followed by eventual flow consumption. Watch for:

- **MUST NOT see:** `NoClassDefFoundError`, `ClassNotFoundException`, `LinkageError`, `IncompatibleClassChangeError` — any of these indicate a classpath problem from the diet.
- **MAY see:** `Parser event dropped (logging-only forwarder): uei=...` — confirms `EventForwarder.sendNow()` is firing through the new classpath.

- [ ] **Step 4: Trigger a clock-skew event (optional, highest-confidence check)**

From the host, adjust one of the flow exporter container clocks forward by >10 seconds:

```bash
docker compose exec hsflowd date -s "$(date -d '+15 seconds')"
```

Wait for the next hsflowd flow export cycle (~30s) and confirm the flow-enricher log shows:

```
Parser event dropped (logging-only forwarder): uei=uei.opennms.org/internal/telemetry/clockSkewDetected
```

This is the definitive runtime proof that the `ParserBase.java:368` → `EventForwarder.sendNow()` call path is intact.

- [ ] **Step 5: Tear down**

```bash
docker compose down
```

### Task D9: Commit delta-v changes

**Files:**
- `pom.xml` (reactor root)
- `core/flow-enricher/pom.xml`

- [ ] **Step 1: Stage only the two files**

```bash
cd /Users/david/development/src/opennms/delta-v
git add pom.xml core/flow-enricher/pom.xml
git status --short
```

Expected: two staged files. The 5 `imports/*.xml` files from requisition drift should still appear as unstaged modifications and should NOT be in the staged list.

- [ ] **Step 2: Commit**

```bash
git commit -m "$(cat <<'EOF'
chore(flow-enricher): drop events.api exclusion block

Bump horizon version to 1.0.9 and remove the 16-entry <exclusion> list
on the events.api dependency in core/flow-enricher/pom.xml. Horizon
1.0.9 slimmed the events.api pom transitive closure structurally
(spring-dependencies and swagger-annotations marked optional,
model-api and camel-dependencies deleted entirely), so the exclusions
are no longer needed to keep eclipselink 2.5.1 and hibernate-core
3.6.11 off the Spring Boot 4 classpath.

Design: docs/superpowers/specs/2026-04-14-flow-enricher-events-api-local-stub-design.md
Horizon change: pbrane/delta-v-horizon PR #[horizon diet PR number]
Horizon release: 1.0.9

Verified via `./mvnw -pl core/flow-enricher dependency:tree` — no
eclipselink, hibernate-core, hibernate-jpa, camel-core, model-api, or
spring-context remains on the classpath.
EOF
)"
```

- [ ] **Step 3: Verify the commit**

```bash
git log -1 --stat
```

Expected: commit with the message above and exactly two files: `pom.xml` and `core/flow-enricher/pom.xml`.

### Task D10: Open delta-v PR

**Files:** none

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feature/flow-enricher-events-api-cleanup
```

- [ ] **Step 2: Open PR against pbrane/delta-v (NOT OpenNMS/opennms)**

Per `feedback_never_pr_opennms`, always use `--repo pbrane/delta-v`:

```bash
gh pr create --repo pbrane/delta-v --base develop \
  --title "chore(flow-enricher): drop events.api exclusion block" \
  --body "$(cat <<'EOF'
## Summary
- Bump `deltav.horizon.version` from 1.0.8 to 1.0.9
- Remove the 16-entry `<exclusion>` list on the `events.api` dep in `core/flow-enricher/pom.xml`

## Motivation
The exclusions violated `feedback_fix_horizon_not_exclusions` — they were masking a symptom (eclipselink 2.5.1 and hibernate-core 3.6.11 leaking onto the Spring Boot 4 classpath) rather than fixing the root cause. Horizon 1.0.9 slims the `events.api` pom transitive closure at the source (pbrane/delta-v-horizon PR #[horizon diet PR number]), so the exclusions are no longer necessary.

Design doc: `docs/superpowers/specs/2026-04-14-flow-enricher-events-api-local-stub-design.md`

## Test plan
- [x] `./mvnw -pl core/flow-enricher clean install` — BUILD SUCCESS
- [x] `./mvnw -pl core/flow-enricher dependency:tree | grep -E '(eclipselink|hibernate-core|hibernate-jpa|camel-core|opennms-model-api|spring-context)'` — empty output
- [x] `./mvnw -pl core/flow-enricher test` — all unit + IT tests green including `LoggingEventForwarderTest` and parser bridge IT tests
- [ ] (Optional) E2E runtime via hsflowd Compose stack with triggered clock skew
EOF
)"
```

- [ ] **Step 3: Confirm the PR targets the right repo**

```bash
gh pr view --repo pbrane/delta-v
```

Expected: the new PR URL shows `https://github.com/pbrane/delta-v/pull/<N>`. If instead it shows `github.com/OpenNMS/opennms/...`, abort and close the PR — you targeted the wrong repo, per `feedback_never_pr_opennms`.

- [ ] **Step 4: Wait for user to review and merge**

**User action required.** Pause for user review.

---

## Phase M — Memory updates

These tasks run after the delta-v PR has merged. They update the persistent memory system to reflect the completed state.

**Working directory:** `/Users/david/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/`

### Task M1: Mark the original memory as DONE

**Files:**
- Modify: `project_flow_enricher_events_api_local_stub.md`

- [ ] **Step 1: Update the frontmatter and status section**

Replace:

```markdown
**Status (2026-04-13):** IN PLACE as interim fix in Phase 2 (feature/flow-enricher-phase2-parser-bridge, Task 1 commit `5a76e783190`). Functionally correct, classpath clean at HEAD, but it's an exclusion-driven fix against the `feedback_fix_horizon_not_exclusions` rule.
```

with:

```markdown
**Status (2026-04-14):** DONE via horizon 1.0.9 pom diet + delta-v PR #[delta-v PR number]. Horizon `features/events/api/pom.xml` marked spring-dependencies and swagger-annotations as `<optional>true</optional>` and deleted camel-dependencies + model-api. Delta-v flow-enricher/pom.xml dropped all 16 exclusions on the events.api block. eclipselink 2.5.1 and hibernate-core 3.6.11 are gone structurally, not by exclusion.
```

- [ ] **Step 2: Append a "Resolution" section at the end**

```markdown

## Resolution

- Horizon PR: pbrane/delta-v-horizon #[horizon diet PR number] (events.api pom diet)
- Horizon release PR: pbrane/delta-v-horizon #[horizon version bump PR number] (1.0.9 bump)
- Delta-v PR: pbrane/delta-v #[delta-v PR number] (version bump + exclusion drop)
- Design doc: docs/superpowers/specs/2026-04-14-flow-enricher-events-api-local-stub-design.md
- Implementation plan: docs/superpowers/plans/2026-04-14-flow-enricher-events-api-cleanup.md
```

### Task M2: Create followup memory for remaining exclusion blocks

**Files:**
- Create: `project_flow_enricher_classpath_batch_cleanup.md`

- [ ] **Step 1: Write the followup memory**

Create a new file at `~/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/project_flow_enricher_classpath_batch_cleanup.md` with this content:

```markdown
---
name: flow-enricher classpath hygiene batch cleanup
description: FUTURE — 10 remaining exclusion blocks in core/flow-enricher/pom.xml + 4 other delta-v modules referencing events.api. Apply the same horizon-side pom diet pattern per feedback_fix_horizon_not_exclusions.
type: project
---
**Status (2026-04-14):** Followup from the events.api cleanup (see `project_flow_enricher_events_api_local_stub.md`).

After the events.api diet landed in horizon 1.0.9 and its exclusions were dropped from flow-enricher/pom.xml, 10 other exclusion blocks remain in `core/flow-enricher/pom.xml`:

- `org.opennms.core.ipc.sink:org.opennms.core.ipc.sink.common` (~15 exclusions)
- `org.opennms.features.telemetry:org.opennms.features.telemetry.common` (~15)
- `org.opennms.features.telemetry.protocols.netflow:org.opennms.features.telemetry.protocols.netflow.adapter` (~15)
- `org.opennms.features.telemetry.protocols.sflow:org.opennms.features.telemetry.protocols.sflow.adapter` (~15)
- `org.opennms.features.telemetry.protocols.netflow:org.opennms.features.telemetry.protocols.netflow.parser` (~17, includes atomikos and features.config)
- `org.opennms.features.telemetry.protocols.sflow:org.opennms.features.telemetry.protocols.sflow.parser` (~15)
- `org.opennms.features.telemetry:org.opennms.features.telemetry.listeners` (~8)
- `org.opennms.features.flows:org.opennms.features.flows.api` (~13)
- `org.opennms.features.flows:org.opennms.features.flows.processing` (~17, includes thresholding-api and jasypt)
- `opennms-util` (test scope, ~10)

Plus 4 other delta-v modules that reference `events.api` with their own exclusion lists: `core/opennms-model-jakarta`, `core/event-forwarder-kafka`, `core/daemon-common`, and the root reactor `pom.xml`.

**Approach:** same as events.api diet. For each horizon module in the list, audit `features/.../pom.xml` in delta-v-horizon for transitive deps that have zero source imports (cargo cult) or can be marked `<optional>true</optional>` because they serve only implementation classes rather than public API. Batch the horizon edits into a single "horizon classpath hygiene" PR + release (1.0.10 or later), then drop the matching exclusion blocks in delta-v.

**Why this matters:** same motivation as events.api — exclusions are symptoms. The 10 blocks are mechanical pom bloat leaking from horizon's modular layout assumption (OSGi) into our Spring Boot 4 classpath.

**When to pick it up:** when the next planned horizon release window opens. Low urgency — the exclusions are functionally correct — but each one costs ~20 lines of POM noise and has the same maintenance risk as the events.api block did.

**Related:**
- `project_flow_enricher_events_api_local_stub.md` — the precedent (DONE)
- `feedback_fix_horizon_not_exclusions.md` — the rule being enforced
- `project_horizon_105_cleanup.md` — the pattern for batched horizon cleanups
```

### Task M3: Update `MEMORY.md` index

**Files:**
- Modify: `MEMORY.md`

- [ ] **Step 1: Find the existing entry for `project_flow_enricher_events_api_local_stub.md`**

```bash
grep -n "project_flow_enricher_events_api_local_stub" ~/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/MEMORY.md
```

Expected: one line showing the current entry.

- [ ] **Step 2: Update the entry to reflect DONE status**

Replace:

```markdown
- [project_flow_enricher_events_api_local_stub.md](project_flow_enricher_events_api_local_stub.md) — FOLLOWUP: Phase 2 Task 1 added 16-item exclusion list to events.api; tier-2 local stub fix preferred to replace it
```

with:

```markdown
- [project_flow_enricher_events_api_local_stub.md](project_flow_enricher_events_api_local_stub.md) — DONE (horizon 1.0.9 + delta-v PR): events.api pom diet eliminated eclipselink/hibernate-3 structurally; all 16 exclusions dropped
```

- [ ] **Step 3: Add the new batch cleanup entry**

Immediately after the entry updated in Step 2, add:

```markdown
- [project_flow_enricher_classpath_batch_cleanup.md](project_flow_enricher_classpath_batch_cleanup.md) — FUTURE: 10 remaining exclusion blocks in flow-enricher/pom.xml + 4 other modules; apply same horizon-side diet pattern
```

- [ ] **Step 4: Verify the index is still under 200 lines**

```bash
wc -l ~/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/MEMORY.md
```

Expected: a number less than 200. If it's over, consider pruning older resolved entries (but do not touch unrelated entries in this PR — just note the line count for a future cleanup).

---

## Self-Review Checklist

After executing each phase, the plan executor should verify:

- [ ] Phase H complete: horizon 1.0.9 is published to GitHub Packages and visible to `mvn dependency:resolve` from any machine with the right settings.xml
- [ ] Phase D complete: delta-v PR merged to `develop` with classpath verification passing
- [ ] Phase M complete: memory system reflects the new state

## Success Criteria (mirrors design doc)

- [ ] Horizon `features/events/api/pom.xml` diet merged and released as 1.0.9
- [ ] Horizon full-reactor build green after the diet (with any cascade-fix dep declarations added inline)
- [ ] Delta-v `deltav.horizon.version` bumped to 1.0.9
- [ ] `core/flow-enricher/pom.xml` `events.api` dep block has zero exclusions
- [ ] `./mvnw -pl core/flow-enricher dependency:tree | grep -E "(eclipselink|hibernate-core|hibernate-jpa|camel-core|opennms-model-api|spring-context)"` returns empty
- [ ] `LoggingEventForwarderTest` and all flow-enricher unit + IT tests pass
- [ ] (Recommended) E2E runtime verification with hsflowd shows no classpath-induced runtime errors
- [ ] Followup memory `project_flow_enricher_classpath_batch_cleanup.md` opened tracking cleanup of the 10 remaining blocks
