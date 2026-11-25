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
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Appending to a string in a loop is linear. Each {@code +=} used to copy the whole string, so three million appends
 * meant copying about 4.5 trillion characters; with ropes they finish in about a second. The timeout is the assertion,
 * set an order of magnitude above the expected time so a slow runner does not trip it.
 */
@Tag("performance")
public class StringConcatenationPerformanceTest extends BaseTest {
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testAppendingMillionsOfCharactersIsLinear() {
        JSValue value = context.eval("""
                (() => {
                  let s = '';
                  for (let i = 0; i < 3000000; i++) {
                    s += 'x';
                    if (s.length !== i + 1) throw new Error('length');
                  }
                  return s.charAt(1500000) + s.length;
                })()
                """);
        assertStringValue(value, "x3000000");
    }
}
