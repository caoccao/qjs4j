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
 * Represents a JavaScript Promise object.
 * Based on ES2020 Promise specification (simplified).
 * Promises represent the eventual result of an asynchronous operation.
 */
public final class JSPromise extends JSObject {
    public static final String NAME = "Promise";
    private final List<ReactionRecord> fulfillReactions;
    private final List<ReactionRecord> rejectReactions;
    private volatile JSValue result;
    private volatile PromiseState state;

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

    private static JSValue callCallable(JSContext context, JSValue callable, JSValue thisArg, JSValue[] args) {
        if (callable instanceof JSProxy proxy) {
            return proxy.apply(context, thisArg, args);
        }
        if (callable instanceof JSFunction function) {
            return function.call(context, thisArg, args);
        }
        return context.throwTypeError("Value is not callable");
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
        JSNativeFunction resolveFunc = new JSNativeFunction("", 1,
                (childContext, thisArg, funcArgs) -> {
                    if (resolveState.alreadyResolved) {
                        return JSUndefined.INSTANCE;
                    }
                    resolveState.alreadyResolved = true;
                    JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                    jsPromise.resolve(childContext, value);
                    return JSUndefined.INSTANCE;
                });
        JSNativeFunction rejectFunc = new JSNativeFunction("", 1,
                (childContext, thisArg, funcArgs) -> {
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
                    JSValue error = context.getPendingException();
                    context.clearPendingException();
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
            callCallable(reaction.context, reaction.capabilityReject, JSUndefined.INSTANCE, new JSValue[]{reason});
        }
    }

    private static void resolveReactionTarget(ReactionRecord reaction, JSValue value) {
        if (reaction.promise != null) {
            reaction.promise.resolve(reaction.context, value);
            return;
        }
        if (reaction.capabilityResolve != null) {
            callCallable(reaction.context, reaction.capabilityResolve, JSUndefined.INSTANCE, new JSValue[]{value});
        }
    }

    /**
     * Add reactions to be called when promise is fulfilled or rejected.
     */
    public void addReactions(ReactionRecord onFulfill, ReactionRecord onReject) {
        JSValue settledResult = null;
        PromiseState settledState;
        synchronized (this) {
            if (state == PromiseState.PENDING) {
                if (onFulfill != null) {
                    fulfillReactions.add(onFulfill);
                }
                if (onReject != null) {
                    rejectReactions.add(onReject);
                }
                return;
            }
            settledState = state;
            settledResult = result;
        }
        if (settledState == PromiseState.FULFILLED && onFulfill != null) {
            // Already fulfilled, trigger immediately
            triggerReaction(onFulfill, settledResult);
        } else if (settledState == PromiseState.REJECTED && onReject != null) {
            // Already rejected, trigger immediately
            triggerReaction(onReject, settledResult);
        }
    }

    /**
     * Fulfill the promise with a value.
     * ES2020 25.6.1.4
     */
    public void fulfill(JSValue value) {
        List<ReactionRecord> reactionsToTrigger;
        synchronized (this) {
            if (state != PromiseState.PENDING) {
                return; // Promise already settled
            }

            state = PromiseState.FULFILLED;
            result = value;
            reactionsToTrigger = new ArrayList<>(fulfillReactions);

            // Clear reactions
            fulfillReactions.clear();
            rejectReactions.clear();
        }

        // Trigger all fulfill reactions
        for (ReactionRecord reaction : reactionsToTrigger) {
            triggerReaction(reaction, value);
        }
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
        List<ReactionRecord> reactionsToTrigger;
        synchronized (this) {
            if (state != PromiseState.PENDING) {
                return; // Promise already settled
            }

            state = PromiseState.REJECTED;
            result = reason;
            reactionsToTrigger = new ArrayList<>(rejectReactions);

            // Clear reactions
            fulfillReactions.clear();
            rejectReactions.clear();
        }

        // Trigger all reject reactions
        for (ReactionRecord reaction : reactionsToTrigger) {
            triggerReaction(reaction, reason);
        }
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

        JSValue thenValue = resolutionObject.get(context, PropertyKey.THEN);
        if (context.hasPendingException()) {
            JSValue error = context.getPendingException();
            context.clearPendingException();
            reject(error);
            return;
        }
        if (!JSTypeChecking.isCallable(thenValue)) {
            fulfill(resolution);
            return;
        }

        ResolveState resolveState = new ResolveState();
        context.enqueueMicrotask(() -> {
            JSNativeFunction resolveFunc = new JSNativeFunction("", 1,
                    (childContext, thisArg, funcArgs) -> {
                        if (resolveState.alreadyResolved) {
                            return JSUndefined.INSTANCE;
                        }
                        resolveState.alreadyResolved = true;
                        JSValue value = funcArgs.length > 0 ? funcArgs[0] : JSUndefined.INSTANCE;
                        resolve(context, value);
                        return JSUndefined.INSTANCE;
                    });
            JSNativeFunction rejectFunc = new JSNativeFunction("", 1,
                    (childContext, thisArg, funcArgs) -> {
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
                        JSValue error = context.getPendingException();
                        context.clearPendingException();
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
                        JSValue error = context.getPendingException();
                        context.clearPendingException();
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
                        errorValue = reaction.context.getPendingException();
                        reaction.context.clearPendingException();
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
    public record ReactionRecord(
            JSFunction handler,
            JSPromise promise,
            JSContext context,
            JSValue capabilityResolve,
            JSValue capabilityReject) {
        public ReactionRecord(JSFunction handler, JSPromise promise, JSContext context) {
            this(handler, promise, context, null, null);
        }

        public ReactionRecord(JSFunction handler, JSContext context, JSValue capabilityResolve, JSValue capabilityReject) {
            this(handler, null, context, capabilityResolve, capabilityReject);
        }
    }

    private static final class ResolveState {
        private boolean alreadyResolved;
    }
}
