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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * Leaving a {@code for await} loop early awaits the iterator's {@code return()} result, as AsyncIteratorClose requires
 * and V8 does.
 * <p>
 * The loop called {@code return()} as it would for a sync iterator and went on, so the statement after the loop ran
 * ticks before V8's, a thenable returned by {@code return()} was never resolved, and a {@code return()} result that is
 * not an object was not rejected. Each case logs the order of events against a ticking promise chain and compares the
 * log with V8's.
 */
public class ForAwaitCloseOrderTest extends BaseJavetTest {
    private static final String HARNESS = """
            var log = [];
            function ticker(n) {
              var p = Promise.resolve();
              for (let i = 0; i < n; i++) { const k = i; p = p.then(() => log.push('t' + k)); }
              return p;
            }
            function makeIter(tag, kind) {
              var i = 0;
              return {
                [Symbol.asyncIterator]() { return this; },
                next() { log.push(tag + '-next' + i); return Promise.resolve({ value: i++, done: false }); },
                return() {
                  log.push(tag + '-return');
                  if (kind === 'sync') return { done: true };
                  if (kind === 'thenable') return { then(r) { log.push(tag + '-then'); r({ done: true }); } };
                  if (kind === 'primitive') return 1;
                  if (kind === 'missing') return undefined;
                  return Promise.resolve({ done: true });
                },
              };
            }
            """;

    @Test
    public void testBreakAndReturnAwaitTheClose() {
        assertStringWithJavet(HARNESS + """
                (async () => {
                  ticker(12);
                  var runs = ['promise', 'sync', 'thenable'].map(async kind => {
                    for await (const v of makeIter(kind, kind)) { log.push(kind + '-body' + v); if (v === 1) break; }
                    log.push(kind + '-after');
                  });
                  var arrays = (async () => {
                    for await (const v of [1, 2, 3]) { log.push('arr' + v); if (v === 2) break; }
                    log.push('arr-after');
                  })();
                  await Promise.all([...runs, arrays]);
                  await ticker(14);
                  return log.join(' ');
                })()""", HARNESS + """
                (async () => {
                  ticker(10);
                  async function early() {
                    for await (const v of makeIter('r', 'promise')) { return 'returned ' + v; }
                  }
                  var result = early().then(v => log.push(v));
                  outer: for (let round = 0; round < 2; round++) {
                    for await (const v of makeIter('c' + round, 'promise')) { continue outer; }
                  }
                  log.push('loops-done');
                  await result;
                  await ticker(12);
                  return log.join(' ');
                })()""");
    }

    @Test
    public void testCloseResults() {
        assertStringWithJavet(HARNESS + """
                (async () => {
                  try {
                    for await (const v of makeIter('p', 'primitive')) { break; }
                    return 'no error';
                  } catch (e) { return e.constructor.name; }
                })()""", HARNESS + """
                (async () => {
                  var messages = [];
                  for (const kind of ['missing', 'primitive']) {
                    try { for await (const v of makeIter(kind, kind)) { break; } }
                    catch (e) { messages.push(e.constructor.name + ': ' + e.message); }
                  }
                  var sync = { [Symbol.iterator]() { return this; }, next() { return { value: 1, done: false }; },
                    return() { return 5; } };
                  try { for await (const v of sync) { break; } } catch (e) { messages.push(e.message); }
                  return messages.join(' | ');
                })()""", """
                (async () => {
                  var log = [];
                  var iter = {
                    [Symbol.asyncIterator]() { return this; },
                    next() { return Promise.resolve({ value: 1, done: false }); },
                    return() { log.push('return'); throw new Error('close failed'); },
                  };
                  try {
                    for await (const v of iter) { break; }
                  } catch (e) { log.push(e.message); }
                  return log.join();
                })()""", """
                (async () => {
                  var log = [];
                  var iter = {
                    [Symbol.asyncIterator]() { return this; },
                    next() { return Promise.resolve({ value: 1, done: false }); },
                    get return() { log.push('get'); return undefined; },
                  };
                  for await (const v of iter) { break; }
                  return log.join();
                })()""",
                """
                        (async () => {
                          var log = [];
                          var sync = { i: 0, [Symbol.iterator]() { return this; }, next() { return { value: this.i++, done: false }; },
                            return() { log.push('sync-return'); return { value: Promise.resolve('rv'), done: true }; } };
                          for await (const v of sync) { if (v === 1) break; }
                          return log.join();
                        })()""");
    }

    @Test
    public void testClosingASyncIteratorChecksItsReturnMethodAndResult() {
        // Breaking out of `for await` over a sync iterable closes it through %AsyncFromSyncIteratorPrototype%.return,
        // which rejects, rather than throws, for every abrupt step.
        assertStringWithJavet(
                """
                        const make = decorate => ({
                          [Symbol.iterator]() {
                            const it = { i: 0, next() { return { value: this.i++, done: false }; } };
                            decorate(it);
                            return it;
                          },
                        });
                        const run = async iterable => {
                          try {
                            for await (const x of iterable) break;
                            return 'ok';
                          } catch (e) {
                            return e.name + ': ' + e.message;
                          }
                        };
                        (async () => {
                          const out = [];
                          const log = [];
                          const results = [
                            it => { it.return = function () { out.push('args ' + arguments.length); return {}; }; },
                            it => Object.defineProperty(it, 'return', { get() { throw new Error('getter'); } }),
                            it => { it.return = null; },
                            it => { it.return = function () { throw new Error('thrown'); }; },
                            it => { it.return = JSON.parse; },
                            it => { it.return = () => { throw 7; }; },
                            it => { it.return = () => 5; },
                            it => { it.return = () => Symbol('s'); },
                            it => { it.return = () => ({ get done() { throw new Error('done'); } }); },
                            it => { it.return = () => ({ done: true, get value() { throw new Error('value'); } }); },
                            it => { it.return = () => ({ done: false, value: Promise.reject(new Error('rejected')) }); },
                            it => { it.return = () => ({ done: 1, value: Promise.resolve(7) }); },
                          ];
                          for (const decorate of results) {
                            out.push(await run(make(decorate)));
                          }
                          const late = run(make(it => { it.return = function () { log.push('return'); throw new Error('late'); }; }));
                          Promise.resolve().then(() => log.push('tick1')).then(() => log.push('tick2'));
                          out.push(await late, log.join());
                          return out.join('\\n');
                        })()""");
        // V8 names the value ("number 5 is not a function"); qjs4j says "iterator return is not a function" here as
        // for every other iterator close, so only the type is compared.
        assertBooleanWithJavet("""
                (async () => {
                  try {
                    const iterable = {
                      [Symbol.iterator]() { return { next: () => ({ done: false }), return: 5 }; },
                    };
                    for await (const x of iterable) break;
                    return false;
                  } catch (e) {
                    return e instanceof TypeError;
                  }
                })()""");
    }

    @Test
    public void testIteratorAcquisitionStopsAtThrowingGetter() {
        assertStringWithJavet("""
                (async () => {
                  const trace = [];
                  const marker = {};
                  for (const failAt of ['async', 'sync', 'next']) {
                    const iterable = {
                      get [Symbol.asyncIterator]() {
                        trace.push('async');
                        if (failAt === 'async') throw marker;
                      },
                      get [Symbol.iterator]() {
                        trace.push('sync');
                        if (failAt === 'sync') throw marker;
                        return () => ({ get next() { trace.push('next'); throw marker; } });
                      }
                    };
                    try { for await (const x of iterable) trace.push('body'); }
                    catch (e) { trace.push(e === marker); }
                  }
                  return trace.join(',');
                })()""");
    }
}
