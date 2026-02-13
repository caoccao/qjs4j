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
import com.caoccao.qjs4j.core.JSSharedArrayBuffer;
import com.caoccao.qjs4j.core.JSValue;

/**
 * SharedArrayBuffer constructor implementation.
 * Based on ES2017 SharedArrayBuffer specification.
 */
public final class SharedArrayBufferConstructor {

    /**
     * SharedArrayBuffer constructor call/new.
     * Delegates to JSSharedArrayBuffer.create().
     * <p>
     * Based on ES2017 24.2.1.1
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSSharedArrayBuffer.create(context, args);
    }

    /**
     * get SharedArrayBuffer[@@species]
     * ES2024 SharedArrayBuffer constructor species getter.
     */
    public static JSValue getSpecies(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg;
    }
}
