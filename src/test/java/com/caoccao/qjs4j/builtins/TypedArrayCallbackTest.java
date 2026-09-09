package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

public class TypedArrayCallbackTest extends BaseJavetTest {
    @ParameterizedTest
    @MethodSource("callbackMethods")
    public void testCallbackArgumentsAndShortCircuiting(String method) {
        assertStringWithJavet("""
                (() => [Uint8Array, Float64Array, BigInt64Array].flatMap(Type =>
                  ['all', 'none', 'middle'].map(match => {
                    const method = '%s';
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
                """.formatted(method));
    }

    @ParameterizedTest
    @MethodSource("callbackMethods")
    public void testCallbackExceptionsStopIteration(String method) {
        assertStringWithJavet("""
                (() => {
                  const log = [];
                  const marker = {};
                  const array = new Uint8Array([1, 2, 3]);
                  try {
                    array['%s'](function () { log.push('callback'); throw marker; }, 0);
                  } catch (e) { return (e === marker) + '|' + log.join(','); }
                  return 'missing error';
                })()
                """.formatted(method));
    }

    @ParameterizedTest
    @MethodSource("callbackMethods")
    public void testCallbacksRetainLengthAcrossBufferChanges(String method) {
        assertStringWithJavet("""
                (() => [false, true].flatMap(fixed => ['shrink', 'grow', 'detach'].map(change => {
                  const method = '%s';
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
                """.formatted(method));
    }

    @ParameterizedTest
    @MethodSource("callbackMethods")
    public void testEmptyArraysAndInvalidReceivers(String method) {
        assertStringWithJavet("""
                (() => {
                  const method = '%s';
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
                })()
                """.formatted(method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reduce", "reduceRight"})
    public void testReductionWithoutInitialValue(String method) {
        assertStringWithJavet("""
                (() => [[], [5], [5, 7, 11]].map(values => {
                  const array = new Int16Array(values);
                  const log = [];
                  try {
                    const result = array['%s']((accumulator, value, index) => {
                      log.push(accumulator + ':' + value + ':' + index);
                      return accumulator - value;
                    });
                    return result + '|' + log.join(',');
                  } catch (e) { return e.name + '|' + log.join(','); }
                }).join(';'))()
                """.formatted(method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"map", "filter"})
    public void testSpeciesConstructionOrder(String method) {
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
                  const result = array['%s']((value, index) => { log.push('callback:' + index); return value; });
                  return Array.from(result).join(',') + '|' + log.join(',');
                })()
                """.formatted(method));
    }

    private static Stream<String> callbackMethods() {
        return Stream.of("every", "filter", "find", "findIndex", "findLast", "findLastIndex", "forEach", "map",
                "reduce", "reduceRight", "some");
    }
}
