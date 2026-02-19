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
        if (!(thisArg instanceof JSObject)) {
            return context.throwTypeError("Promise.all called on non-object");
        }
        JSArray array = toArray(context, args, "Promise.all");
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
                                results.set(index, value);
                                if (--remaining[0] == 0) {
                                    resultPromise.fulfill(results);
                                }
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onReject", 1, (childContext, thisValue, funcArgs) -> {
                                JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                                resultPromise.reject(reason);
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context));
        }
        return resultPromise;
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
                                results.set(index, result);
                                if (--remaining[0] == 0) {
                                    resultPromise.fulfill(results);
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
                                results.set(index, result);
                                if (--remaining[0] == 0) {
                                    resultPromise.fulfill(results);
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
                                resultPromise.fulfill(value);
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onReject", 1, (childContext, thisValue, funcArgs) -> {
                                JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                                errors.set(index, reason);
                                if (--remaining[0] == 0) {
                                    resultPromise.reject(JSAggregateError.create(context, errors));
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
        for (int i = 0; i < length; i++) {
            JSPromise elementPromise = toPromise(context, thisArg, array.get(i));
            if (elementPromise == null) {
                return JSUndefined.INSTANCE;
            }
            elementPromise.addReactions(
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onFulfill", 1, (childContext, thisValue, funcArgs) -> {
                                JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                                resultPromise.fulfill(value);
                                return JSUndefined.INSTANCE;
                            }),
                            null,
                            context),
                    new JSPromise.ReactionRecord(
                            new JSNativeFunction("onReject", 1, (childContext, thisValue, funcArgs) -> {
                                JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                                resultPromise.reject(reason);
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
}
