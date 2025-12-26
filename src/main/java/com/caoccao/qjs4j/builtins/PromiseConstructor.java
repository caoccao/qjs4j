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
     * Promise.resolve(value)
     * ES2020 25.6.4.6
     * Returns a Promise that is resolved with the given value.
     */
    public static JSValue resolve(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // If value is already a promise, return it
        if (value instanceof JSPromise) {
            return value;
        }

        // Create a new promise and fulfill it
        JSPromise promise = new JSPromise();
        promise.fulfill(value);
        return promise;
    }

    /**
     * Promise.reject(reason)
     * ES2020 25.6.4.4
     * Returns a Promise that is rejected with the given reason.
     */
    public static JSValue reject(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // Create a new promise and reject it
        JSPromise promise = new JSPromise();
        promise.reject(reason);
        return promise;
    }

    /**
     * Promise.all(iterable)
     * ES2020 25.6.4.1
     * Returns a Promise that fulfills when all promises fulfill, or rejects when any promise rejects.
     * Simplified: takes an array for now.
     */
    public static JSValue all(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSArray array)) {
            return ctx.throwError("TypeError", "Promise.all requires an iterable");
        }

        int length = (int) array.getLength();

        // Empty array resolves immediately
        if (length == 0) {
            JSPromise promise = new JSPromise();
            promise.fulfill(new JSArray());
            return promise;
        }

        JSPromise resultPromise = new JSPromise();
        JSArray results = new JSArray();
        final int[] remaining = {length}; // How many promises left to resolve

        for (int i = 0; i < length; i++) {
            final int index = i;
            JSValue element = array.get(i);

            // Convert to promise if needed
            if (element instanceof JSPromise elementPromise) {
                // Add reaction to track completion
                elementPromise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onFulfill", 1, (context, thisValue, funcArgs) -> {
                                    results.set(index, funcArgs[0]);
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        resultPromise.fulfill(results);
                                    }
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                ctx
                        ),
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onReject", 1, (context, thisValue, funcArgs) -> {
                                    resultPromise.reject(funcArgs[0]);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                ctx
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
     * Promise.race(iterable)
     * ES2020 25.6.4.5
     * Returns a Promise that settles as soon as any promise in the iterable settles.
     * Simplified: takes an array for now.
     */
    public static JSValue race(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSArray array)) {
            return ctx.throwError("TypeError", "Promise.race requires an iterable");
        }

        int length = (int) array.getLength();
        JSPromise resultPromise = new JSPromise();

        for (int i = 0; i < length; i++) {
            JSValue element = array.get(i);

            if (element instanceof JSPromise elementPromise) {
                // Add reaction to settle on first completion
                elementPromise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onFulfill", 1, (context, thisValue, funcArgs) -> {
                                    resultPromise.fulfill(funcArgs[0]);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                ctx
                        ),
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onReject", 1, (context, thisValue, funcArgs) -> {
                                    resultPromise.reject(funcArgs[0]);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                ctx
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
     * Promise.allSettled(iterable)
     * ES2020 25.6.4.2
     * Returns a Promise that fulfills when all promises have settled (fulfilled or rejected).
     * Simplified: takes an array for now.
     */
    public static JSValue allSettled(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSArray array)) {
            return ctx.throwError("TypeError", "Promise.allSettled requires an iterable");
        }

        int length = (int) array.getLength();

        // Empty array resolves immediately
        if (length == 0) {
            JSPromise promise = new JSPromise();
            promise.fulfill(new JSArray());
            return promise;
        }

        JSPromise resultPromise = new JSPromise();
        JSArray results = new JSArray();
        final int[] remaining = {length};

        for (int i = 0; i < length; i++) {
            final int index = i;
            JSValue element = array.get(i);

            if (element instanceof JSPromise elementPromise) {
                // Add reactions for both fulfill and reject
                elementPromise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onFulfill", 1, (context, thisValue, funcArgs) -> {
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
                                ctx
                        ),
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onReject", 1, (context, thisValue, funcArgs) -> {
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
                                ctx
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
     * Simplified: takes an array for now.
     */
    public static JSValue any(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSArray array)) {
            return ctx.throwError("TypeError", "Promise.any requires an iterable");
        }

        int length = (int) array.getLength();

        // Empty array rejects with AggregateError
        if (length == 0) {
            JSPromise promise = new JSPromise();
            promise.reject(new JSString("AggregateError: All promises were rejected"));
            return promise;
        }

        JSPromise resultPromise = new JSPromise();
        JSArray errors = new JSArray();
        final int[] remaining = {length};

        for (int i = 0; i < length; i++) {
            final int index = i;
            JSValue element = array.get(i);

            if (element instanceof JSPromise elementPromise) {
                elementPromise.addReactions(
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onFulfill", 1, (context, thisValue, funcArgs) -> {
                                    resultPromise.fulfill(funcArgs[0]);
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                ctx
                        ),
                        new JSPromise.ReactionRecord(
                                new JSNativeFunction("onReject", 1, (context, thisValue, funcArgs) -> {
                                    errors.set(index, funcArgs[0]);
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        resultPromise.reject(new JSString("AggregateError: All promises were rejected"));
                                    }
                                    return JSUndefined.INSTANCE;
                                }),
                                null,
                                ctx
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
}
