# ENVIRONMENT
PWD := $(shell pwd)

# PATHS
GROUP := com.primandproper.platform

# TOOL VERSIONS
GRADLE_VERSION := 8.11

# COMMANDS
# Prefer the committed wrapper; `make setup` generates it. Override with `make GRADLE=gradle <target>`.
GRADLE ?= ./gradlew

.DEFAULT_GOAL := help

.PHONY: help
help:
	@echo "Targets:"
	@echo "  setup     generate the Gradle wrapper (needs a system 'gradle' once)"
	@echo "  format    rewrite sources with ktlint (alias: fmt)"
	@echo "  lint      verify formatting/style with ktlint"
	@echo "  build     compile all modules and assemble artifacts"
	@echo "  test      run the JVM unit tests"
	@echo "  check     lint + test (Gradle verification lifecycle)"
	@echo "  clean     delete build outputs"

## PREREQUISITES

# The wrapper jar is committed, so `./gradlew` works out of the box — you don't normally need this.
# `make setup` only regenerates the wrapper (e.g. to bump its Gradle version); needs a system Gradle.
.PHONY: setup
setup:
	gradle wrapper --gradle-version $(GRADLE_VERSION)

## FORMATTING

.PHONY: format
format:
	$(GRADLE) ktlintFormat

.PHONY: fmt
fmt: format

## LINTING

.PHONY: lint
lint:
	$(GRADLE) ktlintCheck

## EXECUTION

.PHONY: build
build:
	$(GRADLE) assemble

.PHONY: test
test:
	$(GRADLE) test

.PHONY: check
check:
	$(GRADLE) check

.PHONY: clean
clean:
	$(GRADLE) clean
