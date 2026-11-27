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

import static com.caoccao.qjs4j.builtins.IteratorPrototype.*;

/** State and iterator acquisition shared by Iterator.zip and Iterator.zipKeyed. */
final class IteratorZip {
    private final List<IteratorRecord> iterators;
    private final PropertyKey[] keys;
    private final JSValue[] keyStrings;
    private final String mode;
    private final JSValue[] padding;
    private int remaining;
    private State state = State.START;

    private IteratorZip(List<IteratorRecord> iterators, String mode, JSValue[] padding, PropertyKey[] keys) {
        this.iterators = iterators;
        this.mode = mode;
        this.padding = padding;
        this.keys = keys;
        keyStrings = cacheKeyStrings(keys);
        remaining = iterators.size();
    }

    private JSValue buildZipResult(JSContext context, JSValue[] values) {
        if (keys == null) {
            return context.createJSArray(values, true);
        }
        JSObject row = keyStrings == null
                ? new JSObject(context)
                : new JSZipKeyedResultObject(context, keys, keyStrings);
        PropertyDescriptor[] descriptors = new PropertyDescriptor[keys.length];
        for (int i = 0; i < keys.length; i++) {
            descriptors[i] = PropertyDescriptor.defaultData(values[i]);
        }
        row.initProperties(keys.clone(), descriptors, values);
        return row;
    }

    private JSValue close(JSContext context, JSValue receiver, JSValue[] args) {
        if (state == State.EXECUTING) {
            return context.throwTypeError("generator is already running");
        }
        if (state == State.DONE) {
            return completed(context);
        }
        // A return before the first next is already complete during IteratorClose.
        state = state == State.START ? State.DONE : State.EXECUTING;
        try {
            return closeAll(context, iterators, null);
        } finally {
            state = State.DONE;
        }
    }

    private JSValue create(JSContext context) {
        return createIteratorObject(context, new JSNativeFunction(context, "next", 0, this::next),
                new JSNativeFunction(context, "return", 0, this::close), "Iterator Helper");
    }

    private JSValue differentLengths(JSContext context) {
        return finish(context, context.throwTypeError("iterators have different lengths"));
    }

    private JSValue finish(JSContext context, JSValue error) {
        state = State.DONE;
        return closeAll(context, iterators, error);
    }

    private JSValue finishStrict(JSContext context, int exhausted) {
        if (exhausted != 0) {
            return differentLengths(context);
        }
        // Only the first iterator may signal the end of a strict zip.
        for (int i = 1; i < iterators.size(); i++) {
            IteratorStep next = step(context, i, iterators.get(i));
            if (next == null) {
                return finish(context, context.getPendingException());
            }
            if (!next.done()) {
                return differentLengths(context);
            }
        }
        state = State.DONE;
        return completed(context);
    }

    private JSValue next(JSContext context, JSValue receiver, JSValue[] args) {
        if (state == State.EXECUTING) {
            return context.throwTypeError("generator is already running");
        }
        if (state == State.DONE || remaining == 0) {
            state = State.DONE;
            return completed(context);
        }
        state = State.EXECUTING;
        try {
            JSValue[] values = new JSValue[iterators.size()];
            for (int i = 0; i < values.length; i++) {
                IteratorRecord record = iterators.get(i);
                if (record != null) {
                    IteratorStep next = step(context, i, record);
                    if (next == null) {
                        return finish(context, context.getPendingException());
                    }
                    if (!next.done()) {
                        values[i] = next.value();
                        continue;
                    }
                    if ("shortest".equals(mode)) {
                        return finish(context, null);
                    }
                    if ("strict".equals(mode)) {
                        return finishStrict(context, i);
                    }
                }
                values[i] = padding != null && padding[i] != null ? padding[i] : JSUndefined.INSTANCE;
            }
            if (remaining == 0) {
                state = State.DONE;
                return completed(context);
            }
            JSValue row = buildZipResult(context, values);
            state = State.YIELD;
            return iteratorResult(context, row, false);
        } catch (RuntimeException e) {
            state = State.DONE;
            throw e;
        }
    }

    private IteratorStep step(JSContext context, int index, IteratorRecord record) {
        IteratorStep result = iteratorStep(context, record.iterator(), record.nextMethod());
        if (result == null || result.done()) {
            iterators.set(index, null);
            remaining--;
        }
        return result;
    }

    private static JSValue[] cacheKeyStrings(PropertyKey[] keys) {
        if (keys == null) {
            return null;
        }
        JSValue[] strings = new JSValue[keys.length];
        for (int i = 0; i < keys.length; i++) {
            if (!keys[i].isString()) {
                return null;
            }
            strings[i] = new JSString(keys[i].asString());
        }
        return strings;
    }

    private static JSValue closeAll(JSContext context, List<IteratorRecord> records, JSValue error) {
        // IteratorCloseAll closes every remaining iterator in reverse order, preserving the first error.
        for (int i = records.size() - 1; i >= 0; i--) {
            IteratorRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            context.clearPendingException();
            JSObject iterator = record.iterator();
            try {
                JSValue method = iterator.get(PropertyKey.RETURN);
                if (!context.hasPendingException() && !method.isNullOrUndefined()) {
                    if (method instanceof JSFunction function) {
                        JSValue result = callSafe(context, function, iterator, JSValue.NO_ARGS);
                        if (error == null && !context.hasPendingException() && !(result instanceof JSObject)) {
                            context.throwTypeError("return did not return an object");
                        }
                    } else if (error == null) {
                        context.throwTypeError("return is not a function");
                    }
                }
            } catch (JSVirtualMachineException e) {
                context.restoreVMException(e);
            }
            if (error == null) {
                error = context.getPendingException();
            }
        }
        context.clearPendingException();
        if (error != null) {
            context.setPendingException(error);
            return error;
        }
        return completed(context);
    }

    /**
     * Get and validate the "mode" option. Returns "shortest", "longest", or "strict".
     */
    private static String getAndValidateMode(JSContext context, JSObject options) {
        JSValue modeValue = options.get(PropertyKey.fromString("mode"));
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

    private static IteratorRecord getIterator(JSContext context, JSObject iterable, String missing, String invalid) {
        JSValue method = iterable.get(PropertyKey.SYMBOL_ITERATOR);
        if (context.hasPendingException()) {
            return null;
        }
        if (!(method instanceof JSFunction function)) {
            context.throwTypeError(missing);
            return null;
        }
        JSValue result = callSafe(context, function, iterable, JSValue.NO_ARGS);
        if (context.hasPendingException()) {
            return null;
        }
        if (!(result instanceof JSObject iterator)) {
            context.throwTypeError(invalid);
            return null;
        }
        JSValue next = iterator.get(PropertyKey.NEXT);
        return context.hasPendingException() ? null : new IteratorRecord(iterator, next);
    }

    /**
     * GetIteratorFlattenable(obj, reject-strings)
     */
    private static IteratorRecord getIteratorFlattenable(JSContext context, JSValue obj) {
        if (!(obj instanceof JSObject objObject)) {
            context.throwTypeError("value is not an object");
            return null;
        }

        JSValue iteratorMethod = objObject.get(PropertyKey.SYMBOL_ITERATOR);
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
            JSValue iterValue = callSafe(context, iterFunc, objObject, JSValue.NO_ARGS);
            if (context.hasPendingException()) {
                return null;
            }
            if (!(iterValue instanceof JSObject iterObj)) {
                context.throwTypeError("iterator is not an object");
                return null;
            }
            iterator = iterObj;
        }

        JSValue nextMethod = iterator.get(PropertyKey.NEXT);
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

    static JSValue zipIterables(JSContext context, JSValue[] args, boolean keyed) {
        JSValue iterablesArg = JSValue.argumentOrUndefined(args, 0);
        JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);

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
            paddingOption = options.get(PropertyKey.fromString("padding"));
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (!(paddingOption instanceof JSUndefined) && !(paddingOption instanceof JSObject)) {
                return context.throwTypeError("padding must be an object or undefined");
            }
        }

        if (keyed) {
            return zipProperties(context, iterablesObject, mode,
                    paddingOption instanceof JSObject object ? object : null);
        }
        return zipSequence(context, iterablesObject, mode, paddingOption);
    }

    private static JSValue zipProperties(JSContext context, JSObject object, String mode, JSObject paddingObject) {
        List<IteratorRecord> records = new ArrayList<>();
        try {
            List<PropertyKey> ownKeys = object.getOwnPropertyKeys();
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            PropertyKey[] keys = new PropertyKey[ownKeys.size()];
            for (PropertyKey key : ownKeys) {
                boolean enumerable = object.isOwnPropertyEnumerable(key);
                if (context.hasPendingException()) {
                    return closeAll(context, records, context.getPendingException());
                }
                if (!enumerable) {
                    continue;
                }
                JSValue value = object.get(key);
                if (context.hasPendingException()) {
                    return closeAll(context, records, context.getPendingException());
                }
                if (!(value instanceof JSUndefined)) {
                    IteratorRecord record = getIteratorFlattenable(context, value);
                    if (record == null) {
                        return closeAll(context, records, context.getPendingException());
                    }
                    keys[records.size()] = key;
                    records.add(record);
                }
            }
            JSValue[] padding = null;
            if (paddingObject != null) {
                padding = new JSValue[records.size()];
                for (int i = 0; i < padding.length; i++) {
                    padding[i] = paddingObject.get(keys[i]);
                    if (context.hasPendingException()) {
                        return closeAll(context, records, context.getPendingException());
                    }
                }
            }
            if (records.size() != keys.length) {
                keys = Arrays.copyOf(keys, records.size());
            }
            return new IteratorZip(records, mode, padding, keys).create(context);
        } catch (JSVirtualMachineException e) {
            context.restoreVMException(e);
            return closeAll(context, records, context.getPendingException());
        }
    }

    private static JSValue zipSequence(JSContext context, JSObject iterable, String mode, JSValue paddingOption) {
        IteratorRecord input = getIterator(context, iterable, "iterables is not iterable",
                "iterator result is not an object");
        if (input == null) {
            return context.getPendingException();
        }
        List<IteratorRecord> records = new ArrayList<>();
        while (true) {
            IteratorStep step = iteratorStep(context, input.iterator(), input.nextMethod());
            if (step == null) {
                return closeAll(context, records, context.getPendingException());
            }
            if (step.done()) {
                break;
            }
            IteratorRecord record = getIteratorFlattenable(context, step.value());
            if (record == null) {
                records.add(0, input); // Close the input after the iterators it produced.
                return closeAll(context, records, context.getPendingException());
            }
            records.add(record);
        }

        JSValue[] padding = null;
        if (paddingOption instanceof JSObject object) {
            IteratorRecord pad = getIterator(context, object, "padding is not iterable",
                    "padding iterator is not an object");
            if (pad == null) {
                return closeAll(context, records, context.getPendingException());
            }
            padding = new JSValue[records.size()];
            int i = 0;
            for (; i < padding.length; i++) {
                IteratorStep step = iteratorStep(context, pad.iterator(), pad.nextMethod());
                if (step == null) {
                    return closeAll(context, records, context.getPendingException());
                }
                if (step.done()) {
                    break;
                }
                padding[i] = step.value();
            }
            if (i == padding.length) {
                closeIterator(context, pad.iterator());
                if (context.hasPendingException()) {
                    return closeAll(context, records, context.getPendingException());
                }
            }
        }
        return new IteratorZip(records, mode, padding, null).create(context);
    }

    private record IteratorRecord(JSObject iterator, JSValue nextMethod) {
    }

    /**
     * Specialized zipKeyed result object that caches key strings for Object.keys(). It falls back automatically if the
     * object shape becomes non-trivial.
     */
    private static final class JSZipKeyedResultObject extends JSObject {
        private final JSValue[] cachedKeyStrings;
        private final PropertyKey[] expectedKeys;
        private boolean pristine;

        private JSZipKeyedResultObject(JSContext context, PropertyKey[] expectedKeys, JSValue[] cachedKeyStrings) {
            super(context);
            this.expectedKeys = expectedKeys;
            this.cachedKeyStrings = cachedKeyStrings;
            this.pristine = true;
        }

        @Override
        public boolean defineProperty(PropertyKey key, PropertyDescriptor descriptor) {
            pristine = false;
            return super.defineProperty(key, descriptor);
        }

        @Override
        public boolean delete(PropertyKey key, boolean throwOnFailure) {
            pristine = false;
            return super.delete(key, throwOnFailure);
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
        public void set(String propertyName, JSValue value) {
            pristine = false;
            super.set(propertyName, value);
        }

    }

    private enum State {
        DONE,
        EXECUTING,
        START,
        YIELD
    }
}
