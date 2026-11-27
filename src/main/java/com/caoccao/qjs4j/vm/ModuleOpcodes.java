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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.error.JSStackFrame;
import com.caoccao.qjs4j.core.module.JSImportDeferMarker;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.exceptions.*;

import java.util.*;

/** Dynamic import evaluation and promise settlement. */
final class ModuleOpcodes {
    private ModuleOpcodes() {
    }

    private static Map<String, String> collectImportAttributes(JSContext context, JSValue optionsValue) {
        if (optionsValue instanceof JSUndefined) {
            return null;
        }
        if (!(optionsValue instanceof JSObject optionsObject)) {
            JSValue errorValue = context.throwTypeError("options must be an object");
            JSValue pendingError = context.takePendingException();
            throw new JSException(pendingError != null ? pendingError : errorValue);
        }

        JSValue withValue = optionsObject.get(PropertyKey.fromString("with"));
        if (context.hasPendingException()) {
            throw new JSException(context.takePendingException());
        }
        if (withValue instanceof JSUndefined) {
            return null;
        }
        if (!(withValue instanceof JSObject withObject)) {
            JSValue errorValue = context.throwTypeError("options.with must be an object");
            JSValue pendingError = context.takePendingException();
            throw new JSException(pendingError != null ? pendingError : errorValue);
        }

        Map<String, String> attributes = new HashMap<>();
        PropertyKey[] enumerableKeys = withObject.enumerableKeys();
        if (context.hasPendingException()) {
            throw new JSException(context.takePendingException());
        }
        for (PropertyKey key : enumerableKeys) {
            if (!key.isString()) {
                continue;
            }
            JSValue attributeValue = withObject.get(key);
            if (context.hasPendingException()) {
                throw new JSException(context.takePendingException());
            }
            if (!(attributeValue instanceof JSString attributeString)) {
                JSValue errorValue = context.throwTypeError("Import assertion value must be a string");
                JSValue pendingError = context.takePendingException();
                throw new JSException(pendingError != null ? pendingError : errorValue);
            }
            attributes.put(key.asString(), attributeString.value());
        }
        return attributes;
    }

    // specifier options -> promise (dynamic module import)
    static void handleImport(Opcode op, ExecutionContext executionContext) {
        JSContext context = executionContext.virtualMachine.context;
        JSValue options = executionContext.pop();
        boolean deferPhase = options == JSImportDeferMarker.VALUE;
        if (deferPhase) {
            options = JSUndefined.INSTANCE;
        }
        JSValue specifier = executionContext.pop();
        JSStackFrame currentStackFrame = context.getCurrentStackFrame();
        String referrerFilename = currentStackFrame != null ? currentStackFrame.filename() : null;

        JSPromise promise = context.createJSPromise();
        final JSPromise.ResolveState resolveState = new JSPromise.ResolveState();
        final String specifierString;
        final Map<String, String> importAttributes;

        try {
            specifierString = JSTypeConversions.toString(context, specifier).toString();
            if (context.hasPendingException()) {
                throw new JSException(context.takePendingException());
            }
            importAttributes = collectImportAttributes(context, options);
        } catch (Exception e) {
            context.clearPendingException();
            JSValue error;
            if (e instanceof JSException js) {
                error = js.getErrorValue();
                if (error == null) {
                    error = context.throwError(js.getMessage());
                }
            } else if (e instanceof JSVirtualMachineException vm) {
                error = vm.getThrownValue();
                if (error == null) {
                    error = context.throwError(e.getMessage() == null ? "Module load error" : e.getMessage());
                }
            } else {
                error = context.throwError("Error", e.getMessage() == null ? "Module load error" : e.getMessage());
            }
            context.clearPendingException();
            resolveState.alreadyResolved = true;
            promise.reject(error);
            executionContext.push(promise);
            executionContext.pc += op.getSize();
            return;
        }
        context.enqueueMicrotask(() -> load(context, deferPhase, specifierString, referrerFilename, importAttributes,
                promise, resolveState));
        executionContext.push(promise);
        executionContext.pc += op.getSize();
    }

    private static void load(JSContext context, boolean deferred, String specifierString, String referrerFilename,
            Map<String, String> importAttributes, JSPromise promise, JSPromise.ResolveState resolveState) {
        try {
            JSObject moduleNamespace;
            if (deferred) {
                moduleNamespace = context.loadDynamicImportModuleDeferred(specifierString, referrerFilename,
                        importAttributes, promise, resolveState);
            } else {
                moduleNamespace = context.loadDynamicImportModule(specifierString, referrerFilename, importAttributes,
                        promise, resolveState);
            }
            if (moduleNamespace != null && !resolveState.alreadyResolved) {
                resolveState.alreadyResolved = true;
                promise.resolve(context, moduleNamespace);
            }
        } catch (Exception e) {
            if (!resolveState.alreadyResolved) {
                resolveState.alreadyResolved = true;
                promise.reject(loadError(context, e));
            } else if (context.hasPendingException()) {
                context.takePendingException();
            }
        }
    }

    private static JSValue loadError(JSContext context, Exception e) {
        if (context.hasPendingException()) {
            return context.takePendingException();
        }
        if (e instanceof JSException js) {
            return js.getErrorValue();
        }
        if (e instanceof JSVirtualMachineException vm) {
            JSValue value = vm.getThrownValue();
            return value != null ? value : new JSString(e.getMessage() == null ? "Unknown error" : e.getMessage());
        }
        JSValue error = context.throwError("Error loading module: " + e.getMessage());
        context.clearPendingException();
        return error;
    }
}
