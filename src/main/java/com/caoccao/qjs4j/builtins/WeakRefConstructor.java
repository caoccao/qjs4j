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
 * WeakRef constructor implementation.
 * Based on ES2021 WeakRef specification.
 * <p>
 * WeakRef(target) creates a weak reference to an object.
 */
public final class WeakRefConstructor {

    /**
     * WeakRef constructor call/new.
     * Delegates to JSWeakRef.create().
     * <p>
     * Based on ES2021 25.1.3.1
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSWeakRef.create(context, args);
    }

    /**
     * WeakRef constructor function.
     * Cannot be called without 'new'.
     *
     * @param context The execution context
     * @param thisArg The this value
     * @param args    Constructor arguments [target]
     * @return TypeError (WeakRef must be called with 'new')
     * @deprecated Use call() method instead, which properly handles both new and direct calls
     */
    @Deprecated
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        // This should be called via VM's handleCallConstructor
        // If called directly, it's an error
        return context.throwTypeError("WeakRef constructor must be called with 'new'");
    }
}
