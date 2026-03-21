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
 * Represents a JavaScript AsyncDisposableStack object.
 * Tracks disposable callbacks and runs them in LIFO order.
 */
public final class JSAsyncDisposableStack extends JSObject {
    public static final String NAME = "AsyncDisposableStack";
    private static final String SUPPRESSED_ERROR_MESSAGE = "An error was suppressed during disposal";
    private final List<DisposeRecord> disposeRecords;
    private boolean disposed;
    private boolean movedFrom;
    private boolean needsAwait;

    public JSAsyncDisposableStack(JSContext context) {
        super(context);
        this.disposeRecords = new ArrayList<>();
        this.disposed = false;
        this.movedFrom = false;
        this.needsAwait = false;
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSAsyncDisposableStack stack = new JSAsyncDisposableStack(context);
        context.transferPrototype(stack, NAME);
        return stack;
    }

    public JSValue adopt(JSContext context, JSValue value, JSValue onDisposeAsync) {
        if (disposed) {
            if (movedFrom) {
                return context.throwTypeError("Cannot call AsyncDisposableStack.prototype.adopt on an already-disposed AsyncDisposableStack");
            }
            return context.throwReferenceError("Cannot add values to a disposed stack!");
        }
        if (!(onDisposeAsync instanceof JSFunction disposeCallback)) {
            return context.throwTypeError("AsyncDisposableStack.adopt requires a function");
        }
        disposeRecords.add(new DisposeRecord(disposeCallback, JSUndefined.INSTANCE, new JSValue[]{value}, true));
        return value;
    }

    private JSValue composeSuppressedError(JSContext context, JSValue error, JSValue suppressed) {
        return JSSuppressedError.create(context, error, suppressed, new JSString(SUPPRESSED_ERROR_MESSAGE));
    }

    public JSValue defer(JSContext context, JSValue onDisposeAsync) {
        if (disposed) {
            if (movedFrom) {
                return context.throwTypeError("Cannot call AsyncDisposableStack.prototype.defer on an already-disposed AsyncDisposableStack");
            }
            return context.throwReferenceError("Cannot add values to a disposed stack!");
        }
        if (!(onDisposeAsync instanceof JSFunction disposeCallback)) {
            return context.throwTypeError("AsyncDisposableStack.defer requires a function");
        }
        disposeRecords.add(new DisposeRecord(disposeCallback, JSUndefined.INSTANCE, JSValue.NO_ARGS, true));
        return JSUndefined.INSTANCE;
    }

    public JSValue disposeAsync(JSContext context) {
        return disposeAsync(context, null);
    }

    public JSValue disposeAsync(JSContext context, JSValue completionError) {
        JSPromise promise = context.createJSPromise();

        JSValue initialError = completionError;
        if (initialError == null && context.hasPendingException()) {
            initialError = context.getPendingException();
            context.clearPendingException();
        }

        if (disposed) {
            if (initialError != null) {
                promise.reject(initialError);
            } else {
                promise.fulfill(JSUndefined.INSTANCE);
            }
            return promise;
        }
        disposed = true;
        disposeNextRecord(context, disposeRecords.size() - 1, initialError, false, promise);
        return promise;
    }

    private void disposeNextRecord(
            JSContext context,
            int index,
            JSValue accumulatedError,
            boolean hasAwaited,
            JSPromise completionPromise) {
        if (index < 0) {
            disposeRecords.clear();
            if (needsAwait && !hasAwaited) {
                // Per spec: If needsAwait is true and hasAwaited is false, Perform ! Await(undefined).
                JSPromise awaitPromise = context.createJSPromise();
                awaitPromise.resolve(context, JSUndefined.INSTANCE);
                JSNativeFunction onFulfilled = new JSNativeFunction(context, "", 1,
                        (childContext, thisArg, args) -> {
                            if (accumulatedError != null) {
                                completionPromise.reject(accumulatedError);
                            } else {
                                completionPromise.fulfill(JSUndefined.INSTANCE);
                            }
                            return JSUndefined.INSTANCE;
                        });
                JSNativeFunction onRejected = new JSNativeFunction(context, "", 1,
                        (childContext, thisArg, args) -> {
                            if (accumulatedError != null) {
                                completionPromise.reject(accumulatedError);
                            } else {
                                completionPromise.fulfill(JSUndefined.INSTANCE);
                            }
                            return JSUndefined.INSTANCE;
                        });
                context.transferPrototype(onFulfilled, JSFunction.NAME);
                context.transferPrototype(onRejected, JSFunction.NAME);
                awaitPromise.addReactions(
                        new JSPromise.ReactionRecord(onFulfilled, null, context),
                        new JSPromise.ReactionRecord(onRejected, null, context));
                return;
            }
            if (accumulatedError != null) {
                completionPromise.reject(accumulatedError);
            } else {
                completionPromise.fulfill(JSUndefined.INSTANCE);
            }
            return;
        }
        DisposeRecord record = disposeRecords.get(index);
        JSValue disposerResult = JSUndefined.INSTANCE;
        JSValue currentError = null;
        try {
            disposerResult = record.function().call(context, record.thisArg(), record.args());
        } catch (JSException e) {
            if (context.hasPendingException()) {
                currentError = context.getPendingException();
                context.clearPendingException();
            } else {
                currentError = e.getErrorValue();
            }
        } catch (JSVirtualMachineException e) {
            if (context.hasPendingException()) {
                currentError = context.getPendingException();
                context.clearPendingException();
            } else if (e.getJsValue() != null) {
                currentError = e.getJsValue();
            } else if (e.getJsError() != null) {
                currentError = e.getJsError();
            } else {
                JSValue error = context.throwError("Error during async disposal: " + e.getMessage());
                JSValue pending = context.getPendingException();
                context.clearPendingException();
                currentError = pending != null ? pending : error;
            }
        } catch (Throwable t) {
            JSValue error = context.throwError("Error during async disposal: " + t.getMessage());
            JSValue pending = context.getPendingException();
            context.clearPendingException();
            currentError = pending != null ? pending : error;
        }

        if (currentError == null && context.hasPendingException()) {
            currentError = context.getPendingException();
            context.clearPendingException();
        }
        JSValue nextAccumulatedError = mergeDisposalError(context, currentError, accumulatedError);
        if (currentError != null) {
            disposeNextRecord(context, index - 1, nextAccumulatedError, hasAwaited, completionPromise);
            return;
        }
        if (!record.awaitResult()) {
            disposeNextRecord(context, index - 1, nextAccumulatedError, hasAwaited, completionPromise);
            return;
        }
        JSPromise awaitedResult = context.createJSPromise();
        awaitedResult.resolve(context, disposerResult);
        JSNativeFunction onFulfilled = new JSNativeFunction(context, "", 1,
                (childContext, thisArg, args) -> {
                    disposeNextRecord(childContext, index - 1, nextAccumulatedError, true, completionPromise);
                    return JSUndefined.INSTANCE;
                });
        JSNativeFunction onRejected = new JSNativeFunction(context, "", 1,
                (childContext, thisArg, args) -> {
                    JSValue rejectionValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                    JSValue mergedError = mergeDisposalError(childContext, rejectionValue, nextAccumulatedError);
                    disposeNextRecord(childContext, index - 1, mergedError, true, completionPromise);
                    return JSUndefined.INSTANCE;
                });
        context.transferPrototype(onFulfilled, JSFunction.NAME);
        context.transferPrototype(onRejected, JSFunction.NAME);
        awaitedResult.addReactions(
                new JSPromise.ReactionRecord(onFulfilled, null, context),
                new JSPromise.ReactionRecord(onRejected, null, context));

    }

    public boolean isDisposed() {
        return disposed;
    }

    private JSValue mergeDisposalError(JSContext context, JSValue currentError, JSValue accumulatedError) {
        if (currentError == null) {
            return accumulatedError;
        }
        if (accumulatedError == null) {
            return currentError;
        }
        return composeSuppressedError(context, currentError, accumulatedError);
    }

    public JSValue move(JSContext context) {
        if (disposed) {
            if (movedFrom) {
                return context.throwTypeError("Cannot call AsyncDisposableStack.prototype.move on an already-disposed AsyncDisposableStack");
            }
            return context.throwReferenceError("Cannot move elements from a disposed stack!");
        }
        JSAsyncDisposableStack newStack = new JSAsyncDisposableStack(context);
        context.transferPrototype(newStack, NAME);
        newStack.disposeRecords.addAll(disposeRecords);
        newStack.needsAwait = needsAwait;
        disposeRecords.clear();
        disposed = true;
        movedFrom = true;
        return newStack;
    }

    public JSValue use(JSContext context, JSValue value) {
        if (disposed) {
            if (movedFrom) {
                return context.throwTypeError("Cannot call AsyncDisposableStack.prototype.use on an already-disposed AsyncDisposableStack");
            }
            return context.throwReferenceError("Cannot add values to a disposed stack!");
        }
        if (value.isNullOrUndefined()) {
            needsAwait = true;
            return value;
        }
        if (!(value instanceof JSObject objectValue)) {
            return context.throwTypeError("AsyncDisposableStack.use requires an object or null/undefined");
        }

        JSValue disposeMethodValue = objectValue.get(PropertyKey.SYMBOL_ASYNC_DISPOSE);
        JSFunction disposeMethod;
        if (disposeMethodValue instanceof JSFunction asyncDisposeMethod) {
            disposeMethod = asyncDisposeMethod;
            disposeRecords.add(new DisposeRecord(disposeMethod, objectValue, JSValue.NO_ARGS, true));
        } else {
            disposeMethodValue = objectValue.get(PropertyKey.SYMBOL_DISPOSE);
            if (!(disposeMethodValue instanceof JSFunction syncDisposeMethod)) {
                return context.throwTypeError("Object is not async disposable");
            }
            disposeMethod = syncDisposeMethod;
            disposeRecords.add(new DisposeRecord(disposeMethod, objectValue, JSValue.NO_ARGS, false));
        }
        return value;
    }

    private record DisposeRecord(JSFunction function, JSValue thisArg, JSValue[] args, boolean awaitResult) {
    }
}
