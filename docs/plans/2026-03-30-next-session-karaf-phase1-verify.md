# Next Session: Karaf Removal Phase 1 — Verification & PR

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Branch: `chore/karaf-removal-phase1` (worktree at `/Users/david/development/src/opennms/delta-v-karaf-removal`)

Phase 1 deletions are complete and compile-verified. 9 commits, 1,077,855 lines deleted, 1,398 files changed. Build succeeds (`make build` passes).

## What was deleted

1. `container/` — Karaf OSGi container (15 subdirectories, feature XMLs)
2. `opennms-full-assembly/`, `opennms-base-assembly/`, `opennms-assemblies/`, `opennms-install/`, `assemble.pl` — Karaf distribution assembly
3. `opennms-bootstrap/`, `opennms-jetty/`, `opennms-config-tester/`, `smoke-test/` — Karaf-specific modules
4. `features/container/` (minion + sentinel) — Karaf assemblies for Minion/Sentinel
5. ~206 `OSGI-INF/blueprint/` directories — OSGi service wiring
6. Makefile `assemble` target
7. 13 Karaf/OSGi/Felix/Pax version properties from root POM
8. 1 OSGi managed dependency (`osgi.annotation`)

## Fixes applied during deletion

- `opennms-bootstrap` dependency removed from `core/password`, `core/test-api/db`, `features/springframework-security`
- `OpenNMSProxyLoginModule` import replaced with string literal in `features/springframework-security` (source + test)

## Remaining tasks

### Task 9: Transitive Dependency Audit

Baseline was captured at `/tmp/dep-tree-before.txt` and `/tmp/karaf-deps-before.txt` (503 Karaf-ecosystem artifact references). Run the same dependency:tree command post-deletion and diff.

```bash
cd /Users/david/development/src/opennms/delta-v-karaf-removal
./compile.pl -DskipTests --projects \
  :org.opennms.core.daemon-boot-alarmd,:org.opennms.core.daemon-boot-bsmd,\
  :org.opennms.core.daemon-boot-collectd,:org.opennms.core.daemon-boot-discovery,\
  :org.opennms.core.daemon-boot-enlinkd,:org.opennms.core.daemon-boot-eventtranslator,\
  :org.opennms.core.daemon-boot-perspectivepollerd,:org.opennms.core.daemon-boot-pollerd,\
  :org.opennms.core.daemon-boot-provisiond,:org.opennms.core.daemon-boot-syslogd,\
  :org.opennms.core.daemon-boot-telemetryd,:org.opennms.core.daemon-boot-trapd \
  -am dependency:tree 2>&1 | tee /tmp/dep-tree-after.txt

grep -E 'org\.apache\.(karaf|servicemix|felix)|org\.ops4j\.pax|org\.osgi' /tmp/dep-tree-after.txt | sort -u > /tmp/karaf-deps-after.txt
diff /tmp/karaf-deps-before.txt /tmp/karaf-deps-after.txt
```

If the baseline files are gone, re-run on develop first for comparison.

### Task 10: Docker Deploy and E2E Verification

1. Build all 12 daemon boot JARs: `make build` (already done — just rebuild JARs)
2. Build Docker images: `cd opennms-container/delta-v && ./build.sh deltav`
3. Deploy: `./deploy.sh up full`
4. Verify health: all 12 daemons pass `/actuator/health`
5. Run E2E tests: trapd, alarmd, pollerd, collectd, enlinkd, provisiond
6. Create PR: `gh pr create --repo pbrane/delta-v --base develop`

### Also pending from earlier session

PR #81 (`fix/daemon-startup-develop`) is open with the daemon startup fixes (DaemonEventConfDao, PrimaryType, OnmsAssetRecord, LldpLinkDaoJpa). Consider merging that first, then rebasing this branch on develop before the E2E tests.
