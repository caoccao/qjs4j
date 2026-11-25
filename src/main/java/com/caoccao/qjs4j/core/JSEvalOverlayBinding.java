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

import java.util.Map;

/**
 * One caller binding a direct eval exposes to the code in its lexical environment. While that eval runs, the binding is
 * a property of the global object. When a later eval, one this code cannot see, overlays the same name, the property
 * becomes that eval's and the binding's value lives in the saved globals the later overlay restores when it ends. Reads
 * and writes go to whichever of the two currently holds the value, so a function defined in an outer eval still reaches
 * the outer caller's local while an inner eval shadows the name.
 */
public final class JSEvalOverlayBinding {
    private final boolean constant;
    private final JSContext context;
    private final Map<String, JSValue> displaced;
    private final PropertyDescriptor displacedDescriptor;
    private final String name;

    JSEvalOverlayBinding(JSContext context, String name, Map<String, JSValue> displaced,
            PropertyDescriptor displacedDescriptor, boolean constant) {
        this.context = context;
        this.name = name;
        this.displaced = displaced;
        this.displacedDescriptor = displacedDescriptor;
        this.constant = constant;
    }

    /** The current value, or null when the name has none: it was absent from the global object when overlaid. */
    public JSValue get() {
        if (displaced != null) {
            return displaced.get(name);
        }
        JSObject global = context.getGlobalObject();
        PropertyKey key = PropertyKey.fromString(name);
        JSValue value = global.get(key);
        return !(value instanceof JSUndefined) || global.has(key) ? value : null;
    }

    /** Whether a later overlay holds the global property, so the value lives in its saved globals. */
    public boolean isDisplaced() {
        return displaced != null;
    }

    /** An object whose property {@code name} reads and writes this binding, for a reference resolved later. */
    public JSObject referenceBase() {
        if (displaced == null && !constant) {
            return context.getGlobalObject();
        }
        JSNativeFunction getter = new JSNativeFunction(context, "get " + name, 0, (ctx, thisArg, args) -> {
            JSValue value = get();
            return value == null ? JSUndefined.INSTANCE : value;
        });
        getter.initializePrototypeChain(context);
        JSNativeFunction setter = new JSNativeFunction(context, "set " + name, 1, (ctx, thisArg, args) -> {
            set(JSValue.argumentOrUndefined(args, 0));
            return JSUndefined.INSTANCE;
        });
        setter.initializePrototypeChain(context);
        PropertyDescriptor descriptor = new PropertyDescriptor();
        descriptor.setGetter(getter);
        descriptor.setSetter(setter);
        descriptor.setEnumerable(true);
        descriptor.setConfigurable(true);
        JSObject holder = context.createJSObject();
        holder.defineProperty(PropertyKey.fromString(name), descriptor);
        return holder;
    }

    /**
     * Store the value where the binding currently lives. A displaced write lands in the later overlay's saved globals,
     * returns to the global object when that overlay ends, and reaches the caller's local when its own eval ends.
     */
    public void set(JSValue value) {
        if (constant || displacedDescriptor != null && !displacedDescriptor.isWritable()) {
            // A const always throws; a named function expression's immutable name only throws in strict code.
            if (constant || context.isStrictMode()) {
                context.throwTypeError("Assignment to constant variable.");
            }
            return;
        }
        if (displaced != null) {
            displaced.put(name, value);
        } else {
            context.setInBareVariableAssignment(true);
            try {
                context.getGlobalObject().set(PropertyKey.fromString(name), value);
            } finally {
                context.setInBareVariableAssignment(false);
            }
        }
    }
}
