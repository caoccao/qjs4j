# Test262 Runner Implementation Plan

## Overview

This document outlines the plan to implement a Test262 conformance test runner for qjs4j. Test262 is the official ECMAScript conformance test suite maintained by TC39. Implementing a runner will allow us to validate qjs4j's ECMAScript compliance and track implementation progress.

### Goals

1. Parse and execute test262 tests from the official test suite
2. Support all test262 flags (async, module, raw, strict modes)
3. Provide comprehensive test result reporting
4. Integrate with existing qjs4j test infrastructure (JUnit)
5. Enable continuous tracking of ECMAScript compliance

### Non-Goals

- Copying test262 source code into qjs4j (always reference `../test262`)
- Adding external YAML parsing libraries (keep zero external dependencies for core functionality)
- Implementing missing ECMAScript features (just test existing ones)

## Architecture

### Component Overview

```
src/test/java/com/caoccao/qjs4j/test262/
├── Test262Runner.java          # Main runner - discovers and executes tests
├── Test262Test.java            # JUnit integration for IDE/Gradle test runs
├── Test262TestCase.java        # Represents a single test case with metadata
├── Test262Parser.java          # Parse YAML frontmatter and JavaScript code
├── Test262Executor.java        # Execute tests with proper flag handling
├── Test262Reporter.java        # Track and report test results
├── Test262Config.java          # Configuration (paths, filters, timeouts)
└── harness/
    └── HarnessLoader.java      # Load and cache harness files
```

### Data Flow

```
1. Discovery: Walk ../test262/test/ directory → Find all .js files
2. Parsing: Test262Parser → Extract frontmatter + code → Test262TestCase
3. Execution: Test262Executor → Load harness → Execute with flags → Result
4. Reporting: Test262Reporter → Aggregate results → Output summary
```

## Test262 File Format

### YAML Frontmatter Structure

Test262 tests use YAML frontmatter enclosed in `/*---` and `---*/`:

```javascript
/*---
description: Description of what this test validates
esid: sec-ecmascript-section-id
features: [feature1, feature2]
flags: [async, module, onlyStrict, noStrict, raw]
includes: [assert.js, sta.js, otherHelper.js]
negative:
  phase: parse|resolution|runtime
  type: SyntaxError|ReferenceError|TypeError|etc
---*/

// JavaScript test code here
```

### Metadata Fields

- **description**: Human-readable test description
- **esid**: ECMAScript specification section ID
- **features**: Array of required features (e.g., `async-iteration`, `class-fields-private`)
- **flags**: Test execution modifiers
  - `async`: Test uses `$DONE()` callback
  - `module`: Execute as ES6 module
  - `raw`: Don't load standard harness files
  - `onlyStrict`: Run only in strict mode
  - `noStrict`: Run only in non-strict mode
- **includes**: Additional harness files to load
- **negative**: Expected error (null for positive tests)
  - `phase`: When error should occur (parse/resolution/runtime)
  - `type`: Error constructor name (e.g., "SyntaxError")

## Implementation Details

### 1. Test262Parser.java

**Responsibilities:**
- Extract YAML frontmatter using regex pattern
- Parse YAML subset (key-value pairs, arrays)
- Extract JavaScript code portion
- Create Test262TestCase objects

**Implementation Strategy:**

```java
public class Test262Parser {
    private static final Pattern FRONTMATTER_PATTERN = 
        Pattern.compile("/\\*---\\s*(.+?)\\s*---\\*/", Pattern.DOTALL);
    
    public static Test262TestCase parse(Path testFile) throws IOException {
        String content = Files.readString(testFile);
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        
        Test262TestCase testCase = new Test262TestCase();
        testCase.setPath(testFile);
        
        if (matcher.find()) {
            String yaml = matcher.group(1);
            parseYaml(yaml, testCase);
            
            // Extract JavaScript code after frontmatter
            int codeStart = matcher.end();
            testCase.setCode(content.substring(codeStart).trim());
        } else {
            // No frontmatter - entire file is code
            testCase.setCode(content);
        }
        
        return testCase;
    }
    
    private static void parseYaml(String yaml, Test262TestCase testCase) {
        // Simple YAML parsing without external dependencies
        // Handle: description, flags, features, includes, negative
        String[] lines = yaml.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("description:")) {
                testCase.setDescription(extractString(line));
            } else if (line.startsWith("flags:")) {
                testCase.setFlags(parseArray(line));
            } else if (line.startsWith("features:")) {
                testCase.setFeatures(parseArray(line));
            } else if (line.startsWith("includes:")) {
                testCase.setIncludes(parseArray(line));
            } else if (line.startsWith("negative:")) {
                testCase.setNegative(parseNegative(lines, currentIndex));
            }
        }
    }
}
```

**YAML Parsing Notes:**
- Keep it simple - we only need basic key-value and array parsing
- No need for full YAML spec compliance
- Focus on test262-specific structure

### 2. HarnessLoader.java

**Responsibilities:**
- Load harness files from `../test262/harness/`
- Cache harness content for reuse
- Inject harness into JSContext global scope

**Core Harness Files:**
- **assert.js**: Assertion functions (`assert()`, `assert.sameValue()`, `assert.throws()`, etc.)
- **sta.js**: Shared Test API - common utilities and helpers
- **Others**: Test-specific helpers referenced in `includes` field

**Implementation:**

```java
public class HarnessLoader {
    private final Path harnessDir;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    
    public HarnessLoader(Path test262Root) {
        this.harnessDir = test262Root.resolve("harness");
    }
    
    public String loadHarness(String filename) throws IOException {
        return cache.computeIfAbsent(filename, fn -> {
            try {
                Path file = harnessDir.resolve(fn);
                return Files.readString(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
    
    public void loadIntoContext(JSContext context, Set<String> includes) 
            throws IOException, JSException {
        // Always load standard harness unless 'raw' flag is set
        for (String include : includes) {
            String code = loadHarness(include);
            context.eval(code, include, false);
        }
    }
}
```

### 3. Test262Executor.java

**Responsibilities:**
- Create JSRuntime and JSContext for each test
- Load appropriate harness files
- Handle execution flags (async, module, strict)
- Catch and validate exceptions
- Return test results

**Execution Strategy:**

```java
public class Test262Executor {
    private final HarnessLoader harnessLoader;
    private final long asyncTimeoutMs = 5000; // 5 second timeout
    
    public TestResult execute(Test262TestCase test) {
        try (JSRuntime runtime = new JSRuntime();
             JSContext context = runtime.createContext()) {
            
            // Load harness files unless 'raw' flag is present
            if (!test.hasFlag("raw")) {
                Set<String> includes = getDefaultIncludes();
                includes.addAll(test.getIncludes());
                harnessLoader.loadIntoContext(context, includes);
            }
            
            // Prepare code with strict mode if needed
            String code = prepareCode(test);
            
            // Execute based on flags
            if (test.hasFlag("async")) {
                return executeAsync(context, runtime, code, test);
            } else if (test.hasFlag("module")) {
                return executeModule(context, runtime, code, test);
            } else {
                return executeScript(context, runtime, code, test);
            }
            
        } catch (Exception e) {
            return handleException(e, test);
        }
    }
    
    private String prepareCode(Test262TestCase test) {
        String code = test.getCode();
        
        // Handle strict mode flags
        if (test.hasFlag("onlyStrict") && !code.startsWith("\"use strict\"")) {
            code = "\"use strict\";\n" + code;
        }
        
        return code;
    }
    
    private TestResult executeAsync(JSContext context, JSRuntime runtime, 
                                     String code, Test262TestCase test) {
        // Set up $DONE callback
        AtomicReference<Object> doneResult = new AtomicReference<>();
        CountDownLatch doneLatch = new CountDownLatch(1);
        
        context.getGlobalObject().defineProperty("$DONE", 
            new JSNativeFunction("$DONE", 1, (ctx, thisArg, args) -> {
                if (args.length > 0 && !args[0].isUndefined()) {
                    doneResult.set(args[0]); // Error passed to $DONE
                } else {
                    doneResult.set(null); // Success
                }
                doneLatch.countDown();
                return JSUndefined.INSTANCE;
            })
        );
        
        try {
            // Execute test code
            context.eval(code, test.getPath().toString(), false);
            
            // Process microtasks (promises)
            runtime.runJobs();
            
            // Wait for $DONE with timeout
            boolean completed = doneLatch.await(asyncTimeoutMs, TimeUnit.MILLISECONDS);
            
            if (!completed) {
                return TestResult.timeout(test);
            }
            
            Object result = doneResult.get();
            if (result != null) {
                // $DONE was called with an error
                if (test.getNegative() != null) {
                    return checkNegativeResult(result, test);
                } else {
                    return TestResult.fail(test, "Async error: " + result);
                }
            } else {
                // $DONE was called without error
                if (test.getNegative() != null) {
                    return TestResult.fail(test, "Expected error not thrown");
                } else {
                    return TestResult.pass(test);
                }
            }
            
        } catch (JSException e) {
            return handleException(e, test);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TestResult.timeout(test);
        }
    }
    
    private TestResult executeModule(JSContext context, JSRuntime runtime,
                                     String code, Test262TestCase test) 
            throws JSException {
        try {
            context.evalModule(code, test.getPath().toString());
            runtime.runJobs();
            
            if (test.getNegative() != null) {
                return TestResult.fail(test, "Expected error not thrown");
            }
            return TestResult.pass(test);
            
        } catch (JSException e) {
            return handleException(e, test);
        }
    }
    
    private TestResult executeScript(JSContext context, JSRuntime runtime,
                                     String code, Test262TestCase test) 
            throws JSException {
        try {
            context.eval(code, test.getPath().toString(), false);
            runtime.runJobs();
            
            if (test.getNegative() != null) {
                return TestResult.fail(test, "Expected error not thrown");
            }
            return TestResult.pass(test);
            
        } catch (JSException e) {
            return handleException(e, test);
        }
    }
    
    private TestResult handleException(Exception e, Test262TestCase test) {
        if (test.getNegative() == null) {
            // Unexpected error
            return TestResult.fail(test, "Unexpected error: " + e.getMessage());
        }
        
        // Check if error type matches expected
        NegativeInfo negative = test.getNegative();
        String errorType = extractErrorType(e);
        
        if (errorType.equals(negative.getType())) {
            return TestResult.pass(test);
        } else {
            return TestResult.fail(test, 
                "Expected " + negative.getType() + " but got " + errorType);
        }
    }
    
    private String extractErrorType(Exception e) {
        if (e instanceof JSException) {
            JSException jsException = (JSException) e;
            JSValue error = jsException.getErrorValue();
            
            if (error instanceof JSObject) {
                JSObject errorObj = (JSObject) error;
                JSValue nameValue = errorObj.get("name");
                if (nameValue != null) {
                    return nameValue.toString();
                }
            }
        }
        
        // Fallback to Java exception type
        return e.getClass().getSimpleName();
    }
}
```

### 4. Test262Runner.java

**Responsibilities:**
- Discover test files in `../test262/test/`
- Filter tests based on configuration
- Execute tests using Test262Executor
- Report results using Test262Reporter

**Implementation:**

```java
public class Test262Runner {
    private final Path test262Root;
    private final Test262Config config;
    private final Test262Parser parser;
    private final Test262Executor executor;
    private final Test262Reporter reporter;
    
    public Test262Runner(Path test262Root, Test262Config config) {
        this.test262Root = test262Root;
        this.config = config;
        this.parser = new Test262Parser();
        this.executor = new Test262Executor(new HarnessLoader(test262Root));
        this.reporter = new Test262Reporter();
    }
    
    public void run() throws IOException {
        Path testsDir = test262Root.resolve("test");
        
        List<Path> testFiles = discoverTests(testsDir);
        System.out.println("Discovered " + testFiles.size() + " tests");
        
        for (Path testFile : testFiles) {
            Test262TestCase testCase = parser.parse(testFile);
            
            // Apply filters
            if (shouldSkipTest(testCase)) {
                reporter.recordSkipped(testCase);
                continue;
            }
            
            // Execute test
            TestResult result = executor.execute(testCase);
            reporter.recordResult(result);
            
            // Print progress
            if (reporter.getTotalExecuted() % 100 == 0) {
                reporter.printProgress();
            }
        }
        
        reporter.printSummary();
    }
    
    private List<Path> discoverTests(Path testsDir) throws IOException {
        List<Path> testFiles = new ArrayList<>();
        
        Files.walk(testsDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".js"))
            .filter(p -> !p.toString().contains("_FIXTURE"))
            .forEach(testFiles::add);
        
        return testFiles;
    }
    
    private boolean shouldSkipTest(Test262TestCase test) {
        // Skip based on missing features
        for (String feature : test.getFeatures()) {
            if (config.isFeatureUnsupported(feature)) {
                return true;
            }
        }
        
        // Skip based on include/exclude patterns
        if (!config.matchesIncludePattern(test.getPath())) {
            return true;
        }
        
        return false;
    }
    
    public static void main(String[] args) throws IOException {
        Path test262Root = args.length > 0 
            ? Paths.get(args[0]) 
            : Paths.get("../test262");
        
        Test262Config config = Test262Config.loadDefault();
        Test262Runner runner = new Test262Runner(test262Root, config);
        runner.run();
    }
}
```

### 5. Test262Test.java (JUnit Integration)

**Responsibilities:**
- Integrate with JUnit test infrastructure
- Allow running test262 tests from IDE or Gradle
- Extend BaseTest for consistent test setup

**Implementation:**

```java
public class Test262Test extends BaseTest {
    private static final Path TEST262_ROOT = Paths.get("../test262");
    
    @TestFactory
    Collection<DynamicTest> test262Suite() throws IOException {
        Test262Config config = Test262Config.loadDefault();
        Test262Runner runner = new Test262Runner(TEST262_ROOT, config);
        Test262Parser parser = new Test262Parser();
        Test262Executor executor = new Test262Executor(
            new HarnessLoader(TEST262_ROOT)
        );
        
        Path testsDir = TEST262_ROOT.resolve("test");
        List<Path> testFiles = discoverTests(testsDir, config);
        
        return testFiles.stream()
            .map(testFile -> DynamicTest.dynamicTest(
                getTestName(testFile),
                () -> {
                    Test262TestCase testCase = parser.parse(testFile);
                    TestResult result = executor.execute(testCase);
                    
                    if (result.isFailed()) {
                        fail(result.getMessage());
                    } else if (result.isTimeout()) {
                        fail("Test timeout");
                    }
                    // Skip and pass are both successful
                }
            ))
            .collect(Collectors.toList());
    }
    
    private String getTestName(Path testFile) {
        return TEST262_ROOT.relativize(testFile).toString();
    }
}
```

### 6. Test262Reporter.java

**Responsibilities:**
- Track test results (pass/fail/skip/timeout)
- Calculate statistics
- Print progress and summary
- Optionally generate HTML/JSON reports

**Implementation:**

```java
public class Test262Reporter {
    private final AtomicInteger passed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger skipped = new AtomicInteger(0);
    private final AtomicInteger timeout = new AtomicInteger(0);
    
    private final List<TestResult> failures = 
        Collections.synchronizedList(new ArrayList<>());
    
    public void recordResult(TestResult result) {
        switch (result.getStatus()) {
            case PASS:
                passed.incrementAndGet();
                break;
            case FAIL:
                failed.incrementAndGet();
                failures.add(result);
                break;
            case SKIP:
                skipped.incrementAndGet();
                break;
            case TIMEOUT:
                timeout.incrementAndGet();
                failures.add(result);
                break;
        }
    }
    
    public void recordSkipped(Test262TestCase test) {
        skipped.incrementAndGet();
    }
    
    public int getTotalExecuted() {
        return passed.get() + failed.get() + timeout.get();
    }
    
    public void printProgress() {
        int total = getTotalExecuted();
        int pass = passed.get();
        int fail = failed.get();
        
        System.out.printf("Progress: %d tests (%d passed, %d failed)%n",
            total, pass, fail);
    }
    
    public void printSummary() {
        int total = getTotalExecuted() + skipped.get();
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("Test262 Results Summary");
        System.out.println("=".repeat(70));
        System.out.printf("Total tests:   %d%n", total);
        System.out.printf("Passed:        %d (%.1f%%)%n", 
            passed.get(), 100.0 * passed.get() / total);
        System.out.printf("Failed:        %d (%.1f%%)%n", 
            failed.get(), 100.0 * failed.get() / total);
        System.out.printf("Timeout:       %d (%.1f%%)%n", 
            timeout.get(), 100.0 * timeout.get() / total);
        System.out.printf("Skipped:       %d (%.1f%%)%n", 
            skipped.get(), 100.0 * skipped.get() / total);
        System.out.println("=".repeat(70));
        
        if (!failures.isEmpty()) {
            System.out.println("\nFailed Tests:");
            for (TestResult failure : failures) {
                System.out.printf("  ❌ %s%n", failure.getTestCase().getPath());
                System.out.printf("     %s%n", failure.getMessage());
            }
        }
    }
}
```

### 7. Test262Config.java

**Responsibilities:**
- Load configuration from file or environment
- Define unsupported features
- Define include/exclude patterns
- Set timeouts and other parameters

**Implementation:**

```java
public class Test262Config {
    private Set<String> unsupportedFeatures;
    private List<String> includePatterns;
    private List<String> excludePatterns;
    private long asyncTimeoutMs;
    
    public static Test262Config loadDefault() {
        Test262Config config = new Test262Config();
        
        // Define unsupported features
        config.unsupportedFeatures = Set.of(
            "Intl",
            "Intl.DateTimeFormat",
            "Intl.NumberFormat",
            "top-level-await",
            "import.meta",
            "hashbang",
            // Add more as discovered
        );
        
        // Default: run all tests
        config.includePatterns = List.of("**/*.js");
        config.excludePatterns = List.of("**/_FIXTURE.js");
        
        // 5 second timeout for async tests
        config.asyncTimeoutMs = 5000;
        
        return config;
    }
    
    public boolean isFeatureUnsupported(String feature) {
        return unsupportedFeatures.contains(feature);
    }
    
    public boolean matchesIncludePattern(Path testPath) {
        // Simple glob pattern matching
        String pathStr = testPath.toString();
        
        // Check exclusions first
        for (String exclude : excludePatterns) {
            if (matches(pathStr, exclude)) {
                return false;
            }
        }
        
        // Check inclusions
        for (String include : includePatterns) {
            if (matches(pathStr, include)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean matches(String path, String pattern) {
        // Convert glob pattern to regex
        String regex = pattern
            .replace(".", "\\.")
            .replace("**", ".*")
            .replace("*", "[^/]*");
        return path.matches(regex);
    }
}
```

## Testing Strategy

### Phase 1: Core Infrastructure

1. Implement Test262Parser with unit tests for:
   - Frontmatter extraction
   - YAML parsing
   - Code extraction
   - Edge cases (no frontmatter, complex YAML)

2. Implement HarnessLoader with tests for:
   - Loading harness files
   - Caching behavior
   - Missing file handling

3. Test basic execution with simple test cases:
   - Positive tests (should pass)
   - Negative tests (should throw expected error)
   - Async tests (use $DONE callback)
   - Module tests (use import/export)

### Phase 2: Test Suite Execution

1. Run small test suite (e.g., `test/language/expressions/addition/`)
2. Validate results manually
3. Fix issues found in executor or harness loading
4. Gradually expand to larger test suites

### Phase 3: Full Suite Integration

1. Run complete test262 suite
2. Categorize failures:
   - Missing features (expected failures - skip)
   - Bugs in qjs4j (track for fixes)
   - Runner issues (fix in runner)
3. Create baseline of expected results
4. Set up CI integration

### Phase 4: Continuous Testing

1. Run test262 suite in CI
2. Track compliance percentage over time
3. Alert on regressions
4. Update as new test262 tests are added

## Configuration and Filtering

### Feature-based Filtering

Skip tests that require unsupported features:

```java
unsupportedFeatures = [
    "Intl",                    // Internationalization API not implemented
    "top-level-await",         // Not implemented yet
    "import.meta",             // Not implemented
    "hashbang",                // Shebang support not needed
    "class-static-block",      // If not implemented
    // Add more as discovered
]
```

### Directory-based Filtering

Focus on specific test directories:

```java
// Run only language tests
includePatterns = ["test/language/**/*.js"]

// Skip Intl tests
excludePatterns = ["test/intl/**/*.js"]
```

### Progressive Approach

1. **Week 1**: Run `test/language/expressions/` and `test/language/statements/`
2. **Week 2**: Add `test/built-ins/Promise/` and `test/built-ins/Array/`
3. **Week 3**: Expand to more built-ins
4. **Week 4**: Run full suite, categorize results

## Build Integration

### Gradle Task

Add test262 task to `build.gradle.kts`:

```kotlin
tasks.register<JavaExec>("test262") {
    group = "verification"
    description = "Run Test262 conformance tests"
    
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.caoccao.qjs4j.test262.Test262Runner")
    
    args = listOf("../test262")
    
    // Increase heap for large test suite
    jvmArgs = listOf("-Xmx2g")
}
```

### JUnit Integration

Tests can be run via:
```bash
./gradlew test --tests "Test262Test"
```

Or from IDE with JUnit test runner.

## Expected Outcomes

### Compliance Metrics

Track over time:
- Total tests: ~40,000+ (test262 is large and growing)
- Passed: Target 80%+ for core features
- Failed: Categorize by reason
- Skipped: Unsupported features (expected)

### Failure Categories

1. **Expected failures**: Missing features (Intl, etc.) → Skip
2. **Known bugs**: Track in issues → Fix over time
3. **Runner bugs**: Issues with test execution → Fix immediately
4. **Test issues**: Upstream test262 bugs → Report upstream

### Success Criteria

- ✅ Runner executes tests correctly
- ✅ All test flags (async, module, strict) work
- ✅ Error matching for negative tests works
- ✅ Harness files load properly
- ✅ Results are accurate and reproducible
- ✅ Integration with build system works

## Open Questions / Decisions Needed

### 1. Test Filtering Strategy

**Options:**
- **A**: Run all tests initially (40k+ tests, long runtime)
- **B**: Start with specific directories (language/expressions, built-ins/Promise)
- **C**: Create feature-based filter from day one

**Recommendation**: Option B - start with core language tests, expand gradually

### 2. Async Timeout Handling

**Options:**
- **A**: Fixed 5-second timeout for all async tests
- **B**: Configurable per test (via config file)
- **C**: Different timeouts for different test directories

**Recommendation**: Option A - 5 seconds is reasonable, can adjust if needed

### 3. Reporting Format

**Options:**
- **A**: Console output only (like current implementation)
- **B**: Generate HTML report (matching existing test reports)
- **C**: JSON output for CI integration
- **D**: All of the above

**Recommendation**: Option D - console for interactive, HTML for local review, JSON for CI

### 4. Parallel Execution

**Options:**
- **A**: Sequential execution (simpler, easier to debug)
- **B**: Parallel execution (faster, but more complex)

**Recommendation**: Start with A (sequential), add B later for performance

### 5. Test262 Version Management

**Options:**
- **A**: User provides test262 directory (current approach)
- **B**: Git submodule for test262
- **C**: Download specific test262 version on demand

**Recommendation**: Option A for now (flexible, no version lock-in)

## Implementation Checklist

- [ ] Create directory structure: `src/test/java/com/caoccao/qjs4j/test262/`
- [ ] Implement Test262TestCase.java (data class)
- [ ] Implement Test262Parser.java with unit tests
- [ ] Implement HarnessLoader.java with unit tests
- [ ] Implement Test262Executor.java
  - [ ] Basic script execution
  - [ ] Module execution
  - [ ] Async execution with $DONE callback
  - [ ] Strict mode handling
  - [ ] Negative test handling
- [ ] Implement Test262Reporter.java
  - [ ] Console output
  - [ ] Progress tracking
  - [ ] Summary generation
- [ ] Implement Test262Config.java
  - [ ] Feature filtering
  - [ ] Directory filtering
  - [ ] Configuration loading
- [ ] Implement Test262Runner.java
  - [ ] Test discovery
  - [ ] Orchestration
  - [ ] Main method
- [ ] Implement Test262Test.java (JUnit integration)
- [ ] Add Gradle task
- [ ] Document usage in README
- [ ] Run initial test suite and establish baseline
- [ ] Set up CI integration

## Timeline Estimate

- **Phase 1** (Core Infrastructure): 2-3 days
  - Parser, Harness Loader, basic Executor
  - Unit tests for each component
  
- **Phase 2** (Integration): 2-3 days
  - Full Executor with all flags
  - Reporter and Config
  - Runner orchestration
  
- **Phase 3** (Testing & Refinement): 3-5 days
  - Run test suites
  - Fix bugs found
  - Optimize performance
  
- **Phase 4** (Documentation & CI): 1-2 days
  - Usage documentation
  - CI integration
  - Baseline establishment

**Total**: 8-13 days

## References

- [Test262 GitHub Repository](https://github.com/tc39/test262)
- [Test262 Contributing Guide](https://github.com/tc39/test262/blob/main/CONTRIBUTING.md)
- [ECMAScript Specification](https://tc39.es/ecma262/)
- qjs4j existing test infrastructure in `src/test/java/com/caoccao/qjs4j/`

## Future Enhancements

1. **Performance optimization**: Parallel test execution
2. **CI integration**: Run on every commit, track trends
3. **Feature coverage tracking**: Map tests to ECMAScript features
4. **Regression detection**: Alert when previously passing tests fail
5. **HTML dashboard**: Visual representation of compliance over time
6. **Test262 version tracking**: Test against multiple test262 versions
