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
 * Implementation of JavaScript String.prototype methods.
 * Based on ES2020 String.prototype specification.
 */
public final class StringPrototype {

    /**
     * String.prototype.charAt(index)
     * ES2020 21.1.3.1
     */
    public static JSValue charAt(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();
        long pos = args.length > 0 ? (long) JSTypeConversions.toInteger(args[0]) : 0;

        if (pos < 0 || pos >= s.length()) {
            return new JSString("");
        }

        return new JSString(String.valueOf(s.charAt((int) pos)));
    }

    /**
     * String.prototype.charCodeAt(index)
     * ES2020 21.1.3.2
     */
    public static JSValue charCodeAt(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();
        long pos = args.length > 0 ? (long) JSTypeConversions.toInteger(args[0]) : 0;

        if (pos < 0 || pos >= s.length()) {
            return new JSNumber(Double.NaN);
        }

        return new JSNumber(s.charAt((int) pos));
    }

    /**
     * String.prototype.codePointAt(index)
     * ES2020 21.1.3.3
     */
    public static JSValue codePointAt(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();
        long pos = args.length > 0 ? (long) JSTypeConversions.toInteger(args[0]) : 0;

        if (pos < 0 || pos >= s.length()) {
            return JSUndefined.INSTANCE;
        }

        return new JSNumber(s.codePointAt((int) pos));
    }

    /**
     * String.prototype.concat(...strings)
     * ES2020 21.1.3.4
     */
    public static JSValue concat(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        StringBuilder result = new StringBuilder(str.value());

        for (JSValue arg : args) {
            result.append(JSTypeConversions.toString(arg).value());
        }

        return new JSString(result.toString());
    }

    /**
     * String.prototype.endsWith(searchString[, endPosition])
     * ES2020 21.1.3.6
     */
    public static JSValue endsWith(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        String searchStr = JSTypeConversions.toString(args[0]).value();
        long endPosition = args.length > 1 ? (long) JSTypeConversions.toInteger(args[1]) : s.length();
        endPosition = Math.max(0, Math.min(endPosition, s.length()));

        int start = (int) (endPosition - searchStr.length());
        if (start < 0) {
            return JSBoolean.FALSE;
        }

        return JSBoolean.valueOf(s.substring(start, (int) endPosition).equals(searchStr));
    }

    /**
     * String.prototype.startsWith(searchString[, position])
     * ES2020 21.1.3.21
     */
    public static JSValue startsWith(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        String searchStr = JSTypeConversions.toString(args[0]).value();
        long position = args.length > 1 ? (long) JSTypeConversions.toInteger(args[1]) : 0;
        position = Math.max(0, Math.min(position, s.length()));

        return JSBoolean.valueOf(s.substring((int) position).startsWith(searchStr));
    }

    /**
     * String.prototype.includes(searchString[, position])
     * ES2020 21.1.3.7
     */
    public static JSValue includes(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        String searchStr = JSTypeConversions.toString(args[0]).value();
        long position = args.length > 1 ? (long) JSTypeConversions.toInteger(args[1]) : 0;
        position = Math.max(0, Math.min(position, s.length()));

        return JSBoolean.valueOf(s.indexOf(searchStr, (int) position) >= 0);
    }

    /**
     * String.prototype.indexOf(searchString[, position])
     * ES2020 21.1.3.8
     */
    public static JSValue indexOf(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();

        if (args.length == 0) {
            return new JSNumber(-1);
        }

        String searchStr = JSTypeConversions.toString(args[0]).value();
        long position = args.length > 1 ? (long) JSTypeConversions.toInteger(args[1]) : 0;
        position = Math.max(0, Math.min(position, s.length()));

        int index = s.indexOf(searchStr, (int) position);
        return new JSNumber(index);
    }

    /**
     * String.prototype.lastIndexOf(searchString[, position])
     * ES2020 21.1.3.9
     */
    public static JSValue lastIndexOf(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();

        if (args.length == 0) {
            return new JSNumber(-1);
        }

        String searchStr = JSTypeConversions.toString(args[0]).value();
        long position = args.length > 1 ? (long) JSTypeConversions.toInteger(args[1]) : s.length();
        position = Math.max(0, Math.min(position, s.length()));

        int index = s.lastIndexOf(searchStr, (int) position);
        return new JSNumber(index);
    }

    /**
     * String.prototype.padEnd(targetLength[, padString])
     * ES2020 21.1.3.11
     */
    public static JSValue padEnd(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();

        long maxLength = args.length > 0 ? (long) JSTypeConversions.toInteger(args[0]) : 0;
        if (maxLength <= s.length()) {
            return str;
        }

        String fillStr = args.length > 1 ? JSTypeConversions.toString(args[1]).value() : " ";
        if (fillStr.isEmpty()) {
            return str;
        }

        int fillLen = (int) (maxLength - s.length());
        StringBuilder result = new StringBuilder(s);

        while (result.length() < maxLength) {
            for (int i = 0; i < fillStr.length() && result.length() < maxLength; i++) {
                result.append(fillStr.charAt(i));
            }
        }

        return new JSString(result.toString());
    }

    /**
     * String.prototype.padStart(targetLength[, padString])
     * ES2020 21.1.3.12
     */
    public static JSValue padStart(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();

        long maxLength = args.length > 0 ? (long) JSTypeConversions.toInteger(args[0]) : 0;
        if (maxLength <= s.length()) {
            return str;
        }

        String fillStr = args.length > 1 ? JSTypeConversions.toString(args[1]).value() : " ";
        if (fillStr.isEmpty()) {
            return str;
        }

        int fillLen = (int) (maxLength - s.length());
        StringBuilder padding = new StringBuilder();

        while (padding.length() < fillLen) {
            for (int i = 0; i < fillStr.length() && padding.length() < fillLen; i++) {
                padding.append(fillStr.charAt(i));
            }
        }

        return new JSString(padding + s);
    }

    /**
     * String.prototype.repeat(count)
     * ES2020 21.1.3.13
     */
    public static JSValue repeat(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();

        long count = args.length > 0 ? (long) JSTypeConversions.toInteger(args[0]) : 0;

        if (count < 0 || Double.isInfinite(count)) {
            return ctx.throwError("RangeError", "Invalid count value");
        }

        if (count == 0 || s.isEmpty()) {
            return new JSString("");
        }

        StringBuilder result = new StringBuilder((int) (s.length() * count));
        for (int i = 0; i < count; i++) {
            result.append(s);
        }

        return new JSString(result.toString());
    }

    /**
     * String.prototype.replace(searchValue, replaceValue)
     * ES2020 21.1.3.14 (simplified - no regex support)
     */
    public static JSValue replace(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();

        if (args.length == 0) {
            return str;
        }

        String searchStr = JSTypeConversions.toString(args[0]).value();
        String replaceStr = args.length > 1 ? JSTypeConversions.toString(args[1]).value() : "undefined";

        int index = s.indexOf(searchStr);
        if (index < 0) {
            return str;
        }

        String result = s.substring(0, index) + replaceStr + s.substring(index + searchStr.length());
        return new JSString(result);
    }

    /**
     * String.prototype.replaceAll(searchValue, replaceValue)
     * ES2020 21.1.3.15 (simplified - no regex support)
     */
    public static JSValue replaceAll(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();

        if (args.length == 0) {
            return str;
        }

        String searchStr = JSTypeConversions.toString(args[0]).value();
        String replaceStr = args.length > 1 ? JSTypeConversions.toString(args[1]).value() : "undefined";

        if (searchStr.isEmpty()) {
            return str;
        }

        return new JSString(s.replace(searchStr, replaceStr));
    }

    /**
     * String.prototype.slice(beginIndex[, endIndex])
     * ES2020 21.1.3.16
     */
    public static JSValue slice(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();
        int len = s.length();

        long begin = args.length > 0 ? (long) JSTypeConversions.toInteger(args[0]) : 0;
        long end = args.length > 1 ? (long) JSTypeConversions.toInteger(args[1]) : len;

        // Handle negative indices
        if (begin < 0) {
            begin = Math.max(len + begin, 0);
        } else {
            begin = Math.min(begin, len);
        }

        if (end < 0) {
            end = Math.max(len + end, 0);
        } else {
            end = Math.min(end, len);
        }

        if (begin >= end) {
            return new JSString("");
        }

        return new JSString(s.substring((int) begin, (int) end));
    }

    /**
     * String.prototype.split(separator[, limit])
     * ES2020 21.1.3.17 (simplified - no regex support)
     */
    public static JSValue split(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();

        if (args.length == 0 || args[0] instanceof JSUndefined) {
            JSArray arr = new JSArray();
            arr.push(str);
            return arr;
        }

        String separator = JSTypeConversions.toString(args[0]).value();
        long limit = args.length > 1 && !(args[1] instanceof JSUndefined)
                ? JSTypeConversions.toUint32(args[1])
                : Long.MAX_VALUE;

        JSArray arr = new JSArray();

        if (separator.isEmpty()) {
            // Split into individual characters
            for (int i = 0; i < s.length() && i < limit; i++) {
                arr.push(new JSString(String.valueOf(s.charAt(i))));
            }
            return arr;
        }

        String[] parts = s.split(java.util.regex.Pattern.quote(separator), -1);
        for (int i = 0; i < parts.length && i < limit; i++) {
            arr.push(new JSString(parts[i]));
        }

        return arr;
    }

    /**
     * String.prototype.substring(indexStart[, indexEnd])
     * ES2020 21.1.3.19
     */
    public static JSValue substring(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();
        int len = s.length();

        long start = args.length > 0 ? (long) JSTypeConversions.toInteger(args[0]) : 0;
        long end = args.length > 1 ? (long) JSTypeConversions.toInteger(args[1]) : len;

        // Clamp to [0, len]
        start = Math.max(0, Math.min(start, len));
        end = Math.max(0, Math.min(end, len));

        // Swap if start > end
        if (start > end) {
            long temp = start;
            start = end;
            end = temp;
        }

        return new JSString(s.substring((int) start, (int) end));
    }

    /**
     * String.prototype.substr(start[, length])
     * ES2020 B.2.3.1 (Deprecated, but still widely used)
     */
    public static JSValue substr(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();
        int len = s.length();

        long start = args.length > 0 ? (long) JSTypeConversions.toInteger(args[0]) : 0;
        long length = args.length > 1 ? (long) JSTypeConversions.toInteger(args[1]) : len;

        // Handle negative start
        if (start < 0) {
            start = Math.max(len + start, 0);
        } else {
            start = Math.min(start, len);
        }

        length = Math.max(0, Math.min(length, len - start));

        return new JSString(s.substring((int) start, (int) (start + length)));
    }

    /**
     * String.prototype.toLowerCase()
     * ES2020 21.1.3.22
     */
    public static JSValue toLowerCase(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        return new JSString(str.value().toLowerCase());
    }

    /**
     * String.prototype.toUpperCase()
     * ES2020 21.1.3.24
     */
    public static JSValue toUpperCase(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        return new JSString(str.value().toUpperCase());
    }

    /**
     * String.prototype.trim()
     * ES2020 21.1.3.26
     */
    public static JSValue trim(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        return new JSString(str.value().strip());
    }

    /**
     * String.prototype.trimStart() / trimLeft()
     * ES2020 21.1.3.27
     */
    public static JSValue trimStart(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        return new JSString(str.value().stripLeading());
    }

    /**
     * String.prototype.trimEnd() / trimRight()
     * ES2020 21.1.3.28
     */
    public static JSValue trimEnd(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        return new JSString(str.value().stripTrailing());
    }

    /**
     * String.prototype.toString()
     * ES2020 21.1.3.23
     */
    public static JSValue toStringMethod(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (thisArg instanceof JSString str) {
            return str;
        }
        return ctx.throwError("TypeError", "String.prototype.toString called on non-string");
    }

    /**
     * String.prototype.valueOf()
     * ES2020 21.1.3.29
     */
    public static JSValue valueOf(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (thisArg instanceof JSString str) {
            return str;
        }
        return ctx.throwError("TypeError", "String.prototype.valueOf called on non-string");
    }

    /**
     * get String.prototype.length
     * ES2020 21.1.3.10
     */
    public static JSValue getLength(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        return new JSNumber(str.value().length());
    }

    /**
     * String.prototype.at(index)
     * ES2022 22.1.3.1
     * Returns the character at the specified index, supporting negative indices.
     */
    public static JSValue at(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(thisArg);
        String s = str.value();

        if (args.length == 0) {
            return JSUndefined.INSTANCE;
        }

        long index = (long) JSTypeConversions.toInteger(args[0]);
        int length = s.length();

        // Handle negative indices
        if (index < 0) {
            index = length + index;
        }

        // Check bounds
        if (index < 0 || index >= length) {
            return JSUndefined.INSTANCE;
        }

        return new JSString(String.valueOf(s.charAt((int) index)));
    }

    /**
     * String.prototype.match(regexp)
     * ES2020 21.1.3.10 (stub - regex support not implemented yet)
     */
    public static JSValue match(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return ctx.throwError("Error", "String.prototype.match not implemented yet");
    }

    /**
     * String.prototype.matchAll(regexp)
     * ES2020 21.1.3.11
     * Returns an iterator of all results matching a string against a regular expression,
     * including capturing groups.
     * (stub - regex support not implemented yet)
     */
    public static JSValue matchAll(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return ctx.throwError("Error", "String.prototype.matchAll not implemented yet");
    }
}
