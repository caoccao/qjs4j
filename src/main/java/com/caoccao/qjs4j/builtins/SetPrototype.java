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

    private static Boolean callHas(JSContext context, SetRecord setRecord, JSValue key) {
        JSValue result = JSCall.call(context, setRecord.hasFunction, setRecord.objectValue, new JSValue[]{key});
        if (context.hasPendingException()) {
            return null;
        }
        return JSTypeConversions.toBoolean(result).isBooleanTrue();
    }

    private static JSValue checkOtherKeys(JSContext context, JSSet set, SetRecord record, boolean expectPresent) {
        IteratorRecord iteratorRecord = getKeysIteratorRecord(context, record);
        if (iteratorRecord == null) {
            return JSUndefined.INSTANCE;
        }
        while (iteratorRecord.advance(context)) {
            if (set.setHas(iteratorRecord.value) != expectPresent) {
                closeIterator(context, iteratorRecord.iteratorObject);
                return JSBoolean.FALSE;
            }
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return JSBoolean.TRUE;
    }

    private static JSValue checkOwnKeys(JSContext context, JSSet set, SetRecord record, boolean expectPresent) {
        JSSet.IterationCursor cursor = set.createIterationCursor();
        while (true) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue item = set.nextIterationValue(cursor);
            if (item == null) {
                break;
            }
            Boolean found = callHas(context, record, item);
            if (found == null) {
                return JSUndefined.INSTANCE;
            }
            if (found != expectPresent) {
                return JSBoolean.FALSE;
            }
        }
        return JSBoolean.TRUE;
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

    private static void closeIterator(JSContext context, JSObject iteratorObject) {
        JSValue returnMethod = iteratorObject.get(PropertyKey.RETURN);
        if (context.hasPendingException()) {
            return;
        }
        if (!JSTypeChecking.isCallable(returnMethod)) {
            return;
        }
        JSCall.call(context, returnMethod, iteratorObject, JSValue.NO_ARGS);
    }

    private static JSSet copySet(JSContext context, JSSet sourceSet) {
        JSSet newSet = context.createJSSet();
        JSSet.IterationCursor cursor = sourceSet.createIterationCursor();
        while (true) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue value = sourceSet.nextIterationValue(cursor);
            if (value == null) {
                break;
            }
            newSet.setAdd(value);
        }
        return newSet;
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
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.difference called on non-Set");
        }
        SetRecord record = getSetRecord(context, JSValue.argumentOrUndefined(args, 0));
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        JSSet newSet = copySet(context, set);
        if (set.size() <= record.size) {
            JSSet.IterationCursor cursor = newSet.createIterationCursor();
            while (true) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
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
            IteratorRecord iteratorRecord = getKeysIteratorRecord(context, record);
            if (iteratorRecord == null) {
                return JSUndefined.INSTANCE;
            }
            while (iteratorRecord.advance(context)) {
                newSet.setDelete(iteratorRecord.value);
            }
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        return newSet;
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
            context.getVirtualMachine().checkExecutionInterruptForNative();
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

    private static IteratorRecord getKeysIteratorRecord(JSContext context, SetRecord setRecord) {
        JSValue iteratorValue = JSCall.call(context, setRecord.keysFunction, setRecord.objectValue, JSValue.NO_ARGS);
        if (context.hasPendingException()) {
            return null;
        }
        if (!(iteratorValue instanceof JSObject iteratorObject)) {
            context.throwTypeError("Iterator is not an object");
            return null;
        }
        // Cache the next method per ES2024 GetIteratorDirect
        JSValue nextMethod = iteratorObject.get(PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return null;
        }
        if (!JSTypeChecking.isCallable(nextMethod)) {
            context.throwTypeError("Iterator next is not a function");
            return null;
        }
        return new IteratorRecord(iteratorObject, nextMethod);
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
            JSValue sizeValue = object != null ? object.get(PropertyKey.SIZE) : JSUndefined.INSTANCE;
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

        JSValue hasValue = object != null ? object.get(PropertyKey.HAS) : JSUndefined.INSTANCE;
        if (context.hasPendingException()) {
            return null;
        }
        if (hasValue instanceof JSUndefined) {
            context.throwTypeError(".has is undefined");
            return null;
        }
        if (!JSTypeChecking.isCallable(hasValue)) {
            context.throwTypeError(".has is not a function");
            return null;
        }

        JSValue keysValue = object != null ? object.get(PropertyKey.KEYS) : JSUndefined.INSTANCE;
        if (context.hasPendingException()) {
            return null;
        }
        if (keysValue instanceof JSUndefined) {
            context.throwTypeError(".keys is undefined");
            return null;
        }
        if (!JSTypeChecking.isCallable(keysValue)) {
            context.throwTypeError(".keys is not a function");
            return null;
        }
        return new SetRecord(value, size, hasValue, keysValue);
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
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.intersection called on non-Set");
        }
        SetRecord record = getSetRecord(context, JSValue.argumentOrUndefined(args, 0));
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        JSSet newSet = context.createJSSet();
        if (set.size() > record.size) {
            IteratorRecord iteratorRecord = getKeysIteratorRecord(context, record);
            if (iteratorRecord == null) {
                return JSUndefined.INSTANCE;
            }
            while (iteratorRecord.advance(context)) {
                if (set.setHas(iteratorRecord.value) && !newSet.setHas(iteratorRecord.value)) {
                    newSet.setAdd(iteratorRecord.value);
                }
            }
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        } else {
            JSSet.IterationCursor cursor = set.createIterationCursor();
            while (true) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
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
     * Set.prototype.isDisjointFrom(other) QuickJS extension.
     */
    public static JSValue isDisjointFrom(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.isDisjointFrom called on non-Set");
        }
        SetRecord record = getSetRecord(context, JSValue.argumentOrUndefined(args, 0));
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        if (set.size() <= record.size) {
            return checkOwnKeys(context, set, record, false);
        }
        return checkOtherKeys(context, set, record, false);
    }

    /**
     * Set.prototype.isSubsetOf(other) QuickJS extension.
     */
    public static JSValue isSubsetOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.isSubsetOf called on non-Set");
        }
        SetRecord record = getSetRecord(context, JSValue.argumentOrUndefined(args, 0));
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        if (set.size() > record.size) {
            return JSBoolean.FALSE;
        }
        return checkOwnKeys(context, set, record, true);
    }

    /**
     * Set.prototype.isSupersetOf(other) QuickJS extension.
     */
    public static JSValue isSupersetOf(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.isSupersetOf called on non-Set");
        }
        SetRecord record = getSetRecord(context, JSValue.argumentOrUndefined(args, 0));
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        if (set.size() < record.size) {
            return JSBoolean.FALSE;
        }
        return checkOtherKeys(context, set, record, true);
    }

    /**
     * Set.prototype.keys() ES2020 23.2.3.8 Returns an iterator over values (same as values()).
     */
    public static JSValue keys(JSContext context, JSValue thisArg, JSValue[] args) {
        // In Set, keys() is the same as values()
        return values(context, thisArg, args);
    }

    private static JSValue merge(JSContext context, JSValue thisArg, JSValue[] args, boolean symmetric) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError(symmetric
                    ? "Set.prototype.symmetricDifference called on non-Set"
                    : "Set.prototype.union called on non-Set");
        }
        SetRecord record = getSetRecord(context, JSValue.argumentOrUndefined(args, 0));
        if (record == null) {
            return JSUndefined.INSTANCE;
        }

        // Per ES2024: GetKeysIterator (step 4) before copying SetData (step 5).
        // The iterator's next getter may modify the set.
        IteratorRecord iteratorRecord = getKeysIteratorRecord(context, record);
        if (iteratorRecord == null) {
            return JSUndefined.INSTANCE;
        }
        JSSet newSet = copySet(context, set);
        while (iteratorRecord.advance(context)) {
            JSValue value = iteratorRecord.value;
            if (symmetric && set.setHas(value)) {
                newSet.setDelete(value);
            } else {
                newSet.setAdd(value);
            }
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return newSet;
    }

    /**
     * Set.prototype.symmetricDifference(other) QuickJS extension.
     */
    public static JSValue symmetricDifference(JSContext context, JSValue thisArg, JSValue[] args) {
        return merge(context, thisArg, args, true);
    }

    /**
     * Set.prototype.union(other) QuickJS extension.
     */
    public static JSValue union(JSContext context, JSValue thisArg, JSValue[] args) {
        return merge(context, thisArg, args, false);
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

    private static final class IteratorRecord {
        private final JSObject iteratorObject;
        private final JSValue nextFunction;
        private JSValue value;

        IteratorRecord(JSObject iteratorObject, JSValue nextFunction) {
            this.iteratorObject = iteratorObject;
            this.nextFunction = nextFunction;
        }

        boolean advance(JSContext context) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue next = JSCall.call(context, nextFunction, iteratorObject, JSValue.NO_ARGS);
            if (context.hasPendingException()) {
                return false;
            }
            if (!(next instanceof JSObject result)) {
                context.throwTypeError("Iterator result must be an object");
                return false;
            }
            JSValue done = result.get(PropertyKey.DONE);
            if (context.hasPendingException() || JSTypeConversions.toBoolean(done).isBooleanTrue()) {
                return false;
            }
            value = result.get(PropertyKey.VALUE);
            return !context.hasPendingException();
        }
    }

    private record SetRecord(JSValue objectValue, long size, JSValue hasFunction, JSValue keysFunction) {
    }
}
