# PR3: Switch Delta-V to External Horizon JARs — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace ~660 in-reactor horizon modules with pre-built JARs from GitHub Packages, leaving only ~20 Delta-V-authored modules in the `pbrane/delta-v` reactor. Target: <2 min clean build.

**Architecture:** Write a new slim root POM (~150 lines) inheriting from `spring-boot-starter-parent:4.0.3` and importing the published `org.opennms:opennms:${deltav.horizon.version}` as a BOM for all horizon dependency management. Re-parent all 20 Delta-V modules to the new root. Delete all horizon source directories. Version: `0.0.1-SNAPSHOT`.

**Tech Stack:** Maven, Spring Boot 4.0.3, GitHub Packages Maven registry, GitHub Actions CI.

**Spec reference:** `docs/plans/2026-04-05-horizon-extraction-spec.md` (PR3 section)

**PR2 output:** `pbrane/delta-v-horizon` repo published 675 modules as `org.opennms:*:1.0.3` (pending CI confirmation) to GitHub Packages.

---

## Adaptations for this plan

1. **TDD does not directly apply.** This is POM restructuring and directory deletion. The "test" is `mvn install -DskipTests` + E2E suite.
2. **BOM import strategy.** Instead of listing ~220 horizon deps individually, we import the horizon root POM (`org.opennms:opennms:${deltav.horizon.version}`) as a Maven BOM. This works because Maven resolves `<type>pom</type><scope>import</scope>` from the published POM artifact.
3. **Spring Boot parent + horizon BOM precedence.** `spring-boot-starter-parent` (actual parent) > horizon BOM (imported). Spring Boot 4.0.3's managed versions for Jackson, SLF4J, Kafka, etc. automatically override the horizon POM's older versions.

---

## Pre-Task Setup

```bash
cd /Users/david/development/src/opennms/delta-v
git checkout develop
git pull origin develop
git checkout -b pr3/horizon-jars
```

Verify `deltav.horizon.version` by confirming publish CI passed:
```bash
gh run list --repo pbrane/delta-v-horizon --limit 1
# Expect: completed success for 1.0.3 tag
```

Verify local `~/.m2/settings.xml` has GitHub Packages auth:
```xml
<servers>
  <server>
    <id>github-deltav-horizon</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_PAT_WITH_PACKAGES_READ</password>
  </server>
</servers>
```

---

## Delta-V Modules (20 total — these STAY)

All in `core/`:

| # | Directory | ArtifactId |
|---|-----------|-----------|
| 1 | `core/daemon-boot-alarmd` | `org.opennms.core.daemon-boot-alarmd` |
| 2 | `core/daemon-boot-bsmd` | `org.opennms.core.daemon-boot-bsmd` |
| 3 | `core/daemon-boot-collectd` | `org.opennms.core.daemon-boot-collectd` |
| 4 | `core/daemon-boot-discovery` | `org.opennms.core.daemon-boot-discovery` |
| 5 | `core/daemon-boot-enlinkd` | `org.opennms.core.daemon-boot-enlinkd` |
| 6 | `core/daemon-boot-eventtranslator` | `org.opennms.core.daemon-boot-eventtranslator` |
| 7 | `core/daemon-boot-minion` | `org.opennms.core.daemon-boot-minion` |
| 8 | `core/daemon-boot-minion-common` | `org.opennms.core.daemon-boot-minion-common` |
| 9 | `core/daemon-boot-perspectivepollerd` | `org.opennms.core.daemon-boot-perspectivepollerd` |
| 10 | `core/daemon-boot-pollerd` | `org.opennms.core.daemon-boot-pollerd` |
| 11 | `core/daemon-boot-provisiond` | `org.opennms.core.daemon-boot-provisiond` |
| 12 | `core/daemon-boot-syslogd` | `org.opennms.core.daemon-boot-syslogd` |
| 13 | `core/daemon-boot-telemetryd` | `org.opennms.core.daemon-boot-telemetryd` |
| 14 | `core/daemon-boot-trapd` | `org.opennms.core.daemon-boot-trapd` |
| 15 | `core/daemon-common` | `org.opennms.core.daemon-common` |
| 16 | `core/daemon-registry` | `org.opennms.core.daemon-registry` |
| 17 | `core/daemon-sink-kafka` | `org.opennms.core.daemon-sink-kafka` |
| 18 | `core/dao-jpa-support` | `org.opennms.core.dao-jpa-support` |
| 19 | `core/db-init` | `org.opennms.core.db-init` |
| 20 | `core/event-forwarder-kafka` | `org.opennms.core.event-forwarder-kafka` |
| 21 | `core/opennms-model-jakarta` | `org.opennms.core.model-jakarta` |

Note: 21 modules (opennms-model-jakarta was originally listed as 20 but is confirmed as Delta-V-authored).

---

## Task 1: Create Feature Branch

**Files:** none

- [ ] **Step 1: Create branch from develop**

```bash
cd /Users/david/development/src/opennms/delta-v
git checkout develop
git pull origin develop
git checkout -b pr3/horizon-jars
```

- [ ] **Step 2: Verify clean state**

```bash
git status --short  # expect: only untracked planning docs
```

---

## Task 2: Write the Slim Root POM

**Files:**
- Modify: `pom.xml` (replace 5,455-line root POM with ~200-line slim parent)

This is the core of PR3. The new POM:
- Inherits from `spring-boot-starter-parent:4.0.3` (provides ~1,800 managed deps)
- Imports horizon BOM (provides ~220 `org.opennms:*` + ~200 third-party managed deps)
- Declares 21 modules
- Configures 6 build plugins (surefire, failsafe, compiler, resources, enforcer, deploy)
- Adds GitHub Packages + opennms-repo repositories

- [ ] **Step 1: Back up current pom.xml**

```bash
cp pom.xml pom.xml.bak
```

- [ ] **Step 2: Write the new slim root POM**

Replace `pom.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.3</version>
    <relativePath/>
  </parent>

  <groupId>org.opennms</groupId>
  <artifactId>delta-v-parent</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <packaging>pom</packaging>
  <name>Delta-V Parent</name>
  <description>Delta-V microservice decomposition of OpenNMS Horizon</description>
  <url>https://github.com/pbrane/delta-v</url>

  <licenses>
    <license>
      <name>GNU Affero General Public License</name>
      <url>https://www.gnu.org/licenses/agpl-3.0.txt</url>
    </license>
  </licenses>

  <scm>
    <connection>scm:git:https://github.com/pbrane/delta-v.git</connection>
    <developerConnection>scm:git:https://github.com/pbrane/delta-v.git</developerConnection>
    <url>https://github.com/pbrane/delta-v</url>
  </scm>

  <!-- ============================================================ -->
  <!-- Properties                                                    -->
  <!-- ============================================================ -->
  <properties>
    <java.version>21</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>

    <!-- Horizon JAR version (published by pbrane/delta-v-horizon) -->
    <deltav.horizon.version>1.0.3</deltav.horizon.version>

    <!-- Plugin versions not inherited from spring-boot-starter-parent -->
    <maven-enforcer-plugin.version>3.4.1</maven-enforcer-plugin.version>
  </properties>

  <!-- ============================================================ -->
  <!-- Modules                                                       -->
  <!-- ============================================================ -->
  <modules>
    <!-- Shared infrastructure -->
    <module>core/daemon-common</module>
    <module>core/daemon-registry</module>
    <module>core/daemon-sink-kafka</module>
    <module>core/dao-jpa-support</module>
    <module>core/event-forwarder-kafka</module>
    <module>core/opennms-model-jakarta</module>

    <!-- Daemon boots (Spring Boot fat JARs) -->
    <module>core/daemon-boot-alarmd</module>
    <module>core/daemon-boot-bsmd</module>
    <module>core/daemon-boot-collectd</module>
    <module>core/daemon-boot-discovery</module>
    <module>core/daemon-boot-enlinkd</module>
    <module>core/daemon-boot-eventtranslator</module>
    <module>core/daemon-boot-minion-common</module>
    <module>core/daemon-boot-minion</module>
    <module>core/daemon-boot-perspectivepollerd</module>
    <module>core/daemon-boot-pollerd</module>
    <module>core/daemon-boot-provisiond</module>
    <module>core/daemon-boot-syslogd</module>
    <module>core/daemon-boot-telemetryd</module>
    <module>core/daemon-boot-trapd</module>

    <!-- Standalone tools -->
    <module>core/db-init</module>
  </modules>

  <!-- ============================================================ -->
  <!-- Repositories                                                  -->
  <!-- ============================================================ -->
  <repositories>
    <repository>
      <id>github-deltav-horizon</id>
      <url>https://maven.pkg.github.com/pbrane/delta-v-horizon</url>
      <snapshots><enabled>false</enabled></snapshots>
    </repository>
    <repository>
      <id>opennms-repo</id>
      <url>https://maven.opennms.org/repository/everything/</url>
      <snapshots><enabled>false</enabled></snapshots>
    </repository>
  </repositories>

  <!-- ============================================================ -->
  <!-- Dependency Management                                         -->
  <!-- ============================================================ -->
  <!-- Priority: spring-boot-starter-parent (actual parent) > horizon BOM (import).
       Spring Boot 4.0.3 versions for Jackson, SLF4J, Kafka, etc. automatically
       override the horizon POM's older values. -->
  <dependencyManagement>
    <dependencies>
      <!-- Horizon BOM — all org.opennms:* deps + third-party managed deps -->
      <dependency>
        <groupId>org.opennms</groupId>
        <artifactId>opennms</artifactId>
        <version>${deltav.horizon.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>

      <!-- Delta-V internal modules (version managed here, not in horizon BOM) -->
      <dependency>
        <groupId>org.opennms.core</groupId>
        <artifactId>org.opennms.core.daemon-common</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>org.opennms.core</groupId>
        <artifactId>org.opennms.core.daemon-registry</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>org.opennms.core</groupId>
        <artifactId>org.opennms.core.daemon-sink-kafka</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>org.opennms.core</groupId>
        <artifactId>org.opennms.core.dao-jpa-support</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>org.opennms.core</groupId>
        <artifactId>org.opennms.core.event-forwarder-kafka</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>org.opennms.core</groupId>
        <artifactId>org.opennms.core.model-jakarta</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>org.opennms.core</groupId>
        <artifactId>org.opennms.core.model-api</artifactId>
        <version>${deltav.horizon.version}</version>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <!-- ============================================================ -->
  <!-- Build                                                         -->
  <!-- ============================================================ -->
  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <artifactId>maven-enforcer-plugin</artifactId>
          <version>${maven-enforcer-plugin.version}</version>
        </plugin>
      </plugins>
    </pluginManagement>

    <plugins>
      <!-- Compiler -->
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <release>${java.version}</release>
          <encoding>${project.build.sourceEncoding}</encoding>
        </configuration>
      </plugin>

      <!-- Resources: custom delimiter for property filtering -->
      <plugin>
        <artifactId>maven-resources-plugin</artifactId>
        <configuration>
          <encoding>${project.build.sourceEncoding}</encoding>
          <escapeString>\</escapeString>
          <delimiters>
            <delimiter>${*}</delimiter>
          </delimiters>
          <useDefaultDelimiters>false</useDefaultDelimiters>
        </configuration>
      </plugin>

      <!-- Surefire: unit tests -->
      <plugin>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <argLine>
            --add-opens java.base/java.lang=ALL-UNNAMED
            --add-opens java.desktop/java.beans=ALL-UNNAMED
            --add-opens java.base/java.io=ALL-UNNAMED
            --add-opens java.base/java.lang.reflect=ALL-UNNAMED
            --add-opens java.base/java.math=ALL-UNNAMED
            --add-opens java.base/java.util=ALL-UNNAMED
            --add-opens java.base/java.util.concurrent=ALL-UNNAMED
            --add-opens java.base/java.util.regex=ALL-UNNAMED
            --add-opens java.base/java.net=ALL-UNNAMED
            --add-opens java.base/java.text=ALL-UNNAMED
            --add-opens java.base/sun.util.locale.provider=ALL-UNNAMED
            --add-opens java.sql/java.sql=ALL-UNNAMED
          </argLine>
          <systemPropertyVariables>
            <java.awt.headless>true</java.awt.headless>
            <java.locale.providers>CLDR,COMPAT</java.locale.providers>
            <mock.debug>false</mock.debug>
            <mock.rundbtests>true</mock.rundbtests>
            <mock.leaveDatabase>false</mock.leaveDatabase>
            <mock.leaveDatabaseOnFailure>false</mock.leaveDatabaseOnFailure>
            <mock.db.driver>org.postgresql.Driver</mock.db.driver>
            <mock.db.url>jdbc:postgresql://localhost:5432/</mock.db.url>
            <mock.db.adminUser>postgres</mock.db.adminUser>
            <mock.db.adminPassword>postgres</mock.db.adminPassword>
          </systemPropertyVariables>
          <forkCount>1</forkCount>
          <reuseForks>false</reuseForks>
          <runOrder>alphabetical</runOrder>
          <shutdown>kill</shutdown>
          <useModulePath>false</useModulePath>
          <forkedProcessExitTimeoutInSeconds>120</forkedProcessExitTimeoutInSeconds>
        </configuration>
        <dependencies>
          <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <version>${junit-jupiter.version}</version>
          </dependency>
          <dependency>
            <groupId>org.junit.vintage</groupId>
            <artifactId>junit-vintage-engine</artifactId>
            <version>${junit-jupiter.version}</version>
          </dependency>
        </dependencies>
      </plugin>

      <!-- Failsafe: integration tests -->
      <plugin>
        <artifactId>maven-failsafe-plugin</artifactId>
        <configuration>
          <systemPropertyVariables>
            <java.locale.providers>CLDR,COMPAT</java.locale.providers>
          </systemPropertyVariables>
          <forkCount>1</forkCount>
          <reuseForks>false</reuseForks>
          <useModulePath>false</useModulePath>
          <forkedProcessTimeoutInSeconds>1800</forkedProcessTimeoutInSeconds>
        </configuration>
        <executions>
          <execution>
            <goals>
              <goal>integration-test</goal>
              <goal>verify</goal>
            </goals>
          </execution>
        </executions>
      </plugin>

      <!-- Enforcer -->
      <plugin>
        <artifactId>maven-enforcer-plugin</artifactId>
        <executions>
          <execution>
            <id>enforce-versions</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
              <rules>
                <requireMavenVersion>
                  <version>[3.9,)</version>
                </requireMavenVersion>
                <requireJavaVersion>
                  <version>[21,)</version>
                </requireJavaVersion>
              </rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Verify POM is well-formed**

```bash
xmllint --noout pom.xml && echo "XML valid"
```

- [ ] **Step 4: Commit**

```bash
git add pom.xml pom.xml.bak
git commit -m "build: write slim delta-v-parent POM (spring-boot-starter-parent + horizon BOM import)"
```

---

## Task 3: Re-parent All 21 Delta-V Modules

**Files:** Modify `<parent>` block in all 21 `core/*/pom.xml` files

Each module currently has:
```xml
<parent>
    <groupId>org.opennms</groupId>
    <artifactId>org.opennms.core</artifactId>
    <version>36.0.0-SNAPSHOT</version>
</parent>
```

Change to:
```xml
<parent>
    <groupId>org.opennms</groupId>
    <artifactId>delta-v-parent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
</parent>
```

Exception: `db-init` currently inherits from `spring-boot-starter-parent:4.0.3`. Change to:
```xml
<parent>
    <groupId>org.opennms</groupId>
    <artifactId>delta-v-parent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
</parent>
```
(db-init now inherits Spring Boot transitively through delta-v-parent)

- [ ] **Step 1: Re-parent all 21 modules**

For each module in the Delta-V Modules table above, edit `core/<module>/pom.xml`:
- Replace the `<parent>` block with the new delta-v-parent reference
- Update `<version>` from `36.0.0-SNAPSHOT` to `0.0.1-SNAPSHOT` (if explicitly declared)

Use a script:
```bash
cd /Users/david/development/src/opennms/delta-v
DELTAV_MODULES="daemon-common daemon-registry daemon-sink-kafka dao-jpa-support event-forwarder-kafka opennms-model-jakarta daemon-boot-alarmd daemon-boot-bsmd daemon-boot-collectd daemon-boot-discovery daemon-boot-enlinkd daemon-boot-eventtranslator daemon-boot-minion-common daemon-boot-minion daemon-boot-perspectivepollerd daemon-boot-pollerd daemon-boot-provisiond daemon-boot-syslogd daemon-boot-telemetryd daemon-boot-trapd db-init"

for mod in $DELTAV_MODULES; do
  echo "Re-parenting core/$mod..."
  # This needs manual editing per module — the parent block structure varies
done
```

Each module's parent block must be individually edited (some use 4-space indent, some 2-space; some have `<relativePath>`, some don't). Use the Edit tool for each.

- [ ] **Step 2: Remove per-module Spring Boot BOM imports**

Currently, each daemon-boot module imports the Spring Boot BOM in its own `<dependencyManagement>`. Since the parent now inherits from `spring-boot-starter-parent`, these are redundant. Remove the `<dependencyManagement>` section importing `spring-boot-dependencies` from each module that has it.

Also remove per-module version overrides for logback, SLF4J, Jackson, and jboss-logging that each module currently pins — these are now handled by the parent's Spring Boot version.

- [ ] **Step 3: Update per-module dependency versions**

Dependencies on horizon modules currently use `${project.version}` (36.0.0-SNAPSHOT). They must NOT use `${project.version}` anymore (that would resolve to 0.0.1-SNAPSHOT). Instead:
- Dependencies on OTHER Delta-V modules: use `${project.version}` (correct — both are 0.0.1-SNAPSHOT)
- Dependencies on horizon modules (`org.opennms:*` published in delta-v-horizon): remove explicit `<version>` — let the horizon BOM manage it

How to tell which is which: if the dependency's artifactId is in the Delta-V Modules table above, it's Delta-V (use `${project.version}`). Everything else is horizon (no explicit version).

- [ ] **Step 4: Verify quick validate pass**

```bash
./mvnw validate 2>&1 | tail -10
```

Expected: `BUILD SUCCESS` (POM parsing only, no compilation).

- [ ] **Step 5: Commit**

```bash
git add core/*/pom.xml
git commit -m "build: re-parent 21 Delta-V modules to delta-v-parent"
```

---

## Task 4: Delete Horizon Source Directories

**Files:** Delete ~660 module directories

- [ ] **Step 1: Delete horizon directories**

```bash
cd /Users/david/development/src/opennms/delta-v

# Top-level horizon directories
rm -rf dependencies/
rm -rf features/
rm -rf integrations/
rm -rf protocols/
rm -rf tests/
rm -rf ui/
rm -rf container/
rm -rf integration-tests/
rm -rf deploy/

# Old-structure horizon modules
rm -rf opennms-alarms/
rm -rf opennms-config/
rm -rf opennms-config-api/
rm -rf opennms-config-dao/
rm -rf opennms-config-jaxb/
rm -rf opennms-config-model/
rm -rf opennms-correlation/
rm -rf opennms-dao/
rm -rf opennms-dao-api/
rm -rf opennms-dao-mock/
rm -rf opennms-icmp/
rm -rf opennms-javamail/
rm -rf opennms-model/
rm -rf opennms-provision/
rm -rf opennms-rrd/
rm -rf opennms-taglib/
rm -rf opennms-util/
rm -rf opennms-web-api/
rm -rf opennms-web-dependencies/
rm -rf opennms-webapp/
rm -rf opennms-webapp-rest/
rm -rf opennms-wmi/

# Horizon core modules (keep only Delta-V modules in core/)
cd core/
# List what to keep
KEEP="daemon-common daemon-registry daemon-sink-kafka dao-jpa-support event-forwarder-kafka opennms-model-jakarta daemon-boot-alarmd daemon-boot-bsmd daemon-boot-collectd daemon-boot-discovery daemon-boot-enlinkd daemon-boot-eventtranslator daemon-boot-minion-common daemon-boot-minion daemon-boot-perspectivepollerd daemon-boot-pollerd daemon-boot-provisiond daemon-boot-syslogd daemon-boot-telemetryd daemon-boot-trapd db-init"
# Delete everything else in core/ except the keepers
for dir in */; do
  dir="${dir%/}"
  if ! echo "$KEEP" | grep -qw "$dir"; then
    echo "Deleting core/$dir"
    rm -rf "$dir"
  fi
done
# Delete core/pom.xml (aggregator no longer needed — root POM lists modules directly)
rm -f pom.xml
cd ..

# Legacy build infrastructure
rm -f compile.pl assemble.pl runtests.sh
rm -rf maven/
rm -rf smoke-test/
rm -rf opennms-container/core/ opennms-container/daemon/ opennms-container/minion/ opennms-container/sentinel/
```

- [ ] **Step 2: Verify only Delta-V modules remain**

```bash
ls core/
# Expect: 21 directories (daemon-boot-*, daemon-common, daemon-registry, etc.)

ls core/ | wc -l
# Expect: 21

# Verify no horizon directories remain
ls features/ 2>&1  # expect: No such file
ls dependencies/ 2>&1  # expect: No such file
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "build: delete horizon source directories (now consumed as JARs from GitHub Packages)"
```

---

## Task 5: Update CI Workflow for GitHub Packages Auth

**Files:** Modify `.github/workflows/delta-v-build-images.yml`

The CI workflow needs to authenticate to GitHub Packages during Maven dependency resolution (to pull horizon JARs).

- [ ] **Step 1: Add Maven settings.xml generation step**

Add before the "Compile daemon-boot modules" step in `delta-v-build-images.yml`:

```yaml
      - name: Configure Maven for GitHub Packages
        run: |
          mkdir -p ~/.m2
          cat > ~/.m2/settings.xml << 'SETTINGS'
          <settings>
            <servers>
              <server>
                <id>github-deltav-horizon</id>
                <username>${env.GITHUB_ACTOR}</username>
                <password>${env.GITHUB_TOKEN}</password>
              </server>
            </servers>
          </settings>
          SETTINGS
        env:
          GITHUB_ACTOR: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

- [ ] **Step 2: Update compile commands**

The current CI uses `./mvnw` which requires the Maven wrapper. After deleting `maven/`, we may need to update. If `mvnw` still works (it downloads Maven), keep it. Otherwise switch to `mvn` from `setup-java`.

Also update the module list — the `DAEMON_BOOTS` variable should match the new reactor:

```yaml
      - name: Compile all modules
        run: ./mvnw -B -DskipTests install
```

(With only 21 modules, we can build the entire reactor instead of listing individual daemon-boots.)

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/delta-v-build-images.yml
git commit -m "ci: add GitHub Packages auth for horizon JAR resolution"
```

---

## Task 6: Update Makefile

**Files:** Modify `Makefile`

- [ ] **Step 1: Simplify Makefile targets**

The current Makefile has targets for the full ~704-module reactor. Update:
- `build` target: `./mvnw -DskipTests install` (builds all 21 modules)
- `test` target: `./mvnw verify`
- Remove/update targets that reference deleted directories
- Remove `module` and `dependents` targets (reactor is flat now)

- [ ] **Step 2: Commit**

```bash
git add Makefile
git commit -m "build: simplify Makefile for 21-module reactor"
```

---

## Task 7: Verify Local Build

**Files:** none modified

- [ ] **Step 1: Clear .m2 cache for Delta-V modules**

```bash
rm -rf ~/.m2/repository/org/opennms/core/org.opennms.core.daemon-*
rm -rf ~/.m2/repository/org/opennms/core/org.opennms.core.dao-jpa-support
rm -rf ~/.m2/repository/org/opennms/core/org.opennms.core.event-forwarder-kafka
rm -rf ~/.m2/repository/org/opennms/core/org.opennms.core.model-jakarta
rm -rf ~/.m2/repository/org/opennms/core/org.opennms.core.db-init
```

- [ ] **Step 2: Build all modules**

```bash
./mvnw -DskipTests install 2>&1 | tee /tmp/pr3-build.log | tail -20
```

Expected: `BUILD SUCCESS` for 21 modules in <2 min.

- [ ] **Step 3: Diagnose and fix any dependency resolution failures**

If a horizon dep can't resolve from GitHub Packages:
1. Check the artifact exists: `./mvnw dependency:get -Dartifact=<groupId>:<artifactId>:${deltav.horizon.version}`
2. Check `~/.m2/settings.xml` has the server entry for `github-deltav-horizon`
3. Check the `<repository>` id in `pom.xml` matches the `<server>` id in `settings.xml`

- [ ] **Step 4: Record build time**

```bash
grep "Total time" /tmp/pr3-build.log
grep -c "Building " /tmp/pr3-build.log
```

Expected: ~21 modules, <2 min.

---

## Task 8: Build Docker Images and Run E2E Tests

**Files:** none modified

- [ ] **Step 1: Build all daemon JARs**

```bash
cd opennms-container/delta-v
./build.sh deltav
```

- [ ] **Step 2: Start the stack**

```bash
./deploy.sh up lite
./deploy.sh status  # wait for all daemons healthy
```

- [ ] **Step 3: Run E2E suite**

```bash
./test-e2e.sh
./test-minion-e2e.sh
./test-syslog-e2e.sh
./test-passive-e2e.sh
./test-collectd-e2e.sh
./test-enlinkd-e2e.sh
./test-minion-rpc-e2e.sh
```

Expected: 93/93 + Minion RPC canary pass.

- [ ] **Step 4: Tear down**

```bash
./deploy.sh down
```

---

## Task 9: Create PR

**Files:** none

- [ ] **Step 1: Push branch**

```bash
git push -u origin pr3/horizon-jars
```

- [ ] **Step 2: Create PR**

```bash
gh pr create --repo pbrane/delta-v --base develop --title "build: switch to external horizon JARs from GitHub Packages" --body "$(cat <<'EOF'
## Summary

- Replaces ~660 in-reactor horizon modules with pre-built JARs from `pbrane/delta-v-horizon` (GitHub Packages)
- New slim root POM (~150 lines) inheriting from `spring-boot-starter-parent:4.0.3`
- Horizon BOM import provides all `org.opennms:*` dependency management
- Delta-V version: `0.0.1-SNAPSHOT` (21 modules)
- Horizon version: `${deltav.horizon.version}` = `1.0.3`

### Build metrics
- Modules: 21 (down from ~704)
- Clean build time: <2 min (down from ~14 min)
- Docker image build: unchanged

## Test plan
- [ ] `mvn install -DskipTests` passes (21 modules)
- [ ] Docker images build and daemons start
- [ ] E2E suite: 93/93 pass
- [ ] Minion RPC canary: pass
EOF
)"
```

---

## Verification Summary

| Check | Command | Expected |
|-------|---------|----------|
| Module count | `./mvnw validate 2>&1 \| grep "Building" \| wc -l` | 21 |
| Build time | `./mvnw -DskipTests install` | <2 min |
| No horizon dirs | `ls features/ 2>&1` | "No such file" |
| Horizon JARs resolve | `./mvnw dependency:tree -f core/daemon-boot-collectd/pom.xml` | All org.opennms:*:1.0.3 |
| Docker build | `./build.sh deltav` | All 14 images |
| E2E tests | Full suite | 93/93 |
| Version | `grep '<version>' pom.xml \| head -1` | `0.0.1-SNAPSHOT` |
