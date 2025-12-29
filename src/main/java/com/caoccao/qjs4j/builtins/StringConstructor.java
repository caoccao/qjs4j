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
import com.caoccao.qjs4j.core.JSTypeConversions;
import com.caoccao.qjs4j.core.JSValue;

/**
 * Implementation of String constructor.
 * Based on ES2020 String specification.
 */
public final class StringConstructor {

    /**
     * String(value)
     * ES2020 21.1.1.1
     * Converts the argument to a string primitive value.
     * When called as a function (not with new), returns a string primitive.
     * When called with new, creates a String object wrapper.
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        // ES2020: If no argument is passed, return empty string
        if (args.length == 0) {
            return new JSString("");
        }

        // Get the value to convert to string
        JSValue value = args[0];

        // Convert to string using ToString
        JSString strValue = JSTypeConversions.toString(context, value);

        // When called as a function (not via new), return primitive string
        // The VM will handle the "new" case separately in handleCallConstructor
        return strValue;
    }
}
