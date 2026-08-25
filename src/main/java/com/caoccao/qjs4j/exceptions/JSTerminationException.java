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

package com.caoccao.qjs4j.exceptions;

/**
 * Execution must stop, and no JavaScript {@code catch} may intercept it.
 * <p>
 * Raised for host-initiated termination — an execution deadline or
 * {@link com.caoccao.qjs4j.core.JSRuntime#requestInterrupt()} — where a script must not be able to
 * keep itself alive by wrapping its own loop in {@code try}/{@code catch}, and for engine defects
 * such as an invalid opcode, which must reach the embedder rather than be masked as a guest error.
 * <p>
 * This extends {@link Error} rather than {@link RuntimeException} deliberately. The engine contains
 * roughly 170 {@code catch} blocks that translate a Java exception into a JavaScript value —
 * {@code catch (JSVirtualMachineException)}, {@code catch (RuntimeException)},
 * {@code catch (Exception)} — spread across promise executors, promise reactions, async function
 * completion, iterator helpers and disposal. Carrying termination as a flag on an exception type
 * those blocks already catch meant every one of them was a place the signal could be demoted to a
 * rejected promise: the guarantee held only where someone had remembered to re-check the flag.
 * Making termination an {@link Error} inverts the default, so a boundary has to name
 * {@code Throwable} or {@code Error} explicitly before it can swallow the signal. The four places
 * in the engine that do are guarded individually.
 * <p>
 * Embedders that need to observe termination should catch this type by name, or let it propagate.
 */
public final class JSTerminationException extends Error {
    /**
     * Create a termination signal.
     *
     * @param message the reason execution was terminated
     */
    public JSTerminationException(String message) {
        super(message);
    }

    /**
     * Termination carries no Java stack: it is a control signal, not a defect report, and it is
     * raised on the interpreter's hottest paths.
     *
     * @return this exception, unchanged
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
