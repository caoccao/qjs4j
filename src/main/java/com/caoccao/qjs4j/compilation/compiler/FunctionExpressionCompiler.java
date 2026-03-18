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
import com.caoccao.qjs4j.core.JSArguments;
import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles compilation of function expressions.
 */
final class FunctionExpressionCompiler extends AstNodeCompiler<FunctionExpression> {
    FunctionExpressionCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    /**
     * Returns true when a strict-mode wrapper function can safely be used
     * around a class body expression. Wrapper functions introduce a new
     * function boundary, which breaks yield/await semantics, so they must
     * only be used when the enclosing context is a plain (non-generator,
     * non-async) sloppy-mode function.
     */
    private boolean canUseStrictWrapper() {
        return !compilerContext.strictMode
                && !compilerContext.isInGeneratorFunction
                && !compilerContext.isInAsyncFunction;
    }

    void compile(FunctionExpression functionExpression, boolean forceNonConstructor) {
        compileFunctionExpressionInternal(functionExpression, forceNonConstructor);
    }

    @Override
    void compile(FunctionExpression functionExpression) {
        compileFunctionExpressionInternal(functionExpression, false);
    }

    private void compileFunctionExpressionInternal(FunctionExpression functionExpression, boolean forceNonConstructor) {
        // Create a new compiler for the function body
        // Nested functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = new BytecodeCompiler(compilerContext.strictMode, compilerContext.captureResolver, compilerContext.context);
        CompilerContext functionContext = functionCompiler.context();

        functionContext.sourceCode = compilerContext.sourceCode;
        functionContext.nonDeletableGlobalBindings.addAll(compilerContext.nonDeletableGlobalBindings);
        functionContext.privateSymbols = compilerContext.privateSymbols;
        inheritVisibleWithObjectBindings(functionContext);

        // Enter function scope and add parameters as locals
        functionContext.scopeManager.enterScope();
        functionContext.inGlobalScope = false;
        functionContext.isInAsyncFunction = functionExpression.isAsync();
        functionContext.isInGeneratorFunction = functionExpression.isGenerator();
        // Inherit class inner name so eval() inside nested functions can resolve it.
        inheritClassInnerNameCapture(functionContext);

        // Check for "use strict" directive early and update strict mode
        // This ensures nested functions inherit the correct strict mode
        if (functionExpression.getBody().hasUseStrictDirective()) {
            functionContext.strictMode = true;
        }

        List<Integer> parameterSlotIndexes = new ArrayList<>();
        List<int[]> destructuringParams = declareParameters(
                functionExpression.getParams(),
                functionContext,
                parameterSlotIndexes);
        if (functionExpression.needsArguments()) {
            declareAndInitializeImplicitArgumentsBinding(functionContext);
        }

        if (functionExpression.getId() != null) {
            boolean conflictsWithParameter = false;
            Set<String> allParamNames = new HashSet<>();
            for (Pattern param : functionExpression.getParams()) {
                allParamNames.addAll(param.getBoundNames());
            }
            conflictsWithParameter = allParamNames.contains(functionExpression.getId().getName());
            if (!conflictsWithParameter && functionExpression.getRestParameter() != null) {
                List<String> restBoundNames = functionExpression.getRestParameter().getArgument().getBoundNames();
                conflictsWithParameter = restBoundNames.contains(functionExpression.getId().getName());
            }
            if (!conflictsWithParameter) {
                functionContext.scopeManager.currentScope().declareLocal(functionExpression.getId().getName());
                // Per ES2024 15.2.5: The BindingIdentifier in a named function expression
                // is an immutable binding. Following QuickJS add_func_var:
                // - In strict mode: mark as const so assignment throws TypeError
                // - In non-strict mode: mark as function name so assignment is silently ignored
                if (functionContext.strictMode) {
                    functionContext.scopeManager.currentScope().markConstLocal(functionExpression.getId().getName());
                } else {
                    functionContext.scopeManager.currentScope().markFunctionNameLocal(functionExpression.getId().getName());
                }
            }
        }

        // Emit default parameter initialization following QuickJS pattern
        if (functionExpression.getDefaults() != null) {
            compilerContext.emitHelpers.emitDefaultParameterInit(
                    functionCompiler,
                    functionExpression.getFunctionParams(),
                    parameterSlotIndexes);
        }

        // Handle rest parameter if present
        // The REST opcode must be emitted early in the function to initialize the rest array
        if (functionExpression.getRestParameter() != null) {
            // Calculate the index where rest arguments start
            int firstRestIndex = functionExpression.getParams().size();

            // Emit REST opcode with the starting index
            functionContext.emitter.emitOpcode(Opcode.REST);
            functionContext.emitter.emitU16(firstRestIndex);

            emitRestParameterBinding(functionExpression.getRestParameter(), functionContext);
        }

        // Emit destructuring for pattern parameters after defaults and rest
        emitParameterDestructuring(functionExpression.getParams(), destructuringParams, functionContext);

        // If this is a generator function, emit INITIAL_YIELD at the start
        if (functionExpression.isGenerator()) {
            functionContext.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }

        // Phase 0: Pre-declare all var bindings as locals before function hoisting.
        // var declarations are function-scoped and must be visible to nested function
        // declarations that may capture them. Without this, Phase 1 function hoisting
        // would fail to resolve captured var references (e.g., inner functions referencing
        // outer var variables would emit GET_VAR instead of GET_VAR_REF).
        functionContext.compilerAnalysis.hoistAllDeclarationsAsLocals(functionExpression.getBody().getBody());

        // Phase 1: Hoist top-level function declarations (ES spec requires function
        // declarations to be initialized before any code executes).
        for (Statement stmt : functionExpression.getBody().getBody()) {
            if (stmt instanceof FunctionDeclaration funcDecl) {
                functionContext.functionDeclarationCompiler.compile(funcDecl);
            }
        }

        // Annex B.3.3.1: Hoist function declarations from blocks/if-statements
        // to the function scope as var bindings (initialized to undefined).
        // Build parameterNames set (BoundNames of argumentsList + "arguments" binding)
        Set<String> exprParamNames = functionExpression.getParameterNames();
        functionContext.compilerAnalysis.hoistFunctionBodyAnnexBDeclarations(functionExpression.getBody().getBody(), exprParamNames);

        // Phase 2: Compile non-FunctionDeclaration statements in source order
        for (Statement stmt : functionExpression.getBody().getBody()) {
            if (stmt instanceof FunctionDeclaration) {
                continue; // Already hoisted in Phase 1
            }
            functionContext.statementCompiler.compile(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        // Check if last statement is a return statement
        List<Statement> bodyStatements = functionExpression.getBody().getBody();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            functionContext.emitter.emitOpcode(Opcode.UNDEFINED);
            int returnValueIndex = functionContext.scopeManager.currentScope().declareLocal("$function_return_" + functionContext.emitter.currentOffset());
            functionContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);
            functionContext.emitHelpers.emitCurrentScopeUsingDisposal();
            functionContext.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
            functionContext.emitter.emitOpcode(functionExpression.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        Integer functionExpressionSelfLocalIndex = null;
        if (functionExpression.getId() != null) {
            functionExpressionSelfLocalIndex = functionContext.scopeManager.currentScope().getLocal(functionExpression.getId().getName());
        }
        int localCount = functionContext.scopeManager.currentScope().getLocalCount();
        String[] localVarNames = functionContext.scopeManager.getLocalVarNames();
        functionContext.scopeManager.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionContext.emitter.build(localCount, localVarNames);

        // Get function name (empty string for anonymous)
        String functionName = functionExpression.getId() != null ? functionExpression.getId().getName() : "";

        // Detect "use strict" directive in function body
        // Combine inherited strict mode with local "use strict" directive
        boolean isStrict = functionContext.strictMode
                || functionExpression.getBody().hasUseStrictDirective();

        // Extract function source code from original source
        String functionSource = compilerContext.extractSourceCode(functionExpression.getLocation());

        // Create JSBytecodeFunction
        int definedArgCount = functionExpression.getFunctionParams().computeDefinedArgCount();
        // Per ES spec FunctionAllocate: async functions, generator functions,
        // async generators, and getter/setter methods are NOT constructable
        boolean isFuncConstructor = !forceNonConstructor
                && !functionExpression.isAsync()
                && !functionExpression.isGenerator();
        JSBytecodeFunction function = new JSBytecodeFunction(
                compilerContext.context,
                functionBytecode,
                functionName,
                definedArgCount,
                JSValue.NO_ARGS,
                null,            // prototype - will be set by VM
                isFuncConstructor,
                functionExpression.isAsync(),
                functionExpression.isGenerator(),
                false,           // isArrow - regular function, not arrow
                isStrict,        // strict - detected from "use strict" directive in function body
                functionSource   // source code for toString()
        );
        function.setHasParameterExpressions(functionExpression.getFunctionParams().hasNonSimpleParameters());
        if (functionExpressionSelfLocalIndex != null) {
            function.setSelfLocalIndex(functionExpressionSelfLocalIndex);
        }

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        compilerContext.emitHelpers.emitCapturedValues(functionCompiler, function);
        // Emit FCLOSURE opcode with function in constant pool
        compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);
    }

    void declareAndInitializeImplicitArgumentsBinding(CompilerContext functionContext) {
        if (functionContext.inGlobalScope || functionContext.isInArrowFunction) {
            return;
        }
        if (functionContext.scopeManager.currentScope().getLocal(JSArguments.NAME) != null) {
            return;
        }

        int argumentsLocalIndex = functionContext.scopeManager.currentScope().declareLocal(JSArguments.NAME);
        functionContext.emitter.emitOpcode(Opcode.SPECIAL_OBJECT);
        functionContext.emitter.emitU8(0);
        functionContext.emitter.emitOpcode(Opcode.PUT_LOC);
        functionContext.emitter.emitU16(argumentsLocalIndex);
    }

    /**
     * Declare function parameters and emit destructuring for pattern params.
     * For Identifier params, declares as a named parameter slot.
     * For destructuring Pattern params (ObjectPattern, ArrayPattern), declares
     * a synthetic parameter slot and emits destructuring code after defaults/rest.
     *
     * @return list of (index, pattern) pairs for destructuring params that need
     * post-processing after default/rest initialization
     */
    List<int[]> declareParameters(
            List<Pattern> params,
            CompilerContext functionContext,
            List<Integer> parameterSlotIndexes) {
        List<int[]> destructuringParams = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            Pattern param = params.get(i);
            if (param instanceof Identifier id) {
                int slotIndex = functionContext.scopeManager.currentScope().declareParameter(id.getName());
                parameterSlotIndexes.add(slotIndex);
            } else {
                // Destructuring parameter: declare a synthetic slot for the argument
                int slotIndex = functionContext.scopeManager.currentScope().declareParameter("$param_" + i);
                parameterSlotIndexes.add(slotIndex);
                // Declare local variables for all bound names in the pattern
                functionContext.patternCompiler.declarePatternVariables(param);
                destructuringParams.add(new int[]{slotIndex, i});
            }
        }
        return destructuringParams;
    }

    void emitParameterDestructuring(List<Pattern> params, List<int[]> destructuringParams,
                                    CompilerContext functionContext) {
        for (int[] entry : destructuringParams) {
            int slotIndex = entry[0];
            int paramIndex = entry[1];
            Pattern pattern = params.get(paramIndex);
            // Push the argument value onto the stack
            functionContext.emitter.emitOpcodeU16(Opcode.GET_LOC, slotIndex);
            // Destructure and assign to local variables
            functionContext.patternCompiler.compile(pattern);
        }
    }

    /**
     * Emit destructuring code for pattern parameters after defaults and rest handling.
     * Reads the argument value from the synthetic parameter slot and destructures it.
     */
    void emitRestParameterBinding(RestParameter restParameter,
                                  CompilerContext functionContext) {
        if (restParameter.getArgument() instanceof Identifier restId) {
            // Simple rest: ...args → declare local and store
            String restParamName = restId.getName();
            int restLocalIndex = functionContext.scopeManager.currentScope().declareLocal(restParamName);
            functionContext.emitter.emitOpcode(Opcode.PUT_LOC);
            functionContext.emitter.emitU16(restLocalIndex);
        } else {
            // Destructured rest: ...[a, b] or ...{a, b} → compile pattern assignment
            functionContext.patternCompiler.compile(restParameter.getArgument());
        }
    }

    /**
     * Compile a class body expression ensuring it executes in strict mode.
     * If the enclosing function is sloppy and not a generator/async, wraps
     * the expression in a strict-mode wrapper function called inline.
     * Otherwise, compiles inline with compile-time strict mode set.
     */
    void emitStrictClassBodyExpression(Expression expression) {
        if (canUseStrictWrapper()) {
            emitStrictExpressionCall(expression);
        } else {
            compilerContext.pushState();
            compilerContext.strictMode = true;
            try {
                compilerContext.expressionCompiler.compile(expression);
            } finally {
                compilerContext.popState();
            }
        }
    }

    private void emitStrictExpressionCall(Expression expression) {
        BytecodeCompiler wrapperCompiler = new BytecodeCompiler(
                true, compilerContext.captureResolver, compilerContext.context);
        CompilerContext wrapperContext = wrapperCompiler.context();

        wrapperContext.sourceCode = compilerContext.sourceCode;
        wrapperContext.nonDeletableGlobalBindings.addAll(compilerContext.nonDeletableGlobalBindings);
        wrapperContext.privateSymbols = compilerContext.privateSymbols;
        inheritVisibleWithObjectBindings(wrapperContext);
        wrapperContext.scopeManager.enterScope();
        wrapperContext.inGlobalScope = false;

        wrapperContext.expressionCompiler.compile(expression);
        wrapperContext.emitter.emitOpcode(Opcode.RETURN);

        int localCount = wrapperContext.scopeManager.isEmpty()
                ? wrapperContext.scopeManager.getMaxLocalCount()
                : wrapperContext.scopeManager.currentScope().getLocalCount();
        String[] localVarNames = wrapperContext.scopeManager.getLocalVarNames();
        Bytecode bytecode = wrapperContext.emitter.build(localCount, localVarNames);

        JSBytecodeFunction wrapperFunction = new JSBytecodeFunction(
                compilerContext.context,
                bytecode,
                "",
                0,
                JSValue.NO_ARGS,
                null,
                false, false, false, false,
                true,
                null
        );
        compilerContext.emitHelpers.emitCapturedValues(wrapperCompiler, wrapperFunction);

        compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, wrapperFunction);
        compilerContext.emitter.emitOpcode(Opcode.UNDEFINED);
        compilerContext.emitter.emitOpcodeU16(Opcode.CALL, 0);
    }

    void inheritClassInnerNameCapture(CompilerContext targetContext) {
        String classInnerName = compilerContext.classInnerNameToCapture;
        if (classInnerName != null) {
            targetContext.classInnerNameToCapture = classInnerName;
            targetContext.captureResolver.resolveCapturedBindingIndex(classInnerName);
        }
    }

    void inheritVisibleWithObjectBindings(CompilerContext functionContext) {
        functionContext.withObjectManager.addInheritedBindingNames(
                compilerContext.withObjectManager.getVisibleBindingNamesForNestedFunction(compilerContext.scopeManager));
    }
}
