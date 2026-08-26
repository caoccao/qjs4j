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
 * Functional interface for observing failures that escape a microtask.
 * <p>
 * Draining the microtask queue cannot propagate an exception to a caller — there is no caller to
 * propagate to — so a failure that escapes {@code microtask.execute()} would otherwise disappear.
 * That includes a throwing {@code .then()} handler, but also an engine defect surfacing as a
 * {@link NullPointerException} or a {@code JSVirtualMachineException}. Install a callback to see
 * them; without one they are still recorded on the context and, for engine defects, logged.
 *
 * @see JSContext#setMicrotaskFailureCallback(IJSMicrotaskFailureCallback)
 * @see JSContext#getMicrotaskFailures()
 */
@FunctionalInterface
public interface IJSMicrotaskFailureCallback {
    /**
     * Called when a microtask fails.
     *
     * @param failure the exception that escaped the microtask
     */
    void onMicrotaskFailure(Throwable failure);
}
