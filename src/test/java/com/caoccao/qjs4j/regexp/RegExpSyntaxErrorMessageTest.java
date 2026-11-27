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

package com.caoccao.qjs4j.regexp;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

/**
 * Invalid patterns are reported with V8's wording, from the constructor, {@code compile} and a literal alike; and back
 * references inside a lookbehind match backwards.
 * <p>
 * Inline modifier groups said "Invalid inline modifiers" for every mistake, a lone quantifier bracket gave its
 * position, a group of unknown type quoted it, and a set operation cut short said "Unexpected end of character class".
 */
public class RegExpSyntaxErrorMessageTest extends BaseJavetTest {

    @Test
    public void testBackwardBackReferences() {
        assertStringWithJavet("""
                ['aab'.replace(/(?<=(a)\\1)b/, 'X'), 'Aab'.replace(/(?<=(a)\\1)b/i, 'X'), 'abb'.search(/(?<=(a)\\1)b/),
                  'aBb'.search(/(?<=(a)\\1)b/i), 'xyab'.replace(/(?<=\\1(a))b/, 'X'), 'ab'.search(/(?<=(a)\\1)c/),
                  'aAb'.search(/(?<=(a)\\1)b/i), 'aAb'.search(/(?<=(a)\\1)b/)].join()""");
    }

    @Test
    public void testCompileAndLiterals() {
        assertErrorWithJavet("eval('/a/gg')", "eval('/(/')", "/a/.compile('(')", "/a/.compile('a', 'zz')");
        assertStringWithJavet("String(/b/.compile('c', 'g'))");
    }

    @Test
    public void testGroupsAndModifiers() {
        assertStringWithJavet(
                """
                        const tryEach = cases => cases.map(([pattern, flags]) => {
                          try {
                            new RegExp(pattern, flags);
                            return pattern + ' ok';
                          } catch (e) {
                            return e.name + ': ' + e.message;
                          }
                        }).join('\\n');
                        tryEach([
                          ['a)', ''], ['(?', ''], ['(?i:a', ''], ['(?<>a)', ''], ['(?<1a>x)', ''], ['(?=a', ''], ['(?<=a', ''],
                          ['(?<a>.)\\\\k<>', ''], ['(?<a>.)\\\\k<a', ''], ['(?<a>x)(?<a>y)', ''], ['\\\\k<a>', 'u'], ['(?<a', ''],
                          ['(?<a>x)\\\\k<b>', ''], ['(?-)', ''], ['(?i-i:a)', ''], ['(?ii:a)', ''], ['(?x:a)', ''], ['(?-:a)', ''],
                          ['(?i--m:a)', ''], ['(?im-m:a)', ''], ['(?iX:a)', ''], ['(?-ii:a)', ''], ['(?i-x:a)', ''], ['(?i', ''],
                          ['(?i)', ''], ['(?-i)', ''], ['(?i-:a)', ''], ['(?-i:a)', ''],
                        ])""");
    }

    @Test
    public void testQuantifiersAndClasses() {
        assertStringWithJavet("""
                const tryEach = cases => cases.map(([pattern, flags]) => {
                  try {
                    new RegExp(pattern, flags);
                    return pattern + ' ok';
                  } catch (e) {
                    return e.name + ': ' + e.message;
                  }
                }).join('\\n');
                tryEach([
                  ['*', ''], ['a|*', ''], ['{1}', ''], ['a{2,1}', ''], ['a{2,1}', 'u'], ['a{1', 'u'], ['a{1,2', 'u'],
                  ['a{', 'u'], ['a{1}{', 'u'], ['a{,5}', 'u'], ['x]', 'u'], ['x}', 'u'], ['{', 'u'], ['a|{', 'u'],
                  ['^{', 'u'], ['[a', ''], ['[a', 'u'], ['[a', 'v'], ['[', 'v'], ['[&&a]', 'v'], ['[a&&&b]', 'v'],
                  ['[ab&&c]', 'v'], ['[a--b&&c]', 'v'], ['[[a]', 'v'], ['[a-z&&b]', 'v'], ['[a&&', 'v'], ['[a--', 'v'],
                  ['[a-', 'v'], ['[a&&b-c]', 'v'], ['[[a', 'v'], ['[a--b-c]', 'v'], ['[--a]', 'v'], ['[ab--c]', 'v'],
                  ['[&&]', 'v'],
                ])""");
    }
}
