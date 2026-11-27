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

package com.caoccao.qjs4j.builtins.array;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class ArrayCallbackTraversalTest extends BaseJavetTest {
    @Test
    public void testAbruptCompletionsStopPropertyAccess() {
        assertStringWithJavet("""
                (() => ['has', 'get', 'callback'].map(failure => {
                  const log = [];
                  const marker = {};
                  const target = {0: 2, 1: 4, length: 2};
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); if (failure === 'has') throw marker; return Reflect.has(t, k); },
                    get(t, k, r) {
                      log.push('get:' + k);
                      if (k !== 'length' && failure === 'get') throw marker;
                      return Reflect.get(t, k, r);
                    }
                  });
                  try {
                    const result = Array.prototype['every'].call(obj, function () {
                      log.push('callback');
                      if (failure === 'callback') throw marker;
                      return true;
                    }, 0);
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                }).join(';'))()
                """, """
                (() => ['has', 'get', 'callback'].map(failure => {
                  const log = [];
                  const marker = {};
                  const target = {0: 2, 1: 4, length: 2};
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); if (failure === 'has') throw marker; return Reflect.has(t, k); },
                    get(t, k, r) {
                      log.push('get:' + k);
                      if (k !== 'length' && failure === 'get') throw marker;
                      return Reflect.get(t, k, r);
                    }
                  });
                  try {
                    const result = Array.prototype['some'].call(obj, function () {
                      log.push('callback');
                      if (failure === 'callback') throw marker;
                      return true;
                    }, 0);
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                }).join(';'))()
                """, """
                (() => ['has', 'get', 'callback'].map(failure => {
                  const log = [];
                  const marker = {};
                  const target = {0: 2, 1: 4, length: 2};
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); if (failure === 'has') throw marker; return Reflect.has(t, k); },
                    get(t, k, r) {
                      log.push('get:' + k);
                      if (k !== 'length' && failure === 'get') throw marker;
                      return Reflect.get(t, k, r);
                    }
                  });
                  try {
                    const result = Array.prototype['find'].call(obj, function () {
                      log.push('callback');
                      if (failure === 'callback') throw marker;
                      return true;
                    }, 0);
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                }).join(';'))()
                """, """
                (() => ['has', 'get', 'callback'].map(failure => {
                  const log = [];
                  const marker = {};
                  const target = {0: 2, 1: 4, length: 2};
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); if (failure === 'has') throw marker; return Reflect.has(t, k); },
                    get(t, k, r) {
                      log.push('get:' + k);
                      if (k !== 'length' && failure === 'get') throw marker;
                      return Reflect.get(t, k, r);
                    }
                  });
                  try {
                    const result = Array.prototype['findIndex'].call(obj, function () {
                      log.push('callback');
                      if (failure === 'callback') throw marker;
                      return true;
                    }, 0);
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                }).join(';'))()
                """, """
                (() => ['has', 'get', 'callback'].map(failure => {
                  const log = [];
                  const marker = {};
                  const target = {0: 2, 1: 4, length: 2};
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); if (failure === 'has') throw marker; return Reflect.has(t, k); },
                    get(t, k, r) {
                      log.push('get:' + k);
                      if (k !== 'length' && failure === 'get') throw marker;
                      return Reflect.get(t, k, r);
                    }
                  });
                  try {
                    const result = Array.prototype['findLast'].call(obj, function () {
                      log.push('callback');
                      if (failure === 'callback') throw marker;
                      return true;
                    }, 0);
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                }).join(';'))()
                """, """
                (() => ['has', 'get', 'callback'].map(failure => {
                  const log = [];
                  const marker = {};
                  const target = {0: 2, 1: 4, length: 2};
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); if (failure === 'has') throw marker; return Reflect.has(t, k); },
                    get(t, k, r) {
                      log.push('get:' + k);
                      if (k !== 'length' && failure === 'get') throw marker;
                      return Reflect.get(t, k, r);
                    }
                  });
                  try {
                    const result = Array.prototype['findLastIndex'].call(obj, function () {
                      log.push('callback');
                      if (failure === 'callback') throw marker;
                      return true;
                    }, 0);
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                }).join(';'))()
                """, """
                (() => ['has', 'get', 'callback'].map(failure => {
                  const log = [];
                  const marker = {};
                  const target = {0: 2, 1: 4, length: 2};
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); if (failure === 'has') throw marker; return Reflect.has(t, k); },
                    get(t, k, r) {
                      log.push('get:' + k);
                      if (k !== 'length' && failure === 'get') throw marker;
                      return Reflect.get(t, k, r);
                    }
                  });
                  try {
                    const result = Array.prototype['reduce'].call(obj, function () {
                      log.push('callback');
                      if (failure === 'callback') throw marker;
                      return true;
                    }, 0);
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                }).join(';'))()
                """, """
                (() => ['has', 'get', 'callback'].map(failure => {
                  const log = [];
                  const marker = {};
                  const target = {0: 2, 1: 4, length: 2};
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); if (failure === 'has') throw marker; return Reflect.has(t, k); },
                    get(t, k, r) {
                      log.push('get:' + k);
                      if (k !== 'length' && failure === 'get') throw marker;
                      return Reflect.get(t, k, r);
                    }
                  });
                  try {
                    const result = Array.prototype['reduceRight'].call(obj, function () {
                      log.push('callback');
                      if (failure === 'callback') throw marker;
                      return true;
                    }, 0);
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                }).join(';'))()
                """);
    }

    @Test
    public void testJoinNumberFormatting() {
        assertStringWithJavet("""
                (() => {
                  const values = Array.from([-0, NaN, Infinity, -Infinity, -2147483648, 2147483647,
                      -2147483649, 2147483648, 0.125, 1e-8, 1e21]);
                  return values.join('|');
                })()""", """
                (() => {
                  const values = Float64Array.from([-0, NaN, Infinity, -Infinity, -2147483648, 2147483647,
                      -2147483649, 2147483648, 0.125, 1e-8, 1e21]);
                  return values.join('|');
                })()""");
    }

    @Test
    public void testLengthAndCallbackValidationOrder() {
        assertStringWithJavet("""
                (() => ['zero', 'coerce', 'get'].flatMap(failure => [undefined, null, 3].map(callback => {
                  const log = [];
                  const marker = {};
                  const obj = {get length() {
                    log.push('length');
                    if (failure === 'get') throw marker;
                    return {valueOf() { log.push('valueOf'); if (failure === 'coerce') throw marker; return 0; }};
                  }};
                  try { Array.prototype['every'].call(obj, callback); return 'missing error'; }
                  catch (e) { return (e === marker ? 'marker' : e.name) + '|' + log.join(','); }
                })).join(';'))()
                """, """
                (() => [null, undefined, 'ab', 42, true].map(receiver => {
                  const method = 'every';
                  const log = [];
                  try {
                    const result = Array.prototype[method].call(receiver, function (a, b, c, d) {
                      const reducing = method.startsWith('reduce');
                      log.push(typeof (reducing ? d : c));
                      return reducing ? a + b : true;
                    }, 'seed');
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return e.name; }
                }).join(';'))()
                """, """
                (() => ['zero', 'coerce', 'get'].flatMap(failure => [undefined, null, 3].map(callback => {
                  const log = [];
                  const marker = {};
                  const obj = {get length() {
                    log.push('length');
                    if (failure === 'get') throw marker;
                    return {valueOf() { log.push('valueOf'); if (failure === 'coerce') throw marker; return 0; }};
                  }};
                  try { Array.prototype['some'].call(obj, callback); return 'missing error'; }
                  catch (e) { return (e === marker ? 'marker' : e.name) + '|' + log.join(','); }
                })).join(';'))()
                """, """
                (() => [null, undefined, 'ab', 42, true].map(receiver => {
                  const method = 'some';
                  const log = [];
                  try {
                    const result = Array.prototype[method].call(receiver, function (a, b, c, d) {
                      const reducing = method.startsWith('reduce');
                      log.push(typeof (reducing ? d : c));
                      return reducing ? a + b : true;
                    }, 'seed');
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return e.name; }
                }).join(';'))()
                """, """
                (() => ['zero', 'coerce', 'get'].flatMap(failure => [undefined, null, 3].map(callback => {
                  const log = [];
                  const marker = {};
                  const obj = {get length() {
                    log.push('length');
                    if (failure === 'get') throw marker;
                    return {valueOf() { log.push('valueOf'); if (failure === 'coerce') throw marker; return 0; }};
                  }};
                  try { Array.prototype['find'].call(obj, callback); return 'missing error'; }
                  catch (e) { return (e === marker ? 'marker' : e.name) + '|' + log.join(','); }
                })).join(';'))()
                """, """
                (() => [null, undefined, 'ab', 42, true].map(receiver => {
                  const method = 'find';
                  const log = [];
                  try {
                    const result = Array.prototype[method].call(receiver, function (a, b, c, d) {
                      const reducing = method.startsWith('reduce');
                      log.push(typeof (reducing ? d : c));
                      return reducing ? a + b : true;
                    }, 'seed');
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return e.name; }
                }).join(';'))()
                """, """
                (() => ['zero', 'coerce', 'get'].flatMap(failure => [undefined, null, 3].map(callback => {
                  const log = [];
                  const marker = {};
                  const obj = {get length() {
                    log.push('length');
                    if (failure === 'get') throw marker;
                    return {valueOf() { log.push('valueOf'); if (failure === 'coerce') throw marker; return 0; }};
                  }};
                  try { Array.prototype['findIndex'].call(obj, callback); return 'missing error'; }
                  catch (e) { return (e === marker ? 'marker' : e.name) + '|' + log.join(','); }
                })).join(';'))()
                """, """
                (() => [null, undefined, 'ab', 42, true].map(receiver => {
                  const method = 'findIndex';
                  const log = [];
                  try {
                    const result = Array.prototype[method].call(receiver, function (a, b, c, d) {
                      const reducing = method.startsWith('reduce');
                      log.push(typeof (reducing ? d : c));
                      return reducing ? a + b : true;
                    }, 'seed');
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return e.name; }
                }).join(';'))()
                """, """
                (() => ['zero', 'coerce', 'get'].flatMap(failure => [undefined, null, 3].map(callback => {
                  const log = [];
                  const marker = {};
                  const obj = {get length() {
                    log.push('length');
                    if (failure === 'get') throw marker;
                    return {valueOf() { log.push('valueOf'); if (failure === 'coerce') throw marker; return 0; }};
                  }};
                  try { Array.prototype['findLast'].call(obj, callback); return 'missing error'; }
                  catch (e) { return (e === marker ? 'marker' : e.name) + '|' + log.join(','); }
                })).join(';'))()
                """, """
                (() => [null, undefined, 'ab', 42, true].map(receiver => {
                  const method = 'findLast';
                  const log = [];
                  try {
                    const result = Array.prototype[method].call(receiver, function (a, b, c, d) {
                      const reducing = method.startsWith('reduce');
                      log.push(typeof (reducing ? d : c));
                      return reducing ? a + b : true;
                    }, 'seed');
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return e.name; }
                }).join(';'))()
                """, """
                (() => ['zero', 'coerce', 'get'].flatMap(failure => [undefined, null, 3].map(callback => {
                  const log = [];
                  const marker = {};
                  const obj = {get length() {
                    log.push('length');
                    if (failure === 'get') throw marker;
                    return {valueOf() { log.push('valueOf'); if (failure === 'coerce') throw marker; return 0; }};
                  }};
                  try { Array.prototype['findLastIndex'].call(obj, callback); return 'missing error'; }
                  catch (e) { return (e === marker ? 'marker' : e.name) + '|' + log.join(','); }
                })).join(';'))()
                """, """
                (() => [null, undefined, 'ab', 42, true].map(receiver => {
                  const method = 'findLastIndex';
                  const log = [];
                  try {
                    const result = Array.prototype[method].call(receiver, function (a, b, c, d) {
                      const reducing = method.startsWith('reduce');
                      log.push(typeof (reducing ? d : c));
                      return reducing ? a + b : true;
                    }, 'seed');
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return e.name; }
                }).join(';'))()
                """, """
                (() => ['zero', 'coerce', 'get'].flatMap(failure => [undefined, null, 3].map(callback => {
                  const log = [];
                  const marker = {};
                  const obj = {get length() {
                    log.push('length');
                    if (failure === 'get') throw marker;
                    return {valueOf() { log.push('valueOf'); if (failure === 'coerce') throw marker; return 0; }};
                  }};
                  try { Array.prototype['reduce'].call(obj, callback); return 'missing error'; }
                  catch (e) { return (e === marker ? 'marker' : e.name) + '|' + log.join(','); }
                })).join(';'))()
                """, """
                (() => [null, undefined, 'ab', 42, true].map(receiver => {
                  const method = 'reduce';
                  const log = [];
                  try {
                    const result = Array.prototype[method].call(receiver, function (a, b, c, d) {
                      const reducing = method.startsWith('reduce');
                      log.push(typeof (reducing ? d : c));
                      return reducing ? a + b : true;
                    }, 'seed');
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return e.name; }
                }).join(';'))()
                """, """
                (() => ['zero', 'coerce', 'get'].flatMap(failure => [undefined, null, 3].map(callback => {
                  const log = [];
                  const marker = {};
                  const obj = {get length() {
                    log.push('length');
                    if (failure === 'get') throw marker;
                    return {valueOf() { log.push('valueOf'); if (failure === 'coerce') throw marker; return 0; }};
                  }};
                  try { Array.prototype['reduceRight'].call(obj, callback); return 'missing error'; }
                  catch (e) { return (e === marker ? 'marker' : e.name) + '|' + log.join(','); }
                })).join(';'))()
                """, """
                (() => [null, undefined, 'ab', 42, true].map(receiver => {
                  const method = 'reduceRight';
                  const log = [];
                  try {
                    const result = Array.prototype[method].call(receiver, function (a, b, c, d) {
                      const reducing = method.startsWith('reduce');
                      log.push(typeof (reducing ? d : c));
                      return reducing ? a + b : true;
                    }, 'seed');
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return e.name; }
                }).join(';'))()
                """);
    }

    @Test
    public void testLocalizedElementCalls() {
        assertStringWithJavet("""
                (() => {
                  const array = [1, 2], options = {}, log = [];
                  const original = Object.getOwnPropertyDescriptor(Number.prototype, 'toLocaleString');
                  try {
                    Object.defineProperty(Number.prototype, 'toLocaleString', {
                      configurable: true,
                      get: function () {
                        'use strict';
                        log.push(typeof this);
                        return new Proxy(function (locales, actualOptions) {
                          'use strict';
                          log.push(typeof this, arguments.length, locales, actualOptions === options);
                          return String(this);
                        }, {});
                      }
                    });
                    const text = array.toLocaleString('de', options, 'extra');
                    Object.defineProperty(Number.prototype, 'toLocaleString', {value: 42});
                    try { array.toLocaleString(); } catch (error) { log.push(error.name); }
                    Object.defineProperty(Number.prototype, 'toLocaleString', original);
                    return [text, array.join(), ...log].join('|');
                  } finally {
                    Object.defineProperty(Number.prototype, 'toLocaleString', original);
                  }
                })()""", """
                (() => {
                  const array = new Uint8Array([1, 2]), options = {}, log = [];
                  const original = Object.getOwnPropertyDescriptor(Number.prototype, 'toLocaleString');
                  try {
                    Object.defineProperty(Number.prototype, 'toLocaleString', {
                      configurable: true,
                      get: function () {
                        'use strict';
                        log.push(typeof this);
                        return new Proxy(function (locales, actualOptions) {
                          'use strict';
                          log.push(typeof this, arguments.length, locales, actualOptions === options);
                          return String(this);
                        }, {});
                      }
                    });
                    const text = array.toLocaleString('de', options, 'extra');
                    Object.defineProperty(Number.prototype, 'toLocaleString', {value: 42});
                    try { array.toLocaleString(); } catch (error) { log.push(error.name); }
                    Object.defineProperty(Number.prototype, 'toLocaleString', original);
                    return [text, array.join(), ...log].join('|');
                  } finally {
                    Object.defineProperty(Number.prototype, 'toLocaleString', original);
                  }
                })()""");
    }

    @Test
    public void testMutationRetainsLengthAndObservesDeletedElements() {
        assertStringWithJavet("""
                (() => ['delete', 'shrink', 'fill'].map(change => {
                  const method = 'every';
                  const reducing = method.startsWith('reduce');
                  const array = [2, , 6, 8];
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'delete') { delete array[0]; delete array[2]; delete array[3]; }
                      if (change === 'shrink') array.length = 1;
                      if (change === 'fill') array[1] = 4;
                      array[4] = 10;
                    }
                    return reducing ? a + '/' + String(value) : method === 'every';
                  }, 'seed');
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['delete', 'shrink', 'fill'].map(change => {
                  const method = 'some';
                  const reducing = method.startsWith('reduce');
                  const array = [2, , 6, 8];
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'delete') { delete array[0]; delete array[2]; delete array[3]; }
                      if (change === 'shrink') array.length = 1;
                      if (change === 'fill') array[1] = 4;
                      array[4] = 10;
                    }
                    return reducing ? a + '/' + String(value) : method === 'every';
                  }, 'seed');
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['delete', 'shrink', 'fill'].map(change => {
                  const method = 'find';
                  const reducing = method.startsWith('reduce');
                  const array = [2, , 6, 8];
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'delete') { delete array[0]; delete array[2]; delete array[3]; }
                      if (change === 'shrink') array.length = 1;
                      if (change === 'fill') array[1] = 4;
                      array[4] = 10;
                    }
                    return reducing ? a + '/' + String(value) : method === 'every';
                  }, 'seed');
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['delete', 'shrink', 'fill'].map(change => {
                  const method = 'findIndex';
                  const reducing = method.startsWith('reduce');
                  const array = [2, , 6, 8];
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'delete') { delete array[0]; delete array[2]; delete array[3]; }
                      if (change === 'shrink') array.length = 1;
                      if (change === 'fill') array[1] = 4;
                      array[4] = 10;
                    }
                    return reducing ? a + '/' + String(value) : method === 'every';
                  }, 'seed');
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['delete', 'shrink', 'fill'].map(change => {
                  const method = 'findLast';
                  const reducing = method.startsWith('reduce');
                  const array = [2, , 6, 8];
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'delete') { delete array[0]; delete array[2]; delete array[3]; }
                      if (change === 'shrink') array.length = 1;
                      if (change === 'fill') array[1] = 4;
                      array[4] = 10;
                    }
                    return reducing ? a + '/' + String(value) : method === 'every';
                  }, 'seed');
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['delete', 'shrink', 'fill'].map(change => {
                  const method = 'findLastIndex';
                  const reducing = method.startsWith('reduce');
                  const array = [2, , 6, 8];
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'delete') { delete array[0]; delete array[2]; delete array[3]; }
                      if (change === 'shrink') array.length = 1;
                      if (change === 'fill') array[1] = 4;
                      array[4] = 10;
                    }
                    return reducing ? a + '/' + String(value) : method === 'every';
                  }, 'seed');
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['delete', 'shrink', 'fill'].map(change => {
                  const method = 'reduce';
                  const reducing = method.startsWith('reduce');
                  const array = [2, , 6, 8];
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'delete') { delete array[0]; delete array[2]; delete array[3]; }
                      if (change === 'shrink') array.length = 1;
                      if (change === 'fill') array[1] = 4;
                      array[4] = 10;
                    }
                    return reducing ? a + '/' + String(value) : method === 'every';
                  }, 'seed');
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['delete', 'shrink', 'fill'].map(change => {
                  const method = 'reduceRight';
                  const reducing = method.startsWith('reduce');
                  const array = [2, , 6, 8];
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'delete') { delete array[0]; delete array[2]; delete array[3]; }
                      if (change === 'shrink') array.length = 1;
                      if (change === 'fill') array[1] = 4;
                      array[4] = 10;
                    }
                    return reducing ? a + '/' + String(value) : method === 'every';
                  }, 'seed');
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """);
    }

    @Test
    public void testProxyOrderCallbackArgumentsAndShortCircuiting() {
        assertStringWithJavet("""
                (() => ['all', 'none', 'middle'].map(match => {
                  const method = 'every';
                  const reducing = method.startsWith('reduce');
                  const log = [];
                  const receiver = {};
                  const target = Object.create({get 1() { log.push('inherited:' + (this === obj)); return 4; }});
                  target[0] = 2;
                  target.length = 4;
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); return Reflect.has(t, k); },
                    get(t, k, r) { log.push('get:' + k); return Reflect.get(t, k, r); }
                  });
                  const result = Array.prototype[method].call(obj, function (a, b, c, d) {
                    'use strict';
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(['call', String(value), index, (reducing ? d : c) === obj,
                      this === (reducing ? undefined : receiver), arguments.length].join(':'));
                    if (reducing) return a + '/' + String(value);
                    return match === 'all' || (match === 'middle' && index === 1) ? 1n : 0n;
                  }, reducing ? 'seed' : receiver);
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['all', 'none', 'middle'].map(match => {
                  const method = 'some';
                  const reducing = method.startsWith('reduce');
                  const log = [];
                  const receiver = {};
                  const target = Object.create({get 1() { log.push('inherited:' + (this === obj)); return 4; }});
                  target[0] = 2;
                  target.length = 4;
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); return Reflect.has(t, k); },
                    get(t, k, r) { log.push('get:' + k); return Reflect.get(t, k, r); }
                  });
                  const result = Array.prototype[method].call(obj, function (a, b, c, d) {
                    'use strict';
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(['call', String(value), index, (reducing ? d : c) === obj,
                      this === (reducing ? undefined : receiver), arguments.length].join(':'));
                    if (reducing) return a + '/' + String(value);
                    return match === 'all' || (match === 'middle' && index === 1) ? 1n : 0n;
                  }, reducing ? 'seed' : receiver);
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['all', 'none', 'middle'].map(match => {
                  const method = 'find';
                  const reducing = method.startsWith('reduce');
                  const log = [];
                  const receiver = {};
                  const target = Object.create({get 1() { log.push('inherited:' + (this === obj)); return 4; }});
                  target[0] = 2;
                  target.length = 4;
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); return Reflect.has(t, k); },
                    get(t, k, r) { log.push('get:' + k); return Reflect.get(t, k, r); }
                  });
                  const result = Array.prototype[method].call(obj, function (a, b, c, d) {
                    'use strict';
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(['call', String(value), index, (reducing ? d : c) === obj,
                      this === (reducing ? undefined : receiver), arguments.length].join(':'));
                    if (reducing) return a + '/' + String(value);
                    return match === 'all' || (match === 'middle' && index === 1) ? 1n : 0n;
                  }, reducing ? 'seed' : receiver);
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['all', 'none', 'middle'].map(match => {
                  const method = 'findIndex';
                  const reducing = method.startsWith('reduce');
                  const log = [];
                  const receiver = {};
                  const target = Object.create({get 1() { log.push('inherited:' + (this === obj)); return 4; }});
                  target[0] = 2;
                  target.length = 4;
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); return Reflect.has(t, k); },
                    get(t, k, r) { log.push('get:' + k); return Reflect.get(t, k, r); }
                  });
                  const result = Array.prototype[method].call(obj, function (a, b, c, d) {
                    'use strict';
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(['call', String(value), index, (reducing ? d : c) === obj,
                      this === (reducing ? undefined : receiver), arguments.length].join(':'));
                    if (reducing) return a + '/' + String(value);
                    return match === 'all' || (match === 'middle' && index === 1) ? 1n : 0n;
                  }, reducing ? 'seed' : receiver);
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['all', 'none', 'middle'].map(match => {
                  const method = 'findLast';
                  const reducing = method.startsWith('reduce');
                  const log = [];
                  const receiver = {};
                  const target = Object.create({get 1() { log.push('inherited:' + (this === obj)); return 4; }});
                  target[0] = 2;
                  target.length = 4;
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); return Reflect.has(t, k); },
                    get(t, k, r) { log.push('get:' + k); return Reflect.get(t, k, r); }
                  });
                  const result = Array.prototype[method].call(obj, function (a, b, c, d) {
                    'use strict';
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(['call', String(value), index, (reducing ? d : c) === obj,
                      this === (reducing ? undefined : receiver), arguments.length].join(':'));
                    if (reducing) return a + '/' + String(value);
                    return match === 'all' || (match === 'middle' && index === 1) ? 1n : 0n;
                  }, reducing ? 'seed' : receiver);
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['all', 'none', 'middle'].map(match => {
                  const method = 'findLastIndex';
                  const reducing = method.startsWith('reduce');
                  const log = [];
                  const receiver = {};
                  const target = Object.create({get 1() { log.push('inherited:' + (this === obj)); return 4; }});
                  target[0] = 2;
                  target.length = 4;
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); return Reflect.has(t, k); },
                    get(t, k, r) { log.push('get:' + k); return Reflect.get(t, k, r); }
                  });
                  const result = Array.prototype[method].call(obj, function (a, b, c, d) {
                    'use strict';
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(['call', String(value), index, (reducing ? d : c) === obj,
                      this === (reducing ? undefined : receiver), arguments.length].join(':'));
                    if (reducing) return a + '/' + String(value);
                    return match === 'all' || (match === 'middle' && index === 1) ? 1n : 0n;
                  }, reducing ? 'seed' : receiver);
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['all', 'none', 'middle'].map(match => {
                  const method = 'reduce';
                  const reducing = method.startsWith('reduce');
                  const log = [];
                  const receiver = {};
                  const target = Object.create({get 1() { log.push('inherited:' + (this === obj)); return 4; }});
                  target[0] = 2;
                  target.length = 4;
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); return Reflect.has(t, k); },
                    get(t, k, r) { log.push('get:' + k); return Reflect.get(t, k, r); }
                  });
                  const result = Array.prototype[method].call(obj, function (a, b, c, d) {
                    'use strict';
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(['call', String(value), index, (reducing ? d : c) === obj,
                      this === (reducing ? undefined : receiver), arguments.length].join(':'));
                    if (reducing) return a + '/' + String(value);
                    return match === 'all' || (match === 'middle' && index === 1) ? 1n : 0n;
                  }, reducing ? 'seed' : receiver);
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """, """
                (() => ['all', 'none', 'middle'].map(match => {
                  const method = 'reduceRight';
                  const reducing = method.startsWith('reduce');
                  const log = [];
                  const receiver = {};
                  const target = Object.create({get 1() { log.push('inherited:' + (this === obj)); return 4; }});
                  target[0] = 2;
                  target.length = 4;
                  const obj = new Proxy(target, {
                    has(t, k) { log.push('has:' + k); return Reflect.has(t, k); },
                    get(t, k, r) { log.push('get:' + k); return Reflect.get(t, k, r); }
                  });
                  const result = Array.prototype[method].call(obj, function (a, b, c, d) {
                    'use strict';
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(['call', String(value), index, (reducing ? d : c) === obj,
                      this === (reducing ? undefined : receiver), arguments.length].join(':'));
                    if (reducing) return a + '/' + String(value);
                    return match === 'all' || (match === 'middle' && index === 1) ? 1n : 0n;
                  }, reducing ? 'seed' : receiver);
                  return String(result) + '|' + log.join(',');
                }).join(';'))()
                """);
    }

    @Test
    public void testReduceInitialValuePresenceAndInheritedAccumulator() {
        assertStringWithJavet("""
                (() => [false, true].flatMap(initial => [0, 1, 3].map(length => {
                  const log = [];
                  const proto = {get 1() { log.push('inherited'); return undefined; }};
                  const obj = Object.create(proto);
                  obj.length = length;
                  const callback = function (a, b, i) { log.push('call:' + i + ':' + String(a)); return 42; };
                  try {
                    const fn = Array.prototype['reduce'];
                    const result = initial ? fn.call(obj, callback, undefined) : fn.call(obj, callback);
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return e.name + '|' + log.join(','); }
                })).join(';'))()
                """, """
                (() => [false, true].flatMap(initial => [0, 1, 3].map(length => {
                  const log = [];
                  const proto = {get 1() { log.push('inherited'); return undefined; }};
                  const obj = Object.create(proto);
                  obj.length = length;
                  const callback = function (a, b, i) { log.push('call:' + i + ':' + String(a)); return 42; };
                  try {
                    const fn = Array.prototype['reduceRight'];
                    const result = initial ? fn.call(obj, callback, undefined) : fn.call(obj, callback);
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return e.name + '|' + log.join(','); }
                })).join(';'))()
                """);
    }

    @Test
    public void testReverseTraversalKeepsSafeIntegerIndices() {
        assertStringWithJavet("""
                (() => {
                  const log = [];
                  const marker = {};
                  const obj = new Proxy({length: Number.MAX_SAFE_INTEGER}, {
                    has(t, k) { log.push('has:' + k); return true; },
                    get(t, k) { log.push('get:' + k); return k === 'length' ? t.length : 'value'; }
                  });
                  try {
                    const method = 'findLast';
                    const result = Array.prototype[method].call(obj, function (a, b, c) {
                      log.push('call:' + (method === 'reduceRight' ? c : b));
                      if (method === 'reduceRight') throw marker;
                      return true;
                    }, 'seed');
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                })()""", """
                (() => {
                  const log = [];
                  const marker = {};
                  const obj = new Proxy({length: Number.MAX_SAFE_INTEGER}, {
                    has(t, k) { log.push('has:' + k); return true; },
                    get(t, k) { log.push('get:' + k); return k === 'length' ? t.length : 'value'; }
                  });
                  try {
                    const method = 'findLastIndex';
                    const result = Array.prototype[method].call(obj, function (a, b, c) {
                      log.push('call:' + (method === 'reduceRight' ? c : b));
                      if (method === 'reduceRight') throw marker;
                      return true;
                    }, 'seed');
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                })()""", """
                (() => {
                  const log = [];
                  const marker = {};
                  const obj = new Proxy({length: Number.MAX_SAFE_INTEGER}, {
                    has(t, k) { log.push('has:' + k); return true; },
                    get(t, k) { log.push('get:' + k); return k === 'length' ? t.length : 'value'; }
                  });
                  try {
                    const method = 'reduceRight';
                    const result = Array.prototype[method].call(obj, function (a, b, c) {
                      log.push('call:' + (method === 'reduceRight' ? c : b));
                      if (method === 'reduceRight') throw marker;
                      return true;
                    }, 'seed');
                    return String(result) + '|' + log.join(',');
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                })()""");
    }

}
