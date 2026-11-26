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

package com.caoccao.qjs4j.core;

import com.caoccao.qjs4j.builtins.NumberPrototype;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSRangeErrorException;

/** Constructor lengths captured before the observable newTarget prototype lookup. */
record ArrayBufferLengths(long length, long maxLength) {

    static ArrayBufferLengths forArrayBuffer(JSContext context, JSValue[] args) {
        long length = JSTypeConversions.toIndex(context, JSValue.argumentOrUndefined(args, 0));
        JSValue maximum = maxLengthOption(context, args, false);
        if (maximum instanceof JSUndefined) {
            return new ArrayBufferLengths(length, -1);
        }
        long maxLength = (long) JSTypeConversions.toInteger(context, maximum);
        if (maxLength < length || maxLength > NumberPrototype.MAX_SAFE_INTEGER) {
            throw new JSException(context.throwRangeError("invalid array buffer max length"));
        }
        return new ArrayBufferLengths(length, maxLength);
    }

    static ArrayBufferLengths forSharedArrayBuffer(JSContext context, JSValue[] args) {
        long length = sharedIndex(context, JSValue.argumentOrUndefined(args, 0), "Invalid array buffer length");
        JSValue maximum = maxLengthOption(context, args, true);
        if (maximum instanceof JSUndefined) {
            return new ArrayBufferLengths(length, -1);
        }
        long maxLength = sharedIndex(context, maximum, "Invalid array buffer max length");
        if (maxLength < length) {
            throw new JSException(context.throwRangeError("Invalid array buffer max length"));
        }
        return new ArrayBufferLengths(length, maxLength);
    }

    private static JSValue maxLengthOption(JSContext context, JSValue[] args, boolean shared) {
        if (args.length < 2 || !(args[1] instanceof JSObject options)) {
            return JSUndefined.INSTANCE;
        }
        boolean alreadyPending = shared && context.hasPendingException();
        JSValue maximum = options.get(PropertyKey.fromString("maxByteLength"));
        if (!alreadyPending && context.hasPendingException()) {
            throw new JSException(context.getPendingException());
        }
        return maximum;
    }

    private static long sharedIndex(JSContext context, JSValue value, String message) {
        try {
            return JSTypeConversions.toIndex(context, value);
        } catch (IllegalArgumentException | JSRangeErrorException e) {
            throw new JSException(context.throwRangeError(message));
        }
    }

    JSArrayBuffer allocate(JSContext context) {
        checkLimit(context, "invalid array buffer length");
        return new JSArrayBuffer(context, length, maxLength);
    }

    JSSharedArrayBuffer allocateShared(JSContext context) {
        checkLimit(context, "Invalid array buffer length");
        return maxLength < 0
                ? new JSSharedArrayBuffer(context, length)
                : new JSSharedArrayBuffer(context, length, maxLength);
    }

    private void checkLimit(JSContext context, String message) {
        if (length > JSArrayBuffer.MAX_DATA_BLOCK_BYTE_LENGTH || maxLength > JSArrayBuffer.MAX_DATA_BLOCK_BYTE_LENGTH) {
            throw new JSException(context.throwRangeError(message));
        }
    }
}
