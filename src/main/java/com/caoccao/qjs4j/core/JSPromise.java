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

import com.caoccao.qjs4j.exceptions.JSException;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a JavaScript Promise object.
 * Based on ES2020 Promise specification (simplified).
 * Promises represent the eventual result of an asynchronous operation.
 */
public final class JSPromise extends JSObject {
    public static final String NAME = "Promise";
    private final List<ReactionRecord> fulfillReactions;
    private final List<ReactionRecord> rejectReactions;
    private JSValue result;
    private PromiseState state;

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

    public static JSObject create(JSContext context, JSValue... args) {
        // Promise requires an executor function
        if (args.length == 0 || !(args[0] instanceof JSFunction executor)) {
            return context.throwTypeError("Promise constructor requires an executor function");
        }
        // Create Promise object
        JSPromise jsPromise = context.createJSPromise();
        final ResolveState resolveState = new ResolveState();
        // Create resolve and reject functions
        JSNativeFunction resolveFunc = new JSNativeFunction("resolve", 1,
                (childContext, thisArg, funcArgs) -> {
                    if (resolveState.alreadyResolved) {
                        return JSUndefined.INSTANCE;
                    }
                    resolveState.alreadyResolved = true;
                    JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                    jsPromise.resolve(childContext, value);
                    return JSUndefined.INSTANCE;
                });
        JSNativeFunction rejectFunc = new JSNativeFunction("reject", 1,
                (childContext, thisArg, funcArgs) -> {
                    if (resolveState.alreadyResolved) {
                        return JSUndefined.INSTANCE;
                    }
                    resolveState.alreadyResolved = true;
                    JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                    jsPromise.reject(reason);
                    return JSUndefined.INSTANCE;
                });
        // Call the executor with resolve and reject
        try {
            JSValue[] executorArgs = new JSValue[]{resolveFunc, rejectFunc};
            executor.call(context, JSUndefined.INSTANCE, executorArgs);
            if (context.hasPendingException()) {
                JSValue error = context.getPendingException();
                context.clearPendingException();
                if (!resolveState.alreadyResolved) {
                    resolveState.alreadyResolved = true;
                    jsPromise.reject(error);
                }
            }
        } catch (JSException e) {
            if (!resolveState.alreadyResolved) {
                resolveState.alreadyResolved = true;
                jsPromise.reject(e.getErrorValue());
            }
        } catch (Exception e) {
            // If executor throws, reject the promise
            if (!resolveState.alreadyResolved) {
                resolveState.alreadyResolved = true;
                jsPromise.reject(new JSString("Error in Promise executor: " + e.getMessage()));
            }
        }
        context.transferPrototype(jsPromise, NAME);
        return jsPromise;
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
     * Get the result value (only meaningful when fulfilled or rejected).
     */
    public JSValue getResult() {
        return result;
    }

    /**
     * Get the current state of the promise.
     */
    public PromiseState getState() {
        return state;
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
     * Resolve the promise using the Promise Resolution Procedure.
     */
    public void resolve(JSContext context, JSValue resolution) {
        if (state != PromiseState.PENDING) {
            return;
        }

        if (resolution == this) {
            JSValue error = context.throwTypeError("promise self resolution");
            context.clearPendingException();
            reject(error);
            return;
        }

        if (resolution instanceof JSPromise resolvedPromise) {
            resolvedPromise.addReactions(
                    new ReactionRecord(null, this, context),
                    new ReactionRecord(null, this, context));
            return;
        }

        if (!(resolution instanceof JSObject resolutionObject)) {
            fulfill(resolution);
            return;
        }

        JSValue thenValue = resolutionObject.get(PropertyKey.THEN);
        if (context.hasPendingException()) {
            JSValue error = context.getPendingException();
            context.clearPendingException();
            reject(error);
            return;
        }
        if (!(thenValue instanceof JSFunction thenFunction)) {
            fulfill(resolution);
            return;
        }

        ResolveState resolveState = new ResolveState();
        context.enqueueMicrotask(() -> {
            JSNativeFunction resolveFunc = new JSNativeFunction("resolve", 1,
                    (childContext, thisArg, funcArgs) -> {
                        if (resolveState.alreadyResolved) {
                            return JSUndefined.INSTANCE;
                        }
                        resolveState.alreadyResolved = true;
                        JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                        resolve(context, value);
                        return JSUndefined.INSTANCE;
                    });
            JSNativeFunction rejectFunc = new JSNativeFunction("reject", 1,
                    (childContext, thisArg, funcArgs) -> {
                        if (resolveState.alreadyResolved) {
                            return JSUndefined.INSTANCE;
                        }
                        resolveState.alreadyResolved = true;
                        JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                        reject(reason);
                        return JSUndefined.INSTANCE;
                    });
            try {
                thenFunction.call(context, resolutionObject, new JSValue[]{resolveFunc, rejectFunc});
                if (context.hasPendingException() && !resolveState.alreadyResolved) {
                    JSValue error = context.getPendingException();
                    context.clearPendingException();
                    resolveState.alreadyResolved = true;
                    reject(error);
                }
            } catch (JSException e) {
                if (!resolveState.alreadyResolved) {
                    resolveState.alreadyResolved = true;
                    reject(e.getErrorValue());
                }
            } catch (Exception e) {
                if (!resolveState.alreadyResolved) {
                    resolveState.alreadyResolved = true;
                    reject(new JSString("Error in promise resolution: " + e.getMessage()));
                }
            }
        });
    }

    @Override
    public String toString() {
        return "[object Promise]";
    }

    /**
     * Trigger a reaction by queueing a microtask.
     * ES2020 requires promise reactions to run as microtasks.
     */
    private void triggerReaction(ReactionRecord reaction, JSValue value) {
        // Enqueue a microtask to execute the reaction
        reaction.context.enqueueMicrotask(() -> {
            if (reaction.handler != null) {
                try {
                    JSValue[] args = new JSValue[]{value};
                    JSValue handlerResult = reaction.handler.call(reaction.context, JSUndefined.INSTANCE, args);
                    if (reaction.context.hasPendingException()) {
                        JSValue error = reaction.context.getPendingException();
                        reaction.context.clearPendingException();
                        if (reaction.promise != null) {
                            reaction.promise.reject(error);
                        }
                        return;
                    }

                    // If the handler returned a value, resolve the chained promise.
                    if (reaction.promise != null) {
                        reaction.promise.resolve(reaction.context, handlerResult);
                    }
                } catch (JSException e) {
                    if (reaction.promise != null) {
                        reaction.promise.reject(e.getErrorValue());
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
        });
    }

    /**
     * Promise states as defined in ES2020.
     */
    public enum PromiseState {
        PENDING,
        FULFILLED,
        REJECTED
    }

    /**
     * A reaction record stores a callback and the promise it will affect.
     */
    public record ReactionRecord(JSFunction handler, JSPromise promise, JSContext context) {
    }

    private static final class ResolveState {
        private boolean alreadyResolved;
    }
}
