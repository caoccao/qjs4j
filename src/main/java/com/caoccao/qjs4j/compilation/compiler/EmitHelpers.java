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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Delegate compiler for bytecode emission helper methods. Provides utility methods for emitting common bytecode
 * patterns such as iterator close, captured values, method calls, and using disposal.
 */
final class EmitHelpers {
    private final CompilerContext compilerContext;

    EmitHelpers(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
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
    void compileOptionalChain(Runnable body, Runnable shortCircuitTail) {
        List<Integer> savedOptionalChainJumps = compilerContext.optionalChainJumps;
        List<Integer> optionalChainJumps = new ArrayList<>();
        compilerContext.optionalChainJumps = optionalChainJumps;
        try {
            body.run();
        } finally {
            compilerContext.optionalChainJumps = savedOptionalChainJumps;
        }
        int jumpOverShortCircuitTail = shortCircuitTail != null ? compilerContext.emitter.emitJump(Opcode.GOTO) : -1;
        for (int optionalChainJump : optionalChainJumps) {
            compilerContext.emitter.patchJump(optionalChainJump, compilerContext.emitter.currentOffset());
        }
        if (shortCircuitTail != null) {
            shortCircuitTail.run();
            compilerContext.emitter.patchJump(jumpOverShortCircuitTail, compilerContext.emitter.currentOffset());
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
    void compileOptionalChainLinkTarget(Expression link, Expression target) {
        if (link.isPartOfOptionalChain() && target.isPartOfOptionalChain()) {
            compilerContext.optionalChainLink = target;
        }
        compilerContext.expressionCompiler.compile(target);
    }

    /**
     * Whether a member or call expression about to be compiled is the next link of the optional chain being compiled.
     * Clears the mark, so nothing compiled inside the node can mistake itself for that link.
     *
     * @param expression
     *            the member or call expression
     * @return true when it joins the chain being compiled
     */
    boolean consumeOptionalChainLink(Expression expression) {
        boolean joinsOptionalChain = compilerContext.optionalChainLink == expression;
        compilerContext.optionalChainLink = null;
        return joinsOptionalChain;
    }

    void emitAbruptCompletionIteratorClose() {
        for (LoopContext loopContext : compilerContext.loopManager) {
            if (loopContext.hasIterator) {
                emitLoopIteratorClose(loopContext);
            }
        }
    }

    void emitActiveFinallyGosubs() {
        Iterator<List<Integer>> gosubPatchIterator = compilerContext.activeFinallyGosubPatches.iterator();
        Iterator<Integer> nipCatchCountIterator = compilerContext.activeFinallyNipCatchCounts.iterator();
        while (gosubPatchIterator.hasNext() && nipCatchCountIterator.hasNext()) {
            List<Integer> gosubPatches = gosubPatchIterator.next();
            int nipCatchCount = nipCatchCountIterator.next();
            compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
            for (int i = 0; i < nipCatchCount; i++) {
                compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
            }
            int gosubPosition = compilerContext.emitter.emitJump(Opcode.GOSUB);
            gosubPatches.add(gosubPosition);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
    }

    /**
     * Emit the Annex B.3.3 var-scope store for a function declaration. In global scope, uses PUT_VAR (global object
     * property). In function scope, uses PUT_LOCAL to the function-scope local (bypassing the block-scoped lexical
     * binding).
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

        boolean hasSpread = arguments.stream().anyMatch(arg -> arg instanceof SpreadElement);

        // Always use index-based pattern (QuickJS style)
        compilerContext.emitter.emitOpcodeU32(Opcode.PUSH_I32, 0);
        for (Expression arg : arguments) {
            if (arg instanceof SpreadElement spreadElement) {
                compilerContext.expressionCompiler.compile(spreadElement.getArgument());
                compilerContext.emitter.emitSourcePosition(spreadElement.getArgument().getLocation());
                compilerContext.emitter.emitOpcode(Opcode.APPEND);
            } else {
                compilerContext.expressionCompiler.compile(arg);
                compilerContext.emitter.emitOpcode(Opcode.DEFINE_ARRAY_EL);
                compilerContext.emitter.emitOpcode(Opcode.INC);
            }
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
    }

    /**
     * AsyncIteratorClose for a {@code for await} loop left by a {@code break}, a {@code continue} to an outer loop or a
     * {@code return}: call the iterator's {@code return()}, await the result and require an object. The loop used to
     * call {@code return()} without awaiting it, as for a sync iterator, so the loop completed ticks before V8's.
     * <p>
     * Runs after {@code ITERATOR_CLOSE} has taken the loop's iterator off the stack, so a {@code return()} that throws
     * is not called a second time by exception unwinding.
     * <p>
     * Stack: unchanged
     */
    void emitAsyncIteratorReturn(int iteratorLocal) {
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, iteratorLocal);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        // Stack: iter undefined undefined undefined -> iter undefined undefined result noReturnMethod
        compilerContext.emitter.emitOpcodeU8(Opcode.ITERATOR_CALL, 2);
        int jumpWithoutReturnMethod = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        compilerContext.emitter.emitOpcode(Opcode.AWAIT);
        compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CHECK_OBJECT);
        compilerContext.emitter.patchJump(jumpWithoutReturnMethod, compilerContext.emitter.currentOffset());
        for (int i = 0; i < 4; i++) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
    }

    /**
     * Build capture source info and set it on the template function. Instead of emitting GET_LOCAL/GET_VAR_REF opcodes
     * to push values onto the stack, we store the capture source information in the template function so FCLOSURE can
     * create VarRef objects directly from the parent frame at runtime. This enables reference-based closure capture
     * (mutations visible across closures).
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
     * Emit the correct opcode sequence for a class method definition. Class methods always use DEFINE_METHOD_COMPUTED
     * so attributes and DefinePropertyOrThrow semantics match ECMAScript class semantics. Stack before: ... obj Stack
     * after: ... obj (method added to obj)
     */
    void emitClassMethodDefinition(MethodDefinition method, JSBytecodeFunction methodFunc, String methodName) {
        String kind = method.getKind();
        boolean isComputedKey = method.isComputed() && !(method.getKey() instanceof Literal);
        // Class definitions must create fresh function objects per evaluation.

        if (isComputedKey) {
            // Per ES2024 10.2.1, all parts of a class are strict mode code.
            if (compilerContext.inClassBody) {
                compilerContext.functionExpressionCompiler.emitStrictClassBodyExpression(method.getKey());
            } else {
                compilerContext.expressionCompiler.compile(method.getKey());
            }
        } else if (method.getKey() instanceof Identifier) {
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(methodName));
        } else {
            // For Literal keys (numeric, null, string), compile the expression directly
            // to preserve the correct type (e.g., JSNumber for numeric property names)
            compilerContext.expressionCompiler.compile(method.getKey());
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
     * Emit CLOSE_LOC opcodes for variables in a pattern. Recursively handles Identifier, ArrayPattern, ObjectPattern,
     * AssignmentPattern, and RestElement.
     */
    void emitCloseLocForPattern(Pattern pattern) {
        if (pattern instanceof Identifier id) {
            Integer localIdx = compilerContext.scopeManager.findLocalInScopes(id.getName());
            if (localIdx != null) {
                compilerContext.emitter.emitOpcodeU16(Opcode.CLOSE_LOC, localIdx);
            }
        } else if (pattern instanceof ArrayPattern arrayPattern) {
            for (Pattern element : arrayPattern.getElements()) {
                if (element != null) {
                    emitCloseLocForPattern(element);
                }
            }
        } else if (pattern instanceof ObjectPattern objectPattern) {
            for (ObjectPatternProperty property : objectPattern.getProperties()) {
                if (property.getValue() != null) {
                    emitCloseLocForPattern(property.getValue());
                }
            }
            if (objectPattern.getRestElement() != null) {
                emitCloseLocForPattern(objectPattern.getRestElement());
            }
        } else if (pattern instanceof AssignmentPattern assignmentPattern) {
            emitCloseLocForPattern(assignmentPattern.getLeft());
        } else if (pattern instanceof RestElement restElement) {
            if (restElement.getArgument() != null) {
                emitCloseLocForPattern(restElement.getArgument());
            }
        }
    }

    /**
     * Emit CLOSE_LOC opcodes for variables declared in a VariableDeclaration. Used at the end of for-loop iteration
     * bodies to freeze VarRefs for per-iteration binding.
     */
    void emitCloseLocForPattern(VariableDeclaration varDecl) {
        for (VariableDeclarator decl : varDecl.getDeclarations()) {
            emitCloseLocForPattern(decl.getId());
        }
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
        emitScopeUsingDisposal(compilerContext.scopeManager.currentScope());
    }

    void emitDefaultParameterInit(BytecodeCompiler functionCompiler, FunctionParams functionParams,
            List<Integer> parameterSlotIndexes, ASTNode ast) {
        List<Pattern> params = functionParams.params();
        List<Expression> defaults = functionParams.defaults();
        if (defaults == null || defaults.isEmpty()) {
            return;
        }
        if (parameterSlotIndexes == null || parameterSlotIndexes.size() < defaults.size()) {
            throw new JSCompilerException("Parameter slot indexes are not aligned with default parameters", ast);
        }

        boolean hasNonSimpleParameters = functionParams.hasNonSimpleParameters();
        Set<String> originalTdzLocals = new HashSet<>(functionCompiler.context().tdzLocals);
        List<Set<String>> parameterBoundNames = new ArrayList<>(params.size());
        Set<String> pendingParameterTdzNames = new HashSet<>();
        for (Pattern pattern : params) {
            Set<String> parameterNames = new HashSet<>(pattern.getBoundNames());
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
                functionCompiler.context().expressionCompiler.compile(defaultExpr);
                if (params.get(i) instanceof Identifier parameterIdentifier && defaultExpr.isAnonymousFunction()) {
                    functionCompiler.context().emitter.emitOpcodeAtom(Opcode.SET_NAME, parameterIdentifier.getName());
                }
                // DUP - duplicate for PUT_ARG
                functionCompiler.context().emitter.emitOpcode(Opcode.DUP);
                // PUT_ARG idx - store back into the argument slot
                functionCompiler.context().emitter.emitOpcodeU16(Opcode.PUT_ARG, i);
                // label: - skip target (value is on stack, either original arg or default)
                functionCompiler.context().emitter.patchJump(skipLabel,
                        functionCompiler.context().emitter.currentOffset());
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

    /**
     * Emit the Annex B.3.3 var store at the function declaration's source position. This copies the block-scoped
     * function value to the enclosing var-scope binding. Per spec, this happens when the FunctionDeclaration is
     * evaluated, not when hoisted.
     */
    void emitDeferredAnnexBVarStore(FunctionDeclaration functionDeclaration) {
        if (functionDeclaration.getId() == null) {
            return;
        }
        String functionName = functionDeclaration.getId().getName();
        boolean isAnnexB = compilerContext.annexBFunctionNames.contains(functionName) && !functionDeclaration.isAsync()
                && !functionDeclaration.isGenerator()
                && !compilerContext.scopeManager.hasEnclosingBlockScopeLocal(functionName);
        if (!isAnnexB) {
            return;
        }
        // Load the function from its block-scope local, then store in var scope
        Integer blockLocalIndex = compilerContext.scopeManager.findLocalInScopes(functionName);
        if (blockLocalIndex != null) {
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, blockLocalIndex);
            emitAnnexBVarStore(functionName);
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
        if (memberExpr.isComputed()) {
            // Per ES spec 13.4.1: for super[expr], the key expression is evaluated
            // BEFORE GetSuperBase(). This ensures side effects in the key expression
            // (e.g. modifying the prototype chain) are visible when the super base
            // is resolved. Stack: [this] -> [this, key, superBase] -> SWAP -> [this, superBase, key]
            emitSuperPropertyKey(memberExpr);
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
            compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        } else {
            // For non-computed super.prop, the key is a static identifier with no side effects.
            // Resolve super base first, then push key.
            compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
            compilerContext.emitter.emitU8(4); // SPECIAL_OBJECT_HOME_OBJECT
            compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
            emitSuperPropertyKey(memberExpr);
        }
        compilerContext.emitter.emitOpcode(Opcode.GET_SUPER_VALUE);
    }

    /**
     * Emit ITERATOR_CLOSE for any for-of loops between the current position and the target loop context. This is needed
     * when labeled break/continue crosses for-of loop boundaries, to properly close inner iterators whose cleanup code
     * would otherwise be skipped. Following QuickJS close_scopes pattern for iterator cleanup.
     */
    void emitIteratorCloseForLoopsUntil(LoopContext target) {
        for (LoopContext loopCtx : compilerContext.loopManager) {
            if (loopCtx == target) {
                break;
            }
            if (loopCtx.hasIterator) {
                emitLoopIteratorClose(loopCtx);
            }
        }
    }

    /**
     * Close a loop's iterator: {@code ITERATOR_CLOSE}, followed for a {@code for await} loop by the awaited
     * {@code return()}.
     */
    void emitLoopIteratorClose(LoopContext loopContext) {
        compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
        if (loopContext.asyncIteratorLocal >= 0) {
            emitAsyncIteratorReturn(loopContext.asyncIteratorLocal);
        }
    }

    /**
     * Record the position a failing access of this member reports in a stack trace, as V8 does: the property name of a
     * {@code .name} access, the {@code [} of a computed one.
     *
     * @param memberExpr
     *            the member expression
     */
    void emitMemberSourcePosition(MemberExpression memberExpr) {
        compilerContext.emitter.emitSourcePosition(
                memberExpr.isComputed() ? memberExpr.getLocation() : memberExpr.getProperty().getLocation());
    }

    void emitMethodCallOnLocalObject(int localIndex, String methodName, int argCount) {
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, localIndex);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, methodName);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        compilerContext.emitter.emitOpcodeU16(Opcode.CALL, argCount);
    }

    void emitMethodCallWithSingleArgOnLocalObject(int localIndex, String methodName) {
        int argLocalIndex = compilerContext.scopeManager.currentScope()
                .declareLocal("#using_arg_" + compilerContext.emitter.currentOffset());
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
                throw new JSCompilerException("Unsupported field key literal type: " + value.getClass(), key);
            }
            return;
        }
        throw new JSCompilerException("Invalid non-computed field key", key);
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
    void emitOptionalChainTest(int dropCount) {
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToNext = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        for (int i = 0; i < dropCount; i++) {
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        compilerContext.optionalChainJumps.add(compilerContext.emitter.emitJump(Opcode.GOTO));
        compilerContext.emitter.patchJump(jumpToNext, compilerContext.emitter.currentOffset());
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

    /**
     * Emit disposal with a caught exception passed as an argument. Stack on entry: [caught_exception] The caught
     * exception is passed to dispose/disposeAsync so it can compose SuppressedErrors.
     */
    void emitScopeUsingDisposalWithException(CompilerScope scope) {
        Integer usingStackLocalIndex = scope.getUsingStackLocalIndex();
        if (usingStackLocalIndex == null) {
            // No using stack - just re-throw the caught exception
            compilerContext.emitter.emitOpcode(Opcode.THROW);
            return;
        }

        // Stack: [caught_exception]
        // Save exception to a temp local so we can pass it as an argument
        int exceptionLocalIndex = compilerContext.scopeManager.currentScope()
                .declareLocal("#using_exception_" + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, exceptionLocalIndex);

        // Check if the using stack was actually initialized at runtime.
        // If the exception occurred before the `using` declaration executed,
        // the stack local is still uninitialized - just re-throw.
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, usingStackLocalIndex);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int skipDisposeJump = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        if (scope.isUsingStackAsync()) {
            // Call disposeAsync(caughtException)
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, usingStackLocalIndex);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "disposeAsync");
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, exceptionLocalIndex);
            compilerContext.emitter.emitOpcodeU16(Opcode.CALL, 1);
            compilerContext.emitter.emitOpcode(Opcode.AWAIT);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        } else {
            // Call dispose(caughtException)
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, usingStackLocalIndex);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "dispose");
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, exceptionLocalIndex);
            compilerContext.emitter.emitOpcodeU16(Opcode.CALL, 1);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
        // After dispose/disposeAsync, the pending exception is set (either original or composed SuppressedError)
        // The VM will detect it and unwind automatically
        int skipRethrowJump = compilerContext.emitter.emitJump(Opcode.GOTO);

        // Stack local was uninitialized - just re-throw the original exception
        compilerContext.emitter.patchJump(skipDisposeJump, compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC, exceptionLocalIndex);
        compilerContext.emitter.emitOpcode(Opcode.THROW);

        compilerContext.emitter.patchJump(skipRethrowJump, compilerContext.emitter.currentOffset());
    }

    void emitSuperPropertyKey(MemberExpression memberExpr) {
        if (memberExpr.isComputed()) {
            compilerContext.expressionCompiler.compile(memberExpr.getProperty());
        } else if (memberExpr.getProperty() instanceof Identifier propId) {
            compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString(propId.getName()));
        } else if (memberExpr.getProperty() instanceof PrivateIdentifier privateIdentifier) {
            throw new JSCompilerException("super private fields are not supported", privateIdentifier);
        } else {
            compilerContext.expressionCompiler.compile(memberExpr.getProperty());
        }
    }

    /** Stack: ... -> ... this superBase propertyKey. Computed keys are evaluated before GetSuperBase. */
    void emitSuperPropertyReference(MemberExpression member) {
        compilerContext.emitter.emitOpcode(Opcode.PUSH_THIS);
        if (member.isComputed()) {
            emitSuperPropertyKey(member);
        }
        compilerContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
        compilerContext.emitter.emitU8(4);
        compilerContext.emitter.emitOpcode(Opcode.GET_SUPER);
        if (member.isComputed()) {
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        } else {
            emitSuperPropertyKey(member);
        }
    }

    void emitUsingDisposalsForScopeDepthGreaterThan(int targetScopeDepth) {
        for (CompilerScope scope : compilerContext.scopeManager) {
            if (scope.getScopeDepth() > targetScopeDepth) {
                // If scope has a CATCH handler on the stack (from using declaration),
                // remove it before calling dispose (we're leaving via control flow)
                if (scope.getUsingCatchJumpPosition() >= 0 && scope.getUsingStackLocalIndex() != null) {
                    compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
                    compilerContext.emitter.emitOpcode(Opcode.NIP_CATCH);
                    compilerContext.emitter.emitOpcode(Opcode.DROP);
                }
                emitScopeUsingDisposal(scope);
            }
        }
    }

    int[] emitWithUnscopablesSkipJumps() {
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int jumpToResolveWithoutUnscopablesOnNullish = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.TYPEOF);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("object"));
        compilerContext.emitter.emitOpcode(Opcode.STRICT_EQ);
        int jumpToCheckBlockedWhenObject = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.TYPEOF);
        compilerContext.emitter.emitOpcodeConstant(Opcode.PUSH_CONST, new JSString("function"));
        compilerContext.emitter.emitOpcode(Opcode.STRICT_EQ);
        int jumpToResolveWithoutUnscopablesOnPrimitive = compilerContext.emitter.emitJump(Opcode.IF_FALSE);

        compilerContext.emitter.patchJump(jumpToCheckBlockedWhenObject, compilerContext.emitter.currentOffset());
        return new int[]{jumpToResolveWithoutUnscopablesOnNullish, jumpToResolveWithoutUnscopablesOnPrimitive};
    }

    int ensureUsingStackLocal(boolean asyncUsingDeclaration, ASTNode ast) {
        CompilerScope scope = compilerContext.scopeManager.currentScope();
        Integer existingLocalIndex = scope.getUsingStackLocalIndex();
        if (existingLocalIndex != null) {
            if (asyncUsingDeclaration && !scope.isUsingStackAsync()) {
                throw new JSCompilerException("Cannot mix await using with sync using stack in the same scope", ast);
            }
            return existingLocalIndex;
        }

        boolean useAsyncStack = asyncUsingDeclaration || compilerContext.isInAsyncFunction;
        String constructorName = useAsyncStack ? JSAsyncDisposableStack.NAME : JSDisposableStack.NAME;
        int stackLocalIndex = scope
                .declareLocal("#using_stack_" + scope.getScopeDepth() + "_" + compilerContext.emitter.currentOffset());

        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_VAR, constructorName);
        compilerContext.emitter.emitOpcodeU16(Opcode.CALL_CONSTRUCTOR, 0);
        compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, stackLocalIndex);

        scope.setUsingStackLocal(stackLocalIndex, useAsyncStack);
        return stackLocalIndex;
    }

    static boolean hasUsingDeclarations(java.util.List<Statement> statements) {
        for (Statement stmt : statements) {
            if (stmt instanceof VariableDeclaration vd
                    && (vd.getKind() == VariableKind.USING || vd.getKind() == VariableKind.AWAIT_USING)) {
                return true;
            }
        }
        return false;
    }
}
