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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSSharedArrayBuffer;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.core.JSValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SharedArrayBuffer constructor methods.
 */
public class SharedArrayBufferConstructorTest extends BaseJavetTest {
    @Test
    public void testCreateSharedArrayBuffer() {
        // Normal case: create with valid length
        JSValue result = JSSharedArrayBuffer.create(context, new JSNumber(32));
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class, jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(32));

        // Normal case: create with zero length
        result = JSSharedArrayBuffer.create(context, new JSNumber(0));
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class, jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(0));

        // Edge case: negative length
        assertRangeError(JSSharedArrayBuffer.create(context, new JSNumber(-1)));

        // Edge case: non-numeric length
        result = JSSharedArrayBuffer.create(context, new JSString("32"));
        assertThat(result).isInstanceOfSatisfying(JSSharedArrayBuffer.class, jsSab -> assertThat(jsSab.getByteLength()).isEqualTo(32));
    }

    @Test
    void testTypeof() {
        assertStringWithJavet("typeof SharedArrayBuffer");

        assertIntegerWithJavet("SharedArrayBuffer.length");

        assertStringWithJavet("SharedArrayBuffer.name");

        assertStringWithJavet(
                "new SharedArrayBuffer().toString()",
                "new SharedArrayBuffer(10).toString()");

        assertErrorWithJavet("SharedArrayBuffer()");
    }
}