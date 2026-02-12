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
import com.caoccao.qjs4j.unicode.UnicodeNormalization;

import java.text.Collator;
import java.util.Locale;

/**
 * Implementation of JavaScript String.prototype methods.
 * Based on ES2020 String.prototype specification.
 */
public final class StringPrototype {

    /**
     * String.prototype.anchor(name)
     * Creates an HTML anchor element with a name attribute.
     */
    public static JSValue anchor(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "a", "name");
    }

    private static String applyRegExpReplacementPattern(String replacementTemplate, String[] captures, String[] groupNames) {
        String replacement = replacementTemplate;
        if (captures != null) {
            for (int i = captures.length - 1; i >= 0; i--) {
                if (captures[i] != null) {
                    replacement = replacement.replace("$" + i, captures[i]);
                }
            }
            if (captures.length > 0 && captures[0] != null) {
                replacement = replacement.replace("$&", captures[0]);
            }
        }
        if (captures != null && groupNames != null) {
            int maxLength = Math.min(captures.length, groupNames.length);
            for (int i = 1; i < maxLength; i++) {
                String groupName = groupNames[i];
                if (groupName != null) {
                    replacement = replacement.replace(
                            "$<" + groupName + ">",
                            captures[i] != null ? captures[i] : "");
                }
            }
        }
        return replacement;
    }

    /**
     * String.prototype.at(index)
     * ES2022 22.1.3.1
     * Returns the character at the specified index, supporting negative indices.
     */
    public static JSValue at(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
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
     * String.prototype.big()
     * Wraps string in <big> element.
     */
    public static JSValue big(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "big", null);
    }

    /**
     * String.prototype.blink()
     * Wraps string in <blink> element.
     */
    public static JSValue blink(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "blink", null);
    }

    /**
     * String.prototype.bold()
     * Wraps string in <b> element.
     */
    public static JSValue bold(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "b", null);
    }

    /**
     * String.prototype.charAt(index)
     * ES2020 21.1.3.1
     */
    public static JSValue charAt(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
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
        JSString str = toStringCheckObject(context, thisArg);
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
        JSString str = toStringCheckObject(context, thisArg);
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
        JSString str = toStringCheckObject(context, thisArg);
        StringBuilder result = new StringBuilder(str.value());

        for (JSValue arg : args) {
            result.append(JSTypeConversions.toString(context, arg).value());
        }

        return new JSString(result.toString());
    }

    /**
     * Helper method to create HTML wrapper strings.
     * Used by anchor(), big(), blink(), bold(), etc.
     */
    private static JSValue createHTML(JSContext context, JSValue thisArg, JSValue[] args,
                                      String tag, String attr) {
        JSString str = toStringCheckObject(context, thisArg);
        StringBuilder result = new StringBuilder();

        result.append('<').append(tag);

        if (attr != null) {
            // Attribute requires a value from args[0]
            JSValue attrValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            JSString attrStr = toStringCheckObject(context, attrValue);
            String attrText = attrStr.value();

            result.append(' ').append(attr).append("=\"");
            // Escape quotes in attribute value
            for (int i = 0; i < attrText.length(); i++) {
                char c = attrText.charAt(i);
                if (c == '"') {
                    result.append("&quot;");
                } else {
                    result.append(c);
                }
            }
            result.append('"');
        }

        result.append('>').append(str.value()).append("</").append(tag).append('>');

        return new JSString(result.toString());
    }

    private static JSValue createNamedGroupsValue(String[] captures, String[] groupNames) {
        if (groupNames == null || captures == null) {
            return JSUndefined.INSTANCE;
        }

        JSObject groups = new JSObject();
        groups.setPrototype(null);

        int maxLength = Math.min(captures.length, groupNames.length);
        for (int i = 1; i < maxLength; i++) {
            String groupName = groupNames[i];
            if (groupName != null && !groups.hasOwnProperty(groupName)) {
                if (captures[i] != null) {
                    groups.set(groupName, new JSString(captures[i]));
                } else {
                    groups.set(groupName, JSUndefined.INSTANCE);
                }
            }
        }
        return groups;
    }

    /**
     * String.prototype.endsWith(searchString[, endPosition])
     * ES2020 21.1.3.6
     */
    public static JSValue endsWith(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }
        int regexp = isRegExp(context, args[0]);
        if (regexp < 0) {
            return JSUndefined.INSTANCE;
        }
        if (regexp > 0) {
            return context.throwTypeError("regexp not supported");
        }

        String searchStr = JSTypeConversions.toString(context, args[0]).value();
        long endPosition = args.length > 1 && !args[1].isUndefined()
                ? (long) JSTypeConversions.toInteger(context, args[1])
                : s.length();
        endPosition = Math.max(0, Math.min(endPosition, s.length()));

        int start = (int) (endPosition - searchStr.length());
        if (start < 0) {
            return JSBoolean.FALSE;
        }

        return JSBoolean.valueOf(s.substring(start, (int) endPosition).equals(searchStr));
    }

    /**
     * Helper method to find the first invalid code point in a string.
     * Returns the index of the first unpaired surrogate, or -1 if the string is well-formed.
     */
    private static int findInvalidCodePoint(String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            // Check if it's a surrogate character
            if (Character.isSurrogate(c)) {
                // High surrogate must be followed by low surrogate
                if (Character.isHighSurrogate(c)) {
                    if (i + 1 < str.length() && Character.isLowSurrogate(str.charAt(i + 1))) {
                        i++; // Valid surrogate pair, skip the low surrogate
                    } else {
                        return i; // Unpaired high surrogate
                    }
                } else {
                    // Unpaired low surrogate
                    return i;
                }
            }
        }
        return -1; // String is well-formed
    }

    /**
     * String.prototype.fixed()
     * Wraps string in <tt> element.
     */
    public static JSValue fixed(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "tt", null);
    }

    /**
     * String.prototype.fontcolor(color)
     * Wraps string in <font> element with color attribute.
     */
    public static JSValue fontcolor(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "font", "color");
    }

    /**
     * String.prototype.fontsize(size)
     * Wraps string in <font> element with size attribute.
     */
    public static JSValue fontsize(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "font", "size");
    }

    /**
     * get String.prototype.length
     * ES2020 21.1.3.10
     */
    public static JSValue getLength(JSContext context, JSValue thisArg, JSValue[] args) {
        long length = 0;
        if (thisArg instanceof JSString jsString) {
            length = jsString.value().length();
        } else if (thisArg instanceof JSStringObject jsStringObject) {
            length = jsStringObject.getValue().value().length();
        } else if (thisArg instanceof JSObject jsObject) {
            // Check if it's a wrapper object with a string primitive value
            JSValue primitiveValue = jsObject.getPrimitiveValue();
            if (primitiveValue instanceof JSString jsString) {
                length = jsString.value().length();
            }
        }
        return new JSNumber(length);
    }

    /**
     * String.prototype.includes(searchString[, position])
     * ES2020 21.1.3.7
     */
    public static JSValue includes(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }
        int regexp = isRegExp(context, args[0]);
        if (regexp < 0) {
            return JSUndefined.INSTANCE;
        }
        if (regexp > 0) {
            return context.throwTypeError("regexp not supported");
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
        JSString str = toStringCheckObject(context, thisArg);
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

    private static int isRegExp(JSContext context, JSValue value) {
        if (!(value instanceof JSObject obj)) {
            return 0;
        }
        JSValue matcher = obj.get(PropertyKey.fromSymbol(JSSymbol.MATCH), context);
        if (context.hasPendingException()) {
            return -1;
        }
        if (!(matcher instanceof JSUndefined)) {
            return JSTypeConversions.toBoolean(matcher).value() ? 1 : 0;
        }
        return value instanceof JSRegExp ? 1 : 0;
    }

    /**
     * String.prototype.isWellFormed()
     * Returns true if the string contains no unpaired surrogates.
     */
    public static JSValue isWellFormed(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        boolean wellFormed = findInvalidCodePoint(str.value()) < 0;
        return JSBoolean.valueOf(wellFormed);
    }

    /**
     * String.prototype.italics()
     * Wraps string in <i> element.
     */
    public static JSValue italics(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "i", null);
    }

    /**
     * String.prototype.lastIndexOf(searchString[, position])
     * ES2020 21.1.3.9
     */
    public static JSValue lastIndexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return new JSNumber(-1);
        }

        String searchStr = JSTypeConversions.toString(context, args[0]).value();
        long position = args.length > 1 && !args[1].isUndefined()
                ? (long) JSTypeConversions.toInteger(context, args[1])
                : s.length();
        position = Math.max(0, Math.min(position, s.length()));

        int index = s.lastIndexOf(searchStr, (int) position);
        return new JSNumber(index);
    }

    /**
     * String.prototype.link(url)
     * Creates an HTML anchor element with an href attribute.
     */
    public static JSValue link(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "a", "href");
    }

    /**
     * String.prototype.localeCompare(that [, locales [, options]])
     * ES2020 21.1.3.11
     * Compares two strings in the current locale.
     */
    public static JSValue localeCompare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String thisStr = str.value();

        JSString thatStr = JSTypeConversions.toString(
                context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        String that = thatStr.value();

        Locale locale = Locale.getDefault();
        if (args.length > 1 && !args[1].isNullOrUndefined()) {
            String localeTag = null;
            if (args[1] instanceof JSArray localeArray && localeArray.getLength() > 0) {
                localeTag = JSTypeConversions.toString(context, localeArray.get(0)).value();
            } else {
                localeTag = JSTypeConversions.toString(context, args[1]).value();
            }
            if (localeTag != null && !localeTag.isEmpty()) {
                Locale candidateLocale = Locale.forLanguageTag(localeTag);
                if (!candidateLocale.getLanguage().isEmpty()) {
                    locale = candidateLocale;
                }
            }
        }

        Collator collator = Collator.getInstance(locale);
        int result = Integer.signum(collator.compare(thisStr, that));
        return new JSNumber(result);
    }

    /**
     * String.prototype.match(regexp)
     * ES2020 21.1.3.10
     * Returns an array of matches when matching a string against a regular expression.
     */
    public static JSValue match(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }

        JSValue regexpArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (regexpArg instanceof JSObject regexpObj) {
            JSValue matcher = regexpObj.get(PropertyKey.fromSymbol(JSSymbol.MATCH), context);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(matcher instanceof JSUndefined) && !(matcher instanceof JSNull)) {
                if (!(matcher instanceof JSFunction matcherFunction)) {
                    return context.throwTypeError("not a function");
                }
                return matcherFunction.call(context, regexpObj, new JSValue[]{thisArg});
            }
        }

        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        // Convert to RegExp if not already
        JSRegExp regexp;
        if (regexpArg instanceof JSRegExp) {
            regexp = (JSRegExp) regexpArg;
        } else if (regexpArg instanceof JSUndefined) {
            regexp = new JSRegExp("", "");
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
            JSArray results = context.createJSArray();
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
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }

        JSValue regexpArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (regexpArg instanceof JSRegExp regexp && !regexp.isGlobal()) {
            return context.throwTypeError("String.prototype.matchAll called with a non-global RegExp argument");
        }
        if (regexpArg instanceof JSObject regexpObj) {
            JSValue matcher = regexpObj.get(PropertyKey.fromSymbol(JSSymbol.MATCH_ALL), context);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(matcher instanceof JSUndefined) && !(matcher instanceof JSNull)) {
                if (!(matcher instanceof JSFunction matcherFunction)) {
                    return context.throwTypeError("not a function");
                }
                return matcherFunction.call(context, regexpObj, new JSValue[]{thisArg});
            }
        }

        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        // Convert to RegExp if not already
        JSRegExp regexp;
        if (regexpArg instanceof JSRegExp inputRegExp) {
            regexp = new JSRegExp(inputRegExp.getPattern(), "g");
        } else if (regexpArg instanceof JSUndefined) {
            regexp = new JSRegExp("", "g");
        } else if (regexpArg instanceof JSString regexpStr) {
            regexp = new JSRegExp(regexpStr.value(), "g");
        } else {
            // Convert to string and create RegExp with 'g' flag
            String pattern = JSTypeConversions.toString(context, regexpArg).value();
            regexp = new JSRegExp(pattern, "g");
        }

        // Collect all matches into an array and return an iterator
        JSArray matches = context.createJSArray();
        RegExpEngine engine = regexp.getEngine();
        int lastIndex = 0;

        while (lastIndex <= s.length()) {
            RegExpEngine.MatchResult result = engine.exec(s, lastIndex);
            if (result == null || !result.matched()) {
                break;
            }

            // Create match array for this result
            JSArray matchArray = context.createJSArray();
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
            matchArray.set("groups", createNamedGroupsValue(captures, regexp.getBytecode().groupNames()));

            matches.push(matchArray);
        }

        return JSIterator.arrayIterator(context, matches);
    }

    /**
     * String.prototype.normalize([form])
     * Returns the Unicode Normalization Form of the string.
     * Valid forms: "NFC" (default), "NFD", "NFKC", "NFKD"
     */
    public static JSValue normalize(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);

        // Default to NFC
        UnicodeNormalization.Form form = UnicodeNormalization.Form.NFC;

        if (args.length > 0 && !JSUndefined.INSTANCE.equals(args[0])) {
            JSString formStr = JSTypeConversions.toString(context, args[0]);
            String formName = formStr.value();

            // Parse normalization form
            switch (formName) {
                case "NFC":
                    form = UnicodeNormalization.Form.NFC;
                    break;
                case "NFD":
                    form = UnicodeNormalization.Form.NFD;
                    break;
                case "NFKC":
                    form = UnicodeNormalization.Form.NFKC;
                    break;
                case "NFKD":
                    form = UnicodeNormalization.Form.NFKD;
                    break;
                default:
                    return context.throwRangeError("The normalization form should be one of NFC, NFD, NFKC, NFKD.");
            }
        }

        String normalized = UnicodeNormalization.normalize(str.value(), form);
        return new JSString(normalized);
    }

    /**
     * String.prototype.padEnd(targetLength[, padString])
     * ES2020 21.1.3.11
     */
    public static JSValue padEnd(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        long maxLength = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;
        if (maxLength <= s.length()) {
            return str;
        }

        String fillStr = args.length > 1 && !args[1].isUndefined()
                ? JSTypeConversions.toString(context, args[1]).value()
                : " ";
        if (fillStr.isEmpty()) {
            return str;
        }

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
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        long maxLength = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;
        if (maxLength <= s.length()) {
            return str;
        }

        String fillStr = args.length > 1 && !args[1].isUndefined()
                ? JSTypeConversions.toString(context, args[1]).value()
                : " ";
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
        JSString str = toStringCheckObject(context, thisArg);
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
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }

        JSValue searchValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue replaceValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        if (searchValue instanceof JSObject searchValueObject) {
            JSValue replacer = searchValueObject.get(PropertyKey.fromSymbol(JSSymbol.REPLACE), context);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(replacer instanceof JSUndefined) && !(replacer instanceof JSNull)) {
                if (!(replacer instanceof JSFunction replacerFunction)) {
                    return context.throwTypeError("not a function");
                }
                return replacerFunction.call(context, searchValueObject, new JSValue[]{thisArg, replaceValue});
            }
        }

        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();
        String replaceStr = JSTypeConversions.toString(context, replaceValue).value();

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
                String[] captures = result.captures();
                String replacement = applyRegExpReplacementPattern(
                        replaceStr,
                        captures,
                        regexp.getBytecode().groupNames());

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
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }

        JSValue searchValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue replaceValue = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        if (searchValue instanceof JSRegExp regexp && !regexp.isGlobal()) {
            return context.throwTypeError("String.prototype.replaceAll called with a non-global RegExp argument");
        }
        if (searchValue instanceof JSObject searchValueObject) {
            JSValue replacer = searchValueObject.get(PropertyKey.fromSymbol(JSSymbol.REPLACE), context);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(replacer instanceof JSUndefined) && !(replacer instanceof JSNull)) {
                if (!(replacer instanceof JSFunction replacerFunction)) {
                    return context.throwTypeError("not a function");
                }
                return replacerFunction.call(context, searchValueObject, new JSValue[]{thisArg, replaceValue});
            }
        }

        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();
        String replaceStr = JSTypeConversions.toString(context, replaceValue).value();

        // Handle RegExp
        if (searchValue instanceof JSRegExp regexp) {
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
                String[] captures = matchResult.captures();
                String replacement = applyRegExpReplacementPattern(
                        replaceStr,
                        captures,
                        regexp.getBytecode().groupNames());

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
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }

        JSValue regexpArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (regexpArg instanceof JSObject regexpObj) {
            JSValue searcher = regexpObj.get(PropertyKey.fromSymbol(JSSymbol.SEARCH), context);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(searcher instanceof JSUndefined) && !(searcher instanceof JSNull)) {
                if (!(searcher instanceof JSFunction searchFunction)) {
                    return context.throwTypeError("not a function");
                }
                return searchFunction.call(context, regexpObj, new JSValue[]{thisArg});
            }
        }

        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        // Convert to RegExp if not already
        JSRegExp regexp;
        if (regexpArg instanceof JSRegExp) {
            regexp = (JSRegExp) regexpArg;
        } else if (regexpArg instanceof JSUndefined) {
            regexp = new JSRegExp("", "");
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
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();
        int len = s.length();

        long begin = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;
        long end = args.length > 1 && !args[1].isUndefined()
                ? (long) JSTypeConversions.toInteger(context, args[1])
                : len;

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
     * String.prototype.small()
     * Wraps string in <small> element.
     */
    public static JSValue small(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "small", null);
    }

    /**
     * String.prototype.split(separator[, limit])
     * ES2020 21.1.3.17
     */
    public static JSValue split(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }

        JSValue separatorArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue limitArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        if (separatorArg instanceof JSObject separatorObject) {
            JSValue splitter = separatorObject.get(PropertyKey.fromSymbol(JSSymbol.SPLIT), context);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(splitter instanceof JSUndefined) && !(splitter instanceof JSNull)) {
                if (!(splitter instanceof JSFunction splitFunction)) {
                    return context.throwTypeError("not a function");
                }
                return splitFunction.call(context, separatorObject, new JSValue[]{thisArg, limitArg});
            }
        }

        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        JSArray arr = context.createJSArray();
        long limit = !(limitArg instanceof JSUndefined)
                ? JSTypeConversions.toUint32(context, limitArg)
                : 0xFFFFFFFFL;
        if (limit == 0) {
            return arr;
        }
        if (separatorArg instanceof JSUndefined) {
            arr.push(str);
            return arr;
        }

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
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        if (args.length == 0) {
            return JSBoolean.FALSE;
        }
        int regexp = isRegExp(context, args[0]);
        if (regexp < 0) {
            return JSUndefined.INSTANCE;
        }
        if (regexp > 0) {
            return context.throwTypeError("regexp not supported");
        }

        String searchStr = JSTypeConversions.toString(context, args[0]).value();
        long position = args.length > 1 && !args[1].isUndefined()
                ? (long) JSTypeConversions.toInteger(context, args[1])
                : 0;
        position = Math.max(0, Math.min(position, s.length()));

        return JSBoolean.valueOf(s.substring((int) position).startsWith(searchStr));
    }

    /**
     * String.prototype.strike()
     * Wraps string in <strike> element.
     */
    public static JSValue strike(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "strike", null);
    }

    /**
     * String.prototype.sub()
     * Wraps string in <sub> element.
     */
    public static JSValue sub(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "sub", null);
    }

    /**
     * String.prototype.substr(start[, length])
     * ES2020 B.2.3.1 (Deprecated, but still widely used)
     */
    public static JSValue substr(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();
        int len = s.length();

        long start = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long length = args.length > 1 && !args[1].isUndefined()
                ? (long) JSTypeConversions.toInteger(context, args[1])
                : len;
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

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
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();
        int len = s.length();

        long start = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;
        long end = args.length > 1 && !args[1].isUndefined()
                ? (long) JSTypeConversions.toInteger(context, args[1])
                : len;

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
     * String.prototype.sup()
     * Wraps string in <sup> element.
     */
    public static JSValue sup(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "sup", null);
    }

    /**
     * String.prototype.toLocaleLowerCase([locale])
     * Converts string to lowercase according to locale-specific rules.
     * For now, this is the same as toLowerCase() (locale parameter is ignored).
     */
    public static JSValue toLocaleLowerCase(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        // In QuickJS, this just calls toLowerCase() - locale is ignored
        return new JSString(str.value().toLowerCase());
    }

    /**
     * String.prototype.toLocaleUpperCase([locale])
     * Converts string to uppercase according to locale-specific rules.
     * For now, this is the same as toUpperCase() (locale parameter is ignored).
     */
    public static JSValue toLocaleUpperCase(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        // In QuickJS, this just calls toUpperCase() - locale is ignored
        return new JSString(str.value().toUpperCase());
    }

    /**
     * String.prototype.toLowerCase()
     * ES2020 21.1.3.22
     */
    public static JSValue toLowerCase(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        return new JSString(str.value().toLowerCase());
    }

    private static JSString toStringCheckObject(JSContext context, JSValue value) {
        if (value == null || value.isNullOrUndefined()) {
            context.throwTypeError("null or undefined are forbidden");
            return new JSString("");
        }
        return JSTypeConversions.toString(context, value);
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
        JSString str = toStringCheckObject(context, thisArg);
        return new JSString(str.value().toUpperCase());
    }

    /**
     * String.prototype.toWellFormed()
     * Returns a string where all unpaired surrogates are replaced with U+FFFD (replacement character).
     */
    public static JSValue toWellFormed(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        // Check if string is already well-formed
        int firstInvalid = findInvalidCodePoint(s);
        if (firstInvalid < 0) {
            // Already well-formed, return original string
            return str;
        }

        // Replace unpaired surrogates with U+FFFD
        StringBuilder result = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (Character.isSurrogate(c)) {
                if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                    // Valid surrogate pair
                    result.append(c);
                    result.append(s.charAt(i + 1));
                    i++;
                } else {
                    // Unpaired surrogate, replace with replacement character
                    result.append('\uFFFD');
                }
            } else {
                result.append(c);
            }
        }

        return new JSString(result.toString());
    }

    /**
     * String.prototype.trim()
     * ES2020 21.1.3.26
     */
    public static JSValue trim(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        return new JSString(str.value().strip());
    }

    /**
     * String.prototype.trimEnd() / trimRight()
     * ES2020 21.1.3.28
     */
    public static JSValue trimEnd(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        return new JSString(str.value().stripTrailing());
    }

    /**
     * String.prototype.trimStart() / trimLeft()
     * ES2020 21.1.3.27
     */
    public static JSValue trimStart(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
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
