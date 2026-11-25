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

/** The ECMAScript Call operation, including callable proxies. */
public final class JSCall {
    private JSCall() {
    }

    public static JSValue[] argumentsFromArrayLike(JSContext context, JSValue arrayArg) {
        if (!(arrayArg instanceof JSObject arrayLike)) {
            context.throwTypeError("CreateListFromArrayLike called on non-object");
            return null;
        }

        JSValue lengthValue = arrayLike.get(PropertyKey.LENGTH);
        if (context.hasPendingException()) {
            return null;
        }

        long length = JSTypeConversions.toLength(context, lengthValue);
        if (context.hasPendingException()) {
            return null;
        }
        if (length > Integer.MAX_VALUE) {
            context.throwRangeError("too many arguments in function call");
            return null;
        }

        JSValue[] callArgs = new JSValue[(int) length];
        for (int i = 0; i < callArgs.length; i++) {
            JSValue argumentValue = arrayLike.get(PropertyKey.fromIndex(i));
            if (context.hasPendingException()) {
                return null;
            }
            callArgs[i] = argumentValue;
        }
        return callArgs;
    }

    public static JSValue call(JSContext context, JSValue callable, JSValue thisArg, JSValue[] args) {
        if (callable instanceof JSProxy proxy) {
            return proxy.apply(context, thisArg, args);
        }
        if (callable instanceof JSFunction function) {
            return function.call(context, thisArg, args);
        }
        return context.throwTypeError("Value is not callable");
    }
}
