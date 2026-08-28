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

package com.caoccao.qjs4j.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JSTypeConversionsTest {
    @Test
    public void testRepeatedStringToNumberConversion() {
        JSString source = new JSString("17.0000000000000000000000000000000000000000000000000000001");
        assertThat(JSTypeConversions.toNumber(null, source).value()).isEqualTo(17);
        assertThat(JSTypeConversions.toNumber(null, source).value()).isEqualTo(17);
    }
}
