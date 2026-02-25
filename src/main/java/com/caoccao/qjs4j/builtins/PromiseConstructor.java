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
import com.caoccao.qjs4j.exceptions.JSException;

import java.util.Arrays;

/**
 * Implementation of Promise constructor and static methods.
 * Based on QuickJS Promise behavior.
 */
public final class PromiseConstructor {

    /**
     * Promise.all(iterable)
     */
    public static JSValue all(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!JSTypeChecking.isConstructor(thisArg)) {
            return context.throwTypeError("Promise.all called on non-constructor");
        }

        PromiseCapability promiseCapability = newPromiseCapability(context, thisArg);
        if (promiseCapability == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue iterable = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue iterator = JSIteratorHelper.getIterator(context, iterable);
        if (context.hasPendingException()) {
            return rejectAbruptPromise(context, promiseCapability);
        }
        if (!(iterator instanceof JSObject iteratorObject)) {
            context.throwTypeError("Value is not iterable");
            return rejectAbruptPromise(context, promiseCapability);
        }

        JSValue promiseResolve = callGet(context, thisArg, PropertyKey.RESOLVE);
        if (context.hasPendingException()) {
            return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
        }
        if (!JSTypeChecking.isCallable(promiseResolve)) {
            context.throwTypeError("Promise.all resolve is not callable");
            return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
        }

        JSArray values = context.createJSArray();
        int index = 0;
        int[] remainingElementsCount = new int[]{1};

        while (true) {
            JSObject nextResult;
            try {
                nextResult = JSIteratorHelper.iteratorNext(iteratorObject, context);
            } catch (JSException e) {
                if (!context.hasPendingException()) {
                    context.setPendingException(e.getErrorValue());
                }
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            } catch (Exception e) {
                if (!context.hasPendingException()) {
                    context.throwError("Error", e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                }
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }
            if (context.hasPendingException()) {
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }
            if (nextResult == null) {
                context.throwTypeError("iterator result is not an object");
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }

            JSValue doneValue = callGet(context, nextResult, PropertyKey.DONE);
            if (context.hasPendingException()) {
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }
            boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
            if (done) {
                remainingElementsCount[0]--;
                if (remainingElementsCount[0] == 0) {
                    callCallable(context, promiseCapability.resolve(), JSUndefined.INSTANCE, new JSValue[]{values});
                    if (context.hasPendingException()) {
                        return rejectAbruptPromise(context, promiseCapability);
                    }
                }
                return promiseCapability.promise();
            }

            JSValue nextValue = callGet(context, nextResult, PropertyKey.VALUE);
            if (context.hasPendingException()) {
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }

            values.set(index, JSUndefined.INSTANCE);
            if (context.hasPendingException()) {
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }

            JSValue nextPromise = callCallable(context, promiseResolve, thisArg, new JSValue[]{nextValue});
            if (context.hasPendingException()) {
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }

            JSValue thenMethod = callGet(context, nextPromise, PropertyKey.THEN);
            if (context.hasPendingException()) {
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }
            if (!JSTypeChecking.isCallable(thenMethod)) {
                context.throwTypeError("Promise.all resolve result then is not callable");
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }

            int currentIndex = index;
            boolean[] alreadyCalled = new boolean[]{false};
            JSNativeFunction resolveElement = new JSNativeFunction("resolveElement", 1,
                    (childContext, thisValue, functionArgs) -> {
                        if (alreadyCalled[0]) {
                            return JSUndefined.INSTANCE;
                        }
                        alreadyCalled[0] = true;
                        JSValue resolvedValue = functionArgs.length > 0 ? functionArgs[0] : JSUndefined.INSTANCE;
                        values.set(currentIndex, resolvedValue);
                        if (childContext.hasPendingException()) {
                            return JSUndefined.INSTANCE;
                        }
                        remainingElementsCount[0]--;
                        if (remainingElementsCount[0] == 0) {
                            callCallable(childContext, promiseCapability.resolve(), JSUndefined.INSTANCE, new JSValue[]{values});
                            if (childContext.hasPendingException()) {
                                rejectAbruptPromise(childContext, promiseCapability);
                            }
                        }
                        return JSUndefined.INSTANCE;
                    });

            remainingElementsCount[0]++;
            callCallable(context, thenMethod, nextPromise, new JSValue[]{resolveElement, promiseCapability.reject()});
            if (context.hasPendingException()) {
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }
            index++;
        }
    }

    /**
     * Promise.allSettled(iterable)
     */
    public static JSValue allSettled(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject)) {
            return context.throwTypeError("Promise.allSettled called on non-object");
        }
        JSArray array = toArray(context, args, "Promise.allSettled");
        if (array == null) {
            return JSUndefined.INSTANCE;
        }
        int length = (int) array.getLength();
        JSPromise resultPromise = context.createJSPromise();
        JSArray results = context.createJSArray();
        if (length == 0) {
            resultPromise.fulfill(results);
            return resultPromise;
        }

        final int[] remaining = {length};
        final Object allSettledLock = new Object();
        for (int i = 0; i < length; i++) {
            final int index = i;
            JSPromise elementPromise = toPromise(context, thisArg, array.get(i));
            if (elementPromise == null) {
                return JSUndefined.INSTANCE;
            }
            elementPromise.addReactions(
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onFulfill", 1, (childContext, thisValue, funcArgs) -> {
                                JSObject result = childContext.createJSObject();
                                result.set(PropertyKey.STATUS, new JSString("fulfilled"));
                                result.set(PropertyKey.VALUE, funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE);
                                synchronized (allSettledLock) {
                                    results.set(index, result);
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        resultPromise.fulfill(results);
                                    }
                                }
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onReject", 1, (childContext, thisValue, funcArgs) -> {
                                JSObject result = childContext.createJSObject();
                                result.set(PropertyKey.STATUS, new JSString("rejected"));
                                result.set(PropertyKey.REASON, funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE);
                                synchronized (allSettledLock) {
                                    results.set(index, result);
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        resultPromise.fulfill(results);
                                    }
                                }
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context));
        }
        return resultPromise;
    }

    /**
     * Promise.any(iterable)
     */
    public static JSValue any(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject)) {
            return context.throwTypeError("Promise.any called on non-object");
        }
        JSArray array = toArray(context, args, "Promise.any");
        if (array == null) {
            return JSUndefined.INSTANCE;
        }
        int length = (int) array.getLength();
        JSPromise resultPromise = context.createJSPromise();
        JSArray errors = context.createJSArray();
        if (length == 0) {
            resultPromise.reject(JSAggregateError.create(context, errors));
            return resultPromise;
        }

        final int[] remaining = {length};
        final Object anyLock = new Object();
        final boolean[] anySettled = {false};
        for (int i = 0; i < length; i++) {
            final int index = i;
            JSPromise elementPromise = toPromise(context, thisArg, array.get(i));
            if (elementPromise == null) {
                return JSUndefined.INSTANCE;
            }
            elementPromise.addReactions(
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onFulfill", 1, (childContext, thisValue, funcArgs) -> {
                                JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                                synchronized (anyLock) {
                                    if (anySettled[0]) {
                                        return JSUndefined.INSTANCE;
                                    }
                                    anySettled[0] = true;
                                    resultPromise.fulfill(value);
                                }
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onReject", 1, (childContext, thisValue, funcArgs) -> {
                                JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                                synchronized (anyLock) {
                                    if (anySettled[0]) {
                                        return JSUndefined.INSTANCE;
                                    }
                                    errors.set(index, reason);
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        anySettled[0] = true;
                                        resultPromise.reject(JSAggregateError.create(context, errors));
                                    }
                                }
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context));
        }
        return resultPromise;
    }

    /**
     * Promise constructor call handler.
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSPromise.create(context, args);
    }

    /**
     * get Promise[@@species]
     */
    public static JSValue getSpecies(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg;
    }

    /**
     * Promise.race(iterable)
     */
    public static JSValue race(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject)) {
            return context.throwTypeError("Promise.race called on non-object");
        }
        JSArray array = toArray(context, args, "Promise.race");
        if (array == null) {
            return JSUndefined.INSTANCE;
        }

        JSPromise resultPromise = context.createJSPromise();
        int length = (int) array.getLength();
        final Object raceLock = new Object();
        final boolean[] raceSettled = {false};
        for (int i = 0; i < length; i++) {
            JSPromise elementPromise = toPromise(context, thisArg, array.get(i));
            if (elementPromise == null) {
                return JSUndefined.INSTANCE;
            }
            elementPromise.addReactions(
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onFulfill", 1, (childContext, thisValue, funcArgs) -> {
                                JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                                synchronized (raceLock) {
                                    if (raceSettled[0]) {
                                        return JSUndefined.INSTANCE;
                                    }
                                    raceSettled[0] = true;
                                    resultPromise.fulfill(value);
                                }
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onReject", 1, (childContext, thisValue, funcArgs) -> {
                                JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                                synchronized (raceLock) {
                                    if (raceSettled[0]) {
                                        return JSUndefined.INSTANCE;
                                    }
                                    raceSettled[0] = true;
                                    resultPromise.reject(reason);
                                }
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context));
        }
        return resultPromise;
    }

    /**
     * Promise.reject(reason)
     */
    public static JSValue reject(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject)) {
            return context.throwTypeError("Promise.reject called on non-object");
        }
        JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSPromise promise = context.createJSPromise();
        promise.reject(reason);
        return promise;
    }

    /**
     * Promise.resolve(value)
     */
    public static JSValue resolve(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject)) {
            return context.throwTypeError("Promise.resolve called on non-object");
        }
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        if (value instanceof JSPromise jsPromise) {
            JSValue constructor = jsPromise.get(PropertyKey.CONSTRUCTOR);
            if (constructor == thisArg) {
                return jsPromise;
            }
        }

        JSPromise promise = context.createJSPromise();
        promise.resolve(context, value);
        return promise;
    }

    private static JSArray toArray(JSContext context, JSValue[] args, String methodName) {
        if (args.length == 0) {
            context.throwTypeError(methodName + " requires an iterable");
            return null;
        }
        JSValue iterable = args[0];
        if (iterable instanceof JSArray jsArray) {
            return jsArray;
        }
        if (!JSIteratorHelper.isIterable(iterable)) {
            context.throwTypeError(methodName + " requires an iterable");
            return null;
        }
        return JSIteratorHelper.toArray(context, iterable);
    }

    private static JSValue callCallable(JSContext context, JSValue callable, JSValue thisArg, JSValue[] args) {
        try {
            if (callable instanceof JSProxy proxy) {
                return proxy.apply(context, thisArg, args);
            }
            if (callable instanceof JSFunction function) {
                return function.call(context, thisArg, args);
            }
            return context.throwTypeError("Value is not callable");
        } catch (JSException e) {
            if (!context.hasPendingException()) {
                context.setPendingException(e.getErrorValue());
            }
            return JSUndefined.INSTANCE;
        } catch (Exception e) {
            if (!context.hasPendingException()) {
                context.throwError("Error", e.getMessage() != null ? e.getMessage() : "Unhandled exception");
            }
            return JSUndefined.INSTANCE;
        }
    }

    private static JSValue callGet(JSContext context, JSValue target, PropertyKey propertyKey) {
        try {
            if (target instanceof JSObject objectTarget) {
                return objectTarget.get(context, propertyKey);
            }
            JSObject boxedTarget = JSTypeConversions.toObject(context, target);
            if (boxedTarget == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return boxedTarget.get(context, propertyKey);
        } catch (JSException e) {
            if (!context.hasPendingException()) {
                context.setPendingException(e.getErrorValue());
            }
            return JSUndefined.INSTANCE;
        } catch (Exception e) {
            if (!context.hasPendingException()) {
                context.throwError("Error", e.getMessage() != null ? e.getMessage() : "Unhandled exception");
            }
            return JSUndefined.INSTANCE;
        }
    }

    private static JSValue closeIteratorAndRejectAbruptPromise(
            JSContext context,
            JSObject iteratorObject,
            PromiseCapability promiseCapability) {
        JSValue pendingError = context.getPendingException();
        context.clearPendingException();
        try {
            JSIteratorHelper.closeIterator(context, iteratorObject);
        } catch (JSException e) {
            if (!context.hasPendingException()) {
                context.setPendingException(e.getErrorValue());
            }
        } catch (Exception e) {
            if (!context.hasPendingException()) {
                context.throwError("Error", e.getMessage() != null ? e.getMessage() : "Unhandled exception");
            }
        }
        if (context.hasPendingException()) {
            context.clearPendingException();
        }
        context.setPendingException(pendingError);
        return rejectAbruptPromise(context, promiseCapability);
    }

    private static PromiseCapability newPromiseCapability(JSContext context, JSValue constructor) {
        PromiseCapability promiseCapability = new PromiseCapability();
        JSNativeFunction executor = new JSNativeFunction("executor", 2, (childContext, thisArg, args) -> {
            if (!promiseCapability.resolve().isUndefined() || !promiseCapability.reject().isUndefined()) {
                return childContext.throwTypeError("Promise capability executor called multiple times");
            }
            JSValue resolveFunction = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            JSValue rejectFunction = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
            promiseCapability.setResolve(resolveFunction);
            promiseCapability.setReject(rejectFunction);
            return JSUndefined.INSTANCE;
        });

        JSValue promise = JSReflectObject.constructSimple(context, constructor, new JSValue[]{executor});
        if (context.hasPendingException()) {
            return null;
        }
        if (!(promise instanceof JSObject)) {
            context.throwTypeError("Promise constructor did not return an object");
            return null;
        }
        if (!JSTypeChecking.isCallable(promiseCapability.resolve()) || !JSTypeChecking.isCallable(promiseCapability.reject())) {
            context.throwTypeError("Promise constructor returned non-callable resolve or reject");
            return null;
        }
        promiseCapability.setPromise(promise);
        return promiseCapability;
    }

    private static JSValue rejectAbruptPromise(JSContext context, PromiseCapability promiseCapability) {
        JSValue error = context.getPendingException();
        if (error == null) {
            error = JSUndefined.INSTANCE;
        }
        context.clearPendingException();
        callCallable(context, promiseCapability.reject(), JSUndefined.INSTANCE, new JSValue[]{error});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return promiseCapability.promise();
    }

    private static JSPromise toPromise(JSContext context, JSValue thisArg, JSValue value) {
        JSValue resolved = resolve(context, thisArg, new JSValue[]{value});
        if (context.hasPendingException()) {
            return null;
        }
        if (resolved instanceof JSPromise jsPromise) {
            return jsPromise;
        }
        context.throwTypeError("Promise.resolve must return a Promise");
        return null;
    }

    /**
     * Promise.try(callback, ...args)
     */
    public static JSValue tryMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject)) {
            return context.throwTypeError("Promise.try called on non-object");
        }
        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return context.throwTypeError("Promise.try requires a function");
        }
        JSValue[] callbackArgs = args.length > 1
                ? Arrays.copyOfRange(args, 1, args.length)
                : new JSValue[0];

        JSPromise resultPromise = context.createJSPromise();
        try {
            JSValue result = callback.call(context, JSUndefined.INSTANCE, callbackArgs);
            if (context.hasPendingException()) {
                JSValue error = context.getPendingException();
                context.clearPendingException();
                resultPromise.reject(error);
            } else {
                resultPromise.resolve(context, result);
            }
        } catch (JSException e) {
            if (context.hasPendingException()) {
                context.clearPendingException();
            }
            resultPromise.reject(e.getErrorValue());
        } catch (Exception e) {
            if (context.hasPendingException()) {
                JSValue error = context.getPendingException();
                context.clearPendingException();
                resultPromise.reject(error);
                return resultPromise;
            }
            if (context.hasPendingException()) {
                context.clearPendingException();
            }
            resultPromise.reject(new com.caoccao.qjs4j.core.JSString(
                    "Error in Promise.try callback: " + e.getMessage()));
        }
        return resultPromise;
    }

    /**
     * Promise.withResolvers()
     */
    public static JSValue withResolvers(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject)) {
            return context.throwTypeError("Promise.withResolvers called on non-object");
        }
        JSPromise promise = context.createJSPromise();
        final boolean[] alreadyResolved = {false};

        JSNativeFunction resolveFn = new JSNativeFunction("resolve", 1, (childContext, thisValue, funcArgs) -> {
            if (alreadyResolved[0]) {
                return JSUndefined.INSTANCE;
            }
            alreadyResolved[0] = true;
            JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
            promise.resolve(childContext, value);
            return JSUndefined.INSTANCE;
        });
        JSNativeFunction rejectFn = new JSNativeFunction("reject", 1, (childContext, thisValue, funcArgs) -> {
            if (alreadyResolved[0]) {
                return JSUndefined.INSTANCE;
            }
            alreadyResolved[0] = true;
            JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
            promise.reject(reason);
            return JSUndefined.INSTANCE;
        });

        JSObject result = context.createJSObject();
        result.set(PropertyKey.PROMISE, promise);
        result.set(PropertyKey.RESOLVE, resolveFn);
        result.set(PropertyKey.REJECT, rejectFn);
        return result;
    }

    private static final class PromiseCapability {
        private JSValue promise;
        private JSValue reject;
        private JSValue resolve;

        private PromiseCapability() {
            promise = JSUndefined.INSTANCE;
            reject = JSUndefined.INSTANCE;
            resolve = JSUndefined.INSTANCE;
        }

        public JSValue promise() {
            return promise;
        }

        public JSValue reject() {
            return reject;
        }

        public JSValue resolve() {
            return resolve;
        }

        public void setPromise(JSValue promise) {
            this.promise = promise;
        }

        public void setReject(JSValue reject) {
            this.reject = reject;
        }

        public void setResolve(JSValue resolve) {
            this.resolve = resolve;
        }
    }
}
