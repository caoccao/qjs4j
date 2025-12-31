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
import com.caoccao.qjs4j.regexp.RegExpEngine;

/**
 * Implementation of JavaScript String.prototype methods.
 * Based on ES2020 String.prototype specification.
 */
public final class StringPrototype {

    /**
     * String.prototype.at(index)
     * ES2022 22.1.3.1
     * Returns the character at the specified index, supporting negative indices.
     */
    public static JSValue at(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return JSUndefined.INSTANCE;
        }

        long index = (long) JSTypeConversions.toInteger(context, args[0]);
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
     * String.prototype.charAt(index)
     * ES2020 21.1.3.1
     */
    public static JSValue charAt(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();
        long pos = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;

        if (pos < 0 || pos >= s.length()) {
            return new JSString("");
        }

        return new JSString(String.valueOf(s.charAt((int) pos)));
    }

    /**
     * String.prototype.charCodeAt(index)
     * ES2020 21.1.3.2
     */
    public static JSValue charCodeAt(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();
        long pos = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;

        if (pos < 0 || pos >= s.length()) {
            return new JSNumber(Double.NaN);
        }

        return new JSNumber(s.charAt((int) pos));
    }

    /**
     * String.prototype.codePointAt(index)
     * ES2020 21.1.3.3
     */
    public static JSValue codePointAt(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();
        long pos = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;

        if (pos < 0 || pos >= s.length()) {
            return JSUndefined.INSTANCE;
        }

        return new JSNumber(s.codePointAt((int) pos));
    }

    /**
     * String.prototype.concat(...strings)
     * ES2020 21.1.3.4
     */
    public static JSValue concat(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        StringBuilder result = new StringBuilder(str.value());

        for (JSValue arg : args) {
            result.append(JSTypeConversions.toString(context, arg).value());
        }

        return new JSString(result.toString());
    }

    /**
     * String.prototype.endsWith(searchString[, endPosition])
     * ES2020 21.1.3.6
     */
    public static JSValue endsWith(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        String searchStr = JSTypeConversions.toString(context, args[0]).value();
        long endPosition = args.length > 1 ? (long) JSTypeConversions.toInteger(context, args[1]) : s.length();
        endPosition = Math.max(0, Math.min(endPosition, s.length()));

        int start = (int) (endPosition - searchStr.length());
        if (start < 0) {
            return JSBoolean.FALSE;
        }

        return JSBoolean.valueOf(s.substring(start, (int) endPosition).equals(searchStr));
    }

    /**
     * get String.prototype.length
     * ES2020 21.1.3.10
     */
    public static JSValue getLength(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        return new JSNumber(str.value().length());
    }

    /**
     * String.prototype.includes(searchString[, position])
     * ES2020 21.1.3.7
     */
    public static JSValue includes(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        String searchStr = JSTypeConversions.toString(context, args[0]).value();
        long position = args.length > 1 ? (long) JSTypeConversions.toInteger(context, args[1]) : 0;
        position = Math.max(0, Math.min(position, s.length()));

        return JSBoolean.valueOf(s.indexOf(searchStr, (int) position) >= 0);
    }

    /**
     * String.prototype.indexOf(searchString[, position])
     * ES2020 21.1.3.8
     */
    public static JSValue indexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return new JSNumber(-1);
        }

        String searchStr = JSTypeConversions.toString(context, args[0]).value();
        long position = args.length > 1 ? (long) JSTypeConversions.toInteger(context, args[1]) : 0;
        position = Math.max(0, Math.min(position, s.length()));

        int index = s.indexOf(searchStr, (int) position);
        return new JSNumber(index);
    }

    /**
     * String.prototype.lastIndexOf(searchString[, position])
     * ES2020 21.1.3.9
     */
    public static JSValue lastIndexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return new JSNumber(-1);
        }

        String searchStr = JSTypeConversions.toString(context, args[0]).value();
        long position = args.length > 1 ? (long) JSTypeConversions.toInteger(context, args[1]) : s.length();
        position = Math.max(0, Math.min(position, s.length()));

        int index = s.lastIndexOf(searchStr, (int) position);
        return new JSNumber(index);
    }

    /**
     * String.prototype.match(regexp)
     * ES2020 21.1.3.10
     * Returns an array of matches when matching a string against a regular expression.
     */
    public static JSValue match(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return JSNull.INSTANCE;
        }

        JSValue regexpArg = args[0];

        // Convert to RegExp if not already
        JSRegExp regexp;
        if (regexpArg instanceof JSRegExp) {
            regexp = (JSRegExp) regexpArg;
        } else if (regexpArg instanceof JSString regexpStr) {
            regexp = new JSRegExp(regexpStr.value(), "");
        } else {
            // Convert to string and create RegExp
            String pattern = JSTypeConversions.toString(context, regexpArg).value();
            regexp = new JSRegExp(pattern, "");
        }

        // Check if global flag is set
        if (regexp.isGlobal()) {
            // Global match: set lastIndex to 0 and collect all matches
            regexp.setLastIndex(0);
            JSArray results = new JSArray();
            int n = 0;

            while (true) {
                // Call exec (which is like JS_RegExpExec)
                JSValue result = RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString(s)});

                if (result instanceof JSNull) {
                    break;
                }

                // Extract the matched string (index 0 of the result array)
                if (result instanceof JSArray resultArray) {
                    JSValue matchStr = resultArray.get(0);
                    if (matchStr instanceof JSString) {
                        results.push(matchStr);
                        n++;

                        // Check for empty match to prevent infinite loop
                        if (((JSString) matchStr).value().isEmpty()) {
                            // Advance lastIndex to prevent infinite loop
                            int thisIndex = regexp.getLastIndex();
                            // Note: fullUnicode advancement not implemented yet, just advance by 1
                            regexp.setLastIndex(thisIndex + 1);
                        }
                    }
                }
            }

            // Return null if no matches found
            if (n == 0) {
                return JSNull.INSTANCE;
            }
            return results;
        } else {
            // Non-global match: just call exec and return result
            return RegExpPrototype.exec(context, regexp, new JSValue[]{new JSString(s)});
        }
    }

    /**
     * String.prototype.matchAll(regexp)
     * ES2020 21.1.3.11
     * Returns an iterator of all results matching a string against a regular expression,
     * including capturing groups.
     */
    public static JSValue matchAll(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return context.throwTypeError("String.prototype.matchAll requires a RegExp argument");
        }

        JSValue regexpArg = args[0];

        // Convert to RegExp if not already
        JSRegExp regexp;
        if (regexpArg instanceof JSRegExp) {
            regexp = (JSRegExp) regexpArg;
        } else if (regexpArg instanceof JSString regexpStr) {
            // For matchAll, the regexp must have the 'g' flag
            regexp = new JSRegExp(regexpStr.value(), "g");
        } else {
            // Convert to string and create RegExp with 'g' flag
            String pattern = JSTypeConversions.toString(context, regexpArg).value();
            regexp = new JSRegExp(pattern, "g");
        }

        // matchAll requires global flag
        if (!regexp.isGlobal()) {
            return context.throwTypeError("String.prototype.matchAll called with a non-global RegExp argument");
        }

        // Collect all matches into an array and return an iterator
        JSArray matches = new JSArray();
        RegExpEngine engine = regexp.getEngine();
        int lastIndex = 0;

        while (lastIndex <= s.length()) {
            RegExpEngine.MatchResult result = engine.exec(s, lastIndex);
            if (result == null || !result.matched()) {
                break;
            }

            // Create match array for this result
            JSArray matchArray = new JSArray();
            String[] captures = result.captures();
            for (int i = 0; i < captures.length; i++) {
                if (captures[i] != null) {
                    matchArray.push(new JSString(captures[i]));
                } else {
                    matchArray.push(JSUndefined.INSTANCE);
                }
            }

            // Add 'index' property and update lastIndex
            int[][] indices = result.indices();
            if (indices != null && indices.length > 0) {
                matchArray.set("index", new JSNumber(indices[0][0]));
                lastIndex = indices[0][1];
                if (lastIndex == indices[0][0]) {
                    lastIndex++; // Prevent infinite loop on zero-width matches
                }
            } else {
                break;
            }

            // Add 'input' property
            matchArray.set("input", new JSString(s));

            matches.push(matchArray);
        }

        // Return an iterator over the matches
        return JSIterator.arrayIterator(matches);
    }

    /**
     * String.prototype.padEnd(targetLength[, padString])
     * ES2020 21.1.3.11
     */
    public static JSValue padEnd(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        long maxLength = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;
        if (maxLength <= s.length()) {
            return str;
        }

        String fillStr = args.length > 1 ? JSTypeConversions.toString(context, args[1]).value() : " ";
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
    public static JSValue padStart(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        long maxLength = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;
        if (maxLength <= s.length()) {
            return str;
        }

        String fillStr = args.length > 1 ? JSTypeConversions.toString(context, args[1]).value() : " ";
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
    public static JSValue repeat(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        long count = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;

        if (count < 0 || Double.isInfinite(count)) {
            return context.throwRangeError("Invalid count value");
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
     * ES2020 21.1.3.14
     * Accepts a string or regular expression as the first argument.
     */
    public static JSValue replace(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return str;
        }

        JSValue searchValue = args[0];
        String replaceStr = args.length > 1 ? JSTypeConversions.toString(context, args[1]).value() : "undefined";

        // Handle RegExp
        if (searchValue instanceof JSRegExp regexp) {
            if (regexp.isGlobal()) {
                return replaceAll(context, thisArg, args);
            } else {
                RegExpEngine engine = regexp.getEngine();
                RegExpEngine.MatchResult result = engine.exec(s, 0);

                if (result == null || !result.matched()) {
                    return str;
                }

                int[][] indices = result.indices();
                if (indices == null || indices.length == 0) {
                    return str;
                }

                int matchStart = indices[0][0];
                int matchEnd = indices[0][1];

                // Build result with replacement
                String replacement = replaceStr;

                // Handle special replacement patterns like $1, $2, etc.
                String[] captures = result.captures();
                if (captures != null) {
                    for (int i = captures.length - 1; i >= 0; i--) {
                        if (captures[i] != null) {
                            replacement = replacement.replace("$" + i, captures[i]);
                        }
                    }
                    // Replace $& with the full match
                    if (captures.length > 0 && captures[0] != null) {
                        replacement = replacement.replace("$&", captures[0]);
                    }
                }

                String resultStr = s.substring(0, matchStart) + replacement + s.substring(matchEnd);
                return new JSString(resultStr);
            }
        }

        // Handle string search
        String searchStr = JSTypeConversions.toString(context, searchValue).value();

        // Handle empty search string: insert at position 0
        if (searchStr.isEmpty()) {
            return new JSString(replaceStr + s);
        }

        int index = s.indexOf(searchStr);
        if (index < 0) {
            return str;
        }

        String result = s.substring(0, index) + replaceStr + s.substring(index + searchStr.length());
        return new JSString(result);
    }

    /**
     * String.prototype.replaceAll(searchValue, replaceValue)
     * ES2020 21.1.3.15
     * Accepts a string or regular expression as the first argument.
     * If a RegExp is provided, it must have the global (g) flag.
     */
    public static JSValue replaceAll(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return str;
        }

        JSValue searchValue = args[0];
        String replaceStr = args.length > 1 ? JSTypeConversions.toString(context, args[1]).value() : "undefined";

        // Handle RegExp
        if (searchValue instanceof JSRegExp regexp) {
            // replaceAll requires global flag
            if (!regexp.isGlobal()) {
                return context.throwTypeError("String.prototype.replaceAll called with a non-global RegExp argument");
            }

            RegExpEngine engine = regexp.getEngine();
            StringBuilder result = new StringBuilder();
            int lastIndex = 0;

            while (lastIndex <= s.length()) {
                RegExpEngine.MatchResult matchResult = engine.exec(s, lastIndex);

                if (matchResult == null || !matchResult.matched()) {
                    // No more matches, append the rest
                    result.append(s.substring(lastIndex));
                    break;
                }

                int[][] indices = matchResult.indices();
                if (indices == null || indices.length == 0) {
                    result.append(s.substring(lastIndex));
                    break;
                }

                int matchStart = indices[0][0];
                int matchEnd = indices[0][1];

                // Append text before match
                result.append(s, lastIndex, matchStart);

                // Build replacement with special patterns
                String replacement = replaceStr;
                String[] captures = matchResult.captures();
                if (captures != null) {
                    for (int i = captures.length - 1; i >= 0; i--) {
                        if (captures[i] != null) {
                            replacement = replacement.replace("$" + i, captures[i]);
                        }
                    }
                    // Replace $& with the full match
                    if (captures.length > 0 && captures[0] != null) {
                        replacement = replacement.replace("$&", captures[0]);
                    }
                }

                // Append replacement
                result.append(replacement);

                // Move past the match
                lastIndex = matchEnd;

                // Prevent infinite loop on zero-width matches
                if (matchStart == matchEnd) {
                    if (lastIndex < s.length()) {
                        result.append(s.charAt(lastIndex));
                        lastIndex++;
                    } else {
                        break;
                    }
                }
            }

            return new JSString(result.toString());
        }

        // Handle string search
        String searchStr = JSTypeConversions.toString(context, searchValue).value();

        // Handle empty search string: insert replacement at every position
        if (searchStr.isEmpty()) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                result.append(replaceStr);
                result.append(s.charAt(i));
            }
            result.append(replaceStr);
            return new JSString(result.toString());
        }

        // Handle non-empty search string
        StringBuilder result = new StringBuilder();
        int pos = 0;
        int index;
        while ((index = s.indexOf(searchStr, pos)) >= 0) {
            result.append(s, pos, index);
            result.append(replaceStr);
            pos = index + searchStr.length();
        }
        result.append(s.substring(pos));
        return new JSString(result.toString());
    }

    /**
     * String.prototype.search(regexp)
     * ES2020 21.1.3.15
     * Accepts a regular expression or string.
     * Returns the index of the first match, or -1.
     */
    public static JSValue search(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return new JSNumber(-1);
        }

        JSValue regexpArg = args[0];

        // Convert to RegExp if not already
        JSRegExp regexp;
        if (regexpArg instanceof JSRegExp) {
            regexp = (JSRegExp) regexpArg;
        } else if (regexpArg instanceof JSString regexpStr) {
            regexp = new JSRegExp(regexpStr.value(), "");
        } else {
            // Convert to string and create RegExp
            String pattern = JSTypeConversions.toString(context, regexpArg).value();
            regexp = new JSRegExp(pattern, "");
        }

        // Use QuickJS engine to find match
        RegExpEngine engine = regexp.getEngine();
        RegExpEngine.MatchResult result = engine.exec(s, 0);

        if (result == null || !result.matched()) {
            return new JSNumber(-1);
        }

        // Return the starting index of the match
        int[][] indices = result.indices();
        if (indices != null && indices.length > 0) {
            return new JSNumber(indices[0][0]);
        }

        return new JSNumber(-1);
    }

    /**
     * String.prototype.slice(beginIndex[, endIndex])
     * ES2020 21.1.3.16
     */
    public static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();
        int len = s.length();

        long begin = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;
        long end = args.length > 1 ? (long) JSTypeConversions.toInteger(context, args[1]) : len;

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
     * ES2020 21.1.3.17
     */
    public static JSValue split(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        if (args.length == 0 || args[0] instanceof JSUndefined) {
            JSArray arr = new JSArray();
            arr.push(str);
            return arr;
        }

        JSValue separatorArg = args[0];
        long limit = args.length > 1 && !(args[1] instanceof JSUndefined)
                ? JSTypeConversions.toUint32(context, args[1])
                : Long.MAX_VALUE;

        JSArray arr = new JSArray();

        // Handle RegExp separator
        if (separatorArg instanceof JSRegExp regexp) {
            RegExpEngine engine = regexp.getEngine();
            int start = 0;

            while (start <= s.length() && arr.getLength() < limit) {
                RegExpEngine.MatchResult result = engine.exec(s, start);

                if (result == null || !result.matched()) {
                    // No more matches, add the rest of the string
                    if (arr.getLength() < limit) {
                        arr.push(new JSString(s.substring(start)));
                    }
                    break;
                }

                int[][] indices = result.indices();
                if (indices != null && indices.length > 0) {
                    int matchStart = indices[0][0];
                    int matchEnd = indices[0][1];

                    // Add the substring before the match
                    arr.push(new JSString(s.substring(start, matchStart)));

                    // Add capture groups if any
                    String[] captures = result.captures();
                    if (captures != null && captures.length > 1) {
                        for (int i = 1; i < captures.length && arr.getLength() < limit; i++) {
                            if (captures[i] != null) {
                                arr.push(new JSString(captures[i]));
                            } else {
                                arr.push(JSUndefined.INSTANCE);
                            }
                        }
                    }

                    // Move past the match
                    start = matchEnd;

                    // Prevent infinite loop on zero-width matches
                    if (matchStart == matchEnd) {
                        if (start < s.length()) {
                            start++;
                        } else {
                            break;
                        }
                    }
                } else {
                    break;
                }
            }

            return arr;
        }

        // Handle string separator
        String separator = JSTypeConversions.toString(context, separatorArg).value();

        if (separator.isEmpty()) {
            // Split into individual characters
            for (int i = 0; i < s.length() && i < limit; i++) {
                arr.push(new JSString(String.valueOf(s.charAt(i))));
            }
            return arr;
        }

        // Manual split without using Java regex
        int start = 0;
        int index;
        while ((index = s.indexOf(separator, start)) != -1 && arr.getLength() < limit) {
            arr.push(new JSString(s.substring(start, index)));
            start = index + separator.length();
        }

        // Add the remaining part if we haven't reached the limit
        if (arr.getLength() < limit) {
            arr.push(new JSString(s.substring(start)));
        }

        return arr;
    }

    /**
     * String.prototype.startsWith(searchString[, position])
     * ES2020 21.1.3.21
     */
    public static JSValue startsWith(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        String searchStr = JSTypeConversions.toString(context, args[0]).value();
        long position = args.length > 1 ? (long) JSTypeConversions.toInteger(context, args[1]) : 0;
        position = Math.max(0, Math.min(position, s.length()));

        return JSBoolean.valueOf(s.substring((int) position).startsWith(searchStr));
    }

    /**
     * String.prototype.substr(start[, length])
     * ES2020 B.2.3.1 (Deprecated, but still widely used)
     */
    public static JSValue substr(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();
        int len = s.length();

        long start = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;
        long length = args.length > 1 ? (long) JSTypeConversions.toInteger(context, args[1]) : len;

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
     * String.prototype.substring(indexStart[, indexEnd])
     * ES2020 21.1.3.19
     */
    public static JSValue substring(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        String s = str.value();
        int len = s.length();

        long start = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;
        long end = args.length > 1 ? (long) JSTypeConversions.toInteger(context, args[1]) : len;

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
     * String.prototype.toLowerCase()
     * ES2020 21.1.3.22
     */
    public static JSValue toLowerCase(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        return new JSString(str.value().toLowerCase());
    }

    /**
     * String.prototype.toString()
     * ES2020 21.1.3.23
     */
    public static JSValue toString_(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asStringWithDownCast()
                .map(jsString -> (JSValue) jsString)
                .orElseGet(() -> context.throwTypeError("String.prototype.toString requires that 'this' be a String"));
    }

    /**
     * String.prototype.toUpperCase()
     * ES2020 21.1.3.24
     */
    public static JSValue toUpperCase(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        return new JSString(str.value().toUpperCase());
    }

    /**
     * String.prototype.trim()
     * ES2020 21.1.3.26
     */
    public static JSValue trim(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        return new JSString(str.value().strip());
    }

    /**
     * String.prototype.trimEnd() / trimRight()
     * ES2020 21.1.3.28
     */
    public static JSValue trimEnd(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        return new JSString(str.value().stripTrailing());
    }

    /**
     * String.prototype.trimStart() / trimLeft()
     * ES2020 21.1.3.27
     */
    public static JSValue trimStart(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = JSTypeConversions.toString(context, thisArg);
        return new JSString(str.value().stripLeading());
    }

    /**
     * String.prototype.valueOf()
     * ES2020 21.1.3.29
     */
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asStringWithDownCast()
                .map(jsString -> (JSValue) jsString)
                .orElseGet(() -> context.throwTypeError("String.prototype.valueOf requires that 'this' be a String"));
    }
}
