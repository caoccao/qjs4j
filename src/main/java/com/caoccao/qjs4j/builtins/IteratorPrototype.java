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
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of iterator-related prototype methods.
 * Based on ES2020 iteration protocols.
 */
public final class IteratorPrototype {
    private static final PropertyKey[] ITERATOR_RESULT_KEYS = {PropertyKey.VALUE, PropertyKey.DONE};
    private static final List<PropertyKey> ITERATOR_RESULT_KEY_LIST = List.of(PropertyKey.VALUE, PropertyKey.DONE);
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

    /**
     * Build the result value for a zip iteration step.
     */
    private static JSValue buildZipResult(
            JSContext context,
            JSValue[] results,
            int iterCount,
            PropertyKey[] keyArray,
            JSValue[] cachedZipKeyStrings) {
        if (keyArray != null) {
            // zipKeyed: create null-prototype object with bulk property initialization
            JSObject resultObject = cachedZipKeyStrings != null
                    ? new JSZipKeyedResultObject(keyArray, cachedZipKeyStrings)
                    : new JSObject();
            PropertyKey[] keys = keyArray.clone();
            PropertyDescriptor[] descriptors = new PropertyDescriptor[iterCount];
            JSValue[] values = new JSValue[iterCount];
            for (int i = 0; i < iterCount; i++) {
                descriptors[i] = PropertyDescriptor.defaultData(results[i]);
                values[i] = results[i];
            }
            resultObject.initProperties(keys, descriptors, values);
            return resultObject;
        } else {
            // zip: create packed array
            return context.createJSArray(Arrays.copyOf(results, iterCount), true);
        }
    }

    /**
     * Safely call a function from within a native callback.
     * When a bytecode function throws, the Java exception propagates out,
     * skipping the native callback's error handling. This method catches
     * JSVirtualMachineException and converts it to a pending exception
     * so the caller can handle it normally via context.hasPendingException().
     */
    private static JSValue callSafe(JSContext context, JSFunction function, JSValue thisArg, JSValue[] args) {
        try {
            return function.call(context, thisArg, args);
        } catch (JSVirtualMachineException e) {
            convertVMException(context, e);
            return JSUndefined.INSTANCE;
        }
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
        JSValue result = callSafe(context, returnFunction, iteratorObject, NO_ARGS);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        return result;
    }

    private static void closeIteratorIgnoringResult(JSContext context, JSObject iteratorObject) {
        JSValue pendingException = context.getPendingException();
        if (pendingException != null) {
            context.clearPendingException();
        }
        closeIterator(context, iteratorObject);
        if (pendingException != null) {
            context.setPendingException(pendingException);
        }
    }

    /**
     * Close all open iterators with a throw completion (error).
     * Marks all as closed and calls IteratorCloseAll in reverse order.
     */
    private static void closeOpenIteratorsWithError(
            JSContext context,
            List<IteratorRecord> iters,
            boolean[] open,
            JSValue error) {

        List<IteratorRecord> openIters = new ArrayList<>();
        for (int i = 0; i < open.length; i++) {
            if (open[i]) {
                openIters.add(iters.get(i));
                open[i] = false;
            }
        }
        // Clear any pending exception before calling IteratorCloseAll
        if (context.hasPendingException()) {
            context.clearPendingException();
        }
        iteratorCloseAll(openIters, error, context);
    }

    /**
     * Close all open iterators with a return completion.
     * Marks all as closed and calls IteratorCloseAll in reverse order.
     */
    private static void closeOpenIteratorsWithReturn(
            JSContext context,
            List<IteratorRecord> iters,
            boolean[] open) {

        List<IteratorRecord> openIters = new ArrayList<>();
        for (int i = 0; i < open.length; i++) {
            if (open[i]) {
                openIters.add(iters.get(i));
                open[i] = false;
            }
        }
        iteratorCloseAll(openIters, null, context);
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
                return childContext.throwTypeError("cannot invoke a running iterator");
            }
            running[0] = true;
            try {
                done[0] = true;
                sourceIndex[0] = sources.size();
                if (currentIterator[0] == null) {
                    return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                }
                JSValue returnMethod = currentIterator[0].get(childContext, PropertyKey.RETURN);
                currentNextMethod[0] = JSUndefined.INSTANCE;
                JSObject iter = currentIterator[0];
                currentIterator[0] = null;
                if (childContext.hasPendingException()) {
                    return childContext.getPendingException();
                }
                if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
                    return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                }
                if (!(returnMethod instanceof JSFunction returnFunctionValue)) {
                    return childContext.throwTypeError("not a function");
                }
                JSValue result = returnFunctionValue.call(childContext, iter, NO_ARGS);
                if (childContext.hasPendingException()) {
                    return childContext.getPendingException();
                }
                return result;
            } finally {
                running[0] = false;
            }
        });

        return createIteratorObject(context, nextFunction, returnFunction, "Iterator Concat");
    }

    /**
     * Convert a JSVirtualMachineException to a pending exception on the context.
     */
    private static void convertVMException(JSContext context, JSVirtualMachineException e) {
        if (e.getJsValue() != null) {
            context.setPendingException(e.getJsValue());
        } else if (e.getJsError() != null) {
            context.setPendingException(e.getJsError());
        } else if (!context.hasPendingException()) {
            context.throwError("Error", e.getMessage() != null ? e.getMessage() : "Unhandled exception");
        }
    }

    private static JSNativeFunction createHelperReturnFunction(JSObject iteratorObject, boolean[] running, boolean[] done) {
        return new JSNativeFunction("return", 0, (childContext, childThisArg, childArgs) -> {
            if (running[0]) {
                return childContext.throwTypeError("cannot invoke a running iterator");
            }
            if (done[0]) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }
            done[0] = true;
            JSValue returnMethod = iteratorObject.get(childContext, PropertyKey.RETURN);
            if (childContext.hasPendingException()) {
                return childContext.getPendingException();
            }
            if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
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
        // Use shared iterator-type-specific prototype if available
        JSObject iterProto = (toStringTag != null) ? context.getIteratorPrototype(toStringTag) : null;
        if (iterProto != null) {
            iteratorObject.setPrototype(iterProto);
        } else {
            context.transferPrototype(iteratorObject, JSIterator.NAME);
        }
        iteratorObject.definePropertyWritableConfigurable("next", nextFunction);
        if (returnFunction != null) {
            iteratorObject.definePropertyWritableConfigurable("return", returnFunction);
        }
        // Only set own toStringTag if no shared prototype provides it
        if (toStringTag != null && iterProto == null) {
            iteratorObject.defineProperty(
                    PropertyKey.SYMBOL_TO_STRING_TAG,
                    PropertyDescriptor.dataDescriptor(new JSString(toStringTag), PropertyDescriptor.DataState.Configurable));
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
            if (!(returnMethod instanceof JSFunction returnFunc)) {
                return childContext.throwTypeError("not a function");
            }
            return returnFunc.call(childContext, wrappedIterator, NO_ARGS);
        });

        return createIteratorObject(context, nextFunction, returnFunction, "Iterator Wrap");
    }

    /**
     * Shared IteratorZip implementation for both zip and zipKeyed.
     * When keys is null, produces arrays (zip). When keys is non-null, produces null-proto objects (zipKeyed).
     */
    private static JSValue createZipIterator(
            JSContext context,
            List<IteratorRecord> iters,
            String mode,
            List<JSValue> padding,
            PropertyKey[] keyArray) {

        int iterCount = iters.size();
        final boolean isShortestMode = "shortest".equals(mode);
        final boolean isStrictMode = "strict".equals(mode);
        final boolean isLongestMode = "longest".equals(mode);
        final JSValue[] cachedZipKeyStrings;
        if (keyArray != null) {
            JSValue[] candidateCachedZipKeyStrings = new JSValue[keyArray.length];
            boolean allStringKeys = true;
            for (int index = 0; index < keyArray.length; index++) {
                if (!keyArray[index].isString()) {
                    allStringKeys = false;
                    break;
                }
                candidateCachedZipKeyStrings[index] = new JSString(keyArray[index].asString());
            }
            if (allStringKeys) {
                cachedZipKeyStrings = candidateCachedZipKeyStrings;
            } else {
                cachedZipKeyStrings = null;
            }
        } else {
            cachedZipKeyStrings = null;
        }
        final JSValue[] paddingValues;
        if (padding != null) {
            paddingValues = new JSValue[iterCount];
            for (int index = 0; index < iterCount; index++) {
                if (index < padding.size()) {
                    paddingValues[index] = padding.get(index);
                } else {
                    paddingValues[index] = JSUndefined.INSTANCE;
                }
            }
        } else {
            paddingValues = null;
        }
        // Track which iterators are still open (by index)
        boolean[] open = new boolean[iterCount];
        for (int i = 0; i < iterCount; i++) {
            open[i] = true;
        }
        int[] openIteratorCount = {iterCount};
        JSValue[] stepResults = new JSValue[iterCount];

        // Generator state: 0 = suspended-start, 1 = suspended-yield, 2 = executing, 3 = completed
        int[] generatorState = {0};

        JSNativeFunction nextFunction = new JSNativeFunction("next", 0, (childContext, childThisArg, childArgs) -> {
            if (generatorState[0] == 2) {
                return childContext.throwTypeError("generator is already running");
            }
            if (generatorState[0] == 3) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }

            generatorState[0] = 2; // executing
            try {
                if (openIteratorCount[0] == 0) {
                    generatorState[0] = 3;
                    return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                }

                for (int i = 0; i < iterCount; i++) {
                    if (!open[i]) {
                        // Already exhausted, use padding
                        stepResults[i] = paddingValues != null ? paddingValues[i] : JSUndefined.INSTANCE;
                        continue;
                    }

                    IteratorRecord iterRecord = iters.get(i);
                    IteratorStep step = iteratorStep(childContext, iterRecord.iterator(), iterRecord.nextMethod());
                    if (step == null) {
                        // Abrupt completion: remove this iter from openIters, close remaining
                        open[i] = false;
                        openIteratorCount[0]--;
                        generatorState[0] = 3;
                        JSValue error = childContext.getPendingException();
                        closeOpenIteratorsWithError(childContext, iters, open, error);
                        return childContext.getPendingException();
                    }

                    if (step.done()) {
                        open[i] = false;
                        openIteratorCount[0]--;

                        if (isShortestMode) {
                            // Close all remaining open iterators with ReturnCompletion
                            generatorState[0] = 3;
                            closeOpenIteratorsWithReturn(childContext, iters, open);
                            if (childContext.hasPendingException()) {
                                return childContext.getPendingException();
                            }
                            return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                        }

                        if (isStrictMode) {
                            if (i != 0) {
                                // Non-first iterator done: immediately close and throw TypeError
                                generatorState[0] = 3;
                                JSValue error = childContext.throwTypeError("iterators have different lengths");
                                closeOpenIteratorsWithError(childContext, iters, open, error);
                                return childContext.getPendingException();
                            }
                            // First iterator (i==0) is done: check remaining with IteratorStep
                            return handleStrictFirstDone(childContext, iters, open, iterCount, generatorState);
                        }

                        // "longest" mode: use padding value
                        stepResults[i] = paddingValues != null ? paddingValues[i] : JSUndefined.INSTANCE;
                    } else {
                        stepResults[i] = step.value();
                    }
                }

                // "longest" mode: check if all iterators are now done
                if (isLongestMode) {
                    if (openIteratorCount[0] == 0) {
                        generatorState[0] = 3;
                        return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                    }
                }

                // Build result
                JSValue resultValue = buildZipResult(childContext, stepResults, iterCount, keyArray, cachedZipKeyStrings);

                generatorState[0] = 1; // suspended-yield
                return iteratorResult(childContext, resultValue, false);
            } catch (Exception e) {
                generatorState[0] = 3;
                throw e;
            }
        });

        JSNativeFunction returnFunction = new JSNativeFunction("return", 0, (childContext, childThisArg, childArgs) -> {
            if (generatorState[0] == 2) {
                return childContext.throwTypeError("generator is already running");
            }
            if (generatorState[0] == 3) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }

            if (generatorState[0] == 0) {
                // suspended-start: set to completed, then close
                generatorState[0] = 3;
            } else {
                // suspended-yield: set to executing during close
                generatorState[0] = 2;
            }

            closeOpenIteratorsWithReturn(childContext, iters, open);

            // After close, set to completed
            generatorState[0] = 3;

            if (childContext.hasPendingException()) {
                return childContext.getPendingException();
            }
            return iteratorResult(childContext, JSUndefined.INSTANCE, true);
        });

        return createIteratorObject(context, nextFunction, returnFunction, "Iterator Helper");
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
        Long limit;
        try {
            limit = toPositiveLimit(context, args);
        } catch (JSVirtualMachineException e) {
            convertVMException(context, e);
            closeIteratorIgnoringResult(context, iteratorObject);
            return context.getPendingException();
        }
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
            closeIteratorIgnoringResult(context, iteratorObject);
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
            JSValue result = callSafe(context, predicate, JSUndefined.INSTANCE, new JSValue[]{step.value(), JSNumber.of(index++)});
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
                    JSValue selected = callSafe(childContext, predicate,
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
            closeIteratorIgnoringResult(context, iteratorObject);
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
            JSValue selected = callSafe(context, predicate, JSUndefined.INSTANCE, new JSValue[]{step.value(), JSNumber.of(index++)});
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

                    JSValue mapped = callSafe(childContext, mapper,
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
            if (done[0]) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
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
            if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
                return iteratorResult(childContext, JSUndefined.INSTANCE, true);
            }
            if (!(returnMethod instanceof JSFunction returnFunctionValue)) {
                return childContext.throwTypeError("not a function");
            }
            JSValue result = callSafe(childContext, returnFunctionValue, iteratorObject, NO_ARGS);
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
            closeIteratorIgnoringResult(context, iteratorObject);
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
            callSafe(context, callback, JSUndefined.INSTANCE, new JSValue[]{step.value(), JSNumber.of(index++)});
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
        // QuickJS: for strings, look up Symbol.iterator on the primitive (not boxed)
        // then call it with the primitive as this. For non-string non-objects, throw.
        boolean isString = sourceValue instanceof JSString;
        if (!isString && !(sourceValue instanceof JSObject)) {
            return context.throwTypeError("Iterator.from called on non-object");
        }

        // For strings, auto-box to find Symbol.iterator but keep raw string for this
        JSValue callThis = sourceValue;
        JSObject lookupObject;
        if (isString) {
            lookupObject = JSTypeConversions.toObject(context, sourceValue);
        } else {
            lookupObject = (JSObject) sourceValue;
        }

        JSObject iteratorObject = lookupObject;
        // For strings, use primitive receiver so strict-mode getters see typeof this === 'string'
        JSValue iteratorMethod = isString
                ? lookupObject.get(context, PropertyKey.SYMBOL_ITERATOR, sourceValue)
                : lookupObject.get(context, PropertyKey.SYMBOL_ITERATOR);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(iteratorMethod instanceof JSUndefined) && !(iteratorMethod instanceof JSNull)) {
            if (!(iteratorMethod instanceof JSFunction iteratorFunction)) {
                return context.throwTypeError("not a function");
            }
            JSValue iterValue = iteratorFunction.call(context, callThis, NO_ARGS);
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

    /**
     * Get and validate the "mode" option. Returns "shortest", "longest", or "strict".
     */
    private static String getAndValidateMode(JSContext context, JSObject options) {
        JSValue modeValue = options.get(context, PropertyKey.fromString("mode"));
        if (context.hasPendingException()) {
            return null;
        }
        if (modeValue instanceof JSUndefined) {
            return "shortest";
        }
        // Must be a string primitive (not a String wrapper), no coercion
        if (!(modeValue instanceof JSString modeStr)) {
            context.throwTypeError("mode must be a string");
            return null;
        }
        String mode = modeStr.value();
        if (!"shortest".equals(mode) && !"longest".equals(mode) && !"strict".equals(mode)) {
            context.throwTypeError("mode must be 'shortest', 'longest', or 'strict'");
            return null;
        }
        return mode;
    }

    private static long getArrayLikeLength(JSContext context, JSObject arrayLike) {
        return JSTypeConversions.toLength(context, arrayLike.get(context, PropertyKey.LENGTH));
    }

    private static JSValue getArrayLikeValue(JSContext context, JSObject arrayLike, long index) {
        return arrayLike.get(context, PropertyKey.fromString(Long.toString(index)));
    }

    /**
     * GetIteratorFlattenable(obj, reject-strings)
     */
    private static IteratorRecord getIteratorFlattenable(JSContext context, JSValue obj) {
        if (!(obj instanceof JSObject objObject)) {
            context.throwTypeError("value is not an object");
            return null;
        }

        JSValue iteratorMethod = objObject.get(context, PropertyKey.SYMBOL_ITERATOR);
        if (context.hasPendingException()) {
            return null;
        }

        JSObject iterator;
        if (iteratorMethod instanceof JSUndefined || iteratorMethod instanceof JSNull) {
            // Use the object itself as the iterator
            iterator = objObject;
        } else {
            if (!(iteratorMethod instanceof JSFunction iterFunc)) {
                context.throwTypeError("Symbol.iterator is not a function");
                return null;
            }
            JSValue iterValue = callSafe(context, iterFunc, objObject, NO_ARGS);
            if (context.hasPendingException()) {
                return null;
            }
            if (!(iterValue instanceof JSObject iterObj)) {
                context.throwTypeError("iterator is not an object");
                return null;
            }
            iterator = iterObj;
        }

        JSValue nextMethod = iterator.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return null;
        }
        return new IteratorRecord(iterator, nextMethod);
    }

    /**
     * GetOptionsObject(options) - ES2024 spec
     */
    private static JSObject getOptionsObject(JSContext context, JSValue options) {
        if (options instanceof JSUndefined) {
            JSObject nullProto = context.createJSObject();
            nullProto.setPrototype(null);
            return nullProto;
        }
        if (options instanceof JSObject obj) {
            return obj;
        }
        context.throwTypeError("options must be an object or undefined");
        return null;
    }

    /**
     * Handle strict mode when first iterator (i==0) is done.
     * Check remaining iterators (k=1..iterCount-1) with IteratorStep.
     */
    private static JSValue handleStrictFirstDone(
            JSContext context,
            List<IteratorRecord> iters,
            boolean[] open,
            int iterCount,
            int[] generatorState) {

        for (int k = 1; k < iterCount; k++) {
            if (!open[k]) {
                continue;
            }
            IteratorRecord iterRecord = iters.get(k);
            IteratorStep step = iteratorStep(context, iterRecord.iterator(), iterRecord.nextMethod());
            if (step == null) {
                // Abrupt completion during IteratorStep
                open[k] = false;
                generatorState[0] = 3;
                JSValue error = context.getPendingException();
                closeOpenIteratorsWithError(context, iters, open, error);
                return context.getPendingException();
            }
            if (step.done()) {
                open[k] = false;
            } else {
                // Not done: TypeError, close all remaining open
                generatorState[0] = 3;
                JSValue error = context.throwTypeError("iterators have different lengths");
                closeOpenIteratorsWithError(context, iters, open, error);
                return context.getPendingException();
            }
        }
        // All done at same time
        generatorState[0] = 3;
        return iteratorResult(context, JSUndefined.INSTANCE, true);
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

    /**
     * IteratorCloseAll(iters, completion) - closes iterators in reverse order.
     * If originalError is non-null, it's a throw completion; otherwise it's a return completion.
     */
    private static void iteratorCloseAll(List<IteratorRecord> iters, JSValue originalError, JSContext context) {
        boolean isThrow = (originalError != null);
        JSValue pendingError = originalError;

        for (int i = iters.size() - 1; i >= 0; i--) {
            JSObject iterator = iters.get(i).iterator();

            // Clear any pending exception before each IteratorClose
            if (context.hasPendingException()) {
                context.clearPendingException();
            }

            // GetMethod(iterator, "return") - may throw via getter/proxy
            JSValue returnMethod;
            try {
                returnMethod = iterator.get(context, PropertyKey.RETURN);
            } catch (JSVirtualMachineException e) {
                convertVMException(context, e);
                if (!isThrow) {
                    pendingError = context.getPendingException();
                    isThrow = true;
                }
                if (context.hasPendingException()) {
                    context.clearPendingException();
                }
                continue;
            }
            if (context.hasPendingException()) {
                if (!isThrow) {
                    pendingError = context.getPendingException();
                    isThrow = true;
                }
                context.clearPendingException();
                continue;
            }
            if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
                // No return method, skip
                continue;
            }
            if (!(returnMethod instanceof JSFunction returnFunc)) {
                if (!isThrow) {
                    pendingError = context.throwTypeError("return is not a function");
                    isThrow = true;
                    context.clearPendingException();
                }
                continue;
            }
            JSValue innerResult = callSafe(context, returnFunc, iterator, NO_ARGS);
            if (context.hasPendingException()) {
                if (!isThrow) {
                    pendingError = context.getPendingException();
                    isThrow = true;
                }
                context.clearPendingException();
            } else if (!isThrow && !(innerResult instanceof JSObject)) {
                pendingError = context.throwTypeError("return did not return an object");
                isThrow = true;
                context.clearPendingException();
            }
        }

        // Restore the final error
        if (context.hasPendingException()) {
            context.clearPendingException();
        }
        if (pendingError != null) {
            context.setPendingException(pendingError);
        }
    }

    private static JSObject iteratorResult(JSContext context, JSValue value, boolean done) {
        JSValue actualValue = value != null ? value : JSUndefined.INSTANCE;
        JSValue doneValue = JSBoolean.valueOf(done);
        JSObject result = new JSIteratorResultObject(actualValue, doneValue);
        context.transferPrototype(result, JSObject.NAME);
        return result;
    }

    private static IteratorStep iteratorStep(JSContext context, JSObject iteratorObject, JSValue methodValue) {
        if (!(methodValue instanceof JSFunction function)) {
            context.throwTypeError("not a function");
            return null;
        }
        JSValue resultValue = callSafe(context, function, iteratorObject, NO_ARGS);
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
        boolean isDone = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
        if (isDone) {
            return new IteratorStep(JSUndefined.INSTANCE, true);
        }
        JSValue value = resultObject.get(context, PropertyKey.VALUE);
        if (context.hasPendingException()) {
            return null;
        }
        if (value == null) {
            value = JSUndefined.INSTANCE;
        }
        return new IteratorStep(value, false);
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
                JSValue mapped = callSafe(childContext, mapper,
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
            closeIteratorIgnoringResult(context, iteratorObject);
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
            JSValue reduced = callSafe(context, reducer, JSUndefined.INSTANCE, new JSValue[]{
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
            closeIteratorIgnoringResult(context, iteratorObject);
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
            JSValue result = callSafe(context, predicate, JSUndefined.INSTANCE, new JSValue[]{step.value(), JSNumber.of(index++)});
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
        Long limit;
        try {
            limit = toPositiveLimit(context, args);
        } catch (JSVirtualMachineException e) {
            convertVMException(context, e);
            closeIteratorIgnoringResult(context, iteratorObject);
            return context.getPendingException();
        }
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
                    closeIterator(childContext, iteratorObject);
                    if (childContext.hasPendingException()) {
                        return childContext.getPendingException();
                    }
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
        if (context.hasPendingException()) {
            return null;
        }
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

    /**
     * Iterator.zip(iterables [, options])
     * Creates an iterator that zips multiple iterables together.
     */
    public static JSValue zip(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue iterablesArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        // Step 1: If iterables is not an Object, throw a TypeError exception.
        if (!(iterablesArg instanceof JSObject iterablesObject)) {
            return context.throwTypeError("iterables is not an object");
        }

        // Step 2: GetOptionsObject
        JSObject options = getOptionsObject(context, optionsArg);
        if (options == null) {
            return context.getPendingException();
        }

        // Step 3-5: Get and validate mode
        String mode = getAndValidateMode(context, options);
        if (mode == null) {
            return context.getPendingException();
        }

        // Step 6-8: Handle padding for "longest" mode
        JSValue paddingOption = JSUndefined.INSTANCE;
        if ("longest".equals(mode)) {
            paddingOption = options.get(context, PropertyKey.fromString("padding"));
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!(paddingOption instanceof JSUndefined) && !(paddingOption instanceof JSObject)) {
                return context.throwTypeError("padding must be an object or undefined");
            }
        }

        // Step 10: GetIterator(iterables, sync)
        JSValue iteratorMethod = iterablesObject.get(context, PropertyKey.SYMBOL_ITERATOR);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(iteratorMethod instanceof JSFunction iteratorFunction)) {
            return context.throwTypeError("iterables is not iterable");
        }
        JSValue inputIterValue = callSafe(context, iteratorFunction, iterablesObject, NO_ARGS);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(inputIterValue instanceof JSObject inputIter)) {
            return context.throwTypeError("iterator result is not an object");
        }
        JSValue inputNextMethod = inputIter.get(context, PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 11-12: Iterate through iterables, collecting iterator records
        List<IteratorRecord> iters = new ArrayList<>();
        IteratorRecord inputIterRecord = new IteratorRecord(inputIter, inputNextMethod);
        while (true) {
            IteratorStep inputStep = iteratorStep(context, inputIter, inputNextMethod);
            if (inputStep == null) {
                // IfAbruptCloseIterators(next, iters) — only close iters, not inputIter
                iteratorCloseAll(iters, context.getPendingException(), context);
                return context.getPendingException();
            }
            if (inputStep.done()) {
                break;
            }

            // GetIteratorFlattenable(next, reject-strings)
            IteratorRecord iterRecord = getIteratorFlattenable(context, inputStep.value());
            if (iterRecord == null) {
                // IfAbruptCloseIterators(iter, « inputIter » ⧺ iters)
                // Combined list: [inputIter, iters[0], iters[1], ...]; close in reverse
                JSValue error = context.getPendingException();
                List<IteratorRecord> combined = new ArrayList<>();
                combined.add(inputIterRecord);
                combined.addAll(iters);
                iteratorCloseAll(combined, error, context);
                return context.getPendingException();
            }
            iters.add(iterRecord);
        }

        int iterCount = iters.size();

        // Step 14: Handle padding for "longest" mode
        List<JSValue> paddingValues = null;
        if ("longest".equals(mode)) {
            paddingValues = new ArrayList<>(iterCount);
            if (paddingOption instanceof JSUndefined) {
                for (int i = 0; i < iterCount; i++) {
                    paddingValues.add(JSUndefined.INSTANCE);
                }
            } else {
                JSObject paddingObject = (JSObject) paddingOption;
                JSValue padIterMethod = paddingObject.get(context, PropertyKey.SYMBOL_ITERATOR);
                if (context.hasPendingException()) {
                    iteratorCloseAll(iters, context.getPendingException(), context);
                    return context.getPendingException();
                }
                if (!(padIterMethod instanceof JSFunction padIterFunc)) {
                    JSValue error = context.throwTypeError("padding is not iterable");
                    iteratorCloseAll(iters, error, context);
                    return context.getPendingException();
                }
                JSValue padIterValue = callSafe(context, padIterFunc, paddingObject, NO_ARGS);
                if (context.hasPendingException()) {
                    iteratorCloseAll(iters, context.getPendingException(), context);
                    return context.getPendingException();
                }
                if (!(padIterValue instanceof JSObject padIter)) {
                    JSValue error = context.throwTypeError("padding iterator is not an object");
                    iteratorCloseAll(iters, error, context);
                    return context.getPendingException();
                }
                JSValue padNextMethod = padIter.get(context, PropertyKey.NEXT);
                if (context.hasPendingException()) {
                    iteratorCloseAll(iters, context.getPendingException(), context);
                    return context.getPendingException();
                }

                boolean usingIterator = true;
                for (int i = 0; i < iterCount; i++) {
                    if (usingIterator) {
                        IteratorStep padStep = iteratorStep(context, padIter, padNextMethod);
                        if (padStep == null) {
                            // IfAbruptCloseIterators(next, iters)
                            iteratorCloseAll(iters, context.getPendingException(), context);
                            return context.getPendingException();
                        }
                        if (padStep.done()) {
                            usingIterator = false;
                            paddingValues.add(JSUndefined.INSTANCE);
                        } else {
                            paddingValues.add(padStep.value());
                        }
                    } else {
                        paddingValues.add(JSUndefined.INSTANCE);
                    }
                }
                // Close padding iterator if not exhausted
                if (usingIterator) {
                    closeIterator(context, padIter);
                    if (context.hasPendingException()) {
                        iteratorCloseAll(iters, context.getPendingException(), context);
                        return context.getPendingException();
                    }
                }
            }
        }

        // Create the zip iterator
        return createZipIterator(context, iters, mode, paddingValues, null);
    }

    /**
     * Iterator.zipKeyed(iterables [, options])
     * Creates an iterator that zips named iterables together, producing objects.
     */
    public static JSValue zipKeyed(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue iterablesArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue optionsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        // Step 1: If iterables is not an Object, throw a TypeError exception.
        if (!(iterablesArg instanceof JSObject iterablesObject)) {
            return context.throwTypeError("iterables is not an object");
        }

        // Step 2: GetOptionsObject
        JSObject options = getOptionsObject(context, optionsArg);
        if (options == null) {
            return context.getPendingException();
        }

        // Step 3-5: Get and validate mode
        String mode = getAndValidateMode(context, options);
        if (mode == null) {
            return context.getPendingException();
        }

        // Step 6-8: Handle padding for "longest" mode
        JSObject paddingObject = null;
        if ("longest".equals(mode)) {
            JSValue paddingOption = options.get(context, PropertyKey.fromString("padding"));
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!(paddingOption instanceof JSUndefined) && !(paddingOption instanceof JSObject)) {
                return context.throwTypeError("padding must be an object or undefined");
            }
            if (paddingOption instanceof JSObject padObj) {
                paddingObject = padObj;
            }
        }

        // Step 10-12: Get own property keys and iterate
        List<PropertyKey> allKeys = iterablesObject.getOwnPropertyKeys();
        List<PropertyKey> keys = new ArrayList<>();
        List<IteratorRecord> iters = new ArrayList<>();

        for (PropertyKey key : allKeys) {
            PropertyDescriptor desc;
            try {
                desc = iterablesObject.getOwnPropertyDescriptor(key);
            } catch (JSVirtualMachineException e) {
                convertVMException(context, e);
                iteratorCloseAll(iters, context.getPendingException(), context);
                return context.getPendingException();
            }
            if (context.hasPendingException()) {
                iteratorCloseAll(iters, context.getPendingException(), context);
                return context.getPendingException();
            }
            if (desc == null || !desc.isEnumerable()) {
                continue;
            }
            JSValue value;
            try {
                value = iterablesObject.get(context, key);
            } catch (JSVirtualMachineException e) {
                convertVMException(context, e);
                iteratorCloseAll(iters, context.getPendingException(), context);
                return context.getPendingException();
            }
            if (context.hasPendingException()) {
                iteratorCloseAll(iters, context.getPendingException(), context);
                return context.getPendingException();
            }
            if (value instanceof JSUndefined) {
                continue;
            }
            IteratorRecord iterRecord = getIteratorFlattenable(context, value);
            if (iterRecord == null) {
                iteratorCloseAll(iters, context.getPendingException(), context);
                return context.getPendingException();
            }
            keys.add(key);
            iters.add(iterRecord);
        }

        // Step 14: Handle padding for "longest" mode
        List<JSValue> paddingValues = null;
        if ("longest".equals(mode)) {
            paddingValues = new ArrayList<>(keys.size());
            if (paddingObject == null) {
                for (int i = 0; i < keys.size(); i++) {
                    paddingValues.add(JSUndefined.INSTANCE);
                }
            } else {
                for (PropertyKey key : keys) {
                    JSValue padValue = paddingObject.get(context, key);
                    if (context.hasPendingException()) {
                        iteratorCloseAll(iters, context.getPendingException(), context);
                        return context.getPendingException();
                    }
                    paddingValues.add(padValue != null ? padValue : JSUndefined.INSTANCE);
                }
            }
        }

        // Create the zip iterator
        return createZipIterator(context, iters, mode, paddingValues, keys.toArray(new PropertyKey[0]));
    }

    private record ConcatSource(JSObject sourceObject, JSFunction iteratorMethod) {
    }

    private record IteratorRecord(JSObject iterator, JSValue nextMethod) {
    }

    private record IteratorStep(JSValue value, boolean done) {
    }

    /**
     * Specialized CreateIteratorResultObject for iterator helper hot paths.
     * It lazily materializes to a normal object when mutated.
     */
    private static final class JSIteratorResultObject extends JSObject {
        private JSValue doneValue;
        private boolean pristine;
        private JSValue valueValue;

        private JSIteratorResultObject(JSValue valueValue, JSValue doneValue) {
            super();
            this.valueValue = valueValue;
            this.doneValue = doneValue;
            this.pristine = true;
        }

        @Override
        public boolean defineOwnProperty(PropertyKey key, PropertyDescriptor descriptor, JSContext context) {
            materialize();
            return super.defineOwnProperty(key, descriptor, context);
        }

        @Override
        public void defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
            materialize();
            super.defineProperty(key, descriptor);
        }

        @Override
        public boolean delete(JSContext context, PropertyKey key) {
            materialize();
            return super.delete(context, key);
        }

        @Override
        public boolean delete(PropertyKey key) {
            materialize();
            return super.delete(key);
        }

        @Override
        public boolean delete(String propertyName) {
            materialize();
            return super.delete(propertyName);
        }

        @Override
        public PropertyKey[] enumerableKeys() {
            if (pristine) {
                return ITERATOR_RESULT_KEYS.clone();
            }
            return super.enumerableKeys();
        }

        @Override
        public JSValue get(JSContext context, PropertyKey key) {
            if (pristine) {
                if (PropertyKey.VALUE.equals(key)) {
                    return valueValue;
                }
                if (PropertyKey.DONE.equals(key)) {
                    return doneValue;
                }
            }
            return super.get(context, key);
        }

        @Override
        public PropertyDescriptor getOwnPropertyDescriptor(PropertyKey key) {
            if (pristine) {
                if (PropertyKey.VALUE.equals(key)) {
                    return PropertyDescriptor.defaultData(valueValue);
                }
                if (PropertyKey.DONE.equals(key)) {
                    return PropertyDescriptor.defaultData(doneValue);
                }
                return null;
            }
            return super.getOwnPropertyDescriptor(key);
        }

        @Override
        public List<PropertyKey> getOwnPropertyKeys() {
            if (pristine) {
                return ITERATOR_RESULT_KEY_LIST;
            }
            return super.getOwnPropertyKeys();
        }

        @Override
        public boolean hasOwnProperty(String propertyName) {
            if (pristine && ("value".equals(propertyName) || "done".equals(propertyName))) {
                return true;
            }
            return super.hasOwnProperty(propertyName);
        }

        @Override
        public boolean hasOwnProperty(PropertyKey key) {
            if (pristine && (PropertyKey.VALUE.equals(key) || PropertyKey.DONE.equals(key))) {
                return true;
            }
            return super.hasOwnProperty(key);
        }

        private void materialize() {
            if (!pristine) {
                return;
            }
            initProperties(
                    ITERATOR_RESULT_KEYS.clone(),
                    new PropertyDescriptor[]{
                            PropertyDescriptor.defaultData(valueValue),
                            PropertyDescriptor.defaultData(doneValue)
                    },
                    new JSValue[]{valueValue, doneValue}
            );
            pristine = false;
            valueValue = JSUndefined.INSTANCE;
            doneValue = JSUndefined.INSTANCE;
        }

        @Override
        public PropertyKey[] ownPropertyKeys() {
            if (pristine) {
                return ITERATOR_RESULT_KEYS.clone();
            }
            return super.ownPropertyKeys();
        }

        @Override
        public void set(String propertyName, JSValue value) {
            materialize();
            super.set(propertyName, value);
        }

        @Override
        public void set(int index, JSValue value) {
            materialize();
            super.set(index, value);
        }

        @Override
        public void set(PropertyKey key, JSValue value) {
            materialize();
            super.set(key, value);
        }

        @Override
        public void set(JSContext context, PropertyKey key, JSValue value) {
            materialize();
            super.set(context, key, value);
        }

        @Override
        public void set(JSContext context, PropertyKey key, JSValue value, JSObject receiver) {
            materialize();
            super.set(context, key, value, receiver);
        }
    }

    /**
     * Specialized zipKeyed result object that caches key strings for Object.keys().
     * It falls back automatically if the object shape becomes non-trivial.
     */
    private static final class JSZipKeyedResultObject extends JSObject {
        private final JSValue[] cachedKeyStrings;
        private final PropertyKey[] expectedKeys;
        private boolean pristine;

        private JSZipKeyedResultObject(PropertyKey[] expectedKeys, JSValue[] cachedKeyStrings) {
            super();
            this.expectedKeys = expectedKeys;
            this.cachedKeyStrings = cachedKeyStrings;
            this.pristine = true;
        }

        @Override
        public boolean defineOwnProperty(PropertyKey key, PropertyDescriptor descriptor, JSContext context) {
            pristine = false;
            return super.defineOwnProperty(key, descriptor, context);
        }

        @Override
        public void defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
            pristine = false;
            super.defineProperty(key, descriptor);
        }

        @Override
        public boolean delete(JSContext context, PropertyKey key) {
            pristine = false;
            return super.delete(context, key);
        }

        @Override
        public boolean delete(PropertyKey key) {
            pristine = false;
            return super.delete(key);
        }

        @Override
        public boolean delete(String propertyName) {
            pristine = false;
            return super.delete(propertyName);
        }

        @Override
        public JSValue[] enumerableStringKeyValuesFastPath() {
            if (!pristine) {
                return null;
            }
            return cachedKeyStrings;
        }

        @Override
        public JSValue[] enumerableStringPropertyValuesFastPath() {
            if (!pristine) {
                return null;
            }
            return propertyValues.length == expectedKeys.length
                    ? propertyValues
                    : Arrays.copyOf(propertyValues, expectedKeys.length);
        }

        @Override
        public void set(String propertyName, JSValue value) {
            pristine = false;
            super.set(propertyName, value);
        }

        @Override
        public void set(int index, JSValue value) {
            pristine = false;
            super.set(index, value);
        }

        @Override
        public void set(PropertyKey key, JSValue value) {
            pristine = false;
            super.set(key, value);
        }

        @Override
        public void set(JSContext context, PropertyKey key, JSValue value) {
            pristine = false;
            super.set(context, key, value);
        }

        @Override
        public void set(JSContext context, PropertyKey key, JSValue value, JSObject receiver) {
            pristine = false;
            super.set(context, key, value, receiver);
        }
    }
}
