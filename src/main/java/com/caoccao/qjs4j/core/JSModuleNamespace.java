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

/** Property writes and extensibility are fixed for both eager and deferred module namespaces. */
abstract sealed class JSModuleNamespace extends JSObject permits JSImportNamespaceObject, JSDeferredModuleNamespace {
    JSModuleNamespace(JSContext context) {
        super(context);
    }

    @Override
    public final boolean isExtensible() {
        return false;
    }

    @Override
    public final PropertyKey[] ownPropertyKeys() {
        return getOwnPropertyKeys().toArray(new PropertyKey[0]);
    }

    @Override
    public final void set(PropertyKey key, JSValue value) {
        setWithReceiverAndException(key, value, this);
    }

    @Override
    public final boolean setWithReceiverAndException(PropertyKey key, JSValue value, JSObject receiver) {
        if (context.isStrictMode()) {
            context.throwTypeError(
                    "Cannot assign to read only property '" + key.toPropertyString() + "' of [object Module]");
        }
        return false;
    }

    @Override
    public final boolean setWithResult(PropertyKey key, JSValue value) {
        return false;
    }

    @Override
    public final boolean setWithResult(PropertyKey key, JSValue value, JSObject receiver) {
        return false;
    }
}
