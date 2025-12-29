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
 * Unit tests for Symbol constructor static methods.
 */
public class SymbolConstructorTest extends BaseTest {

    @Test
    public void testCall() {
        // Normal case: with description
        JSValue result = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("test")});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertEquals("test", symbol.getDescription());

        // Normal case: with numeric description
        result = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        symbol = result.asSymbol().orElseThrow();
        assertEquals("42", symbol.getDescription());

        // Normal case: with undefined description
        result = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{JSUndefined.INSTANCE});
        symbol = result.asSymbol().orElseThrow();
        assertNull(symbol.getDescription());

        // Normal case: no arguments
        result = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{});
        symbol = result.asSymbol().orElseThrow();
        assertNull(symbol.getDescription());

        // Normal case: symbols are unique
        JSSymbol symbol1 = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("same")}).asSymbol().orElseThrow();
        JSSymbol symbol2 = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("same")}).asSymbol().orElseThrow();
        assertNotEquals(symbol1, symbol2);
        assertEquals("same", symbol1.getDescription());
        assertEquals("same", symbol2.getDescription());
    }

    @Test
    public void testGetHasInstance() {
        JSValue result = SymbolConstructor.getHasInstance(context, JSUndefined.INSTANCE, new JSValue[]{});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertEquals(JSSymbol.HAS_INSTANCE, symbol);
    }

    @Test
    public void testGetIsConcatSpreadable() {
        JSValue result = SymbolConstructor.getIsConcatSpreadable(context, JSUndefined.INSTANCE, new JSValue[]{});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertEquals(JSSymbol.IS_CONCAT_SPREADABLE, symbol);
    }

    @Test
    public void testGetIterator() {
        JSValue result = SymbolConstructor.getIterator(context, JSUndefined.INSTANCE, new JSValue[]{});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertEquals(JSSymbol.ITERATOR, symbol);
    }

    @Test
    public void testGetToPrimitive() {
        JSValue result = SymbolConstructor.getToPrimitive(context, JSUndefined.INSTANCE, new JSValue[]{});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertEquals(JSSymbol.TO_PRIMITIVE, symbol);
    }

    @Test
    public void testGetToStringTag() {
        JSValue result = SymbolConstructor.getToStringTag(context, JSUndefined.INSTANCE, new JSValue[]{});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertEquals(JSSymbol.TO_STRING_TAG, symbol);
    }

    @Test
    public void testKeyFor() {
        // Normal case: symbol from registry
        JSSymbol symbol = SymbolConstructor.symbolFor(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("registryKey")}).asSymbol().orElseThrow();
        JSValue result = SymbolConstructor.keyFor(context, JSUndefined.INSTANCE, new JSValue[]{symbol});
        assertEquals("registryKey", result.asString().map(JSString::value).orElseThrow());

        // Normal case: symbol not in registry
        JSSymbol unregisteredSymbol = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("unregistered")}).asSymbol().orElseThrow();
        result = SymbolConstructor.keyFor(context, JSUndefined.INSTANCE, new JSValue[]{unregisteredSymbol});
        assertTrue(result.isUndefined());

        // Edge case: no arguments
        result = SymbolConstructor.keyFor(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: non-symbol argument
        result = SymbolConstructor.keyFor(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("not a symbol")});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: null
        result = SymbolConstructor.keyFor(context, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertTypeError(result);
        assertPendingException(context);

        // Edge case: undefined
        result = SymbolConstructor.keyFor(context, JSUndefined.INSTANCE, new JSValue[]{JSUndefined.INSTANCE});
        assertTypeError(result);
        assertPendingException(context);
    }

    @Test
    public void testSymbolFor() {
        // Normal case: create new symbol in registry
        JSValue result = SymbolConstructor.symbolFor(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("test")});
        JSSymbol symbol1 = result.asSymbol().orElseThrow();
        assertEquals("test", symbol1.getDescription());

        // Normal case: get existing symbol from registry
        result = SymbolConstructor.symbolFor(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("test")});
        JSSymbol symbol2 = result.asSymbol().orElseThrow();
        assertEquals(symbol1, symbol2); // Same symbol instance

        // Normal case: different keys create different symbols
        result = SymbolConstructor.symbolFor(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("other")});
        JSSymbol symbol3 = result.asSymbol().orElseThrow();
        assertNotEquals(symbol1, symbol3);
        assertEquals("other", symbol3.getDescription());

        // Normal case: numeric key
        result = SymbolConstructor.symbolFor(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)});
        JSSymbol symbol4 = result.asSymbol().orElseThrow();
        assertEquals("123", symbol4.getDescription());

        // Edge case: no arguments
        result = SymbolConstructor.symbolFor(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }
}