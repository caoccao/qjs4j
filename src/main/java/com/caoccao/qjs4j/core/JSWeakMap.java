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

import com.caoccao.qjs4j.exceptions.JSTypeErrorException;

/**
 * Represents a JavaScript WeakMap object.
 * Keys must be objects and are weakly referenced.
 * WeakMaps are not enumerable.
 * <p>
 * The map holds no reference to its keys or its values: an entry lives on the key, in that key's
 * {@link JSWeakEntryTable}, and names this map by identity. That is what makes the collection an
 * ephemeron — a value reachable only through its entry cannot keep its own key alive — and what
 * makes lookup use {@code ==} rather than {@code equals}. See {@link JSWeakEntryTable} for why
 * both properties needed the storage inverted.
 */
public final class JSWeakMap extends JSObject {
    public static final String NAME = "WeakMap";

    /**
     * Create an empty WeakMap.
     */
    public JSWeakMap(JSContext context) {
        super(context);
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
        JSWeakMap weakMapObj = context.createJSWeakMap();
        initializePrototypeFromNewTarget(context, weakMapObj);
        if (context.hasPendingException()) {
            return returnAbruptResult(context, weakMapObj);
        }

        if (args.length > 0 && !(args[0] instanceof JSUndefined) && !(args[0] instanceof JSNull)) {
            JSValue iterableArg = args[0];

            JSValue adder = weakMapObj.get(PropertyKey.SET);
            if (context.hasPendingException()) {
                return returnAbruptResult(context, weakMapObj);
            }
            if (!(adder instanceof JSFunction adderFunction)) {
                return context.throwTypeError("set/add is not a function");
            }

            JSValue iterator = JSIteratorHelper.getIterator(context, iterableArg);
            if (context.hasPendingException()) {
                return returnAbruptResult(context, weakMapObj);
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
                    return returnAbruptResult(context, weakMapObj);
                }
                if (nextResult == null) {
                    return context.throwTypeError("Iterator result must be an object");
                }

                JSValue done = nextResult.get(PropertyKey.DONE);
                if (context.hasPendingException()) {
                    return returnAbruptResult(context, weakMapObj);
                }
                if (JSTypeConversions.toBoolean(done).isBooleanTrue()) {
                    break;
                }

                JSValue entry = nextResult.get(PropertyKey.VALUE);
                if (context.hasPendingException()) {
                    return returnAbruptResult(context, weakMapObj);
                }
                if (!(entry instanceof JSObject entryObj)) {
                    closeIterator(context, iterator);
                    return context.throwTypeError("Iterator value must be an object");
                }

                JSValue key = entryObj.get(PropertyKey.ZERO);
                if (context.hasPendingException()) {
                    closeIterator(context, iterator);
                    return returnAbruptResult(context, weakMapObj);
                }
                JSValue value = entryObj.get(PropertyKey.ONE);
                if (context.hasPendingException()) {
                    closeIterator(context, iterator);
                    return returnAbruptResult(context, weakMapObj);
                }
                try {
                    adderFunction.call(context, weakMapObj, new JSValue[]{key, value});
                } catch (RuntimeException e) {
                    closeIterator(context, iterator);
                    throw e;
                }
                if (context.hasPendingException()) {
                    closeIterator(context, iterator);
                    return returnAbruptResult(context, weakMapObj);
                }
            }
        }
        return weakMapObj;
    }

    /**
     * The entry table on a key, or {@code null} when the key holds none and none is wanted.
     *
     * @param key    the key
     * @param create true to create the table
     * @return the table, or {@code null}
     */
    private static JSWeakEntryTable entriesOf(JSValue key, boolean create) {
        // The same predicate the guest-facing prototype methods use. Testing only the Java type
        // here let the public Java API create state JavaScript itself is forbidden to create: a
        // registered symbol is deliberately not a valid weak key, because Symbol.for keeps it
        // reachable for the life of the realm and it can never be collected.
        if (!isWeakMapKey(key)) {
            return null;
        }
        if (key instanceof JSObject keyObject) {
            return keyObject.weakEntries(create);
        }
        return ((JSSymbol) key).weakEntries(create);
    }

    private static void initializePrototypeFromNewTarget(JSContext context, JSWeakMap weakMapObject) {
        JSValue newTarget = context.getNativeConstructorNewTarget();
        if (!(newTarget instanceof JSObject newTargetObject)) {
            return;
        }
        JSObject resolvedPrototype = context.getPrototypeFromConstructor(newTargetObject, JSWeakMap.NAME);
        if (context.hasPendingException()) {
            return;
        }
        if (resolvedPrototype != null) {
            weakMapObject.setPrototype(resolvedPrototype);
        }
    }

    /**
     * Whether a value may be held weakly as a key.
     * <p>
     * Objects and unregistered symbols can, per ES2024 CanBeHeldWeakly. A symbol from
     * {@code Symbol.for} cannot: the global registry keeps it alive for the realm's lifetime, so an
     * entry keyed on one could never be collected.
     *
     * @param key the candidate key
     * @return true when the key can be held weakly
     */
    public static boolean isWeakMapKey(JSValue key) {
        if (key instanceof JSObject) {
            return true;
        }
        if (key instanceof JSSymbol s) {
            return !s.isRegistered();
        }
        return false;
    }

    private static JSObject returnAbruptResult(JSContext context, JSWeakMap fallbackObject) {
        JSValue pendingException = context.getPendingException();
        if (pendingException instanceof JSObject pendingObject) {
            return pendingObject;
        }
        return fallbackObject;
    }

    /**
     * Let go of the values held for collections that have themselves been collected.
     * <p>
     * Any weak-collection operation drains the runtime's queue, so a dead collection's values are
     * released without anything having to touch the particular keys it used — which is what pruning
     * on access could never do.
     */
    private void releaseDeadEntries() {
        context.getRuntime().releaseDeadWeakCollectionEntries();
    }

    @Override
    public String toString() {
        return "[object WeakMap]";
    }

    /**
     * Delete a key from the WeakMap.
     */
    public boolean weakMapDelete(JSValue key) {
        releaseDeadEntries();
        JSWeakEntryTable entries = entriesOf(key, false);
        return entries != null && entries.remove(this);
    }

    /**
     * Get a value from the WeakMap by key.
     */
    public JSValue weakMapGet(JSValue key) {
        releaseDeadEntries();
        JSWeakEntryTable entries = entriesOf(key, false);
        JSValue value = entries == null ? null : entries.get(this);
        return value != null ? value : JSUndefined.INSTANCE;
    }

    /**
     * Check if the WeakMap has a key.
     */
    public boolean weakMapHas(JSValue key) {
        releaseDeadEntries();
        JSWeakEntryTable entries = entriesOf(key, false);
        return entries != null && entries.has(this);
    }

    /**
     * Set a key-value pair in the WeakMap.
     * <p>
     * The key must be an object or an <em>unregistered</em> symbol, exactly as
     * {@code WeakMap.prototype.set} requires — see {@link #isWeakMapKey(JSValue)}. The value may be
     * any value, including {@code undefined}.
     *
     * @param key   the key
     * @param value the value
     * @throws JSTypeErrorException when the key cannot be held weakly
     */
    public void weakMapSet(JSValue key, JSValue value) {
        releaseDeadEntries();
        JSWeakEntryTable entries = entriesOf(key, true);
        if (entries == null) {
            throw new JSTypeErrorException("Invalid WeakMap key type");
        }
        // A WeakMap value of undefined must still register the key as present, so the entry table
        // stores JSUndefined.INSTANCE rather than null and `has` tests for an entry, not a value.
        entries.put(this, value == null ? JSUndefined.INSTANCE : value, context.getRuntime().weakCollectionOwners());
    }
}
