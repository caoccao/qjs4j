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

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a JavaScript Promise object.
 * Based on ES2020 Promise specification (simplified).
 * Promises represent the eventual result of an asynchronous operation.
 */
public final class JSPromise extends JSObject {
    /**
     * Promise states as defined in ES2020.
     */
    public enum PromiseState {
        PENDING,
        FULFILLED,
        REJECTED
    }

    private PromiseState state;
    private JSValue result;
    private final List<ReactionRecord> fulfillReactions;
    private final List<ReactionRecord> rejectReactions;

    /**
     * A reaction record stores a callback and the promise it will affect.
     */
    public static class ReactionRecord {
        public final JSFunction handler;
        public final JSPromise promise;
        public final JSContext context;

        public ReactionRecord(JSFunction handler, JSPromise promise, JSContext context) {
            this.handler = handler;
            this.promise = promise;
            this.context = context;
        }
    }

    /**
     * Create a new Promise in pending state.
     */
    public JSPromise() {
        super();
        this.state = PromiseState.PENDING;
        this.result = JSUndefined.INSTANCE;
        this.fulfillReactions = new ArrayList<>();
        this.rejectReactions = new ArrayList<>();
    }

    /**
     * Get the current state of the promise.
     */
    public PromiseState getState() {
        return state;
    }

    /**
     * Get the result value (only meaningful when fulfilled or rejected).
     */
    public JSValue getResult() {
        return result;
    }

    /**
     * Fulfill the promise with a value.
     * ES2020 25.6.1.4
     */
    public void fulfill(JSValue value) {
        if (state != PromiseState.PENDING) {
            return; // Promise already settled
        }

        state = PromiseState.FULFILLED;
        result = value;

        // Trigger all fulfill reactions
        for (ReactionRecord reaction : fulfillReactions) {
            triggerReaction(reaction, value);
        }

        // Clear reactions
        fulfillReactions.clear();
        rejectReactions.clear();
    }

    /**
     * Reject the promise with a reason.
     * ES2020 25.6.1.7
     */
    public void reject(JSValue reason) {
        if (state != PromiseState.PENDING) {
            return; // Promise already settled
        }

        state = PromiseState.REJECTED;
        result = reason;

        // Trigger all reject reactions
        for (ReactionRecord reaction : rejectReactions) {
            triggerReaction(reaction, reason);
        }

        // Clear reactions
        fulfillReactions.clear();
        rejectReactions.clear();
    }

    /**
     * Add reactions to be called when promise is fulfilled or rejected.
     */
    public void addReactions(ReactionRecord onFulfill, ReactionRecord onReject) {
        if (state == PromiseState.PENDING) {
            if (onFulfill != null) {
                fulfillReactions.add(onFulfill);
            }
            if (onReject != null) {
                rejectReactions.add(onReject);
            }
        } else if (state == PromiseState.FULFILLED && onFulfill != null) {
            // Already fulfilled, trigger immediately
            triggerReaction(onFulfill, result);
        } else if (state == PromiseState.REJECTED && onReject != null) {
            // Already rejected, trigger immediately
            triggerReaction(onReject, result);
        }
    }

    /**
     * Trigger a reaction by calling the handler.
     * Simplified: runs synchronously for now.
     * In a full implementation, this would queue a microtask.
     */
    private void triggerReaction(ReactionRecord reaction, JSValue value) {
        if (reaction.handler != null) {
            try {
                JSValue[] args = new JSValue[]{value};
                JSValue handlerResult = reaction.handler.call(reaction.context, JSUndefined.INSTANCE, args);

                // If the handler returned a value, fulfill the chained promise
                if (reaction.promise != null) {
                    if (handlerResult instanceof JSPromise returnedPromise) {
                        // If handler returns a promise, chain it
                        returnedPromise.addReactions(
                                new ReactionRecord(null, reaction.promise, reaction.context) {
                                    {
                                        // When the returned promise fulfills, fulfill the chained promise
                                    }
                                },
                                new ReactionRecord(null, reaction.promise, reaction.context) {
                                    {
                                        // When the returned promise rejects, reject the chained promise
                                    }
                                }
                        );
                    } else {
                        // Normal value, fulfill the chained promise
                        reaction.promise.fulfill(handlerResult);
                    }
                }
            } catch (Exception e) {
                // If handler throws, reject the chained promise
                if (reaction.promise != null) {
                    reaction.promise.reject(new JSString("Error in promise handler: " + e.getMessage()));
                }
            }
        } else {
            // No handler, just pass the value through
            if (reaction.promise != null) {
                if (state == PromiseState.FULFILLED) {
                    reaction.promise.fulfill(value);
                } else {
                    reaction.promise.reject(value);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "[object Promise]";
    }
}
