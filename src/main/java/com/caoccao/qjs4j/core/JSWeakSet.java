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
 * Represents a JavaScript WeakSet object.
 * Values must be objects and are weakly referenced.
 * WeakSets are not enumerable.
 * <p>
 * Membership lives on the member, in its {@link JSWeakEntryTable}, and names this set by identity —
 * see {@link JSWeakMap} and {@link JSWeakEntryTable}.
 */
public final class JSWeakSet extends JSObject {
    public static final String NAME = "WeakSet";

    /**
     * Create an empty WeakSet.
     */
    public JSWeakSet(JSContext context) {
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

    /**
     * The entry table on a member, or {@code null} when it holds none and none is wanted.
     *
     * @param value  the member
     * @param create true to create the table
     * @return the table, or {@code null}
     */
    private static JSWeakEntryTable entriesOf(JSValue value, boolean create) {
        // The same predicate the guest-facing prototype methods use. Testing only the Java type
        // here let the public Java API create state JavaScript itself is forbidden to create: a
        // registered symbol is deliberately not a valid weak member, because Symbol.for keeps it
        // reachable for the life of the realm and it can never be collected.
        if (!isWeakSetValue(value)) {
            return null;
        }
        if (value instanceof JSObject valueObject) {
            return valueObject.weakEntries(create);
        }
        return ((JSSymbol) value).weakEntries(create);
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

    /**
     * Whether a value may be held weakly as a member.
     * <p>
     * Objects and unregistered symbols can, per ES2024 CanBeHeldWeakly. A symbol from
     * {@code Symbol.for} cannot: the global registry keeps it alive for the realm's lifetime, so a
     * membership keyed on one could never be collected.
     *
     * @param value the candidate member
     * @return true when the value can be held weakly
     */
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
        return "[object WeakSet]";
    }

    /**
     * Add a value to the WeakSet.
     * <p>
     * The value must be an object or an <em>unregistered</em> symbol, exactly as
     * {@code WeakSet.prototype.add} requires — see {@link #isWeakSetValue(JSValue)}.
     *
     * @param value the member to add
     * @throws JSTypeErrorException when the value cannot be held weakly
     */
    public void weakSetAdd(JSValue value) {
        releaseDeadEntries();
        JSWeakEntryTable entries = entriesOf(value, true);
        if (entries == null) {
            throw new JSTypeErrorException("Invalid WeakSet value type");
        }
        entries.put(this, JSWeakEntryTable.PRESENT, context.getRuntime().weakCollectionOwners());
    }

    /**
     * Delete a value from the WeakSet.
     */
    public boolean weakSetDelete(JSValue value) {
        releaseDeadEntries();
        JSWeakEntryTable entries = entriesOf(value, false);
        return entries != null && entries.remove(this);
    }

    /**
     * Check if the WeakSet has a value.
     */
    public boolean weakSetHas(JSValue value) {
        releaseDeadEntries();
        JSWeakEntryTable entries = entriesOf(value, false);
        return entries != null && entries.has(this);
    }
}
