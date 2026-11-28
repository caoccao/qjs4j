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
import java.util.function.Predicate;

/** Each boolean RegExp flag has a JavaScript property, an internal slot and a flag character. */
enum RegExpFlag {
    DOT_ALL('s', "dotAll", JSRegExp::isDotAll),
    GLOBAL('g', "global", JSRegExp::isGlobal),
    HAS_INDICES('d', "hasIndices", JSRegExp::hasIndices),
    IGNORE_CASE('i', "ignoreCase", JSRegExp::isIgnoreCase),
    MULTILINE('m', "multiline", JSRegExp::isMultiline),
    STICKY('y', "sticky", JSRegExp::isSticky),
    UNICODE('u', "unicode", JSRegExp::isUnicode),
    UNICODE_SETS('v', "unicodeSets", JSRegExp::isUnicodeSets);

    // Get(flags) reads these properties in specification order, including getters on exotic receivers.
    private static final RegExpFlag[] ACCESS_ORDER = {HAS_INDICES, GLOBAL, IGNORE_CASE, MULTILINE, DOT_ALL, UNICODE,
            UNICODE_SETS, STICKY};
    private final char character;
    private final PropertyKey key;
    private final String name;
    private final Predicate<JSRegExp> slot;

    RegExpFlag(char character, String name, Predicate<JSRegExp> slot) {
        this.character = character;
        this.name = name;
        this.key = PropertyKey.fromString(name);
        this.slot = slot;
    }

    JSValue read(JSContext context, JSValue receiver, boolean accessor) {
        if (RegExpPrototype.isRegExpPrototypeObject(context, receiver)) {
            return JSUndefined.INSTANCE;
        }
        if (receiver instanceof JSRegExp regexp) {
            return JSBoolean.valueOf(slot.test(regexp));
        }
        if (accessor) {
            return context.throwTypeError("RegExp.prototype." + name
                    + (receiver instanceof JSObject ? " called on non-RegExp" : " called on non-object"));
        }
        return this == DOT_ALL
                ? context.throwTypeError("RegExp.prototype.dotAll called on non-RegExp")
                : JSUndefined.INSTANCE;
    }

    static String readFlags(JSContext context, JSObject regexp) {
        StringBuilder flags = new StringBuilder(ACCESS_ORDER.length);
        for (RegExpFlag flag : ACCESS_ORDER) {
            JSValue value = regexp.get(flag.key);
            if (context.hasPendingException()) {
                return null;
            }
            if (JSTypeChecking.isTruthy(value)) {
                flags.append(flag.character);
            }
        }
        return flags.toString();
    }
}
