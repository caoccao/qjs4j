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
import com.caoccao.qjs4j.unicode.UnicodePropertyResolver;
import com.caoccao.qjs4j.vm.StackFrame;
import com.caoccao.qjs4j.vm.VarRef;
import com.caoccao.qjs4j.vm.VirtualMachine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a JavaScript execution context.
 * Based on QuickJS JSContext structure.
 * <p>
 * A context is an independent JavaScript execution environment with:
 * - Its own global object and built-in objects
 * - Its own module cache
 * - Its own call stack and exception state
 * - Shared runtime resources (atoms, GC, job queue)
 * <p>
 * Multiple contexts can exist in a single runtime, each isolated
 * from the others (separate globals, separate module namespaces).
 */
public final class JSContext implements AutoCloseable {
    private static final int DEFAULT_MAX_STACK_DEPTH = 1000;
    private static final Pattern DYNAMIC_IMPORT_EXPORT_CLASS_NAME_PATTERN =
            Pattern.compile("^class\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final Pattern DYNAMIC_IMPORT_EXPORT_FUNCTION_NAME_PATTERN =
            Pattern.compile("^(?:async\\s+)?function(?:\\s*\\*)?\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final JSValue GLOBAL_LEXICAL_UNINITIALIZED = new JSSymbol("GlobalLexicalUninitialized");
    private static final Pattern MODULE_BINDING_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s*([^;]*?)\\s+from\\s+(['\"])([^'\"\\r\\n]+)\\2(?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");
    private static final Pattern MODULE_EXPORT_SYNTAX_PATTERN =
            Pattern.compile("(?m)^\\s*export\\s");
    private static final Pattern MODULE_NAMESPACE_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s*(?:(defer)\\s+)?\\*\\s*as\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s+from\\s+(['\"])([^'\"\\r\\n]+)\\3(?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");
    private static final Pattern MODULE_SIDE_EFFECT_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s*(['\"])([^'\"\\r\\n]+)\\1(?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");
    private static final Pattern MODULE_STATIC_IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s*(?:defer\\s+)?(?:[^;]*?\\s+from\\s+)?['\"]([^'\"\\r\\n]+)['\"](?:\\s+with\\s*\\{[^}]*\\})?\\s*;?\\s*$");
    private static final Pattern MODULE_STATIC_IMPORT_SYNTAX_PATTERN =
            Pattern.compile("(?m)^\\s*import(?!\\s*\\(|\\s*\\.)");
    private static final Pattern MODULE_TOP_LEVEL_AWAIT_PATTERN =
            Pattern.compile("(?m)^\\s*await\\b");
    private static final Pattern MODULE_WITH_CLAUSE_PATTERN =
            Pattern.compile("with\\s*\\{([^}]*)\\}");
    // Call stack management
    private final Deque<JSStackFrame> callStack;
    private final Map<String, JSDynamicImportModule> dynamicImportModuleCache;
    // Stack trace capture
    private final List<StackTraceElement> errorStackTrace;
    private final Deque<EvalOverlayFrame> evalOverlayFrames;
    private final List<JSFinalizationRegistry> finalizationRegistries;
    // Global declaration tracking for cross-script collision detection
    // Following QuickJS global_var_obj pattern (GlobalDeclarationInstantiation)
    private final Set<String> globalConstDeclarations;
    private final Set<String> globalLexDeclarations;
    private final Map<String, JSValue> globalLexicalBindings;
    private final Set<String> globalVarDeclarations;
    private final Map<String, JSObject> importMetaCache;
    // Shared iterator prototypes by toStringTag (e.g., "Array Iterator" → %ArrayIteratorPrototype%)
    private final Map<String, JSObject> iteratorPrototypes;
    private final JSGlobalObject jsGlobalObject;
    // Microtask queue for promise resolution and async operations
    private final JSMicrotaskQueue microtaskQueue;
    private final Map<String, JSModule> moduleCache;
    private final String[] regExpLegacyCaptures;
    private final JSRuntime runtime;
    private final UnicodePropertyResolver unicodePropertyResolver;
    private final VirtualMachine virtualMachine;
    private boolean activeGlobalFunctionBindingConfigurable;
    private Set<String> activeGlobalFunctionBindingInitializations;
    // Counter for tracking the order modules have their async evaluation set
    private int asyncEvaluationOrderCounter;
    // Internal constructor references (not exposed in global scope)
    private JSObject asyncFunctionConstructor;
    private JSObject asyncGeneratorFunctionPrototype;
    // Async generator prototype chain (not exposed in global scope)
    private JSObject asyncGeneratorPrototype;
    private JSObject cachedDatePrototype;
    // Cached Object.prototype for fast internal object creation
    private JSObject cachedObjectPrototype;
    private JSObject cachedPromisePrototype;
    // Temporarily holds new.target during native constructor calls
    // so native constructors can check if called directly vs from subclass
    private JSValue constructorNewTarget;
    private JSValue currentThis;
    private int evalOverlayLookupSuppressionDepth;
    // Generator prototype chain (not exposed in global scope)
    private JSObject generatorFunctionPrototype;
    // Flag set by the VM's PUT_VAR handler before calling globalObject.set()
    // so that import overlay setters can distinguish bare variable assignment
    // (which should throw TypeError) from property-based writes (which should succeed).
    private boolean inBareVariableAssignment;
    private boolean inCatchHandler;
    private int maxStackDepth;
    private JSValue nativeConstructorNewTarget;
    private boolean pendingClassFieldEval;
    private int pendingDirectEvalCalls;
    // Exception state
    private JSValue pendingException;
    // Promise rejection callback
    private IJSPromiseRejectCallback promiseRejectCallback;
    private String regExpLegacyInput;
    private String regExpLegacyLastMatch;
    private String regExpLegacyLastParen;
    private String regExpLegacyLeftContext;
    private String regExpLegacyRightContext;
    private int stackDepth;
    // Execution state
    private boolean strictMode;
    private boolean suppressEvalMicrotaskProcessing;
    // The %ThrowTypeError% intrinsic (shared across Function.prototype and strict arguments)
    private JSNativeFunction throwTypeErrorIntrinsic;
    private boolean waitable;

    /**
     * Create a new execution context.
     */
    JSContext(JSRuntime runtime) {
        this.callStack = new ArrayDeque<>();
        this.activeGlobalFunctionBindingConfigurable = false;
        this.activeGlobalFunctionBindingInitializations = null;
        this.errorStackTrace = new ArrayList<>();
        this.globalConstDeclarations = new HashSet<>();
        this.globalLexDeclarations = new HashSet<>();
        this.globalLexicalBindings = new HashMap<>();
        this.globalVarDeclarations = new HashSet<>();
        this.importMetaCache = new HashMap<>();
        this.waitable = true;
        this.inCatchHandler = false;
        this.evalOverlayFrames = new ArrayDeque<>();
        this.finalizationRegistries = new ArrayList<>();
        this.iteratorPrototypes = new HashMap<>();
        this.jsGlobalObject = new JSGlobalObject(this);
        this.maxStackDepth = DEFAULT_MAX_STACK_DEPTH;
        this.microtaskQueue = new JSMicrotaskQueue(this);
        this.dynamicImportModuleCache = new HashMap<>();
        this.moduleCache = new HashMap<>();
        this.pendingException = null;
        this.runtime = runtime;
        this.unicodePropertyResolver = new UnicodePropertyResolver();
        this.asyncEvaluationOrderCounter = 0;
        this.evalOverlayLookupSuppressionDepth = 0;
        this.inBareVariableAssignment = false;
        this.pendingDirectEvalCalls = 0;
        this.regExpLegacyCaptures = new String[9];
        this.regExpLegacyInput = "";
        this.regExpLegacyLastMatch = "";
        this.regExpLegacyLastParen = "";
        this.regExpLegacyLeftContext = "";
        this.regExpLegacyRightContext = "";
        for (int captureIndex = 0; captureIndex < regExpLegacyCaptures.length; captureIndex++) {
            regExpLegacyCaptures[captureIndex] = "";
        }
        this.stackDepth = 0;
        this.strictMode = false;
        this.virtualMachine = new VirtualMachine(this);
        this.nativeConstructorNewTarget = null;

        this.currentThis = jsGlobalObject.getGlobalObject();
        initializeGlobalObject();
    }

    private static int parseHex(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < text.length(); i++) {
            int digit = Character.digit(text.charAt(i), 16);
            if (digit < 0) {
                return -1;
            }
            if (value > (Integer.MAX_VALUE - digit) / 16) {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }


    private void appendDynamicImportDefaultExportNameFixup(
            StringBuilder transformedSourceBuilder,
            String localName) {
        transformedSourceBuilder.append("if (typeof ")
                .append(localName)
                .append(" === \"function\" && (!Object.prototype.hasOwnProperty.call(")
                .append(localName)
                .append(", \"name\") || ")
                .append(localName)
                .append(".name === \"\" || ")
                .append(localName)
                .append(".name === \"")
                .append(localName)
                .append("\")) {\n")
                .append("  Object.defineProperty(")
                .append(localName)
                .append(", \"name\", { value: \"default\", configurable: true });\n")
                .append("}\n");
    }

    private void appendDynamicImportExportAssignments(
            StringBuilder transformedSourceBuilder,
            String exportBindingName,
            List<JSDynamicImportModule.LocalExportBinding> localExportBindings,
            Set<String> importedBindingNames) {
        transformedSourceBuilder.append("const __qjs4jModuleExports = ")
                .append(exportBindingName)
                .append(";\n");
        for (JSDynamicImportModule.LocalExportBinding localExportBinding : localExportBindings) {
            String escapedName = escapeJavaScriptString(localExportBinding.exportedName());
            // Exports are always live bindings, including re-exported imports.
            transformedSourceBuilder.append("Object.defineProperty(__qjs4jModuleExports, \"")
                    .append(escapedName)
                    .append("\", { enumerable: true, configurable: false, get() { return ")
                    .append(localExportBinding.localName())
                    .append("; } });\n");
        }
    }

    private void applyImportClauseBindings(
            JSObject globalObject,
            Map<String, JSValue> savedGlobals,
            Set<String> absentKeys,
            JSObject namespaceObject,
            String importClause) {
        String clause = importClause.trim();
        if (clause.isEmpty()) {
            return;
        }
        if (clause.startsWith("{")) {
            bindNamedImports(globalObject, savedGlobals, absentKeys, namespaceObject, clause);
            return;
        }

        int commaIndex = clause.indexOf(',');
        if (commaIndex < 0) {
            bindImportOverlayLiveBinding(globalObject, savedGlobals, absentKeys, clause, namespaceObject, "default");
            return;
        }

        String defaultBinding = clause.substring(0, commaIndex).trim();
        if (!defaultBinding.isEmpty()) {
            bindImportOverlayLiveBinding(globalObject, savedGlobals, absentKeys, defaultBinding, namespaceObject, "default");
        }

        String remainder = clause.substring(commaIndex + 1).trim();
        if (remainder.startsWith("*")) {
            String namespaceBinding = remainder.replaceFirst("^\\*\\s*as\\s+", "").trim();
            if (!namespaceBinding.isEmpty()) {
                bindImportOverlayValue(globalObject, savedGlobals, absentKeys, namespaceBinding, namespaceObject);
            }
        } else if (remainder.startsWith("{")) {
            bindNamedImports(globalObject, savedGlobals, absentKeys, namespaceObject, remainder);
        }
    }

    /**
     * Bind an import as a live binding using a getter that reads from the namespace.
     * This ensures that changes to the exported value are reflected in the import.
     */
    private void bindImportOverlayLiveBinding(
            JSObject globalObject,
            Map<String, JSValue> savedGlobals,
            Set<String> absentKeys,
            String localName,
            JSObject namespaceObject,
            String importedName) {
        String bindingName = localName.trim();
        if (bindingName.isEmpty()) {
            return;
        }
        PropertyKey key = PropertyKey.fromString(bindingName);
        if (globalObject.has(key)) {
            if (!savedGlobals.containsKey(bindingName)) {
                JSValue currentValue = globalObject.get(key);
                savedGlobals.put(bindingName, currentValue);
            }
        } else {
            absentKeys.add(bindingName);
        }
        PropertyKey importKey = PropertyKey.fromString(importedName);
        JSNativeFunction getter = new JSNativeFunction(this, "get " + bindingName, 0,
                (context, thisArg, args) -> {
                    JSValue val = namespaceObject.get(importKey);
                    if (context != null && context.hasPendingException()) {
                        JSValue exception = context.getPendingException();
                        context.clearPendingException();
                        throw new JSException(exception);
                    }
                    if (val == JSUndefined.INSTANCE && namespaceObject instanceof JSImportNamespaceObject namespace) {
                        JSValue earlyValue = namespace.getEarlyExportBinding(importedName);
                        if (earlyValue != null) {
                            return earlyValue;
                        }
                    }
                    return val != null ? val : JSUndefined.INSTANCE;
                });
        getter.initializePrototypeChain(this);
        // Setter distinguishes bare variable assignment (PUT_VAR, e.g., check = true)
        // from property-based writes (PUT_FIELD, e.g., globalThis.check = true).
        // Bare variable assignment to an import binding throws TypeError (ES2024 immutable binding).
        // Property-based writes update savedGlobals for correct value restoration.
        JSNativeFunction setter = new JSNativeFunction(this, "set " + bindingName, 1,
                (ctx, thisArg, args) -> {
                    if (ctx != null && ctx.isInBareVariableAssignment()) {
                        ctx.throwTypeError("Assignment to constant variable.");
                        return JSUndefined.INSTANCE;
                    }
                    JSValue val = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                    savedGlobals.put(bindingName, val);
                    return JSUndefined.INSTANCE;
                });
        setter.initializePrototypeChain(this);
        PropertyDescriptor descriptor = new PropertyDescriptor();
        descriptor.setGetter(getter);
        descriptor.setSetter(setter);
        descriptor.setConfigurable(true);
        descriptor.setEnumerable(true);
        globalObject.defineProperty(key, descriptor);
    }

    private void bindImportOverlayValue(
            JSObject globalObject,
            Map<String, JSValue> savedGlobals,
            Set<String> absentKeys,
            String localName,
            JSValue value) {
        String bindingName = localName.trim();
        if (bindingName.isEmpty()) {
            return;
        }
        PropertyKey key = PropertyKey.fromString(bindingName);
        if (globalObject.has(key)) {
            if (!savedGlobals.containsKey(bindingName)) {
                savedGlobals.put(bindingName, globalObject.get(key));
            }
        } else {
            absentKeys.add(bindingName);
        }
        PropertyDescriptor descriptor = new PropertyDescriptor();
        descriptor.setValue(value != null ? value : JSUndefined.INSTANCE);
        descriptor.setWritable(false);
        descriptor.setEnumerable(true);
        descriptor.setConfigurable(true);
        globalObject.defineProperty(key, descriptor);
    }

    private void bindNamedImports(
            JSObject globalObject,
            Map<String, JSValue> savedGlobals,
            Set<String> absentKeys,
            JSObject namespaceObject,
            String namedClause) {
        String clause = namedClause.trim();
        if (!clause.startsWith("{") || !clause.endsWith("}")) {
            return;
        }
        String specifiersText = clause.substring(1, clause.length() - 1).trim();
        if (specifiersText.isEmpty()) {
            return;
        }
        for (String rawSpecifier : splitOnTopLevelCommas(specifiersText)) {
            String specifier = rawSpecifier.trim();
            if (specifier.isEmpty()) {
                continue;
            }
            String importedName;
            String localName;
            int asIndex = findTopLevelAs(specifier);
            if (asIndex >= 0) {
                importedName = parseModuleExportNameValue(specifier.substring(0, asIndex).trim());
                localName = specifier.substring(asIndex + 2).trim();
            } else {
                importedName = parseModuleExportNameValue(specifier);
                localName = importedName;
            }
            // ES2024 16.2.1.6.3: If the imported binding doesn't exist in a
            // finalized module namespace, it is a SyntaxError (linking error).
            if (namespaceObject instanceof JSImportNamespaceObject nsObj && nsObj.isFinalized()) {
                PropertyKey importKey = PropertyKey.fromString(importedName);
                if (!nsObj.has(importKey)) {
                    throw new JSSyntaxErrorException(
                            "The requested module does not provide an export named '" + importedName + "'");
                }
            }
            bindImportOverlayLiveBinding(globalObject, savedGlobals, absentKeys, localName, namespaceObject, importedName);
        }
    }

    /**
     * Capture stack trace when exception is thrown.
     */
    private void captureErrorStackTrace() {
        clearErrorStackTrace();
        for (JSStackFrame frame : callStack) {
            errorStackTrace.add(new StackTraceElement(
                    "JavaScript",
                    frame.functionName(),
                    frame.filename(),
                    frame.lineNumber()
            ));
        }
    }

    /**
     * Capture stack trace and attach to error object.
     */
    private void captureStackTrace(JSObject error) {
        StringBuilder stackTrace = new StringBuilder();

        StackFrame vmStackFrame = virtualMachine != null ? virtualMachine.getCurrentFrame() : null;
        while (vmStackFrame != null) {
            JSFunction frameFunction = vmStackFrame.getFunction();
            String functionName = "<anonymous>";
            if (frameFunction != null) {
                String candidateFunctionName = frameFunction.getName();
                if (candidateFunctionName != null && !candidateFunctionName.isEmpty()) {
                    functionName = candidateFunctionName;
                }
            }
            String filename = frameFunction != null ? frameFunction.getImportMetaFilename() : null;
            if (filename == null || filename.isEmpty()) {
                filename = "<eval>";
            }
            stackTrace.append("    at ")
                    .append(functionName)
                    .append(" (")
                    .append(filename)
                    .append(":")
                    .append(1)
                    .append(")\n");
            vmStackFrame = vmStackFrame.getCaller();
        }

        for (JSStackFrame frame : callStack) {
            stackTrace.append("    at ")
                    .append(frame.functionName())
                    .append(" (")
                    .append(frame.filename())
                    .append(":")
                    .append(frame.lineNumber())
                    .append(")\n");
        }

        error.defineProperty(
                PropertyKey.STACK,
                PropertyDescriptor.dataDescriptor(
                        new JSString(stackTrace.toString()),
                        PropertyDescriptor.DataState.ConfigurableWritable));
    }

    private void chainImportPromiseOntoAsyncDependencies(
            List<JSPromise> dependencyPromises,
            JSObject namespace,
            JSPromise importPromise,
            JSPromise.ResolveState resolveState) {
        int[] remaining = new int[]{dependencyPromises.size()};
        for (JSPromise dependencyPromise : dependencyPromises) {
            JSNativeFunction onFulfill = new JSNativeFunction(this, "", 0,
                    (ctx, thisArg, args) -> {
                        remaining[0]--;
                        if (remaining[0] == 0 && !resolveState.alreadyResolved) {
                            resolveState.alreadyResolved = true;
                            importPromise.resolve(ctx, namespace);
                        }
                        return JSUndefined.INSTANCE;
                    });
            onFulfill.initializePrototypeChain(this);
            JSNativeFunction onReject = new JSNativeFunction(this, "", 1,
                    (ctx, thisArg, args) -> {
                        if (!resolveState.alreadyResolved) {
                            resolveState.alreadyResolved = true;
                            JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                            importPromise.reject(reason);
                        }
                        return JSUndefined.INSTANCE;
                    });
            onReject.initializePrototypeChain(this);
            dependencyPromise.addReactions(
                    new JSPromise.ReactionRecord(onFulfill, this, null, null),
                    new JSPromise.ReactionRecord(onReject, this, null, null));
        }
    }

    private void chainImportPromiseOntoAsyncModule(
            JSDynamicImportModule moduleRecord,
            JSPromise importPromise,
            JSPromise.ResolveState resolveState) {
        JSObject namespace = moduleRecord.namespace();
        JSNativeFunction onFulfill = new JSNativeFunction(this, "", 0,
                (ctx, thisArg, args) -> {
                    if (!resolveState.alreadyResolved) {
                        resolveState.alreadyResolved = true;
                        importPromise.resolve(ctx, namespace);
                    }
                    return JSUndefined.INSTANCE;
                });
        onFulfill.initializePrototypeChain(this);
        JSNativeFunction onReject = new JSNativeFunction(this, "", 1,
                (ctx, thisArg, args) -> {
                    if (!resolveState.alreadyResolved) {
                        resolveState.alreadyResolved = true;
                        JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                        importPromise.reject(reason);
                    }
                    return JSUndefined.INSTANCE;
                });
        onReject.initializePrototypeChain(this);
        moduleRecord.asyncEvaluationPromise().addReactions(
                new JSPromise.ReactionRecord(onFulfill, this, null, null),
                new JSPromise.ReactionRecord(onReject, this, null, null));
    }

    /**
     * Clear the pending exception in both context and VM.
     * This is needed when an async function catches an exception.
     */
    public void clearAllPendingExceptions() {
        clearPendingException();
        clearErrorStackTrace();
        virtualMachine.clearPendingException();
    }

    private void clearCallStack() {
        callStack.clear();
    }

    private void clearErrorStackTrace() {
        this.errorStackTrace.clear();
    }

    /**
     * Clear the module cache.
     */
    private void clearModuleCache() {
        dynamicImportModuleCache.clear();
        importMetaCache.clear();
        moduleCache.clear();
    }

    /**
     * Clear the pending exception.
     */
    public void clearPendingException() {
        this.pendingException = null;
    }

    /**
     * Close this context and release resources.
     * Called when the context is no longer needed.
     */
    @Override
    public void close() {
        // Clear all caches
        clearModuleCache();
        clearCallStack();
        clearPendingException();
        clearErrorStackTrace();
        // Remove from runtime
        runtime.destroyContext(this);
    }

    private Map<String, JSSymbol> collectEvalPrivateSymbols(JSBytecodeFunction callerFunction) {
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

    private void collectImportBindings(
            String importLine,
            Set<String> bindingNames,
            Map<String, ImportBinding> importedBindings) {
        Matcher namespaceMatcher = MODULE_NAMESPACE_IMPORT_PATTERN.matcher(importLine);
        if (namespaceMatcher.find()) {
            boolean deferredImport = "defer".equals(namespaceMatcher.group(1));
            String localName = namespaceMatcher.group(2);
            String sourceSpecifier = namespaceMatcher.group(4);
            registerImportedBinding(
                    localName,
                    sourceSpecifier,
                    "*namespace*",
                    deferredImport,
                    bindingNames,
                    importedBindings);
            return;
        }
        Matcher bindingMatcher = MODULE_BINDING_IMPORT_PATTERN.matcher(importLine);
        if (!bindingMatcher.find()) {
            return;
        }
        String clause = bindingMatcher.group(1).trim();
        String sourceSpecifier = bindingMatcher.group(3);
        if (clause.startsWith("*") || clause.startsWith("defer *")) {
            return;
        }
        if (clause.startsWith("{")) {
            collectNamedImportBindings(clause, sourceSpecifier, bindingNames, importedBindings);
            return;
        }
        int commaIndex = clause.indexOf(',');
        if (commaIndex < 0) {
            registerImportedBinding(clause, sourceSpecifier, "default", false, bindingNames, importedBindings);
            return;
        }
        String defaultName = clause.substring(0, commaIndex).trim();
        if (!defaultName.isEmpty()) {
            registerImportedBinding(defaultName, sourceSpecifier, "default", false, bindingNames, importedBindings);
        }
        String remainder = clause.substring(commaIndex + 1).trim();
        if (remainder.startsWith("{")) {
            collectNamedImportBindings(remainder, sourceSpecifier, bindingNames, importedBindings);
        } else if (remainder.startsWith("*")) {
            String namespaceBinding = remainder.replaceFirst("^\\*\\s*as\\s+", "").trim();
            if (!namespaceBinding.isEmpty()) {
                registerImportedBinding(
                        namespaceBinding,
                        sourceSpecifier,
                        "*namespace*",
                        false,
                        bindingNames,
                        importedBindings);
            }
        }
    }

    private void collectNamedImportBindings(
            String namedClause,
            String sourceSpecifier,
            Set<String> bindingNames,
            Map<String, ImportBinding> importedBindings) {
        String clause = namedClause.trim();
        if (!clause.startsWith("{") || !clause.endsWith("}")) {
            return;
        }
        String specifiersText = clause.substring(1, clause.length() - 1).trim();
        if (specifiersText.isEmpty()) {
            return;
        }
        for (String rawSpecifier : splitOnTopLevelCommas(specifiersText)) {
            String specifier = rawSpecifier.trim();
            if (specifier.isEmpty()) {
                continue;
            }
            String importedName;
            String localName;
            int asIndex = findTopLevelAs(specifier);
            if (asIndex >= 0) {
                importedName = parseModuleExportNameValue(specifier.substring(0, asIndex).trim());
                localName = specifier.substring(asIndex + 2).trim();
            } else {
                importedName = parseModuleExportNameValue(specifier);
                localName = importedName;
            }
            registerImportedBinding(localName, sourceSpecifier, importedName, false, bindingNames, importedBindings);
        }
    }

    public boolean consumeGlobalFunctionBindingInitialization(String name) {
        return activeGlobalFunctionBindingInitializations != null
                && activeGlobalFunctionBindingInitializations.remove(name);
    }

    public boolean consumeScheduledClassFieldEvalCall() {
        boolean result = pendingClassFieldEval;
        pendingClassFieldEval = false;
        return result;
    }

    public boolean consumeScheduledDirectEvalCall() {
        if (pendingDirectEvalCalls > 0) {
            pendingDirectEvalCalls--;
            return true;
        }
        return false;
    }

    public JSObject createImportMetaObject(String filename) {
        String cacheKey = filename != null ? filename : "";
        JSObject importMetaObject = importMetaCache.get(cacheKey);
        if (importMetaObject == null) {
            importMetaObject = new JSObject(this);
            importMetaObject.setPrototype(null);
            if (filename != null && !filename.isEmpty() && !filename.startsWith("<")) {
                importMetaObject.set(PropertyKey.fromString("url"), new JSString(filename));
            }
            importMetaCache.put(cacheKey, importMetaObject);
        }
        return importMetaObject;
    }

    public JSAggregateError createJSAggregateError(String message) {
        JSAggregateError jsError = new JSAggregateError(this, message);
        transferPrototype(jsError, JSAggregateError.NAME);
        captureStackTrace(jsError);
        return jsError;
    }

    /**
     * Create a new JSArray with proper prototype chain.
     * Sets the array's prototype to Array.prototype from the global object.
     *
     * @return A new JSArray instance with prototype set
     */
    public JSArray createJSArray() {
        return createJSArray(0);
    }

    /**
     * Create a new JSArray with specified length and proper prototype chain.
     * Sets the array's prototype to Array.prototype from the global object.
     *
     * @param length Initial length of the array
     * @return A new JSArray instance with prototype set
     */
    public JSArray createJSArray(long length) {
        return createJSArray(length, (int) length);
    }

    /**
     * Create a new JSArray with specified length, capacity, and proper prototype chain.
     * Sets the array's prototype to Array.prototype from the global object.
     *
     * @param length   Initial length of the array
     * @param capacity Initial capacity of the array
     * @return A new JSArray instance with prototype set
     */
    public JSArray createJSArray(long length, int capacity) {
        JSArray jsArray = new JSArray(this, length, capacity);
        transferPrototype(jsArray, JSArray.NAME);
        return jsArray;
    }

    /**
     * Create a new JSArray with specified values and proper prototype chain.
     * Sets the array's prototype to Array.prototype from the global object.
     *
     * @param values Initial values of the array
     * @return A new JSArray instance with prototype set
     */
    public JSArray createJSArray(JSValue... values) {
        JSArray jsArray = new JSArray(this, values);
        transferPrototype(jsArray, JSArray.NAME);
        return jsArray;
    }

    /**
     * Create a new JSArray taking ownership of a freshly allocated values array.
     * Internal fast path to avoid an extra defensive copy in hot built-in paths.
     */
    public JSArray createJSArray(JSValue[] values, boolean takeOwnership) {
        JSArray jsArray = new JSArray(this, values, takeOwnership);
        transferPrototype(jsArray, JSArray.NAME);
        return jsArray;
    }

    /**
     * Create a new JSArrayBuffer with proper prototype chain.
     *
     * @param byteLength The length in bytes
     * @return A new JSArrayBuffer instance with prototype set
     */
    public JSArrayBuffer createJSArrayBuffer(int byteLength) {
        JSArrayBuffer jsArrayBuffer = new JSArrayBuffer(this, byteLength);
        transferPrototype(jsArrayBuffer, JSArrayBuffer.NAME);
        return jsArrayBuffer;
    }

    /**
     * Create a new resizable JSArrayBuffer with proper prototype chain.
     *
     * @param byteLength    The initial length in bytes
     * @param maxByteLength The maximum length in bytes, or -1 for non-resizable
     * @return A new JSArrayBuffer instance with prototype set
     */
    public JSArrayBuffer createJSArrayBuffer(int byteLength, int maxByteLength) {
        JSArrayBuffer jsArrayBuffer = new JSArrayBuffer(this, byteLength, maxByteLength);
        transferPrototype(jsArrayBuffer, JSArrayBuffer.NAME);
        return jsArrayBuffer;
    }

    /**
     * ES2024 7.3.34 ArraySpeciesCreate(originalArray, length).
     * Following QuickJS JS_ArraySpeciesCreate.
     *
     * @return the new array-like object, or null if an exception was set on context
     */
    public JSValue createJSArraySpecies(JSObject originalArray, long length) {
        // Step 3: If IsArray(originalArray) is false, return ArrayCreate(length)
        int isArr = JSTypeChecking.isArray(this, originalArray);
        if (isArr < 0) {
            return null;
        }
        if (isArr == 0) {
            // ArrayCreate(length): throw RangeError if length > 2^32 - 1
            if (length > 0xFFFFFFFFL) {
                return throwRangeError("Invalid array length");
            }
            return createJSArray(length, 0);
        }

        // Step 4: Let C be ? Get(originalArray, "constructor")
        JSValue ctor = originalArray.get(PropertyKey.CONSTRUCTOR);
        if (hasPendingException()) {
            return null;
        }

        // Step 5: If IsConstructor(C) is true, cross-realm check.
        // ES2024 10.4.2.3 ArraySpeciesCreate step 6.a-c:
        // if constructor realm differs and C is that realm's intrinsic %Array%,
        // treat C as undefined.
        if (JSTypeChecking.isConstructor(ctor) && ctor instanceof JSObject ctorObject) {
            JSContext constructorRealm = getFunctionRealm(ctorObject);
            if (hasPendingException()) {
                return null;
            }
            if (constructorRealm != this) {
                JSValue realmArrayConstructor = constructorRealm.getGlobalObject().get(JSArray.NAME);
                if (ctor == realmArrayConstructor) {
                    ctor = JSUndefined.INSTANCE;
                }
            }
        }

        // Step 6: If Type(C) is Object, get Symbol.species
        if (ctor instanceof JSObject ctorObj) {
            ctor = ctorObj.get(PropertyKey.SYMBOL_SPECIES);
            if (hasPendingException()) {
                return null;
            }
            if (ctor instanceof JSNull) {
                ctor = JSUndefined.INSTANCE;
            }
        }

        // Step 7: If C is undefined, return ArrayCreate(length)
        if (ctor instanceof JSUndefined) {
            // ArrayCreate(length): throw RangeError if length > 2^32 - 1
            if (length > 0xFFFFFFFFL) {
                return throwRangeError("Invalid array length");
            }
            return createJSArray(length, 0);
        }

        // Step 8: If IsConstructor(C) is false, throw a TypeError exception
        if (!JSTypeChecking.isConstructor(ctor)) {
            return throwTypeError("Species constructor is not a constructor");
        }

        // Step 9: Return ? Construct(C, « length »)
        JSValue result = JSReflectObject.constructSimple(this, ctor, new JSValue[]{JSNumber.of(length)});
        if (hasPendingException()) {
            return null;
        }
        return result;
    }

    /**
     * Create a new JSBigInt64Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSBigInt64Array instance with prototype set
     */
    public JSBigInt64Array createJSBigInt64Array(int length) {
        return initializeTypedArray(new JSBigInt64Array(this, length), JSBigInt64Array.NAME);
    }

    public JSBigInt64Array createJSBigInt64Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSBigInt64Array(this, buffer, byteOffset, length), JSBigInt64Array.NAME);
    }

    public JSBigIntObject createJSBigIntObject(JSBigInt value) {
        JSBigIntObject wrapper = new JSBigIntObject(this, value);
        transferPrototype(wrapper, JSBigIntObject.NAME);
        return wrapper;
    }

    /**
     * Create a new JSBigUint64Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSBigUint64Array instance with prototype set
     */
    public JSBigUint64Array createJSBigUint64Array(int length) {
        return initializeTypedArray(new JSBigUint64Array(this, length), JSBigUint64Array.NAME);
    }

    public JSBigUint64Array createJSBigUint64Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSBigUint64Array(this, buffer, byteOffset, length), JSBigUint64Array.NAME);
    }

    public JSBooleanObject createJSBooleanObject(JSBoolean value) {
        JSBooleanObject wrapper = new JSBooleanObject(this, value);
        transferPrototype(wrapper, JSBooleanObject.NAME);
        return wrapper;
    }

    /**
     * Create a new JSDataView with proper prototype chain.
     *
     * @param buffer     The ArrayBuffer to view
     * @param byteOffset The offset in bytes
     * @param byteLength The length in bytes
     * @return A new JSDataView instance with prototype set
     */
    public JSDataView createJSDataView(IJSArrayBuffer buffer, int byteOffset, int byteLength) {
        JSDataView jsDataView = new JSDataView(this, buffer, byteOffset, byteLength);
        transferPrototype(jsDataView, JSDataView.NAME);
        return jsDataView;
    }

    /**
     * Create a new JSDate with proper prototype chain.
     *
     * @param timeValue The time value in milliseconds
     * @return A new JSDate instance with prototype set
     */
    public JSDate createJSDate(double timeValue) {
        JSDate jsDate = new JSDate(this, timeValue);
        if (cachedDatePrototype != null) {
            jsDate.setPrototype(cachedDatePrototype);
        } else {
            transferPrototype(jsDate, JSDate.NAME);
        }
        return jsDate;
    }

    public JSDisposableStack createJSDisposableStack() {
        JSDisposableStack stack = new JSDisposableStack(this);
        transferPrototype(stack, JSDisposableStack.NAME);
        return stack;
    }

    public JSError createJSError(String message) {
        JSError jsError = new JSError(this, message);
        transferPrototype(jsError, JSError.NAME);
        captureStackTrace(jsError);
        return jsError;
    }

    public JSEvalError createJSEvalError(String message) {
        JSEvalError jsError = new JSEvalError(this, message);
        transferPrototype(jsError, JSEvalError.NAME);
        captureStackTrace(jsError);
        return jsError;
    }

    /**
     * Create a new JSFloat16Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSFloat16Array instance with prototype set
     */
    public JSFloat16Array createJSFloat16Array(int length) {
        return initializeTypedArray(new JSFloat16Array(this, length), JSFloat16Array.NAME);
    }

    public JSFloat16Array createJSFloat16Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSFloat16Array(this, buffer, byteOffset, length), JSFloat16Array.NAME);
    }

    /**
     * Create a new JSFloat32Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSFloat32Array instance with prototype set
     */
    public JSFloat32Array createJSFloat32Array(int length) {
        return initializeTypedArray(new JSFloat32Array(this, length), JSFloat32Array.NAME);
    }

    public JSFloat32Array createJSFloat32Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSFloat32Array(this, buffer, byteOffset, length), JSFloat32Array.NAME);
    }

    /**
     * Create a new JSFloat64Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSFloat64Array instance with prototype set
     */
    public JSFloat64Array createJSFloat64Array(int length) {
        return initializeTypedArray(new JSFloat64Array(this, length), JSFloat64Array.NAME);
    }

    public JSFloat64Array createJSFloat64Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSFloat64Array(this, buffer, byteOffset, length), JSFloat64Array.NAME);
    }

    /**
     * Create a new JSInt16Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSInt16Array instance with prototype set
     */
    public JSInt16Array createJSInt16Array(int length) {
        return initializeTypedArray(new JSInt16Array(this, length), JSInt16Array.NAME);
    }

    public JSInt16Array createJSInt16Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSInt16Array(this, buffer, byteOffset, length), JSInt16Array.NAME);
    }

    /**
     * Create a new JSInt32Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSInt32Array instance with prototype set
     */
    public JSInt32Array createJSInt32Array(int length) {
        return initializeTypedArray(new JSInt32Array(this, length), JSInt32Array.NAME);
    }

    public JSInt32Array createJSInt32Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSInt32Array(this, buffer, byteOffset, length), JSInt32Array.NAME);
    }

    /**
     * Create a new JSInt8Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSInt8Array instance with prototype set
     */
    public JSInt8Array createJSInt8Array(int length) {
        return initializeTypedArray(new JSInt8Array(this, length), JSInt8Array.NAME);
    }

    public JSInt8Array createJSInt8Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSInt8Array(this, buffer, byteOffset, length), JSInt8Array.NAME);
    }

    /**
     * Create a new JSMap with proper prototype chain.
     *
     * @return A new JSMap instance with prototype set
     */
    public JSMap createJSMap() {
        JSMap jsMap = new JSMap(this);
        transferPrototype(jsMap, JSMap.NAME);
        return jsMap;
    }

    public JSNumberObject createJSNumberObject(JSNumber value) {
        JSNumberObject wrapper = new JSNumberObject(this, value);
        transferPrototype(wrapper, JSNumberObject.NAME);
        return wrapper;
    }

    /**
     * Create a new JSObject with proper prototype chain.
     * Sets the object's prototype to Object.prototype from the global object.
     *
     * @return A new JSObject instance with prototype set
     */
    public JSObject createJSObject() {
        JSObject jsObject = new JSObject(this);
        if (cachedObjectPrototype != null) {
            jsObject.setPrototype(cachedObjectPrototype);
        } else {
            transferPrototype(jsObject, JSObject.NAME);
        }
        return jsObject;
    }

    /**
     * Create a new JSPromise with proper prototype chain.
     *
     * @return A new JSPromise instance with prototype set
     */
    public JSPromise createJSPromise() {
        JSPromise jsPromise = new JSPromise(this);
        if (cachedPromisePrototype != null) {
            jsPromise.setPrototype(cachedPromisePrototype);
        } else {
            transferPrototype(jsPromise, JSPromise.NAME);
        }
        return jsPromise;
    }

    public JSRangeError createJSRangeError(String message) {
        JSRangeError jsError = new JSRangeError(this, message);
        transferPrototype(jsError, JSRangeError.NAME);
        captureStackTrace(jsError);
        return jsError;
    }

    public JSReferenceError createJSReferenceError(String message) {
        JSReferenceError jsError = new JSReferenceError(this, message);
        transferPrototype(jsError, JSReferenceError.NAME);
        captureStackTrace(jsError);
        return jsError;
    }

    /**
     * Create a new JSRegExp with proper prototype chain.
     *
     * @param pattern The regular expression pattern
     * @param flags   The regular expression flags
     * @return A new JSRegExp instance with prototype set
     */
    public JSRegExp createJSRegExp(String pattern, String flags) {
        JSRegExp jsRegExp = new JSRegExp(this, pattern, flags);
        transferPrototype(jsRegExp, JSRegExp.NAME);
        return jsRegExp;
    }

    /**
     * Create a new JSSet with proper prototype chain.
     *
     * @return A new JSSet instance with prototype set
     */
    public JSSet createJSSet() {
        JSSet jsSet = new JSSet(this);
        transferPrototype(jsSet, JSSet.NAME);
        return jsSet;
    }

    public JSStringObject createJSStringObject() {
        JSStringObject wrapper = new JSStringObject(this);
        transferPrototype(wrapper, JSStringObject.NAME);
        return wrapper;
    }

    public JSStringObject createJSStringObject(JSString value) {
        JSStringObject wrapper = new JSStringObject(this, value);
        transferPrototype(wrapper, JSStringObject.NAME);
        return wrapper;
    }

    public JSSuppressedError createJSSuppressedError(String message) {
        JSSuppressedError jsError = new JSSuppressedError(this, message);
        transferPrototype(jsError, JSSuppressedError.NAME);
        captureStackTrace(jsError);
        return jsError;
    }

    public JSSymbolObject createJSSymbolObject(JSSymbol value) {
        JSSymbolObject wrapper = new JSSymbolObject(this, value);
        transferPrototype(wrapper, JSSymbolObject.NAME);
        return wrapper;
    }

    public JSSyntaxError createJSSyntaxError(String message) {
        JSSyntaxError jsError = new JSSyntaxError(this, message);
        transferPrototype(jsError, JSSyntaxError.NAME);
        captureStackTrace(jsError);
        return jsError;
    }

    public JSTypeError createJSTypeError(String message) {
        JSTypeError jsError = new JSTypeError(this, message);
        transferPrototype(jsError, JSTypeError.NAME);
        captureStackTrace(jsError);
        return jsError;
    }

    public JSURIError createJSURIError(String message) {
        JSURIError jsError = new JSURIError(this, message);
        transferPrototype(jsError, JSURIError.NAME);
        captureStackTrace(jsError);
        return jsError;
    }

    /**
     * Create a new JSUint16Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint16Array instance with prototype set
     */
    public JSUint16Array createJSUint16Array(int length) {
        return initializeTypedArray(new JSUint16Array(this, length), JSUint16Array.NAME);
    }

    public JSUint16Array createJSUint16Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSUint16Array(this, buffer, byteOffset, length), JSUint16Array.NAME);
    }

    /**
     * Create a new JSUint32Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint32Array instance with prototype set
     */
    public JSUint32Array createJSUint32Array(int length) {
        return initializeTypedArray(new JSUint32Array(this, length), JSUint32Array.NAME);
    }

    public JSUint32Array createJSUint32Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSUint32Array(this, buffer, byteOffset, length), JSUint32Array.NAME);
    }

    /**
     * Create a new JSUint8Array with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint8Array instance with prototype set
     */
    public JSUint8Array createJSUint8Array(int length) {
        return initializeTypedArray(new JSUint8Array(this, length), JSUint8Array.NAME);
    }

    public JSUint8Array createJSUint8Array(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSUint8Array(this, buffer, byteOffset, length), JSUint8Array.NAME);
    }

    /**
     * Create a new JSUint8ClampedArray with proper prototype chain.
     *
     * @param length The length of the array
     * @return A new JSUint8ClampedArray instance with prototype set
     */
    public JSUint8ClampedArray createJSUint8ClampedArray(int length) {
        return initializeTypedArray(new JSUint8ClampedArray(this, length), JSUint8ClampedArray.NAME);
    }

    public JSUint8ClampedArray createJSUint8ClampedArray(IJSArrayBuffer buffer, int byteOffset, int length) {
        return initializeTypedArray(new JSUint8ClampedArray(this, buffer, byteOffset, length), JSUint8ClampedArray.NAME);
    }

    /**
     * Create a new JSWeakMap with proper prototype chain.
     *
     * @return A new JSWeakMap instance with prototype set
     */
    public JSWeakMap createJSWeakMap() {
        JSWeakMap jsWeakMap = new JSWeakMap(this);
        transferPrototype(jsWeakMap, JSWeakMap.NAME);
        return jsWeakMap;
    }

    /**
     * Create a new JSWeakSet with proper prototype chain.
     *
     * @return A new JSWeakSet instance with prototype set
     */
    public JSWeakSet createJSWeakSet() {
        JSWeakSet jsWeakSet = new JSWeakSet(this);
        transferPrototype(jsWeakSet, JSWeakSet.NAME);
        return jsWeakSet;
    }

    private String createModuleExportBindingName(String resolvedSpecifier) {
        return "__qjs4jDynamicImportExports$" + Math.abs(resolvedSpecifier.hashCode()) + "$"
                + dynamicImportModuleCache.size();
    }

    private JSImportNamespaceObject createModuleNamespaceObject() {
        return new JSImportNamespaceObject(this);
    }

    private String decodeIdentifierEscapes(String text) {
        if (text == null || text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder decodedTextBuilder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch != '\\' || index + 1 >= text.length() || text.charAt(index + 1) != 'u') {
                decodedTextBuilder.append(ch);
                continue;
            }
            int escapeStart = index;
            index += 2;
            if (index < text.length() && text.charAt(index) == '{') {
                int braceEnd = text.indexOf('}', index + 1);
                if (braceEnd < 0) {
                    decodedTextBuilder.append(text, escapeStart, index + 1);
                    index = escapeStart;
                    continue;
                }
                String codePointText = text.substring(index + 1, braceEnd);
                int codePoint = parseHex(codePointText);
                if (codePoint >= 0) {
                    decodedTextBuilder.appendCodePoint(codePoint);
                    index = braceEnd;
                } else {
                    decodedTextBuilder.append(text, escapeStart, braceEnd + 1);
                    index = braceEnd;
                }
                continue;
            }
            if (index + 3 >= text.length()) {
                decodedTextBuilder.append(text, escapeStart, text.length());
                break;
            }
            String hexText = text.substring(index, index + 4);
            int codePoint = parseHex(hexText);
            if (codePoint >= 0) {
                decodedTextBuilder.append((char) codePoint);
                index += 3;
            } else {
                decodedTextBuilder.append(text, escapeStart, index + 4);
                index += 3;
            }
        }
        return decodedTextBuilder.toString();
    }

    private void defineDynamicImportNamespaceForwardingBinding(
            JSDynamicImportModule moduleRecord,
            String exportName,
            JSDynamicImportModule targetModuleRecord,
            String targetSpecifier,
            String importedName) {
        JSImportNamespaceObject namespace = moduleRecord.namespace();
        PropertyKey exportKey = PropertyKey.fromString(exportName);
        if (namespace.hasDefinedOwnProperty(exportKey)) {
            return;
        }
        JSNativeFunction getter = new JSNativeFunction(this, "get " + exportName, 0,
                (ctx, thisArg, args) -> {
                    if ("*namespace*".equals(importedName)) {
                        return targetModuleRecord.namespace();
                    }
                    String resolvedImportedName = getDynamicImportModuleExport(
                            targetModuleRecord, importedName, targetSpecifier);
                    return targetModuleRecord.namespace().get(PropertyKey.fromString(resolvedImportedName));
                });
        getter.initializePrototypeChain(this);
        PropertyDescriptor descriptor = new PropertyDescriptor();
        descriptor.setGetter(getter);
        descriptor.setEnumerable(true);
        descriptor.setConfigurable(true);
        namespace.defineExportBinding(this, exportKey, descriptor);
        namespace.registerExportName(exportName);
    }

    private void defineDynamicImportNamespaceValue(
            JSDynamicImportModule moduleRecord,
            String exportName,
            JSValue exportValue) {
        // Use All (writable, enumerable, configurable) during construction so that
        // mergeStarReExport can delete ambiguous bindings. finalizeNamespace() will
        // report them as non-configurable via getOwnPropertyDescriptor override.
        moduleRecord.namespace().defineExportBinding(
                this,
                PropertyKey.fromString(exportName),
                exportValue,
                PropertyDescriptor.DataState.All);
        moduleRecord.namespace().registerExportName(exportName);
    }

    /**
     * Enqueue a microtask to be executed.
     *
     * @param microtask The microtask to enqueue
     */
    public void enqueueMicrotask(JSMicrotaskQueue.Microtask microtask) {
        microtaskQueue.enqueue(microtask);
    }

    /**
     * Enter strict mode.
     */
    public void enterStrictMode() {
        this.strictMode = true;
    }

    private String escapeJavaScriptString(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    /**
     * Evaluate JavaScript code in this context.
     * <p>
     * In full implementation, this would:
     * 1. Parse the source code
     * 2. Compile to bytecode
     * 3. Execute the bytecode
     * 4. Return the completion value
     *
     * @param code JavaScript source code
     * @return The completion value, or exception if eval throws
     */
    public JSValue eval(String code) {
        return evalOrThrow(eval(code, "<eval>", false, false, false, false, false, false));
    }

    /**
     * Evaluate code with source location information.
     *
     * @param code     JavaScript source code
     * @param filename Source filename for stack traces
     * @param isModule Whether to evaluate as module (vs script)
     * @return The completion value
     */
    public JSValue eval(String code, String filename, boolean isModule) {
        return evalOrThrow(eval(code, filename, isModule, false, false, false, false, false));
    }

    /**
     * Eval js value.
     *
     * @param code         the code
     * @param filename     the filename
     * @param isModule     the is module
     * @param isDirectEval the is direct eval
     * @return the js value
     */
    public JSValue eval(String code, String filename, boolean isModule, boolean isDirectEval) {
        return evalOrThrow(eval(code, filename, isModule, isDirectEval, false, false, false, false));
    }

    private JSValue eval(String code, String filename, boolean isModule, boolean isDirectEval,
                         boolean predeclareProgramLexicalsAsLocals,
                         boolean skipGlobalDeclarationTracking,
                         boolean inheritedStrictModeForDirectEval,
                         boolean useDirectEvalCallerFrame) {
        if (code == null || code.isEmpty()) {
            return JSUndefined.INSTANCE;
        }

        // Check for recursion limit
        if (!pushStackFrame(new JSStackFrame("<eval>", filename, 1))) {
            return throwError("RangeError", "Maximum call stack size exceeded");
        }

        JSDynamicImportModule dynamicImportEvalModuleRecord = null;
        EvalOverlayFrame moduleNamespaceImportOverlay = null;
        JSDynamicImportModule selfModuleRecord = null;
        JSDynamicImportModule.Status selfModulePreviousStatus = null;
        boolean removeSelfModuleRecordAfterEval = false;
        boolean skipEvaluatedDynamicImportModule = false;
        boolean shouldTrackDynamicImportModule = isModule
                && !isDirectEval
                && filename != null
                && !filename.isEmpty()
                && !filename.startsWith("<")
                && (code.contains("import(") || code.contains("import.defer(")
                || hasModuleExportSyntax(code)
                || hasModuleStaticImportSyntax(code)
                || hasModuleTopLevelAwaitSyntax(code));
        if (shouldTrackDynamicImportModule) {
            String resolvedModuleSpecifier;
            try {
                resolvedModuleSpecifier = resolveDynamicImportSpecifier(filename, null, filename);
            } catch (JSException jsException) {
                resolvedModuleSpecifier = normalizeModuleSpecifier(filename);
            }
            JSDynamicImportModule existingRecord = dynamicImportModuleCache.get(resolvedModuleSpecifier);
            boolean executingTransformedModuleSource = existingRecord != null
                    && !Objects.equals(existingRecord.rawSource(), code)
                    && Objects.equals(existingRecord.transformedSource(), code);
            if (!executingTransformedModuleSource) {
                dynamicImportEvalModuleRecord = existingRecord;
                if (dynamicImportEvalModuleRecord == null) {
                    dynamicImportEvalModuleRecord =
                            new JSDynamicImportModule(resolvedModuleSpecifier, createModuleNamespaceObject());
                    dynamicImportEvalModuleRecord.setStatus(JSDynamicImportModule.Status.LOADING);
                    dynamicImportEvalModuleRecord.setRawSource(code);
                    // Validate the original source for early errors (duplicate exports,
                    // unresolvable bindings, etc.) before doing IIFE transformation.
                    new Compiler(code, filename).setContext(this).parse(true);
                    parseDynamicImportModuleSource(dynamicImportEvalModuleRecord);
                    dynamicImportModuleCache.put(resolvedModuleSpecifier, dynamicImportEvalModuleRecord);
                } else if (dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.EVALUATED) {
                    skipEvaluatedDynamicImportModule = true;
                }
            }
        }
        boolean evaluatingRawDynamicImportModule =
                dynamicImportEvalModuleRecord != null
                        && Objects.equals(dynamicImportEvalModuleRecord.rawSource(), code);
        boolean shouldEvaluateRawModuleThroughTransformedSource =
                evaluatingRawDynamicImportModule
                        && !dynamicImportEvalModuleRecord.hasExportSyntax()
                        && !dynamicImportEvalModuleRecord.hasTLA()
                        && hasModuleStaticImportSyntax(code)
                        && code.contains("import(");
        boolean shouldEvaluateRawTopLevelAwaitModule =
                evaluatingRawDynamicImportModule
                        && !dynamicImportEvalModuleRecord.hasExportSyntax()
                        && dynamicImportEvalModuleRecord.hasTLA()
                        && !hasModuleStaticImportSyntax(code);

        Compiler compiler = new Compiler(code, filename).setContext(this);
        // Per QuickJS, eval code has is_eval=true which prevents top-level return.
        // Only syntactic direct eval should inherit caller frame semantics.
        StackFrame directEvalCallerFrame = isDirectEval && useDirectEvalCallerFrame
                ? virtualMachine.getCurrentFrame()
                : null;
        boolean allowNewTargetInEval = false;
        boolean allowSuperPropertyInEval = false;
        boolean allowSuperCallInEval = false;
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
            if (directEvalCallerFrame != null && (strictMode || inheritedStrictModeForDirectEval)) {
                compiler.setInheritedStrictMode(true);
            }
        }
        if (predeclareProgramLexicalsAsLocals) {
            compiler.setPredeclareProgramLexicalsAsLocals(true);
        }
        JSValue evalError = null;
        try {
            if (skipEvaluatedDynamicImportModule) {
                processMicrotasks();
                return JSUndefined.INSTANCE;
            }

            if (evaluatingRawDynamicImportModule
                    && (dynamicImportEvalModuleRecord.hasExportSyntax()
                    || shouldEvaluateRawModuleThroughTransformedSource
                    || shouldEvaluateRawTopLevelAwaitModule)) {
                JSValue evalResult = evaluateDynamicImportModule(dynamicImportEvalModuleRecord);
                if (dynamicImportEvalModuleRecord.hasTLA() && evalResult instanceof JSPromise asyncPromise) {
                    dynamicImportEvalModuleRecord.setAsyncEvaluationOrder(asyncEvaluationOrderCounter++);
                    dynamicImportEvalModuleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING_ASYNC);
                    dynamicImportEvalModuleRecord.setAsyncEvaluationPromise(asyncPromise);
                    registerAsyncModuleCompletion(dynamicImportEvalModuleRecord, asyncPromise, new HashSet<>());
                } else {
                    if (dynamicImportEvalModuleRecord.hasExportSyntax()) {
                        resolveDynamicImportReExports(dynamicImportEvalModuleRecord, new HashSet<>());
                        dynamicImportEvalModuleRecord.namespace().finalizeNamespace();
                    }
                    dynamicImportEvalModuleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                }
                if (!suppressEvalMicrotaskProcessing) {
                    processMicrotasks();
                }
                if (dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
                    evalError = dynamicImportEvalModuleRecord.evaluationError();
                    return null;
                }
                return JSUndefined.INSTANCE;
            }

            // Phase 1-3: Lexer → Parser → Compiler (compile to bytecode)
            JSBytecodeFunction func;
            Compiler.CompileResult compileResult = compiler.compile(isModule);
            func = compileResult.function();
            Set<String> globalScriptFunctionNames = null;
            if (!isModule && !isDirectEval && !skipGlobalDeclarationTracking) {
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
                    if (globalLexDeclarations.contains(name)) {
                        throw new JSSyntaxErrorException(
                                "Identifier '" + name + "' has already been declared");
                    }
                    // Check for non-configurable property on global object
                    PropertyKey key = PropertyKey.fromString(name);
                    PropertyDescriptor desc = jsGlobalObject.getGlobalObject().getOwnPropertyDescriptor(key);
                    if (desc != null && !desc.isConfigurable()) {
                        throw new JSSyntaxErrorException(
                                "Identifier '" + name + "' has already been declared");
                    }
                    // Check against script-level var declarations (these should be
                    // non-configurable per spec, tracked separately)
                    if (globalVarDeclarations.contains(name)) {
                        throw new JSSyntaxErrorException(
                                "Identifier '" + name + "' has already been declared");
                    }
                }

                // Check: var/function names must not collide with existing lex declarations
                for (String name : newVarDecls) {
                    if (globalLexDeclarations.contains(name)) {
                        throw new JSSyntaxErrorException(
                                "Identifier '" + name + "' has already been declared");
                    }
                }

                // Check CreateGlobalFunctionBinding preconditions before execution.
                for (String functionName : globalScriptFunctionNames) {
                    PropertyKey key = PropertyKey.fromString(functionName);
                    PropertyDescriptor desc = jsGlobalObject.getGlobalObject().getOwnPropertyDescriptor(key);
                    if (desc == null) {
                        if (!jsGlobalObject.getGlobalObject().isExtensible()) {
                            throw new JSException(throwTypeError("cannot define variable '" + functionName + "'"));
                        }
                        continue;
                    }
                    if (!desc.isConfigurable()) {
                        if (desc.isAccessorDescriptor()
                                || !(desc.isWritable() && desc.isEnumerable())) {
                            throw new JSException(throwTypeError("cannot define variable '" + functionName + "'"));
                        }
                    }
                }

                // Register new declarations for future collision checks
                globalConstDeclarations.addAll(newConstDecls);
                globalLexDeclarations.addAll(newLexDecls);
                globalVarDeclarations.addAll(newVarDecls);
                for (String lexicalName : newLexDecls) {
                    globalLexicalBindings.putIfAbsent(lexicalName, GLOBAL_LEXICAL_UNINITIALIZED);
                }

                // CreateGlobalVarDeclaration: define var bindings as non-configurable
                // properties on the global object (per ES2024 9.1.1.4.17 / QuickJS
                // js_closure_define_global_var with is_direct_or_indirect_eval=FALSE).
                // This must happen BEFORE execution so bindings exist at script start.
                for (String name : newVarDecls) {
                    if (globalScriptFunctionNames.contains(name)) {
                        continue;
                    }
                    PropertyKey key = PropertyKey.fromString(name);
                    PropertyDescriptor existing = jsGlobalObject.getGlobalObject().getOwnPropertyDescriptor(key);
                    if (existing == null && !jsGlobalObject.getGlobalObject().isExtensible()) {
                        throw new JSException(throwTypeError("cannot define variable '" + name + "'"));
                    }
                    if (existing == null) {
                        // Property doesn't exist: create {writable, enumerable, NOT configurable}
                        jsGlobalObject.getGlobalObject().defineProperty(key,
                                PropertyDescriptor.dataDescriptor(
                                        JSUndefined.INSTANCE,
                                        PropertyDescriptor.DataState.EnumerableWritable
                                ));
                    }
                }
            }

            // For eval code: EvalDeclarationInstantiation step 8 (ES2024 19.2.1.3)
            // Check CanDeclareGlobalFunction for all function declarations before executing.
            // If any function declaration targets a non-configurable global property that is
            // not both writable and enumerable, throw TypeError before any code runs.
            // Following QuickJS js_closure2 first-pass check with JS_CheckDefineGlobalVar.
            Set<String> globalEvalFunctionNames = null;
            if (!isModule && isDirectEval) {
                Program.GlobalDeclarations globalDeclarations = compileResult.ast().getGlobalDeclarations();
                Set<String> evalVarDeclarations = globalDeclarations.varDeclarations();
                globalEvalFunctionNames = globalDeclarations.functionDeclarations();
                for (String functionName : globalEvalFunctionNames) {
                    PropertyKey key = PropertyKey.fromString(functionName);
                    PropertyDescriptor desc = jsGlobalObject.getGlobalObject().getOwnPropertyDescriptor(key);
                    if (desc != null && !desc.isConfigurable()) {
                        if (desc.isAccessorDescriptor()
                                || !(desc.isWritable() && desc.isEnumerable())) {
                            throw new JSException(throwTypeError("cannot define variable '" + functionName + "'"));
                        }
                    }
                    if (desc == null && !jsGlobalObject.getGlobalObject().isExtensible()) {
                        throw new JSException(throwTypeError("cannot define variable '" + functionName + "'"));
                    }
                    if (directEvalCallerFrame != null && directEvalCallerFrame.getCaller() == null) {
                        if (desc == null || desc.isConfigurable()) {
                            JSValue initialValue = desc != null && desc.hasValue()
                                    ? desc.getValue()
                                    : JSUndefined.INSTANCE;
                            jsGlobalObject.getGlobalObject().defineProperty(
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
                        if (!jsGlobalObject.getGlobalObject().has(key)
                                && !jsGlobalObject.getGlobalObject().isExtensible()) {
                            throw new JSException(throwTypeError("cannot define variable '" + declarationName + "'"));
                        }
                    }
                }
            }

            // Initialize the function's prototype chain so it inherits from Function.prototype
            func.initializePrototypeChain(this);
            func.setImportMetaFilename(filename);

            if (isModule && !isDirectEval
                    && filename != null && !filename.isEmpty() && !filename.startsWith("<")) {
                // Register the current module as EVALUATING in the cache so that
                // self-imports (import defer * as self from './thisFile.js')
                // can detect re-entrancy and throw TypeError instead of recursing.
                String normalizedFilename = Paths.get(filename).normalize().toString();
                JSDynamicImportModule existingRecord = dynamicImportModuleCache.get(normalizedFilename);
                if (existingRecord != null) {
                    // Module already in cache (e.g. from loadJSDynamicImportModule).
                    // Mark it as EVALUATING so self-imports detect the re-entrancy.
                    selfModulePreviousStatus = existingRecord.status();
                    existingRecord.setStatus(JSDynamicImportModule.Status.EVALUATING);
                    selfModuleRecord = existingRecord;
                } else {
                    selfModuleRecord = new JSDynamicImportModule(
                            normalizedFilename, createModuleNamespaceObject());
                    selfModuleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING);
                    selfModuleRecord.setRawSource(code);
                    dynamicImportModuleCache.put(normalizedFilename, selfModuleRecord);
                    removeSelfModuleRecordAfterEval = true;
                }
                initializeHoistedFunctionExportBindings(selfModuleRecord);
                moduleNamespaceImportOverlay = evaluateModuleImportsInOrder(code, filename);
            }

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
            setGlobalFunctionBindingInitializations(
                    globalFunctionBindingInitializations,
                    globalFunctionBindingsConfigurable);

            // Phase 4: Execute bytecode in the virtual machine
            // For direct eval, inherit the caller's 'this' binding per ES2024 PerformEval.
            // In strict mode functions called without receiver, 'this' is undefined, and
            // eval('this') must see that same undefined value, not the global object.
            // ES2024 16.2.1.6.4: Module top-level 'this' is undefined.
            JSValue evalThisArg = isModule && !isDirectEval
                    ? JSUndefined.INSTANCE
                    : jsGlobalObject.getGlobalObject();
            JSValue evalNewTarget = JSUndefined.INSTANCE;
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
            JSValue result;
            try {
                result = virtualMachine.execute(func, evalThisArg, JSValue.NO_ARGS, evalNewTarget);
            } finally {
                setGlobalFunctionBindingInitializations(null, false);
            }
            if (isModule
                    && !isDirectEval
                    && moduleNamespaceImportOverlay != null
                    && evaluatingRawDynamicImportModule
                    && dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.hasExportSyntax()
                    && result instanceof JSPromise asyncModulePromise) {
                registerDeferredEvalOverlayRestore(asyncModulePromise, moduleNamespaceImportOverlay);
                moduleNamespaceImportOverlay = null;
            }

            if (!isModule
                    && isDirectEval
                    && directEvalCallerFrame != null
                    && directEvalCallerFrame.getCaller() == null) {
                if (globalEvalFunctionNames == null) {
                    globalEvalFunctionNames = new LinkedHashSet<>();
                }
                for (String functionName : globalEvalFunctionNames) {
                    PropertyKey key = PropertyKey.fromString(functionName);
                    if (!jsGlobalObject.getGlobalObject().has(key)) {
                        continue;
                    }
                    JSValue functionValue = jsGlobalObject.getGlobalObject().get(key);
                    PropertyDescriptor existingDescriptor = jsGlobalObject.getGlobalObject().getOwnPropertyDescriptor(key);
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
                    jsGlobalObject.getGlobalObject().defineProperty(key, descriptor);
                }
            }
            if (!isModule && !isDirectEval && globalScriptFunctionNames != null) {
                for (String functionName : globalScriptFunctionNames) {
                    PropertyKey key = PropertyKey.fromString(functionName);
                    if (!jsGlobalObject.getGlobalObject().has(key)) {
                        continue;
                    }
                    JSValue functionValue = jsGlobalObject.getGlobalObject().get(key);
                    PropertyDescriptor descriptor = new PropertyDescriptor();
                    descriptor.setValue(functionValue);
                    descriptor.setWritable(true);
                    descriptor.setEnumerable(true);
                    descriptor.setConfigurable(false);
                    jsGlobalObject.getGlobalObject().defineProperty(key, descriptor);
                }
            }

            // Check if there's a pending exception
            if (hasPendingException()) {
                evalError = getPendingException();
                return null;
            }

            // Process all pending microtasks before returning
            if (!suppressEvalMicrotaskProcessing) {
                processMicrotasks();
            }

            if (evaluatingRawDynamicImportModule
                    && dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.LOADING) {
                dynamicImportEvalModuleRecord.namespace().finalizeNamespace();
                dynamicImportEvalModuleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
            }

            return result != null ? result : JSUndefined.INSTANCE;
        } catch (JSException e) {
            evalError = e.getErrorValue();
            return null;
        } catch (JSSyntaxErrorException e) {
            evalError = throwError("SyntaxError", e.getMessage());
            return null;
        } catch (JSCompilerException e) {
            evalError = throwError("SyntaxError", e.getMessage());
            return null;
        } catch (JSVirtualMachineException e) {
            if (e.getJsError() != null) {
                evalError = e.getJsError();
            } else if (e.getJsValue() != null) {
                evalError = e.getJsValue();
            } else if (hasPendingException()) {
                evalError = getPendingException();
            } else {
                evalError = throwError("VM error: " + e.getMessage());
            }
            return null;
        } catch (JSErrorException e) {
            evalError = throwError(e);
            return null;
        } catch (Exception e) {
            evalError = throwError("Execution error: " + e.getMessage());
            return null;
        } finally {
            if (evalError != null && dynamicImportEvalModuleRecord != null
                    && dynamicImportEvalModuleRecord.status() == JSDynamicImportModule.Status.LOADING) {
                dynamicImportModuleCache.remove(dynamicImportEvalModuleRecord.resolvedSpecifier());
            }
            if (selfModuleRecord != null) {
                if (removeSelfModuleRecordAfterEval) {
                    if (dynamicImportModuleCache.get(selfModuleRecord.resolvedSpecifier()) == selfModuleRecord
                            && selfModuleRecord.status() == JSDynamicImportModule.Status.EVALUATING) {
                        dynamicImportModuleCache.remove(selfModuleRecord.resolvedSpecifier());
                    }
                } else if (selfModuleRecord.status() == JSDynamicImportModule.Status.EVALUATING
                        && selfModulePreviousStatus != null) {
                    selfModuleRecord.setStatus(selfModulePreviousStatus);
                }
            }
            restoreEvalOverlayFrame(moduleNamespaceImportOverlay);
            popStackFrame();
            // Clear ALL possible dirty state to ensure clean slate for next eval()
            stackDepth = callStack.size();
            inCatchHandler = false;
            currentThis = jsGlobalObject.getGlobalObject();
            evalOverlayLookupSuppressionDepth = 0;
            clearPendingException();
            clearErrorStackTrace();
            if (evalError != null) {
                setPendingException(evalError);
            }
        }
    }

    public JSValue evalDirect(String code, String filename, boolean inheritedStrictMode) {
        return evalOrThrow(eval(code, filename, false, true, false, false, inheritedStrictMode, true));
    }

    JSValue evalDirectInternal(String code, String filename, boolean inheritedStrictMode) {
        return eval(code, filename, false, true, false, false, inheritedStrictMode, true);
    }

    public JSValue evalIndirect(String code, String filename) {
        return evalOrThrow(eval(code, filename, false, true, false, false, false, false));
    }

    JSValue evalIndirectInternal(String code, String filename) {
        return eval(code, filename, false, true, false, false, false, false);
    }

    /**
     * Convert a null return from the private eval() into a JSException throw.
     * Used by all public eval methods to maintain the throwing API contract.
     */
    private JSValue evalOrThrow(JSValue result) {
        if (result == null) {
            JSValue error = getPendingException();
            clearPendingException();
            throw new JSException(error);
        }
        return result;
    }

    public JSValue evalWithProgramLexicalsAsLocals(String code, String filename, boolean isModule) {
        return evalOrThrow(eval(code, filename, isModule, false, true, true, false, false));
    }

    JSValue evaluateDynamicImportModule(JSDynamicImportModule moduleRecord) {
        if (!moduleRecord.hasExportSyntax()) {
            validateModuleScriptEarlyErrors(moduleRecord.rawSource());
            return eval(moduleRecord.transformedSource(), moduleRecord.resolvedSpecifier(), true);
        }
        String exportBindingName = moduleRecord.exportBindingName();
        JSObject globalObject = getGlobalObject();
        JSObject moduleNamespace = moduleRecord.namespace();
        globalObject.set(PropertyKey.fromString(exportBindingName), moduleNamespace);
        try {
            String transformedSource = moduleRecord.transformedSource();
            return eval(transformedSource, moduleRecord.resolvedSpecifier(), true);
        } finally {
            globalObject.delete(PropertyKey.fromString(exportBindingName));
        }
    }

    /**
     * Process all module imports in source order, handling side-effect, namespace, and binding
     * imports together. This ensures deferred modules' async dependencies are pre-evaluated
     * in the correct position relative to other imports.
     */
    private EvalOverlayFrame evaluateModuleImportsInOrder(String code, String filename) {
        String scanCode = maskModuleComments(code);
        JSObject globalObject = getGlobalObject();
        Map<String, JSValue> savedGlobals = new HashMap<>();
        Set<String> absentKeys = new HashSet<>();

        // Collect all import matches with their positions for ordered processing
        List<int[]> importPositions = new ArrayList<>();
        // type 0=side-effect, 1=namespace, 2=binding
        Matcher sideEffectMatcher = MODULE_SIDE_EFFECT_IMPORT_PATTERN.matcher(scanCode);
        while (sideEffectMatcher.find()) {
            importPositions.add(new int[]{sideEffectMatcher.start(), 0, importPositions.size()});
        }
        Matcher namespaceMatcher = MODULE_NAMESPACE_IMPORT_PATTERN.matcher(scanCode);
        while (namespaceMatcher.find()) {
            importPositions.add(new int[]{namespaceMatcher.start(), 1, importPositions.size()});
        }
        Matcher bindingMatcher = MODULE_BINDING_IMPORT_PATTERN.matcher(scanCode);
        while (bindingMatcher.find()) {
            importPositions.add(new int[]{bindingMatcher.start(), 2, importPositions.size()});
        }
        importPositions.sort((a, b) -> Integer.compare(a[0], b[0]));

        // Suppress microtask processing during import evaluation to prevent
        // nested eval() calls from prematurely draining the microtask queue.
        // This ensures async TLA module completions happen after ALL imports
        // are processed, producing correct evaluation order.
        boolean prevSuppress = suppressEvalMicrotaskProcessing;
        suppressEvalMicrotaskProcessing = true;
        try {
            // Re-match each import in source order
            for (int[] pos : importPositions) {
                int type = pos[1];
                int start = pos[0];

                if (type == 0) {
                    // Side-effect import
                    sideEffectMatcher.reset(scanCode);
                    if (sideEffectMatcher.find(start) && sideEffectMatcher.start() == start) {
                        String specifier = sideEffectMatcher.group(2);
                        Map<String, String> importAttributes = extractImportAttributes(sideEffectMatcher.group(0));
                        loadDynamicImportModule(specifier, filename, importAttributes);
                        // If this side-effect import was generated for an export-from,
                        // resolve the corresponding re-export binding immediately so
                        // self-imports see the re-exported names in the namespace.
                        resolveIncrementalReExport(specifier, filename);
                    }
                } else if (type == 1) {
                    // Namespace import
                    namespaceMatcher.reset(scanCode);
                    if (namespaceMatcher.find(start) && namespaceMatcher.start() == start) {
                        String deferKeyword = namespaceMatcher.group(1);
                        String localName = namespaceMatcher.group(2);
                        String specifier = namespaceMatcher.group(4);
                        Map<String, String> importAttributes = extractImportAttributes(namespaceMatcher.group(0));
                        JSObject namespaceObject;
                        if ("defer".equals(deferKeyword)) {
                            namespaceObject = loadDynamicImportModuleDeferred(specifier, filename, importAttributes);
                        } else {
                            namespaceObject = loadDynamicImportModule(specifier, filename, importAttributes);
                        }
                        bindImportOverlayValue(globalObject, savedGlobals, absentKeys, localName, namespaceObject);
                    }
                } else {
                    // Binding import
                    bindingMatcher.reset(scanCode);
                    if (bindingMatcher.find(start) && bindingMatcher.start() == start) {
                        String importClause = bindingMatcher.group(1).trim();
                        if (importClause.startsWith("*") || importClause.startsWith("defer *")) {
                            continue;
                        }
                        String specifier = bindingMatcher.group(3);
                        Map<String, String> importAttributes = extractImportAttributes(bindingMatcher.group(0));
                        if (specifier.endsWith(".json")
                                && importAttributes != null
                                && "json".equals(importAttributes.get("type"))) {
                            if (hasNonDefaultNamedBindings(importClause)) {
                                throw new JSSyntaxErrorException(
                                        "JSON modules do not support named exports");
                            }
                        }
                        if (importAttributes != null) {
                            String attrType = importAttributes.get("type");
                            if ("text".equals(attrType) || "bytes".equals(attrType)) {
                                if (hasNonDefaultNamedBindings(importClause)) {
                                    throw new JSSyntaxErrorException(
                                            (("text".equals(attrType)) ? "Text" : "Bytes")
                                                    + " modules do not support named exports");
                                }
                            }
                        }
                        JSObject namespaceObject = loadDynamicImportModule(specifier, filename, importAttributes);
                        applyImportClauseBindings(
                                globalObject,
                                savedGlobals,
                                absentKeys,
                                namespaceObject,
                                importClause);
                    }
                }
            }
        } finally {
            suppressEvalMicrotaskProcessing = prevSuppress;
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
                Map<String, String> importAttributes = extractImportAttributes(bindingMatcher.group(0));
                if (importAttributes != null) {
                    String importType = importAttributes.get("type");
                    if ("text".equals(importType) || "bytes".equals(importType)) {
                        continue;
                    }
                }
                String specifier = bindingMatcher.group(3);
                String resolvedSpec;
                try {
                    resolvedSpec = resolveDynamicImportSpecifier(specifier, filename, specifier);
                } catch (Exception e) {
                    continue;
                }
                JSDynamicImportModule moduleRecord = dynamicImportModuleCache.get(resolvedSpec);
                if (moduleRecord != null
                        && moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED
                        && moduleRecord.namespace().isFinalized()) {
                    validateNamedImportBindings(moduleRecord.namespace(), importClause);
                } else if (moduleRecord != null
                        && (moduleRecord.status() == JSDynamicImportModule.Status.LOADING
                        || moduleRecord.status() == JSDynamicImportModule.Status.EVALUATING)) {
                    // For self-referencing or circular imports, the namespace may not be finalized.
                    // Validate through recursive ResolveExport instead of namespace properties.
                    validateNamedImportBindingsAgainstExplicitExports(moduleRecord, importClause);
                }
            }
        }

        // Drain microtasks to settle any EVALUATING_ASYNC modules before
        // the module body runs. This ensures TLA deps complete in the right order.
        if (!suppressEvalMicrotaskProcessing) {
            processMicrotasks();
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
                    specifier = sideEffectMatcher.group(2);
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
                    specifier = namespaceMatcher.group(4);
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
                    specifier = bindingMatcher.group(3);
                }
            }
            if (specifier != null) {
                try {
                    String resolvedSpec = resolveDynamicImportSpecifier(specifier, filename, specifier);
                    JSDynamicImportModule moduleRecord = dynamicImportModuleCache.get(resolvedSpec);
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
        return new EvalOverlayFrame(savedGlobals, absentKeys);
    }

    /**
     * Exit strict mode.
     */
    public void exitStrictMode() {
        this.strictMode = false;
    }

    private void extractDestructuringNames(String innerText, List<String> names) {
        // Split on top-level commas and extract bound names
        String trimmed = innerText.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        int depth = 0;
        int start = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                extractSingleDestructuringName(trimmed.substring(start, i).trim(), names);
                start = i + 1;
            }
        }
        extractSingleDestructuringName(trimmed.substring(start).trim(), names);
    }

    private String extractExportedFunctionOrClassName(String exportClause) {
        Matcher functionMatcher = DYNAMIC_IMPORT_EXPORT_FUNCTION_NAME_PATTERN.matcher(exportClause);
        if (functionMatcher.find()) {
            return functionMatcher.group(1);
        }
        Matcher classMatcher = DYNAMIC_IMPORT_EXPORT_CLASS_NAME_PATTERN.matcher(exportClause);
        if (classMatcher.find()) {
            String candidateName = classMatcher.group(1);
            if (JSKeyword.EXTENDS.equals(candidateName)) {
                return null;
            }
            return candidateName;
        }
        return null;
    }

    private Map<String, String> extractImportAttributes(String importStatement) {
        Matcher withMatcher = MODULE_WITH_CLAUSE_PATTERN.matcher(importStatement);
        if (!withMatcher.find()) {
            return null;
        }
        String withBody = withMatcher.group(1).trim();
        if (withBody.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> attributes = new HashMap<>();
        String[] pairs = withBody.split(",");
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }
            String key = trimmed.substring(0, colonIndex).trim();
            String value = trimmed.substring(colonIndex + 1).trim();
            // Remove surrounding quotes from value
            if (value.length() >= 2
                    && ((value.startsWith("'") && value.endsWith("'"))
                    || (value.startsWith("\"") && value.endsWith("\"")))) {
                value = value.substring(1, value.length() - 1);
            }
            attributes.put(key, value);
        }
        return attributes;
    }

    private List<String> extractSimpleDeclarationNames(String declarationSource) {
        String declarationText = declarationSource.trim();
        int firstSpaceIndex = declarationText.indexOf(' ');
        if (firstSpaceIndex < 0 || firstSpaceIndex >= declarationText.length() - 1) {
            return List.of();
        }
        String declaratorsText = declarationText.substring(firstSpaceIndex + 1).trim();
        if (declaratorsText.endsWith(";")) {
            declaratorsText = declaratorsText.substring(0, declaratorsText.length() - 1).trim();
        }
        if (declaratorsText.isEmpty()) {
            return List.of();
        }
        // Split on commas at the top level only (not inside parens, brackets, braces, or strings)
        List<String> declarators = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < declaratorsText.length(); i++) {
            char ch = declaratorsText.charAt(i);
            if (inString) {
                if (ch == stringChar && (i == 0 || declaratorsText.charAt(i - 1) != '\\')) {
                    inString = false;
                }
            } else if (ch == '\'' || ch == '"' || ch == '`') {
                inString = true;
                stringChar = ch;
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                declarators.add(declaratorsText.substring(start, i));
                start = i + 1;
            }
        }
        declarators.add(declaratorsText.substring(start));
        List<String> declarationNames = new ArrayList<>(declarators.size());
        for (String declarator : declarators) {
            String declaratorText = declarator.trim();
            int assignmentIndex = findTopLevelAssignment(declaratorText);
            if (assignmentIndex >= 0) {
                declaratorText = declaratorText.substring(0, assignmentIndex).trim();
            }
            if (!declaratorText.isEmpty()) {
                if (declaratorText.startsWith("{") && declaratorText.endsWith("}")) {
                    // Object destructuring pattern: { a, b, c: d } → extract bound names
                    extractDestructuringNames(declaratorText.substring(1, declaratorText.length() - 1), declarationNames);
                } else if (declaratorText.startsWith("[") && declaratorText.endsWith("]")) {
                    // Array destructuring pattern: [a, b, c] → extract bound names
                    extractDestructuringNames(declaratorText.substring(1, declaratorText.length() - 1), declarationNames);
                } else {
                    declarationNames.add(declaratorText);
                }
            }
        }
        return declarationNames;
    }

    private void extractSingleDestructuringName(String element, List<String> names) {
        if (element.isEmpty() || element.equals("...")) {
            return;
        }
        // Handle rest element: ...name
        if (element.startsWith("...")) {
            element = element.substring(3).trim();
        }
        // Handle rename: key: value
        int colonIndex = element.indexOf(':');
        if (colonIndex >= 0) {
            element = element.substring(colonIndex + 1).trim();
        }
        // Handle default value: name = defaultVal
        int eqIndex = findTopLevelAssignment(element);
        if (eqIndex >= 0) {
            element = element.substring(0, eqIndex).trim();
        }
        // Check for nested destructuring
        if (element.startsWith("{") && element.endsWith("}")) {
            extractDestructuringNames(element.substring(1, element.length() - 1), names);
        } else if (element.startsWith("[") && element.endsWith("]")) {
            extractDestructuringNames(element.substring(1, element.length() - 1), names);
        } else if (!element.isEmpty()) {
            names.add(element);
        }
    }

    /**
     * Find the end of a function/class declaration body in the given text.
     * Scans for the first '{' and its matching '}', returning the index
     * just after the closing brace. Returns -1 if not found.
     */
    private int findEndOfDeclarationBody(String text) {
        String trimmedText = text.stripLeading();
        if (trimmedText.startsWith(JSKeyword.CLASS)
                && (trimmedText.length() == JSKeyword.CLASS.length()
                || !Character.isJavaIdentifierPart(trimmedText.charAt(JSKeyword.CLASS.length())))) {
            int classBodyOpenBraceIndex = findLikelyClassBodyOpenBrace(text);
            if (classBodyOpenBraceIndex < 0) {
                return -1;
            }
            int classBodyCloseBraceIndex = findMatchingClosingBrace(text, classBodyOpenBraceIndex);
            if (classBodyCloseBraceIndex < 0) {
                return -1;
            }
            return classBodyCloseBraceIndex + 1;
        }
        int braceDepth = 0;
        boolean foundOpen = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inTemplate = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (ch == '/' && i + 1 < text.length()) {
                if (text.charAt(i + 1) == '/') {
                    inLineComment = true;
                    i++;
                    continue;
                }
                if (text.charAt(i + 1) == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
            }
            if (inSingleQuote) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }
            if (inTemplate) {
                if (ch == '\\') {
                    i++;
                } else if (ch == '`') {
                    inTemplate = false;
                }
                continue;
            }
            if (ch == '\'') {
                inSingleQuote = true;
            } else if (ch == '"') {
                inDoubleQuote = true;
            } else if (ch == '`') {
                inTemplate = true;
            } else if (ch == '{') {
                foundOpen = true;
                braceDepth++;
            } else if (ch == '}') {
                braceDepth--;
                if (foundOpen && braceDepth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private int findLikelyClassBodyOpenBrace(String text) {
        int openBraceIndex = text.indexOf('{');
        while (openBraceIndex >= 0) {
            int closeBraceIndex = findMatchingClosingBrace(text, openBraceIndex);
            if (closeBraceIndex < 0) {
                return -1;
            }
            int nextTokenIndex = skipWhitespace(text, closeBraceIndex + 1);
            if (nextTokenIndex >= text.length()) {
                return openBraceIndex;
            }
            if (!isExpressionContinuationCharacter(text.charAt(nextTokenIndex))) {
                return openBraceIndex;
            }
            openBraceIndex = text.indexOf('{', openBraceIndex + 1);
        }
        return -1;
    }

    /**
     * Find the matching '}' for the '{' at the given position, skipping quoted strings.
     */
    private int findMatchingCloseBrace(String text, int openBraceIndex) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = openBraceIndex + 1; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == '}' && !inSingleQuote && !inDoubleQuote) {
                return i;
            }
        }
        return -1;
    }

    private int findMatchingClosingBrace(String text, int openBraceIndex) {
        if (openBraceIndex < 0 || openBraceIndex >= text.length() || text.charAt(openBraceIndex) != '{') {
            return -1;
        }
        int braceDepth = 1;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inTemplate = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int index = openBraceIndex + 1; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && index + 1 < text.length() && text.charAt(index + 1) == '/') {
                    inBlockComment = false;
                    index++;
                }
                continue;
            }
            if (ch == '/' && index + 1 < text.length()) {
                if (text.charAt(index + 1) == '/') {
                    inLineComment = true;
                    index++;
                    continue;
                }
                if (text.charAt(index + 1) == '*') {
                    inBlockComment = true;
                    index++;
                    continue;
                }
            }
            if (inSingleQuote) {
                if (ch == '\\') {
                    index++;
                } else if (ch == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (ch == '\\') {
                    index++;
                } else if (ch == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }
            if (inTemplate) {
                if (ch == '\\') {
                    index++;
                } else if (ch == '`') {
                    inTemplate = false;
                }
                continue;
            }
            if (ch == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (ch == '"') {
                inDoubleQuote = true;
                continue;
            }
            if (ch == '`') {
                inTemplate = true;
                continue;
            }
            if (ch == '{') {
                braceDepth++;
            } else if (ch == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    /**
     * Find the index of " as " keyword at the top level (not inside quotes).
     * Returns the index of 'a' in "as", or -1 if not found.
     */
    private int findTopLevelAs(String text) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < text.length() - 3; i++) {
            char ch = text.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (!inSingleQuote && !inDoubleQuote
                    && Character.isWhitespace(ch)
                    && text.charAt(i + 1) == 'a'
                    && text.charAt(i + 2) == 's'
                    && i + 3 < text.length()
                    && Character.isWhitespace(text.charAt(i + 3))) {
                return i + 1;
            }
        }
        return -1;
    }

    private int findTopLevelAssignment(String text) {
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (ch == stringChar && (i == 0 || text.charAt(i - 1) != '\\')) {
                    inString = false;
                }
            } else if (ch == '\'' || ch == '"' || ch == '`') {
                inString = true;
                stringChar = ch;
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
            } else if (ch == '=' && depth == 0 && i + 1 < text.length() && text.charAt(i + 1) != '=') {
                return i;
            }
        }
        return -1;
    }

    /**
     * ES2024 16.2.1.5.2.4 GatherAvailableAncestors.
     * Collects all ancestor modules whose pending async dependencies have all resolved.
     */
    private void gatherAvailableAncestors(JSDynamicImportModule module,
                                          List<JSDynamicImportModule> execList) {
        List<JSDynamicImportModule> dependents = new ArrayList<>(module.pendingDependents());
        module.pendingDependents().clear();
        for (JSDynamicImportModule dependent : dependents) {
            if (execList.contains(dependent)) {
                continue;
            }
            dependent.decrementPendingAsyncDependencyCount();
            if (dependent.pendingAsyncDependencyCount() <= 0) {
                execList.add(dependent);
                if (!dependent.hasTLA()) {
                    // Non-TLA modules will execute synchronously, so their ancestors
                    // might also become available immediately.
                    gatherAvailableAncestors(dependent, execList);
                }
            }
        }
    }

    private void gatherDeferredAsyncDependencySpecifiers(
            String resolvedSpecifier,
            String sourceCode,
            Set<String> visitedSpecifiers,
            Set<String> asyncDependencySpecifiers) {
        if (!visitedSpecifiers.add(resolvedSpecifier)) {
            return;
        }
        String scanSourceCode = maskModuleComments(sourceCode);
        if (MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(scanSourceCode).find()) {
            asyncDependencySpecifiers.add(resolvedSpecifier);
            return;
        }
        Matcher matcher = MODULE_STATIC_IMPORT_PATTERN.matcher(scanSourceCode);
        while (matcher.find()) {
            String childSpecifier = matcher.group(1);
            String resolvedChildSpecifier = resolveDynamicImportSpecifier(
                    childSpecifier,
                    resolvedSpecifier,
                    childSpecifier);
            String childSourceCode;
            try {
                JSDynamicImportModule childRecord = dynamicImportModuleCache.get(resolvedChildSpecifier);
                if (childRecord != null && childRecord.rawSource() != null && !childRecord.rawSource().isEmpty()) {
                    childSourceCode = childRecord.rawSource();
                } else {
                    childSourceCode = Files.readString(Path.of(resolvedChildSpecifier));
                }
            } catch (IOException ioException) {
                throw new JSException(throwTypeError("Cannot find module '" + childSpecifier + "'"));
            }
            gatherDeferredAsyncDependencySpecifiers(
                    resolvedChildSpecifier,
                    childSourceCode,
                    visitedSpecifiers,
                    asyncDependencySpecifiers);
        }
    }

    /**
     * Get the AsyncFunction constructor (internal use only).
     * Used for setting up prototype chains for async functions.
     */
    public JSObject getAsyncFunctionConstructor() {
        return asyncFunctionConstructor;
    }

    public JSObject getAsyncGeneratorFunctionPrototype() {
        return asyncGeneratorFunctionPrototype;
    }

    public JSObject getAsyncGeneratorPrototype() {
        return asyncGeneratorPrototype;
    }

    /**
     * Get the full call stack.
     */
    public List<JSStackFrame> getCallStack() {
        return new ArrayList<>(callStack);
    }

    /**
     * Get the pending exception.
     */
    public JSValue getConstructorNewTarget() {
        return constructorNewTarget;
    }

    /**
     * Get the current stack frame.
     */
    public JSStackFrame getCurrentStackFrame() {
        return callStack.peek();
    }

    /**
     * Get the current 'this' binding.
     */
    public JSValue getCurrentThis() {
        return currentThis;
    }

    private String getDynamicImportCacheKey(String resolvedSpecifier, Map<String, String> importAttributes) {
        if (importAttributes == null) {
            return resolvedSpecifier;
        }
        String importType = importAttributes.get("type");
        if ("text".equals(importType) || "bytes".equals(importType)) {
            return resolvedSpecifier + "\u0000type=" + importType;
        }
        return resolvedSpecifier;
    }

    private String getDynamicImportModuleExport(
            JSDynamicImportModule moduleRecord,
            String exportName,
            String targetSpecifier) {
        DynamicImportExportResolution resolution = resolveDynamicImportExport(
                moduleRecord,
                exportName,
                new HashSet<>(),
                new HashSet<>());
        if (resolution.ambiguous()) {
            throw new JSException(throwSyntaxError("ambiguous indirect export: " + exportName));
        }
        if (!resolution.found()) {
            throw new JSException(throwSyntaxError(
                    "module '" + targetSpecifier + "' does not provide export '" + exportName + "'"));
        }
        return resolution.bindingName();
    }

    /**
     * Get the error stack trace.
     */
    public List<StackTraceElement> getErrorStackTrace() {
        return new ArrayList<>(errorStackTrace);
    }

    private List<JSPromise> getEvaluatingAsyncDependencyPromises(JSDynamicImportModule moduleRecord) {
        String scanSource = maskModuleComments(moduleRecord.rawSource());
        Matcher matcher = MODULE_STATIC_IMPORT_PATTERN.matcher(scanSource);
        List<JSPromise> dependencyPromises = new ArrayList<>();
        Set<String> seenSpecifiers = new HashSet<>();
        JSDynamicImportModule moduleCycleRoot =
                moduleRecord.cycleRoot() != null ? moduleRecord.cycleRoot() : moduleRecord;
        while (matcher.find()) {
            String specifier = matcher.group(1);
            try {
                String resolved = resolveDynamicImportSpecifier(
                        specifier, moduleRecord.resolvedSpecifier(), specifier);
                JSDynamicImportModule depRecord = dynamicImportModuleCache.get(resolved);
                if (depRecord == null) {
                    continue;
                }
                JSDynamicImportModule effectiveDep = depRecord;
                if (depRecord.cycleRoot() != null) {
                    JSDynamicImportModule depCycleRoot = depRecord.cycleRoot();
                    if (depCycleRoot != moduleCycleRoot) {
                        effectiveDep = depCycleRoot;
                    }
                }
                if (effectiveDep.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC
                        && effectiveDep.asyncEvaluationPromise() != null
                        && seenSpecifiers.add(effectiveDep.resolvedSpecifier())) {
                    dependencyPromises.add(effectiveDep.asyncEvaluationPromise());
                }
            } catch (JSException ignored) {
                // Skip unresolvable specifiers.
            }
        }
        return dependencyPromises;
    }

    public JSContext getFunctionRealm(JSObject constructor) {
        return getFunctionRealmInternal(constructor, 0);
    }

    private JSContext getFunctionRealmInternal(JSValue value, int depth) {
        if (depth > 1000) {
            throwTypeError("too much recursion");
            return this;
        }
        if (value instanceof JSBoundFunction boundFunction) {
            return getFunctionRealmInternal(boundFunction.getTarget(), depth + 1);
        }
        if (value instanceof JSProxy proxy) {
            if (proxy.isRevoked()) {
                throwTypeError("Cannot perform 'get' on a proxy that has been revoked");
                return this;
            }
            return getFunctionRealmInternal(proxy.getTarget(), depth + 1);
        }
        if (value instanceof JSFunction function) {
            JSContext functionContext = function.getHomeContext();
            if (functionContext != null) {
                return functionContext;
            }
        }
        return this;
    }

    /**
     * Get the GeneratorFunction prototype (internal use only).
     * Used for setting up prototype chains for generator functions.
     */
    public JSObject getGeneratorFunctionPrototype() {
        return generatorFunctionPrototype;
    }

    public JSObject getGlobalObject() {
        return jsGlobalObject.getGlobalObject();
    }

    public String getIntrinsicDefaultPrototypeName(JSFunction function) {
        JSConstructorType constructorType = function.getConstructorType();
        if (constructorType != null) {
            return switch (constructorType) {
                case AGGREGATE_ERROR -> JSAggregateError.NAME;
                case ARRAY -> JSArray.NAME;
                case ARRAY_BUFFER -> JSArrayBuffer.NAME;
                case ASYNC_DISPOSABLE_STACK -> JSAsyncDisposableStack.NAME;
                case BIG_INT_OBJECT -> JSBigIntObject.NAME;
                case BOOLEAN_OBJECT -> JSBooleanObject.NAME;
                case DATA_VIEW -> JSDataView.NAME;
                case DATE -> JSDate.NAME;
                case DISPOSABLE_STACK -> JSDisposableStack.NAME;
                case ERROR -> JSError.NAME;
                case EVAL_ERROR -> JSEvalError.NAME;
                case FINALIZATION_REGISTRY -> JSFinalizationRegistry.NAME;
                case MAP -> JSMap.NAME;
                case NUMBER_OBJECT -> JSNumberObject.NAME;
                case PROMISE -> JSPromise.NAME;
                case PROXY -> JSObject.NAME;
                case RANGE_ERROR -> JSRangeError.NAME;
                case REFERENCE_ERROR -> JSReferenceError.NAME;
                case REGEXP -> JSRegExp.NAME;
                case SET -> JSSet.NAME;
                case SHARED_ARRAY_BUFFER -> JSSharedArrayBuffer.NAME;
                case STRING_OBJECT -> JSStringObject.NAME;
                case SUPPRESSED_ERROR -> JSSuppressedError.NAME;
                case SYMBOL_OBJECT -> JSSymbolObject.NAME;
                case SYNTAX_ERROR -> JSSyntaxError.NAME;
                case TYPED_ARRAY_BIGINT64 -> JSBigInt64Array.NAME;
                case TYPED_ARRAY_BIGUINT64 -> JSBigUint64Array.NAME;
                case TYPED_ARRAY_FLOAT16 -> JSFloat16Array.NAME;
                case TYPED_ARRAY_FLOAT32 -> JSFloat32Array.NAME;
                case TYPED_ARRAY_FLOAT64 -> JSFloat64Array.NAME;
                case TYPED_ARRAY_INT16 -> JSInt16Array.NAME;
                case TYPED_ARRAY_INT32 -> JSInt32Array.NAME;
                case TYPED_ARRAY_INT8 -> JSInt8Array.NAME;
                case TYPED_ARRAY_UINT16 -> JSUint16Array.NAME;
                case TYPED_ARRAY_UINT32 -> JSUint32Array.NAME;
                case TYPED_ARRAY_UINT8 -> JSUint8Array.NAME;
                case TYPED_ARRAY_UINT8_CLAMPED -> JSUint8ClampedArray.NAME;
                case TYPE_ERROR -> JSTypeError.NAME;
                case URI_ERROR -> JSURIError.NAME;
                case WEAK_MAP -> JSWeakMap.NAME;
                case WEAK_REF -> JSWeakRef.NAME;
                case WEAK_SET -> JSWeakSet.NAME;
            };
        }
        if (function instanceof JSClass) {
            return JSObject.NAME;
        }
        String functionName = function.getName();
        if (JSFunction.NAME.equals(functionName)) {
            return JSFunction.NAME;
        }
        if ("GeneratorFunction".equals(functionName)) {
            return "GeneratorFunction";
        }
        if ("AsyncFunction".equals(functionName)) {
            return "AsyncFunction";
        }
        if ("AsyncGeneratorFunction".equals(functionName)) {
            return "AsyncGeneratorFunction";
        }
        if (JSIterator.NAME.equals(functionName)) {
            return JSIterator.NAME;
        }
        return JSObject.NAME;
    }

    private JSObject getIntrinsicPrototype(JSContext realmContext, String intrinsicDefaultPrototypeName) {
        if (JSObject.NAME.equals(intrinsicDefaultPrototypeName)) {
            return realmContext.getObjectPrototype();
        }
        if ("GeneratorFunction".equals(intrinsicDefaultPrototypeName)) {
            JSObject generatorFunctionPrototype = realmContext.getGeneratorFunctionPrototype();
            if (generatorFunctionPrototype != null) {
                return generatorFunctionPrototype;
            }
            return realmContext.getObjectPrototype();
        }
        if ("AsyncGeneratorFunction".equals(intrinsicDefaultPrototypeName)) {
            JSObject asyncGeneratorFunctionPrototype = realmContext.getAsyncGeneratorFunctionPrototype();
            if (asyncGeneratorFunctionPrototype != null) {
                return asyncGeneratorFunctionPrototype;
            }
            return realmContext.getObjectPrototype();
        }
        if ("AsyncFunction".equals(intrinsicDefaultPrototypeName)) {
            JSObject asyncFunctionConstructor = realmContext.getAsyncFunctionConstructor();
            if (asyncFunctionConstructor != null) {
                JSValue asyncFunctionPrototype = asyncFunctionConstructor.get(PropertyKey.PROTOTYPE);
                if (asyncFunctionPrototype instanceof JSObject asyncFunctionPrototypeObject) {
                    return asyncFunctionPrototypeObject;
                }
            }
            JSValue fallbackFunctionConstructor = realmContext.getGlobalObject().get(JSFunction.NAME);
            if (fallbackFunctionConstructor instanceof JSObject fallbackFunctionObject) {
                JSValue fallbackFunctionPrototype = fallbackFunctionObject.get(PropertyKey.PROTOTYPE);
                if (fallbackFunctionPrototype instanceof JSObject fallbackFunctionPrototypeObject) {
                    return fallbackFunctionPrototypeObject;
                }
            }
            return realmContext.getObjectPrototype();
        }

        JSValue intrinsicConstructor = realmContext.getGlobalObject().get(intrinsicDefaultPrototypeName);
        if (intrinsicConstructor instanceof JSObject intrinsicObject) {
            JSValue intrinsicPrototype = intrinsicObject.get(PropertyKey.PROTOTYPE);
            if (intrinsicPrototype instanceof JSObject intrinsicPrototypeObject) {
                return intrinsicPrototypeObject;
            }
        }
        return realmContext.getObjectPrototype();
    }

    public JSObject getIteratorPrototype(String tag) {
        return iteratorPrototypes.get(tag);
    }

    public java.util.Collection<JSObject> getIteratorPrototypes() {
        return iteratorPrototypes.values();
    }

    public JSGlobalObject getJSGlobalObject() {
        return jsGlobalObject;
    }

    public int getMaxStackDepth() {
        return maxStackDepth;
    }

    /**
     * Get the microtask queue for this context.
     */
    public JSMicrotaskQueue getMicrotaskQueue() {
        return microtaskQueue;
    }

    /**
     * Get a cached module.
     */
    public JSModule getModule(String specifier) {
        return moduleCache.get(specifier);
    }

    public JSValue getNativeConstructorNewTarget() {
        return nativeConstructorNewTarget;
    }

    public JSObject getObjectPrototype() {
        return cachedObjectPrototype;
    }

    public JSValue getPendingException() {
        return pendingException;
    }

    public IJSPromiseRejectCallback getPromiseRejectCallback() {
        return promiseRejectCallback;
    }

    public JSObject getPrototypeFromConstructor(JSObject constructor, String intrinsicDefaultPrototypeName) {
        JSValue prototype = constructor.get(PropertyKey.PROTOTYPE);
        if (hasPendingException()) {
            return null;
        }
        if (prototype instanceof JSObject prototypeObject) {
            return prototypeObject;
        }

        JSContext functionRealm = getFunctionRealm(constructor);
        if (hasPendingException()) {
            return null;
        }
        return getIntrinsicPrototype(functionRealm, intrinsicDefaultPrototypeName);
    }

    public String getRegExpLegacyCapture(int captureIndex) {
        if (captureIndex < 1 || captureIndex > regExpLegacyCaptures.length) {
            return "";
        }
        String captureValue = regExpLegacyCaptures[captureIndex - 1];
        if (captureValue == null) {
            return "";
        } else {
            return captureValue;
        }
    }

    public String getRegExpLegacyInput() {
        return regExpLegacyInput;
    }

    public String getRegExpLegacyLastMatch() {
        return regExpLegacyLastMatch;
    }

    public String getRegExpLegacyLastParen() {
        return regExpLegacyLastParen;
    }

    public String getRegExpLegacyLeftContext() {
        return regExpLegacyLeftContext;
    }

    public String getRegExpLegacyRightContext() {
        return regExpLegacyRightContext;
    }

    public JSRuntime getRuntime() {
        return runtime;
    }

    /**
     * Get the current call stack depth.
     */
    public int getStackDepth() {
        return stackDepth;
    }

    /**
     * Get the %ThrowTypeError% intrinsic function.
     * This is the single shared function used for Function.prototype caller/arguments
     * and strict mode arguments.callee per ES spec.
     */
    public JSNativeFunction getThrowTypeErrorIntrinsic() {
        return throwTypeErrorIntrinsic;
    }

    public UnicodePropertyResolver getUnicodePropertyResolver() {
        return unicodePropertyResolver;
    }

    /**
     * Get the virtual machine for this context.
     */
    public VirtualMachine getVirtualMachine() {
        return virtualMachine;
    }

    public boolean hasEvalOverlayBinding(String name) {
        if (evalOverlayLookupSuppressionDepth > 0) {
            return false;
        }
        for (EvalOverlayFrame evalOverlayFrame : evalOverlayFrames) {
            if (evalOverlayFrame.savedGlobals().containsKey(name)
                    || evalOverlayFrame.absentKeys().contains(name)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasEvalOverlayFrames() {
        if (evalOverlayLookupSuppressionDepth > 0) {
            return false;
        }
        return !evalOverlayFrames.isEmpty();
    }

    private boolean hasEvaluatingAsyncDependency(JSDynamicImportModule moduleRecord) {
        String scanSource = maskModuleComments(moduleRecord.rawSource());
        Matcher matcher = MODULE_STATIC_IMPORT_PATTERN.matcher(scanSource);
        while (matcher.find()) {
            String specifier = matcher.group(1);
            try {
                String resolved = resolveDynamicImportSpecifier(
                        specifier, moduleRecord.resolvedSpecifier(), specifier);
                JSDynamicImportModule depRecord = dynamicImportModuleCache.get(resolved);
                if (depRecord != null
                        && depRecord.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC) {
                    return true;
                }
            } catch (JSException ignored) {
                // Skip unresolvable specifiers
            }
        }
        return false;
    }

    public boolean hasGlobalConstDeclaration(String name) {
        return globalConstDeclarations.contains(name);
    }

    public boolean hasGlobalLexDeclaration(String name) {
        return globalLexDeclarations.contains(name);
    }

    public boolean hasGlobalLexicalBinding(String name) {
        return globalLexicalBindings.containsKey(name);
    }

    private boolean hasModuleExportSyntax(String code) {
        return MODULE_EXPORT_SYNTAX_PATTERN.matcher(maskModuleComments(code)).find();
    }

    private boolean hasModuleStaticImportSyntax(String code) {
        return MODULE_STATIC_IMPORT_SYNTAX_PATTERN.matcher(maskModuleComments(code)).find();
    }

    private boolean hasModuleTopLevelAwaitSyntax(String code) {
        return MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(maskModuleComments(code)).find();
    }

    /**
     * Check if an import clause contains named bindings other than 'default'.
     * E.g., {@code {name}} returns true, {@code {default as x}} returns false.
     */
    private boolean hasNonDefaultNamedBindings(String importClause) {
        int braceStart = importClause.indexOf('{');
        if (braceStart < 0) {
            return false;
        }
        int braceEnd = importClause.indexOf('}', braceStart);
        if (braceEnd < 0) {
            return false;
        }
        String body = importClause.substring(braceStart + 1, braceEnd);
        for (String entry : body.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Get the imported name (before 'as')
            String[] parts = trimmed.split("\\s+as\\s+");
            String importedName = parts[0].trim();
            if (!"default".equals(importedName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if there's a pending exception.
     */
    public boolean hasPendingException() {
        return pendingException != null;
    }

    /**
     * Initialize the global object with built-in properties.
     * Delegates to JSGlobalObject to set up all global functions and properties.
     */
    private void initializeGlobalObject() {
        jsGlobalObject.initialize();
        // Cache Object.prototype for fast access in hot paths (e.g., iteratorResult, createJSObject)
        JSValue objectCtor = jsGlobalObject.getGlobalObject().get(JSObject.NAME);
        if (objectCtor instanceof JSObject objCtorObj) {
            JSValue proto = objCtorObj.get(PropertyKey.PROTOTYPE);
            if (proto instanceof JSObject protoObj) {
                this.cachedObjectPrototype = protoObj;
            }
        }
        JSValue dateCtor = jsGlobalObject.getGlobalObject().get(JSDate.NAME);
        if (dateCtor instanceof JSObject dateCtorObj) {
            JSValue proto = dateCtorObj.get(PropertyKey.PROTOTYPE);
            if (proto instanceof JSObject protoObj) {
                this.cachedDatePrototype = protoObj;
            }
        }
        JSValue promiseCtor = jsGlobalObject.getGlobalObject().get(JSPromise.NAME);
        if (promiseCtor instanceof JSObject promiseCtorObject) {
            JSValue proto = promiseCtorObject.get(PropertyKey.PROTOTYPE);
            if (proto instanceof JSObject protoObj) {
                this.cachedPromisePrototype = protoObj;
            }
        }
    }

    private void initializeHoistedFunctionExportBindings(JSDynamicImportModule moduleRecord) {
        if (moduleRecord == null
                || moduleRecord.hoistedFunctionExportBindingsInitialized()) {
            return;
        }
        List<JSDynamicImportModule.HoistedFunctionExportBinding> hoistedBindings =
                moduleRecord.hoistedFunctionExportBindings();
        if (hoistedBindings.isEmpty()) {
            moduleRecord.setHoistedFunctionExportBindingsInitialized(true);
            return;
        }

        StringBuilder sourceBuilder = new StringBuilder();
        sourceBuilder.append("(function () {\n");
        for (JSDynamicImportModule.HoistedFunctionExportBinding hoistedBinding : hoistedBindings) {
            sourceBuilder.append(hoistedBinding.functionDeclarationSource()).append('\n');
        }
        sourceBuilder.append("return {");
        for (int bindingIndex = 0; bindingIndex < hoistedBindings.size(); bindingIndex++) {
            JSDynamicImportModule.HoistedFunctionExportBinding hoistedBinding = hoistedBindings.get(bindingIndex);
            if (bindingIndex > 0) {
                sourceBuilder.append(", ");
            }
            sourceBuilder.append("\"")
                    .append(escapeJavaScriptString(hoistedBinding.localName()))
                    .append("\": ")
                    .append(hoistedBinding.localName());
        }
        sourceBuilder.append("};\n})();");

        JSValue bindingsValue = eval(
                sourceBuilder.toString(),
                "<hoisted-export-init>",
                true,
                false);
        if (!(bindingsValue instanceof JSObject functionBindingsObject)) {
            moduleRecord.setHoistedFunctionExportBindingsInitialized(true);
            return;
        }

        JSImportNamespaceObject namespaceObject = moduleRecord.namespace();
        for (JSDynamicImportModule.HoistedFunctionExportBinding hoistedBinding : hoistedBindings) {
            PropertyKey localKey = PropertyKey.fromString(hoistedBinding.localName());
            JSValue functionValue = functionBindingsObject.get(localKey);
            if (hasPendingException()) {
                JSValue pendingError = getPendingException();
                clearPendingException();
                throw new JSException(pendingError);
            }
            if (!(functionValue instanceof JSFunction)) {
                continue;
            }
            namespaceObject.setEarlyExportBinding(hoistedBinding.exportedName(), functionValue);
        }
        moduleRecord.setHoistedFunctionExportBindingsInitialized(true);
    }

    private <T extends JSTypedArray> T initializeTypedArray(T typedArray, String constructorName) {
        transferPrototype(typedArray, constructorName);
        var buffer = typedArray.getBuffer();
        if (buffer instanceof JSObject jsObject && jsObject.getPrototype() == null) {
            transferPrototype(jsObject, buffer.isShared() ? JSSharedArrayBuffer.NAME : JSArrayBuffer.NAME);
        }
        return typedArray;
    }

    public boolean isActiveGlobalFunctionBindingConfigurable() {
        return activeGlobalFunctionBindingConfigurable;
    }

    private boolean isCompleteStaticImportStatement(String importStatement) {
        if (importStatement == null || importStatement.isBlank()) {
            return false;
        }
        String normalizedImportStatement = importStatement.strip();
        if (MODULE_NAMESPACE_IMPORT_PATTERN.matcher(normalizedImportStatement).matches()) {
            return true;
        }
        if (MODULE_BINDING_IMPORT_PATTERN.matcher(normalizedImportStatement).matches()) {
            return true;
        }
        return MODULE_SIDE_EFFECT_IMPORT_PATTERN.matcher(normalizedImportStatement).matches();
    }

    private boolean isDynamicImportDefaultDeclarationClause(String defaultClause) {
        return defaultClause.startsWith("function")
                || defaultClause.startsWith("async function")
                || defaultClause.startsWith("class");
    }

    private boolean isExpressionContinuationCharacter(char ch) {
        return ch == ')' || ch == ']' || ch == '}'
                || ch == ',' || ch == '.' || ch == ':'
                || ch == '?' || ch == '+'
                || ch == '-' || ch == '*'
                || ch == '/' || ch == '%'
                || ch == '<' || ch == '>'
                || ch == '=' || ch == '&'
                || ch == '|' || ch == '^';
    }

    public boolean isGlobalLexicalBindingInitialized(String name) {
        JSValue value = globalLexicalBindings.get(name);
        return value != null && value != GLOBAL_LEXICAL_UNINITIALIZED;
    }

    public boolean isInBareVariableAssignment() {
        return inBareVariableAssignment;
    }

    private boolean isSelfImportBinding(ImportBinding importBinding, String moduleSpecifier) {
        if (importBinding == null
                || importBinding.sourceSpecifier() == null
                || importBinding.sourceSpecifier().isEmpty()) {
            return false;
        }
        try {
            String resolvedImportSpecifier = resolveDynamicImportSpecifier(
                    importBinding.sourceSpecifier(),
                    moduleSpecifier,
                    importBinding.sourceSpecifier());
            Path resolvedImportPath = Path.of(resolvedImportSpecifier).normalize().toAbsolutePath();
            Path modulePath = Path.of(moduleSpecifier).normalize().toAbsolutePath();
            String resolvedImportPathString = resolvedImportPath.toString();
            String modulePathString = modulePath.toString();
            if (resolvedImportPathString.equals(modulePathString)) {
                return true;
            }
            return resolvedImportPathString.equalsIgnoreCase(modulePathString);
        } catch (JSException ignored) {
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isStaticImportLine(String trimmedLine) {
        if (trimmedLine == null || !trimmedLine.startsWith("import")) {
            return false;
        }
        if (trimmedLine.startsWith("import(") || trimmedLine.startsWith("import.")) {
            return false;
        }
        if (trimmedLine.length() == "import".length()) {
            return false;
        }
        char nextChar = trimmedLine.charAt("import".length());
        return !Character.isLetterOrDigit(nextChar) && nextChar != '_' && nextChar != '$';
    }

    /**
     * Check if in strict mode.
     */
    public boolean isStrictMode() {
        return strictMode;
    }

    public boolean isWaitable() {
        return waitable;
    }

    public JSObject loadDynamicImportModule(String specifier, String referrerFilename) {
        return loadDynamicImportModule(specifier, referrerFilename, null);
    }

    public JSObject loadDynamicImportModule(
            String specifier,
            String referrerFilename,
            Map<String, String> importAttributes) {
        return loadDynamicImportModule(specifier, referrerFilename, importAttributes, null, null);
    }

    /**
     * Load a dynamic import module. When importPromise and resolveState are provided
     * (from a dynamic import() expression), the method chains the import promise onto
     * the module's async evaluation promise if the module has TLA.
     * Returns null when the import promise is handled internally.
     */
    public JSObject loadDynamicImportModule(
            String specifier,
            String referrerFilename,
            Map<String, String> importAttributes,
            JSPromise importPromise,
            JSPromise.ResolveState resolveState) {
        String resolvedSpecifier = resolveDynamicImportSpecifier(specifier, referrerFilename, specifier);
        String moduleCacheKey = getDynamicImportCacheKey(resolvedSpecifier, importAttributes);
        // Check if the module was pre-loaded (deferred) but not yet evaluated.
        JSDynamicImportModule preloaded = dynamicImportModuleCache.get(moduleCacheKey);
        if (preloaded != null && preloaded.status() == JSDynamicImportModule.Status.LOADING
                && preloaded.deferredPreload()) {
            try {
                evaluateDynamicImportModule(preloaded);
                resolveDynamicImportReExports(preloaded, new HashSet<>());
                preloaded.namespace().finalizeNamespace();
                preloaded.setStatus(JSDynamicImportModule.Status.EVALUATED);
            } catch (JSException jsException) {
                preloaded.setEvaluationError(jsException.getErrorValue());
                preloaded.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
                throw jsException;
            }
            return preloaded.namespace();
        }
        JSDynamicImportModule moduleRecord =
                loadJSDynamicImportModule(resolvedSpecifier, new HashSet<>(), importAttributes);
        // If the module is still completing async evaluation, chain the import promise
        // onto the module's async evaluation promise instead of resolving immediately.
        if (importPromise != null && resolveState != null
                && moduleRecord.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC
                && moduleRecord.asyncEvaluationPromise() != null) {
            chainImportPromiseOntoAsyncModule(moduleRecord, importPromise, resolveState);
            return null;
        }
        if (importPromise != null && resolveState != null
                && moduleRecord.status() != JSDynamicImportModule.Status.EVALUATED_ERROR) {
            List<JSPromise> asyncDependencyPromises = getEvaluatingAsyncDependencyPromises(moduleRecord);
            if (!asyncDependencyPromises.isEmpty()) {
                chainImportPromiseOntoAsyncDependencies(
                        asyncDependencyPromises,
                        moduleRecord.namespace(),
                        importPromise,
                        resolveState);
                return null;
            }
        }
        // If the module evaluation failed, throw so the import() promise gets rejected
        if (moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
            throw new JSException(moduleRecord.evaluationError());
        }
        return moduleRecord.namespace();
    }

    public JSObject loadDynamicImportModuleDeferred(
            String specifier,
            String referrerFilename,
            Map<String, String> importAttributes) {
        return loadDynamicImportModuleDeferred(specifier, referrerFilename, importAttributes, null, null);
    }

    /**
     * Load a module in deferred mode. When importPromise and resolveState are provided
     * (dynamic import.defer() case), the method handles resolving the import promise
     * internally — chaining it onto TLA evaluation promises if needed.
     * Returns null when the import promise is handled internally.
     */
    public JSObject loadDynamicImportModuleDeferred(
            String specifier,
            String referrerFilename,
            Map<String, String> importAttributes,
            JSPromise importPromise,
            JSPromise.ResolveState resolveState) {
        String resolvedSpecifier = resolveDynamicImportSpecifier(specifier, referrerFilename, specifier);
        String moduleCacheKey = getDynamicImportCacheKey(resolvedSpecifier, importAttributes);
        JSDynamicImportModule moduleRecord = dynamicImportModuleCache.get(moduleCacheKey);
        if (moduleRecord == null) {
            moduleRecord = new JSDynamicImportModule(resolvedSpecifier, createModuleNamespaceObject());
            moduleRecord.setStatus(JSDynamicImportModule.Status.LOADING);
            moduleRecord.setDeferredPreload(true);
            dynamicImportModuleCache.put(moduleCacheKey, moduleRecord);
            try {
                String importType = importAttributes != null ? importAttributes.get("type") : null;
                // Handle type: 'text' import attribute
                if ("text".equals(importType)) {
                    String sourceCode = Files.readString(Path.of(resolvedSpecifier));
                    moduleRecord.setRawSource(sourceCode);
                    defineDynamicImportNamespaceValue(moduleRecord, "default", new JSString(sourceCode));
                    moduleRecord.explicitExportNames().add("default");
                    moduleRecord.exportOrigins().put("default", resolvedSpecifier);
                    moduleRecord.namespace().finalizeNamespace();
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    return moduleRecord.namespace();
                }
                // Handle type: 'bytes' import attribute
                if ("bytes".equals(importType)) {
                    byte[] fileBytes = Files.readAllBytes(Path.of(resolvedSpecifier));
                    moduleRecord.setRawSource("");
                    JSArrayBuffer arrayBuffer = new JSArrayBuffer(this, fileBytes);
                    transferPrototype(arrayBuffer, JSArrayBuffer.NAME);
                    arrayBuffer.setImmutable(true);
                    JSUint8Array uint8Array = createJSUint8Array(arrayBuffer, 0, fileBytes.length);
                    defineDynamicImportNamespaceValue(moduleRecord, "default", uint8Array);
                    moduleRecord.explicitExportNames().add("default");
                    moduleRecord.exportOrigins().put("default", resolvedSpecifier);
                    moduleRecord.namespace().finalizeNamespace();
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    return moduleRecord.namespace();
                }
                String sourceCode = Files.readString(Path.of(resolvedSpecifier));
                moduleRecord.setRawSource(sourceCode);
                if (resolvedSpecifier.endsWith(".json")) {
                    if (!"json".equals(importType)) {
                        throw new JSException(throwTypeError("Import attribute type must be 'json'"));
                    }
                    JSValue jsonDefaultValue = parseJsonModuleSource(sourceCode);
                    defineDynamicImportNamespaceValue(moduleRecord, "default", jsonDefaultValue);
                    moduleRecord.explicitExportNames().add("default");
                    moduleRecord.exportOrigins().put("default", resolvedSpecifier);
                    moduleRecord.namespace().finalizeNamespace();
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    return moduleRecord.namespace();
                }
                parseDynamicImportModuleSource(moduleRecord);
                // Eagerly validate syntax of deferred modules per spec.
                // SyntaxErrors are not deferred — they must be detected at linking time.
                try {
                    new Compiler(sourceCode, resolvedSpecifier).setContext(this).parse(true);
                } catch (JSSyntaxErrorException syntaxError) {
                    dynamicImportModuleCache.remove(moduleCacheKey);
                    throw new JSException(throwSyntaxError(syntaxError.getMessage()));
                } catch (JSCompilerException compilerError) {
                    dynamicImportModuleCache.remove(moduleCacheKey);
                    throw new JSException(throwSyntaxError(compilerError.getMessage()));
                }
            } catch (IOException ioException) {
                dynamicImportModuleCache.remove(moduleCacheKey);
                throw new JSException(throwTypeError("Cannot find module '" + resolvedSpecifier + "'"));
            } catch (JSException jsException) {
                dynamicImportModuleCache.remove(moduleCacheKey);
                throw jsException;
            }
        }

        if (moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED
                || moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
            // Even for already-evaluated (or error) modules, return the deferred namespace wrapper.
            // Deferred namespaces are distinct objects from eager namespaces per spec.
            // For EVALUATED_ERROR, ensureEvaluated() will rethrow the cached error.
            if (moduleRecord.deferredNamespace() == null) {
                moduleRecord.setDeferredNamespace(new JSDeferredModuleNamespace(this, moduleRecord));
            }
            return moduleRecord.deferredNamespace();
        }

        LinkedHashSet<String> asyncDependencySpecifiers = new LinkedHashSet<>();
        gatherDeferredAsyncDependencySpecifiers(
                resolvedSpecifier,
                moduleRecord.rawSource(),
                new HashSet<>(),
                asyncDependencySpecifiers);
        List<JSPromise> tlaEvaluationPromises = new ArrayList<>();
        boolean prevSuppress = suppressEvalMicrotaskProcessing;
        suppressEvalMicrotaskProcessing = true;
        try {
            for (String asyncDependencySpecifier : asyncDependencySpecifiers) {
                if (asyncDependencySpecifier.equals(resolvedSpecifier)
                        && moduleRecord.status() == JSDynamicImportModule.Status.LOADING) {
                    // Self-module with TLA: set EVALUATING before eval so nested
                    // deferred imports of this module see the correct state.
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING);
                    JSValue evalResult = evaluateDynamicImportModule(moduleRecord);
                    if (moduleRecord.hasTLA() && evalResult instanceof JSPromise asyncPromise) {
                        moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING_ASYNC);
                        moduleRecord.setAsyncEvaluationPromise(asyncPromise);
                        registerAsyncModuleCompletion(moduleRecord, asyncPromise, new HashSet<>());
                        tlaEvaluationPromises.add(asyncPromise);
                    } else {
                        resolveDynamicImportReExports(moduleRecord, new HashSet<>());
                        moduleRecord.namespace().finalizeNamespace();
                        moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    }
                } else {
                    JSDynamicImportModule depRecord =
                            loadJSDynamicImportModule(asyncDependencySpecifier, new HashSet<>(), importAttributes);
                    if (depRecord.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC
                            && depRecord.asyncEvaluationPromise() != null) {
                        tlaEvaluationPromises.add(depRecord.asyncEvaluationPromise());
                    }
                }
            }
        } finally {
            suppressEvalMicrotaskProcessing = prevSuppress;
        }

        if (moduleRecord.deferredNamespace() == null) {
            moduleRecord.setDeferredNamespace(new JSDeferredModuleNamespace(this, moduleRecord));
        }

        // When called from dynamic import.defer() (importPromise != null) and there are
        // pending TLA evaluation promises, chain the import promise resolution onto them
        // using a Promise.all-like counter. This avoids relying on processMicrotasks()
        // which is a no-op when called re-entrantly from within a microtask.
        if (importPromise != null && !tlaEvaluationPromises.isEmpty()) {
            JSObject deferredNs = moduleRecord.deferredNamespace();
            int[] remaining = {tlaEvaluationPromises.size()};
            for (JSPromise tlaPromise : tlaEvaluationPromises) {
                JSNativeFunction onFulfill = new JSNativeFunction(this, "", 0,
                        (ctx, thisArg, args) -> {
                            remaining[0]--;
                            if (remaining[0] == 0 && !resolveState.alreadyResolved) {
                                resolveState.alreadyResolved = true;
                                importPromise.resolve(ctx, deferredNs);
                            }
                            return JSUndefined.INSTANCE;
                        });
                onFulfill.initializePrototypeChain(this);
                JSNativeFunction onReject = new JSNativeFunction(this, "", 1,
                        (ctx, thisArg, args) -> {
                            if (!resolveState.alreadyResolved) {
                                resolveState.alreadyResolved = true;
                                JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                importPromise.reject(reason);
                            }
                            return JSUndefined.INSTANCE;
                        });
                onReject.initializePrototypeChain(this);
                tlaPromise.addReactions(
                        new JSPromise.ReactionRecord(onFulfill, this, null, null),
                        new JSPromise.ReactionRecord(onReject, this, null, null));
            }
            return null; // Import promise will be resolved via TLA promise chain
        }

        // Static import defer case: drain microtasks to complete EVALUATING_ASYNC modules.
        // Only drain when not called from evaluateModuleImportsInOrder
        // (which has its own drain after all imports are processed).
        if (!suppressEvalMicrotaskProcessing && !asyncDependencySpecifiers.isEmpty()) {
            processMicrotasks();
        }

        return moduleRecord.deferredNamespace();
    }

    private JSDynamicImportModule loadJSDynamicImportModule(
            String resolvedSpecifier,
            Set<String> importResolutionStack,
            Map<String, String> importAttributes) {
        String moduleCacheKey = getDynamicImportCacheKey(resolvedSpecifier, importAttributes);
        JSDynamicImportModule cachedRecord = dynamicImportModuleCache.get(moduleCacheKey);
        if (cachedRecord != null) {
            if (cachedRecord.status() == JSDynamicImportModule.Status.EVALUATED) {
                return cachedRecord;
            }
            if (cachedRecord.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
                throw new JSException(cachedRecord.evaluationError());
            }
            if (cachedRecord.status() == JSDynamicImportModule.Status.LOADING
                    || cachedRecord.status() == JSDynamicImportModule.Status.EVALUATING
                    || cachedRecord.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC) {
                return cachedRecord;
            }
        }

        JSDynamicImportModule moduleRecord =
                new JSDynamicImportModule(resolvedSpecifier, createModuleNamespaceObject());
        moduleRecord.setStatus(JSDynamicImportModule.Status.LOADING);
        dynamicImportModuleCache.put(moduleCacheKey, moduleRecord);

        try {
            String importType = importAttributes != null ? importAttributes.get("type") : null;
            // Handle type: 'text' import attribute
            if ("text".equals(importType)) {
                String sourceCode = Files.readString(Path.of(resolvedSpecifier));
                moduleRecord.setRawSource(sourceCode);
                defineDynamicImportNamespaceValue(moduleRecord, "default", new JSString(sourceCode));
                moduleRecord.explicitExportNames().add("default");
                moduleRecord.exportOrigins().put("default", resolvedSpecifier);
                moduleRecord.namespace().finalizeNamespace();
                moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                return moduleRecord;
            }
            // Handle type: 'bytes' import attribute
            if ("bytes".equals(importType)) {
                byte[] fileBytes = Files.readAllBytes(Path.of(resolvedSpecifier));
                moduleRecord.setRawSource("");
                JSArrayBuffer arrayBuffer = new JSArrayBuffer(this, fileBytes);
                transferPrototype(arrayBuffer, JSArrayBuffer.NAME);
                arrayBuffer.setImmutable(true);
                JSUint8Array uint8Array = createJSUint8Array(arrayBuffer, 0, fileBytes.length);
                defineDynamicImportNamespaceValue(moduleRecord, "default", uint8Array);
                moduleRecord.explicitExportNames().add("default");
                moduleRecord.exportOrigins().put("default", resolvedSpecifier);
                moduleRecord.namespace().finalizeNamespace();
                moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                return moduleRecord;
            }
            String sourceCode = Files.readString(Path.of(resolvedSpecifier));
            moduleRecord.setRawSource(sourceCode);
            if (resolvedSpecifier.endsWith(".json")) {
                if (!"json".equals(importType)) {
                    throw new JSException(throwTypeError("Import attribute type must be 'json'"));
                }
                JSValue jsonDefaultValue = parseJsonModuleSource(sourceCode);
                defineDynamicImportNamespaceValue(moduleRecord, "default", jsonDefaultValue);
                moduleRecord.explicitExportNames().add("default");
                moduleRecord.exportOrigins().put("default", resolvedSpecifier);
                moduleRecord.namespace().finalizeNamespace();
                moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                return moduleRecord;
            }
            parseDynamicImportModuleSource(moduleRecord);
            resolveDynamicImportReExports(moduleRecord, importResolutionStack);
            // Pre-load all static imports so we can detect EVALUATING_ASYNC dependencies.
            // Without this, a module's deps aren't loaded until eval() → evaluateModuleImportsInOrder,
            // which is too late for the hasEvaluatingAsyncDependency check.
            if (suppressEvalMicrotaskProcessing) {
                preloadStaticImports(moduleRecord, importResolutionStack, importAttributes);
            }
            if (suppressEvalMicrotaskProcessing
                    && hasEvaluatingAsyncDependency(moduleRecord)) {
                // ES2024 16.2.1.5.2.1: Module depends on an EVALUATING_ASYNC module.
                // Don't evaluate yet; register as a pending dependent.
                // Set EVALUATING_ASYNC so transitive dependents also defer.
                moduleRecord.setAsyncEvaluationOrder(asyncEvaluationOrderCounter++);
                moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING_ASYNC);
                registerPendingDependent(moduleRecord);
                return moduleRecord;
            }
            if (moduleRecord.hasTLA()) {
                // Set EVALUATING before eval so nested deferred imports see correct state.
                moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING);
                // Suppress microtasks during eval so we can register the completion
                // callback before the microtask drain.
                boolean prevSuppress = suppressEvalMicrotaskProcessing;
                suppressEvalMicrotaskProcessing = true;
                JSValue evalResult;
                try {
                    evalResult = evaluateDynamicImportModule(moduleRecord);
                } finally {
                    suppressEvalMicrotaskProcessing = prevSuppress;
                }
                if (evalResult instanceof JSPromise asyncPromise) {
                    moduleRecord.setAsyncEvaluationOrder(asyncEvaluationOrderCounter++);
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING_ASYNC);
                    moduleRecord.setAsyncEvaluationPromise(asyncPromise);
                    registerAsyncModuleCompletion(moduleRecord, asyncPromise, importResolutionStack);
                    if (!suppressEvalMicrotaskProcessing) {
                        // Not in a suppressed context — drain microtasks now to
                        // let the async module complete before returning.
                        processMicrotasks();
                    }
                    return moduleRecord;
                }
                // TLA module but eval didn't return a promise (e.g., no actual await hit).
                // Fall through to normal completion.
            } else {
                evaluateDynamicImportModule(moduleRecord);
            }
            moduleRecord.namespace().finalizeNamespace();
            moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
            return moduleRecord;
        } catch (IOException ioException) {
            throw new JSException(throwTypeError("Cannot find module '" + resolvedSpecifier + "'"));
        } catch (JSSyntaxErrorException syntaxErrorException) {
            JSValue error = throwSyntaxError(syntaxErrorException.getMessage());
            moduleRecord.setEvaluationError(error);
            moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
            throw new JSException(error);
        } catch (JSCompilerException compilerException) {
            JSValue error = throwSyntaxError(compilerException.getMessage());
            moduleRecord.setEvaluationError(error);
            moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
            throw new JSException(error);
        } catch (JSException jsException) {
            // Keep the module in cache with EVALUATED_ERROR status so that subsequent
            // deferred imports can rethrow the same error object (per spec).
            moduleRecord.setEvaluationError(jsException.getErrorValue());
            moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
            throw jsException;
        } catch (Exception exception) {
            dynamicImportModuleCache.remove(moduleCacheKey);
            throw new JSException(throwError(exception.getMessage() != null ? exception.getMessage() : "Module load error"));
        }
    }

    /**
     * Load and cache a JavaScript module.
     *
     * @param specifier Module specifier (file path or URL)
     * @return The loaded module
     * @throws JSModule.ModuleLinkingException    if module cannot be loaded or linked
     * @throws JSModule.ModuleEvaluationException if module evaluation fails
     */
    public JSModule loadModule(String specifier) throws JSModule.ModuleLinkingException, JSModule.ModuleEvaluationException {
        // Check cache first
        JSModule cached = moduleCache.get(specifier);
        return cached;

        // In full implementation:
        // 1. Resolve the module specifier to absolute path
        // 2. Load the module source code
        // 3. Parse and compile as module
        // 4. Link imported/exported bindings
        // 5. Execute module code (if not already executed)
        // 6. Cache and return the module

        // For now, return null to indicate module not found
        // A full implementation would load from filesystem or URL
    }

    private String maskModuleComments(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return "";
        }
        StringBuilder maskedBuilder = new StringBuilder(sourceCode.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inTemplateLiteral = false;
        for (int index = 0; index < sourceCode.length(); index++) {
            char currentChar = sourceCode.charAt(index);
            char nextChar = index + 1 < sourceCode.length() ? sourceCode.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (currentChar == '\n' || currentChar == '\r') {
                    inLineComment = false;
                    maskedBuilder.append(currentChar);
                } else {
                    maskedBuilder.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (currentChar == '*' && nextChar == '/') {
                    maskedBuilder.append(' ');
                    maskedBuilder.append(' ');
                    index++;
                    inBlockComment = false;
                    continue;
                }
                if (currentChar == '\n' || currentChar == '\r') {
                    maskedBuilder.append(currentChar);
                } else {
                    maskedBuilder.append(' ');
                }
                continue;
            }
            if (inSingleQuote) {
                maskedBuilder.append(currentChar);
                if (currentChar == '\\' && index + 1 < sourceCode.length()) {
                    index++;
                    maskedBuilder.append(sourceCode.charAt(index));
                } else if (currentChar == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                maskedBuilder.append(currentChar);
                if (currentChar == '\\' && index + 1 < sourceCode.length()) {
                    index++;
                    maskedBuilder.append(sourceCode.charAt(index));
                } else if (currentChar == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }
            if (inTemplateLiteral) {
                maskedBuilder.append(currentChar);
                if (currentChar == '\\' && index + 1 < sourceCode.length()) {
                    index++;
                    maskedBuilder.append(sourceCode.charAt(index));
                } else if (currentChar == '`') {
                    inTemplateLiteral = false;
                }
                continue;
            }

            if (currentChar == '/' && nextChar == '/') {
                maskedBuilder.append(' ');
                maskedBuilder.append(' ');
                index++;
                inLineComment = true;
                continue;
            }
            if (currentChar == '/' && nextChar == '*') {
                maskedBuilder.append(' ');
                maskedBuilder.append(' ');
                index++;
                inBlockComment = true;
                continue;
            }
            if (currentChar == '\'') {
                inSingleQuote = true;
            } else if (currentChar == '"') {
                inDoubleQuote = true;
            } else if (currentChar == '`') {
                inTemplateLiteral = true;
            }
            maskedBuilder.append(currentChar);
        }
        return maskedBuilder.toString();
    }

    private void mergeStarReExport(
            JSDynamicImportModule moduleRecord,
            JSDynamicImportModule targetModuleRecord,
            Map<String, String> exportOrigins,
            String targetSpecifier) {
        Set<String> candidateExportNames = new TreeSet<>();
        for (PropertyKey key : targetModuleRecord.namespace().getOwnPropertyKeys()) {
            if (key.isString()) {
                candidateExportNames.add(key.asString());
            }
        }
        candidateExportNames.addAll(targetModuleRecord.explicitExportNames());
        for (JSDynamicImportModule.ReExportBinding reExportBinding : targetModuleRecord.reExportBindings()) {
            if (!reExportBinding.starExport()) {
                candidateExportNames.add(reExportBinding.exportedName());
            }
        }

        for (String exportName : candidateExportNames) {
            if ("default".equals(exportName)) {
                continue;
            }
            // Skip names already known to be ambiguous in this module
            // (from a previous incremental or full re-export resolution pass).
            if (moduleRecord.ambiguousExportNames().contains(exportName)) {
                continue;
            }
            if (targetModuleRecord.ambiguousExportNames().contains(exportName)) {
                moduleRecord.ambiguousExportNames().add(exportName);
                moduleRecord.namespace().removeExportBinding(exportName);
                exportOrigins.remove(exportName);
                continue;
            }
            if (moduleRecord.explicitExportNames().contains(exportName)) {
                continue;
            }
            DynamicImportExportResolution resolution = resolveDynamicImportExport(
                    targetModuleRecord,
                    exportName,
                    new HashSet<>(),
                    new HashSet<>());
            if (resolution.ambiguous()) {
                moduleRecord.ambiguousExportNames().add(exportName);
                moduleRecord.namespace().removeExportBinding(exportName);
                exportOrigins.remove(exportName);
                continue;
            }
            if (!resolution.found()) {
                continue;
            }
            String existingOrigin = exportOrigins.get(exportName);
            String candidateOrigin = resolution.moduleRecord().resolvedSpecifier();
            if (existingOrigin == null) {
                defineDynamicImportNamespaceForwardingBinding(
                        moduleRecord,
                        exportName,
                        resolution.moduleRecord(),
                        candidateOrigin,
                        resolution.bindingName());
                exportOrigins.put(exportName, candidateOrigin);
                continue;
            }
            if (!existingOrigin.equals(candidateOrigin)) {
                moduleRecord.ambiguousExportNames().add(exportName);
                moduleRecord.namespace().removeExportBinding(exportName);
                exportOrigins.remove(exportName);
            }
        }
    }

    private String normalizeModuleSpecifier(String specifier) {
        if (specifier == null || specifier.isEmpty()) {
            return "";
        }
        try {
            return Paths.get(specifier).normalize().toString();
        } catch (InvalidPathException invalidPathException) {
            return specifier;
        }
    }

    private void parseDynamicImportExportList(
            String exportListText,
            String sourceSpecifier,
            List<JSDynamicImportModule.LocalExportBinding> localExportBindings,
            List<JSDynamicImportModule.ReExportBinding> reExportBindings) {
        // Split on commas at the top level only (not inside quoted strings)
        List<String> exportEntries = splitOnTopLevelCommas(exportListText);
        for (String exportEntry : exportEntries) {
            String exportText = exportEntry.trim();
            if (exportText.isEmpty()) {
                continue;
            }
            String localName;
            String exportedName;
            // Parse "localName as exportedName" with support for string literals
            int asIndex = findTopLevelAs(exportText);
            if (asIndex >= 0) {
                String rawLocal = exportText.substring(0, asIndex).trim();
                String rawExported = exportText.substring(asIndex + 2).trim();
                localName = parseModuleExportNameValue(rawLocal);
                exportedName = parseModuleExportNameValue(rawExported);
            } else {
                localName = parseModuleExportNameValue(exportText);
                exportedName = localName;
            }
            if (sourceSpecifier == null) {
                localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(localName, exportedName));
            } else {
                reExportBindings.add(new JSDynamicImportModule.ReExportBinding(sourceSpecifier, localName, exportedName, false));
            }
        }
    }

    private void parseDynamicImportModuleSource(JSDynamicImportModule moduleRecord) {
        String sourceCode = moduleRecord.rawSource();
        String scanSourceCode = maskModuleComments(sourceCode);
        StringBuilder importPreambleBuilder = new StringBuilder();
        StringBuilder transformedSourceBuilder = new StringBuilder(sourceCode.length() + 128);
        List<JSDynamicImportModule.HoistedFunctionExportBinding> hoistedFunctionExportBindings = new ArrayList<>();
        List<JSDynamicImportModule.LocalExportBinding> localExportBindings = new ArrayList<>();
        List<JSDynamicImportModule.ReExportBinding> reExportBindings = new ArrayList<>();
        Map<String, ImportBinding> importedBindings = new HashMap<>();
        Set<String> importedBindingNames = new HashSet<>();
        boolean hasExportSyntax = false;
        int defaultExportIndex = 0;
        StringBuilder defaultExportNameFixups = new StringBuilder();

        String[] lines = sourceCode.split("\n", -1);
        String[] scanLines = scanSourceCode.split("\n", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            String normalizedLine = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            String scanLine = lineIndex < scanLines.length ? scanLines[lineIndex] : "";
            String parseLine = scanLine.endsWith("\r") ? scanLine.substring(0, scanLine.length() - 1) : scanLine;
            String trimmedLine = parseLine.stripLeading();
            // Extract import lines to be placed before the IIFE wrapper
            if (isStaticImportLine(trimmedLine)) {
                StringBuilder importStatementBuilder = new StringBuilder(normalizedLine);
                StringBuilder importStatementScanBuilder = new StringBuilder(parseLine);
                while (!isCompleteStaticImportStatement(importStatementScanBuilder.toString())
                        && lineIndex + 1 < lines.length) {
                    lineIndex++;
                    String nextLine = lines[lineIndex];
                    String normalizedNextLine = nextLine.endsWith("\r")
                            ? nextLine.substring(0, nextLine.length() - 1)
                            : nextLine;
                    String nextScanLine = lineIndex < scanLines.length ? scanLines[lineIndex] : "";
                    String normalizedNextScanLine = nextScanLine.endsWith("\r")
                            ? nextScanLine.substring(0, nextScanLine.length() - 1)
                            : nextScanLine;
                    importStatementBuilder.append('\n').append(normalizedNextLine);
                    importStatementScanBuilder.append('\n').append(normalizedNextScanLine);
                }
                String importStatementSource = importStatementBuilder.toString();
                String importStatementForScan = importStatementScanBuilder.toString().strip();
                importPreambleBuilder.append(importStatementSource).append('\n');
                collectImportBindings(importStatementForScan, importedBindingNames, importedBindings);
                continue;
            }
            if (!trimmedLine.startsWith("export ") && !trimmedLine.startsWith("export{")
                    && !trimmedLine.startsWith("export*") && !trimmedLine.equals("export")) {
                transformedSourceBuilder.append(normalizedLine).append('\n');
                continue;
            }

            hasExportSyntax = true;
            String exportClause;
            if (trimmedLine.startsWith("export ")) {
                exportClause = trimmedLine.substring("export ".length()).trim();
            } else if (trimmedLine.equals("export") || trimmedLine.startsWith("export") && trimmedLine.substring("export".length()).isBlank()) {
                exportClause = "";
            } else {
                // export{ or export* — no space after 'export'
                exportClause = trimmedLine.substring("export".length()).trim();
            }
            // If the export clause is empty (bare 'export', 'export' with trailing comments/whitespace),
            // look ahead to subsequent lines for the continuation.
            if (exportClause.isEmpty()) {
                StringBuilder exportContinuation = new StringBuilder();
                while (lineIndex + 1 < lines.length) {
                    lineIndex++;
                    String nextScanLine = lineIndex < scanLines.length ? scanLines[lineIndex] : "";
                    String normalizedNextScanLine = nextScanLine.endsWith("\r")
                            ? nextScanLine.substring(0, nextScanLine.length() - 1) : nextScanLine;
                    exportContinuation.append(normalizedNextScanLine.stripLeading());
                    if (exportContinuation.toString().contains("}") || exportContinuation.toString().contains("*")) {
                        break;
                    }
                }
                exportClause = exportContinuation.toString().trim();
            }
            if (exportClause.startsWith("default ")) {
                String defaultClause = exportClause.substring("default ".length()).trim();
                if (isDynamicImportDefaultDeclarationClause(defaultClause)) {
                    String declarationName = extractExportedFunctionOrClassName(defaultClause);
                    String declarationLine = normalizedLine.replaceFirst("^(\\s*)export\\s+default\\s+", "$1");
                    // For multi-line declarations (class/function body spans multiple lines),
                    // accumulate subsequent lines until the body is complete.
                    while (findEndOfDeclarationBody(declarationLine) < 0 && lineIndex + 1 < lines.length) {
                        lineIndex++;
                        String nextLine = lines[lineIndex];
                        String normalizedNext = nextLine.endsWith("\r") ? nextLine.substring(0, nextLine.length() - 1) : nextLine;
                        declarationLine = declarationLine + "\n" + normalizedNext;
                    }
                    boolean anonymousDefaultDeclaration = false;
                    if (declarationName == null || declarationName.isEmpty()) {
                        String defaultLocalName = "__qjs4jDefaultExport$" + defaultExportIndex++;
                        declarationName = defaultLocalName;
                        // Use var assignment instead of renaming the declaration.
                        // var hoists to the IIFE function scope, so the getter in the
                        // export preamble can reference it before this line executes.
                        // Split the declaration from any trailing statements on the same line
                        // (e.g., `export default class {} if (...) { ... }`).
                        int bodyEnd = findEndOfDeclarationBody(declarationLine);
                        String declarationPart;
                        String remainingCode;
                        if (bodyEnd >= 0 && bodyEnd < declarationLine.length()) {
                            declarationPart = declarationLine.substring(0, bodyEnd);
                            remainingCode = declarationLine.substring(bodyEnd).trim();
                        } else {
                            declarationPart = declarationLine;
                            remainingCode = "";
                        }
                        // For anonymous default class exports, insert a static block
                        // at the start of the class body to set .name = "default" before
                        // any static field initializers run. ES2024 specifies that
                        // default-exported anonymous classes get the name "default" during
                        // ClassDefinitionEvaluation, before static elements are evaluated.
                        if (defaultClause.startsWith("class")) {
                            int openBrace = findLikelyClassBodyOpenBrace(declarationPart);
                            if (openBrace >= 0) {
                                // ES2024 15.2.3.11: Only set name to "default" if the class
                                // doesn't already have a "name" own property (e.g. static name method).
                                declarationPart = declarationPart.substring(0, openBrace + 1)
                                        + " static { if (!Object.prototype.hasOwnProperty.call(this, 'name')"
                                        + " || this.name === ''"
                                        + " || this.name === '" + defaultLocalName + "') { "
                                        + "Object.defineProperty(this, 'name', {value: 'default', configurable: true}); } }"
                                        + declarationPart.substring(openBrace + 1);
                            }
                        }
                        if (defaultClause.startsWith("class")) {
                            transformedSourceBuilder.append("let ")
                                    .append(defaultLocalName)
                                    .append(" = ")
                                    .append(declarationPart)
                                    .append(";\n");
                            appendDynamicImportDefaultExportNameFixup(transformedSourceBuilder, declarationName);
                        } else {
                            String renamedDeclaration = renameAnonymousDefaultExportDeclaration(
                                    declarationPart, defaultLocalName);
                            transformedSourceBuilder.append(renamedDeclaration).append('\n');
                            appendDynamicImportDefaultExportNameFixup(defaultExportNameFixups, declarationName);
                        }
                        if (!remainingCode.isEmpty()) {
                            transformedSourceBuilder.append(remainingCode).append('\n');
                        }
                        anonymousDefaultDeclaration = true;
                    }
                    if (!anonymousDefaultDeclaration) {
                        // Named default exports (e.g., export default class Foo { ... })
                        // need var hoisting so the export preamble getter can reference
                        // the name before the declaration executes during self-import.
                        if (defaultClause.startsWith("class")) {
                            int bodyEnd = findEndOfDeclarationBody(declarationLine);
                            String declarationPart;
                            String remainingCode;
                            if (bodyEnd >= 0 && bodyEnd < declarationLine.length()) {
                                declarationPart = declarationLine.substring(0, bodyEnd);
                                remainingCode = declarationLine.substring(bodyEnd).trim();
                            } else {
                                declarationPart = declarationLine;
                                remainingCode = "";
                            }
                            transformedSourceBuilder.append("let ")
                                    .append(declarationName)
                                    .append(" = ")
                                    .append(declarationPart)
                                    .append(";\n");
                            if (!remainingCode.isEmpty()) {
                                transformedSourceBuilder.append(remainingCode).append('\n');
                            }
                        } else {
                            transformedSourceBuilder.append(declarationLine).append('\n');
                        }
                    }
                    localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(declarationName, "default"));
                } else {
                    String defaultExpression = defaultClause;
                    while (defaultExpression.endsWith(";")) {
                        defaultExpression = defaultExpression.substring(0, defaultExpression.length() - 1).trim();
                    }
                    String defaultLocalName = "__qjs4jDefaultExport$" + defaultExportIndex++;
                    transformedSourceBuilder.append("let ")
                            .append(defaultLocalName)
                            .append(" = (0, ")
                            .append(defaultExpression)
                            .append(");\n");
                    appendDynamicImportDefaultExportNameFixup(transformedSourceBuilder, defaultLocalName);
                    localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(defaultLocalName, "default"));
                }
                continue;
            }

            if (exportClause.startsWith("var ")
                    || exportClause.startsWith("let ")
                    || exportClause.startsWith("const ")) {
                transformedSourceBuilder.append(normalizedLine.replaceFirst("export\\s+", "")).append('\n');
                for (String declarationName : extractSimpleDeclarationNames(exportClause)) {
                    localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(declarationName, declarationName));
                }
                continue;
            }

            if (exportClause.startsWith("function ")
                    || exportClause.startsWith("function*")
                    || exportClause.startsWith("async function ")
                    || exportClause.startsWith("async function*")
                    || exportClause.startsWith("class ")) {
                String declarationLine = normalizedLine.replaceFirst("^(\\s*)export\\s+", "$1");
                while (findEndOfDeclarationBody(declarationLine) < 0 && lineIndex + 1 < lines.length) {
                    lineIndex++;
                    String nextLine = lines[lineIndex];
                    String normalizedNextLine = nextLine.endsWith("\r")
                            ? nextLine.substring(0, nextLine.length() - 1)
                            : nextLine;
                    declarationLine = declarationLine + "\n" + normalizedNextLine;
                }
                int declarationBodyEnd = findEndOfDeclarationBody(declarationLine);
                String declarationPart = declarationLine;
                String remainingCode = "";
                if (declarationBodyEnd >= 0 && declarationBodyEnd < declarationLine.length()) {
                    declarationPart = declarationLine.substring(0, declarationBodyEnd);
                    remainingCode = declarationLine.substring(declarationBodyEnd).trim();
                }
                transformedSourceBuilder.append(declarationPart).append('\n');
                if (!remainingCode.isEmpty()) {
                    transformedSourceBuilder.append(remainingCode).append('\n');
                }
                String declarationName = extractExportedFunctionOrClassName(exportClause);
                if (declarationName == null || declarationName.isEmpty()) {
                    throw new JSException(throwSyntaxError("Invalid export statement"));
                }
                localExportBindings.add(new JSDynamicImportModule.LocalExportBinding(declarationName, declarationName));
                if (exportClause.startsWith("function ")
                        || exportClause.startsWith("function*")
                        || exportClause.startsWith("async function ")
                        || exportClause.startsWith("async function*")) {
                    hoistedFunctionExportBindings.add(new JSDynamicImportModule.HoistedFunctionExportBinding(
                            declarationName,
                            declarationName,
                            declarationPart));
                }
                continue;
            }

            if (exportClause.startsWith("{")) {
                String exportSpecifiersText = exportClause;
                while (findMatchingCloseBrace(exportSpecifiersText, 0) < 0 && lineIndex + 1 < lines.length) {
                    lineIndex++;
                    String nextScanLine = lineIndex < scanLines.length ? scanLines[lineIndex] : "";
                    String normalizedNextScanLine = nextScanLine.endsWith("\r")
                            ? nextScanLine.substring(0, nextScanLine.length() - 1)
                            : nextScanLine;
                    exportSpecifiersText = exportSpecifiersText + "\n" + normalizedNextScanLine.stripLeading();
                }

                int closeBraceIndex = findMatchingCloseBrace(exportSpecifiersText, 0);
                if (closeBraceIndex < 0) {
                    throw new JSException(throwSyntaxError("Invalid export statement"));
                }
                String exportListText = exportSpecifiersText.substring(1, closeBraceIndex).trim();
                String afterBraceText = exportSpecifiersText.substring(closeBraceIndex + 1).trim();
                while (afterBraceText.endsWith(";")) {
                    afterBraceText = afterBraceText.substring(0, afterBraceText.length() - 1).trim();
                }
                String sourceSpecifier = null;
                if (!afterBraceText.isEmpty()) {
                    if (!afterBraceText.startsWith("from ")) {
                        throw new JSException(throwSyntaxError("Invalid export statement"));
                    }
                    String fromText = afterBraceText.substring("from ".length()).trim();
                    sourceSpecifier = stripQuotedSpecifier(fromText);
                    // Add side-effect import to ensure source-order evaluation.
                    // ES2024 requires all module dependencies (imports AND re-exports)
                    // to be evaluated in source order before the requesting module.
                    importPreambleBuilder.append("import '").append(sourceSpecifier).append("';\n");
                }
                int localBindingStartIndex = localExportBindings.size();
                parseDynamicImportExportList(exportListText, sourceSpecifier, localExportBindings, reExportBindings);
                if (sourceSpecifier == null && localBindingStartIndex < localExportBindings.size()) {
                    for (int localBindingIndex = localExportBindings.size() - 1;
                         localBindingIndex >= localBindingStartIndex;
                         localBindingIndex--) {
                        JSDynamicImportModule.LocalExportBinding localExportBinding =
                                localExportBindings.get(localBindingIndex);
                        ImportBinding importBinding = importedBindings.get(localExportBinding.localName());
                        if (importBinding == null || importBinding.deferredImport()) {
                            continue;
                        }
                        localExportBindings.remove(localBindingIndex);
                        reExportBindings.add(new JSDynamicImportModule.ReExportBinding(
                                importBinding.sourceSpecifier(),
                                importBinding.importedName(),
                                localExportBinding.exportedName(),
                                false));
                    }
                }
                continue;
            }

            if (exportClause.startsWith("*")) {
                String afterStarText = exportClause.substring(1).trim();
                if (afterStarText.startsWith("as ")) {
                    // Handle both identifier and string literal export names:
                    // export * as name from '...'
                    // export * as "name" from '...'
                    String afterAs = afterStarText.substring(3).trim();
                    String exportedName;
                    String remainingAfterName;
                    if (afterAs.startsWith("\"") || afterAs.startsWith("'")) {
                        char quote = afterAs.charAt(0);
                        int closeQuote = afterAs.indexOf(quote, 1);
                        if (closeQuote < 0) {
                            throw new JSException(throwSyntaxError("Invalid export statement"));
                        }
                        exportedName = afterAs.substring(1, closeQuote);
                        remainingAfterName = afterAs.substring(closeQuote + 1).trim();
                    } else {
                        Matcher identMatcher = Pattern.compile("^([A-Za-z_$][A-Za-z0-9_$]*)\\s+(.*)$")
                                .matcher(afterAs);
                        if (!identMatcher.find()) {
                            throw new JSException(throwSyntaxError("Invalid export statement"));
                        }
                        exportedName = identMatcher.group(1);
                        remainingAfterName = identMatcher.group(2).trim();
                    }
                    if (!remainingAfterName.startsWith("from ")) {
                        throw new JSException(throwSyntaxError("Invalid export statement"));
                    }
                    String fromText = remainingAfterName.substring(5).trim();
                    while (fromText.endsWith(";")) {
                        fromText = fromText.substring(0, fromText.length() - 1).trim();
                    }
                    String sourceSpecifier = stripQuotedSpecifier(fromText);
                    reExportBindings.add(new JSDynamicImportModule.ReExportBinding(sourceSpecifier, "*namespace*", exportedName, false));
                    // Add side-effect import for source-order evaluation
                    importPreambleBuilder.append("import '").append(sourceSpecifier).append("';\n");
                    continue;
                }
                if (afterStarText.startsWith("from ")) {
                    String fromText = afterStarText.substring("from ".length()).trim();
                    while (fromText.endsWith(";")) {
                        fromText = fromText.substring(0, fromText.length() - 1).trim();
                    }
                    String sourceSpecifier = stripQuotedSpecifier(fromText);
                    reExportBindings.add(new JSDynamicImportModule.ReExportBinding(sourceSpecifier, "*", "*", true));
                    // Add side-effect import for source-order evaluation
                    importPreambleBuilder.append("import '").append(sourceSpecifier).append("';\n");
                    continue;
                }
                throw new JSException(throwSyntaxError("Invalid export statement"));
            }

            throw new JSException(throwSyntaxError("Unexpected export syntax"));
        }

        moduleRecord.setHasExportSyntax(hasExportSyntax);
        moduleRecord.hoistedFunctionExportBindings().clear();
        moduleRecord.hoistedFunctionExportBindings().addAll(hoistedFunctionExportBindings);
        moduleRecord.setHoistedFunctionExportBindingsInitialized(false);
        moduleRecord.localExportBindings().addAll(localExportBindings);
        moduleRecord.reExportBindings().addAll(reExportBindings);
        for (JSDynamicImportModule.LocalExportBinding localExportBinding : localExportBindings) {
            moduleRecord.explicitExportNames().add(localExportBinding.exportedName());
            moduleRecord.exportOrigins().put(localExportBinding.exportedName(), moduleRecord.resolvedSpecifier());
            moduleRecord.namespace().registerExportName(localExportBinding.exportedName());
        }
        for (JSDynamicImportModule.ReExportBinding reExportBinding : reExportBindings) {
            if (reExportBinding.starExport()) {
                continue;
            }
            moduleRecord.explicitExportNames().add(reExportBinding.exportedName());
            moduleRecord.namespace().registerExportName(reExportBinding.exportedName());
        }

        boolean hasTLA = MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(transformedSourceBuilder).find()
                || Pattern.compile("\\bawait\\b").matcher(scanSourceCode).find();
        moduleRecord.setHasTLA(hasTLA);

        if (hasExportSyntax) {
            String exportBindingName = createModuleExportBindingName(moduleRecord.resolvedSpecifier());
            // Build the export assignment preamble separately — it goes at the START
            // of the IIFE body so self-import getters can read from the namespace
            // before user code executes. Getter functions are lazy (not called at
            // definition time), so TDZ for const/class locals is not violated.
            StringBuilder exportPreamble = new StringBuilder();
            appendDynamicImportExportAssignments(
                    exportPreamble, exportBindingName,
                    localExportBindings, importedBindingNames);
            if (!defaultExportNameFixups.isEmpty()) {
                exportPreamble.append(defaultExportNameFixups);
            }
            LinkedHashSet<String> importedBindingsToCapture = new LinkedHashSet<>();
            for (JSDynamicImportModule.LocalExportBinding localExportBinding : localExportBindings) {
                ImportBinding importBinding = importedBindings.get(localExportBinding.localName());
                if (importBinding != null && importBinding.deferredImport()) {
                    importedBindingsToCapture.add(localExportBinding.localName());
                }
            }
            String transformedSource;
            if (hasTLA) {
                // For TLA export modules, capture only exportBindingName.
                // Imported bindings from self-imports stay live to preserve TDZ behavior.
                // Other imported bindings are captured so they remain available after
                // import-overlay cleanup while async module evaluation continues.
                LinkedHashSet<String> tlaImportedBindingsToCapture = new LinkedHashSet<>();
                for (String importedBindingName : importedBindingNames) {
                    ImportBinding importBinding = importedBindings.get(importedBindingName);
                    if (importBinding == null
                            || isSelfImportBinding(importBinding, moduleRecord.resolvedSpecifier())) {
                        continue;
                    }
                    tlaImportedBindingsToCapture.add(importedBindingName);
                }
                List<String> paramNames = new ArrayList<>();
                paramNames.add(exportBindingName);
                paramNames.addAll(tlaImportedBindingsToCapture);
                String paramList = String.join(", ", paramNames);
                transformedSource = importPreambleBuilder
                        + "(async function(" + paramList + ") {\n"
                        + exportPreamble
                        + transformedSourceBuilder
                        + "})(" + paramList + ");\n";
            } else {
                if (importedBindingsToCapture.isEmpty()) {
                    transformedSource = importPreambleBuilder
                            + "(function () {\n"
                            + exportPreamble
                            + transformedSourceBuilder
                            + "})();\n";
                } else {
                    String paramList = String.join(", ", importedBindingsToCapture);
                    transformedSource = importPreambleBuilder
                            + "(function (" + paramList + ") {\n"
                            + exportPreamble
                            + transformedSourceBuilder
                            + "})(" + paramList + ");\n";
                }
            }
            moduleRecord.setTransformedSource(transformedSource);
            moduleRecord.setExportBindingName(exportBindingName);
        } else if (!importedBindingNames.isEmpty() || hasTLA) {
            // Wrap non-export modules in an IIFE to capture imported bindings in closure.
            String paramList = String.join(", ", importedBindingNames);
            String transformedSource = importPreambleBuilder
                    + (hasTLA ? "(async function(" : "(function(")
                    + paramList + ") {\n"
                    + transformedSourceBuilder
                    + "})(" + paramList + ");\n";
            moduleRecord.setTransformedSource(transformedSource);
        } else {
            moduleRecord.setTransformedSource(sourceCode);
        }
    }

    private JSValue parseJsonModuleSource(String sourceCode) {
        JSValue jsonValue = getGlobalObject().get(PropertyKey.fromString("JSON"));
        if (hasPendingException()) {
            JSValue error = getPendingException();
            clearPendingException();
            throw new JSException(error);
        }
        if (!(jsonValue instanceof JSObject jsonObject)) {
            throw new JSException(throwTypeError("JSON is not an object"));
        }

        JSValue parseValue = jsonObject.get(PropertyKey.fromString("parse"));
        if (hasPendingException()) {
            JSValue error = getPendingException();
            clearPendingException();
            throw new JSException(error);
        }

        JSValue[] parseArguments = new JSValue[]{new JSString(sourceCode)};
        JSValue parsedValue;
        if (parseValue instanceof JSFunction parseFunction) {
            parsedValue = parseFunction.call(this, jsonObject, parseArguments);
        } else if (parseValue instanceof JSProxy parseProxy) {
            parsedValue = parseProxy.apply(this, jsonObject, parseArguments);
        } else {
            throw new JSException(throwTypeError("JSON.parse is not a function"));
        }
        if (hasPendingException()) {
            JSValue error = getPendingException();
            clearPendingException();
            throw new JSException(error);
        }
        return parsedValue;
    }

    /**
     * Parse a ModuleExportName value: either a quoted string literal or an identifier name.
     * Removes quotes from string literals, applies identifier escape decoding to identifiers.
     */
    private String parseModuleExportNameValue(String raw) {
        String trimmed = raw.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return decodeIdentifierEscapes(trimmed);
    }

    void pollFinalizationRegistries() {
        for (int registryIndex = 0; registryIndex < finalizationRegistries.size(); registryIndex++) {
            finalizationRegistries.get(registryIndex).pollCleanups();
        }
    }

    public void popEvalOverlay() {
        if (!evalOverlayFrames.isEmpty()) {
            evalOverlayFrames.pop();
        }
    }

    public void popEvalOverlayLookupSuppression() {
        if (evalOverlayLookupSuppressionDepth > 0) {
            evalOverlayLookupSuppressionDepth--;
        }
    }

    /**
     * Pop the current stack frame.
     */
    public JSStackFrame popStackFrame() {
        if (callStack.isEmpty()) {
            return null;
        }
        stackDepth--;
        return callStack.pop();
    }

    /**
     * Pre-load all static imports of a module so that EVALUATING_ASYNC dependencies
     * are discovered before we decide whether to defer or evaluate the module.
     */
    private void preloadStaticImports(JSDynamicImportModule moduleRecord,
                                      Set<String> importResolutionStack,
                                      Map<String, String> importAttributes) {
        String scanSource = maskModuleComments(moduleRecord.rawSource());
        Matcher matcher = MODULE_STATIC_IMPORT_PATTERN.matcher(scanSource);
        while (matcher.find()) {
            // Skip import defer statements — deferred modules must not be eagerly evaluated
            String fullMatch = matcher.group(0).stripLeading();
            if (fullMatch.startsWith("import") && fullMatch.length() > 6) {
                String afterImport = fullMatch.substring(6).stripLeading();
                if (afterImport.startsWith("defer")) {
                    continue;
                }
            }
            String specifier = matcher.group(1);
            try {
                String resolved = resolveDynamicImportSpecifier(
                        specifier, moduleRecord.resolvedSpecifier(), specifier);
                JSDynamicImportModule depRecord = dynamicImportModuleCache.get(resolved);
                if (depRecord != null) {
                    // ES2024 16.2.1.5.2.1 step 11.d: If the dependency is still on the
                    // evaluation stack (LOADING/EVALUATING), we're in a cycle. Set the
                    // current module's cycleRoot to the dependency's root (or the dependency
                    // itself if it has no cycle root).
                    if (depRecord.status() == JSDynamicImportModule.Status.LOADING
                            || depRecord.status() == JSDynamicImportModule.Status.EVALUATING) {
                        JSDynamicImportModule root =
                                depRecord.cycleRoot() != null ? depRecord.cycleRoot() : depRecord;
                        moduleRecord.setCycleRoot(root);
                    }
                } else {
                    loadJSDynamicImportModule(resolved,
                            new HashSet<>(importResolutionStack), importAttributes);
                }
            } catch (JSException ignored) {
                // Skip unresolvable specifiers
            }
        }
    }

    /**
     * Process all pending microtasks.
     * This should be called at the end of each task in the event loop.
     */
    public void processMicrotasks() {
        microtaskQueue.processMicrotasks();
        pollFinalizationRegistries();
    }

    public void pushEvalOverlay(Map<String, JSValue> savedGlobals, Set<String> absentKeys) {
        evalOverlayFrames.push(new EvalOverlayFrame(savedGlobals, absentKeys));
    }

    public void pushEvalOverlayLookupSuppression() {
        evalOverlayLookupSuppressionDepth++;
    }

    /**
     * Push a new stack frame.
     * Returns false if stack limit exceeded.
     */
    public boolean pushStackFrame(JSStackFrame frame) {
        if (stackDepth >= maxStackDepth) {
            return false;
        }
        callStack.push(frame);
        stackDepth++;
        return true;
    }

    public JSValue readGlobalLexicalBinding(String name) {
        return globalLexicalBindings.get(name);
    }

    /**
     * Implements ReadyForSyncExecution(_module_, _seen_) from the import-defer spec.
     * Returns true if the module and all its transitive dependencies can be evaluated synchronously.
     */
    boolean readyForSyncExecution(String resolvedSpecifier, Set<String> seen) {
        if (!seen.add(resolvedSpecifier)) {
            return true;
        }
        JSDynamicImportModule record = dynamicImportModuleCache.get(resolvedSpecifier);
        if (record != null) {
            if (record.status() == JSDynamicImportModule.Status.EVALUATED
                    || record.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
                return true;
            }
            if (record.status() == JSDynamicImportModule.Status.EVALUATING
                    || record.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC) {
                return false;
            }
        }
        // For LOADING status or no record, check the source for TLA and dependencies
        String sourceCode = null;
        if (record != null && record.rawSource() != null) {
            sourceCode = record.rawSource();
        } else {
            try {
                sourceCode = Files.readString(Path.of(resolvedSpecifier));
            } catch (IOException ioException) {
                return true;
            }
        }
        String scanSourceCode = maskModuleComments(sourceCode);
        if (MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(scanSourceCode).find()) {
            return false;
        }
        Matcher matcher = MODULE_STATIC_IMPORT_PATTERN.matcher(scanSourceCode);
        while (matcher.find()) {
            String childSpecifier = matcher.group(1);
            String resolvedChildSpecifier;
            try {
                resolvedChildSpecifier = resolveDynamicImportSpecifier(
                        childSpecifier, resolvedSpecifier, childSpecifier);
            } catch (JSException jsException) {
                continue;
            }
            if (!readyForSyncExecution(resolvedChildSpecifier, seen)) {
                return false;
            }
        }
        return true;
    }

    private void registerAsyncModuleCompletion(
            JSDynamicImportModule moduleRecord,
            JSPromise asyncPromise,
            Set<String> importResolutionStack) {
        JSNativeFunction onFulfill = new JSNativeFunction(this, "onFulfill", 0,
                (ctx, thisArg, args) -> {
                    resolveDynamicImportReExports(moduleRecord, new HashSet<>());
                    moduleRecord.namespace().finalizeNamespace();
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    triggerPendingDependents(moduleRecord);
                    return JSUndefined.INSTANCE;
                });
        onFulfill.initializePrototypeChain(this);
        JSNativeFunction onReject = new JSNativeFunction(this, "onReject", 1,
                (ctx, thisArg, args) -> {
                    JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                    moduleRecord.setEvaluationError(error);
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
                    triggerPendingDependents(moduleRecord);
                    return JSUndefined.INSTANCE;
                });
        onReject.initializePrototypeChain(this);
        asyncPromise.addReactions(
                new JSPromise.ReactionRecord(onFulfill, this, null, null),
                new JSPromise.ReactionRecord(onReject, this, null, null));
    }

    private void registerDeferredEvalOverlayRestore(
            JSPromise asyncModulePromise,
            EvalOverlayFrame evalOverlayFrame) {
        if (asyncModulePromise == null || evalOverlayFrame == null) {
            return;
        }
        JSNativeFunction onFulfill = new JSNativeFunction(this, "", 0,
                (ctx, thisArg, args) -> {
                    restoreEvalOverlayFrame(evalOverlayFrame);
                    return JSUndefined.INSTANCE;
                });
        onFulfill.initializePrototypeChain(this);
        JSNativeFunction onReject = new JSNativeFunction(this, "", 1,
                (ctx, thisArg, args) -> {
                    restoreEvalOverlayFrame(evalOverlayFrame);
                    return JSUndefined.INSTANCE;
                });
        onReject.initializePrototypeChain(this);
        asyncModulePromise.addReactions(
                new JSPromise.ReactionRecord(onFulfill, this, null, null),
                new JSPromise.ReactionRecord(onReject, this, null, null));
    }

    public void registerFinalizationRegistry(JSFinalizationRegistry registry) {
        finalizationRegistries.add(registry);
    }

    private void registerImportedBinding(
            String localName,
            String sourceSpecifier,
            String importedName,
            boolean deferredImport,
            Set<String> bindingNames,
            Map<String, ImportBinding> importedBindings) {
        String normalizedLocalName = localName == null ? "" : localName.trim();
        if (normalizedLocalName.isEmpty()) {
            return;
        }
        bindingNames.add(normalizedLocalName);
        if (sourceSpecifier != null && !sourceSpecifier.isEmpty()) {
            importedBindings.put(normalizedLocalName, new ImportBinding(sourceSpecifier, importedName, deferredImport));
        }
    }

    /**
     * Register a module in the cache.
     */
    public void registerIteratorPrototype(String tag, JSObject prototype) {
        iteratorPrototypes.put(tag, prototype);
    }

    public void registerModule(String specifier, JSModule module) {
        moduleCache.put(specifier, module);
    }

    private void registerPendingDependent(JSDynamicImportModule moduleRecord) {
        String scanSource = maskModuleComments(moduleRecord.rawSource());
        Matcher matcher = MODULE_STATIC_IMPORT_PATTERN.matcher(scanSource);
        int asyncDepCount = 0;
        Set<String> registeredOnSpecifiers = new HashSet<>();
        // Determine this module's effective cycle root for same-cycle detection.
        JSDynamicImportModule moduleCycleRoot =
                moduleRecord.cycleRoot() != null ? moduleRecord.cycleRoot() : moduleRecord;
        while (matcher.find()) {
            String specifier = matcher.group(1);
            try {
                String resolved = resolveDynamicImportSpecifier(
                        specifier, moduleRecord.resolvedSpecifier(), specifier);
                JSDynamicImportModule depRecord = dynamicImportModuleCache.get(resolved);
                if (depRecord == null) {
                    continue;
                }
                // ES2024 16.2.1.5.2.1 step 11.c.iv.1: Follow CycleRoot pointer only
                // for dependencies NOT in the same cycle. Modules in the same cycle
                // (sharing a cycle root) register directly on each other.
                JSDynamicImportModule effectiveDep = depRecord;
                if (depRecord.cycleRoot() != null) {
                    JSDynamicImportModule depCycleRoot = depRecord.cycleRoot();
                    // Only follow CycleRoot if the dependency is in a DIFFERENT cycle.
                    if (depCycleRoot != moduleCycleRoot) {
                        effectiveDep = depCycleRoot;
                    }
                }
                if (effectiveDep.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC
                        && registeredOnSpecifiers.add(effectiveDep.resolvedSpecifier())) {
                    effectiveDep.pendingDependents().add(moduleRecord);
                    asyncDepCount++;
                }
            } catch (JSException ignored) {
                // Skip unresolvable specifiers
            }
        }
        moduleRecord.setPendingAsyncDependencyCount(asyncDepCount);
    }

    private String renameAnonymousDefaultExportDeclaration(
            String declarationLine,
            String defaultLocalName) {
        String replacementName = Matcher.quoteReplacement(defaultLocalName);
        String renamedFunctionDeclaration = declarationLine.replaceFirst(
                "^(\\s*(?:async\\s+)?function(?:\\s*\\*)?)\\s*\\(",
                "$1 " + replacementName + "(");
        if (!renamedFunctionDeclaration.equals(declarationLine)) {
            return renamedFunctionDeclaration;
        }

        String renamedClassDeclaration = declarationLine.replaceFirst(
                "^(\\s*class)\\b",
                "$1 " + replacementName);
        if (!renamedClassDeclaration.equals(declarationLine)) {
            return renamedClassDeclaration;
        }

        throw new JSException(throwSyntaxError("Invalid default export declaration"));
    }

    private DynamicImportExportResolution resolveDynamicImportExport(
            JSDynamicImportModule moduleRecord,
            String exportName,
            Set<String> resolveSet,
            Set<String> exportStarSet) {
        if (moduleRecord.ambiguousExportNames().contains(exportName)) {
            return DynamicImportExportResolution.ambiguousResolution();
        }

        String resolveSetKey = moduleRecord.resolvedSpecifier() + "::" + exportName;
        if (!resolveSet.add(resolveSetKey)) {
            // Circular resolve request. Per ES2024 ResolveExport, return null.
            return DynamicImportExportResolution.notFoundResolution();
        }

        for (JSDynamicImportModule.LocalExportBinding localExportBinding : moduleRecord.localExportBindings()) {
            if (exportName.equals(localExportBinding.exportedName())) {
                return DynamicImportExportResolution.resolvedResolution(moduleRecord, exportName);
            }
        }

        for (JSDynamicImportModule.ReExportBinding reExportBinding : moduleRecord.reExportBindings()) {
            if (reExportBinding.starExport()) {
                continue;
            }
            if (!exportName.equals(reExportBinding.exportedName())) {
                continue;
            }
            String targetSpecifier = resolveDynamicImportSpecifier(
                    reExportBinding.sourceSpecifier(),
                    moduleRecord.resolvedSpecifier(),
                    reExportBinding.sourceSpecifier());
            JSDynamicImportModule targetModuleRecord =
                    loadJSDynamicImportModule(targetSpecifier, new HashSet<>(), null);
            if ("*namespace*".equals(reExportBinding.importedName())) {
                return DynamicImportExportResolution.resolvedResolution(targetModuleRecord, "*namespace*");
            }
            return resolveDynamicImportExport(
                    targetModuleRecord,
                    reExportBinding.importedName(),
                    resolveSet,
                    exportStarSet);
        }

        if ("default".equals(exportName)) {
            return DynamicImportExportResolution.notFoundResolution();
        }

        String exportStarSetKey = moduleRecord.resolvedSpecifier() + "::" + exportName;
        if (!exportStarSet.add(exportStarSetKey)) {
            return DynamicImportExportResolution.notFoundResolution();
        }

        DynamicImportExportResolution starResolution = DynamicImportExportResolution.notFoundResolution();
        for (JSDynamicImportModule.ReExportBinding reExportBinding : moduleRecord.reExportBindings()) {
            if (!reExportBinding.starExport()) {
                continue;
            }
            String targetSpecifier = resolveDynamicImportSpecifier(
                    reExportBinding.sourceSpecifier(),
                    moduleRecord.resolvedSpecifier(),
                    reExportBinding.sourceSpecifier());
            JSDynamicImportModule targetModuleRecord =
                    loadJSDynamicImportModule(targetSpecifier, new HashSet<>(), null);
            DynamicImportExportResolution resolution = resolveDynamicImportExport(
                    targetModuleRecord,
                    exportName,
                    resolveSet,
                    exportStarSet);
            if (resolution.ambiguous()) {
                return resolution;
            }
            if (!resolution.found()) {
                continue;
            }
            if (!starResolution.found()) {
                starResolution = resolution;
                continue;
            }
            boolean sameTargetModule = starResolution.moduleRecord() == resolution.moduleRecord();
            boolean sameBindingName = Objects.equals(starResolution.bindingName(), resolution.bindingName());
            if (!sameTargetModule || !sameBindingName) {
                return DynamicImportExportResolution.ambiguousResolution();
            }
        }
        return starResolution;
    }

    void resolveDynamicImportReExports(
            JSDynamicImportModule moduleRecord,
            Set<String> importResolutionStack) {
        if (moduleRecord.reExportBindings().isEmpty()) {
            return;
        }

        if (!importResolutionStack.add(moduleRecord.resolvedSpecifier())) {
            throw new JSException(throwSyntaxError("Circular module dependency"));
        }
        try {
            Map<String, String> exportOrigins = moduleRecord.exportOrigins();
            for (JSDynamicImportModule.ReExportBinding reExportBinding : moduleRecord.reExportBindings()) {
                String targetSpecifier = resolveDynamicImportSpecifier(
                        reExportBinding.sourceSpecifier(),
                        moduleRecord.resolvedSpecifier(),
                        reExportBinding.sourceSpecifier());
                JSDynamicImportModule targetModuleRecord =
                        loadJSDynamicImportModule(targetSpecifier, importResolutionStack, null);
                if (reExportBinding.starExport()) {
                    mergeStarReExport(moduleRecord, targetModuleRecord, exportOrigins, targetSpecifier);
                    continue;
                }
                String importedName = reExportBinding.importedName();
                DynamicImportExportResolution resolution;
                if ("*namespace*".equals(importedName)) {
                    resolution = DynamicImportExportResolution.resolvedResolution(targetModuleRecord, "*namespace*");
                } else {
                    resolution = resolveDynamicImportExport(
                            targetModuleRecord,
                            importedName,
                            new HashSet<>(),
                            new HashSet<>());
                }
                if (resolution.ambiguous()) {
                    throw new JSException(throwSyntaxError(
                            "ambiguous indirect export: " + reExportBinding.exportedName()));
                }
                if (!resolution.found()) {
                    throw new JSException(throwSyntaxError(
                            "module '" + targetSpecifier + "' does not provide export '" + importedName + "'"));
                }
                String existingOrigin = exportOrigins.get(reExportBinding.exportedName());
                String resolvedOrigin = resolution.moduleRecord().resolvedSpecifier();
                if (existingOrigin != null && existingOrigin.equals(resolvedOrigin)) {
                    continue;
                }
                defineDynamicImportNamespaceForwardingBinding(
                        moduleRecord,
                        reExportBinding.exportedName(),
                        resolution.moduleRecord(),
                        resolvedOrigin,
                        resolution.bindingName());
                moduleRecord.explicitExportNames().add(reExportBinding.exportedName());
                exportOrigins.put(reExportBinding.exportedName(), resolvedOrigin);
            }
        } finally {
            importResolutionStack.remove(moduleRecord.resolvedSpecifier());
        }
    }

    private String resolveDynamicImportSpecifier(
            String specifier,
            String referrerFilename,
            String errorSpecifier) {
        final Path rawSpecifierPath;
        try {
            rawSpecifierPath = Paths.get(specifier);
        } catch (InvalidPathException invalidPathException) {
            throw new JSException(throwTypeError("Cannot find module '" + errorSpecifier + "'"));
        }

        Path resolvedPath = rawSpecifierPath;
        if (!resolvedPath.isAbsolute()
                && referrerFilename != null
                && !referrerFilename.isEmpty()
                && !referrerFilename.startsWith("<")) {
            Path referrerPath = Paths.get(referrerFilename);
            Path parentPath = referrerPath.getParent();
            if (parentPath != null) {
                resolvedPath = parentPath.resolve(resolvedPath);
            }
        }
        resolvedPath = resolvedPath.normalize();
        if (!Files.exists(resolvedPath)) {
            throw new JSException(throwTypeError("Cannot find module '" + errorSpecifier + "'"));
        }
        return resolvedPath.toString();
    }

    /**
     * After loading a side-effect import (from an export-from line), resolve matching
     * re-export bindings for the current module immediately. This populates the namespace
     * before the IIFE body runs, so self-imports can see re-exported names.
     */
    private void resolveIncrementalReExport(String specifier, String filename) {
        String normalizedFilename = Paths.get(filename).normalize().toString();
        JSDynamicImportModule currentModule = dynamicImportModuleCache.get(normalizedFilename);
        if (currentModule == null || currentModule.reExportBindings().isEmpty()) {
            return;
        }
        String resolvedTargetSpec;
        try {
            resolvedTargetSpec = resolveDynamicImportSpecifier(
                    specifier, filename, specifier);
        } catch (Exception e) {
            return;
        }
        JSDynamicImportModule targetModule = dynamicImportModuleCache.get(resolvedTargetSpec);
        if (targetModule == null || targetModule.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
            return;
        }
        Map<String, String> exportOrigins = currentModule.exportOrigins();
        for (JSDynamicImportModule.ReExportBinding reExport : currentModule.reExportBindings()) {
            String reExportTargetSpec;
            try {
                reExportTargetSpec = resolveDynamicImportSpecifier(
                        reExport.sourceSpecifier(),
                        currentModule.resolvedSpecifier(),
                        reExport.sourceSpecifier());
            } catch (Exception e) {
                continue;
            }
            if (!reExportTargetSpec.equals(resolvedTargetSpec)) {
                continue;
            }
            if (reExport.starExport()) {
                mergeStarReExport(currentModule, targetModule, exportOrigins, reExportTargetSpec);
            } else {
                String importedName = reExport.importedName();
                DynamicImportExportResolution resolution;
                if ("*namespace*".equals(importedName)) {
                    resolution = DynamicImportExportResolution.resolvedResolution(targetModule, "*namespace*");
                } else {
                    resolution = resolveDynamicImportExport(
                            targetModule,
                            importedName,
                            new HashSet<>(),
                            new HashSet<>());
                }
                if (resolution.ambiguous()) {
                    throw new JSException(throwSyntaxError(
                            "ambiguous indirect export: " + reExport.exportedName()));
                }
                if (!resolution.found()) {
                    throw new JSException(throwSyntaxError(
                            "module '" + reExportTargetSpec + "' does not provide export '" + importedName + "'"));
                }
                String existingOrigin = exportOrigins.get(reExport.exportedName());
                String resolvedOrigin = resolution.moduleRecord().resolvedSpecifier();
                if (existingOrigin != null && existingOrigin.equals(resolvedOrigin)) {
                    continue;
                }
                defineDynamicImportNamespaceForwardingBinding(
                        currentModule,
                        reExport.exportedName(),
                        resolution.moduleRecord(),
                        resolvedOrigin,
                        resolution.bindingName());
                currentModule.explicitExportNames().add(reExport.exportedName());
                exportOrigins.put(reExport.exportedName(), resolvedOrigin);
            }
        }
    }

    private void restoreEvalOverlayFrame(EvalOverlayFrame evalOverlayFrame) {
        if (evalOverlayFrame == null) {
            return;
        }
        JSObject globalObject = getGlobalObject();
        for (var entry : evalOverlayFrame.savedGlobals().entrySet()) {
            PropertyKey key = PropertyKey.fromString(entry.getKey());
            // Use defineProperty to restore data properties, overwriting any accessor
            // properties that were set up for live import bindings.
            globalObject.defineProperty(key, entry.getValue(), PropertyDescriptor.DataState.All);
        }
        for (String absentKey : evalOverlayFrame.absentKeys()) {
            globalObject.delete(PropertyKey.fromString(absentKey));
        }
    }

    public void resumeEvalOverlays(JSGlobalObject.EvalOverlaySnapshot evalOverlaySnapshot) {
        if (evalOverlaySnapshot == null) {
            return;
        }
        JSObject globalObject = getGlobalObject();
        for (var entry : evalOverlaySnapshot.values().entrySet()) {
            globalObject.set(PropertyKey.fromString(entry.getKey()), entry.getValue());
        }
        for (String absentKey : evalOverlaySnapshot.absentKeys()) {
            globalObject.delete(PropertyKey.fromString(absentKey));
        }
    }

    public void scheduleClassFieldEvalCall() {
        pendingClassFieldEval = true;
    }

    public void scheduleDirectEvalCall() {
        pendingDirectEvalCalls++;
    }

    /**
     * Set the AsyncFunction constructor (internal use only).
     * Called during global object initialization.
     */
    public void setAsyncFunctionConstructor(JSObject asyncFunctionConstructor) {
        this.asyncFunctionConstructor = asyncFunctionConstructor;
    }

    public void setAsyncGeneratorFunctionPrototype(JSObject asyncGeneratorFunctionPrototype) {
        this.asyncGeneratorFunctionPrototype = asyncGeneratorFunctionPrototype;
    }

    public void setAsyncGeneratorPrototype(JSObject asyncGeneratorPrototype) {
        this.asyncGeneratorPrototype = asyncGeneratorPrototype;
    }

    public void setConstructorNewTarget(JSValue newTarget) {
        this.constructorNewTarget = newTarget;
    }

    /**
     * Set the current 'this' binding.
     */
    public void setCurrentThis(JSValue thisValue) {
        this.currentThis = thisValue != null ? thisValue : jsGlobalObject.getGlobalObject();
    }

    /**
     * Set the GeneratorFunction prototype (internal use only).
     * Called during global object initialization.
     */
    public void setGeneratorFunctionPrototype(JSObject generatorFunctionPrototype) {
        this.generatorFunctionPrototype = generatorFunctionPrototype;
    }

    public void setGlobalFunctionBindingInitializations(Set<String> functionNames, boolean configurable) {
        activeGlobalFunctionBindingConfigurable = configurable;
        activeGlobalFunctionBindingInitializations = functionNames;
    }

    public void setInBareVariableAssignment(boolean value) {
        this.inBareVariableAssignment = value;
    }

    /**
     * Set the maximum stack depth.
     */
    public void setMaxStackDepth(int depth) {
        this.maxStackDepth = depth;
    }

    public void setNativeConstructorNewTarget(JSValue newTarget) {
        this.nativeConstructorNewTarget = newTarget;
    }

    /**
     * Set the pending exception.
     */
    public void setPendingException(JSValue exception) {
        if (!inCatchHandler) {
            this.pendingException = exception;
            captureErrorStackTrace();
        }
    }

    /**
     * Set the promise rejection callback.
     * This callback is invoked when a promise rejection occurs in an await expression.
     * If the callback returns true, the rejection is considered handled and the catch
     * clause will take effect instead of throwing an exception.
     */
    public void setPromiseRejectCallback(IJSPromiseRejectCallback callback) {
        this.promiseRejectCallback = callback;
    }

    public void setRegExpLegacyInput(String inputValue) {
        if (inputValue == null) {
            regExpLegacyInput = "";
        } else {
            regExpLegacyInput = inputValue;
        }
    }

    /**
     * Set the %ThrowTypeError% intrinsic function.
     * Called during global object initialization.
     */
    public void setThrowTypeErrorIntrinsic(JSNativeFunction throwTypeError) {
        this.throwTypeErrorIntrinsic = throwTypeError;
    }

    public void setWaitable(boolean waitable) {
        this.waitable = waitable;
    }

    private int skipWhitespace(String text, int startIndex) {
        int index = Math.max(0, startIndex);
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * Split a string on commas that are not inside quoted strings.
     */
    private List<String> splitOnTopLevelCommas(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (ch == ',' && !inSingleQuote && !inDoubleQuote) {
                result.add(text.substring(start, i));
                start = i + 1;
            }
        }
        result.add(text.substring(start));
        return result;
    }

    private String stripQuotedSpecifier(String text) {
        String specifierText = text.trim();
        if (specifierText.length() < 2) {
            throw new JSException(throwSyntaxError("Invalid module specifier"));
        }
        char firstChar = specifierText.charAt(0);
        if (firstChar != '\'' && firstChar != '"') {
            throw new JSException(throwSyntaxError("Invalid module specifier"));
        }
        // Find the closing quote — everything after (e.g., `with { ... }`) is ignored
        int closeQuote = specifierText.indexOf(firstChar, 1);
        if (closeQuote < 0) {
            throw new JSException(throwSyntaxError("Invalid module specifier"));
        }
        return specifierText.substring(1, closeQuote);
    }

    public JSGlobalObject.EvalOverlaySnapshot suspendEvalOverlays() {
        if (evalOverlayFrames.isEmpty()) {
            return null;
        }
        JSObject globalObject = getGlobalObject();
        Set<String> overlaidKeys = new HashSet<>();
        for (EvalOverlayFrame evalOverlayFrame : evalOverlayFrames) {
            overlaidKeys.addAll(evalOverlayFrame.savedGlobals().keySet());
            overlaidKeys.addAll(evalOverlayFrame.absentKeys());
        }

        Map<String, JSValue> suspendedValues = new HashMap<>();
        Set<String> suspendedAbsentKeys = new HashSet<>();
        for (String key : overlaidKeys) {
            PropertyKey propertyKey = PropertyKey.fromString(key);
            if (globalObject.has(propertyKey)) {
                suspendedValues.put(key, globalObject.get(propertyKey));
            } else {
                suspendedAbsentKeys.add(key);
            }
        }

        Iterator<EvalOverlayFrame> descendingIterator = evalOverlayFrames.descendingIterator();
        while (descendingIterator.hasNext()) {
            EvalOverlayFrame evalOverlayFrame = descendingIterator.next();
            for (var entry : evalOverlayFrame.savedGlobals().entrySet()) {
                PropertyKey overlayKey = PropertyKey.fromString(entry.getKey());
                PropertyDescriptor currentDescriptor = globalObject.getOwnPropertyDescriptor(overlayKey);
                if (currentDescriptor != null
                        && currentDescriptor.isDataDescriptor()
                        && !currentDescriptor.isWritable()) {
                    globalObject.defineProperty(overlayKey, entry.getValue(), PropertyDescriptor.DataState.All);
                } else {
                    globalObject.set(overlayKey, entry.getValue());
                }
            }
            for (String absentKey : evalOverlayFrame.absentKeys()) {
                globalObject.delete(PropertyKey.fromString(absentKey));
            }
        }
        return new JSGlobalObject.EvalOverlaySnapshot(suspendedValues, suspendedAbsentKeys);
    }

    /**
     * Throw a AggregateError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwAggregateError(String message) {
        return throwError(JSAggregateError.NAME, message);
    }

    /**
     * Throw a JavaScript error.
     * Creates an Error object and sets it as the pending exception.
     *
     * @param message Error message
     * @return The error value (for convenience in return statements)
     */
    public JSError throwError(String message) {
        return throwError(JSError.NAME, message);
    }

    /**
     * Throw a JavaScript error of a specific type.
     *
     * @param errorType Error constructor name (Error, TypeError, RangeError, etc.)
     * @param message   Error message
     * @return The error value
     */
    public JSError throwError(String errorType, String message) {
        // Create error object using the proper error class
        JSError jsError = switch (errorType) {
            case JSAggregateError.NAME -> new JSAggregateError(this, message);
            case JSEvalError.NAME -> new JSEvalError(this, message);
            case JSRangeError.NAME -> new JSRangeError(this, message);
            case JSReferenceError.NAME -> new JSReferenceError(this, message);
            case JSSyntaxError.NAME -> new JSSyntaxError(this, message);
            case JSTypeError.NAME -> new JSTypeError(this, message);
            case JSURIError.NAME -> new JSURIError(this, message);
            default -> new JSError(this, message);
        };
        return throwError(jsError);
    }

    public JSError throwError(JSError jsError) {
        transferPrototype(jsError, jsError.getErrorName());
        // Capture stack trace
        captureStackTrace(jsError);
        // Set as pending exception
        setPendingException(jsError);
        return jsError;
    }

    public JSError throwError(JSErrorException jsErrorException) {
        if (jsErrorException == null) {
            return throwError("Unknown error");
        }
        return throwError(jsErrorException.getErrorType().name(), jsErrorException.getMessage());
    }

    /**
     * Throw a EvalError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwEvalError(String message) {
        return throwError(JSEvalError.NAME, message);
    }

    /**
     * Throw a RangeError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwRangeError(String message) {
        return throwError(JSRangeError.NAME, message);
    }

    /**
     * Throw a ReferenceError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwReferenceError(String message) {
        return throwError(JSReferenceError.NAME, message);
    }

    /**
     * Throw a SyntaxError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwSyntaxError(String message) {
        return throwError(JSSyntaxError.NAME, message);
    }

    /**
     * Throw a TypeError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwTypeError(String message) {
        return throwError(JSTypeError.NAME, message);
    }

    /**
     * Throw a URIError.
     *
     * @param message Error message
     * @return The error value
     */
    public JSError throwURIError(String message) {
        return throwError(JSURIError.NAME, message);
    }

    public boolean transferPrototype(JSObject receiver, String constructorName) {
        JSValue constructor = jsGlobalObject.getGlobalObject().get(constructorName);
        if (constructor instanceof JSObject jsObject) {
            return transferPrototype(receiver, jsObject);
        }
        return false;
    }

    public boolean transferPrototype(JSObject receiver, JSObject constructor) {
        JSValue prototype = constructor.get(PropertyKey.PROTOTYPE);
        if (prototype instanceof JSObject) {
            receiver.setPrototype((JSObject) prototype);
            return true;
        }
        return false;
    }

    /**
     * Transfer prototype using Get(constructor, "prototype") with full JS semantics.
     * This is used by constructor paths that must observe accessors and propagate abrupt completions.
     */
    public boolean transferPrototypeFromConstructor(JSObject receiver, JSObject constructor) {
        JSObject prototype = getPrototypeFromConstructor(constructor, JSObject.NAME);
        if (prototype == null) {
            return false;
        }
        receiver.setPrototype(prototype);
        return true;
    }

    private void triggerPendingDependents(JSDynamicImportModule moduleRecord) {
        // ES2024 16.2.1.5.2.4 AsyncModuleExecutionFulfilled / 16.2.1.5.2.5 AsyncModuleExecutionRejected
        // Step 1: Gather all ancestors that are now ready (all async deps resolved)
        List<JSDynamicImportModule> readyModules = new ArrayList<>();
        gatherAvailableAncestors(moduleRecord, readyModules);

        // Step 2: Sort by async evaluation order (the order they were deferred during DFS)
        readyModules.sort(Comparator.comparingInt(JSDynamicImportModule::asyncEvaluationOrder));

        // Step 3: Execute each ready module in order
        for (JSDynamicImportModule ready : readyModules) {
            if (moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
                // Propagate error to dependents
                ready.setEvaluationError(moduleRecord.evaluationError());
                ready.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
                triggerPendingDependents(ready);
                continue;
            }
            try {
                ready.setStatus(JSDynamicImportModule.Status.EVALUATING);
                JSValue evalResult = evaluateDynamicImportModule(ready);
                if (ready.hasTLA() && evalResult instanceof JSPromise asyncPromise) {
                    ready.setAsyncEvaluationOrder(asyncEvaluationOrderCounter++);
                    ready.setStatus(JSDynamicImportModule.Status.EVALUATING_ASYNC);
                    ready.setAsyncEvaluationPromise(asyncPromise);
                    registerAsyncModuleCompletion(ready, asyncPromise, new HashSet<>());
                } else {
                    resolveDynamicImportReExports(ready, new HashSet<>());
                    ready.namespace().finalizeNamespace();
                    ready.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    triggerPendingDependents(ready);
                }
            } catch (JSException jsException) {
                ready.setEvaluationError(jsException.getErrorValue());
                ready.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
                triggerPendingDependents(ready);
            }
        }
    }

    public void updateRegExpLegacyStatics(
            String inputValue,
            String[] captureValues,
            int[][] captureIndices,
            int fallbackStartIndex) {
        String normalizedInput = inputValue != null ? inputValue : "";
        regExpLegacyInput = normalizedInput;

        String matchedText = "";
        if (captureValues != null && captureValues.length > 0 && captureValues[0] != null) {
            matchedText = captureValues[0];
        }

        int inputLength = normalizedInput.length();
        int matchStart = 0;
        int matchEnd = 0;
        if (captureIndices != null
                && captureIndices.length > 0
                && captureIndices[0] != null
                && captureIndices[0].length >= 2) {
            matchStart = Math.max(0, Math.min(inputLength, captureIndices[0][0]));
            matchEnd = Math.max(matchStart, Math.min(inputLength, captureIndices[0][1]));
            if (matchedText.isEmpty() && matchEnd >= matchStart) {
                matchedText = normalizedInput.substring(matchStart, matchEnd);
            }
        } else if (!matchedText.isEmpty()) {
            int normalizedFallbackStart = Math.max(0, fallbackStartIndex);
            int foundIndex = normalizedInput.indexOf(matchedText, normalizedFallbackStart);
            if (foundIndex < 0) {
                foundIndex = normalizedInput.indexOf(matchedText);
            }
            if (foundIndex >= 0) {
                matchStart = foundIndex;
                matchEnd = Math.min(inputLength, foundIndex + matchedText.length());
            }
        }

        regExpLegacyLastMatch = matchedText;
        regExpLegacyLeftContext = normalizedInput.substring(0, matchStart);
        regExpLegacyRightContext = normalizedInput.substring(matchEnd);

        for (int captureIndex = 0; captureIndex < regExpLegacyCaptures.length; captureIndex++) {
            String captureValue = "";
            int captureValueIndex = captureIndex + 1;
            if (captureValues != null
                    && captureValueIndex < captureValues.length
                    && captureValues[captureValueIndex] != null) {
                captureValue = captureValues[captureValueIndex];
            }
            regExpLegacyCaptures[captureIndex] = captureValue;
        }

        String lastParenValue = "";
        if (captureValues != null && captureValues.length > 1) {
            for (int captureIndex = captureValues.length - 1; captureIndex >= 1; captureIndex--) {
                String captureValue = captureValues[captureIndex];
                if (captureValue != null) {
                    lastParenValue = captureValue;
                    break;
                }
            }
        }
        regExpLegacyLastParen = lastParenValue;
    }

    private void validateImportNameAgainstModuleRecord(
            JSDynamicImportModule moduleRecord,
            String importedName) {
        DynamicImportExportResolution resolution = resolveDynamicImportExport(
                moduleRecord,
                importedName,
                new HashSet<>(),
                new HashSet<>());
        if (resolution.ambiguous()) {
            throw new JSSyntaxErrorException(
                    "ambiguous indirect export: " + importedName);
        }
        if (!resolution.found()) {
            throw new JSSyntaxErrorException(
                    "The requested module does not provide an export named '" + importedName + "'");
        }
    }

    private void validateModuleScriptEarlyErrors(String sourceCode) {
        String sourceWithoutComments = sourceCode
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
        Matcher varMatcher = Pattern.compile("\\bvar\\s+([A-Za-z_$][A-Za-z0-9_$]*)").matcher(sourceWithoutComments);
        Set<String> varNames = new HashSet<>();
        while (varMatcher.find()) {
            varNames.add(varMatcher.group(1));
        }
        Matcher functionMatcher = Pattern.compile("\\bfunction\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(")
                .matcher(sourceWithoutComments);
        while (functionMatcher.find()) {
            String functionName = functionMatcher.group(1);
            if (varNames.contains(functionName)) {
                throw new JSException(throwSyntaxError("Identifier '" + functionName + "' has already been declared"));
            }
        }
    }

    /**
     * Validate that all named imports in an import clause exist in the finalized namespace.
     * Throws SyntaxError if any binding is missing (ES2024 linking error).
     */
    private void validateNamedImportBindings(JSImportNamespaceObject namespace, String importClause) {
        String clause = importClause.trim();
        if (clause.isEmpty() || clause.startsWith("*") || clause.startsWith("defer *")) {
            return;
        }
        if (clause.startsWith("{")) {
            validateNamedImportSpecifiers(namespace, clause);
            return;
        }
        int commaIdx = clause.indexOf(',');
        if (commaIdx < 0) {
            PropertyKey defaultKey = PropertyKey.fromString("default");
            if (!namespace.has(defaultKey)) {
                throw new JSSyntaxErrorException(
                        "The requested module does not provide an export named 'default'");
            }
            return;
        }

        String defaultBinding = clause.substring(0, commaIdx).trim();
        if (!defaultBinding.isEmpty()) {
            PropertyKey defaultKey = PropertyKey.fromString("default");
            if (!namespace.has(defaultKey)) {
                throw new JSSyntaxErrorException(
                        "The requested module does not provide an export named 'default'");
            }
        }

        String remainder = clause.substring(commaIdx + 1).trim();
        if (remainder.startsWith("{")) {
            validateNamedImportSpecifiers(namespace, remainder);
        }
    }

    private void validateNamedImportBindingsAgainstExplicitExports(
            JSDynamicImportModule moduleRecord, String importClause) {
        String clause = importClause.trim();
        if (clause.isEmpty() || clause.startsWith("*") || clause.startsWith("defer *")) {
            return;
        }
        if (clause.startsWith("{")) {
            validateNamedImportSpecifiersAgainstModuleRecord(moduleRecord, clause);
            return;
        }
        int commaIdx = clause.indexOf(',');
        if (commaIdx < 0) {
            validateImportNameAgainstModuleRecord(moduleRecord, "default");
            return;
        }

        String defaultBinding = clause.substring(0, commaIdx).trim();
        if (!defaultBinding.isEmpty()) {
            validateImportNameAgainstModuleRecord(moduleRecord, "default");
        }

        String remainder = clause.substring(commaIdx + 1).trim();
        if (remainder.startsWith("{")) {
            validateNamedImportSpecifiersAgainstModuleRecord(moduleRecord, remainder);
        }
    }

    private void validateNamedImportSpecifiers(JSImportNamespaceObject namespace, String namedClause) {
        if (!namedClause.startsWith("{") || !namedClause.endsWith("}")) {
            return;
        }
        String specifiersText = namedClause.substring(1, namedClause.length() - 1).trim();
        if (specifiersText.isEmpty()) {
            return;
        }
        for (String rawSpecifier : splitOnTopLevelCommas(specifiersText)) {
            String specifier = rawSpecifier.trim();
            if (specifier.isEmpty()) {
                continue;
            }
            String importedName;
            int asIndex = findTopLevelAs(specifier);
            if (asIndex >= 0) {
                importedName = parseModuleExportNameValue(specifier.substring(0, asIndex).trim());
            } else {
                importedName = parseModuleExportNameValue(specifier);
            }
            PropertyKey importKey = PropertyKey.fromString(importedName);
            if (!namespace.has(importKey)) {
                throw new JSSyntaxErrorException(
                        "The requested module does not provide an export named '" + importedName + "'");
            }
        }
    }

    private void validateNamedImportSpecifiersAgainstModuleRecord(
            JSDynamicImportModule moduleRecord,
            String namedClause) {
        if (!namedClause.startsWith("{") || !namedClause.endsWith("}")) {
            return;
        }
        String specifiersText = namedClause.substring(1, namedClause.length() - 1).trim();
        if (specifiersText.isEmpty()) {
            return;
        }
        for (String rawSpecifier : splitOnTopLevelCommas(specifiersText)) {
            String specifier = rawSpecifier.trim();
            if (specifier.isEmpty()) {
                continue;
            }
            String importedName;
            int asIndex = findTopLevelAs(specifier);
            if (asIndex >= 0) {
                importedName = parseModuleExportNameValue(specifier.substring(0, asIndex).trim());
            } else {
                importedName = parseModuleExportNameValue(specifier);
            }
            validateImportNameAgainstModuleRecord(moduleRecord, importedName);
        }
    }

    public void writeGlobalLexicalBinding(String name, JSValue value) {
        globalLexicalBindings.put(name, value);
    }

    private record DynamicImportExportResolution(
            JSDynamicImportModule moduleRecord,
            String bindingName,
            boolean ambiguous) {
        private static DynamicImportExportResolution ambiguousResolution() {
            return new DynamicImportExportResolution(null, null, true);
        }

        private static DynamicImportExportResolution notFoundResolution() {
            return new DynamicImportExportResolution(null, null, false);
        }

        private static DynamicImportExportResolution resolvedResolution(
                JSDynamicImportModule moduleRecord,
                String bindingName) {
            return new DynamicImportExportResolution(moduleRecord, bindingName, false);
        }

        private boolean found() {
            return moduleRecord != null;
        }
    }

    private record EvalOverlayFrame(Map<String, JSValue> savedGlobals, Set<String> absentKeys) {
    }

    private record ImportBinding(String sourceSpecifier, String importedName, boolean deferredImport) {
    }


}
