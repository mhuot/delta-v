# Next Session: PR1 — Phase 2 Reactor Pruning

> Copy everything below the line into the next Claude Code conversation.

---

## Goal

Start PR1 of the Phase 3 horizon-extraction migration. PR1 is **Phase 2 (reactor pruning)** — remove ~561 unneeded `<module>` entries from `pbrane/delta-v`'s root `pom.xml`, leaving ~219 modules in the reactor (the transitive closure of the 16 daemon-boot modules + db-init + minion-boot).

**Target build time after PR1:** ~4 minutes clean full build (down from ~10 min).

## Prerequisites (verify before starting)

- PR #119 must be merged to `develop` (adds the Phase 3 spec + PR0 plan docs)
- PR #120 must be merged to `develop` (adds `test-minion-rpc-e2e.sh`, the canary)
- Running the canary locally should produce **11 assertions pass / 0 fail**:
  ```bash
  cd opennms-container/delta-v && ./deploy.sh up lite
  ./test-minion-rpc-e2e.sh --pre-clean --verbose
  ```

If either PR is unmerged, stop and address that first.

## What PR1 Does

### Changes
- Edit `pbrane/delta-v/pom.xml` (root): remove ~561 `<module>` entries for horizon modules Delta-V does NOT transitively reference
- Horizon source stays on the filesystem (not deleted) — just no longer built
- The ~219 modules that stay in the reactor = ~20 Delta-V-authored modules + ~199 horizon modules in the transitive closure

### How to compute the closure
For each of the 18 entry points (16 daemon-boots + db-init + minion-boot):
```bash
mvn dependency:list -DincludeGroupIds=org.opennms -f <entry-point-pom>
```
Union all the results → that's the closure. Anything not in the union is prunable.

### Validation (critical)
- `make build` succeeds with the pruned reactor
- `./test-minion-rpc-e2e.sh --pre-clean --verbose` → **11 passed / 0 failed**
- Full E2E suite (93 tests) passes
- Measured build time is recorded in the PR description (~4 min expected)

### Rollback
Revert the PR — horizon source was never moved, so revert restores the reactor immediately.

## Reference Documents

- **Spec:** `docs/plans/2026-04-05-horizon-extraction-spec.md` (PR1 detailed in Section 6 Migration Plan)
- **Brainstorm transcript:** `docs/plans/2026-04-05-horizon-extraction-brainstorm.md` (Q6 explains why Path 1 sequential migration was chosen)
- **Canary test:** `opennms-container/delta-v/test-minion-rpc-e2e.sh` (must not regress)

## Suggested Approach for the Session

1. **Verify prerequisites** — confirm #119 and #120 are merged; run canary locally to establish baseline
2. **Use `superpowers:brainstorming` briefly** to surface any risks in the closure calculation (e.g., modules provided via runtime classpath rather than `<dependency>` declarations, plugins that load classes reflectively, etc.)
3. **Use `superpowers:writing-plans`** to create a step-by-step implementation plan that includes:
   - Closure computation script
   - POM editing strategy (how to remove 561 `<module>` entries safely)
   - Validation checkpoints after each major change
4. **Execute via `superpowers:subagent-driven-development`** — brand-new feature branch from latest develop
5. **Use `superpowers:finishing-a-development-branch`** to open PR1 against `develop`

## Risks to Consider

- **Runtime-only dependencies**: some horizon modules may contribute via classpath assembly without appearing in `dependency:list`. If a daemon fails to start after PR1, that's the likely culprit.
- **Plugin-provided modules**: Maven plugins (assembly, shade, etc.) may reference modules that don't appear in dependency trees.
- **Test-scope gaps**: `mvn dependency:list` with `-DincludeGroupIds=org.opennms` may or may not include test-scope deps — verify by checking whether test-only modules end up in the closure.
- **Incremental vs. big-bang pruning**: consider pruning in phases (e.g., delete obviously-dead modules first, re-validate, then narrow closer to the closure). Reduces blast radius per commit.

## Important Reminders

- **Never create PRs against OpenNMS/opennms** — always `--repo pbrane/delta-v`
- **Always `git pull develop` before creating feature branches**
- **Feature branches for all work; merge via PR**
- **Run the canary test after every significant change** — it's the Phase 3 gate
