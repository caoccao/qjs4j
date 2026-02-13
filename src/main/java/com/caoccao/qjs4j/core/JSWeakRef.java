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

import java.lang.ref.WeakReference;

/**
 * Represents a WeakRef object in JavaScript.
 * Based on ES2021 WeakRef specification.
 * <p>
 * WeakRef allows you to hold a weak reference to an object without preventing
 * its garbage collection. When the object is collected, deref() returns undefined.
 * <p>
 * Key characteristics:
 * - Does not prevent garbage collection of the target
 * - deref() returns the target if still alive, undefined if collected
 * - Works with objects and symbols
 * - Part of the WeakRefs proposal (ES2021)
 */
public final class JSWeakRef extends JSObject {
    public static final String NAME = "WeakRef";
    private final WeakReference<JSValue> targetRef;

    /**
     * Create a new WeakRef.
     *
     * @param target The target object to weakly reference
     */
    public JSWeakRef(JSValue target) {
        super();
        if (target == null) {
            throw new IllegalArgumentException("WeakRef target cannot be null");
        }
        this.targetRef = new WeakReference<>(target);
    }

    public static JSObject create(JSContext context, JSValue... args) {
        // WeakRef requires exactly 1 argument: target
        if (args.length == 0) {
            return context.throwTypeError("WeakRef: invalid target");
        }
        JSValue targetArg = args[0];
        if (!isWeakRefTarget(targetArg)) {
            return context.throwTypeError("WeakRef: invalid target");
        }
        JSWeakRef weakRef = new JSWeakRef(targetArg);
        context.transferPrototype(weakRef, NAME);
        return weakRef;
    }

    public static boolean isWeakRefTarget(JSValue value) {
        return value instanceof JSObject || value instanceof JSSymbol;
    }

    /**
     * Dereference the weak reference.
     * ES2021 WeakRef.prototype.deref()
     *
     * @return The target object if still alive, undefined if collected
     */
    public JSValue deref() {
        JSValue target = targetRef.get();
        return target != null ? target : JSUndefined.INSTANCE;
    }

    /**
     * Get the underlying weak reference.
     * For internal use.
     *
     * @return The Java WeakReference
     */
    public WeakReference<JSValue> getWeakReference() {
        return targetRef;
    }

    /**
     * Check if the target has been collected.
     *
     * @return True if the target has been garbage collected
     */
    public boolean isCollected() {
        return targetRef.get() == null;
    }

    @Override
    public String toString() {
        return "[object WeakRef]";
    }
}
