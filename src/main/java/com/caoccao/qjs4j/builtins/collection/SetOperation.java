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

/** Set composition methods, including observable access to the other set-like object's methods. */
enum SetOperation {
    DIFFERENCE("difference"),
    INTERSECTION("intersection"),
    IS_DISJOINT_FROM("isDisjointFrom"),
    IS_SUBSET_OF("isSubsetOf"),
    IS_SUPERSET_OF("isSupersetOf"),
    SYMMETRIC_DIFFERENCE("symmetricDifference"),
    UNION("union");

    private final String method;

    SetOperation(String method) {
        this.method = method;
    }

    JSValue apply(JSContext context, JSValue receiver, JSValue[] args) {
        if (!(receiver instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype." + method + " called on non-Set");
        }
        SetRecord other = getSetRecord(context, JSValue.argumentOrUndefined(args, 0));
        if (other == null) {
            return JSUndefined.INSTANCE;
        }
        return switch (this) {
            case IS_SUBSET_OF -> set.size() > other.size ? JSBoolean.FALSE : visitOwnKeys(context, set, other, null);
            case IS_SUPERSET_OF ->
                set.size() < other.size ? JSBoolean.FALSE : checkOtherKeys(context, set, other, true);
            case IS_DISJOINT_FROM -> set.size() <= other.size
                    ? visitOwnKeys(context, set, other, null)
                    : checkOtherKeys(context, set, other, false);
            case DIFFERENCE, INTERSECTION -> filter(context, set, other);
            case UNION, SYMMETRIC_DIFFERENCE -> merge(context, set, other);
        };
    }

    private JSValue filter(JSContext context, JSSet set, SetRecord other) {
        JSSet result = this == DIFFERENCE ? copySet(context, set) : context.createJSSet();
        if (set.size() <= other.size) {
            // Difference iterates its snapshot. Intersection observes mutations of the receiver.
            return visitOwnKeys(context, this == DIFFERENCE ? result : set, other, result);
        }
        IteratorRecord keys = getKeysIteratorRecord(context, other);
        if (keys == null) {
            return JSUndefined.INSTANCE;
        }
        while (keys.advance(context)) {
            if (this == DIFFERENCE) {
                result.setDelete(keys.value);
            } else if (set.setHas(keys.value)) {
                result.setAdd(keys.value);
            }
        }
        return context.hasPendingException() ? JSUndefined.INSTANCE : result;
    }

    private JSValue merge(JSContext context, JSSet set, SetRecord record) {
        // GetKeysIterator before copying SetData.
        // The iterator's next getter may modify the set.
        IteratorRecord iteratorRecord = getKeysIteratorRecord(context, record);
        if (iteratorRecord == null) {
            return JSUndefined.INSTANCE;
        }
        JSSet newSet = copySet(context, set);
        while (iteratorRecord.advance(context)) {
            JSValue value = iteratorRecord.value;
            if (this == SYMMETRIC_DIFFERENCE && set.setHas(value)) {
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

    private JSValue visitOwnKeys(JSContext context, JSSet source, SetRecord other, JSSet result) {
        JSSet.IterationCursor cursor = source.createIterationCursor();
        while (true) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            JSValue item = source.nextIterationValue(cursor);
            if (item == null) {
                return result == null ? JSBoolean.TRUE : result;
            }
            JSValue has = JSCall.call(context, other.hasFunction, other.objectValue, new JSValue[]{item});
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            boolean present = JSTypeConversions.toBoolean(has).isBooleanTrue();
            if (result == null) {
                if (present != (this == IS_SUBSET_OF)) {
                    return JSBoolean.FALSE;
                }
            } else if (present) {
                if (this == DIFFERENCE) {
                    result.setDelete(item);
                } else {
                    result.setAdd(item);
                }
            }
        }
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

    private static JSValue getMethod(JSContext context, JSObject object, PropertyKey key) {
        JSValue method = object != null ? object.get(key) : JSUndefined.INSTANCE;
        if (context.hasPendingException()) {
            return null;
        }
        if (!JSTypeChecking.isCallable(method)) {
            context.throwTypeError("." + key.toPropertyString()
                    + (method instanceof JSUndefined ? " is undefined" : " is not a function"));
            return null;
        }
        return method;
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

        JSValue hasValue = getMethod(context, object, PropertyKey.HAS);
        if (hasValue == null) {
            return null;
        }
        JSValue keysValue = getMethod(context, object, PropertyKey.KEYS);
        if (keysValue == null) {
            return null;
        }
        return new SetRecord(value, size, hasValue, keysValue);
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
