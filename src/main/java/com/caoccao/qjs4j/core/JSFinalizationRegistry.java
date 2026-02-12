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

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a FinalizationRegistry object in JavaScript.
 * Based on QuickJS JS_CLASS_FINALIZATION_REGISTRY implementation.
 * <p>
 * FinalizationRegistry allows you to register cleanup callbacks that are
 * called when registered objects are garbage collected.
 * <p>
 * Prototype methods (register, unregister) are defined on the prototype
 * in JSGlobalObject, not on each instance.
 */
public final class JSFinalizationRegistry extends JSObject {
    public static final String NAME = "FinalizationRegistry";
    private final JSFunction cleanupCallback;
    private final Thread cleanupThread;
    private final JSContext context;
    private final ReferenceQueue<JSObject> referenceQueue;
    private final Map<PhantomReference<JSObject>, RegistrationRecord> registrations;
    private volatile boolean running;

    /**
     * Create a new FinalizationRegistry.
     *
     * @param context         The execution context
     * @param cleanupCallback Callback function called with held values
     */
    public JSFinalizationRegistry(JSContext context, JSFunction cleanupCallback) {
        super();
        this.cleanupCallback = cleanupCallback;
        this.context = context;
        this.referenceQueue = new ReferenceQueue<>();
        this.registrations = new ConcurrentHashMap<>();
        this.running = true;

        // Start cleanup thread to monitor the reference queue
        this.cleanupThread = new Thread(this::cleanupLoop, "FinalizationRegistry-Cleanup");
        this.cleanupThread.setDaemon(true);
        this.cleanupThread.start();
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSValue cbArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        if (!(cbArg instanceof JSFunction callback)) {
            return context.throwTypeError("argument must be a function");
        }
        JSObject jsObject = new JSFinalizationRegistry(context, callback);
        context.transferPrototype(jsObject, NAME);
        return jsObject;
    }

    /**
     * Cleanup loop that monitors the reference queue.
     * Runs in a background thread.
     */
    private void cleanupLoop() {
        while (running) {
            try {
                // Wait for a reference to be enqueued (blocking)
                PhantomReference<?> ref = (PhantomReference<?>) referenceQueue.remove();

                // Get the registration record
                RegistrationRecord record = registrations.remove(ref);

                if (record != null) {
                    // Call cleanup callback as a microtask
                    context.enqueueMicrotask(() -> {
                        try {
                            cleanupCallback.call(context, JSUndefined.INSTANCE,
                                    new JSValue[]{record.heldValue});
                        } catch (Exception e) {
                            // Cleanup callback errors should not crash the program
                            System.err.println("FinalizationRegistry cleanup error: " + e.getMessage());
                        }
                    });
                }

                // Clear the phantom reference
                ref.clear();
            } catch (InterruptedException e) {
                // Thread interrupted, exit loop
                break;
            } catch (Exception e) {
                // Log error but continue running
                System.err.println("FinalizationRegistry cleanup loop error: " + e.getMessage());
            }
        }
    }

    /**
     * Get the number of active registrations.
     * For debugging/testing purposes.
     *
     * @return The number of registered objects
     */
    public int getRegistrationCount() {
        return registrations.size();
    }

    /**
     * Register an object for finalization.
     * Called by FinalizationRegistryPrototype.register() after validation.
     *
     * @param target          The object to monitor
     * @param heldValue       Value passed to cleanup callback
     * @param unregisterToken Optional token for manual unregistration (null if none)
     */
    public void register(JSObject target, JSValue heldValue, JSValue unregisterToken) {
        // Create phantom reference to track when target is collected
        PhantomReference<JSObject> phantomRef = new PhantomReference<>(target, referenceQueue);

        // Store registration
        RegistrationRecord record = new RegistrationRecord(heldValue, unregisterToken);
        registrations.put(phantomRef, record);
    }

    /**
     * Stop the cleanup thread and clear all registrations.
     * Called when the registry is no longer needed.
     */
    public void shutdown() {
        running = false;
        cleanupThread.interrupt();
        registrations.clear();
    }

    @Override
    public String toString() {
        return "[object FinalizationRegistry]";
    }

    /**
     * Unregister all entries matching the given token.
     * Following QuickJS behavior, removes ALL entries with matching token,
     * not just the first one.
     * Called by FinalizationRegistryPrototype.unregister() after validation.
     *
     * @param unregisterToken The token provided during registration
     * @return True if at least one registration was removed
     */
    public boolean unregister(JSValue unregisterToken) {
        boolean removed = false;
        var iterator = registrations.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            RegistrationRecord record = entry.getValue();
            if (record.unregisterToken != null && record.unregisterToken == unregisterToken) {
                iterator.remove();
                entry.getKey().clear();
                removed = true;
            }
        }
        return removed;
    }

    /**
     * Record for a registered object.
     */
    private record RegistrationRecord(JSValue heldValue, JSValue unregisterToken) {
    }
}
