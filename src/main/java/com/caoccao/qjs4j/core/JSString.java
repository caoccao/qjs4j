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

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * Represents a JavaScript string value. Supports atom indexing for interned strings.
 * <p>
 * A long concatenation is kept as a rope — a node over its two halves — and flattened into one Java {@link String} the
 * first time its characters are needed, as QuickJS does. Every concatenation used to copy both operands, so building a
 * string with {@code s += 'x'} in a loop was quadratic: 200,000 appends took 38 times as long as in QuickJS. The length
 * of a rope is known without flattening it, so a loop that reads {@code s.length} as it appends stays linear too.
 */
public final class JSString implements JSValue {
    public static final JSString EMPTY = new JSString("");
    /**
     * Maximum length of a JavaScript string, in UTF-16 code units.
     * <p>
     * Operations that would produce a longer string raise {@code RangeError: Invalid string length} rather than
     * exhausting the heap with an {@link OutOfMemoryError}.
     * <p>
     * Matches the 64-bit V8 string representation limit. Ropes can represent this length without a flat allocation;
     * operations that materialize characters still require sufficient host heap.
     */
    public static final int MAX_LENGTH = (1 << 29) - 24;
    public static final String NAME = "String";

    /**
     * Concatenations shorter than this are copied at once: a rope node costs more than copying a short string.
     */
    static final int ROPE_MIN_LENGTH = 256;

    private final int atomIndex;
    private JSString left;
    private final int length;
    private JSString right;
    /**
     * The characters, or null for a rope not flattened yet. Volatile so that a string flattened on one thread reads
     * consistently on another.
     */
    private volatile String value;
    private JSString(JSString left, JSString right, int length) {
        this.value = null;
        this.atomIndex = -1;
        this.length = length;
        this.left = left;
        this.right = right;
    }

    public JSString(String value) {
        this(value, -1);
    }

    public JSString(String value, int atomIndex) {
        this.value = Objects.requireNonNull(value, "A JavaScript string cannot be null");
        this.atomIndex = atomIndex;
        this.length = value.length();
        this.left = null;
        this.right = null;
    }

    public int atomIndex() {
        return atomIndex;
    }

    @Override
    public boolean equals(Object object) {
        return object == this
                || object instanceof JSString other && atomIndex == other.atomIndex && value().equals(other.value());
    }

    /**
     * Flatten a rope into one string, without recursion: a rope built by appending in a loop is as deep as it is long.
     */
    private String flatten() {
        StringBuilder stringBuilder = new StringBuilder(length);
        ArrayDeque<JSString> pending = new ArrayDeque<>();
        pending.push(this);
        while (!pending.isEmpty()) {
            JSString node = pending.pop();
            String nodeValue = node.value;
            if (nodeValue != null) {
                stringBuilder.append(nodeValue);
            } else {
                pending.push(node.right);
                pending.push(node.left);
            }
        }
        String flattened = stringBuilder.toString();
        value = flattened;
        left = null;
        right = null;
        return flattened;
    }

    @Override
    public int hashCode() {
        return 31 * value().hashCode() + atomIndex;
    }

    /**
     * Whether this string is still a rope, its characters not yet materialized.
     *
     * @return true for an unflattened rope
     */
    boolean isRope() {
        return value == null;
    }

    /**
     * The length in UTF-16 code units, known without flattening a rope.
     *
     * @return the length
     */
    public int length() {
        return length;
    }

    @Override
    public Object toJavaObject() {
        return value();
    }

    @Override
    public String toString() {
        return value();
    }

    @Override
    public JSValueType type() {
        return JSValueType.STRING;
    }

    /**
     * The characters, flattening a rope on first use.
     *
     * @return the string
     */
    public String value() {
        String currentValue = value;
        return currentValue != null ? currentValue : flatten();
    }

    /**
     * Concatenate two strings, keeping a long result as a rope. The caller checks the result against
     * {@link #MAX_LENGTH}.
     *
     * @param left
     *            the first string
     * @param right
     *            the second string
     * @return the concatenation
     */
    public static JSString concat(JSString left, JSString right) {
        if (left.length == 0) {
            return right;
        }
        if (right.length == 0) {
            return left;
        }
        int length = left.length + right.length;
        if (length < ROPE_MIN_LENGTH) {
            return new JSString(left.value() + right.value());
        }
        // Appending a short string to a rope that ends in a short leaf rebuilds the leaf instead of adding a node:
        // one node per appended character would cost tens of bytes for every two-byte character.
        JSString leftLeft = left.left;
        JSString leftRight = left.right;
        // A rope node always has both children.
        if (right.length < ROPE_MIN_LENGTH && leftLeft != null && leftRight.value != null
                && leftRight.length + right.length < ROPE_MIN_LENGTH) {
            return new JSString(leftLeft, new JSString(leftRight.value + right.value()), length);
        }
        return new JSString(left, right, length);
    }

    /** The ordinary, non-interned primitive string for one UTF-16 code unit. */
    public static JSString of(char codeUnit) {
        return codeUnit < Ascii.STRINGS.length ? Ascii.STRINGS[codeUnit] : new JSString(String.valueOf(codeUnit));
    }

    private static final class Ascii {
        private static final JSString[] STRINGS = new JSString[128];
        static {
            for (char i = 0; i < STRINGS.length; i++) {
                STRINGS[i] = new JSString(String.valueOf(i));
            }
        }
    }
}
