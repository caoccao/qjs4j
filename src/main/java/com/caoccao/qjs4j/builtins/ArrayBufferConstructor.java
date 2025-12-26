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
 * Implementation of ArrayBuffer constructor and static methods.
 * Based on ES2020 ArrayBuffer specification.
 */
public final class ArrayBufferConstructor {

    /**
     * ArrayBuffer.isView(value)
     * ES2020 24.1.3.1
     * Returns true if value is a TypedArray or DataView.
     */
    public static JSValue isView(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSBoolean.FALSE;
        }

        JSValue arg = args[0];
        boolean isView = arg instanceof JSTypedArray || arg instanceof JSDataView;
        return JSBoolean.valueOf(isView);
    }
}
