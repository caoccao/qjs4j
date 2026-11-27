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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.JSArray;
import com.caoccao.qjs4j.core.JSBoolean;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end regression for https://github.com/caoccao/qjs4j/issues/7.
 */
@Tag("performance")
public class OctaneV7Issue7PerformanceTest extends BaseTest {
    private static final List<String> EXPECTED_SUITE_NAMES = List.of("Richards", "DeltaBlue", "Crypto", "RayTrace",
            "EarleyBoyer", "RegExp", "Splay", "NavierStokes");

    @Test
    public void testOctaneV7Issue7ThroughContextEval() throws IOException {
        // The fixture keeps all eight workloads and uses a short timing window for the regression test.
        JSValue value = context.eval(loadCode("performance/octane-v7-issue-7.js"));

        assertThat(value).isInstanceOfSatisfying(JSArray.class, testResult -> {
            assertThat(testResult.get(0)).isEqualTo(JSBoolean.TRUE);
            assertThat(testResult.get(1)).isInstanceOfSatisfying(JSArray.class, output -> {
                List<String> outputLines = new ArrayList<>((int) output.getLength());
                for (int index = 0; index < output.getLength(); index++) {
                    assertThat(output.get(index)).isInstanceOf(JSString.class);
                    outputLines.add(((JSString) output.get(index)).value());
                }
                assertThat(outputLines).hasSize(EXPECTED_SUITE_NAMES.size() + 2);
                for (int index = 0; index < EXPECTED_SUITE_NAMES.size(); index++) {
                    assertThat(outputLines.get(index)).startsWith(EXPECTED_SUITE_NAMES.get(index) + ": ");
                }
                assertThat(outputLines.get(EXPECTED_SUITE_NAMES.size())).isEqualTo("----");
                assertThat(outputLines.get(EXPECTED_SUITE_NAMES.size() + 1)).startsWith("Score (version 7): ");
            });
        });
    }
}
