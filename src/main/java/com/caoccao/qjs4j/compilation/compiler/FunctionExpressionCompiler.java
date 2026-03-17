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
final class FunctionExpressionCompiler {
    private final CompilerContext compilerContext;

    FunctionExpressionCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileFunctionExpression(FunctionExpression functionExpression, boolean forceNonConstructor) {
        compileFunctionExpressionInternal(functionExpression, forceNonConstructor);
    }

    void compileFunctionExpression(FunctionExpression functionExpression) {
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
        compilerContext.functionCompiler.inheritVisibleWithObjectBindings(functionContext);

        // Enter function scope and add parameters as locals
        functionContext.scopeManager.enterScope();
        functionContext.inGlobalScope = false;
        functionContext.isInAsyncFunction = functionExpression.isAsync();
        functionContext.isInGeneratorFunction = functionExpression.isGenerator();
        // Inherit class inner name so eval() inside nested functions can resolve it.
        compilerContext.functionCompiler.inheritClassInnerNameCapture(functionContext);

        // Check for "use strict" directive early and update strict mode
        // This ensures nested functions inherit the correct strict mode
        if (functionExpression.getBody().hasUseStrictDirective()) {
            functionContext.strictMode = true;
        }

        List<Integer> parameterSlotIndexes = new ArrayList<>();
        List<int[]> destructuringParams = compilerContext.functionCompiler.declareParameters(
                functionExpression.getParams(),
                functionContext,
                parameterSlotIndexes);
        if (functionExpression.needsArguments()) {
            compilerContext.functionCompiler.declareAndInitializeImplicitArgumentsBinding(functionContext);
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

            compilerContext.functionCompiler.emitRestParameterBinding(functionExpression.getRestParameter(), functionContext);
        }

        // Emit destructuring for pattern parameters after defaults and rest
        compilerContext.functionCompiler.emitParameterDestructuring(functionExpression.getParams(), destructuringParams, functionContext);

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
                functionContext.functionDeclarationCompiler.compileFunctionDeclaration(funcDecl);
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
            functionContext.statementCompiler.compileStatement(stmt);
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
}
