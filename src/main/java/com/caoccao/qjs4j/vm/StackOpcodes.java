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

/** Fixed-width operand-stack permutations. Each instruction supplies constant offsets for its slot moves. */
final class StackOpcodes {
    private StackOpcodes() {
    }

    static void drop(Opcode op, ExecutionContext executionContext) {
        executionContext.sp--;
        executionContext.pc++;
    }

    static void duplicate(ExecutionContext ctx, int depth, int count) {
        JSStackValue[] stack = ctx.stack();
        int sp = ctx.sp;
        int first = sp - depth;
        stack[sp] = stack[first];
        if (count >= 2) {
            stack[sp + 1] = stack[first + 1];
        }
        if (count == 3) {
            stack[sp + 2] = stack[first + 2];
        }
        ctx.sp = sp + count;
        ctx.pc++;
    }

    static void insertTop(ExecutionContext ctx, int depth) {
        JSStackValue[] stack = ctx.stack();
        int first = ctx.sp - depth;
        JSStackValue top = stack[ctx.sp - 1];
        System.arraycopy(stack, first, stack, first + 1, depth);
        stack[first] = top;
        ctx.sp++;
        ctx.pc++;
    }

    static void nip(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack();
        int sp = executionContext.sp;
        stack[sp - 2] = stack[sp - 1];
        executionContext.sp = sp - 1;
        executionContext.pc++;
    }

    static void rotateLeft(ExecutionContext ctx, int depth) {
        JSStackValue[] stack = ctx.stack();
        int first = ctx.sp - depth;
        JSStackValue bottom = stack[first];
        System.arraycopy(stack, first + 1, stack, first, depth - 1);
        stack[ctx.sp - 1] = bottom;
        ctx.pc++;
    }

    /** PERM rotates the operands below the top, ROT3R includes the top. */
    static void rotateRight(ExecutionContext ctx, int width, int topOffset) {
        JSStackValue[] stack = ctx.stack();
        int first = ctx.sp - topOffset - width;
        JSStackValue top = stack[first + width - 1];
        if (width == 4) {
            stack[first + 3] = stack[first + 2];
        }
        if (width >= 3) {
            stack[first + 2] = stack[first + 1];
        }
        stack[first + 1] = stack[first];
        stack[first] = top;
        ctx.pc++;
    }

    static void swap(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack();
        int sp = executionContext.sp;
        JSStackValue temporaryValue = stack[sp - 1];
        stack[sp - 1] = stack[sp - 2];
        stack[sp - 2] = temporaryValue;
        executionContext.pc++;
    }

    static void swapPair(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack();
        int sp = executionContext.sp;
        JSStackValue firstPairLeft = stack[sp - 4];
        JSStackValue firstPairRight = stack[sp - 3];
        stack[sp - 4] = stack[sp - 2];
        stack[sp - 3] = stack[sp - 1];
        stack[sp - 2] = firstPairLeft;
        stack[sp - 1] = firstPairRight;
        executionContext.pc++;
    }
}
