/*
 * Copyright (c) 2025-2026. caoccao.com Sam Cao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.caoccao.qjs4j.test262;

import com.caoccao.qjs4j.test262.harness.HarnessLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A negative test's {@code type} names the constructor the thrown value must be an instance of.
 * Finding that out by reading {@code thrown.constructor.name} asks the failing program to describe
 * its own failure: every part of that expression is guest-writable, so
 * {@code throw \{ constructor: \{ name: 'TypeError' \} \}} satisfied a test that requires a real
 * {@code TypeError}, and the reads could run an accessor or a Proxy trap while the runner was
 * classifying a failure.
 * <p>
 * The type is now decided by identity the test cannot forge: native errors are sealed Java classes,
 * and {@code Test262Error} is matched against the harness prototype captured before the test ran.
 */
public class Test262NegativeTypeIdentityTest {
    @TempDir
    Path test262Root;
    private Test262Executor executor;
    private Path testDirectory;

    private Test262TestCase harnessCase(String code, String type) {
        Test262TestCase testCase = new Test262TestCase(testDirectory.resolve("synthetic.js"));
        testCase.setCode(code);
        testCase.setNegative(new Test262TestCase.NegativeInfo("runtime", type));
        testCase.setVariant(Test262TestCase.Variant.NON_STRICT);
        return testCase;
    }

    private Test262TestCase rawCase(String code, String type) {
        Test262TestCase testCase = new Test262TestCase(testDirectory.resolve("synthetic.js"));
        testCase.setFlags(Set.of("raw"));
        testCase.setCode(code);
        testCase.setNegative(new Test262TestCase.NegativeInfo("runtime", type));
        testCase.setVariant(Test262TestCase.Variant.NON_STRICT);
        return testCase;
    }

    @BeforeEach
    public void setUp() throws IOException {
        Path harnessDirectory = test262Root.resolve("harness");
        Files.createDirectories(harnessDirectory);
        Files.writeString(harnessDirectory.resolve("assert.js"),
                "function assert(c, m) { if (!c) throw new Error(m); }\n");
        Files.writeString(harnessDirectory.resolve("sta.js"),
                "function Test262Error(message) { this.message = message || ''; }\n"
                        + "Test262Error.prototype.toString = function () {\n"
                        + "  return 'Test262Error: ' + this.message;\n"
                        + "};\n"
                        + "function $DONOTEVALUATE() {\n"
                        + "  throw 'Test262: This statement should not be evaluated.';\n"
                        + "}\n");
        testDirectory = test262Root.resolve("test");
        Files.createDirectories(testDirectory);
        executor = new Test262Executor(new HarnessLoader(test262Root), 500, 5000);
    }

    @Test
    void testAForgedConstructorOnAnAccessorDoesNotSatisfyATypedNegative() {
        TestResult result = executor.execute(rawCase(
                "throw Object.defineProperty({}, 'constructor', "
                        + "{ get: function () { return { name: 'RangeError' }; } });",
                "RangeError"));
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    void testANativeErrorDoesNotSatisfyADifferentNativeType() {
        assertThat(executor.execute(rawCase("throw new RangeError('e');", "TypeError")).isPassed())
                .isFalse();
        assertThat(executor.execute(rawCase("throw new Error('e');", "TypeError")).isPassed())
                .isFalse();
        // A subclass of Error is not an Error for this purpose either way round.
        assertThat(executor.execute(rawCase("throw new TypeError('e');", "Error")).isPassed())
                .isFalse();
    }

    @Test
    void testAPlainObjectWithAForgedConstructorDoesNotSatisfyATypedNegative() {
        // The review's reproduction.
        TestResult result = executor.execute(
                rawCase("throw { constructor: { name: 'TypeError' } };", "TypeError"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("Expected TypeError");
    }

    @Test
    void testAProxyThatAnswersEveryReadDoesNotSatisfyATypedNegative() {
        TestResult result = executor.execute(rawCase(
                "throw new Proxy({}, { get: function (t, k) { "
                        + "return k === 'constructor' ? { name: 'SyntaxError' } : 'SyntaxError'; } });",
                "SyntaxError"));
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    void testARenamedNativeConstructorDoesNotChangeWhatItsInstancesAre() {
        TestResult result = executor.execute(rawCase(
                "Object.defineProperty(TypeError, 'name', { value: 'RangeError' });\n"
                        + "throw new TypeError('still a TypeError');",
                "TypeError"));
        assertThat(result.isPassed()).as(result.getMessage()).isTrue();
    }

    @Test
    void testAThrownStringDoesNotSatisfyATypedNegative() {
        assertThat(executor.execute(rawCase("throw 'TypeError';", "TypeError")).isPassed()).isFalse();
        assertThat(executor.execute(rawCase("throw 'Test262Error';", "Test262Error")).isPassed()).isFalse();
    }

    @Test
    void testAnErrorWhoseConstructorPropertyWasOverwrittenIsStillItself() {
        // The value is a real TypeError. Rewriting the property that used to be consulted must not
        // change what it is — in either direction.
        TestResult result = executor.execute(rawCase(
                "var e = new TypeError('real'); e.constructor = { name: 'RangeError' }; throw e;",
                "TypeError"));
        assertThat(result.isPassed()).as(result.getMessage()).isTrue();

        TestResult mislabelled = executor.execute(rawCase(
                "var e = new TypeError('real'); e.constructor = { name: 'RangeError' }; throw e;",
                "RangeError"));
        assertThat(mislabelled.isPassed()).isFalse();
    }

    @Test
    void testAnObjectInheritingTheHarnessPrototypeIsAccepted() {
        // Inheritance, not just direct instantiation: the chain is walked.
        TestResult result = executor.execute(harnessCase(
                "function Sub() {}\n"
                        + "Sub.prototype = Object.create(Test262Error.prototype);\n"
                        + "throw new Sub();",
                "Test262Error"));
        assertThat(result.isPassed()).as(result.getMessage()).isTrue();
    }

    @Test
    void testEveryNativeErrorTypeIsRecognised() {
        for (String[] nativeError : new String[][]{
                {"Error", "new Error('e')"},
                {"EvalError", "new EvalError('e')"},
                {"RangeError", "new RangeError('e')"},
                {"ReferenceError", "new ReferenceError('e')"},
                {"SyntaxError", "new SyntaxError('e')"},
                {"TypeError", "new TypeError('e')"},
                {"URIError", "new URIError('e')"},
                {"AggregateError", "new AggregateError([], 'e')"}}) {
            TestResult result = executor.execute(rawCase("throw " + nativeError[1] + ";", nativeError[0]));
            assertThat(result.isPassed())
                    .as(nativeError[0] + ": " + result.getMessage())
                    .isTrue();
        }
    }

    @Test
    void testReplacingTheHarnessErrorDoesNotLetAnImposterSatisfyIt() {
        // The prototype was captured while the harness was the only thing that had run, so a test
        // that installs its own Test262Error afterwards cannot make its objects answer to the name.
        TestResult result = executor.execute(harnessCase(
                "function Imposter() {}\n"
                        + "Test262Error = Imposter;\n"
                        + "throw new Imposter();",
                "Test262Error"));
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    void testTheHarnessErrorIsRecognisedByItsPrototype() {
        TestResult result = executor.execute(
                harnessCase("throw new Test262Error('real');", "Test262Error"));
        assertThat(result.isPassed()).as(result.getMessage()).isTrue();
    }
}
