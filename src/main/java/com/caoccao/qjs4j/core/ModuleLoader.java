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

import com.caoccao.qjs4j.exceptions.JSCompilerException;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSSyntaxErrorException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;

/**
 * Loads modules, caches them, and decides when each one's body may run.
 * <p>
 * It owns the realm's module cache and the {@code import.meta} objects that go with it, resolves a
 * specifier against its referrer, and reads the three kinds of payload the engine understands:
 * JavaScript source, JSON, and the {@code type: 'text'} / {@code type: 'bytes'} attributes, whose
 * single {@code default} export it manufactures without compiling anything.
 * <p>
 * The harder half is ordering. A module whose body contains a top-level {@code await} finishes
 * asynchronously, and everything that imports it has to wait — so a dependent registers itself on
 * the modules it is waiting for, an {@code asyncEvaluationOrder} is stamped on each one as it is
 * deferred, and {@link #triggerPendingDependents} runs the ones that become ready in that order.
 * This is ES2024 16.2.1.5.2's AsyncModuleExecutionFulfilled / GatherAvailableAncestors, and
 * {@code import defer} adds ReadyForSyncExecution on top of it.
 */
final class ModuleLoader {
    private final JSContext context;
    private final Map<String, JSDynamicImportModule> dynamicImportModuleCache = new HashMap<>();
    private final Map<String, JSObject> importMetaCache = new HashMap<>();
    private final ModuleLinker linker;
    private final ModuleSourceTransformer transformer;
    // Counter for tracking the order modules have their async evaluation set
    private int asyncEvaluationOrderCounter;

    ModuleLoader(JSContext context, ModuleSourceTransformer transformer, ModuleLinker linker) {
        this.context = context;
        this.transformer = transformer;
        this.linker = linker;
        this.asyncEvaluationOrderCounter = 0;
    }

    /**
     * Put a module record in the cache.
     * <p>
     * Package-private so the eval pipeline can register the module it is about to evaluate, which
     * is what lets a self-import see the re-entrancy.
     *
     * @param moduleCacheKey the resolved specifier, possibly qualified by an import type
     * @param moduleRecord   the record to cache
     */
    void cacheModule(String moduleCacheKey, JSDynamicImportModule moduleRecord) {
        dynamicImportModuleCache.put(moduleCacheKey, moduleRecord);
    }

    /**
     * The module record the dynamic-import cache holds for a key, or null.
     * <p>
     * Package-private so {@link ModuleLinker} can look a module up without owning the cache.
     *
     * @param moduleCacheKey the resolved specifier, possibly qualified by an import type
     * @return the cached record, or null when there is none
     */
    JSDynamicImportModule cachedModule(String moduleCacheKey) {
        return dynamicImportModuleCache.get(moduleCacheKey);
    }

    void chainImportPromiseOntoAsyncDependencies(
            List<JSPromise> dependencyPromises,
            JSObject namespace,
            JSPromise importPromise,
            JSPromise.ResolveState resolveState) {
        int[] remaining = new int[]{dependencyPromises.size()};
        for (JSPromise dependencyPromise : dependencyPromises) {
            JSNativeFunction onFulfill = new JSNativeFunction(context, "", 0,
                    (ctx, thisArg, args) -> {
                        remaining[0]--;
                        if (remaining[0] == 0 && !resolveState.alreadyResolved) {
                            resolveState.alreadyResolved = true;
                            importPromise.resolve(ctx, namespace);
                        }
                        return JSUndefined.INSTANCE;
                    });
            onFulfill.initializePrototypeChain(context);
            JSNativeFunction onReject = new JSNativeFunction(context, "", 1,
                    (ctx, thisArg, args) -> {
                        if (!resolveState.alreadyResolved) {
                            resolveState.alreadyResolved = true;
                            JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                            importPromise.reject(reason);
                        }
                        return JSUndefined.INSTANCE;
                    });
            onReject.initializePrototypeChain(context);
            dependencyPromise.addReactions(
                    new JSPromise.ReactionRecord(onFulfill, context, null, null),
                    new JSPromise.ReactionRecord(onReject, context, null, null));
        }
    }

    void chainImportPromiseOntoAsyncModule(
            JSDynamicImportModule moduleRecord,
            JSPromise importPromise,
            JSPromise.ResolveState resolveState) {
        JSObject namespace = moduleRecord.namespace();
        JSNativeFunction onFulfill = new JSNativeFunction(context, "", 0,
                (ctx, thisArg, args) -> {
                    if (!resolveState.alreadyResolved) {
                        resolveState.alreadyResolved = true;
                        importPromise.resolve(ctx, namespace);
                    }
                    return JSUndefined.INSTANCE;
                });
        onFulfill.initializePrototypeChain(context);
        JSNativeFunction onReject = new JSNativeFunction(context, "", 1,
                (ctx, thisArg, args) -> {
                    if (!resolveState.alreadyResolved) {
                        resolveState.alreadyResolved = true;
                        JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                        importPromise.reject(reason);
                    }
                    return JSUndefined.INSTANCE;
                });
        onReject.initializePrototypeChain(context);
        moduleRecord.asyncEvaluationPromise().addReactions(
                new JSPromise.ReactionRecord(onFulfill, context, null, null),
                new JSPromise.ReactionRecord(onReject, context, null, null));
    }

    /**
     * Clear the module cache.
     */
    void clearModuleCache() {
        dynamicImportModuleCache.clear();
        importMetaCache.clear();
    }

    JSObject createImportMetaObject(String filename) {
        String cacheKey = filename != null ? filename : "";
        JSObject importMetaObject = importMetaCache.get(cacheKey);
        if (importMetaObject == null) {
            importMetaObject = new JSObject(context);
            importMetaObject.setPrototype(null);
            if (filename != null && !filename.isEmpty() && !filename.startsWith("<")) {
                importMetaObject.set(PropertyKey.fromString("url"), new JSString(filename));
            }
            importMetaCache.put(cacheKey, importMetaObject);
        }
        return importMetaObject;
    }

    JSImportNamespaceObject createModuleNamespaceObject() {
        return new JSImportNamespaceObject(context);
    }

    JSValue evaluateDynamicImportModule(JSDynamicImportModule moduleRecord) {
        if (!moduleRecord.hasExportSyntax()) {
            linker.validateModuleScriptEarlyErrors(moduleRecord.rawSource());
            return context.eval(moduleRecord.transformedSource(), moduleRecord.resolvedSpecifier(), true);
        }
        String exportBindingName = moduleRecord.exportBindingName();
        JSObject globalObject = context.getGlobalObject();
        JSObject moduleNamespace = moduleRecord.namespace();
        globalObject.set(PropertyKey.fromString(exportBindingName), moduleNamespace);
        try {
            String transformedSource = moduleRecord.transformedSource();
            return context.eval(transformedSource, moduleRecord.resolvedSpecifier(), true);
        } finally {
            globalObject.delete(PropertyKey.fromString(exportBindingName));
        }
    }

    /**
     * ES2024 16.2.1.5.2.4 GatherAvailableAncestors.
     * Collects all ancestor modules whose pending async dependencies have all resolved.
     */
    void gatherAvailableAncestors(JSDynamicImportModule module,
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

    void gatherDeferredAsyncDependencySpecifiers(
            String resolvedSpecifier,
            String sourceCode,
            Set<String> visitedSpecifiers,
            Set<String> asyncDependencySpecifiers) {
        if (!visitedSpecifiers.add(resolvedSpecifier)) {
            return;
        }
        String scanSourceCode = transformer.maskModuleComments(sourceCode);
        if (ModuleSourceTransformer.MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(scanSourceCode).find()) {
            asyncDependencySpecifiers.add(resolvedSpecifier);
            return;
        }
        Matcher matcher = ModuleSourceTransformer.MODULE_STATIC_IMPORT_PATTERN.matcher(scanSourceCode);
        while (matcher.find()) {
            String childSpecifier = transformer.decodeModuleStringLiteralValue(matcher.group(1));
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
                throw new JSException(context.throwTypeError("Cannot find module '" + childSpecifier + "'"));
            }
            gatherDeferredAsyncDependencySpecifiers(
                    resolvedChildSpecifier,
                    childSourceCode,
                    visitedSpecifiers,
                    asyncDependencySpecifiers);
        }
    }

    String getDynamicImportCacheKey(String resolvedSpecifier, Map<String, String> importAttributes) {
        if (importAttributes == null) {
            return resolvedSpecifier;
        }
        String importType = importAttributes.get("type");
        if ("text".equals(importType) || "bytes".equals(importType)) {
            return resolvedSpecifier + "\u0000type=" + importType;
        }
        return resolvedSpecifier;
    }

    List<JSPromise> getEvaluatingAsyncDependencyPromises(JSDynamicImportModule moduleRecord) {
        String scanSource = transformer.maskModuleComments(moduleRecord.rawSource());
        Matcher matcher = ModuleSourceTransformer.MODULE_STATIC_IMPORT_PATTERN.matcher(scanSource);
        List<JSPromise> dependencyPromises = new ArrayList<>();
        Set<String> seenSpecifiers = new HashSet<>();
        JSDynamicImportModule moduleCycleRoot =
                moduleRecord.cycleRoot() != null ? moduleRecord.cycleRoot() : moduleRecord;
        while (matcher.find()) {
            String specifier = transformer.decodeModuleStringLiteralValue(matcher.group(1));
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
                // Skip unresolvable specifiers. Discarding the Java exception is not enough: the
                // matching throwTypeError also left a pending exception on the context, and a
                // later activation would otherwise pick up that stale error.
                context.clearPendingException();
            }
        }
        return dependencyPromises;
    }

    boolean hasEvaluatingAsyncDependency(JSDynamicImportModule moduleRecord) {
        String scanSource = transformer.maskModuleComments(moduleRecord.rawSource());
        Matcher matcher = ModuleSourceTransformer.MODULE_STATIC_IMPORT_PATTERN.matcher(scanSource);
        while (matcher.find()) {
            String specifier = transformer.decodeModuleStringLiteralValue(matcher.group(1));
            try {
                String resolved = resolveDynamicImportSpecifier(
                        specifier, moduleRecord.resolvedSpecifier(), specifier);
                JSDynamicImportModule depRecord = dynamicImportModuleCache.get(resolved);
                if (depRecord != null
                        && depRecord.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC) {
                    return true;
                }
            } catch (JSException ignored) {
                // Skip unresolvable specifiers. Clear the pending exception the matching
                // throwTypeError left behind, so the context does not stay in an exception state.
                context.clearPendingException();
            }
        }
        return false;
    }

    /**
     * Whether the source about to be evaluated is a module's own generated source rather than the
     * text an author wrote.
     * <p>
     * A module with exports is evaluated by handing its rewritten source back to {@code eval} under
     * the same file name, and in that text the {@code export} declarations have already become
     * ordinary assignments. Anything that reads it as a module therefore sees a module that
     * exports nothing.
     *
     * @param code     the source about to be evaluated
     * @param filename the name it is being evaluated under
     * @return true when this is a cached module's transformed source
     */
    boolean isTransformedModuleSource(String code, String filename) {
        if (filename == null || filename.isEmpty() || filename.startsWith("<")) {
            return false;
        }
        String resolvedSpecifier;
        try {
            resolvedSpecifier = resolveDynamicImportSpecifier(filename, null, filename);
        } catch (JSException unresolvable) {
            context.clearPendingException();
            resolvedSpecifier = normalizeModuleSpecifier(filename);
        }
        JSDynamicImportModule moduleRecord = dynamicImportModuleCache.get(resolvedSpecifier);
        return moduleRecord != null
                && !Objects.equals(moduleRecord.rawSource(), code)
                && Objects.equals(moduleRecord.transformedSource(), code);
    }

    JSObject loadDynamicImportModule(String specifier, String referrerFilename) {
        return loadDynamicImportModule(specifier, referrerFilename, null);
    }

    JSObject loadDynamicImportModule(
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
    JSObject loadDynamicImportModule(
            String specifier,
            String referrerFilename,
            Map<String, String> importAttributes,
            JSPromise importPromise,
            JSPromise.ResolveState resolveState) {
        // Everything evaluated from here down is a module pulled in to satisfy an import,
        // not the module the host asked for. See getImportedModuleBodyEvaluationCount().
        boolean previouslyEvaluatingImportedModule = context.isEvaluatingImportedModule();
        context.setEvaluatingImportedModule(true);
        try {
            return loadDynamicImportModuleInternal(
                    specifier, referrerFilename, importAttributes, importPromise, resolveState);
        } finally {
            context.setEvaluatingImportedModule(previouslyEvaluatingImportedModule);
        }
    }

    JSObject loadDynamicImportModuleDeferred(
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
    JSObject loadDynamicImportModuleDeferred(
            String specifier,
            String referrerFilename,
            Map<String, String> importAttributes,
            JSPromise importPromise,
            JSPromise.ResolveState resolveState) {
        // Everything evaluated from here down is a module pulled in to satisfy an import,
        // not the module the host asked for. See getImportedModuleBodyEvaluationCount().
        boolean previouslyEvaluatingImportedModule = context.isEvaluatingImportedModule();
        context.setEvaluatingImportedModule(true);
        try {
            return loadDynamicImportModuleDeferredInternal(
                    specifier, referrerFilename, importAttributes, importPromise, resolveState);
        } finally {
            context.setEvaluatingImportedModule(previouslyEvaluatingImportedModule);
        }
    }

    JSObject loadDynamicImportModuleDeferredInternal(
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
                    // The payload is data, not source. Putting it through the module normaliser
                    // meant a text file whose bytes happened to look like a declaration was
                    // tokenised and compiled as JavaScript, so `export {}; this is arbitrary text`
                    // was a SyntaxError instead of a string.
                    String sourceCode = Files.readString(Path.of(resolvedSpecifier));
                    moduleRecord.setRawSource(sourceCode);
                    context.importBindingInstaller().defineDynamicImportNamespaceValue(moduleRecord, "default", new JSString(sourceCode));
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
                    JSArrayBuffer arrayBuffer = new JSArrayBuffer(context, fileBytes);
                    context.transferPrototype(arrayBuffer, JSArrayBuffer.NAME);
                    arrayBuffer.setImmutable(true);
                    JSUint8Array uint8Array = context.createJSUint8Array(arrayBuffer, 0, fileBytes.length);
                    context.importBindingInstaller().defineDynamicImportNamespaceValue(moduleRecord, "default", uint8Array);
                    moduleRecord.explicitExportNames().add("default");
                    moduleRecord.exportOrigins().put("default", resolvedSpecifier);
                    moduleRecord.namespace().finalizeNamespace();
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    return moduleRecord.namespace();
                }
                String sourceCode = Files.readString(Path.of(resolvedSpecifier));
                moduleRecord.setRawSource(transformer.normalizeModuleDeclarationLines(sourceCode));
                if (resolvedSpecifier.endsWith(".json")) {
                    if (!"json".equals(importType)) {
                        throw new JSException(context.throwTypeError("Import attribute type must be 'json'"));
                    }
                    JSValue jsonDefaultValue = parseJsonModuleSource(sourceCode);
                    context.importBindingInstaller().defineDynamicImportNamespaceValue(moduleRecord, "default", jsonDefaultValue);
                    moduleRecord.explicitExportNames().add("default");
                    moduleRecord.exportOrigins().put("default", resolvedSpecifier);
                    moduleRecord.namespace().finalizeNamespace();
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    return moduleRecord.namespace();
                }
                transformer.parseDynamicImportModuleSource(moduleRecord);
                // Eagerly validate syntax of deferred modules per spec.
                // SyntaxErrors are not deferred — they must be detected at linking time.
                transformer.requireDependencyModuleSourceCompiles(sourceCode, resolvedSpecifier);
            } catch (IOException ioException) {
                dynamicImportModuleCache.remove(moduleCacheKey);
                throw new JSException(context.throwTypeError("Cannot find module '" + resolvedSpecifier + "'"));
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
                moduleRecord.setDeferredNamespace(new JSDeferredModuleNamespace(context, moduleRecord));
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
        boolean prevSuppress = context.isSuppressingEvalMicrotasks();
        context.setSuppressingEvalMicrotasks(true);
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
                        linker.resolveDynamicImportReExports(moduleRecord, new HashSet<>());
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
            context.setSuppressingEvalMicrotasks(prevSuppress);
        }

        if (moduleRecord.deferredNamespace() == null) {
            moduleRecord.setDeferredNamespace(new JSDeferredModuleNamespace(context, moduleRecord));
        }

        // When called from dynamic import.defer() (importPromise != null) and there are
        // pending TLA evaluation promises, chain the import promise resolution onto them
        // using a Promise.all-like counter. This avoids relying on context.processMicrotasks()
        // which is a no-op when called re-entrantly from within a microtask.
        if (importPromise != null && !tlaEvaluationPromises.isEmpty()) {
            JSObject deferredNs = moduleRecord.deferredNamespace();
            int[] remaining = {tlaEvaluationPromises.size()};
            for (JSPromise tlaPromise : tlaEvaluationPromises) {
                JSNativeFunction onFulfill = new JSNativeFunction(context, "", 0,
                        (ctx, thisArg, args) -> {
                            remaining[0]--;
                            if (remaining[0] == 0 && !resolveState.alreadyResolved) {
                                resolveState.alreadyResolved = true;
                                importPromise.resolve(ctx, deferredNs);
                            }
                            return JSUndefined.INSTANCE;
                        });
                onFulfill.initializePrototypeChain(context);
                JSNativeFunction onReject = new JSNativeFunction(context, "", 1,
                        (ctx, thisArg, args) -> {
                            if (!resolveState.alreadyResolved) {
                                resolveState.alreadyResolved = true;
                                JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                                importPromise.reject(reason);
                            }
                            return JSUndefined.INSTANCE;
                        });
                onReject.initializePrototypeChain(context);
                tlaPromise.addReactions(
                        new JSPromise.ReactionRecord(onFulfill, context, null, null),
                        new JSPromise.ReactionRecord(onReject, context, null, null));
            }
            return null; // Import promise will be resolved via TLA promise chain
        }

        // Static import defer case: drain microtasks to complete EVALUATING_ASYNC modules.
        // Only drain when not called from evaluateModuleImportsInOrder
        // (which has its own drain after all imports are processed).
        if (!context.isSuppressingEvalMicrotasks() && !asyncDependencySpecifiers.isEmpty()) {
            context.processMicrotasks();
        }

        return moduleRecord.deferredNamespace();
    }

    JSObject loadDynamicImportModuleInternal(
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
                linker.resolveDynamicImportReExports(preloaded, new HashSet<>());
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

    JSDynamicImportModule loadJSDynamicImportModule(
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
                // Data, not source — see the deferred path above.
                String sourceCode = Files.readString(Path.of(resolvedSpecifier));
                moduleRecord.setRawSource(sourceCode);
                context.importBindingInstaller().defineDynamicImportNamespaceValue(moduleRecord, "default", new JSString(sourceCode));
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
                JSArrayBuffer arrayBuffer = new JSArrayBuffer(context, fileBytes);
                context.transferPrototype(arrayBuffer, JSArrayBuffer.NAME);
                arrayBuffer.setImmutable(true);
                JSUint8Array uint8Array = context.createJSUint8Array(arrayBuffer, 0, fileBytes.length);
                context.importBindingInstaller().defineDynamicImportNamespaceValue(moduleRecord, "default", uint8Array);
                moduleRecord.explicitExportNames().add("default");
                moduleRecord.exportOrigins().put("default", resolvedSpecifier);
                moduleRecord.namespace().finalizeNamespace();
                moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                return moduleRecord;
            }
            String sourceCode = Files.readString(Path.of(resolvedSpecifier));
            moduleRecord.setRawSource(transformer.normalizeModuleDeclarationLines(sourceCode));
            if (resolvedSpecifier.endsWith(".json")) {
                if (!"json".equals(importType)) {
                    throw new JSException(context.throwTypeError("Import attribute type must be 'json'"));
                }
                JSValue jsonDefaultValue = parseJsonModuleSource(sourceCode);
                context.importBindingInstaller().defineDynamicImportNamespaceValue(moduleRecord, "default", jsonDefaultValue);
                moduleRecord.explicitExportNames().add("default");
                moduleRecord.exportOrigins().put("default", resolvedSpecifier);
                moduleRecord.namespace().finalizeNamespace();
                moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                return moduleRecord;
            }
            // The dependency's own early errors, raised against the dependency's own text. Without
            // this they surfaced only once the file had become generated module code, so a
            // duplicate `__proto__` at offset 33 of a 53-character file was reported at offset 228.
            transformer.requireDependencyModuleSourceCompiles(sourceCode, resolvedSpecifier);
            transformer.parseDynamicImportModuleSource(moduleRecord);
            linker.resolveDynamicImportReExports(moduleRecord, importResolutionStack);
            // Pre-load all static imports so we can detect EVALUATING_ASYNC dependencies.
            // Without this, a module's deps aren't loaded until eval() → evaluateModuleImportsInOrder,
            // which is too late for the hasEvaluatingAsyncDependency check.
            if (context.isSuppressingEvalMicrotasks()) {
                preloadStaticImports(moduleRecord, importResolutionStack, importAttributes);
            }
            if (context.isSuppressingEvalMicrotasks()
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
                boolean prevSuppress = context.isSuppressingEvalMicrotasks();
                context.setSuppressingEvalMicrotasks(true);
                JSValue evalResult;
                try {
                    evalResult = evaluateDynamicImportModule(moduleRecord);
                } finally {
                    context.setSuppressingEvalMicrotasks(prevSuppress);
                }
                if (evalResult instanceof JSPromise asyncPromise) {
                    moduleRecord.setAsyncEvaluationOrder(asyncEvaluationOrderCounter++);
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING_ASYNC);
                    moduleRecord.setAsyncEvaluationPromise(asyncPromise);
                    registerAsyncModuleCompletion(moduleRecord, asyncPromise, importResolutionStack);
                    if (!context.isSuppressingEvalMicrotasks()) {
                        // Not in a suppressed context — drain microtasks now to
                        // let the async module complete before returning.
                        context.processMicrotasks();
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
            // The record was registered before the payload was read, and the read never happened —
            // so there is no module here, only a specifier that resolved to something unreadable, a
            // directory being the ordinary case. Leaving the record behind makes the next import of
            // the same specifier find it, see LOADING, and answer with a namespace nothing ever
            // populated, rather than failing the way the first one did. The deferred path above
            // already evicts for the same reason.
            dynamicImportModuleCache.remove(moduleCacheKey);
            throw new JSException(context.throwTypeError("Cannot find module '" + resolvedSpecifier + "'"));
        } catch (JSSyntaxErrorException syntaxErrorException) {
            JSValue error = context.throwSyntaxError(
                    syntaxErrorException.getMessage(), syntaxErrorException.getSourceLocation());
            moduleRecord.setEvaluationError(error);
            moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
            throw new JSException(error);
        } catch (JSCompilerException compilerException) {
            JSValue error = context.throwSyntaxError(
                    compilerException.getMessage(), compilerException.getSourceLocation());
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
            throw new JSException(context.throwError(exception.getMessage() != null ? exception.getMessage() : "Module load error"));
        }
    }

    /**
     * How many modules the dynamic-import cache holds.
     * <p>
     * Read by {@link ModuleSourceTransformer} when it invents a name for a module's generated
     * export binding, so two modules transformed in one realm cannot collide on it.
     *
     * @return the number of cached module records
     */
    int moduleCacheSize() {
        return dynamicImportModuleCache.size();
    }

    /**
     * The order stamp for the next module to be deferred by an asynchronous dependency.
     * <p>
     * ES2024 16.2.1.5.2 gives every module deferred during the depth-first evaluation an
     * {@code [[AsyncEvaluationOrder]]}, and {@link #triggerPendingDependents} runs the ones that
     * become ready in that order.
     *
     * @return the next order stamp
     */
    int nextAsyncEvaluationOrder() {
        return asyncEvaluationOrderCounter++;
    }

    String normalizeModuleSpecifier(String specifier) {
        if (specifier == null || specifier.isEmpty()) {
            return "";
        }
        try {
            return Paths.get(specifier).normalize().toString();
        } catch (InvalidPathException invalidPathException) {
            return specifier;
        }
    }

    JSValue parseJsonModuleSource(String sourceCode) {
        JSValue jsonValue = context.getGlobalObject().get(PropertyKey.fromString("JSON"));
        if (context.hasPendingException()) {
            JSValue error = context.getPendingException();
            context.clearPendingException();
            throw new JSException(error);
        }
        if (!(jsonValue instanceof JSObject jsonObject)) {
            throw new JSException(context.throwTypeError("JSON is not an object"));
        }

        JSValue parseValue = jsonObject.get(PropertyKey.fromString("parse"));
        if (context.hasPendingException()) {
            JSValue error = context.getPendingException();
            context.clearPendingException();
            throw new JSException(error);
        }

        JSValue[] parseArguments = new JSValue[]{new JSString(sourceCode)};
        JSValue parsedValue;
        if (parseValue instanceof JSFunction parseFunction) {
            parsedValue = parseFunction.call(context, jsonObject, parseArguments);
        } else if (parseValue instanceof JSProxy parseProxy) {
            parsedValue = parseProxy.apply(context, jsonObject, parseArguments);
        } else {
            throw new JSException(context.throwTypeError("JSON.parse is not a function"));
        }
        if (context.hasPendingException()) {
            JSValue error = context.getPendingException();
            context.clearPendingException();
            throw new JSException(error);
        }
        return parsedValue;
    }

    /**
     * Pre-load all static imports of a module so that EVALUATING_ASYNC dependencies
     * are discovered before we decide whether to defer or evaluate the module.
     */

    void preloadStaticImports(JSDynamicImportModule moduleRecord,
                              Set<String> importResolutionStack,
                              Map<String, String> importAttributes) {
        String scanSource = transformer.maskModuleComments(moduleRecord.rawSource());
        Matcher matcher = ModuleSourceTransformer.MODULE_STATIC_IMPORT_PATTERN.matcher(scanSource);
        while (matcher.find()) {
            // Skip import defer statements — deferred modules must not be eagerly evaluated
            String fullMatch = matcher.group(0).stripLeading();
            if (fullMatch.startsWith("import") && fullMatch.length() > 6) {
                String afterImport = fullMatch.substring(6).stripLeading();
                if (afterImport.startsWith("defer")) {
                    continue;
                }
            }
            String specifier = transformer.decodeModuleStringLiteralValue(matcher.group(1));
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
                // Skip unresolvable specifiers. Clear the pending exception the matching
                // throwTypeError left behind, so the context does not stay in an exception state.
                context.clearPendingException();
            }
        }
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
        String scanSourceCode = transformer.maskModuleComments(sourceCode);
        if (ModuleSourceTransformer.MODULE_TOP_LEVEL_AWAIT_PATTERN.matcher(scanSourceCode).find()) {
            return false;
        }
        Matcher matcher = ModuleSourceTransformer.MODULE_STATIC_IMPORT_PATTERN.matcher(scanSourceCode);
        while (matcher.find()) {
            String childSpecifier = transformer.decodeModuleStringLiteralValue(matcher.group(1));
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

    void registerAsyncModuleCompletion(
            JSDynamicImportModule moduleRecord,
            JSPromise asyncPromise,
            Set<String> importResolutionStack) {
        JSNativeFunction onFulfill = new JSNativeFunction(context, "onFulfill", 0,
                (ctx, thisArg, args) -> {
                    linker.resolveDynamicImportReExports(moduleRecord, new HashSet<>());
                    moduleRecord.namespace().finalizeNamespace();
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
                    triggerPendingDependents(moduleRecord);
                    return JSUndefined.INSTANCE;
                });
        onFulfill.initializePrototypeChain(context);
        JSNativeFunction onReject = new JSNativeFunction(context, "onReject", 1,
                (ctx, thisArg, args) -> {
                    JSValue error = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                    // A top-level-await body that finished and then rejected still failed while
                    // the graph was being evaluated. Counting it is what lets a host tell that
                    // apart from a graph that never got past linking — see
                    // hasModuleBodyEvaluationFailed().
                    context.recordFailedModuleBodyEvaluation();
                    moduleRecord.setEvaluationError(error);
                    moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
                    triggerPendingDependents(moduleRecord);
                    return JSUndefined.INSTANCE;
                });
        onReject.initializePrototypeChain(context);
        asyncPromise.addReactions(
                new JSPromise.ReactionRecord(onFulfill, context, null, null),
                new JSPromise.ReactionRecord(onReject, context, null, null));
    }

    void registerPendingDependent(JSDynamicImportModule moduleRecord) {
        String scanSource = transformer.maskModuleComments(moduleRecord.rawSource());
        Matcher matcher = ModuleSourceTransformer.MODULE_STATIC_IMPORT_PATTERN.matcher(scanSource);
        int asyncDepCount = 0;
        Set<String> registeredOnSpecifiers = new HashSet<>();
        // Determine this module's effective cycle root for same-cycle detection.
        JSDynamicImportModule moduleCycleRoot =
                moduleRecord.cycleRoot() != null ? moduleRecord.cycleRoot() : moduleRecord;
        while (matcher.find()) {
            String specifier = transformer.decodeModuleStringLiteralValue(matcher.group(1));
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
                // Skip unresolvable specifiers. Clear the pending exception the matching
                // throwTypeError left behind, so the context does not stay in an exception state.
                context.clearPendingException();
            }
        }
        moduleRecord.setPendingAsyncDependencyCount(asyncDepCount);
    }

    /**
     * Drop a module record from the cache.
     * <p>
     * Package-private so the eval pipeline can evict the record it registered when the evaluation
     * it registered it for did not complete.
     *
     * @param moduleCacheKey the resolved specifier, possibly qualified by an import type
     */
    void removeCachedModule(String moduleCacheKey) {
        dynamicImportModuleCache.remove(moduleCacheKey);
    }

    String resolveDynamicImportSpecifier(
            String specifier,
            String referrerFilename,
            String errorSpecifier) {
        final Path rawSpecifierPath;
        try {
            rawSpecifierPath = Paths.get(specifier);
        } catch (InvalidPathException invalidPathException) {
            throw new JSException(context.throwTypeError("Cannot find module '" + errorSpecifier + "'"));
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
            throw new JSException(context.throwTypeError("Cannot find module '" + errorSpecifier + "'"));
        }
        return resolvedPath.toString();
    }

    void triggerPendingDependents(JSDynamicImportModule moduleRecord) {
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
                    linker.resolveDynamicImportReExports(ready, new HashSet<>());
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
}
