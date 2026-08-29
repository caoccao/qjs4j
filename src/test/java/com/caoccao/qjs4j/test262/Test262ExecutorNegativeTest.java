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
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A negative test carries two claims: the phase the error occurs in, and the constructor it is an
 * instance of. Checking neither is what let a program that parses cleanly and throws at run time
 * pass a parse-phase test, and let {@code throw "SyntaxError"} pass a typed one.
 * <p>
 * The cases here use the {@code raw} flag so no harness file is read; they run against the engine
 * alone and do not need a Test262 checkout.
 */
public class Test262ExecutorNegativeTest {
    private final Test262Executor executor = new Test262Executor(new HarnessLoader(Paths.get("../test262")), 500, 5000);

    private Test262TestCase negativeCase(String code, String phase, String type, String... extraFlags) {
        Test262TestCase testCase = new Test262TestCase(Paths.get("synthetic/negative.js"));
        Set<String> flags = new java.util.HashSet<>(Set.of("raw"));
        flags.addAll(Set.of(extraFlags));
        testCase.setFlags(flags);
        testCase.setCode(code);
        testCase.setNegative(new Test262TestCase.NegativeInfo(phase, type));
        testCase.setVariant(Test262TestCase.Variant.RAW);
        return testCase;
    }

    @Test
    void testAsyncDoneErrorMatchesOnConstructorNotMessage() {
        // The message names another error class; the constructor is what counts.
        Test262TestCase misleading = negativeCase(
                "$DONE(new TypeError('this mentions SyntaxError'));", "runtime", "SyntaxError", "async");
        TestResult misleadingResult = executor.execute(misleading);
        assertThat(misleadingResult.isPassed()).isFalse();
        assertThat(misleadingResult.getMessage()).contains("Expected SyntaxError but got TypeError");

        Test262TestCase matching = negativeCase(
                "$DONE(new TypeError('boom'));", "runtime", "TypeError", "async");
        assertThat(executor.execute(matching).isPassed()).isTrue();
    }

    @Test
    void testAsyncDoneWithNonErrorFails() {
        Test262TestCase testCase = negativeCase("$DONE('SyntaxError');", "runtime", "SyntaxError", "async");
        TestResult result = executor.execute(testCase);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("a thrown string");
    }

    @Test
    void testAsyncNegativeRejectsNonRuntimePhase() {
        Test262TestCase testCase = negativeCase(
                "$DONE(new SyntaxError('late'));", "parse", "SyntaxError", "async");
        TestResult result = executor.execute(testCase);
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    void testParsePhaseAcceptsRealSyntaxError() {
        assertThat(executor.execute(negativeCase("var 1x = 2;", "parse", "SyntaxError")).isPassed()).isTrue();
    }

    @Test
    void testParsePhaseRejectsRuntimeThrow() {
        // The exact false pass the review reproduced: a program that parses and throws later.
        Test262TestCase testCase = negativeCase(
                "throw new SyntaxError('runtime, not parse');", "parse", "SyntaxError");
        TestResult result = executor.execute(testCase);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("the source compiled successfully");
    }

    @Test
    void testParsePhaseRejectsWrongErrorType() {
        Test262TestCase testCase = negativeCase("var 1x = 2;", "parse", "TypeError");
        TestResult result = executor.execute(testCase);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("Expected a parse-phase TypeError");
    }

    @Test
    void testRuntimePhaseAcceptsMatchingConstructor() {
        assertThat(executor.execute(
                negativeCase("null.x;", "runtime", "TypeError")).isPassed()).isTrue();
        assertThat(executor.execute(
                negativeCase("throw new SyntaxError('boom');", "runtime", "SyntaxError")).isPassed()).isTrue();
    }

    @Test
    void testRuntimePhaseRejectsParseFailure() {
        // A runtime-phase test must get past compilation first.
        Test262TestCase testCase = negativeCase("var 1x = 2;", "runtime", "SyntaxError");
        TestResult result = executor.execute(testCase);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("failed to compile");
    }

    @Test
    void testRuntimePhaseRejectsThrownStringNamingTheErrorClass() {
        // The other false pass the review reproduced: `throw 'SyntaxError'` is not a SyntaxError.
        Test262TestCase testCase = negativeCase("throw 'SyntaxError';", "runtime", "SyntaxError");
        TestResult result = executor.execute(testCase);
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessage()).contains("a thrown string");
    }

    @Test
    void testStrictVariantPrependsUseStrictDirective() {
        // `var public` is only an error under a strict prologue, so this passes as a parse-phase
        // SyntaxError in the strict variant and fails to be one in the non-strict variant.
        Test262TestCase strict = negativeCase("var public = 1;", "parse", "SyntaxError");
        strict.setVariant(Test262TestCase.Variant.STRICT);
        assertThat(executor.execute(strict).isPassed()).isTrue();

        Test262TestCase nonStrict = negativeCase("var public = 1;", "parse", "SyntaxError");
        nonStrict.setVariant(Test262TestCase.Variant.NON_STRICT);
        assertThat(executor.execute(nonStrict).isPassed()).isFalse();
    }
}
