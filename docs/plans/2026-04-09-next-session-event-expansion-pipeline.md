# Next Session: Event Expansion Pipeline + Alarm Description Fix

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Branch: `develop` (clean, all PRs merged)

We just shipped `JdbcEventUtil` (PR #135) — a JDBC-backed implementation of the `EventUtil` interface in `daemon-common` that resolves `%nodelabel%`, `%ifalias%`, `%foreignsource%`, `%foreignid%`, `%nodelocation%`, `%primaryinterface%`, `%asset[field]%`, and `%hostname%` tokens via direct SQL queries. It replaces 6 identical anonymous stub implementations across Alarmd, Collectd, Enlinkd, PerspectivePollerd, Pollerd, and Telemetryd.

**Problem discovered during validation:** Alarm `logmsg` and `description` columns in PostgreSQL are **empty** for SNMP trap alarms. The only alarm with a non-empty `logmsg` was a syslog-sourced event (which carries the raw syslog message directly). This means the event definition's `<logmsg>` and `<descr>` templates (containing `%nodelabel%`, `%ifalias%` etc.) are never being applied to events before `AlarmPersisterImpl` persists them as alarms.

`JdbcEventUtil.expandParms()` (inherited from `AbstractEventUtil`) is correct and ready — but it's never called with the template text because the templates aren't on the event in the first place.

## What Needs Investigation

### 1. Where does event template expansion happen in the pipeline?

In classic OpenNMS, the flow is:
```
Event arrives → Eventd EventExpander applies <logmsg>/<descr> from eventconf → EventUtil.expandParms() resolves %tokens% → event stored/forwarded
```

In Delta-V, Eventd is eliminated. Each daemon has its own `EventConfEnrichmentService` that loads alarm-data (reduction keys, severity, alarm-type) from the `events_enrichment` table. But does it also apply `<logmsg>` and `<descr>` templates?

**Check these files:**
- `core/daemon-common/src/main/java/org/deltav/core/daemon/common/EventConfEnrichmentService.java` — does it set logmsg/descr on the event XML?
- `core/daemon-common/src/main/java/org/deltav/core/daemon/common/KafkaEventTransportConfiguration.java` — the event enrichment pipeline
- The horizon JAR's `EventExpander` class — is it wired in any daemon?
- `AlarmPersisterImpl` in the horizon JAR — where does it read logmsg/descr from?

### 2. What's in the events_enrichment table?

```sql
docker compose exec -T -e PGPASSWORD=opennms postgres \
  psql -U opennms -d opennms -c "SELECT column_name FROM information_schema.columns WHERE table_name = 'events_enrichment' ORDER BY ordinal_position"
```

Does it store `logmsg` and `descr` templates, or only alarm-data fields?

### 3. What does the Kafka event XML look like?

Check actual event XML on the `opennms-fault-events` Kafka topic — do events have `<logmsg>` and `<descr>` elements, or are they missing?

```bash
docker exec delta-v-alarmd /bin/sh -c "cat /tmp/last-event.xml" 2>/dev/null
```

Or consume directly from Kafka:
```bash
docker exec delta-v-kafka-1 kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic opennms-fault-events --from-beginning --max-messages 3 --timeout-ms 5000
```

## What Needs To Be Done

Based on the investigation, likely one of:

**Option A:** `EventConfEnrichmentService` needs to apply `<logmsg>` and `<descr>` templates from the event configuration to the event XML before publishing to Kafka. This would mean enriching the event with these fields alongside alarm-data.

**Option B:** `AlarmPersisterImpl` (in Alarmd) needs to look up the event definition's `<logmsg>`/`<descr>` and call `EventUtil.expandParms()` itself, since the event on Kafka doesn't carry these fields.

**Option C:** Wire the horizon `EventExpander` class into the event publishing path in each daemon.

## After Fixing

### Add E2E assertions

Add to `test-minion-e2e.sh` after the alarm creation check:

```bash
# Verify alarm description is expanded (not empty, no raw %tokens%)
ALARM_LOGMSG=$(psql_query "SELECT logmsg FROM alarms WHERE eventuei = 'uei.opennms.org/translator/traps/SNMP_Link_Down' AND logmsg IS NOT NULL AND logmsg != '' LIMIT 1")
if [ -n "$ALARM_LOGMSG" ]; then
  if echo "$ALARM_LOGMSG" | grep -q '%nodelabel%'; then
    fail "Alarm logmsg contains unexpanded %nodelabel% token"
  else
    pass "Alarm logmsg expanded: $ALARM_LOGMSG"
  fi
else
  fail "Alarm logmsg is empty — event template expansion not working"
fi
```

### Cherry-pick NMS-19631 into delta-v-horizon

After the expansion pipeline is working, pick `30293ef543b` (ifAlias event parameter expansion) into delta-v-horizon. This adds `getIfAliasByNodeAndIfIndex(nodeId, ifIndex)` which is more reliable for SNMP trap events that have ifIndex but not always an IP address. `JdbcEventUtil` will need the new method added.

## Key Files

- `core/daemon-common/src/main/java/org/deltav/core/daemon/common/EventConfEnrichmentService.java`
- `core/daemon-common/src/main/java/org/deltav/core/daemon/common/JdbcEventUtil.java` (new, PR #135)
- `core/daemon-common/src/main/java/org/deltav/core/daemon/common/KafkaEventTransportConfiguration.java`
- `core/daemon-boot-alarmd/src/main/java/org/deltav/netmgt/alarmd/boot/AlarmdConfiguration.java`
- Horizon JAR: `org.opennms.netmgt.alarmd.AlarmPersisterImpl`
- Horizon JAR: `org.opennms.netmgt.eventd.EventExpander` (if it exists separately)

## Important Reminders

- **Never create PRs against OpenNMS/opennms** — always `--repo pbrane/delta-v`
- **Always git pull develop** before creating feature branches
- **Package policy:** `org.deltav` for new code, `org.opennms` for blatant copies or horizon-compat-required
- **Copyright:** BeaconStrategists for `org.deltav`, dual header for horizon-derived
