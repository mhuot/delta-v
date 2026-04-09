# Next Session: Complete opennms-model Utility Class Port

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Branch: `fix/horizon-1.0.5-cleanup` (PR #130 on pbrane/delta-v)

Horizon 1.0.5 is published and clean (ActiveMQ, C3P0, ServiceMix Kafka removed at source). Delta-v has been bumped to 1.0.5 with exclusions removed.

The clean build exposed that **opennms-model utility classes** were never ported to model-jakarta. PR #102 ported all 43 JPA entities, but non-entity classes (JAXB adapters, discovery models, etc.) were left behind. Previous Docker builds silently included opennms-model via stale .m2 cache — a stowaway that masked the gap. The current clean build is the first time daemons have truly run without opennms-model.

**Already ported/stubbed in this branch:**
- `OnmsCriteria` — empty stub (classloading shim for OnmsDao defaults)
- `IPPollAddress`, `IPPollRange`, `IPAddrRange` — full copies in model-jakarta
- `snmp4j` — explicit dependency added to trapd, enlinkd, telemetryd

**Known still missing (from runtime errors):**
- `PrimaryTypeAdapter` — JAXB adapter for PrimaryType enum, used by Provisiond XML unmarshalling
- Likely more — every import/provisioning path that touches opennms-model utility classes

## What Needs To Be Done

### 1. Systematic scan of all opennms-model classes referenced at runtime

Compare what's in the old working Docker image (`36.0.0-SNAPSHOT` tag) vs the current one to identify ALL opennms-model classes that were being used:

```bash
# List all classes in old opennms-model JAR
docker run --rm --entrypoint jar opennms/provisiond:36.0.0-SNAPSHOT \
  tf /opt/libs/internal/opennms-model-36.0.0-SNAPSHOT.jar | grep "\.class$"
```

Then cross-reference against the horizon source to categorize each class:
- **JPA entities** (already ported to model-jakarta) — skip
- **JAXB adapters** (PrimaryTypeAdapter, etc.) — copy to model-jakarta
- **Utility/model classes** (discovery package, OnmsCriteria, etc.) — copy or stub
- **Dead code** (not referenced by any delta-v daemon) — skip

### 2. Copy/port all needed utility classes to model-jakarta

For each needed class:
- Check dependencies (commons-lang ToStringBuilder → replace with plain Java)
- Copy to model-jakarta with same package path
- Verify it compiles

### 3. Verify

```bash
make build                    # compile
build.sh deltav               # Docker images
deploy.sh down && deploy.sh up full   # redeploy
# Wait for ALL HEALTHY
# Run all 8 E2E suites — target: 119/119
```

### 4. Push and get E2E green

All changes go on `fix/horizon-1.0.5-cleanup` branch (PR #130).

## Key Files

- `core/opennms-model-jakarta/src/main/java/org/opennms/netmgt/model/` — where ported classes live
- `opennms-container/delta-v/build.sh` — Docker image builder
- `opennms-container/delta-v/compute-shared-libs.sh` — classpath layer partitioning (priority/internal/external)

## Architecture Reminder

Docker classpath order:
1. `/opt/libs/priority/` — model-jakarta, dao-jpa-support (jakarta.persistence entities WIN)
2. `/opt/libs/internal/` — org.opennms JARs shared across daemons
3. `/opt/libs/external/` — third-party JARs
4. `/opt/libs/daemon/` — per-daemon JARs
5. `/opt/app/` — Spring Boot application JAR

model-jakarta in priority/ ensures its jakarta.persistence entities override any javax.persistence entities. Utility classes from opennms-model don't conflict — they just need to be present somewhere on the classpath.

## Important Reminders

- **Never create PRs against OpenNMS/opennms** — always `--repo pbrane/delta-v`
- **Always purge .m2 cache** when testing: `find ~/.m2/repository/org/opennms -name "*1.0.4*" -exec rm -rf {} +`
- **The 36.0.0-SNAPSHOT Docker tag** is the last known working image — use it for comparison
- **opennms-model must NOT come back** — port the utility classes, don't re-add the JAR
