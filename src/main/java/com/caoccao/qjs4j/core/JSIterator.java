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
    public static final String NAME = "Iterator";
    private final JSContext context;
    private final IteratorFunction iteratorFunction;
    private boolean exhausted;

    /**
     * Create an iterator with the given iteration logic.
     */
    public JSIterator(JSContext context, IteratorFunction iteratorFunction) {
        this(context, iteratorFunction, null, true);
    }

    /**
     * Create an iterator with the given iteration logic and optional toStringTag.
     */
    public JSIterator(JSContext context, IteratorFunction iteratorFunction, String toStringTag) {
        this(context, iteratorFunction, toStringTag, true);
    }

    /**
     * Create an iterator with optional own next() installation.
     */
    public JSIterator(JSContext context, IteratorFunction iteratorFunction, String toStringTag, boolean defineOwnNext) {
        super();
        this.context = context;
        this.iteratorFunction = iteratorFunction;
        this.exhausted = false;

        // Use shared iterator-type-specific prototype if available
        JSObject iterProto = (toStringTag != null) ? context.getIteratorPrototype(toStringTag) : null;
        if (iterProto != null) {
            this.setPrototype(iterProto);
        } else {
            context.transferPrototype(this, NAME);
        }

        if (defineOwnNext) {
            // Set up 'next' as an own property for iterator kinds that do not
            // rely on prototype-dispatched next semantics.
            JSNativeFunction nextMethod = new JSNativeFunction("next", 0, (childContext, thisArg, args) -> {
                if (thisArg instanceof JSIterator iter) {
                    return iter.next();
                }
                return this.next();
            });
            this.set(PropertyKey.NEXT, nextMethod);
        }

        // Make the iterator iterable by adding [Symbol.iterator] method
        JSNativeFunction iteratorMethod = new JSNativeFunction("@@iterator", 0, (childContext, thisArg, args) -> thisArg);
        definePropertyWritableConfigurable(JSSymbol.ITERATOR, iteratorMethod);

        // Only set own toStringTag if no shared prototype provides it
        if (toStringTag != null && iterProto == null) {
            defineProperty(
                    PropertyKey.SYMBOL_TO_STRING_TAG,
                    PropertyDescriptor.dataDescriptor(new JSString(toStringTag), false, false, true));
        }
    }

    /**
     * Create an array iterator.
     */
    public static JSIterator arrayIterator(JSContext context, JSArray array) {
        final int[] index = {0};
        return new JSIterator(context, () -> {
            if (index[0] < array.getLength()) {
                JSValue value = array.get(index[0]++);
                return IteratorResult.of(context, value);
            }
            return IteratorResult.done(context);
        }, "Array Iterator", false);
    }

    /**
     * Create a Map entries iterator.
     */
    public static JSIterator mapEntriesIterator(JSContext context, JSMap map) {
        final JSMap.IterationCursor cursor = map.createIterationCursor();
        return new JSIterator(context, () -> {
            JSMap.IterationEntry entry = map.nextIterationEntry(cursor);
            if (entry != null) {
                JSArray pair = context.createJSArray();
                pair.push(entry.key());
                pair.push(entry.value());
                return IteratorResult.of(context, pair);
            }
            return IteratorResult.done(context);
        }, "Map Iterator");
    }

    /**
     * Create a Map keys iterator.
     */
    public static JSIterator mapKeysIterator(JSContext context, JSMap map) {
        final JSMap.IterationCursor cursor = map.createIterationCursor();
        return new JSIterator(context, () -> {
            JSMap.IterationEntry entry = map.nextIterationEntry(cursor);
            if (entry != null) {
                return IteratorResult.of(context, entry.key());
            }
            return IteratorResult.done(context);
        }, "Map Iterator");
    }

    /**
     * Create a Map values iterator.
     */
    public static JSIterator mapValuesIterator(JSContext context, JSMap map) {
        final JSMap.IterationCursor cursor = map.createIterationCursor();
        return new JSIterator(context, () -> {
            JSMap.IterationEntry entry = map.nextIterationEntry(cursor);
            if (entry != null) {
                return IteratorResult.of(context, entry.value());
            }
            return IteratorResult.done(context);
        }, "Map Iterator");
    }

    /**
     * Create a Set entries iterator.
     * Returns [value, value] pairs (Set uses value twice for consistency with Map).
     */
    public static JSIterator setEntriesIterator(JSContext context, JSSet set) {
        final JSSet.IterationCursor cursor = set.createIterationCursor();
        return new JSIterator(context, () -> {
            JSValue value = set.nextIterationValue(cursor);
            if (value != null) {
                JSArray pair = context.createJSArray();
                pair.push(value);
                pair.push(value); // In Set, both elements are the same value
                return IteratorResult.of(context, pair);
            }
            return IteratorResult.done(context);
        }, "Set Iterator");
    }

    /**
     * Create a Set values iterator.
     */
    public static JSIterator setValuesIterator(JSContext context, JSSet set) {
        final JSSet.IterationCursor cursor = set.createIterationCursor();
        return new JSIterator(context, () -> {
            JSValue value = set.nextIterationValue(cursor);
            if (value != null) {
                return IteratorResult.of(context, value);
            }
            return IteratorResult.done(context);
        }, "Set Iterator");
    }

    /**
     * Create a string iterator.
     */
    public static JSIterator stringIterator(JSContext context, JSString string) {
        final String str = string.value();
        final int[] index = {0};
        return new JSIterator(context, () -> {
            if (index[0] < str.length()) {
                // Handle surrogate pairs for proper Unicode iteration
                int codePoint = str.codePointAt(index[0]);
                String character = new String(Character.toChars(codePoint));
                index[0] += Character.charCount(codePoint);
                return IteratorResult.of(context, new JSString(character));
            }
            return IteratorResult.done(context);
        }, "String Iterator");
    }

    /**
     * Get the next value in the iteration.
     * Returns an object with 'value' and 'done' properties.
     */
    public JSObject next() {
        if (exhausted) {
            return IteratorResult.done(context).toObject();
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
        private final JSContext context;

        public IteratorResult(JSContext context, JSValue value, boolean done) {
            this.context = context;
            this.value = value;
            this.done = done;
        }

        public static IteratorResult done(JSContext context) {
            return new IteratorResult(context, JSUndefined.INSTANCE, true);
        }

        public static IteratorResult of(JSContext context, JSValue value) {
            return new IteratorResult(context, value, false);
        }

        public JSObject toObject() {
            JSObject obj = context.createJSObject();
            obj.set(PropertyKey.VALUE, value);
            obj.set(PropertyKey.DONE, JSBoolean.valueOf(done));
            return obj;
        }
    }
}
