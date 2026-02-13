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
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Unit tests for Iterator constructor static methods.
 */
public class IteratorConstructorTest extends BaseJavetTest {
    private JSValue evalError(String code) {
        try {
            context.eval(code);
            fail("Expected JSException for code: " + code);
            return null;
        } catch (JSException e) {
            return e.getErrorValue();
        }
    }

    @Test
    void testIteratorCannotBeConstructedDirectly() {
        assertErrorWithJavet(
                "new Iterator()",
                "Iterator()");
    }

    @Test
    void testIteratorConcat() {
        assertThat(context.eval("typeof Iterator.concat").toJavaObject()).isEqualTo("function");
        assertThat(context.eval("Iterator.concat([1, 2], new Set([3, 4])).toArray().join(',')").toJavaObject())
                .isEqualTo("1,2,3,4");
        assertThat(context.eval("Object.prototype.toString.call(Iterator.concat([1]))").toJavaObject())
                .isEqualTo("[object Iterator Concat]");
    }

    @Test
    void testIteratorConcatErrors() {
        assertTypeError(evalError("Iterator.concat(1)"));
        assertTypeError(evalError("Iterator.concat({})"));
        assertTypeError(evalError(
                "(() => { let it; const src = { [Symbol.iterator]() { return { next() { return it.next(); } }; } }; it = Iterator.concat(src); it.next(); })();"));
    }

    @Test
    void testIteratorName() throws Exception {
        assertStringWithJavet(
                "Iterator.name");
    }

    @Test
    void testIteratorPrototypeConstructorDescriptor() {
        assertStringWithJavet(
                "(() => { const d = Object.getOwnPropertyDescriptor(Iterator.prototype, 'constructor'); return typeof d.get + ',' + typeof d.set + ',' + d.enumerable + ',' + d.configurable; })();");
    }

    @Test
    void testIteratorRegistrationSemantics() {
        assertStringWithJavet(
                "Object.prototype.toString.call(Iterator.prototype);",
                "Object.prototype.toString.call(Iterator.from([1]).map(v => v));");
        assertBooleanWithJavet(
                "(() => { try { Iterator.prototype.constructor = 1; return false; } catch (e) { return e instanceof TypeError; } })();");
    }

    @Test
    void testIteratorToStringTagSemantics() {
        assertThat(context.eval("(() => { try { Iterator.prototype[Symbol.toStringTag] = 'X'; return 'OK'; } catch (e) { return e.name; } })();").toJavaObject())
                .isEqualTo("TypeError");
        assertThat(context.eval("(() => { const it = Iterator.from([1]).map(v => v); it[Symbol.toStringTag] = 'X'; return Object.prototype.toString.call(it); })();").toJavaObject())
                .isEqualTo("[object Iterator Helper]");
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet(
                "typeof Iterator;",
                "typeof Iterator.from",
                "typeof Iterator.prototype",
                "typeof Iterator.prototype.map",
                "typeof Iterator.prototype.filter",
                "typeof Iterator.prototype.toArray");
        assertIntegerWithJavet(
                "Iterator.length;");
    }
}
