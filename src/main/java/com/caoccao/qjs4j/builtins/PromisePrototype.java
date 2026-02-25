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
        return invokeThen(context, thisArg, thenArgs);
    }

    /**
     * Promise.prototype.finally(onFinally)
     * ES2020 25.6.5.2
     * Returns a new Promise, and attaches a callback that is called when the promise is settled.
     */
    public static JSValue finallyMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue onFinallyValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue constructor = speciesConstructor(context, thisArg, getDefaultPromiseConstructor(context));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!(onFinallyValue instanceof JSFunction onFinally)) {
            return invokeThen(context, thisArg, new JSValue[]{onFinallyValue, onFinallyValue});
        }

        JSNativeFunction onFulfilledWrapper = PromiseConstructor.createBuiltinFunction(context, "", 1,
                (childContext, thisValue, funcArgs) -> {
                    JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                    JSValue onFinallyResult = onFinally.call(childContext, JSUndefined.INSTANCE, new JSValue[0]);
                    if (childContext.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    JSValue resolved = PromiseConstructor.resolve(childContext, constructor, new JSValue[]{onFinallyResult});
                    if (childContext.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    JSNativeFunction valueThunk = PromiseConstructor.createBuiltinFunction(childContext, "", 0,
                            (nestedContext, nestedThis, nestedArgs) -> value);
                    return invokeThen(childContext, resolved, new JSValue[]{valueThunk});
                });

        JSNativeFunction onRejectedWrapper = PromiseConstructor.createBuiltinFunction(context, "", 1,
                (childContext, thisValue, funcArgs) -> {
                    JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                    JSValue onFinallyResult = onFinally.call(childContext, JSUndefined.INSTANCE, new JSValue[0]);
                    if (childContext.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    JSValue resolved = PromiseConstructor.resolve(childContext, constructor, new JSValue[]{onFinallyResult});
                    if (childContext.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                    JSNativeFunction thrower = PromiseConstructor.createBuiltinFunction(childContext, "", 0,
                            (nestedContext, nestedThis, nestedArgs) -> {
                                nestedContext.setPendingException(reason);
                                return JSUndefined.INSTANCE;
                            });
                    return invokeThen(childContext, resolved, new JSValue[]{thrower});
                });

        return invokeThen(context, thisArg, new JSValue[]{onFulfilledWrapper, onRejectedWrapper});
    }

    private static JSValue getDefaultPromiseConstructor(JSContext context) {
        JSObject global = context.getGlobalObject();
        if (global == null) {
            return JSUndefined.INSTANCE;
        }
        return global.get(context, PropertyKey.fromString(JSPromise.NAME));
    }

    private static JSValue invokeThen(JSContext context, JSValue target, JSValue[] thenArgs) {
        JSValue thenMethod = PromiseConstructor.callGet(context, target, PropertyKey.THEN);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!JSTypeChecking.isCallable(thenMethod)) {
            return context.throwTypeError("then is not a function");
        }
        return PromiseConstructor.callCallable(context, thenMethod, target, thenArgs);
    }

    private static JSValue speciesConstructor(JSContext context, JSValue target, JSValue defaultConstructor) {
        JSObject targetObject;
        if (target instanceof JSObject jsObject) {
            targetObject = jsObject;
        } else {
            targetObject = JSTypeConversions.toObject(context, target);
            if (targetObject == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        JSValue constructor = targetObject.get(context, PropertyKey.CONSTRUCTOR);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (constructor instanceof JSUndefined) {
            return defaultConstructor;
        }
        if (!(constructor instanceof JSObject constructorObject)) {
            return context.throwTypeError("Promise constructor is not an object");
        }
        JSValue species = constructorObject.get(context, PropertyKey.SYMBOL_SPECIES);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (species instanceof JSUndefined || species instanceof JSNull) {
            return defaultConstructor;
        }
        if (!JSTypeChecking.isConstructor(species)) {
            return context.throwTypeError("Promise[Symbol.species] is not a constructor");
        }
        return species;
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

        JSValue constructor = speciesConstructor(context, promise, getDefaultPromiseConstructor(context));
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        PromiseConstructor.PromiseCapability promiseCapability = PromiseConstructor.newPromiseCapability(context, constructor);
        if (promiseCapability == null) {
            return JSUndefined.INSTANCE;
        }

        JSFunction onFulfilled = null;
        JSFunction onRejected = null;

        if (args.length > 0 && args[0] instanceof JSFunction) {
            onFulfilled = (JSFunction) args[0];
        }

        if (args.length > 1 && args[1] instanceof JSFunction) {
            onRejected = (JSFunction) args[1];
        }

        JSPromise.ReactionRecord fulfillReaction;
        JSPromise.ReactionRecord rejectReaction;
        if (promiseCapability.promise() instanceof JSPromise chainedPromise) {
            fulfillReaction = new JSPromise.ReactionRecord(onFulfilled, chainedPromise, context);
            rejectReaction = new JSPromise.ReactionRecord(onRejected, chainedPromise, context);
        } else {
            fulfillReaction = new JSPromise.ReactionRecord(
                    onFulfilled, context, promiseCapability.resolve(), promiseCapability.reject());
            rejectReaction = new JSPromise.ReactionRecord(
                    onRejected, context, promiseCapability.resolve(), promiseCapability.reject());
        }

        // Add reactions to the promise
        promise.addReactions(fulfillReaction, rejectReaction);

        return promiseCapability.promise();
    }
}
