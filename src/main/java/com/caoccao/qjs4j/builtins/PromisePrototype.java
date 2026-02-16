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
     * Promise.prototype.catch(onRejected)
     * ES2020 25.6.5.1
     * Returns a new Promise, and attaches a callback for rejection only.
     */
    public static JSValue catchMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        // catch is just then(undefined, onRejected)
        JSValue[] thenArgs = new JSValue[]{JSUndefined.INSTANCE, args.length > 0 ? args[0] : JSUndefined.INSTANCE};
        return then(context, thisArg, thenArgs);
    }

    /**
     * Promise.prototype.finally(onFinally)
     * ES2020 25.6.5.2
     * Returns a new Promise, and attaches a callback that is called when the promise is settled.
     */
    public static JSValue finallyMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSPromise promise)) {
            return context.throwTypeError("Promise.prototype.finally called on non-Promise");
        }
        JSValue onFinallyValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue constructor = getPromiseConstructor(context, promise);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(onFinallyValue instanceof JSFunction onFinally)) {
            // Non-callable onFinally: pass through via then(undefined behavior).
            return then(context, thisArg, new JSValue[]{onFinallyValue, onFinallyValue});
        }

        JSNativeFunction onFulfilledWrapper = new JSNativeFunction("onFinallyFulfilled", 1,
                (childContext, thisValue, funcArgs) -> {
                    JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                    JSValue onFinallyResult = onFinally.call(childContext, JSUndefined.INSTANCE, new JSValue[0]);
                    if (childContext.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    JSValue resolved = PromiseConstructor.resolve(childContext, constructor, new JSValue[]{onFinallyResult});
                    if (!(resolved instanceof JSPromise resolvedPromise)) {
                        return value;
                    }
                    JSNativeFunction valueThunk = new JSNativeFunction("valueThunk", 0,
                            (nestedContext, nestedThis, nestedArgs) -> value);
                    return then(childContext, resolvedPromise, new JSValue[]{valueThunk, JSUndefined.INSTANCE});
                });

        JSNativeFunction onRejectedWrapper = new JSNativeFunction("onFinallyRejected", 1,
                (childContext, thisValue, funcArgs) -> {
                    JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                    JSValue onFinallyResult = onFinally.call(childContext, JSUndefined.INSTANCE, new JSValue[0]);
                    if (childContext.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    JSValue resolved = PromiseConstructor.resolve(childContext, constructor, new JSValue[]{onFinallyResult});
                    if (!(resolved instanceof JSPromise resolvedPromise)) {
                        JSPromise rejectedPromise = childContext.createJSPromise();
                        rejectedPromise.reject(reason);
                        return rejectedPromise;
                    }
                    JSNativeFunction thrower = new JSNativeFunction("thrower", 0,
                            (nestedContext, nestedThis, nestedArgs) -> {
                                JSPromise rejectedPromise = nestedContext.createJSPromise();
                                rejectedPromise.reject(reason);
                                return rejectedPromise;
                            });
                    return then(childContext, resolvedPromise, new JSValue[]{thrower, JSUndefined.INSTANCE});
                });

        return then(context, thisArg, new JSValue[]{onFulfilledWrapper, onRejectedWrapper});
    }

    private static JSValue getPromiseConstructor(JSContext context, JSPromise promise) {
        JSValue constructor = promise.get("constructor");
        if (constructor instanceof JSObject constructorObject) {
            JSValue species = constructorObject.get(PropertyKey.SYMBOL_SPECIES);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            if (!(species instanceof JSUndefined) && !(species instanceof JSNull)) {
                if (species instanceof JSObject) {
                    return species;
                }
                return context.throwTypeError("Promise[Symbol.species] is not an object");
            }
            return constructor;
        }
        JSObject global = context.getGlobalObject();
        if (global != null) {
            return global.get("Promise");
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * Promise.prototype.then(onFulfilled, onRejected)
     * ES2020 25.6.5.4
     * Returns a new Promise, and attaches callbacks for fulfillment and/or rejection.
     */
    public static JSValue then(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSPromise promise)) {
            return context.throwTypeError("Promise.prototype.then called on non-Promise");
        }

        JSFunction onFulfilled = null;
        JSFunction onRejected = null;

        if (args.length > 0 && args[0] instanceof JSFunction) {
            onFulfilled = (JSFunction) args[0];
        }

        if (args.length > 1 && args[1] instanceof JSFunction) {
            onRejected = (JSFunction) args[1];
        }

        // Create a new promise to be returned (for chaining).
        JSPromise chainedPromise = context.createJSPromise();

        // Create reaction records
        JSPromise.ReactionRecord fulfillReaction = null;
        if (onFulfilled != null) {
            fulfillReaction = new JSPromise.ReactionRecord(onFulfilled, chainedPromise, context);
        } else {
            // If no onFulfilled, pass value through
            fulfillReaction = new JSPromise.ReactionRecord(null, chainedPromise, context);
        }

        JSPromise.ReactionRecord rejectReaction = null;
        if (onRejected != null) {
            rejectReaction = new JSPromise.ReactionRecord(onRejected, chainedPromise, context);
        } else {
            // If no onRejected, pass rejection through
            rejectReaction = new JSPromise.ReactionRecord(null, chainedPromise, context);
        }

        // Add reactions to the promise
        promise.addReactions(fulfillReaction, rejectReaction);

        return chainedPromise;
    }
}
