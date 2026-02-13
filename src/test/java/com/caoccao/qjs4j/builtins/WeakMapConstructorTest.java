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

public class WeakMapConstructorTest extends BaseJavetTest {

    @Test
    void testTypeof() {
        assertStringWithJavet("typeof WeakMap");

        assertIntegerWithJavet("WeakMap.length");

        assertStringWithJavet("WeakMap.name");

        assertStringWithJavet("new WeakMap().toString()");

        assertErrorWithJavet("WeakMap()");
    }

    @Test
    void testWeakMapConstructorIterableEdgeCases() {
        assertBooleanWithJavet("""
                var k1 = {};
                var k2 = {};
                var source = new Map([[k1, 1], [k2, 2]]);
                var wm = new WeakMap(source);
                wm.get(k1) === 1 && wm.get(k2) === 2""");

        assertThatThrownBy(() -> context.eval("new WeakMap({})"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new WeakMap([[1, 'a']])"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
    }

    @Test
    void testWeakMapConstructorIteratorCloseAndAdderLookup() {
        assertThat(context.eval("""
                (() => {
                  const original = WeakMap.prototype.set;
                  try {
                    WeakMap.prototype.set = function (k, v) {
                      this.__adderUsed = true;
                      return original.call(this, k, v);
                    };
                    const key = {};
                    const weakMap = new WeakMap([[key, 1]]);
                    return weakMap.__adderUsed === true && weakMap.get(key) === 1;
                  } finally {
                    WeakMap.prototype.set = original;
                  }
                })()""").toJavaObject()).isEqualTo(true);

        assertThat(context.eval("""
                (() => {
                  const original = WeakMap.prototype.set;
                  try {
                    WeakMap.prototype.set = 1;
                    new WeakMap([]);
                    return false;
                  } catch (e) {
                    return e instanceof TypeError;
                  } finally {
                    WeakMap.prototype.set = original;
                  }
                })()""").toJavaObject()).isEqualTo(true);
    }

    @Test
    void testWeakMapPrototypeDescriptors() {
        assertBooleanWithJavet("""
                (() => {
                  const setDesc = Object.getOwnPropertyDescriptor(WeakMap.prototype, 'set');
                  const tagDesc = Object.getOwnPropertyDescriptor(WeakMap.prototype, Symbol.toStringTag);
                  return !!setDesc
                    && setDesc.writable
                    && !setDesc.enumerable
                    && setDesc.configurable
                    && !!tagDesc
                    && tagDesc.value === 'WeakMap'
                    && !tagDesc.writable
                    && !tagDesc.enumerable
                    && tagDesc.configurable
                    && typeof tagDesc.get === 'undefined';
                })()""");

        assertThat(context.eval("""
                (() => {
                  const d1 = Object.getOwnPropertyDescriptor(WeakMap.prototype, 'getOrInsert');
                  const d2 = Object.getOwnPropertyDescriptor(WeakMap.prototype, 'getOrInsertComputed');
                  return !!d1
                    && d1.writable
                    && !d1.enumerable
                    && d1.configurable
                    && !!d2
                    && d2.writable
                    && !d2.enumerable
                    && d2.configurable;
                })()""").toJavaObject()).isEqualTo(true);
    }

    @Test
    void testWeakMapSymbolKeys() {
        assertThat(context.eval("""
                (() => {
                  const weakMap = new WeakMap();
                  const symbolKey = Symbol('k');
                  weakMap.set(symbolKey, 1);
                  return weakMap.get(symbolKey) === 1
                    && weakMap.has(symbolKey)
                    && weakMap.delete(symbolKey)
                    && !weakMap.has(symbolKey);
                })()""").toJavaObject()).isEqualTo(true);
    }

}
