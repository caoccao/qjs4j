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
import com.caoccao.qjs4j.core.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A stack frame must own its arguments.
 * <p>
 * {@code internalHandleCall} passes the VM's single shared reusable buffer whenever the callee is a
 * {@link JSBytecodeFunction}. {@link StackFrame} copied it only when the function declared locals;
 * a bytecode function with no locals adopted the shared array directly, so the next borrow — by
 * anything that frame went on to call — would rewrite this frame's arguments in place.
 */
public class StackFrameArgumentOwnershipTest extends BaseJavetTest {

    private JSBytecodeFunction compile(String source) {
        return (JSBytecodeFunction) context.eval(source);
    }

    @Test
    public void testArgumentsSurviveANestedCallFromJavaScript() {
        assertStringWithJavet(
                """
                        (function () {
                          function inner(a, b, c) { return a + b + c }
                          function outer() {
                            const before = arguments[0];
                            const middle = inner(9, 9, 9);
                            return before + '/' + middle + '/' + arguments[0];
                          }
                          return outer(1, 2, 3);
                        })()""");
    }

    @Test
    public void testArgumentsSurviveANestedCallInAFunctionWithNoDeclaredLocals() {
        assertStringWithJavet(
                """
                        (function () {
                          function noLocals() { return arguments.length }
                          function outer() {
                            const before = arguments[0];
                            noLocals(7, 7, 7, 7);
                            return before + '/' + arguments[0];
                          }
                          return outer(1, 2, 3);
                        })()""");
    }

    @Test
    public void testFrameDoesNotAliasTheCallersArgumentArray() {
        JSBytecodeFunction function = compile("(function () { return 1 })");
        JSValue[] sharedBuffer = {new JSString("original"), JSNumber.of(2)};
        StackFrame frame = new StackFrame(
                function, JSUndefined.INSTANCE, sharedBuffer, 2, null, JSUndefined.INSTANCE, 0);

        assertThat(frame.getArguments())
                .as("the frame must not adopt the caller's array")
                .isNotSameAs(sharedBuffer);

        // Simulate the next borrow of the shared buffer.
        sharedBuffer[0] = new JSString("clobbered");
        sharedBuffer[1] = JSNumber.of(99);

        assertThat(frame.getArgument(0).toString()).isEqualTo("original");
        assertThat(frame.getArgument(1)).isEqualTo(JSNumber.of(2));
    }

    @Test
    public void testFrameWithDeclaredLocalsAlsoOwnsItsArguments() {
        JSBytecodeFunction function = compile("(function (a, b) { let c = a + b; return c })");
        JSValue[] sharedBuffer = {JSNumber.of(1), JSNumber.of(2)};
        StackFrame frame = new StackFrame(
                function, JSUndefined.INSTANCE, sharedBuffer, 2, null, JSUndefined.INSTANCE, 0);

        sharedBuffer[0] = JSNumber.of(99);
        assertThat(frame.getArgument(0)).isEqualTo(JSNumber.of(1));
    }

    @Test
    public void testTailCallDoesNotCorruptArguments() {
        assertStringWithJavet(
                """
                        (function () {
                          'use strict';
                          function target(a, b) { return a * 10 + b }
                          function viaTail(x, y) { return target(x, y) }
                          function outer() {
                            const before = arguments[0];
                            const result = viaTail(3, 4);
                            return before + '/' + result + '/' + arguments[0];
                          }
                          return outer(5, 6);
                        })()""");
    }

    @Test
    public void testZeroArgumentFrameIsWellFormed() {
        JSBytecodeFunction function = compile("(function () { return 1 })");
        StackFrame frame = new StackFrame(
                function, JSUndefined.INSTANCE, JSValue.NO_ARGS, 0, null, JSUndefined.INSTANCE, 0);
        assertThat(frame.getArgumentCount()).isZero();
        assertThat(frame.getArgument(0)).isEqualTo(JSUndefined.INSTANCE);
    }
}
