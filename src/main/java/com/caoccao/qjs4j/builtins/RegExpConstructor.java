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

    /**
     * RegExp called as a function (without new).
     * <p>
     * ES2024 22.2.3.1 RegExp(pattern, flags)
     * When called as a function (new.target is undefined):
     * - If pattern is regexp-like and flags is undefined, and pattern.constructor === RegExp,
     *   return pattern unchanged.
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
                return (JSObject) context.getPendingException();
            }
            if (patternIsRegExp) {
                // Step 3b: Get pattern.constructor
                if (patternArg instanceof JSObject patternObj) {
                    JSValue patternConstructor = patternObj.get(context, PropertyKey.CONSTRUCTOR);
                    if (context.hasPendingException()) {
                        return (JSObject) context.getPendingException();
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
     * get RegExp[@@species]
     * ES2024 22.2.4.2
     * Returns the this value.
     */
    public static JSValue getSpecies(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg;
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
        JSValue matchProp = obj.get(context, PropertyKey.SYMBOL_MATCH);
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
