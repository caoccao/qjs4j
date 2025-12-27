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
     * Iterator.prototype.next()
     * Returns the next value in the iteration.
     */
    public static JSValue next(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIterator iterator)) {
            return ctx.throwError("TypeError", "Iterator.prototype.next called on non-iterator");
        }

        return iterator.next();
    }

    /**
     * Array.prototype.values()
     * Returns an iterator of array values.
     */
    public static JSValue arrayValues(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray array)) {
            return ctx.throwError("TypeError", "Array.prototype.values called on non-array");
        }

        return JSIterator.arrayIterator(array);
    }

    /**
     * Array.prototype.keys()
     * Returns an iterator of array indices.
     */
    public static JSValue arrayKeys(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray array)) {
            return ctx.throwError("TypeError", "Array.prototype.keys called on non-array");
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
     * Array.prototype.entries()
     * Returns an iterator of [index, value] pairs.
     */
    public static JSValue arrayEntries(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSArray array)) {
            return ctx.throwError("TypeError", "Array.prototype.entries called on non-array");
        }

        final int[] index = {0};
        return new JSIterator(() -> {
            if (index[0] < array.getLength()) {
                JSArray pair = new JSArray();
                pair.push(new JSNumber(index[0]));
                pair.push(array.get(index[0]));
                index[0]++;
                return JSIterator.IteratorResult.of(pair);
            }
            return JSIterator.IteratorResult.done();
        });
    }

    /**
     * String.prototype[Symbol.iterator]()
     * Returns an iterator of string characters.
     */
    public static JSValue stringIterator(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSString str)) {
            // Try to get primitive value for boxed strings
            if (thisArg instanceof JSObject obj) {
                JSValue primitiveValue = obj.get("[[PrimitiveValue]]");
                if (primitiveValue instanceof JSString boxedStr) {
                    return JSIterator.stringIterator(boxedStr);
                }
            }
            return ctx.throwError("TypeError", "String iterator called on non-string");
        }

        return JSIterator.stringIterator(str);
    }

    /**
     * Map.prototype.entries() - returns iterator
     * Returns an iterator of [key, value] pairs.
     */
    public static JSValue mapEntriesIterator(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "Map.prototype.entries called on non-Map");
        }

        return JSIterator.mapEntriesIterator(map);
    }

    /**
     * Map.prototype.keys() - returns iterator
     * Returns an iterator of keys.
     */
    public static JSValue mapKeysIterator(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "Map.prototype.keys called on non-Map");
        }

        return JSIterator.mapKeysIterator(map);
    }

    /**
     * Map.prototype.values() - returns iterator
     * Returns an iterator of values.
     */
    public static JSValue mapValuesIterator(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return ctx.throwError("TypeError", "Map.prototype.values called on non-Map");
        }

        return JSIterator.mapValuesIterator(map);
    }

    /**
     * Set.prototype.values() - returns iterator
     * Returns an iterator of values.
     */
    public static JSValue setValuesIterator(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return ctx.throwError("TypeError", "Set.prototype.values called on non-Set");
        }

        return JSIterator.setValuesIterator(set);
    }

    /**
     * Set.prototype.keys() - returns iterator
     * In Set, keys() is the same as values().
     */
    public static JSValue setKeysIterator(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return setValuesIterator(ctx, thisArg, args);
    }

    /**
     * Set.prototype.entries() - returns iterator
     * Returns an iterator of [value, value] pairs.
     */
    public static JSValue setEntriesIterator(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return ctx.throwError("TypeError", "Set.prototype.entries called on non-Set");
        }

        final java.util.Iterator<JSMap.KeyWrapper> iter = set.values().iterator();
        return new JSIterator(() -> {
            if (iter.hasNext()) {
                JSValue value = iter.next().value();
                JSArray pair = new JSArray();
                pair.push(value);
                pair.push(value); // In Set, both elements are the same
                return JSIterator.IteratorResult.of(pair);
            }
            return JSIterator.IteratorResult.done();
        });
    }
}
