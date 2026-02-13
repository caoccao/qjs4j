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
import com.caoccao.qjs4j.exceptions.JSException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class WeakSetConstructorTest extends BaseJavetTest {

    @Test
    void testTypeof() {
        assertStringWithJavet("typeof WeakSet");

        assertIntegerWithJavet("WeakSet.length");

        assertStringWithJavet("WeakSet.name");

        assertStringWithJavet("new WeakSet().toString()");

        assertErrorWithJavet("WeakSet()");
    }

    @Test
    void testWeakSetConstructorIterableEdgeCases() {
        assertBooleanWithJavet("""
                var v1 = {};
                var v2 = {};
                var source = new Set([v1, v2]);
                var ws = new WeakSet(source);
                ws.has(v1) && ws.has(v2)""");

        assertThatThrownBy(() -> context.eval("new WeakSet({})"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new WeakSet([1])"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
    }

    @Test
    void testWeakSetConstructorIteratorCloseAndAdderLookup() {
        assertThat(context.eval("""
                (() => {
                  const original = WeakSet.prototype.add;
                  try {
                    WeakSet.prototype.add = function (v) {
                      this.__adderUsed = true;
                      return original.call(this, v);
                    };
                    const value = {};
                    const weakSet = new WeakSet([value]);
                    return weakSet.__adderUsed === true && weakSet.has(value);
                  } finally {
                    WeakSet.prototype.add = original;
                  }
                })()""").toJavaObject()).isEqualTo(true);

        assertThat(context.eval("""
                (() => {
                  const original = WeakSet.prototype.add;
                  try {
                    WeakSet.prototype.add = 1;
                    new WeakSet([]);
                    return false;
                  } catch (e) {
                    return e instanceof TypeError;
                  } finally {
                    WeakSet.prototype.add = original;
                  }
                })()""").toJavaObject()).isEqualTo(true);
    }

    @Test
    void testWeakSetPrototypeDescriptors() {
        assertBooleanWithJavet("""
                (() => {
                  const addDesc = Object.getOwnPropertyDescriptor(WeakSet.prototype, 'add');
                  const tagDesc = Object.getOwnPropertyDescriptor(WeakSet.prototype, Symbol.toStringTag);
                  return !!addDesc
                    && addDesc.writable
                    && !addDesc.enumerable
                    && addDesc.configurable
                    && !!tagDesc
                    && tagDesc.value === 'WeakSet'
                    && !tagDesc.writable
                    && !tagDesc.enumerable
                    && tagDesc.configurable
                    && typeof tagDesc.get === 'undefined';
                })()""");
    }

    @Test
    void testWeakSetSymbolValues() {
        assertThat(context.eval("""
                (() => {
                  const weakSet = new WeakSet();
                  const symbolValue = Symbol('k');
                  weakSet.add(symbolValue);
                  return weakSet.has(symbolValue)
                    && weakSet.delete(symbolValue)
                    && !weakSet.has(symbolValue);
                })()""").toJavaObject()).isEqualTo(true);
    }

}
