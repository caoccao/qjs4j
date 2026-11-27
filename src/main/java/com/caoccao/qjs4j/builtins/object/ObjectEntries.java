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

package com.caoccao.qjs4j.builtins.object;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.iterator.JSIteratorHelper;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

/** Consumes an iterable for Object.fromEntries and Object.groupBy. */
final class ObjectEntries {
    private ObjectEntries() {
    }

    private static boolean addEntry(JSContext context, JSObject result, JSValue iterator, JSValue nextItem) {
        // Steps 4d-4k: errors from here on CLOSE the iterator
        try {
            // Step 4d: If nextItem is not an Object, close iterator and throw TypeError
            if (!(nextItem instanceof JSObject entryObj)) {
                closeIteratorAndThrow(context, iterator, "Iterator value "
                        + JSTypeConversions.toString(context, nextItem).value() + " is not an entry object");
                return false;
            }

            // Step 4e: Get(nextItem, "0") — key. Errors close the iterator.
            JSValue keyValue = entryObj.get(PropertyKey.ZERO);
            if (context.hasPendingException()) {
                closeIteratorPreserveError(context, iterator);
                return false;
            }

            // Step 4g: Get(nextItem, "1") — value. Errors close the iterator.
            JSValue entryValue = entryObj.get(PropertyKey.ONE);
            if (context.hasPendingException()) {
                closeIteratorPreserveError(context, iterator);
                return false;
            }

            // Step 4k: adder = CreateDataPropertyOnObject: ToPropertyKey + CreateDataProperty
            // ToPropertyKey supports symbols. Errors close the iterator.
            PropertyKey key = PropertyKey.fromValue(context, keyValue);
            if (context.hasPendingException()) {
                closeIteratorPreserveError(context, iterator);
                return false;
            }

            // CreateDataProperty (defineProperty semantics, not Set)
            result.defineProperty(key, PropertyDescriptor.defaultData(entryValue));
        } catch (JSVirtualMachineException e) {
            // A bytecode function called during key/value processing threw.
            // Close the iterator, preserving the original error, then re-throw.
            closeAfterThrow(context, iterator, JSVirtualMachineException.class);
            throw e;
        }
        return true;
    }

    private static boolean addToGroup(JSContext context, JSObject result, JSValue iterator, JSValue callback,
            JSValue element, long index) {
        // Call callback - errors close iterator
        JSValue keyValue;
        try {
            keyValue = JSCall.call(context, callback, JSUndefined.INSTANCE, new JSValue[]{element, JSNumber.of(index)});
            if (context.hasPendingException()) {
                closeIteratorPreserveError(context, iterator);
                return false;
            }
        } catch (Exception e) {
            closeAfterThrow(context, iterator, RuntimeException.class);
            throw e;
        }

        // Convert key to property key
        PropertyKey key = PropertyKey.fromValue(context, keyValue);
        if (context.hasPendingException()) {
            closeIteratorPreserveError(context, iterator);
            return false;
        }

        // Get or create array for this key
        JSValue existingGroup = result.get(key);
        JSArray group;
        if (existingGroup instanceof JSArray existingArray) {
            group = existingArray;
        } else {
            group = context.createJSArray();
            result.set(key, group);
        }

        group.push(element);
        return true;
    }

    private static void closeAfterThrow(JSContext context, JSValue iterator,
            Class<? extends RuntimeException> ignoredOnClose) {
        JSValue saved = context.takePendingException();
        try {
            JSIteratorHelper.closeIterator(context, iterator);
        } catch (RuntimeException closeError) {
            if (!ignoredOnClose.isInstance(closeError)) {
                throw closeError;
            }
        }
        context.clearPendingException();
        if (saved != null) {
            context.setPendingException(saved);
        }
    }

    private static void closeIteratorAndThrow(JSContext context, JSValue iterator, String message) {
        JSIteratorHelper.closeIterator(context, iterator);
        // Clear any exception from closeIterator, throw our TypeError
        context.clearPendingException();
        context.throwTypeError(message);
    }

    private static JSValue closeIteratorPreserveError(JSContext context, JSValue iterator) {
        JSValue savedError = context.takePendingException();
        JSIteratorHelper.closeIterator(context, iterator);
        // Restore the original error
        if (context.hasPendingException()) {
            context.clearPendingException();
        }
        context.setPendingException(savedError);
        return JSUndefined.INSTANCE;
    }

    static JSValue collect(JSContext context, JSValue items, JSValue callback) {
        // IteratorStep and IteratorValue propagate errors before the per-element close boundary.
        JSValue iterator = JSIteratorHelper.getIterator(context, items);
        if (iterator == null) {
            if (!context.hasPendingException()) {
                return context.throwTypeError("object is not iterable");
            }
            return JSUndefined.INSTANCE;
        }

        JSObject result;
        if (callback == null) {
            result = context.createJSObject();
        } else {
            result = new JSObject(context);
            result.setPrototype(null);
        }

        long index = 0;
        while (true) {
            JSObject iterResult = JSIteratorHelper.iteratorNext(iterator, context);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (iterResult == null) {
                return context.throwTypeError("Iterator result is not an object");
            }

            JSValue doneValue = iterResult.get(PropertyKey.DONE);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                break;
            }

            JSValue element = iterResult.get(PropertyKey.VALUE);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            boolean added = callback == null
                    ? addEntry(context, result, iterator, element)
                    : addToGroup(context, result, iterator, callback, element, index++);
            if (!added) {
                return JSUndefined.INSTANCE;
            }
        }
        return result;
    }
}
