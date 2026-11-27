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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.core.error.JSStackFrame;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.vm.StackFrame;
import com.caoccao.qjs4j.vm.VarRef;

import java.util.*;

/** The global eval function, including direct-eval caller scope and cross-realm execution. */
final class GlobalEval {
    private GlobalEval() {
    }

    /**
     * Analyze caller function var-environment names in one parse. bodyVarNames excludes parameter names.
     * functionVarEnvironmentNames includes parameter names and body var declarations.
     */
    private static CallerVarEnvironmentAnalysis analyzeCallerVarEnvironment(JSBytecodeFunction callerBytecodeFunction) {
        String source = callerBytecodeFunction.getSourceCode();
        if (source == null || source.isBlank()) {
            return null;
        }
        Expression functionLikeExpression = extractFunctionLikeExpression(source);
        if (!(functionLikeExpression instanceof FunctionNode function)) {
            return null;
        }
        Set<String> bodyVars = new HashSet<>();
        Set<String> constants = new HashSet<>();
        Set<String> names = function.getParameterNames();
        if (function.getRestParameter() != null) {
            names.addAll(function.getRestParameter().getArgument().getBoundNames());
        }
        ASTNode body = function instanceof FunctionExpression ordinary
                ? ordinary.getBody()
                : ((ArrowFunctionExpression) function).getBody();
        if (body instanceof BlockStatement block) {
            collectCallerBindings(block, bodyVars, constants);
        }
        names.addAll(bodyVars);
        return new CallerVarEnvironmentAnalysis(bodyVars, names, constants);
    }

    private static void collectCallerBindings(ASTNode node, Set<String> vars, Set<String> constants) {
        if (node instanceof FunctionDeclaration function && function.getId() != null) {
            vars.add(function.getId().getName());
        } else if (node instanceof VariableDeclaration declaration) {
            Set<String> target = switch (declaration.getKind()) {
                case VAR -> vars;
                case CONST -> constants;
                default -> null;
            };
            if (target != null) {
                for (VariableDeclarator binding : declaration.getDeclarations()) {
                    target.addAll(binding.getId().getBoundNames());
                }
            }
        } else if (node instanceof BlockStatement block) {
            collectCallerBindings(block.getBody(), vars, constants);
        } else if (node instanceof IfStatement branch) {
            collectCallerBindings(branch.getConsequent(), vars, constants);
            collectCallerBindings(branch.getAlternate(), vars, constants);
        } else if (node instanceof ForStatement loop) {
            collectCallerBindings(loop.getInit(), vars, constants);
            collectCallerBindings(loop.getBody(), vars, constants);
        } else if (node instanceof ForEachStatement loop) {
            collectCallerBindings(loop.getLeft(), vars, constants);
            collectCallerBindings(loop.getBody(), vars, constants);
        } else if (node instanceof WhileLoop loop) {
            collectCallerBindings(loop.getBody(), vars, constants);
        } else if (node instanceof SwitchStatement selection) {
            for (SwitchStatement.SwitchCase clause : selection.getCases()) {
                collectCallerBindings(clause.getConsequent(), vars, constants);
            }
        } else if (node instanceof TryStatement attempt) {
            collectCallerBindings(attempt.getBlock(), vars, constants);
            if (attempt.getHandler() != null) {
                if (attempt.getHandler().getParam() != null) {
                    vars.addAll(attempt.getHandler().getParam().getBoundNames());
                }
                collectCallerBindings(attempt.getHandler().getBody(), vars, constants);
            }
            collectCallerBindings(attempt.getFinalizer(), vars, constants);
        } else if (node instanceof LabeledStatement labeled) {
            collectCallerBindings(labeled.getBody(), vars, constants);
        }
    }

    private static void collectCallerBindings(List<Statement> statements, Set<String> vars, Set<String> constants) {
        for (Statement statement : statements) {
            collectCallerBindings(statement, vars, constants);
        }
    }

    private static boolean containsIdentifierReference(String sourceCode, String identifierName) {
        if (sourceCode == null || sourceCode.isEmpty() || identifierName == null || identifierName.isEmpty()) {
            return false;
        }
        int searchIndex = 0;
        while (searchIndex <= sourceCode.length() - identifierName.length()) {
            int matchIndex = sourceCode.indexOf(identifierName, searchIndex);
            if (matchIndex < 0) {
                return false;
            }
            int beforeIndex = matchIndex - 1;
            int afterIndex = matchIndex + identifierName.length();
            boolean hasIdentifierCharBefore = beforeIndex >= 0 && isIdentifierPartAscii(sourceCode.charAt(beforeIndex));
            boolean hasIdentifierCharAfter = afterIndex < sourceCode.length()
                    && isIdentifierPartAscii(sourceCode.charAt(afterIndex));
            if (!hasIdentifierCharBefore && !hasIdentifierCharAfter) {
                return true;
            }
            searchIndex = matchIndex + identifierName.length();
        }
        return false;
    }

    static JSValue evaluate(JSContext realmContext, JSContext callerContext, JSValue[] args,
            boolean forceDirectEvalCall) {
        JSValue x = JSValue.argumentOrUndefined(args, 0);

        // If x is not a string, return it unchanged
        if (!(x instanceof JSString)) {
            return x;
        }

        String code = ((JSString) x).value();
        boolean isDirectEvalCall = forceDirectEvalCall;
        boolean isSameRealm = (realmContext == callerContext);
        boolean shouldUseCallerFrameSemantics = isDirectEvalCall && isSameRealm;

        // Scope overlay: capture enclosing function's local variables onto the global
        // object so that eval code's GET_VAR/PUT_VAR can access them.
        StackFrame callerFrame = callerContext.getVirtualMachine().getCurrentFrame();
        boolean hasSameRealmCallerFrame = isSameRealm && callerFrame != null;
        // Eval is "inside a function" only if same-realm and the callerFrame is NOT the top-level program.
        boolean isEvalInFunction = shouldUseCallerFrameSemantics && hasSameRealmCallerFrame
                && callerFrame.getFunction() instanceof JSBytecodeFunction && callerFrame.getCaller() != null;
        boolean shouldOverlayLocals = isEvalInFunction;
        boolean shouldOverlayTopLevelLexicals = shouldUseCallerFrameSemantics && hasSameRealmCallerFrame
                && !isEvalInFunction && callerFrame.getFunction() instanceof JSBytecodeFunction;
        boolean inheritedStrictMode = shouldUseCallerFrameSemantics && callerFrame != null
                && callerFrame.getFunction() instanceof JSBytecodeFunction bytecodeFunction
                && bytecodeFunction.isStrict();
        String[] localVarNames = null;
        Map<String, Integer> localVarNameToIndex = null;
        Set<String> localVarNameSet = null;
        Map<String, Integer> capturedVarOverlaySlots = null;
        EvalGlobalOverlay overlay = null;
        Set<String> constantOverlayBindingNames = new HashSet<>();
        Set<String> evalVarDeclarations = null;
        Set<String> evalLexDeclarations = null;
        Set<String> evalFunctionDeclarations = null;
        Set<String> functionVarEnvironmentNames = null;
        Set<String> constLexicalNames = null;
        CallerVarEnvironmentAnalysis callerVarEnvironmentAnalysis = null;
        boolean parsedEvalDeclarations = false;
        boolean evalCodeStrict = inheritedStrictMode;
        boolean overlayStatePushed = false;
        JSObject global = realmContext.getGlobalObject();
        JSBytecodeFunction callerBytecodeFunction = callerFrame != null
                && callerFrame.getFunction() instanceof JSBytecodeFunction bytecodeFunction ? bytecodeFunction : null;
        // Track function expression name binding index so eval cannot overwrite it.
        // Per ES2024 14.1.2 step 27, the binding is immutable (TypeError in strict,
        // silently ignored in sloppy mode).
        int functionNameLocalIndex = callerBytecodeFunction != null ? callerBytecodeFunction.getSelfLocalIndex() : -1;
        // The named-function-expression bindings this frame resolves through a parameter
        // environment rather than through an ordinary local. The two are kept apart because
        // they are not the same claim, and conflating them was a defect: a name is not a
        // binding identity, so an *inherited* name says nothing about a binding of the same
        // spelling that this frame declares itself. A nested closure written in an enclosing
        // initializer may have its own parameter, `var`, lexical, class or function called `n`,
        // and that nearer binding shadows the function-expression one and is mutable — but
        // membership in a set of names classified it as the immutable one, so direct eval's
        // assignment to it was discarded. The frame's *own* name is a binding of this frame, so
        // it stays authoritative. See EvalGlobalOverlay.bindParameterFunction.
        String activeOwnParameterScopeFunctionName = callerBytecodeFunction == null
                ? null
                : callerBytecodeFunction.getActiveParameterScopeFunctionName(callerFrame);
        Set<String> inheritedParameterScopeFunctionNames = callerBytecodeFunction == null
                ? Set.of()
                : callerBytecodeFunction.getInheritedParameterScopeFunctionNames();

        // When eval runs inside a function, snapshot global property names so we can
        // clean up var/function bindings that should be function-scoped (not global).
        Set<String> globalKeysBefore = null;
        if (hasSameRealmCallerFrame) {
            globalKeysBefore = new HashSet<>();
            for (PropertyKey propertyKey : global.ownPropertyKeys()) {
                if (propertyKey.isString()) {
                    globalKeysBefore.add(propertyKey.asString());
                }
            }
        }
        if (callerBytecodeFunction != null && shouldOverlayLocals) {
            callerVarEnvironmentAnalysis = analyzeCallerVarEnvironment(callerBytecodeFunction);
            if (callerVarEnvironmentAnalysis != null) {
                functionVarEnvironmentNames = callerVarEnvironmentAnalysis.functionVarEnvironmentNames();
                constLexicalNames = callerVarEnvironmentAnalysis.constLexicalNames();
            }
        }

        if ((shouldOverlayLocals || shouldOverlayTopLevelLexicals) && callerBytecodeFunction != null
                && callerBytecodeFunction.getBytecode().getLocalVarNames() != null) {
            localVarNames = callerBytecodeFunction.getBytecode().getLocalVarNames();
            localVarNameToIndex = new HashMap<>();
            localVarNameSet = new HashSet<>();
            capturedVarOverlaySlots = new LinkedHashMap<>();
            overlay = new EvalGlobalOverlay(global);
            JSValue[] locals = callerFrame.getLocals();
            for (int i = 0; i < localVarNames.length && i < locals.length; i++) {
                String name = localVarNames[i];
                if (name == null) {
                    continue;
                }
                if (name.startsWith("#")) {
                    continue;
                }
                if (shouldOverlayTopLevelLexicals && locals[i] instanceof JSSymbol symbolValue
                        && "UninitializedMarker".equals(symbolValue.getDescription())) {
                    continue;
                }
                if (shouldOverlayTopLevelLexicals && !callerContext.hasGlobalLexDeclaration(name)) {
                    boolean localBindingIsUndefined = locals[i] == null || locals[i] == JSUndefined.INSTANCE;
                    boolean evalSourceMentionsBinding = containsIdentifierReference(code, name);
                    if (localBindingIsUndefined || !evalSourceMentionsBinding) {
                        continue;
                    }
                }
                localVarNameSet.add(name);
                localVarNameToIndex.putIfAbsent(name, i);
                overlay.bind(name, locals[i]);
                // Function name bindings are immutable: make the overlay property
                // non-writable so PUT_VAR throws TypeError in strict mode and
                // silently fails in sloppy mode.
                boolean isFunctionNameBinding = functionNameLocalIndex >= 0 && i == functionNameLocalIndex;
                boolean isConstLexicalBinding = shouldOverlayLocals && constLexicalNames != null
                        && constLexicalNames.contains(name);
                if (isFunctionNameBinding || isConstLexicalBinding) {
                    overlay.makeImmutable(name, locals[i]);
                    if (isConstLexicalBinding && !isFunctionNameBinding) {
                        constantOverlayBindingNames.add(name);
                    }
                }
            }
            if (shouldOverlayLocals) {
                IdentityHashMap<StackFrame, Boolean> visitedLexicalFrames = new IdentityHashMap<>();
                if (callerBytecodeFunction.isEvalDynamicScopeLookupEnabled()
                        && callerBytecodeFunction.getEvalDynamicScopeFrame() != null) {
                    StackFrame evalScopeFrame = callerBytecodeFunction.getEvalDynamicScopeFrame();
                    while (evalScopeFrame != null && visitedLexicalFrames.put(evalScopeFrame, Boolean.TRUE) == null) {
                        if (evalScopeFrame != callerFrame) {
                            overlay.bindFrameLocals(evalScopeFrame, localVarNameSet);
                            overlay.bindDynamicVariables(evalScopeFrame);
                        }
                        evalScopeFrame = evalScopeFrame.getCaller();
                    }
                }
                VarRef[] callerVarRefs = callerBytecodeFunction.getVarRefs();
                if (callerVarRefs != null) {
                    int selfCaptureIndex = callerBytecodeFunction.getSelfCaptureIndex();
                    for (int captureSlot = 0; captureSlot < callerVarRefs.length; captureSlot++) {
                        String capturedVarName = callerBytecodeFunction.getCapturedVarName(captureSlot);
                        if (capturedVarName == null || capturedVarName.startsWith("#")) {
                            continue;
                        }
                        if (localVarNameSet.contains(capturedVarName)) {
                            continue;
                        }
                        JSValue capturedValue = callerFrame.getVarRef(captureSlot);
                        overlay.bind(capturedVarName, capturedValue);
                        capturedVarOverlaySlots.put(capturedVarName, captureSlot);
                        // Both captures represent immutable function-expression bindings.
                        if (inheritedParameterScopeFunctionNames.contains(capturedVarName)
                                || selfCaptureIndex >= 0 && captureSlot == selfCaptureIndex) {
                            overlay.makeImmutable(capturedVarName, capturedValue);
                        }
                    }
                }
                // ES2024 15.2.5: a default initializer runs in the parameter environment, whose
                // outer environment holds the named function expression's own binding. A `var`,
                // lexical, class or function declaration of that name in the body is a
                // different binding, and it does not exist yet. Both are slots of one flattened
                // frame and the name maps to the body one, so while the frame is still
                // initializing parameters the name has to be pointed back at the function.
                overlay.bindParameterFunction(callerFrame, callerBytecodeFunction);
                overlay.bindDynamicVariables(callerFrame);
                List<WithObjectCandidate> withObjectCandidates = new ArrayList<>();
                JSValue[] callerLocals = callerFrame.getLocals();
                for (int i = 0; i < localVarNames.length && i < callerLocals.length; i++) {
                    String name = localVarNames[i];
                    JSValue localValue = callerLocals[i];
                    if (name == null || localValue == null || localValue == JSUndefined.INSTANCE) {
                        continue;
                    }
                    if (!(localValue instanceof JSObject withObject) || !name.startsWith("#withObject")) {
                        continue;
                    }
                    withObjectCandidates.add(new WithObjectCandidate(parseWithDepth(name), withObject));
                }
                withObjectCandidates.sort(Comparator.comparingInt(WithObjectCandidate::depth));
                for (WithObjectCandidate withObjectCandidate : withObjectCandidates) {
                    JSObject withObject = withObjectCandidate.object();
                    for (PropertyKey propertyKey : withObject.ownPropertyKeys()) {
                        if (!propertyKey.isString()) {
                            continue;
                        }
                        String keyName = propertyKey.asString();
                        overlay.bind(keyName, withObject.get(propertyKey));
                    }
                }
            }
        }

        constantOverlayBindingNames.remove(activeOwnParameterScopeFunctionName);

        // This eval's lexical environment, nested in the caller's when the caller is itself eval code. The
        // compiled eval code and every function created inside it carry it; only they may consult the overlay.
        JSEvalEnvironment evalEnvironment = new JSEvalEnvironment(
                callerFrame != null && callerFrame.getFunction() != null
                        ? callerFrame.getFunction().getEvalEnvironment()
                        : null);
        realmContext.setPendingDirectEvalEnvironment(evalEnvironment);
        if (overlay != null) {
            overlay.push(evalEnvironment, constantOverlayBindingNames);
            overlayStatePushed = true;
        }

        try {
            if (hasSameRealmCallerFrame) {
                try {
                    Compiler evalCompiler = new Compiler(code, "<eval>").setContext(realmContext);
                    Program evalAst = evalCompiler.parse(false);
                    Program.GlobalDeclarations globalDeclarations = evalAst.getGlobalDeclarations();
                    evalVarDeclarations = globalDeclarations.varDeclarations();
                    evalLexDeclarations = globalDeclarations.lexicalDeclarations();
                    evalFunctionDeclarations = globalDeclarations.functionDeclarations();
                    parsedEvalDeclarations = true;
                    evalCodeStrict = evalCodeStrict || evalAst.isStrict();
                } catch (Exception ignored) {
                    // Parse error in eval code - let the normal eval path report it.
                }
            }

            // EvalDeclarationInstantiation: check if eval code declares "var arguments"
            // in a function with parameter expressions. Per QuickJS add_arguments_arg(),
            // a lexical "arguments" binding exists in the argument scope when the function
            // has parameter expressions (non-strict mode), preventing eval from redeclaring it.
            if (isEvalInFunction && callerBytecodeFunction != null && callerBytecodeFunction.hasParameterExpressions()
                    && !callerBytecodeFunction.isStrict() && !evalCodeStrict && parsedEvalDeclarations
                    && evalVarDeclarations != null && evalVarDeclarations.contains("arguments")) {
                if (!callerBytecodeFunction.isArrow() || callerBytecodeFunction.hasArgumentsParameterBinding()) {
                    throw new JSException(
                            realmContext.throwError("SyntaxError", "Identifier 'arguments' has already been declared"));
                }
            }
            // EvalDeclarationInstantiation: in functions with parameter expressions,
            // eval cannot create a var with the same name as a parameter.
            // Per spec, the parameter scope is separate from the var scope,
            // so "var x" in eval conflicts with parameter "x".
            if (isEvalInFunction && callerBytecodeFunction != null && callerBytecodeFunction.hasParameterExpressions()
                    && !callerBytecodeFunction.isStrict() && !evalCodeStrict && parsedEvalDeclarations
                    && evalVarDeclarations != null && localVarNameSet != null) {
                callerVarEnvironmentAnalysis = analyzeCallerVarEnvironment(callerBytecodeFunction);
                Set<String> bodyVarNames = callerVarEnvironmentAnalysis != null
                        ? callerVarEnvironmentAnalysis.bodyVarNames()
                        : null;
                if (bodyVarNames != null) {
                    for (String evalVar : evalVarDeclarations) {
                        if (JSKeyword.ARGUMENTS.equals(evalVar)) {
                            continue;
                        }
                        if (localVarNameSet.contains(evalVar) && !bodyVarNames.contains(evalVar)) {
                            throw new JSException(realmContext.throwError("SyntaxError",
                                    "Identifier '" + evalVar + "' has already been declared"));
                        }
                    }
                }
            }
            // EvalDeclarationInstantiation step 5 is conditioned on the strictness of the
            // *eval code*, not of the caller. A direct eval in a strict function is strict by
            // inheritance and is already excluded by !evalCodeStrict; an indirect eval is
            // never strict by inheritance, so testing the caller's strictness here silently
            // dropped the global-lexical collision check for `(0,eval)('var x')` written
            // inside a strict script.
            if (hasSameRealmCallerFrame && !evalCodeStrict && parsedEvalDeclarations && evalVarDeclarations != null) {
                if (callerBytecodeFunction != null && callerFrame != null && callerFrame.getCaller() != null) {
                    if (callerVarEnvironmentAnalysis == null) {
                        callerVarEnvironmentAnalysis = analyzeCallerVarEnvironment(callerBytecodeFunction);
                    }
                    functionVarEnvironmentNames = callerVarEnvironmentAnalysis != null
                            ? callerVarEnvironmentAnalysis.functionVarEnvironmentNames()
                            : null;
                }
                for (String declarationName : evalVarDeclarations) {
                    if (isEvalInFunction && JSKeyword.ARGUMENTS.equals(declarationName)
                            && callerBytecodeFunction != null) {
                        continue;
                    }
                    if (callerBytecodeFunction != null && callerBytecodeFunction.isArrow()
                            && JSKeyword.ARGUMENTS.equals(declarationName)
                            && !callerBytecodeFunction.hasArgumentsParameterBinding()) {
                        continue;
                    }
                    // An indirect eval's variable environment is the global environment
                    // wherever the call appears, so the global-lexical collision check applies
                    // even when the calling frame is a function. Only a direct eval has to be
                    // at top level for that to be true.
                    if (callerFrame != null && (!isDirectEvalCall || callerFrame.getCaller() == null)
                            && callerContext.hasGlobalLexDeclaration(declarationName)) {
                        throw new JSException(realmContext.throwError("SyntaxError",
                                "Identifier '" + declarationName + "' has already been declared"));
                    }
                    if (localVarNameSet != null && localVarNameSet.contains(declarationName)
                            && functionVarEnvironmentNames != null
                            && !functionVarEnvironmentNames.contains(declarationName)) {
                        throw new JSException(realmContext.throwError("SyntaxError",
                                "Identifier '" + declarationName + "' has already been declared"));
                    }
                }
            }

            if (isEvalInFunction && parsedEvalDeclarations && evalVarDeclarations != null && overlay != null) {
                for (String declarationName : evalVarDeclarations) {
                    boolean shadowsCallerLocal = localVarNameSet != null && localVarNameSet.contains(declarationName);
                    if (shadowsCallerLocal) {
                        if (!evalCodeStrict && callerFrame != null && localVarNameToIndex != null) {
                            Integer localIndex = localVarNameToIndex.get(declarationName);
                            if (localIndex != null && functionVarEnvironmentNames != null
                                    && functionVarEnvironmentNames.contains(declarationName)
                                    && (functionNameLocalIndex < 0 || localIndex != functionNameLocalIndex)) {
                                callerFrame.setDynamicVarBindingAlias(declarationName, localIndex);
                            }
                        }
                        continue;
                    }
                    PropertyKey declarationKey = PropertyKey.fromString(declarationName);
                    JSValue initialValue;
                    if (isEvalInFunction) {
                        JSValue dynamicVarBindingValue = callerFrame != null
                                ? callerFrame.getDynamicVarBinding(declarationName)
                                : null;
                        if (dynamicVarBindingValue != null) {
                            initialValue = dynamicVarBindingValue;
                        } else {
                            // Direct eval var declarations in functions create or reuse
                            // function var-environment bindings, not global properties.
                            initialValue = JSUndefined.INSTANCE;
                        }
                    } else if (overlay.wasAbsent(declarationName)) {
                        // Outer lexical overlays should not initialize eval-introduced var bindings.
                        // Var declarations created by eval start as undefined in the caller var environment.
                        initialValue = JSUndefined.INSTANCE;
                    } else {
                        initialValue = global.has(declarationKey) ? global.get(declarationKey) : JSUndefined.INSTANCE;
                    }
                    overlay.bind(declarationName, initialValue);
                    if (!evalCodeStrict && callerFrame != null) {
                        callerFrame.setDynamicVarBinding(declarationName, initialValue);
                    }
                }
            }

            if (!isDirectEvalCall && !evalCodeStrict && parsedEvalDeclarations) {
                if (evalFunctionDeclarations != null) {
                    Map<String, PropertyDescriptor> existingFunctionDescriptors = new HashMap<>();
                    for (String functionDeclarationName : evalFunctionDeclarations) {
                        existingFunctionDescriptors.put(functionDeclarationName,
                                GlobalFunctionBindings.checkFunction(global, functionDeclarationName));
                    }
                    for (String functionDeclarationName : evalFunctionDeclarations) {
                        GlobalFunctionBindings.prepareEvalFunction(global, functionDeclarationName,
                                existingFunctionDescriptors.get(functionDeclarationName));
                    }
                }
                if (evalVarDeclarations != null) {
                    GlobalFunctionBindings.checkEvalVars(global, evalVarDeclarations, evalFunctionDeclarations);
                }
            }

            JSGlobalObject.EvalOverlaySnapshot suspendedOverlaySnapshot = null;
            if (!isDirectEvalCall) {
                suspendedOverlaySnapshot = realmContext.suspendEvalOverlays();
                realmContext.pushEvalOverlayLookupSuppression();
            }
            String evalFilename = "<eval>";
            JSStackFrame callerStackFrame = callerContext.getCurrentStackFrame();
            if (callerStackFrame != null && callerStackFrame.filename() != null
                    && !callerStackFrame.filename().isEmpty()) {
                evalFilename = callerStackFrame.filename();
            }
            JSValue result = isDirectEvalCall
                    ? realmContext.evalDirectInternal(code, evalFilename, inheritedStrictMode)
                    : realmContext.evalIndirectInternal(code, evalFilename);
            if (!isDirectEvalCall) {
                realmContext.popEvalOverlayLookupSuppression();
            }
            if (suspendedOverlaySnapshot != null) {
                realmContext.resumeEvalOverlays(suspendedOverlaySnapshot);
            }
            if (result == null) {
                JSValue error = realmContext.getPendingException();
                if (error != null) {
                    realmContext.clearPendingException();
                    callerContext.setPendingException(error);
                }
                return JSUndefined.INSTANCE;
            }

            // Copy modified values back to caller's locals
            if (localVarNames != null) {
                JSValue[] locals = callerFrame.getLocals();
                for (int i = 0; i < localVarNames.length && i < locals.length; i++) {
                    String name = localVarNames[i];
                    if (name == null) {
                        continue;
                    }
                    if (name.startsWith("#")) {
                        continue;
                    }
                    if (evalCodeStrict && evalVarDeclarations != null && evalVarDeclarations.contains(name)) {
                        continue;
                    }
                    // Skip function name bindings — they are immutable and
                    // the non-writable overlay property prevented modification.
                    if (functionNameLocalIndex >= 0 && i == functionNameLocalIndex) {
                        continue;
                    }
                    // While the parameter environment is what the name resolves through, the
                    // overlay holds the function, not the body's binding of the same name.
                    // Copying it back would put the function into the body's slot.
                    //
                    // Only this frame's own name. Every name in this loop is a local of this
                    // frame, and a local is a nearer binding than anything an enclosing
                    // parameter environment provides, so an inherited name here belongs to a
                    // binding that is being shadowed. Skipping those discarded the write: a
                    // nested closure's `let n = 1; eval('n = 2')` left n at 1.
                    if (name.equals(activeOwnParameterScopeFunctionName)) {
                        continue;
                    }
                    PropertyKey key = PropertyKey.fromString(name);
                    if (global.has(key)) {
                        locals[i] = global.get(key);
                    }
                }
            }
            if (capturedVarOverlaySlots != null && callerBytecodeFunction != null) {
                int selfCaptureIndex = callerBytecodeFunction.getSelfCaptureIndex();
                for (Map.Entry<String, Integer> entry : capturedVarOverlaySlots.entrySet()) {
                    String name = entry.getKey();
                    int captureSlot = entry.getValue();
                    if (evalCodeStrict && evalVarDeclarations != null && evalVarDeclarations.contains(name)) {
                        continue;
                    }
                    if (selfCaptureIndex >= 0 && captureSlot == selfCaptureIndex) {
                        continue;
                    }
                    // Immutable, like any other named-function-expression binding. A capture is
                    // only reached for a name this frame has no local of, so the entry really
                    // does describe the binding the overlay holds.
                    if (inheritedParameterScopeFunctionNames.contains(name)) {
                        continue;
                    }
                    PropertyKey key = PropertyKey.fromString(name);
                    if (global.has(key)) {
                        callerFrame.setVarRef(captureSlot, global.get(key));
                    }
                }
            }
            if (isEvalInFunction && !evalCodeStrict && parsedEvalDeclarations && evalVarDeclarations != null
                    && callerFrame != null) {
                for (String declarationName : evalVarDeclarations) {
                    if (localVarNameSet != null && localVarNameSet.contains(declarationName)) {
                        continue;
                    }
                    PropertyKey key = PropertyKey.fromString(declarationName);
                    if (global.has(key)) {
                        callerFrame.setDynamicVarBinding(declarationName, global.get(key));
                    }
                }
            }

            if (!isDirectEvalCall && !evalCodeStrict && evalFunctionDeclarations != null) {
                GlobalFunctionBindings.finish(global, evalFunctionDeclarations, true);
            }

            return result;
        } catch (JSException e) {
            // Propagate exception to the caller's context so the caller's VM sees it
            callerContext.setPendingException(e.getErrorValue());
            return JSUndefined.INSTANCE;
        } finally {
            if (overlay != null) {
                boolean keepGlobalVars = callerFrame != null && callerFrame.getCaller() == null && !evalCodeStrict
                        && parsedEvalDeclarations;
                overlay.restore(keepGlobalVars ? evalVarDeclarations : null, callerContext);
            }
            // Clean up eval-created bindings that should be function-scoped (not global).
            // Per ES2024 B.3.3.3, var/function declarations in eval inside a function
            // go to the function's variable environment, not the global.
            if (hasSameRealmCallerFrame && globalKeysBefore != null && parsedEvalDeclarations
                    && evalVarDeclarations != null && (isEvalInFunction || evalCodeStrict)) {
                for (String declarationName : evalVarDeclarations) {
                    if (globalKeysBefore.contains(declarationName)) {
                        continue;
                    }
                    if (localVarNameSet != null && localVarNameSet.contains(declarationName)) {
                        continue;
                    }
                    global.delete(PropertyKey.fromString(declarationName));
                }
            }
            if (globalKeysBefore != null && parsedEvalDeclarations && evalLexDeclarations != null) {
                for (String declarationName : evalLexDeclarations) {
                    if (globalKeysBefore.contains(declarationName)) {
                        continue;
                    }
                    global.delete(PropertyKey.fromString(declarationName));
                }
            }
            if (overlayStatePushed) {
                realmContext.popEvalOverlay();
            }
            // For functions with parameter expressions, eval-created vars need to
            // persist on the global object so closures created in parameter defaults
            // can see them. Per spec, these vars go into the parameter scope's var
            // environment, which closures in defaults close over.
            if (isEvalInFunction && callerBytecodeFunction != null && callerBytecodeFunction.hasParameterExpressions()
                    && !evalCodeStrict && parsedEvalDeclarations && evalVarDeclarations != null
                    && callerFrame != null) {
                for (String declarationName : evalVarDeclarations) {
                    if (localVarNameSet != null && localVarNameSet.contains(declarationName)) {
                        continue;
                    }
                    JSValue dynValue = callerFrame.getDynamicVarBinding(declarationName);
                    if (dynValue != null) {
                        global.set(PropertyKey.fromString(declarationName), dynValue);
                    }
                }
            }
        }
    }

    private static Expression extractFunctionLikeExpression(String source) {
        Expression directExpression = parseTopLevelExpression("(" + source + ")");
        if (directExpression instanceof FunctionExpression || directExpression instanceof ArrowFunctionExpression) {
            return directExpression;
        }

        Expression objectLiteralExpression = parseTopLevelExpression("({" + source + "})");
        if (!(objectLiteralExpression instanceof ObjectExpression objectExpression)
                || objectExpression.getProperties().isEmpty()) {
            return null;
        }
        ObjectExpressionProperty firstProperty = objectExpression.getProperties().get(0);
        if (firstProperty.getValue() instanceof FunctionExpression functionExpression) {
            return functionExpression;
        }
        return null;
    }

    private static boolean isIdentifierPartAscii(char character) {
        if (character >= 'a' && character <= 'z') {
            return true;
        }
        if (character >= 'A' && character <= 'Z') {
            return true;
        }
        if (character >= '0' && character <= '9') {
            return true;
        }
        return character == '_' || character == '$';
    }

    private static Expression parseTopLevelExpression(String wrappedSource) {
        try {
            Program parsedProgram = new Compiler(wrappedSource, "<eval-caller>").parse(false);
            if (parsedProgram.getBody().isEmpty()
                    || !(parsedProgram.getBody().get(0) instanceof ExpressionStatement expressionStatement)) {
                return null;
            }
            return expressionStatement.getExpression();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int parseWithDepth(String localName) {
        int depth = 0;
        int index = "$withObject".length();
        while (index < localName.length()) {
            char current = localName.charAt(index);
            if (current < '0' || current > '9') {
                return depth;
            }
            depth = depth * 10 + (current - '0');
            index++;
        }
        return depth;
    }

    private record CallerVarEnvironmentAnalysis(Set<String> bodyVarNames, Set<String> functionVarEnvironmentNames,
            Set<String> constLexicalNames) {
    }

    private record WithObjectCandidate(int depth, JSObject object) {
    }
}
