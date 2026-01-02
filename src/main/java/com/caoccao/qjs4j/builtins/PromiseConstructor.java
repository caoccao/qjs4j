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
 * Implementation of Promise constructor and static methods.
 * Based on ES2020 Promise specification (simplified).
 */
public final class PromiseConstructor {

    /**
     * Promise.all(iterable)
     * ES2020 25.6.4.1
     * Returns a Promise that fulfills when all promises fulfill, or rejects when any promise rejects.
     */
    public static JSValue all(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Promise.all requires an iterable");
        }

        // Convert iterable to array
        JSArray array;
        if (args[0] instanceof JSArray jsArray) {
            array = jsArray;
        } else if (JSIteratorHelper.isIterable(args[0])) {
            array = JSIteratorHelper.toArray(context, args[0]);
        } else {
            return context.throwTypeError("Promise.all requires an iterable");
        }

        int length = (int) array.getLength();

        // Empty array resolves immediately
        if (length == 0) {
            JSPromise promise = createPromise(context);
            promise.fulfill(context.createJSArray());
            return promise;
        }

        JSPromise resultPromise = createPromise(context);
        JSArray results = context.createJSArray();
        final int[] remaining = {length}; // How many promises left to resolve

        for (int i = 0; i < length; i++) {
            final int index = i;
            JSValue element = array.get(i);

            // Convert to promise if needed
            if (element instanceof JSPromise elementPromise) {
                // Add reaction to track completion
                elementPromise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onFulfill", 1, (childContext, thisValue, funcArgs) -> {
                                    results.set(index, funcArgs[0]);
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        resultPromise.fulfill(results);
                                    }
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                context
                        ),
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onReject", 1, (childContext, thisValue, funcArgs) -> {
                                    resultPromise.reject(funcArgs[0]);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                context
                        )
                );
            } else {
                // Not a promise, treat as already resolved
                results.set(index, element);
                remaining[0]--;
                if (remaining[0] == 0) {
                    resultPromise.fulfill(results);
                }
            }
        }

        return resultPromise;
    }

    /**
     * Promise.allSettled(iterable)
     * ES2020 25.6.4.2
     * Returns a Promise that fulfills when all promises have settled (fulfilled or rejected).
     */
    public static JSValue allSettled(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Promise.allSettled requires an iterable");
        }

        // Convert iterable to array
        JSArray array;
        if (args[0] instanceof JSArray jsArray) {
            array = jsArray;
        } else if (JSIteratorHelper.isIterable(args[0])) {
            array = JSIteratorHelper.toArray(context, args[0]);
        } else {
            return context.throwTypeError("Promise.allSettled requires an iterable");
        }

        int length = (int) array.getLength();

        // Empty array resolves immediately
        if (length == 0) {
            JSPromise promise = createPromise(context);
            promise.fulfill(context.createJSArray());
            return promise;
        }

        JSPromise resultPromise = createPromise(context);
        JSArray results = context.createJSArray();
        final int[] remaining = {length};

        for (int i = 0; i < length; i++) {
            final int index = i;
            JSValue element = array.get(i);

            if (element instanceof JSPromise elementPromise) {
                // Add reactions for both fulfill and reject
                elementPromise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onFulfill", 1, (childContext, thisValue, funcArgs) -> {
                                    JSObject result = new JSObject();
                                    result.set("status", new JSString("fulfilled"));
                                    result.set("value", funcArgs[0]);
                                    results.set(index, result);
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        resultPromise.fulfill(results);
                                    }
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                context
                        ),
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onReject", 1, (childContext, thisValue, funcArgs) -> {
                                    JSObject result = new JSObject();
                                    result.set("status", new JSString("rejected"));
                                    result.set("reason", funcArgs[0]);
                                    results.set(index, result);
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        resultPromise.fulfill(results);
                                    }
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                context
                        )
                );
            } else {
                // Not a promise, treat as already fulfilled
                JSObject result = new JSObject();
                result.set("status", new JSString("fulfilled"));
                result.set("value", element);
                results.set(index, result);
                remaining[0]--;
                if (remaining[0] == 0) {
                    resultPromise.fulfill(results);
                }
            }
        }

        return resultPromise;
    }

    /**
     * Promise.any(iterable)
     * ES2021 25.6.4.3
     * Returns a Promise that fulfills when any promise fulfills, or rejects when all promises reject.
     */
    public static JSValue any(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Promise.any requires an iterable");
        }

        // Convert iterable to array
        JSArray array;
        if (args[0] instanceof JSArray jsArray) {
            array = jsArray;
        } else if (JSIteratorHelper.isIterable(args[0])) {
            array = JSIteratorHelper.toArray(context, args[0]);
        } else {
            return context.throwTypeError("Promise.any requires an iterable");
        }

        int length = (int) array.getLength();

        // Empty array rejects with AggregateError
        if (length == 0) {
            JSPromise promise = createPromise(context);
            promise.reject(new JSString("AggregateError: All promises were rejected"));
            return promise;
        }

        JSPromise resultPromise = createPromise(context);
        JSArray errors = context.createJSArray();
        final int[] remaining = {length};

        for (int i = 0; i < length; i++) {
            final int index = i;
            JSValue element = array.get(i);

            if (element instanceof JSPromise elementPromise) {
                elementPromise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onFulfill", 1, (childContext, thisValue, funcArgs) -> {
                                    resultPromise.fulfill(funcArgs[0]);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                context
                        ),
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onReject", 1, (childContext, thisValue, funcArgs) -> {
                                    errors.set(index, funcArgs[0]);
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        resultPromise.reject(new JSString("AggregateError: All promises were rejected"));
                                    }
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                context
                        )
                );
            } else {
                // Not a promise, fulfill immediately
                resultPromise.fulfill(element);
                break;
            }
        }

        return resultPromise;
    }

    /**
     * Helper method to create a new Promise with the correct prototype.
     */
    private static JSPromise createPromise(JSContext context) {
        JSPromise promise = new JSPromise();
        JSObject prototype = getPromisePrototype(context);
        if (prototype != null) {
            promise.setPrototype(prototype);
        }
        return promise;
    }

    /**
     * Helper method to get the Promise prototype from the global object.
     * This is needed to set the [[Prototype]] on newly created promises.
     */
    private static JSObject getPromisePrototype(JSContext context) {
        JSValue promiseConstructor = context.getGlobalObject().get("Promise");
        if (promiseConstructor instanceof JSObject) {
            JSValue prototype = ((JSObject) promiseConstructor).get("prototype");
            if (prototype instanceof JSObject) {
                return (JSObject) prototype;
            }
        }
        return null;
    }

    /**
     * Promise.race(iterable)
     * ES2020 25.6.4.5
     * Returns a Promise that settles as soon as any promise in the iterable settles.
     */
    public static JSValue race(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwTypeError("Promise.race requires an iterable");
        }

        // Convert iterable to array
        JSArray array;
        if (args[0] instanceof JSArray jsArray) {
            array = jsArray;
        } else if (JSIteratorHelper.isIterable(args[0])) {
            array = JSIteratorHelper.toArray(context, args[0]);
        } else {
            return context.throwTypeError("Promise.race requires an iterable");
        }

        int length = (int) array.getLength();
        JSPromise resultPromise = createPromise(context);

        for (int i = 0; i < length; i++) {
            JSValue element = array.get(i);

            if (element instanceof JSPromise elementPromise) {
                // Add reaction to settle on first completion
                elementPromise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onFulfill", 1, (childContext, thisValue, funcArgs) -> {
                                    resultPromise.fulfill(funcArgs[0]);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                context
                        ),
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onReject", 1, (childContext, thisValue, funcArgs) -> {
                                    resultPromise.reject(funcArgs[0]);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                context
                        )
                );
            } else {
                // Not a promise, fulfill immediately
                resultPromise.fulfill(element);
                break;
            }
        }

        return resultPromise;
    }

    /**
     * Promise.reject(reason)
     * ES2020 25.6.4.4
     * Returns a Promise that is rejected with the given reason.
     */
    public static JSValue reject(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // Create a new promise and reject it
        JSPromise promise = createPromise(context);
        promise.reject(reason);
        return promise;
    }

    /**
     * Promise.resolve(value)
     * ES2020 25.6.4.6
     * Returns a Promise that is resolved with the given value.
     */
    public static JSValue resolve(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // If value is already a promise, return it
        if (value instanceof JSPromise) {
            return value;
        }

        // Create a new promise and fulfill it
        JSPromise promise = createPromise(context);
        promise.fulfill(value);
        return promise;
    }

    /**
     * Promise.withResolvers()
     * ES2024 27.2.4.9
     * Returns an object with a new promise and its resolve/reject functions.
     */
    public static JSValue withResolvers(JSContext context, JSValue thisArg, JSValue[] args) {
        JSPromise promise = createPromise(context);

        // Create resolve function
        JSNativeFunction resolveFn = new JSNativeFunction("resolve", 1, (childContext, thisValue, funcArgs) -> {
            JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
            promise.fulfill(value);
            return JSUndefined.INSTANCE;
        });

        // Create reject function
        JSNativeFunction rejectFn = new JSNativeFunction("reject", 1, (childContext, thisValue, funcArgs) -> {
            JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
            promise.reject(reason);
            return JSUndefined.INSTANCE;
        });

        // Create result object
        JSObject result = new JSObject();
        result.set("promise", promise);
        result.set("resolve", resolveFn);
        result.set("reject", rejectFn);

        return result;
    }
}
