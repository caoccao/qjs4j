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
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.vm.Opcode;

import java.math.BigInteger;
import java.util.List;

/**
 * Delegate compiler for bytecode emission helper methods.
 * Provides utility methods for emitting common bytecode patterns
 * such as iterator close, captured values, method calls, and using disposal.
 */
final class EmitHelpers {
    private final CompilerContext ctx;
    private final CompilerDelegates delegates;

    EmitHelpers(CompilerContext ctx, CompilerDelegates delegates) {
        this.ctx = ctx;
        this.delegates = delegates;
    }

    void emitAbruptCompletionIteratorClose() {
        for (LoopContext loopContext : ctx.loopStack) {
            if (loopContext.hasIterator) {
                ctx.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
            }
        }
    }

    /**
     * Emit the Annex B.3.3 var-scope store for a function declaration.
     * In global scope, uses PUT_VAR (global object property).
     * In function scope, uses PUT_LOCAL to the function-scope local
     * (bypassing the block-scoped lexical binding).
     */
    void emitAnnexBVarStore(String functionName) {
        if (ctx.inGlobalScope) {
            ctx.emitter.emitOpcodeAtom(Opcode.PUT_VAR, functionName);
        } else {
            Integer funcScopeLocal = ctx.annexBFunctionScopeLocals.get(functionName);
            if (funcScopeLocal != null) {
                ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, funcScopeLocal);
            } else {
                // Fallback: store as global var (shouldn't normally happen)
                ctx.emitter.emitOpcodeAtom(Opcode.PUT_VAR, functionName);
            }
        }
    }

    void emitArgumentsArrayWithSpread(List<Expression> arguments) {
        ctx.emitter.emitOpcode(Opcode.ARRAY_NEW);

        boolean hasSpread = arguments.stream()
                .anyMatch(arg -> arg instanceof SpreadElement);

        if (!hasSpread) {
            for (Expression arg : arguments) {
                delegates.expressions.compileExpression(arg);
                ctx.emitter.emitOpcode(Opcode.PUSH_ARRAY);
            }
            return;
        }

        // QuickJS-style lowering keeps an explicit append index once spread appears.
        int idx = 0;
        boolean needsIndex = false;
        for (Expression arg : arguments) {
            if (arg instanceof SpreadElement spreadElement) {
                if (!needsIndex) {
                    ctx.emitter.emitOpcodeU32(Opcode.PUSH_I32, idx);
                    needsIndex = true;
                }
                delegates.expressions.compileExpression(spreadElement.argument());
                ctx.emitter.emitOpcode(Opcode.APPEND);
            } else if (needsIndex) {
                delegates.expressions.compileExpression(arg);
                ctx.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                ctx.emitter.emitOpcode(Opcode.INC);
            } else {
                delegates.expressions.compileExpression(arg);
                ctx.emitter.emitOpcode(Opcode.PUSH_ARRAY);
                idx++;
            }
        }
        if (needsIndex) {
            ctx.emitter.emitOpcode(Opcode.DROP);
        }
    }

    /**
     * Build capture source info and set it on the template function.
     * Instead of emitting GET_LOCAL/GET_VAR_REF opcodes to push values onto the stack,
     * we store the capture source information in the template function so FCLOSURE
     * can create VarRef objects directly from the parent frame at runtime.
     * This enables reference-based closure capture (mutations visible across closures).
     */
    void emitCapturedValues(BytecodeCompiler nestedCompiler, JSBytecodeFunction templateFunction) {
        int captureCount = nestedCompiler.context().captureResolver.getCapturedBindingCount();
        if (captureCount == 0) {
            return;
        }
        int[] captureSourceInfos = new int[captureCount];
        int i = 0;
        for (CaptureResolver.CaptureBinding binding : nestedCompiler.context().captureResolver.getCapturedBindings()) {
            if (binding.source().type() == CaptureResolver.CaptureSourceType.LOCAL) {
                // Positive value = LOCAL capture at this index
                captureSourceInfos[i] = binding.source().index();
            } else {
                // Negative value = VAR_REF capture, encoded as -(index + 1)
                captureSourceInfos[i] = -(binding.source().index() + 1);
            }
            i++;
        }
        templateFunction.setCaptureSourceInfos(captureSourceInfos);
    }

    /**
     * Emit the correct opcode sequence for a class method definition.
     * For getter/setter methods, emits DEFINE_METHOD_COMPUTED with accessor flags.
     * For regular methods, emits DEFINE_METHOD with the method name atom.
     * Stack before: ... obj
     * Stack after:  ... obj (method added to obj)
     */
    void emitClassMethodDefinition(ClassDeclaration.MethodDefinition method,
                                   JSBytecodeFunction methodFunc, String methodName) {
        String kind = method.kind();
        if ("get".equals(kind) || "set".equals(kind)) {
            // Getter/setter: use DEFINE_METHOD_COMPUTED with accessor flags
            // Stack: ... obj -> ... obj key method -> ... obj
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(methodName));
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, methodFunc);
            int methodKind = "get".equals(kind) ? 1 : 2;
            // Class properties are not enumerable (no enumerable flag)
            ctx.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, methodKind);
        } else {
            // Regular method: use DEFINE_METHOD with atom name
            // Stack: ... obj -> ... obj method -> ... obj
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, methodFunc);
            ctx.emitter.emitOpcodeAtom(Opcode.DEFINE_METHOD, methodName);
        }
    }

    /**
     * Emit CLOSE_LOC opcodes for variables declared in a VariableDeclaration.
     * Used at the end of for-loop iteration bodies to freeze VarRefs for per-iteration binding.
     */
    void emitCloseLocForPattern(VariableDeclaration varDecl) {
        for (VariableDeclaration.VariableDeclarator decl : varDecl.declarations()) {
            emitCloseLocForPattern(decl.id());
        }
    }

    /**
     * Emit CLOSE_LOC opcodes for variables in a pattern.
     * Handles Identifier patterns and can be extended for destructuring.
     */
    void emitCloseLocForPattern(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            Integer localIdx = ctx.findLocalInScopes(id.name());
            if (localIdx != null) {
                ctx.emitter.emitOpcodeU16(Opcode.CLOSE_LOC, localIdx);
            }
        }
        // Destructuring patterns would need recursive handling here
    }

    void emitConditionalVarInit(String name) {
        ctx.emitter.emitOpcodeAtom(Opcode.PUSH_ATOM_VALUE, name);
        ctx.emitter.emitOpcodeAtom(Opcode.GET_VAR, "globalThis");
        ctx.emitter.emitOpcode(Opcode.IN);
        int skipJump = ctx.emitter.emitJump(Opcode.IF_TRUE);
        ctx.emitter.emitOpcode(Opcode.UNDEFINED);
        ctx.emitter.emitOpcodeAtom(Opcode.PUT_VAR, name);
        ctx.emitter.patchJump(skipJump, ctx.emitter.currentOffset());
    }

    void emitCurrentScopeUsingDisposal() {
        emitScopeUsingDisposal(ctx.currentScope());
    }

    void emitDefaultParameterInit(BytecodeCompiler functionCompiler, List<Expression> defaults) {
        for (int i = 0; i < defaults.size(); i++) {
            Expression defaultExpr = defaults.get(i);
            if (defaultExpr != null) {
                // GET_ARG idx - push the argument value onto the stack
                functionCompiler.context().emitter.emitOpcodeU16(Opcode.GET_ARG, i);
                // DUP - duplicate for the comparison
                functionCompiler.context().emitter.emitOpcode(Opcode.DUP);
                // UNDEFINED - push undefined for comparison
                functionCompiler.context().emitter.emitOpcode(Opcode.UNDEFINED);
                // STRICT_EQ - check if arg === undefined
                functionCompiler.context().emitter.emitOpcode(Opcode.STRICT_EQ);
                // IF_FALSE label - if arg !== undefined, skip default
                int skipLabel = functionCompiler.context().emitter.emitJump(Opcode.IF_FALSE);
                // DROP - drop the duplicated arg value (it was undefined)
                functionCompiler.context().emitter.emitOpcode(Opcode.DROP);
                // Compile the default expression
                functionCompiler.delegates().expressions.compileExpression(defaultExpr);
                // DUP - duplicate for PUT_ARG
                functionCompiler.context().emitter.emitOpcode(Opcode.DUP);
                // PUT_ARG idx - store back into the argument slot
                functionCompiler.context().emitter.emitOpcodeU16(Opcode.PUT_ARG, i);
                // label: - skip target (value is on stack, either original arg or default)
                functionCompiler.context().emitter.patchJump(skipLabel, functionCompiler.context().emitter.currentOffset());
                // PUT_LOCAL idx - store into the local variable slot
                functionCompiler.context().emitter.emitOpcodeU16(Opcode.PUT_LOCAL, i);
            }
        }
    }

    void emitGetSuperValue(MemberExpression memberExpr, boolean keepReceiverForCall) {
        // Stack start: []
        // Push current this as receiver for super property resolution.
        ctx.emitter.emitOpcode(Opcode.PUSH_THIS);
        if (keepReceiverForCall) {
            // Keep one copy of receiver for the eventual CALL/APPLY thisArg.
            ctx.emitter.emitOpcode(Opcode.DUP);
        }
        // Resolve super base: [[HomeObject]].[[Prototype]]
        ctx.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
        ctx.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
        ctx.emitter.emitOpcode(Opcode.GET_SUPER);
        emitSuperPropertyKey(memberExpr);
        ctx.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
    }

    /**
     * Emit ITERATOR_CLOSE for any for-of loops between the current position and the target
     * loop context. This is needed when labeled break/continue crosses for-of loop boundaries,
     * to properly close inner iterators whose cleanup code would otherwise be skipped.
     * Following QuickJS close_scopes pattern for iterator cleanup.
     */
    void emitIteratorCloseForLoopsUntil(LoopContext target) {
        for (LoopContext loopCtx : ctx.loopStack) {
            if (loopCtx == target) {
                break;
            }
            if (loopCtx.hasIterator) {
                ctx.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
            }
        }
    }

    void emitMethodCallOnLocalObject(int localIndex, String methodName, int argCount) {
        ctx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
        ctx.emitter.emitOpcode(Opcode.DUP);
        ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, methodName);
        ctx.emitter.emitOpcode(Opcode.SWAP);
        ctx.emitter.emitOpcodeU16(Opcode.CALL, argCount);
    }

    void emitMethodCallWithSingleArgOnLocalObject(int localIndex, String methodName) {
        int argLocalIndex = ctx.currentScope().declareLocal("$using_arg_" + ctx.emitter.currentOffset());
        ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, argLocalIndex);

        ctx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, localIndex);
        ctx.emitter.emitOpcode(Opcode.DUP);
        ctx.emitter.emitOpcodeAtom(Opcode.GET_FIELD, methodName);
        ctx.emitter.emitOpcode(Opcode.SWAP);
        ctx.emitter.emitOpcodeU16(Opcode.GET_LOCAL, argLocalIndex);
        ctx.emitter.emitOpcodeU16(Opcode.CALL, 1);
    }

    void emitNonComputedPublicFieldKey(Expression key) {
        if (key instanceof Identifier id) {
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.name()));
            return;
        }
        if (key instanceof Literal literal) {
            Object value = literal.value();
            if (value == null) {
                ctx.emitter.emitOpcode(Opcode.NULL);
            } else if (value instanceof Boolean bool) {
                ctx.emitter.emitOpcode(bool ? Opcode.PUSH_TRUE : Opcode.PUSH_FALSE);
            } else if (value instanceof BigInteger bigInt) {
                ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSBigInt(bigInt));
            } else if (value instanceof Number num) {
                ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(num.doubleValue()));
            } else if (value instanceof String str) {
                ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(str));
            } else {
                throw new JSCompilerException("Unsupported field key literal type: " + value.getClass());
            }
            return;
        }
        throw new JSCompilerException("Invalid non-computed field key");
    }

    void emitScopeUsingDisposal(CompilerScope scope) {
        Integer usingStackLocalIndex = scope.getUsingStackLocalIndex();
        if (usingStackLocalIndex == null) {
            return;
        }

        if (scope.isUsingStackAsync()) {
            emitMethodCallOnLocalObject(usingStackLocalIndex, "disposeAsync", 0);
            ctx.emitter.emitOpcode(Opcode.AWAIT);
            ctx.emitter.emitOpcode(Opcode.DROP);
        } else {
            emitMethodCallOnLocalObject(usingStackLocalIndex, "dispose", 0);
            ctx.emitter.emitOpcode(Opcode.DROP);
        }
    }

    void emitSuperPropertyKey(MemberExpression memberExpr) {
        if (memberExpr.computed()) {
            delegates.expressions.compileExpression(memberExpr.property());
        } else if (memberExpr.property() instanceof Identifier propId) {
            ctx.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(propId.name()));
        } else if (memberExpr.property() instanceof PrivateIdentifier) {
            throw new JSCompilerException("super private fields are not supported");
        } else {
            delegates.expressions.compileExpression(memberExpr.property());
        }
    }

    void emitUsingDisposalsForScopeDepthGreaterThan(int targetScopeDepth) {
        for (CompilerScope scope : ctx.scopes) {
            if (scope.getScopeDepth() > targetScopeDepth) {
                emitScopeUsingDisposal(scope);
            }
        }
    }

    int ensureUsingStackLocal(boolean asyncUsingDeclaration) {
        CompilerScope scope = ctx.currentScope();
        Integer existingLocalIndex = scope.getUsingStackLocalIndex();
        if (existingLocalIndex != null) {
            if (asyncUsingDeclaration && !scope.isUsingStackAsync()) {
                throw new JSCompilerException("Cannot mix await using with sync using stack in the same scope");
            }
            return existingLocalIndex;
        }

        boolean useAsyncStack = asyncUsingDeclaration || ctx.isInAsyncFunction;
        String constructorName = useAsyncStack ? JSAsyncDisposableStack.NAME : JSDisposableStack.NAME;
        int stackLocalIndex = scope.declareLocal("$using_stack_" + scope.getScopeDepth() + "_" + ctx.emitter.currentOffset());

        ctx.emitter.emitOpcodeAtom(Opcode.GET_VAR, constructorName);
        ctx.emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, 0);
        ctx.emitter.emitOpcodeU16(Opcode.PUT_LOCAL, stackLocalIndex);

        scope.setUsingStackLocal(stackLocalIndex, useAsyncStack);
        return stackLocalIndex;
    }
}
