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

package com.caoccao.qjs4j.core.iterator;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.Iterator;

/** Promise continuations for adapting synchronous iterator methods to asynchronous iteration. */
final class AsyncFromSyncIterator {
    private AsyncFromSyncIterator() {
    }

    static JSPromise close(JSContext context, JSObject syncIterator) {
        JSValue result;
        try {
            JSValue returnMethod = syncIterator.get(PropertyKey.RETURN);
            if (context.hasPendingException()) {
                return JSAsyncIterator.rejectPendingException(context);
            }
            if (returnMethod.isNullOrUndefined()) {
                // Step 7: no return method — resolve with { value: undefined, done: true }.
                JSObject iteratorResult = JSIteratorHelper.createResult(context, JSUndefined.INSTANCE, true);
                JSPromise promise = context.createJSPromise();
                promise.resolve(context, iteratorResult);
                return promise;
            }
            if (!(returnMethod instanceof JSFunction returnFunction)) {
                JSValue error = context.throwTypeError("iterator return is not a function");
                context.clearPendingException();
                return JSAsyncIterator.createRejectedPromise(context, error);
            }
            result = returnFunction.call(context, syncIterator);
        } catch (JSException e) {
            context.clearPendingException();
            return JSAsyncIterator.createRejectedPromise(context, e.getErrorValue());
        } catch (JSVirtualMachineException e) {
            JSValue error = context.hasPendingException() ? context.takeExecutionException() : e.getJsValue();
            return JSAsyncIterator.createRejectedPromise(context,
                    error != null ? error : context.throwError(e.getMessage()));
        }
        if (context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        if (!(result instanceof JSObject resultObject)) {
            JSValue error = context.throwTypeError(
                    "Iterator result " + JSAsyncIterator.describeIteratorResult(result) + " is not an object");
            context.clearPendingException();
            return JSAsyncIterator.createRejectedPromise(context, error);
        }
        JSValue done = resultObject.get(PropertyKey.DONE);
        if (context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        JSValue resultValue = resultObject.get(PropertyKey.VALUE);
        if (context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        // AsyncFromSyncIteratorContinuation: awaits the value, then resolves { value, done }.
        return continueWith(context, resultValue, JSTypeConversions.toBoolean(done) == JSBoolean.TRUE, null);
    }

    private static void closeSyncIteratorAfterRejection(JSContext context, JSObject iterator) {
        if (iterator == null) {
            return;
        }
        try {
            JSValue method = iterator.get(PropertyKey.RETURN);
            if (!context.hasPendingException() && !method.isNullOrUndefined()) {
                JSCall.call(context, method, iterator);
            }
        } catch (JSException | JSVirtualMachineException ignored) {
            // IteratorClose with a throw completion preserves the original rejection.
        } finally {
            context.clearAllPendingExceptions();
        }
    }

    static JSPromise continueNext(JSContext context, JSObject result, JSObject iteratorToClose) {
        JSValue value = result.get(PropertyKey.VALUE);
        if (context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        JSValue done = result.get(PropertyKey.DONE);
        if (context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        return continueWith(context, value, JSTypeChecking.isTruthy(done), iteratorToClose);
    }

    static JSPromise continueWith(JSContext context, JSValue value, boolean done, JSObject syncIterator) {
        JSPromise valueWrapperPromise;
        if (value instanceof JSPromise promiseValue) {
            // PromiseResolve(%Promise%, value) performs observable constructor access.
            promiseValue.get(PropertyKey.CONSTRUCTOR);
            valueWrapperPromise = promiseValue;
        } else {
            valueWrapperPromise = context.createJSPromise();
            valueWrapperPromise.resolve(context, value);
        }

        if (context.hasPendingException()) {
            JSValue reason = context.takeExecutionException();
            if (!done) {
                closeSyncIteratorAfterRejection(context, syncIterator);
            }
            return JSAsyncIterator.createRejectedPromise(context, reason);
        }

        JSPromise resultPromise = context.createJSPromise();
        valueWrapperPromise.observe(context,
                new JSNativeFunction(context, "onResolve", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                    JSValue resolved = JSValue.argumentOrUndefined(callbackArgs, 0);
                    JSObject result = JSIteratorHelper.createResult(context, resolved, done);
                    resultPromise.fulfill(result);
                    return JSUndefined.INSTANCE;
                }), new JSNativeFunction(context, "onReject", 1, (callbackContext, callbackThisArg, callbackArgs) -> {
                    JSValue reason = JSValue.argumentOrUndefined(callbackArgs, 0);
                    if (!done) {
                        closeSyncIteratorAfterRejection(context, syncIterator);
                    }
                    resultPromise.reject(reason);
                    return JSUndefined.INSTANCE;
                }));
        return resultPromise;
    }

    static JSPromise next(JSContext context, JSObject iterator, JSValue next) {
        JSValue result;
        try {
            result = JSCall.call(context, next, iterator);
        } catch (JSException e) {
            if (context.hasPendingException()) {
                context.clearAllPendingExceptions();
            }
            return JSAsyncIterator.createRejectedPromise(context, e.getErrorValue());
        } catch (Exception e) {
            JSValue reason = context.hasPendingException()
                    ? context.takeExecutionException()
                    : new JSString(e.getMessage() != null ? e.getMessage() : e.toString());
            return JSAsyncIterator.createRejectedPromise(context, reason);
        }
        if (context.hasPendingException()) {
            return JSAsyncIterator.rejectPendingException(context);
        }
        if (!(result instanceof JSObject object)) {
            JSValue error = context.throwTypeError("iterator result must be an object");
            context.clearPendingException();
            return JSAsyncIterator.createRejectedPromise(context, error);
        }
        return continueNext(context, object, iterator);
    }
}
