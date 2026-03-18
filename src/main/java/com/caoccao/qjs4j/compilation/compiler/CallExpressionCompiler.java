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

final class CallExpressionCompiler extends AstNodeCompiler<CallExpression> {
    CallExpressionCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(CallExpression callExpr) {
        boolean hasSpread = callExpr.getArguments().stream().anyMatch(arg -> arg instanceof SpreadElement);
        if (hasSpread) {
            compileCallExpressionWithSpread(callExpr);
        } else {
            compileCallExpressionRegular(callExpr);
        }
    }

    private void compileCallExpressionRegular(CallExpression callExpr) {
        if (callExpr.getCallee() instanceof Identifier calleeId && JSKeyword.SUPER.equals(calleeId.getName())) {
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
        boolean isTailCall = compilerContext.emitTailCalls;
        compilerContext.emitTailCalls = false;

        if (!callExpr.isOptional()
                && callExpr.getCallee() instanceof Identifier calleeId
                && JSKeyword.EVAL.equals(calleeId.getName())) {
            compilerContext.expressionCompiler.compile(callExpr.getCallee());
            for (Expression arg : callExpr.getArguments()) {
                compilerContext.expressionCompiler.compile(arg);
            }
            compilerContext.emitter.emitOpcode(Opcode.EVAL);
            compilerContext.emitter.emitU16(callExpr.getArguments().size());
            int evalFlags = isTailCall ? 1 : 0;
            if (compilerContext.inClassFieldInitializer || compilerContext.classFieldEvalContext) {
                evalFlags |= 2;
            }
            compilerContext.emitter.emitU16(evalFlags);
            return;
        }

        if (callExpr.getCallee() instanceof MemberExpression memberExpr
                && memberExpr.getObject().isSuperIdentifier()
                && (callExpr.isOptional() || memberExpr.isOptional())) {
            compileOptionalSuperMemberCallExpression(callExpr, memberExpr, isTailCall);
            return;
        }

        if (callExpr.getCallee() instanceof MemberExpression memberExpr
                && (callExpr.isOptional() || memberExpr.isOptional() || memberExpr.getObject().isPartOfOptionalChain())) {
            compileOptionalMemberCallExpression(callExpr, memberExpr, isTailCall);
            return;
        }
        if (callExpr.isOptional()) {
            compileOptionalCallExpression(callExpr, isTailCall);
            return;
        }

        if (callExpr.getCallee() instanceof MemberExpression memberExpr) {
            Opcode callMethodOpcode = isTailCall ? Opcode.TAIL_CALL_METHOD : Opcode.CALL_METHOD;

            if (memberExpr.getObject().isSuperIdentifier()) {
                compilerContext.emitHelpers.emitGetSuperValue(memberExpr, true);
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
                for (Expression arg : callExpr.getArguments()) {
                    compilerContext.expressionCompiler.compile(arg);
                }
                compilerContext.emitter.emitOpcodeU16(callMethodOpcode, callExpr.getArguments().size());
                return;
            }

            compilerContext.expressionCompiler.compile(memberExpr.getObject());

            if (memberExpr.isComputed()) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.expressionCompiler.compile(memberExpr.getProperty());
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

            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            for (Expression arg : callExpr.getArguments()) {
                compilerContext.expressionCompiler.compile(arg);
            }

            compilerContext.emitter.emitOpcodeU16(callMethodOpcode, callExpr.getArguments().size());
        } else {
            if (callExpr.getCallee() instanceof Identifier calleeId
                    && (compilerContext.withObjectManager.hasActiveWithObject()
                    || !compilerContext.withObjectManager.getInheritedBindingNames().isEmpty())) {
                compilerContext.identifierCompiler.emitWithAwareIdentifierLookupForCall(calleeId.getName());
            } else {
                compilerContext.expressionCompiler.compile(callExpr.getCallee());
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            }
            for (Expression arg : callExpr.getArguments()) {
                compilerContext.expressionCompiler.compile(arg);
            }
            Opcode callOpcode = isTailCall ? Opcode.TAIL_CALL : Opcode.CALL;
            compilerContext.emitter.emitOpcodeU16(callOpcode, callExpr.getArguments().size());
        }
    }

    private void compileCallExpressionWithSpread(CallExpression callExpr) {
        compilerContext.emitTailCalls = false;
        if (callExpr.getCallee() instanceof Identifier calleeId && JSKeyword.SUPER.equals(calleeId.getName())) {
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
        if (!callExpr.isOptional()
                && callExpr.getCallee() instanceof Identifier calleeId
                && JSKeyword.EVAL.equals(calleeId.getName())) {
            compilerContext.expressionCompiler.compile(callExpr.getCallee());
            compilerContext.emitHelpers.emitArgumentsArrayWithSpread(callExpr.getArguments());
            compilerContext.emitter.emitOpcodeU16(Opcode.APPLY_EVAL, 0);
            return;
        }
        if (callExpr.getCallee() instanceof MemberExpression memberExpr) {
            if (memberExpr.getObject().isSuperIdentifier()) {
                compilerContext.emitHelpers.emitGetSuperValue(memberExpr, true);
                compilerContext.emitHelpers.emitArgumentsArrayWithSpread(callExpr.getArguments());
                compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 0);
                return;
            }

            compilerContext.expressionCompiler.compile(memberExpr.getObject());

            if (memberExpr.isComputed()) {
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.expressionCompiler.compile(memberExpr.getProperty());
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
                    && (compilerContext.withObjectManager.hasActiveWithObject()
                    || !compilerContext.withObjectManager.getInheritedBindingNames().isEmpty())) {
                compilerContext.identifierCompiler.emitWithAwareIdentifierLookupForCall(calleeId.getName());
                compilerContext.emitter.emitOpcode(Opcode.SWAP);
            } else {
                compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                compilerContext.expressionCompiler.compile(callExpr.getCallee());
            }
        }

        compilerContext.emitHelpers.emitArgumentsArrayWithSpread(callExpr.getArguments());
        compilerContext.emitter.emitOpcodeU16(Opcode.APPLY, 0);
    }

    private void compileOptionalCallExpression(CallExpression callExpr, boolean isTailCall) {
        compilerContext.expressionCompiler.compile(callExpr.getCallee());
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToUndefined = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        for (Expression arg : callExpr.getArguments()) {
            compilerContext.expressionCompiler.compile(arg);
        }
        Opcode callOpcode = isTailCall ? Opcode.TAIL_CALL : Opcode.CALL;
        compilerContext.emitter.emitOpcodeU16(callOpcode, callExpr.getArguments().size());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        compilerContext.emitter.patchJump(jumpToUndefined, compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);

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
        compilerContext.expressionCompiler.compile(chain.get(0).getObject());

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
            compilerContext.expressionCompiler.compile(arg);
        }
        compilerContext.emitter.emitOpcodeU16(callMethodOpcode, callExpr.getArguments().size());
        int jumpToEnd = compilerContext.emitter.emitJump(Opcode.GOTO);

        int jumpToEndAfterOptionalCallUndefined = -1;
        if (callExpr.isOptional()) {
            compilerContext.emitter.patchJump(jumpToUndefinedFromOptionalCall, compilerContext.emitter.currentOffset());
            compilerContext.emitter.emitOpcode(Opcode.DROP);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
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
        compilerContext.emitHelpers.emitGetSuperValue(memberExpression, true);

        int jumpToUndefined = -1;
        if (callExpression.isOptional() || memberExpression.isOptional()) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
            jumpToUndefined = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        }

        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        for (Expression argument : callExpression.getArguments()) {
            compilerContext.expressionCompiler.compile(argument);
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
            compilerContext.expressionCompiler.compile(memberExpr.getProperty());
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
            compilerContext.expressionCompiler.compile(memberExpr.getProperty());
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
}
