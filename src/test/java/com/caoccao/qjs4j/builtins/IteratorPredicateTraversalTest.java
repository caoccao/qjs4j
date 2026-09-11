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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class IteratorPredicateTraversalTest extends BaseJavetTest {
    @ParameterizedTest
    @ValueSource(strings = {"every", "some", "find"})
    public void testCallbackErrorSurvivesIteratorCloseError(String method) {
        assertStringWithJavet("""
                (() => {
                  const log = [];
                  const marker = {};
                  const iterator = {
                    next() { log.push('next'); return {value: 7, done: false}; },
                    get return() { log.push('return'); throw new Error('close'); }
                  };
                  try { Iterator.prototype['%s'].call(iterator, () => { log.push('callback'); throw marker; }); }
                  catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()
                """.formatted(method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"every", "some", "find"})
    public void testCloseDistinguishesMissingReturnFromPrimitiveResult(String method) {
        assertStringWithJavet(
                """
                        (() => [false, true].flatMap(callable => [undefined, null, 0, false, 'x', 1n, Symbol('x'), {}].map(value => {
                          const method = '%s';
                          const iterator = {
                            next() { return {value: 7, done: false}; },
                            return: callable ? () => value : value
                          };
                          try { return String(Iterator.prototype[method].call(iterator, () => method !== 'every')); }
                          catch (e) { return e.name; }
                        })).join(';'))()
                        """
                        .formatted(method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"every", "some", "find"})
    public void testCloseErrorsReplaceShortCircuitResult(String method) {
        assertStringWithJavet("""
                (() => ['throw', 'primitive', 'noncallable'].map(failure => {
                  const method = '%s';
                  const log = [];
                  const marker = {};
                  const iterator = {
                    next() { log.push('next'); return {value: 7, done: false}; },
                    get return() {
                      log.push('return');
                      if (failure === 'noncallable') return 42;
                      return function () { log.push('close'); if (failure === 'throw') throw marker; return 0; };
                    }
                  };
                  try { Iterator.prototype[method].call(iterator, () => method !== 'every'); }
                  catch (e) { return (e === marker ? 'marker' : e.name) + '|' + log.join(','); }
                  return 'missing error';
                }).join(';'))()
                """.formatted(method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"every", "some", "find"})
    public void testInvalidPredicateClosesBeforeReadingNext(String method) {
        assertStringWithJavet("""
                (() => [undefined, null, 42].map(predicate => {
                  const log = [];
                  const iterator = {
                    get next() { log.push('next'); throw new Error('next'); },
                    get return() { log.push('return'); return function () { log.push('close'); return {}; }; }
                  };
                  try { Iterator.prototype['%s'].call(iterator, predicate); }
                  catch (e) { return e.name + '|' + log.join(','); }
                  return 'missing error';
                }).join(';'))()
                """.formatted(method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"every", "some", "find"})
    public void testNextIsCachedAndCloseOnlyFollowsShortCircuit(String method) {
        assertStringWithJavet("""
                (() => ['empty', 'all', 'none', 'middle'].map(match => {
                  const log = [];
                  let index = 0;
                  const iterator = {
                    get next() {
                      log.push('get:next');
                      return function () {
                        log.push('next:' + (this === iterator));
                        const current = index++;
                        return {
                          get done() { log.push('done:' + current); return match === 'empty' || current === 3; },
                          get value() { log.push('value:' + current); return current + 5; }
                        };
                      };
                    },
                    get return() {
                      log.push('get:return');
                      return function () {
                        log.push('return:' + (this === iterator) + ':' + arguments.length);
                        return {get value() { throw new Error('unused'); }};
                      };
                    }
                  };
                  const result = Iterator.prototype['%s'].call(iterator, function (value, i) {
                    'use strict';
                    log.push(['callback', value, i, this === undefined, arguments.length].join(':'));
                    Object.defineProperty(iterator, 'next', {value() { throw new Error('uncached'); }});
                    return match === 'all' || (match === 'middle' && i === 1) ? {} : 0;
                  });
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """.formatted(method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"every", "some", "find"})
    public void testStepErrorsDoNotCloseIterator(String method) {
        assertStringWithJavet("""
                (() => ['next', 'done', 'value'].map(failure => {
                  const log = [];
                  const marker = {};
                  const iterator = {
                    next() {
                      log.push('next');
                      if (failure === 'next') throw marker;
                      return {
                        get done() { log.push('done'); if (failure === 'done') throw marker; return false; },
                        get value() { log.push('value'); throw marker; }
                      };
                    },
                    return() { log.push('return'); return {}; }
                  };
                  try { Iterator.prototype['%s'].call(iterator, () => { log.push('callback'); return true; }); }
                  catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                }).join(';'))()
                """.formatted(method));
    }
}
