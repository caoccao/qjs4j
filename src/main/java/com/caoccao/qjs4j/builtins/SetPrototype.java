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
 * Implementation of Set.prototype methods.
 * Based on ES2020 Set specification.
 */
public final class SetPrototype {
    private static final JSValue[] NO_ARGS = new JSValue[0];

    /**
     * Set.prototype.add(value)
     * ES2020 23.2.3.1
     * Adds the value to the Set. Returns the Set object.
     */
    public static JSValue add(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.add called on non-Set");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        set.setAdd(value);
        return set; // Return the Set object for chaining
    }

    private static Boolean callHas(JSContext context, SetRecord setRecord, JSValue key) {
        JSValue result = setRecord.hasFunction.call(context, setRecord.objectValue, new JSValue[]{key});
        if (context.hasPendingException()) {
            return null;
        }
        return JSTypeConversions.toBoolean(result).isBooleanTrue();
    }

    /**
     * Set.prototype.clear()
     * ES2020 23.2.3.2
     * Removes all values from the Set.
     */
    public static JSValue clear(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.clear called on non-Set");
        }

        set.setClear();
        return JSUndefined.INSTANCE;
    }

    private static void closeIterator(JSContext context, JSObject iteratorObject) {
        JSValue returnMethod = iteratorObject.get(PropertyKey.fromString("return"), context);
        if (context.hasPendingException()) {
            return;
        }
        if (!(returnMethod instanceof JSFunction returnFunction)) {
            return;
        }
        returnFunction.call(context, iteratorObject, NO_ARGS);
    }

    private static JSSet copySet(JSContext context, JSSet sourceSet) {
        JSSet newSet = context.createJSSet();
        JSSet.IterationCursor cursor = sourceSet.createIterationCursor();
        while (true) {
            JSValue value = sourceSet.nextIterationValue(cursor);
            if (value == null) {
                break;
            }
            newSet.setAdd(value);
        }
        return newSet;
    }

    /**
     * Set.prototype.delete(value)
     * ES2020 23.2.3.4
     * Removes the value from the Set. Returns true if the value existed and was removed.
     */
    public static JSValue delete(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.delete called on non-Set");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return JSBoolean.valueOf(set.setDelete(value));
    }

    /**
     * Set.prototype.difference(other)
     * QuickJS extension.
     */
    public static JSValue difference(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.difference called on non-Set");
        }
        SetRecord record = getSetRecord(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        JSSet newSet = copySet(context, set);
        if (set.size() <= record.size) {
            JSSet.IterationCursor cursor = newSet.createIterationCursor();
            while (true) {
                JSValue item = newSet.nextIterationValue(cursor);
                if (item == null) {
                    break;
                }
                Boolean found = callHas(context, record, item);
                if (found == null) {
                    return JSUndefined.INSTANCE;
                }
                if (found) {
                    newSet.setDelete(item);
                }
            }
        } else {
            JSObject iterator = getKeysIterator(context, record);
            if (iterator == null) {
                return JSUndefined.INSTANCE;
            }
            while (true) {
                IteratorStep step = iteratorStep(context, iterator);
                if (step == null) {
                    return JSUndefined.INSTANCE;
                }
                if (step.done) {
                    break;
                }
                newSet.setDelete(step.value);
            }
        }
        return newSet;
    }

    /**
     * Set.prototype.entries()
     * ES2020 23.2.3.5
     * Returns an iterator over [value, value] pairs.
     */
    public static JSValue entries(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.entries called on non-Set");
        }

        return JSIterator.setEntriesIterator(context, set);
    }

    /**
     * Set.prototype.forEach(callbackFn, thisArg)
     * ES2020 23.2.3.6
     * Executes a provided function once per each value in the Set, in insertion order.
     * Note: The set can be modified while traversing it. Newly added values
     * during iteration will be visited (matching QuickJS behavior).
     */
    public static JSValue forEach(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.forEach called on non-Set");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Set.prototype.forEach requires a function");
        }

        JSValue callbackThisArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSSet.IterationCursor cursor = set.createIterationCursor();
        while (true) {
            JSValue value = set.nextIterationValue(cursor);
            if (value == null) {
                break;
            }
            JSValue[] callbackArgs = new JSValue[]{value, value, set};
            callback.call(context, callbackThisArg, callbackArgs);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }

        return JSUndefined.INSTANCE;
    }

    private static JSObject getKeysIterator(JSContext context, SetRecord setRecord) {
        JSValue iteratorValue = setRecord.keysFunction.call(context, setRecord.objectValue, NO_ARGS);
        if (context.hasPendingException()) {
            return null;
        }
        if (iteratorValue instanceof JSObject iteratorObject) {
            return iteratorObject;
        }
        context.throwTypeError("Iterator is not an object");
        return null;
    }

    private static SetRecord getSetRecord(JSContext context, JSValue value) {
        if (value instanceof JSNull || value instanceof JSUndefined) {
            context.throwTypeError("Cannot convert undefined or null to object");
            return null;
        }

        JSObject object = value instanceof JSObject jsObject ? jsObject : null;
        long size;
        if (value instanceof JSSet set) {
            size = set.size();
        } else {
            JSValue sizeValue = object != null
                    ? object.get(PropertyKey.fromString("size"), context)
                    : JSUndefined.INSTANCE;
            if (context.hasPendingException()) {
                return null;
            }
            double sizeDouble = JSTypeConversions.toNumber(context, sizeValue).value();
            if (context.hasPendingException()) {
                return null;
            }
            if (Double.isNaN(sizeDouble)) {
                context.throwTypeError(".size is not a number");
                return null;
            }
            if (sizeDouble < 0) {
                context.throwRangeError(".size must be positive");
                return null;
            }
            if (sizeDouble >= Long.MAX_VALUE) {
                size = Long.MAX_VALUE;
            } else {
                size = (long) sizeDouble;
            }
        }

        JSValue hasValue = object != null
                ? object.get(PropertyKey.fromString("has"), context)
                : JSUndefined.INSTANCE;
        if (context.hasPendingException()) {
            return null;
        }
        if (hasValue instanceof JSUndefined) {
            context.throwTypeError(".has is undefined");
            return null;
        }
        if (!(hasValue instanceof JSFunction hasFunction)) {
            context.throwTypeError(".has is not a function");
            return null;
        }

        JSValue keysValue = object != null
                ? object.get(PropertyKey.fromString("keys"), context)
                : JSUndefined.INSTANCE;
        if (context.hasPendingException()) {
            return null;
        }
        if (keysValue instanceof JSUndefined) {
            context.throwTypeError(".keys is undefined");
            return null;
        }
        if (!(keysValue instanceof JSFunction keysFunction)) {
            context.throwTypeError(".keys is not a function");
            return null;
        }
        return new SetRecord(value, size, hasFunction, keysFunction);
    }

    /**
     * get Set.prototype.size
     * ES2020 23.2.3.9
     * Returns the number of values in the Set.
     */
    public static JSValue getSize(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("get Set.prototype.size called on non-Set");
        }

        return JSNumber.of(set.size());
    }

    /**
     * Set.prototype.has(value)
     * ES2020 23.2.3.7
     * Returns a boolean indicating whether a value exists in the Set.
     */
    public static JSValue has(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.has called on non-Set");
        }

        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return JSBoolean.valueOf(set.setHas(value));
    }

    /**
     * Set.prototype.intersection(other)
     * QuickJS extension.
     */
    public static JSValue intersection(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.intersection called on non-Set");
        }
        SetRecord record = getSetRecord(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        JSSet newSet = context.createJSSet();
        if (set.size() > record.size) {
            JSObject iterator = getKeysIterator(context, record);
            if (iterator == null) {
                return JSUndefined.INSTANCE;
            }
            while (true) {
                IteratorStep step = iteratorStep(context, iterator);
                if (step == null) {
                    return JSUndefined.INSTANCE;
                }
                if (step.done) {
                    break;
                }
                if (set.setHas(step.value) && !newSet.setHas(step.value)) {
                    newSet.setAdd(step.value);
                }
            }
        } else {
            JSSet.IterationCursor cursor = set.createIterationCursor();
            while (true) {
                JSValue item = set.nextIterationValue(cursor);
                if (item == null) {
                    break;
                }
                Boolean found = callHas(context, record, item);
                if (found == null) {
                    return JSUndefined.INSTANCE;
                }
                if (found && !newSet.setHas(item)) {
                    newSet.setAdd(item);
                }
            }
        }
        return newSet;
    }

    /**
     * Set.prototype.isDisjointFrom(other)
     * QuickJS extension.
     */
    public static JSValue isDisjointFrom(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.isDisjointFrom called on non-Set");
        }
        SetRecord record = getSetRecord(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        if (set.size() <= record.size) {
            JSSet.IterationCursor cursor = set.createIterationCursor();
            while (true) {
                JSValue item = set.nextIterationValue(cursor);
                if (item == null) {
                    break;
                }
                Boolean found = callHas(context, record, item);
                if (found == null) {
                    return JSUndefined.INSTANCE;
                }
                if (found) {
                    return JSBoolean.FALSE;
                }
            }
        } else {
            JSObject iterator = getKeysIterator(context, record);
            if (iterator == null) {
                return JSUndefined.INSTANCE;
            }
            while (true) {
                IteratorStep step = iteratorStep(context, iterator);
                if (step == null) {
                    return JSUndefined.INSTANCE;
                }
                if (step.done) {
                    break;
                }
                if (set.setHas(step.value)) {
                    closeIterator(context, iterator);
                    return JSBoolean.FALSE;
                }
            }
        }
        return JSBoolean.TRUE;
    }

    /**
     * Set.prototype.isSubsetOf(other)
     * QuickJS extension.
     */
    public static JSValue isSubsetOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.isSubsetOf called on non-Set");
        }
        SetRecord record = getSetRecord(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        if (set.size() > record.size) {
            return JSBoolean.FALSE;
        }
        JSSet.IterationCursor cursor = set.createIterationCursor();
        while (true) {
            JSValue item = set.nextIterationValue(cursor);
            if (item == null) {
                break;
            }
            Boolean found = callHas(context, record, item);
            if (found == null) {
                return JSUndefined.INSTANCE;
            }
            if (!found) {
                return JSBoolean.FALSE;
            }
        }
        return JSBoolean.TRUE;
    }

    /**
     * Set.prototype.isSupersetOf(other)
     * QuickJS extension.
     */
    public static JSValue isSupersetOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.isSupersetOf called on non-Set");
        }
        SetRecord record = getSetRecord(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        if (set.size() < record.size) {
            return JSBoolean.FALSE;
        }
        JSObject iterator = getKeysIterator(context, record);
        if (iterator == null) {
            return JSUndefined.INSTANCE;
        }
        while (true) {
            IteratorStep step = iteratorStep(context, iterator);
            if (step == null) {
                return JSUndefined.INSTANCE;
            }
            if (step.done) {
                break;
            }
            if (!set.setHas(step.value)) {
                closeIterator(context, iterator);
                return JSBoolean.FALSE;
            }
        }
        return JSBoolean.TRUE;
    }

    private static IteratorStep iteratorStep(JSContext context, JSObject iteratorObject) {
        JSObject nextResult = JSIteratorHelper.iteratorNext(iteratorObject, context);
        if (context.hasPendingException()) {
            return null;
        }
        if (nextResult == null) {
            context.throwTypeError("Iterator result must be an object");
            return null;
        }
        JSValue done = nextResult.get("done");
        if (JSTypeConversions.toBoolean(done).isBooleanTrue()) {
            return new IteratorStep(JSUndefined.INSTANCE, true);
        }
        return new IteratorStep(nextResult.get("value"), false);
    }

    /**
     * Set.prototype.keys()
     * ES2020 23.2.3.8
     * Returns an iterator over values (same as values()).
     */
    public static JSValue keys(JSContext context, JSValue thisArg, JSValue[] args) {
        // In Set, keys() is the same as values()
        return values(context, thisArg, args);
    }

    /**
     * Set.prototype.symmetricDifference(other)
     * QuickJS extension.
     */
    public static JSValue symmetricDifference(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.symmetricDifference called on non-Set");
        }
        SetRecord record = getSetRecord(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        JSSet newSet = copySet(context, set);
        JSObject iterator = getKeysIterator(context, record);
        if (iterator == null) {
            return JSUndefined.INSTANCE;
        }
        while (true) {
            IteratorStep step = iteratorStep(context, iterator);
            if (step == null) {
                return JSUndefined.INSTANCE;
            }
            if (step.done) {
                break;
            }
            boolean presentInThis = set.setHas(step.value);
            boolean presentInNewSet = newSet.setHas(step.value);
            if (presentInThis) {
                newSet.setDelete(step.value);
            } else if (!presentInNewSet) {
                newSet.setAdd(step.value);
            }
        }
        return newSet;
    }

    /**
     * Set.prototype.union(other)
     * QuickJS extension.
     */
    public static JSValue union(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.union called on non-Set");
        }
        SetRecord record = getSetRecord(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        JSSet newSet = copySet(context, set);
        JSObject iterator = getKeysIterator(context, record);
        if (iterator == null) {
            return JSUndefined.INSTANCE;
        }
        while (true) {
            IteratorStep step = iteratorStep(context, iterator);
            if (step == null) {
                return JSUndefined.INSTANCE;
            }
            if (step.done) {
                break;
            }
            newSet.setAdd(step.value);
        }
        return newSet;
    }

    /**
     * Set.prototype.values()
     * ES2020 23.2.3.10
     * Returns an iterator over values.
     */
    public static JSValue values(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.values called on non-Set");
        }

        return JSIterator.setValuesIterator(context, set);
    }

    private record IteratorStep(JSValue value, boolean done) {
    }

    private record SetRecord(JSValue objectValue, long size, JSFunction hasFunction, JSFunction keysFunction) {
    }
}
