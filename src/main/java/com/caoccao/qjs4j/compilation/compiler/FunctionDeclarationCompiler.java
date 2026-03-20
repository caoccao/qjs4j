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

import com.caoccao.qjs4j.compilation.ast.FunctionDeclaration;
import com.caoccao.qjs4j.compilation.ast.ReturnStatement;
import com.caoccao.qjs4j.compilation.ast.Statement;
import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles compilation of function declarations.
 */
final class FunctionDeclarationCompiler extends AstNodeCompiler<FunctionDeclaration> {
    FunctionDeclarationCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    @Override
    void compile(FunctionDeclaration funcDecl) {
        // Pre-declare function name as a local in the current scope (if non-global and not
        // already declared). This must happen BEFORE creating the child compiler so the
        // function body can capture the name via closure for self-reference.
        // Per QuickJS: function declarations inside blocks/switch cases create a lexical
        // binding in the current scope, even if a parent scope has the same name (e.g.,
        // a parameter). This prevents the function object from overwriting the parent binding.
        String functionName = funcDecl.getId().getName();
        if (!compilerContext.inGlobalScope) {
            if (compilerContext.scopeManager.currentScope().getLocal(functionName) == null) {
                compilerContext.scopeManager.currentScope().declareLocal(functionName);
            }
        }

        // Create a new compiler for the function body
        // Nested functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = new BytecodeCompiler(compilerContext.strictMode, compilerContext.captureResolver, compilerContext.context);
        CompilerContext functionContext = functionCompiler.context();

        functionContext.sourceCode = compilerContext.sourceCode;
        functionContext.nonDeletableGlobalBindings.addAll(compilerContext.nonDeletableGlobalBindings);
        functionContext.privateSymbols = compilerContext.privateSymbols;
        compilerContext.functionExpressionCompiler.inheritVisibleWithObjectBindings(functionContext);

        // Enter function scope and add parameters as locals
        functionContext.scopeManager.enterScope();
        functionContext.inGlobalScope = false;
        functionContext.isInAsyncFunction = funcDecl.isAsync();  // Track if this is an async function
        functionContext.isInGeneratorFunction = funcDecl.isGenerator();
        // Inherit class inner name so eval() inside nested functions can resolve it.
        compilerContext.functionExpressionCompiler.inheritClassInnerNameCapture(functionContext);
        compilerContext.functionExpressionCompiler.inheritVisibleLexicalCapturesForDirectEvalInBody(
                functionContext,
                funcDecl.getBody(),
                funcDecl.getFunctionParams().hasNonSimpleParameters());

        // Check for "use strict" directive early and update strict mode
        // This ensures nested functions inherit the correct strict mode
        if (funcDecl.getBody().hasUseStrictDirective()) {
            functionContext.strictMode = true;
        }

        List<Integer> parameterSlotIndexes = new ArrayList<>();
        List<int[]> destructuringParams = compilerContext.functionExpressionCompiler.declareParameters(
                funcDecl.getParams(),
                functionContext,
                parameterSlotIndexes);
        if (funcDecl.needsArguments()) {
            compilerContext.functionExpressionCompiler.declareAndInitializeImplicitArgumentsBinding(functionContext);
        }

        // Emit default parameter initialization following QuickJS pattern
        if (funcDecl.getDefaults() != null) {
            compilerContext.emitHelpers.emitDefaultParameterInit(
                    functionCompiler,
                    funcDecl.getFunctionParams(),
                    parameterSlotIndexes);
        }

        // Handle rest parameter if present
        // The REST opcode must be emitted early in the function to initialize the rest array
        if (funcDecl.getRestParameter() != null) {
            // Calculate the index where rest arguments start
            int firstRestIndex = funcDecl.getParams().size();

            // Emit REST opcode with the starting index
            functionContext.emitter.emitOpcode(Opcode.REST);
            functionContext.emitter.emitU16(firstRestIndex);

            compilerContext.functionExpressionCompiler.emitRestParameterBinding(funcDecl.getRestParameter(), functionContext);
        }

        // Emit destructuring for pattern parameters after defaults and rest
        compilerContext.functionExpressionCompiler.emitParameterDestructuring(funcDecl.getParams(), destructuringParams, functionContext);

        // If this is a generator function, emit INITIAL_YIELD at the start
        if (funcDecl.isGenerator()) {
            functionContext.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }

        // Phase 0: Pre-declare all var bindings as locals before function hoisting.
        // var declarations are function-scoped and must be visible to nested function
        // declarations that may capture them. Without this, Phase 1 function hoisting
        // would fail to resolve captured var references (e.g., inner functions referencing
        // outer var variables would emit GET_VAR instead of GET_VAR_REF).
        functionContext.compilerAnalysis.hoistAllDeclarationsAsLocals(funcDecl.getBody().getBody());

        // Phase 1: Hoist top-level function declarations (ES spec requires function
        // declarations to be initialized before any code executes).
        for (Statement stmt : funcDecl.getBody().getBody()) {
            if (stmt instanceof FunctionDeclaration innerFuncDecl) {
                functionContext.functionDeclarationCompiler.compile(innerFuncDecl);
            }
        }

        // Annex B.3.3.1: Hoist function declarations from blocks/if-statements
        // to the function scope as var bindings (initialized to undefined).
        Set<String> declParamNames = funcDecl.getParameterNames();
        functionContext.compilerAnalysis.hoistFunctionBodyAnnexBDeclarations(funcDecl.getBody().getBody(), declParamNames);

        // Phase 2: Compile non-FunctionDeclaration statements in source order
        for (Statement stmt : funcDecl.getBody().getBody()) {
            if (stmt instanceof FunctionDeclaration) {
                continue; // Already hoisted in Phase 1
            }
            functionContext.statementCompiler.compile(stmt);
        }

        // If body doesn't end with return, add implicit return undefined
        List<Statement> bodyStatements = funcDecl.getBody().getBody();
        if (bodyStatements.isEmpty() || !(bodyStatements.get(bodyStatements.size() - 1) instanceof ReturnStatement)) {
            functionContext.emitter.emitOpcode(Opcode.UNDEFINED);
            int returnValueIndex = functionContext.scopeManager.currentScope().declareLocal("$function_return_" + functionContext.emitter.currentOffset());
            functionContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, returnValueIndex);
            functionContext.emitHelpers.emitCurrentScopeUsingDisposal();
            functionContext.emitter.emitOpcodeU16(Opcode.GET_LOC, returnValueIndex);
            // Emit RETURN_ASYNC for async functions, RETURN for sync functions
            functionContext.emitter.emitOpcode(funcDecl.isAsync() ? Opcode.RETURN_ASYNC : Opcode.RETURN);
        }

        int localCount = functionContext.scopeManager.currentScope().getLocalCount();
        String[] localVarNames = functionContext.scopeManager.getLocalVarNames();
        functionContext.scopeManager.exitScope();

        // Build the function bytecode
        Bytecode functionBytecode = functionContext.emitter.build(localCount, localVarNames);

        // Function name (already extracted at start of method)

        // Detect "use strict" directive in function body
        // Combine inherited strict mode with local "use strict" directive
        boolean isStrict = functionContext.strictMode
                || funcDecl.getBody().hasUseStrictDirective();

        // Extract function source code from original source
        String functionSource = compilerContext.extractSourceCode(funcDecl.getLocation());

        // Trim trailing whitespace from the extracted source
        // This is needed because the parser's end offset may include whitespace after the closing brace
        if (functionSource != null) {
            functionSource = functionSource.stripTrailing();
        }

        // If extraction failed, build a simplified representation
        if (functionSource == null || functionSource.isEmpty()) {
            StringBuilder funcSource = new StringBuilder();
            if (funcDecl.isAsync()) {
                funcSource.append("async ");
            }
            funcSource.append(JSKeyword.FUNCTION);
            if (funcDecl.isGenerator()) {
                funcSource.append("*");
            }
            funcSource.append(" ").append(functionName).append("(");
            for (int i = 0; i < funcDecl.getParams().size(); i++) {
                if (i > 0) {
                    funcSource.append(", ");
                }
                funcSource.append(funcDecl.getParams().get(i).toPatternString());
            }
            if (funcDecl.getRestParameter() != null) {
                if (!funcDecl.getParams().isEmpty()) {
                    funcSource.append(", ");
                }
                funcSource.append("...").append(funcDecl.getRestParameter().getArgument().toPatternString());
            }
            funcSource.append(") { [function body] }");
            functionSource = funcSource.toString();
        }

        // Check if the function captures its own name (e.g., block-scoped function declaration
        // where the body references f). This fixes the chicken-and-egg problem where FCLOSURE
        // captures the local before the function is stored in it.
        // Following QuickJS var_refs pattern: the closure variable pointing to the function
        // itself is patched after creation via selfCaptureIndex metadata.
        Integer selfCaptureIdx = functionContext.captureResolver.findCapturedBindingIndex(functionName);
        int selfCaptureIndex = selfCaptureIdx != null ? selfCaptureIdx : -1;

        // Create JSBytecodeFunction
        int definedArgCount = funcDecl.getFunctionParams().computeDefinedArgCount();
        // Per ES spec FunctionAllocate: async functions, generator functions,
        // async generators are NOT constructable
        boolean isFuncConstructor = !funcDecl.isAsync() && !funcDecl.isGenerator();
        JSBytecodeFunction function = new JSBytecodeFunction(
                compilerContext.context,
                functionBytecode,
                functionName,
                definedArgCount,
                JSValue.NO_ARGS,
                null,            // prototype - will be set by VM
                isFuncConstructor,
                funcDecl.isAsync(),
                funcDecl.isGenerator(),
                false,           // isArrow - regular function, not arrow
                isStrict,        // strict - detected from "use strict" directive in function body
                functionSource,  // source code for toString()
                selfCaptureIndex // closure self-reference index (-1 if none)
        );
        function.setHasParameterExpressions(funcDecl.getFunctionParams().hasNonSimpleParameters());

        compilerContext.emitHelpers.emitCapturedValues(functionCompiler, function);
        // Emit FCLOSURE opcode with function in constant pool
        compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);

        // Store the function in a variable with its name
        // Per B.3.3.1: the Annex B runtime hook only fires if no enclosing block
        // scope has a lexical binding for the same name (otherwise replacing this
        // function with var F would produce an Early Error).
        boolean isAnnexB = compilerContext.annexBFunctionNames.contains(functionName)
                && !funcDecl.isAsync()
                && !funcDecl.isGenerator()
                && !compilerContext.scopeManager.hasEnclosingBlockScopeLocal(functionName);
        Integer localIndex = compilerContext.scopeManager.findLocalInScopes(functionName);
        if (localIndex != null) {
            if (isAnnexB && !compilerContext.suppressAnnexBVarStore) {
                // Annex B.3.3 runtime hook: store in both block scope and var scope
                compilerContext.emitter.emitOpcode(Opcode.DUP);
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                compilerContext.emitHelpers.emitAnnexBVarStore(functionName);
            } else {
                compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
            }
        } else {
            // Declare the function as a global variable or in the current scope
            if (compilerContext.inGlobalScope) {
                if (!compilerContext.evalMode) {
                    compilerContext.nonDeletableGlobalBindings.add(functionName);
                }
                compilerContext.emitter.emitOpcodeAtom(Opcode.PUT_VAR, functionName);
            } else {
                // Declare it as a local
                localIndex = compilerContext.scopeManager.currentScope().declareLocal(functionName);
                if (isAnnexB && !compilerContext.suppressAnnexBVarStore) {
                    compilerContext.emitter.emitOpcode(Opcode.DUP);
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                    compilerContext.emitHelpers.emitAnnexBVarStore(functionName);
                } else {
                    compilerContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, localIndex);
                }
            }
        }
    }
}
