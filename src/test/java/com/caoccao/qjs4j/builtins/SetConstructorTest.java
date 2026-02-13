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

public class SetConstructorTest extends BaseJavetTest {

    @Test
    void testGroupByAndSpecies() {
        assertBooleanWithJavet("""
                const desc = Object.getOwnPropertyDescriptor(Set, Symbol.species);
                typeof desc.get === 'function'
                && desc.set === undefined
                && desc.enumerable === false
                && desc.configurable === true
                && Set[Symbol.species] === Set
                """);

        assertThat(context.eval("""
                (() => {
                  const grouped = Set.groupBy([1, 2, 3, 4], v => v % 2);
                  const iterable = {
                    [Symbol.iterator]() {
                      let i = 0;
                      return {
                        next() {
                          if (i < 3) return { value: i++, done: false };
                          return { value: undefined, done: true };
                        }
                      };
                    }
                  };
                  const grouped2 = Set.groupBy(iterable, v => v === 0 ? 'z' : 'n');
                  const desc = Object.getOwnPropertyDescriptor(Set, 'groupBy');
                  return grouped instanceof Map
                    && grouped.get(0).join(',') === '2,4'
                    && grouped.get(1).join(',') === '1,3'
                    && grouped2 instanceof Map
                    && grouped2.get('z').join(',') === '0'
                    && grouped2.get('n').join(',') === '1,2'
                    && typeof desc.value === 'function'
                    && desc.value.length === 2
                    && desc.enumerable === false
                    && desc.configurable === true
                    && desc.writable === true;
                })()
                """).toJavaObject()).isEqualTo(true);
    }

    @Test
    void testSetConstructorIterableEdgeCases() {
        assertBooleanWithJavet("""
                var set = new Set(new Set([0, 1, 2]));
                set.size === 3 && set.has(0) && set.has(1) && set.has(2)""");

        assertBooleanWithJavet("""
                var set = new Set('aba');
                set.size === 2 && set.has('a') && set.has('b')""");

        assertThatThrownBy(() -> context.eval("new Set({})"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");
        assertThatThrownBy(() -> context.eval("new Set(1)"))
                .isInstanceOf(JSException.class)
                .hasMessageContaining("TypeError");

    }

    @Test
    void testSetConstructorUsesAdderAndClosesIteratorOnError() {
        context.eval("""
                globalThis.__setAddCallCount = 0;
                globalThis.__setReturnCallCount = 0;
                globalThis.__setOriginalAdd = Set.prototype.add;
                globalThis.__setIterable = {
                  [Symbol.iterator]() {
                    let i = 0;
                    return {
                      next() {
                        if (i === 0) {
                          i++;
                          return { value: 1, done: false };
                        }
                        if (i === 1) {
                          i++;
                          return { value: 2, done: false };
                        }
                        return { value: undefined, done: true };
                      },
                      return() {
                        __setReturnCallCount++;
                        return { done: true };
                      }
                    };
                  }
                };
                Set.prototype.add = function(v) {
                  __setAddCallCount++;
                  if (v === 2) throw new Error('boom');
                  return __setOriginalAdd.call(this, v);
                };
                """);
        try {
            assertThatThrownBy(() -> context.eval("new Set(__setIterable)"))
                    .isInstanceOf(JSException.class)
                    .hasMessageContaining("boom");
            assertThat(context.eval("__setAddCallCount + ':' + __setReturnCallCount").toJavaObject())
                    .isEqualTo("2:1");
        } finally {
            context.eval("""
                    Set.prototype.add = __setOriginalAdd;
                    delete globalThis.__setOriginalAdd;
                    delete globalThis.__setIterable;
                    delete globalThis.__setAddCallCount;
                    delete globalThis.__setReturnCallCount;
                    """);
        }
    }

    @Test
    void testTypeof() {
        // Set should be a function
        assertStringWithJavet("typeof Set");

        // Set.length should be 0
        assertIntegerWithJavet("Set.length");

        // Set.name should be "Set"
        assertStringWithJavet("Set.name");

        assertStringWithJavet("new Set().toString()");

        assertErrorWithJavet("Set()");
    }
}
