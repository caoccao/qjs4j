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

package com.caoccao.qjs4j.types;

import com.caoccao.qjs4j.core.JSFunction;
import com.caoccao.qjs4j.core.JSObject;
import com.caoccao.qjs4j.core.JSValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a JavaScript Promise.
 */
public final class JSPromise extends JSObject {
    private State state;
    private JSValue result;
    private final List<PromiseReaction> fulfillReactions;
    private final List<PromiseReaction> rejectReactions;

    public enum State {
        PENDING,
        FULFILLED,
        REJECTED
    }

    public JSPromise() {
        this.state = State.PENDING;
        this.fulfillReactions = new ArrayList<>();
        this.rejectReactions = new ArrayList<>();
    }

    public void fulfill(JSValue value) {
    }

    public void reject(JSValue reason) {
    }

    public JSPromise then(JSFunction onFulfilled, JSFunction onRejected) {
        return null;
    }

    public JSPromise catch_(JSFunction onRejected) {
        return null;
    }

    public JSPromise finally_(JSFunction onFinally) {
        return null;
    }

    public State getState() {
        return state;
    }

    public record PromiseReaction(JSFunction handler, JSPromise promise) {
    }
}
