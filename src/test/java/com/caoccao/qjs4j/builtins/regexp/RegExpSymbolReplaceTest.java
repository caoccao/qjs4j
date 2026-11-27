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

package com.caoccao.qjs4j.builtins.regexp;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * {@code RegExp.prototype[Symbol.replace]} reads each match result through the protocol, in the specification's order,
 * and {@code String.prototype.split} treats a RegExp without {@code Symbol.split} as a string.
 * <p>
 * A match result whose getters throw stops the replacement with that error; a {@code null} {@code groups} is a
 * TypeError only for a string replacement; and results behind the last replaced position are skipped. Split used to
 * match a RegExp whose {@code Symbol.split} was removed as a pattern anyway, where V8 looks for its source text.
 */
public class RegExpSymbolReplaceTest extends BaseJavetTest {
    @Test
    public void testMatchResultProtocol() {
        assertStringWithJavet(
                """
                        const replaceWith = (results, replacement, flags = '') => {
                          const re = new RegExp('a', flags);
                          const queue = results.slice();
                          re.exec = () => (queue.length ? queue.shift() : null);
                          try {
                            return re[Symbol.replace]('abcdef', replacement);
                          } catch (e) {
                            return e.name + ': ' + e.message;
                          }
                        };
                        const thrower = message => ({ get() { throw new Error(message); } });
                        const value = message => ({
                          valueOf() { throw new Error(message); },
                          toString() { throw new Error(message); },
                        });
                        [
                          replaceWith([Object.defineProperty({}, 'length', thrower('length'))], 'X'),
                          replaceWith([{ length: value('toLength') }], 'X'),
                          replaceWith([Object.defineProperty({ length: 1 }, 0, thrower('match'))], 'X'),
                          replaceWith([{ length: 1, 0: value('match toString') }], 'X'),
                          replaceWith([Object.defineProperty({ length: 1, 0: 'a' }, 'index', thrower('index'))], 'X'),
                          replaceWith([{ length: 1, 0: 'a', index: value('toInteger') }], 'X'),
                          replaceWith([Object.defineProperty({ length: 2, 0: 'a', index: 0 }, 1, thrower('capture'))], 'X'),
                          replaceWith([{ length: 2, 0: 'a', index: 0, 1: value('capture toString') }], 'X'),
                          replaceWith([{ length: 2, 0: 'a', index: 0, 1: undefined }], '[$1]'),
                          replaceWith([Object.defineProperty({ length: 1, 0: 'a', index: 0 }, 'groups', thrower('groups'))], 'X'),
                          replaceWith([{ length: 1, 0: 'a', index: 0, groups: null }], '$<x>'),
                          replaceWith([{ length: 1, 0: 'a', index: 0, groups: Object.defineProperty({}, 'x', thrower('group')) }],
                            '$<x>'),
                          replaceWith([{ length: 1, 0: 'a', index: 0, groups: 'xy' }], '[$<length>]'),
                          replaceWith([{ length: 1, 0: 'a', index: 0, groups: null }], () => 'F'),
                          replaceWith([{ length: 1, 0: 'a', index: 0, groups: { x: 'G' } }], (...args) => typeof args.at(-1)),
                          replaceWith([{ length: 1, 0: 'cd', index: 2 }, { length: 1, 0: 'a', index: 0 },
                            { length: 1, 0: 'f', index: 5 }], 'X', 'g'),
                          replaceWith([{ length: 1, 0: 'a', index: 0 }], () => { throw new Error('replacer'); }),
                        ].join('\\n')""");
    }

    @Test
    public void testReplaceWithoutSymbolReplace() {
        // A RegExp whose Symbol.replace is undefined or null is an ordinary object to replace: it is searched for as
        // its
        // source text. It used to be matched as a pattern anyway.
        assertStringWithJavet("""
                const re = /a/;
                re[Symbol.replace] = undefined;
                const global = /a/g;
                global[Symbol.replace] = undefined;
                const nulled = /a/;
                Object.defineProperty(nulled, Symbol.replace, { value: null });
                const noMatch = /a/;
                noMatch[Symbol.match] = undefined;
                noMatch[Symbol.replace] = undefined;
                let replaceAllResult;
                try {
                  replaceAllResult = 'a/a/gb'.replaceAll(global, 'X');
                } catch (e) {
                  replaceAllResult = e.name + ': ' + e.message;
                }
                JSON.stringify([
                  'a/a/b'.replace(re, 'X'),
                  'a/a/gb'.replace(global, 'X'),
                  replaceAllResult,
                  'a/a/b'.replace(nulled, '[$&]'),
                  'a/a/b'.replace(re, (match, index) => match + index),
                  'aa'.replace(noMatch, 'X'),
                  'a/a/b'.replace(/a/, 'X'),
                ])""");
    }

    @Test
    public void testSplitWithoutSymbolSplit() {
        assertStringWithJavet("""
                const re = /,/;
                re[Symbol.split] = undefined;
                const nulled = /,/;
                Object.defineProperty(nulled, Symbol.split, { value: null });
                JSON.stringify(['a,b'.split(re), 'x/,/y'.split(re), 'a,b'.split(nulled), 'a,b'.split(/,/)])""");
    }
}
