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
 * Enumeration of promise rejection events. Used by PromiseRejectCallback to identify the type of promise rejection
 * event.
 */
public enum PromiseRejectEvent {
    /**
     * A rejection handler was added to a promise after it was already rejected.
     */
    PromiseHandlerAddedAfterReject,

    /**
     * A promise was rejected after it was already resolved.
     */
    PromiseRejectAfterResolved,

    /**
     * A promise was rejected but has no rejection handler.
     */
    PromiseRejectWithNoHandler,

    /**
     * A promise was resolved after it was already resolved.
     */
    PromiseResolveAfterResolved
}
