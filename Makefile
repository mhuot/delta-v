##
# Makefile to build Delta-V (OpenNMS fork) from source
#
# Adapted from Bluebird Community (bbc-opennms) Makefile.
# Provides standardized build targets that work identically in CI and locally.
##
.DEFAULT_GOAL := quick-build

SHELL                 := /bin/bash -o nounset -o pipefail -o errexit
WORKING_DIRECTORY     := $(shell pwd)
SITE_FILE             := antora-playbook-local.yml
ARTIFACTS_DIR         := target/artifacts
MAVEN_SHARDS          := 1
MAVEN_SHARD_IDX       := 0
MAVEN_BIN             := maven/bin/mvn
MAVEN_ARGS            := --batch-mode -DupdatePolicy=never -Djava.awt.headless=true -Daether.connector.resumeDownloads=false -Daether.connector.basic.threads=1 -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -DvaadinJavaMaxMemory=2g -DmaxCpus=8 -Dstyle.color=always -Djdk.util.zip.disableZip64ExtraFieldValidation=true -Dmaven.wagon.http.retryHandler.count=3 -Dfailsafe.rerunFailingTestsCount=2 -Dsurefire.rerunFailingTestsCount=2
export MAVEN_OPTS     := -XX:+UseG1GC -XX:InitialRAMPercentage=75.0 -XX:MaxRAMPercentage=75.0 -XX:ReservedCodeCacheSize=1g -XX:+TieredCompilation -XX:TieredStopAtLevel=1 -XX:-UseGCOverheadLimit -XX:-MaxFDLimit -XX:MaxGCPauseMillis=200

GIT_BRANCH            := $(shell git rev-parse --abbrev-ref HEAD)
OPENNMS_VERSION       ?= $(shell sed -n '/<version>/{s/.*<version>\(.*\)<\/version>.*/\1/p;q;}' pom.xml)
VERSION               := $(shell echo ${OPENNMS_VERSION} | sed -e 's,-SNAPSHOT,,')
RELEASE_BRANCH        := $(shell echo ${GIT_BRANCH} | sed -e 's,/,-,g')
ifndef GITHUB_RUN_NUMBER
override RELEASE_BUILD_NUM = 0
endif

RELEASE_BUILD_NUM     ?= ${GITHUB_RUN_NUMBER}
RELEASE_COMMIT        := $(shell git rev-parse --short HEAD)
OPEN_FILES_LIMIT      := 20000
CURRENT_FILES_LIMIT   := $(shell ulimit -n 2>/dev/null || echo 0)
RELEASE_VERSION       := UNSET.0.0
RELEASE_BRANCH        := develop
PUSH_RELEASE          := false
MAJOR_VERSION         := $(shell echo $(RELEASE_VERSION) | cut -d. -f1)
MINOR_VERSION         := $(shell echo $(RELEASE_VERSION) | cut -d. -f2)
PATCH_VERSION         := $(shell echo $(RELEASE_VERSION) | cut -d. -f3)
SNAPSHOT_VERSION      := $(MAJOR_VERSION).$(MINOR_VERSION).$(shell expr $(PATCH_VERSION) + 1)-SNAPSHOT
RELEASE_LOG           := target/release.log
OK                    := "[ OK ]"
FAILED                := "[ FAILED ]"
SKIP                  := "[ SKIP ]"
SKIP_UI_TESTS         := true
JAVA_MAJOR_VERSION    := 17

# Package requirements
PKG_CORE_HOME         := /opt/opennms
PKG_CORE_RRD          := /var/lib/opennms/rrd
PKG_CORE_REPORTS      := /var/lib/opennms/reports
PKG_CORE_LOGS         := /var/log/opennms
PKG_CORE_DEPLOY       := /var/lib/opennms/deploy

PKG_MINION_HOME       := /opt/minion
PKG_MINION_LOGS       := /var/log/minion
PKG_MINION_DEPLOY     := /var/lib/minion/deploy

PKG_SENTINEL_HOME     := /opt/sentinel
PKG_SENTINEL_LOGS     := /var/log/sentinel
PKG_SENTINEL_DEPLOY   := /var/lib/sentinel/deploy

BUILD_ROOT            := $(ARTIFACTS_DIR)/buildroot
PKG_RELEASE           := $(RELEASE_BUILD_NUM)
MAINTAINER_EMAIL      ?= maintainer@delta-v.dev

INSTALL_VERSION       := ${OPENNMS_VERSION}-${RELEASE_COMMIT}
BUILD_DATE            := $(shell date '+%Y%m%d')
OCI_PLATFORM          := linux/$(shell uname -m)
OCI_REGISTRY          ?= ghcr.io
OCI_REGISTRY_USER     ?= changeme
OCI_REGISTRY_PASSWORD ?= changeme
OCI_REGISTRY_ORG      ?= pbrane
TRIVY_ARGS            := --timeout 30m --format json

DOCKER_ANTORA_IMAGE   := opennms/antora:3.1.4-b10433

define setversion
	@echo -n "Set Maven release version:   "
	@mvn versions:set -DnewVersion=$(1) >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "Set version Karaf Test case: "
	@sed -i.versionsBackup 's/$(OPENNMS_VERSION)/$(1)/g' opennms-full-assembly/src/test/java/org/opennms/assemblies/karaf/OnmsKarafTestCase.java >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "Set version web assets:      "
	@sed -i.versionsBackup 's/$(OPENNMS_VERSION)/$(1)/g' core/web-assets/package.json >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "Set version Antora docs:     "
	@sed -i.versionsBackup 's/$(OPENNMS_VERSION)/$(1)/g' docs/antora.yml >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "Set version Maven deploy:    "
	@cd deploy && mvn versions:set -DnewVersion=$(1) >>../$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "Set version OSGi:            "
	@sed -i.versionsBackup 's/\<opennms\.osgi\.version\>$(VERSION).SNAPSHOT\<\/opennms\.osgi\.version\>/\<opennms\.osgi\.version\>$(1)\<\/opennms\.osgi\.version\>/g' pom.xml >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "Set version smoke-test:      "
	@cd smoke-test && mvn versions:set -DnewVersion=$(1) >>../$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
endef

.PHONY: help
help:
	@echo ""
	@echo "Makefile to build artifacts for Delta-V (OpenNMS fork)"
	@echo ""
	@echo "Requirements to build:"
	@echo "  * OpenJDK 17 Development Kit"
	@echo "  * Maven 3.8.x (shipped with the git repo in maven/)"
	@echo "  * NodeJS with pnpm"
	@echo ""
	@echo "Build targets:"
	@echo "  help:                  Show this help"
	@echo "  validate:              Fail quickly by checking project structure with mvn:clean"
	@echo "  maven-structure-graph: Generate JSON with Maven structure for test class list generation"
	@echo "  test-lists:            Generate lists of JUnit and Integration Test class names for job splitting"
	@echo "  compile:               Compile from source with expensive tasks"
	@echo "  assemble:              Assemble build artifacts for production"
	@echo "  quick-build:           Quick compile and quick assemble for development (default)"
	@echo "  quick-compile:         Quick compile for fast development feedback"
	@echo "  quick-assemble:        Quick assemble to run on a local system"
	@echo "  core-pkg-deb:          Build Core Debian packages"
	@echo "  core-pkg-rpm:          Build Core RPM packages"
	@echo "  minion-pkg-deb:        Build Minion Debian packages"
	@echo "  minion-pkg-rpm:        Build Minion RPM packages"
	@echo "  sentinel-pkg-deb:      Build Sentinel Debian packages"
	@echo "  sentinel-pkg-rpm:      Build Sentinel RPM packages"
	@echo "  all-pkgs:              Build all packages"
	@echo ""
	@echo "Container Images:"
	@echo "  core-oci:              Build container image for Core, tag: local/core:latest"
	@echo "  minion-oci:            Build container image for Minion, tag: local/minion:latest"
	@echo "  sentinel-oci:          Build container image for Sentinel, tag: local/sentinel:latest"
	@echo ""
	@echo "Security & Quality:"
	@echo "  core-oci-sbom:         Create SBOM for Core container image"
	@echo "  minion-oci-sbom:       Create SBOM for Minion container image"
	@echo "  sentinel-oci-sbom:     Create SBOM for Sentinel container image"
	@echo "  core-oci-sec-scan:     Security scan for Core container image"
	@echo "  minion-oci-sec-scan:   Security scan for Minion container image"
	@echo "  sentinel-oci-sec-scan: Security scan for Sentinel container image"
	@echo "  code-coverage:         Test code coverage with SonarScanner CLI"
	@echo ""
	@echo "Test suites:"
	@echo "  quick-smoke:           Simple smoke test to verify the application can start"
	@echo "  core-e2e:              Run end-to-end tests against Core"
	@echo "  minion-e2e:            Run end-to-end tests against Minion"
	@echo "  sentinel-e2e:          Run end-to-end tests against Sentinel"
	@echo "  unit-tests:            Run unit test suite (supports sharding via MAVEN_SHARDS/MAVEN_SHARD_IDX)"
	@echo "  integration-tests:     Run integration test suite (supports sharding)"
	@echo "  javadocs:              Generate Java docs"
	@echo ""
	@echo "Documentation:"
	@echo "  docs:                  Build Antora docs with local Antora"
	@echo "  docs-docker:           Build Antora docs with Docker"
	@echo "  docs-serve:            Serve docs locally via Docker/Nginx on port 8080"
	@echo "  docs-serve-stop:       Stop local docs server"
	@echo ""
	@echo "Utility:"
	@echo "  install-core:          Install assembly to $(PKG_CORE_HOME)"
	@echo "  uninstall-core:        Remove installed version from $(PKG_CORE_HOME)"
	@echo "  collect-artifacts:     Collect build artifacts in $(ARTIFACTS_DIR)"
	@echo "  collect-testresults:   Collect test results in $(ARTIFACTS_DIR)/tests"
	@echo "  spinup-postgres:       Spin up PostgreSQL container for integration tests"
	@echo "  destroy-postgres:      Shutdown and destroy PostgreSQL container"
	@echo "  clean:                 Clean assembly and docs"
	@echo "  clean-all:             Clean git repo, docs, M2 artifacts, and assemblies"
	@echo ""

.PHONY: deps-build
deps-build:
	@echo "Check build dependencies: Java JDK, NodeJS, pnpm, paste, python3"
	@echo -n "Check Maven binary:          "
	@command -v $(MAVEN_BIN) > /dev/null
	@echo $(OK)
	@echo -n "Check Java runtime:          "
	@command -v java > /dev/null
	@echo $(OK)
	@echo -n "Check Java compiler:         "
	@command -v javac > /dev/null
	@echo $(OK)
	@echo -n "Check Node Package manager:  "
	@command -v npm > /dev/null 2>&1 && echo $(OK) || echo $(SKIP)
	@echo -n "Check paste binary:          "
	@command -v paste > /dev/null
	@echo $(OK)
	@echo -n "Check Python3:               "
	@command -v python3 > /dev/null
	@echo $(OK)
	@echo -n "Check pnpm:                  "
	@command -v pnpm > /dev/null 2>&1 && echo $(OK) || echo $(SKIP)
	@mkdir -p $(ARTIFACTS_DIR)
	@echo -n "Check Java version $(JAVA_MAJOR_VERSION):       "
	@java -version 2>&1 | grep '$(JAVA_MAJOR_VERSION)\..*' >/dev/null
	@echo $(OK)
	@echo -n "Check file limits ($(OPEN_FILES_LIMIT)):   "
	@if [ "$$(ulimit -n)" -lt "$(OPEN_FILES_LIMIT)" ]; then \
	  echo $(SKIP); \
	  echo "  (file limit is $(CURRENT_FILES_LIMIT), $(OPEN_FILES_LIMIT) recommended)"; \
	else \
	  echo $(OK); \
	fi

.PHONY: deps-packages
deps-packages:
	@echo "Check dependencies to build packages"
	command -v fpm
	command -v rpmbuild

.PHONY: deps-docs
deps-docs:
	@echo "Check documentation build dependency: antora"
	command -v antora

.PHONY: deps-docs-docker
deps-docs-docker:
	@command -v docker

.PHONY: deps-oci
deps-oci:
	@echo "Check OCI build dependency: docker"
	command -v docker
	command -v tar

.PHONY: deps-oci-sbom
deps-oci-sbom:
	@echo "Check OCI SBOM dependency: syft"
	command -v syft

.PHONY: deps-oci-sec-scan
deps-oci-sec-scan:
	@echo "Check OCI security scan dependency: trivy"
	command -v trivy

.PHONY: deps-sonar
deps-sonar:
	@echo "Check code coverage test dependency: sonar-scanner"
	command -v sonar-scanner

.PHONY: show-info
show-info:
	@echo "MAVEN_OPTS=\"$(MAVEN_OPTS)\""
	@echo "MAVEN_ARGS=\"$(MAVEN_ARGS)\""
	@$(MAVEN_BIN) --version

.PHONY: validate
validate: deps-build show-info
	$(MAVEN_BIN) clean
	$(MAVEN_BIN) clean --file opennms-full-assembly/pom.xml -Dbuild.profile=default

.PHONY: maven-structure-graph
maven-structure-graph: deps-build show-info
	$(MAVEN_BIN) org.opennms.maven.plugins:structure-maven-plugin:1.0:structure $(MAVEN_ARGS) -Dbuild.profile=default -Droot.dir=$(WORKING_DIRECTORY) --fail-at-end -Prun-expensive-tasks -Pbuild-bamboo

.PHONY: test-lists
test-lists: maven-structure-graph
	mkdir -p $(ARTIFACTS_DIR)/tests
	python3 .cicd-assets/find-tests/find-tests.py generate-test-lists --changes-only="false" --output-unit-test-classes="$(ARTIFACTS_DIR)/tests/unit_tests_classnames" --output-integration-test-classes="$(ARTIFACTS_DIR)/tests/integration_tests_classnames" .
	cat $(ARTIFACTS_DIR)/tests/*_tests_classnames | python3 .cicd-assets/find-tests/find-tests.py generate-test-modules --output="$(ARTIFACTS_DIR)/tests/test_modules" .
	find smoke-test -type f -regex ".*\/src\/test\/java\/.*IT.*\.java" | sed -e 's#^.*src/test/java/\(.*\)\.java#\1#' | tr "/" "." > $(ARTIFACTS_DIR)/tests/smoke_tests_classnames

.PHONY: compile
compile: maven-structure-graph
	$(MAVEN_BIN) install $(MAVEN_ARGS) -DskipTests=true -Dbuild.profile=default -Droot.dir=$(WORKING_DIRECTORY) -Dbuild.skip.tarball=false -Prun-expensive-tasks -Psmoke -Dbuild.type=production -Dbuild.sbom=true -pl '!core/db-init' -P'!jspc' 2>&1 | tee $(ARTIFACTS_DIR)/mvn.compile.log

.PHONY: compile-ui
compile-ui:
	cd ui && pnpm install && pnpm build && \
	if [ "$(SKIP_UI_TESTS)" == "false" ]; then pnpm test; else echo "Skip UI Tests"; fi;

.PHONY: assemble
assemble: deps-build show-info
	$(MAVEN_BIN) install $(MAVEN_ARGS) -DskipTests=true -Dbuild.profile=default -Droot.dir=$(WORKING_DIRECTORY) -Dopennms.home=$(PKG_CORE_HOME) -Dinstall.version=$(INSTALL_VERSION) -Pbuild-bamboo -Prun-expensive-tasks -Dbuild.skip.tarball=false -Denable.license=true -Dbuild.type=production --file opennms-full-assembly/pom.xml 2>&1 | tee $(ARTIFACTS_DIR)/mvn.assemble.log

.PHONY: quick-build
quick-build: quick-compile quick-assemble

.PHONY: quick-compile
quick-compile: maven-structure-graph
	$(MAVEN_BIN) install $(MAVEN_ARGS) -T 1C -DskipTests=true -Dbuild.profile=default -Droot.dir=$(WORKING_DIRECTORY) -Dcyclonedx.skip=true -pl '!core/db-init' -P'!jspc' 2>&1 | tee $(ARTIFACTS_DIR)/mvn.quick-compile.log

.PHONY: quick-assemble
quick-assemble: deps-build show-info
	$(MAVEN_BIN) install $(MAVEN_ARGS) -DskipTests=true -Dbuild.profile=default -Droot.dir=$(WORKING_DIRECTORY) -Dopennms.home=$(PKG_CORE_HOME) -Dinstall.version=$(INSTALL_VERSION) --file opennms-full-assembly/pom.xml 2>&1 | tee $(ARTIFACTS_DIR)/mvn.quick-assemble.log

.PHONY: core-oci
core-oci:
ifeq (,$(wildcard ./opennms-full-assembly/target/opennms-full-assembly-*-core.tar.gz))
	@echo "Can't build the Core container image, the build artifact"
	@echo "./opennms-full-assembly/target/opennms-full-assembly-$(OPENNMS_VERSION)-core.tar.gz doesn't exist."
	@echo ""
	@echo "You can create the artifact with:"
	@echo ""
	@echo "  make quick-compile && make quick-assemble"
	@echo ""
	@exit 1
endif
	mkdir -p opennms-container/core/tarball-root && \
	tar xzf opennms-full-assembly/target/opennms-full-assembly-*-core.tar.gz -C opennms-container/core/tarball-root && \
	cd opennms-container/core && \
	echo "$(INSTALL_VERSION)" > tarball-root/etc/version.info && \
	docker build --platform=$(OCI_PLATFORM) \
		 --build-arg BUILD_DATE=$(BUILD_DATE) \
		 --build-arg VERSION=$(OPENNMS_VERSION) \
		 --build-arg REVISION=$(RELEASE_COMMIT) \
		 -t local/core:latest .

.PHONY: minion-oci
minion-oci:
ifeq (,$(wildcard ./opennms-assemblies/minion/target/org.opennms.assemblies.minion-*-minion.tar.gz))
	@echo "Can't build the Minion container image, the build artifact"
	@echo "./opennms-assemblies/minion/target/org.opennms.assemblies.minion-$(OPENNMS_VERSION)-minion.tar.gz doesn't exist."
	@echo ""
	@echo "You can create the artifact with:"
	@echo ""
	@echo "  make quick-compile && make quick-assemble"
	@echo ""
	@exit 1
endif
	mkdir -p opennms-container/minion/tarball-root && \
	tar xzf opennms-assemblies/minion/target/org.opennms.assemblies.minion-*-minion.tar.gz --strip-component 1 -C opennms-container/minion/tarball-root && \
	cd opennms-container/minion && \
	echo "$(INSTALL_VERSION)" > tarball-root/etc/version.info && \
	cat minion-config-schema.yml.in | sed -e 's,@VERSION@,$(OPENNMS_VERSION),' \
		-e 's,@REVISION@,$(RELEASE_COMMIT),' \
		-e 's,@BRANCH@,$(GIT_BRANCH),' \
		-e 's,@BUILD_NUMBER@,$(RELEASE_BUILD_NUM),' > minion-config-schema.yml && \
	docker build --platform=$(OCI_PLATFORM) \
		 --build-arg BUILD_DATE=$(BUILD_DATE) \
		 --build-arg VERSION=$(OPENNMS_VERSION) \
		 --build-arg REVISION=$(RELEASE_COMMIT) \
		 -t local/minion:latest .

.PHONY: sentinel-oci
sentinel-oci:
ifeq (,$(wildcard ./opennms-assemblies/sentinel/target/org.opennms.assemblies.sentinel-*-sentinel.tar.gz))
	@echo "Can't build the Sentinel container image, the build artifact"
	@echo "./opennms-assemblies/sentinel/target/org.opennms.assemblies.sentinel-$(OPENNMS_VERSION)-sentinel.tar.gz doesn't exist."
	@echo ""
	@echo "You can create the artifact with:"
	@echo ""
	@echo "  make quick-compile && make quick-assemble"
	@echo ""
	@exit 1
endif
	mkdir -p opennms-container/sentinel/tarball-root && \
	tar xzf opennms-assemblies/sentinel/target/org.opennms.assemblies.sentinel-*-sentinel.tar.gz --strip-component 1 -C opennms-container/sentinel/tarball-root
	cd opennms-container/sentinel && \
	echo "$(INSTALL_VERSION)" > tarball-root/etc/version.info && \
	docker build --platform=$(OCI_PLATFORM) \
		 --build-arg BUILD_DATE=$(BUILD_DATE) \
		 --build-arg VERSION=$(OPENNMS_VERSION) \
		 --build-arg REVISION=$(RELEASE_COMMIT) \
		 -t local/sentinel:latest .

.PHONY: core-oci-sbom
core-oci-sbom: deps-oci-sbom core-oci
	mkdir -p $(ARTIFACTS_DIR)/oci
	syft scan local/core:latest -o cyclonedx=$(ARTIFACTS_DIR)/oci/core-oci-sbom.xml --quiet

.PHONY: minion-oci-sbom
minion-oci-sbom: deps-oci-sbom minion-oci
	mkdir -p $(ARTIFACTS_DIR)/oci
	syft scan local/minion:latest -o cyclonedx=$(ARTIFACTS_DIR)/oci/minion-oci-sbom.xml --quiet

.PHONY: sentinel-oci-sbom
sentinel-oci-sbom: deps-oci-sbom sentinel-oci
	mkdir -p $(ARTIFACTS_DIR)/oci
	syft scan local/sentinel:latest -o cyclonedx=$(ARTIFACTS_DIR)/oci/sentinel-oci-sbom.xml --quiet

.PHONY: core-oci-sec-scan
core-oci-sec-scan: deps-oci-sec-scan core-oci
	mkdir -p $(ARTIFACTS_DIR)/oci
	trivy image local/core:latest $(TRIVY_ARGS) -o $(ARTIFACTS_DIR)/oci/core-trivy-report.json

.PHONY: minion-oci-sec-scan
minion-oci-sec-scan: deps-oci-sec-scan minion-oci
	mkdir -p $(ARTIFACTS_DIR)/oci
	trivy image local/minion:latest $(TRIVY_ARGS) -o $(ARTIFACTS_DIR)/oci/minion-trivy-report.json

.PHONY: sentinel-oci-sec-scan
sentinel-oci-sec-scan: deps-oci-sec-scan sentinel-oci
	mkdir -p $(ARTIFACTS_DIR)/oci
	trivy image local/sentinel:latest $(TRIVY_ARGS) -o $(ARTIFACTS_DIR)/oci/sentinel-trivy-report.json

# Smoke test: verify the application can start
.PHONY: quick-smoke
quick-smoke: deps-oci core-oci test-lists
	$(MAVEN_BIN) install $(MAVEN_ARGS) -N -DskipTests=false -DskipITs=false -DfailIfNoTests=false -Dtest.fork.count=1 -Dit.test="MenuHeaderIT,SinglePortFlowsIT" --fail-fast -Dfailsafe.skipAfterFailureCount=1 -P!smoke.all -Psmoke.core --file smoke-test/pom.xml 2>&1 | tee $(ARTIFACTS_DIR)/mvn.smoke-quick.log

.PHONY: core-e2e
core-e2e: deps-oci test-lists core-oci minion-oci sentinel-oci
	$(eval CORE_E2E_TESTS ?= $(shell cat $(ARTIFACTS_DIR)/tests/smoke_tests_classnames | awk "NR%$(MAVEN_SHARDS)==$(MAVEN_SHARD_IDX)" | paste -s -d, -))
	$(MAVEN_BIN) install $(MAVEN_ARGS) -N -DskipTests=false -DskipITs=false -DfailIfNoTests=false -Dtest.fork.count=1 -Dit.test="$(CORE_E2E_TESTS)" --fail-fast -Dfailsafe.skipAfterFailureCount=1 -P!smoke.all -Psmoke.core --file smoke-test/pom.xml 2>&1 | tee $(ARTIFACTS_DIR)/mvn.core-smoke.log

.PHONY: minion-e2e
minion-e2e: deps-oci test-lists minion-oci sentinel-oci core-oci
	$(eval MINION_E2E_TESTS ?= $(shell cat $(ARTIFACTS_DIR)/tests/smoke_tests_classnames | awk "NR%$(MAVEN_SHARDS)==$(MAVEN_SHARD_IDX)" | paste -s -d, -))
	$(MAVEN_BIN) install $(MAVEN_ARGS) -N -DskipTests=false -DskipITs=false -DfailIfNoTests=false -Dtest.fork.count=1 -Dit.test="$(MINION_E2E_TESTS)" --fail-fast -Dfailsafe.skipAfterFailureCount=1 -P!smoke.all -Psmoke.minion --file smoke-test/pom.xml 2>&1 | tee $(ARTIFACTS_DIR)/mvn.minion-smoke.log

.PHONY: sentinel-e2e
sentinel-e2e: deps-oci test-lists sentinel-oci minion-oci core-oci
	$(eval SENTINEL_E2E_TESTS ?= $(shell cat $(ARTIFACTS_DIR)/tests/smoke_tests_classnames | awk "NR%$(MAVEN_SHARDS)==$(MAVEN_SHARD_IDX)" | paste -s -d, -))
	$(MAVEN_BIN) install $(MAVEN_ARGS) -N -DskipTests=false -DskipITs=false -DfailIfNoTests=false -Dtest.fork.count=1 -Dit.test="$(SENTINEL_E2E_TESTS)" --fail-fast -Dfailsafe.skipAfterFailureCount=1 -P!smoke.all -Psmoke.sentinel --file smoke-test/pom.xml 2>&1 | tee $(ARTIFACTS_DIR)/mvn.sentinel-smoke.log

# Unit tests with optional sharding and specific test selection
.PHONY: unit-tests
unit-tests: test-lists spinup-postgres
	$(eval U_TESTS ?= $(shell grep -Fxv -f ./.cicd-assets/_skipTests.txt $(ARTIFACTS_DIR)/tests/unit_tests_classnames | awk "NR%$(MAVEN_SHARDS)==$(MAVEN_SHARD_IDX)" | paste -s -d, -))
	$(eval TESTS_PROJECTS ?= $(shell cat ${ARTIFACTS_DIR}/tests/test_modules | paste -s -d, -))
	$(MAVEN_BIN) install $(MAVEN_ARGS) -T 1C -DskipTests=true -DskipITs=true -Dbuild.profile=default -Droot.dir=$(WORKING_DIRECTORY) -Dfailsafe.skipAfterFailureCount=1 -P!checkstyle -P!production -Pbuild-bamboo -Dbuild.skip.tarball=true -Dmaven.test.skip.exec=true --fail-fast --also-make --projects "$(TESTS_PROJECTS)" -pl '!core/db-init' -P'!jspc' 2>&1 | tee $(ARTIFACTS_DIR)/mvn.tests.compile.log
	if [ $(command -v ionice) ]; then ionice; fi; nice $(MAVEN_BIN) install $(MAVEN_ARGS) -DskipTests=false -DskipITs=true -DskipSurefire=false -DskipFailsafe=true -Dbuild.profile=default -Droot.dir=$(WORKING_DIRECTORY) -Dfailsafe.skipAfterFailureCount=1 -P!checkstyle -P!production -Pbuild-bamboo -Pcoverage -Dbuild.skip.tarball=true -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -DrunPingTests=false --fail-fast -Dorg.opennms.core.test-api.dbCreateThreads=1 -Dorg.opennms.core.test-api.snmp.useMockSnmpStrategy=false -Dtest="$(U_TESTS)" --projects "$(TESTS_PROJECTS)" -pl '!core/db-init' -P'!jspc' 2>&1 | tee $(ARTIFACTS_DIR)/mvn.u_tests.log

.PHONY: integration-tests
integration-tests: test-lists spinup-postgres
	$(eval I_TESTS ?= $(shell grep -Fxv -f ./.cicd-assets/_skipIntegrationTests.txt $(ARTIFACTS_DIR)/tests/integration_tests_classnames | awk "NR%$(MAVEN_SHARDS)==$(MAVEN_SHARD_IDX)" | paste -s -d, -))
	$(eval TESTS_PROJECTS ?= $(shell cat $(ARTIFACTS_DIR)/tests/test_modules | paste -s -d, -))
	$(MAVEN_BIN) install $(MAVEN_ARGS) -T 1C -DskipTests=true -DskipITs=true -Dbuild.profile=default -Droot.dir=$(WORKING_DIRECTORY) -Dfailsafe.skipAfterFailureCount=1 -P!checkstyle -P!production -Pbuild-bamboo -Dbuild.skip.tarball=true -Dmaven.test.skip.exec=true --fail-fast --also-make --projects "$(TESTS_PROJECTS)" -pl '!core/db-init' -P'!jspc' 2>&1 | tee $(ARTIFACTS_DIR)/mvn.tests.compile.log
	if [ $(command -v ionice) ]; then ionice; fi; nice $(MAVEN_BIN) install $(MAVEN_ARGS) -DskipTests=false -DskipITs=false -DskipSurefire=true -DskipFailsafe=false -Dbuild.profile=default -Droot.dir=$(WORKING_DIRECTORY) -Dfailsafe.skipAfterFailureCount=1 -P!checkstyle -P!production -Pbuild-bamboo -Pcoverage -Dbuild.skip.tarball=true -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -DrunPingTests=false --fail-fast -Dorg.opennms.core.test-api.dbCreateThreads=1 -Dorg.opennms.core.test-api.snmp.useMockSnmpStrategy=false -Dtest="$(U_TESTS)" -Dit.test="$(I_TESTS)" --projects "$(TESTS_PROJECTS)" -pl '!core/db-init' -P'!jspc' 2>&1 | tee $(ARTIFACTS_DIR)/mvn.i_tests.log

.PHONY: code-coverage
code-coverage: deps-sonar
	mkdir -p $(ARTIFACTS_DIR)/code-coverage
	find . -type f '!' -path './.git/*' -name jacoco.xml | sort -u > $(ARTIFACTS_DIR)/code-coverage/jacoco.xml
	for src in $(shell find . -type d '!' -path './.git/*' -name target | sed -e 's,/target,/src,') ; do \
		echo $$src/main ; \
		echo $$src/assembly ; \
	done \
	| sort -u > $(ARTIFACTS_DIR)/code-coverage/source-folders.txt
	find . -type d '!' -path './.git/*' -a \( -name surefire-reports\* -o -name failsafe-reports\* \) | sort -u > $(ARTIFACTS_DIR)/code-coverage/junit-report-folders.txt
	for src in $(shell find . -type d '!' -path './.git/*' -name target | sed -e 's,/target,/src,') ; do \
		echo $$src/test ; \
	done \
	| sort -u > $(ARTIFACTS_DIR)/code-coverage/test-folders.txt
	for test_classes_dir in $(shell cat target/artifacts/code-coverage/junit-report-folders.txt | sed -e 's,/surefire-reports,,' | sed -e 's,/failsafe-reports,,') ; do \
		find "$$test_classes_dir" -maxdepth 1 -type d -name test-classes ; \
	done \
	| sort -u > $(ARTIFACTS_DIR)/code-coverage/test-class-folders.txt
	for classes_dir in $(shell cat target/artifacts/code-coverage/junit-report-folders.txt | sed -e 's,/surefire-reports,,' | sed -e 's,/failsafe-reports,,') ; do \
		find "$$classes_dir" -maxdepth 1 -type d -name classes ; \
	done \
	| sort -u > $(ARTIFACTS_DIR)/code-coverage/class-folders.txt

.PHONY: core-pkg-buildroot
core-pkg-buildroot:
ifeq (,$(wildcard ./opennms-full-assembly/target/opennms-full-assembly-*-core.tar.gz))
	@echo "Can't build the Core build root directory structure"
	@echo "./opennms-full-assembly/target/opennms-full-assembly-$(OPENNMS_VERSION)-core.tar.gz doesn't exist."
	@echo "You can create the artifact with: make quick-compile && make quick-assemble"
	@exit 1
endif
	mkdir -p "$(BUILD_ROOT)/core/opt/opennms"
	mkdir -p "$(ARTIFACTS_DIR)/packages/core"
	tar xzf "./opennms-full-assembly/target/opennms-full-assembly-$(OPENNMS_VERSION)-core.tar.gz" -C "$(BUILD_ROOT)/core/opt/opennms"
	rm -rf "$(BUILD_ROOT)/core/opt/opennms/logs" \
           "$(BUILD_ROOT)/core/opt/opennms/share/rrd" \
           "$(BUILD_ROOT)/core/opt/opennms/share/reports" \
           "$(BUILD_ROOT)/core/opt/opennms/deploy"
	mkdir -p "$(BUILD_ROOT)/core$(PKG_CORE_RRD)" \
             "$(BUILD_ROOT)/core$(PKG_CORE_REPORTS)" \
             "$(BUILD_ROOT)/core$(PKG_CORE_LOGS)" \
             "$(BUILD_ROOT)/core$(PKG_CORE_DEPLOY)" \
             "$(BUILD_ROOT)/core/usr/lib/systemd/system"
	cp "$(BUILD_ROOT)/core/opt/opennms/etc/opennms.service" "$(BUILD_ROOT)/core/usr/lib/systemd/system"

.PHONY: core-pkg-deb
core-pkg-deb: deps-packages core-pkg-buildroot
	@echo "==== Building Debian Core Packages ===="
	@echo "Version: $(OPENNMS_VERSION)  Release: $(PKG_RELEASE)"
	fpm -s dir -t deb -p $(ARTIFACTS_DIR)/packages/core/NAME_VERSION_ARCH_$(PKG_RELEASE).deb \
		-n "delta-v-core" \
		-v "$(OPENNMS_VERSION)-$(PKG_RELEASE)" \
		--config-files /opt/opennms/etc \
		--description "Delta-V Core services" \
		--url "https://github.com/pbrane/delta-v" \
		--maintainer "Maintainer <$(MAINTAINER_EMAIL)>" \
		--depends jicmp \
		--depends jicmp6 \
		--depends jrrd2 \
		--deb-recommends openjdk-17-jdk-headless \
		--deb-suggests "postgresql (>= 14.0)" \
		--after-install packages/pkg-postinst-core.sh \
		-C "$(BUILD_ROOT)/core"

.PHONY: core-pkg-rpm
core-pkg-rpm: deps-packages core-pkg-buildroot
	@echo "==== Building RPM Core Packages ===="
	@echo "Version: $(OPENNMS_VERSION)  Release: $(PKG_RELEASE)"
	fpm -s dir -t rpm -p $(ARTIFACTS_DIR)/packages/core/NAME_VERSION_ARCH_$(PKG_RELEASE).rpm \
		-n "delta-v-core" \
		-v "$(OPENNMS_VERSION)_$(PKG_RELEASE)" \
		--config-files /opt/opennms/etc \
		--description "Delta-V Core services" \
		--url "https://github.com/pbrane/delta-v" \
		--maintainer "Maintainer <$(MAINTAINER_EMAIL)>" \
		--depends jicmp \
		--depends jicmp6 \
		--depends jrrd2 \
		--rpm-tag "Recommends: java-17-openjdk-devel" \
		--rpm-tag "Suggests: postgresql-server >= 14.0" \
		--after-install packages/pkg-postinst-core.sh \
		-C "$(BUILD_ROOT)/core"

.PHONY: minion-pkg-buildroot
minion-pkg-buildroot:
ifeq (,$(wildcard ./opennms-assemblies/minion/target/org.opennms.assemblies.minion-*-minion.tar.gz))
	@echo "Can't build the Minion build root directory structure"
	@echo "You can create the artifact with: make quick-compile && make quick-assemble"
	@exit 1
endif
	mkdir -p "$(BUILD_ROOT)/minion/opt/minion"
	mkdir -p "$(ARTIFACTS_DIR)/packages/minion"
	tar xzf "./opennms-assemblies/minion/target/org.opennms.assemblies.minion-$(OPENNMS_VERSION)-minion.tar.gz" --strip-component 1 -C "$(BUILD_ROOT)/minion/opt/minion"
	rm -rf "$(BUILD_ROOT)/minion/opt/minion/data/log" \
           "$(BUILD_ROOT)/minion/opt/minion/deploy"
	mkdir -p "$(BUILD_ROOT)/minion$(PKG_MINION_HOME)" \
             "$(BUILD_ROOT)/minion$(PKG_MINION_LOGS)" \
             "$(BUILD_ROOT)/minion$(PKG_MINION_DEPLOY)" \
             "$(BUILD_ROOT)/minion/usr/lib/systemd/system"
	mv "$(BUILD_ROOT)/minion/opt/minion/etc/minion.service" "$(BUILD_ROOT)/minion/usr/lib/systemd/system"
	mv "$(BUILD_ROOT)/minion/opt/minion/etc/minion.init" "$(BUILD_ROOT)/minion/opt/minion/bin/minion"

.PHONY: minion-pkg-deb
minion-pkg-deb: deps-packages minion-pkg-buildroot
	@echo "==== Building Debian Minion Packages ===="
	@echo "Version: $(OPENNMS_VERSION)  Release: $(PKG_RELEASE)"
	fpm -s dir -t deb -p $(ARTIFACTS_DIR)/packages/minion/NAME_VERSION_ARCH_$(PKG_RELEASE).deb \
		-n "delta-v-minion" \
		-v "$(OPENNMS_VERSION)-$(PKG_RELEASE)" \
		--config-files /opt/minion/etc \
		--description "Delta-V monitoring proxy service" \
		--url "https://github.com/pbrane/delta-v" \
		--maintainer "Maintainer <$(MAINTAINER_EMAIL)>" \
		--depends jicmp \
		--depends jicmp6 \
		--deb-recommends openjdk-17-jdk-headless \
		--after-install packages/pkg-postinst-minion.sh \
		-C "$(BUILD_ROOT)/minion"

.PHONY: minion-pkg-rpm
minion-pkg-rpm: deps-packages minion-pkg-buildroot
	@echo "==== Building RPM Minion Packages ===="
	@echo "Version: $(OPENNMS_VERSION)  Release: $(PKG_RELEASE)"
	fpm -s dir -t rpm -p $(ARTIFACTS_DIR)/packages/minion/NAME_VERSION_ARCH_$(PKG_RELEASE).rpm \
		-n "delta-v-minion" \
		-v "$(OPENNMS_VERSION)_$(PKG_RELEASE)" \
		--config-files /opt/minion/etc \
		--description "Delta-V monitoring proxy service" \
		--url "https://github.com/pbrane/delta-v" \
		--maintainer "Maintainer <$(MAINTAINER_EMAIL)>" \
		--depends jicmp \
		--depends jicmp6 \
		--rpm-tag "Recommends: java-17-openjdk-devel" \
		--after-install packages/pkg-postinst-minion.sh \
		-C "$(BUILD_ROOT)/minion"

.PHONY: sentinel-pkg-buildroot
sentinel-pkg-buildroot:
ifeq (,$(wildcard ./opennms-assemblies/sentinel/target/org.opennms.assemblies.sentinel-*-sentinel.tar.gz))
	@echo "Can't build the Sentinel build root directory structure"
	@echo "You can create the artifact with: make quick-compile && make quick-assemble"
	@exit 1
endif
	mkdir -p "$(BUILD_ROOT)/sentinel/opt/sentinel"
	mkdir -p "$(ARTIFACTS_DIR)/packages/sentinel"
	tar xzf "./opennms-assemblies/sentinel/target/org.opennms.assemblies.sentinel-$(OPENNMS_VERSION)-sentinel.tar.gz" --strip-component 1 -C "$(BUILD_ROOT)/sentinel/opt/sentinel"
	rm -rf "$(BUILD_ROOT)/sentinel/opt/sentinel/data/log" \
           "$(BUILD_ROOT)/sentinel/opt/sentinel/deploy"
	mkdir -p "$(BUILD_ROOT)/sentinel$(PKG_SENTINEL_HOME)" \
             "$(BUILD_ROOT)/sentinel$(PKG_SENTINEL_LOGS)" \
             "$(BUILD_ROOT)/sentinel$(PKG_SENTINEL_DEPLOY)" \
             "$(BUILD_ROOT)/sentinel/usr/lib/systemd/system"
	mv "$(BUILD_ROOT)/sentinel/opt/sentinel/etc/sentinel.service" "$(BUILD_ROOT)/sentinel/usr/lib/systemd/system"
	mv "$(BUILD_ROOT)/sentinel/opt/sentinel/etc/sentinel.init" "$(BUILD_ROOT)/sentinel/opt/sentinel/bin/sentinel"

.PHONY: sentinel-pkg-deb
sentinel-pkg-deb: deps-packages sentinel-pkg-buildroot
	@echo "==== Building Debian Sentinel Packages ===="
	@echo "Version: $(OPENNMS_VERSION)  Release: $(PKG_RELEASE)"
	fpm -s dir -t deb -p $(ARTIFACTS_DIR)/packages/sentinel/NAME_VERSION_ARCH_$(PKG_RELEASE).deb \
		-n "delta-v-sentinel" \
		-v "$(OPENNMS_VERSION)-$(PKG_RELEASE)" \
		--config-files /opt/sentinel/etc \
		--description "Delta-V services to horizontally scale backend workloads" \
		--url "https://github.com/pbrane/delta-v" \
		--maintainer "Maintainer <$(MAINTAINER_EMAIL)>" \
		--deb-recommends openjdk-17-jdk-headless \
		--after-install packages/pkg-postinst-sentinel.sh \
		-C "$(BUILD_ROOT)/sentinel"

.PHONY: sentinel-pkg-rpm
sentinel-pkg-rpm: deps-packages sentinel-pkg-buildroot
	@echo "==== Building RPM Sentinel Packages ===="
	@echo "Version: $(OPENNMS_VERSION)  Release: $(PKG_RELEASE)"
	fpm -s dir -t rpm -p $(ARTIFACTS_DIR)/packages/sentinel/NAME_VERSION_ARCH_$(PKG_RELEASE).rpm \
		-n "delta-v-sentinel" \
		-v "$(OPENNMS_VERSION)_$(PKG_RELEASE)" \
		--config-files /opt/sentinel/etc \
		--description "Delta-V services to horizontally scale backend workloads" \
		--url "https://github.com/pbrane/delta-v" \
		--maintainer "Maintainer <$(MAINTAINER_EMAIL)>" \
		--rpm-tag "Recommends: java-17-openjdk-devel" \
		--after-install packages/pkg-postinst-sentinel.sh \
		-C "$(BUILD_ROOT)/sentinel"

.PHONY: all-pkgs
all-pkgs: core-pkg-deb core-pkg-rpm minion-pkg-deb minion-pkg-rpm sentinel-pkg-deb sentinel-pkg-rpm

.PHONY: javadocs
javadocs: deps-build show-info
	$(MAVEN_BIN) javadoc:aggregate --batch-mode -Prun-expensive-tasks

.PHONY: docs
docs: deps-docs
	@echo "Build Antora docs..."
	antora --stacktrace $(SITE_FILE)

.PHONY: docs-docker
docs-docker: deps-docs-docker
	@echo "Build Antora docs with docker ..."
	docker run --rm -v $(WORKING_DIRECTORY):/antora $(DOCKER_ANTORA_IMAGE) --stacktrace generate $(SITE_FILE)

.PHONY: docs-clean
docs-clean:
	@echo "Delete build and public artifacts ..."
	@rm -rf build public

.PHONY: docs-clean-cache
docs-clean-cache:
	@echo "Clean Antora cache for git repositories and UI components ..."
	@rm -rf .cache

.PHONY: docs-serve
docs-serve:
	@echo "Start Nginx with public folder as html root ..."
	docker run --rm -v $(WORKING_DIRECTORY)/public:/usr/share/nginx/html --name delta-v-docs -p 8080:80 -d nginx

.PHONY: docs-serve-stop
docs-serve-stop:
	@echo "Stopping Nginx docs server ..."
	docker stop delta-v-docs

.PHONY: install-core
install-core: quick-compile quick-assemble
	@echo "Install Core to $(PKG_CORE_HOME)"
	mkdir -p $(PKG_CORE_HOME)
	tar xzf ./target/opennms-$(OPENNMS_VERSION).tar.gz -C $(PKG_CORE_HOME)

.PHONY: uninstall-core
uninstall-core:
	@echo "Uninstall Core from $(PKG_CORE_HOME)"
	rm -rf "$(PKG_CORE_HOME)/*"

.PHONY: clean-all
clean-all: clean-m2 clean-git

.PHONY: clean-git
clean-git:
	git clean -fdx

.PHONY: clean-m2
clean-m2:
	rm -rf ~/.m2/repository/org/opennms

.PHONY: clean-assembly
clean-assembly:
	$(MAVEN_BIN) -Passemblies clean

.PHONY: clean-docs
clean-docs: docs-clean docs-clean-cache

.PHONY: clean-buildroot
clean-buildroot:
	@echo "Delete build root content for package builds ..."
	@rm -rf $(BUILD_ROOT)

.PHONY: clean-packages
clean-packages:
	@echo "Delete RPM and Debian package artifacts ..."
	@rm -rf $(ARTIFACTS_DIR)/packages

.PHONY: clean
clean: clean-assembly clean-docs

.PHONY: collect-artifacts
collect-artifacts:
	mkdir -p $(ARTIFACTS_DIR)/{archives,config-schema,oci}
	find . -type f -regex "^\.\/target\/opennms-.*\.tar\.gz" -exec mv -v {} $(ARTIFACTS_DIR)/archives \;
	find . -type f -regex "^\.\/opennms-assemblies\/minion\/target\/org.opennms.assemblies.minion-.*\.tar\.gz" -exec mv -v {} $(ARTIFACTS_DIR)/archives/minion-${OPENNMS_VERSION}.tar.gz \;
	find . -type f -regex "^\.\/opennms-assemblies\/sentinel\/target\/org.opennms.assemblies.sentinel-.*\.tar\.gz" -exec mv -v {} $(ARTIFACTS_DIR)/archives/sentinel-${OPENNMS_VERSION}.tar.gz \;
	find . -type f -regex "^\.\/opennms-assemblies\/xsds\/target\/.*-xsds\.tar\.gz" -exec mv -v {} $(ARTIFACTS_DIR)/archives/opennms-${OPENNMS_VERSION}-xsds.tar.gz \;
	find . -type f -regex "^\.\/opennms-full-assembly\/target\/opennms-full-assembly-.*-core\.tar\.gz" -exec mv -v {} $(ARTIFACTS_DIR)/archives/opennms-${OPENNMS_VERSION}-core.tar.gz \;
	find . -type f -regex "^\.\/opennms-full-assembly\/target\/opennms-full-assembly-.*-optional\.tar\.gz" -exec mv -v {} $(ARTIFACTS_DIR)/archives/opennms-${OPENNMS_VERSION}-optional.tar.gz \;
	find . -type f -regex "^\.\/opennms-full-assembly\/target\/THIRD-PARTY.txt" -exec mv -v {} $(ARTIFACTS_DIR) \;
	find . -type f -regex "^\.\/opennms-container\/.*\/images\/.*\.oci" -exec mv -v {} $(ARTIFACTS_DIR)/oci \;
	find . -type f -regex "^\.\/target\/bom.*" -exec mv -v {} $(ARTIFACTS_DIR) \;

.PHONY: collect-testresults
collect-testresults:
	mkdir -p $(ARTIFACTS_DIR)/{surefire-reports,failsafe-reports,recordings}
	find . -type f -regex ".*\/target\/.*\.mp4" -exec mv -v {} $(ARTIFACTS_DIR)/recordings \;
	find . -type f -regex ".*\/target\/surefire-reports\/.*\.xml" -exec mv -v {} $(ARTIFACTS_DIR)/surefire-reports/ \;
	find . -type f -regex ".*\/target\/failsafe-reports\/.*\.xml" -exec mv -v {} $(ARTIFACTS_DIR)/failsafe-reports/ \;
	find . -type d -regex "^\.\/target\/logs" -exec tar czf $(ARTIFACTS_DIR)/logs.tar.gz {} \;
	find . -type d -regex "^\./smoke-test\/target\/logs" -exec tar czf $(ARTIFACTS_DIR)/smoke-test-logs.tar.gz {} \;
	find . -type d -regex "^\./smoke-test\/target\/screenshots" -exec tar czf $(ARTIFACTS_DIR)/smoke-test-screenshots.tar.gz {} \;
	find . -type f -regex "^\.\/target\/structure-graph\.json" -exec mv -v {} $(ARTIFACTS_DIR) \;

.PHONY: spinup-postgres
spinup-postgres: deps-oci
	@echo "Spin-up PostgreSQL database for tests using Docker Compose on port 5432/tcp"
	docker compose -f .cicd-assets/postgres/compose.yaml up -d

.PHONY: destroy-postgres
destroy-postgres: deps-oci
	@echo "Shutdown and remove PostgreSQL database using Docker Compose"
	docker compose -f .cicd-assets/postgres/compose.yaml down -v

.PHONY: registry-login
registry-login: deps-oci
	@echo ${OCI_REGISTRY_PASSWORD} | docker login --username ${OCI_REGISTRY_USER} --password-stdin ${OCI_REGISTRY}

.PHONY: version
version: deps-build
	$(call setversion,$(RELEASE_VERSION))

.PHONY: release
release: deps-build
	@mkdir -p target
	@echo ""
	@echo "Release version:              $(RELEASE_VERSION)"
	@echo "New snapshot version:          $(SNAPSHOT_VERSION)"
	@echo "Git version tag:              v$(RELEASE_VERSION)"
	@echo "Release log:                  $(RELEASE_LOG)"
	@echo "Current branch:               $(GIT_BRANCH)"
	@echo "Release branch:               $(RELEASE_BRANCH)"
	@echo ""
	@echo -n "Check release branch:        "
	@if [ "$(GIT_BRANCH)" != "$(RELEASE_BRANCH)" ]; then echo "Releases are made from the $(RELEASE_BRANCH) branch, your branch is $(GIT_BRANCH)."; exit 1; fi
	@echo "$(OK)"
	@echo -n "Check branch in sync:        "
	@if [ "$(git rev-parse HEAD)" != "$(git rev-parse @{u})" ]; then echo "$(RELEASE_BRANCH) branch not in sync with remote origin."; exit 1; fi
	@echo "$(OK)"
	@echo -n "Check uncommited changes:    "
	@if git status --porcelain | grep -q .; then echo "There are uncommited changes in your repository."; exit 1; fi
	@echo "$(OK)"
	@echo -n "Check release version:       "
	@if [ "$(RELEASE_VERSION)" = "UNSET.0.0" ]; then echo "Set a release version, e.g. make release RELEASE_VERSION=1.0.0"; exit 1; fi
	@echo "$(OK)"
	@echo -n "Check version tag available: "
	@if git rev-parse v$(RELEASE_VERSION) >$(RELEASE_LOG) 2>&1; then echo "Tag v$(RELEASE_VERSION) already exists"; exit 1; fi
	@echo "$(OK)"
	@$(call setversion,$(RELEASE_VERSION))
	@mvn validate >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "Git commit new release:      "
	@git commit --signoff -am "release: Delta-V $(RELEASE_VERSION)" >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@echo -n "Set Git version tag:         "
	@git tag -a "v$(RELEASE_VERSION)" -m "Release Delta-V version $(RELEASE_VERSION)" >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@$(call setversion,$(SNAPSHOT_VERSION))
	@echo -n "Git commit snapshot release: "
	@git commit --signoff -am "release: Delta-V $(SNAPSHOT_VERSION)" >>$(RELEASE_LOG) 2>&1
	@echo "$(OK)"
	@if [ "$(PUSH_RELEASE)" = "true" ]; then \
		echo -n "Push commits:                "; \
		git push >>$(RELEASE_LOG) 2>&1; \
		echo "$(OK)"; \
		echo -n "Push tag:                    "; \
		git push origin v$(RELEASE_VERSION) >>$(RELEASE_LOG) 2>&1; \
		echo "$(OK)"; \
	else \
		echo "Push commits and tag:        $(SKIP)"; \
	fi;
