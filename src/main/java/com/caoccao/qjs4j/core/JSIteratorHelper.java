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

/**
 * Helper utilities for working with iterators and the iteration protocols.
 * Provides support for for...of loops and other iteration patterns.
 */
public final class JSIteratorHelper {

    /**
     * Execute a for...of loop over an iterable.
     * This is a helper for bytecode that implements for...of loops.
     *
     * @param context  The execution context
     * @param iterable The iterable to loop over
     * @param callback Function to call for each value
     */
    public static void forOf(JSContext context, JSValue iterable, IterationCallback callback) {
        // Get the iterator
        JSValue iterator = getIterator(context, iterable);
        if (iterator == null) {
            context.throwTypeError("Object is not iterable");
            return;
        }

        // Iterate until done
        while (true) {
            JSObject result = iteratorNext(iterator, context);
            if (result == null) {
                break;
            }

            // Check if done (pass context to invoke getters)
            JSValue doneValue = result.get(PropertyKey.DONE, context);
            if (context.hasPendingException()) {
                break;
            }
            boolean done = JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE;

            if (done) {
                break;
            }

            // Get the value and call the callback (pass context to invoke getters)
            JSValue value = result.get(PropertyKey.VALUE, context);
            if (context.hasPendingException()) {
                break;
            }
            if (!callback.iterate(value)) {
                // Callback returned false, break early
                break;
            }
        }
    }

    /**
     * Get an iterator from an iterable object.
     * Calls the object's [Symbol.iterator] method to get an iterator.
     *
     * @param context  The execution context
     * @param iterable The iterable object
     * @return An iterator, or null if the object is not iterable
     */
    public static JSValue getIterator(JSContext context, JSValue iterable) {
        // Handle string primitives specially
        if (iterable instanceof JSString jsString) {
            return JSIterator.stringIterator(context, jsString);
        }

        if (!(iterable instanceof JSObject iterableObj)) {
            return null;
        }

        // Get the [Symbol.iterator] method
        JSValue iteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ITERATOR, context);
        if (context.hasPendingException()) {
            return null;
        }

        if (!(iteratorMethod instanceof JSFunction iteratorFunc)) {
            return null;
        }

        // Call the iterator method to get the iterator
        JSValue iterator = iteratorFunc.call(context, iterable, new JSValue[0]);
        if (context.hasPendingException()) {
            return null;
        }

        // Verify it's an object with a next method
        if (iterator instanceof JSObject iteratorObj) {
            JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT, context);
            if (context.hasPendingException()) {
                return null;
            }
            if (nextMethod instanceof JSFunction) {
                return iterator;
            }
        }

        return null;
    }

    /**
     * Check if a value is iterable (has Symbol.iterator).
     *
     * @param value The value to check
     * @return true if iterable, false otherwise
     */
    public static boolean isIterable(JSValue value) {
        // Strings are always iterable
        if (value instanceof JSString) {
            return true;
        }

        if (!(value instanceof JSObject obj)) {
            return false;
        }

        JSValue iteratorMethod = obj.get(PropertyKey.SYMBOL_ITERATOR);
        return iteratorMethod instanceof JSFunction;
    }

    /**
     * Call next() on an iterator and return the result.
     *
     * @param iterator The iterator object
     * @param context  The execution context
     * @return The iterator result object with {value, done}
     */
    public static JSObject iteratorNext(JSValue iterator, JSContext context) {
        if (!(iterator instanceof JSObject iteratorObj)) {
            return null;
        }

        // Handle JSIterator instances directly
        if (iterator instanceof JSIterator jsIterator) {
            return jsIterator.next();
        }

        // Handle JSGenerator instances directly
        if (iterator instanceof JSGenerator jsGenerator) {
            return jsGenerator.next(JSUndefined.INSTANCE);
        }

        // Generic iterator protocol: call the next() method
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT, context);
        if (context.hasPendingException()) {
            return null;
        }
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            return null;
        }

        JSValue result = nextFunc.call(context, iterator, new JSValue[0]);

        // Result should be an object with value and done properties
        if (result instanceof JSObject resultObj) {
            return resultObj;
        }

        return null;
    }

    /**
     * IterableToList ( items ) — ES2024 7.4.7
     * Strictly follows the spec: calls GetIterator, then repeatedly calls
     * IteratorStep/IteratorValue, propagating abrupt completions at every step.
     *
     * @param context  The execution context
     * @param items    The iterable value
     * @return A JSArray of the iterated values, or null if an exception was set
     */
    public static JSArray iterableToList(JSContext context, JSValue items) {
        // Handle string primitives — strings are iterable
        if (items instanceof JSString jsString) {
            JSArray result = context.createJSArray();
            String s = jsString.value();
            for (int i = 0; i < s.length(); i++) {
                result.push(new JSString(String.valueOf(s.charAt(i))));
            }
            return result;
        }

        // Step 1-2: GetIterator(items, sync)
        if (!(items instanceof JSObject itemsObj)) {
            context.throwTypeError("Value is not iterable");
            return null;
        }

        // GetMethod(items, @@iterator) — get the Symbol.iterator property
        JSValue iteratorMethod = itemsObj.get(PropertyKey.SYMBOL_ITERATOR, context);
        if (context.hasPendingException()) {
            return null;
        }

        if (iteratorMethod == null || iteratorMethod instanceof JSUndefined || iteratorMethod instanceof JSNull) {
            context.throwTypeError("object is not iterable");
            return null;
        }
        if (!(iteratorMethod instanceof JSFunction iteratorFunc)) {
            context.throwTypeError("object is not iterable");
            return null;
        }

        // Call(method, items) to get the iterator
        JSValue iterator = iteratorFunc.call(context, items, new JSValue[0]);
        if (context.hasPendingException()) {
            return null;
        }
        if (!(iterator instanceof JSObject iteratorObj)) {
            context.throwTypeError("iterator must return an object");
            return null;
        }

        // GetV(iterator, "next")
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT, context);
        if (context.hasPendingException()) {
            return null;
        }
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            context.throwTypeError("iterator next is not a function");
            return null;
        }

        // Iterate
        JSArray result = context.createJSArray();
        while (true) {
            // IteratorStep: Call iterator.next()
            JSValue nextResult = nextFunc.call(context, iterator, new JSValue[0]);
            if (context.hasPendingException()) {
                return null;
            }
            if (!(nextResult instanceof JSObject nextResultObj)) {
                context.throwTypeError("iterator result is not an object");
                return null;
            }

            // IteratorComplete: Get "done"
            JSValue doneValue = nextResultObj.get(PropertyKey.DONE, context);
            if (context.hasPendingException()) {
                return null;
            }
            if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                break;
            }

            // IteratorValue: Get "value"
            JSValue value = nextResultObj.get(PropertyKey.VALUE, context);
            if (context.hasPendingException()) {
                return null;
            }
            result.push(value);
        }
        return result;
    }

    /**
     * Convert an iterable to an array.
     *
     * @param iterable The iterable to convert
     * @param context  The execution context
     * @return A JSArray containing all values from the iterable
     */
    public static JSArray toArray(JSContext context, JSValue iterable) {
        JSArray result = context.createJSArray();

        forOf(context, iterable, (value) -> {
            result.push(value);
            return true;
        });

        return result;
    }

    /**
     * Convert a JSIterator to an array.
     *
     * @param context  the context
     * @param iterator The iterator to convert
     * @return A JSArray containing all values from the iterator
     */
    public static JSArray toArray(JSContext context, JSIterator iterator) {
        JSArray result = context.createJSArray();
        while (true) {
            JSObject iterResult = iterator.next();
            JSValue doneValue = iterResult.get("done");
            boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
            if (done) {
                break;
            }
            JSValue value = iterResult.get("value");
            result.push(value);
        }
        return result;
    }

    /**
     * Functional interface for iteration callbacks.
     */
    @FunctionalInterface
    public interface IterationCallback {
        /**
         * Called for each iterated value.
         *
         * @param value The current value
         * @return true to continue iteration, false to break
         */
        boolean iterate(JSValue value);
    }
}
