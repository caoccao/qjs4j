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
        JSValue result = SymbolConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("test")});
        assertInstanceOf(JSSymbol.class, result);
        JSSymbol symbol = (JSSymbol) result;
        assertEquals("test", symbol.getDescription());

        // Normal case: with numeric description
        result = SymbolConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        assertInstanceOf(JSSymbol.class, result);
        symbol = (JSSymbol) result;
        assertEquals("42", symbol.getDescription());

        // Normal case: with undefined description
        result = SymbolConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{JSUndefined.INSTANCE});
        assertInstanceOf(JSSymbol.class, result);
        symbol = (JSSymbol) result;
        assertNull(symbol.getDescription());

        // Normal case: no arguments
        result = SymbolConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertInstanceOf(JSSymbol.class, result);
        symbol = (JSSymbol) result;
        assertNull(symbol.getDescription());

        // Normal case: symbols are unique
        JSSymbol symbol1 = (JSSymbol) SymbolConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("same")});
        JSSymbol symbol2 = (JSSymbol) SymbolConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("same")});
        assertNotEquals(symbol1, symbol2);
        assertEquals("same", symbol1.getDescription());
        assertEquals("same", symbol2.getDescription());
    }

    @Test
    public void testSymbolFor() {
        // Normal case: create new symbol in registry
        JSValue result = SymbolConstructor.symbolFor(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("test")});
        assertInstanceOf(JSSymbol.class, result);
        JSSymbol symbol1 = (JSSymbol) result;
        assertEquals("test", symbol1.getDescription());

        // Normal case: get existing symbol from registry
        result = SymbolConstructor.symbolFor(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("test")});
        assertInstanceOf(JSSymbol.class, result);
        JSSymbol symbol2 = (JSSymbol) result;
        assertEquals(symbol1, symbol2); // Same symbol instance

        // Normal case: different keys create different symbols
        result = SymbolConstructor.symbolFor(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("other")});
        assertInstanceOf(JSSymbol.class, result);
        JSSymbol symbol3 = (JSSymbol) result;
        assertNotEquals(symbol1, symbol3);
        assertEquals("other", symbol3.getDescription());

        // Normal case: numeric key
        result = SymbolConstructor.symbolFor(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)});
        assertInstanceOf(JSSymbol.class, result);
        JSSymbol symbol4 = (JSSymbol) result;
        assertEquals("123", symbol4.getDescription());

        // Edge case: no arguments
        result = SymbolConstructor.symbolFor(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testKeyFor() {
        // Normal case: symbol from registry
        JSSymbol symbol = (JSSymbol) SymbolConstructor.symbolFor(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("registryKey")});
        JSValue result = SymbolConstructor.keyFor(ctx, JSUndefined.INSTANCE, new JSValue[]{symbol});
        assertEquals("registryKey", ((JSString) result).getValue());

        // Normal case: symbol not in registry
        JSSymbol unregisteredSymbol = (JSSymbol) SymbolConstructor.call(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("unregistered")});
        result = SymbolConstructor.keyFor(ctx, JSUndefined.INSTANCE, new JSValue[]{unregisteredSymbol});
        assertEquals(JSUndefined.INSTANCE, result);

        // Edge case: no arguments
        result = SymbolConstructor.keyFor(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: non-symbol argument
        result = SymbolConstructor.keyFor(ctx, JSUndefined.INSTANCE, new JSValue[]{new JSString("not a symbol")});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: null
        result = SymbolConstructor.keyFor(ctx, JSUndefined.INSTANCE, new JSValue[]{JSNull.INSTANCE});
        assertTypeError(result);
        assertPendingException(ctx);

        // Edge case: undefined
        result = SymbolConstructor.keyFor(ctx, JSUndefined.INSTANCE, new JSValue[]{JSUndefined.INSTANCE});
        assertTypeError(result);
        assertPendingException(ctx);
    }

    @Test
    public void testGetIterator() {
        JSValue result = SymbolConstructor.getIterator(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertInstanceOf(JSSymbol.class, result);
        JSSymbol symbol = (JSSymbol) result;
        assertEquals(JSSymbol.ITERATOR, symbol);
    }

    @Test
    public void testGetToStringTag() {
        JSValue result = SymbolConstructor.getToStringTag(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertInstanceOf(JSSymbol.class, result);
        JSSymbol symbol = (JSSymbol) result;
        assertEquals(JSSymbol.TO_STRING_TAG, symbol);
    }

    @Test
    public void testGetHasInstance() {
        JSValue result = SymbolConstructor.getHasInstance(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertInstanceOf(JSSymbol.class, result);
        JSSymbol symbol = (JSSymbol) result;
        assertEquals(JSSymbol.HAS_INSTANCE, symbol);
    }

    @Test
    public void testGetIsConcatSpreadable() {
        JSValue result = SymbolConstructor.getIsConcatSpreadable(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertInstanceOf(JSSymbol.class, result);
        JSSymbol symbol = (JSSymbol) result;
        assertEquals(JSSymbol.IS_CONCAT_SPREADABLE, symbol);
    }

    @Test
    public void testGetToPrimitive() {
        JSValue result = SymbolConstructor.getToPrimitive(ctx, JSUndefined.INSTANCE, new JSValue[]{});
        assertInstanceOf(JSSymbol.class, result);
        JSSymbol symbol = (JSSymbol) result;
        assertEquals(JSSymbol.TO_PRIMITIVE, symbol);
    }
}