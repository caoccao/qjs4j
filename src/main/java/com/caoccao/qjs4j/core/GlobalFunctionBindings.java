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

import java.util.Set;
import com.caoccao.qjs4j.exceptions.JSException;

/** Global function and var declaration checks shared by script, direct and indirect eval. */
final class GlobalFunctionBindings {
    private GlobalFunctionBindings() {
    }

    static void checkEvalVars(JSObject global, Set<String> vars, Set<String> functions) {
        for (String name : vars) {
            if (functions != null && functions.contains(name)) {
                continue;
            }
            if (!global.has(PropertyKey.fromString(name)) && !global.isExtensible()) {
                throw new JSException(global.getContext().throwTypeError("cannot define variable '" + name + "'"));
            }
        }
    }

    static PropertyDescriptor checkFunction(JSObject global, String name) {
        PropertyDescriptor existing = global.getOwnPropertyDescriptor(PropertyKey.fromString(name));
        boolean allowed = existing == null
                ? global.isExtensible()
                : existing.isConfigurable()
                        || !existing.isAccessorDescriptor() && existing.isWritable() && existing.isEnumerable();
        if (!allowed) {
            throw new JSException(global.getContext().throwTypeError("cannot define variable '" + name + "'"));
        }
        return existing;
    }

    static void finish(JSObject global, Set<String> names, boolean eval) {
        for (String name : names) {
            PropertyKey key = PropertyKey.fromString(name);
            if (!global.has(key)) {
                continue;
            }
            JSValue value = global.get(key);
            if (eval) {
                PropertyDescriptor existing = global.getOwnPropertyDescriptor(key);
                if (existing != null && !existing.isConfigurable()) {
                    PropertyDescriptor descriptor = new PropertyDescriptor();
                    descriptor.setValue(value);
                    descriptor.setWritable(existing.isWritable());
                    descriptor.setEnumerable(existing.isEnumerable());
                    descriptor.setConfigurable(false);
                    global.defineProperty(key, descriptor);
                    continue;
                }
            }
            global.defineProperty(key, value,
                    eval ? PropertyDescriptor.DataState.All : PropertyDescriptor.DataState.EnumerableWritable);
        }
    }

    static void prepareEvalFunction(JSObject global, String name, PropertyDescriptor existing) {
        if (existing == null || existing.isConfigurable()) {
            JSValue value = existing != null && existing.hasValue() ? existing.getValue() : JSUndefined.INSTANCE;
            global.defineProperty(PropertyKey.fromString(name), value, PropertyDescriptor.DataState.All);
        }
    }
}
