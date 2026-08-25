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
 * <p>
 * The failed-delete and failed-assignment messages name the receiver, and V8's rule for that is
 * subtler than it looks: {@code Object::NoSideEffectsToMaybeString} produces {@code #<Ctor>} only
 * while the object's {@code toString} is still {@code Object.prototype.toString}, so an Array or a
 * Date — which carry their own — fall through to {@code [object Tag]} instead. Reporting
 * {@code #<Array>} for an array was the visible symptom of not implementing that test.
 */
public class JSErrorMessageParityTest extends BaseJavetTest {

    @Test
    public void testBlockedArrayLengthTruncationNamesTheElement() {
        // The delete is the operation that actually failed, so V8 reports that rather than the
        // length assignment. Both the strict-mode assignment and Object.defineProperty report it;
        // Reflect.defineProperty still just answers false.
        assertStringWithJavet(
                """
                        (function () {
                          'use strict';
                          const a = [1, 2, 3];
                          Object.defineProperty(a, '1', { value: 9, configurable: false });
                          try { a.length = 0; return 'NO ERROR' } catch (e) { return e.name + ': ' + e.message }
                        })()""",
                """
                        (function () {
                          'use strict';
                          const a = [];
                          Object.defineProperty(a, '2147483648', { value: 1, configurable: false });
                          try { a.length = 0; return 'NO ERROR' } catch (e) { return e.name + ': ' + e.message }
                        })()""",
                """
                        (function () {
                          const a = [1, 2, 3];
                          Object.defineProperty(a, '1', { value: 9, configurable: false });
                          try { Object.defineProperty(a, 'length', { value: 0 }); return 'NO ERROR' }
                          catch (e) { return e.name + ': ' + e.message }
                        })()""",
                """
                        (function () {
                          const a = [1, 2, 3];
                          Object.defineProperty(a, '1', { value: 9, configurable: false });
                          return String(Reflect.defineProperty(a, 'length', { value: 0 })) + ',' + a.length;
                        })()""");
    }

    @Test
    public void testFailedAssignmentNamesTheReceiverTheWayV8Does() {
        assertStringWithJavet(
                """
                        (function () {
                          'use strict';
                          const o = { v: 1 };
                          Object.freeze(o);
                          try { o.v = 2; return 'NO ERROR' } catch (e) { return e.message }
                        })()""",
                """
                        (function () {
                          'use strict';
                          class Widget { constructor() { this.v = 1 } }
                          const o = new Widget();
                          Object.freeze(o);
                          try { o.v = 2; return 'NO ERROR' } catch (e) { return e.message }
                        })()""",
                """
                        (function () {
                          'use strict';
                          const a = [1, 2];
                          Object.freeze(a);
                          try { a[0] = 9; return 'NO ERROR' } catch (e) { return e.message }
                        })()""");
    }

    @Test
    public void testFailedDeleteNamesTheReceiverTheWayV8Does() {
        // #<Ctor> for an ordinary object, [object Tag] for anything with its own toString.
        assertStringWithJavet(
                """
                        (function () {
                          'use strict';
                          const o = {};
                          Object.defineProperty(o, 'x', { value: 1, configurable: false });
                          try { delete o.x; return 'NO ERROR' } catch (e) { return e.message }
                        })()""",
                """
                        (function () {
                          'use strict';
                          class Widget {}
                          const o = new Widget();
                          Object.defineProperty(o, 'x', { value: 1, configurable: false });
                          try { delete o.x; return 'NO ERROR' } catch (e) { return e.message }
                        })()""",
                """
                        (function () {
                          'use strict';
                          const a = [1, 2, 3];
                          Object.defineProperty(a, '1', { value: 9, configurable: false });
                          try { delete a[1]; return 'NO ERROR' } catch (e) { return e.message }
                        })()""",
                """
                        (function () {
                          'use strict';
                          const d = new Date();
                          Object.defineProperty(d, 'x', { value: 1, configurable: false });
                          try { delete d.x; return 'NO ERROR' } catch (e) { return e.message }
                        })()""",
                """
                        (function () {
                          'use strict';
                          const m = new Map();
                          Object.defineProperty(m, 'x', { value: 1, configurable: false });
                          try { delete m.x; return 'NO ERROR' } catch (e) { return e.message }
                        })()""",
                """
                        (function () {
                          'use strict';
                          const o = { [Symbol.toStringTag]: 'Custom', toString() { return 'c' } };
                          Object.defineProperty(o, 'x', { value: 1, configurable: false });
                          try { delete o.x; return 'NO ERROR' } catch (e) { return e.message }
                        })()""");
    }

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
