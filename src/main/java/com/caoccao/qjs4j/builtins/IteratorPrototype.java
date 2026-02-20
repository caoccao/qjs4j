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

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of iterator-related prototype methods.
 * Based on ES2020 iteration protocols.
 */
public final class IteratorPrototype {
    private static final JSValue[] NO_ARGS = new JSValue[0];

    private IteratorPrototype() {
    }

    /**
     * Array.prototype.entries()
     * Returns an iterator of [index, value] pairs.
     */
    public static JSValue arrayEntries(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject arrayLike = JSTypeConversions.toObject(context, thisArg);
        if (arrayLike == null) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        final long[] index = {0};
        return new JSIterator(context, () -> {
            // ES2024 %ArrayIteratorPrototype%.next step 5: TypedArray out-of-bounds check
            if (arrayLike instanceof JSTypedArray ta && ta.isOutOfBounds()) {
                context.throwTypeError("Cannot perform Array Iterator.prototype.next on a typed array backed by a detached or out-of-bounds buffer");
                return JSIterator.IteratorResult.done(context);
            }
            if (index[0] < getArrayLikeLength(context, arrayLike)) {
                JSArray pair = context.createJSArray();
                pair.push(JSNumber.of(index[0]));
                pair.push(getArrayLikeValue(context, arrayLike, index[0]));
                index[0]++;
                return JSIterator.IteratorResult.of(context, pair);
            }
            return JSIterator.IteratorResult.done(context);
        }, "Array Iterator", false);
    }

    /**
     * Array.prototype.keys()
     * Returns an iterator of array indices.
     */
    public static JSValue arrayKeys(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject arrayLike = JSTypeConversions.toObject(context, thisArg);
        if (arrayLike == null) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        final long[] index = {0};
        return new JSIterator(context, () -> {
            if (arrayLike instanceof JSTypedArray ta && ta.isOutOfBounds()) {
                context.throwTypeError("Cannot perform Array Iterator.prototype.next on a typed array backed by a detached or out-of-bounds buffer");
                return JSIterator.IteratorResult.done(context);
            }
            if (index[0] < getArrayLikeLength(context, arrayLike)) {
                return JSIterator.IteratorResult.of(context, JSNumber.of(index[0]++));
            }
            return JSIterator.IteratorResult.done(context);
        }, "Array Iterator", false);
    }

    /**
     * Array.prototype.values()
     * Returns an iterator of array values.
     */
    public static JSValue arrayValues(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject arrayLike = JSTypeConversions.toObject(context, thisArg);
        if (arrayLike == null) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }

        final long[] index = {0};
        return new JSIterator(context, () -> {
            if (arrayLike instanceof JSTypedArray ta && ta.isOutOfBounds()) {
                context.throwTypeError("Cannot perform Array Iterator.prototype.next on a typed array backed by a detached or out-of-bounds buffer");
                return JSIterator.IteratorResult.done(context);
            }
            if (index[0] < getArrayLikeLength(context, arrayLike)) {
                return JSIterator.IteratorResult.of(context, getArrayLikeValue(context, arrayLike, index[0]++));
            }
            return JSIterator.IteratorResult.done(context);
        }, "Array Iterator", false);
    }

    private static JSValue closeIterator(JSContext context, JSObject iteratorObject) {
        JSValue returnMethod = iteratorObject.get(context, PropertyKey.RETURN);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
            return JSUndefined.INSTANCE;
        }
        if (!(returnMethod instanceof JSFunction returnFunction)) {
            return context.throwTypeError("not a function");
        }
        JSValue result = returnFunction.call(context, iteratorObject, NO_ARGS);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        return result;
    }

    private static void closeIteratorIgnoringResult(JSContext context, JSObject iteratorObject) {
        JSValue pendingException = context.getPendingException();
        closeIterator(context, iteratorObject);
        if (pendingException != null) {
            context.setPendingException(pendingException);
        }
    }

    /**
     * Iterator.concat(...iterables)
     * Creates an iterator that concatenates the provided iterables.
     */
    public static JSValue concat(JSContext context, JSValue thisArg, JSValue[] args) {
        List<ConcatSource> sources = new ArrayList<>(args.length);
        for (JSValue arg : args) {
            if (!(arg instanceof JSObject sourceObject)) {
                return context.throwTypeError("not an object");
            }
            JSValue iteratorMethod = sourceObject.get(context, PropertyKey.SYMBOL_ITERATOR);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!(iteratorMethod instanceof JSFunction iteratorFunction)) {
                return context.throwTypeError("not a function");
            }
            sources.add(new ConcatSource(sourceObject, iteratorFunction));
        }

        final int[] sourceIndex = {0};
        final boolean[] done = {false};
        final boolean[] running = {false};
        final JSObject[] currentIterator = {null};
        final JSValue[] currentNextMethod = {JSUndefined.INSTANCE};

        JSNativeFunction nextFunction = new JSNativeFunction("next", 0, (childContext, childThisArg, childArgs) -> {
            if (running[0]) {
                return childContext.throwTypeError("already running");
            }
            if (done[0]) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }
            running[0] = true;
            try {
                while (sourceIndex[0] < sources.size()) {
                    if (currentIterator[0] == null) {
                        ConcatSource source = sources.get(sourceIndex[0]);
                        JSValue iteratorValue = source.iteratorMethod().call(childContext, source.sourceObject(), NO_ARGS);
                        if (childContext.hasPendingException()) {
                            done[0] = true;
                            return childContext.getPendingException();
                        }
                        if (!(iteratorValue instanceof JSObject iteratorObject)) {
                            done[0] = true;
                            return childContext.throwTypeError("not an object");
                        }
                        currentIterator[0] = iteratorObject;
                        currentNextMethod[0] = iteratorObject.get(childContext, PropertyKey.NEXT);
                        if (childContext.hasPendingException()) {
                            done[0] = true;
                            return childContext.getPendingException();
                        }
                    }

                    IteratorStep step = iteratorStep(childContext, currentIterator[0], currentNextMethod[0]);
                    if (step == null) {
                        done[0] = true;
                        return childContext.getPendingException();
                    }
                    if (!step.done()) {
                        return iteratorResult(childContext, step.value(), false);
                    }
                    currentIterator[0] = null;
                    currentNextMethod[0] = JSUndefined.INSTANCE;
                    sourceIndex[0]++;
                }
                done[0] = true;
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            } finally {
                running[0] = false;
            }
        });

        JSNativeFunction returnFunction = new JSNativeFunction("return", 0, (childContext, childThisArg, childArgs) -> {
            if (running[0]) {
                return childContext.throwTypeError("already running");
            }
            done[0] = true;
            sourceIndex[0] = sources.size();
            if (currentIterator[0] == null) {
                return JSUndefined.INSTANCE;
            }
            JSValue returnMethod = currentIterator[0].get(childContext, PropertyKey.RETURN);
            currentNextMethod[0] = JSUndefined.INSTANCE;
            JSObject iter = currentIterator[0];
            currentIterator[0] = null;
            if (childContext.hasPendingException()) {
                return childContext.getPendingException();
            }
            if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
                return JSUndefined.INSTANCE;
            }
            if (!(returnMethod instanceof JSFunction returnFunctionValue)) {
                return childContext.throwTypeError("not a function");
            }
            JSValue result = returnFunctionValue.call(childContext, iter, NO_ARGS);
            if (childContext.hasPendingException()) {
                return childContext.getPendingException();
            }
            return result;
        });

        return createIteratorObject(context, nextFunction, returnFunction, "Iterator Concat");
    }

    private static JSNativeFunction createHelperReturnFunction(JSObject iteratorObject, boolean[] running, boolean[] done) {
        return new JSNativeFunction("return", 0, (childContext, childThisArg, childArgs) -> {
            if (running[0]) {
                return childContext.throwTypeError("cannot invoke a running iterator");
            }
            done[0] = true;
            JSValue returnMethod = iteratorObject.get(childContext, PropertyKey.RETURN);
            if (childContext.hasPendingException()) {
                return childContext.getPendingException();
            }
            if (!(returnMethod instanceof JSFunction returnFunctionValue)) {
                return childContext.throwTypeError("not a function");
            }
            JSValue result = returnFunctionValue.call(childContext, iteratorObject, NO_ARGS);
            if (childContext.hasPendingException()) {
                return childContext.getPendingException();
            }
            if (result instanceof JSObject resultObject) {
                return resultObject;
            }
            return iteratorResult(childContext, result, true);
        });
    }

    private static JSObject createIteratorObject(
            JSContext context,
            JSNativeFunction nextFunction,
            JSNativeFunction returnFunction,
            String toStringTag) {
        JSObject iteratorObject = context.createJSObject();
        context.transferPrototype(iteratorObject, JSIterator.NAME);
        iteratorObject.definePropertyWritableConfigurable("next", nextFunction);
        if (returnFunction != null) {
            iteratorObject.definePropertyWritableConfigurable("return", returnFunction);
        }
        if (toStringTag != null) {
            iteratorObject.defineProperty(
                    PropertyKey.SYMBOL_TO_STRING_TAG,
                    PropertyDescriptor.dataDescriptor(new JSString(toStringTag), false, false, true));
        }
        return iteratorObject;
    }

    private static JSValue createIteratorWrap(JSContext context, JSObject wrappedIterator, JSValue wrappedNextMethod) {
        final boolean[] done = {false};

        JSNativeFunction nextFunction = new JSNativeFunction("next", 0, (childContext, childThisArg, childArgs) -> {
            if (done[0]) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }
            IteratorStep step = iteratorStep(childContext, wrappedIterator, wrappedNextMethod);
            if (step == null) {
                done[0] = true;
                return childContext.getPendingException();
            }
            if (step.done()) {
                done[0] = true;
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }
            return iteratorResult(childContext, step.value(), false);
        });

        JSNativeFunction returnFunction = new JSNativeFunction("return", 0, (childContext, childThisArg, childArgs) -> {
            done[0] = true;
            JSValue returnMethod = wrappedIterator.get(childContext, PropertyKey.RETURN);
            if (childContext.hasPendingException()) {
                return childContext.getPendingException();
            }
            if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }
            IteratorStep step = iteratorStep(childContext, wrappedIterator, returnMethod);
            if (step == null) {
                return childContext.getPendingException();
            }
            return iteratorResult(childContext, step.value(), step.done());
        });

        return createIteratorObject(context, nextFunction, returnFunction, null);
    }

    /**
     * Iterator.prototype.drop(limit)
     * Returns an iterator that skips the first limit elements.
     */
    public static JSValue drop(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        Long limit = toPositiveLimit(context, args);
        if (limit == null) {
            closeIteratorIgnoringResult(context, iteratorObject);
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        final long[] remaining = {limit};
        final boolean[] done = {false};
        final boolean[] running = {false};

        JSNativeFunction nextFunction = new JSNativeFunction("next", 0, (childContext, childThisArg, childArgs) -> {
            if (running[0]) {
                return childContext.throwTypeError("cannot invoke a running iterator");
            }
            if (done[0]) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }
            running[0] = true;
            try {
                while (remaining[0] > 0) {
                    IteratorStep step = iteratorStep(childContext, iteratorObject, nextMethod);
                    if (step == null) {
                        done[0] = true;
                        return childContext.getPendingException();
                    }
                    if (step.done()) {
                        done[0] = true;
                        return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                    }
                    remaining[0]--;
                }
                IteratorStep step = iteratorStep(childContext, iteratorObject, nextMethod);
                if (step == null) {
                    done[0] = true;
                    return childContext.getPendingException();
                }
                if (step.done()) {
                    done[0] = true;
                    return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                }
                return iteratorResult(childContext, step.value(), false);
            } finally {
                running[0] = false;
            }
        });

        JSNativeFunction returnFunction = createHelperReturnFunction(iteratorObject, running, done);
        return createIteratorObject(context, nextFunction, returnFunction, "Iterator Helper");
    }

    /**
     * Iterator.prototype.every(predicate)
     * Tests whether all elements satisfy the predicate.
     */
    public static JSValue every(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSFunction predicate = requireFunction(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (predicate == null) {
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        long index = 0;
        while (true) {
            IteratorStep step = iteratorStep(context, iteratorObject, nextMethod);
            if (step == null) {
                return context.getPendingException();
            }
            if (step.done()) {
                return JSBoolean.TRUE;
            }
            JSValue result = predicate.call(context, JSUndefined.INSTANCE, new JSValue[]{step.value(), JSNumber.of(index++)});
            if (context.hasPendingException()) {
                closeIteratorIgnoringResult(context, iteratorObject);
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(result) == JSBoolean.FALSE) {
                JSValue closeResult = closeIterator(context, iteratorObject);
                if (closeResult != JSUndefined.INSTANCE && context.hasPendingException()) {
                    return closeResult;
                }
                return JSBoolean.FALSE;
            }
        }
    }

    /**
     * Iterator.prototype.filter(predicate)
     * Returns an iterator of elements that satisfy the predicate.
     */
    public static JSValue filter(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSFunction predicate = requireFunction(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (predicate == null) {
            closeIteratorIgnoringResult(context, iteratorObject);
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        final long[] index = {0};
        final boolean[] done = {false};
        final boolean[] running = {false};

        JSNativeFunction nextFunction = new JSNativeFunction("next", 0, (childContext, childThisArg, childArgs) -> {
            if (running[0]) {
                return childContext.throwTypeError("cannot invoke a running iterator");
            }
            if (done[0]) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }
            running[0] = true;
            try {
                while (true) {
                    IteratorStep step = iteratorStep(childContext, iteratorObject, nextMethod);
                    if (step == null) {
                        done[0] = true;
                        return childContext.getPendingException();
                    }
                    if (step.done()) {
                        done[0] = true;
                        return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                    }
                    JSValue selected = predicate.call(
                            childContext,
                            JSUndefined.INSTANCE,
                            new JSValue[]{step.value(), JSNumber.of(index[0]++)});
                    if (childContext.hasPendingException()) {
                        done[0] = true;
                        closeIteratorIgnoringResult(childContext, iteratorObject);
                        return childContext.getPendingException();
                    }
                    if (JSTypeConversions.toBoolean(selected) == JSBoolean.TRUE) {
                        return iteratorResult(childContext, step.value(), false);
                    }
                }
            } finally {
                running[0] = false;
            }
        });

        JSNativeFunction returnFunction = createHelperReturnFunction(iteratorObject, running, done);
        return createIteratorObject(context, nextFunction, returnFunction, "Iterator Helper");
    }

    /**
     * Iterator.prototype.find(predicate)
     * Returns the first element that satisfies the predicate.
     */
    public static JSValue find(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSFunction predicate = requireFunction(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (predicate == null) {
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        long index = 0;
        while (true) {
            IteratorStep step = iteratorStep(context, iteratorObject, nextMethod);
            if (step == null) {
                return context.getPendingException();
            }
            if (step.done()) {
                return JSUndefined.INSTANCE;
            }
            JSValue selected = predicate.call(context, JSUndefined.INSTANCE, new JSValue[]{step.value(), JSNumber.of(index++)});
            if (context.hasPendingException()) {
                closeIteratorIgnoringResult(context, iteratorObject);
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(selected) == JSBoolean.TRUE) {
                JSValue closeResult = closeIterator(context, iteratorObject);
                if (closeResult != JSUndefined.INSTANCE && context.hasPendingException()) {
                    return closeResult;
                }
                return step.value();
            }
        }
    }

    /**
     * Iterator.prototype.flatMap(mapper)
     * Returns an iterator that applies mapper and flattens the result.
     */
    public static JSValue flatMap(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSFunction mapper = requireFunction(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (mapper == null) {
            closeIteratorIgnoringResult(context, iteratorObject);
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        final long[] index = {0};
        final boolean[] done = {false};
        final boolean[] running = {false};
        final JSObject[] innerIterator = {null};
        final JSValue[] innerNextMethod = {JSUndefined.INSTANCE};

        JSNativeFunction nextFunction = new JSNativeFunction("next", 0, (childContext, childThisArg, childArgs) -> {
            if (running[0]) {
                return childContext.throwTypeError("cannot invoke a running iterator");
            }
            if (done[0]) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }
            running[0] = true;
            try {
                while (true) {
                    if (innerIterator[0] != null) {
                        IteratorStep innerStep = iteratorStep(childContext, innerIterator[0], innerNextMethod[0]);
                        if (innerStep == null) {
                            done[0] = true;
                            closeIteratorIgnoringResult(childContext, innerIterator[0]);
                            innerIterator[0] = null;
                            closeIteratorIgnoringResult(childContext, iteratorObject);
                            return childContext.getPendingException();
                        }
                        if (!innerStep.done()) {
                            return iteratorResult(childContext, innerStep.value(), false);
                        }
                        closeIteratorIgnoringResult(childContext, innerIterator[0]);
                        innerIterator[0] = null;
                        innerNextMethod[0] = JSUndefined.INSTANCE;
                        continue;
                    }

                    IteratorStep step = iteratorStep(childContext, iteratorObject, nextMethod);
                    if (step == null) {
                        done[0] = true;
                        return childContext.getPendingException();
                    }
                    if (step.done()) {
                        done[0] = true;
                        return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                    }

                    JSValue mapped = mapper.call(
                            childContext,
                            JSUndefined.INSTANCE,
                            new JSValue[]{step.value(), JSNumber.of(index[0]++)});
                    if (childContext.hasPendingException()) {
                        done[0] = true;
                        closeIteratorIgnoringResult(childContext, iteratorObject);
                        return childContext.getPendingException();
                    }
                    if (!(mapped instanceof JSObject mappedObject)) {
                        done[0] = true;
                        closeIteratorIgnoringResult(childContext, iteratorObject);
                        return childContext.throwTypeError("not an object");
                    }

                    JSValue iteratorMethod = mappedObject.get(childContext, PropertyKey.SYMBOL_ITERATOR);
                    if (childContext.hasPendingException()) {
                        done[0] = true;
                        closeIteratorIgnoringResult(childContext, iteratorObject);
                        return childContext.getPendingException();
                    }

                    JSObject mappedIteratorObject = mappedObject;
                    if (!(iteratorMethod instanceof JSUndefined) && !(iteratorMethod instanceof JSNull)) {
                        if (!(iteratorMethod instanceof JSFunction iteratorFunction)) {
                            done[0] = true;
                            closeIteratorIgnoringResult(childContext, iteratorObject);
                            return childContext.throwTypeError("not a function");
                        }
                        JSValue mappedIteratorValue = iteratorFunction.call(childContext, mappedObject, NO_ARGS);
                        if (childContext.hasPendingException()) {
                            done[0] = true;
                            closeIteratorIgnoringResult(childContext, iteratorObject);
                            return childContext.getPendingException();
                        }
                        if (!(mappedIteratorValue instanceof JSObject jsObject)) {
                            done[0] = true;
                            closeIteratorIgnoringResult(childContext, iteratorObject);
                            return childContext.throwTypeError("not an object");
                        }
                        mappedIteratorObject = jsObject;
                    }

                    innerIterator[0] = mappedIteratorObject;
                    innerNextMethod[0] = mappedIteratorObject.get(childContext, PropertyKey.NEXT);
                    if (childContext.hasPendingException()) {
                        done[0] = true;
                        closeIteratorIgnoringResult(childContext, iteratorObject);
                        return childContext.getPendingException();
                    }
                }
            } finally {
                running[0] = false;
            }
        });

        JSNativeFunction returnFunction = new JSNativeFunction("return", 0, (childContext, childThisArg, childArgs) -> {
            if (running[0]) {
                return childContext.throwTypeError("cannot invoke a running iterator");
            }
            done[0] = true;
            if (innerIterator[0] != null) {
                closeIteratorIgnoringResult(childContext, innerIterator[0]);
                innerIterator[0] = null;
                innerNextMethod[0] = JSUndefined.INSTANCE;
            }
            JSValue returnMethod = iteratorObject.get(childContext, PropertyKey.RETURN);
            if (childContext.hasPendingException()) {
                return childContext.getPendingException();
            }
            if (!(returnMethod instanceof JSFunction returnFunctionValue)) {
                return childContext.throwTypeError("not a function");
            }
            JSValue result = returnFunctionValue.call(childContext, iteratorObject, NO_ARGS);
            if (childContext.hasPendingException()) {
                return childContext.getPendingException();
            }
            if (result instanceof JSObject resultObject) {
                return resultObject;
            }
            return iteratorResult(childContext, result, true);
        });

        return createIteratorObject(context, nextFunction, returnFunction, "Iterator Helper");
    }

    // Iterator helper methods (ES2024) - Placeholders for now

    /**
     * Iterator.prototype.forEach(fn)
     * Calls fn for each element.
     */
    public static JSValue forEach(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSFunction callback = requireFunction(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (callback == null) {
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        long index = 0;
        while (true) {
            IteratorStep step = iteratorStep(context, iteratorObject, nextMethod);
            if (step == null) {
                return context.getPendingException();
            }
            if (step.done()) {
                return JSUndefined.INSTANCE;
            }
            callback.call(context, JSUndefined.INSTANCE, new JSValue[]{step.value(), JSNumber.of(index++)});
            if (context.hasPendingException()) {
                closeIteratorIgnoringResult(context, iteratorObject);
                return context.getPendingException();
            }
        }
    }

    /**
     * Iterator.from(object)
     * Creates an iterator from an iterable object.
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue sourceValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (sourceValue instanceof JSString jsString) {
            return JSIterator.stringIterator(context, jsString);
        }
        if (!(sourceValue instanceof JSObject sourceObject)) {
            return context.throwTypeError("Iterator.from called on non-object");
        }

        JSObject iteratorObject = sourceObject;
        JSValue iteratorMethod = sourceObject.get(context, PropertyKey.SYMBOL_ITERATOR);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(iteratorMethod instanceof JSUndefined) && !(iteratorMethod instanceof JSNull)) {
            if (!(iteratorMethod instanceof JSFunction iteratorFunction)) {
                return context.throwTypeError("not a function");
            }
            JSValue iterValue = iteratorFunction.call(context, sourceObject, NO_ARGS);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!(iterValue instanceof JSObject jsObject)) {
                return context.throwTypeError("not an object");
            }
            iteratorObject = jsObject;
        }

        JSValue nextMethod = iteratorObject.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        if (isIteratorInstance(context, iteratorObject)) {
            return iteratorObject;
        }

        return createIteratorWrap(context, iteratorObject, nextMethod);
    }

    private static long getArrayLikeLength(JSContext context, JSObject arrayLike) {
        return JSTypeConversions.toLength(context, arrayLike.get(context, PropertyKey.LENGTH));
    }

    private static JSValue getArrayLikeValue(JSContext context, JSObject arrayLike, long index) {
        if (index <= Integer.MAX_VALUE) {
            return arrayLike.get(context, PropertyKey.fromIndex((int) index));
        }
        return arrayLike.get(context, PropertyKey.fromString(Long.toString(index)));
    }

    private static boolean isIteratorInstance(JSContext context, JSObject object) {
        JSValue iteratorConstructorValue = context.getGlobalObject().get(context, PropertyKey.ITERATOR_CAP);
        if (context.hasPendingException() || !(iteratorConstructorValue instanceof JSObject iteratorConstructorObject)) {
            return false;
        }
        JSValue iteratorPrototypeValue = iteratorConstructorObject.get(context, PropertyKey.PROTOTYPE);
        if (context.hasPendingException() || !(iteratorPrototypeValue instanceof JSObject iteratorPrototypeObject)) {
            return false;
        }
        JSObject current = object;
        while (current != null) {
            if (current == iteratorPrototypeObject) {
                return true;
            }
            current = current.getPrototype();
        }
        return false;
    }

    private static JSObject iteratorResult(JSContext context, JSValue value, boolean done) {
        JSObject result = context.createJSObject();
        result.set(PropertyKey.VALUE, value != null ? value : JSUndefined.INSTANCE);
        result.set(PropertyKey.DONE, JSBoolean.valueOf(done));
        return result;
    }

    private static IteratorStep iteratorStep(JSContext context, JSObject iteratorObject, JSValue methodValue) {
        if (!(methodValue instanceof JSFunction function)) {
            context.throwTypeError("not a function");
            return null;
        }
        JSValue resultValue = function.call(context, iteratorObject, NO_ARGS);
        if (context.hasPendingException()) {
            return null;
        }
        if (!(resultValue instanceof JSObject resultObject)) {
            context.throwTypeError("not an object");
            return null;
        }
        JSValue doneValue = resultObject.get(context, PropertyKey.DONE);
        if (context.hasPendingException()) {
            return null;
        }
        JSValue value = resultObject.get(context, PropertyKey.VALUE);
        if (context.hasPendingException()) {
            return null;
        }
        if (value == null) {
            value = JSUndefined.INSTANCE;
        }
        return new IteratorStep(value, JSTypeConversions.toBoolean(doneValue).isBooleanTrue());
    }

    /**
     * Iterator.prototype.map(mapper)
     * Returns an iterator of mapped values.
     */
    public static JSValue map(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSFunction mapper = requireFunction(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (mapper == null) {
            closeIteratorIgnoringResult(context, iteratorObject);
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        final long[] index = {0};
        final boolean[] done = {false};
        final boolean[] running = {false};

        JSNativeFunction nextFunction = new JSNativeFunction("next", 0, (childContext, childThisArg, childArgs) -> {
            if (running[0]) {
                return childContext.throwTypeError("cannot invoke a running iterator");
            }
            if (done[0]) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }
            running[0] = true;
            try {
                IteratorStep step = iteratorStep(childContext, iteratorObject, nextMethod);
                if (step == null) {
                    done[0] = true;
                    return childContext.getPendingException();
                }
                if (step.done()) {
                    done[0] = true;
                    return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                }
                JSValue mapped = mapper.call(
                        childContext,
                        JSUndefined.INSTANCE,
                        new JSValue[]{step.value(), JSNumber.of(index[0]++)});
                if (childContext.hasPendingException()) {
                    done[0] = true;
                    closeIteratorIgnoringResult(childContext, iteratorObject);
                    return childContext.getPendingException();
                }
                return iteratorResult(childContext, mapped, false);
            } finally {
                running[0] = false;
            }
        });

        JSNativeFunction returnFunction = createHelperReturnFunction(iteratorObject, running, done);
        return createIteratorObject(context, nextFunction, returnFunction, "Iterator Helper");
    }

    /**
     * Map.prototype.entries() - returns iterator
     * Returns an iterator of [key, value] pairs.
     */
    public static JSValue mapEntriesIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.entries called on non-Map");
        }

        return JSIterator.mapEntriesIterator(context, map);
    }

    /**
     * Map.prototype.keys() - returns iterator
     * Returns an iterator of keys.
     */
    public static JSValue mapKeysIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.keys called on non-Map");
        }

        return JSIterator.mapKeysIterator(context, map);
    }

    /**
     * Map.prototype.values() - returns iterator
     * Returns an iterator of values.
     */
    public static JSValue mapValuesIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.values called on non-Map");
        }

        return JSIterator.mapValuesIterator(context, map);
    }

    /**
     * Iterator.prototype.next()
     * Returns the next value in the iteration.
     */
    public static JSValue next(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIterator iterator)) {
            return context.throwTypeError("Iterator.prototype.next called on non-iterator");
        }

        return iterator.next();
    }

    /**
     * Iterator.prototype.reduce(reducer, initialValue)
     * Reduces the iterator to a single value.
     */
    public static JSValue reduce(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSFunction reducer = requireFunction(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (reducer == null) {
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        long index;
        JSValue accumulator;
        if (args.length > 1) {
            accumulator = args[1];
            index = 0;
        } else {
            IteratorStep firstStep = iteratorStep(context, iteratorObject, nextMethod);
            if (firstStep == null) {
                return context.getPendingException();
            }
            if (firstStep.done()) {
                return context.throwTypeError("empty iterator");
            }
            accumulator = firstStep.value();
            index = 1;
        }

        while (true) {
            IteratorStep step = iteratorStep(context, iteratorObject, nextMethod);
            if (step == null) {
                return context.getPendingException();
            }
            if (step.done()) {
                return accumulator;
            }
            JSValue reduced = reducer.call(context, JSUndefined.INSTANCE, new JSValue[]{
                    accumulator,
                    step.value(),
                    JSNumber.of(index++),
            });
            if (context.hasPendingException()) {
                closeIteratorIgnoringResult(context, iteratorObject);
                return context.getPendingException();
            }
            accumulator = reduced;
        }
    }

    private static JSFunction requireFunction(JSContext context, JSValue value) {
        if (value instanceof JSFunction function) {
            return function;
        }
        context.throwTypeError("not a function");
        return null;
    }

    private static JSObject requireObject(JSContext context, JSValue value) {
        if (value instanceof JSObject jsObject) {
            return jsObject;
        }
        context.throwTypeError("not an object");
        return null;
    }

    /**
     * Set.prototype.entries() - returns iterator
     * Returns an iterator of [value, value] pairs.
     */
    public static JSValue setEntriesIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.entries called on non-Set");
        }
        return JSIterator.setEntriesIterator(context, set);
    }

    /**
     * Set.prototype.keys() - returns iterator
     * In Set, keys() is the same as values().
     */
    public static JSValue setKeysIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValuesIterator(context, thisArg, args);
    }

    /**
     * Set.prototype.values() - returns iterator
     * Returns an iterator of values.
     */
    public static JSValue setValuesIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.values called on non-Set");
        }

        return JSIterator.setValuesIterator(context, set);
    }

    /**
     * Iterator.prototype.some(predicate)
     * Tests whether any element satisfies the predicate.
     */
    public static JSValue some(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSFunction predicate = requireFunction(context, args.length > 0 ? args[0] : JSUndefined.INSTANCE);
        if (predicate == null) {
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        long index = 0;
        while (true) {
            IteratorStep step = iteratorStep(context, iteratorObject, nextMethod);
            if (step == null) {
                return context.getPendingException();
            }
            if (step.done()) {
                return JSBoolean.FALSE;
            }
            JSValue result = predicate.call(context, JSUndefined.INSTANCE, new JSValue[]{step.value(), JSNumber.of(index++)});
            if (context.hasPendingException()) {
                closeIteratorIgnoringResult(context, iteratorObject);
                return context.getPendingException();
            }
            if (JSTypeConversions.toBoolean(result) == JSBoolean.TRUE) {
                JSValue closeResult = closeIterator(context, iteratorObject);
                if (closeResult != JSUndefined.INSTANCE && context.hasPendingException()) {
                    return closeResult;
                }
                return JSBoolean.TRUE;
            }
        }
    }

    /**
     * String.prototype[Symbol.iterator]()
     * Returns an iterator of string characters.
     */
    public static JSValue stringIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSString str)) {
            // Try to get primitive value for boxed strings
            if (thisArg instanceof JSObject obj) {
                JSValue primitiveValue = obj.getPrimitiveValue();
                if (primitiveValue instanceof JSString boxedStr) {
                    return JSIterator.stringIterator(context, boxedStr);
                }
            }
            return context.throwTypeError("String iterator called on non-string");
        }

        return JSIterator.stringIterator(context, str);
    }

    /**
     * Iterator.prototype.take(limit)
     * Returns an iterator of the first limit elements.
     */
    public static JSValue take(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        Long limit = toPositiveLimit(context, args);
        if (limit == null) {
            closeIteratorIgnoringResult(context, iteratorObject);
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        final long[] remaining = {limit};
        final boolean[] done = {false};
        final boolean[] running = {false};

        JSNativeFunction nextFunction = new JSNativeFunction("next", 0, (childContext, childThisArg, childArgs) -> {
            if (running[0]) {
                return childContext.throwTypeError("cannot invoke a running iterator");
            }
            if (done[0]) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }
            running[0] = true;
            try {
                if (remaining[0] <= 0) {
                    done[0] = true;
                    closeIteratorIgnoringResult(childContext, iteratorObject);
                    return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                }
                remaining[0]--;
                IteratorStep step = iteratorStep(childContext, iteratorObject, nextMethod);
                if (step == null) {
                    done[0] = true;
                    return childContext.getPendingException();
                }
                if (step.done()) {
                    done[0] = true;
                    return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                }
                return iteratorResult(childContext, step.value(), false);
            } finally {
                running[0] = false;
            }
        });

        JSNativeFunction returnFunction = createHelperReturnFunction(iteratorObject, running, done);
        return createIteratorObject(context, nextFunction, returnFunction, "Iterator Helper");
    }

    /**
     * Iterator.prototype.toArray()
     * Converts the iterator to an array.
     */
    public static JSValue toArray(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        JSArray result = context.createJSArray();
        while (true) {
            IteratorStep step = iteratorStep(context, iteratorObject, nextMethod);
            if (step == null) {
                return context.getPendingException();
            }
            if (step.done()) {
                return result;
            }
            result.push(step.value());
        }
    }

    private static Long toPositiveLimit(JSContext context, JSValue[] args) {
        JSValue limitValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        double number = JSTypeConversions.toNumber(context, limitValue).value();
        if (Double.isNaN(number)) {
            context.throwRangeError("must be positive");
            return null;
        }
        if (Double.isInfinite(number)) {
            if (number < 0) {
                context.throwRangeError("must be positive");
                return null;
            }
            return NumberPrototype.MAX_SAFE_INTEGER;
        }
        long limit = (long) JSTypeConversions.toInteger(context, JSNumber.of(number));
        if (limit < 0) {
            context.throwRangeError("must be positive");
            return null;
        }
        return limit;
    }

    private record ConcatSource(JSObject sourceObject, JSFunction iteratorMethod) {
    }

    private record IteratorStep(JSValue value, boolean done) {
    }
}
