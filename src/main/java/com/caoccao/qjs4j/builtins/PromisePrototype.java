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
 * Implementation of Promise.prototype methods.
 * Based on ES2020 Promise specification (simplified).
 */
public final class PromisePrototype {

    /**
     * Promise.prototype.then(onFulfilled, onRejected)
     * ES2020 25.6.5.4
     * Returns a new Promise, and attaches callbacks for fulfillment and/or rejection.
     */
    public static JSValue then(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSPromise promise)) {
            return ctx.throwError("TypeError", "Promise.prototype.then called on non-Promise");
        }

        JSFunction onFulfilled = null;
        JSFunction onRejected = null;

        if (args.length > 0 && args[0] instanceof JSFunction) {
            onFulfilled = (JSFunction) args[0];
        }

        if (args.length > 1 && args[1] instanceof JSFunction) {
            onRejected = (JSFunction) args[1];
        }

        // Create a new promise to be returned (for chaining)
        JSPromise chainedPromise = new JSPromise();

        // Create reaction records
        JSPromise.ReactionRecord fulfillReaction = null;
        if (onFulfilled != null) {
            fulfillReaction = new JSPromise.ReactionRecord(onFulfilled, chainedPromise, ctx);
        } else {
            // If no onFulfilled, pass value through
            fulfillReaction = new JSPromise.ReactionRecord(null, chainedPromise, ctx);
        }

        JSPromise.ReactionRecord rejectReaction = null;
        if (onRejected != null) {
            rejectReaction = new JSPromise.ReactionRecord(onRejected, chainedPromise, ctx);
        } else {
            // If no onRejected, pass rejection through
            rejectReaction = new JSPromise.ReactionRecord(null, chainedPromise, ctx);
        }

        // Add reactions to the promise
        promise.addReactions(fulfillReaction, rejectReaction);

        return chainedPromise;
    }

    /**
     * Promise.prototype.catch(onRejected)
     * ES2020 25.6.5.1
     * Returns a new Promise, and attaches a callback for rejection only.
     */
    public static JSValue catchMethod(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // catch is just then(undefined, onRejected)
        JSValue[] thenArgs = new JSValue[]{JSUndefined.INSTANCE, args.length > 0 ? args[0] : JSUndefined.INSTANCE};
        return then(ctx, thisArg, thenArgs);
    }

    /**
     * Promise.prototype.finally(onFinally)
     * ES2020 25.6.5.2
     * Returns a new Promise, and attaches a callback that is called when the promise is settled.
     */
    public static JSValue finallyMethod(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSPromise promise)) {
            return ctx.throwError("TypeError", "Promise.prototype.finally called on non-Promise");
        }

        if (args.length == 0 || !(args[0] instanceof JSFunction onFinally)) {
            // If no callback, just return the promise
            return promise;
        }

        // Create wrapper functions that call onFinally and then pass through the value/reason
        JSNativeFunction onFulfilledWrapper = new JSNativeFunction("onFulfilled", 1,
                (context, thisValue, funcArgs) -> {
                    // Call the finally handler
                    onFinally.call(context, JSUndefined.INSTANCE, new JSValue[0]);
                    // Pass through the fulfillment value
                    return funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                });

        JSNativeFunction onRejectedWrapper = new JSNativeFunction("onRejected", 1,
                (context, thisValue, funcArgs) -> {
                    // Call the finally handler
                    onFinally.call(context, JSUndefined.INSTANCE, new JSValue[0]);
                    // Create a new rejected promise to pass through the rejection
                    JSPromise rejectedPromise = new JSPromise();
                    rejectedPromise.reject(funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE);
                    return rejectedPromise;
                });

        // Call then with both wrappers
        JSValue[] thenArgs = new JSValue[]{onFulfilledWrapper, onRejectedWrapper};
        return then(ctx, thisArg, thenArgs);
    }
}
