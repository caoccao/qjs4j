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
import com.caoccao.qjs4j.vm.Bytecode;
import com.caoccao.qjs4j.vm.Opcode;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared utilities for function compilation: parameter handling,
 * tagged template objects, strict-mode wrapping, and inheritance helpers.
 */
final class FunctionCompiler {
    private final CompilerContext compilerContext;

    FunctionCompiler(CompilerContext compilerContext) {
        this.compilerContext = compilerContext;
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

    JSArray createTaggedTemplateObject(TemplateLiteral template) {
        List<String> cookedQuasis = template.getQuasis();
        List<String> rawQuasis = template.getRawQuasis();
        int segmentCount = rawQuasis.size();

        JSArray templateObject = new JSArray(compilerContext.context);
        JSArray rawArray = new JSArray(compilerContext.context);

        for (int i = 0; i < segmentCount; i++) {
            JSString rawValue = new JSString(rawQuasis.get(i));
            rawArray.set(i, rawValue);
            rawArray.defineProperty(
                    PropertyKey.fromIndex(i),
                    PropertyDescriptor.dataDescriptor(rawValue, PropertyDescriptor.DataState.Enumerable));

            String cookedQuasi = cookedQuasis.get(i);
            JSValue cookedValue = cookedQuasi == null ? JSUndefined.INSTANCE : new JSString(cookedQuasi);
            templateObject.set(i, cookedValue);
            templateObject.defineProperty(
                    PropertyKey.fromIndex(i),
                    PropertyDescriptor.dataDescriptor(cookedValue, PropertyDescriptor.DataState.Enumerable));
        }

        // QuickJS/spec attributes for template objects.
        rawArray.defineProperty(PropertyKey.fromString("length"), JSNumber.of(segmentCount), PropertyDescriptor.DataState.None);
        templateObject.defineProperty(PropertyKey.fromString("length"), JSNumber.of(segmentCount), PropertyDescriptor.DataState.None);
        templateObject.defineProperty(PropertyKey.fromString("raw"), rawArray, PropertyDescriptor.DataState.None);

        rawArray.freeze();
        templateObject.freeze();
        return templateObject;
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
            functionContext.patternCompiler.compilePatternAssignment(pattern);
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
            functionContext.patternCompiler.compilePatternAssignment(restParameter.getArgument());
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
                compilerContext.expressionCompiler.compileExpression(expression);
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

        wrapperContext.expressionCompiler.compileExpression(expression);
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
