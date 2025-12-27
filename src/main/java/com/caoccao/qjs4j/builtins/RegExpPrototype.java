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
     * RegExp.prototype.test(str)
     * ES2020 21.2.5.17
     * Tests for a match in a string.
     */
    public static JSValue test(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return ctx.throwError("TypeError", "RegExp.prototype.test called on non-RegExp");
        }

        String str = args.length > 0 ? JSTypeConversions.toString(args[0]).value() : "";

        // Get lastIndex
        int lastIndex = regexp.isGlobal() ? regexp.getLastIndex() : 0;

        // Execute regex
        RegExpEngine.MatchResult result = regexp.getEngine().exec(str, lastIndex);

        if (result != null && result.matched()) {
            // Update lastIndex for global regexes
            if (regexp.isGlobal()) {
                regexp.setLastIndex(result.endIndex());
            }
            return JSBoolean.TRUE;
        } else {
            // Reset lastIndex on failure for global regexes
            if (regexp.isGlobal()) {
                regexp.setLastIndex(0);
            }
            return JSBoolean.FALSE;
        }
    }

    /**
     * RegExp.prototype.exec(str)
     * ES2020 21.2.5.2.1
     * Executes a search for a match in a string.
     */
    public static JSValue exec(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return ctx.throwError("TypeError", "RegExp.prototype.exec called on non-RegExp");
        }

        String str = args.length > 0 ? JSTypeConversions.toString(args[0]).value() : "";

        // Get lastIndex
        int lastIndex = regexp.isGlobal() ? regexp.getLastIndex() : 0;

        // Execute regex
        RegExpEngine.MatchResult result = regexp.getEngine().exec(str, lastIndex);

        if (result != null && result.matched()) {
            // Create result array
            JSArray array = new JSArray();

            // Add matched string
            String matched = str.substring(result.startIndex(), result.endIndex());
            array.push(new JSString(matched));

            // Add capture groups
            if (result.captures() != null) {
                for (String capture : result.captures()) {
                    if (capture != null) {
                        array.push(new JSString(capture));
                    } else {
                        array.push(JSUndefined.INSTANCE);
                    }
                }
            }

            // Set properties
            array.set("index", new JSNumber(result.startIndex()));
            array.set("input", new JSString(str));

            // Update lastIndex for global regexes
            if (regexp.isGlobal()) {
                regexp.setLastIndex(result.endIndex());
            }

            return array;
        } else {
            // Reset lastIndex on failure for global regexes
            if (regexp.isGlobal()) {
                regexp.setLastIndex(0);
            }
            return JSNull.INSTANCE;
        }
    }

    /**
     * RegExp.prototype.toString()
     * ES2020 21.2.5.14
     */
    public static JSValue toStringMethod(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return ctx.throwError("TypeError", "RegExp.prototype.toString called on non-RegExp");
        }

        return new JSString("/" + regexp.getPattern() + "/" + regexp.getFlags());
    }

    /**
     * get RegExp.prototype.source
     * ES2020 21.2.5.10
     */
    public static JSValue getSource(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return ctx.throwError("TypeError", "RegExp.prototype.source called on non-RegExp");
        }

        return new JSString(regexp.getPattern());
    }

    /**
     * get RegExp.prototype.flags
     * ES2020 21.2.5.3
     */
    public static JSValue getFlags(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return ctx.throwError("TypeError", "RegExp.prototype.flags called on non-RegExp");
        }

        return new JSString(regexp.getFlags());
    }

    /**
     * get RegExp.prototype.global
     * ES2020 21.2.5.4
     */
    public static JSValue getGlobal(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return regexp.get("global");
    }

    /**
     * get RegExp.prototype.ignoreCase
     * ES2020 21.2.5.5
     */
    public static JSValue getIgnoreCase(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return regexp.get("ignoreCase");
    }

    /**
     * get RegExp.prototype.multiline
     * ES2020 21.2.5.7
     */
    public static JSValue getMultiline(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSRegExp regexp)) {
            return JSUndefined.INSTANCE;
        }

        return regexp.get("multiline");
    }
}
