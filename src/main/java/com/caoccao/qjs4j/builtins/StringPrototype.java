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
import com.caoccao.qjs4j.unicode.UnicodeNormalization;
import com.caoccao.qjs4j.unicode.UnicodePropertyResolver;

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
     * Append a capture to a substitution, refusing to pass the string length limit. "$1" repeated in a replacement
     * multiplies a capture's length: GetSubstitution used to build such a string until the JVM ran out of memory.
     *
     * @return false with a pending RangeError when the result would be too long
     */
    private static boolean appendCapture(JSContext context, StringResultBuilder builder, String capture) {
        String text = capture != null ? capture : "";
        if (exceedsMaxLength(context, builder, text.length())) {
            return false;
        }
        builder.append(text);
        return true;
    }

    /**
     * GetSubstitution: expand the replacement template's tokens. Named-group property access and conversion stay lazy
     * and in template order.
     *
     * @param captures
     *            the match followed by its captures, a missing capture being null
     * @param namedCapturesValue
     *            the match result's groups, or null when there are none
     * @return the substitution, or null with a pending exception
     */
    private static JSString applyRegExpReplacementPattern(JSContext context, String replacementTemplate, String input,
            int matchStart, int matchEnd, String[] captures, JSValue namedCapturesValue) {
        // RegExp.prototype[Symbol.replace] has already refused null groups (ToObject), so only undefined means none.
        boolean hasNamedCaptures = namedCapturesValue != null && !(namedCapturesValue instanceof JSUndefined);
        StringResultBuilder resultBuilder = new StringResultBuilder(context);
        String prefix = null;
        String suffix = null;
        int replacementIndex = 0;
        while (replacementIndex < replacementTemplate.length()) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            char currentChar = replacementTemplate.charAt(replacementIndex);
            if (currentChar != '$') {
                // Preserve literal runs as a single leaf. Per-character appends rebuild short rope leaves for every
                // match and can allocate gigabytes of temporary strings before the result's length check fires.
                int nextToken = replacementTemplate.indexOf('$', replacementIndex + 1);
                int literalEnd = nextToken >= 0 ? nextToken : replacementTemplate.length();
                resultBuilder.append(replacementTemplate, replacementIndex, literalEnd);
                replacementIndex = literalEnd;
                continue;
            }
            if (replacementIndex + 1 >= replacementTemplate.length()) {
                resultBuilder.append(currentChar);
                replacementIndex++;
                continue;
            }
            char nextChar = replacementTemplate.charAt(replacementIndex + 1);
            if (nextChar == '$') {
                resultBuilder.append('$');
                replacementIndex += 2;
                continue;
            }
            if (nextChar == '&') {
                if (exceedsMaxLength(context, resultBuilder, captures[0].length())) {
                    return null;
                }
                resultBuilder.append(captures[0]);
                replacementIndex += 2;
                continue;
            }
            if (nextChar == '`') {
                if (exceedsMaxLength(context, resultBuilder, matchStart)) {
                    return null;
                }
                if (prefix == null) {
                    prefix = input.substring(0, matchStart);
                }
                resultBuilder.append(prefix);
                replacementIndex += 2;
                continue;
            }
            if (nextChar == '\'') {
                if (exceedsMaxLength(context, resultBuilder, input.length() - matchEnd)) {
                    return null;
                }
                if (suffix == null) {
                    suffix = input.substring(matchEnd);
                }
                resultBuilder.append(suffix);
                replacementIndex += 2;
                continue;
            }
            if (nextChar == '<') {
                if (!hasNamedCaptures) {
                    // Without named captures, consume only "$<" so later substitutions still run.
                    resultBuilder.append("$<");
                    replacementIndex += 2;
                    continue;
                }
                int closeIndex = replacementTemplate.indexOf('>', replacementIndex + 2);
                if (closeIndex >= 0) {
                    String groupName = replacementTemplate.substring(replacementIndex + 2, closeIndex);
                    JSObject namedCapturesObject = JSTypeConversions.toObject(context, namedCapturesValue);
                    JSValue groupValue = namedCapturesObject.get(PropertyKey.fromString(groupName));
                    if (context.hasPendingException()) {
                        return null;
                    }
                    if (!(groupValue instanceof JSUndefined)) {
                        String replacement = JSTypeConversions.toString(context, groupValue).value();
                        if (context.hasPendingException()
                                || exceedsMaxLength(context, resultBuilder, replacement.length())) {
                            return null;
                        }
                        resultBuilder.append(replacement);
                    }
                    replacementIndex = closeIndex + 1;
                    continue;
                }
            }
            if (nextChar == '0') {
                if (replacementIndex + 2 < replacementTemplate.length()) {
                    char secondDigit = replacementTemplate.charAt(replacementIndex + 2);
                    if (secondDigit >= '1' && secondDigit <= '9') {
                        int captureIndex = secondDigit - '0';
                        if (captureIndex < captures.length) {
                            if (!appendCapture(context, resultBuilder, captures[captureIndex])) {
                                return null;
                            }
                            replacementIndex += 3;
                            continue;
                        }
                    }
                }
            }
            if (nextChar >= '1' && nextChar <= '9') {
                int captureIndex = nextChar - '0';
                int consumedDigits = 1;
                if (replacementIndex + 2 < replacementTemplate.length()) {
                    char secondDigit = replacementTemplate.charAt(replacementIndex + 2);
                    if (secondDigit >= '0' && secondDigit <= '9') {
                        int twoDigitCaptureIndex = captureIndex * 10 + (secondDigit - '0');
                        if (twoDigitCaptureIndex < captures.length) {
                            captureIndex = twoDigitCaptureIndex;
                            consumedDigits = 2;
                        }
                    }
                }
                if (captureIndex < captures.length) {
                    if (!appendCapture(context, resultBuilder, captures[captureIndex])) {
                        return null;
                    }
                    replacementIndex += 1 + consumedDigits;
                    continue;
                }
            }
            resultBuilder.append('$');
            replacementIndex++;
        }
        return resultBuilder.build();
    }

    static JSString applyRegExpReplacementWithNamedCapturesObject(JSContext context, JSValue replaceValue, String input,
            int matchStart, int matchEnd, String[] captures, JSValue namedCapturesValue) {
        if (JSTypeChecking.isCallable(replaceValue)) {
            JSValue[] callbackArgs = buildRegExpReplaceCallbackArgs(context, input, matchStart, captures,
                    namedCapturesValue);
            if (context.hasPendingException()) {
                return null;
            }
            JSValue callbackResult = JSCall.call(context, replaceValue, JSUndefined.INSTANCE, callbackArgs);
            if (context.hasPendingException()) {
                return null;
            }
            return JSTypeConversions.toString(context, callbackResult);
        }
        String replacementTemplate = JSTypeConversions.toString(context, replaceValue).value();
        if (context.hasPendingException()) {
            return null;
        }
        return applyRegExpReplacementPattern(context, replacementTemplate, input, matchStart, matchEnd, captures,
                namedCapturesValue);
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

        return new JSString(String.valueOf(s.charAt((int) index)));
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

    private static JSValue[] buildRegExpReplaceCallbackArgs(JSContext context, String input, int matchStart,
            String[] captures, JSValue namedCapturesValue) {
        boolean hasNamedCaptures = !(namedCapturesValue instanceof JSUndefined);
        int callbackArgCount = captures.length + 2 + (hasNamedCaptures ? 1 : 0);
        JSValue[] callbackArgs = new JSValue[callbackArgCount];
        for (int captureIndex = 0; captureIndex < captures.length; captureIndex++) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            String captureValue = captures[captureIndex];
            callbackArgs[captureIndex] = captureValue != null ? new JSString(captureValue) : JSUndefined.INSTANCE;
        }
        callbackArgs[captures.length] = JSNumber.of(matchStart);
        callbackArgs[captures.length + 1] = new JSString(input);
        if (hasNamedCaptures) {
            callbackArgs[captures.length + 2] = namedCapturesValue;
        }
        return callbackArgs;
    }

    /**
     * String.prototype.charAt(index) ES2020 21.1.3.1
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
     * String.prototype.charCodeAt(index) ES2020 21.1.3.2
     */
    public static JSValue charCodeAt(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();
        long pos = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;

        if (pos < 0 || pos >= s.length()) {
            return JSNumber.of(Double.NaN);
        }

        return JSNumber.of(s.charAt((int) pos));
    }

    /**
     * String.prototype.codePointAt(index) ES2020 21.1.3.3
     */
    public static JSValue codePointAt(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();
        long pos = args.length > 0 ? (long) JSTypeConversions.toInteger(context, args[0]) : 0;

        if (pos < 0 || pos >= s.length()) {
            return JSUndefined.INSTANCE;
        }

        return JSNumber.of(s.codePointAt((int) pos));
    }

    private static int codePointToLower16(int codePoint) {
        return switch (codePoint) {
            case 0xA7DC -> 0x019B; // LATIN CAPITAL LETTER LAMBDA WITH STROKE -> small (Unicode 16.0)
            case 0xA7CB -> 0x0264; // LATIN CAPITAL LETTER RAMS HORN -> small (Unicode 15.0)
            case 0x1C89 -> 0x1C8A; // CYRILLIC CAPITAL LETTER TJE -> small (Unicode 15.0)
            case 0xA7CC -> 0xA7CD; // LATIN CAPITAL LETTER S WITH DIAGONAL STROKE -> small (Unicode 15.0)
            case 0xA7D0 -> 0xA7D1; // LATIN CAPITAL LETTER CLOSED INSULAR G -> small (Unicode 15.0)
            case 0xA7D6 -> 0xA7D7; // LATIN CAPITAL LETTER MIDDLE SCOTS S -> small (Unicode 15.0)
            case 0xA7D8 -> 0xA7D9; // LATIN CAPITAL LETTER SIGMOID S -> small (Unicode 15.0)
            case 0xA7DA -> 0xA7DB; // LATIN CAPITAL LETTER LAMBDA -> small (Unicode 15.0)
            case 0x2C2F -> 0x2C5F; // GLAGOLITIC CAPITAL LETTER CAUDATE CHRIVI -> small (Unicode 14.0)
            case 0xA7C0 -> 0xA7C1; // LATIN CAPITAL LETTER OLD POLISH O -> small (Unicode 14.0)
            case 0xA7CE -> 0xA7CF; // LATIN CAPITAL LETTER PHARYNGEAL VOICED FRICATIVE -> small (Unicode 15.0)
            case 0xA7D2 -> 0xA7D3; // LATIN CAPITAL LETTER DOUBLE THORN -> small (Unicode 15.0)
            case 0xA7D4 -> 0xA7D5; // LATIN CAPITAL LETTER HALF H -> small (Unicode 15.0)
            default -> {
                // Vithkuqi capital -> small (Unicode 15.0, offset 0x27)
                if (codePoint >= 0x10570 && codePoint <= 0x10595 && codePoint != 0x1057B && codePoint != 0x1058B) {
                    yield codePoint + 0x27;
                }
                // Garay uppercase -> lowercase (Unicode 16.0, offset 0x20)
                if (codePoint >= 0x10D50 && codePoint <= 0x10D65) {
                    yield codePoint + 0x20;
                }
                yield codePoint;
            }
        };
    }

    private static int codePointToUpper16(int codePoint) {
        return switch (codePoint) {
            case 0x019B -> 0xA7DC; // LATIN SMALL LETTER LAMBDA WITH STROKE -> CAPITAL (Unicode 16.0)
            case 0x0264 -> 0xA7CB; // LATIN SMALL LETTER RAMS HORN -> CAPITAL (Unicode 15.0)
            case 0x1C8A -> 0x1C89; // CYRILLIC SMALL LETTER TJE -> CAPITAL (Unicode 15.0)
            case 0xA7CD -> 0xA7CC; // LATIN SMALL LETTER S WITH DIAGONAL STROKE -> CAPITAL (Unicode 15.0)
            case 0xA7D1 -> 0xA7D0; // LATIN SMALL LETTER CLOSED INSULAR G -> CAPITAL (Unicode 15.0)
            case 0xA7D7 -> 0xA7D6; // LATIN SMALL LETTER MIDDLE SCOTS S -> CAPITAL (Unicode 15.0)
            case 0xA7D9 -> 0xA7D8; // LATIN SMALL LETTER SIGMOID S -> CAPITAL (Unicode 15.0)
            case 0xA7DB -> 0xA7DA; // LATIN SMALL LETTER LAMBDA -> CAPITAL (Unicode 15.0)
            case 0x2C5F -> 0x2C2F; // GLAGOLITIC SMALL LETTER CAUDATE CHRIVI -> CAPITAL (Unicode 14.0)
            case 0xA7C1 -> 0xA7C0; // LATIN SMALL LETTER OLD POLISH O -> CAPITAL (Unicode 14.0)
            case 0xA7CF -> 0xA7CE; // LATIN SMALL LETTER PHARYNGEAL VOICED FRICATIVE -> CAPITAL (Unicode 15.0)
            case 0xA7D3 -> 0xA7D2; // LATIN SMALL LETTER DOUBLE THORN -> CAPITAL (Unicode 15.0)
            case 0xA7D5 -> 0xA7D4; // LATIN SMALL LETTER HALF H -> CAPITAL (Unicode 15.0)
            default -> {
                // Vithkuqi small -> capital (Unicode 15.0, offset 0x27)
                if (codePoint >= 0x10597 && codePoint <= 0x105BC && codePoint != 0x105A2 && codePoint != 0x105B2) {
                    yield codePoint - 0x27;
                }
                // Garay lowercase -> uppercase (Unicode 16.0, offset 0x20)
                if (codePoint >= 0x10D70 && codePoint <= 0x10D85) {
                    yield codePoint - 0x20;
                }
                yield codePoint;
            }
        };
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
            context.getVirtualMachine().checkExecutionInterruptForNative();
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
                context.getVirtualMachine().checkExecutionInterruptForNative();
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
     * Whether appending would pass {@link JSString#MAX_LENGTH}; if so, raise {@code RangeError: Invalid string length}.
     */
    private static boolean exceedsMaxLength(JSContext context, StringResultBuilder builder, long addition) {
        if (builder.length() + addition > JSString.MAX_LENGTH) {
            context.throwRangeError("Invalid string length");
            return true;
        }
        return false;
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
     * ES2024 GetSubstitution for string replace/replaceAll.
     */
    private static JSString getSubstitution(JSContext context, String matched, String str, int position,
            String replacement) {
        return applyRegExpReplacementPattern(context, replacement, str, position, position + matched.length(),
                new String[]{matched}, null);
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
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

        // A missing search string is undefined, which converts to "undefined"; it used to answer -1.
        String searchStr = JSTypeConversions.toString(context, JSValue.argumentOrUndefined(args, 0)).value();
        long position = args.length > 1 ? (long) JSTypeConversions.toInteger(context, args[1]) : 0;
        position = Math.max(0, Math.min(position, s.length()));

        int index = s.indexOf(searchStr, (int) position);
        return JSNumber.of(index);
    }

    private static JSValue invokeRegExpMethod(JSContext context, JSValue thisArg, JSValue[] args,
            PropertyKey methodKey) {
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }

        JSValue regexpArg = JSValue.argumentOrUndefined(args, 0);
        if (regexpArg instanceof JSObject regexpObj) {
            JSValue matcher = regexpObj.get(methodKey);
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

        // Step 6: Let rx be RegExpCreate(regexp, undefined).
        String pattern;
        if (regexpArg instanceof JSUndefined) {
            pattern = "";
        } else {
            pattern = JSTypeConversions.toString(context, regexpArg).value();
        }
        JSRegExp rx = context.createJSRegExp(pattern, "");
        // Invoke the method on the newly created RegExp with the coerced string.
        JSValue method = rx.get(methodKey);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (method instanceof JSFunction function) {
            return function.call(context, rx, new JSValue[]{str});
        }
        return context.throwTypeError("not a function");
    }

    /**
     * Check if a code point is "Cased" per Unicode property. Uses the full Unicode Cased property table.
     */
    private static boolean isCasedUnicode(UnicodePropertyResolver resolver, int codePoint) {
        int[] ranges = resolver.resolveBinaryProperty("Cased");
        if (ranges == null) {
            return Character.isUpperCase(codePoint) || Character.isLowerCase(codePoint)
                    || Character.isTitleCase(codePoint);
        }
        return isInRanges(codePoint, ranges);
    }

    /**
     * Check if a code point is "Case_Ignorable" per Unicode property. Uses the full Unicode Case_Ignorable property
     * table.
     */
    private static boolean isCaseIgnorableUnicode(UnicodePropertyResolver resolver, int codePoint) {
        int type = Character.getType(codePoint);
        boolean fallbackCaseIgnorable = type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.FORMAT || type == Character.MODIFIER_LETTER || type == Character.MODIFIER_SYMBOL;
        int[] ranges = resolver.resolveBinaryProperty("Case_Ignorable");
        if (ranges == null) {
            return fallbackCaseIgnorable;
        }
        return isInRanges(codePoint, ranges) || fallbackCaseIgnorable;
    }

    /**
     * Check if a code point is a combining mark.
     */
    private static boolean isCombiningMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    /**
     * ES2024 WhiteSpace + LineTerminator predicate. Matches: TAB, VT, FF, SP, NBSP, BOM/ZWNBSP, USP (Unicode
     * Space_Separator), LF, CR, LS, PS.
     */
    private static boolean isEcmaWhitespace(char ch) {
        return ch == '\t' || ch == '\n' || ch == '\u000B' || ch == '\f' || ch == '\r' || ch == ' ' || ch == '\u00A0'
                || ch == '\uFEFF' || ch == '\u1680' || (ch >= '\u2000' && ch <= '\u200A') || ch == '\u2028'
                || ch == '\u2029' || ch == '\u202F' || ch == '\u205F' || ch == '\u3000';
    }

    /**
     * Test if sigma at position sigmaPos is in a "final" context. Final sigma: preceded by a cased letter (skipping
     * case-ignorable) and NOT followed by a cased letter (skipping case-ignorable).
     */
    private static boolean isFinalSigma(UnicodePropertyResolver resolver, String s, int sigmaPos) {
        // Look backward: skip case-ignorable, check for cased
        int k = sigmaPos;
        int prevCodePoint = -1;
        while (k > 0) {
            int cp = Character.codePointBefore(s, k);
            k -= Character.charCount(cp);
            if (!isCaseIgnorableUnicode(resolver, cp)) {
                prevCodePoint = cp;
                break;
            }
        }
        if (prevCodePoint == -1 || !isCasedUnicode(resolver, prevCodePoint)) {
            return false;
        }

        // Look forward: skip case-ignorable, check for NOT cased
        k = sigmaPos + 1;
        while (k < s.length()) {
            int cp = s.codePointAt(k);
            k += Character.charCount(cp);
            if (!isCaseIgnorableUnicode(resolver, cp)) {
                return !isCasedUnicode(resolver, cp);
            }
        }
        return true; // End of string, no following cased letter
    }

    /**
     * Binary search to check if a code point is in any of the given ranges. Ranges are stored as [start1, end1, start2,
     * end2, ...] (inclusive).
     */
    private static boolean isInRanges(int codePoint, int[] ranges) {
        int low = 0;
        int high = ranges.length / 2 - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int start = ranges[mid * 2];
            int end = ranges[mid * 2 + 1];
            if (codePoint < start) {
                high = mid - 1;
            } else if (codePoint > end) {
                low = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a code point has the Unicode Soft_Dotted property.
     */
    private static boolean isSoftDottedUnicode(UnicodePropertyResolver resolver, int codePoint) {
        int[] ranges = resolver.resolveBinaryProperty("Soft_Dotted");
        if (ranges == null) {
            return false;
        }
        return isInRanges(codePoint, ranges);
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
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();

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
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }

        JSValue regexpArg = JSValue.argumentOrUndefined(args, 0);
        // Step 2: If regexp is neither undefined nor null
        if (!(regexpArg instanceof JSUndefined) && !(regexpArg instanceof JSNull)) {
            // Step 2a: isRegExp check
            if (JSTypeChecking.isRegExp(context, regexpArg) == 1) {
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                // Step 2a.ii: Check flags contain "g"
                if (regexpArg instanceof JSObject regObj) {
                    JSValue flags = regObj.get(PropertyKey.fromString("flags"));
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    if (flags instanceof JSUndefined || flags instanceof JSNull) {
                        return context.throwTypeError("flags is null or undefined");
                    }
                    String flagsStr = JSTypeConversions.toString(context, flags).value();
                    if (!flagsStr.contains("g")) {
                        return context
                                .throwTypeError("String.prototype.matchAll called with a non-global RegExp argument");
                    }
                }
            }
            // Step 2b: GetMethod(regexp, @@matchAll)
            if (regexpArg instanceof JSObject regexpObj) {
                JSValue matcher = regexpObj.get(PropertyKey.SYMBOL_MATCH_ALL);
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
        }

        // Step 3: Let S be ? ToString(O).
        JSString str = toStringCheckObject(context, thisArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // Step 4: Let rx be ? RegExpCreate(regexp, "g").
        String pattern;
        if (regexpArg instanceof JSRegExp inputRegExp) {
            pattern = inputRegExp.getPattern();
        } else if (regexpArg instanceof JSUndefined || regexpArg instanceof JSNull) {
            pattern = regexpArg instanceof JSUndefined ? "" : "null";
        } else {
            pattern = JSTypeConversions.toString(context, regexpArg).value();
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        JSRegExp rx = context.createJSRegExp(pattern, "g");
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // Step 5: Return ? Invoke(rx, @@matchAll, « S »).
        JSValue matchAllMethod = rx.get(PropertyKey.SYMBOL_MATCH_ALL);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (matchAllMethod instanceof JSFunction matchAllFunction) {
            return matchAllFunction.call(context, rx, new JSValue[]{str});
        }
        return context.throwTypeError("RegExp.prototype[Symbol.matchAll] is not a function");
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
            context.getVirtualMachine().checkExecutionInterruptForNative();
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
     * Lithuanian uppercasing removes U+0307 when it follows a Soft_Dotted code point with only combining marks in
     * between.
     */
    private static String removeLithuanianSoftDottedDots(UnicodePropertyResolver resolver, String input) {
        StringBuilder result = new StringBuilder(input.length());
        int index = 0;
        while (index < input.length()) {
            int codePoint = input.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            if (codePoint == 0x0307) {
                int lookBackIndex = result.length();
                int previousCodePoint = -1;
                while (lookBackIndex > 0) {
                    previousCodePoint = Character.codePointBefore(result, lookBackIndex);
                    lookBackIndex -= Character.charCount(previousCodePoint);
                    if (!isCombiningMark(previousCodePoint)) {
                        break;
                    }
                }
                if (previousCodePoint != -1 && isSoftDottedUnicode(resolver, previousCodePoint)) {
                    index += charCount;
                    continue;
                }
            }
            result.appendCodePoint(codePoint);
            index += charCount;
        }
        return result.toString();
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
            return new JSString("");
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
        JSString result = new JSString("");
        JSString block = new JSString(s);
        while (count != 0) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
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
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }

        JSValue searchValue = JSValue.argumentOrUndefined(args, 0);
        JSValue replaceValue = JSValue.argumentOrUndefined(args, 1);
        if (searchValue instanceof JSObject searchValueObject) {
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
        String s = str.value();
        // A RegExp whose Symbol.replace is undefined or null is searched for as its string, like any other object.
        String searchStr = JSTypeConversions.toString(context, searchValue).value();
        boolean functionalReplace = JSTypeChecking.isCallable(replaceValue);
        String replaceStr = functionalReplace ? null : JSTypeConversions.toString(context, replaceValue).value();

        int index = s.indexOf(searchStr);
        if (index < 0) {
            return str;
        }

        JSString replacement;
        if (functionalReplace) {
            JSValue replResult = JSCall.call(context, replaceValue, JSUndefined.INSTANCE,
                    new JSValue[]{new JSString(searchStr), JSNumber.of(index), new JSString(s)});
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            replacement = JSTypeConversions.toString(context, replResult);
        } else {
            replacement = getSubstitution(context, searchStr, s, index, replaceStr);
            if (replacement == null) {
                return JSUndefined.INSTANCE;
            }
        }
        return concatenateReplacement(context, new JSString(s.substring(0, index)), replacement,
                s.substring(index + searchStr.length()));
    }

    /**
     * String.prototype.replaceAll(searchValue, replaceValue) ES2020 21.1.3.15 Accepts a string or regular expression as
     * the first argument. If a RegExp is provided, it must have the global (g) flag.
     */
    public static JSValue replaceAll(JSContext context, JSValue thisArg, JSValue[] args) {
        if (thisArg == null || thisArg.isNullOrUndefined()) {
            return context.throwTypeError("cannot convert to object");
        }

        JSValue searchValue = JSValue.argumentOrUndefined(args, 0);
        JSValue replaceValue = JSValue.argumentOrUndefined(args, 1);
        // Step 2: If searchValue is neither undefined nor null
        if (!(searchValue instanceof JSUndefined) && !(searchValue instanceof JSNull)) {
            // Step 2a: isRegExp check
            if (JSTypeChecking.isRegExp(context, searchValue) == 1) {
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                // Step 2a.ii: Check flags contain "g"
                if (searchValue instanceof JSObject searchObj) {
                    JSValue flags = searchObj.get(PropertyKey.fromString("flags"));
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    String flagsStr = JSTypeConversions.toString(context, flags).value();
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    if (!flagsStr.contains("g")) {
                        return context
                                .throwTypeError("String.prototype.replaceAll called with a non-global RegExp argument");
                    }
                }
            }
            // Step 2b: GetMethod(searchValue, @@replace)
            if (searchValue instanceof JSObject searchValueObject) {
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

        // Collect all match positions
        java.util.List<Integer> matchPositions = new java.util.ArrayList<>();
        int searchLen = searchStr.length();
        int pos = 0;
        if (searchLen == 0) {
            // Empty search: match at every position including end
            for (int i = 0; i <= s.length(); i++) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                matchPositions.add(i);
            }
        } else {
            while ((pos = s.indexOf(searchStr, pos)) >= 0) {
                context.getVirtualMachine().checkExecutionInterruptForNative();
                matchPositions.add(pos);
                pos += searchLen;
            }
        }

        // Build result
        StringResultBuilder result = new StringResultBuilder(context);
        int endOfLastMatch = 0;
        for (int matchPosition : matchPositions) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            result.append(s, endOfLastMatch, matchPosition);
            JSString replacement;
            if (functionalReplace) {
                JSValue replResult = JSCall.call(context, replaceValue, JSUndefined.INSTANCE,
                        new JSValue[]{new JSString(searchStr), JSNumber.of(matchPosition), new JSString(s)});
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                replacement = JSTypeConversions.toString(context, replResult);
            } else {
                replacement = getSubstitution(context, searchStr, s, matchPosition, replaceStr);
                if (replacement == null) {
                    return JSUndefined.INSTANCE;
                }
            }
            if (exceedsMaxLength(context, result, replacement.length())) {
                return JSUndefined.INSTANCE;
            }
            result.append(replacement);
            endOfLastMatch = matchPosition + searchLen;
        }
        return concatenateReplacement(context, result.build(), new JSString(""), s.substring(endOfLastMatch));
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
                context.getVirtualMachine().checkExecutionInterruptForNative();
                arr.push(new JSString(String.valueOf(s.charAt(i))));
            }
            return arr;
        }

        // Manual split without using Java regex
        int start = 0;
        int index;
        while ((index = s.indexOf(separator, start)) != -1 && arr.getLength() < limit) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
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
     * String.prototype.substring(indexStart[, indexEnd]) ES2020 21.1.3.19
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
        JSString str = toStringCheckObject(context, thisArg);
        Locale locale = JSIntlObject.resolveLocale(context, args, 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String language = locale.getLanguage();
        if ("tr".equals(language) || "az".equals(language) || "lt".equals(language)) {
            return new JSString(str.value().toLowerCase(locale));
        }
        return new JSString(toLowerCaseWithSigma(context.getUnicodePropertyResolver(), str.value()));
    }

    /**
     * String.prototype.toLocaleUpperCase([locale]) Converts string to uppercase according to locale-specific rules. For
     * now, this is the same as toUpperCase() (locale parameter is ignored).
     */
    public static JSValue toLocaleUpperCase(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        Locale locale = JSIntlObject.resolveLocale(context, args, 0);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String source = str.value();
        if ("lt".equals(locale.getLanguage())) {
            source = removeLithuanianSoftDottedDots(context.getUnicodePropertyResolver(), source);
        }
        return new JSString(toUpperCaseUnicode16(source.toUpperCase(locale)));
    }

    /**
     * String.prototype.toLowerCase() ES2020 21.1.3.22 Handles Greek final sigma per Unicode SpecialCasing.
     */
    public static JSValue toLowerCase(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        return new JSString(toLowerCaseWithSigma(context.getUnicodePropertyResolver(), str.value()));
    }

    /**
     * Custom toLowerCase that handles the Greek final sigma rule. When U+03A3 (SIGMA) is at a "final" position
     * (preceded by a cased letter with possible case-ignorable characters in between, and NOT followed by a cased
     * letter), it maps to U+03C2 (final sigma) instead of U+03C3.
     */
    private static String toLowerCaseWithSigma(UnicodePropertyResolver resolver, String s) {
        boolean needsCustom = false;
        for (int i = 0; i < s.length();) {
            int codePoint = s.codePointAt(i);
            if (codePoint == 0x03A3 || codePointToLower16(codePoint) != codePoint) {
                needsCustom = true;
                break;
            }
            i += Character.charCount(codePoint);
        }
        if (!needsCustom) {
            return s.toLowerCase();
        }

        StringBuilder result = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int codePoint = s.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            if (codePoint == 0x03A3) {
                // Check final sigma condition
                if (isFinalSigma(resolver, s, i)) {
                    result.append('\u03C2'); // final sigma
                } else {
                    result.append('\u03C3'); // regular sigma
                }
            } else {
                int lower = Character.toLowerCase(codePoint);
                // Apply Unicode 16.0 additions
                int lower16 = codePointToLower16(lower);
                result.appendCodePoint(lower16);
            }
            i += charCount;
        }
        return result.toString();
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
            return new JSString("");
        }
        return JSTypeConversions.toString(context, value);
    }

    /**
     * String.prototype.toUpperCase() ES2020 21.1.3.24
     */
    public static JSValue toUpperCase(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        return new JSString(toUpperCaseUnicode16(str.value()));
    }

    /**
     * toUpperCase with Unicode 16.0 case mappings that Java 17 (Unicode 14.0) lacks.
     */
    private static String toUpperCaseUnicode16(String s) {
        String result = s.toUpperCase();
        // Apply Unicode 16.0 additions
        boolean needsPatch = false;
        for (int i = 0; i < result.length();) {
            int codePoint = result.codePointAt(i);
            if (codePointToUpper16(codePoint) != codePoint) {
                needsPatch = true;
                break;
            }
            i += Character.charCount(codePoint);
        }
        if (!needsPatch) {
            return result;
        }
        StringBuilder sb = new StringBuilder(result.length());
        for (int i = 0; i < result.length();) {
            int codePoint = result.codePointAt(i);
            sb.appendCodePoint(codePointToUpper16(codePoint));
            i += Character.charCount(codePoint);
        }
        return sb.toString();
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
            context.getVirtualMachine().checkExecutionInterruptForNative();
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
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();
        int start = 0;
        int end = s.length();
        while (start < end && isEcmaWhitespace(s.charAt(start))) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            start++;
        }
        while (end > start && isEcmaWhitespace(s.charAt(end - 1))) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            end--;
        }
        return new JSString(s.substring(start, end));
    }

    /**
     * String.prototype.trimEnd() / trimRight() ES2020 21.1.3.28
     */
    public static JSValue trimEnd(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();
        int end = s.length();
        while (end > 0 && isEcmaWhitespace(s.charAt(end - 1))) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            end--;
        }
        return new JSString(s.substring(0, end));
    }

    /**
     * String.prototype.trimStart() / trimLeft() ES2020 21.1.3.27
     */
    public static JSValue trimStart(JSContext context, JSValue thisArg, JSValue[] args) {
        JSString str = toStringCheckObject(context, thisArg);
        String s = str.value();
        int start = 0;
        while (start < s.length() && isEcmaWhitespace(s.charAt(start))) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
            start++;
        }
        return new JSString(s.substring(start));
    }

    /**
     * String.prototype.valueOf() ES2020 21.1.3.29
     */
    public static JSValue valueOf(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg.asStringWithDownCast().map(jsString -> (JSValue) jsString)
                .orElseGet(() -> context.throwTypeError("String.prototype.valueOf requires that 'this' be a String"));
    }

    private enum SearchMode {
        INCLUDES, PREFIX, SUFFIX
    }
}
