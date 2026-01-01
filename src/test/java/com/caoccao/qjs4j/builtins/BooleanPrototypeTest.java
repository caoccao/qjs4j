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
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Unit tests for Boolean.prototype methods.
 */
public class BooleanPrototypeTest extends BaseJavetTest {
    @Test
    public void testEquals() {
        assertBooleanWithJavet(
                // Verify that loose equality passes between primitive and primitive
                "true == true",
                "true == false",
                "true == Boolean(true)",
                "true == Boolean(false)",
                // Verify that strict equality passes between primitive and primitive
                "true === true",
                "true === false",
                "true === Boolean(true)",
                "true === Boolean(false)",
                // Verify that loose equality passes between primitive and primitive
                "Boolean(true) == Boolean(true)",
                "Boolean(true) == Boolean(false)",
                // Verify that loose equality passes between primitive and object
                "true == new Boolean(true)",
                "true == new Boolean(false)",
                "Boolean(true) == new Boolean(true)",
                "Boolean(true) == new Boolean(false)",
                // Verify that loose equality fails between object and object
                "new Boolean(true) == new Boolean(true)",
                // Verify that strict equality fails between primitive and object
                "true === new Boolean(true)",
                // Verify that strict equality fails between object and object
                "new Boolean(true) === new Boolean(true)");
    }

    @Test
    public void testToString() {
        // Normal case: true
        JSValue result = BooleanPrototype.toString(context, JSBoolean.TRUE, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("true"));

        // Normal case: false
        result = BooleanPrototype.toString(context, JSBoolean.FALSE, new JSValue[]{});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("false"));

        // Edge case: called on non-boolean
        assertTypeError(BooleanPrototype.toString(context, new JSString("not boolean"), new JSValue[]{}));
        assertPendingException(context);

        // Edge case: Boolean object without PrimitiveValue
        JSObject invalidObj = new JSObject();
        assertTypeError(BooleanPrototype.toString(context, invalidObj, new JSValue[]{}));
        assertPendingException(context);
    }

    @Test
    public void testTypeof() {
        assertStringWithJavet("typeof true", "typeof false", "typeof new Boolean(true)");
    }

    @Test
    public void testValueOf() {
        // Normal case: true
        JSValue result = BooleanPrototype.valueOf(context, JSBoolean.TRUE, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.TRUE);

        // Normal case: false
        result = BooleanPrototype.valueOf(context, JSBoolean.FALSE, new JSValue[]{});
        assertThat(result).isEqualTo(JSBoolean.FALSE);

        // Edge case: called on non-boolean
        assertTypeError(BooleanPrototype.valueOf(context, new JSNumber(123), new JSValue[]{}));
        assertPendingException(context);

        // Edge case: Boolean object without PrimitiveValue
        JSObject invalidObj = new JSObject();
        assertTypeError(BooleanPrototype.valueOf(context, invalidObj, new JSValue[]{}));
        assertPendingException(context);
    }
}
