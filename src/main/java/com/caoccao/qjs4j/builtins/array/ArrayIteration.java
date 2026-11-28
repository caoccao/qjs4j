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

package com.caoccao.qjs4j.builtins.array;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.buffer.JSTypedArray;

/** Callback iteration shared by Array and TypedArray. */
enum ArrayIteration {
    EVERY("every", JSBoolean.FALSE, JSBoolean.TRUE),
    FIND("find", JSBoolean.TRUE, JSUndefined.INSTANCE),
    FIND_INDEX("findIndex", JSBoolean.TRUE, JSNumber.of(-1)),
    FIND_LAST("findLast", JSBoolean.TRUE, JSUndefined.INSTANCE),
    FIND_LAST_INDEX("findLastIndex", JSBoolean.TRUE, JSNumber.of(-1)),
    FOR_EACH("forEach", null, JSUndefined.INSTANCE),
    SOME("some", JSBoolean.TRUE, JSBoolean.FALSE);

    private final JSValue completion;
    private final String methodName;
    private final JSBoolean stopWhen;
    private final String typedArrayMethod;

    ArrayIteration(String methodName, JSBoolean stopWhen, JSValue completion) {
        this.methodName = methodName;
        this.typedArrayMethod = "TypedArray.prototype." + methodName;
        this.stopWhen = stopWhen;
        this.completion = completion;
    }

    private JSValue iterate(JSContext context, JSObject object, JSTypedArray typedArray, long length, JSValue[] args) {
        JSValue callback = args[0];
        JSValue callbackThis = JSValue.argumentOrUndefined(args, 1);
        boolean reverse = this == FIND_LAST || this == FIND_LAST_INDEX;
        long step = reverse ? -1 : 1;
        long end = reverse ? -1 : length;
        boolean visitsHoles = visitsHoles();
        for (long i = reverse ? length - 1 : 0; i != end; i += step) {
            context.checkExecutionInterrupt();
            JSValue element = typedArray != null
                    ? typedArray.getElementOrUndefined(i)
                    : visitsHoles
                            ? ArrayPrototype.getElement(context, object, i)
                            : ArrayLikeElements.getPresent(context, object, i);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!visitsHoles && element == null) {
                continue;
            }
            JSValue result = ArrayLikeElements.callElement(context, callback, callbackThis, element, i, object);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (stopWhen != null && JSTypeConversions.toBoolean(result) == stopWhen) {
                return matched(element, i);
            }
        }
        return completion;
    }

    private JSValue matched(JSValue element, long index) {
        return switch (this) {
            case FIND, FIND_LAST -> element;
            case FIND_INDEX, FIND_LAST_INDEX -> JSNumber.of(index);
            default -> stopWhen;
        };
    }

    JSValue onArray(JSContext context, JSValue receiver, JSValue[] args) {
        JSObject object = ArrayPrototype.requireArrayLike(context, receiver, methodName);
        long length = ArrayLikeElements.callbackLength(context, object, args);
        return length < 0 ? context.getPendingException() : iterate(context, object, null, length, args);
    }

    JSValue onTypedArray(JSContext context, JSValue receiver, JSValue[] args) {
        JSTypedArray array = TypedArrayPrototype.toTypedArrayForCallback(context, receiver, args, typedArrayMethod);
        return array == null ? context.getPendingException() : iterate(context, array, array, array.getLength(), args);
    }

    private boolean visitsHoles() {
        return this == FIND || this == FIND_INDEX || this == FIND_LAST || this == FIND_LAST_INDEX;
    }
}
