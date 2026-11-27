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

import com.caoccao.qjs4j.core.module.ModuleSourceScan;
import com.caoccao.qjs4j.core.module.ModuleSourceText;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.exceptions.JSException;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;

/** Orders modules waiting on top-level await and settles the dynamic imports that depend on them. */
final class AsyncModuleEvaluation {
    private int asyncEvaluationOrderCounter;
    private final JSContext context;
    private final ModuleLoader loader;

    AsyncModuleEvaluation(JSContext context, ModuleLoader loader) {
        this.context = context;
        this.loader = loader;
    }

    void awaitCompletion(JSDynamicImportModule moduleRecord, JSPromise asyncPromise) {
        moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING_ASYNC);
        moduleRecord.setAsyncEvaluationPromise(asyncPromise);
        JSNativeFunction onFulfill = new JSNativeFunction(context, "onFulfill", 0, (ctx, thisArg, args) -> {
            loader.finishLinkedModule(moduleRecord);
            triggerPendingDependents(moduleRecord);
            return JSUndefined.INSTANCE;
        });
        onFulfill.initializePrototypeChain(context);
        JSNativeFunction onReject = new JSNativeFunction(context, "onReject", 1, (ctx, thisArg, args) -> {
            JSValue error = JSValue.argumentOrUndefined(args, 0);
            // A top-level-await body that finished and then rejected still failed while
            // the graph was being evaluated. Counting it is what lets a host tell that
            // apart from a graph that never got past linking — see
            // hasModuleBodyEvaluationFailed().
            context.recordFailedModuleBodyEvaluation();
            moduleRecord.failEvaluation(error);
            triggerPendingDependents(moduleRecord);
            return JSUndefined.INSTANCE;
        });
        onReject.initializePrototypeChain(context);
        asyncPromise.addReactions(new JSPromise.ReactionRecord(onFulfill, context, null, null),
                new JSPromise.ReactionRecord(onReject, context, null, null));
    }

    private JSDynamicImportModule cachedDependency(JSDynamicImportModule module, Matcher matcher) {
        String specifier = ModuleSourceText.decodeModuleStringLiteralValue(matcher.group(1));
        try {
            String resolved = loader.resolveDynamicImportSpecifier(specifier, module.resolvedSpecifier(), specifier);
            return loader.cachedModule(resolved);
        } catch (JSException ignored) {
            // Resolution also sets a pending exception. This scan skips unresolvable requests.
            context.clearPendingException();
            return null;
        }
    }

    void chainImportPromiseOntoAsyncDependencies(List<JSPromise> dependencyPromises, JSObject namespace,
            JSPromise importPromise, JSPromise.ResolveState resolveState) {
        int[] remaining = {dependencyPromises.size()};
        JSNativeFunction onFulfill = new JSNativeFunction(context, "", 0, (ctx, thisArg, args) -> {
            if (--remaining[0] == 0) {
                resolveImportPromise(ctx, namespace, importPromise, resolveState);
            }
            return JSUndefined.INSTANCE;
        });
        onFulfill.initializePrototypeChain(context);
        JSNativeFunction onReject = importRejectionHandler(importPromise, resolveState);
        for (JSPromise dependencyPromise : dependencyPromises) {
            dependencyPromise.observe(context, onFulfill, onReject);
        }
    }

    void chainImportPromiseOntoAsyncModule(JSDynamicImportModule moduleRecord, JSPromise importPromise,
            JSPromise.ResolveState resolveState) {
        JSObject namespace = moduleRecord.namespace();
        JSNativeFunction onFulfill = new JSNativeFunction(context, "", 0, (ctx, thisArg, args) -> {
            resolveImportPromise(ctx, namespace, importPromise, resolveState);
            return JSUndefined.INSTANCE;
        });
        onFulfill.initializePrototypeChain(context);
        moduleRecord.asyncEvaluationPromise().observe(context, onFulfill,
                importRejectionHandler(importPromise, resolveState));
    }

    /**
     * ES2024 16.2.1.5.2.4 GatherAvailableAncestors. Collects all ancestor modules whose pending async dependencies have
     * all resolved.
     */
    void gatherAvailableAncestors(JSDynamicImportModule module, List<JSDynamicImportModule> execList) {
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

    List<JSPromise> getEvaluatingAsyncDependencyPromises(JSDynamicImportModule module) {
        List<JSPromise> promises = new ArrayList<>();
        visitAsyncDependencies(module, true, dependency -> promises.add(dependency.asyncEvaluationPromise()));
        return promises;
    }

    boolean hasEvaluatingAsyncDependency(JSDynamicImportModule moduleRecord) {
        Matcher matcher = staticImports(moduleRecord);
        while (matcher.find()) {
            JSDynamicImportModule depRecord = cachedDependency(moduleRecord, matcher);
            if (depRecord != null && depRecord.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC) {
                return true;
            }
        }
        return false;
    }

    private JSNativeFunction importRejectionHandler(JSPromise importPromise, JSPromise.ResolveState resolveState) {
        JSNativeFunction handler = new JSNativeFunction(context, "", 1, (ctx, thisArg, args) -> {
            if (!resolveState.alreadyResolved) {
                resolveState.alreadyResolved = true;
                importPromise.reject(JSValue.argumentOrUndefined(args, 0));
            }
            return JSUndefined.INSTANCE;
        });
        handler.initializePrototypeChain(context);
        return handler;
    }

    /**
     * The order stamp for the next module to be deferred by an asynchronous dependency.
     * <p>
     * ES2024 16.2.1.5.2 gives every module deferred during the depth-first evaluation an
     * {@code [[AsyncEvaluationOrder]]}, and {@link #triggerPendingDependents} runs the ones that become ready in that
     * order.
     *
     * @return the next order stamp
     */
    int nextAsyncEvaluationOrder() {
        return asyncEvaluationOrderCounter++;
    }

    void registerPendingDependent(JSDynamicImportModule module) {
        int count = visitAsyncDependencies(module, false, dependency -> dependency.pendingDependents().add(module));
        module.setPendingAsyncDependencyCount(count);
    }

    private Matcher staticImports(JSDynamicImportModule module) {
        return ModuleSourceTransformer.MODULE_STATIC_IMPORT_PATTERN
                .matcher(ModuleSourceScan.maskModuleComments(module.rawSource()));
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
                ready.failEvaluation(moduleRecord.evaluationError());
                triggerPendingDependents(ready);
                continue;
            }
            try {
                ready.setStatus(JSDynamicImportModule.Status.EVALUATING);
                JSValue evalResult = loader.evaluateDynamicImportModule(ready);
                if (ready.hasTLA() && evalResult instanceof JSPromise asyncPromise) {
                    ready.setAsyncEvaluationOrder(asyncEvaluationOrderCounter++);
                    awaitCompletion(ready, asyncPromise);
                } else {
                    loader.finishLinkedModule(ready);
                    triggerPendingDependents(ready);
                }
            } catch (JSException jsException) {
                ready.failEvaluation(jsException.getErrorValue());
                triggerPendingDependents(ready);
            }
        }
    }

    private int visitAsyncDependencies(JSDynamicImportModule module, boolean requirePromise,
            Consumer<JSDynamicImportModule> visitor) {
        Matcher imports = staticImports(module);
        Set<String> seen = new HashSet<>();
        JSDynamicImportModule root = module.cycleRoot() != null ? module.cycleRoot() : module;
        while (imports.find()) {
            JSDynamicImportModule dependency = cachedDependency(module, imports);
            if (dependency != null) {
                // Within a cycle, register on the dependency itself. Across cycles, register on its root.
                JSDynamicImportModule effective = effectiveDependency(dependency, root);
                if (effective.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC
                        && (!requirePromise || effective.asyncEvaluationPromise() != null)
                        && seen.add(effective.resolvedSpecifier())) {
                    visitor.accept(effective);
                }
            }
        }
        return seen.size();
    }

    private static JSDynamicImportModule effectiveDependency(JSDynamicImportModule dependency,
            JSDynamicImportModule moduleCycleRoot) {
        JSDynamicImportModule root = dependency.cycleRoot();
        return root != null && root != moduleCycleRoot ? root : dependency;
    }

    private static void resolveImportPromise(JSContext context, JSObject namespace, JSPromise importPromise,
            JSPromise.ResolveState resolveState) {
        if (!resolveState.alreadyResolved) {
            resolveState.alreadyResolved = true;
            importPromise.resolve(context, namespace);
        }
    }
}
