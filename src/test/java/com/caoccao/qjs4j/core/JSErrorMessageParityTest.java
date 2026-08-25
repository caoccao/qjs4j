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

/**
 * Diagnostic messages that a developer reads must match V8's wording.
 * <p>
 * The temporal-dead-zone error said only {@code "variable is uninitialized"} — it named no
 * variable, so a TDZ error in a large function gave nothing to go on, even though the local
 * variable names are in the bytecode. Reading a property of {@code null} said
 * {@code "cannot read property 'foo' of null"} where V8 says
 * {@code "Cannot read properties of null (reading 'foo')"}.
 */
public class JSErrorMessageParityTest extends BaseJavetTest {

    @Test
    public void testNullPropertyReadMessage() {
        assertStringWithJavet(
                "try { null.foo } catch (e) { e.message }",
                "try { null.length } catch (e) { e.message }",
                "try { null['computed'] } catch (e) { e.message }");
    }

    @Test
    public void testTemporalDeadZoneMessageForAssignment() {
        assertStringWithJavet(
                """
                        (function () {
                            try { assigned = 5; let assigned } catch (e) { return e.message }
                        })()""");
    }

    @Test
    public void testTemporalDeadZoneMessageNamesTheVariable() {
        assertStringWithJavet(
                "try { tz; let tz = 1 } catch (e) { e.message }",
                "try { tc; const tc = 1 } catch (e) { e.message }",
                """
                        (function () {
                            try { inner; let inner = 1 } catch (e) { return e.message }
                        })()""");
    }

    @Test
    public void testUndefinedPropertyReadMessage() {
        assertStringWithJavet(
                "try { undefined.foo } catch (e) { e.message }",
                "try { undefined.length } catch (e) { e.message }",
                """
                        const holder = {};
                        try { holder.missing.deeper } catch (e) { e.message }""");
    }
}
