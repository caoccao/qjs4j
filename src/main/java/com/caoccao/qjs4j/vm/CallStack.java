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
import com.caoccao.qjs4j.core.JSStackValue;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.Arrays;

/**
 * Represents the value stack for the VM.
 * Following QuickJS stack operations: sp[-1], sp[-2], etc.
 * Can store both JSValue and internal markers like CatchOffset.
 */
public final class CallStack {
    private static final int INITIAL_STACK_SIZE = 8192;
    /**
     * Maximum number of operand slots across all active frames.
     * <p>
     * Exhausting it is what a script sees as call-stack exhaustion, so it is reported as
     * {@code RangeError: Maximum call stack size exceeded} — the same error V8 raises and the same
     * error the VM's own frame-depth limit raises. It used to surface as an untyped
     * {@code "Stack overflow"}, so the same condition reached scripts under two different names
     * depending on which limit tripped first.
     */
    private static final int MAX_STACK_SIZE = 65536;
    JSStackValue[] stack;  // package-private for direct access from VirtualMachine
    int stackTop;          // package-private for direct access from VirtualMachine

    public CallStack() {
        this.stack = new JSStackValue[INITIAL_STACK_SIZE];
        this.stackTop = 0;
    }

    /**
     * Drop count values from the stack (QuickJS: sp -= count).
     * <p>
     * The count is bounds-checked: an unchecked {@code stackTop -= count} could silently drive the
     * stack top negative, after which every later index computation is wrong with no diagnostic.
     * The vacated slots are cleared so the values they held become collectable.
     *
     * @param count how many values to drop
     * @throws JSVirtualMachineException when the count is negative or exceeds the stack depth
     */
    public void drop(int count) {
        if (count < 0 || count > stackTop) {
            throw new JSVirtualMachineException(
                    "Stack underflow in drop: stackTop=" + stackTop + ", count=" + count);
        }
        Arrays.fill(stack, stackTop - count, stackTop, null);
        stackTop -= count;
    }

    public int getStackTop() {
        return stackTop;
    }

    private void grow() {
        growTo(stack.length * 2);
    }

    private void growTo(int minCapacity) {
        // The capacity check comes first. Doubling until newCapacity >= minCapacity overflows to a
        // negative int for minCapacity > 2^30, then to 0, and 0 < minCapacity stays true forever:
        // the loop never terminates and never reaches the MAX_STACK_SIZE guard below it.
        if (minCapacity < 0 || minCapacity > MAX_STACK_SIZE) {
            throw new JSRangeErrorException("Maximum call stack size exceeded");
        }
        int newCapacity = Math.min(MAX_STACK_SIZE, Math.max(stack.length * 2, minCapacity));
        JSStackValue[] newStack = new JSStackValue[newCapacity];
        System.arraycopy(stack, 0, newStack, 0, stackTop);
        stack = newStack;
    }

    /**
     * Peek at a value on the stack (QuickJS: sp[-1-offset]).
     * offset=0 means sp[-1], offset=1 means sp[-2], etc.
     */
    public JSValue peek(int offset) {
        int index = stackTop - 1 - offset;
        if (index < 0) {
            throw new JSVirtualMachineException(
                    "Stack underflow in peek: stackTop=" + stackTop + ", offset=" + offset);
        }
        return asJSValue(stack[index], "peek");
    }

    /**
     * Pop a value from the stack (QuickJS: *--sp).
     * Returns JSValue for normal stack operations.
     */
    public JSValue pop() {
        if (stackTop <= 0) {
            throw new JSVirtualMachineException("Stack underflow in pop: stackTop=" + stackTop);
        }
        return asJSValue(stack[--stackTop], "pop");
    }

    /**
     * Cast a slot to a JSValue, reporting an internal marker as a diagnosable engine error.
     * <p>
     * A bare cast turns a {@link JSCatchOffset} left in the slot by miscompiled bytecode into a
     * {@link ClassCastException} with no context.
     *
     * @param stackValue the raw slot contents
     * @param operation  the operation being performed, used in the error message
     * @return the slot contents as a JSValue
     */
    private JSValue asJSValue(JSStackValue stackValue, String operation) {
        if (stackValue instanceof JSValue value) {
            return value;
        }
        throw new JSVirtualMachineException(
                "Internal engine error: " + operation + " found "
                        + (stackValue == null ? "an empty slot" : stackValue.getClass().getSimpleName())
                        + " where a value was expected, at stackTop=" + stackTop);
    }

    /**
     * Pop any stack value, including internal markers.
     * Used for exception unwinding to find CatchOffset markers.
     */
    public JSStackValue popStackValue() {
        if (stackTop <= 0) {
            throw new JSVirtualMachineException(
                    "Stack underflow in popStackValue: stackTop=" + stackTop);
        }
        return stack[--stackTop];
    }

    /**
     * Push a value onto the stack (QuickJS: *sp++ = value).
     */
    public void push(JSValue value) {
        if (stackTop >= stack.length) {
            grow();
        }
        stack[stackTop++] = value;
    }

    /**
     * Push a stack value (including internal markers like CatchOffset).
     */
    public void pushStackValue(JSStackValue value) {
        if (stackTop >= stack.length) {
            grow();
        }
        stack[stackTop++] = value;
    }

    /**
     * Set a value at a stack position (QuickJS: sp[-1-offset] = value).
     * offset=0 means sp[-1], offset=1 means sp[-2], etc.
     */
    public void set(int offset, JSValue value) {
        int index = stackTop - 1 - offset;
        if (index < 0) {
            throw new JSVirtualMachineException(
                    "Stack underflow in set: stackTop=" + stackTop + ", offset=" + offset);
        }
        stack[index] = value;
    }

    /**
     * Set the stack top position.
     * Used to restore stack state after function calls.
     * <p>
     * When the stack shrinks, the vacated slots are cleared. Leaving them populated pinned whatever
     * object graph the dead values referenced for as long as the slot was not overwritten — up to
     * {@code MAX_STACK_SIZE} entries' worth. This is the once-per-return sweep, so the cost is one
     * short {@code Arrays.fill} per call rather than a write barrier on every {@code pop()}.
     *
     * @param top the new stack top
     */
    public void setStackTop(int top) {
        if (top < 0 || top > stack.length) {
            throw new JSVirtualMachineException(
                    "Invalid stack top: " + top + " (capacity " + stack.length + ")");
        }
        if (top < stackTop) {
            Arrays.fill(stack, top, stackTop, null);
        }
        this.stackTop = top;
    }
}
