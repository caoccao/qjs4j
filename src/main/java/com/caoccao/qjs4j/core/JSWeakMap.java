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

import java.util.WeakHashMap;

/**
 * Represents a JavaScript WeakMap object.
 * Keys must be objects and are weakly referenced.
 * WeakMaps are not enumerable.
 */
public final class JSWeakMap extends JSObject {
    public static final String NAME = "WeakMap";
    private final WeakHashMap<JSObject, JSValue> objectData;
    private final WeakHashMap<JSSymbol, JSValue> symbolData;

    /**
     * Create an empty WeakMap.
     */
    public JSWeakMap() {
        super();
        this.objectData = new WeakHashMap<>();
        this.symbolData = new WeakHashMap<>();
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
            returnFunction.call(context, iterator, new JSValue[0]);
        }
        if (pendingException != null) {
            context.setPendingException(pendingException);
        }
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSWeakMap weakMapObj = context.createJSWeakMap();

        if (args.length > 0 && !(args[0] instanceof JSUndefined) && !(args[0] instanceof JSNull)) {
            JSValue iterableArg = args[0];

            JSValue adder = weakMapObj.get(context, PropertyKey.SET);
            if (context.hasPendingException()) {
                return weakMapObj;
            }
            if (!(adder instanceof JSFunction adderFunction)) {
                return context.throwTypeError("set/add is not a function");
            }

            JSValue iterator = JSIteratorHelper.getIterator(context, iterableArg);
            if (!(iterator instanceof JSObject)) {
                return context.throwTypeError("Object is not iterable");
            }

            while (true) {
                JSObject nextResult;
                try {
                    nextResult = JSIteratorHelper.iteratorNext(iterator, context);
                } catch (RuntimeException e) {
                    closeIterator(context, iterator);
                    throw e;
                }
                if (nextResult instanceof JSError) {
                    closeIterator(context, iterator);
                    return nextResult;
                }
                if (context.hasPendingException()) {
                    closeIterator(context, iterator);
                    JSValue pendingException = context.getPendingException();
                    if (pendingException instanceof JSObject pendingObject) {
                        return pendingObject;
                    }
                    return context.throwTypeError("WeakMap constructor failed");
                }
                if (nextResult == null) {
                    closeIterator(context, iterator);
                    return context.throwTypeError("Iterator result must be an object");
                }

                JSValue done = nextResult.get(context, PropertyKey.DONE);
                if (context.hasPendingException()) {
                    closeIterator(context, iterator);
                    return weakMapObj;
                }
                if (JSTypeConversions.toBoolean(done).isBooleanTrue()) {
                    break;
                }

                JSValue entry = nextResult.get(context, PropertyKey.VALUE);
                if (context.hasPendingException()) {
                    closeIterator(context, iterator);
                    return weakMapObj;
                }
                if (!(entry instanceof JSObject entryObj)) {
                    closeIterator(context, iterator);
                    return context.throwTypeError("Iterator value must be an object");
                }

                JSValue key = entryObj.get(context, PropertyKey.ZERO);
                if (context.hasPendingException()) {
                    closeIterator(context, iterator);
                    return weakMapObj;
                }
                JSValue value = entryObj.get(context, PropertyKey.ONE);
                if (context.hasPendingException()) {
                    closeIterator(context, iterator);
                    return weakMapObj;
                }
                JSValue adderResult;
                try {
                    adderResult = adderFunction.call(context, weakMapObj, new JSValue[]{key, value});
                } catch (RuntimeException e) {
                    closeIterator(context, iterator);
                    throw e;
                }
                if (adderResult instanceof JSError || context.hasPendingException()) {
                    closeIterator(context, iterator);
                    if (adderResult instanceof JSObject adderResultObject) {
                        return adderResultObject;
                    }
                    JSValue pendingException = context.getPendingException();
                    if (pendingException instanceof JSObject pendingObject) {
                        return pendingObject;
                    }
                    return context.throwTypeError("WeakMap constructor failed");
                }
            }
        }
        return weakMapObj;
    }

    public static boolean isWeakMapKey(JSValue key) {
        if (key instanceof JSObject) return true;
        if (key instanceof JSSymbol s) return !s.isRegistered();
        return false;
    }

    @Override
    public String toString() {
        return "[object WeakMap]";
    }

    /**
     * Delete a key from the WeakMap.
     */
    public boolean weakMapDelete(JSValue key) {
        if (key instanceof JSObject keyObject) {
            return objectData.remove(keyObject) != null;
        }
        if (key instanceof JSSymbol symbolKey) {
            return symbolData.remove(symbolKey) != null;
        }
        return false;
    }

    /**
     * Get a value from the WeakMap by key.
     */
    public JSValue weakMapGet(JSValue key) {
        JSValue value;
        if (key instanceof JSObject keyObject) {
            value = objectData.get(keyObject);
        } else if (key instanceof JSSymbol symbolKey) {
            value = symbolData.get(symbolKey);
        } else {
            value = null;
        }
        return value != null ? value : JSUndefined.INSTANCE;
    }

    /**
     * Check if the WeakMap has a key.
     */
    public boolean weakMapHas(JSValue key) {
        if (key instanceof JSObject keyObject) {
            return objectData.containsKey(keyObject);
        }
        if (key instanceof JSSymbol symbolKey) {
            return symbolData.containsKey(symbolKey);
        }
        return false;
    }

    /**
     * Set a key-value pair in the WeakMap.
     * Key must be an object.
     */
    public void weakMapSet(JSValue key, JSValue value) {
        if (key instanceof JSObject keyObject) {
            objectData.put(keyObject, value);
        } else if (key instanceof JSSymbol symbolKey) {
            symbolData.put(symbolKey, value);
        } else {
            throw new IllegalArgumentException("Invalid WeakMap key type");
        }
    }
}
