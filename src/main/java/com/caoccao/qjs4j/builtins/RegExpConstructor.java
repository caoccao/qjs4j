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
 * Implementation of RegExp constructor and static methods.
 * Based on ES2024 RegExp specification.
 */
public final class RegExpConstructor {
    private static final String OTHER_PUNCTUATORS = ",-=<>#&!%:;@~'`\"";
    private static final String REGEXP_SYNTAX_CHARACTERS = "^$\\\\.*+?()[]{}|/";

    private static void appendEscapedCodeUnit(StringBuilder escapedBuilder, char codeUnit) {
        if (REGEXP_SYNTAX_CHARACTERS.indexOf(codeUnit) >= 0) {
            escapedBuilder.append('\\').append(codeUnit);
            return;
        }
        switch (codeUnit) {
            case '\t':
                escapedBuilder.append("\\t");
                return;
            case '\n':
                escapedBuilder.append("\\n");
                return;
            case 0x0B:
                escapedBuilder.append("\\v");
                return;
            case '\f':
                escapedBuilder.append("\\f");
                return;
            case '\r':
                escapedBuilder.append("\\r");
                return;
            case ' ':
                escapedBuilder.append("\\x20");
                return;
            default:
                break;
        }

        if (OTHER_PUNCTUATORS.indexOf(codeUnit) >= 0) {
            appendHexEscape(escapedBuilder, codeUnit);
            return;
        }

        if (isEcmaWhitespaceOrLineTerminator(codeUnit)) {
            if (codeUnit <= 0xFF) {
                appendHexEscape(escapedBuilder, codeUnit);
            } else {
                appendUnicodeEscape(escapedBuilder, codeUnit);
            }
            return;
        }

        escapedBuilder.append(codeUnit);
    }

    private static void appendHexEscape(StringBuilder escapedBuilder, int codePoint) {
        escapedBuilder.append("\\x");
        appendLowerHexPadded(escapedBuilder, codePoint, 2);
    }

    private static void appendLowerHexPadded(StringBuilder escapedBuilder, int value, int width) {
        String hex = Integer.toHexString(value);
        for (int paddingIndex = hex.length(); paddingIndex < width; paddingIndex++) {
            escapedBuilder.append('0');
        }
        escapedBuilder.append(hex);
    }

    private static void appendUnicodeEscape(StringBuilder escapedBuilder, int codeUnit) {
        escapedBuilder.append("\\u");
        appendLowerHexPadded(escapedBuilder, codeUnit, 4);
    }

    /**
     * RegExp called as a function (without new).
     * <p>
     * ES2024 22.2.3.1 RegExp(pattern, flags)
     * When called as a function (new.target is undefined):
     * - If pattern is regexp-like and flags is undefined, and pattern.constructor === RegExp,
     * return pattern unchanged.
     * - Otherwise, delegate to the constructor path.
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue patternArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue flagsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        // ES2024 22.2.3.1 step 3: If new.target is undefined (called as function)
        // Check if patternIsRegExp and flags is undefined
        if (flagsArg instanceof JSUndefined) {
            boolean patternIsRegExp = isRegExp(context, patternArg);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (patternIsRegExp) {
                // Step 3b: Get pattern.constructor
                if (patternArg instanceof JSObject patternObj) {
                    JSValue patternConstructor = patternObj.get(PropertyKey.CONSTRUCTOR);
                    if (context.hasPendingException()) {
                        return context.getPendingException();
                    }
                    // Step 3b-iii: If SameValue(newTarget, patternConstructor), return pattern
                    JSValue regexpConstructor = context.getGlobalObject().get(JSRegExp.NAME);
                    if (patternConstructor == regexpConstructor) {
                        return patternArg;
                    }
                }
            }
        }

        // Fall through to normal constructor behavior
        return JSRegExp.create(context, args);
    }

    /**
     * RegExp.escape(string)
     * ES2024 22.2.5 RegExp.escape
     */
    public static JSValue escape(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue stringValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(stringValue instanceof JSString jsString)) {
            return context.throwTypeError("input argument must be a string");
        }

        String input = jsString.value();
        if (input.isEmpty()) {
            return new JSString("");
        }

        StringBuilder escapedBuilder = new StringBuilder(input.length() * 2);
        int stringIndex = 0;
        if (isAsciiLetterOrDigit(input.charAt(0))) {
            appendHexEscape(escapedBuilder, input.charAt(0));
            stringIndex = 1;
        }

        while (stringIndex < input.length()) {
            char currentChar = input.charAt(stringIndex);
            if (Character.isHighSurrogate(currentChar)) {
                if (stringIndex + 1 < input.length() && Character.isLowSurrogate(input.charAt(stringIndex + 1))) {
                    escapedBuilder.append(currentChar).append(input.charAt(stringIndex + 1));
                    stringIndex += 2;
                    continue;
                }
                appendUnicodeEscape(escapedBuilder, currentChar);
                stringIndex++;
                continue;
            }
            if (Character.isLowSurrogate(currentChar)) {
                appendUnicodeEscape(escapedBuilder, currentChar);
                stringIndex++;
                continue;
            }
            appendEscapedCodeUnit(escapedBuilder, currentChar);
            stringIndex++;
        }

        return new JSString(escapedBuilder.toString());
    }

    /**
     * get RegExp[@@species]
     * ES2024 22.2.4.2
     * Returns the this value.
     */
    public static JSValue getSpecies(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg;
    }

    private static boolean isAsciiLetterOrDigit(char value) {
        if (value >= '0' && value <= '9') {
            return true;
        }
        if (value >= 'A' && value <= 'Z') {
            return true;
        }
        return value >= 'a' && value <= 'z';
    }

    private static boolean isEcmaWhitespaceOrLineTerminator(char value) {
        if (value == '\n' || value == '\r' || value == '\t' || value == '\f' || value == 0x0B || value == ' ') {
            return true;
        }
        if (value == 0x00A0 || value == 0x1680 || value == 0x2028 || value == 0x2029
                || value == 0x202F || value == 0x205F || value == 0x3000 || value == 0xFEFF) {
            return true;
        }
        return value >= 0x2000 && value <= 0x200A;
    }

    /**
     * Check if a value is regexp-like per ES2024 IsRegExp.
     * Checks Symbol.match first, then falls back to instanceof JSRegExp.
     */
    private static boolean isRegExp(JSContext context, JSValue value) {
        if (!(value instanceof JSObject obj)) {
            return false;
        }
        // Check Symbol.match property
        JSValue matchProp = obj.get(PropertyKey.SYMBOL_MATCH);
        if (context.hasPendingException()) {
            return false;
        }
        if (!(matchProp instanceof JSUndefined)) {
            return JSTypeConversions.toBoolean(matchProp).value();
        }
        // Fall back to instanceof check
        return value instanceof JSRegExp;
    }
}
