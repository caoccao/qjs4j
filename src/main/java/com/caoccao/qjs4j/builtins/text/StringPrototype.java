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

package com.caoccao.qjs4j.builtins.text;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.intl.JSIntlCollator;
import com.caoccao.qjs4j.core.regexp.JSRegExp;
import com.caoccao.qjs4j.unicode.UnicodeCaseMapping;
import com.caoccao.qjs4j.unicode.UnicodeNormalization;

import java.util.Locale;

/**
 * Implementation of JavaScript String.prototype methods. Based on ES2020 String.prototype specification.
 */
public final class StringPrototype {

    /**
     * String.prototype.anchor(name) Creates an HTML anchor element with a name attribute.
     */
    public static JSValue anchor(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "a", "name");
    }

    /**
     * String.prototype.at(index) ES2022 22.1.3.1 Returns the character at the specified index, supporting negative
     * indices.
     */
    public static JSValue at(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        // A missing index is undefined, which ToIntegerOrInfinity makes 0: "abc".at() is "a". It used to be undefined.
        long index = (long) JSTypeConversions.toInteger(context, JSValue.argumentOrUndefined(args, 0));
        int length = s.length();

        // Handle negative indices
        if (index < 0) {
            index = length + index;
        }

        // Check bounds
        if (index < 0 || index >= length) {
            return JSUndefined.INSTANCE;
        }

        return JSString.of(s.charAt((int) index));
    }

    /**
     * String.prototype.big() Wraps string in <big> element.
     */
    public static JSValue big(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "big", null);
    }

    /**
     * String.prototype.blink() Wraps string in <blink> element.
     */
    public static JSValue blink(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "blink", null);
    }

    /**
     * String.prototype.bold() Wraps string in <b> element.
     */
    public static JSValue bold(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "b", null);
    }

    private static JSValue callRegExpMethod(JSContext context, JSValue method, JSObject object, JSValue input) {
        if (!JSTypeChecking.isCallable(method)) {
            return context.throwTypeError("not a function");
        }
        return JSCall.call(context, method, object, new JSValue[]{input});
    }

    private static JSValue characterAt(JSContext context, JSValue receiver, JSValue[] args, CharacterResult kind) {
        String value = toStringCheckObject(context, receiver).value();
        long position = integerArgument(context, args, 0, 0);
        if (position < 0 || position >= value.length()) {
            return switch (kind) {
                case CHARACTER -> JSString.EMPTY;
                case CODE_UNIT -> JSNumber.of(Double.NaN);
                case CODE_POINT -> JSUndefined.INSTANCE;
            };
        }
        int index = (int) position;
        return switch (kind) {
            case CHARACTER -> JSString.of(value.charAt(index));
            case CODE_UNIT -> JSNumber.of(value.charAt(index));
            case CODE_POINT -> JSNumber.of(value.codePointAt(index));
        };
    }

    /**
     * String.prototype.charAt(index) ES2020 21.1.3.1
     */
    public static JSValue charAt(JSContext context, JSValue thisArg, JSValue[] args) {
        return characterAt(context, thisArg, args, CharacterResult.CHARACTER);
    }

    /**
     * String.prototype.charCodeAt(index) ES2020 21.1.3.2
     */
    public static JSValue charCodeAt(JSContext context, JSValue thisArg, JSValue[] args) {
        return characterAt(context, thisArg, args, CharacterResult.CODE_UNIT);
    }

    /**
     * String.prototype.codePointAt(index) ES2020 21.1.3.3
     */
    public static JSValue codePointAt(JSContext context, JSValue thisArg, JSValue[] args) {
        return characterAt(context, thisArg, args, CharacterResult.CODE_POINT);
    }

    /**
     * String.prototype.concat(...strings) ES2020 21.1.3.4
     */
    public static JSValue concat(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString result = toStringCheckObject(context, thisArg);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        for (JSValue arg : args) {
            context.checkExecutionInterrupt();
            JSString appended = JSTypeConversions.toString(context, arg);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if ((long) result.length() + appended.length() > JSString.MAX_LENGTH) {
                return context.throwRangeError("Invalid string length");
            }
            result = JSString.concat(result, appended);
        }
        return result;
    }

    /**
     * The result of a replacement, within the string length limit.
     */
    private static JSValue concatenateReplacement(JSContext context, JSString head, JSString replacement, String tail) {
        if ((long) head.length() + replacement.length() + tail.length() > JSString.MAX_LENGTH) {
            return context.throwRangeError("Invalid string length");
        }
        return JSString.concat(JSString.concat(head, replacement), new JSString(tail));
    }

    /**
     * Helper method to create HTML wrapper strings. Used by anchor(), big(), blink(), bold(), etc.
     */
    private static JSValue createHTML(JSContext context, JSValue thisArg, JSValue[] args, String tag, String attr) {
        JSString str = toStringCheckObject(context, thisArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        StringBuilder result = new StringBuilder();

        result.append('<').append(tag);

        if (attr != null) {
            // Attribute requires a value from args[0]
            JSValue attrValue = JSValue.argumentOrUndefined(args, 0);
            JSString attrStr = toStringCheckObject(context, attrValue);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String attrText = attrStr.value();

            result.append(' ').append(attr).append("=\"");
            // Escape quotes in attribute value
            for (int i = 0; i < attrText.length(); i++) {
                context.checkExecutionInterrupt();
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

    /**
     * String.prototype.endsWith(searchString[, endPosition]) ES2020 21.1.3.6
     */
    public static JSValue endsWith(JSContext context, JSValue thisArg, JSValue[] args) {
        return searchString(context, thisArg, args, SearchMode.SUFFIX);
    }

    /**
     * Helper method to find the first invalid code point in a string. Returns the index of the first unpaired
     * surrogate, or -1 if the string is well-formed.
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
     * String.prototype.fixed() Wraps string in <tt> element.
     */
    public static JSValue fixed(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "tt", null);
    }

    /**
     * String.prototype.fontcolor(color) Wraps string in <font> element with color attribute.
     */
    public static JSValue fontcolor(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "font", "color");
    }

    /**
     * String.prototype.fontsize(size) Wraps string in <font> element with size attribute.
     */
    public static JSValue fontsize(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "font", "size");
    }

    /**
     * get String.prototype.length ES2020 21.1.3.10
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
        return JSNumber.of(length);
    }

    /**
     * String.prototype.includes(searchString[, position]) ES2020 21.1.3.7
     */
    public static JSValue includes(JSContext context, JSValue thisArg, JSValue[] args) {
        return searchString(context, thisArg, args, SearchMode.INCLUDES);
    }

    /**
     * String.prototype.indexOf(searchString[, position]) ES2020 21.1.3.8
     */
    public static JSValue indexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        String s = toStringCheckObject(context, thisArg).value();

        // A missing search string is undefined, which converts to "undefined"; it used to answer -1.
        String searchStr = JSTypeConversions.toString(context, JSValue.argumentOrUndefined(args, 0)).value();
        long position = args.length > 1 ? (long) JSTypeConversions.toInteger(context, args[1]) : 0;
        position = Math.max(0, Math.min(position, s.length()));

        int index = s.indexOf(searchStr, (int) position);
        return JSNumber.of(index);
    }

    private static long integerArgument(JSContext context, JSValue[] args, int index, long fallback) {
        return index >= args.length || args[index] instanceof JSUndefined
                ? fallback
                : (long) JSTypeConversions.toInteger(context, args[index]);
    }

    /**
     * ES2024 WhiteSpace + LineTerminator predicate. Matches: TAB, VT, FF, SP, NBSP, BOM/ZWNBSP, USP (Unicode
     * Space_Separator), LF, CR, LS, PS.
     */

    private static JSValue invokeRegExpMethod(JSContext context, JSValue thisArg, JSValue[] args,
            PropertyKey methodKey) {
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }
        boolean all = methodKey == PropertyKey.SYMBOL_MATCH_ALL;
        JSValue argument = JSValue.argumentOrUndefined(args, 0);
        if (argument instanceof JSObject object) {
            if (all && !requireGlobalRegExp(context, object, "matchAll")) {
                return JSUndefined.INSTANCE;
            }
            JSValue method = object.get(methodKey);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!method.isNullOrUndefined()) {
                return callRegExpMethod(context, method, object, thisArg);
            }
        }
        JSString input = toStringCheckObject(context, thisArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String pattern;
        if (all && argument instanceof JSRegExp regexp) {
            pattern = regexp.getPattern();
        } else {
            pattern = argument instanceof JSUndefined ? "" : JSTypeConversions.toString(context, argument).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        JSRegExp regexp = context.createJSRegExp(pattern, all ? "g" : "");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSValue method = regexp.get(methodKey);
        return context.hasPendingException() ? JSUndefined.INSTANCE : callRegExpMethod(context, method, regexp, input);
    }

    /**
     * String.prototype.isWellFormed() Returns true if the string contains no unpaired surrogates.
     */
    public static JSValue isWellFormed(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        boolean wellFormed = findInvalidCodePoint(str.value()) < 0;
        return JSBoolean.valueOf(wellFormed);
    }

    /**
     * String.prototype.italics() Wraps string in <i> element.
     */
    public static JSValue italics(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "i", null);
    }

    /**
     * String.prototype.lastIndexOf(searchString[, position]) ES2020 21.1.3.9
     */
    public static JSValue lastIndexOf(JSContext context, JSValue thisArg, JSValue[] args) {
        String s = toStringCheckObject(context, thisArg).value();

        // A missing search string is undefined, which converts to "undefined"; it used to answer -1.
        String searchStr = JSTypeConversions.toString(context, JSValue.argumentOrUndefined(args, 0)).value();
        // ES2024 22.1.3.9: If numPos is NaN, let pos be +∞; otherwise let pos be ToIntegerOrInfinity(numPos)
        long position;
        if (args.length > 1 && !args[1].isUndefined()) {
            double numPos = JSTypeConversions.toNumber(context, args[1]).value();
            if (Double.isNaN(numPos)) {
                position = s.length();
            } else {
                position = (long) JSTypeConversions.toInteger(context, args[1]);
            }
        } else {
            position = s.length();
        }
        position = Math.max(0, Math.min(position, s.length()));

        int index = s.lastIndexOf(searchStr, (int) position);
        return JSNumber.of(index);
    }

    /**
     * String.prototype.link(url) Creates an HTML anchor element with an href attribute.
     */
    public static JSValue link(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "a", "href");
    }

    private static JSValue localeCase(JSContext context, JSValue receiver, JSValue[] args, boolean upper) {
        JSString value = toStringCheckObject(context, receiver);
        Locale locale = JSIntlObject.resolveLocale(context, args, 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(upper
                ? UnicodeCaseMapping.upper(context.getUnicodePropertyResolver(), value.value(), locale)
                : UnicodeCaseMapping.lower(context.getUnicodePropertyResolver(), value.value(), locale));
    }

    /**
     * String.prototype.localeCompare(that [, locales [, options]]) ES2020 21.1.3.11 Compares two strings in the current
     * locale.
     */
    public static JSValue localeCompare(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String thisStr = str.value();

        JSString thatStr = JSTypeConversions.toString(context, JSValue.argumentOrUndefined(args, 0));
        String that = thatStr.value();

        JSValue localeValue = JSValue.argumentOrUndefined(args, 1);
        JSValue optionsValue = JSValue.argumentOrUndefined(args, 2);
        JSValue collatorValue = JSIntlObject.createCollator(context, context.createJSObject(),
                new JSValue[]{localeValue, optionsValue});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(collatorValue instanceof JSIntlCollator collator)) {
            return context.throwTypeError("Intl.Collator constructor returned invalid object");
        }
        int result = collator.compare(thisStr, that);
        return JSNumber.of(result);
    }

    /**
     * String.prototype.match(regexp) ES2020 21.1.3.10 Returns an array of matches when matching a string against a
     * regular expression.
     */
    public static JSValue match(JSContext context, JSValue thisArg, JSValue[] args) {
        return invokeRegExpMethod(context, thisArg, args, PropertyKey.SYMBOL_MATCH);
    }

    /**
     * String.prototype.matchAll(regexp) ES2020 21.1.3.11 Returns an iterator of all results matching a string against a
     * regular expression, including capturing groups.
     */
    public static JSValue matchAll(JSContext context, JSValue thisArg, JSValue[] args) {
        return invokeRegExpMethod(context, thisArg, args, PropertyKey.SYMBOL_MATCH_ALL);
    }

    /**
     * String.prototype.normalize([form]) Returns the Unicode Normalization Form of the string. Valid forms: "NFC"
     * (default), "NFD", "NFKC", "NFKD"
     */
    public static JSValue normalize(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // Default to NFC
        UnicodeNormalization.Form form = UnicodeNormalization.Form.NFC;

        if (args.length > 0 && !JSUndefined.INSTANCE.equals(args[0])) {
            JSString formStr = JSTypeConversions.toString(context, args[0]);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            String formName = formStr.value();

            // Parse normalization form
            switch (formName) {
                case "NFC" :
                    form = UnicodeNormalization.Form.NFC;
                    break;
                case "NFD" :
                    form = UnicodeNormalization.Form.NFD;
                    break;
                case "NFKC" :
                    form = UnicodeNormalization.Form.NFKC;
                    break;
                case "NFKD" :
                    form = UnicodeNormalization.Form.NFKD;
                    break;
                default :
                    return context.throwRangeError("The normalization form should be one of NFC, NFD, NFKC, NFKD.");
            }
        }

        String normalized = UnicodeNormalization.normalize(str.value(), form);
        return new JSString(normalized);
    }

    private static JSValue pad(JSContext context, JSValue thisArg, JSValue[] args, boolean atStart) {
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

        if (maxLength > JSString.MAX_LENGTH) {
            return context.throwRangeError("Invalid string length");
        }

        int fillLength = (int) maxLength - s.length();
        StringBuilder result = new StringBuilder();
        if (!atStart) {
            result.append(s);
        }
        // Limit uninterrupted copying even when the fill string is very large.
        for (int copied = 0; copied < fillLength;) {
            context.checkExecutionInterrupt();
            int offset = copied % fillStr.length();
            int count = Math.min(1024, Math.min(fillStr.length() - offset, fillLength - copied));
            result.append(fillStr, offset, offset + count);
            copied += count;
        }
        if (atStart) {
            result.append(s);
        }
        return new JSString(result.toString());
    }

    /**
     * String.prototype.padEnd(targetLength[, padString]) ES2020 21.1.3.11
     */
    public static JSValue padEnd(JSContext context, JSValue thisArg, JSValue[] args) {
        return pad(context, thisArg, args, false);
    }

    /**
     * String.prototype.padStart(targetLength[, padString]) ES2020 21.1.3.12
     */
    public static JSValue padStart(JSContext context, JSValue thisArg, JSValue[] args) {
        return pad(context, thisArg, args, true);
    }

    /**
     * String.prototype.repeat(count) ES2020 21.1.3.13
     */
    public static JSValue repeat(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        double countDouble = args.length > 0 ? JSTypeConversions.toInteger(context, args[0]) : 0;

        if (countDouble < 0 || Double.isInfinite(countDouble)) {
            return context.throwRangeError("Invalid count value");
        }

        long count = (long) countDouble;

        if (count == 0 || s.isEmpty()) {
            return JSString.EMPTY;
        }

        // Check the resulting length before allocating. Pre-sizing the builder to
        // min(s.length() * count, Integer.MAX_VALUE) attempted a 2 GiB char[] up front and failed
        // with OutOfMemoryError instead of the RangeError the specification calls for.
        //
        // Divide rather than multiply. `s.length() * count` is a long multiplication, and for a
        // count above Long.MAX_VALUE the (long) cast of the double saturates, so 'xx'.repeat(1e20)
        // wrapped to -2: the length guard passed and `new StringBuilder(-2)` raised
        // NegativeArraySizeException, escaping the script's own catch as an internal engine error.
        if (count > JSString.MAX_LENGTH / s.length()) {
            return context.throwRangeError("Invalid string length");
        }
        JSString result = JSString.EMPTY;
        JSString block = new JSString(s);
        while (count != 0) {
            context.checkExecutionInterrupt();
            if ((count & 1) != 0) {
                result = JSString.concat(result, block);
            }
            count >>>= 1;
            if (count != 0) {
                block = JSString.concat(block, block);
            }
        }
        return result;
    }

    /**
     * String.prototype.replace(searchValue, replaceValue) ES2020 21.1.3.14 Accepts a string or regular expression as
     * the first argument.
     */
    public static JSValue replace(JSContext context, JSValue thisArg, JSValue[] args) {
        return replace(context, thisArg, args, false);
    }

    private static JSValue replace(JSContext context, JSValue thisArg, JSValue[] args, boolean all) {
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }

        JSValue searchValue = JSValue.argumentOrUndefined(args, 0);
        JSValue replaceValue = JSValue.argumentOrUndefined(args, 1);
        if (searchValue instanceof JSObject searchValueObject) {
            if (all && !requireGlobalRegExp(context, searchValueObject, "replaceAll")) {
                return JSUndefined.INSTANCE;
            }
            JSValue replacer = searchValueObject.get(PropertyKey.SYMBOL_REPLACE);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(replacer instanceof JSUndefined) && !(replacer instanceof JSNull)) {
                if (!JSTypeChecking.isCallable(replacer)) {
                    return context.throwTypeError("not a function");
                }
                return JSCall.call(context, replacer, searchValueObject, new JSValue[]{thisArg, replaceValue});
            }
        }

        JSString str = toStringCheckObject(context, thisArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String s = str.value();
        String searchStr = JSTypeConversions.toString(context, searchValue).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        boolean functionalReplace = JSTypeChecking.isCallable(replaceValue);
        String replaceStr = functionalReplace ? null : JSTypeConversions.toString(context, replaceValue).value();
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (all) {
            return replaceAllMatches(context, str, searchStr, replaceValue, replaceStr);
        }
        int index = s.indexOf(searchStr);
        if (index < 0) {
            return str;
        }

        JSString replacement = StringReplacement.forStringMatch(context, searchStr, s, index, replaceValue, replaceStr);
        if (replacement == null) {
            return JSUndefined.INSTANCE;
        }
        return concatenateReplacement(context, new JSString(s.substring(0, index)), replacement,
                s.substring(index + searchStr.length()));
    }

    /**
     * String.prototype.replaceAll(searchValue, replaceValue) ES2020 21.1.3.15 Accepts a string or regular expression as
     * the first argument. If a RegExp is provided, it must have the global (g) flag.
     */
    public static JSValue replaceAll(JSContext context, JSValue thisArg, JSValue[] args) {
        return replace(context, thisArg, args, true);
    }

    private static JSValue replaceAllMatches(JSContext context, JSString str, String searchStr, JSValue replaceValue,
            String replaceStr) {
        String input = str.value();
        int position = input.indexOf(searchStr);
        if (position < 0) {
            return str;
        }
        StringResultBuilder result = new StringResultBuilder(context);
        int end = 0;
        while (position >= 0) {
            context.checkExecutionInterrupt();
            result.append(input, end, position);
            JSString replacement = StringReplacement.forStringMatch(context, searchStr, input, position, replaceValue,
                    replaceStr);
            if (replacement == null || context.hasPendingException() || result.exceedsMaxLength(replacement.length())) {
                return JSUndefined.INSTANCE;
            }
            result.append(replacement);
            end = position + searchStr.length();
            if (searchStr.isEmpty()) {
                if (position == input.length()) {
                    break;
                }
                position++;
            } else {
                position = input.indexOf(searchStr, end);
            }
        }
        if (result.exceedsMaxLength(input.length() - end)) {
            return JSUndefined.INSTANCE;
        }
        result.append(input, end, input.length());
        return result.build();
    }

    private static boolean requireGlobalRegExp(JSContext context, JSObject object, String operation) {
        int isRegExp = JSTypeChecking.isRegExp(context, object);
        if (context.hasPendingException() || isRegExp != 1) {
            return !context.hasPendingException();
        }
        JSValue flags = object.get(PropertyKey.fromString("flags"));
        if (context.hasPendingException()) {
            return false;
        }
        if (flags.isNullOrUndefined()) {
            context.throwTypeError("flags is null or undefined");
            return false;
        }
        JSString text = JSTypeConversions.toString(context, flags);
        if (context.hasPendingException()) {
            return false;
        }
        if (text.value().indexOf('g') < 0) {
            context.throwTypeError("String.prototype." + operation + " called with a non-global RegExp argument");
            return false;
        }
        return true;
    }

    /**
     * String.prototype.search(regexp) ES2020 21.1.3.15 Accepts a regular expression or string. Returns the index of the
     * first match, or -1.
     */
    public static JSValue search(JSContext context, JSValue thisArg, JSValue[] args) {
        return invokeRegExpMethod(context, thisArg, args, PropertyKey.SYMBOL_SEARCH);
    }

    private static JSValue searchString(JSContext context, JSValue thisArg, JSValue[] args, SearchMode mode) {
        String value = toStringCheckObject(context, thisArg).value();
        JSValue needle = JSValue.argumentOrUndefined(args, 0);
        int regexp = JSTypeChecking.isRegExp(context, needle);
        if (regexp < 0) {
            return JSUndefined.INSTANCE;
        }
        if (regexp > 0) {
            return context.throwTypeError("regexp not supported");
        }

        String search = JSTypeConversions.toString(context, needle).value();
        long position = args.length > 1 && !args[1].isUndefined()
                ? (long) JSTypeConversions.toInteger(context, args[1])
                : (mode == SearchMode.SUFFIX ? value.length() : 0);
        int offset = (int) Math.max(0, Math.min(position, value.length()));
        return JSBoolean.valueOf(switch (mode) {
            case INCLUDES -> value.indexOf(search, offset) >= 0;
            case PREFIX -> value.startsWith(search, offset);
            case SUFFIX -> value.startsWith(search, offset - search.length());
        });
    }

    /**
     * String.prototype.slice(beginIndex[, endIndex]) ES2020 21.1.3.16
     */
    public static JSValue slice(JSContext context, JSValue thisArg, JSValue[] args) {
        String s = toStringCheckObject(context, thisArg).value();
        int len = s.length();

        long begin = integerArgument(context, args, 0, 0);
        long end = integerArgument(context, args, 1, len);

        int startIndex = stringIndex(begin, len, true);
        int endIndex = stringIndex(end, len, true);
        return new JSString(s.substring(startIndex, Math.max(startIndex, endIndex)));
    }

    /**
     * String.prototype.small() Wraps string in <small> element.
     */
    public static JSValue small(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "small", null);
    }

    /**
     * String.prototype.split(separator[, limit]) ES2020 21.1.3.17
     */
    public static JSValue split(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }

        JSValue separatorArg = JSValue.argumentOrUndefined(args, 0);
        JSValue limitArg = JSValue.argumentOrUndefined(args, 1);
        if (separatorArg instanceof JSObject separatorObject) {
            JSValue splitter = separatorObject.get(PropertyKey.SYMBOL_SPLIT);
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
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String s = str.value();

        JSArray arr = context.createJSArray();
        // Per ES2024 spec ordering: ToUint32(limit) before ToString(separator)
        long limit = !(limitArg instanceof JSUndefined) ? JSTypeConversions.toUint32(context, limitArg) : 0xFFFFFFFFL;
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        // A RegExp reaches here only when its Symbol.split is undefined or null, and is then a string like any other
        // object (step 6). It used to be matched as a pattern anyway: /,/ split "a,b" where V8 looks for "/,/".
        String separator = null;
        if (!(separatorArg instanceof JSUndefined)) {
            separator = JSTypeConversions.toString(context, separatorArg).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        if (limit == 0) {
            return arr;
        }
        if (separatorArg instanceof JSUndefined) {
            arr.push(str);
            return arr;
        }

        // Handle string separator (already computed above)
        if (separator.isEmpty()) {
            // Split into individual characters
            for (int i = 0; i < s.length() && i < limit; i++) {
                context.checkExecutionInterrupt();
                arr.push(JSString.of(s.charAt(i)));
            }
            return arr;
        }

        // Manual split without using Java regex
        int start = 0;
        int index;
        while ((index = s.indexOf(separator, start)) != -1 && arr.getLength() < limit) {
            context.checkExecutionInterrupt();
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
     * String.prototype.startsWith(searchString[, position]) ES2020 21.1.3.21
     */
    public static JSValue startsWith(JSContext context, JSValue thisArg, JSValue[] args) {
        return searchString(context, thisArg, args, SearchMode.PREFIX);
    }

    /**
     * String.prototype.strike() Wraps string in <strike> element.
     */
    public static JSValue strike(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "strike", null);
    }

    private static int stringIndex(long value, int length, boolean relative) {
        if (relative && value < 0) {
            value += length;
        }
        return (int) Math.max(0, Math.min(value, length));
    }

    /**
     * String.prototype.sub() Wraps string in <sub> element.
     */
    public static JSValue sub(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "sub", null);
    }

    /**
     * String.prototype.substr(start[, length]) ES2020 B.2.3.1 (Deprecated, but still widely used)
     */
    public static JSValue substr(JSContext context, JSValue thisArg, JSValue[] args) {
        String s = toStringCheckObject(context, thisArg).value();
        int len = s.length();

        long start = integerArgument(context, args, 0, 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        long length = integerArgument(context, args, 1, len);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        start = stringIndex(start, len, true);
        length = Math.max(0, Math.min(length, len - start));

        return new JSString(s.substring((int) start, (int) (start + length)));
    }

    /**
     * String.prototype.substring(indexStart[, indexEnd]) ES2020 21.1.3.19
     */
    public static JSValue substring(JSContext context, JSValue thisArg, JSValue[] args) {
        String s = toStringCheckObject(context, thisArg).value();
        int len = s.length();

        long start = integerArgument(context, args, 0, 0);
        long end = integerArgument(context, args, 1, len);

        int first = stringIndex(start, len, false);
        int last = stringIndex(end, len, false);
        return new JSString(s.substring(Math.min(first, last), Math.max(first, last)));
    }

    /**
     * String.prototype.sup() Wraps string in <sup> element.
     */
    public static JSValue sup(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHTML(context, thisArg, args, "sup", null);
    }

    /**
     * String.prototype.toLocaleLowerCase([locale]) Converts string to lowercase according to locale-specific rules. For
     * now, this is the same as toLowerCase() (locale parameter is ignored).
     */
    public static JSValue toLocaleLowerCase(JSContext context, JSValue thisArg, JSValue[] args) {
        return localeCase(context, thisArg, args, false);
    }

    /**
     * String.prototype.toLocaleUpperCase([locale]) Converts string to uppercase according to locale-specific rules. For
     * now, this is the same as toUpperCase() (locale parameter is ignored).
     */
    public static JSValue toLocaleUpperCase(JSContext context, JSValue thisArg, JSValue[] args) {
        return localeCase(context, thisArg, args, true);
    }

    /**
     * String.prototype.toLowerCase() ES2020 21.1.3.22 Handles Greek final sigma per Unicode SpecialCasing.
     */
    public static JSValue toLowerCase(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        return new JSString(UnicodeCaseMapping.lower(context.getUnicodePropertyResolver(), str.value(), Locale.ROOT));
    }

    /**
     * String.prototype.toString() ES2020 21.1.3.23
     */
    public static JSValue toString_(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asStringWithDownCast().map(jsString -> (JSValue) jsString)
                .orElseGet(() -> context.throwTypeError("String.prototype.toString requires that 'this' be a String"));
    }

    /**
     * ES2024 7.2.8 IsRegExp(argument).
     */
    private static JSString toStringCheckObject(JSContext context, JSValue value) {
        if (value == null || value.isNullOrUndefined()) {
            context.throwTypeError("null or undefined are forbidden");
            return JSString.EMPTY;
        }
        return JSTypeConversions.toString(context, value);
    }

    /**
     * String.prototype.toUpperCase() ES2020 21.1.3.24
     */
    public static JSValue toUpperCase(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        return new JSString(UnicodeCaseMapping.upper(context.getUnicodePropertyResolver(), str.value(), Locale.ROOT));
    }

    /**
     * String.prototype.toWellFormed() Returns a string where all unpaired surrogates are replaced with U+FFFD
     * (replacement character).
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
            context.checkExecutionInterrupt();
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
     * String.prototype.trim() ES2020 21.1.3.26
     */
    public static JSValue trim(JSContext context, JSValue thisArg, JSValue[] args) {
        return trimString(context, thisArg, true, true);
    }

    /**
     * String.prototype.trimEnd() / trimRight() ES2020 21.1.3.28
     */
    public static JSValue trimEnd(JSContext context, JSValue thisArg, JSValue[] args) {
        return trimString(context, thisArg, false, true);
    }

    /**
     * String.prototype.trimStart() / trimLeft() ES2020 21.1.3.27
     */
    public static JSValue trimStart(JSContext context, JSValue thisArg, JSValue[] args) {
        return trimString(context, thisArg, true, false);
    }

    private static JSValue trimString(JSContext context, JSValue thisArg, boolean leading, boolean trailing) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();
        int start = 0;
        int end = s.length();
        while (leading && start < end && JSTypeConversions.isEcmaWhitespace(s.charAt(start))) {
            context.checkExecutionInterrupt();
            start++;
        }
        while (trailing && end > start && JSTypeConversions.isEcmaWhitespace(s.charAt(end - 1))) {
            context.checkExecutionInterrupt();
            end--;
        }
        return new JSString(s.substring(start, end));
    }

    /**
     * String.prototype.valueOf() ES2020 21.1.3.29
     */
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asStringWithDownCast().map(jsString -> (JSValue) jsString)
                .orElseGet(() -> context.throwTypeError("String.prototype.valueOf requires that 'this' be a String"));
    }

    private enum CharacterResult {
        CHARACTER,
        CODE_POINT,
        CODE_UNIT
    }

    private enum SearchMode {
        INCLUDES,
        PREFIX,
        SUFFIX
    }
}
