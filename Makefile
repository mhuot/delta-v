# ==============================================================================
# OpenNMS Build Facade
#
# Usage:
#   make build                          Compile and install (tests skipped)
#   make module MODULE=:opennms-dao     Build one module + its dependencies
#   make dependents MODULE=:opennms-dao Build one module + modules that depend on it
#   make test-class MODULE=:opennms-dao TEST=SomeDaoTest   Run a single unit test
#   make test-class MODULE=:opennms-dao TEST=SomeDaoIT     Run a single integration test
#   make unit-tests                     Build and run all unit tests
#   make it-tests                       Build and run all integration tests
#   make all-test                       Build and run all tests (unit + integration)
#   make assemble                       Assemble distribution (default profile)
#   make assemble PROFILE=dir|full|fulldir
#
# Overridable variables (set on command line or in environment):
#   MODULE       Maven --projects selector, e.g. :opennms-dao or groupId:artifactId
#   TEST         Test class name for test-class target (suffix IT = integration test)
#   PROFILE      Assembly profile: default | dir | full | fulldir (default: dir)
#   MAVEN_FLAGS  Extra Maven flags (default: -DskipTests -B)
#   MAVEN_OPTS   JVM options for Maven (has a sensible default below)
# ==============================================================================

MODULE      ?=
TEST        ?=
PROFILE     ?= dir
MAVEN_FLAGS ?= -DskipTests -B
MAVEN_OPTS  ?= -Xmx3g \
               -XX:ReservedCodeCacheSize=512m \
               -XX:+TieredCompilation \
               -XX:TieredStopAtLevel=1 \
               -XX:-UseGCOverheadLimit \
               -XX:+UseParallelGC \
               -XX:-MaxFDLimit \
               -Djdk.util.zip.disableZip64ExtraFieldValidation=true \
               -Dmaven.wagon.http.retryHandler.count=3

MVN         := ./mvnw
COMMON      := --color=always \
               -Djava.awt.headless=true \
               -Daether.connector.resumeDownloads=false \
               -Daether.connector.basic.threads=1 \
               -Droot.dir=$(CURDIR)

export MAVEN_OPTS

.PHONY: help build module dependents test-class test ui assemble clean

.DEFAULT_GOAL := help

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*##' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*##"}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'
	@echo ""
	@echo "Variables (override on command line):"
	@echo "  MODULE       Maven module selector (e.g. :opennms-dao)          (current: $(MODULE))"
	@echo "  TEST         Test class name (suffix IT = integration test)     (current: $(TEST))"
	@echo "  PROFILE      Assembly profile: default | dir | full | fulldir  (current: $(PROFILE))"
	@echo "  MAVEN_FLAGS  Extra Maven flags                                  (current: $(MAVEN_FLAGS))"
	@echo "  MAVEN_OPTS   JVM options passed to Maven"

build: ## Compile and package all modules (tests skipped)
	$(MVN) $(MAVEN_FLAGS) $(COMMON) \
	  -Dbuild.profile=default \
	  install

module: ## Build one module and its upstream dependencies; set MODULE=:artifactId
	@test -n "$(MODULE)" || (echo "ERROR: MODULE is required, e.g.: make module MODULE=:opennms-dao" && exit 1)
	$(MVN) $(MAVEN_FLAGS) $(COMMON) \
	  -Dbuild.profile=default \
	  --projects $(MODULE) \
	  --also-make \
	  install

dependents: ## Build one module and all modules that depend on it; set MODULE=:artifactId
	@test -n "$(MODULE)" || (echo "ERROR: MODULE is required, e.g.: make dependents MODULE=:opennms-dao" && exit 1)
	$(MVN) $(MAVEN_FLAGS) $(COMMON) \
	  -Dbuild.profile=default \
	  --projects $(MODULE) \
	  --also-make-dependents \
	  install

test-class: ## Run a single test class; set MODULE=:artifactId TEST=ClassName (suffix IT = integration test)
	@test -n "$(MODULE)" || (echo "ERROR: MODULE is required, e.g.: make test-class MODULE=:opennms-dao TEST=SomeDaoTest" && exit 1)
	@test -n "$(TEST)"   || (echo "ERROR: TEST is required, e.g.: make test-class MODULE=:opennms-dao TEST=SomeDaoTest" && exit 1)
	$(MVN) -B $(COMMON) \
	  -Dbuild.profile=default \
	  --projects $(MODULE) \
	  --also-make \
	  $(if $(filter %IT,$(TEST)),-Dit.test=$(TEST),-Dtest=$(TEST) -DskipTests=false) \
	  $(if $(filter %IT,$(TEST)),failsafe:integration-test failsafe:verify,install)

unit-tests: ## Build and run all unit tests
	$(MVN) -B $(COMMON) \
	  -Dbuild.profile=default \
	  -DskipTests=false \
	  -DskipITs=true \
	  verify

it-tests: ## Build and run all unit integration tests
	$(MVN) -B $(COMMON) \
	  -Dbuild.profile=default \
	  -DskipTests=true \
	  -DskipITs=false \
	  verify

all-tests: ## Build and run all tests (unit + integration)
	$(MVN) -B $(COMMON) \
	  -Dbuild.profile=default \
	  -DskipTests=false \
	  -DskipITs=false \
	  verify

ui: ## Install, build, and test the Vue UI
	cd ui && pnpm install && pnpm build && pnpm test

assemble: ## Assemble the distribution; set PROFILE=dir|full|fulldir (default: dir)
	cd opennms-full-assembly && \
	$(CURDIR)/mvnw $(MAVEN_FLAGS) $(COMMON) \
	  -Dbuild.profile=$(PROFILE) \
	  install

clean: ## Remove all build artifacts
	$(MVN) -B clean
