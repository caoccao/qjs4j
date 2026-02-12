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

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class JSArraySemanticsTest extends BaseJavetTest {
    @Test
    public void testArrayConstructorLengthBounds() {
        assertStringWithJavet(
                "(() => String(new Array(4294967295).length))()",
                "(() => { try { new Array(4294967296); return 'no-error'; } catch (e) { return e.name; } })()"
        );
    }

    @Test
    public void testArrayLengthAssignmentConversion() {
        assertStringWithJavet(
                "(() => { const a = [1, 2, 3]; a.length = '2'; return JSON.stringify(a) + ':' + a.length; })()",
                "(() => { const a = [1, 2, 3]; try { a.length = 1.5; return 'no-error'; } catch (e) { return e.name + ':' + a.length; } })()",
                "(() => { const a = [1, 2, 3]; try { a.length = '1.5'; return 'no-error'; } catch (e) { return e.name + ':' + a.length; } })()"
        );
    }

    @Test
    public void testArrayNumericStringIndexAssignment() {
        assertStringWithJavet(
                "(() => { const a = []; a['1'] = 42; return a.length + ',' + a[1]; })()"
        );
    }

    @Test
    public void testArraySparseUnshift() {
        assertStringWithJavet(
                "(() => { const a = []; a[20000] = 1; a.unshift(0); return a[0] + ',' + a[20001] + ',' + a.length; })()"
        );
    }

    @Test
    public void testDenseArrayElementPropertySemantics() {
        assertStringWithJavet(
                "(() => JSON.stringify(Object.keys([1, 2])))()",
                "(() => { const d = Object.getOwnPropertyDescriptor([1], '0'); return d ? [d.value, d.writable, d.enumerable, d.configurable].join(',') : 'undefined'; })()",
                "(() => [1].hasOwnProperty('0') ? 'true' : 'false')()"
        );
    }
}
