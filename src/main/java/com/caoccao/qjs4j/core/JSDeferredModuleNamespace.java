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
 * A deferred module namespace proxy that lazily evaluates the module on first property access. Implements the [[Get]],
 * [[Has]], etc. internal methods per the import-defer specification.
 */
final class JSDeferredModuleNamespace extends JSModuleNamespace {
    private final JSDynamicImportModule moduleRecord;

    JSDeferredModuleNamespace(JSContext context, JSDynamicImportModule moduleRecord) {
        super(context);
        this.moduleRecord = moduleRecord;
        setPrototype(null);
        definePropertyInternal(PropertyKey.SYMBOL_TO_STRING_TAG,
                PropertyDescriptor.dataDescriptor(new JSString("Deferred Module"), PropertyDescriptor.DataState.None));
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
            setEvaluationPendingException(jsException);
            return false;
        }
    }

    @Override
    public boolean delete(PropertyKey key, boolean throwOnFailure) {
        if (isSymbolLikeNamespaceKey(key)) {
            return true;
        }
        try {
            return ensureEvaluated().delete(key, throwOnFailure);
        } catch (JSException jsException) {
            setEvaluationPendingException(jsException);
            return false;
        }
    }

    private JSImportNamespaceObject ensureEvaluated() {
        if (moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED_ERROR) {
            throw new JSException(moduleRecord.evaluationError());
        }
        if (moduleRecord.status() == JSDynamicImportModule.Status.EVALUATING
                || moduleRecord.status() == JSDynamicImportModule.Status.EVALUATING_ASYNC) {
            throw new JSException(context
                    .throwTypeError("Cannot access deferred namespace of a module that is currently being evaluated"));
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
    public JSValue get(PropertyKey key) {
        if (isSymbolLikeNamespaceKey(key)) {
            return super.get(key);
        }
        try {
            return ensureEvaluated().get(key);
        } catch (JSException jsException) {
            setEvaluationPendingException(jsException);
            return JSUndefined.INSTANCE;
        }
    }

    @Override
    public JSValue get(PropertyKey key, JSValue receiver) {
        JSValue effectiveReceiver = receiver != null ? receiver : this;
        if (isSymbolLikeNamespaceKey(key)) {
            return super.get(key, effectiveReceiver);
        }
        try {
            return ensureEvaluated().get(key, effectiveReceiver);
        } catch (JSException jsException) {
            setEvaluationPendingException(jsException);
            return JSUndefined.INSTANCE;
        }
    }

    @Override
    protected PropertyDescriptor getOwnPropertyDescriptorRaw(PropertyKey key) {
        if (isSymbolLikeNamespaceKey(key)) {
            return super.getOwnPropertyDescriptorRaw(key);
        }
        try {
            return ensureEvaluated().getOwnPropertyDescriptor(key);
        } catch (JSException jsException) {
            setEvaluationPendingException(jsException);
            return null;
        }
    }

    @Override
    public List<PropertyKey> getOwnPropertyKeys() {
        try {
            return ensureEvaluated().getOwnPropertyKeys();
        } catch (JSException jsException) {
            setEvaluationPendingException(jsException);
            return List.of();
        }
    }

    @Override
    public JSObject getPrototype() {
        return null;
    }

    @Override
    protected JSValue getWithReceiver(PropertyKey key, JSValue receiver, int depth) {
        if (isSymbolLikeNamespaceKey(key)) {
            return super.getWithReceiver(key, receiver, depth);
        }
        try {
            return ensureEvaluated().get(key, receiver);
        } catch (JSException jsException) {
            setEvaluationPendingException(jsException);
            return JSUndefined.INSTANCE;
        }
    }

    @Override
    protected boolean has(PropertyKey key, int depth) {
        if (isSymbolLikeNamespaceKey(key)) {
            return super.has(key, depth);
        }
        try {
            return ensureEvaluated().has(key);
        } catch (JSException jsException) {
            setEvaluationPendingException(jsException);
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
            setEvaluationPendingException(jsException);
            return false;
        }
    }

    /**
     * Reading a non-symbol key evaluates the deferred module, so this object decides the lookup itself and must be
     * dispatched to when it appears in a prototype chain.
     *
     * @param key
     *            the property being looked up
     * @return true unless the key is one of the namespace's own symbol-like keys
     */
    @Override
    protected boolean interceptsPropertyLookup(PropertyKey key) {
        return !isSymbolLikeNamespaceKey(key);
    }

    private boolean isSymbolLikeNamespaceKey(PropertyKey key) {
        if (moduleRecord.status() == JSDynamicImportModule.Status.EVALUATED) {
            return false;
        }
        return key.isSymbol() || PropertyKey.THEN.equals(key);
    }

    @Override
    public void preventExtensions() {
        // Namespace objects are already non-extensible; no-op without evaluation
    }

    private void setEvaluationPendingException(JSException jsException) {
        JSValue error = jsException.getErrorValue();
        if (error != null) {
            context.setPendingException(error);
        } else {
            context.throwError(jsException.getMessage() != null ? jsException.getMessage() : "Module evaluation error");
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
