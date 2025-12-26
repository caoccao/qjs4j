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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Module loader for ES6 modules.
 * Handles module resolution, loading, and caching.
 */
public final class ModuleLoader implements JSModule.ModuleResolver {
    private final JSContext context;
    private final Map<String, JSModule> moduleCache;
    private final Path basePath;

    /**
     * Create a module loader.
     *
     * @param context The execution context
     * @param basePath Base path for resolving relative module specifiers
     */
    public ModuleLoader(JSContext context, Path basePath) {
        this.context = context;
        this.moduleCache = new HashMap<>();
        this.basePath = basePath != null ? basePath : Paths.get("").toAbsolutePath();
    }

    /**
     * Create a module loader with current directory as base path.
     */
    public ModuleLoader(JSContext context) {
        this(context, null);
    }

    /**
     * Load a module from a file.
     *
     * @param specifier Module specifier (file path or module name)
     * @return The loaded module
     * @throws JSModule.ModuleLinkingException if loading or linking fails
     * @throws JSModule.ModuleEvaluationException if evaluation fails
     */
    public JSModule load(String specifier) throws JSModule.ModuleLinkingException, JSModule.ModuleEvaluationException {
        // Check cache
        JSModule cached = moduleCache.get(specifier);
        if (cached != null) {
            return cached;
        }

        // Resolve to absolute path
        Path modulePath = resolveModulePath(specifier);
        String absoluteUrl = modulePath.toAbsolutePath().toString();

        // Check if already cached by absolute path
        cached = moduleCache.get(absoluteUrl);
        if (cached != null) {
            moduleCache.put(specifier, cached); // Cache by specifier too
            return cached;
        }

        // Load module source
        String source;
        try {
            source = Files.readString(modulePath);
        } catch (IOException e) {
            throw new JSModule.ModuleLinkingException("Cannot load module " + specifier + ": " + e.getMessage(), e);
        }

        // Compile module
        JSBytecodeFunction moduleFunction;
        try {
            moduleFunction = com.caoccao.qjs4j.compiler.Compiler.compileModule(source, absoluteUrl);
        } catch (Exception e) {
            throw new JSModule.ModuleLinkingException("Cannot compile module " + specifier + ": " + e.getMessage(), e);
        }

        // Create module record
        JSModule module = new JSModule(absoluteUrl, moduleFunction);

        // Cache immediately to handle circular dependencies
        moduleCache.put(specifier, module);
        moduleCache.put(absoluteUrl, module);

        // Link the module (this will recursively load dependencies)
        module.link(this, context);

        return module;
    }

    /**
     * Resolve a module specifier to an absolute path.
     * ES2020 module resolution algorithm.
     */
    private Path resolveModulePath(String specifier) throws JSModule.ModuleLinkingException {
        // Relative path (starts with ./ or ../)
        if (specifier.startsWith("./") || specifier.startsWith("../")) {
            Path resolved = basePath.resolve(specifier).normalize();

            // Try exact match
            if (Files.exists(resolved)) {
                return resolved;
            }

            // Try adding .js extension
            Path withJs = Paths.get(resolved.toString() + ".js");
            if (Files.exists(withJs)) {
                return withJs;
            }

            // Try adding .mjs extension
            Path withMjs = Paths.get(resolved.toString() + ".mjs");
            if (Files.exists(withMjs)) {
                return withMjs;
            }

            throw new JSModule.ModuleLinkingException("Cannot find module: " + specifier);
        }

        // Absolute path
        if (specifier.startsWith("/")) {
            Path absolute = Paths.get(specifier);
            if (Files.exists(absolute)) {
                return absolute;
            }
            throw new JSModule.ModuleLinkingException("Cannot find module: " + specifier);
        }

        // Bare specifier (e.g., 'lodash')
        // In a full implementation, this would search node_modules
        // For now, just throw an error
        throw new JSModule.ModuleLinkingException("Bare module specifiers not yet supported: " + specifier);
    }

    /**
     * Resolve a module specifier from a referrer module.
     * Implements JSModule.ModuleResolver interface.
     */
    @Override
    public JSModule resolve(String specifier, JSModule referrer) {
        try {
            // If referrer exists, resolve relative to its location
            if (referrer != null) {
                String referrerUrl = referrer.getUrl();
                Path referrerPath = Paths.get(referrerUrl).getParent();

                // Create a temporary loader with the referrer's directory as base
                ModuleLoader referrerLoader = new ModuleLoader(context, referrerPath);
                referrerLoader.moduleCache.putAll(this.moduleCache); // Share cache

                return referrerLoader.load(specifier);
            } else {
                // No referrer, resolve from base path
                return load(specifier);
            }
        } catch (Exception e) {
            // Return null to indicate resolution failure
            // The caller will handle the error
            return null;
        }
    }

    /**
     * Evaluate a loaded module.
     *
     * @param module The module to evaluate
     * @return The evaluation result
     * @throws JSModule.ModuleEvaluationException if evaluation fails
     */
    public JSValue evaluate(JSModule module) throws JSModule.ModuleEvaluationException {
        return module.evaluate(context);
    }

    /**
     * Load and evaluate a module in one step.
     *
     * @param specifier Module specifier
     * @return The module namespace object
     * @throws JSModule.ModuleLinkingException if loading or linking fails
     * @throws JSModule.ModuleEvaluationException if evaluation fails
     */
    public JSObject import_(String specifier) throws JSModule.ModuleLinkingException, JSModule.ModuleEvaluationException {
        JSModule module = load(specifier);
        evaluate(module);
        return module.getNamespace();
    }

    /**
     * Get the module cache.
     */
    public Map<String, JSModule> getCache() {
        return moduleCache;
    }

    /**
     * Clear the module cache.
     */
    public void clearCache() {
        moduleCache.clear();
    }
}
