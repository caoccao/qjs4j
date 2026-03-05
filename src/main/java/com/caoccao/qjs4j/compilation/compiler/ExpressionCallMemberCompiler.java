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
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.core.JSSymbol;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.ArrayList;

final class ExpressionCallMemberCompiler {
    private final CompilerContext compilerContext;
    private final CompilerDelegates delegates;
    private final ExpressionCompiler owner;

    ExpressionCallMemberCompiler(ExpressionCompiler owner, CompilerContext compilerContext, CompilerDelegates delegates) {
        this.owner = owner;
        this.compilerContext = compilerContext;
        this.delegates = delegates;
    }

    void compileCallExpression(CallExpression callExpr) {
        boolean hasSpread = callExpr.arguments().stream().anyMatch(arg -> arg instanceof SpreadElement);
        if (hasSpread) {
            compileCallExpressionWithSpread(callExpr);
        } else {
            compileCallExpressionRegular(callExpr);
        }
    }

    void compileCallExpressionRegular(CallExpression callExpr) {
        if (callExpr.callee() instanceof Identifier calleeId && JSKeyword.SUPER.equals(calleeId.name())) {
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(3);
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(2);
            compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            compilerContext.emitter.emitOpcode(Opcode.INIT_CTOR);
            emitPendingPostSuperInitialization();
            return;
        }
        if (callExpr.callee() instanceof Identifier calleeId && JSKeyword.EVAL.equals(calleeId.name())) {
            boolean isTailCallForEval = compilerContext.emitTailCalls;
            compilerContext.emitTailCalls = false;
            owner.compileExpression(callExpr.callee());
            for (Expression arg : callExpr.arguments()) {
                owner.compileExpression(arg);
            }
            compilerContext.emitter.emitOpcode(Opcode.EVAL);
            compilerContext.emitter.emitU16(callExpr.arguments().size());
            int evalFlags = isTailCallForEval ? 1 : 0;
            if (compilerContext.inClassFieldInitializer || compilerContext.classFieldEvalContext) {
                evalFlags |= 2;  // bit 1: in class field initializer
            }
            compilerContext.emitter.emitU16(evalFlags);
            return;
        }
        // Determine which CALL opcode to use (TAIL_CALL when in tail position)
        // Reset flag immediately so nested calls (in callee/arguments) are not treated as tail calls
        boolean isTailCall = compilerContext.emitTailCalls;
        compilerContext.emitTailCalls = false;

        if (callExpr.callee() instanceof MemberExpression memberExpr) {
            Opcode callMethodOpcode = isTailCall ? Opcode.TAIL_CALL_METHOD : Opcode.CALL_METHOD;

            if (compilerContext.isSuperMemberExpression(memberExpr)) {
                delegates.emitHelpers.emitGetSuperValue(memberExpr, true);
                // Stack: [this, func] → SWAP → [func, this]
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                for (Expression arg : callExpr.arguments()) {
                    owner.compileExpression(arg);
                }
                compilerContext.emitter.emitOpcodeU16(callMethodOpcode, callExpr.arguments().size());
                return;
            }

            owner.compileExpression(memberExpr.object());

            if (memberExpr.computed()) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                owner.compileExpression(memberExpr.property());
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                String fieldName = privateId.name();
                JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                if (symbol != null) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                } else {
                    throw new JSSyntaxErrorException("Unexpected private field");
                }
            } else if (memberExpr.property() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, propId.name());
            }

            // SWAP converts obj/func to func/obj (internalHandleCall's expected layout)
            // and sets propertyAccessLock to protect the error-message chain during arg evaluation
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            for (Expression arg : callExpr.arguments()) {
                owner.compileExpression(arg);
            }

            compilerContext.emitter.emitOpcodeU16(callMethodOpcode, callExpr.arguments().size());
        } else {
            if (callExpr.callee() instanceof Identifier calleeId && compilerContext.hasActiveWithObject()) {
                // Inside a with scope, lookup returns [value, receiver] where receiver
                // is the with object if the name was found there (ES spec 12.3.4.1 step 4.b.ii)
                owner.emitWithAwareIdentifierLookupForCall(calleeId.name());
            } else {
                owner.compileExpression(callExpr.callee());
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }
            for (Expression arg : callExpr.arguments()) {
                owner.compileExpression(arg);
            }
            Opcode callOpcode = isTailCall ? Opcode.TAIL_CALL : Opcode.CALL;
            compilerContext.emitter.emitOpcodeU16(callOpcode, callExpr.arguments().size());
        }
    }

    void compileCallExpressionWithSpread(CallExpression callExpr) {
        // TCO not supported for spread calls (they use APPLY, not CALL)
        compilerContext.emitTailCalls = false;
        if (callExpr.callee() instanceof Identifier calleeId && JSKeyword.SUPER.equals(calleeId.name())) {
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(3);
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(2);
            compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            compilerContext.emitter.emitOpcode(Opcode.INIT_CTOR);
            emitPendingPostSuperInitialization();
            return;
        }
        if (callExpr.callee() instanceof Identifier calleeId && JSKeyword.EVAL.equals(calleeId.name())) {
            owner.compileExpression(callExpr.callee());
            delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY_EVAL, 0);
            return;
        }
        if (callExpr.callee() instanceof MemberExpression memberExpr) {
            if (compilerContext.isSuperMemberExpression(memberExpr)) {
                delegates.emitHelpers.emitGetSuperValue(memberExpr, true);
                delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
                compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 0);
                return;
            }

            owner.compileExpression(memberExpr.object());

            if (memberExpr.computed()) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                owner.compileExpression(memberExpr.property());
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.property() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, propId.name());
            } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                String fieldName = privateId.name();
                JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                if (symbol != null) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                } else {
                    throw new JSSyntaxErrorException("Unexpected private field");
                }
            }
        } else {
            if (callExpr.callee() instanceof Identifier calleeId && compilerContext.hasActiveWithObject()) {
                // Lookup returns [value, receiver]; APPLY expects [receiver, function, ...]
                owner.emitWithAwareIdentifierLookupForCall(calleeId.name());
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
            } else {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                owner.compileExpression(callExpr.callee());
            }
        }

        delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.arguments());
        compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 0);
    }

    void compileMemberExpression(MemberExpression memberExpr) {
        if (compilerContext.isSuperMemberExpression(memberExpr)) {
            delegates.emitHelpers.emitGetSuperValue(memberExpr, false);
            return;
        }

        // Detect non-optional continuation of an optional chain (e.g., `.#f` in `o?.c.#f`)
        // The entire chain after `?.` must short-circuit together.
        if (!memberExpr.optional() && isPartOfOptionalChain(memberExpr.object())) {
            compileOptionalChainFull(memberExpr);
            return;
        }

        owner.compileExpression(memberExpr.object());

        if (memberExpr.optional()) {
            // Optional chaining: obj?.prop
            // Stack: obj -> DUP -> IS_UNDEFINED_OR_NULL -> IF_TRUE(undef) -> access -> GOTO(end) -> undef: DROP UNDEFINED -> end:
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            int jumpToUndefined = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

            // Normal property access
            emitPropertyAccess(memberExpr);

            int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

            // Undefined path
            compilerContext.emitter.patchJump(jumpToUndefined, compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);

            // End
            compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
            return;
        }

        emitPropertyAccess(memberExpr);
    }

    void compileNewExpression(NewExpression newExpr) {
        boolean hasSpread = newExpr.arguments().stream().anyMatch(arg -> arg instanceof SpreadElement);

        owner.compileExpression(newExpr.callee());

        if (hasSpread) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(newExpr.arguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            return;
        }

        for (Expression arg : newExpr.arguments()) {
            owner.compileExpression(arg);
        }
        compilerContext.emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, newExpr.arguments().size());
    }

    /**
     * Compile an optional chain as a single unit so all accesses after `?.` share
     * one short-circuit exit. E.g., `o?.c.#f` → null-check o, then access .c and .#f
     * inside the non-null branch.
     */
    private void compileOptionalChainFull(MemberExpression memberExpr) {
        // Collect the chain of member accesses from outermost to innermost,
        // stopping at the nearest optional root.
        var chain = new ArrayList<MemberExpression>();
        Expression current = memberExpr;
        while (current instanceof MemberExpression mem) {
            chain.add(0, mem);
            if (mem.optional()) {
                break;
            }
            current = mem.object();
        }

        // chain[0] is the optional root (e.g., o?.c), chain[0].object is o
        MemberExpression optionalRoot = chain.get(0);

        // Compile the object of the optional root (may itself be an optional chain)
        owner.compileExpression(optionalRoot.object());

        // Null check — short-circuit the entire chain
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToUndefined = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        // Normal path: compile all chain accesses
        for (MemberExpression link : chain) {
            emitPropertyAccess(link);
        }

        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        // Undefined path
        compilerContext.emitter.patchJump(jumpToUndefined, compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);

        // End
        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
    }

    private void emitPendingPostSuperInitialization() {
        if (compilerContext.pendingPostSuperInitialization != null) {
            compilerContext.pendingPostSuperInitialization.run();
        }
    }

    private void emitPropertyAccess(MemberExpression memberExpr) {
        if (memberExpr.computed()) {
            owner.compileExpression(memberExpr.property());
            compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        } else if (memberExpr.property() instanceof PrivateIdentifier privateId) {
            String fieldName = privateId.name();
            JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
            if (symbol != null) {
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
            } else {
                throw new JSSyntaxErrorException("Unexpected private field");
            }
        } else if (memberExpr.property() instanceof Identifier propId) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.name());
        }
    }

    private boolean isPartOfOptionalChain(Expression expr) {
        if (expr instanceof MemberExpression mem) {
            return mem.optional() || isPartOfOptionalChain(mem.object());
        }
        return false;
    }
}
