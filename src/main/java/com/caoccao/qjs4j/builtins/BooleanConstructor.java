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
 * Implementation of Boolean constructor.
 * Based on ES2020 Boolean specification.
 */
public final class BooleanConstructor {

    /**
     * Boolean(value)
     * ES2020 19.3.1.1
     * Converts the argument to a boolean primitive value.
     * When called as a function (not with new), returns a boolean primitive.
     * When called with new, creates a Boolean object wrapper.
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        // Get the value to convert to boolean
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // Convert to boolean using ToBoolean
        JSBoolean boolValue = JSTypeConversions.toBoolean(value);

        // When called as a function (not via new), return primitive boolean
        // The VM will handle the "new" case separately in handleCallConstructor
        return boolValue;
    }
}
