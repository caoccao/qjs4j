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
import com.caoccao.qjs4j.core.JSGenerator;
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;

/**
 * Implementation of Generator.prototype methods.
 * Based on ES2020 Generator specification (simplified).
 */
public final class GeneratorPrototype {

    /**
     * Generator.prototype.next(value)
     * ES2020 25.5.1.2
     * Resumes the generator and returns an IteratorResult.
     */
    public static JSValue next(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSGenerator generator)) {
            return context.throwTypeError("Generator.prototype.next called on non-generator");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return generator.next(value);
    }

    /**
     * Generator.prototype.return(value)
     * ES2020 25.5.1.3
     * Finishes the generator and returns the given value.
     */
    public static JSValue returnMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSGenerator generator)) {
            return context.throwTypeError("Generator.prototype.return called on non-generator");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return generator.returnMethod(value);
    }

    /**
     * Generator.prototype.throw(exception)
     * ES2020 25.5.1.4
     * Throws an exception into the generator.
     */
    public static JSValue throwMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSGenerator generator)) {
            return context.throwTypeError("Generator.prototype.throw called on non-generator");
        }

        JSValue exception = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        try {
            return generator.throwMethod(exception);
        } catch (RuntimeException e) {
            return context.throwError(e.getMessage());
        }
    }
}
