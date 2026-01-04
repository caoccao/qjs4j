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

package com.caoccao.qjs4j.performance;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.values.V8Value;
import com.caoccao.javet.values.reference.V8ValueArray;
import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSArray;
import com.caoccao.qjs4j.core.JSValue;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

/**
 * Performance test comparing V8 and qjs4j execution of prime number calculation.
 * Run with: ./gradlew performanceTest
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class PrimeNumberTest extends BaseJavetTest {

    private String primeNumberCode;

    @Benchmark
    public void benchmarkQjs4j() {
        resetContext();
        JSValue jsValue = context.eval(primeNumberCode);
        assertThat(jsValue).isInstanceOfSatisfying(JSArray.class, jsArray ->
                assertThat(jsArray.getLength()).isEqualTo(5133));
    }

    @Benchmark
    public void benchmarkV8() throws JavetException {
        v8Runtime.resetContext();
        try (V8Value v8Value = v8Runtime.getExecutor(primeNumberCode).execute()) {
            assertThat(v8Value).isInstanceOfSatisfying(V8ValueArray.class, v8ValueArray -> {
                try {
                    assertThat(v8ValueArray.getLength()).isEqualTo(5133);
                } catch (JavetException e) {
                    fail(e);
                }
            });
        }
    }

    @Setup
    public void jmhSetup() throws Exception {
        setUp();
    }

    @TearDown
    public void jmhTearDown() throws Exception {
        tearDown();
    }

    private void loadPrimeNumberCode() throws IOException {
        primeNumberCode = IOUtils.resourceToString(
                "performance/prime-number.js",
                StandardCharsets.UTF_8,
                getClass().getClassLoader());
    }

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loadPrimeNumberCode();
    }

    /**
     * JUnit test wrapper for qjs4j benchmark.
     */
    @Test
    @Tag("performance")
    public void testQjs4jPerformance() throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(this.getClass().getSimpleName() + ".benchmarkQjs4j")
                .build();
        new Runner(opt).run();
    }

    /**
     * JUnit test wrapper for V8 benchmark.
     */
    @Test
    @Tag("performance")
    public void testV8Performance() throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(this.getClass().getSimpleName() + ".benchmarkV8")
                .build();
        new Runner(opt).run();
    }
}
