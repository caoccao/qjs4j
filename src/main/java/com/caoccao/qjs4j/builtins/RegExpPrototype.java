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

        return regexp.get("global");
    }

    /**
     * get RegExp.prototype.ignoreCase
     * ES2020 21.2.5.5
     */
    public static JSValue getIgnoreCase(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return regexp.get("ignoreCase");
    }

    /**
     * get RegExp.prototype.multiline
     * ES2020 21.2.5.7
     */
    public static JSValue getMultiline(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return regexp.get("multiline");
    }

    /**
     * get RegExp.prototype.source
     * ES2020 21.2.5.10
     */
    public static JSValue getSource(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return context.throwTypeError("RegExp.prototype.source called on non-RegExp");
        }

        return new JSString(regexp.getPattern());
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
