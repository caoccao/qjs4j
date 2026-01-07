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
 * Implementation of Set constructor and static methods.
 * Based on ES2020 Set specification.
 */
public final class SetConstructor {

    /**
     * Set constructor call/new.
     * Delegates to JSSet.create().
     * <p>
     * Based on ES2020 23.2.1
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSSet.create(context, args);
    }
}
