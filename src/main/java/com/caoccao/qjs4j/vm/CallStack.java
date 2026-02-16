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

import com.caoccao.qjs4j.core.JSStackValue;
import com.caoccao.qjs4j.core.JSValue;

/**
 * Represents the value stack for the VM.
 * Following QuickJS stack operations: sp[-1], sp[-2], etc.
 * Can store both JSValue and internal markers like CatchOffset.
 */
public final class CallStack {
    final JSStackValue[] stack;  // package-private for direct access from VirtualMachine
    int stackTop;                // package-private for direct access from VirtualMachine

    public CallStack() {
        this.stack = new JSStackValue[1024];
        this.stackTop = 0;
    }

    /**
     * Drop count values from the stack (QuickJS: sp -= count).
     */
    public void drop(int count) {
        stackTop -= count;
    }

    public int getStackTop() {
        return stackTop;
    }

    /**
     * Peek at a value on the stack (QuickJS: sp[-1-offset]).
     * offset=0 means sp[-1], offset=1 means sp[-2], etc.
     */
    public JSValue peek(int offset) {
        int index = stackTop - 1 - offset;
        if (index < 0) {
            throw new IllegalStateException("Stack underflow in peek: stackTop=" + stackTop + ", offset=" + offset);
        }
        return (JSValue) stack[index];
    }

    /**
     * Pop a value from the stack (QuickJS: *--sp).
     * Returns JSValue for normal stack operations.
     */
    public JSValue pop() {
        if (stackTop <= 0) {
            throw new IllegalStateException("Stack underflow in pop: stackTop=" + stackTop);
        }
        return (JSValue) stack[--stackTop];
    }

    /**
     * Pop any stack value, including internal markers.
     * Used for exception unwinding to find CatchOffset markers.
     */
    public JSStackValue popStackValue() {
        if (stackTop <= 0) {
            throw new IllegalStateException("Stack underflow in popStackValue: stackTop=" + stackTop);
        }
        return stack[--stackTop];
    }

    /**
     * Push a value onto the stack (QuickJS: *sp++ = value).
     */
    public void push(JSValue value) {
        stack[stackTop++] = value;
    }

    /**
     * Push a stack value (including internal markers like CatchOffset).
     */
    public void pushStackValue(JSStackValue value) {
        stack[stackTop++] = value;
    }

    /**
     * Set a value at a stack position (QuickJS: sp[-1-offset] = value).
     * offset=0 means sp[-1], offset=1 means sp[-2], etc.
     */
    public void set(int offset, JSValue value) {
        int index = stackTop - 1 - offset;
        if (index < 0) {
            throw new IllegalStateException("Stack underflow in set: stackTop=" + stackTop + ", offset=" + offset);
        }
        stack[index] = value;
    }

    /**
     * Set the stack top position.
     * Used to restore stack state after function calls.
     */
    public void setStackTop(int top) {
        this.stackTop = top;
    }
}
