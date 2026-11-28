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

package com.caoccao.qjs4j.builtins.collection;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.collection.JSSet;
import com.caoccao.qjs4j.core.iterator.JSIterator;

/**
 * Implementation of Set.prototype methods. Based on ES2020 Set specification.
 */
public final class SetPrototype {

    /**
     * Set.prototype.add(value) ES2020 23.2.3.1 Adds the value to the Set. Returns the Set object.
     */
    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.add called on non-Set");
        }

        JSValue value = JSValue.argumentOrUndefined(args, 0);
        set.setAdd(value);
        return set; // Return the Set object for chaining
    }

    /**
     * Set.prototype.clear() ES2020 23.2.3.2 Removes all values from the Set.
     */
    public static JSValue clear(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.clear called on non-Set");
        }

        set.setClear();
        return JSUndefined.INSTANCE;
    }

    /**
     * Set.prototype.delete(value) ES2020 23.2.3.4 Removes the value from the Set. Returns true if the value existed and
     * was removed.
     */
    public static JSValue delete(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.delete called on non-Set");
        }

        JSValue value = JSValue.argumentOrUndefined(args, 0);
        return JSBoolean.valueOf(set.setDelete(value));
    }

    /**
     * Set.prototype.difference(other) QuickJS extension.
     */
    public static JSValue difference(JSContext context, JSValue thisArg, JSValue[] args) {
        return SetOperation.DIFFERENCE.apply(context, thisArg, args);
    }

    /**
     * Set.prototype.entries() ES2020 23.2.3.5 Returns an iterator over [value, value] pairs.
     */
    public static JSValue entries(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.entries called on non-Set");
        }

        return JSIterator.setEntriesIterator(context, set);
    }

    /**
     * Set.prototype.forEach(callbackFn, thisArg) ES2020 23.2.3.6 Executes a provided function once per each value in
     * the Set, in insertion order. Note: The set can be modified while traversing it. Newly added values during
     * iteration will be visited (matching QuickJS behavior).
     */
    public static JSValue forEach(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.forEach called on non-Set");
        }

        if (args.length == 0 || !JSTypeChecking.isCallable(args[0])) {
            return context.throwTypeError("Set.prototype.forEach requires a function");
        }
        JSValue callback = args[0];

        JSValue callbackThisArg = JSValue.argumentOrUndefined(args, 1);

        JSSet.IterationCursor cursor = set.createIterationCursor();
        while (true) {
            context.checkExecutionInterrupt();
            JSValue value = set.nextIterationValue(cursor);
            if (value == null) {
                break;
            }
            JSValue[] callbackArgs = new JSValue[]{value, value, set};
            JSCall.call(context, callback, callbackThisArg, callbackArgs);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        return JSUndefined.INSTANCE;
    }

    /**
     * get Set.prototype.size ES2020 23.2.3.9 Returns the number of values in the Set.
     */
    public static JSValue getSize(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("get Set.prototype.size called on non-Set");
        }

        return JSNumber.of(set.size());
    }

    /**
     * Set.prototype.has(value) ES2020 23.2.3.7 Returns a boolean indicating whether a value exists in the Set.
     */
    public static JSValue has(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.has called on non-Set");
        }

        JSValue value = JSValue.argumentOrUndefined(args, 0);
        return JSBoolean.valueOf(set.setHas(value));
    }

    /**
     * Set.prototype.intersection(other) QuickJS extension.
     */
    public static JSValue intersection(JSContext context, JSValue thisArg, JSValue[] args) {
        return SetOperation.INTERSECTION.apply(context, thisArg, args);
    }

    /**
     * Set.prototype.isDisjointFrom(other) QuickJS extension.
     */
    public static JSValue isDisjointFrom(JSContext context, JSValue thisArg, JSValue[] args) {
        return SetOperation.IS_DISJOINT_FROM.apply(context, thisArg, args);
    }

    /**
     * Set.prototype.isSubsetOf(other) QuickJS extension.
     */
    public static JSValue isSubsetOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return SetOperation.IS_SUBSET_OF.apply(context, thisArg, args);
    }

    /**
     * Set.prototype.isSupersetOf(other) QuickJS extension.
     */
    public static JSValue isSupersetOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return SetOperation.IS_SUPERSET_OF.apply(context, thisArg, args);
    }

    /**
     * Set.prototype.keys() ES2020 23.2.3.8 Returns an iterator over values (same as values()).
     */
    public static JSValue keys(JSContext context, JSValue thisArg, JSValue[] args) {
        // In Set, keys() is the same as values()
        return values(context, thisArg, args);
    }

    /**
     * Set.prototype.symmetricDifference(other) QuickJS extension.
     */
    public static JSValue symmetricDifference(JSContext context, JSValue thisArg, JSValue[] args) {
        return SetOperation.SYMMETRIC_DIFFERENCE.apply(context, thisArg, args);
    }

    /**
     * Set.prototype.union(other) QuickJS extension.
     */
    public static JSValue union(JSContext context, JSValue thisArg, JSValue[] args) {
        return SetOperation.UNION.apply(context, thisArg, args);
    }

    /**
     * Set.prototype.values() ES2020 23.2.3.10 Returns an iterator over values.
     */
    public static JSValue values(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.values called on non-Set");
        }

        return JSIterator.setValuesIterator(context, set);
    }

}
