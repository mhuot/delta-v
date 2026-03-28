# Next Session Prompt

> Copy everything below the line into the next Claude Code conversation.

---

## Context

All 12 daemons run as standalone Spring Boot 4 apps. The opennms-services monolith has been eliminated (PR #65). All 6 E2E test suites pass (78/78). Recent PRs:

- **PR #66**: BeanUtils bridge in daemon-common — prevents legacy ContextRegistry fallback
- **PR #68**: Dependency freshening — commons-jxpath, log4j2, postgresql, removed ehcache/dbunit
- **PR #69**: DaemonEntityScopeProvider — real MATE interpolation (node/interface/service/SCV/env scopes)
- **PR #70**: Missing merge() methods + findByIpAddress for Enlinkd Jakarta models
- **PR #71**: Standardized `--pre-clean` / `--post-cleanup` across all E2E tests

## What's Next

Prioritized follow-ups from memory:

### 1. Thresholding E2E Tests (highest value)
Verify thresholding works end-to-end in Collectd, Pollerd, PerspectivePollerd. The `EntityScopeProvider` prerequisite is now satisfied (PR #69). `ThresholdingService` is still stubbed with a no-op — needs real implementation.

### 2. AbstractServiceDaemon → SpringServiceDaemon Interface
All 12 daemon classes still extend `AbstractServiceDaemon`. Convert to a leaner `SpringServiceDaemon` interface. The `SpringServiceDaemonSmartLifecycle` adapter already wraps the daemons.

### 3. Constructor Injection Cleanup
Remove `@Autowired` field injection from service implementation classes (`features/poller/impl`, `features/collection/impl`, `features/event-translator`). Convert to constructor injection following the daemon-boot pattern.

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
