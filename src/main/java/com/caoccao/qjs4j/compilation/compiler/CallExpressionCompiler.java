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
import com.caoccao.qjs4j.vm.Opcode;

final class CallExpressionCompiler extends AstNodeCompiler<CallExpression> {
    CallExpressionCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(CallExpression callExpr) {
        boolean joinsOptionalChain = compilerContext.emitHelpers.consumeOptionalChainLink(callExpr);
        if (!joinsOptionalChain && callExpr.isPartOfOptionalChain()) {
            // The last link of an optional chain: every `?.` in it exits here.
            compilerContext.emitHelpers.compileOptionalChain(() -> compileCall(callExpr), null);
        } else {
            compileCall(callExpr);
        }
    }

    private void compileCall(CallExpression callExpr) {
        boolean hasSpread = callExpr.getArguments().stream().anyMatch(arg -> arg instanceof SpreadElement);
        if (callExpr.getCallee() instanceof Identifier calleeId && JSKeyword.SUPER.equals(calleeId.getName())) {
            if (hasSpread) {
                compilerContext.emitTailCalls = false;
            }
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(3);
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(2);
            compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
            compilerContext.emitHelpers.emitArgumentsArrayWithSpread(callExpr.getArguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 1);
            compilerContext.emitter.emitOpcode(Opcode.INIT_CTOR);
            emitPendingPostSuperInitialization();
            return;
        }
        if (hasSpread) {
            compileCallExpressionWithSpread(callExpr);
        } else {
            compileCallExpressionRegular(callExpr);
        }
    }

    private void compileCallExpressionRegular(CallExpression callExpr) {
        boolean isTailCall = compilerContext.emitTailCalls;
        compilerContext.emitTailCalls = false;

        if (callExpr.isDirectEvalCall()) {
            compilerContext.expressionCompiler.compile(callExpr.getCallee());
            for (Expression arg : callExpr.getArguments()) {
                compilerContext.expressionCompiler.compile(arg);
            }
            emitCallSourcePosition(callExpr);
            compilerContext.emitter.emitOpcode(Opcode.EVAL);
            compilerContext.emitter.emitU16(callExpr.getArguments().size());
            int evalFlags = isTailCall ? 1 : 0;
            if (compilerContext.inClassFieldInitializer || compilerContext.classFieldEvalContext) {
                evalFlags |= 2;
            }
            compilerContext.emitter.emitU16(evalFlags);
            return;
        }

        Opcode callOpcode;
        if (callExpr.getCallee() instanceof MemberExpression memberExpr) {
            // Stack: -> receiver function -> function receiver
            emitMemberCalleeWithReceiver(memberExpr, callExpr.isPartOfOptionalChain());
            if (callExpr.isOptional()) {
                compilerContext.emitHelpers.emitOptionalChainTest(2);
            }
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            callOpcode = isTailCall ? Opcode.TAIL_CALL_METHOD : Opcode.CALL_METHOD;
        } else {
            // Stack: -> function receiver
            emitCalleeWithReceiver(callExpr);
            callOpcode = isTailCall ? Opcode.TAIL_CALL : Opcode.CALL;
        }
        for (Expression arg : callExpr.getArguments()) {
            compilerContext.expressionCompiler.compile(arg);
        }
        emitCallSourcePosition(callExpr);
        compilerContext.emitter.emitOpcodeU16(callOpcode, callExpr.getArguments().size());
    }

    private void compileCallExpressionWithSpread(CallExpression callExpr) {
        compilerContext.emitTailCalls = false;
        if (callExpr.isDirectEvalCall()) {
            compilerContext.expressionCompiler.compile(callExpr.getCallee());
            compilerContext.emitHelpers.emitArgumentsArrayWithSpread(callExpr.getArguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY_EVAL, 0);
            return;
        }
        if (callExpr.getCallee() instanceof MemberExpression memberExpr) {
            // Stack: -> receiver function
            emitMemberCalleeWithReceiver(memberExpr, callExpr.isPartOfOptionalChain());
            if (callExpr.isOptional()) {
                compilerContext.emitHelpers.emitOptionalChainTest(2);
            }
        } else {
            // Stack: -> function receiver -> receiver function
            emitCalleeWithReceiver(callExpr);
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        }

        compilerContext.emitHelpers.emitArgumentsArrayWithSpread(callExpr.getArguments());
        emitCallSourcePosition(callExpr);
        compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 0);
    }

    /**
     * Emit a callee that is not a member expression, with the receiver the call passes. Stack: -> function receiver
     */
    private void emitCalleeWithReceiver(CallExpression callExpr) {
        if (callExpr.getCallee() instanceof Identifier calleeId
                && (compilerContext.withObjectManager.hasActiveWithObject()
                        || !compilerContext.withObjectManager.getInheritedBindingNames().isEmpty())) {
            // Inside `with`, the receiver is the with object the name resolved on.
            compilerContext.identifierCompiler.emitWithAwareIdentifierLookupForCall(calleeId.getName());
            if (callExpr.isOptional()) {
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                compilerContext.emitHelpers.emitOptionalChainTest(2);
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
            }
            return;
        }
        compilerContext.emitHelpers.compileOptionalChainLinkTarget(callExpr, callExpr.getCallee());
        if (callExpr.isOptional()) {
            compilerContext.emitHelpers.emitOptionalChainTest(1);
        }
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
    }

    /**
     * Record the position a frame stopped at this call reports in a stack trace, as V8 does: the property name of a
     * {@code .name} method call, the callee of a plain call, otherwise — a computed method call included — the opening
     * parenthesis.
     */
    private void emitCallSourcePosition(CallExpression callExpr) {
        Expression callee = callExpr.getCallee();
        compilerContext.emitter.emitCallDescription(CallPrinter.describeCallee(callee));
        if (callee instanceof MemberExpression memberExpr && !memberExpr.isComputed()) {
            compilerContext.emitter.emitSourcePosition(memberExpr.getProperty().getLocation());
        } else if (callee instanceof Identifier) {
            compilerContext.emitter.emitSourcePosition(callee.getLocation());
        } else {
            compilerContext.emitter.emitSourcePosition(callExpr.getLocation());
        }
    }

    /**
     * Emit a member expression being called, keeping its object as the receiver. Stack: -> receiver function
     * <p>
     * A callee that belongs to an optional chain the call does not belong to is one that parentheses ended:
     * {@code (a?.b)()}. When that chain short-circuits, the parentheses evaluate to {@code undefined} and the call is
     * still made — it throws a {@code TypeError} — so the chain is compiled on its own and its short-circuit path
     * supplies an {@code undefined} receiver beside the {@code undefined} function. The call used to be skipped along
     * with the chain.
     *
     * @param memberExpr
     *            the callee
     * @param callIsPartOfOptionalChain
     *            whether the call itself belongs to an optional chain
     */
    void emitMemberCalleeWithReceiver(MemberExpression memberExpr, boolean callIsPartOfOptionalChain) {
        if (memberExpr.getObject().isSuperIdentifier()) {
            compilerContext.emitHelpers.emitGetSuperValue(memberExpr, true);
        } else if (memberExpr.isPartOfOptionalChain() && !callIsPartOfOptionalChain) {
            compilerContext.emitHelpers.compileOptionalChain(() -> emitMemberLinkWithReceiver(memberExpr),
                    () -> compilerContext.emitter.emitOpcode(Opcode.UNDEFINED));
        } else {
            emitMemberLinkWithReceiver(memberExpr);
        }
    }

    private void emitMemberLinkWithReceiver(MemberExpression memberExpr) {
        // Stack: -> object -> object function
        compilerContext.emitHelpers.compileOptionalChainLinkTarget(memberExpr, memberExpr.getObject());
        if (memberExpr.isOptional()) {
            compilerContext.emitHelpers.emitOptionalChainTest(1);
        }
        compilerContext.memberExpressionCompiler.emitPropertyAccess(memberExpr, true);
    }

    private void emitPendingPostSuperInitialization() {
        if (compilerContext.pendingPostSuperInitialization != null) {
            compilerContext.pendingPostSuperInitialization.run();
        }
    }
}
