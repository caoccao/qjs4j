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

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Represents a JavaScript WeakSet object.
 * Values must be objects and are weakly referenced.
 * WeakSets are not enumerable.
 */
public final class JSWeakSet extends JSObject {
    public static final String NAME = "WeakSet";
    private final Set<JSObject> objectData;
    private final Set<JSSymbol> symbolData;

    /**
     * Create an empty WeakSet.
     */
    public JSWeakSet(JSContext context) {
        super(context);
        this.objectData = Collections.newSetFromMap(new WeakHashMap<>());
        this.symbolData = Collections.newSetFromMap(new WeakHashMap<>());
    }

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
                // Preserve the original abrupt completion.
            }
        }
        if (pendingException != null) {
            context.clearPendingException();
            context.setPendingException(pendingException);
        }
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSWeakSet weakSetObj = context.createJSWeakSet();
        initializePrototypeFromNewTarget(context, weakSetObj);
        if (context.hasPendingException()) {
            return returnAbruptResult(context, weakSetObj);
        }

        if (args.length > 0 && !(args[0] instanceof JSUndefined) && !(args[0] instanceof JSNull)) {
            JSValue iterableArg = args[0];

            JSValue adder = weakSetObj.get(PropertyKey.fromString("add"));
            if (context.hasPendingException()) {
                return returnAbruptResult(context, weakSetObj);
            }
            if (!(adder instanceof JSFunction adderFunction)) {
                return context.throwTypeError("set/add is not a function");
            }

            JSValue iterator = JSIteratorHelper.getIterator(context, iterableArg);
            if (context.hasPendingException()) {
                return returnAbruptResult(context, weakSetObj);
            }
            if (!(iterator instanceof JSObject)) {
                return context.throwTypeError("Object is not iterable");
            }

            while (true) {
                JSObject nextResult;
                try {
                    nextResult = JSIteratorHelper.iteratorNext(iterator, context);
                } catch (RuntimeException e) {
                    throw e;
                }
                if (context.hasPendingException()) {
                    return returnAbruptResult(context, weakSetObj);
                }
                if (nextResult == null) {
                    return context.throwTypeError("Iterator result must be an object");
                }

                JSValue done = nextResult.get(PropertyKey.DONE);
                if (context.hasPendingException()) {
                    return returnAbruptResult(context, weakSetObj);
                }
                if (JSTypeConversions.toBoolean(done).isBooleanTrue()) {
                    break;
                }

                JSValue value = nextResult.get(PropertyKey.VALUE);
                if (context.hasPendingException()) {
                    return returnAbruptResult(context, weakSetObj);
                }
                try {
                    adderFunction.call(context, weakSetObj, new JSValue[]{value});
                } catch (RuntimeException e) {
                    closeIterator(context, iterator);
                    throw e;
                }
                if (context.hasPendingException()) {
                    closeIterator(context, iterator);
                    return returnAbruptResult(context, weakSetObj);
                }
            }
        }
        return weakSetObj;
    }

    private static void initializePrototypeFromNewTarget(JSContext context, JSWeakSet weakSetObject) {
        JSValue newTarget = context.getNativeConstructorNewTarget();
        if (!(newTarget instanceof JSObject newTargetObject)) {
            return;
        }
        JSObject resolvedPrototype = context.getPrototypeFromConstructor(newTargetObject, JSWeakSet.NAME);
        if (context.hasPendingException()) {
            return;
        }
        if (resolvedPrototype != null) {
            weakSetObject.setPrototype(resolvedPrototype);
        }
    }

    public static boolean isWeakSetValue(JSValue value) {
        if (value instanceof JSObject) {
            return true;
        }
        if (value instanceof JSSymbol s) {
            return !s.isRegistered();
        }
        return false;
    }

    private static JSObject returnAbruptResult(JSContext context, JSWeakSet fallbackObject) {
        JSValue pendingException = context.getPendingException();
        if (pendingException instanceof JSObject pendingObject) {
            return pendingObject;
        }
        return fallbackObject;
    }

    @Override
    public String toString() {
        return "[object WeakSet]";
    }

    /**
     * Add a value to the WeakSet.
     * Value must be an object.
     */
    public void weakSetAdd(JSValue value) {
        if (value instanceof JSObject valueObject) {
            objectData.add(valueObject);
        } else if (value instanceof JSSymbol symbolValue) {
            symbolData.add(symbolValue);
        } else {
            throw new IllegalArgumentException("Invalid WeakSet value type");
        }
    }

    /**
     * Delete a value from the WeakSet.
     */
    public boolean weakSetDelete(JSValue value) {
        if (value instanceof JSObject valueObject) {
            return objectData.remove(valueObject);
        }
        if (value instanceof JSSymbol symbolValue) {
            return symbolData.remove(symbolValue);
        }
        return false;
    }

    /**
     * Check if the WeakSet has a value.
     */
    public boolean weakSetHas(JSValue value) {
        if (value instanceof JSObject valueObject) {
            return objectData.contains(valueObject);
        }
        if (value instanceof JSSymbol symbolValue) {
            return symbolData.contains(symbolValue);
        }
        return false;
    }
}
