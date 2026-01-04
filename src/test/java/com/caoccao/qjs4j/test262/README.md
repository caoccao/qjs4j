# Test262 Runner for qjs4j

This directory contains the Test262 ECMAScript conformance test runner for qjs4j.

## Overview

[Test262](https://github.com/tc39/test262) is the official ECMAScript conformance test suite maintained by TC39. This runner allows qjs4j to validate its ECMAScript compliance against thousands of official tests.

## Setup

1. Clone the test262 repository alongside qjs4j:

```bash
cd ..
git clone https://github.com/tc39/test262.git
cd qjs4j
```

The test262 source code should be at `../test262` relative to the qjs4j project root.

## Running Tests

### Command Line Runner

Run all test262 tests:

```bash
./gradlew test262
```

Run a quick subset of tests for validation:

```bash
./gradlew test262Quick
```

Run only language tests:

```bash
./gradlew test262Language
```

### JUnit Integration

Run test262 tests via JUnit (runs a limited subset):

```bash
./gradlew test --tests "Test262Test"
```

Or run from your IDE by executing the `Test262Test` class.

## Configuration

The test runner automatically skips tests for unsupported features:

- Intl (Internationalization API)
- top-level-await
- import.meta
- hashbang
- And other features not yet implemented

See [Test262Config.java](Test262Config.java) for the complete list.

## Components

- **Test262Runner.java** - Main command-line runner
- **Test262Test.java** - JUnit integration for IDE/Gradle execution
- **Test262Parser.java** - Parses YAML frontmatter and JavaScript code
- **Test262Executor.java** - Executes tests with proper flag handling
- **Test262Reporter.java** - Tracks and reports results
- **Test262Config.java** - Configuration and feature filtering
- **HarnessLoader.java** - Loads test262 harness files (assert.js, sta.js)

## Test Execution

The runner handles all test262 features:

- **Async tests**: Uses `$DONE()` callback with timeout
- **Module tests**: Executes as ES6 modules
- **Strict mode**: Applies `"use strict"` when required
- **Negative tests**: Validates expected errors
- **Harness files**: Loads assert.js, sta.js, and other helpers

## Results

After execution, you'll see:

```
======================================================================
Test262 Results Summary
======================================================================
Total tests:   1000
Executed:      800
Passed:        640 (80.0%)
Failed:        150 (18.8%)
Timeout:       10 (1.2%)
Skipped:       200
======================================================================
```

Failed tests are listed with their error messages to help identify issues.

## Documentation

For detailed implementation information, see [docs/migration/TEST262.md](../../docs/migration/TEST262.md).
