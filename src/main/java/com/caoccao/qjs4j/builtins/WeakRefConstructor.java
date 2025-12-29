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

import com.caoccao.qjs4j.core.*;

/**
 * WeakRef constructor implementation.
 * Based on ES2021 WeakRef specification.
 * <p>
 * WeakRef(target) creates a weak reference to an object.
 */
public final class WeakRefConstructor {

    /**
     * WeakRef constructor function.
     * Cannot be called without 'new'.
     *
     * @param context The execution context
     * @param thisArg The this value
     * @param args    Constructor arguments [target]
     * @return TypeError (WeakRef must be called with 'new')
     */
    public static JSValue construct(JSContext context, JSValue thisArg, JSValue[] args) {
        // This should be called via VM's handleCallConstructor
        // If called directly, it's an error
        return context.throwTypeError("WeakRef constructor must be called with 'new'");
    }

    /**
     * Create a WeakRef instance.
     * Called by VM when 'new WeakRef(target)' is executed.
     *
     * @param context The execution context
     * @param target  The target object
     * @return A new WeakRef instance or error
     */
    public static JSValue createWeakRef(JSContext context, JSValue target) {
        // Validate target is an object
        if (!(target instanceof JSObject targetObj)) {
            return context.throwTypeError("WeakRef target must be an object");
        }

        // Cannot create WeakRef to null
        if (target instanceof JSNull) {
            return context.throwTypeError("WeakRef target cannot be null");
        }

        // Create and return WeakRef
        return new JSWeakRef(targetObj);
    }
}
