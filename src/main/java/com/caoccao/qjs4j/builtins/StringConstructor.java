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
 * Implementation of String constructor.
 * Based on ES2020 String specification.
 */
public final class StringConstructor {

    /**
     * String(value)
     * ES2020 21.1.1.1
     * Converts the argument to a string primitive value.
     * When called as a function (not with new), returns a string primitive.
     * When called with new, creates a String object wrapper.
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        // ES2020: If no argument is passed, return empty string
        if (args.length == 0) {
            return new JSString("");
        }

        // Get the value to convert to string
        JSValue value = args[0];

        // ES2020 21.1.1.1: When called as a function and the argument is a Symbol,
        // return SymbolDescriptiveString(sym) instead of calling ToString (which would throw)
        if (value instanceof JSSymbol sym) {
            String desc = sym.getDescription();
            if (desc == null) {
                return new JSString("Symbol()");
            }
            return new JSString("Symbol(" + desc + ")");
        }

        // Convert to string using ToString
        JSString strValue = JSTypeConversions.toString(context, value);

        // When called as a function (not via new), return primitive string
        // The VM will handle the "new" case separately in handleCallConstructor
        return strValue;
    }

    /**
     * String.fromCharCode(...codeUnits)
     * ES2020 21.1.2.1
     * Returns a string created from a sequence of UTF-16 code units.
     */
    public static JSValue fromCharCode(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSString("");
        }

        StringBuilder result = new StringBuilder();
        for (JSValue arg : args) {
            // Convert to uint16
            int codeUnit = JSTypeConversions.toUint16(context, arg);
            result.append((char) codeUnit);
        }

        return new JSString(result.toString());
    }

    /**
     * String.fromCodePoint(...codePoints)
     * ES2020 21.1.2.2
     * Returns a string created from a sequence of Unicode code points.
     */
    public static JSValue fromCodePoint(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return new JSString("");
        }

        StringBuilder result = new StringBuilder();
        for (JSValue arg : args) {
            // Convert to number
            double value = JSTypeConversions.toNumber(context, arg).value();

            // Check if it's an integer
            if (!Double.isFinite(value) || value != Math.floor(value)) {
                return context.throwRangeError("Invalid code point " + value);
            }

            // Convert to integer
            int codePoint = (int) value;

            // Check if code point is in valid range [0, 0x10FFFF]
            if (codePoint < 0 || codePoint > 0x10FFFF) {
                return context.throwRangeError("Invalid code point " + codePoint);
            }

            // Append the code point (Java handles surrogate pairs automatically)
            result.appendCodePoint(codePoint);
        }

        return new JSString(result.toString());
    }

    /**
     * String.raw(template, ...substitutions)
     * ES2020 21.1.2.4
     * Returns a string created from a raw template string.
     * This is used as a tag function for template literals.
     */
    public static JSValue raw(JSContext context, JSValue thisArg, JSValue[] args) {
        // If called without arguments or with undefined/null, throw TypeError
        if (args.length == 0) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        JSValue template = args[0];

        // Coerce template to object
        JSObject cooked;
        if (template instanceof JSObject) {
            cooked = (JSObject) template;
        } else if (template instanceof JSUndefined || template instanceof JSNull) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        } else {
            // For primitives, we need to convert to object
            // This is a simplified handling - in practice this would use ToObject
            return context.throwTypeError("String.raw called on non-object");
        }

        // Get the raw array
        JSValue rawValue = cooked.get("raw");
        if (rawValue instanceof JSUndefined || rawValue instanceof JSNull) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        // Coerce raw to object
        JSObject raw;
        if (rawValue instanceof JSObject) {
            raw = (JSObject) rawValue;
        } else {
            return context.throwTypeError("String.raw requires template.raw to be an object");
        }

        // Get length of raw array
        JSValue lengthValue = raw.get("length");
        long literalSegments = JSTypeConversions.toLength(context, lengthValue);

        // If length is 0, return empty string
        if (literalSegments <= 0) {
            return new JSString("");
        }

        StringBuilder result = new StringBuilder();

        // Process each segment
        for (long i = 0; i < literalSegments; i++) {
            // Get the raw string at index i
            JSValue nextVal = raw.get((int) i);
            String nextSeg = JSTypeConversions.toString(context, nextVal).value();
            result.append(nextSeg);

            // Add substitution if available (substitutions start from args[1])
            if (i + 1 < literalSegments) {
                int subIndex = (int) i + 1;
                if (subIndex < args.length) {
                    JSValue sub = args[subIndex];
                    String subStr = JSTypeConversions.toString(context, sub).value();
                    result.append(subStr);
                }
            }
        }

        return new JSString(result.toString());
    }
}
