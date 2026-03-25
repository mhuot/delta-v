# Next Session: Fix Hibernate Entity Resolution for Classpath-Based Launch

## Context

Branch `feat/layered-jar-deduplication` replaces the single 4.85GB `daemon-deltav-springboot` Docker image with a shared `daemon-base` (~419MB) plus 12 per-daemon images. All 13 images build successfully. The change switches daemon launch from `java -jar /opt/fat.jar` (Spring Boot fat JAR loader) to `java -cp "/opt/libs/external/*:..." MainClass` (flat classpath).

## The Blocker

Provisiond crashes on startup with:
```
org.hibernate.UnknownEntityException: Could not resolve root entity 'OnmsDistPoller'
```

**Root cause:** The Spring Boot fat JAR launcher performs automatic JPA entity scanning via its custom classloader. When we switch to flat `-cp` based launch, this auto-scanning doesn't happen. Hibernate can't find the `OnmsDistPoller` entity class even though its JAR is on the classpath.

## What Works

5 daemons start successfully with classpath launch: **Alarmd, EventTranslator, Trapd, Syslogd, Discovery**. These either don't use JPA or their `PersistenceManagedTypes` bean explicitly registers all entities they need.

## What Needs Investigation

1. **Read Provisiond's JPA configuration**: `core/daemon-boot-provisiond/src/main/java/org/opennms/netmgt/provision/boot/ProvisiondJpaConfiguration.java` — check how `PersistenceManagedTypes` is configured and whether `OnmsDistPoller` is in the registered package list.

2. **Check how Alarmd's JPA config works** (it's healthy): `core/daemon-boot-alarmd/src/main/java/org/opennms/netmgt/alarmd/boot/AlarmdJpaConfiguration.java` — compare the entity registration pattern.

3. **Test hypothesis**: Add `-Dspring.jpa.properties.hibernate.archive.autodetection=class` to Provisiond's `JAVA_OPTS` in docker-compose.yml and see if it fixes entity discovery.

4. **Alternative fix**: Ensure `PersistenceManagedTypes` in each daemon's JPA config includes all entity packages needed (e.g., `org.opennms.netmgt.model` which contains `OnmsDistPoller`).

5. **Verify all JPA-using daemons**: After fixing Provisiond, test BSMd, Pollerd, PerspectivePollerd, Enlinkd, Collectd, Telemetryd.

## Files to Look At

- `core/daemon-boot-provisiond/src/main/java/**/ProvisiondJpaConfiguration.java`
- `core/daemon-boot-alarmd/src/main/java/**/AlarmdJpaConfiguration.java` (working reference)
- `opennms-container/delta-v/docker-compose.yml` (JAVA_OPTS per daemon)
- `opennms-container/delta-v/entrypoint.sh` (classpath launch)

## How to Test

```bash
cd opennms-container/delta-v
./build.sh deltav
COMPOSE_PROFILES=passive docker compose up -d
docker compose logs provisiond 2>&1 | tail -30
```

## Branch State

- Branch: `feat/layered-jar-deduplication` (8 commits, pushed to origin)
- Spec: `docs/superpowers/specs/2026-03-25-layered-jar-deduplication-design.md`
- Plan: `docs/superpowers/plans/2026-03-25-layered-jar-deduplication.md`
