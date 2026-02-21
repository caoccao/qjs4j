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
 * Functional interface for handling promise rejections in await expressions.
 * If this callback is set, it will be invoked when a promise rejection occurs,
 * allowing custom handling of the rejection before the exception is propagated.
 */
@FunctionalInterface
public interface IJSPromiseRejectCallback {
    /**
     * Called when a promise rejection event occurs.
     *
     * @param event   The type of promise rejection event
     * @param promise The promise that triggered the event
     * @param result  The result value (rejection reason or resolution value)
     */
    void callback(PromiseRejectEvent event, JSPromise promise, JSValue result);
}
