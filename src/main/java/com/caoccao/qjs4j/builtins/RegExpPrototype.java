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
 * Implementation of JavaScript RegExp.prototype methods.
 * Based on ES2020 RegExp specification.
 */
public final class RegExpPrototype {

    private static int advanceStringIndexUnicode(String s, int index) {
        if (index + 1 >= s.length()) {
            return index + 1;
        }
        char c = s.charAt(index);
        if (Character.isHighSurrogate(c) && Character.isLowSurrogate(s.charAt(index + 1))) {
            return index + 2;
        }
        return index + 1;
    }

    /**
     * RegExp.prototype.compile(pattern, flags)
     * AnnexB B.2.5.1
     * Reinitializes the RegExp object in-place.
     */
    public static JSValue compile(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.compile called on non-RegExp");
        }
        JSValue realmRegExpConstructor = context.getGlobalObject().get("RegExp");
        JSValue receiverConstructor = regexp.get("constructor");
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
            return context.throwSyntaxError("Invalid regular expression: " + e.getMessage());
        }

        // Spec step 12: Perform ? Set(obj, "lastIndex", 0, true).
        // The 'true' means throw TypeError if the property is non-writable.
        PropertyDescriptor lastIndexDesc = regexp.getOwnPropertyDescriptor(PropertyKey.fromString("lastIndex"));
        if (lastIndexDesc != null && !lastIndexDesc.isWritable()) {
            return context.throwTypeError("Cannot assign to read only property 'lastIndex'");
        }
        regexp.setLastIndex(0);

        return regexp;
    }

    private static JSValue createIndexPairValue(int[] pair) {
        if (pair == null || pair.length < 2 || pair[0] < 0 || pair[1] < 0) {
            return JSUndefined.INSTANCE;
        }
        JSArray range = new JSArray();
        range.push(new JSNumber(pair[0]));
        range.push(new JSNumber(pair[1]));
        return range;
    }

    private static JSValue createIndicesValue(int[][] indices, String[] groupNames) {
        if (indices == null) {
            return JSUndefined.INSTANCE;
        }
        JSArray indicesArray = new JSArray();
        JSObject groupIndices = new JSObject();
        groupIndices.setPrototype(null);
        for (int i = 0; i < indices.length; i++) {
            JSValue pairValue = createIndexPairValue(indices[i]);
            indicesArray.push(pairValue);
            if (i > 0 && groupNames != null && i < groupNames.length) {
                String groupName = groupNames[i];
                if (groupName != null && !groupIndices.hasOwnProperty(groupName)) {
                    groupIndices.set(groupName, pairValue);
                }
            }
        }
        if (groupNames != null) {
            indicesArray.set("groups", groupIndices);
        }
        return indicesArray;
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
     * RegExp.prototype.exec(str)
     * ES2020 21.2.5.2.1
     * Executes a search for a match in a string.
     */
    public static JSValue exec(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.exec called on non-RegExp");
        }

        String str = args.length > 0 ? JSTypeConversions.toString(context, args[0]).value() : "";

        // Get lastIndex
        int lastIndex = regexp.isGlobal() || regexp.isSticky() ? regexp.getLastIndex() : 0;

        // Execute regex using QuickJS engine
        RegExpEngine engine = regexp.getEngine();
        RegExpEngine.MatchResult result = engine.exec(str, lastIndex);

        if (result != null && result.matched()) {
            // Create result array
            JSArray array = context.createJSArray();

            // Add matched string and capture groups
            String[] captures = result.captures();
            for (int i = 0; i < captures.length; i++) {
                if (captures[i] != null) {
                    array.push(new JSString(captures[i]));
                } else {
                    array.push(JSUndefined.INSTANCE);
                }
            }

            // Set properties
            int[][] indices = result.indices();
            if (indices != null && indices.length > 0) {
                array.set("index", new JSNumber(indices[0][0]));
            }
            array.set("input", new JSString(str));
            array.set("groups", createNamedGroupsValue(captures, regexp.getBytecode().groupNames()));
            if (regexp.hasIndices()) {
                array.set("indices", createIndicesValue(indices, regexp.getBytecode().groupNames()));
            }

            // Update lastIndex for global/sticky regexes
            if (regexp.isGlobal() || regexp.isSticky()) {
                if (indices != null && indices.length > 0) {
                    regexp.setLastIndex(indices[0][1]);
                }
            }

            return array;
        } else {
            // Reset lastIndex on failure for global/sticky regexes
            if (regexp.isGlobal() || regexp.isSticky()) {
                regexp.setLastIndex(0);
            }
            return JSNull.INSTANCE;
        }
    }

    /**
     * get RegExp.prototype.dotAll
     * ES2020 21.2.5.6
     */
    public static JSValue getDotAll(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isDotAll());
    }

    /**
     * get RegExp.prototype.flags
     * ES2020 21.2.5.3
     */
    public static JSValue getFlags(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.flags called on non-RegExp");
        }

        return new JSString(regexp.getFlags());
    }

    /**
     * get RegExp.prototype.global
     * ES2020 21.2.5.4
     */
    public static JSValue getGlobal(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isGlobal());
    }

    /**
     * get RegExp.prototype.hasIndices
     * ES2022
     */
    public static JSValue getHasIndices(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.hasIndices());
    }

    /**
     * get RegExp.prototype.ignoreCase
     * ES2020 21.2.5.5
     */
    public static JSValue getIgnoreCase(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isIgnoreCase());
    }

    /**
     * get RegExp.prototype.multiline
     * ES2020 21.2.5.7
     */
    public static JSValue getMultiline(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isMultiline());
    }

    /**
     * get RegExp.prototype.source
     * ES2020 21.2.5.10
     */
    public static JSValue getSource(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.source called on non-RegExp");
        }

        String source = regexp.getPattern();
        if (source.isEmpty()) {
            source = "(?:)";
        }
        return new JSString(source);
    }

    /**
     * get RegExp.prototype.sticky
     * ES2020 21.2.5.12
     */
    public static JSValue getSticky(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isSticky());
    }

    /**
     * get RegExp.prototype.unicode
     * ES2020 21.2.5.15
     */
    public static JSValue getUnicode(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isUnicode());
    }

    /**
     * get RegExp.prototype.unicodeSets
     * ES2024
     */
    public static JSValue getUnicodeSets(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return JSBoolean.valueOf(regexp.isUnicodeSets());
    }

    /**
     * RegExp.prototype[@@split](string, limit)
     * ES2024 22.2.6.14
     */
    public static JSValue symbolSplit(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp rx)) {
            return context.throwTypeError("RegExp.prototype[@@split] called on non-RegExp");
        }

        String s = args.length > 0 ? JSTypeConversions.toString(context, args[0]).value() : "";

        // Step 5: Get flags
        String flags = rx.getFlags();
        boolean unicodeMatching = flags.contains("u");
        String newFlags = flags.contains("y") ? flags : flags + "y";

        // Step 10: Construct splitter = Construct(C, « rx, newFlags »)
        // Per spec, this calls RegExp(rx, newFlags) which calls IsRegExp(rx),
        // accessing rx[Symbol.match] and triggering any getter side effects.
        rx.get(PropertyKey.fromSymbol(JSSymbol.MATCH), context);
        // After the getter may have mutated rx via compile(), read the pattern.
        JSRegExp splitter;
        try {
            splitter = new JSRegExp(rx.getPattern(), newFlags);
            context.transferPrototype(splitter, JSRegExp.NAME);
        } catch (Exception e) {
            return context.throwSyntaxError("Invalid regular expression: " + e.getMessage());
        }

        // Step 13: ToUint32(limit) — must happen AFTER splitter construction (step 10)
        // so that side-effects in valueOf don't affect the splitter's pattern.
        long limit = args.length > 1 && !(args[1] instanceof JSUndefined)
                ? JSTypeConversions.toUint32(context, args[1])
                : 0xFFFFFFFFL;

        JSArray result = context.createJSArray();
        int lengthA = 0;

        if (limit == 0) {
            return result;
        }

        int size = s.length();
        if (size == 0) {
            // Step 15: If string is empty, check if splitter matches it
            RegExpEngine.MatchResult z = splitter.getEngine().exec(s, 0);
            if (z == null || !z.matched()) {
                result.push(new JSString(s));
            }
            return result;
        }

        // Step 16-17: Main split loop
        int p = 0; // Start of segment
        int q = p; // Current search position
        RegExpEngine engine = splitter.getEngine();

        while (q < size) {
            // Set splitter lastIndex and exec
            RegExpEngine.MatchResult z = engine.exec(s, q);

            if (z == null || !z.matched()) {
                // No match: advance q
                q = unicodeMatching ? advanceStringIndexUnicode(s, q) : q + 1;
            } else {
                int[][] indices = z.indices();
                if (indices == null || indices.length == 0) {
                    q = unicodeMatching ? advanceStringIndexUnicode(s, q) : q + 1;
                    continue;
                }
                int e = Math.min(indices[0][1], size);

                if (e == p) {
                    // Zero-width match at segment start: advance
                    q = unicodeMatching ? advanceStringIndexUnicode(s, q) : q + 1;
                } else {
                    // Add substring before match
                    result.push(new JSString(s.substring(p, indices[0][0])));
                    lengthA++;
                    if (lengthA == limit) {
                        return result;
                    }

                    // Add capture groups
                    String[] captures = z.captures();
                    if (captures != null) {
                        for (int i = 1; i < captures.length; i++) {
                            if (captures[i] != null) {
                                result.push(new JSString(captures[i]));
                            } else {
                                result.push(JSUndefined.INSTANCE);
                            }
                            lengthA++;
                            if (lengthA == limit) {
                                return result;
                            }
                        }
                    }

                    p = e;
                    q = p;
                }
            }
        }

        // Step 18: Add trailing segment
        result.push(new JSString(s.substring(p)));
        return result;
    }

    /**
     * RegExp.prototype.test(str)
     * ES2020 21.2.5.17
     * Tests for a match in a string.
     */
    public static JSValue test(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.test called on non-RegExp");
        }

        String str = args.length > 0 ? JSTypeConversions.toString(context, args[0]).value() : "";

        // Get lastIndex
        int lastIndex = regexp.isGlobal() || regexp.isSticky() ? regexp.getLastIndex() : 0;

        // Execute regex using QuickJS engine
        RegExpEngine engine = regexp.getEngine();
        RegExpEngine.MatchResult result = engine.exec(str, lastIndex);

        boolean matched = result != null && result.matched();

        if (matched) {
            // Update lastIndex for global/sticky regexes
            if (regexp.isGlobal() || regexp.isSticky()) {
                int[][] indices = result.indices();
                if (indices != null && indices.length > 0) {
                    regexp.setLastIndex(indices[0][1]);
                }
            }
            return JSBoolean.TRUE;
        } else {
            // Reset lastIndex on failure for global/sticky regexes
            if (regexp.isGlobal() || regexp.isSticky()) {
                regexp.setLastIndex(0);
            }
            return JSBoolean.FALSE;
        }
    }

    /**
     * RegExp.prototype.toString()
     * ES2020 21.2.5.14
     */
    public static JSValue toStringMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.toString called on non-RegExp");
        }

        // Per ES spec, empty pattern should be "(?:)"
        String pattern = regexp.getPattern();
        if (pattern.isEmpty()) {
            pattern = "(?:)";
        }
        return new JSString("/" + pattern + "/" + regexp.getFlags());
    }
}
