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
import com.caoccao.qjs4j.core.disposable.JSAsyncDisposableStack;
import com.caoccao.qjs4j.core.disposable.JSDisposableStack;
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

    int beginUsingScope(boolean hasUsing) {
        if (!hasUsing) {
            return -1;
        }
        int handler = compilerContext.emitter.emitJump(Opcode.CATCH);
        compilerContext.scopeManager.currentScope().setUsingCatchJumpPosition(handler);
        return handler;
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
            compilerContext.emitter.patchJumpToHere(optionalChainJump);
        }
        if (shortCircuitTail != null) {
            shortCircuitTail.run();
            compilerContext.emitter.patchJumpToHere(jumpOverShortCircuitTail);
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
                compilerContext.emitter.emitPutLocal(funcScopeLocal);
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
                compilerContext.emitter.emitArrayElement();
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
        compilerContext.emitter.emitGetLocal(iteratorLocal);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        // Stack: iter undefined undefined undefined -> iter undefined undefined result noReturnMethod
        compilerContext.emitter.emitOpcodeU8(Opcode.ITERATOR_CALL, 2);
        int jumpWithoutReturnMethod = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        compilerContext.emitter.emitOpcode(Opcode.AWAIT);
        compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CHECK_OBJECT);
        compilerContext.emitter.patchJumpToHere(jumpWithoutReturnMethod);
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
            compilerContext.emitter.emitPushConstant(new JSString(methodName));
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
        compilerContext.emitter.patchJumpToHere(skipJump);
    }

    void emitConstAssignmentError(String name) {
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitter.emitOpcodeAtom(Opcode.THROW_ERROR, name);
        compilerContext.emitter.emitU8(0);
    }

    void emitConstAssignmentErrorForCaptured(String name, int capturedIndex) {
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_VAR_REF_CHECK, capturedIndex);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitHelpers.emitConstAssignmentError(name);
    }

    void emitConstAssignmentErrorForLocal(String name, int localIndex) {
        compilerContext.emitter.emitOpcodeU16(Opcode.GET_LOC_CHECK, localIndex);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.emitHelpers.emitConstAssignmentError(name);
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

        if (!functionParams.hasNonSimpleParameters()) {
            return;
        }
        CompilerContext functionContext = functionCompiler.context();
        Set<String> originalTdzLocals = new HashSet<>(functionContext.tdzLocals);
        List<Set<String>> parameterBoundNames = new ArrayList<>(params.size());
        Set<String> pendingParameterTdzNames = new HashSet<>();
        for (Pattern pattern : params) {
            Set<String> parameterNames = new HashSet<>(pattern.getBoundNames());
            parameterBoundNames.add(parameterNames);
            pendingParameterTdzNames.addAll(parameterNames);
        }

        for (int i = 0; i < defaults.size(); i++) {
            int parameterSlotIndex = parameterSlotIndexes.get(i);
            functionContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, parameterSlotIndex);
        }

        for (int i = 0; i < defaults.size(); i++) {
            Expression defaultExpr = defaults.get(i);
            functionContext.tdzLocals.clear();
            functionContext.tdzLocals.addAll(originalTdzLocals);
            functionContext.tdzLocals.addAll(pendingParameterTdzNames);
            // GET_ARG idx - push the argument value onto the stack
            functionContext.emitter.emitOpcodeU16(Opcode.GET_ARG, i);
            if (defaultExpr != null) {
                // DUP - duplicate for the comparison
                functionContext.emitter.emitOpcode(Opcode.DUP);
                // UNDEFINED - push undefined for comparison
                functionContext.emitter.emitOpcode(Opcode.UNDEFINED);
                // STRICT_EQ - check if arg === undefined
                functionContext.emitter.emitOpcode(Opcode.STRICT_EQ);
                // IF_FALSE label - if arg !== undefined, skip default
                int skipLabel = functionContext.emitter.emitJump(Opcode.IF_FALSE);
                // DROP - drop the duplicated arg value (it was undefined)
                functionContext.emitter.emitOpcode(Opcode.DROP);
                // Compile the default expression
                functionContext.expressionCompiler.compile(defaultExpr);
                if (params.get(i) instanceof Identifier parameterIdentifier && defaultExpr.isAnonymousFunction()) {
                    functionContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, parameterIdentifier.getName());
                }
                // DUP - duplicate for PUT_ARG
                functionContext.emitter.emitOpcode(Opcode.DUP);
                // PUT_ARG idx - store back into the argument slot
                functionContext.emitter.emitOpcodeU16(Opcode.PUT_ARG, i);
                // label: - skip target (value is on stack, either original arg or default)
                functionContext.emitter.patchJumpToHere(skipLabel);
            }
            // PUT_LOCAL idx - store into the local variable slot
            int parameterSlotIndex = parameterSlotIndexes.get(i);
            functionContext.emitter.emitPutLocal(parameterSlotIndex);
            pendingParameterTdzNames.removeAll(parameterBoundNames.get(i));
        }
        functionContext.tdzLocals.clear();
        functionContext.tdzLocals.addAll(originalTdzLocals);
    }

    /** Stack: value -> value, evaluating the initializer only for undefined. */
    void emitDefaultValue(String inferredName, Expression initializer) {
        compilerContext.emitter.emitOpcode(Opcode.DUP);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED);
        int skip = compilerContext.emitter.emitJump(Opcode.IF_FALSE);
        compilerContext.emitter.emitOpcode(Opcode.DROP);
        compilerContext.expressionCompiler.compile(initializer);
        if (inferredName != null && initializer.isAnonymousFunction()) {
            compilerContext.emitter.emitOpcodeAtom(Opcode.SET_NAME, inferredName);
        }
        compilerContext.emitter.patchJumpToHere(skip);
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
            compilerContext.emitter.emitGetLocal(blockLocalIndex);
            emitAnnexBVarStore(functionName);
        }
    }

    void emitDropIteratorState() {
        compilerContext.emitter.emitOpcode(Opcode.DROP); // catch offset
        compilerContext.emitter.emitOpcode(Opcode.DROP); // next method
        compilerContext.emitter.emitOpcode(Opcode.DROP); // iterator
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
            compilerContext.emitter.emitSuperBase();
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
        } else {
            // For non-computed super.prop, the key is a static identifier with no side effects.
            // Resolve super base first, then push key.
            compilerContext.emitter.emitSuperBase();
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

    void emitIteratorCloseUnlessDone(int doneLocal) {
        compilerContext.emitter.emitGetLocal(doneLocal);
        int exhausted = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        compilerContext.emitter.emitOpcode(Opcode.ITERATOR_CLOSE);
        int end = compilerContext.emitter.emitJump(Opcode.GOTO);
        compilerContext.emitter.patchJumpToHere(exhausted);
        emitDropIteratorState();
        compilerContext.emitter.patchJumpToHere(end);
    }

    void emitIteratorRestArray(int targetDepth) {
        compilerContext.emitter.emitOpcodeU16(Opcode.ARRAY_FROM, 0);
        compilerContext.emitter.emitOpcode(Opcode.PUSH_I32);
        compilerContext.emitter.emitI32(0);

        // Stack above the iterator: pre-evaluated target, result array, next index.
        int loopStart = compilerContext.emitter.currentOffset();
        compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, 2 + targetDepth);
        int doneJump = compilerContext.emitter.emitJump(Opcode.IF_TRUE);
        compilerContext.emitter.emitArrayElement();
        compilerContext.emitter.emitOpcode(Opcode.GOTO);
        compilerContext.emitter.emitU32(loopStart - (compilerContext.emitter.currentOffset() + 4));
        compilerContext.emitter.patchJumpToHere(doneJump);
        compilerContext.emitter.emitOpcode(Opcode.DROP); // final value
        compilerContext.emitter.emitOpcode(Opcode.DROP); // next index
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
        compilerContext.emitter.emitGetLocal(localIndex);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, methodName);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        compilerContext.emitter.emitOpcodeU16(Opcode.CALL, argCount);
    }

    void emitMethodCallWithSingleArgOnLocalObject(int localIndex, String methodName) {
        int argLocalIndex = compilerContext.storeTemporary("#using_arg_");

        compilerContext.emitter.emitGetLocal(localIndex);
        compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, methodName);
        compilerContext.emitter.emitOpcode(Opcode.SWAP);
        compilerContext.emitter.emitGetLocal(argLocalIndex);
        compilerContext.emitter.emitOpcodeU16(Opcode.CALL, 1);
    }

    void emitNextDestructuringValue(int depth, int doneLocal) {
        compilerContext.emitter.emitOpcodeU8(Opcode.FOR_OF_NEXT, depth);
        if (doneLocal >= 0) {
            compilerContext.emitter.emitOpcode(Opcode.DUP);
            compilerContext.emitter.emitPutLocal(doneLocal);
        }
        compilerContext.emitter.emitOpcode(Opcode.DROP);
    }

    void emitNonComputedPublicFieldKey(Expression key) {
        if (key instanceof Identifier id) {
            compilerContext.emitter.emitPushConstant(new JSString(id.getName()));
            return;
        }
        if (key instanceof Literal literal) {
            Object value = literal.getValue();
            if (value == null) {
                compilerContext.emitter.emitOpcode(Opcode.NULL);
            } else if (value instanceof Boolean bool) {
                compilerContext.emitter.emitOpcode(bool ? Opcode.PUSH_TRUE : Opcode.PUSH_FALSE);
            } else if (value instanceof BigInteger bigInt) {
                compilerContext.emitter.emitPushConstant(new JSBigInt(bigInt));
            } else if (value instanceof Number num) {
                compilerContext.emitter.emitPushConstant(JSNumber.of(num.doubleValue()));
            } else if (value instanceof String str) {
                compilerContext.emitter.emitPushConstant(new JSString(str));
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
        compilerContext.emitter.patchJumpToHere(jumpToNext);
    }

    void emitPrivateSymbol(PrivateIdentifier privateIdentifier) {
        JSSymbol symbol = compilerContext.privateSymbol(privateIdentifier);
        if (symbol == null) {
            throw new JSCompilerException("undefined private field '#" + privateIdentifier.getName() + "'",
                    privateIdentifier);
        }

        compilerContext.emitter.emitPushConstant(symbol);
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
        int exceptionLocalIndex = compilerContext.storeTemporary("#using_exception_");

        // Check if the using stack was actually initialized at runtime.
        // If the exception occurred before the `using` declaration executed,
        // the stack local is still uninitialized - just re-throw.
        compilerContext.emitter.emitGetLocal(usingStackLocalIndex);
        compilerContext.emitter.emitOpcode(Opcode.IS_UNDEFINED_OR_NULL);
        int skipDisposeJump = compilerContext.emitter.emitJump(Opcode.IF_TRUE);

        if (scope.isUsingStackAsync()) {
            // Call disposeAsync(caughtException)
            compilerContext.emitter.emitGetLocal(usingStackLocalIndex);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "disposeAsync");
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            compilerContext.emitter.emitGetLocal(exceptionLocalIndex);
            compilerContext.emitter.emitOpcodeU16(Opcode.CALL, 1);
            compilerContext.emitter.emitOpcode(Opcode.AWAIT);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        } else {
            // Call dispose(caughtException)
            compilerContext.emitter.emitGetLocal(usingStackLocalIndex);
            compilerContext.emitter.emitOpcodeAtom(Opcode.GET_FIELD2, "dispose");
            compilerContext.emitter.emitOpcode(Opcode.SWAP);
            compilerContext.emitter.emitGetLocal(exceptionLocalIndex);
            compilerContext.emitter.emitOpcodeU16(Opcode.CALL, 1);
            compilerContext.emitter.emitOpcode(Opcode.DROP);
        }
        // After dispose/disposeAsync, the pending exception is set (either original or composed SuppressedError)
        // The VM will detect it and unwind automatically
        int skipRethrowJump = compilerContext.emitter.emitJump(Opcode.GOTO);

        // Stack local was uninitialized - just re-throw the original exception
        compilerContext.emitter.patchJumpToHere(skipDisposeJump);
        compilerContext.emitter.emitGetLocal(exceptionLocalIndex);
        compilerContext.emitter.emitOpcode(Opcode.THROW);

        compilerContext.emitter.patchJumpToHere(skipRethrowJump);
    }

    void emitSuperPropertyKey(MemberExpression memberExpr) {
        if (memberExpr.isComputed()) {
            compilerContext.expressionCompiler.compile(memberExpr.getProperty());
        } else if (memberExpr.getProperty() instanceof Identifier propId) {
            compilerContext.emitter.emitPushConstant(new JSString(propId.getName()));
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
        compilerContext.emitter.emitSuperBase();
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
        compilerContext.emitter.emitPutLocal(stackLocalIndex);

        scope.setUsingStackLocal(stackLocalIndex, useAsyncStack);
        return stackLocalIndex;
    }

    /** Dispose on both normal and abrupt completion, preserving a block result when present. */
    void finishUsingScope(int handler, boolean hasCompletionValue) {
        if (handler < 0) {
            emitCurrentScopeUsingDisposal();
            return;
        }
        BytecodeEmitter emitter = compilerContext.emitter;
        if (!hasCompletionValue) {
            emitter.emitOpcode(Opcode.UNDEFINED);
        }
        emitter.emitOpcode(Opcode.NIP_CATCH);
        if (!hasCompletionValue) {
            emitter.emitOpcode(Opcode.DROP);
        }
        emitCurrentScopeUsingDisposal();
        int end = emitter.emitJump(Opcode.GOTO);
        emitter.patchJumpToHere(handler);
        emitScopeUsingDisposalWithException(compilerContext.scopeManager.currentScope());
        emitter.patchJumpToHere(end);
    }

    int startArrayDestructuring(String doneLocalPrefix, boolean hasRest) {
        compilerContext.emitter.emitOpcode(Opcode.FOR_OF_START);
        if (hasRest) {
            return -1;
        }
        int doneLocal = compilerContext.scopeManager.currentScope()
                .declareLocal(doneLocalPrefix + compilerContext.emitter.currentOffset());
        compilerContext.emitter.emitOpcode(Opcode.PUSH_FALSE);
        compilerContext.emitter.emitPutLocal(doneLocal);
        return doneLocal;
    }

    static boolean hasUsingDeclarations(java.util.List<Statement> statements) {
        for (Statement stmt : statements) {
            if (stmt instanceof VariableDeclaration vd && vd.getKind().isUsing()) {
                return true;
            }
        }
        return false;
    }
}
