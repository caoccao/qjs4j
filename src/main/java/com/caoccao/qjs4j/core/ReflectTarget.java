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

/** Common argument handling for Reflect object and property operations. */
final class ReflectTarget {
    private ReflectTarget() {
    }

    static JSValue apply(JSContext context, String name, JSValue[] args, Operation operation) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect." + name + " called on non-object");
        }
        return operation.apply(context, target, args);
    }

    static JSValue property(JSContext context, String name, JSValue[] args, PropertyOperation operation) {
        if (args.length == 0 || !(args[0] instanceof JSObject target)) {
            return context.throwTypeError("Reflect." + name + " called on non-object");
        }
        PropertyKey key = PropertyKey.fromValue(context, JSValue.argumentOrUndefined(args, 1));
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        return operation.apply(context, target, key, args);
    }

    @FunctionalInterface
    interface Operation {
        JSValue apply(JSContext context, JSObject target, JSValue[] args);
    }

    @FunctionalInterface
    interface PropertyOperation {
        JSValue apply(JSContext context, JSObject target, PropertyKey key, JSValue[] args);
    }
}
