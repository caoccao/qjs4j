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

package com.caoccao.qjs4j.builtins.regexp;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.regexp.JSRegExp;
import com.caoccao.qjs4j.regexp.RegExpEngine;

/**
 * Implementation of JavaScript RegExp.prototype methods. Based on ES2020 RegExp specification.
 */
public final class RegExpPrototype {
    static final PropertyKey PROPERTY_FLAGS = PropertyKey.fromString("flags");

    /**
     * RegExp.prototype.compile(pattern, flags) AnnexB B.2.5.1 Reinitializes the RegExp object in-place.
     */
    public static JSValue compile(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.compile called on non-RegExp");
        }
        JSValue realmRegExpConstructor = context.getCachedRegExpConstructor();
        JSValue receiverConstructor = regexp.get(PropertyKey.CONSTRUCTOR);
        if (realmRegExpConstructor != receiverConstructor) {
            return context.throwTypeError("RegExp.prototype.compile called on incompatible receiver");
        }

        String pattern = "";
        String flags = "";
        if (args.length > 0) {
            JSValue patternArg = args[0];
            if (patternArg instanceof JSRegExp existingRegExp) {
                if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
                    return context.throwTypeError("Cannot supply flags when constructing one RegExp from another");
                }
                pattern = existingRegExp.getPattern();
                flags = existingRegExp.getFlags();
            } else if (!(patternArg instanceof JSUndefined)) {
                pattern = JSTypeConversions.toString(context, patternArg).value();
            }
            if (args.length > 1 && !(args[1] instanceof JSUndefined) && !(args[0] instanceof JSRegExp)) {
                flags = JSTypeConversions.toString(context, args[1]).value();
            }
        }

        try {
            regexp.reinitialize(pattern, flags);
        } catch (Exception e) {
            return context.throwSyntaxError(JSRegExp.describeSyntaxError(pattern, flags, e));
        }

        // Spec step 12: Perform ? Set(obj, "lastIndex", 0, true).
        // The 'true' means throw TypeError if the property is non-writable.
        PropertyDescriptor lastIndexDesc = regexp.getOwnPropertyDescriptor(PropertyKey.LAST_INDEX);
        if (lastIndexDesc != null && !lastIndexDesc.isWritable()) {
            return context.throwTypeError("Cannot assign to read only property 'lastIndex'");
        }
        regexp.setLastIndex(0);

        return regexp;
    }

    private static JSValue createIndexPairValue(JSContext context, int[] pair) {
        if (pair == null || pair.length < 2 || pair[0] < 0 || pair[1] < 0) {
            return JSUndefined.INSTANCE;
        }
        JSArray range = context.createJSArray();
        range.push(JSNumber.of(pair[0]));
        range.push(JSNumber.of(pair[1]));
        return range;
    }

    private static JSValue createIndicesValue(JSContext context, int[][] indices, String[] groupNames) {
        if (indices == null) {
            return JSUndefined.INSTANCE;
        }
        JSArray indicesArray = context.createJSArray();
        JSObject groupIndices = new JSObject(context);
        groupIndices.setPrototype(null);
        for (int i = 0; i < indices.length; i++) {
            JSValue pairValue = createIndexPairValue(context, indices[i]);
            indicesArray.push(pairValue);
            if (i > 0 && groupNames != null && i < groupNames.length) {
                String groupName = groupNames[i];
                if (groupName != null) {
                    if (!(pairValue instanceof JSUndefined) || !groupIndices.hasOwnProperty(groupName)) {
                        groupIndices.defineProperty(PropertyKey.fromString(groupName), pairValue,
                                PropertyDescriptor.DataState.All);
                    }
                }
            }
        }
        JSValue groupsValue = groupNames != null ? groupIndices : JSUndefined.INSTANCE;
        indicesArray.defineProperty(PropertyKey.GROUPS, groupsValue, PropertyDescriptor.DataState.All);
        return indicesArray;
    }

    private static JSValue createNamedGroupsValue(JSContext context, String[] captures, String[] groupNames) {
        if (groupNames == null || captures == null) {
            return JSUndefined.INSTANCE;
        }

        JSObject groups = new JSObject(context);
        groups.setPrototype(null);

        int maxLength = Math.min(captures.length, groupNames.length);
        for (int i = 1; i < maxLength; i++) {
            String groupName = groupNames[i];
            if (groupName != null) {
                if (captures[i] != null) {
                    groups.defineProperty(PropertyKey.fromString(groupName), new JSString(captures[i]),
                            PropertyDescriptor.DataState.All);
                } else if (!groups.hasOwnProperty(groupName)) {
                    groups.defineProperty(PropertyKey.fromString(groupName), JSUndefined.INSTANCE,
                            PropertyDescriptor.DataState.All);
                }
            }
        }
        return groups;
    }

    private static String escapeRegExpSourcePattern(String source) {
        if (source.isEmpty()) {
            return "(?:)";
        }
        StringBuilder escaped = new StringBuilder(source.length() + 8);
        boolean afterBackslash = false;
        boolean inCharClass = false;
        for (int index = 0; index < source.length(); index++) {
            char currentChar = source.charAt(index);
            if (afterBackslash) {
                // Previous character was an unescaped backslash.
                // Pass through most characters, but escape line terminators.
                afterBackslash = false;
                switch (currentChar) {
                    case '\n' -> escaped.append("n");
                    case '\r' -> escaped.append("r");
                    case '\u2028' -> escaped.append("u2028");
                    case '\u2029' -> escaped.append("u2029");
                    default -> escaped.append(currentChar);
                }
            } else {
                switch (currentChar) {
                    case '\\' -> {
                        afterBackslash = true;
                        escaped.append('\\');
                    }
                    case '/' -> {
                        if (inCharClass) {
                            escaped.append('/');
                        } else {
                            escaped.append("\\/");
                        }
                    }
                    case '[' -> {
                        inCharClass = true;
                        escaped.append('[');
                    }
                    case ']' -> {
                        inCharClass = false;
                        escaped.append(']');
                    }
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    case '\u2028' -> escaped.append("\\u2028");
                    case '\u2029' -> escaped.append("\\u2029");
                    default -> escaped.append(currentChar);
                }
            }
        }
        return escaped.toString();
    }

    /**
     * RegExp.prototype.exec(str) ES2020 21.2.5.2.1 Executes a search for a match in a string.
     */
    public static JSValue exec(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.exec called on non-RegExp");
        }

        JSString inputString = JSTypeConversions.toString(context, JSValue.argumentOrUndefined(args, 0));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String str = inputString.value();

        long lastIndexLong = RegExpStringOperations.readLastIndex(context, regexp);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // ES2024 22.2.7.2: Read flags AFTER ToLength(lastIndex), because
        // side-effects during ToLength (e.g. RegExp.prototype.compile) may
        // have changed the internal flags and engine.
        boolean global = regexp.isGlobal();
        boolean sticky = regexp.isSticky();

        int lastIndex = 0;
        if (global || sticky) {
            if (lastIndexLong > str.length()) {
                RegExpStringOperations.setLastIndexOrThrow(context, regexp, JSNumber.of(0));
                if (context.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                return JSNull.INSTANCE;
            }
            lastIndex = (int) lastIndexLong;
        }

        RegExpEngine engine = regexp.getEngine();
        RegExpEngine.MatchResult result = engine.exec(str, lastIndex,
                context.getVirtualMachine()::checkExecutionInterrupt);

        if (result != null && result.matched()) {
            String[] captures = result.captures();
            JSValue[] values = new JSValue[captures.length];
            for (int i = 0; i < captures.length; i++) {
                values[i] = captures[i] == null ? JSUndefined.INSTANCE : new JSString(captures[i]);
            }
            JSArray array = context.createJSArray(values, true);

            int[][] indices = result.indices();
            if (indices != null && indices.length > 0) {
                array.defineProperty(PropertyKey.INDEX, JSNumber.of(indices[0][0]), PropertyDescriptor.DataState.All);
            }
            array.defineProperty(PropertyKey.INPUT, inputString, PropertyDescriptor.DataState.All);
            array.defineProperty(PropertyKey.GROUPS,
                    createNamedGroupsValue(context, captures, regexp.getBytecode().groupNames()),
                    PropertyDescriptor.DataState.All);
            if (regexp.hasIndices()) {
                array.defineProperty(PropertyKey.INDICES,
                        createIndicesValue(context, indices, regexp.getBytecode().groupNames()),
                        PropertyDescriptor.DataState.All);
            }

            context.updateRegExpLegacyStatics(str, captures, indices, lastIndex);

            if (global || sticky) {
                if (indices != null && indices.length > 0) {
                    RegExpStringOperations.setLastIndexOrThrow(context, regexp, JSNumber.of(indices[0][1]));
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                }
            }

            return array;
        }

        if (global || sticky) {
            RegExpStringOperations.setLastIndexOrThrow(context, regexp, JSNumber.of(0));
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        return JSNull.INSTANCE;
    }

    /**
     * get RegExp.prototype.dotAll ES2020 21.2.5.6
     */
    public static JSValue getDotAll(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.DOT_ALL.read(context, thisArg, false);
    }

    public static JSValue getDotAllAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.DOT_ALL.read(context, thisArg, true);
    }

    /**
     * get RegExp.prototype.flags ES2020 21.2.5.3
     */
    public static JSValue getFlags(JSContext context, JSValue thisArg, JSValue[] args) {
        if (isRegExpPrototypeObject(context, thisArg)) {
            return new JSString("");
        }
        if (!(thisArg instanceof JSRegExp regexpObject)) {
            return context.throwTypeError("RegExp.prototype.flags called on non-RegExp");
        }
        if (regexpObject.getPrototype() == null) {
            return new JSString(regexpObject.getFlags());
        }
        String flags = RegExpFlag.readFlags(context, regexpObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(flags != null ? flags : "");
    }

    public static JSValue getFlagsAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype.flags called on non-object");
        }
        if (isRegExpPrototypeObject(context, thisArg)) {
            return new JSString("");
        }
        String flags = RegExpFlag.readFlags(context, regexpObject);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return new JSString(flags != null ? flags : "");
    }

    /**
     * get RegExp.prototype.global ES2020 21.2.5.4
     */
    public static JSValue getGlobal(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.GLOBAL.read(context, thisArg, false);
    }

    public static JSValue getGlobalAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.GLOBAL.read(context, thisArg, true);
    }

    /**
     * get RegExp.prototype.hasIndices ES2022
     */
    public static JSValue getHasIndices(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.HAS_INDICES.read(context, thisArg, false);
    }

    public static JSValue getHasIndicesAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.HAS_INDICES.read(context, thisArg, true);
    }

    /**
     * get RegExp.prototype.ignoreCase ES2020 21.2.5.5
     */
    public static JSValue getIgnoreCase(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.IGNORE_CASE.read(context, thisArg, false);
    }

    public static JSValue getIgnoreCaseAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.IGNORE_CASE.read(context, thisArg, true);
    }

    /**
     * get RegExp.prototype.multiline ES2020 21.2.5.7
     */
    public static JSValue getMultiline(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.MULTILINE.read(context, thisArg, false);
    }

    public static JSValue getMultilineAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.MULTILINE.read(context, thisArg, true);
    }

    /**
     * get RegExp.prototype.source ES2020 21.2.5.10
     */
    public static JSValue getSource(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.source called on non-RegExp");
        }

        return new JSString(escapeRegExpSourcePattern(regexp.getPattern()));
    }

    public static JSValue getSourceAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject)) {
            return context.throwTypeError("RegExp.prototype.source called on non-object");
        }
        if (isRegExpPrototypeObject(context, thisArg)) {
            return new JSString("(?:)");
        }
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.source called on non-RegExp");
        }
        return new JSString(escapeRegExpSourcePattern(regexp.getPattern()));
    }

    /**
     * get RegExp.prototype.sticky ES2020 21.2.5.12
     */
    public static JSValue getSticky(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.STICKY.read(context, thisArg, false);
    }

    public static JSValue getStickyAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.STICKY.read(context, thisArg, true);
    }

    /**
     * get RegExp.prototype.unicode ES2020 21.2.5.15
     */
    public static JSValue getUnicode(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.UNICODE.read(context, thisArg, false);
    }

    public static JSValue getUnicodeAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.UNICODE.read(context, thisArg, true);
    }

    /**
     * get RegExp.prototype.unicodeSets ES2024
     */
    public static JSValue getUnicodeSets(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.UNICODE_SETS.read(context, thisArg, false);
    }

    public static JSValue getUnicodeSetsAccessor(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpFlag.UNICODE_SETS.read(context, thisArg, true);
    }

    static boolean isRegExpPrototypeObject(JSContext context, JSValue value) {
        return value instanceof JSObject && value == context.getCachedRegExpPrototype();
    }

    /**
     * %RegExpStringIteratorPrototype%.next()
     */
    public static JSValue regExpStringIteratorNext(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpStringOperations.regExpStringIteratorNext(context, thisArg, args);
    }

    /**
     * RegExp.prototype[@@match](string) ES2024 22.2.5.6
     */
    public static JSValue symbolMatch(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpStringOperations.symbolMatch(context, thisArg, args);
    }

    /**
     * RegExp.prototype[@@matchAll](string) ES2024 22.2.6.9 (subset aligned with test262 semantics in current slice).
     */
    public static JSValue symbolMatchAll(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpStringOperations.symbolMatchAll(context, thisArg, args);
    }

    /**
     * RegExp.prototype[@@replace](string, replaceValue) ES2024 22.2.6.11 (subset sufficient for current test262 slice).
     */
    public static JSValue symbolReplace(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpStringOperations.symbolReplace(context, thisArg, args);
    }

    /**
     * RegExp.prototype[@@search](string) ES2024 22.2.6.13
     */
    public static JSValue symbolSearch(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpStringOperations.symbolSearch(context, thisArg, args);
    }

    /**
     * RegExp.prototype[@@split](string, limit) ES2024 22.2.6.14
     */
    public static JSValue symbolSplit(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpStringOperations.symbolSplit(context, thisArg, args);
    }

    /**
     * RegExp.prototype.test(str) ES2020 21.2.5.17 Tests for a match in a string.
     */
    public static JSValue test(JSContext context, JSValue thisArg, JSValue[] args) {
        return RegExpStringOperations.test(context, thisArg, args);
    }

    /**
     * RegExp.prototype.toString() ES2024 22.2.5.14 Reads "source" and "flags" properties from the this value (any
     * object).
     */
    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject regexpObject)) {
            return context.throwTypeError("RegExp.prototype.toString requires that 'this' be an Object");
        }
        JSValue sourceValue = regexpObject.get(PropertyKey.fromString("source"));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSString sourceString = JSTypeConversions.toString(context, sourceValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSValue flagsValue = regexpObject.get(PROPERTY_FLAGS);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        JSString flagsString = JSTypeConversions.toString(context, flagsValue);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return new JSString("/" + sourceString.value() + "/" + flagsString.value());
    }
}
