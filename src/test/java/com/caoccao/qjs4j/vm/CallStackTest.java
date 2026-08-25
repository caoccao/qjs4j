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

import com.caoccao.qjs4j.core.JSCatchOffset;
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * {@link CallStack} bounds and diagnostics.
 * <p>
 * {@code drop(int)} had no lower bound and could drive {@code stackTop} negative silently;
 * {@code peek}/{@code pop}/{@code set} raised raw {@code IllegalStateException}s that the engine's
 * own error machinery does not understand; {@code pop}/{@code peek} blind-cast the slot so an
 * internal marker produced a bare {@code ClassCastException}; and vacated slots were never cleared,
 * so a 65,536-entry array could pin object graphs long after the values were dead.
 */
public class CallStackTest {

    @Test
    public void testDropClearsVacatedSlots() {
        CallStack stack = new CallStack();
        stack.push(JSNumber.of(1));
        stack.push(JSNumber.of(2));
        stack.push(JSNumber.of(3));
        stack.drop(2);
        assertThat(stack.getStackTop()).isEqualTo(1);
        assertThat(stack.stack[1]).isNull();
        assertThat(stack.stack[2]).isNull();
        assertThat(stack.peek(0)).isEqualTo(JSNumber.of(1));
    }

    @Test
    public void testDropOfZeroAndOfTheWholeStackAreAccepted() {
        CallStack stack = new CallStack();
        stack.push(JSNumber.of(1));
        stack.push(JSNumber.of(2));
        assertThatCode(() -> stack.drop(0)).doesNotThrowAnyException();
        assertThat(stack.getStackTop()).isEqualTo(2);
        assertThatCode(() -> stack.drop(2)).doesNotThrowAnyException();
        assertThat(stack.getStackTop()).isZero();
    }

    @Test
    public void testDropRejectsCountBeyondTheStackDepth() {
        CallStack stack = new CallStack();
        stack.push(JSNumber.of(1));
        assertThatThrownBy(() -> stack.drop(2))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("Stack underflow in drop");
        assertThatThrownBy(() -> stack.drop(-1))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("Stack underflow in drop");
        assertThat(stack.getStackTop()).as("a rejected drop must not move the stack top").isEqualTo(1);
    }

    @Test
    public void testGrowingBeyondTheMaximumRaisesRangeError() {
        CallStack stack = new CallStack();
        // The capacity check must come before the doubling loop; the loop used to overflow to a
        // negative int and spin forever instead of reaching the guard. The error is a RangeError
        // with V8's wording, matching what the VM's own frame-depth limit reports — the same
        // condition used to reach scripts under two different names.
        assertThatThrownBy(() -> {
            for (int index = 0; index <= 65536; index++) {
                stack.push(JSNumber.of(index));
            }
        })
                .isInstanceOf(JSRangeErrorException.class)
                .hasMessage("Maximum call stack size exceeded");
    }

    @Test
    public void testPeekAndPopUnderflowRaiseVirtualMachineExceptions() {
        CallStack stack = new CallStack();
        assertThatThrownBy(stack::pop)
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("Stack underflow in pop");
        assertThatThrownBy(stack::popStackValue)
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("Stack underflow in popStackValue");
        assertThatThrownBy(() -> stack.peek(0))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("Stack underflow in peek");
        assertThatThrownBy(() -> stack.set(0, JSNumber.of(1)))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("Stack underflow in set");
    }

    @Test
    public void testPopOfAnInternalMarkerIsDiagnosable() {
        CallStack stack = new CallStack();
        stack.pushStackValue(new JSCatchOffset(42, false));
        assertThatThrownBy(stack::pop)
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("Internal engine error")
                .hasMessageContaining("JSCatchOffset");
    }

    @Test
    public void testPushAndPopRoundTrip() {
        CallStack stack = new CallStack();
        stack.push(JSNumber.of(1));
        stack.push(JSNumber.of(2));
        assertThat(stack.getStackTop()).isEqualTo(2);
        assertThat(stack.peek(0)).isEqualTo(JSNumber.of(2));
        assertThat(stack.peek(1)).isEqualTo(JSNumber.of(1));
        assertThat(stack.pop()).isEqualTo(JSNumber.of(2));
        assertThat(stack.pop()).isEqualTo(JSNumber.of(1));
        assertThat(stack.getStackTop()).isZero();
    }

    @Test
    public void testSetStackTopClearsVacatedSlotsWhenShrinking() {
        CallStack stack = new CallStack();
        for (int index = 0; index < 5; index++) {
            stack.push(JSNumber.of(index));
        }
        stack.setStackTop(2);
        assertThat(stack.getStackTop()).isEqualTo(2);
        for (int index = 2; index < 5; index++) {
            assertThat(stack.stack[index]).as("slot %d", index).isNull();
        }
        assertThat(stack.peek(0)).isEqualTo(JSNumber.of(1));
    }

    @Test
    public void testSetStackTopRejectsAnOutOfRangeTop() {
        CallStack stack = new CallStack();
        assertThatThrownBy(() -> stack.setStackTop(-1))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("Invalid stack top");
        assertThatThrownBy(() -> stack.setStackTop(Integer.MAX_VALUE))
                .isInstanceOf(JSVirtualMachineException.class)
                .hasMessageContaining("Invalid stack top");
    }
}
