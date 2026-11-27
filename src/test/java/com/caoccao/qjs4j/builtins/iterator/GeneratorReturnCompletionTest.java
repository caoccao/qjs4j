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

package com.caoccao.qjs4j.builtins.iterator;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * {@code generator.return(v)} runs the generator's {@code finally} blocks, which may yield, await or throw.
 * <p>
 * A return completion was unwound with {@code v} itself as the exception payload and a VM-wide flag saying "this is a
 * return". A {@code yield} inside a {@code finally} suspended the generator and lost the flag, so on resumption the end
 * of the block rethrew {@code v} as an ordinary exception; a {@code throw} inside a {@code finally} was mistaken for
 * the return and swallowed; a {@code catch} inside a {@code finally} was skipped. Async generators threw a private
 * signal object instead, which a {@code catch} block could catch and which never finished delivering when a
 * {@code finally} awaited. Every case is compared against V8.
 */
public class GeneratorReturnCompletionTest extends BaseJavetTest {
    @Test
    public void testAsyncGeneratorReturnThroughFinally() {
        assertStringWithJavet(
                afterFirstAsyncYield("try { yield 1; } finally { yield 2; }",
                        "return JSON.stringify([await it.return(9), await it.next(), await it.next()]);"),
                afterFirstAsyncYield("try { yield 1; } finally { await null; yield 2; }",
                        "return JSON.stringify([await it.return(9), await it.next()]);"),
                afterFirstAsyncYield("try { yield 1; } finally { await null; }",
                        "return JSON.stringify([await it.return(9), await it.next()]);"),
                afterFirstAsyncYield("try { yield 1; } finally { throw new Error('ft'); }",
                        "try { await it.return(9); return 'no'; } catch (e) { return e.message; }"),
                afterFirstAsyncYield("try { yield 1; } finally { await null; throw new Error('ft2'); }",
                        "try { await it.return(9); return 'no'; } catch (e) { return e.message; }"),
                afterFirstAsyncYield("try { yield 1 } catch (e) { yield 'caught ' + typeof e } finally { }",
                        "return JSON.stringify([await it.return(9), await it.next()]);"),
                """
                        (async () => {
                          async function* g() {
                            try { yield 1 } finally { try { await Promise.reject('r') } catch (e) { yield 'caught ' + e } }
                          }
                          const it = g();
                          await it.next();
                          return JSON.stringify([await it.return(9), await it.next()]);
                        })()""",
                afterFirstAsyncYield("try { yield 1 } finally { await Promise.reject(new Error('rej')) }",
                        "try { await it.return(9); return 'no' } catch (e) { return e.message }"),
                afterFirstAsyncYield("try { yield 1 } finally { return 5 }",
                        "return JSON.stringify([await it.return(9), await it.next()]);"),
                afterFirstAsyncYield("try { yield 1 } finally { }",
                        "return JSON.stringify([await it.return(Promise.resolve(7)), await it.next()]);"),
                afterFirstAsyncYield("try { yield 1 } catch (e) { yield e.message }", """
                        const p = Promise.resolve(3);
                        Object.defineProperty(p, 'constructor', { get() { throw new Error('ctor') } });
                        return JSON.stringify([await it.return(p), await it.next()]);"""), """
                        (async () => {
                          async function* inner() { try { yield 1; } finally { yield 'inner-finally'; } }
                          async function* outer() { yield* inner(); }
                          const it = outer();
                          await it.next();
                          return JSON.stringify([await it.return(9), await it.next()]);
                        })()""");
    }

    @Test
    public void testIteratorCloseDuringAReturnCompletion() {
        // A close that fails replaces the return completion, and the generator's own catch can take it.
        // The template takes the iterator's "return" member and the code that uses the iterator.
        String template = """
                var log = [];
                var iter = {
                  [Symbol.iterator]() {
                    return { next() { return { value: 1, done: false } }, %s };
                  }
                };
                %s""";
        String throwingClose = "return() { throw new Error('close') }";
        assertStringWithJavet(template.formatted(throwingClose,
                afterFirstYield("try { for (const x of iter) { yield x } } catch (e) { yield 'caught ' + e.message }",
                        "JSON.stringify([it.return(9), it.next()])")),
                template.formatted("return: 1",
                        afterFirstYield(
                                "try { for (const x of iter) { yield x } } catch (e) { yield e.constructor.name }",
                                "JSON.stringify([it.return(9), it.next()])")),
                template.formatted("return() { return 1 }",
                        afterFirstYield(
                                "try { for (const x of iter) { yield x } } catch (e) { yield e.constructor.name }",
                                "JSON.stringify([it.return(9), it.next()])")),
                template.formatted("get return() { throw new Error('getter') }",
                        afterFirstYield("try { for (const x of iter) { yield x } } catch (e) { yield e.message }",
                                "JSON.stringify([it.return(9), it.next()])")),
                template.formatted("return() { log.push('closed'); return {} }",
                        afterFirstYield("for (const x of iter) { yield x }", "JSON.stringify([it.return(9), log])")),
                template.formatted(throwingClose,
                        afterFirstYield("for (const x of iter) { yield x }",
                                "try { it.return(9); 'no' } catch (e) { e.message }")),
                // A throw completion keeps its own exception whatever the close does.
                template.formatted(throwingClose, """
                        function* g() {
                          try { for (const x of iter) { throw new Error('body') } } catch (e) { return e.message }
                        }
                        g().next().value"""), template.formatted("return() { return 1 }", """
                        try { for (const x of iter) { throw new Error('body') } } catch (e) { e.message }"""),
                template.formatted("return: 1", """
                        try { for (const x of iter) { throw new Error('body') } } catch (e) { e.message }"""));
    }

    @Test
    public void testReturnThroughYieldStar() {
        assertStringWithJavet("""
                var log = [];
                function* inner() { yield 1 }
                function* outer() { try { yield* inner() } finally { log.push('outer finally') } }
                var it = outer();
                it.next();
                JSON.stringify([it.return(5), it.next(), log])""", """
                var iter = { [Symbol.iterator]() { return { next() { return { value: 1, done: false } } } } };
                function* outer() { yield* iter }
                var it = outer();
                it.next();
                JSON.stringify([it.return(5), it.next()])""");
    }

    @Test
    public void testThrowInsideFinallyReplacesTheReturn() {
        assertStringWithJavet(
                afterFirstYield("try { yield 1; } finally { throw new Error('ft'); }",
                        "try { it.return(9); 'no throw' } catch (e) { e.message }"),
                afterFirstYield("try { yield 1; } finally { throw new Error('ft'); }", """
                        try { it.return(9) } catch (e) {}
                        JSON.stringify(it.next())"""),
                afterFirstYield("try { yield 1; } finally { yield 2; throw new Error('after') }", """
                        it.return(9);
                        try { it.next(); 'no' } catch (e) { e.message }"""),
                """
                        var log = [];
                        function* g() { try { yield 1 } finally { try { throw 'x' } catch (e) { log.push('caught ' + e) } } }
                        var it = g();
                        it.next();
                        JSON.stringify([it.return(9), log])""",
                """
                        var log = [];
                        function* g() { try { yield 1 } finally { try { throw 'x' } finally { log.push('inner') } } }
                        var it = g();
                        it.next();
                        try { it.return(9) } catch (e) { log.push('outer ' + e) }
                        JSON.stringify(log)""",
                afterFirstYield("try { yield 1; } finally { try { throw 'x' } catch (e) { } }",
                        "JSON.stringify(it.return(9))"),
                afterFirstYield("try { yield 1; } finally { return 5 }", "JSON.stringify(it.return(9))"));
    }

    @Test
    public void testYieldInsideFinallyKeepsTheReturn() {
        assertStringWithJavet(
                afterFirstYield("try { yield 1; } finally { yield 2; }",
                        "JSON.stringify([it.return(9), it.next(), it.next()])"),
                afterFirstYield("try { try { yield 1; } finally { yield 2; } } finally { yield 3; }",
                        "JSON.stringify([it.return(9), it.next(), it.next(), it.next()])"),
                afterFirstYield("try { try { yield 1; } finally { yield 2; } } catch (e) { yield 'caught ' + e }",
                        "JSON.stringify([it.return(9), it.throw('x'), it.next()])"),
                afterFirstYield("try { yield 1; } finally { yield 2; }",
                        "JSON.stringify([it.return(9), it.return(10), it.next()])"),
                afterFirstYield("try { yield 1; } finally { var r = yield 2; yield r; }",
                        "JSON.stringify([it.return(9), it.next('a'), it.next('b')])"),
                """
                        function* inner() { try { yield 1; } finally { yield 'inner-finally'; } }
                        function* outer() { yield* inner(); }
                        var it = outer();
                        it.next();
                        JSON.stringify([it.return(9), it.next()])""",
                afterFirstYield("try { yield 1; } catch (e) { return 'caught' } finally { yield 2 }",
                        "JSON.stringify([it.return(9), it.next()])"),
                afterFirstYield("try { yield 1 } finally { for (const x of [1,2]) { yield x } }",
                        "JSON.stringify([it.return(9), it.next(), it.next()])"),
                afterFirstYield("for (const x of [1,2,3]) { try { yield x } finally { yield 'f' + x } }",
                        "JSON.stringify([it.return(9), it.next()])"),
                afterFirstYield("try { yield 1 } finally { try { yield 2 } finally { yield 3 } }",
                        "JSON.stringify([it.return(9), it.next(), it.next()])"));
    }

    private static String afterFirstAsyncYield(String generatorBody, String continuation) {
        return """
                (async () => {
                  async function* g() { %s }
                  const it = g();
                  await it.next();
                  %s
                })()""".formatted(generatorBody, continuation);
    }

    private static String afterFirstYield(String generatorBody, String continuation) {
        return """
                function* g() { %s }
                var it = g();
                it.next();
                %s""".formatted(generatorBody, continuation);
    }
}
