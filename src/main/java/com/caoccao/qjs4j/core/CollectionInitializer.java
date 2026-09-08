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

package com.caoccao.qjs4j.core;

/**
 * Shared iterable initialization for Map, Set, WeakMap, and WeakSet. Each collection retains its own storage and adder
 * while sharing the constructor protocol.
 */
final class CollectionInitializer {
    private CollectionInitializer() {
    }

    /**
     * Close after an abrupt completion, preserving its pending exception.
     */
    private static void closeIterator(JSContext context, JSValue iterator) {
        if (!(iterator instanceof JSObject iteratorObject)) {
            return;
        }
        JSValue pendingException = context.getPendingException();
        if (pendingException != null) {
            context.clearPendingException();
        }
        JSValue returnMethod = iteratorObject.get(PropertyKey.RETURN);
        if (returnMethod instanceof JSFunction returnFunction) {
            try {
                returnFunction.call(context, iterator, JSValue.NO_ARGS);
            } catch (RuntimeException ignored) {
                // Per spec, the original error takes precedence over iterator close errors
            }
        }
        // Clear any exception set by the return call, then restore the original
        if (pendingException != null) {
            context.clearPendingException();
            context.setPendingException(pendingException);
        }
    }

    static JSObject initializeFromIterable(JSContext context, JSObject collection, JSValue[] args, boolean entries) {
        if (args.length == 0 || args[0] instanceof JSUndefined || args[0] instanceof JSNull) {
            return collection;
        }
        JSValue adder = collection.get(entries ? PropertyKey.SET : PropertyKey.fromString("add"));
        if (context.hasPendingException()) {
            return returnAbruptResult(context, collection);
        }
        if (!(adder instanceof JSFunction adderFunction)) {
            return context.throwTypeError("set/add is not a function");
        }

        JSIteratorHelper.IteratorRecord iteratorRecord = JSIteratorHelper.getIteratorRecord(context, args[0]);
        if (context.hasPendingException()) {
            return returnAbruptResult(context, collection);
        }
        if (iteratorRecord == null) {
            return context.throwTypeError("Object is not iterable");
        }
        JSObject iterator = iteratorRecord.iterator();

        while (true) {
            JSValue result = iteratorRecord.nextMethod().call(context, iterator, JSValue.NO_ARGS);
            // Failures from advancing the iterator do not close it.
            if (context.hasPendingException()) {
                return returnAbruptResult(context, collection);
            }
            if (!(result instanceof JSObject nextResult)) {
                return context.throwTypeError("Iterator result must be an object");
            }
            JSValue done = nextResult.get(PropertyKey.DONE);
            if (context.hasPendingException()) {
                return returnAbruptResult(context, collection);
            }
            if (JSTypeConversions.toBoolean(done).isBooleanTrue()) {
                return collection;
            }
            JSValue item = nextResult.get(PropertyKey.VALUE);
            if (context.hasPendingException()) {
                return returnAbruptResult(context, collection);
            }

            JSValue[] adderArgs;
            if (entries) {
                if (!(item instanceof JSObject entry)) {
                    closeIterator(context, iterator);
                    return context.throwTypeError("Iterator value must be an object");
                }
                JSValue key = entry.get(PropertyKey.ZERO);
                if (context.hasPendingException()) {
                    closeIterator(context, iterator);
                    return returnAbruptResult(context, collection);
                }
                JSValue value = entry.get(PropertyKey.ONE);
                if (context.hasPendingException()) {
                    closeIterator(context, iterator);
                    return returnAbruptResult(context, collection);
                }
                adderArgs = new JSValue[]{key, value};
            } else {
                adderArgs = new JSValue[]{item};
            }

            try {
                adderFunction.call(context, collection, adderArgs);
            } catch (RuntimeException e) {
                closeIterator(context, iterator);
                throw e;
            }
            if (context.hasPendingException()) {
                closeIterator(context, iterator);
                return returnAbruptResult(context, collection);
            }
        }
    }

    static void initializePrototypeFromNewTarget(JSContext context, JSObject collection, String name) {
        JSValue newTarget = context.getNativeConstructorNewTarget();
        if (!(newTarget instanceof JSObject newTargetObject)) {
            return;
        }
        JSObject resolvedPrototype = context.getPrototypeFromConstructor(newTargetObject, name);
        if (context.hasPendingException()) {
            return;
        }
        if (resolvedPrototype != null) {
            collection.setPrototype(resolvedPrototype);
        }
    }

    static JSObject returnAbruptResult(JSContext context, JSObject fallbackObject) {
        JSValue pendingException = context.getPendingException();
        if (pendingException instanceof JSObject pendingObject) {
            return pendingObject;
        }
        return fallbackObject;
    }
}
