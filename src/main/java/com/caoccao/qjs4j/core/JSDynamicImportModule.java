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

import java.util.*;

/**
 * Tracks the loading, evaluation, and export state of a dynamically imported module.
 */
final class JSDynamicImportModule {
    private final Set<String> ambiguousExportNames;
    private final Set<String> explicitExportNames;
    private final Map<String, String> exportOrigins;
    private final List<HoistedFunctionExportBinding> hoistedFunctionExportBindings;
    private final List<LocalExportBinding> localExportBindings;
    private final JSImportNamespaceObject namespace;
    private final List<JSDynamicImportModule> pendingDependents;
    private final List<ReExportBinding> reExportBindings;
    private final String resolvedSpecifier;
    private int asyncEvaluationOrder;
    private JSPromise asyncEvaluationPromise;
    private JSDynamicImportModule cycleRoot;
    private JSObject deferredNamespace;
    private boolean deferredPreload;
    private JSValue evaluationError;
    private String exportBindingName;
    private boolean hasExportSyntax;
    private boolean hasTLA;
    private boolean hoistedFunctionExportBindingsInitialized;
    private int pendingAsyncDependencyCount;
    private String rawSource;
    private Status status;
    private String transformedSource;

    JSDynamicImportModule(String resolvedSpecifier, JSImportNamespaceObject namespace) {
        this.ambiguousExportNames = new HashSet<>();
        this.asyncEvaluationOrder = -1;
        this.explicitExportNames = new HashSet<>();
        this.exportOrigins = new HashMap<>();
        this.namespace = namespace;
        this.hoistedFunctionExportBindings = new ArrayList<>();
        this.localExportBindings = new ArrayList<>();
        this.pendingAsyncDependencyCount = 0;
        this.pendingDependents = new ArrayList<>();
        this.reExportBindings = new ArrayList<>();
        this.resolvedSpecifier = resolvedSpecifier;
        this.status = Status.LOADING;
        this.asyncEvaluationPromise = null;
        this.cycleRoot = null;
        this.deferredNamespace = null;
        this.deferredPreload = false;
        this.evaluationError = null;
        this.exportBindingName = null;
        this.hasExportSyntax = false;
        this.hoistedFunctionExportBindingsInitialized = false;
        this.hasTLA = false;
        this.rawSource = "";
        this.transformedSource = "";
    }

    Set<String> ambiguousExportNames() {
        return ambiguousExportNames;
    }

    int asyncEvaluationOrder() {
        return asyncEvaluationOrder;
    }

    JSPromise asyncEvaluationPromise() {
        return asyncEvaluationPromise;
    }

    JSDynamicImportModule cycleRoot() {
        return cycleRoot;
    }

    void decrementPendingAsyncDependencyCount() {
        this.pendingAsyncDependencyCount--;
    }

    JSObject deferredNamespace() {
        return deferredNamespace;
    }

    boolean deferredPreload() {
        return deferredPreload;
    }

    JSValue evaluationError() {
        return evaluationError;
    }

    Set<String> explicitExportNames() {
        return explicitExportNames;
    }

    String exportBindingName() {
        return exportBindingName;
    }

    Map<String, String> exportOrigins() {
        return exportOrigins;
    }

    boolean hasExportSyntax() {
        return hasExportSyntax;
    }

    boolean hasTLA() {
        return hasTLA;
    }

    List<HoistedFunctionExportBinding> hoistedFunctionExportBindings() {
        return hoistedFunctionExportBindings;
    }

    boolean hoistedFunctionExportBindingsInitialized() {
        return hoistedFunctionExportBindingsInitialized;
    }

    List<LocalExportBinding> localExportBindings() {
        return localExportBindings;
    }

    JSImportNamespaceObject namespace() {
        return namespace;
    }

    int pendingAsyncDependencyCount() {
        return pendingAsyncDependencyCount;
    }

    List<JSDynamicImportModule> pendingDependents() {
        return pendingDependents;
    }

    String rawSource() {
        return rawSource;
    }

    List<ReExportBinding> reExportBindings() {
        return reExportBindings;
    }

    String resolvedSpecifier() {
        return resolvedSpecifier;
    }

    void setAsyncEvaluationOrder(int order) {
        this.asyncEvaluationOrder = order;
    }

    void setAsyncEvaluationPromise(JSPromise promise) {
        this.asyncEvaluationPromise = promise;
    }

    void setCycleRoot(JSDynamicImportModule cycleRoot) {
        this.cycleRoot = cycleRoot;
    }

    void setDeferredNamespace(JSObject deferredNamespace) {
        this.deferredNamespace = deferredNamespace;
    }

    void setDeferredPreload(boolean deferredPreload) {
        this.deferredPreload = deferredPreload;
    }

    void setEvaluationError(JSValue evaluationError) {
        this.evaluationError = evaluationError;
    }

    void setExportBindingName(String exportBindingName) {
        this.exportBindingName = exportBindingName;
    }

    void setHasExportSyntax(boolean hasExportSyntax) {
        this.hasExportSyntax = hasExportSyntax;
    }

    void setHasTLA(boolean hasTLA) {
        this.hasTLA = hasTLA;
    }

    void setHoistedFunctionExportBindingsInitialized(boolean hoistedFunctionExportBindingsInitialized) {
        this.hoistedFunctionExportBindingsInitialized = hoistedFunctionExportBindingsInitialized;
    }

    void setPendingAsyncDependencyCount(int count) {
        this.pendingAsyncDependencyCount = count;
    }

    void setRawSource(String rawSource) {
        this.rawSource = rawSource;
    }

    void setStatus(Status status) {
        this.status = status;
    }

    void setTransformedSource(String transformedSource) {
        this.transformedSource = transformedSource;
    }

    Status status() {
        return status;
    }

    String transformedSource() {
        return transformedSource;
    }

    enum Status {
        LOADING,
        EVALUATING,
        EVALUATED,
        EVALUATING_ASYNC,
        EVALUATED_ERROR
    }

    record HoistedFunctionExportBinding(
            String localName,
            String exportedName,
            String functionDeclarationSource) {
    }

    record LocalExportBinding(String localName, String exportedName) {
    }

    record ReExportBinding(
            String sourceSpecifier,
            String importedName,
            String exportedName,
            boolean starExport) {
    }
}
