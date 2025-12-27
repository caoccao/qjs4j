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

package com.caoccao.qjs4j.types;

import com.caoccao.qjs4j.core.*;

import java.util.*;

/**
 * Represents a JavaScript ES6 module.
 * Based on ES2020 Module Record specification.
 * <p>
 * A module has:
 * - Named exports (export { x, y })
 * - Default export (export default ...)
 * - Named imports (import { x } from 'module')
 * - Namespace imports (import * as ns from 'module')
 * - Import bindings that reference exports from other modules
 * - Evaluation state (unlinked, linking, linked, evaluating, evaluated)
 */
public final class JSModule {
    /**
     * Module evaluation states based on ES2020 Cyclic Module Records.
     */
    public enum ModuleStatus {
        UNLINKED,      // Module not yet linked to its dependencies
        LINKING,       // Currently resolving dependencies
        LINKED,        // All dependencies resolved, ready to evaluate
        EVALUATING,    // Currently executing module code
        EVALUATED      // Module code has been executed
    }

    private final String url;
    private final JSBytecodeFunction moduleFunction;
    private final Map<String, ExportEntry> namedExports;
    private final Map<String, ImportEntry> namedImports;
    private JSValue defaultExport;
    private final List<JSModule> requestedModules;
    private ModuleStatus status;
    private final JSObject namespace;

    // For circular dependency detection
    private final int dfsIndex;
    private final int dfsAncestorIndex;

    /**
     * Create a new module.
     *
     * @param url            Module URL or identifier
     * @param moduleFunction The compiled module code as a function
     */
    public JSModule(String url, JSBytecodeFunction moduleFunction) {
        this.url = url;
        this.moduleFunction = moduleFunction;
        this.namedExports = new HashMap<>();
        this.namedImports = new HashMap<>();
        this.defaultExport = null;
        this.requestedModules = new ArrayList<>();
        this.status = ModuleStatus.UNLINKED;
        this.namespace = new JSObject();
        this.dfsIndex = -1;
        this.dfsAncestorIndex = -1;
    }

    /**
     * Add a named export to this module.
     *
     * @param exportName The name to export as
     * @param localName  The local binding name
     */
    public void addNamedExport(String exportName, String localName) {
        namedExports.put(exportName, new ExportEntry(exportName, localName, null, null));
    }

    /**
     * Add a re-export from another module.
     *
     * @param exportName    The name to export as
     * @param moduleRequest The module to import from
     * @param importName    The name to import from that module
     */
    public void addReExport(String exportName, String moduleRequest, String importName) {
        namedExports.put(exportName, new ExportEntry(exportName, null, moduleRequest, importName));
    }

    /**
     * Set the default export.
     *
     * @param value The default export value
     */
    public void setDefaultExport(JSValue value) {
        this.defaultExport = value;
        namedExports.put("default", new ExportEntry("default", "*default*", null, null));
    }

    /**
     * Add a named import from another module.
     *
     * @param localName     The local binding name
     * @param moduleRequest The module to import from
     * @param importName    The name to import (or "*" for namespace import)
     */
    public void addImport(String localName, String moduleRequest, String importName) {
        namedImports.put(localName, new ImportEntry(moduleRequest, importName, localName));
    }

    /**
     * Add a module dependency.
     *
     * @param module The module this module depends on
     */
    public void addDependency(JSModule module) {
        if (!requestedModules.contains(module)) {
            requestedModules.add(module);
        }
    }

    /**
     * Link this module to its dependencies.
     * ES2020 15.2.1.16 Link() method.
     *
     * @param resolver Module resolver for resolving import specifiers
     * @param ctx      The execution context
     * @throws ModuleLinkingException if linking fails
     */
    public void link(ModuleResolver resolver, JSContext ctx) throws ModuleLinkingException {
        if (status == ModuleStatus.LINKING || status == ModuleStatus.EVALUATING) {
            throw new ModuleLinkingException("Circular module dependency detected: " + url);
        }
        if (status == ModuleStatus.LINKED || status == ModuleStatus.EVALUATED) {
            return; // Already linked
        }

        status = ModuleStatus.LINKING;

        try {
            // Resolve all import entries
            for (ImportEntry importEntry : namedImports.values()) {
                JSModule importedModule = resolver.resolve(importEntry.moduleRequest, this);
                if (importedModule == null) {
                    throw new ModuleLinkingException("Cannot resolve module: " + importEntry.moduleRequest);
                }
                addDependency(importedModule);

                // Recursively link dependencies
                importedModule.link(resolver, ctx);
            }

            status = ModuleStatus.LINKED;
        } catch (Exception e) {
            status = ModuleStatus.UNLINKED;
            throw new ModuleLinkingException("Failed to link module " + url + ": " + e.getMessage(), e);
        }
    }

    /**
     * Evaluate this module.
     * ES2020 15.2.1.17 Evaluate() method.
     *
     * @param ctx The execution context
     * @return The module evaluation result
     * @throws ModuleEvaluationException if evaluation fails
     */
    public JSValue evaluate(JSContext ctx) throws ModuleEvaluationException {
        if (status == ModuleStatus.EVALUATING) {
            // Circular evaluation - this is ok, just return undefined
            return JSUndefined.INSTANCE;
        }
        if (status == ModuleStatus.EVALUATED) {
            return JSUndefined.INSTANCE; // Already evaluated
        }
        if (status != ModuleStatus.LINKED) {
            throw new ModuleEvaluationException("Cannot evaluate unlinked module: " + url);
        }

        status = ModuleStatus.EVALUATING;

        try {
            // First, evaluate all dependencies
            for (JSModule dependency : requestedModules) {
                dependency.evaluate(ctx);
            }

            // Execute the module code
            com.caoccao.qjs4j.vm.VirtualMachine vm = new com.caoccao.qjs4j.vm.VirtualMachine(ctx);
            JSValue result = vm.execute(moduleFunction, namespace, new JSValue[0]);

            // Check for exceptions
            if (ctx.hasPendingException()) {
                JSValue exception = ctx.getPendingException();
                ctx.clearPendingException();
                status = ModuleStatus.LINKED; // Reset to linked state
                throw new ModuleEvaluationException("Module evaluation failed: " + url, exception);
            }

            // Populate namespace object with exports
            populateNamespace(ctx);

            status = ModuleStatus.EVALUATED;
            return result != null ? result : JSUndefined.INSTANCE;
        } catch (ModuleEvaluationException e) {
            throw e;
        } catch (Exception e) {
            status = ModuleStatus.LINKED; // Reset to linked state
            throw new ModuleEvaluationException("Error evaluating module " + url + ": " + e.getMessage(), e);
        }
    }

    /**
     * Populate the namespace object with exported values.
     */
    private void populateNamespace(JSContext ctx) {
        // Add all named exports to the namespace
        for (Map.Entry<String, ExportEntry> entry : namedExports.entrySet()) {
            String exportName = entry.getKey();
            ExportEntry exportEntry = entry.getValue();

            if (exportEntry.moduleRequest == null) {
                // Local export
                JSValue value = namespace.get(exportEntry.localName);
                if (value == null) {
                    value = JSUndefined.INSTANCE;
                }
                namespace.set(exportName, value);
            } else {
                // Re-export from another module
                // This would need to resolve the imported value
                // For now, set to undefined
                namespace.set(exportName, JSUndefined.INSTANCE);
            }
        }

        // Add default export
        if (defaultExport != null) {
            namespace.set("default", defaultExport);
        }

        // Make namespace object non-extensible (modules are sealed)
        // In a full implementation, this would call Object.preventExtensions()
    }

    /**
     * Get a named export value.
     *
     * @param name The export name
     * @return The exported value, or null if not found
     */
    public JSValue getExport(String name) {
        return namespace.get(name);
    }

    /**
     * Get the module namespace object.
     * Used for: import * as ns from 'module'
     *
     * @return The namespace object containing all exports
     */
    public JSObject getNamespace() {
        return namespace;
    }

    /**
     * Get the module URL/identifier.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Get the module status.
     */
    public ModuleStatus getStatus() {
        return status;
    }

    /**
     * Get all named exports.
     */
    public Map<String, ExportEntry> getNamedExports() {
        return Collections.unmodifiableMap(namedExports);
    }

    /**
     * Get all dependencies.
     */
    public List<JSModule> getDependencies() {
        return Collections.unmodifiableList(requestedModules);
    }

    /**
     * Export entry record.
     * Represents: export { x as y } or export { x } from 'module'
     *
     * @param exportName    Name exported to other modules
     * @param localName     Local binding name (for local exports)
     * @param moduleRequest Module to re-export from (for re-exports)
     * @param importName    Name to import from that module (for re-exports)
     */
        public record ExportEntry(String exportName, String localName, String moduleRequest, String importName) {
    }

    /**
     * Import entry record.
     * Represents: import { x as y } from 'module'
     *
     * @param moduleRequest Module specifier to import from
     * @param importName    Name to import (or "*" for namespace)
     * @param localName     Local binding name
     */
        public record ImportEntry(String moduleRequest, String importName, String localName) {
    }

    /**
     * Module resolver interface.
     * Resolves module specifiers to module instances.
     */
    public interface ModuleResolver {
        /**
         * Resolve a module specifier to a module.
         *
         * @param specifier The module specifier (e.g., './foo.js', 'lodash')
         * @param referrer  The module requesting the import
         * @return The resolved module, or null if not found
         */
        JSModule resolve(String specifier, JSModule referrer);
    }

    /**
     * Exception thrown when module linking fails.
     */
    public static class ModuleLinkingException extends Exception {
        public ModuleLinkingException(String message) {
            super(message);
        }

        public ModuleLinkingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when module evaluation fails.
     */
    public static class ModuleEvaluationException extends Exception {
        private final JSValue jsException;

        public ModuleEvaluationException(String message) {
            super(message);
            this.jsException = null;
        }

        public ModuleEvaluationException(String message, JSValue jsException) {
            super(message);
            this.jsException = jsException;
        }

        public ModuleEvaluationException(String message, Throwable cause) {
            super(message, cause);
            this.jsException = null;
        }

        public JSValue getJsException() {
            return jsException;
        }
    }

    @Override
    public String toString() {
        return "Module[" + url + ", status=" + status + "]";
    }
}
