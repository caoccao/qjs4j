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
 * Implementation of iterator-related prototype methods.
 * Based on ES2020 iteration protocols.
 */
public final class IteratorPrototype {

    /**
     * Array.prototype.entries()
     * Returns an iterator of [index, value] pairs.
     */
    public static JSValue arrayEntries(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray array)) {
            return context.throwTypeError("Array.prototype.entries called on non-array");
        }

        final int[] index = {0};
        return new JSIterator(() -> {
            if (index[0] < array.getLength()) {
                JSArray pair = context.createJSArray();
                pair.push(new JSNumber(index[0]));
                pair.push(array.get(index[0]));
                index[0]++;
                return JSIterator.IteratorResult.of(pair);
            }
            return JSIterator.IteratorResult.done();
        });
    }

    /**
     * Array.prototype.keys()
     * Returns an iterator of array indices.
     */
    public static JSValue arrayKeys(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray array)) {
            return context.throwTypeError("Array.prototype.keys called on non-array");
        }

        final int[] index = {0};
        return new JSIterator(() -> {
            if (index[0] < array.getLength()) {
                return JSIterator.IteratorResult.of(new JSNumber(index[0]++));
            }
            return JSIterator.IteratorResult.done();
        });
    }

    /**
     * Array.prototype.values()
     * Returns an iterator of array values.
     */
    public static JSValue arrayValues(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray array)) {
            return context.throwTypeError("Array.prototype.values called on non-array");
        }

        return JSIterator.arrayIterator(array);
    }

    /**
     * Iterator.prototype.drop(limit)
     * Returns an iterator that skips the first limit elements.
     */
    public static JSValue drop(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwError("Iterator.prototype.drop not yet implemented");
    }

    /**
     * Iterator.prototype.every(predicate)
     * Tests whether all elements satisfy the predicate.
     */
    public static JSValue every(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwError("Iterator.prototype.every not yet implemented");
    }

    /**
     * Iterator.prototype.filter(predicate)
     * Returns an iterator of elements that satisfy the predicate.
     */
    public static JSValue filter(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwError("Iterator.prototype.filter not yet implemented");
    }

    /**
     * Iterator.prototype.find(predicate)
     * Returns the first element that satisfies the predicate.
     */
    public static JSValue find(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwError("Iterator.prototype.find not yet implemented");
    }

    /**
     * Iterator.prototype.flatMap(mapper)
     * Returns an iterator that applies mapper and flattens the result.
     */
    public static JSValue flatMap(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwError("Iterator.prototype.flatMap not yet implemented");
    }

    /**
     * Iterator.prototype.forEach(fn)
     * Calls fn for each element.
     */
    public static JSValue forEach(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwError("Iterator.prototype.forEach not yet implemented");
    }

    /**
     * Iterator.from(object)
     * Creates an iterator from an iterable object.
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwError("Iterator.from not yet implemented");
    }

    /**
     * Iterator.prototype.map(mapper)
     * Returns an iterator of mapped values.
     */
    public static JSValue map(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwError("Iterator.prototype.map not yet implemented");
    }

    // Iterator helper methods (ES2024) - Placeholders for now

    /**
     * Map.prototype.entries() - returns iterator
     * Returns an iterator of [key, value] pairs.
     */
    public static JSValue mapEntriesIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.entries called on non-Map");
        }

        return JSIterator.mapEntriesIterator(context, map);
    }

    /**
     * Map.prototype.keys() - returns iterator
     * Returns an iterator of keys.
     */
    public static JSValue mapKeysIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.keys called on non-Map");
        }

        return JSIterator.mapKeysIterator(map);
    }

    /**
     * Map.prototype.values() - returns iterator
     * Returns an iterator of values.
     */
    public static JSValue mapValuesIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.values called on non-Map");
        }

        return JSIterator.mapValuesIterator(map);
    }

    /**
     * Iterator.prototype.next()
     * Returns the next value in the iteration.
     */
    public static JSValue next(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIterator iterator)) {
            return context.throwTypeError("Iterator.prototype.next called on non-iterator");
        }

        return iterator.next();
    }

    /**
     * Iterator.prototype.reduce(reducer, initialValue)
     * Reduces the iterator to a single value.
     */
    public static JSValue reduce(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwError("Iterator.prototype.reduce not yet implemented");
    }

    /**
     * Set.prototype.entries() - returns iterator
     * Returns an iterator of [value, value] pairs.
     */
    public static JSValue setEntriesIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.entries called on non-Set");
        }

        final java.util.Iterator<JSMap.KeyWrapper> iter = set.values().iterator();
        return new JSIterator(() -> {
            if (iter.hasNext()) {
                JSValue value = iter.next().value();
                JSArray pair = context.createJSArray();
                pair.push(value);
                pair.push(value); // In Set, both elements are the same
                return JSIterator.IteratorResult.of(pair);
            }
            return JSIterator.IteratorResult.done();
        });
    }

    /**
     * Set.prototype.keys() - returns iterator
     * In Set, keys() is the same as values().
     */
    public static JSValue setKeysIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValuesIterator(context, thisArg, args);
    }

    /**
     * Set.prototype.values() - returns iterator
     * Returns an iterator of values.
     */
    public static JSValue setValuesIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.values called on non-Set");
        }

        return JSIterator.setValuesIterator(set);
    }

    /**
     * Iterator.prototype.some(predicate)
     * Tests whether any element satisfies the predicate.
     */
    public static JSValue some(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwError("Iterator.prototype.some not yet implemented");
    }

    /**
     * String.prototype[Symbol.iterator]()
     * Returns an iterator of string characters.
     */
    public static JSValue stringIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSString str)) {
            // Try to get primitive value for boxed strings
            if (thisArg instanceof JSObject obj) {
                JSValue primitiveValue = obj.getPrimitiveValue();
                if (primitiveValue instanceof JSString boxedStr) {
                    return JSIterator.stringIterator(boxedStr);
                }
            }
            return context.throwTypeError("String iterator called on non-string");
        }

        return JSIterator.stringIterator(str);
    }

    /**
     * Iterator.prototype.take(limit)
     * Returns an iterator of the first limit elements.
     */
    public static JSValue take(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwError("Iterator.prototype.take not yet implemented");
    }

    /**
     * Iterator.prototype.toArray()
     * Converts the iterator to an array.
     */
    public static JSValue toArray(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwError("Iterator.prototype.toArray not yet implemented");
    }
}
