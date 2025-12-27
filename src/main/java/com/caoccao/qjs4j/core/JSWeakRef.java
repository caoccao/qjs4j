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
 * - Only works with objects, not primitives
 * - Part of the WeakRefs proposal (ES2021)
 */
public final class JSWeakRef extends JSObject {
    private final WeakReference<JSObject> targetRef;

    /**
     * Create a new WeakRef.
     *
     * @param target The target object to weakly reference
     */
    public JSWeakRef(JSObject target) {
        super();
        if (target == null) {
            throw new IllegalArgumentException("WeakRef target cannot be null");
        }
        this.targetRef = new WeakReference<>(target);

        // Add deref() method
        this.set("deref", new JSNativeFunction("deref", 0, (ctx, thisArg, args) -> {
            return deref();
        }));
    }

    /**
     * Dereference the weak reference.
     * ES2021 WeakRef.prototype.deref()
     *
     * @return The target object if still alive, undefined if collected
     */
    public JSValue deref() {
        JSObject target = targetRef.get();
        return target != null ? target : JSUndefined.INSTANCE;
    }

    /**
     * Get the underlying weak reference.
     * For internal use.
     *
     * @return The Java WeakReference
     */
    public WeakReference<JSObject> getWeakReference() {
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
