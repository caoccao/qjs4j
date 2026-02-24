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
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;
import com.caoccao.qjs4j.core.PropertyKey;

/**
 * Implementation of Iterator constructor.
 * Based on ES2024 Iterator specification.
 * <p>
 * Iterator is an abstract class that cannot be directly constructed,
 * but serves as the base for iterator objects via subclassing.
 */
public final class IteratorConstructor {

    /**
     * Iterator constructor call handler.
     * <p>
     * According to the ES2024 spec and QuickJS implementation:
     * - Iterator requires 'new' (throws if called as a function)
     * - Iterator cannot be directly constructed (throws "abstract class not constructable")
     * - Only subclasses of Iterator can be constructed
     *
     * @param context The execution context
     * @param thisArg The this value (the newly constructed object)
     * @param args    The arguments array
     * @return JSUndefined for subclass construction, or throws TypeError for direct construction
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        // Check new.target to determine if this is direct construction or subclass construction.
        // Following QuickJS js_iterator_constructor: if new_target IS the Iterator constructor
        // itself, throw "abstract class not constructable". Otherwise allow subclass construction.
        JSValue newTarget = context.getConstructorNewTarget();
        if (newTarget != null) {
            JSValue iteratorCtor = context.getGlobalObject().get(context, PropertyKey.ITERATOR_CAP);
            if (newTarget == iteratorCtor) {
                return context.throwTypeError("Abstract class Iterator not directly constructable");
            }
        }
        // Subclass construction - return undefined so constructFunction returns thisArg
        return JSUndefined.INSTANCE;
    }
}
