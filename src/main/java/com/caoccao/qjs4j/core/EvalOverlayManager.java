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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The stack of temporary global-object overlays a module's imports are installed as.
 * <p>
 * This engine has no module environment records yet: an {@code import} binding is installed as a
 * property of the global object for the duration of the module body and taken away again
 * afterwards. A frame records what each overlay displaced — the values that were there
 * ({@code savedGlobals}) and the names that were not there at all ({@code absentKeys}) — so
 * {@link #restoreFrame} can put the realm back exactly as it was.
 * <p>
 * The suspension depth is separate from the stack. Some lookups have to see through the overlays to
 * the realm underneath (an indirect eval reaching the real global scope, for instance), and they
 * raise the depth rather than unwinding the stack, because the overlay must still be there when
 * they finish.
 */
final class EvalOverlayManager {
    private final JSContext context;
    private final Deque<Frame> frames = new ArrayDeque<>();
    private int lookupSuppressionDepth;

    EvalOverlayManager(JSContext context) {
        this.context = context;
        this.lookupSuppressionDepth = 0;
    }

    void clear() {
        frames.clear();
    }

    boolean hasBinding(String name) {
        if (lookupSuppressionDepth > 0) {
            return false;
        }
        for (Frame frame : frames) {
            if (frame.savedGlobals().containsKey(name)
                    || frame.absentKeys().contains(name)) {
                return true;
            }
        }
        return false;
    }

    boolean hasFrames() {
        if (lookupSuppressionDepth > 0) {
            return false;
        }
        return !frames.isEmpty();
    }

    void pop() {
        if (!frames.isEmpty()) {
            frames.pop();
        }
    }

    void popLookupSuppression() {
        if (lookupSuppressionDepth > 0) {
            lookupSuppressionDepth--;
        }
    }

    void push(Map<String, JSValue> savedGlobals, Set<String> absentKeys) {
        frames.push(new Frame(savedGlobals, absentKeys));
    }

    void pushLookupSuppression() {
        lookupSuppressionDepth++;
    }

    /**
     * Restore a frame once the module body it belongs to has settled, rather than when
     * {@code eval} returns.
     * <p>
     * A top-level-await module's body is still running when {@code eval} returns its promise, so
     * taking the overlay away then would remove the module's imports from underneath it.
     *
     * @param asyncModulePromise the module's evaluation promise
     * @param frame              the frame to restore when it settles
     */
    void registerDeferredRestore(JSPromise asyncModulePromise, Frame frame) {
        if (asyncModulePromise == null || frame == null) {
            return;
        }
        JSNativeFunction onFulfill = new JSNativeFunction(context, "", 0,
                (ctx, thisArg, args) -> {
                    restoreFrame(frame);
                    return JSUndefined.INSTANCE;
                });
        onFulfill.initializePrototypeChain(context);
        JSNativeFunction onReject = new JSNativeFunction(context, "", 1,
                (ctx, thisArg, args) -> {
                    restoreFrame(frame);
                    return JSUndefined.INSTANCE;
                });
        onReject.initializePrototypeChain(context);
        asyncModulePromise.addReactions(
                new JSPromise.ReactionRecord(onFulfill, context, null, null),
                new JSPromise.ReactionRecord(onReject, context, null, null));
    }

    void resetLookupSuppression() {
        lookupSuppressionDepth = 0;
    }

    void restoreFrame(Frame frame) {
        if (frame == null) {
            return;
        }
        JSObject globalObject = context.getGlobalObject();
        for (var entry : frame.savedGlobals().entrySet()) {
            PropertyKey key = PropertyKey.fromString(entry.getKey());
            // Use defineProperty to restore data properties, overwriting any accessor
            // properties that were set up for live import bindings.
            globalObject.defineProperty(key, entry.getValue(), PropertyDescriptor.DataState.All);
        }
        for (String absentKey : frame.absentKeys()) {
            globalObject.delete(PropertyKey.fromString(absentKey));
        }
    }

    void resume(JSGlobalObject.EvalOverlaySnapshot evalOverlaySnapshot) {
        if (evalOverlaySnapshot == null) {
            return;
        }
        JSObject globalObject = context.getGlobalObject();
        for (var entry : evalOverlaySnapshot.values().entrySet()) {
            globalObject.set(PropertyKey.fromString(entry.getKey()), entry.getValue());
        }
        for (String absentKey : evalOverlaySnapshot.absentKeys()) {
            globalObject.delete(PropertyKey.fromString(absentKey));
        }
    }

    JSGlobalObject.EvalOverlaySnapshot suspend() {
        if (frames.isEmpty()) {
            return null;
        }
        JSObject globalObject = context.getGlobalObject();
        Set<String> overlaidKeys = new HashSet<>();
        for (Frame frame : frames) {
            overlaidKeys.addAll(frame.savedGlobals().keySet());
            overlaidKeys.addAll(frame.absentKeys());
        }

        Map<String, JSValue> suspendedValues = new HashMap<>();
        Set<String> suspendedAbsentKeys = new HashSet<>();
        for (String key : overlaidKeys) {
            PropertyKey propertyKey = PropertyKey.fromString(key);
            if (globalObject.has(propertyKey)) {
                suspendedValues.put(key, globalObject.get(propertyKey));
            } else {
                suspendedAbsentKeys.add(key);
            }
        }

        Iterator<Frame> descendingIterator = frames.descendingIterator();
        while (descendingIterator.hasNext()) {
            Frame frame = descendingIterator.next();
            for (var entry : frame.savedGlobals().entrySet()) {
                PropertyKey overlayKey = PropertyKey.fromString(entry.getKey());
                PropertyDescriptor currentDescriptor = globalObject.getOwnPropertyDescriptor(overlayKey);
                if (currentDescriptor != null
                        && currentDescriptor.isDataDescriptor()
                        && !currentDescriptor.isWritable()) {
                    globalObject.defineProperty(overlayKey, entry.getValue(), PropertyDescriptor.DataState.All);
                } else {
                    globalObject.set(overlayKey, entry.getValue());
                }
            }
            for (String absentKey : frame.absentKeys()) {
                globalObject.delete(PropertyKey.fromString(absentKey));
            }
        }
        return new JSGlobalObject.EvalOverlaySnapshot(suspendedValues, suspendedAbsentKeys);
    }

    /**
     * What one overlay displaced on the global object.
     *
     * @param savedGlobals the values the overlaid names held before the overlay was installed
     * @param absentKeys   the overlaid names that had no value at all, so restoring means deleting
     */
    record Frame(Map<String, JSValue> savedGlobals, Set<String> absentKeys) {
    }
}
