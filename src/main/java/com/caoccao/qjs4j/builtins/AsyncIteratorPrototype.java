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
 * %AsyncIteratorPrototype% built-in methods.
 */
public final class AsyncIteratorPrototype {
    private AsyncIteratorPrototype() {
    }

    private static JSPromise createPromiseResolve(JSContext context, JSValue value) {
        JSPromise wrapperPromise = context.createJSPromise();
        if (value instanceof JSPromise promiseValue) {
            promiseValue.get(context, PropertyKey.CONSTRUCTOR);
            if (context.hasPendingException()) {
                JSValue exception = context.getPendingException();
                context.clearAllPendingExceptions();
                wrapperPromise.reject(exception);
                return wrapperPromise;
            }
        }
        wrapperPromise.resolve(context, value);
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            wrapperPromise.reject(exception);
        }
        return wrapperPromise;
    }

    /**
     * %AsyncIteratorPrototype%[@@asyncDispose]().
     * Calls `this.return?.(undefined)` and resolves to `undefined`.
     */
    public static JSValue asyncDispose(JSContext context, JSValue thisArg, JSValue[] args) {
        JSPromise resultPromise = context.createJSPromise();

        JSObject iteratorObject;
        if (thisArg instanceof JSObject objectValue) {
            iteratorObject = objectValue;
        } else {
            iteratorObject = JSTypeConversions.toObject(context, thisArg);
            if (context.hasPendingException() || iteratorObject == null) {
                JSValue exception = context.hasPendingException()
                        ? context.getPendingException()
                        : context.throwTypeError("Cannot convert undefined or null to object");
                context.clearAllPendingExceptions();
                resultPromise.reject(exception);
                return resultPromise;
            }
        }

        JSValue returnMethodValue = iteratorObject.get(context, PropertyKey.RETURN);
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            resultPromise.reject(exception);
            return resultPromise;
        }
        if (returnMethodValue.isNullOrUndefined()) {
            resultPromise.fulfill(JSUndefined.INSTANCE);
            return resultPromise;
        }
        if (!(returnMethodValue instanceof JSFunction returnFunction)) {
            JSValue exception = context.throwTypeError("iterator return is not a function");
            context.clearAllPendingExceptions();
            resultPromise.reject(exception);
            return resultPromise;
        }

        JSValue returnResult;
        try {
            returnResult = returnFunction.call(context, iteratorObject, new JSValue[]{JSUndefined.INSTANCE});
        } catch (Exception e) {
            if (context.hasPendingException()) {
                JSValue exception = context.getPendingException();
                context.clearAllPendingExceptions();
                resultPromise.reject(exception);
            } else {
                resultPromise.reject(new JSString(e.getMessage() != null ? e.getMessage() : e.toString()));
            }
            return resultPromise;
        }
        if (context.hasPendingException()) {
            JSValue exception = context.getPendingException();
            context.clearAllPendingExceptions();
            resultPromise.reject(exception);
            return resultPromise;
        }

        JSPromise wrapperPromise = createPromiseResolve(context, returnResult);
        wrapperPromise.addReactions(
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("", 1, (childContext, callbackThisArg, callbackArgs) -> {
                            resultPromise.fulfill(JSUndefined.INSTANCE);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                ),
                new JSPromise.ReactionRecord(
                        new JSNativeFunction("", 1, (childContext, callbackThisArg, callbackArgs) -> {
                            JSValue error = callbackArgs.length > 0 ? callbackArgs[0] : JSUndefined.INSTANCE;
                            resultPromise.reject(error);
                            return JSUndefined.INSTANCE;
                        }),
                        null,
                        context
                )
        );
        return resultPromise;
    }
}
