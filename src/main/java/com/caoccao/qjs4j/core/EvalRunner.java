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

import com.caoccao.qjs4j.compilation.ast.Program;
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.exceptions.*;
import com.caoccao.qjs4j.vm.StackFrame;
import com.caoccao.qjs4j.vm.VarRef;

import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;

/**
 * Runs source: QuickJS's {@code JS_EvalInternal}, as a class.
 * <p>
 * Every public {@code eval} overload on {@link JSContext} funnels through the one private method
 * here, which is the only place the lifecycle guard, the stack-depth check and the exception
 * translation live. What that method does is laid out phase by phase in {@code EvalActivation}: an
 * activation object rather than a six-hundred-line body, because the phases share a great deal of
 * state and the order they run in is the specification's, not an implementation detail.
 * <p>
 * It also owns the two pieces of state that only mean anything between a call site and the eval it
 * is about to perform — whether the next {@code eval(...)} call is a syntactic direct eval, and
 * whether it sits in a class field initializer — because the compiler emits the call before the VM
 * makes it, and the flag is how the two agree on which it was.
 */
final class EvalRunner {
    private final JSContext context;
    private final ModuleSourceTransformer transformer;
    private boolean pendingClassFieldEval;
    private int pendingDirectEvalCalls;

    EvalRunner(JSContext context, ModuleSourceTransformer transformer) {
        this.context = context;
        this.transformer = transformer;
        this.pendingClassFieldEval = false;
        this.pendingDirectEvalCalls = 0;
    }

    Map<String, JSSymbol> collectEvalPrivateSymbols(JSBytecodeFunction callerFunction) {
        if (callerFunction == null) {
            return Map.of();
        }
        LinkedHashMap<String, JSSymbol> privateSymbolsByName = new LinkedHashMap<>();
        IdentityHashMap<JSSymbol, JSSymbol> symbolRemap = callerFunction.getClassPrivateSymbolRemap();
        if (symbolRemap != null && !symbolRemap.isEmpty()) {
            for (Map.Entry<JSSymbol, JSSymbol> entry : symbolRemap.entrySet()) {
                JSSymbol templateSymbol = entry.getKey();
                if (templateSymbol == null) {
                    continue;
                }
                String description = templateSymbol.getDescription();
                if (description == null || description.length() < 2 || description.charAt(0) != '#') {
                    continue;
                }
                String privateName = description.substring(1);
                JSSymbol activeSymbol = entry.getValue() != null ? entry.getValue() : templateSymbol;
                privateSymbolsByName.putIfAbsent(privateName, activeSymbol);
            }
        }
        if (!privateSymbolsByName.isEmpty()) {
            return privateSymbolsByName;
        }
        Set<JSSymbol> classPrivateSymbols = callerFunction.getClassPrivateSymbols();
        if (classPrivateSymbols == null || classPrivateSymbols.isEmpty()) {
            return Map.of();
        }
        for (JSSymbol symbol : classPrivateSymbols) {
            if (symbol == null) {
                continue;
            }
            String description = symbol.getDescription();
            if (description == null || description.length() < 2 || description.charAt(0) != '#') {
                continue;
            }
            privateSymbolsByName.putIfAbsent(description.substring(1), symbol);
        }
        return privateSymbolsByName;
    }

    boolean consumeScheduledClassFieldEvalCall() {
        boolean result = pendingClassFieldEval;
        pendingClassFieldEval = false;
        return result;
    }

    boolean consumeScheduledDirectEvalCall() {
        if (pendingDirectEvalCalls > 0) {
            pendingDirectEvalCalls--;
            return true;
        }
        return false;
    }

    JSValue eval(String code, String filename, boolean isModule, boolean isDirectEval,
                 boolean predeclareProgramLexicalsAsLocals,
                 boolean skipGlobalDeclarationTracking,
                 boolean inheritedStrictModeForDirectEval,
                 boolean useDirectEvalCallerFrame) {
        // The single gateway every public eval overload funnels through, so the lifecycle check
        // belongs here. Duplicating it in selected overloads left eval(code, filename, isModule,
        // isDirectEval) unguarded: a closed context ran the source, mutated the realm, and only
        // then failed inside the automatic microtask drain — after the side effects had landed.
        context.requireOpen();
        if (code == null || code.isEmpty()) {
            return JSUndefined.INSTANCE;
        }
        // Check for recursion limit
        if (!context.pushStackFrame(new JSStackFrame("<eval>", filename, 1))) {
            return context.throwError("RangeError", "Maximum call stack size exceeded");
        }
        EvalActivation activation = new EvalActivation(
                code, filename, isModule, isDirectEval,
                predeclareProgramLexicalsAsLocals, skipGlobalDeclarationTracking,
                inheritedStrictModeForDirectEval, useDirectEvalCallerFrame);
        try {
            return activation.run();
        } catch (JSException e) {
            activation.evalError = e.getErrorValue();
            return null;
        } catch (JSSyntaxErrorException e) {
            activation.evalError = context.throwSyntaxError(e.getMessage(), e.getSourceLocation());
            return null;
        } catch (JSCompilerException e) {
            activation.evalError = context.throwSyntaxError(e.getMessage(), e.getSourceLocation());
            return null;
        } catch (JSVirtualMachineException e) {
            if (e.getJsError() != null) {
                activation.evalError = e.getJsError();
            } else if (e.getJsValue() != null) {
                activation.evalError = e.getJsValue();
            } else if (context.hasPendingException()) {
                activation.evalError = context.getPendingException();
            } else {
                activation.evalError = context.throwError("VM error: " + e.getMessage());
            }
            return null;
        } catch (JSErrorException e) {
            activation.evalError = context.throwError(e);
            return null;
        } catch (Exception e) {
            activation.evalError = context.throwError("Execution error: " + e.getMessage());
            return null;
        } finally {
            activation.release();
        }
    }

    /**
     * Convert a null return from the private eval() into a JSException throw.
     * Used by all public eval methods to maintain the throwing API contract.
     */
    JSValue evalOrThrow(JSValue result) {
        if (result == null) {
            JSValue error = context.getPendingException();
            context.clearPendingException();
            throw new JSException(error);
        }
        return result;
    }

    /**
     * Process all module imports in source order, handling side-effect, namespace, and binding
     * imports together. This ensures deferred modules' async dependencies are pre-evaluated
     * in the correct position relative to other imports.
     */
    EvalOverlayManager.Frame evaluateModuleImportsInOrder(String code, String filename) {
        String scanCode = transformer.maskModuleComments(code);
        JSObject globalObject = context.getGlobalObject();
        Map<String, JSValue> savedGlobals = new HashMap<>();
        Set<String> absentKeys = new HashSet<>();

        // Collect all import matches with their positions for ordered processing
        List<int[]> importPositions = new ArrayList<>();
        // type 0=side-effect, 1=namespace, 2=binding
        Matcher sideEffectMatcher = ModuleSourceTransformer.MODULE_SIDE_EFFECT_IMPORT_PATTERN.matcher(scanCode);
        while (sideEffectMatcher.find()) {
            importPositions.add(new int[]{sideEffectMatcher.start(), 0, importPositions.size()});
        }
        Matcher namespaceMatcher = ModuleSourceTransformer.MODULE_NAMESPACE_IMPORT_PATTERN.matcher(scanCode);
        while (namespaceMatcher.find()) {
            importPositions.add(new int[]{namespaceMatcher.start(), 1, importPositions.size()});
        }
        Matcher bindingMatcher = ModuleSourceTransformer.MODULE_BINDING_IMPORT_PATTERN.matcher(scanCode);
        while (bindingMatcher.find()) {
            importPositions.add(new int[]{bindingMatcher.start(), 2, importPositions.size()});
        }
        importPositions.sort((a, b) -> Integer.compare(a[0], b[0]));

        // Suppress microtask processing during import evaluation to prevent
        // nested eval() calls from prematurely draining the microtask queue.
        // This ensures async TLA module completions happen after ALL imports
        // are processed, producing correct evaluation order.
        boolean prevSuppress = context.isSuppressingEvalMicrotasks();
        context.setSuppressingEvalMicrotasks(true);
        try {
            // Re-match each import in source order
            for (int[] pos : importPositions) {
                int type = pos[1];
                int start = pos[0];

                if (type == 0) {
                    // Side-effect import
                    sideEffectMatcher.reset(scanCode);
                    if (sideEffectMatcher.find(start) && sideEffectMatcher.start() == start) {
                        String specifier = transformer.decodeModuleStringLiteralValue(sideEffectMatcher.group(2));
                        Map<String, String> importAttributes = transformer.extractImportAttributes(sideEffectMatcher.group(0));
                        context.moduleLoader().loadDynamicImportModule(specifier, filename, importAttributes);
                        // If this side-effect import was generated for an export-from,
                        // resolve the corresponding re-export binding immediately so
                        // self-imports see the re-exported names in the namespace.
                        context.moduleLinker().resolveIncrementalReExport(specifier, filename);
                    }
                } else if (type == 1) {
                    // Namespace import
                    namespaceMatcher.reset(scanCode);
                    if (namespaceMatcher.find(start) && namespaceMatcher.start() == start) {
                        String deferKeyword = namespaceMatcher.group(1);
                        String localName = namespaceMatcher.group(2);
                        String specifier = transformer.decodeModuleStringLiteralValue(namespaceMatcher.group(4));
                        Map<String, String> importAttributes = transformer.extractImportAttributes(namespaceMatcher.group(0));
                        JSObject namespaceObject;
                        if ("defer".equals(deferKeyword)) {
                            namespaceObject = context.moduleLoader().loadDynamicImportModuleDeferred(specifier, filename, importAttributes);
                        } else {
                            namespaceObject = context.moduleLoader().loadDynamicImportModule(specifier, filename, importAttributes);
                        }
                        context.importBindingInstaller().bindImportOverlayValue(globalObject, savedGlobals, absentKeys, localName, namespaceObject);
                    }
                } else {
                    // Binding import
                    bindingMatcher.reset(scanCode);
                    if (bindingMatcher.find(start) && bindingMatcher.start() == start) {
                        String importClause = bindingMatcher.group(1).trim();
                        if (importClause.startsWith("*") || importClause.startsWith("defer *")) {
                            continue;
                        }
                        String specifier = transformer.decodeModuleStringLiteralValue(bindingMatcher.group(3));
                        Map<String, String> importAttributes = transformer.extractImportAttributes(bindingMatcher.group(0));
                        if (specifier.endsWith(".json")
                                && importAttributes != null
                                && "json".equals(importAttributes.get("type"))) {
                            if (transformer.hasNonDefaultNamedBindings(importClause)) {
                                throw new JSSyntaxErrorException(
                                        "JSON modules do not support named exports");
                            }
                        }
                        if (importAttributes != null) {
                            String attrType = importAttributes.get("type");
                            if ("text".equals(attrType) || "bytes".equals(attrType)) {
                                if (transformer.hasNonDefaultNamedBindings(importClause)) {
                                    throw new JSSyntaxErrorException(
                                            (("text".equals(attrType)) ? "Text" : "Bytes")
                                                    + " modules do not support named exports");
                                }
                            }
                        }
                        JSObject namespaceObject = context.moduleLoader().loadDynamicImportModule(specifier, filename, importAttributes);
                        context.importBindingInstaller().applyImportClauseBindings(
                                globalObject,
                                savedGlobals,
                                absentKeys,
                                namespaceObject,
                                importClause);
                    }
                }
            }
        } finally {
            context.setSuppressingEvalMicrotasks(prevSuppress);
        }

        // Post-import linking validation: verify that all named import bindings
        // actually exist in their (now finalized) module namespaces.
        // ES2024 16.2.1.6.3: Missing bindings are SyntaxError at link time.
        for (int[] pos : importPositions) {
            if (pos[1] != 2) {
                continue; // only check binding imports
            }
            bindingMatcher.reset(scanCode);
            if (bindingMatcher.find(pos[0]) && bindingMatcher.start() == pos[0]) {
                String importClause = bindingMatcher.group(1).trim();
                if (importClause.startsWith("*") || importClause.startsWith("defer *")) {
                    continue;
                }
                Map<String, String> importAttributes = transformer.extractImportAttributes(bindingMatcher.group(0));
                if (importAttributes != null) {
                    String importType = importAttributes.get("type");
                    if ("text".equals(importType) || "bytes".equals(importType)) {
                        continue;
                    }
                }
                String specifier = transformer.decodeModuleStringLiteralValue(bindingMatcher.group(3));
                String resolvedSpec;
                try {
                    resolvedSpec = context.moduleLoader().resolveDynamicImportSpecifier(specifier, filename, specifier);
                } catch (Exception e) {
                    continue;
                }
                JSDynamicImportModule moduleRecord = context.moduleLoader().cachedModule(resolvedSpec);
                if (moduleRecord != null
                        && moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED
                        && moduleRecord.namespace().isFinalized()) {
                    context.moduleLinker().validateNamedImportBindings(moduleRecord.namespace(), importClause);
                } else if (moduleRecord != null
                        && (moduleRecord.status() == JSDynamicImportModule.Status.LOADING
                        || moduleRecord.status() == JSDynamicImportModule.Status.EVALUATING)) {
                    // For self-referencing or circular imports, the namespace may not be finalized.
                    // Validate through recursive ResolveExport instead of namespace properties.
                    context.moduleLinker().validateNamedImportBindingsAgainstExplicitExports(moduleRecord, importClause);
                }
            }
        }

        // Drain microtasks to settle any EVALUATING_ASYNC modules before
        // the module body runs. This ensures TLA deps complete in the right order.
        if (!context.isSuppressingEvalMicrotasks()) {
            context.processMicrotasks();
        }

        // ES2024: If any imported module's evaluation failed (e.g., TLA rejection),
        // propagate the error to the importing module before its body runs.
        // Skip deferred imports — their errors are deferred until namespace access.
        for (int[] pos : importPositions) {
            int type = pos[1];
            int start = pos[0];
            String specifier = null;
            if (type == 0) {
                sideEffectMatcher.reset(scanCode);
                if (sideEffectMatcher.find(start) && sideEffectMatcher.start() == start) {
                    specifier = transformer.decodeModuleStringLiteralValue(sideEffectMatcher.group(2));
                }
            } else if (type == 1) {
                namespaceMatcher.reset(scanCode);
                if (namespaceMatcher.find(start) && namespaceMatcher.start() == start) {
                    // Skip deferred namespace imports — evaluation errors are deferred
                    // until the namespace is accessed (EnsureDeferredNamespaceEvaluation).
                    String deferKeyword = namespaceMatcher.group(1);
                    if ("defer".equals(deferKeyword)) {
                        continue;
                    }
                    specifier = transformer.decodeModuleStringLiteralValue(namespaceMatcher.group(4));
                }
            } else {
                bindingMatcher.reset(scanCode);
                if (bindingMatcher.find(start) && bindingMatcher.start() == start) {
                    // Skip deferred namespace imports that also match the binding pattern.
                    // These are handled by the namespace (type==1) branch above.
                    String importClause = bindingMatcher.group(1).trim();
                    if (importClause.startsWith("*") || importClause.startsWith("defer *")) {
                        continue;
                    }
                    specifier = transformer.decodeModuleStringLiteralValue(bindingMatcher.group(3));
                }
            }
            if (specifier != null) {
                try {
                    String resolvedSpec = context.moduleLoader().resolveDynamicImportSpecifier(specifier, filename, specifier);
                    JSDynamicImportModule moduleRecord = context.moduleLoader().cachedModule(resolvedSpec);
                    if (moduleRecord != null
                            && moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
                        throw new JSException(moduleRecord.evaluationError());
                    }
                } catch (JSException e) {
                    throw e;
                } catch (Exception ignored) {
                    // Specifier resolution failure is handled elsewhere
                }
            }
        }

        if (savedGlobals.isEmpty() && absentKeys.isEmpty()) {
            return null;
        }
        return new EvalOverlayManager.Frame(savedGlobals, absentKeys);
    }

    void scheduleClassFieldEvalCall() {
        pendingClassFieldEval = true;
    }

    void scheduleDirectEvalCall() {
        pendingDirectEvalCalls++;
    }

    /**
     * One run of the eval pipeline, from source text to completion value.
     * <p>
     * A class rather than one long method because the pipeline is a sequence of phases that share
     * a great deal of state: the source (which module normalisation rewrites), the compiler and its
     * flavour flags, the module records registered on the way in and unwound on the way out, and
     * the error that decides what the context is left holding. Each phase below is one of the
     * stages ECMAScript names — early errors, linking, GlobalDeclarationInstantiation /
     * EvalDeclarationInstantiation, evaluation — in the order they must happen.
     * <p>
     * An activation is used once. It is created after the stack frame is pushed and released in
     * {@code eval}'s {@code finally}, so every record it registered is unwound whether the body
     * completed, threw, or left a pending exception behind.
     */
    private final class EvalActivation {
        private final String filename;
        private final boolean inheritedStrictModeForDirectEval;
        private final boolean isDirectEval;
        private final boolean isModule;
        private final boolean predeclareProgramLexicalsAsLocals;
        private final boolean skipGlobalDeclarationTracking;
        private final boolean useDirectEvalCallerFrame;
        private boolean allowNewTargetInEval;
        private boolean allowSuperCallInEval;
        private boolean allowSuperPropertyInEval;
        private String code;
        private Compiler.CompileResult compileResult;
        private Compiler compiler;
        private StackFrame directEvalCallerFrame;
        private JSDynamicImportModule dynamicImportEvalModuleRecord;
        private JSValue evalError;
        private JSValue evalNewTarget;
        private JSValue evalThisArg;
        private boolean evaluatingRawDynamicImportModule;
        private JSBytecodeFunction func;
        private Set<String> globalEvalFunctionNames;
        private Set<String> globalScriptFunctionNames;
        private EvalOverlayManager.Frame moduleNamespaceImportOverlay;
        private boolean removeSelfModuleRecordAfterEval;
        private JSDynamicImportModule.Status selfModulePreviousStatus;
        private JSDynamicImportModule selfModuleRecord;
        private boolean shouldEvaluateRawModuleThroughTransformedSource;
        private boolean shouldEvaluateRawTopLevelAwaitModule;
        private boolean skipEvaluatedDynamicImportModule;

        private EvalActivation(
                String code,
                String filename,
                boolean isModule,
                boolean isDirectEval,
                boolean predeclareProgramLexicalsAsLocals,
                boolean skipGlobalDeclarationTracking,
                boolean inheritedStrictModeForDirectEval,
                boolean useDirectEvalCallerFrame) {
            this.code = code;
            this.filename = filename;
            this.isModule = isModule;
            this.isDirectEval = isDirectEval;
            this.predeclareProgramLexicalsAsLocals = predeclareProgramLexicalsAsLocals;
            this.skipGlobalDeclarationTracking = skipGlobalDeclarationTracking;
            this.inheritedStrictModeForDirectEval = inheritedStrictModeForDirectEval;
            this.useDirectEvalCallerFrame = useDirectEvalCallerFrame;
        }

        /**
         * Build the compiler and resolve which flavour of eval this is.
         * <p>
         * Direct eval inherits a great deal from the frame that called it — strictness,
         * {@code new.target}, the home object {@code super.x} resolves against, whether
         * {@code super()} is allowed, and the private names in scope — and all of it is decided
         * here, from the caller's frame, before a single token is read.
         */
        private void createCompiler() {
            compiler = new Compiler(code, filename).setContext(context);
            // Per QuickJS, eval code has is_eval=true which prevents top-level return.
            // Only syntactic direct eval should inherit caller frame semantics.
            directEvalCallerFrame = isDirectEval && useDirectEvalCallerFrame
                    ? context.getVirtualMachine().getCurrentFrame()
                    : null;
            Map<String, JSSymbol> evalPrivateSymbols = Map.of();
            boolean isClassFieldEval = consumeScheduledClassFieldEvalCall();
            if (isDirectEval) {
                compiler.setEval(true);
                if (directEvalCallerFrame != null
                        && directEvalCallerFrame.getFunction() instanceof JSBytecodeFunction callerBytecodeFunction) {
                    allowNewTargetInEval = callerBytecodeFunction.isNewTargetAllowed();
                    // Arrow functions inherit super binding from their enclosing method.
                    // Following QuickJS: eval inherits super_allowed from the calling function
                    // regardless of whether it is an arrow function or not.
                    allowSuperPropertyInEval = callerBytecodeFunction.getHomeObject() != null;
                    evalPrivateSymbols = collectEvalPrivateSymbols(callerBytecodeFunction);
                    // Per QuickJS: direct eval inherits super_call_allowed from the calling function.
                    // This is true for derived constructors, arrows inside derived constructors,
                    // and nested eval that already has super call allowed.
                    if (callerBytecodeFunction.isDerivedConstructor()) {
                        allowSuperCallInEval = true;
                    } else if (callerBytecodeFunction.isArrow() && directEvalCallerFrame.getDerivedThisRef() != null) {
                        allowSuperCallInEval = true;
                    } else if (callerBytecodeFunction.isEvalSuperCallAllowed()) {
                        allowSuperCallInEval = true;
                    }
                }
                // ES2024: class field initializer eval forbids arguments, new.target resolves to undefined
                // Per spec 16.1.7, eval in class field initializer applies "outside constructor" rules,
                // so super() is a SyntaxError there.
                if (isClassFieldEval) {
                    compiler.setClassFieldEval(true);
                    allowSuperCallInEval = false;
                }
                compiler.setEvalContextFlags(allowSuperPropertyInEval, allowNewTargetInEval, allowSuperCallInEval);
                compiler.setEvalPrivateSymbols(evalPrivateSymbols);
                // Direct eval creates a fresh lexical environment whose bindings do not leak.
                compiler.setPredeclareProgramLexicalsAsLocals(true);
                if (directEvalCallerFrame != null && (context.isStrictMode() || inheritedStrictModeForDirectEval)) {
                    compiler.setInheritedStrictMode(true);
                }
            }
            if (predeclareProgramLexicalsAsLocals) {
                compiler.setPredeclareProgramLexicalsAsLocals(true);
            }
        }

        /**
         * Define the global function bindings the body created, now that it has run.
         * <p>
         * CreateGlobalFunctionBinding's second half: the property's attributes depend on whether it
         * already existed and on whether this was a script (non-configurable) or a global direct
         * eval (configurable).
         */
        private void defineGlobalFunctionBindingsAfterExecution() {
            if (!isModule
                    && isDirectEval
                    && directEvalCallerFrame != null
                    && directEvalCallerFrame.getCaller() == null) {
                if (globalEvalFunctionNames == null) {
                    globalEvalFunctionNames = new LinkedHashSet<>();
                }
                for (String functionName : globalEvalFunctionNames) {
                    PropertyKey key = PropertyKey.fromString(functionName);
                    if (!context.getGlobalObject().has(key)) {
                        continue;
                    }
                    JSValue functionValue = context.getGlobalObject().get(key);
                    PropertyDescriptor existingDescriptor = context.getGlobalObject().getOwnPropertyDescriptor(key);
                    PropertyDescriptor descriptor = new PropertyDescriptor();
                    descriptor.setValue(functionValue);
                    if (existingDescriptor == null || existingDescriptor.isConfigurable()) {
                        descriptor.setWritable(true);
                        descriptor.setEnumerable(true);
                        descriptor.setConfigurable(true);
                    } else {
                        descriptor.setWritable(existingDescriptor.isWritable());
                        descriptor.setEnumerable(existingDescriptor.isEnumerable());
                        descriptor.setConfigurable(false);
                    }
                    context.getGlobalObject().defineProperty(key, descriptor);
                }
            }
            if (!isModule && !isDirectEval && globalScriptFunctionNames != null) {
                for (String functionName : globalScriptFunctionNames) {
                    PropertyKey key = PropertyKey.fromString(functionName);
                    if (!context.getGlobalObject().has(key)) {
                        continue;
                    }
                    JSValue functionValue = context.getGlobalObject().get(key);
                    PropertyDescriptor descriptor = new PropertyDescriptor();
                    descriptor.setValue(functionValue);
                    descriptor.setWritable(true);
                    descriptor.setEnumerable(true);
                    descriptor.setConfigurable(false);
                    context.getGlobalObject().defineProperty(key, descriptor);
                }
            }
        }

        /**
         * Evaluate a tracked module through its own record rather than as plain source.
         * <p>
         * A module with exports, or one whose imports have to be pulled in first, runs from the
         * transformed source held on its record; the record then carries whether it finished, is
         * still awaiting, or failed.
         *
         * @return the completion value, or null when the module's evaluation failed
         */
        private JSValue evaluateThroughModuleRecord() {
            JSValue evalResult = context.moduleLoader().evaluateDynamicImportModule(dynamicImportEvalModuleRecord);
            if (dynamicImportEvalModuleRecord.hasTLA() && evalResult instanceof JSPromise asyncPromise) {
                dynamicImportEvalModuleRecord.setAsyncEvaluationOrder(context.moduleLoader().nextAsyncEvaluationOrder());
                dynamicImportEvalModuleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING_ASYNC);
                dynamicImportEvalModuleRecord.setAsyncEvaluationPromise(asyncPromise);
                context.moduleLoader().registerAsyncModuleCompletion(dynamicImportEvalModuleRecord, asyncPromise, new HashSet<>());
            } else {
                if (dynamicImportEvalModuleRecord.hasExportSyntax()) {
                    context.moduleLinker().resolveDynamicImportReExports(dynamicImportEvalModuleRecord, new HashSet<>());
                    dynamicImportEvalModuleRecord.namespace().finalizeNamespace();
                }
                dynamicImportEvalModuleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
            }
            if (!context.isSuppressingEvalMicrotasks()) {
                context.processMicrotasks();
            }
            if (dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
                evalError = dynamicImportEvalModuleRecord.evaluationError();
                return null;
            }
            return JSUndefined.INSTANCE;
        }

        /**
         * Run the compiled body on the virtual machine.
         * <p>
         * The module-body counters move here, and only here: crossing this line is the observable
         * boundary between a graph that failed to link and one that linked and then threw. See
         * {@link JSContext#getModuleBodyEvaluationCount()}.
         *
         * @return the body's completion value, or null when it left a pending exception
         */
        private JSValue executeBody() {
            JSValue result;
            boolean evaluatingModuleBody = isModule && !isDirectEval;
            if (evaluatingModuleBody) {
                // Everything a module needs linked is linked by now — imports were resolved and
                // the modules behind them were pulled in above. Crossing this line is the
                // observable boundary between the two phases a negative module test distinguishes.
                context.recordModuleBodyEvaluation();
            }
            try {
                result = context.getVirtualMachine().execute(func, evalThisArg, JSValue.NO_ARGS, evalNewTarget);
                if (evaluatingModuleBody && result == null) {
                    // A pending exception rather than a thrown one, but the body still did not
                    // finish.
                    context.recordFailedModuleBodyEvaluation();
                }
            } catch (RuntimeException | Error moduleBodyFailure) {
                if (evaluatingModuleBody) {
                    context.recordFailedModuleBodyEvaluation();
                }
                throw moduleBodyFailure;
            } finally {
                context.setGlobalFunctionBindingInitializations(null, false);
            }
            return result;
        }

        /**
         * Mark a tracked module as evaluated once its body has run to completion.
         */
        private void finalizeDynamicImportModuleRecord() {
            if (evaluatingRawDynamicImportModule
                    && dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.LOADING) {
                dynamicImportEvalModuleRecord.namespace().finalizeNamespace();
                dynamicImportEvalModuleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
            }
        }

        /**
         * Hand the import overlay to the module's evaluation promise when the body is still running.
         * <p>
         * A top-level-await body has not finished when {@code execute} returns its promise, so
         * taking its imports away now would remove them from underneath it.
         *
         * @param result the body's completion value
         */
        private void handOverImportOverlayToAsyncModule(JSValue result) {
            if (isModule
                    && !isDirectEval
                    && moduleNamespaceImportOverlay != null
                    && evaluatingRawDynamicImportModule
                    && dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.hasExportSyntax()
                    && result instanceof JSPromise asyncModulePromise) {
                context.evalOverlayManager().registerDeferredRestore(asyncModulePromise, moduleNamespaceImportOverlay);
                moduleNamespaceImportOverlay = null;
            }
        }

        /**
         * EvalDeclarationInstantiation's CanDeclareGlobalFunction / CanDeclareGlobalVar checks,
         * ES2024 19.2.1.3 step 8.
         * <p>
         * Every check runs before any code does: a function declaration targeting a
         * non-configurable global property that is not both writable and enumerable is a
         * {@code TypeError} raised in front of the eval, not part-way through it. Following
         * QuickJS's {@code js_closure2} first pass with {@code JS_CheckDefineGlobalVar}.
         */
        private void instantiateEvalGlobalDeclarations() {
            if (isModule || !isDirectEval) {
                return;
            }
            Program.GlobalDeclarations globalDeclarations = compileResult.ast().getGlobalDeclarations();
            Set<String> evalVarDeclarations = globalDeclarations.varDeclarations();
            globalEvalFunctionNames = globalDeclarations.functionDeclarations();
            for (String functionName : globalEvalFunctionNames) {
                PropertyKey key = PropertyKey.fromString(functionName);
                PropertyDescriptor desc = context.getGlobalObject().getOwnPropertyDescriptor(key);
                if (desc != null && !desc.isConfigurable()) {
                    if (desc.isAccessorDescriptor()
                            || !(desc.isWritable() && desc.isEnumerable())) {
                        throw new JSException(context.throwTypeError("cannot define variable '" + functionName + "'"));
                    }
                }
                if (desc == null && !context.getGlobalObject().isExtensible()) {
                    throw new JSException(context.throwTypeError("cannot define variable '" + functionName + "'"));
                }
                if (directEvalCallerFrame != null && directEvalCallerFrame.getCaller() == null) {
                    if (desc == null || desc.isConfigurable()) {
                        JSValue initialValue = desc != null && desc.hasValue()
                                ? desc.getValue()
                                : JSUndefined.INSTANCE;
                        context.getGlobalObject().defineProperty(
                                key,
                                PropertyDescriptor.dataDescriptor(initialValue, PropertyDescriptor.DataState.All));
                    }
                }
            }
            if (directEvalCallerFrame != null && directEvalCallerFrame.getCaller() == null) {
                for (String declarationName : evalVarDeclarations) {
                    if (globalEvalFunctionNames.contains(declarationName)) {
                        continue;
                    }
                    PropertyKey key = PropertyKey.fromString(declarationName);
                    if (!context.getGlobalObject().has(key)
                            && !context.getGlobalObject().isExtensible()) {
                        throw new JSException(context.throwTypeError("cannot define variable '" + declarationName + "'"));
                    }
                }
            }
        }

        /**
         * GlobalDeclarationInstantiation for a top-level script, ES2024 16.1.7.
         * <p>
         * Every redeclaration conflict is decided before anything runs — against the names previous
         * scripts declared as well as against the global object's own properties — and the
         * {@code var} bindings are created as non-configurable properties, so they exist from the
         * first statement onwards.
         */
        private void instantiateScriptGlobalDeclarations() {
            if (isModule || isDirectEval || skipGlobalDeclarationTracking) {
                return;
            }
            // Top-level script: check GlobalDeclarationInstantiation per ES2024 16.1.7
            func = compileResult.function();

            // Collect new declarations from this script
            Program.GlobalDeclarations globalDeclarations = compileResult.ast().getGlobalDeclarations();
            Set<String> newConstDecls = globalDeclarations.constDeclarations();
            Set<String> newVarDecls = globalDeclarations.varDeclarations();
            Set<String> newLexDecls = globalDeclarations.lexicalDeclarations();
            globalScriptFunctionNames = globalDeclarations.functionDeclarations();

            // Check: let/const names must not collide with existing lex declarations
            // or restricted global properties (non-configurable or script-level var)
            for (String name : newLexDecls) {
                if (context.globalLexicalScope().hasLexDeclaration(name)) {
                    throw new JSSyntaxErrorException(
                            "Identifier '" + name + "' has already been declared");
                }
                // Check for non-configurable property on global object
                PropertyKey key = PropertyKey.fromString(name);
                PropertyDescriptor desc = context.getGlobalObject().getOwnPropertyDescriptor(key);
                if (desc != null && !desc.isConfigurable()) {
                    throw new JSSyntaxErrorException(
                            "Identifier '" + name + "' has already been declared");
                }
                // Check against script-level var declarations (these should be
                // non-configurable per spec, tracked separately)
                if (context.globalLexicalScope().hasVarDeclaration(name)) {
                    throw new JSSyntaxErrorException(
                            "Identifier '" + name + "' has already been declared");
                }
            }

            // Check: var/function names must not collide with existing lex declarations
            for (String name : newVarDecls) {
                if (context.globalLexicalScope().hasLexDeclaration(name)) {
                    throw new JSSyntaxErrorException(
                            "Identifier '" + name + "' has already been declared");
                }
            }

            // Check CreateGlobalFunctionBinding preconditions before execution.
            for (String functionName : globalScriptFunctionNames) {
                PropertyKey key = PropertyKey.fromString(functionName);
                PropertyDescriptor desc = context.getGlobalObject().getOwnPropertyDescriptor(key);
                if (desc == null) {
                    if (!context.getGlobalObject().isExtensible()) {
                        throw new JSException(context.throwTypeError("cannot define variable '" + functionName + "'"));
                    }
                    continue;
                }
                if (!desc.isConfigurable()) {
                    if (desc.isAccessorDescriptor()
                            || !(desc.isWritable() && desc.isEnumerable())) {
                        throw new JSException(context.throwTypeError("cannot define variable '" + functionName + "'"));
                    }
                }
            }

            // Register new declarations for future collision checks
            context.globalLexicalScope().declareScriptGlobals(newConstDecls, newLexDecls, newVarDecls);

            // CreateGlobalVarDeclaration: define var bindings as non-configurable
            // properties on the global object (per ES2024 9.1.1.4.17 / QuickJS
            // js_closure_define_global_var with is_direct_or_indirect_eval=FALSE).
            // This must happen BEFORE execution so bindings exist at script start.
            for (String name : newVarDecls) {
                if (globalScriptFunctionNames.contains(name)) {
                    continue;
                }
                PropertyKey key = PropertyKey.fromString(name);
                PropertyDescriptor existing = context.getGlobalObject().getOwnPropertyDescriptor(key);
                if (existing == null && !context.getGlobalObject().isExtensible()) {
                    throw new JSException(context.throwTypeError("cannot define variable '" + name + "'"));
                }
                if (existing == null) {
                    // Property doesn't exist: create {writable, enumerable, NOT configurable}
                    context.getGlobalObject().defineProperty(key,
                            PropertyDescriptor.dataDescriptor(
                                    JSUndefined.INSTANCE,
                                    PropertyDescriptor.DataState.EnumerableWritable
                            ));
                }
            }
        }

        /**
         * Lexer → Parser → Compiler: turn the source into a bytecode function.
         */
        private void parseAndCompile() {
            compileResult = compiler.compile(isModule);
            func = compileResult.function();
        }

        /**
         * Resolve the {@code this} binding, {@code new.target} and the captured references the body
         * runs with.
         * <p>
         * ES2024 PerformEval: a direct eval sees its caller's {@code this}, and — where the caller
         * allows it — its {@code new.target}, its home object, and the shared {@code this} binding
         * a derived constructor's {@code super()} initialises. A module's top-level {@code this} is
         * {@code undefined} (16.2.1.6.4).
         */
        private void prepareExecutionEnvironment() {
            // For direct eval, inherit the caller's 'this' binding per ES2024 PerformEval.
            // In strict mode functions called without receiver, 'this' is undefined, and
            // eval('this') must see that same undefined value, not the global object.
            // ES2024 16.2.1.6.4: Module top-level 'this' is undefined.
            evalThisArg = isModule && !isDirectEval
                    ? JSUndefined.INSTANCE
                    : context.getGlobalObject();
            evalNewTarget = JSUndefined.INSTANCE;
            if (isDirectEval && directEvalCallerFrame != null) {
                evalThisArg = directEvalCallerFrame.getThisArg();
                if (allowNewTargetInEval) {
                    evalNewTarget = directEvalCallerFrame.getNewTarget();
                }
                if (allowSuperPropertyInEval) {
                    func.setHomeObject(directEvalCallerFrame.getFunction().getHomeObject());
                }
                if (allowSuperCallInEval) {
                    func.setEvalSuperCallAllowed(true);
                    // Set up new.target for super() calls: inherit from caller
                    // (including arrows that capture new.target from the constructor)
                    JSFunction callerFunction = directEvalCallerFrame.getFunction();
                    if (evalNewTarget == null || evalNewTarget instanceof JSUndefined) {
                        if (callerFunction instanceof JSBytecodeFunction callerBf && callerBf.isArrow()) {
                            JSValue capturedNewTarget = callerBf.getCapturedNewTarget();
                            if (capturedNewTarget != null) {
                                evalNewTarget = capturedNewTarget;
                            }
                        }
                        if (evalNewTarget == null || evalNewTarget instanceof JSUndefined) {
                            evalNewTarget = directEvalCallerFrame.getNewTarget();
                        }
                    }
                    // Set capturedNewTarget so arrows created inside eval can inherit it via FCLOSURE
                    func.setCapturedNewTarget(evalNewTarget);
                    // Set capturedActiveFunction so SPECIAL_OBJECT 2 returns the constructor
                    if (callerFunction instanceof JSBytecodeFunction callerBf) {
                        if (callerBf.isArrow()) {
                            JSFunction activeFunction = callerBf.getCapturedActiveFunction();
                            if (activeFunction != null) {
                                func.setCapturedActiveFunction(activeFunction);
                            }
                        } else if (callerBf.isEvalSuperCallAllowed() && callerBf.getCapturedActiveFunction() != null) {
                            func.setCapturedActiveFunction(callerBf.getCapturedActiveFunction());
                        } else {
                            func.setCapturedActiveFunction(callerFunction);
                        }
                    }
                    // Set capturedDerivedThisRef so INIT_CTOR can find the shared this binding
                    VarRef callerDerivedThisRef = directEvalCallerFrame.getDerivedThisRef();
                    if (callerDerivedThisRef != null) {
                        func.setCapturedDerivedThisRef(callerDerivedThisRef);
                    }
                }
                if (allowNewTargetInEval) {
                    func.setNewTargetAllowed(true);
                }
            }
        }

        /**
         * Give the compiled body its prototype chain and its {@code import.meta} file name.
         */
        private void prepareFunction() {
            // Initialize the function's prototype chain so it inherits from Function.prototype
            func.initializePrototypeChain(context);
            func.setImportMetaFilename(filename);
        }

        /**
         * Announce which global function bindings the body is about to initialise.
         * <p>
         * The VM's function-declaration handler consumes these as it defines each one, which is how
         * CreateGlobalFunctionBinding's attributes reach a binding created from bytecode.
         */
        private void prepareGlobalFunctionBindingInitializations() {
            Set<String> globalFunctionBindingInitializations = null;
            boolean globalFunctionBindingsConfigurable = false;
            if (!isModule && !isDirectEval && globalScriptFunctionNames != null) {
                globalFunctionBindingInitializations = new HashSet<>(globalScriptFunctionNames);
            }
            if (!isModule
                    && isDirectEval
                    && directEvalCallerFrame != null
                    && directEvalCallerFrame.getCaller() == null
                    && globalEvalFunctionNames != null) {
                if (globalFunctionBindingInitializations == null) {
                    globalFunctionBindingInitializations = new HashSet<>();
                }
                globalFunctionBindingInitializations.addAll(globalEvalFunctionNames);
                globalFunctionBindingsConfigurable = true;
            }
            context.setGlobalFunctionBindingInitializations(
                    globalFunctionBindingInitializations,
                    globalFunctionBindingsConfigurable);
        }

        /**
         * Raise the source's early errors, link the graph it names, and put its declarations on
         * their own lines — in that order, and all before anything is evaluated.
         */
        private void prepareModuleSource() {
            if (!isModule || isDirectEval) {
                return;
            }
            // Every early error the source has is raised here, against the text the caller
            // passed, before a single character of it is rewritten. Downstream the source is
            // split onto more lines and then wrapped in generated module code, and neither
            // keeps the caller's coordinate system: a duplicate `__proto__` at offset 44 of a
            // 60-character module was reported at offset 119, which names no character of the
            // input at all. See requireModuleSourceCompiles.
            transformer.requireModuleSourceCompiles(code);
            // Link before evaluating: a name this graph imports and nothing exports is a
            // failure of the whole graph, and must be raised before any of it runs. See
            // requireModuleGraphLinks. It runs on the source as written, not on the normalised
            // copy below, because a position it reports has to be a position in the text the
            // caller passed — inserting line breaks first would shift them.
            //
            // Not on a module's own transformed source, though: that text is generated, its
            // `export` declarations have already been rewritten away, and linking it would ask
            // the graph what a module with no exports left provides.
            if (!context.moduleLoader().isTransformedModuleSource(code, filename)) {
                context.moduleLinker().requireModuleGraphLinks(code, filename);
            }
            // Put every top-level import/export declaration on its own lines before anything
            // downstream classifies module source by line. The compile above has already
            // accepted the source as written, so the split cannot silently repair it.
            code = transformer.normalizeModuleDeclarationLines(code, false);
        }

        /**
         * Register this module in the cache as EVALUATING, then evaluate its imports.
         * <p>
         * The record goes in before the imports are processed, because that is what lets a module
         * importing itself — {@code import defer * as self from './thisFile.js'} — see the
         * re-entrancy and fail instead of recursing.
         */
        private void registerSelfModuleAndEvaluateImports() {
            if (!isModule || isDirectEval
                    || filename == null || filename.isEmpty() || filename.startsWith("<")) {
                return;
            }
            // Register the current module as EVALUATING in the cache so that
            // self-imports (import defer * as self from './thisFile.js')
            // can detect re-entrancy and throw TypeError instead of recursing.
            String normalizedFilename = Paths.get(filename).normalize().toString();
            JSDynamicImportModule existingRecord = context.moduleLoader().cachedModule(normalizedFilename);
            if (existingRecord != null) {
                // Module already in cache (e.g. from loadJSDynamicImportModule).
                // Mark it as EVALUATING so self-imports detect the re-entrancy.
                selfModulePreviousStatus = existingRecord.status();
                existingRecord.setStatus(JSDynamicImportModule.Status.EVALUATING);
                selfModuleRecord = existingRecord;
            } else {
                selfModuleRecord = new JSDynamicImportModule(
                        normalizedFilename, context.moduleLoader().createModuleNamespaceObject());
                selfModuleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING);
                selfModuleRecord.setRawSource(code);
                context.moduleLoader().cacheModule(normalizedFilename, selfModuleRecord);
                removeSelfModuleRecordAfterEval = true;
            }
            context.importBindingInstaller().initializeHoistedFunctionExportBindings(selfModuleRecord);
            moduleNamespaceImportOverlay = evaluateModuleImportsInOrder(code, filename);
        }

        /**
         * Unwind everything this activation registered, and leave the context in a clean state.
         * <p>
         * Runs whether the body completed, threw, or left a pending exception behind: a module
         * record registered for an evaluation that did not finish is evicted, the import overlay is
         * taken off the global object, and the stack frame is popped. The pending exception is
         * cleared and then re-set from {@code evalError}, so what the context holds afterwards is
         * exactly the error this eval is reporting and nothing a nested one left over.
         */
        private void release() {
            if (evalError != null && dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.LOADING) {
                context.moduleLoader().removeCachedModule(dynamicImportEvalModuleRecord.resolvedSpecifier());
            }
            if (selfModuleRecord != null) {
                if (removeSelfModuleRecordAfterEval) {
                    if (context.moduleLoader().cachedModule(selfModuleRecord.resolvedSpecifier()) == selfModuleRecord
                            && selfModuleRecord.status() == JSDynamicImportModule.Status.EVALUATING) {
                        context.moduleLoader().removeCachedModule(selfModuleRecord.resolvedSpecifier());
                    }
                } else if (selfModuleRecord.status() == JSDynamicImportModule.Status.EVALUATING
                        && selfModulePreviousStatus != null) {
                    selfModuleRecord.setStatus(selfModulePreviousStatus);
                }
            }
            context.evalOverlayManager().restoreFrame(moduleNamespaceImportOverlay);
            context.popStackFrame();
            // Clear ALL possible dirty state to ensure clean slate for next eval()
            context.clearTransientEvalState();
            context.evalOverlayManager().resetLookupSuppression();
            context.clearPendingException();
            context.clearErrorStackTrace();
            if (evalError != null) {
                context.setPendingException(evalError);
            }
        }

        /**
         * Run the pipeline.
         *
         * @return the completion value, or null when the eval failed and {@code evalError} says how
         */
        private JSValue run() {
            prepareModuleSource();
            trackDynamicImportModule();
            createCompiler();
            if (skipEvaluatedDynamicImportModule) {
                context.processMicrotasks();
                return JSUndefined.INSTANCE;
            }
            if (evaluatingRawDynamicImportModule
                    && (dynamicImportEvalModuleRecord.hasExportSyntax()
                    || shouldEvaluateRawModuleThroughTransformedSource
                    || shouldEvaluateRawTopLevelAwaitModule)) {
                return evaluateThroughModuleRecord();
            }

            parseAndCompile();
            instantiateScriptGlobalDeclarations();
            instantiateEvalGlobalDeclarations();
            prepareFunction();
            registerSelfModuleAndEvaluateImports();
            prepareGlobalFunctionBindingInitializations();
            prepareExecutionEnvironment();

            JSValue result = executeBody();

            handOverImportOverlayToAsyncModule(result);
            defineGlobalFunctionBindingsAfterExecution();

            // Check if there's a pending exception
            if (context.hasPendingException()) {
                evalError = context.getPendingException();
                return null;
            }

            // Process all pending microtasks before returning
            if (!context.isSuppressingEvalMicrotasks()) {
                context.processMicrotasks();
            }

            finalizeDynamicImportModuleRecord();

            return result != null ? result : JSUndefined.INSTANCE;
        }

        /**
         * Decide whether this source is a module the realm should track a record for, and register
         * or reuse that record.
         * <p>
         * Only a module loaded under a real file name is tracked: a record is what lets a later
         * {@code import} of the same file find it rather than evaluate it a second time, and source
         * handed straight to {@code eval} has no identity to key one on.
         */
        private void trackDynamicImportModule() {
            boolean shouldTrackDynamicImportModule = isModule
                    && !isDirectEval
                    && filename != null
                    && !filename.isEmpty()
                    && !filename.startsWith("<")
                    && (code.contains("import(") || code.contains("import.defer(")
                    || transformer.hasModuleExportSyntax(code)
                    || transformer.hasModuleStaticImportSyntax(code)
                    || transformer.hasModuleTopLevelAwaitSyntax(code));
            if (shouldTrackDynamicImportModule) {
                String resolvedModuleSpecifier;
                try {
                    resolvedModuleSpecifier = context.moduleLoader().resolveDynamicImportSpecifier(filename, null, filename);
                } catch (JSException jsException) {
                    // A filename that does not resolve to a module on disk is normal here — the
                    // source was handed to eval() directly. Clear the pending exception the
                    // matching throwTypeError left on the context: catching the Java exception
                    // alone leaves the context in an exception state, and the next activation
                    // reports that stale error as its own failure.
                    context.clearPendingException();
                    resolvedModuleSpecifier = context.moduleLoader().normalizeModuleSpecifier(filename);
                }
                JSDynamicImportModule existingRecord = context.moduleLoader().cachedModule(resolvedModuleSpecifier);
                boolean executingTransformedModuleSource = existingRecord != null
                        && !Objects.equals(existingRecord.rawSource(), code)
                        && Objects.equals(existingRecord.transformedSource(), code);
                if (!executingTransformedModuleSource) {
                    dynamicImportEvalModuleRecord = existingRecord;
                    if (dynamicImportEvalModuleRecord == null) {
                        dynamicImportEvalModuleRecord =
                                new JSDynamicImportModule(resolvedModuleSpecifier, context.moduleLoader().createModuleNamespaceObject());
                        dynamicImportEvalModuleRecord.setStatus(JSDynamicImportModule.Status.LOADING);
                        dynamicImportEvalModuleRecord.setRawSource(code);
                        // Early errors were already raised above, against the caller's own text
                        // rather than this normalised copy of it.
                        transformer.parseDynamicImportModuleSource(dynamicImportEvalModuleRecord);
                        context.moduleLoader().cacheModule(resolvedModuleSpecifier, dynamicImportEvalModuleRecord);
                    } else if (dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.EVALUATED) {
                        skipEvaluatedDynamicImportModule = true;
                    }
                }
            }
            evaluatingRawDynamicImportModule =
                    dynamicImportEvalModuleRecord != null
                            && Objects.equals(dynamicImportEvalModuleRecord.rawSource(), code);
            shouldEvaluateRawModuleThroughTransformedSource =
                    evaluatingRawDynamicImportModule
                            && !dynamicImportEvalModuleRecord.hasExportSyntax()
                            && !dynamicImportEvalModuleRecord.hasTLA()
                            && transformer.hasModuleStaticImportSyntax(code)
                            && code.contains("import(");
            shouldEvaluateRawTopLevelAwaitModule =
                    evaluatingRawDynamicImportModule
                            && !dynamicImportEvalModuleRecord.hasExportSyntax()
                            && dynamicImportEvalModuleRecord.hasTLA()
                            && !transformer.hasModuleStaticImportSyntax(code);
        }
    }
}
