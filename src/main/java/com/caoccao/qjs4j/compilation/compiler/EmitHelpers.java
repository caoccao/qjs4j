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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Delegate compiler for bytecode emission helper methods.
 * Provides utility methods for emitting common bytecode patterns
 * such as iterator close, captured values, method calls, and using disposal.
 */
final class EmitHelpers {
    private final CompilerContext compilerContext;
    private final CompilerDelegates delegates;

    EmitHelpers(CompilerContext compilerContext, CompilerDelegates delegates) {
        this.compilerContext = compilerContext;
        this.delegates = delegates;
    }

    void emitAbruptCompletionIteratorClose() {
        for (LoopContext loopContext : compilerContext.loopStack) {
            if (loopContext.hasIterator) {
                compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
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
        if (compilerContext.inGlobalScope) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, functionName);
        } else {
            Integer funcScopeLocal = compilerContext.annexBFunctionScopeLocals.get(functionName);
            if (funcScopeLocal != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, funcScopeLocal);
            } else {
                // Fallback: store as global var (shouldn't normally happen)
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, functionName);
            }
        }
    }

    void emitArgumentsArrayWithSpread(List<Expression> arguments) {
        compilerContext.emitter.emitOpcodeU16(Opcode.ARRAY_FROM, 0);

        boolean hasSpread = arguments.stream()
                .anyMatch(arg -> arg instanceof SpreadElement);

        // Always use index-based pattern (QuickJS style)
        compilerContext.emitter.emitOpcodeU32(Opcode.PUSH_I32, 0);
        for (Expression arg : arguments) {
            if (arg instanceof SpreadElement spreadElement) {
                delegates.expressions.compileExpression(spreadElement.getArgument());
                compilerContext.emitter.emitOpcode(Opcode.APPEND);
            } else {
                delegates.expressions.compileExpression(arg);
                compilerContext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                compilerContext.emitter.emitOpcode(Opcode.INC);
            }
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
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
        templateFunction.setCapturedVarNames(nestedCompiler.context().captureResolver.getCapturedBindingNamesBySlot());
    }

    /**
     * Emit the correct opcode sequence for a class method definition.
     * Class methods always use DEFINE_METHOD_COMPUTED so attributes and
     * DefinePropertyOrThrow semantics match ECMAScript class semantics.
     * Stack before: ... obj
     * Stack after:  ... obj (method added to obj)
     */
    void emitClassMethodDefinition(MethodDefinition method,
                                   JSBytecodeFunction methodFunc, String methodName) {
        String kind = method.getKind();
        boolean isComputedKey = method.isComputed() && !(method.getKey() instanceof Literal);
        // Class definitions must create fresh function objects per evaluation.

        if (isComputedKey) {
            delegates.expressions.compileExpression(method.getKey());
        } else if (method.getKey() instanceof Identifier) {
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(methodName));
        } else {
            // For Literal keys (numeric, null, string), compile the expression directly
            // to preserve the correct type (e.g., JSNumber for numeric property names)
            delegates.expressions.compileExpression(method.getKey());
        }

        compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, methodFunc);

        int methodKind;
        if (JSKeyword.GET.equals(kind)) {
            methodKind = 1;
        } else if (JSKeyword.SET.equals(kind)) {
            methodKind = 2;
        } else {
            methodKind = 0;
        }
        // Class methods are non-enumerable, so enumerable bit is not set.
        compilerContext.emitter.emitOpcodeU8(Opcode.DEFINE_METHOD_COMPUTED, methodKind);
    }

    /**
     * Emit CLOSE_LOC opcodes for variables declared in a VariableDeclaration.
     * Used at the end of for-loop iteration bodies to freeze VarRefs for per-iteration binding.
     */
    void emitCloseLocForPattern(VariableDeclaration varDecl) {
        for (VariableDeclaration.VariableDeclarator decl : varDecl.getDeclarations()) {
            emitCloseLocForPattern(decl.getId());
        }
    }

    /**
     * Emit CLOSE_LOC opcodes for variables in a pattern.
     * Handles Identifier patterns and can be extended for destructuring.
     */
    void emitCloseLocForPattern(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            Integer localIdx = compilerContext.findLocalInScopes(id.getName());
            if (localIdx != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.CLOSE_LOC, localIdx);
            }
        }
        // Destructuring patterns would need recursive handling here
    }

    void emitConditionalVarInit(String name) {
        compilerContext.emitter.emitOpcodeAtom(Opcode.PUSH_ATOM_VALUE, name);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_VAR, "globalThis");
        compilerContext.emitter.emitOpcode(Opcode.IN);
        int skipJump = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, name);
        compilerContext.emitter.patchJump(skipJump, compilerContext.emitter.currentOffset());
    }

    void emitCurrentScopeUsingDisposal() {
        emitScopeUsingDisposal(compilerContext.currentScope());
    }

    void emitDefaultParameterInit(
            BytecodeCompiler functionCompiler,
            List<Pattern> params,
            List<Expression> defaults,
            RestParameter restParameter,
            List<Integer> parameterSlotIndexes) {
        if (defaults == null || defaults.isEmpty()) {
            return;
        }
        if (parameterSlotIndexes == null || parameterSlotIndexes.size() < defaults.size()) {
            throw new JSCompilerException("Parameter slot indexes are not aligned with default parameters");
        }

        boolean hasNonSimpleParameters = CompilerContext.hasNonSimpleParameters(params, defaults, restParameter);
        Set<String> originalTdzLocals = new HashSet<>(functionCompiler.context().tdzLocals);
        List<Set<String>> parameterBoundNames = new ArrayList<>(params.size());
        Set<String> pendingParameterTdzNames = new HashSet<>();
        for (Pattern pattern : params) {
            Set<String> parameterNames = new HashSet<>(CompilerContext.extractBoundNames(pattern));
            parameterBoundNames.add(parameterNames);
            pendingParameterTdzNames.addAll(parameterNames);
        }

        if (hasNonSimpleParameters) {
            for (int i = 0; i < defaults.size(); i++) {
                int parameterSlotIndex = parameterSlotIndexes.get(i);
                functionCompiler.context().emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, parameterSlotIndex);
            }
        }

        for (int i = 0; i < defaults.size(); i++) {
            Expression defaultExpr = defaults.get(i);
            if (hasNonSimpleParameters) {
                functionCompiler.context().tdzLocals.clear();
                functionCompiler.context().tdzLocals.addAll(originalTdzLocals);
                functionCompiler.context().tdzLocals.addAll(pendingParameterTdzNames);
            }
            if (!hasNonSimpleParameters && defaultExpr == null) {
                continue;
            }
            // GET_ARG idx - push the argument value onto the stack
            functionCompiler.context().emitter.emitOpcodeU16(Opcode.GET_ARG, i);
            if (defaultExpr != null) {
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
            }
            // PUT_LOCAL idx - store into the local variable slot
            int parameterSlotIndex = parameterSlotIndexes.get(i);
            functionCompiler.context().emitter.emitOpcodeU16(Opcode.PUT_LOC, parameterSlotIndex);
            if (hasNonSimpleParameters) {
                pendingParameterTdzNames.removeAll(parameterBoundNames.get(i));
            }
        }
        if (hasNonSimpleParameters) {
            functionCompiler.context().tdzLocals.clear();
            functionCompiler.context().tdzLocals.addAll(originalTdzLocals);
        }
    }

    void emitGetSuperValue(MemberExpression memberExpr, boolean keepReceiverForCall) {
        // Stack start: []
        // Push current this as receiver for super property resolution.
        compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
        if (keepReceiverForCall) {
            // Keep one copy of receiver for the eventual CALL/APPLY thisArg.
            compilerContext.emitter.emitOpcode(Opcode.DUP);
        }
        // Resolve super base: [[HomeObject]].[[Prototype]]
        compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
        compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
        compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
        emitSuperPropertyKey(memberExpr);
        compilerContext.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
    }

    /**
     * Emit ITERATOR_CLOSE for any for-of loops between the current position and the target
     * loop context. This is needed when labeled break/continue crosses for-of loop boundaries,
     * to properly close inner iterators whose cleanup code would otherwise be skipped.
     * Following QuickJS close_scopes pattern for iterator cleanup.
     */
    void emitIteratorCloseForLoopsUntil(LoopContext target) {
        for (LoopContext loopCtx : compilerContext.loopStack) {
            if (loopCtx == target) {
                break;
            }
            if (loopCtx.hasIterator) {
                compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
            }
        }
    }

    void emitMethodCallOnLocalObject(int localIndex, String methodName, int argCount) {
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, localIndex);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, methodName);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        compilerContext.emitter.emitOpcodeU16(Opcode.CALL, argCount);
    }

    void emitMethodCallWithSingleArgOnLocalObject(int localIndex, String methodName) {
        int argLocalIndex = compilerContext.currentScope().declareLocal("$using_arg_" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, argLocalIndex);

        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, localIndex);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, methodName);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, argLocalIndex);
        compilerContext.emitter.emitOpcodeU16(Opcode.CALL, 1);
    }

    void emitNonComputedPublicFieldKey(Expression key) {
        if (key instanceof Identifier id) {
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(id.getName()));
            return;
        }
        if (key instanceof Literal literal) {
            Object value = literal.getValue();
            if (value == null) {
                compilerContext.emitter.emitOpcode(Opcode.NULL);
            } else if (value instanceof Boolean bool) {
                compilerContext.emitter.emitOpcode(bool ? Opcode.PUSH_TRUE : Opcode.PUSH_FALSE);
            } else if (value instanceof BigInteger bigInt) {
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSBigInt(bigInt));
            } else if (value instanceof Number num) {
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, JSNumber.of(num.doubleValue()));
            } else if (value instanceof String str) {
                compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(str));
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
            compilerContext.emitter.emitOpcode(Opcode.AWAIT);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        } else {
            emitMethodCallOnLocalObject(usingStackLocalIndex, "dispose", 0);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
    }

    void emitSuperPropertyKey(MemberExpression memberExpr) {
        if (memberExpr.isComputed()) {
            delegates.expressions.compileExpression(memberExpr.getProperty());
        } else if (memberExpr.getProperty() instanceof Identifier propId) {
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(propId.getName()));
        } else if (memberExpr.getProperty() instanceof PrivateIdentifier) {
            throw new JSCompilerException("super private fields are not supported");
        } else {
            delegates.expressions.compileExpression(memberExpr.getProperty());
        }
    }

    void emitUsingDisposalsForScopeDepthGreaterThan(int targetScopeDepth) {
        for (CompilerScope scope : compilerContext.scopes) {
            if (scope.getScopeDepth() > targetScopeDepth) {
                emitScopeUsingDisposal(scope);
            }
        }
    }

    int ensureUsingStackLocal(boolean asyncUsingDeclaration) {
        CompilerScope scope = compilerContext.currentScope();
        Integer existingLocalIndex = scope.getUsingStackLocalIndex();
        if (existingLocalIndex != null) {
            if (asyncUsingDeclaration && !scope.isUsingStackAsync()) {
                throw new JSCompilerException("Cannot mix await using with sync using stack in the same scope");
            }
            return existingLocalIndex;
        }

        boolean useAsyncStack = asyncUsingDeclaration || compilerContext.isInAsyncFunction;
        String constructorName = useAsyncStack ? JSAsyncDisposableStack.NAME : JSDisposableStack.NAME;
        int stackLocalIndex = scope.declareLocal("$using_stack_" + scope.getScopeDepth() + "_" + compilerContext.emitter.currentOffset());

        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_VAR, constructorName);
        compilerContext.emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, 0);
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, stackLocalIndex);

        scope.setUsingStackLocal(stackLocalIndex, useAsyncStack);
        return stackLocalIndex;
    }
}
