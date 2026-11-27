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

import com.caoccao.qjs4j.core.promise.JSPromise;

import java.util.*;

/**
 * Temporary global-object overlays used by direct eval to expose caller bindings. Frames retain displaced values and
 * descriptors for restoration. Lookup suppression lets indirect eval reach the realm's global scope. Module imports use
 * {@link JSModuleEnvironment} instead.
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
            if (frame.savedGlobals().containsKey(name) || frame.absentKeys().contains(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an overlay that {@code environment} can see binds {@code name}: one installed by the eval that owns the
     * environment, or by an eval that code is nested in. Overlays of unrelated evals, even while active, do not count.
     */
    boolean hasBinding(String name, JSEvalEnvironment environment) {
        return resolve(name, environment) != null;
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

    void push(Map<String, JSValue> savedGlobals, Map<String, PropertyDescriptor> savedDescriptors,
            Set<String> absentKeys, JSEvalEnvironment environment, Set<String> constantBindings) {
        frames.push(new Frame(savedGlobals, absentKeys, environment, savedDescriptors, constantBindings));
    }

    void push(Map<String, JSValue> savedGlobals, Set<String> absentKeys) {
        push(savedGlobals, absentKeys, null);
    }

    void push(Map<String, JSValue> savedGlobals, Set<String> absentKeys, JSEvalEnvironment environment) {
        push(savedGlobals, Map.of(), absentKeys, environment, Set.of());
    }

    void pushLookupSuppression() {
        lookupSuppressionDepth++;
    }

    /**
     * Restore an overlay frame after an asynchronous operation settles.
     * <p>
     * Kept as the deferred counterpart of synchronous frame restoration; module imports do not use overlays.
     *
     * @param asyncModulePromise
     *            the module's evaluation promise
     * @param frame
     *            the frame to restore when it settles
     */
    void registerDeferredRestore(JSPromise asyncModulePromise, Frame frame) {
        if (asyncModulePromise == null || frame == null) {
            return;
        }
        JSNativeFunction onFulfill = new JSNativeFunction(context, "", 0, (ctx, thisArg, args) -> {
            restoreFrame(frame);
            return JSUndefined.INSTANCE;
        });
        onFulfill.initializePrototypeChain(context);
        JSNativeFunction onReject = new JSNativeFunction(context, "", 1, (ctx, thisArg, args) -> {
            restoreFrame(frame);
            return JSUndefined.INSTANCE;
        });
        onReject.initializePrototypeChain(context);
        asyncModulePromise.addReactions(new JSPromise.ReactionRecord(onFulfill, context, null, null),
                new JSPromise.ReactionRecord(onReject, context, null, null));
    }

    void resetLookupSuppression() {
        lookupSuppressionDepth = 0;
    }

    /**
     * The binding of {@code name} that code in {@code environment} sees, or null. Frames are walked newest first; an
     * overlay of the name that the code cannot see is remembered, because the nearest such overlay above the owner is
     * the one that took the name's global property over and keeps the owner's value in its saved globals.
     */
    Binding resolve(String name, JSEvalEnvironment environment) {
        if (lookupSuppressionDepth > 0 || environment == null) {
            return null;
        }
        Frame displacer = null;
        for (Frame frame : frames) {
            if (!frame.savedGlobals().containsKey(name) && !frame.absentKeys().contains(name)) {
                continue;
            }
            if (frame.environment() != null && environment.isWithin(frame.environment())) {
                return new Binding(frame, displacer);
            }
            displacer = frame;
        }
        return null;
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
                if (currentDescriptor != null && currentDescriptor.isDataDescriptor()
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
     * Where a visible binding lives: in {@code owner}'s overlay on the global object, unless {@code displacer}, a later
     * overlay of the same name that the code cannot see, holds the global property and keeps the value in its saved
     * globals.
     */
    record Binding(Frame owner, Frame displacer) {
    }

    /**
     * What one overlay displaced on the global object.
     *
     * @param savedGlobals
     *            the values the overlaid names held before the overlay was installed
     * @param absentKeys
     *            the overlaid names that had no value at all, so restoring means deleting
     * @param environment
     *            the eval that installed the overlay, which only code in its lexical environment may consult
     */
    record Frame(Map<String, JSValue> savedGlobals, Set<String> absentKeys, JSEvalEnvironment environment,
            Map<String, PropertyDescriptor> savedDescriptors, Set<String> constantBindings) {
        Frame(Map<String, JSValue> savedGlobals, Set<String> absentKeys) {
            this(savedGlobals, absentKeys, null, Map.of(), Set.of());
        }
    }
}
