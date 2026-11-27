package com.caoccao.qjs4j.builtins.array;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class TypedArrayCallbackTest extends BaseJavetTest {
    @Test
    public void testCallbackArgumentsAndShortCircuiting() {
        assertStringWithJavet("""
                (() => [Uint8Array, Float64Array, BigInt64Array].flatMap(Type =>
                  ['all', 'none', 'middle'].map(match => {
                    const method = 'every';
                    const reducing = method.startsWith('reduce');
                    const big = Type === BigInt64Array;
                    const array = new Type(big ? [2n, 4n, 6n] : [2, 4, 6]);
                    const receiver = {};
                    const log = [];
                    const callback = function(a, b, c, d) {
                      'use strict';
                      const value = reducing ? b : a;
                      const index = reducing ? c : b;
                      const owner = reducing ? d : c;
                      log.push([String(value), index, owner === array,
                        this === (reducing ? undefined : receiver), arguments.length].join(':'));
                      if (reducing) return a + '/' + String(value);
                      const selected = match === 'all' || (match === 'middle' && index === 1);
                      return big ? (selected ? 1n : 0n) : (selected ? 1 : 0);
                    };
                    const result = array[method](callback, reducing ? 'seed' : receiver);
                    const output = ArrayBuffer.isView(result) ? Array.from(result, String).join(',') : String(result);
                    return output + '|' + log.join(',');
                  })).join(';'))()
                """, """
                (() => [Uint8Array, Float64Array, BigInt64Array].flatMap(Type =>
                  ['all', 'none', 'middle'].map(match => {
                    const method = 'filter';
                    const reducing = method.startsWith('reduce');
                    const big = Type === BigInt64Array;
                    const array = new Type(big ? [2n, 4n, 6n] : [2, 4, 6]);
                    const receiver = {};
                    const log = [];
                    const callback = function(a, b, c, d) {
                      'use strict';
                      const value = reducing ? b : a;
                      const index = reducing ? c : b;
                      const owner = reducing ? d : c;
                      log.push([String(value), index, owner === array,
                        this === (reducing ? undefined : receiver), arguments.length].join(':'));
                      if (reducing) return a + '/' + String(value);
                      const selected = match === 'all' || (match === 'middle' && index === 1);
                      return big ? (selected ? 1n : 0n) : (selected ? 1 : 0);
                    };
                    const result = array[method](callback, reducing ? 'seed' : receiver);
                    const output = ArrayBuffer.isView(result) ? Array.from(result, String).join(',') : String(result);
                    return output + '|' + log.join(',');
                  })).join(';'))()
                """, """
                (() => [Uint8Array, Float64Array, BigInt64Array].flatMap(Type =>
                  ['all', 'none', 'middle'].map(match => {
                    const method = 'find';
                    const reducing = method.startsWith('reduce');
                    const big = Type === BigInt64Array;
                    const array = new Type(big ? [2n, 4n, 6n] : [2, 4, 6]);
                    const receiver = {};
                    const log = [];
                    const callback = function(a, b, c, d) {
                      'use strict';
                      const value = reducing ? b : a;
                      const index = reducing ? c : b;
                      const owner = reducing ? d : c;
                      log.push([String(value), index, owner === array,
                        this === (reducing ? undefined : receiver), arguments.length].join(':'));
                      if (reducing) return a + '/' + String(value);
                      const selected = match === 'all' || (match === 'middle' && index === 1);
                      return big ? (selected ? 1n : 0n) : (selected ? 1 : 0);
                    };
                    const result = array[method](callback, reducing ? 'seed' : receiver);
                    const output = ArrayBuffer.isView(result) ? Array.from(result, String).join(',') : String(result);
                    return output + '|' + log.join(',');
                  })).join(';'))()
                """, """
                (() => [Uint8Array, Float64Array, BigInt64Array].flatMap(Type =>
                  ['all', 'none', 'middle'].map(match => {
                    const method = 'findIndex';
                    const reducing = method.startsWith('reduce');
                    const big = Type === BigInt64Array;
                    const array = new Type(big ? [2n, 4n, 6n] : [2, 4, 6]);
                    const receiver = {};
                    const log = [];
                    const callback = function(a, b, c, d) {
                      'use strict';
                      const value = reducing ? b : a;
                      const index = reducing ? c : b;
                      const owner = reducing ? d : c;
                      log.push([String(value), index, owner === array,
                        this === (reducing ? undefined : receiver), arguments.length].join(':'));
                      if (reducing) return a + '/' + String(value);
                      const selected = match === 'all' || (match === 'middle' && index === 1);
                      return big ? (selected ? 1n : 0n) : (selected ? 1 : 0);
                    };
                    const result = array[method](callback, reducing ? 'seed' : receiver);
                    const output = ArrayBuffer.isView(result) ? Array.from(result, String).join(',') : String(result);
                    return output + '|' + log.join(',');
                  })).join(';'))()
                """, """
                (() => [Uint8Array, Float64Array, BigInt64Array].flatMap(Type =>
                  ['all', 'none', 'middle'].map(match => {
                    const method = 'findLast';
                    const reducing = method.startsWith('reduce');
                    const big = Type === BigInt64Array;
                    const array = new Type(big ? [2n, 4n, 6n] : [2, 4, 6]);
                    const receiver = {};
                    const log = [];
                    const callback = function(a, b, c, d) {
                      'use strict';
                      const value = reducing ? b : a;
                      const index = reducing ? c : b;
                      const owner = reducing ? d : c;
                      log.push([String(value), index, owner === array,
                        this === (reducing ? undefined : receiver), arguments.length].join(':'));
                      if (reducing) return a + '/' + String(value);
                      const selected = match === 'all' || (match === 'middle' && index === 1);
                      return big ? (selected ? 1n : 0n) : (selected ? 1 : 0);
                    };
                    const result = array[method](callback, reducing ? 'seed' : receiver);
                    const output = ArrayBuffer.isView(result) ? Array.from(result, String).join(',') : String(result);
                    return output + '|' + log.join(',');
                  })).join(';'))()
                """, """
                (() => [Uint8Array, Float64Array, BigInt64Array].flatMap(Type =>
                  ['all', 'none', 'middle'].map(match => {
                    const method = 'findLastIndex';
                    const reducing = method.startsWith('reduce');
                    const big = Type === BigInt64Array;
                    const array = new Type(big ? [2n, 4n, 6n] : [2, 4, 6]);
                    const receiver = {};
                    const log = [];
                    const callback = function(a, b, c, d) {
                      'use strict';
                      const value = reducing ? b : a;
                      const index = reducing ? c : b;
                      const owner = reducing ? d : c;
                      log.push([String(value), index, owner === array,
                        this === (reducing ? undefined : receiver), arguments.length].join(':'));
                      if (reducing) return a + '/' + String(value);
                      const selected = match === 'all' || (match === 'middle' && index === 1);
                      return big ? (selected ? 1n : 0n) : (selected ? 1 : 0);
                    };
                    const result = array[method](callback, reducing ? 'seed' : receiver);
                    const output = ArrayBuffer.isView(result) ? Array.from(result, String).join(',') : String(result);
                    return output + '|' + log.join(',');
                  })).join(';'))()
                """, """
                (() => [Uint8Array, Float64Array, BigInt64Array].flatMap(Type =>
                  ['all', 'none', 'middle'].map(match => {
                    const method = 'forEach';
                    const reducing = method.startsWith('reduce');
                    const big = Type === BigInt64Array;
                    const array = new Type(big ? [2n, 4n, 6n] : [2, 4, 6]);
                    const receiver = {};
                    const log = [];
                    const callback = function(a, b, c, d) {
                      'use strict';
                      const value = reducing ? b : a;
                      const index = reducing ? c : b;
                      const owner = reducing ? d : c;
                      log.push([String(value), index, owner === array,
                        this === (reducing ? undefined : receiver), arguments.length].join(':'));
                      if (reducing) return a + '/' + String(value);
                      const selected = match === 'all' || (match === 'middle' && index === 1);
                      return big ? (selected ? 1n : 0n) : (selected ? 1 : 0);
                    };
                    const result = array[method](callback, reducing ? 'seed' : receiver);
                    const output = ArrayBuffer.isView(result) ? Array.from(result, String).join(',') : String(result);
                    return output + '|' + log.join(',');
                  })).join(';'))()
                """, """
                (() => [Uint8Array, Float64Array, BigInt64Array].flatMap(Type =>
                  ['all', 'none', 'middle'].map(match => {
                    const method = 'map';
                    const reducing = method.startsWith('reduce');
                    const big = Type === BigInt64Array;
                    const array = new Type(big ? [2n, 4n, 6n] : [2, 4, 6]);
                    const receiver = {};
                    const log = [];
                    const callback = function(a, b, c, d) {
                      'use strict';
                      const value = reducing ? b : a;
                      const index = reducing ? c : b;
                      const owner = reducing ? d : c;
                      log.push([String(value), index, owner === array,
                        this === (reducing ? undefined : receiver), arguments.length].join(':'));
                      if (reducing) return a + '/' + String(value);
                      const selected = match === 'all' || (match === 'middle' && index === 1);
                      return big ? (selected ? 1n : 0n) : (selected ? 1 : 0);
                    };
                    const result = array[method](callback, reducing ? 'seed' : receiver);
                    const output = ArrayBuffer.isView(result) ? Array.from(result, String).join(',') : String(result);
                    return output + '|' + log.join(',');
                  })).join(';'))()
                """, """
                (() => [Uint8Array, Float64Array, BigInt64Array].flatMap(Type =>
                  ['all', 'none', 'middle'].map(match => {
                    const method = 'reduce';
                    const reducing = method.startsWith('reduce');
                    const big = Type === BigInt64Array;
                    const array = new Type(big ? [2n, 4n, 6n] : [2, 4, 6]);
                    const receiver = {};
                    const log = [];
                    const callback = function(a, b, c, d) {
                      'use strict';
                      const value = reducing ? b : a;
                      const index = reducing ? c : b;
                      const owner = reducing ? d : c;
                      log.push([String(value), index, owner === array,
                        this === (reducing ? undefined : receiver), arguments.length].join(':'));
                      if (reducing) return a + '/' + String(value);
                      const selected = match === 'all' || (match === 'middle' && index === 1);
                      return big ? (selected ? 1n : 0n) : (selected ? 1 : 0);
                    };
                    const result = array[method](callback, reducing ? 'seed' : receiver);
                    const output = ArrayBuffer.isView(result) ? Array.from(result, String).join(',') : String(result);
                    return output + '|' + log.join(',');
                  })).join(';'))()
                """, """
                (() => [Uint8Array, Float64Array, BigInt64Array].flatMap(Type =>
                  ['all', 'none', 'middle'].map(match => {
                    const method = 'reduceRight';
                    const reducing = method.startsWith('reduce');
                    const big = Type === BigInt64Array;
                    const array = new Type(big ? [2n, 4n, 6n] : [2, 4, 6]);
                    const receiver = {};
                    const log = [];
                    const callback = function(a, b, c, d) {
                      'use strict';
                      const value = reducing ? b : a;
                      const index = reducing ? c : b;
                      const owner = reducing ? d : c;
                      log.push([String(value), index, owner === array,
                        this === (reducing ? undefined : receiver), arguments.length].join(':'));
                      if (reducing) return a + '/' + String(value);
                      const selected = match === 'all' || (match === 'middle' && index === 1);
                      return big ? (selected ? 1n : 0n) : (selected ? 1 : 0);
                    };
                    const result = array[method](callback, reducing ? 'seed' : receiver);
                    const output = ArrayBuffer.isView(result) ? Array.from(result, String).join(',') : String(result);
                    return output + '|' + log.join(',');
                  })).join(';'))()
                """, """
                (() => [Uint8Array, Float64Array, BigInt64Array].flatMap(Type =>
                  ['all', 'none', 'middle'].map(match => {
                    const method = 'some';
                    const reducing = method.startsWith('reduce');
                    const big = Type === BigInt64Array;
                    const array = new Type(big ? [2n, 4n, 6n] : [2, 4, 6]);
                    const receiver = {};
                    const log = [];
                    const callback = function(a, b, c, d) {
                      'use strict';
                      const value = reducing ? b : a;
                      const index = reducing ? c : b;
                      const owner = reducing ? d : c;
                      log.push([String(value), index, owner === array,
                        this === (reducing ? undefined : receiver), arguments.length].join(':'));
                      if (reducing) return a + '/' + String(value);
                      const selected = match === 'all' || (match === 'middle' && index === 1);
                      return big ? (selected ? 1n : 0n) : (selected ? 1 : 0);
                    };
                    const result = array[method](callback, reducing ? 'seed' : receiver);
                    const output = ArrayBuffer.isView(result) ? Array.from(result, String).join(',') : String(result);
                    return output + '|' + log.join(',');
                  })).join(';'))()
                """);
    }

    @Test
    public void testCallbackExceptionsStopIteration() {
        assertStringWithJavet("""
                (() => {
                  const log = [];
                  const marker = {};
                  const array = new Uint8Array([1, 2, 3]);
                  try {
                    array['every'](function () { log.push('callback'); throw marker; }, 0);
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()""", """
                (() => {
                  const log = [];
                  const marker = {};
                  const array = new Uint8Array([1, 2, 3]);
                  try {
                    array['filter'](function () { log.push('callback'); throw marker; }, 0);
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()""", """
                (() => {
                  const log = [];
                  const marker = {};
                  const array = new Uint8Array([1, 2, 3]);
                  try {
                    array['find'](function () { log.push('callback'); throw marker; }, 0);
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()""", """
                (() => {
                  const log = [];
                  const marker = {};
                  const array = new Uint8Array([1, 2, 3]);
                  try {
                    array['findIndex'](function () { log.push('callback'); throw marker; }, 0);
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()""", """
                (() => {
                  const log = [];
                  const marker = {};
                  const array = new Uint8Array([1, 2, 3]);
                  try {
                    array['findLast'](function () { log.push('callback'); throw marker; }, 0);
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()""", """
                (() => {
                  const log = [];
                  const marker = {};
                  const array = new Uint8Array([1, 2, 3]);
                  try {
                    array['findLastIndex'](function () { log.push('callback'); throw marker; }, 0);
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()""", """
                (() => {
                  const log = [];
                  const marker = {};
                  const array = new Uint8Array([1, 2, 3]);
                  try {
                    array['forEach'](function () { log.push('callback'); throw marker; }, 0);
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()""", """
                (() => {
                  const log = [];
                  const marker = {};
                  const array = new Uint8Array([1, 2, 3]);
                  try {
                    array['map'](function () { log.push('callback'); throw marker; }, 0);
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()""", """
                (() => {
                  const log = [];
                  const marker = {};
                  const array = new Uint8Array([1, 2, 3]);
                  try {
                    array['reduce'](function () { log.push('callback'); throw marker; }, 0);
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()""", """
                (() => {
                  const log = [];
                  const marker = {};
                  const array = new Uint8Array([1, 2, 3]);
                  try {
                    array['reduceRight'](function () { log.push('callback'); throw marker; }, 0);
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()""", """
                (() => {
                  const log = [];
                  const marker = {};
                  const array = new Uint8Array([1, 2, 3]);
                  try {
                    array['some'](function () { log.push('callback'); throw marker; }, 0);
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()""");
    }

    @Test
    public void testCallbacksRetainLengthAcrossBufferChanges() {
        assertStringWithJavet("""
                (() => [false, true].flatMap(fixed => ['shrink', 'grow', 'detach'].map(change => {
                  const method = 'every';
                  const reducing = method.startsWith('reduce');
                  const buffer = new ArrayBuffer(3, { maxByteLength: 5 });
                  const array = fixed ? new Uint8Array(buffer, 0, 3) : new Uint8Array(buffer);
                  array.set([2, 4, 6]);
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'detach') buffer.transfer();
                      else buffer.resize(change === 'shrink' ? 1 : 5);
                    }
                    if (reducing) return a + '/' + String(value);
                    // Keep predicates traversing every original index, including missing elements.
                    return method === 'every' || method === 'filter';
                  }, 'seed');
                  const output = ArrayBuffer.isView(result) ? Array.from(result).join(',') : String(result);
                  return output + '|' + log.join(',');
                })).join(';'))()
                """, """
                (() => [false, true].flatMap(fixed => ['shrink', 'grow', 'detach'].map(change => {
                  const method = 'filter';
                  const reducing = method.startsWith('reduce');
                  const buffer = new ArrayBuffer(3, { maxByteLength: 5 });
                  const array = fixed ? new Uint8Array(buffer, 0, 3) : new Uint8Array(buffer);
                  array.set([2, 4, 6]);
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'detach') buffer.transfer();
                      else buffer.resize(change === 'shrink' ? 1 : 5);
                    }
                    if (reducing) return a + '/' + String(value);
                    // Keep predicates traversing every original index, including missing elements.
                    return method === 'every' || method === 'filter';
                  }, 'seed');
                  const output = ArrayBuffer.isView(result) ? Array.from(result).join(',') : String(result);
                  return output + '|' + log.join(',');
                })).join(';'))()
                """, """
                (() => [false, true].flatMap(fixed => ['shrink', 'grow', 'detach'].map(change => {
                  const method = 'find';
                  const reducing = method.startsWith('reduce');
                  const buffer = new ArrayBuffer(3, { maxByteLength: 5 });
                  const array = fixed ? new Uint8Array(buffer, 0, 3) : new Uint8Array(buffer);
                  array.set([2, 4, 6]);
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'detach') buffer.transfer();
                      else buffer.resize(change === 'shrink' ? 1 : 5);
                    }
                    if (reducing) return a + '/' + String(value);
                    // Keep predicates traversing every original index, including missing elements.
                    return method === 'every' || method === 'filter';
                  }, 'seed');
                  const output = ArrayBuffer.isView(result) ? Array.from(result).join(',') : String(result);
                  return output + '|' + log.join(',');
                })).join(';'))()
                """, """
                (() => [false, true].flatMap(fixed => ['shrink', 'grow', 'detach'].map(change => {
                  const method = 'findIndex';
                  const reducing = method.startsWith('reduce');
                  const buffer = new ArrayBuffer(3, { maxByteLength: 5 });
                  const array = fixed ? new Uint8Array(buffer, 0, 3) : new Uint8Array(buffer);
                  array.set([2, 4, 6]);
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'detach') buffer.transfer();
                      else buffer.resize(change === 'shrink' ? 1 : 5);
                    }
                    if (reducing) return a + '/' + String(value);
                    // Keep predicates traversing every original index, including missing elements.
                    return method === 'every' || method === 'filter';
                  }, 'seed');
                  const output = ArrayBuffer.isView(result) ? Array.from(result).join(',') : String(result);
                  return output + '|' + log.join(',');
                })).join(';'))()
                """, """
                (() => [false, true].flatMap(fixed => ['shrink', 'grow', 'detach'].map(change => {
                  const method = 'findLast';
                  const reducing = method.startsWith('reduce');
                  const buffer = new ArrayBuffer(3, { maxByteLength: 5 });
                  const array = fixed ? new Uint8Array(buffer, 0, 3) : new Uint8Array(buffer);
                  array.set([2, 4, 6]);
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'detach') buffer.transfer();
                      else buffer.resize(change === 'shrink' ? 1 : 5);
                    }
                    if (reducing) return a + '/' + String(value);
                    // Keep predicates traversing every original index, including missing elements.
                    return method === 'every' || method === 'filter';
                  }, 'seed');
                  const output = ArrayBuffer.isView(result) ? Array.from(result).join(',') : String(result);
                  return output + '|' + log.join(',');
                })).join(';'))()
                """, """
                (() => [false, true].flatMap(fixed => ['shrink', 'grow', 'detach'].map(change => {
                  const method = 'findLastIndex';
                  const reducing = method.startsWith('reduce');
                  const buffer = new ArrayBuffer(3, { maxByteLength: 5 });
                  const array = fixed ? new Uint8Array(buffer, 0, 3) : new Uint8Array(buffer);
                  array.set([2, 4, 6]);
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'detach') buffer.transfer();
                      else buffer.resize(change === 'shrink' ? 1 : 5);
                    }
                    if (reducing) return a + '/' + String(value);
                    // Keep predicates traversing every original index, including missing elements.
                    return method === 'every' || method === 'filter';
                  }, 'seed');
                  const output = ArrayBuffer.isView(result) ? Array.from(result).join(',') : String(result);
                  return output + '|' + log.join(',');
                })).join(';'))()
                """, """
                (() => [false, true].flatMap(fixed => ['shrink', 'grow', 'detach'].map(change => {
                  const method = 'forEach';
                  const reducing = method.startsWith('reduce');
                  const buffer = new ArrayBuffer(3, { maxByteLength: 5 });
                  const array = fixed ? new Uint8Array(buffer, 0, 3) : new Uint8Array(buffer);
                  array.set([2, 4, 6]);
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'detach') buffer.transfer();
                      else buffer.resize(change === 'shrink' ? 1 : 5);
                    }
                    if (reducing) return a + '/' + String(value);
                    // Keep predicates traversing every original index, including missing elements.
                    return method === 'every' || method === 'filter';
                  }, 'seed');
                  const output = ArrayBuffer.isView(result) ? Array.from(result).join(',') : String(result);
                  return output + '|' + log.join(',');
                })).join(';'))()
                """, """
                (() => [false, true].flatMap(fixed => ['shrink', 'grow', 'detach'].map(change => {
                  const method = 'map';
                  const reducing = method.startsWith('reduce');
                  const buffer = new ArrayBuffer(3, { maxByteLength: 5 });
                  const array = fixed ? new Uint8Array(buffer, 0, 3) : new Uint8Array(buffer);
                  array.set([2, 4, 6]);
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'detach') buffer.transfer();
                      else buffer.resize(change === 'shrink' ? 1 : 5);
                    }
                    if (reducing) return a + '/' + String(value);
                    // Keep predicates traversing every original index, including missing elements.
                    return method === 'every' || method === 'filter';
                  }, 'seed');
                  const output = ArrayBuffer.isView(result) ? Array.from(result).join(',') : String(result);
                  return output + '|' + log.join(',');
                })).join(';'))()
                """, """
                (() => [false, true].flatMap(fixed => ['shrink', 'grow', 'detach'].map(change => {
                  const method = 'reduce';
                  const reducing = method.startsWith('reduce');
                  const buffer = new ArrayBuffer(3, { maxByteLength: 5 });
                  const array = fixed ? new Uint8Array(buffer, 0, 3) : new Uint8Array(buffer);
                  array.set([2, 4, 6]);
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'detach') buffer.transfer();
                      else buffer.resize(change === 'shrink' ? 1 : 5);
                    }
                    if (reducing) return a + '/' + String(value);
                    // Keep predicates traversing every original index, including missing elements.
                    return method === 'every' || method === 'filter';
                  }, 'seed');
                  const output = ArrayBuffer.isView(result) ? Array.from(result).join(',') : String(result);
                  return output + '|' + log.join(',');
                })).join(';'))()
                """, """
                (() => [false, true].flatMap(fixed => ['shrink', 'grow', 'detach'].map(change => {
                  const method = 'reduceRight';
                  const reducing = method.startsWith('reduce');
                  const buffer = new ArrayBuffer(3, { maxByteLength: 5 });
                  const array = fixed ? new Uint8Array(buffer, 0, 3) : new Uint8Array(buffer);
                  array.set([2, 4, 6]);
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'detach') buffer.transfer();
                      else buffer.resize(change === 'shrink' ? 1 : 5);
                    }
                    if (reducing) return a + '/' + String(value);
                    // Keep predicates traversing every original index, including missing elements.
                    return method === 'every' || method === 'filter';
                  }, 'seed');
                  const output = ArrayBuffer.isView(result) ? Array.from(result).join(',') : String(result);
                  return output + '|' + log.join(',');
                })).join(';'))()
                """, """
                (() => [false, true].flatMap(fixed => ['shrink', 'grow', 'detach'].map(change => {
                  const method = 'some';
                  const reducing = method.startsWith('reduce');
                  const buffer = new ArrayBuffer(3, { maxByteLength: 5 });
                  const array = fixed ? new Uint8Array(buffer, 0, 3) : new Uint8Array(buffer);
                  array.set([2, 4, 6]);
                  const log = [];
                  const result = array[method](function (a, b, c) {
                    const value = reducing ? b : a;
                    const index = reducing ? c : b;
                    log.push(index + ':' + String(value));
                    if (log.length === 1) {
                      if (change === 'detach') buffer.transfer();
                      else buffer.resize(change === 'shrink' ? 1 : 5);
                    }
                    if (reducing) return a + '/' + String(value);
                    // Keep predicates traversing every original index, including missing elements.
                    return method === 'every' || method === 'filter';
                  }, 'seed');
                  const output = ArrayBuffer.isView(result) ? Array.from(result).join(',') : String(result);
                  return output + '|' + log.join(',');
                })).join(';'))()
                """);
    }

    @Test
    public void testEmptyArraysAndInvalidReceivers() {
        assertStringWithJavet("""
                (() => {
                  const method = 'every';
                  const outcomes = [];
                  for (const initial of [[], [undefined], ['seed']]) {
                    let called = false;
                    try {
                      const result = new Uint8Array()[method](() => { called = true; }, ...initial);
                      outcomes.push(String(result) + ':' + called);
                    } catch (e) { outcomes.push(e.name + ':' + called); }
                  }
                  for (const kind of ['plain', 'proxy', 'detached', 'outOfBounds', 'empty']) {
                    const buffer = new ArrayBuffer(4, { maxByteLength: 8 });
                    let array = new Uint8Array(buffer, 2, 2);
                    if (kind === 'plain') array = {};
                    if (kind === 'proxy') array = new Proxy(array, {});
                    if (kind === 'detached') buffer.transfer();
                    if (kind === 'outOfBounds') buffer.resize(0);
                    if (kind === 'empty') array = new Uint8Array();
                    try { Uint8Array.prototype[method].call(array, null); }
                    catch (e) { outcomes.push(kind + ':' + e.name); }
                  }
                  return outcomes.join('|');
                })()""", """
                (() => {
                  const method = 'filter';
                  const outcomes = [];
                  for (const initial of [[], [undefined], ['seed']]) {
                    let called = false;
                    try {
                      const result = new Uint8Array()[method](() => { called = true; }, ...initial);
                      outcomes.push(String(result) + ':' + called);
                    } catch (e) { outcomes.push(e.name + ':' + called); }
                  }
                  for (const kind of ['plain', 'proxy', 'detached', 'outOfBounds', 'empty']) {
                    const buffer = new ArrayBuffer(4, { maxByteLength: 8 });
                    let array = new Uint8Array(buffer, 2, 2);
                    if (kind === 'plain') array = {};
                    if (kind === 'proxy') array = new Proxy(array, {});
                    if (kind === 'detached') buffer.transfer();
                    if (kind === 'outOfBounds') buffer.resize(0);
                    if (kind === 'empty') array = new Uint8Array();
                    try { Uint8Array.prototype[method].call(array, null); }
                    catch (e) { outcomes.push(kind + ':' + e.name); }
                  }
                  return outcomes.join('|');
                })()""", """
                (() => {
                  const method = 'find';
                  const outcomes = [];
                  for (const initial of [[], [undefined], ['seed']]) {
                    let called = false;
                    try {
                      const result = new Uint8Array()[method](() => { called = true; }, ...initial);
                      outcomes.push(String(result) + ':' + called);
                    } catch (e) { outcomes.push(e.name + ':' + called); }
                  }
                  for (const kind of ['plain', 'proxy', 'detached', 'outOfBounds', 'empty']) {
                    const buffer = new ArrayBuffer(4, { maxByteLength: 8 });
                    let array = new Uint8Array(buffer, 2, 2);
                    if (kind === 'plain') array = {};
                    if (kind === 'proxy') array = new Proxy(array, {});
                    if (kind === 'detached') buffer.transfer();
                    if (kind === 'outOfBounds') buffer.resize(0);
                    if (kind === 'empty') array = new Uint8Array();
                    try { Uint8Array.prototype[method].call(array, null); }
                    catch (e) { outcomes.push(kind + ':' + e.name); }
                  }
                  return outcomes.join('|');
                })()""", """
                (() => {
                  const method = 'findIndex';
                  const outcomes = [];
                  for (const initial of [[], [undefined], ['seed']]) {
                    let called = false;
                    try {
                      const result = new Uint8Array()[method](() => { called = true; }, ...initial);
                      outcomes.push(String(result) + ':' + called);
                    } catch (e) { outcomes.push(e.name + ':' + called); }
                  }
                  for (const kind of ['plain', 'proxy', 'detached', 'outOfBounds', 'empty']) {
                    const buffer = new ArrayBuffer(4, { maxByteLength: 8 });
                    let array = new Uint8Array(buffer, 2, 2);
                    if (kind === 'plain') array = {};
                    if (kind === 'proxy') array = new Proxy(array, {});
                    if (kind === 'detached') buffer.transfer();
                    if (kind === 'outOfBounds') buffer.resize(0);
                    if (kind === 'empty') array = new Uint8Array();
                    try { Uint8Array.prototype[method].call(array, null); }
                    catch (e) { outcomes.push(kind + ':' + e.name); }
                  }
                  return outcomes.join('|');
                })()""", """
                (() => {
                  const method = 'findLast';
                  const outcomes = [];
                  for (const initial of [[], [undefined], ['seed']]) {
                    let called = false;
                    try {
                      const result = new Uint8Array()[method](() => { called = true; }, ...initial);
                      outcomes.push(String(result) + ':' + called);
                    } catch (e) { outcomes.push(e.name + ':' + called); }
                  }
                  for (const kind of ['plain', 'proxy', 'detached', 'outOfBounds', 'empty']) {
                    const buffer = new ArrayBuffer(4, { maxByteLength: 8 });
                    let array = new Uint8Array(buffer, 2, 2);
                    if (kind === 'plain') array = {};
                    if (kind === 'proxy') array = new Proxy(array, {});
                    if (kind === 'detached') buffer.transfer();
                    if (kind === 'outOfBounds') buffer.resize(0);
                    if (kind === 'empty') array = new Uint8Array();
                    try { Uint8Array.prototype[method].call(array, null); }
                    catch (e) { outcomes.push(kind + ':' + e.name); }
                  }
                  return outcomes.join('|');
                })()""", """
                (() => {
                  const method = 'findLastIndex';
                  const outcomes = [];
                  for (const initial of [[], [undefined], ['seed']]) {
                    let called = false;
                    try {
                      const result = new Uint8Array()[method](() => { called = true; }, ...initial);
                      outcomes.push(String(result) + ':' + called);
                    } catch (e) { outcomes.push(e.name + ':' + called); }
                  }
                  for (const kind of ['plain', 'proxy', 'detached', 'outOfBounds', 'empty']) {
                    const buffer = new ArrayBuffer(4, { maxByteLength: 8 });
                    let array = new Uint8Array(buffer, 2, 2);
                    if (kind === 'plain') array = {};
                    if (kind === 'proxy') array = new Proxy(array, {});
                    if (kind === 'detached') buffer.transfer();
                    if (kind === 'outOfBounds') buffer.resize(0);
                    if (kind === 'empty') array = new Uint8Array();
                    try { Uint8Array.prototype[method].call(array, null); }
                    catch (e) { outcomes.push(kind + ':' + e.name); }
                  }
                  return outcomes.join('|');
                })()""", """
                (() => {
                  const method = 'forEach';
                  const outcomes = [];
                  for (const initial of [[], [undefined], ['seed']]) {
                    let called = false;
                    try {
                      const result = new Uint8Array()[method](() => { called = true; }, ...initial);
                      outcomes.push(String(result) + ':' + called);
                    } catch (e) { outcomes.push(e.name + ':' + called); }
                  }
                  for (const kind of ['plain', 'proxy', 'detached', 'outOfBounds', 'empty']) {
                    const buffer = new ArrayBuffer(4, { maxByteLength: 8 });
                    let array = new Uint8Array(buffer, 2, 2);
                    if (kind === 'plain') array = {};
                    if (kind === 'proxy') array = new Proxy(array, {});
                    if (kind === 'detached') buffer.transfer();
                    if (kind === 'outOfBounds') buffer.resize(0);
                    if (kind === 'empty') array = new Uint8Array();
                    try { Uint8Array.prototype[method].call(array, null); }
                    catch (e) { outcomes.push(kind + ':' + e.name); }
                  }
                  return outcomes.join('|');
                })()""", """
                (() => {
                  const method = 'map';
                  const outcomes = [];
                  for (const initial of [[], [undefined], ['seed']]) {
                    let called = false;
                    try {
                      const result = new Uint8Array()[method](() => { called = true; }, ...initial);
                      outcomes.push(String(result) + ':' + called);
                    } catch (e) { outcomes.push(e.name + ':' + called); }
                  }
                  for (const kind of ['plain', 'proxy', 'detached', 'outOfBounds', 'empty']) {
                    const buffer = new ArrayBuffer(4, { maxByteLength: 8 });
                    let array = new Uint8Array(buffer, 2, 2);
                    if (kind === 'plain') array = {};
                    if (kind === 'proxy') array = new Proxy(array, {});
                    if (kind === 'detached') buffer.transfer();
                    if (kind === 'outOfBounds') buffer.resize(0);
                    if (kind === 'empty') array = new Uint8Array();
                    try { Uint8Array.prototype[method].call(array, null); }
                    catch (e) { outcomes.push(kind + ':' + e.name); }
                  }
                  return outcomes.join('|');
                })()""", """
                (() => {
                  const method = 'reduce';
                  const outcomes = [];
                  for (const initial of [[], [undefined], ['seed']]) {
                    let called = false;
                    try {
                      const result = new Uint8Array()[method](() => { called = true; }, ...initial);
                      outcomes.push(String(result) + ':' + called);
                    } catch (e) { outcomes.push(e.name + ':' + called); }
                  }
                  for (const kind of ['plain', 'proxy', 'detached', 'outOfBounds', 'empty']) {
                    const buffer = new ArrayBuffer(4, { maxByteLength: 8 });
                    let array = new Uint8Array(buffer, 2, 2);
                    if (kind === 'plain') array = {};
                    if (kind === 'proxy') array = new Proxy(array, {});
                    if (kind === 'detached') buffer.transfer();
                    if (kind === 'outOfBounds') buffer.resize(0);
                    if (kind === 'empty') array = new Uint8Array();
                    try { Uint8Array.prototype[method].call(array, null); }
                    catch (e) { outcomes.push(kind + ':' + e.name); }
                  }
                  return outcomes.join('|');
                })()""", """
                (() => {
                  const method = 'reduceRight';
                  const outcomes = [];
                  for (const initial of [[], [undefined], ['seed']]) {
                    let called = false;
                    try {
                      const result = new Uint8Array()[method](() => { called = true; }, ...initial);
                      outcomes.push(String(result) + ':' + called);
                    } catch (e) { outcomes.push(e.name + ':' + called); }
                  }
                  for (const kind of ['plain', 'proxy', 'detached', 'outOfBounds', 'empty']) {
                    const buffer = new ArrayBuffer(4, { maxByteLength: 8 });
                    let array = new Uint8Array(buffer, 2, 2);
                    if (kind === 'plain') array = {};
                    if (kind === 'proxy') array = new Proxy(array, {});
                    if (kind === 'detached') buffer.transfer();
                    if (kind === 'outOfBounds') buffer.resize(0);
                    if (kind === 'empty') array = new Uint8Array();
                    try { Uint8Array.prototype[method].call(array, null); }
                    catch (e) { outcomes.push(kind + ':' + e.name); }
                  }
                  return outcomes.join('|');
                })()""", """
                (() => {
                  const method = 'some';
                  const outcomes = [];
                  for (const initial of [[], [undefined], ['seed']]) {
                    let called = false;
                    try {
                      const result = new Uint8Array()[method](() => { called = true; }, ...initial);
                      outcomes.push(String(result) + ':' + called);
                    } catch (e) { outcomes.push(e.name + ':' + called); }
                  }
                  for (const kind of ['plain', 'proxy', 'detached', 'outOfBounds', 'empty']) {
                    const buffer = new ArrayBuffer(4, { maxByteLength: 8 });
                    let array = new Uint8Array(buffer, 2, 2);
                    if (kind === 'plain') array = {};
                    if (kind === 'proxy') array = new Proxy(array, {});
                    if (kind === 'detached') buffer.transfer();
                    if (kind === 'outOfBounds') buffer.resize(0);
                    if (kind === 'empty') array = new Uint8Array();
                    try { Uint8Array.prototype[method].call(array, null); }
                    catch (e) { outcomes.push(kind + ':' + e.name); }
                  }
                  return outcomes.join('|');
                })()""");
    }

    @Test
    public void testReductionWithoutInitialValue() {
        assertStringWithJavet("""
                (() => [[], [5], [5, 7, 11]].map(values => {
                  const array = new Int16Array(values);
                  const log = [];
                  try {
                    const result = array['reduce']((accumulator, value, index) => {
                      log.push(accumulator + ':' + value + ':' + index);
                      return accumulator - value;
                    });
                    return result + '|' + log.join(',');
                  } catch (e) { return e.name + '|' + log.join(','); }
                }).join(';'))()
                """, """
                (() => [[], [5], [5, 7, 11]].map(values => {
                  const array = new Int16Array(values);
                  const log = [];
                  try {
                    const result = array['reduceRight']((accumulator, value, index) => {
                      log.push(accumulator + ':' + value + ':' + index);
                      return accumulator - value;
                    });
                    return result + '|' + log.join(',');
                  } catch (e) { return e.name + '|' + log.join(','); }
                }).join(';'))()
                """);
    }

    @Test
    public void testSpeciesConstructionOrder() {
        assertStringWithJavet("""
                (() => {
                  const log = [];
                  const array = new Uint8Array([2, 4]);
                  Object.defineProperty(array, 'length', { get() { throw 'observable length'; } });
                  Object.defineProperty(array, 'constructor', { get() {
                    log.push('constructor');
                    return { get [Symbol.species]() {
                      log.push('species');
                      return function (length) { log.push('construct:' + length); return new Uint8Array(length); };
                    } };
                  } });
                  const result = array['map']((value, index) => { log.push('callback:' + index); return value; });
                  return Array.from(result).join(',') + '|' + log.join(',');
                })()""", """
                (() => {
                  const log = [];
                  const array = new Uint8Array([2, 4]);
                  Object.defineProperty(array, 'length', { get() { throw 'observable length'; } });
                  Object.defineProperty(array, 'constructor', { get() {
                    log.push('constructor');
                    return { get [Symbol.species]() {
                      log.push('species');
                      return function (length) { log.push('construct:' + length); return new Uint8Array(length); };
                    } };
                  } });
                  const result = array['filter']((value, index) => { log.push('callback:' + index); return value; });
                  return Array.from(result).join(',') + '|' + log.join(',');
                })()""");
    }

}
