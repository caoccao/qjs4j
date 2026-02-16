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
import com.caoccao.qjs4j.core.JSNumber;
import com.caoccao.qjs4j.core.JSTypeConversions;
import com.caoccao.qjs4j.core.JSValue;

/**
 * Implementation of Number constructor.
 * Based on ES2020 Number specification.
 */
public final class NumberConstructor {

    /**
     * Number(value)
     * ES2020 20.1.1.1
     * Converts the argument to a number primitive value.
     * When called as a function (not with new), returns a number primitive.
     * When called with new, creates a Number object wrapper.
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        // ES2020: If no argument is passed, return +0
        if (args.length == 0) {
            return JSNumber.of(0.0);
        }

        // Get the value to convert to number
        JSValue value = args[0];

        // Convert to number using ToNumber
        JSNumber numValue = JSTypeConversions.toNumber(context, value);

        // When called as a function (not via new), return primitive number
        // The VM will handle the "new" case separately in handleCallConstructor
        return numValue;
    }
}
