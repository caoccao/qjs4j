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
import java.util.List;

/**
 * Implementation of iterator-related prototype methods. Based on ES2020 iteration protocols.
 */
public final class IteratorPrototype {
    private static final List<PropertyKey> ITERATOR_RESULT_KEY_LIST = List.of(PropertyKey.VALUE, PropertyKey.DONE);
    private static final PropertyKey[] ITERATOR_RESULT_KEYS = {PropertyKey.VALUE, PropertyKey.DONE};

    private IteratorPrototype() {
    }

    /**
     * Array.prototype.entries() Returns an iterator of [index, value] pairs.
     */
    public static JSValue arrayEntries(JSContext context, JSValue thisArg, JSValue[] args) {
        return arrayIterator(context, thisArg, ArrayIteration.ENTRIES);
    }

    private static JSValue arrayIterator(JSContext context, JSValue thisArg, ArrayIteration kind) {
        JSObject arrayLike = JSTypeConversions.toObject(context, thisArg);
        if (arrayLike == null) {
            return context.throwTypeError("Cannot convert undefined or null to object");
        }
        long[] index = {0};
        return new JSIterator(context, () -> {
            if (arrayLike instanceof JSTypedArray typed && typed.isOutOfBounds()) {
                context.throwTypeError(
                        "Cannot perform Array Iterator.prototype.next on a typed array backed by a detached or out-of-bounds buffer");
                return JSIterator.IteratorResult.done(context);
            }
            if (index[0] >= getArrayLikeLength(context, arrayLike)) {
                return JSIterator.IteratorResult.done(context);
            }
            if (kind == ArrayIteration.KEYS) {
                return JSIterator.IteratorResult.of(context, JSNumber.of(index[0]++));
            }
            if (kind == ArrayIteration.VALUES) {
                return JSIterator.IteratorResult.of(context, getArrayLikeValue(context, arrayLike, index[0]++));
            }
            JSArray pair = context.createJSArray();
            pair.push(JSNumber.of(index[0]));
            pair.push(getArrayLikeValue(context, arrayLike, index[0]));
            index[0]++;
            return JSIterator.IteratorResult.of(context, pair);
        }, "Array Iterator", false);
    }

    /**
     * Array.prototype.keys() Returns an iterator of array indices.
     */
    public static JSValue arrayKeys(JSContext context, JSValue thisArg, JSValue[] args) {
        return arrayIterator(context, thisArg, ArrayIteration.KEYS);
    }

    /**
     * Array.prototype.values() Returns an iterator of array values.
     */
    public static JSValue arrayValues(JSContext context, JSValue thisArg, JSValue[] args) {
        return arrayIterator(context, thisArg, ArrayIteration.VALUES);
    }

    /**
     * Safely call a function from within a native callback. When a bytecode function throws, the Java exception
     * propagates out, skipping the native callback's error handling. This method catches JSVirtualMachineException and
     * converts it to a pending exception so the caller can handle it normally via context.hasPendingException().
     */
    static JSValue callSafe(JSContext context, JSFunction function, JSValue thisArg, JSValue[] args) {
        try {
            return function.call(context, thisArg, args);
        } catch (JSVirtualMachineException e) {
            convertVMException(context, e);
            return JSUndefined.INSTANCE;
        }
    }

    static JSValue closeIterator(JSContext context, JSObject iteratorObject) {
        JSValue returnMethod = iteratorObject.get(PropertyKey.RETURN);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
            return JSUndefined.INSTANCE;
        }
        if (!(returnMethod instanceof JSFunction returnFunction)) {
            return context.throwTypeError("not a function");
        }
        JSValue result = callSafe(context, returnFunction, iteratorObject, JSValue.NO_ARGS);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(result instanceof JSObject)) {
            return context.throwTypeError("not an object");
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

    static JSObject completed(JSContext context) {
        return iteratorResult(context, JSUndefined.INSTANCE, true);
    }

    /**
     * Iterator.concat(...iterables) Creates an iterator that concatenates the provided iterables.
     */
    public static JSValue concat(JSContext context, JSValue thisArg, JSValue[] args) {
        List<ConcatSource> sources = new ArrayList<>(args.length);
        for (JSValue arg : args) {
            if (!(arg instanceof JSObject sourceObject)) {
                return context.throwTypeError("not an object");
            }
            JSValue iteratorMethod = sourceObject.get(PropertyKey.SYMBOL_ITERATOR);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!(iteratorMethod instanceof JSFunction iteratorFunction)) {
                return context.throwTypeError("not a function");
            }
            sources.add(new ConcatSource(sourceObject, iteratorFunction));
        }

        return new ConcatCursor(sources).create(context, context, "Iterator Concat");
    }

    /**
     * Convert a JSVirtualMachineException to a pending exception on the context.
     */
    static void convertVMException(JSContext context, JSVirtualMachineException e) {
        if (e.getJsValue() != null) {
            context.setPendingException(e.getJsValue());
        } else if (e.getJsError() != null) {
            context.setPendingException(e.getJsError());
        } else if (!context.hasPendingException()) {
            context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
        }
    }

    private static JSValue createHelper(JSContext context, JSValue thisArg, JSValue[] args, HelperOperation operation) {
        JSObject iterator = requireObject(context, thisArg);
        if (iterator == null) {
            return context.getPendingException();
        }
        JSFunction callback = null;
        long count = 0;
        if (operation == HelperOperation.DROP || operation == HelperOperation.TAKE) {
            Long limit;
            try {
                limit = toPositiveLimit(context, args);
            } catch (JSVirtualMachineException exception) {
                convertVMException(context, exception);
                limit = null;
            }
            if (limit == null) {
                closeIteratorIgnoringResult(context, iterator);
                return context.getPendingException();
            }
            count = limit;
        } else {
            callback = requireFunction(context, JSValue.argumentOrUndefined(args, 0));
            if (callback == null) {
                closeIteratorIgnoringResult(context, iterator);
                return context.getPendingException();
            }
        }
        JSValue nextMethod = iterator.get(PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        IteratorHelper helper = new IteratorHelper(iterator, nextMethod, operation, callback, count);
        return helper.create(context, iterator.getContext(), "Iterator Helper");
    }

    static JSObject createIteratorObject(JSContext context, JSNativeFunction nextFunction,
            JSNativeFunction returnFunction, String toStringTag) {
        nextFunction.initializePrototypeChain(context);
        if (returnFunction != null) {
            returnFunction.initializePrototypeChain(context);
        }
        JSObject iteratorObject;
        if ("Iterator Helper".equals(toStringTag) || "Iterator Wrap".equals(toStringTag)) {
            iteratorObject = new JSWrapOrHelperIteratorObject(context);
        } else {
            iteratorObject = context.createJSObject();
        }
        if (iteratorObject instanceof JSWrapOrHelperIteratorObject wrapOrHelperIteratorObject) {
            wrapOrHelperIteratorObject.setInternalNext(nextFunction);
            wrapOrHelperIteratorObject.setInternalReturn(returnFunction);
        }
        // Use shared iterator-type-specific prototype if available
        JSObject iterProto = (toStringTag != null) ? context.getIteratorPrototype(toStringTag) : null;
        if (iterProto != null) {
            iteratorObject.setPrototype(iterProto);
        } else {
            context.transferPrototype(iteratorObject, JSIterator.NAME);
        }
        if (!(iteratorObject instanceof JSWrapOrHelperIteratorObject)) {
            iteratorObject.defineProperty(PropertyKey.NEXT, nextFunction,
                    PropertyDescriptor.DataState.ConfigurableWritable);
            if (returnFunction != null) {
                iteratorObject.defineProperty(PropertyKey.RETURN, returnFunction,
                        PropertyDescriptor.DataState.ConfigurableWritable);
            }
        }
        // Only set own toStringTag if no shared prototype provides it
        if (toStringTag != null && iterProto == null) {
            iteratorObject.defineProperty(PropertyKey.SYMBOL_TO_STRING_TAG, PropertyDescriptor
                    .dataDescriptor(new JSString(toStringTag), PropertyDescriptor.DataState.Configurable));
        }
        return iteratorObject;
    }

    private static JSValue createIteratorWrap(JSContext context, JSObject wrappedIterator, JSValue wrappedNextMethod) {
        final boolean[] done = {false};

        JSNativeFunction nextFunction = new JSNativeFunction(context, "next", 0,
                (childContext, childThisArg, childArgs) -> {
                    if (done[0]) {
                        return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                    }
                    if (!(wrappedNextMethod instanceof JSFunction wrappedNextFunction)) {
                        return childContext.throwTypeError("not a function");
                    }
                    JSValue wrappedResult = callSafe(childContext, wrappedNextFunction, wrappedIterator,
                            JSValue.NO_ARGS);
                    if (childContext.hasPendingException()) {
                        done[0] = true;
                        return childContext.getPendingException();
                    }
                    if (wrappedResult instanceof JSObject wrappedResultObject) {
                        JSValue doneValue = wrappedResultObject.get(PropertyKey.DONE);
                        if (childContext.hasPendingException()) {
                            done[0] = true;
                            return childContext.getPendingException();
                        }
                        if (JSTypeConversions.toBoolean(doneValue).isBooleanTrue()) {
                            done[0] = true;
                        }
                    }
                    return wrappedResult;
                });

        JSNativeFunction returnFunction = new JSNativeFunction(context, "return", 0,
                (childContext, childThisArg, childArgs) -> {
                    done[0] = true;
                    JSValue returnMethod = wrappedIterator.get(PropertyKey.RETURN);
                    if (childContext.hasPendingException()) {
                        return childContext.getPendingException();
                    }
                    if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
                        return iteratorResult(childContext, JSUndefined.INSTANCE, true);
                    }
                    if (!(returnMethod instanceof JSFunction returnFunc)) {
                        return childContext.throwTypeError("not a function");
                    }
                    return returnFunc.call(childContext, wrappedIterator, JSValue.NO_ARGS);
                });

        return createIteratorObject(context, nextFunction, returnFunction, "Iterator Wrap");
    }

    /**
     * Iterator.prototype.drop(limit) Returns an iterator that skips the first limit elements.
     */
    public static JSValue drop(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHelper(context, thisArg, args, HelperOperation.DROP);
    }

    /**
     * Iterator.prototype.every(predicate) Tests whether all elements satisfy the predicate.
     */
    public static JSValue every(JSContext context, JSValue thisArg, JSValue[] args) {
        return iterateWithCallback(context, thisArg, args, PredicateOperation.EVERY);
    }

    /**
     * Iterator.prototype.filter(predicate) Returns an iterator of elements that satisfy the predicate.
     */
    public static JSValue filter(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHelper(context, thisArg, args, HelperOperation.FILTER);
    }

    /**
     * Iterator.prototype.find(predicate) Returns the first element that satisfies the predicate.
     */
    public static JSValue find(JSContext context, JSValue thisArg, JSValue[] args) {
        return iterateWithCallback(context, thisArg, args, PredicateOperation.FIND);
    }

    /**
     * Iterator.prototype.flatMap(mapper) Returns an iterator that applies mapper and flattens the result.
     */
    public static JSValue flatMap(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSFunction mapper = requireFunction(context, JSValue.argumentOrUndefined(args, 0));
        if (mapper == null) {
            closeIteratorIgnoringResult(context, iteratorObject);
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        return new FlatMapCursor(iteratorObject, nextMethod, mapper).create(context, context, "Iterator Helper");
    }

    /**
     * Iterator.prototype.forEach(fn) Calls fn for each element.
     */
    public static JSValue forEach(JSContext context, JSValue thisArg, JSValue[] args) {
        return iterateWithCallback(context, thisArg, args, PredicateOperation.FOR_EACH);
    }

    /**
     * Iterator.from(object) Creates an iterator from an iterable object.
     */
    public static JSValue from(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue sourceValue = JSValue.argumentOrUndefined(args, 0);
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
                ? lookupObject.get(PropertyKey.SYMBOL_ITERATOR, sourceValue)
                : lookupObject.get(PropertyKey.SYMBOL_ITERATOR);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!(iteratorMethod instanceof JSUndefined) && !(iteratorMethod instanceof JSNull)) {
            if (!(iteratorMethod instanceof JSFunction iteratorFunction)) {
                return context.throwTypeError("not a function");
            }
            JSValue iterValue = iteratorFunction.call(context, callThis, JSValue.NO_ARGS);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!(iterValue instanceof JSObject jsObject)) {
                return context.throwTypeError("not an object");
            }
            iteratorObject = jsObject;
        }

        JSValue nextMethod = iteratorObject.get(PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        if (isIteratorInstance(context, iteratorObject)) {
            return iteratorObject;
        }

        return createIteratorWrap(context, iteratorObject, nextMethod);
    }

    private static long getArrayLikeLength(JSContext context, JSObject arrayLike) {
        return JSTypeConversions.toLength(context, arrayLike.get(PropertyKey.LENGTH));
    }

    private static JSValue getArrayLikeValue(JSContext context, JSObject arrayLike, long index) {
        return arrayLike.get(PropertyKey.fromIndexString(index));
    }

    private static boolean isIteratorInstance(JSContext context, JSObject object) {
        JSValue iteratorConstructorValue = context.getGlobalObject().get(PropertyKey.ITERATOR_CAP);
        if (context.hasPendingException()
                || !(iteratorConstructorValue instanceof JSObject iteratorConstructorObject)) {
            return false;
        }
        JSValue iteratorPrototypeValue = iteratorConstructorObject.get(PropertyKey.PROTOTYPE);
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

    private static JSValue iterateWithCallback(JSContext context, JSValue thisArg, JSValue[] args,
            PredicateOperation operation) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSFunction predicate = requireFunction(context, JSValue.argumentOrUndefined(args, 0));
        if (predicate == null) {
            closeIteratorIgnoringResult(context, iteratorObject);
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        JSBoolean stopResult = operation == PredicateOperation.EVERY ? JSBoolean.FALSE : JSBoolean.TRUE;
        long index = 0;
        while (true) {
            IteratorStep step = iteratorStep(context, iteratorObject, nextMethod);
            if (step == null) {
                return context.getPendingException();
            }
            if (step.done()) {
                return operation == PredicateOperation.FIND || operation == PredicateOperation.FOR_EACH
                        ? JSUndefined.INSTANCE
                        : JSBoolean.valueOf(operation == PredicateOperation.EVERY);
            }
            JSValue result = callSafe(context, predicate, JSUndefined.INSTANCE,
                    new JSValue[]{step.value(), JSNumber.of(index++)});
            if (context.hasPendingException()) {
                // A callback error takes precedence over any error from return().
                closeIteratorIgnoringResult(context, iteratorObject);
                return context.getPendingException();
            }
            if (operation != PredicateOperation.FOR_EACH && JSTypeConversions.toBoolean(result) == stopResult) {
                closeIterator(context, iteratorObject);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                return operation == PredicateOperation.FIND ? step.value() : stopResult;
            }
        }
    }

    static JSObject iteratorResult(JSContext context, JSValue value, boolean done) {
        JSValue actualValue = value != null ? value : JSUndefined.INSTANCE;
        JSValue doneValue = JSBoolean.valueOf(done);
        JSObject result = new JSIteratorResultObject(context, actualValue, doneValue);
        context.transferPrototype(result, JSObject.NAME);
        return result;
    }

    static IteratorStep iteratorStep(JSContext context, JSObject iteratorObject, JSValue methodValue) {
        if (!(methodValue instanceof JSFunction function)) {
            context.throwTypeError("not a function");
            return null;
        }
        JSValue resultValue = callSafe(context, function, iteratorObject, JSValue.NO_ARGS);
        if (context.hasPendingException()) {
            return null;
        }
        if (!(resultValue instanceof JSObject resultObject)) {
            context.throwTypeError("not an object");
            return null;
        }
        JSValue doneValue = resultObject.get(PropertyKey.DONE);
        if (context.hasPendingException()) {
            return null;
        }
        boolean isDone = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();
        if (isDone) {
            return new IteratorStep(JSUndefined.INSTANCE, true);
        }
        JSValue value = resultObject.get(PropertyKey.VALUE);
        if (context.hasPendingException()) {
            return null;
        }
        if (value == null) {
            value = JSUndefined.INSTANCE;
        }
        return new IteratorStep(value, false);
    }

    /**
     * Iterator.prototype.map(mapper) Returns an iterator of mapped values.
     */
    public static JSValue map(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHelper(context, thisArg, args, HelperOperation.MAP);
    }

    /**
     * Map.prototype.entries() - returns iterator Returns an iterator of [key, value] pairs.
     */
    public static JSValue mapEntriesIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.entries called on non-Map");
        }

        return JSIterator.mapEntriesIterator(context, map);
    }

    /**
     * Map.prototype.keys() - returns iterator Returns an iterator of keys.
     */
    public static JSValue mapKeysIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.keys called on non-Map");
        }

        return JSIterator.mapKeysIterator(context, map);
    }

    /**
     * Map.prototype.values() - returns iterator Returns an iterator of values.
     */
    public static JSValue mapValuesIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSMap map)) {
            return context.throwTypeError("Map.prototype.values called on non-Map");
        }

        return JSIterator.mapValuesIterator(context, map);
    }

    /**
     * Iterator.prototype.next() Returns the next value in the iteration.
     */
    public static JSValue next(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIterator iterator)) {
            return context.throwTypeError("Iterator.prototype.next called on non-iterator");
        }

        return iterator.next();
    }

    /**
     * Iterator.prototype.reduce(reducer, initialValue) Reduces the iterator to a single value.
     */
    public static JSValue reduce(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSFunction reducer = requireFunction(context, JSValue.argumentOrUndefined(args, 0));
        if (reducer == null) {
            closeIteratorIgnoringResult(context, iteratorObject);
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(PropertyKey.NEXT);
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
            JSValue reduced = callSafe(context, reducer, JSUndefined.INSTANCE,
                    new JSValue[]{accumulator, step.value(), JSNumber.of(index++),});
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

    private static JSValue returnFromHelper(JSContext context, JSObject iteratorObject, boolean safeCall) {
        JSValue returnMethod = iteratorObject.get(PropertyKey.RETURN);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
            return completed(context);
        }
        if (!(returnMethod instanceof JSFunction returnFunctionValue)) {
            return context.throwTypeError("not a function");
        }
        JSValue result = safeCall
                ? callSafe(context, returnFunctionValue, iteratorObject, JSValue.NO_ARGS)
                : returnFunctionValue.call(context, iteratorObject, JSValue.NO_ARGS);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (result instanceof JSObject resultObject) {
            return resultObject;
        }
        return iteratorResult(context, result, true);
    }

    /**
     * Set.prototype.entries() - returns iterator Returns an iterator of [value, value] pairs.
     */
    public static JSValue setEntriesIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.entries called on non-Set");
        }
        return JSIterator.setEntriesIterator(context, set);
    }

    /**
     * Set.prototype.keys() - returns iterator In Set, keys() is the same as values().
     */
    public static JSValue setKeysIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        return setValuesIterator(context, thisArg, args);
    }

    /**
     * Set.prototype.values() - returns iterator Returns an iterator of values.
     */
    public static JSValue setValuesIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSSet set)) {
            return context.throwTypeError("Set.prototype.values called on non-Set");
        }

        return JSIterator.setValuesIterator(context, set);
    }

    /**
     * Iterator.prototype.some(predicate) Tests whether any element satisfies the predicate.
     */
    public static JSValue some(JSContext context, JSValue thisArg, JSValue[] args) {
        return iterateWithCallback(context, thisArg, args, PredicateOperation.SOME);
    }

    /**
     * String.prototype[Symbol.iterator]() Returns an iterator of string characters.
     */
    public static JSValue stringIterator(JSContext context, JSValue thisArg, JSValue[] args) {
        // ES2024 22.1.5.1: String.prototype[@@iterator]()
        // Step 1: Let O be ? RequireObjectCoercible(this value).
        if (thisArg instanceof JSUndefined || thisArg instanceof JSNull) {
            return context.throwTypeError("String.prototype[Symbol.iterator] called on null or undefined");
        }
        // Step 2: Let S be ? ToString(O).
        if (thisArg instanceof JSString str) {
            return JSIterator.stringIterator(context, str);
        }
        if (thisArg instanceof JSObject obj) {
            // Fast path for boxed strings
            JSValue primitiveValue = obj.getPrimitiveValue();
            if (primitiveValue instanceof JSString boxedStr) {
                return JSIterator.stringIterator(context, boxedStr);
            }
            // General object: use ToString coercion (handles custom toString)
            JSString str = JSTypeConversions.toString(context, obj);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            return JSIterator.stringIterator(context, str);
        }
        // Raw non-string primitive (number, boolean, symbol, bigint)
        return context.throwTypeError("String.prototype[Symbol.iterator] requires that 'this' be a String");
    }

    /**
     * Iterator.prototype.take(limit) Returns an iterator of the first limit elements.
     */
    public static JSValue take(JSContext context, JSValue thisArg, JSValue[] args) {
        return createHelper(context, thisArg, args, HelperOperation.TAKE);
    }

    /**
     * Iterator.prototype.toArray() Converts the iterator to an array.
     */
    public static JSValue toArray(JSContext context, JSValue thisArg, JSValue[] args) {
        JSObject iteratorObject = requireObject(context, thisArg);
        if (iteratorObject == null) {
            return context.getPendingException();
        }
        JSValue nextMethod = iteratorObject.get(PropertyKey.NEXT);
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
        JSValue limitValue = JSValue.argumentOrUndefined(args, 0);
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

    public static JSValue wrapOrHelperNext(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWrapOrHelperIteratorObject wrapOrHelperIteratorObject)) {
            return context.throwTypeError("Iterator.prototype.next called on non-iterator");
        }
        JSFunction internalNext = wrapOrHelperIteratorObject.getInternalNext();
        if (internalNext == null) {
            return context.throwTypeError("Iterator.prototype.next called on non-iterator");
        }
        return internalNext.call(context, wrapOrHelperIteratorObject, JSValue.NO_ARGS);
    }

    public static JSValue wrapOrHelperReturn(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSWrapOrHelperIteratorObject wrapOrHelperIteratorObject)) {
            return context.throwTypeError("Iterator.prototype.next called on non-iterator");
        }
        JSFunction internalReturn = wrapOrHelperIteratorObject.getInternalReturn();
        if (internalReturn == null) {
            return completed(context);
        }
        return internalReturn.call(context, wrapOrHelperIteratorObject, JSValue.NO_ARGS);
    }

    /**
     * Iterator.zip(iterables [, options]) Creates an iterator that zips multiple iterables together.
     */
    public static JSValue zip(JSContext context, JSValue thisArg, JSValue[] args) {
        return IteratorZip.zipIterables(context, args, false);
    }

    /**
     * Iterator.zipKeyed(iterables [, options]) Creates an iterator that zips named iterables together, producing
     * objects.
     */
    public static JSValue zipKeyed(JSContext context, JSValue thisArg, JSValue[] args) {
        return IteratorZip.zipIterables(context, args, true);
    }

    private enum ArrayIteration {
        ENTRIES, KEYS, VALUES
    }

    private static final class ConcatCursor extends IteratorCursor {
        JSObject currentIterator;
        JSValue currentNextMethod;
        int sourceIndex;
        private final List<ConcatSource> sources;

        ConcatCursor(List<ConcatSource> sources) {
            this.sources = sources;
        }

        @Override
        JSValue advance(JSContext context) {
            while (sourceIndex < sources.size()) {
                if (currentIterator == null) {
                    ConcatSource source = sources.get(sourceIndex);
                    JSValue iteratorValue = source.iteratorMethod().call(context, source.sourceObject(),
                            JSValue.NO_ARGS);
                    if (context.hasPendingException()) {
                        done = true;
                        return context.getPendingException();
                    }
                    if (!(iteratorValue instanceof JSObject iteratorObject)) {
                        done = true;
                        return context.throwTypeError("not an object");
                    }
                    currentIterator = iteratorObject;
                    currentNextMethod = iteratorObject.get(PropertyKey.NEXT);
                    if (context.hasPendingException()) {
                        done = true;
                        return context.getPendingException();
                    }
                }

                IteratorStep step = iteratorStep(context, currentIterator, currentNextMethod);
                if (step == null) {
                    done = true;
                    return context.getPendingException();
                }
                if (!step.done()) {
                    return iteratorResult(context, step.value(), false);
                }
                currentIterator = null;
                currentNextMethod = JSUndefined.INSTANCE;
                sourceIndex++;
            }
            done = true;
            return completed(context);
        }
        @Override
        JSValue close(JSContext context, JSValue thisArg, JSValue[] args) {
            if (running) {
                return context.throwTypeError("cannot invoke a running iterator");
            }
            running = true;
            try {
                done = true;
                sourceIndex = sources.size();
                if (currentIterator == null) {
                    return completed(context);
                }
                JSValue returnMethod = currentIterator.get(PropertyKey.RETURN);
                currentNextMethod = JSUndefined.INSTANCE;
                JSObject iter = currentIterator;
                currentIterator = null;
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
                    return completed(context);
                }
                if (!(returnMethod instanceof JSFunction returnFunctionValue)) {
                    return context.throwTypeError("not a function");
                }
                JSValue result = returnFunctionValue.call(context, iter, JSValue.NO_ARGS);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                return result;
            } finally {
                running = false;
            }
        }

        @Override
        String runningError() {
            return "already running";
        }
    }

    private record ConcatSource(JSObject sourceObject, JSFunction iteratorMethod) {
    }

    private static final class FlatMapCursor extends IteratorCursor {
        long index;
        JSObject innerIterator;
        JSValue innerNextMethod;
        private final JSObject iteratorObject;
        private final JSFunction mapper;
        private final JSValue nextMethod;

        FlatMapCursor(JSObject iteratorObject, JSValue nextMethod, JSFunction mapper) {
            this.iteratorObject = iteratorObject;
            this.nextMethod = nextMethod;
            this.mapper = mapper;
        }

        @Override
        JSValue advance(JSContext context) {
            while (true) {
                if (innerIterator != null) {
                    IteratorStep innerStep = iteratorStep(context, innerIterator, innerNextMethod);
                    if (innerStep == null) {
                        done = true;
                        closeIteratorIgnoringResult(context, innerIterator);
                        innerIterator = null;
                        closeIteratorIgnoringResult(context, iteratorObject);
                        return context.getPendingException();
                    }
                    if (!innerStep.done()) {
                        return iteratorResult(context, innerStep.value(), false);
                    }
                    innerIterator = null;
                    innerNextMethod = JSUndefined.INSTANCE;
                    continue;
                }

                IteratorStep step = iteratorStep(context, iteratorObject, nextMethod);
                if (step == null) {
                    done = true;
                    return context.getPendingException();
                }
                if (step.done()) {
                    done = true;
                    return completed(context);
                }

                JSValue mapped = callSafe(context, mapper, JSUndefined.INSTANCE,
                        new JSValue[]{step.value(), JSNumber.of(index++)});
                if (context.hasPendingException()) {
                    return fail(context, null);
                }
                if (!(mapped instanceof JSObject mappedObject)) {
                    return fail(context, "not an object");
                }

                JSValue iteratorMethod = mappedObject.get(PropertyKey.SYMBOL_ITERATOR);
                if (context.hasPendingException()) {
                    return fail(context, null);
                }

                JSObject mappedIteratorObject = mappedObject;
                if (!(iteratorMethod instanceof JSUndefined) && !(iteratorMethod instanceof JSNull)) {
                    if (!(iteratorMethod instanceof JSFunction iteratorFunction)) {
                        return fail(context, "not a function");
                    }
                    JSValue mappedIteratorValue = iteratorFunction.call(context, mappedObject, JSValue.NO_ARGS);
                    if (context.hasPendingException()) {
                        return fail(context, null);
                    }
                    if (!(mappedIteratorValue instanceof JSObject jsObject)) {
                        return fail(context, "not an object");
                    }
                    mappedIteratorObject = jsObject;
                }

                innerIterator = mappedIteratorObject;
                innerNextMethod = mappedIteratorObject.get(PropertyKey.NEXT);
                if (context.hasPendingException()) {
                    return fail(context, null);
                }
            }
        }

        @Override
        JSValue close(JSContext context, JSValue thisArg, JSValue[] args) {
            if (running) {
                return context.throwTypeError("cannot invoke a running iterator");
            }
            if (done) {
                return completed(context);
            }
            done = true;
            if (innerIterator != null) {
                closeIteratorIgnoringResult(context, innerIterator);
                innerIterator = null;
                innerNextMethod = JSUndefined.INSTANCE;
            }
            return returnFromHelper(context, iteratorObject, true);
        }

        private JSValue fail(JSContext context, String typeError) {
            done = true;
            closeIteratorIgnoringResult(context, iteratorObject);
            return typeError == null ? context.getPendingException() : context.throwTypeError(typeError);
        }
    }

    private enum HelperOperation {
        DROP, FILTER, MAP, TAKE
    }

    private abstract static class IteratorCursor {
        boolean done;
        boolean running;
        abstract JSValue advance(JSContext context);
        abstract JSValue close(JSContext context, JSValue thisArg, JSValue[] args);
        final JSObject create(JSContext context, JSContext returnContext, String tag) {
            return createIteratorObject(context, new JSNativeFunction(context, "next", 0, this::next),
                    new JSNativeFunction(returnContext, "return", 0, this::close), tag);
        }

        final JSValue next(JSContext context, JSValue thisArg, JSValue[] args) {
            if (running) {
                return context.throwTypeError(runningError());
            }
            if (done) {
                return completed(context);
            }
            running = true;
            try {
                return advance(context);
            } finally {
                running = false;
            }
        }

        String runningError() {
            return "cannot invoke a running iterator";
        }
    }

    private static final class IteratorHelper extends IteratorCursor {
        private final JSFunction callback;
        private long index;
        private final JSObject iterator;
        private final JSValue nextMethod;
        private final HelperOperation operation;
        private long remaining;

        IteratorHelper(JSObject iterator, JSValue nextMethod, HelperOperation operation, JSFunction callback,
                long remaining) {
            this.iterator = iterator;
            this.nextMethod = nextMethod;
            this.operation = operation;
            this.callback = callback;
            this.remaining = remaining;
        }

        @Override
        JSValue advance(JSContext context) {
            if (operation == HelperOperation.TAKE && remaining-- <= 0) {
                done = true;
                closeIterator(context, iterator);
                return context.hasPendingException()
                        ? context.getPendingException()
                        : iteratorResult(context, JSUndefined.INSTANCE, true);
            }
            while (true) {
                IteratorStep step = iteratorStep(context, iterator, nextMethod);
                if (step == null || step.done()) {
                    done = true;
                    return step == null
                            ? context.getPendingException()
                            : iteratorResult(context, JSUndefined.INSTANCE, true);
                }
                if (operation == HelperOperation.DROP && remaining > 0) {
                    remaining--;
                    continue;
                }
                if (callback == null) {
                    return iteratorResult(context, step.value(), false);
                }
                JSValue result = callSafe(context, callback, JSUndefined.INSTANCE,
                        new JSValue[]{step.value(), JSNumber.of(index++)});
                if (context.hasPendingException()) {
                    done = true;
                    closeIteratorIgnoringResult(context, iterator);
                    return context.getPendingException();
                }
                if (operation == HelperOperation.MAP) {
                    return iteratorResult(context, result, false);
                }
                if (JSTypeConversions.toBoolean(result) == JSBoolean.TRUE) {
                    return iteratorResult(context, step.value(), false);
                }
            }
        }

        @Override
        JSValue close(JSContext context, JSValue thisArg, JSValue[] args) {
            if (running) {
                return context.throwTypeError("cannot invoke a running iterator");
            }
            if (done) {
                return completed(context);
            }
            done = true;
            return returnFromHelper(context, iterator, false);
        }
    }

    record IteratorStep(JSValue value, boolean done) {
    }

    /**
     * Specialized CreateIteratorResultObject for iterator helper hot paths. It lazily materializes to a normal object
     * when mutated.
     */
    private static final class JSIteratorResultObject extends JSObject {
        private JSValue doneValue;
        private boolean pristine;
        private JSValue valueValue;

        private JSIteratorResultObject(JSContext context, JSValue valueValue, JSValue doneValue) {
            super(context);
            this.valueValue = valueValue;
            this.doneValue = doneValue;
            this.pristine = true;
        }

        @Override
        public boolean defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
            materialize();
            return super.defineProperty(key, descriptor);
        }

        @Override
        public boolean delete(PropertyKey key, boolean throwOnFailure) {
            materialize();
            return super.delete(key, throwOnFailure);
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
        public JSValue get(PropertyKey key) {
            if (pristine) {
                if (PropertyKey.VALUE.equals(key)) {
                    return valueValue;
                }
                if (PropertyKey.DONE.equals(key)) {
                    return doneValue;
                }
            }
            return super.get(key);
        }

        @Override
        public JSValue get(PropertyKey key, JSValue receiver) {
            if (pristine) {
                if (PropertyKey.VALUE.equals(key)) {
                    return valueValue;
                }
                if (PropertyKey.DONE.equals(key)) {
                    return doneValue;
                }
            }
            return super.get(key, receiver);
        }

        @Override
        protected PropertyDescriptor getOwnPropertyDescriptorRaw(PropertyKey key) {
            if (pristine) {
                if (PropertyKey.VALUE.equals(key)) {
                    return PropertyDescriptor.defaultData(valueValue);
                }
                if (PropertyKey.DONE.equals(key)) {
                    return PropertyDescriptor.defaultData(doneValue);
                }
                return null;
            }
            return super.getOwnPropertyDescriptorRaw(key);
        }

        @Override
        public List<PropertyKey> getOwnPropertyKeys() {
            if (pristine) {
                return ITERATOR_RESULT_KEY_LIST;
            }
            return super.getOwnPropertyKeys();
        }

        @Override
        public boolean hasOwnProperty(PropertyKey key) {
            if (pristine && (PropertyKey.VALUE.equals(key) || PropertyKey.DONE.equals(key))) {
                return true;
            }
            return super.hasOwnProperty(key);
        }

        @Override
        public boolean hasOwnProperty(String propertyName) {
            if (pristine && ("value".equals(propertyName) || "done".equals(propertyName))) {
                return true;
            }
            return super.hasOwnProperty(propertyName);
        }

        private void materialize() {
            if (!pristine) {
                return;
            }
            initProperties(ITERATOR_RESULT_KEYS.clone(), new PropertyDescriptor[]{
                    PropertyDescriptor.defaultData(valueValue), PropertyDescriptor.defaultData(doneValue)},
                    new JSValue[]{valueValue, doneValue});
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
        public void set(String propertyName, JSValue value) {
            materialize();
            super.set(propertyName, value);
        }

    }

    private static final class JSWrapOrHelperIteratorObject extends JSObject {
        private JSFunction internalNext;
        private JSFunction internalReturn;

        private JSWrapOrHelperIteratorObject(JSContext context) {
            super(context);
        }

        private JSFunction getInternalNext() {
            return internalNext;
        }

        private JSFunction getInternalReturn() {
            return internalReturn;
        }

        private void setInternalNext(JSFunction internalNext) {
            this.internalNext = internalNext;
        }

        private void setInternalReturn(JSFunction internalReturn) {
            this.internalReturn = internalReturn;
        }
    }

    private enum PredicateOperation {
        EVERY, FIND, FOR_EACH, SOME
    }
}
