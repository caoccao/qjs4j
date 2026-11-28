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

package com.caoccao.qjs4j.compilation.compiler;

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.ArrayList;
import java.util.List;

/** Tracks the short-circuit exits of one continuous optional chain. */
final class OptionalChainCompiler {
    private OptionalChainCompiler() {
    }

    /**
     * Compile one optional chain, every link of it sharing one exit: QuickJS's {@code optional_chaining_label}.
     * <p>
     * Each {@code ?.} that finds a nullish value jumps to the end of the chain with {@code undefined}, so the links
     * after it — member accesses, calls and their arguments — are skipped together, while a link after an ordinary call
     * or member access still sees whatever that produced. Each link used to be compiled with a null check of its own,
     * which treated an {@code undefined} returned by {@code f?.()} as if the chain had short-circuited, and a
     * short-circuited call in tail position skipped the {@code return} after it.
     *
     * @param body
     *            emits the chain; Stack: -> value
     * @param shortCircuitTail
     *            emitted on the short-circuit path only, after {@code undefined} is pushed, or null for none
     */
    static void compileOptionalChain(CompilerContext compilerContext, Runnable body, Runnable shortCircuitTail) {
        BytecodeEmitter emitter = compilerContext.emitter;
        List<Integer> savedOptionalChainJumps = compilerContext.optionalChainJumps;
        List<Integer> optionalChainJumps = new ArrayList<>();
        compilerContext.optionalChainJumps = optionalChainJumps;
        try {
            body.run();
        } finally {
            compilerContext.optionalChainJumps = savedOptionalChainJumps;
        }
        int jumpOverShortCircuitTail = shortCircuitTail != null ? emitter.emitJump(Opcode.GOTO) : -1;
        for (int optionalChainJump : optionalChainJumps) {
            emitter.patchJumpToHere(optionalChainJump);
        }
        if (shortCircuitTail != null) {
            shortCircuitTail.run();
            emitter.patchJumpToHere(jumpOverShortCircuitTail);
        }
    }

    /**
     * Compile the object or callee of a link of an optional chain.
     * <p>
     * When both the link and its target belong to the chain being compiled, the target joins it, so a {@code ?.}
     * anywhere further down jumps straight to the end of the whole chain. Otherwise the target is an ordinary
     * expression — the chain's root, or a chain that parentheses ended — and is compiled on its own.
     *
     * @param link
     *            the member or call expression whose object or callee this is
     * @param target
     *            the object or callee
     */
    static void compileOptionalChainLinkTarget(CompilerContext compilerContext, Expression link, Expression target) {
        if (link.isPartOfOptionalChain() && target.isPartOfOptionalChain()) {
            compilerContext.optionalChainLink = target;
        }
        compilerContext.compileExpression(target);
    }

    /**
     * Whether a member or call expression about to be compiled is the next link of the optional chain being compiled.
     * Clears the mark, so nothing compiled inside the node can mistake itself for that link.
     *
     * @param expression
     *            the member or call expression
     * @return true when it joins the chain being compiled
     */
    static boolean consumeOptionalChainLink(CompilerContext compilerContext, Expression expression) {
        boolean joinsOptionalChain = compilerContext.optionalChainLink == expression;
        compilerContext.optionalChainLink = null;
        return joinsOptionalChain;
    }

    /**
     * QuickJS's {@code optional_chain_test}: short-circuit the optional chain being compiled when the value on top of
     * the stack is nullish.
     * <p>
     * Stack: ... value -> ... value when it is neither {@code undefined} nor {@code null}; otherwise {@code dropCount}
     * values are dropped, {@code undefined} is pushed, and control jumps to the end of the chain.
     *
     * @param dropCount
     *            how many values the short-circuit path drops, the tested value included
     */
    static void emitOptionalChainTest(CompilerContext compilerContext, int dropCount) {
        BytecodeEmitter emitter = compilerContext.emitter;
        emitter.emitOpcode(Opcode.DUP);
        emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToNext = emitter.emitJump(Opcode.IF_FALSE);
        for (int i = 0; i < dropCount; i++) {
            emitter.emitOpcode(Opcode.DROP);
        }
        emitter.emitOpcode(Opcode.UNDEFINED);
        compilerContext.optionalChainJumps.add(emitter.emitJump(Opcode.GOTO));
        emitter.patchJumpToHere(jumpToNext);
    }
}
