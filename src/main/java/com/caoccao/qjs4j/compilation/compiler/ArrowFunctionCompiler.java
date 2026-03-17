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
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Handles compilation of arrow function expressions.
 */
final class ArrowFunctionCompiler {
    private final CompilerContext compilerContext;

    ArrowFunctionCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
    }

    void compileArrowFunctionExpression(ArrowFunctionExpression arrowExpr) {
        // Create a new compiler for the function body
        // Arrow functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = new BytecodeCompiler(compilerContext.strictMode, compilerContext.captureResolver, compilerContext.context);
        CompilerContext functionContext = functionCompiler.context();

        functionContext.sourceCode = compilerContext.sourceCode;
        functionContext.nonDeletableGlobalBindings.addAll(compilerContext.nonDeletableGlobalBindings);
        functionContext.privateSymbols = compilerContext.privateSymbols;
        compilerContext.functionCompiler.inheritVisibleWithObjectBindings(functionContext);

        // Enter function scope and add parameters as locals
        functionContext.scopeManager.enterScope();
        functionContext.inGlobalScope = false;
        functionContext.isInAsyncFunction = arrowExpr.isAsync();  // Track if this is an async function
        functionContext.isInGeneratorFunction = false;  // Arrow functions cannot be generators
        functionContext.isInArrowFunction = true;  // Arrow functions don't have their own arguments
        // Arrow functions inherit class field eval context (new.target resolves to undefined,
        // eval('arguments') should throw SyntaxError)
        functionContext.classFieldEvalContext = compilerContext.classFieldEvalContext
                || compilerContext.inClassFieldInitializer;
        // Inherit class inner name so eval() inside nested arrows can resolve it.
        compilerContext.functionCompiler.inheritClassInnerNameCapture(functionContext);
        // Arrow functions inherit arguments from enclosing non-arrow function.
        // If the parent is a regular function (not arrow, not global program), it has arguments binding.
        // If the parent is also an arrow, inherit whatever it has.
        // The global program is not a function and doesn't provide 'arguments'.
        if (compilerContext.isInArrowFunction) {
            functionContext.hasEnclosingArgumentsBinding = compilerContext.hasEnclosingArgumentsBinding;
        } else {
            functionContext.hasEnclosingArgumentsBinding = !compilerContext.isGlobalProgram;
        }

        // Check for "use strict" directive if body is a block statement
        if (arrowExpr.getBody() instanceof BlockStatement block
                && block.hasUseStrictDirective()) {
            functionContext.strictMode = true;
        }

        boolean hasNonSimpleParameters = arrowExpr.getFunctionParams().hasNonSimpleParameters();
        boolean hasArgumentsParameterBinding = false;
        for (Pattern parameter : arrowExpr.getParams()) {
            if (parameter.getBoundNames().contains(JSArguments.NAME)) {
                hasArgumentsParameterBinding = true;
                break;
            }
        }
        if (!hasArgumentsParameterBinding && arrowExpr.getRestParameter() != null) {
            hasArgumentsParameterBinding = arrowExpr.getRestParameter().getArgument().getBoundNames()
                    .contains(JSArguments.NAME);
        }
        boolean hasDirectEvalVarArgumentsInDefaults = arrowExpr.getDefaults() != null
                && arrowExpr.getDefaults().stream()
                .filter(Objects::nonNull)
                .anyMatch(Expression::containsDirectEvalVarArguments);
        boolean needsSyntheticEvalArgumentsBinding = hasDirectEvalVarArgumentsInDefaults
                && arrowExpr.getDefaults() != null
                && !functionContext.hasEnclosingArgumentsBinding
                && !hasArgumentsParameterBinding;

        List<Integer> parameterSlotIndexes = new ArrayList<>();
        List<int[]> destructuringParams = compilerContext.functionCompiler.declareParameters(
                arrowExpr.getParams(),
                functionContext,
                parameterSlotIndexes);

        // For top-level arrows with parameter expressions, keep a local slot for dynamically
        // declared `arguments` from direct eval in parameter initializers.
        if (needsSyntheticEvalArgumentsBinding
                && functionContext.scopeManager.findLocalInScopes(JSArguments.NAME) == null) {
            int argumentsLocalIndex = functionContext.scopeManager.currentScope().declareLocal(JSArguments.NAME);
            functionContext.tdzLocals.add(JSArguments.NAME);
            functionContext.emitter.emitOpcodeU16(Opcode.SET_LOC_UNINITIALIZED, argumentsLocalIndex);
        }

        // Emit default parameter initialization following QuickJS pattern:
        // GET_ARG idx, DUP, UNDEFINED, STRICT_EQ, IF_FALSE label, DROP, <default>, DUP, PUT_ARG idx, label:
        if (arrowExpr.getDefaults() != null) {
            compilerContext.emitHelpers.emitDefaultParameterInit(
                    functionCompiler,
                    arrowExpr.getFunctionParams(),
                    parameterSlotIndexes);
        }

        // Handle rest parameter if present
        // The REST opcode must be emitted early in the function to initialize the rest array
        if (arrowExpr.getRestParameter() != null) {
            // Calculate the index where rest arguments start
            int firstRestIndex = arrowExpr.getParams().size();

            // Emit REST opcode with the starting index
            functionContext.emitter.emitOpcode(Opcode.REST);
            functionContext.emitter.emitU16(firstRestIndex);

            compilerContext.functionCompiler.emitRestParameterBinding(arrowExpr.getRestParameter(), functionContext);
        }

        // Emit destructuring for pattern parameters after defaults and rest
        compilerContext.functionCompiler.emitParameterDestructuring(arrowExpr.getParams(), destructuringParams, functionContext);

        boolean enteredBodyScope = false;
        CompilerScope savedVarDeclarationScopeOverride = functionContext.varDeclarationScopeOverride;
        int localCount;
        String[] localVarNames;
        {
            // Compile function body
            // Arrow functions can have expression body or block statement body
            if (arrowExpr.getBody() instanceof BlockStatement block) {
                if (needsSyntheticEvalArgumentsBinding) {
                    functionContext.scopeManager.enterScope();
                    enteredBodyScope = true;
                    functionContext.varDeclarationScopeOverride = functionContext.scopeManager.currentScope();
                }

                try {
                    // Phase 0: Pre-declare var and top-level function names as locals so nested
                    // closures resolve captures to VarRef slots consistently with function bodies.
                    functionContext.compilerAnalysis.hoistAllDeclarationsAsLocals(block.getBody());

                    // Phase 1: Hoist top-level function declarations before executing body statements.
                    for (Statement statement : block.getBody()) {
                        if (statement instanceof FunctionDeclaration functionDeclaration) {
                            functionContext.functionDeclarationCompiler.compileFunctionDeclaration(functionDeclaration);
                        }
                    }

                    // Annex B.3.3.1: Hoist function declarations from blocks/if-statements
                    // into function var bindings when allowed.
                    Set<String> declarationParameterNames = arrowExpr.getParameterNames();
                    functionContext.compilerAnalysis.hoistFunctionBodyAnnexBDeclarations(
                            block.getBody(), declarationParameterNames);

                    // Compile block body statements.
                    for (Statement stmt : block.getBody()) {
                        if (stmt instanceof FunctionDeclaration) {
                            continue;
                        }
                        functionContext.statementCompiler.compileStatement(stmt);
                    }

                    // If body doesn't end with return, add implicit return undefined
                    List<Statement> bodyStatements = block.getBody();
                    if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
                        functionContext.emitter.emitOpcode(Opcode.UNDEFINED);
                        int returnValueIndex = functionContext.scopeManager.currentScope().declareLocal("$arrow_return_" + functionContext.emitter.currentOffset());
                        functionContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);
                        functionContext.emitHelpers.emitCurrentScopeUsingDisposal();
                        functionContext.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
                        // Emit RETURN_ASYNC for async functions, RETURN for sync functions
                        functionContext.emitter.emitOpcode(arrowExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
                    }
                } finally {
                    if (enteredBodyScope) {
                        functionContext.varDeclarationScopeOverride = savedVarDeclarationScopeOverride;
                    }
                }
            } else if (arrowExpr.getBody() instanceof Expression expr) {
                // Expression body - implicitly returns the expression value
                functionContext.expressionCompiler.compileExpression(expr);
                int returnValueIndex = functionContext.scopeManager.currentScope().declareLocal("$arrow_return_" + functionContext.emitter.currentOffset());
                functionContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);
                functionContext.emitHelpers.emitCurrentScopeUsingDisposal();
                functionContext.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
                // Emit RETURN_ASYNC for async functions, RETURN for sync functions
                functionContext.emitter.emitOpcode(arrowExpr.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
            }

            localCount = functionContext.scopeManager.currentScope().getLocalCount();
            localVarNames = functionContext.scopeManager.getLocalVarNames();
        }
        if (enteredBodyScope) {
            functionContext.scopeManager.exitScope();
        }
        functionContext.scopeManager.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionContext.emitter.build(localCount, localVarNames);

        // Arrow functions are always anonymous
        String functionName = "";

        // Extract function source code from original source
        String functionSource = compilerContext.extractSourceCode(arrowExpr.getLocation());

        // Create JSBytecodeFunction
        // Arrow functions cannot be constructors
        int definedArgCount = arrowExpr.getFunctionParams().computeDefinedArgCount();
        JSBytecodeFunction function = new JSBytecodeFunction(
                compilerContext.context,
                functionBytecode,
                functionName,
                definedArgCount,
                JSValue.NO_ARGS,
                null,            // prototype - arrow functions don't have prototype
                false,           // isConstructor - arrow functions cannot be constructors
                arrowExpr.isAsync(),
                false,           // Arrow functions cannot be generators
                true,            // isArrow - this is an arrow function
                functionContext.strictMode,  // strict - inherit from enclosing scope
                functionSource   // source code for toString()
        );
        function.setHasParameterExpressions(hasNonSimpleParameters);
        function.setHasArgumentsParameterBinding(hasArgumentsParameterBinding);

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        compilerContext.emitHelpers.emitCapturedValues(functionCompiler, function);
        // Emit FCLOSURE opcode with function in constant pool
        compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);
    }
}
