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
 * Represents a JavaScript Iterator object.
 * Based on ES2020 Iterator protocol.
 * <p>
 * An iterator is an object that implements the iterator protocol by having a next() method
 * that returns an object with two properties: value and done.
 */
public class JSIterator extends JSObject {
    private final IteratorFunction iteratorFunction;
    private boolean exhausted;

    /**
     * Create an iterator with the given iteration logic.
     */
    public JSIterator(IteratorFunction iteratorFunction) {
        super();
        this.iteratorFunction = iteratorFunction;
        this.exhausted = false;

        // Set up 'next' method as a property (required by iterator protocol)
        JSNativeFunction nextMethod = new JSNativeFunction("next", 0, (context, thisArg, args) -> {
            if (thisArg instanceof JSIterator iter) {
                return iter.next();
            }
            return this.next();
        });
        this.set("next", nextMethod);

        // Make the iterator iterable by adding [Symbol.iterator] method
        JSNativeFunction iteratorMethod = new JSNativeFunction("@@iterator", 0, (context, thisArg, args) -> this);
        this.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), iteratorMethod);
    }

    /**
     * Create an array iterator.
     */
    public static JSIterator arrayIterator(JSArray array) {
        final int[] index = {0};
        return new JSIterator(() -> {
            if (index[0] < array.getLength()) {
                JSValue value = array.get(index[0]++);
                return IteratorResult.of(value);
            }
            return IteratorResult.done();
        });
    }

    /**
     * Create a Map entries iterator.
     */
    public static JSIterator mapEntriesIterator(JSContext context, JSMap map) {
        final java.util.Iterator<java.util.Map.Entry<JSMap.KeyWrapper, JSValue>> iter = map.entries().iterator();
        return new JSIterator(() -> {
            if (iter.hasNext()) {
                java.util.Map.Entry<JSMap.KeyWrapper, JSValue> entry = iter.next();
                JSArray pair = context.createJSArray();
                pair.push(entry.getKey().value());
                pair.push(entry.getValue());
                return IteratorResult.of(pair);
            }
            return IteratorResult.done();
        });
    }

    /**
     * Create a Map keys iterator.
     */
    public static JSIterator mapKeysIterator(JSMap map) {
        final java.util.Iterator<JSMap.KeyWrapper> iter = map.keys().iterator();
        return new JSIterator(() -> {
            if (iter.hasNext()) {
                JSValue key = iter.next().value();
                return IteratorResult.of(key);
            }
            return IteratorResult.done();
        });
    }

    /**
     * Create a Map values iterator.
     */
    public static JSIterator mapValuesIterator(JSMap map) {
        final java.util.Iterator<JSValue> iter = map.values().iterator();
        return new JSIterator(() -> {
            if (iter.hasNext()) {
                JSValue value = iter.next();
                return IteratorResult.of(value);
            }
            return IteratorResult.done();
        });
    }

    /**
     * Create a Set entries iterator.
     * Returns [value, value] pairs (Set uses value twice for consistency with Map).
     */
    public static JSIterator setEntriesIterator(JSContext context, JSSet set) {
        final java.util.Iterator<JSMap.KeyWrapper> iter = set.values().iterator();
        return new JSIterator(() -> {
            if (iter.hasNext()) {
                JSValue value = iter.next().value();
                JSArray pair = context.createJSArray();
                pair.push(value);
                pair.push(value); // In Set, both elements are the same value
                return IteratorResult.of(pair);
            }
            return IteratorResult.done();
        });
    }

    /**
     * Create a Set values iterator.
     */
    public static JSIterator setValuesIterator(JSSet set) {
        final java.util.Iterator<JSMap.KeyWrapper> iter = set.values().iterator();
        return new JSIterator(() -> {
            if (iter.hasNext()) {
                JSValue value = iter.next().value();
                return IteratorResult.of(value);
            }
            return IteratorResult.done();
        });
    }

    /**
     * Create a string iterator.
     */
    public static JSIterator stringIterator(JSString string) {
        final String str = string.value();
        final int[] index = {0};
        return new JSIterator(() -> {
            if (index[0] < str.length()) {
                // Handle surrogate pairs for proper Unicode iteration
                int codePoint = str.codePointAt(index[0]);
                String character = new String(Character.toChars(codePoint));
                index[0] += Character.charCount(codePoint);
                return IteratorResult.of(new JSString(character));
            }
            return IteratorResult.done();
        });
    }

    /**
     * Get the next value in the iteration.
     * Returns an object with 'value' and 'done' properties.
     */
    public JSObject next() {
        if (exhausted) {
            return IteratorResult.done().toObject();
        }

        IteratorResult result = iteratorFunction.next();
        if (result.done) {
            exhausted = true;
        }
        return result.toObject();
    }

    @Override
    public String toString() {
        return "[object Iterator]";
    }

    /**
     * Functional interface for iterator logic.
     */
    @FunctionalInterface
    public interface IteratorFunction {
        /**
         * Get the next iteration result.
         *
         * @return IteratorResult with value and done status
         */
        IteratorResult next();
    }

    /**
     * Represents an iterator result: { value: any, done: boolean }
     */
    public static class IteratorResult {
        public final boolean done;
        public final JSValue value;

        public IteratorResult(JSValue value, boolean done) {
            this.value = value;
            this.done = done;
        }

        public static IteratorResult done() {
            return new IteratorResult(JSUndefined.INSTANCE, true);
        }

        public static IteratorResult of(JSValue value) {
            return new IteratorResult(value, false);
        }

        public JSObject toObject() {
            JSObject obj = new JSObject();
            obj.set("value", value);
            obj.set("done", JSBoolean.valueOf(done));
            return obj;
        }
    }
}
