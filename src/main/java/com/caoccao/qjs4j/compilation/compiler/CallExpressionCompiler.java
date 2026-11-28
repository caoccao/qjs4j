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

final class CallExpressionCompiler {
    private CallExpressionCompiler() {
    }

    static void compile(CompilerContext compilerContext, CallExpression callExpr) {
        boolean joinsOptionalChain = OptionalChainCompiler.consumeOptionalChainLink(compilerContext, callExpr);
        if (!joinsOptionalChain && callExpr.isPartOfOptionalChain()) {
            // The last link of an optional chain: every `?.` in it exits here.
            OptionalChainCompiler.compileOptionalChain(compilerContext, () -> compileCall(compilerContext, callExpr),
                    null);
        } else {
            compileCall(compilerContext, callExpr);
        }
    }

    private static void compileCall(CompilerContext compilerContext, CallExpression callExpr) {
        BytecodeEmitter emitter = compilerContext.emitter;
        boolean hasSpread = callExpr.getArguments().stream().anyMatch(arg -> arg instanceof SpreadElement);
        if (callExpr.getCallee() instanceof Identifier calleeId && JSKeyword.SUPER.equals(calleeId.getName())) {
            if (hasSpread) {
                compilerContext.emitTailCalls = false;
            }
            emitter.emitIntOperand(Opcode.SPECIAL_OBJECT, 3);
            emitter.emitIntOperand(Opcode.SPECIAL_OBJECT, 2);
            emitter.emitOpcode(Opcode.GET_SUPER);
            ArrayExpressionCompiler.emitArray(compilerContext, callExpr.getArguments());
            emitter.emitIntOperand(Opcode.APPLY, 1);
            emitter.emitOpcode(Opcode.INIT_CTOR);
            emitPendingPostSuperInitialization(compilerContext);
            return;
        }
        if (hasSpread) {
            compileCallExpressionWithSpread(compilerContext, callExpr);
        } else {
            compileCallExpressionRegular(compilerContext, callExpr);
        }
    }

    private static void compileCallExpressionRegular(CompilerContext compilerContext, CallExpression callExpr) {
        BytecodeEmitter emitter = compilerContext.emitter;
        boolean isTailCall = compilerContext.emitTailCalls;
        compilerContext.emitTailCalls = false;

        if (callExpr.isDirectEvalCall()) {
            compilerContext.compileExpression(callExpr.getCallee());
            for (Expression arg : callExpr.getArguments()) {
                compilerContext.compileExpression(arg);
            }
            emitCallSourcePosition(compilerContext, callExpr);
            emitter.emitOpcode(Opcode.EVAL);
            emitter.emitU16(callExpr.getArguments().size());
            int evalFlags = isTailCall ? 1 : 0;
            if (compilerContext.inClassFieldInitializer || compilerContext.classFieldEvalContext) {
                evalFlags |= 2;
            }
            emitter.emitU16(evalFlags);
            return;
        }

        Opcode callOpcode;
        if (callExpr.getCallee() instanceof MemberExpression memberExpr) {
            // Stack: -> receiver function -> function receiver
            emitMemberCalleeWithReceiver(compilerContext, memberExpr, callExpr.isPartOfOptionalChain());
            if (callExpr.isOptional()) {
                OptionalChainCompiler.emitOptionalChainTest(compilerContext, 2);
            }
            emitter.emitOpcode(Opcode.SWAP);
            callOpcode = isTailCall ? Opcode.TAIL_CALL_METHOD : Opcode.CALL_METHOD;
        } else {
            // Stack: -> function receiver
            emitCalleeWithReceiver(compilerContext, callExpr);
            callOpcode = isTailCall ? Opcode.TAIL_CALL : Opcode.CALL;
        }
        for (Expression arg : callExpr.getArguments()) {
            compilerContext.compileExpression(arg);
        }
        emitCallSourcePosition(compilerContext, callExpr);
        emitter.emitOpcodeU16(callOpcode, callExpr.getArguments().size());
    }

    private static void compileCallExpressionWithSpread(CompilerContext compilerContext, CallExpression callExpr) {
        BytecodeEmitter emitter = compilerContext.emitter;
        compilerContext.emitTailCalls = false;
        if (callExpr.isDirectEvalCall()) {
            compilerContext.compileExpression(callExpr.getCallee());
            ArrayExpressionCompiler.emitArray(compilerContext, callExpr.getArguments());
            emitter.emitIntOperand(Opcode.APPLY_EVAL, 0);
            return;
        }
        if (callExpr.getCallee() instanceof MemberExpression memberExpr) {
            // Stack: -> receiver function
            emitMemberCalleeWithReceiver(compilerContext, memberExpr, callExpr.isPartOfOptionalChain());
            if (callExpr.isOptional()) {
                OptionalChainCompiler.emitOptionalChainTest(compilerContext, 2);
            }
        } else {
            // Stack: -> function receiver -> receiver function
            emitCalleeWithReceiver(compilerContext, callExpr);
            emitter.emitOpcode(Opcode.SWAP);
        }

        ArrayExpressionCompiler.emitArray(compilerContext, callExpr.getArguments());
        emitCallSourcePosition(compilerContext, callExpr);
        emitter.emitIntOperand(Opcode.APPLY, 0);
    }

    /**
     * Emit a callee that is not a member expression, with the receiver the call passes. Stack: -> function receiver
     */
    private static void emitCalleeWithReceiver(CompilerContext compilerContext, CallExpression callExpr) {
        BytecodeEmitter emitter = compilerContext.emitter;
        if (callExpr.getCallee() instanceof Identifier calleeId
                && (compilerContext.withObjectManager.hasActiveWithObject()
                        || !compilerContext.withObjectManager.getInheritedBindingNames().isEmpty())) {
            // Inside `with`, the receiver is the with object the name resolved on.
            IdentifierCompiler.emitWithAwareIdentifierLookupForCall(compilerContext, calleeId.getName());
            if (callExpr.isOptional()) {
                emitter.emitOpcode(Opcode.SWAP);
                OptionalChainCompiler.emitOptionalChainTest(compilerContext, 2);
                emitter.emitOpcode(Opcode.SWAP);
            }
            return;
        }
        OptionalChainCompiler.compileOptionalChainLinkTarget(compilerContext, callExpr, callExpr.getCallee());
        if (callExpr.isOptional()) {
            OptionalChainCompiler.emitOptionalChainTest(compilerContext, 1);
        }
        emitter.emitOpcode(Opcode.UNDEFINED);
    }

    /**
     * Record the position a frame stopped at this call reports in a stack trace, as V8 does: the property name of a
     * {@code .name} method call, the callee of a plain call, otherwise — a computed method call included — the opening
     * parenthesis.
     */
    private static void emitCallSourcePosition(CompilerContext compilerContext, CallExpression callExpr) {
        BytecodeEmitter emitter = compilerContext.emitter;
        Expression callee = callExpr.getCallee();
        emitter.emitCallDescription(CallPrinter.describeCallee(callee));
        if (callee instanceof MemberExpression memberExpr && !memberExpr.isComputed()) {
            emitter.emitSourcePosition(memberExpr.getProperty().getLocation());
        } else if (callee instanceof Identifier) {
            emitter.emitSourcePosition(callee.getLocation());
        } else {
            emitter.emitSourcePosition(callExpr.getLocation());
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
    static void emitMemberCalleeWithReceiver(CompilerContext compilerContext, MemberExpression memberExpr,
            boolean callIsPartOfOptionalChain) {
        if (memberExpr.getObject().isSuperIdentifier()) {
            MemberExpressionCompiler.emitGetSuperValue(compilerContext, memberExpr, true);
        } else if (memberExpr.isPartOfOptionalChain() && !callIsPartOfOptionalChain) {
            OptionalChainCompiler.compileOptionalChain(compilerContext,
                    () -> emitMemberLinkWithReceiver(compilerContext, memberExpr),
                    () -> compilerContext.emitter.emitOpcode(Opcode.UNDEFINED));
        } else {
            emitMemberLinkWithReceiver(compilerContext, memberExpr);
        }
    }

    private static void emitMemberLinkWithReceiver(CompilerContext compilerContext, MemberExpression memberExpr) {
        // Stack: -> object -> object function
        OptionalChainCompiler.compileOptionalChainLinkTarget(compilerContext, memberExpr, memberExpr.getObject());
        if (memberExpr.isOptional()) {
            OptionalChainCompiler.emitOptionalChainTest(compilerContext, 1);
        }
        MemberExpressionCompiler.emitPropertyAccess(compilerContext, memberExpr, true);
    }

    private static void emitPendingPostSuperInitialization(CompilerContext compilerContext) {
        if (compilerContext.pendingPostSuperInitialization != null) {
            compilerContext.pendingPostSuperInitialization.run();
        }
    }
}
