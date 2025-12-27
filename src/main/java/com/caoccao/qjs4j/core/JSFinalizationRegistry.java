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
 * Based on ES2021 FinalizationRegistry specification.
 * <p>
 * FinalizationRegistry allows you to register cleanup callbacks that are
 * called when registered objects are garbage collected.
 * <p>
 * Key characteristics:
 * - Register objects with associated held values
 * - Cleanup callback is called with the held value when object is collected
 * - Optional unregister token for manual cleanup removal
 * - Callbacks run in microtasks after garbage collection
 * - Part of the WeakRefs proposal (ES2021)
 */
public final class JSFinalizationRegistry extends JSObject {
    private final JSFunction cleanupCallback;
    private final JSContext context;
    private final ReferenceQueue<JSObject> referenceQueue;
    private final Map<PhantomReference<JSObject>, RegistrationRecord> registrations;
    private final Map<JSValue, PhantomReference<JSObject>> unregisterTokenMap;
    private final Thread cleanupThread;
    private volatile boolean running;

    /**
         * Record for a registered object.
         */
        private record RegistrationRecord(JSValue heldValue, JSValue unregisterToken) {
    }

    /**
     * Create a new FinalizationRegistry.
     *
     * @param cleanupCallback Callback function called with held values
     * @param ctx             The execution context
     */
    public JSFinalizationRegistry(JSFunction cleanupCallback, JSContext ctx) {
        super();
        this.cleanupCallback = cleanupCallback;
        this.context = ctx;
        this.referenceQueue = new ReferenceQueue<>();
        this.registrations = new ConcurrentHashMap<>();
        this.unregisterTokenMap = new ConcurrentHashMap<>();
        this.running = true;

        // Start cleanup thread to monitor the reference queue
        this.cleanupThread = new Thread(this::cleanupLoop, "FinalizationRegistry-Cleanup");
        this.cleanupThread.setDaemon(true);
        this.cleanupThread.start();

        // Add register() method
        this.set("register", new JSNativeFunction("register", 2, (context, thisArg, args) -> {
            if (args.length < 2) {
                return context.throwError("TypeError", "FinalizationRegistry.register requires target and heldValue");
            }

            JSValue target = args[0];
            JSValue heldValue = args[1];
            JSValue unregisterToken = args.length > 2 ? args[2] : null;

            if (!(target instanceof JSObject targetObj)) {
                return context.throwError("TypeError", "FinalizationRegistry target must be an object");
            }

            // Cannot register the same object as target and unregister token
            if (unregisterToken != null && target == unregisterToken) {
                return context.throwError("TypeError", "Target and unregister token cannot be the same object");
            }

            register(targetObj, heldValue, unregisterToken);
            return JSUndefined.INSTANCE;
        }));

        // Add unregister() method
        this.set("unregister", new JSNativeFunction("unregister", 1, (context, thisArg, args) -> {
            if (args.length == 0) {
                return context.throwError("TypeError", "FinalizationRegistry.unregister requires unregisterToken");
            }

            JSValue unregisterToken = args[0];
            boolean removed = unregister(unregisterToken);
            return JSBoolean.valueOf(removed);
        }));
    }

    /**
     * Register an object for finalization.
     * ES2021 FinalizationRegistry.prototype.register()
     *
     * @param target          The object to monitor
     * @param heldValue       Value passed to cleanup callback
     * @param unregisterToken Optional token for manual unregistration
     */
    public void register(JSObject target, JSValue heldValue, JSValue unregisterToken) {
        // Create phantom reference to track when target is collected
        PhantomReference<JSObject> phantomRef = new PhantomReference<>(target, referenceQueue);

        // Store registration
        RegistrationRecord record = new RegistrationRecord(heldValue, unregisterToken);
        registrations.put(phantomRef, record);

        // Store unregister token mapping if provided
        if (unregisterToken != null) {
            unregisterTokenMap.put(unregisterToken, phantomRef);
        }
    }

    /**
     * Unregister an object using its unregister token.
     * ES2021 FinalizationRegistry.prototype.unregister()
     *
     * @param unregisterToken The token provided during registration
     * @return True if a registration was removed
     */
    public boolean unregister(JSValue unregisterToken) {
        PhantomReference<JSObject> phantomRef = unregisterTokenMap.remove(unregisterToken);
        if (phantomRef != null) {
            registrations.remove(phantomRef);
            return true;
        }
        return false;
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
                    // Remove from unregister token map
                    if (record.unregisterToken != null) {
                        unregisterTokenMap.remove(record.unregisterToken);
                    }

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
     * Stop the cleanup thread and clear all registrations.
     * Called when the registry is no longer needed.
     */
    public void shutdown() {
        running = false;
        cleanupThread.interrupt();
        registrations.clear();
        unregisterTokenMap.clear();
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

    @Override
    public String toString() {
        return "[object FinalizationRegistry]";
    }
}
