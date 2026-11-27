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

package com.caoccao.qjs4j.builtins.promise;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.error.JSAggregateError;
import com.caoccao.qjs4j.core.iterator.JSIteratorHelper;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.Arrays;

/**
 * Implementation of Promise constructor and static methods. Based on QuickJS Promise behavior.
 */
public final class PromiseConstructor {

    /**
     * Promise.all(iterable)
     */
    public static JSValue all(JSContext context, JSValue thisArg, JSValue[] args) {
        return performPromiseCombinator(context, thisArg, args, PromiseCombinatorMode.ALL, false);
    }

    /**
     * Promise.allKeyed(dictionary)
     */
    public static JSValue allKeyed(JSContext context, JSValue thisArg, JSValue[] args) {
        return performPromiseCombinator(context, thisArg, args, PromiseCombinatorMode.ALL, true);
    }

    /**
     * Promise.allSettled(iterable)
     */
    public static JSValue allSettled(JSContext context, JSValue thisArg, JSValue[] args) {
        return performPromiseCombinator(context, thisArg, args, PromiseCombinatorMode.ALL_SETTLED, false);
    }

    /**
     * Promise.allSettledKeyed(dictionary)
     */
    public static JSValue allSettledKeyed(JSContext context, JSValue thisArg, JSValue[] args) {
        return performPromiseCombinator(context, thisArg, args, PromiseCombinatorMode.ALL_SETTLED, true);
    }

    /**
     * Promise.any(iterable)
     */
    public static JSValue any(JSContext context, JSValue thisArg, JSValue[] args) {
        return performPromiseCombinator(context, thisArg, args, PromiseCombinatorMode.ANY, false);
    }

    /**
     * Promise constructor call handler.
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSPromise.create(context, args);
    }

    public static JSValue callCallable(JSContext context, JSValue callable, JSValue thisArg, JSValue[] args) {
        try {
            return JSCall.call(context, callable, thisArg, args);
        } catch (Exception e) {
            captureException(context, e);
            return JSUndefined.INSTANCE;
        }
    }

    static JSValue callGet(JSContext context, JSValue target, PropertyKey propertyKey) {
        try {
            if (target instanceof JSObject objectTarget) {
                return objectTarget.get(propertyKey);
            }
            JSObject boxedTarget = JSTypeConversions.toObject(context, target);
            if (boxedTarget == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return boxedTarget.get(propertyKey);
        } catch (Exception e) {
            captureException(context, e);
            return JSUndefined.INSTANCE;
        }
    }

    private static void captureException(JSContext context, Exception exception) {
        if (context.hasPendingException()) {
            return;
        }
        if (exception instanceof JSException jsException) {
            context.setPendingException(jsException.getErrorValue());
        } else if (exception instanceof JSVirtualMachineException vmException && vmException.getJsValue() != null) {
            context.setPendingException(vmException.getJsValue());
        } else if (exception instanceof JSVirtualMachineException vmException && vmException.getJsError() != null) {
            context.setPendingException(vmException.getJsError());
        } else {
            context.throwError(exception.getMessage() != null ? exception.getMessage() : "Unhandled exception");
        }
    }

    private static JSValue closeIteratorAndRejectAbruptPromise(JSContext context, JSObject iteratorObject,
            PromiseCapability promiseCapability) {
        JSValue pendingError = context.takePendingException();
        try {
            JSIteratorHelper.closeIterator(context, iteratorObject);
        } catch (JSException e) {
            if (!context.hasPendingException()) {
                context.setPendingException(e.getErrorValue());
            }
        } catch (Exception e) {
            if (!context.hasPendingException()) {
                context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
            }
        }
        if (context.hasPendingException()) {
            context.clearPendingException();
        }
        context.setPendingException(pendingError);
        return rejectAbruptPromise(context, promiseCapability);
    }

    private static JSValue combineIterable(JSContext context, JSValue thisArg, JSValue iterable,
            PromiseCombinatorMode mode, String methodName, PromiseCapability promiseCapability,
            JSValue promiseResolve) {
        JSValue iterator = JSIteratorHelper.getIterator(context, iterable);
        if (context.hasPendingException()) {
            return rejectAbruptPromise(context, promiseCapability);
        }
        if (!(iterator instanceof JSObject iteratorObject)) {
            context.throwTypeError("Value is not iterable");
            return rejectAbruptPromise(context, promiseCapability);
        }

        // Race passes the capability functions through and never collects element values.
        CombinatorResults results = mode == PromiseCombinatorMode.RACE
                ? null
                : new CombinatorResults(context, mode, promiseCapability);
        int index = 0;
        while (true) {
            JSObject nextResult = nextPromiseIteratorResult(context, iteratorObject);
            if (context.hasPendingException()) {
                return rejectAbruptPromise(context, promiseCapability);
            }
            if (nextResult == null) {
                if (results != null && --results.remaining == 0) {
                    return results.finish(context, false);
                }
                return promiseCapability.promise();
            }

            JSValue nextValue = callGet(context, nextResult, PropertyKey.VALUE);
            if (context.hasPendingException()) {
                return rejectAbruptPromise(context, promiseCapability);
            }

            if (results != null) {
                results.values.set(index, JSUndefined.INSTANCE);
                if (context.hasPendingException()) {
                    return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
                }
            }

            JSValue nextPromise = callCallable(context, promiseResolve, thisArg, new JSValue[]{nextValue});
            if (context.hasPendingException()) {
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }

            JSValue thenMethod = getCallableProperty(context, nextPromise, PropertyKey.THEN, methodName,
                    " resolve result then is not callable");
            if (thenMethod == null) {
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }

            JSValue[] handlers = results == null
                    ? new JSValue[]{promiseCapability.resolve(), promiseCapability.reject()}
                    : results.handlers(context, index++);
            callCallable(context, thenMethod, nextPromise, handlers);
            if (context.hasPendingException()) {
                return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
            }
        }
    }

    private static JSValue combineKeyed(JSContext context, JSValue thisArg, JSValue dictionary,
            PromiseCombinatorMode mode, String methodName, PromiseCapability promiseCapability,
            JSValue promiseResolve) {
        if (dictionary.isNullOrUndefined()) {
            context.throwTypeError("Cannot convert undefined or null to object");
            return rejectAbruptPromise(context, promiseCapability);
        }

        JSObject dictionaryObject;
        if (dictionary instanceof JSObject obj) {
            dictionaryObject = obj;
        } else {
            context.throwTypeError(methodName + " requires an object argument");
            return rejectAbruptPromise(context, promiseCapability);
        }

        JSObject resultObject = context.createJSObject();
        PropertyKey[] propertyKeys = dictionaryObject.enumerableKeys();
        String[] keys = new String[propertyKeys.length];
        for (int i = 0; i < propertyKeys.length; i++) {
            keys[i] = propertyKeys[i].toString();
        }
        int[] remainingCount = {keys.length};

        if (keys.length == 0) {
            callCallable(context, promiseCapability.resolve(), JSUndefined.INSTANCE, new JSValue[]{resultObject});
            return promiseCapability.promise();
        }

        for (String key : keys) {
            JSValue value = dictionaryObject.get(PropertyKey.fromString(key));
            JSValue nextPromise = callCallable(context, promiseResolve, thisArg, new JSValue[]{value});
            if (context.hasPendingException()) {
                return rejectAbruptPromise(context, promiseCapability);
            }

            PropertyKey resultKey = PropertyKey.fromString(key);
            JSNativeFunction onFulfilled = keyedElementHandler(context, promiseCapability, resultObject, resultKey,
                    remainingCount, mode, true);
            JSNativeFunction onRejected = keyedElementHandler(context, promiseCapability, resultObject, resultKey,
                    remainingCount, mode, false);

            context.transferPrototype(onFulfilled, JSFunction.NAME);
            context.transferPrototype(onRejected, JSFunction.NAME);
            JSValue thenFn = callGet(context, nextPromise, PropertyKey.THEN);
            if (context.hasPendingException()) {
                return rejectAbruptPromise(context, promiseCapability);
            }
            callCallable(context, thenFn, nextPromise, new JSValue[]{onFulfilled, onRejected});
            if (context.hasPendingException()) {
                return rejectAbruptPromise(context, promiseCapability);
            }
        }

        return promiseCapability.promise();
    }

    static JSNativeFunction createBuiltinFunction(JSContext context, String functionName, int length,
            JSNativeCallback callback) {
        JSNativeFunction function = new JSNativeFunction(context, functionName, length, callback);
        context.transferPrototype(function, JSFunction.NAME);
        return function;
    }

    private static JSValue getCallableProperty(JSContext context, JSValue object, PropertyKey key, String methodName,
            String error) {
        JSValue value = callGet(context, object, key);
        if (context.hasPendingException()) {
            return null;
        }
        if (!JSTypeChecking.isCallable(value)) {
            context.throwTypeError(methodName + error);
            return null;
        }
        return value;
    }

    private static String getPromiseCombinatorName(PromiseCombinatorMode mode) {
        return switch (mode) {
            case ALL -> "Promise.all";
            case ALL_SETTLED -> "Promise.allSettled";
            case ANY -> "Promise.any";
            case RACE -> "Promise.race";
        };
    }

    /**
     * get Promise[@@species]
     */
    public static JSValue getSpecies(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg;
    }

    private static JSNativeFunction keyedElementHandler(JSContext context, PromiseCapability capability,
            JSObject result, PropertyKey key, int[] remaining, PromiseCombinatorMode mode, boolean fulfilled) {
        return new JSNativeFunction(context, "", 1, (childContext, thisArg, args) -> {
            JSValue value = JSValue.argumentOrUndefined(args, 0);
            if (!fulfilled && mode == PromiseCombinatorMode.ALL) {
                callCallable(childContext, capability.reject(), JSUndefined.INSTANCE, new JSValue[]{value});
            } else {
                result.set(key,
                        mode == PromiseCombinatorMode.ALL_SETTLED
                                ? settledResult(childContext, value, fulfilled)
                                : value);
                if (--remaining[0] == 0) {
                    callCallable(childContext, capability.resolve(), JSUndefined.INSTANCE, new JSValue[]{result});
                }
            }
            return JSUndefined.INSTANCE;
        });
    }

    static PromiseCapability newPromiseCapability(JSContext context, JSValue constructor) {
        PromiseCapability promiseCapability = new PromiseCapability();
        JSNativeFunction executor = createBuiltinFunction(context, "", 2, (childContext, thisArg, args) -> {
            if (!promiseCapability.resolve().isUndefined() || !promiseCapability.reject().isUndefined()) {
                return childContext.throwTypeError("Promise capability executor called multiple times");
            }
            JSValue resolveFunction = JSValue.argumentOrUndefined(args, 0);
            JSValue rejectFunction = JSValue.argumentOrUndefined(args, 1);
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
        if (!JSTypeChecking.isCallable(promiseCapability.resolve())
                || !JSTypeChecking.isCallable(promiseCapability.reject())) {
            context.throwTypeError("Promise constructor returned non-callable resolve or reject");
            return null;
        }
        promiseCapability.setPromise(promise);
        return promiseCapability;
    }

    /** A null result signals exhaustion or an exception while advancing the iterator. */
    private static JSObject nextPromiseIteratorResult(JSContext context, JSObject iterator) {
        JSObject result;
        try {
            result = JSIteratorHelper.iteratorNext(iterator, context);
        } catch (Exception exception) {
            captureException(context, exception);
            return null;
        }
        if (context.hasPendingException()) {
            return null;
        }
        if (result == null) {
            context.throwTypeError("iterator result is not an object");
            return null;
        }
        JSValue done = callGet(context, result, PropertyKey.DONE);
        return context.hasPendingException() || JSTypeConversions.toBoolean(done).isBooleanTrue() ? null : result;
    }

    private static JSValue performPromiseCombinator(JSContext context, JSValue thisArg, JSValue[] args,
            PromiseCombinatorMode mode, boolean keyed) {
        String methodName = getPromiseCombinatorName(mode) + (keyed ? "Keyed" : "");
        if (!JSTypeChecking.isConstructor(thisArg)) {
            return context.throwTypeError(methodName + " called on non-constructor");
        }

        PromiseCapability promiseCapability = newPromiseCapability(context, thisArg);
        if (promiseCapability == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue promiseResolve = getCallableProperty(context, thisArg, PropertyKey.RESOLVE, methodName,
                " resolve is not callable");
        if (promiseResolve == null) {
            return rejectAbruptPromise(context, promiseCapability);
        }

        JSValue input = JSValue.argumentOrUndefined(args, 0);
        if (keyed) {
            return combineKeyed(context, thisArg, input, mode, methodName, promiseCapability, promiseResolve);
        }
        return combineIterable(context, thisArg, input, mode, methodName, promiseCapability, promiseResolve);
    }

    /**
     * Promise.race(iterable)
     */
    public static JSValue race(JSContext context, JSValue thisArg, JSValue[] args) {
        return performPromiseCombinator(context, thisArg, args, PromiseCombinatorMode.RACE, false);
    }

    /**
     * Promise.reject(reason)
     */
    public static JSValue reject(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!JSTypeChecking.isConstructor(thisArg)) {
            return context.throwTypeError("Promise.reject called on non-constructor");
        }
        JSValue reason = JSValue.argumentOrUndefined(args, 0);
        PromiseCapability promiseCapability = newPromiseCapability(context, thisArg);
        if (promiseCapability == null) {
            return JSUndefined.INSTANCE;
        }
        callCallable(context, promiseCapability.reject(), JSUndefined.INSTANCE, new JSValue[]{reason});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return promiseCapability.promise();
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

    /**
     * Promise.resolve(value)
     */
    public static JSValue resolve(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!JSTypeChecking.isConstructor(thisArg)) {
            return context.throwTypeError("Promise.resolve called on non-constructor");
        }
        JSValue value = JSValue.argumentOrUndefined(args, 0);

        if (value instanceof JSPromise jsPromise) {
            JSValue constructor = jsPromise.get(PropertyKey.CONSTRUCTOR);
            if (constructor == thisArg) {
                return jsPromise;
            }
        }

        PromiseCapability promiseCapability = newPromiseCapability(context, thisArg);
        if (promiseCapability == null) {
            return JSUndefined.INSTANCE;
        }
        callCallable(context, promiseCapability.resolve(), JSUndefined.INSTANCE, new JSValue[]{value});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return promiseCapability.promise();
    }

    private static JSObject settledResult(JSContext context, JSValue value, boolean fulfilled) {
        JSObject result = context.createJSObject();
        result.set(PropertyKey.STATUS, new JSString(fulfilled ? "fulfilled" : "rejected"));
        result.set(fulfilled ? PropertyKey.VALUE : PropertyKey.REASON, value);
        return result;
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

    /**
     * Promise.try(callback, ...args)
     */
    public static JSValue tryMethod(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!JSTypeChecking.isConstructor(thisArg)) {
            return context.throwTypeError("Promise.try called on non-constructor");
        }
        if (args.length == 0 || !JSTypeChecking.isCallable(args[0])) {
            return context.throwTypeError("Promise.try requires a function");
        }

        PromiseCapability promiseCapability = newPromiseCapability(context, thisArg);
        if (promiseCapability == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue callback = args[0];
        JSValue[] callbackArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : JSValue.NO_ARGS;
        JSValue callbackResult = callCallable(context, callback, JSUndefined.INSTANCE, callbackArgs);
        if (context.hasPendingException()) {
            return rejectAbruptPromise(context, promiseCapability);
        }

        callCallable(context, promiseCapability.resolve(), JSUndefined.INSTANCE, new JSValue[]{callbackResult});
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        return promiseCapability.promise();
    }

    /**
     * Promise.withResolvers()
     */
    public static JSValue withResolvers(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!JSTypeChecking.isConstructor(thisArg)) {
            return context.throwTypeError("Promise.withResolvers called on non-constructor");
        }

        PromiseCapability promiseCapability = newPromiseCapability(context, thisArg);
        if (promiseCapability == null) {
            return JSUndefined.INSTANCE;
        }

        JSObject result = context.createJSObject();
        result.set(PropertyKey.PROMISE, promiseCapability.promise());
        result.set(PropertyKey.RESOLVE, promiseCapability.resolve());
        result.set(PropertyKey.REJECT, promiseCapability.reject());
        return result;
    }

    /** Result slots and outstanding reactions shared by all, allSettled and any. */
    private static final class CombinatorResults {
        private final PromiseCapability capability;
        private final PromiseCombinatorMode mode;
        // One extra element keeps synchronous thenables from settling before iteration finishes.
        private int remaining = 1;
        private boolean settled;
        private final JSArray values;

        CombinatorResults(JSContext context, PromiseCombinatorMode mode, PromiseCapability capability) {
            this.capability = capability;
            this.mode = mode;
            values = context.createJSArray();
        }

        private JSNativeFunction elementHandler(JSContext context, int index, boolean[] called, boolean fulfilled) {
            return createBuiltinFunction(context, "", 1, (childContext, thisValue, args) -> {
                if (!called[0]) {
                    called[0] = true;
                    JSValue value = JSValue.argumentOrUndefined(args, 0);
                    values.set(index,
                            mode == PromiseCombinatorMode.ALL_SETTLED
                                    ? settledResult(childContext, value, fulfilled)
                                    : value);
                    if (!childContext.hasPendingException() && --remaining == 0) {
                        finish(childContext, mode == PromiseCombinatorMode.ANY);
                    }
                }
                return JSUndefined.INSTANCE;
            });
        }

        JSValue finish(JSContext context, boolean fromAnyRejectHandler) {
            if (settled) {
                return capability.promise();
            }
            settled = true;
            if (mode == PromiseCombinatorMode.ANY) {
                callCallable(context, capability.reject(), JSUndefined.INSTANCE,
                        new JSValue[]{JSAggregateError.create(context, values)});
            } else {
                callCallable(context, capability.resolve(), JSUndefined.INSTANCE, new JSValue[]{values});
            }
            if (context.hasPendingException()) {
                if (fromAnyRejectHandler) {
                    settled = false;
                }
                return rejectAbruptPromise(context, capability);
            }
            return capability.promise();
        }

        JSValue[] handlers(JSContext context, int index) {
            boolean[] called = {false};
            JSValue resolve = mode == PromiseCombinatorMode.ANY
                    ? capability.resolve()
                    : elementHandler(context, index, called, true);
            JSValue reject = mode == PromiseCombinatorMode.ALL
                    ? capability.reject()
                    : elementHandler(context, index, called, false);
            remaining++;
            return new JSValue[]{resolve, reject};
        }
    }

    static final class PromiseCapability {
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

    private enum PromiseCombinatorMode {
        ALL,
        ALL_SETTLED,
        ANY,
        RACE
    }
}
