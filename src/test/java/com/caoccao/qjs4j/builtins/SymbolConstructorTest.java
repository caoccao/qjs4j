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
 * Unit tests for Symbol constructor static methods.
 */
public class SymbolConstructorTest extends BaseJavetTest {

    @Test
    public void testCall() {
        // Normal case: with description
        JSValue result = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("test")});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertThat(symbol.getDescription()).isEqualTo("test");

        // Normal case: with numeric description
        result = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(42)});
        symbol = result.asSymbol().orElseThrow();
        assertThat(symbol.getDescription()).isEqualTo("42");

        // Normal case: with undefined description
        result = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{JSUndefined.INSTANCE});
        symbol = result.asSymbol().orElseThrow();
        assertThat(symbol.getDescription()).isNull();

        // Normal case: no arguments
        result = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{});
        symbol = result.asSymbol().orElseThrow();
        assertThat(symbol.getDescription()).isNull();

        // Normal case: symbols are unique
        JSSymbol symbol1 = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("same")}).asSymbol().orElseThrow();
        JSSymbol symbol2 = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("same")}).asSymbol().orElseThrow();
        assertThat(symbol1).isNotEqualTo(symbol2);
        assertThat(symbol1.getDescription()).isEqualTo("same");
        assertThat(symbol2.getDescription()).isEqualTo("same");
    }

    @Test
    public void testGetAsyncDispose() {
        JSValue result = SymbolConstructor.getAsyncDispose(context, JSUndefined.INSTANCE, new JSValue[]{});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertThat(symbol).isEqualTo(JSSymbol.ASYNC_DISPOSE);
    }

    @Test
    public void testGetDispose() {
        JSValue result = SymbolConstructor.getDispose(context, JSUndefined.INSTANCE, new JSValue[]{});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertThat(symbol).isEqualTo(JSSymbol.DISPOSE);
    }

    @Test
    public void testGetHasInstance() {
        JSValue result = SymbolConstructor.getHasInstance(context, JSUndefined.INSTANCE, new JSValue[]{});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertThat(symbol).isEqualTo(JSSymbol.HAS_INSTANCE);
    }

    @Test
    public void testGetIsConcatSpreadable() {
        JSValue result = SymbolConstructor.getIsConcatSpreadable(context, JSUndefined.INSTANCE, new JSValue[]{});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertThat(symbol).isEqualTo(JSSymbol.IS_CONCAT_SPREADABLE);
    }

    @Test
    public void testGetIterator() {
        JSValue result = SymbolConstructor.getIterator(context, JSUndefined.INSTANCE, new JSValue[]{});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertThat(symbol).isEqualTo(JSSymbol.ITERATOR);
    }

    @Test
    public void testGetToPrimitive() {
        JSValue result = SymbolConstructor.getToPrimitive(context, JSUndefined.INSTANCE, new JSValue[]{});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertThat(symbol).isEqualTo(JSSymbol.TO_PRIMITIVE);
    }

    @Test
    public void testGetToStringTag() {
        JSValue result = SymbolConstructor.getToStringTag(context, JSUndefined.INSTANCE, new JSValue[]{});
        JSSymbol symbol = result.asSymbol().orElseThrow();
        assertThat(symbol).isEqualTo(JSSymbol.TO_STRING_TAG);
    }

    @Test
    public void testKeyFor() {
        // Normal case: symbol from registry
        JSSymbol symbol = SymbolConstructor.symbolFor(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("registryKey")}).asSymbol().orElseThrow();
        JSValue result = SymbolConstructor.keyFor(context, JSUndefined.INSTANCE, new JSValue[]{symbol});
        assertThat(result).isInstanceOfSatisfying(JSString.class, jsStr -> assertThat(jsStr.value()).isEqualTo("registryKey"));

        // Normal case: symbol not in registry
        JSSymbol unregisteredSymbol = SymbolConstructor.call(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("unregistered")}).asSymbol().orElseThrow();
        result = SymbolConstructor.keyFor(context, JSUndefined.INSTANCE, new JSValue[]{unregisteredSymbol});
        assertThat(result).isEqualTo(JSUndefined.INSTANCE);

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
        assertThat(symbol1.getDescription()).isEqualTo("test");

        // Normal case: get existing symbol from registry
        result = SymbolConstructor.symbolFor(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("test")});
        JSSymbol symbol2 = result.asSymbol().orElseThrow();
        assertThat(symbol2).isEqualTo(symbol1); // Same symbol instance

        // Normal case: different keys create different symbols
        result = SymbolConstructor.symbolFor(context, JSUndefined.INSTANCE, new JSValue[]{new JSString("other")});
        JSSymbol symbol3 = result.asSymbol().orElseThrow();
        assertThat(symbol3).isNotEqualTo(symbol1);
        assertThat(symbol3.getDescription()).isEqualTo("other");

        // Normal case: numeric key
        result = SymbolConstructor.symbolFor(context, JSUndefined.INSTANCE, new JSValue[]{new JSNumber(123)});
        JSSymbol symbol4 = result.asSymbol().orElseThrow();
        assertThat(symbol4.getDescription()).isEqualTo("123");

        // Edge case: no arguments
        result = SymbolConstructor.symbolFor(context, JSUndefined.INSTANCE, new JSValue[]{});
        assertTypeError(result);
        assertPendingException(context);
    }
}
