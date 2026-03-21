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
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

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
        return performPromiseCombinator(context, thisArg, args, PromiseCombinatorMode.ALL);
    }

    /**
     * Promise.allSettled(iterable)
     */
    public static JSValue allSettled(JSContext context, JSValue thisArg, JSValue[] args) {
        return performPromiseCombinator(context, thisArg, args, PromiseCombinatorMode.ALL_SETTLED);
    }

    /**
     * Promise.allKeyed(dictionary)
     */
    public static JSValue allKeyed(JSContext context, JSValue thisArg, JSValue[] args) {
        return performPromiseKeyedCombinator(context, thisArg, args, PromiseCombinatorMode.ALL);
    }

    /**
     * Promise.allSettledKeyed(dictionary)
     */
    public static JSValue allSettledKeyed(JSContext context, JSValue thisArg, JSValue[] args) {
        return performPromiseKeyedCombinator(context, thisArg, args, PromiseCombinatorMode.ALL_SETTLED);
    }

    /**
     * Promise.any(iterable)
     */
    public static JSValue any(JSContext context, JSValue thisArg, JSValue[] args) {
        return performPromiseCombinator(context, thisArg, args, PromiseCombinatorMode.ANY);
    }

    /**
     * Promise constructor call handler.
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSPromise.create(context, args);
    }

    static JSValue callCallable(JSContext context, JSValue callable, JSValue thisArg, JSValue[] args) {
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
        } catch (JSVirtualMachineException e) {
            if (!context.hasPendingException()) {
                if (e.getJsValue() != null) {
                    context.setPendingException(e.getJsValue());
                } else if (e.getJsError() != null) {
                    context.setPendingException(e.getJsError());
                } else {
                    context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                }
            }
            return JSUndefined.INSTANCE;
        } catch (Exception e) {
            if (!context.hasPendingException()) {
                context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
            }
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
        } catch (JSException e) {
            if (!context.hasPendingException()) {
                context.setPendingException(e.getErrorValue());
            }
            return JSUndefined.INSTANCE;
        } catch (JSVirtualMachineException e) {
            if (!context.hasPendingException()) {
                if (e.getJsValue() != null) {
                    context.setPendingException(e.getJsValue());
                } else if (e.getJsError() != null) {
                    context.setPendingException(e.getJsError());
                } else {
                    context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                }
            }
            return JSUndefined.INSTANCE;
        } catch (Exception e) {
            if (!context.hasPendingException()) {
                context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
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
                context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
            }
        }
        if (context.hasPendingException()) {
            context.clearPendingException();
        }
        context.setPendingException(pendingError);
        return rejectAbruptPromise(context, promiseCapability);
    }

    static JSNativeFunction createBuiltinFunction(
            JSContext context,
            String functionName,
            int length,
            JSNativeCallback callback) {
        JSNativeFunction function = new JSNativeFunction(context, functionName, length, callback);
        context.transferPrototype(function, JSFunction.NAME);
        return function;
    }

    private static JSValue finalizePromiseCombinator(
            JSContext context,
            PromiseCapability promiseCapability,
            PromiseCombinatorMode mode,
            JSArray values,
            boolean[] combinatorSettled) {
        if (combinatorSettled[0]) {
            return promiseCapability.promise();
        }
        combinatorSettled[0] = true;
        if (mode == PromiseCombinatorMode.ANY) {
            callCallable(context, promiseCapability.reject(), JSUndefined.INSTANCE,
                    new JSValue[]{JSAggregateError.create(context, values)});
        } else {
            callCallable(context, promiseCapability.resolve(), JSUndefined.INSTANCE, new JSValue[]{values});
        }
        if (context.hasPendingException()) {
            return rejectAbruptPromise(context, promiseCapability);
        }
        return promiseCapability.promise();
    }

    private static String getPromiseCombinatorName(PromiseCombinatorMode mode) {
        return switch (mode) {
            case ALL -> "Promise.all";
            case ALL_SETTLED -> "Promise.allSettled";
            case ANY -> "Promise.any";
        };
    }

    /**
     * get Promise[@@species]
     */
    public static JSValue getSpecies(JSContext context, JSValue thisArg, JSValue[] args) {
        return thisArg;
    }

    static PromiseCapability newPromiseCapability(JSContext context, JSValue constructor) {
        PromiseCapability promiseCapability = new PromiseCapability();
        JSNativeFunction executor = createBuiltinFunction(context, "", 2, (childContext, thisArg, args) -> {
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

    private static JSValue performPromiseCombinator(
            JSContext context,
            JSValue thisArg,
            JSValue[] args,
            PromiseCombinatorMode mode) {
        String methodName = getPromiseCombinatorName(mode);
        if (!JSTypeChecking.isConstructor(thisArg)) {
            return context.throwTypeError(methodName + " called on non-constructor");
        }

        PromiseCapability promiseCapability = newPromiseCapability(context, thisArg);
        if (promiseCapability == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue promiseResolve = callGet(context, thisArg, PropertyKey.RESOLVE);
        if (context.hasPendingException()) {
            return rejectAbruptPromise(context, promiseCapability);
        }
        if (!JSTypeChecking.isCallable(promiseResolve)) {
            context.throwTypeError(methodName + " resolve is not callable");
            return rejectAbruptPromise(context, promiseCapability);
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

        JSArray values = context.createJSArray();
        int[] remainingElementsCount = new int[]{1};
        boolean[] iteratorDone = new boolean[]{false};
        boolean[] combinatorSettled = new boolean[]{false};
        int index = 0;

        while (true) {
            JSObject nextResult;
            try {
                nextResult = JSIteratorHelper.iteratorNext(iteratorObject, context);
            } catch (JSException e) {
                if (!context.hasPendingException()) {
                    context.setPendingException(e.getErrorValue());
                }
                iteratorDone[0] = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone[0]);
            } catch (JSVirtualMachineException e) {
                if (!context.hasPendingException()) {
                    if (e.getJsValue() != null) {
                        context.setPendingException(e.getJsValue());
                    } else if (e.getJsError() != null) {
                        context.setPendingException(e.getJsError());
                    } else {
                        context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                    }
                }
                iteratorDone[0] = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone[0]);
            } catch (Exception e) {
                if (!context.hasPendingException()) {
                    context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                }
                iteratorDone[0] = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone[0]);
            }
            if (context.hasPendingException()) {
                iteratorDone[0] = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone[0]);
            }
            if (nextResult == null) {
                context.throwTypeError("iterator result is not an object");
                iteratorDone[0] = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone[0]);
            }

            JSValue doneValue = callGet(context, nextResult, PropertyKey.DONE);
            if (context.hasPendingException()) {
                iteratorDone[0] = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone[0]);
            }
            boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
            if (done) {
                iteratorDone[0] = true;
                remainingElementsCount[0]--;
                if (remainingElementsCount[0] == 0) {
                    return finalizePromiseCombinator(context, promiseCapability, mode, values, combinatorSettled);
                }
                return promiseCapability.promise();
            }

            JSValue nextValue = callGet(context, nextResult, PropertyKey.VALUE);
            if (context.hasPendingException()) {
                iteratorDone[0] = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone[0]);
            }

            values.set(index, JSUndefined.INSTANCE);
            if (context.hasPendingException()) {
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, false);
            }

            JSValue nextPromise = callCallable(context, promiseResolve, thisArg, new JSValue[]{nextValue});
            if (context.hasPendingException()) {
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, false);
            }

            JSValue thenMethod = callGet(context, nextPromise, PropertyKey.THEN);
            if (context.hasPendingException()) {
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, false);
            }
            if (!JSTypeChecking.isCallable(thenMethod)) {
                context.throwTypeError(methodName + " resolve result then is not callable");
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, false);
            }

            int currentIndex = index;
            boolean[] alreadyCalled = new boolean[]{false};
            JSValue resolveHandler = mode == PromiseCombinatorMode.ANY
                    ? promiseCapability.resolve()
                    : createBuiltinFunction(context, "", 1,
                    (childContext, thisValue, functionArgs) -> {
                        if (alreadyCalled[0]) {
                            return JSUndefined.INSTANCE;
                        }
                        alreadyCalled[0] = true;
                        JSValue settledValue = functionArgs.length > 0 ? functionArgs[0] : JSUndefined.INSTANCE;
                        switch (mode) {
                            case ALL -> {
                                values.set(currentIndex, settledValue);
                                if (childContext.hasPendingException()) {
                                    return JSUndefined.INSTANCE;
                                }
                                remainingElementsCount[0]--;
                                if (remainingElementsCount[0] == 0) {
                                    finalizePromiseCombinator(childContext, promiseCapability, mode, values, combinatorSettled);
                                }
                            }
                            case ALL_SETTLED -> {
                                JSObject resultObject = childContext.createJSObject();
                                resultObject.set(PropertyKey.STATUS, new JSString("fulfilled"));
                                resultObject.set(PropertyKey.VALUE, settledValue);
                                values.set(currentIndex, resultObject);
                                if (childContext.hasPendingException()) {
                                    return JSUndefined.INSTANCE;
                                }
                                remainingElementsCount[0]--;
                                if (remainingElementsCount[0] == 0) {
                                    finalizePromiseCombinator(childContext, promiseCapability, mode, values, combinatorSettled);
                                }
                            }
                            case ANY -> {
                                combinatorSettled[0] = true;
                                callCallable(childContext, promiseCapability.resolve(), JSUndefined.INSTANCE, new JSValue[]{settledValue});
                                if (childContext.hasPendingException()) {
                                    combinatorSettled[0] = false;
                                    rejectAbruptPromise(childContext, promiseCapability);
                                }
                            }
                        }
                        return JSUndefined.INSTANCE;
                    });

            JSNativeFunction rejectElement = switch (mode) {
                case ALL -> null;
                case ALL_SETTLED -> createBuiltinFunction(context, "", 1,
                        (childContext, thisValue, functionArgs) -> {
                            if (alreadyCalled[0]) {
                                return JSUndefined.INSTANCE;
                            }
                            alreadyCalled[0] = true;
                            JSValue rejectionReason = functionArgs.length > 0 ? functionArgs[0] : JSUndefined.INSTANCE;
                            JSObject resultObject = childContext.createJSObject();
                            resultObject.set(PropertyKey.STATUS, new JSString("rejected"));
                            resultObject.set(PropertyKey.REASON, rejectionReason);
                            values.set(currentIndex, resultObject);
                            if (childContext.hasPendingException()) {
                                return JSUndefined.INSTANCE;
                            }
                            remainingElementsCount[0]--;
                            if (remainingElementsCount[0] == 0) {
                                finalizePromiseCombinator(childContext, promiseCapability, mode, values, combinatorSettled);
                            }
                            return JSUndefined.INSTANCE;
                        });
                case ANY -> createBuiltinFunction(context, "", 1,
                        (childContext, thisValue, functionArgs) -> {
                            if (alreadyCalled[0]) {
                                return JSUndefined.INSTANCE;
                            }
                            alreadyCalled[0] = true;
                            JSValue rejectionReason = functionArgs.length > 0 ? functionArgs[0] : JSUndefined.INSTANCE;
                            values.set(currentIndex, rejectionReason);
                            if (childContext.hasPendingException()) {
                                return JSUndefined.INSTANCE;
                            }
                            remainingElementsCount[0]--;
                            if (remainingElementsCount[0] == 0 && !combinatorSettled[0]) {
                                combinatorSettled[0] = true;
                                callCallable(childContext, promiseCapability.reject(), JSUndefined.INSTANCE,
                                        new JSValue[]{JSAggregateError.create(childContext, values)});
                                if (childContext.hasPendingException()) {
                                    combinatorSettled[0] = false;
                                    rejectAbruptPromise(childContext, promiseCapability);
                                }
                            }
                            return JSUndefined.INSTANCE;
                        });
            };

            remainingElementsCount[0]++;
            JSValue rejectHandler = mode == PromiseCombinatorMode.ALL ? promiseCapability.reject() : rejectElement;
            callCallable(context, thenMethod, nextPromise, new JSValue[]{resolveHandler, rejectHandler});
            if (context.hasPendingException()) {
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, false);
            }
            index++;
        }
    }

    /**
     * Promise.race(iterable)
     */
    public static JSValue race(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!JSTypeChecking.isConstructor(thisArg)) {
            return context.throwTypeError("Promise.race called on non-constructor");
        }

        PromiseCapability promiseCapability = newPromiseCapability(context, thisArg);
        if (promiseCapability == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue promiseResolve = callGet(context, thisArg, PropertyKey.RESOLVE);
        if (context.hasPendingException()) {
            return rejectAbruptPromise(context, promiseCapability);
        }
        if (!JSTypeChecking.isCallable(promiseResolve)) {
            context.throwTypeError("Promise.race resolve is not callable");
            return rejectAbruptPromise(context, promiseCapability);
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

        boolean iteratorDone = false;
        while (true) {
            JSObject nextResult;
            try {
                nextResult = JSIteratorHelper.iteratorNext(iteratorObject, context);
            } catch (JSException e) {
                if (!context.hasPendingException()) {
                    context.setPendingException(e.getErrorValue());
                }
                iteratorDone = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone);
            } catch (JSVirtualMachineException e) {
                if (!context.hasPendingException()) {
                    if (e.getJsValue() != null) {
                        context.setPendingException(e.getJsValue());
                    } else if (e.getJsError() != null) {
                        context.setPendingException(e.getJsError());
                    } else {
                        context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                    }
                }
                iteratorDone = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone);
            } catch (Exception e) {
                if (!context.hasPendingException()) {
                    context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
                }
                iteratorDone = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone);
            }
            if (context.hasPendingException()) {
                iteratorDone = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone);
            }
            if (nextResult == null) {
                context.throwTypeError("iterator result is not an object");
                iteratorDone = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone);
            }

            JSValue doneValue = callGet(context, nextResult, PropertyKey.DONE);
            if (context.hasPendingException()) {
                iteratorDone = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone);
            }
            boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
            if (done) {
                return promiseCapability.promise();
            }

            JSValue nextValue = callGet(context, nextResult, PropertyKey.VALUE);
            if (context.hasPendingException()) {
                iteratorDone = true;
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, iteratorDone);
            }

            JSValue nextPromise = callCallable(context, promiseResolve, thisArg, new JSValue[]{nextValue});
            if (context.hasPendingException()) {
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, false);
            }

            JSValue thenMethod = callGet(context, nextPromise, PropertyKey.THEN);
            if (context.hasPendingException()) {
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, false);
            }
            if (!JSTypeChecking.isCallable(thenMethod)) {
                context.throwTypeError("Promise.race resolve result then is not callable");
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, false);
            }

            callCallable(context, thenMethod, nextPromise,
                    new JSValue[]{promiseCapability.resolve(), promiseCapability.reject()});
            if (context.hasPendingException()) {
                return rejectAbruptPromiseMaybeClose(context, iteratorObject, promiseCapability, false);
            }
        }
    }

    /**
     * Promise.reject(reason)
     */
    public static JSValue reject(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!JSTypeChecking.isConstructor(thisArg)) {
            return context.throwTypeError("Promise.reject called on non-constructor");
        }
        JSValue reason = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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

    private static JSValue rejectAbruptPromiseMaybeClose(
            JSContext context,
            JSObject iteratorObject,
            PromiseCapability promiseCapability,
            boolean iteratorDone) {
        if (iteratorDone) {
            return rejectAbruptPromise(context, promiseCapability);
        }
        return closeIteratorAndRejectAbruptPromise(context, iteratorObject, promiseCapability);
    }

    /**
     * Promise.resolve(value)
     */
    public static JSValue resolve(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!JSTypeChecking.isConstructor(thisArg)) {
            return context.throwTypeError("Promise.resolve called on non-constructor");
        }
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

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

    private static JSValue performPromiseKeyedCombinator(
            JSContext context,
            JSValue thisArg,
            JSValue[] args,
            PromiseCombinatorMode mode) {
        String methodName = mode == PromiseCombinatorMode.ALL ? "Promise.allKeyed" : "Promise.allSettledKeyed";
        if (!JSTypeChecking.isConstructor(thisArg)) {
            return context.throwTypeError(methodName + " called on non-constructor");
        }

        PromiseCapability promiseCapability = newPromiseCapability(context, thisArg);
        if (promiseCapability == null) {
            return JSUndefined.INSTANCE;
        }

        JSValue promiseResolve = callGet(context, thisArg, PropertyKey.RESOLVE);
        if (context.hasPendingException()) {
            return rejectAbruptPromise(context, promiseCapability);
        }
        if (!JSTypeChecking.isCallable(promiseResolve)) {
            context.throwTypeError(methodName + " resolve is not callable");
            return rejectAbruptPromise(context, promiseCapability);
        }

        JSValue dictionary = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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

            String capturedKey = key;
            JSNativeFunction onFulfilled;
            if (mode == PromiseCombinatorMode.ALL) {
                onFulfilled = new JSNativeFunction(context, "", 1, (ctx, ta, a) -> {
                    JSValue resolvedValue = a.length > 0 ? a[0] : JSUndefined.INSTANCE;
                    resultObject.set(PropertyKey.fromString(capturedKey), resolvedValue);
                    remainingCount[0]--;
                    if (remainingCount[0] == 0) {
                        callCallable(ctx, promiseCapability.resolve(), JSUndefined.INSTANCE, new JSValue[]{resultObject});
                    }
                    return JSUndefined.INSTANCE;
                });
            } else {
                onFulfilled = new JSNativeFunction(context, "", 1, (ctx, ta, a) -> {
                    JSValue resolvedValue = a.length > 0 ? a[0] : JSUndefined.INSTANCE;
                    JSObject settledResult = ctx.createJSObject();
                    settledResult.set(PropertyKey.fromString("status"), new JSString("fulfilled"));
                    settledResult.set(PropertyKey.fromString("value"), resolvedValue);
                    resultObject.set(PropertyKey.fromString(capturedKey), settledResult);
                    remainingCount[0]--;
                    if (remainingCount[0] == 0) {
                        callCallable(ctx, promiseCapability.resolve(), JSUndefined.INSTANCE, new JSValue[]{resultObject});
                    }
                    return JSUndefined.INSTANCE;
                });
            }

            JSNativeFunction onRejected;
            if (mode == PromiseCombinatorMode.ALL) {
                onRejected = new JSNativeFunction(context, "", 1, (ctx, ta, a) -> {
                    JSValue reason = a.length > 0 ? a[0] : JSUndefined.INSTANCE;
                    callCallable(ctx, promiseCapability.reject(), JSUndefined.INSTANCE, new JSValue[]{reason});
                    return JSUndefined.INSTANCE;
                });
            } else {
                onRejected = new JSNativeFunction(context, "", 1, (ctx, ta, a) -> {
                    JSValue reason = a.length > 0 ? a[0] : JSUndefined.INSTANCE;
                    JSObject settledResult = ctx.createJSObject();
                    settledResult.set(PropertyKey.fromString("status"), new JSString("rejected"));
                    settledResult.set(PropertyKey.fromString("reason"), reason);
                    resultObject.set(PropertyKey.fromString(capturedKey), settledResult);
                    remainingCount[0]--;
                    if (remainingCount[0] == 0) {
                        callCallable(ctx, promiseCapability.resolve(), JSUndefined.INSTANCE, new JSValue[]{resultObject});
                    }
                    return JSUndefined.INSTANCE;
                });
            }

            context.transferPrototype(onFulfilled, JSFunction.NAME);
            context.transferPrototype(onRejected, JSFunction.NAME);
            JSValue thenFn = callGet(context, nextPromise, PropertyKey.fromString("then"));
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

    private enum PromiseCombinatorMode {
        ALL,
        ALL_SETTLED,
        ANY
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
}
