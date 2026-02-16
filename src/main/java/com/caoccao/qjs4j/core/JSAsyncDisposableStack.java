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
    private static final JSValue[] NO_ARGS = new JSValue[0];
    private static final String SUPPRESSED_ERROR_MESSAGE = "An error was suppressed during disposal.";
    private final List<DisposeRecord> disposeRecords;
    private boolean disposed;

    public JSAsyncDisposableStack() {
        super();
        this.disposeRecords = new ArrayList<>();
        this.disposed = false;
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSAsyncDisposableStack stack = new JSAsyncDisposableStack();
        context.transferPrototype(stack, NAME);
        return stack;
    }

    public JSValue adopt(JSContext context, JSValue value, JSValue onDisposeAsync) {
        if (disposed) {
            return context.throwTypeError("Cannot add to a disposed AsyncDisposableStack");
        }
        if (!(onDisposeAsync instanceof JSFunction disposeCallback)) {
            return context.throwTypeError("AsyncDisposableStack.adopt requires a function");
        }
        disposeRecords.add(new DisposeRecord(disposeCallback, JSUndefined.INSTANCE, new JSValue[]{value}));
        return value;
    }

    private boolean awaitPromise(JSContext context, JSPromise promise) {
        for (int i = 0; i < 10000 && promise.getState() == JSPromise.PromiseState.PENDING; i++) {
            context.processMicrotasks();
        }
        return promise.getState() != JSPromise.PromiseState.PENDING;
    }

    private JSValue composeSuppressedError(JSContext context, JSValue error, JSValue suppressed) {
        return JSSuppressedError.create(context, error, suppressed, new JSString(SUPPRESSED_ERROR_MESSAGE));
    }

    public JSValue defer(JSContext context, JSValue onDisposeAsync) {
        if (disposed) {
            return context.throwTypeError("Cannot add to a disposed AsyncDisposableStack");
        }
        if (!(onDisposeAsync instanceof JSFunction disposeCallback)) {
            return context.throwTypeError("AsyncDisposableStack.defer requires a function");
        }
        disposeRecords.add(new DisposeRecord(disposeCallback, JSUndefined.INSTANCE, NO_ARGS));
        return JSUndefined.INSTANCE;
    }

    public JSValue disposeAsync(JSContext context) {
        JSPromise promise = context.createJSPromise();
        if (disposed) {
            promise.fulfill(JSUndefined.INSTANCE);
            return promise;
        }
        disposed = true;

        JSValue disposalError = null;
        for (int i = disposeRecords.size() - 1; i >= 0; i--) {
            DisposeRecord record = disposeRecords.get(i);
            JSValue error = invokeDisposer(context, record);
            if (error != null) {
                disposalError = disposalError == null
                        ? error
                        : composeSuppressedError(context, error, disposalError);
            }
        }
        disposeRecords.clear();

        if (disposalError != null) {
            promise.reject(disposalError);
        } else {
            promise.fulfill(JSUndefined.INSTANCE);
        }
        return promise;
    }

    private JSValue invokeDisposer(JSContext context, DisposeRecord record) {
        JSValue result;
        try {
            result = record.function().call(context, record.thisArg(), record.args());
        } catch (JSException e) {
            if (context.hasPendingException()) {
                JSValue pending = context.getPendingException();
                context.clearPendingException();
                return pending;
            }
            return e.getErrorValue();
        } catch (JSVirtualMachineException e) {
            if (context.hasPendingException()) {
                JSValue pending = context.getPendingException();
                context.clearPendingException();
                return pending;
            }
            if (e.getJsError() != null) {
                return e.getJsError();
            }
            JSValue error = context.throwError("Error during async disposal: " + e.getMessage());
            JSValue pending = context.getPendingException();
            context.clearPendingException();
            return pending != null ? pending : error;
        } catch (Throwable t) {
            JSValue error = context.throwError("Error during async disposal: " + t.getMessage());
            JSValue pending = context.getPendingException();
            context.clearPendingException();
            return pending != null ? pending : error;
        }

        if (context.hasPendingException()) {
            JSValue pending = context.getPendingException();
            context.clearPendingException();
            return pending;
        }

        if (result instanceof JSPromise promise) {
            if (!awaitPromise(context, promise)) {
                JSValue error = context.throwError("Promise did not settle during async disposal");
                JSValue pending = context.getPendingException();
                context.clearPendingException();
                return pending != null ? pending : error;
            }
            if (promise.getState() == JSPromise.PromiseState.REJECTED) {
                return promise.getResult();
            }
        }

        return null;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public JSValue move(JSContext context) {
        if (disposed) {
            return context.throwTypeError("Cannot move a disposed AsyncDisposableStack");
        }
        JSAsyncDisposableStack newStack = new JSAsyncDisposableStack();
        newStack.disposeRecords.addAll(disposeRecords);
        newStack.setPrototype(getPrototype());
        disposeRecords.clear();
        disposed = true;
        return newStack;
    }

    public JSValue use(JSContext context, JSValue value) {
        if (disposed) {
            return context.throwTypeError("Cannot add to a disposed AsyncDisposableStack");
        }
        if (value.isNullOrUndefined()) {
            return value;
        }
        if (!(value instanceof JSObject objectValue)) {
            return context.throwTypeError("AsyncDisposableStack.use requires an object or null/undefined");
        }

        JSValue disposeMethodValue = objectValue.get(PropertyKey.SYMBOL_ASYNC_DISPOSE, context);
        JSFunction disposeMethod;
        if (disposeMethodValue instanceof JSFunction asyncDisposeMethod) {
            disposeMethod = asyncDisposeMethod;
        } else {
            disposeMethodValue = objectValue.get(PropertyKey.SYMBOL_DISPOSE, context);
            if (!(disposeMethodValue instanceof JSFunction syncDisposeMethod)) {
                return context.throwTypeError("Object is not async disposable");
            }
            disposeMethod = syncDisposeMethod;
        }

        disposeRecords.add(new DisposeRecord(disposeMethod, objectValue, NO_ARGS));
        return value;
    }

    private record DisposeRecord(JSFunction function, JSValue thisArg, JSValue[] args) {
    }
}
