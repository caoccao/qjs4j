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

/** Callback iteration shared by Array and TypedArray. */
enum ArrayIteration {
    EVERY("every", JSBoolean.FALSE, JSBoolean.TRUE), FOR_EACH("forEach", null, JSUndefined.INSTANCE), SOME("some",
            JSBoolean.TRUE, JSBoolean.FALSE);

    final JSValue completion;
    final String methodName;
    final JSBoolean stopResult;
    final String typedArrayMethod;

    ArrayIteration(String methodName, JSBoolean stopResult, JSValue completion) {
        this.methodName = methodName;
        this.typedArrayMethod = "TypedArray.prototype." + methodName;
        this.stopResult = stopResult;
        this.completion = completion;
    }

    boolean stopsAt(JSValue callbackResult) {
        return stopResult != null && JSTypeConversions.toBoolean(callbackResult) == stopResult;
    }
}
