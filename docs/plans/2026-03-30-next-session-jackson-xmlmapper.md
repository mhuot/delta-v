# Next Session: Replace JaxbUtils with Jackson XmlMapper in DaemonEventConfDao

---

## Context

Branch: `chore/karaf-removal-phase1` (worktree at `/Users/david/development/src/opennms/delta-v-karaf-removal`)
PR: pbrane/delta-v#84

`DaemonEventConfDao.loadEventsFromDB()` currently uses `JaxbUtils.unmarshal(Event.class, xmlContent)` which fails at runtime because EclipseLink MOXy (the JAXB implementation) does package scanning that triggers `ClassNotFoundException: ResourceTypeUtils` from opennms-model.

## The Fix

Replace JaxbUtils with Jackson XmlMapper. Jackson is already on every daemon classpath via Spring Boot 4.

### In DaemonEventConfDao.java

```java
// Replace:
import org.opennms.core.xml.JaxbUtils;

// With:
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

// Add field:
private static final XmlMapper XML_MAPPER = new XmlMapper();
static {
    XML_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}

// Replace the unmarshal call:
// Old: Event event = JaxbUtils.unmarshal(Event.class, xmlContent);
// New: Event event = XML_MAPPER.readValue(xmlContent, Event.class);
```

### Dependencies

Check if `jackson-dataformat-xml` is already on the daemon-common classpath (likely via Spring Boot). If not, add:
```xml
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-xml</artifactId>
</dependency>
```

### Potential issues

1. The eventconf `Event` class (in opennms-config-model) has JAXB annotations (`@XmlRootElement`, `@XmlElement`). Jackson's XmlMapper can read JAXB annotations if `jackson-module-jaxb-annotations` is on the classpath. Alternatively, Jackson can use field names directly with `FAIL_ON_UNKNOWN_PROPERTIES = false`.

2. Test by checking that a sample eventconf XML string round-trips correctly through `XmlMapper.readValue()`.

### After fix

1. Remove the `opennms-config-model` dependency from daemon-common if JaxbUtils was the only reason it was needed (check other imports)
2. Remove `jaxb-api`, `jakarta.xml.bind-api`, `jaxb-runtime` if no longer needed
3. Rebuild: `make build`
4. Docker: `build.sh deltav` + `deploy.sh up full`
5. Health check all 12 daemons
6. Run E2E tests
7. Push and update PR #84

### Also

- The same fix should be applied to `EventConfEnrichmentService` on the `develop` branch (merged from PR #81) since it has the same JaxbUtils call path
- The `fix/daemon-startup-develop` branch was merged but the Docker E2E was never verified
