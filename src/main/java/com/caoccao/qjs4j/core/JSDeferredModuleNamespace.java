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

import com.caoccao.qjs4j.exceptions.JSException;

import java.util.HashSet;
import java.util.List;

/**
 * A deferred module namespace proxy that lazily evaluates the module
 * on first property access. Implements the [[Get]], [[Has]], etc.
 * internal methods per the import-defer specification.
 */
final class JSDeferredModuleNamespace extends JSObject {
    private final JSContext context;
    private final JSDynamicImportModule moduleRecord;

    JSDeferredModuleNamespace(
            JSContext context,
            JSDynamicImportModule moduleRecord) {
        super(context);
        this.context = context;
        this.moduleRecord = moduleRecord;
        setPrototype(null);
        definePropertyInternal(
                PropertyKey.SYMBOL_TO_STRING_TAG,
            PropertyDescriptor.dataDescriptor(
                new JSString("Deferred Module"),
                PropertyDescriptor.DataState.None));
        super.preventExtensions();
    }

    @Override
    public boolean defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
        if (isSymbolLikeNamespaceKey(key)) {
            return false;
        }
        try {
            return ensureEvaluated().defineProperty(key, descriptor);
        } catch (JSException jsException) {
            setEvaluationPendingException(context, jsException);
            return false;
        }
    }

    @Override
    public boolean delete(PropertyKey key) {
        if (isSymbolLikeNamespaceKey(key)) {
            return true;
        }
        try {
            return ensureEvaluated().delete(key);
        } catch (JSException jsException) {
            setEvaluationPendingException(context, jsException);
            return false;
        }
    }

    private JSImportNamespaceObject ensureEvaluated() {
        if (moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
            throw new JSException(moduleRecord.evaluationError());
        }
        if (moduleRecord.status() == JSDynamicImportModule.Status.EVALUATING
                || moduleRecord.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC) {
            throw new JSException(context.throwTypeError(
                    "Cannot access deferred namespace of a module that is currently being evaluated"));
        }
        if (moduleRecord.status() != JSDynamicImportModule.Status.EVALUATED) {
            if (!context.readyForSyncExecution(moduleRecord.resolvedSpecifier(), new HashSet<>())) {
                throw new JSException(context.throwTypeError(
                        "Cannot synchronously evaluate deferred module because it or one of its dependencies is not ready"));
            }
            moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATING);
            try {
                context.evaluateDynamicImportModule(moduleRecord);
                context.resolveDynamicImportReExports(moduleRecord, new HashSet<>());
                moduleRecord.namespace().finalizeNamespace();
                moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED);
            } catch (JSException jsException) {
                moduleRecord.setEvaluationError(jsException.getErrorValue());
                moduleRecord.setStatus(JSDynamicImportModule.Status.EVALUATED_ERROR);
                throw jsException;
            }
        }
        return moduleRecord.namespace();
    }

    @Override
    public JSValue get(JSContext context, PropertyKey key) {
        JSContext effectiveContext = resolveContext(context);
        if (isSymbolLikeNamespaceKey(key)) {
            return super.get(effectiveContext, key);
        }
        try {
            return ensureEvaluated().get(effectiveContext, key);
        } catch (JSException jsException) {
            setEvaluationPendingException(effectiveContext, jsException);
            return JSUndefined.INSTANCE;
        }
    }

    @Override
    public JSValue get(JSContext context, PropertyKey key, JSValue receiver) {
        JSContext effectiveContext = resolveContext(context);
        JSValue effectiveReceiver = receiver != null ? receiver : this;
        if (isSymbolLikeNamespaceKey(key)) {
            return super.get(effectiveContext, key, effectiveReceiver);
        }
        try {
            return ensureEvaluated().get(effectiveContext, key, effectiveReceiver);
        } catch (JSException jsException) {
            setEvaluationPendingException(effectiveContext, jsException);
            return JSUndefined.INSTANCE;
        }
    }

    @Override
    public JSValue get(PropertyKey key) {
        if (isSymbolLikeNamespaceKey(key)) {
            return super.get(key);
        }
        try {
            return ensureEvaluated().get(key);
        } catch (JSException jsException) {
            setEvaluationPendingException(null, jsException);
            return JSUndefined.INSTANCE;
        }
    }

    @Override
    public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
        if (isSymbolLikeNamespaceKey(key)) {
            return super.getOwnPropertyDescriptor(key);
        }
        try {
            return ensureEvaluated().getOwnPropertyDescriptor(key);
        } catch (JSException jsException) {
            setEvaluationPendingException(null, jsException);
            return null;
        }
    }

    @Override
    public List<PropertyKey> getOwnPropertyKeys() {
        try {
            return ensureEvaluated().getOwnPropertyKeys();
        } catch (JSException jsException) {
            setEvaluationPendingException(null, jsException);
            return List.of();
        }
    }

    @Override
    public JSObject getPrototype() {
        return null;
    }

    @Override
    protected JSValue getWithReceiver(JSContext context, PropertyKey key, JSValue receiver) {
        JSContext effectiveContext = resolveContext(context);
        if (isSymbolLikeNamespaceKey(key)) {
            return super.getWithReceiver(effectiveContext, key, receiver);
        }
        try {
            return ensureEvaluated().get(effectiveContext, key, receiver);
        } catch (JSException jsException) {
            setEvaluationPendingException(effectiveContext, jsException);
            return JSUndefined.INSTANCE;
        }
    }

    @Override
    public boolean has(PropertyKey key) {
        if (isSymbolLikeNamespaceKey(key)) {
            return super.has(key);
        }
        try {
            return ensureEvaluated().has(key);
        } catch (JSException jsException) {
            setEvaluationPendingException(null, jsException);
            return false;
        }
    }

    @Override
    public boolean hasOwnProperty(PropertyKey key) {
        if (isSymbolLikeNamespaceKey(key)) {
            return super.hasOwnProperty(key);
        }
        try {
            return ensureEvaluated().hasOwnProperty(key);
        } catch (JSException jsException) {
            setEvaluationPendingException(null, jsException);
            return false;
        }
    }

    @Override
    public boolean isExtensible() {
        return false;
    }

    private boolean isSymbolLikeNamespaceKey(PropertyKey key) {
        if (moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED) {
            return false;
        }
        return key.isSymbol() || PropertyKey.THEN.equals(key);
    }

    @Override
    public PropertyKey[] ownPropertyKeys() {
        try {
            return ensureEvaluated().ownPropertyKeys();
        } catch (JSException jsException) {
            setEvaluationPendingException(null, jsException);
            return new PropertyKey[0];
        }
    }

    @Override
    public void preventExtensions() {
        // Namespace objects are already non-extensible; no-op without evaluation
    }

    @Override
    public void set(JSContext context, PropertyKey key, JSValue value) {
        // Namespace objects are immutable; no-op without evaluation
    }

    @Override
    public void set(PropertyKey key, JSValue value) {
        set(resolveContext(null), key, value);
    }

    private void setEvaluationPendingException(JSContext callerContext, JSException jsException) {
        JSContext effectiveContext = callerContext != null ? callerContext : this.context;
        JSValue errorValue = jsException.getErrorValue();
        if (errorValue != null) {
            effectiveContext.setPendingException(errorValue);
        } else {
            effectiveContext.setPendingException(
                    effectiveContext.throwError("Error",
                            jsException.getMessage() != null ? jsException.getMessage() : "Module evaluation error"));
        }
    }

    @Override
    public void setPrototype(JSObject prototype) {
        if (prototype == null) {
            super.setPrototype(null);
        }
        // Setting non-null prototype on namespace is a no-op per spec
    }
}
