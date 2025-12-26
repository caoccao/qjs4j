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

/**
 * Implementation of dynamic import() for ES6 modules.
 * ES2020 specification: import() returns a Promise that resolves to the module namespace.
 *
 * Usage:
 *   import('./module.js').then(mod => {
 *     console.log(mod.exportedValue);
 *   });
 */
public final class DynamicImport {

    /**
     * Implement dynamic import as a function.
     * Returns a promise that resolves to the module namespace.
     *
     * @param ctx The execution context
     * @param specifier Module specifier to import
     * @param loader Module loader to use
     * @return A promise that resolves to the module namespace object
     */
    public static JSPromise import_(JSContext ctx, String specifier, ModuleLoader loader) {
        JSPromise promise = new JSPromise();

        // Queue module loading as a microtask
        ctx.enqueueMicrotask(() -> {
            try {
                // Load and evaluate the module
                JSObject namespace = loader.import_(specifier);

                // Fulfill the promise with the namespace
                promise.fulfill(namespace);
            } catch (JSModule.ModuleLinkingException e) {
                // Reject with a TypeError for linking errors
                JSObject error = new JSObject();
                error.set("name", new JSString("TypeError"));
                error.set("message", new JSString("Cannot import module: " + e.getMessage()));
                promise.reject(error);
            } catch (JSModule.ModuleEvaluationException e) {
                // Reject with the JS exception if available
                JSValue jsException = e.getJsException();
                if (jsException != null) {
                    promise.reject(jsException);
                } else {
                    JSObject error = new JSObject();
                    error.set("name", new JSString("Error"));
                    error.set("message", new JSString("Module evaluation failed: " + e.getMessage()));
                    promise.reject(error);
                }
            } catch (Exception e) {
                // Reject with a generic error
                JSObject error = new JSObject();
                error.set("name", new JSString("Error"));
                error.set("message", new JSString("Import failed: " + e.getMessage()));
                promise.reject(error);
            }
        });

        return promise;
    }

    /**
     * Create a native function wrapper for import().
     *
     * @param ctx The execution context
     * @param loader Module loader to use
     * @return A JSNativeFunction that implements import()
     */
    public static JSNativeFunction createImportFunction(JSContext ctx, ModuleLoader loader) {
        return new JSNativeFunction("import", 1, (context, thisArg, args) -> {
            if (args.length == 0) {
                return context.throwError("TypeError", "import() requires a module specifier");
            }

            // Convert specifier to string
            String specifier = JSTypeConversions.toString(args[0]).getValue();

            // Return promise
            return import_(context, specifier, loader);
        });
    }
}
