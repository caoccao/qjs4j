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

/**
 * Implementation of Iterator constructor.
 * Based on ES2024 Iterator specification.
 * <p>
 * Iterator is an abstract class that cannot be directly constructed,
 * but serves as the base for iterator objects.
 */
public final class IteratorConstructor {

    /**
     * Iterator constructor call handler.
     * <p>
     * According to the ES2024 spec and quickjs implementation:
     * - Iterator requires 'new' (throws if called as a function)
     * - Iterator cannot be directly constructed (throws "abstract class not constructable")
     * - Only subclasses of Iterator can be constructed
     *
     * @param context The execution context
     * @param thisArg The this value (new.target for constructor calls)
     * @param args    The arguments array
     * @return Never returns normally, always throws TypeError
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        // Iterator is an abstract class and cannot be directly constructed
        // This matches quickjs behavior: js_iterator_constructor throws
        // "abstract class not constructable" when called directly
        return context.throwTypeError("Abstract class Iterator not directly constructable");
    }
}
