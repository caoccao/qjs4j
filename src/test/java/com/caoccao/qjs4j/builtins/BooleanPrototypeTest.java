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

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Boolean.prototype methods.
 */
public class BooleanPrototypeTest extends BaseTest {

    @Test
    public void testToString() {
        // Normal case: true
        JSValue result = BooleanPrototype.toString(ctx, JSBoolean.TRUE, new JSValue[]{});
        assertEquals("true", result.asString().map(JSString::value).orElse(""));

        // Normal case: false
        result = BooleanPrototype.toString(ctx, JSBoolean.FALSE, new JSValue[]{});
        assertEquals("false", result.asString().map(JSString::value).orElse(""));

        // Normal case: Boolean object wrapper
        JSObject boolObj = new JSObject();
        boolObj.set("[[PrimitiveValue]]", JSBoolean.TRUE);
        result = BooleanPrototype.toString(ctx, boolObj, new JSValue[]{});
        assertEquals("true", result.asString().map(JSString::value).orElse(""));

        JSObject boolObj2 = new JSObject();
        boolObj2.set("[[PrimitiveValue]]", JSBoolean.FALSE);
        result = BooleanPrototype.toString(ctx, boolObj2, new JSValue[]{});
        assertEquals("false", result.asString().map(JSString::value).orElse(""));

        // Edge case: called on non-boolean
        assertTypeError(BooleanPrototype.toString(ctx, new JSString("not boolean"), new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: Boolean object without PrimitiveValue
        JSObject invalidObj = new JSObject();
        assertTypeError(BooleanPrototype.toString(ctx, invalidObj, new JSValue[]{}));
        assertPendingException(ctx);
    }

    @Test
    public void testValueOf() {
        // Normal case: true
        JSValue result = BooleanPrototype.valueOf(ctx, JSBoolean.TRUE, new JSValue[]{});
        assertTrue(result.isBooleanTrue());

        // Normal case: false
        result = BooleanPrototype.valueOf(ctx, JSBoolean.FALSE, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Normal case: Boolean object wrapper
        JSObject boolObj = new JSObject();
        boolObj.set("[[PrimitiveValue]]", JSBoolean.TRUE);
        result = BooleanPrototype.valueOf(ctx, boolObj, new JSValue[]{});
        assertTrue(result.isBooleanTrue());

        JSObject boolObj2 = new JSObject();
        boolObj2.set("[[PrimitiveValue]]", JSBoolean.FALSE);
        result = BooleanPrototype.valueOf(ctx, boolObj2, new JSValue[]{});
        assertTrue(result.isBooleanFalse());

        // Edge case: called on non-boolean
        assertTypeError(BooleanPrototype.valueOf(ctx, new JSNumber(123), new JSValue[]{}));
        assertPendingException(ctx);

        // Edge case: Boolean object without PrimitiveValue
        JSObject invalidObj = new JSObject();
        assertTypeError(BooleanPrototype.valueOf(ctx, invalidObj, new JSValue[]{}));
        assertPendingException(ctx);
    }
}
