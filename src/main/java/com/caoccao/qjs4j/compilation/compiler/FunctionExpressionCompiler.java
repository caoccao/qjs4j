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

import java.util.*;

/**
 * Handles compilation of function expressions.
 */
final class FunctionExpressionCompiler extends AstNodeCompiler<FunctionExpression> {
    FunctionExpressionCompiler(CompilerContext compilerContext) {
        super(compilerContext);
    }

    /**
     * Returns true when a strict-mode wrapper function can safely be used around a class body expression. Wrapper
     * functions introduce a new function boundary, which breaks yield/await semantics, so they must only be used when
     * the enclosing context is a plain (non-generator, non-async) sloppy-mode function.
     */
    private boolean canUseStrictWrapper() {
        return !compilerContext.strictMode && !compilerContext.isInGeneratorFunction
                && !compilerContext.isInAsyncFunction;
    }

    @Override
    void compile(FunctionExpression functionExpression) {
        OrdinaryFunctionCompiler.compile(compilerContext, functionExpression, false);
    }

    void compile(FunctionExpression functionExpression, boolean forceNonConstructor) {
        OrdinaryFunctionCompiler.compile(compilerContext, functionExpression, forceNonConstructor);
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
     * Declare function parameters and emit destructuring for pattern params. For Identifier params, declares as a named
     * parameter slot. For destructuring Pattern params (ObjectPattern, ArrayPattern), declares a synthetic parameter
     * slot and emits destructuring code after defaults/rest.
     *
     * @return list of (index, pattern) pairs for destructuring params that need post-processing after default/rest
     *         initialization
     */
    List<int[]> declareParameters(List<Pattern> params, CompilerContext functionContext,
            List<Integer> parameterSlotIndexes) {
        List<int[]> destructuringParams = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            Pattern param = params.get(i);
            if (param instanceof Identifier id) {
                int slotIndex = functionContext.scopeManager.currentScope().declareParameter(id.getName());
                parameterSlotIndexes.add(slotIndex);
            } else {
                // Destructuring parameter: declare a synthetic slot for the argument
                int slotIndex = functionContext.scopeManager.currentScope().declareParameter("#param_" + i);
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
            // A frame failing to destructure an argument reports the parameter's position.
            functionContext.emitter.emitSourcePosition(pattern.getLocation());
            // Push the argument value onto the stack
            functionContext.emitter.emitOpcodeU16(Opcode.GET_LOC, slotIndex);
            // Destructure and assign to local variables
            functionContext.patternCompiler.compile(pattern);
        }
    }

    /**
     * Emit destructuring code for pattern parameters after defaults and rest handling. Reads the argument value from
     * the synthetic parameter slot and destructures it.
     */
    void emitRestParameterBinding(RestParameter restParameter, CompilerContext functionContext) {
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
     * Compile a class body expression ensuring it executes in strict mode. If the enclosing function is sloppy and not
     * a generator/async, wraps the expression in a strict-mode wrapper function called inline. Otherwise, compiles
     * inline with compile-time strict mode set.
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
        BytecodeCompiler wrapperCompiler = compilerContext.newNestedCompiler(true, compilerContext.privateSymbols);
        CompilerContext wrapperContext = wrapperCompiler.context();
        compilerContext.functionExpressionCompiler.inheritVisibleWithObjectBindings(wrapperContext);
        wrapperContext.scopeManager.enterScope();
        wrapperContext.inGlobalScope = false;

        wrapperContext.expressionCompiler.compile(expression);
        wrapperContext.emitter.emitOpcode(Opcode.RETURN);

        int localCount = wrapperContext.scopeManager.isEmpty()
                ? wrapperContext.scopeManager.getMaxLocalCount()
                : wrapperContext.scopeManager.currentScope().getLocalCount();
        String[] localVarNames = wrapperContext.scopeManager.getLocalVarNames();
        Bytecode bytecode = wrapperContext.emitter.build(localCount, localVarNames);

        JSBytecodeFunction wrapperFunction = new JSBytecodeFunction(compilerContext.context, bytecode, "", 0,
                JSValue.NO_ARGS, null, false, false, false, false, true, null);
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

    /**
     * Carry the enclosing parameter environment's function-expression binding into a nested function, and on into
     * anything nested inside that.
     * <p>
     * Compiled references need nothing: they resolve to the enclosing slot by index. A direct {@code eval} does,
     * because it resolves by name on the frame it runs on — the closure's — and from there the enclosing function's
     * slot is reachable only through a capture. The name is captured whether or not the closure mentions it, since
     * whether it does is only knowable by reading a string at run time.
     *
     * @param targetContext
     *            the nested function's compilation context
     * @return the inherited bindings; empty when there are none
     */
    Set<String> inheritParameterScopeFunctionNameCapture(CompilerContext targetContext) {
        Set<String> parameterScopeFunctionNames = compilerContext.parameterScopeFunctionNames;
        if (!parameterScopeFunctionNames.isEmpty()) {
            targetContext.parameterScopeFunctionNames = parameterScopeFunctionNames;
            // Every one of them, not just the innermost: they are all still in scope here.
            for (String parameterScopeFunctionName : parameterScopeFunctionNames) {
                targetContext.captureResolver.resolveCapturedBindingIndex(parameterScopeFunctionName);
            }
        }
        return parameterScopeFunctionNames;
    }

    void inheritVisibleLexicalCapturesForDirectEvalInBody(CompilerContext targetContext, BlockStatement functionBody,
            boolean hasNonSimpleParameters) {
        if (hasNonSimpleParameters || !mayContainDirectEvalInBody(functionBody)) {
            return;
        }
        Set<String> visibleOuterBindingNames = new LinkedHashSet<>();
        for (CompilerScope compilerScope : compilerContext.scopeManager) {
            for (String localName : compilerScope.getLocals().keySet()) {
                if (localName == null || localName.startsWith("#")) {
                    continue;
                }
                visibleOuterBindingNames.add(localName);
            }
        }
        for (String bindingName : visibleOuterBindingNames) {
            if (targetContext.scopeManager.findLocalInScopes(bindingName) != null) {
                continue;
            }
            targetContext.captureResolver.resolveCapturedBindingIndex(bindingName);
        }
    }

    void inheritVisibleWithObjectBindings(CompilerContext functionContext) {
        functionContext.withObjectManager.addInheritedBindingNames(compilerContext.withObjectManager
                .getVisibleBindingNamesForNestedFunction(compilerContext.scopeManager));
    }

    <T extends ASTNode & FunctionNode> void initializeParameters(BytecodeCompiler functionCompiler, T node,
            List<Integer> parameterSlots, List<int[]> destructuringParams, boolean generator) {
        CompilerContext functionContext = functionCompiler.context();
        compilerContext.emitHelpers.emitDefaultParameterInit(functionCompiler, node.getFunctionParams(), parameterSlots,
                node);
        if (node.getRestParameter() != null) {
            functionContext.emitter.emitOpcode(Opcode.REST);
            functionContext.emitter.emitU16(node.getParams().size());
            emitRestParameterBinding(node.getRestParameter(), functionContext);
        }
        emitParameterDestructuring(node.getParams(), destructuringParams, functionContext);
        if (generator) {
            functionContext.emitter.emitOpcode(Opcode.INITIAL_YIELD);
        }
    }

    private boolean mayContainDirectEvalInBody(BlockStatement functionBody) {
        if (compilerContext.sourceCode == null || functionBody == null || functionBody.getLocation() == null) {
            return false;
        }
        int sourceLength = compilerContext.sourceCode.length();
        int startOffset = Math.max(0, functionBody.getLocation().offset());
        int endOffset = Math.min(sourceLength, Math.max(startOffset, functionBody.getLocation().endOffset()));
        if (endOffset <= startOffset) {
            return false;
        }
        String functionBodySourceSlice = compilerContext.sourceCode.substring(startOffset, endOffset);
        if (functionBodySourceSlice.contains("eval(")) {
            return true;
        } else {
            return functionBodySourceSlice.contains("eval (");
        }
    }

}
