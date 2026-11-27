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
import com.caoccao.qjs4j.core.JSKeyword;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;
import java.util.*;

/** Compiles the shared function environment, parameter initialization, body and closure. */
final class OrdinaryFunctionCompiler {
    private OrdinaryFunctionCompiler() {
    }

    static <T extends ASTNode & OrdinaryFunctionNode> JSBytecodeFunction compile(CompilerContext compilerContext,
            T node, boolean forceNonConstructor) {
        boolean declaration = node instanceof FunctionDeclaration;
        FunctionExpressionCompiler parameters = compilerContext.functionExpressionCompiler;
        // Create a new compiler for the function body
        // Nested functions inherit strict mode from parent (QuickJS behavior)
        BytecodeCompiler functionCompiler = compilerContext.newFunctionCompiler(node.getLocation());
        CompilerContext functionContext = functionCompiler.context();

        // Enter function scope and add parameters as locals
        functionContext.enterFunctionScope(node.isAsync(), node.isGenerator());
        // Inherit class inner name so eval() inside nested functions can resolve it.
        parameters.inheritClassInnerNameCapture(functionContext);
        Set<String> enclosingParameterScopeFunctionNames = parameters
                .inheritParameterScopeFunctionNameCapture(functionContext);
        parameters.inheritVisibleLexicalCapturesForDirectEvalInBody(functionContext, node.getBody(),
                node.getFunctionParams().hasNonSimpleParameters());

        // Check for "use strict" directive early and update strict mode
        // This ensures nested functions inherit the correct strict mode
        if (node.getBody().hasUseStrictDirective()) {
            functionContext.strictMode = true;
        }

        List<Integer> parameterSlotIndexes = new ArrayList<>();
        List<int[]> destructuringParams = parameters.declareParameters(node.getParams(), functionContext,
                parameterSlotIndexes);
        if (node.needsArguments()) {
            parameters.declareAndInitializeImplicitArgumentsBinding(functionContext);
        }

        // ES2024 15.2.5: the BindingIdentifier of a named function expression is bound in a
        // function environment that *wraps* the function's parameter and variable environments.
        // Default-parameter initializers resolve through the parameter environment into it, so the
        // name is visible there; a `var`, lexical, class or function declaration of the same name
        // in the body creates a different binding that shadows it from the body onwards, and that
        // binding starts as undefined rather than holding the function object.
        //
        // Both halves are modelled with two local slots: the self-name slot is declared first so
        // defaults see it, and the body declaration then takes over the name in a fresh slot.
        Integer selfNameLocalIndex = null;
        boolean selfNameShadowedByBodyDeclaration = false;
        if (!declaration && node.getId() != null) {
            String selfName = node.getId().getName();
            boolean conflictsWithParameter = node.getFunctionParams().bindsName(selfName);
            if (!conflictsWithParameter) {
                selfNameShadowedByBodyDeclaration = functionContext.compilerAnalysis
                        .bodyDeclaresBinding(node.getBody().getBody(), selfName);
                selfNameLocalIndex = functionContext.scopeManager.currentScope().declareLocal(selfName);
                // Per ES2024 15.2.5: The BindingIdentifier in a named function expression
                // is an immutable binding. Following QuickJS add_func_var:
                // - In strict mode: mark as const so assignment throws TypeError
                // - In non-strict mode: mark as function name so assignment is silently ignored
                if (functionContext.strictMode) {
                    functionContext.scopeManager.currentScope().markConstLocal(selfName);
                } else {
                    functionContext.scopeManager.currentScope().markFunctionNameLocal(selfName);
                }
            }
        }

        // Everything compiled from here to the end of parameter initialization sits in the
        // parameter environment, so a closure created there has to carry the function-expression
        // bindings with it — see CompilerContext.parameterScopeFunctionNames. This function's own
        // name goes in front of whatever it inherited rather than replacing it, because a closure
        // here is inside both environments. The inherited list is restored afterwards rather than
        // cleared: a function nested in an *enclosing* function's default initializer is still
        // inside that initializer, body and all.
        Set<String> inheritedParameterScopeFunctionNames = functionContext.parameterScopeFunctionNames;
        if (selfNameShadowedByBodyDeclaration) {
            functionContext.parameterScopeFunctionNames = withParameterScopeName(inheritedParameterScopeFunctionNames,
                    node.getId().getName());
        }

        parameters.initializeParameters(functionCompiler, node, parameterSlotIndexes, destructuringParams,
                node.isGenerator());

        // The parameter environment is fully built by now, so anything the body declares under the
        // function's own name takes over that name in a slot of its own. Bytecode emitted above for
        // the default initializers keeps referring to the self-name slot by index.
        functionContext.parameterScopeFunctionNames = inheritedParameterScopeFunctionNames;
        int bodyScopeEnteredLocalIndex = -1;
        if (selfNameShadowedByBodyDeclaration) {
            String selfName = node.getId().getName();
            // The one thing an index cannot express: which of the two environments the frame is
            // executing in. Direct eval resolves by name at run time and needs to know, so the
            // transition is recorded in a slot of its own — false for the whole of parameter
            // initialization, true from the first instruction of the body.
            bodyScopeEnteredLocalIndex = functionContext.scopeManager.currentScope()
                    .declareLocal("#function_expression_body_entered_" + selfName);
            functionContext.emitter.emitOpcode(Opcode.PUSH_TRUE);
            functionContext.emitter.emitOpcodeU16(Opcode.PUT_LOC, bodyScopeEnteredLocalIndex);
            functionContext.scopeManager.currentScope().shadowLocalWithFreshSlot(selfName,
                    "#function_expression_name_" + selfName);
        }

        FunctionBodyCompiler.ORDINARY.compile(functionContext, node, node.getBody(), node.isAsync());

        Bytecode functionBytecode = functionContext.finishFunctionScope();

        String functionName = node.getId() == null ? "" : node.getId().getName();
        String functionSource = compilerContext.extractSourceCode(node.getLocation());
        int selfCaptureIndex = -1;
        if (declaration) {
            functionSource = declarationSource(node, functionName, functionSource);
            Integer index = functionContext.captureResolver.findCapturedBindingIndex(functionName);
            if (index != null) {
                selfCaptureIndex = index;
            }
        }
        boolean constructable = !forceNonConstructor && !node.isAsync() && !node.isGenerator();
        JSBytecodeFunction function = new JSBytecodeFunction(compilerContext.context, functionBytecode, functionName,
                node.getFunctionParams().computeDefinedArgCount(), JSValue.NO_ARGS, null, constructable, node.isAsync(),
                node.isGenerator(), false, functionContext.strictMode, functionSource, selfCaptureIndex);
        function.setHasParameterExpressions(node.getFunctionParams().hasNonSimpleParameters());
        if (selfNameLocalIndex != null) {
            function.setSelfLocalIndex(selfNameLocalIndex);
        }
        if (bodyScopeEnteredLocalIndex >= 0) {
            function.setOwnParameterScopeFunctionName(node.getId().getName(), bodyScopeEnteredLocalIndex);
        }
        // Created inside enclosing default initializers: those captures are the bindings, and they
        // are in scope for this function's whole lifetime, so they have no phase slot.
        function.setInheritedParameterScopeFunctionNames(enclosingParameterScopeFunctionNames);

        // Prototype chain will be initialized when the function is loaded
        // during bytecode execution (see FCLOSURE opcode handler)

        compilerContext.emitHelpers.emitCapturedValues(functionCompiler, function);
        // Emit FCLOSURE opcode with function in constant pool
        compilerContext.emitter.emitOpcodeConstant(Opcode.FCLOSURE, function);
        return function;
    }

    private static String declarationSource(OrdinaryFunctionNode node, String functionName, String functionSource) {
        // Trim trailing whitespace from the extracted source
        // This is needed because the parser's end offset may include whitespace after the closing brace
        if (functionSource != null) {
            functionSource = functionSource.stripTrailing();
        }

        // If extraction failed, build a simplified representation
        if (functionSource == null || functionSource.isEmpty()) {
            StringBuilder funcSource = new StringBuilder();
            if (node.isAsync()) {
                funcSource.append("async ");
            }
            funcSource.append(JSKeyword.FUNCTION);
            if (node.isGenerator()) {
                funcSource.append("*");
            }
            funcSource.append(" ").append(functionName).append("(");
            for (int i = 0; i < node.getParams().size(); i++) {
                if (i > 0) {
                    funcSource.append(", ");
                }
                funcSource.append(node.getParams().get(i).toPatternString());
            }
            if (node.getRestParameter() != null) {
                if (!node.getParams().isEmpty()) {
                    funcSource.append(", ");
                }
                funcSource.append("...").append(node.getRestParameter().getArgument().toPatternString());
            }
            funcSource.append(") { [function body] }");
            functionSource = funcSource.toString();
        }
        return functionSource;
    }

    /**
     * Add a name to the set of named-function-expression bindings the enclosing parameter environments make visible.
     * <p>
     * A set that already carries the name is already the answer, and is returned as it stands: an inner function
     * expression reusing an outer one's name shadows it, and the entry says all the consumers ask — whether the name
     * resolves through a parameter environment.
     *
     * @param enclosingNames
     *            the enclosing environments' names
     * @param name
     *            the name to add
     * @return the set including that name
     */
    private static Set<String> withParameterScopeName(Set<String> enclosingNames, String name) {
        if (enclosingNames.isEmpty()) {
            return Set.of(name);
        }
        if (enclosingNames.contains(name)) {
            return enclosingNames;
        }
        Set<String> names = new HashSet<>(enclosingNames);
        names.add(name);
        return Collections.unmodifiableSet(names);
    }
}
