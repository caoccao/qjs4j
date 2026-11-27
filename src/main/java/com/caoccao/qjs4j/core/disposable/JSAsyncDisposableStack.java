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

package com.caoccao.qjs4j.core.disposable;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

/**
 * Represents a JavaScript AsyncDisposableStack object. Tracks disposable callbacks and runs them in LIFO order.
 */
public final class JSAsyncDisposableStack extends JSDisposableStackBase {
    public static final String NAME = "AsyncDisposableStack";
    private boolean movedFrom;
    private boolean needsAwait;

    public JSAsyncDisposableStack(JSContext context) {
        super(context);
    }

    public JSValue disposeAsync(JSContext context) {
        return disposeAsync(context, null);
    }

    public JSValue disposeAsync(JSContext context, JSValue completionError) {
        JSPromise promise = context.createJSPromise();

        JSValue initialError = completionError == null ? context.takePendingException() : completionError;

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

    @Override
    JSValue disposedError(JSContext context, String operation) {
        if (movedFrom) {
            return context.throwTypeError("Cannot call AsyncDisposableStack.prototype." + operation
                    + " on an already-disposed AsyncDisposableStack");
        }
        return context.throwReferenceError("move".equals(operation)
                ? "Cannot move elements from a disposed stack!"
                : "Cannot add values to a disposed stack!");
    }

    private void disposeNextRecord(JSContext context, int index, JSValue accumulatedError, boolean hasAwaited,
            JSPromise completionPromise) {
        if (index < 0) {
            disposeRecords.clear();
            if (needsAwait && !hasAwaited) {
                // Per spec: If needsAwait is true and hasAwaited is false, Perform ! Await(undefined).
                JSPromise awaitPromise = context.createJSPromise();
                awaitPromise.resolve(context, JSUndefined.INSTANCE);
                JSNativeFunction finish = new JSNativeFunction(context, "", 1, (childContext, thisArg, args) -> {
                    settleCompletion(completionPromise, accumulatedError);
                    return JSUndefined.INSTANCE;
                });
                context.transferPrototype(finish, JSFunction.NAME);
                awaitPromise.observe(context, finish, finish);
                return;
            }
            settleCompletion(completionPromise, accumulatedError);
            return;
        }
        DisposeRecord record = disposeRecords.get(index);
        JSValue disposerResult = JSUndefined.INSTANCE;
        JSValue currentError = null;
        try {
            disposerResult = record.function().call(context, record.thisArg(), record.args());
        } catch (JSException e) {
            if (context.hasPendingException()) {
                currentError = context.takePendingException();
            } else {
                currentError = e.getErrorValue();
            }
        } catch (JSVirtualMachineException e) {
            if (context.hasPendingException()) {
                currentError = context.takePendingException();
            } else if (e.getJsValue() != null) {
                currentError = e.getJsValue();
            } else if (e.getJsError() != null) {
                currentError = e.getJsError();
            } else {
                JSValue error = context.throwError("Error during async disposal: " + e.getMessage());
                JSValue pending = context.takePendingException();
                currentError = pending != null ? pending : error;
            }
        }
        // Anything else — a host defect, OutOfMemoryError, termination — is not a JavaScript error and reaches the
        // virtual machine, which reports it to the embedder uncatchably.

        if (currentError == null && context.hasPendingException()) {
            currentError = context.takePendingException();
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
        JSNativeFunction onFulfilled = new JSNativeFunction(context, "", 1, (childContext, thisArg, args) -> {
            disposeNextRecord(childContext, index - 1, nextAccumulatedError, true, completionPromise);
            return JSUndefined.INSTANCE;
        });
        JSNativeFunction onRejected = new JSNativeFunction(context, "", 1, (childContext, thisArg, args) -> {
            JSValue rejectionValue = JSValue.argumentOrUndefined(args, 0);
            JSValue mergedError = mergeDisposalError(childContext, rejectionValue, nextAccumulatedError);
            disposeNextRecord(childContext, index - 1, mergedError, true, completionPromise);
            return JSUndefined.INSTANCE;
        });
        context.transferPrototype(onFulfilled, JSFunction.NAME);
        context.transferPrototype(onRejected, JSFunction.NAME);
        awaitedResult.observe(context, onFulfilled, onRejected);

    }

    @Override
    public JSValue move(JSContext context) {
        if (disposed) {
            return disposedError(context, "move");
        }
        JSAsyncDisposableStack newStack = new JSAsyncDisposableStack(context);
        context.transferPrototype(newStack, NAME);
        transferRecords(newStack);
        newStack.needsAwait = needsAwait;
        movedFrom = true;
        return newStack;
    }

    @Override
    public JSValue use(JSContext context, JSValue value) {
        if (disposed) {
            return disposedError(context, "use");
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

    public static JSObject create(JSContext context, JSValue... args) {
        JSAsyncDisposableStack stack = new JSAsyncDisposableStack(context);
        context.transferPrototype(stack, NAME);
        return stack;
    }

    private static void settleCompletion(JSPromise promise, JSValue error) {
        if (error == null) {
            promise.fulfill(JSUndefined.INSTANCE);
        } else {
            promise.reject(error);
        }
    }
}
