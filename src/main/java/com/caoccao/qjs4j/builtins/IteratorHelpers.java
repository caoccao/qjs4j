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
import java.util.*;
import static com.caoccao.qjs4j.builtins.IteratorPrototype.*;

/** State and algorithms for the lazy Iterator helpers and Iterator.concat. */
final class IteratorHelpers {
    private IteratorHelpers() {
    }

    /**
     * Iterator.concat(...iterables) Creates an iterator that concatenates the provided iterables.
     */
    static JSValue concat(JSContext context, JSValue[] args) {
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

    static JSValue createHelper(JSContext context, JSValue thisArg, JSValue[] args, HelperOperation operation) {
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
                context.restoreVMException(exception);
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
        if (operation == HelperOperation.FLAT_MAP) {
            return new FlatMapCursor(iterator, nextMethod, callback).create(context, context, "Iterator Helper");
        }
        IteratorHelper helper = new IteratorHelper(iterator, nextMethod, operation, callback, count);
        return helper.create(context, iterator.getContext(), "Iterator Helper");
    }

    private static JSValue returnFromHelper(JSContext context, JSObject iteratorObject, JSValue returnMethod,
            ReturnMode mode) {
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
            return completed(context);
        }
        if (!(returnMethod instanceof JSFunction returnFunctionValue)) {
            return context.throwTypeError("not a function");
        }
        JSValue result = mode == ReturnMode.SAFE
                ? callSafe(context, returnFunctionValue, iteratorObject, JSValue.NO_ARGS)
                : returnFunctionValue.call(context, iteratorObject, JSValue.NO_ARGS);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (mode == ReturnMode.FORWARD || result instanceof JSObject) {
            return result;
        }
        return iteratorResult(context, result, true);
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
        JSValue close(JSContext context) {
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
                return returnFromHelper(context, iter, returnMethod, ReturnMode.FORWARD);
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
        JSValue close(JSContext context) {
            if (done) {
                return completed(context);
            }
            done = true;
            if (innerIterator != null) {
                closeIteratorIgnoringResult(context, innerIterator);
                innerIterator = null;
                innerNextMethod = JSUndefined.INSTANCE;
            }
            return returnFromHelper(context, iteratorObject, iteratorObject.get(PropertyKey.RETURN), ReturnMode.SAFE);
        }

        private JSValue fail(JSContext context, String typeError) {
            done = true;
            closeIteratorIgnoringResult(context, iteratorObject);
            return typeError == null ? context.getPendingException() : context.throwTypeError(typeError);
        }
    }

    enum HelperOperation {
        DROP,
        FILTER,
        FLAT_MAP,
        MAP,
        TAKE
    }

    private abstract static class IteratorCursor {
        boolean done;
        boolean running;
        abstract JSValue advance(JSContext context);

        abstract JSValue close(JSContext context);

        final JSValue close(JSContext context, JSValue thisArg, JSValue[] args) {
            if (running) {
                return context.throwTypeError("cannot invoke a running iterator");
            }
            return close(context);
        }

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
        JSValue close(JSContext context) {
            if (done) {
                return completed(context);
            }
            done = true;
            return returnFromHelper(context, iterator, iterator.get(PropertyKey.RETURN), ReturnMode.WRAP);
        }
    }
    private enum ReturnMode {
        FORWARD,
        SAFE,
        WRAP
    }
}
