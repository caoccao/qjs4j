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

package com.caoccao.qjs4j.compiler.ast;

import com.caoccao.qjs4j.BaseJavetTest;
import org.junit.jupiter.api.Test;

public class LabeledStatementTest extends BaseJavetTest {

    @Test
    public void testLabelInFunction() {
        assertIntegerWithJavet(
                """
                        function f() {
                            var x = 0;
                            loop: for (var i = 0; i < 5; i++) {
                                if (i === 3) break loop;
                                x += i;
                            }
                            return x;
                        }
                        f();""");
    }

    @Test
    public void testLabeledBlockStatement() {
        assertIntegerWithJavet("var x = 0; label: { x = 42; } x;");
    }

    @Test
    public void testLabeledBreakBlock() {
        assertIntegerWithJavet(
                """
                        var x = 0;
                        block: {
                            x = 1;
                            break block;
                            x = 2;
                        }
                        x;""");
    }

    @Test
    public void testLabeledBreakBlockSkipsRemaining() {
        assertStringWithJavet(
                """
                        var result = '';
                        block: {
                            result += 'a';
                            break block;
                            result += 'b';
                        }
                        result += 'c';
                        result;""");
    }

    @Test
    public void testLabeledBreakForInLoop() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var obj = {a: 1, b: 2, c: 3, d: 4};
                        loop: for (var key in obj) {
                            count++;
                            if (count === 2) break loop;
                        }
                        count;""");
    }

    @Test
    public void testLabeledBreakForLoop() {
        assertIntegerWithJavet(
                """
                        var last = 0;
                        loop: for (var i = 0; i < 100; i++) {
                            last = i;
                            if (i === 5) break loop;
                        }
                        last;""");
    }

    @Test
    public void testLabeledBreakForOfLoop() {
        assertIntegerWithJavet(
                """
                        var sum = 0;
                        loop: for (var x of [1, 2, 3, 4, 5]) {
                            sum += x;
                            if (x === 3) break loop;
                        }
                        sum;""");
    }

    @Test
    public void testLabeledBreakFromTryCatch() {
        assertIntegerWithJavet(
                """
                        var x = 0;
                        block: {
                            try {
                                x = 1;
                                break block;
                            } catch (e) {
                                x = 2;
                            }
                            x = 3;
                        }
                        x;""");
    }

    @Test
    public void testLabeledBreakInNestedTripleLoop() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        outer: for (var i = 0; i < 10; i++) {
                            for (var j = 0; j < 10; j++) {
                                for (var k = 0; k < 10; k++) {
                                    count++;
                                    if (count === 5) break outer;
                                }
                            }
                        }
                        count;""");
    }

    @Test
    public void testLabeledBreakNestedBlocks() {
        assertStringWithJavet(
                """
                        var result = '';
                        outer: {
                            result += 'a';
                            inner: {
                                result += 'b';
                                break outer;
                                result += 'c';
                            }
                            result += 'd';
                        }
                        result += 'e';
                        result;""");
    }

    @Test
    public void testLabeledBreakOuterForInLoop() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var obj1 = {a: 1, b: 2};
                        var obj2 = {x: 1, y: 2};
                        outer: for (var k1 in obj1) {
                            for (var k2 in obj2) {
                                count++;
                                if (count === 2) break outer;
                            }
                        }
                        count;""");
    }

    @Test
    public void testLabeledBreakOuterForLoop() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        outer: for (var i = 0; i < 10; i++) {
                            for (var j = 0; j < 10; j++) {
                                count++;
                                if (j === 2) break outer;
                            }
                        }
                        count;""");
    }

    @Test
    public void testLabeledBreakOuterForOfLoop() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        outer: for (var a of [1, 2, 3]) {
                            for (var b of [10, 20, 30]) {
                                count++;
                                if (b === 20) break outer;
                            }
                        }
                        count;""");
    }

    @Test
    public void testLabeledBreakOuterWhileLoop() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        outer: while (true) {
                            while (true) {
                                count++;
                                if (count === 3) break outer;
                            }
                        }
                        count;""");
    }

    @Test
    public void testLabeledBreakSwitch() {
        assertIntegerWithJavet(
                """
                        var x = 0;
                        block: {
                            switch (1) {
                                case 1:
                                    x = 10;
                                    break block;
                                case 2:
                                    x = 20;
                            }
                            x = 30;
                        }
                        x;""");
    }

    @Test
    public void testLabeledBreakSwitchInsideLoop() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        outer: for (var i = 0; i < 5; i++) {
                            switch (i) {
                                case 3:
                                    break outer;
                                default:
                                    count++;
                            }
                        }
                        count;""");
    }

    @Test
    public void testLabeledBreakWhileLoop() {
        assertIntegerWithJavet(
                """
                        var x = 0;
                        loop: while (true) {
                            x++;
                            if (x === 5) break loop;
                        }
                        x;""");
    }

    @Test
    public void testLabeledContinueForLoop() {
        assertIntegerWithJavet(
                """
                        var sum = 0;
                        loop: for (var i = 0; i < 5; i++) {
                            if (i === 3) continue loop;
                            sum += i;
                        }
                        sum;""");
    }

    @Test
    public void testLabeledContinueForOfLoop() {
        assertIntegerWithJavet(
                """
                        var sum = 0;
                        loop: for (var x of [1, 2, 3, 4, 5]) {
                            if (x === 3) continue loop;
                            sum += x;
                        }
                        sum;""");
    }

    @Test
    public void testLabeledContinueMiddleLoop() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        for (var i = 0; i < 2; i++) {
                            middle: for (var j = 0; j < 3; j++) {
                                for (var k = 0; k < 3; k++) {
                                    if (k === 1) continue middle;
                                    count++;
                                }
                            }
                        }
                        count;""");
    }

    @Test
    public void testLabeledContinueOuterForLoop() {
        assertStringWithJavet(
                """
                        var result = '';
                        outer: for (var i = 0; i < 3; i++) {
                            for (var j = 0; j < 3; j++) {
                                if (j === 1) continue outer;
                                result += i + '' + j + ',';
                            }
                        }
                        result;""");
    }

    @Test
    public void testLabeledContinueOuterForOfLoop() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        outer: for (var a of [1, 2, 3]) {
                            for (var b of [10, 20, 30]) {
                                if (b === 20) continue outer;
                                count++;
                            }
                        }
                        count;""");
    }

    @Test
    public void testLabeledContinueOuterWhileLoop() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var i = 0;
                        outer: while (i < 3) {
                            i++;
                            var j = 0;
                            while (j < 3) {
                                j++;
                                if (j === 2) continue outer;
                                count++;
                            }
                        }
                        count;""");
    }

    @Test
    public void testLabeledContinueWhileLoop() {
        assertIntegerWithJavet(
                """
                        var sum = 0;
                        var i = 0;
                        loop: while (i < 10) {
                            i++;
                            if (i % 2 === 0) continue loop;
                            sum += i;
                        }
                        sum;""");
    }

    @Test
    public void testLabeledEmptyStatement() {
        assertIntegerWithJavet("(function() { label: ; return 42; })();");
    }

    @Test
    public void testLabeledExpressionStatement() {
        assertIntegerWithJavet("var x = 0; label: x = 42; x;");
    }

    @Test
    public void testLabeledForWithInnerForOf() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        outer: for (var i = 0; i < 3; i++) {
                            for (var x of [1, 2, 3]) {
                                count++;
                                if (x === 2) break outer;
                            }
                        }
                        count;""");
    }

    @Test
    public void testLabeledForWithInnerWhile() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        outer: for (var i = 0; i < 3; i++) {
                            var j = 0;
                            while (j < 3) {
                                j++;
                                count++;
                                if (j === 2) continue outer;
                            }
                        }
                        count;""");
    }

    @Test
    public void testLabeledFunctionDeclaration() {
        assertUndefinedWithJavet("label: function g() {}");
    }

    @Test
    public void testLabeledFunctionDeclarationCallable() {
        assertIntegerWithJavet("label: function g() { return 42; } g();");
    }

    @Test
    public void testLabeledIfStatement() {
        assertIntegerWithJavet(
                """
                        var x = 0;
                        label: if (true) { x = 42; }
                        x;""");
    }

    @Test
    public void testLabeledWhileWithInnerFor() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        var i = 0;
                        outer: while (i < 3) {
                            i++;
                            for (var j = 0; j < 3; j++) {
                                count++;
                                if (j === 1) continue outer;
                            }
                        }
                        count;""");
    }

    @Test
    public void testMultipleLabelsBreakInner() {
        assertIntegerWithJavet(
                """
                        var x = 0;
                        outer: inner: {
                            x = 1;
                            break inner;
                            x = 2;
                        }
                        x;""");
    }

    @Test
    public void testMultipleLabelsBreakOuter() {
        assertIntegerWithJavet(
                """
                        var x = 0;
                        outer: inner: {
                            x = 1;
                            break outer;
                            x = 2;
                        }
                        x;""");
    }

    @Test
    public void testMultipleLabelsOnBlock() {
        assertIntegerWithJavet("var x = 0; a: b: { x = 10; } x;");
    }

    @Test
    public void testMultipleLabelsOnStatement() {
        assertUndefinedWithJavet("label1: label2: function f() {}");
    }

    @Test
    public void testSameLabelInDifferentScopes() {
        assertIntegerWithJavet(
                """
                        var x = 0;
                        label: { x += 1; break label; }
                        label: { x += 10; break label; }
                        x;""");
    }

    @Test
    public void testUnlabeledBreakInsideLabeledBlock() {
        assertIntegerWithJavet(
                """
                        var count = 0;
                        label: {
                            for (var i = 0; i < 10; i++) {
                                if (i === 3) break;
                                count++;
                            }
                        }
                        count;""");
    }

    @Test
    public void testUnlabeledContinueInsideLabeledBlock() {
        assertIntegerWithJavet(
                """
                        var sum = 0;
                        label: {
                            for (var i = 0; i < 5; i++) {
                                if (i === 2) continue;
                                sum += i;
                            }
                        }
                        sum;""");
    }
}
