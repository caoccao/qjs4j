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

import com.caoccao.qjs4j.core.JSContext;
import com.caoccao.qjs4j.core.JSString;
import com.caoccao.qjs4j.exceptions.JSException;

/** Builds string results as ropes, checking their length before each append. */
final class StringResultBuilder {
    private final JSContext context;
    private JSString value = new JSString("");

    StringResultBuilder(JSContext context) {
        this.context = context;
    }

    void append(char character) {
        append(String.valueOf(character));
    }

    void append(JSString text) {
        if ((long) value.length() + text.length() > JSString.MAX_LENGTH) {
            throw new JSException(context.throwRangeError("Invalid string length"));
        }
        value = JSString.concat(value, text);
    }

    void append(String text) {
        append(new JSString(text));
    }

    void append(String text, int start, int end) {
        append(text.substring(start, end));
    }

    JSString build() {
        return value;
    }

    boolean exceedsMaxLength(long addition) {
        if ((long) value.length() + addition <= JSString.MAX_LENGTH) {
            return false;
        }
        context.throwRangeError("Invalid string length");
        return true;
    }

    int length() {
        return value.length();
    }
}
