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

package com.caoccao.qjs4j.builtins.object;

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
        var symbolConstructor = builtin(SymbolConstructor::call);
        JSValue result = symbolConstructor.call("test");
        assertThat(result.asSymbol().orElseThrow().getDescription()).isEqualTo("test");

        // Normal case: with numeric description
        assertThat(symbolConstructor.call(42).asSymbol().orElseThrow().getDescription()).isEqualTo("42");

        // Normal case: with undefined description
        assertThat(symbolConstructor.call(JSUndefined.INSTANCE).asSymbol().orElseThrow().getDescription()).isNull();

        // Normal case: no arguments
        assertThat(symbolConstructor.call().asSymbol().orElseThrow().getDescription()).isNull();

        // Normal case: symbols are unique
        JSSymbol symbol1 = symbolConstructor.call("same").asSymbol().orElseThrow();
        JSSymbol symbol2 = symbolConstructor.call("same").asSymbol().orElseThrow();
        assertThat(symbol1).isNotEqualTo(symbol2);
        assertThat(symbol1.getDescription()).isEqualTo("same");
        assertThat(symbol2.getDescription()).isEqualTo("same");
    }

    @Test
    public void testGetAsyncDispose() {
        assertThat(callFunction(SymbolConstructor::getAsyncDispose).asSymbol().orElseThrow())
                .isEqualTo(JSSymbol.ASYNC_DISPOSE);
    }

    @Test
    public void testGetDispose() {
        assertThat(callFunction(SymbolConstructor::getDispose).asSymbol().orElseThrow()).isEqualTo(JSSymbol.DISPOSE);
    }

    @Test
    public void testGetHasInstance() {
        assertThat(callFunction(SymbolConstructor::getHasInstance).asSymbol().orElseThrow())
                .isEqualTo(JSSymbol.HAS_INSTANCE);
    }

    @Test
    public void testGetIsConcatSpreadable() {
        assertThat(callFunction(SymbolConstructor::getIsConcatSpreadable).asSymbol().orElseThrow())
                .isEqualTo(JSSymbol.IS_CONCAT_SPREADABLE);
    }

    @Test
    public void testGetIterator() {
        assertThat(callFunction(SymbolConstructor::getIterator).asSymbol().orElseThrow()).isEqualTo(JSSymbol.ITERATOR);
    }

    @Test
    public void testGetToPrimitive() {
        assertThat(callFunction(SymbolConstructor::getToPrimitive).asSymbol().orElseThrow())
                .isEqualTo(JSSymbol.TO_PRIMITIVE);
    }

    @Test
    public void testGetToStringTag() {
        assertThat(callFunction(SymbolConstructor::getToStringTag).asSymbol().orElseThrow())
                .isEqualTo(JSSymbol.TO_STRING_TAG);
    }

    @Test
    public void testKeyFor() {
        // Normal case: symbol from registry
        JSSymbol symbol = callFunction(SymbolConstructor::symbolFor, "registryKey").asSymbol().orElseThrow();
        var keyFor = builtin(SymbolConstructor::keyFor);
        assertStringValue(keyFor.call(symbol), "registryKey");

        // Normal case: symbol not in registry
        JSSymbol unregisteredSymbol = callFunction(SymbolConstructor::call, "unregistered").asSymbol().orElseThrow();
        assertThat(keyFor.call(unregisteredSymbol)).isEqualTo(JSUndefined.INSTANCE);

        // Edge case: no arguments
        assertPendingTypeError(keyFor.call());

        // Edge case: non-symbol argument
        assertPendingTypeError(keyFor.call("not a symbol"));

        // Edge case: null
        assertPendingTypeError(keyFor.call(JSNull.INSTANCE));

        // Edge case: undefined
        assertPendingTypeError(keyFor.call(JSUndefined.INSTANCE));
    }

    @Test
    public void testSymbolFor() {
        // Normal case: create new symbol in registry
        var symbolFor = builtin(SymbolConstructor::symbolFor);
        JSValue result = symbolFor.call("test");
        JSSymbol symbol1 = result.asSymbol().orElseThrow();
        assertThat(symbol1.getDescription()).isEqualTo("test");

        // Normal case: get existing symbol from registry
        assertThat(symbolFor.call("test").asSymbol().orElseThrow()).isEqualTo(symbol1); // Same symbol instance

        // Normal case: different keys create different symbols
        JSSymbol symbol3 = symbolFor.call("other").asSymbol().orElseThrow();
        assertThat(symbol3).isNotEqualTo(symbol1);
        assertThat(symbol3.getDescription()).isEqualTo("other");

        // Normal case: numeric key
        assertThat(symbolFor.call(123).asSymbol().orElseThrow().getDescription()).isEqualTo("123");

        // Edge case: no arguments coerces undefined to "undefined" key
        JSSymbol symbol5 = symbolFor.call().asSymbol().orElseThrow();
        assertThat(symbol5.getDescription()).isEqualTo("undefined");

        assertThat(symbolFor.call(JSUndefined.INSTANCE).asSymbol().orElseThrow()).isEqualTo(symbol5);
    }

    @Test
    public void testSymbolRegistrationDescriptors() {
        assertBooleanWithJavet("""
                (() => {
                    const d = Object.getOwnPropertyDescriptor(Symbol, "for");
                    return typeof d.value === "function"
                        && d.writable === true
                        && d.enumerable === false
                        && d.configurable === true;
                })()
                """, """
                (() => {
                    const d = Object.getOwnPropertyDescriptor(Symbol, "keyFor");
                    return typeof d.value === "function"
                        && d.writable === true
                        && d.enumerable === false
                        && d.configurable === true;
                })()
                """, """
                (() => {
                  const d = Object.getOwnPropertyDescriptor(Symbol, "iterator");
                  return typeof d.value === "symbol"
                    && d.value === Symbol.iterator
                    && d.writable === false
                    && d.enumerable === false
                    && d.configurable === false;
                })()""", """
                (() => {
                  const d = Object.getOwnPropertyDescriptor(Symbol, "prototype");
                  return d.value === Symbol.prototype
                    && d.writable === false
                    && d.enumerable === false
                    && d.configurable === false;
                })()""");
    }

    @Test
    public void testSymbolRegistryIsRuntimeScoped() {
        try (JSRuntime runtime1 = new JSRuntime();
                JSContext context1 = runtime1.createContext();
                JSRuntime runtime2 = new JSRuntime();
                JSContext context2 = runtime2.createContext()) {
            JSSymbol symbol1 = SymbolConstructor.symbolFor(context1, JSUndefined.INSTANCE, nativeArguments("sameKey"))
                    .asSymbol().orElseThrow();
            JSSymbol symbol2 = SymbolConstructor.symbolFor(context2, JSUndefined.INSTANCE, nativeArguments("sameKey"))
                    .asSymbol().orElseThrow();

            assertThat(symbol1).isNotEqualTo(symbol2);

            JSValue key1 = SymbolConstructor.keyFor(context1, JSUndefined.INSTANCE, new JSValue[]{symbol1});
            JSValue key2 = SymbolConstructor.keyFor(context2, JSUndefined.INSTANCE, new JSValue[]{symbol2});
            JSValue crossKey1 = SymbolConstructor.keyFor(context1, JSUndefined.INSTANCE, new JSValue[]{symbol2});
            JSValue crossKey2 = SymbolConstructor.keyFor(context2, JSUndefined.INSTANCE, new JSValue[]{symbol1});

            assertStringValue(key1, "sameKey");
            assertStringValue(key2, "sameKey");
            assertThat(crossKey1).isEqualTo(JSUndefined.INSTANCE);
            assertThat(crossKey2).isEqualTo(JSUndefined.INSTANCE);
        }
    }

    @Test
    public void testWellKnownSymbolProperties() {
        assertBooleanWithJavet("""
                (() => {
                  const names = [
                    "iterator", "asyncIterator", "toStringTag", "hasInstance",
                    "isConcatSpreadable", "toPrimitive", "match", "matchAll",
                    "replace", "search", "split", "species", "unscopables",
                    "dispose", "asyncDispose"
                  ];
                  return names.every(name => typeof Symbol[name] === "symbol");
                })()""", """
                (() => {
                  const sym = Symbol.iterator;
                  const before = Symbol.iterator;
                  let strictTypeError = false;
                  try {
                    (function() {
                      "use strict";
                      Symbol.iterator = Symbol("x");
                    })();
                  } catch (e) {
                    strictTypeError = e instanceof TypeError;
                  }
                  return strictTypeError && Symbol.iterator === before && Symbol.iterator === sym;
                })()""");
    }
}
