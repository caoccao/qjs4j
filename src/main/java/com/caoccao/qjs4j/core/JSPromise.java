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
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a JavaScript Promise object. Based on ES2020 Promise specification (simplified). Promises represent the
 * eventual result of an asynchronous operation.
 * <p>
 * A promise is confined to its context's thread, like the rest of the engine, so its state needs no lock. It used to
 * lock on every operation because {@code Atomics.waitAsync} settled its promise from a helper thread; that thread now
 * enqueues a job that settles it on the context's thread (EnqueueResolveInAgentJob), and enqueueing a microtask is the
 * one cross-thread operation the engine supports.
 */
public final class JSPromise extends JSObject {
    public static final String NAME = "Promise";
    private final List<ReactionRecord> fulfillReactions;
    /**
     * {@code [[PromiseIsHandled]]}: whether a handler has ever been attached, which decides whether a rejection is
     * reported to the host as unhandled.
     */
    private boolean handled;
    private final List<ReactionRecord> rejectReactions;
    private JSValue result;
    private PromiseState state;

    /**
     * Create a new Promise in pending state.
     */
    public JSPromise(JSContext context) {
        super(context);
        this.state = PromiseState.PENDING;
        this.result = JSUndefined.INSTANCE;
        this.fulfillReactions = new ArrayList<>();
        this.handled = false;
        this.rejectReactions = new ArrayList<>();
    }

    /**
     * Add reactions to be called when promise is fulfilled or rejected.
     */
    public void addReactions(ReactionRecord onFulfill, ReactionRecord onReject) {
        // PerformPromiseThen: attaching reactions is what handles a rejection.
        markHandled();
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
     * Fulfill the promise with a value. ES2020 25.6.1.4
     */
    public void fulfill(JSValue value) {
        if (state != PromiseState.PENDING) {
            return; // Promise already settled
        }
        state = PromiseState.FULFILLED;
        result = value;
        List<ReactionRecord> reactionsToTrigger = new ArrayList<>(fulfillReactions);
        fulfillReactions.clear();
        rejectReactions.clear();

        // Trigger all fulfill reactions
        for (ReactionRecord reaction : reactionsToTrigger) {
            triggerReaction(reaction, value);
        }
    }

    public JSNativeFunction fulfillHandler(JSContext context, String name) {
        return new JSNativeFunction(context, name, 1, (childContext, thisArg, args) -> {
            fulfill(JSValue.argumentOrUndefined(args, 0));
            return JSUndefined.INSTANCE;
        });
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
     * Whether a handler has been attached to this promise, or its result otherwise consumed:
     * {@code [[PromiseIsHandled]]}.
     *
     * @return true when a rejection of this promise is handled
     */
    public boolean isHandled() {
        return handled;
    }

    /**
     * Record that this promise's rejection is handled: the {@code [[PromiseIsHandled]]} step of PerformPromiseThen,
     * also taken by any engine path that consumes a settled promise's result without attaching a reaction, such as an
     * {@code await} that finds the promise already settled.
     * <p>
     * A promise that was already rejected with no handler was reported to the host as
     * {@link PromiseRejectEvent#PromiseRejectWithNoHandler}; it is now reported as
     * {@link PromiseRejectEvent#PromiseHandlerAddedAfterReject} (HostPromiseRejectionTracker with {@code "handle"}).
     */
    public void markHandled() {
        boolean handlerAddedAfterReject = state == PromiseState.REJECTED && !handled;
        handled = true;
        if (handlerAddedAfterReject) {
            trackRejection(PromiseRejectEvent.PromiseHandlerAddedAfterReject);
        }
    }

    /**
     * Reject the promise with a reason. ES2020 25.6.1.7
     */
    public void reject(JSValue reason) {
        if (state != PromiseState.PENDING) {
            return; // Promise already settled
        }
        state = PromiseState.REJECTED;
        result = reason;
        List<ReactionRecord> reactionsToTrigger = new ArrayList<>(rejectReactions);
        fulfillReactions.clear();
        rejectReactions.clear();
        boolean rejectedWithNoHandler = !handled;
        // RejectPromise step 7: HostPromiseRejectionTracker(promise, "reject") when nothing handles it yet.
        if (rejectedWithNoHandler) {
            trackRejection(PromiseRejectEvent.PromiseRejectWithNoHandler);
        }

        // Trigger all reject reactions
        for (ReactionRecord reaction : reactionsToTrigger) {
            triggerReaction(reaction, reason);
        }
    }

    public JSNativeFunction rejectHandler(JSContext context, String name) {
        return new JSNativeFunction(context, name, 1, (childContext, thisArg, args) -> {
            reject(JSValue.argumentOrUndefined(args, 0));
            return JSUndefined.INSTANCE;
        });
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

        if (!(resolution instanceof JSObject resolutionObject)) {
            fulfill(resolution);
            return;
        }

        JSValue thenValue = resolutionObject.get(PropertyKey.THEN);
        if (context.hasPendingException()) {
            JSValue error = context.takePendingException();
            reject(error);
            return;
        }
        if (!JSTypeChecking.isCallable(thenValue)) {
            fulfill(resolution);
            return;
        }

        ResolveState resolveState = new ResolveState();
        context.enqueueMicrotask(() -> {
            JSNativeFunction resolveFunc = new JSNativeFunction(context, "", 1, (childContext, thisArg, funcArgs) -> {
                if (resolveState.alreadyResolved) {
                    return JSUndefined.INSTANCE;
                }
                resolveState.alreadyResolved = true;
                JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                resolve(context, value);
                return JSUndefined.INSTANCE;
            });
            JSNativeFunction rejectFunc = new JSNativeFunction(context, "", 1, (childContext, thisArg, funcArgs) -> {
                if (resolveState.alreadyResolved) {
                    return JSUndefined.INSTANCE;
                }
                resolveState.alreadyResolved = true;
                JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                reject(reason);
                return JSUndefined.INSTANCE;
            });
            context.transferPrototype(resolveFunc, JSFunction.NAME);
            context.transferPrototype(rejectFunc, JSFunction.NAME);
            try {
                if (thenValue instanceof JSProxy thenProxy) {
                    thenProxy.apply(context, resolutionObject, new JSValue[]{resolveFunc, rejectFunc});
                } else {
                    ((JSFunction) thenValue).call(context, resolutionObject, new JSValue[]{resolveFunc, rejectFunc});
                }
                if (context.hasPendingException() && !resolveState.alreadyResolved) {
                    JSValue error = context.takePendingException();
                    resolveState.alreadyResolved = true;
                    reject(error);
                }
            } catch (JSException e) {
                if (!resolveState.alreadyResolved) {
                    resolveState.alreadyResolved = true;
                    reject(e.getErrorValue());
                }
                if (context.hasPendingException()) {
                    context.clearPendingException();
                }
            } catch (JSVirtualMachineException e) {
                if (!resolveState.alreadyResolved) {
                    resolveState.alreadyResolved = true;
                    if (e.getJsValue() != null) {
                        reject(e.getJsValue());
                    } else if (e.getJsError() != null) {
                        reject(e.getJsError());
                    } else if (context.hasPendingException()) {
                        JSValue error = context.takePendingException();
                        reject(error);
                    } else {
                        reject(new JSString("Error in promise resolution: "
                                + (e.getMessage() != null ? e.getMessage() : "Unhandled exception")));
                    }
                }
                if (context.hasPendingException()) {
                    context.clearPendingException();
                }
            } catch (Exception e) {
                if (!resolveState.alreadyResolved) {
                    resolveState.alreadyResolved = true;
                    if (context.hasPendingException()) {
                        JSValue error = context.takePendingException();
                        reject(error);
                    } else {
                        reject(new JSString("Error in promise resolution: " + e.getMessage()));
                    }
                }
            }
        });
    }

    @Override
    public String toString() {
        return "[object Promise]";
    }

    /**
     * HostPromiseRejectionTracker: report a rejection event to the host's promise reject callback, if one is installed.
     * <p>
     * The callback used to be invoked only when an {@code await} found a rejected promise — which is exactly the case
     * where a handler exists — and never for a rejection that nothing handled.
     */
    private void trackRejection(PromiseRejectEvent event) {
        IJSPromiseRejectCallback callback = context.getPromiseRejectCallback();
        if (callback != null) {
            callback.callback(event, this, result);
        }
    }

    /**
     * Trigger a reaction by queueing a microtask. ES2020 requires promise reactions to run as microtasks.
     */
    private void triggerReaction(ReactionRecord reaction, JSValue value) {
        // Enqueue a microtask to execute the reaction
        reaction.context.enqueueMicrotask(() -> {
            if (reaction.handler != null) {
                try {
                    JSValue[] args = new JSValue[]{value};
                    JSValue handlerResult = reaction.handler.call(reaction.context, JSUndefined.INSTANCE, args);
                    if (reaction.context.hasPendingException()) {
                        JSValue error = reaction.context.takePendingException();
                        rejectReactionTarget(reaction, error);
                        return;
                    }

                    // If the handler returned a value, resolve the chained promise.
                    resolveReactionTarget(reaction, handlerResult);
                } catch (JSException e) {
                    rejectReactionTarget(reaction, e.getErrorValue());
                    if (reaction.context.hasPendingException()) {
                        reaction.context.clearPendingException();
                    }
                } catch (Exception e) {
                    // If handler throws, reject the chained promise with the JS error value
                    JSValue errorValue = null;
                    if (reaction.context.hasPendingException()) {
                        errorValue = reaction.context.takePendingException();
                    } else if (e instanceof JSVirtualMachineException vmException && vmException.getJsValue() != null) {
                        errorValue = vmException.getJsValue();
                    }
                    if (errorValue != null) {
                        rejectReactionTarget(reaction, errorValue);
                    } else {
                        rejectReactionTarget(reaction, new JSString("Error in promise handler: " + e.getMessage()));
                    }
                }
            } else {
                // No handler, just pass the value through
                if (state == PromiseState.FULFILLED) {
                    resolveReactionTarget(reaction, value);
                } else {
                    rejectReactionTarget(reaction, value);
                }
            }
        });
    }

    public static JSObject create(JSContext context, JSValue... args) {
        // Promise requires an executor function
        if (args.length == 0 || !JSTypeChecking.isCallable(args[0])) {
            return context.throwTypeError("Promise constructor requires an executor function");
        }
        JSValue executor = args[0];
        // Create Promise object
        JSPromise jsPromise = context.createJSPromise();
        final ResolveState resolveState = new ResolveState();
        // Create resolve and reject functions
        JSNativeFunction resolveFunc = new JSNativeFunction(context, "", 1, (childContext, thisArg, funcArgs) -> {
            if (resolveState.alreadyResolved) {
                return JSUndefined.INSTANCE;
            }
            resolveState.alreadyResolved = true;
            JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
            jsPromise.resolve(childContext, value);
            return JSUndefined.INSTANCE;
        });
        JSNativeFunction rejectFunc = new JSNativeFunction(context, "", 1, (childContext, thisArg, funcArgs) -> {
            if (resolveState.alreadyResolved) {
                return JSUndefined.INSTANCE;
            }
            resolveState.alreadyResolved = true;
            JSValue reason = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
            jsPromise.reject(reason);
            return JSUndefined.INSTANCE;
        });
        context.transferPrototype(resolveFunc, JSFunction.NAME);
        context.transferPrototype(rejectFunc, JSFunction.NAME);
        // Call the executor with resolve and reject
        try {
            JSValue[] executorArgs = new JSValue[]{resolveFunc, rejectFunc};
            JSCall.call(context, executor, JSUndefined.INSTANCE, executorArgs);
            if (context.hasPendingException()) {
                JSValue error = context.takePendingException();
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
            if (context.hasPendingException()) {
                context.clearPendingException();
            }
        } catch (JSVirtualMachineException e) {
            if (!resolveState.alreadyResolved) {
                resolveState.alreadyResolved = true;
                if (e.getJsValue() != null) {
                    jsPromise.reject(e.getJsValue());
                } else if (e.getJsError() != null) {
                    jsPromise.reject(e.getJsError());
                } else if (context.hasPendingException()) {
                    JSValue error = context.takePendingException();
                    jsPromise.reject(error);
                } else {
                    jsPromise.reject(new JSString(e.getMessage() != null ? e.getMessage() : "Unhandled exception"));
                }
            }
            if (context.hasPendingException()) {
                context.clearPendingException();
            }
        } catch (Exception e) {
            // If executor throws, reject the promise
            if (!resolveState.alreadyResolved) {
                resolveState.alreadyResolved = true;
                jsPromise.reject(new JSString("Error in Promise executor: " + e.getMessage()));
            }
            if (context.hasPendingException()) {
                context.clearPendingException();
            }
        }
        context.transferPrototype(jsPromise, NAME);
        return jsPromise;
    }

    private static void rejectReactionTarget(ReactionRecord reaction, JSValue reason) {
        if (reaction.promise != null) {
            reaction.promise.reject(reason);
            return;
        }
        if (reaction.capabilityReject != null) {
            JSCall.call(reaction.context, reaction.capabilityReject, JSUndefined.INSTANCE, new JSValue[]{reason});
        }
    }

    private static void resolveReactionTarget(ReactionRecord reaction, JSValue value) {
        if (reaction.promise != null) {
            reaction.promise.resolve(reaction.context, value);
            return;
        }
        if (reaction.capabilityResolve != null) {
            JSCall.call(reaction.context, reaction.capabilityResolve, JSUndefined.INSTANCE, new JSValue[]{value});
        }
    }

    /**
     * Promise states as defined in ES2020.
     */
    public enum PromiseState {
        FULFILLED, PENDING, REJECTED
    }

    /**
     * A reaction record stores a callback and the promise it will affect.
     */
    public record ReactionRecord(JSFunction handler, JSPromise promise, JSContext context, JSValue capabilityResolve,
            JSValue capabilityReject) {
        public ReactionRecord(JSFunction handler, JSPromise promise, JSContext context) {
            this(handler, promise, context, null, null);
        }

        public ReactionRecord(JSFunction handler, JSContext context, JSValue capabilityResolve,
                JSValue capabilityReject) {
            this(handler, null, context, capabilityResolve, capabilityReject);
        }
    }

    public static final class ResolveState {
        public boolean alreadyResolved;
    }
}
