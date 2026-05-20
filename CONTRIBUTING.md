# Contributing to TaskBridge

Thank you for considering a contribution. This document covers building locally, running tests, and submitting changes.

## Prerequisites

- Java 17 or 21
- Maven 3.9+

## Building

```bash
mvn compile
```

## Running tests

Run the full suite (unit tests + integration tests):

```bash
mvn test
```

Run only the client starter tests:

```bash
mvn test -pl taskbridge-client-spring-boot-starter
```

Run only the server tests:

```bash
mvn test -pl taskbridge-server
```

Run the end-to-end integration tests (spins up a full Flowable + worker stack in a single JVM):

```bash
mvn test -pl examples/taskbridge-example-flowable-app -Dtest=TaskBridgeIntegrationSpec
```

## Project structure

| Module                                     | Purpose                                         |
|--------------------------------------------|-------------------------------------------------|
| `taskbridge-client-spring-boot-starter`    | Client auto-configuration and worker runtime    |
| `taskbridge-server`                        | Server embedded in the Flowable service         |
| `examples/taskbridge-example-flowable-app` | Runnable Flowable app used in integration tests |
| `examples/taskbridge-example-worker-app`   | Runnable worker app example                     |

## Submitting a pull request

1. Fork the repository and create a branch from `main`.
2. Make your changes. Add or update tests to cover them.
3. Verify `mvn test` passes in full.
4. Open a pull request — the PR template will guide you through the description.

## Reporting a bug

Use the [Bug Report](.github/ISSUE_TEMPLATE/bug_report.md) template. Include the TaskBridge version, Spring Boot
version, Flowable version, and a minimal reproducer.

## Code style

- Java 17+ — records, sealed types, pattern matching, and text blocks are all fine.
- No Javadoc for obvious methods. Add a comment only when the *why* is non-obvious.
- Tests are written in Groovy with [Spock](https://spockframework.org/).
