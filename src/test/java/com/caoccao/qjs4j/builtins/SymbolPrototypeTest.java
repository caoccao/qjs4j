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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SymbolPrototype methods.
 */
public class SymbolPrototypeTest extends BaseTest {

    @Test
    public void testToString() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Normal case: symbol with description
        JSSymbol symbolWithDesc = new JSSymbol("testDescription");
        JSValue result = SymbolPrototype.toString(ctx, symbolWithDesc, new JSValue[]{});
        assertEquals("Symbol(testDescription)", ((JSString) result).getValue());

        // Normal case: symbol without description
        JSSymbol symbolNoDesc = new JSSymbol(null);
        result = SymbolPrototype.toString(ctx, symbolNoDesc, new JSValue[]{});
        assertEquals("Symbol()", ((JSString) result).getValue());

        // Normal case: symbol object wrapper
        JSObject symbolObj = new JSObject();
        symbolObj.set("[[PrimitiveValue]]", symbolWithDesc);
        result = SymbolPrototype.toString(ctx, symbolObj, new JSValue[]{});
        assertEquals("Symbol(testDescription)", ((JSString) result).getValue());

        // Edge case: called on non-symbol
        result = SymbolPrototype.toString(ctx, new JSString("not a symbol"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: symbol object with wrong primitive value
        JSObject badSymbolObj = new JSObject();
        badSymbolObj.set("[[PrimitiveValue]]", new JSString("not a symbol"));
        result = SymbolPrototype.toString(ctx, badSymbolObj, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testValueOf() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Normal case: symbol
        JSSymbol symbol = new JSSymbol("test");
        JSValue result = SymbolPrototype.valueOf(ctx, symbol, new JSValue[]{});
        assertEquals(symbol, result);

        // Normal case: symbol object wrapper
        JSObject symbolObj = new JSObject();
        symbolObj.set("[[PrimitiveValue]]", symbol);
        result = SymbolPrototype.valueOf(ctx, symbolObj, new JSValue[]{});
        assertEquals(symbol, result);

        // Edge case: called on non-symbol
        result = SymbolPrototype.valueOf(ctx, new JSString("not a symbol"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: symbol object with wrong primitive value
        JSObject badSymbolObj = new JSObject();
        badSymbolObj.set("[[PrimitiveValue]]", new JSString("not a symbol"));
        result = SymbolPrototype.valueOf(ctx, badSymbolObj, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetDescription() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Normal case: symbol with description
        JSSymbol symbolWithDesc = new JSSymbol("testDescription");
        JSValue result = SymbolPrototype.getDescription(ctx, symbolWithDesc, new JSValue[]{});
        assertEquals("testDescription", ((JSString) result).getValue());

        // Normal case: symbol without description
        JSSymbol symbolNoDesc = new JSSymbol(null);
        result = SymbolPrototype.getDescription(ctx, symbolNoDesc, new JSValue[]{});
        assertEquals(JSUndefined.INSTANCE, result);

        // Normal case: symbol object wrapper
        JSObject symbolObj = new JSObject();
        symbolObj.set("[[PrimitiveValue]]", symbolWithDesc);
        result = SymbolPrototype.getDescription(ctx, symbolObj, new JSValue[]{});
        assertEquals("testDescription", ((JSString) result).getValue());

        // Edge case: called on non-symbol
        result = SymbolPrototype.getDescription(ctx, new JSString("not a symbol"), new JSValue[]{});
        assertEquals(JSUndefined.INSTANCE, result);

        // Edge case: symbol object with wrong primitive value
        JSObject badSymbolObj = new JSObject();
        badSymbolObj.set("[[PrimitiveValue]]", new JSString("not a symbol"));
        result = SymbolPrototype.getDescription(ctx, badSymbolObj, new JSValue[]{});
        assertEquals(JSUndefined.INSTANCE, result);
    }

    @Test
    public void testToPrimitive() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Normal case: symbol
        JSSymbol symbol = new JSSymbol("test");
        JSValue result = SymbolPrototype.toPrimitive(ctx, symbol, new JSValue[]{});
        assertEquals(symbol, result);

        // Normal case: symbol object wrapper
        JSObject symbolObj = new JSObject();
        symbolObj.set("[[PrimitiveValue]]", symbol);
        result = SymbolPrototype.toPrimitive(ctx, symbolObj, new JSValue[]{});
        assertEquals(symbol, result);

        // Edge case: called on non-symbol (should behave same as valueOf)
        result = SymbolPrototype.toPrimitive(ctx, new JSString("not a symbol"), new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testToStringTag() {
        JSContext ctx = new JSContext(new JSRuntime());

        // Normal case: any this value
        JSValue result = SymbolPrototype.toStringTag(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertEquals("Symbol", ((JSString) result).getValue());

        result = SymbolPrototype.toStringTag(ctx, new JSString("anything"), new JSValue[]{});
        assertEquals("Symbol", ((JSString) result).getValue());

        result = SymbolPrototype.toStringTag(ctx, new JSObject(), new JSValue[]{});
        assertEquals("Symbol", ((JSString) result).getValue());
    }
}