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
        boolean hasSpread = callExpr.getArguments().stream().anyMatch(arg -> arg instanceof SpreadElement);
        if (hasSpread) {
            compileCallExpressionWithSpread(callExpr);
        } else {
            compileCallExpressionRegular(callExpr);
        }
    }

    void compileCallExpressionRegular(CallExpression callExpr) {
        if (callExpr.getCallee() instanceof Identifier calleeId && JSKeyword.SUPER.equals(calleeId.getName())) {
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(3);
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(2);
            compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.getArguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            compilerContext.emitter.emitOpcode(Opcode.INIT_CTOR);
            emitPendingPostSuperInitialization();
            return;
        }
        // Determine which CALL opcode to use (TAIL_CALL when in tail position)
        // Reset flag immediately so nested calls (in callee/arguments) are not treated as tail calls
        boolean isTailCall = compilerContext.emitTailCalls;
        compilerContext.emitTailCalls = false;

        if (!callExpr.isOptional()
                && callExpr.getCallee() instanceof Identifier calleeId
                && JSKeyword.EVAL.equals(calleeId.getName())) {
            owner.compileExpression(callExpr.getCallee());
            for (Expression arg : callExpr.getArguments()) {
                owner.compileExpression(arg);
            }
            compilerContext.emitter.emitOpcode(Opcode.EVAL);
            compilerContext.emitter.emitU16(callExpr.getArguments().size());
            int evalFlags = isTailCall ? 1 : 0;
            if (compilerContext.inClassFieldInitializer || compilerContext.classFieldEvalContext) {
                evalFlags |= 2;  // bit 1: in class field initializer
            }
            compilerContext.emitter.emitU16(evalFlags);
            return;
        }

        if (callExpr.getCallee() instanceof MemberExpression memberExpr
                && AstUtils.isSuperMemberExpression(memberExpr)
                && (callExpr.isOptional() || memberExpr.isOptional())) {
            compileOptionalSuperMemberCallExpression(callExpr, memberExpr, isTailCall);
            return;
        }

        if (callExpr.getCallee() instanceof MemberExpression memberExpr
                && (callExpr.isOptional() || memberExpr.isOptional() || isPartOfOptionalChain(memberExpr.getObject()))) {
            compileOptionalMemberCallExpression(callExpr, memberExpr, isTailCall);
            return;
        }
        if (callExpr.isOptional()) {
            compileOptionalCallExpression(callExpr, isTailCall);
            return;
        }

        if (callExpr.getCallee() instanceof MemberExpression memberExpr) {
            Opcode callMethodOpcode = isTailCall ? Opcode.TAIL_CALL_METHOD : Opcode.CALL_METHOD;

            if (AstUtils.isSuperMemberExpression(memberExpr)) {
                delegates.emitHelpers.emitGetSuperValue(memberExpr, true);
                // Stack: [this, func] → SWAP → [func, this]
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                for (Expression arg : callExpr.getArguments()) {
                    owner.compileExpression(arg);
                }
                compilerContext.emitter.emitOpcodeU16(callMethodOpcode, callExpr.getArguments().size());
                return;
            }

            owner.compileExpression(memberExpr.getObject());

            if (memberExpr.isComputed()) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                owner.compileExpression(memberExpr.getProperty());
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateId) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                String fieldName = privateId.getName();
                JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                if (symbol != null) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                } else {
                    throw new JSSyntaxErrorException("Unexpected private field");
                }
            } else if (memberExpr.getProperty() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, propId.getName());
            }

            // SWAP converts obj/func to func/obj (internalHandleCall's expected layout)
            // and sets propertyAccessLock to protect the error-message chain during arg evaluation
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            for (Expression arg : callExpr.getArguments()) {
                owner.compileExpression(arg);
            }

            compilerContext.emitter.emitOpcodeU16(callMethodOpcode, callExpr.getArguments().size());
        } else {
            if (callExpr.getCallee() instanceof Identifier calleeId
                    && (compilerContext.hasActiveWithObject()
                    || !compilerContext.inheritedWithObjectBindingNames.isEmpty())) {
                // Inside a with scope, lookup returns [value, receiver] where receiver
                // is the with object if the name was found there (ES spec 12.3.4.1 step 4.b.ii)
                owner.emitWithAwareIdentifierLookupForCall(calleeId.getName());
            } else {
                owner.compileExpression(callExpr.getCallee());
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }
            for (Expression arg : callExpr.getArguments()) {
                owner.compileExpression(arg);
            }
            Opcode callOpcode = isTailCall ? Opcode.TAIL_CALL : Opcode.CALL;
            compilerContext.emitter.emitOpcodeU16(callOpcode, callExpr.getArguments().size());
        }
    }

    void compileCallExpressionWithSpread(CallExpression callExpr) {
        // TCO not supported for spread calls (they use APPLY, not CALL)
        compilerContext.emitTailCalls = false;
        if (callExpr.getCallee() instanceof Identifier calleeId && JSKeyword.SUPER.equals(calleeId.getName())) {
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(3);
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(2);
            compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.getArguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            compilerContext.emitter.emitOpcode(Opcode.INIT_CTOR);
            emitPendingPostSuperInitialization();
            return;
        }
        if (!callExpr.isOptional()
                && callExpr.getCallee() instanceof Identifier calleeId
                && JSKeyword.EVAL.equals(calleeId.getName())) {
            owner.compileExpression(callExpr.getCallee());
            delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.getArguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY_EVAL, 0);
            return;
        }
        if (callExpr.getCallee() instanceof MemberExpression memberExpr) {
            if (AstUtils.isSuperMemberExpression(memberExpr)) {
                delegates.emitHelpers.emitGetSuperValue(memberExpr, true);
                delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.getArguments());
                compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 0);
                return;
            }

            owner.compileExpression(memberExpr.getObject());

            if (memberExpr.isComputed()) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                owner.compileExpression(memberExpr.getProperty());
                compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            } else if (memberExpr.getProperty() instanceof Identifier propId) {
                compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, propId.getName());
            } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateId) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                String fieldName = privateId.getName();
                JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
                if (symbol != null) {
                    compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                    compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
                } else {
                    throw new JSSyntaxErrorException("Unexpected private field");
                }
            }
        } else {
            if (callExpr.getCallee() instanceof Identifier calleeId
                    && (compilerContext.hasActiveWithObject()
                    || !compilerContext.inheritedWithObjectBindingNames.isEmpty())) {
                // Lookup returns [value, receiver]; APPLY expects [receiver, function, ...]
                owner.emitWithAwareIdentifierLookupForCall(calleeId.getName());
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
            } else {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                owner.compileExpression(callExpr.getCallee());
            }
        }

        delegates.emitHelpers.emitArgumentsArrayWithSpread(callExpr.getArguments());
        compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 0);
    }

    void compileMemberExpression(MemberExpression memberExpr) {
        if (AstUtils.isSuperMemberExpression(memberExpr)) {
            delegates.emitHelpers.emitGetSuperValue(memberExpr, false);
            return;
        }

        // Detect non-optional continuation of an optional chain (e.g., `.#f` in `o?.c.#f`)
        // The entire chain after `?.` must short-circuit together.
        if (!memberExpr.isOptional() && isPartOfOptionalChain(memberExpr.getObject())) {
            compileOptionalChainFull(memberExpr);
            return;
        }

        owner.compileExpression(memberExpr.getObject());

        if (memberExpr.isOptional()) {
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
        boolean hasSpread = newExpr.getArguments().stream().anyMatch(arg -> arg instanceof SpreadElement);

        owner.compileExpression(newExpr.getCallee());

        if (hasSpread) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            delegates.emitHelpers.emitArgumentsArrayWithSpread(newExpr.getArguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            return;
        }

        for (Expression arg : newExpr.getArguments()) {
            owner.compileExpression(arg);
        }
        compilerContext.emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, newExpr.getArguments().size());
    }

    private void compileOptionalCallExpression(CallExpression callExpr, boolean isTailCall) {
        owner.compileExpression(callExpr.getCallee());
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToUndefined = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        for (Expression arg : callExpr.getArguments()) {
            owner.compileExpression(arg);
        }
        Opcode callOpcode = isTailCall ? Opcode.TAIL_CALL : Opcode.CALL;
        compilerContext.emitter.emitOpcodeU16(callOpcode, callExpr.getArguments().size());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        compilerContext.emitter.patchJump(jumpToUndefined, compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);

        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
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
            if (mem.isOptional()) {
                break;
            }
            current = mem.getObject();
        }

        // chain[0] is the optional root (e.g., o?.c), chain[0].object is o
        MemberExpression optionalRoot = chain.get(0);

        // Compile the object of the optional root (may itself be an optional chain)
        owner.compileExpression(optionalRoot.getObject());

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

    private void compileOptionalMemberCallExpression(
            CallExpression callExpr,
            MemberExpression calleeMemberExpression,
            boolean isTailCall) {
        Opcode callMethodOpcode = isTailCall ? Opcode.TAIL_CALL_METHOD : Opcode.CALL_METHOD;

        ArrayList<MemberExpression> chain = new ArrayList<>();
        Expression current = calleeMemberExpression;
        while (current instanceof MemberExpression memberExpression) {
            chain.add(0, memberExpression);
            if (memberExpression.isOptional()) {
                break;
            }
            current = memberExpression.getObject();
        }

        if (chain.isEmpty()) {
            compileOptionalCallExpression(callExpr, isTailCall);
            return;
        }

        boolean hasOptionalRoot = chain.get(0).isOptional();
        owner.compileExpression(chain.get(0).getObject());

        int jumpToUndefinedFromOptionalRoot = -1;
        if (hasOptionalRoot) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            jumpToUndefinedFromOptionalRoot = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        }

        for (int index = 0; index < chain.size() - 1; index++) {
            emitPropertyAccess(chain.get(index));
        }
        emitMemberFunctionWithReceiver(chain.get(chain.size() - 1));

        int jumpToUndefinedFromOptionalCall = -1;
        if (callExpr.isOptional()) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            jumpToUndefinedFromOptionalCall = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        }

        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        for (Expression arg : callExpr.getArguments()) {
            owner.compileExpression(arg);
        }
        compilerContext.emitter.emitOpcodeU16(callMethodOpcode, callExpr.getArguments().size());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        int jumpToEndAfterOptionalCallUndefined = -1;
        if (callExpr.isOptional()) {
            compilerContext.emitter.patchJump(jumpToUndefinedFromOptionalCall, compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DROP); // function value
            compilerContext.emitter.emitOpcode(Opcode.DROP); // receiver
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            jumpToEndAfterOptionalCallUndefined = compilerContext.emitter.emitJump(Opcode.GOTO);
        }

        int jumpToEndAfterOptionalRootUndefined = -1;
        if (hasOptionalRoot) {
            compilerContext.emitter.patchJump(jumpToUndefinedFromOptionalRoot, compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            jumpToEndAfterOptionalRootUndefined = compilerContext.emitter.emitJump(Opcode.GOTO);
        }

        int endOffset = compilerContext.emitter.currentOffset();
        compilerContext.emitter.patchJump(jumpToEnd, endOffset);
        if (jumpToEndAfterOptionalCallUndefined >= 0) {
            compilerContext.emitter.patchJump(jumpToEndAfterOptionalCallUndefined, endOffset);
        }
        if (jumpToEndAfterOptionalRootUndefined >= 0) {
            compilerContext.emitter.patchJump(jumpToEndAfterOptionalRootUndefined, endOffset);
        }
    }

    private void compileOptionalSuperMemberCallExpression(
            CallExpression callExpression,
            MemberExpression memberExpression,
            boolean isTailCall) {
        Opcode callMethodOpcode = isTailCall ? Opcode.TAIL_CALL_METHOD : Opcode.CALL_METHOD;
        delegates.emitHelpers.emitGetSuperValue(memberExpression, true);

        int jumpToUndefined = -1;
        if (callExpression.isOptional() || memberExpression.isOptional()) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            jumpToUndefined = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        }

        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        for (Expression argument : callExpression.getArguments()) {
            owner.compileExpression(argument);
        }
        compilerContext.emitter.emitOpcodeU16(callMethodOpcode, callExpression.getArguments().size());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        if (jumpToUndefined >= 0) {
            compilerContext.emitter.patchJump(jumpToUndefined, compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        }

        compilerContext.emitter.patchJump(jumpToEnd, compilerContext.emitter.currentOffset());
    }

    private void emitMemberFunctionWithReceiver(MemberExpression memberExpr) {
        if (memberExpr.isComputed()) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            owner.compileExpression(memberExpr.getProperty());
            compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
            return;
        }
        if (memberExpr.getProperty() instanceof PrivateIdentifier privateId) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            String fieldName = privateId.getName();
            JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
            if (symbol == null) {
                throw new JSSyntaxErrorException("Unexpected private field");
            }
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
            compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
            return;
        }
        if (memberExpr.getProperty() instanceof Identifier propId) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, propId.getName());
        }
    }

    private void emitPendingPostSuperInitialization() {
        if (compilerContext.pendingPostSuperInitialization != null) {
            compilerContext.pendingPostSuperInitialization.run();
        }
    }

    private void emitPropertyAccess(MemberExpression memberExpr) {
        if (memberExpr.isComputed()) {
            owner.compileExpression(memberExpr.getProperty());
            compilerContext.emitter.emitOpcode(Opcode.GET_ARRAY_EL);
        } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateId) {
            String fieldName = privateId.getName();
            JSSymbol symbol = compilerContext.privateSymbols != null ? compilerContext.privateSymbols.get(fieldName) : null;
            if (symbol != null) {
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, symbol);
                compilerContext.emitter.emitOpcode(Opcode.GET_PRIVATE_FIELD);
            } else {
                throw new JSSyntaxErrorException("Unexpected private field");
            }
        } else if (memberExpr.getProperty() instanceof Identifier propId) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD, propId.getName());
        }
    }

    private boolean isPartOfOptionalChain(Expression expr) {
        if (expr instanceof MemberExpression mem) {
            return mem.isOptional() || isPartOfOptionalChain(mem.getObject());
        }
        if (expr instanceof CallExpression callExpression) {
            return callExpression.isOptional() || isPartOfOptionalChain(callExpression.getCallee());
        }
        return false;
    }
}
