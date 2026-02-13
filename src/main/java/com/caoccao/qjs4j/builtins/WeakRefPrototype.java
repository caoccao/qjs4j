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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.core.JSWeakRef;

/**
 * Implementation of WeakRef.prototype methods.
 * Based on ES2021 WeakRef specification.
 */
public final class WeakRefPrototype {

    /**
     * WeakRef.prototype.deref()
     * ES2021 25.1.3.2
     */
    public static JSValue deref(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWeakRef weakRef)) {
            return context.throwTypeError("WeakRef.prototype.deref called on incompatible receiver");
        }
        return weakRef.deref();
    }
}
