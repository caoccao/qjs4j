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

package com.caoccao.qjs4j.builtins.array;

import com.caoccao.qjs4j.core.*;

/** Start and end indices after applying the relative-index rules to a captured length. */
public record ArrayIndexRange(long start, long end) {
    static ArrayIndexRange read(JSContext context, JSValue[] args, int first, long length) {
        long start = relativeIndex(context, args, first, length, 0);
        if (context.hasPendingException()) {
            return null;
        }
        long end = relativeIndex(context, args, first + 1, length, length);
        return context.hasPendingException() ? null : new ArrayIndexRange(start, end);
    }

    static long elementIndex(JSContext context, JSValue[] args, long length) {
        long value = toIntegerIndex(context, JSValue.argumentOrUndefined(args, 0));
        return value < 0 ? length + value : value;
    }

    long count() {
        return Math.max(0, end - start);
    }

    public static long relativeIndex(JSContext context, JSValue[] args, int index, long length, long defaultValue) {
        if (args.length <= index || args[index] instanceof JSUndefined) {
            return defaultValue;
        }
        long value = toIntegerIndex(context, args[index]);
        return value < 0 ? Math.max(length + value, 0) : Math.min(value, length);
    }

    static long toIntegerIndex(JSContext context, JSValue value) {
        // Java's narrowing conversion truncates towards zero, maps NaN to zero and saturates
        // infinities at the long bounds, which the caller then clamps to the array length.
        double number = value instanceof JSNumber index
                ? index.value()
                : JSTypeConversions.toNumber(context, value).value();
        return (long) number;
    }

    static long searchStart(double fromIndex, long length, boolean fromEnd) {
        long index = (long) fromIndex;
        if (index < 0) {
            index += length;
        }
        return fromEnd ? Math.max(-1L, Math.min(index, length - 1)) : Math.max(0L, Math.min(index, length));
    }

    /** Indices and count after coercing copyWithin's three arguments from left to right. */
    record Copy(long start, long target, long count) {
        static Copy read(JSContext context, JSValue[] args, long length) {
            long target = relativeIndex(context, args, 0, length, 0);
            if (context.hasPendingException()) {
                return null;
            }
            ArrayIndexRange source = ArrayIndexRange.read(context, args, 1, length);
            return source == null ? null : new Copy(source.start(), target, Math.min(source.count(), length - target));
        }

        boolean overlapsForward() {
            return start < target && target - start < count;
        }
    }
}
