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
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

/**
 * Represents a JavaScript DisposableStack object. Tracks disposable callbacks and runs them in LIFO order.
 */
public final class JSDisposableStack extends JSDisposableStackBase {
    public static final String NAME = "DisposableStack";

    public JSDisposableStack(JSContext context) {
        super(context);
    }

    public JSValue dispose(JSContext context) {
        return dispose(context, null);
    }

    public JSValue dispose(JSContext context, JSValue completionError) {
        JSValue initialError = completionError == null ? context.takePendingException() : completionError;

        if (disposed) {
            if (initialError != null) {
                context.setPendingException(initialError);
                return initialError;
            }
            return JSUndefined.INSTANCE;
        }
        disposed = true;

        // Follow ES spec DisposeResources: fold each disposal error with the
        // current completion value. This ensures correct SuppressedError nesting:
        // SuppressedError(lastDisposalError, SuppressedError(prevDisposalError, initialError))
        JSValue completion = initialError;
        for (int i = disposeRecords.size() - 1; i >= 0; i--) {
            DisposeRecord record = disposeRecords.get(i);
            JSValue error = invokeDisposer(context, record);
            completion = mergeDisposalError(context, error, completion);
        }
        disposeRecords.clear();

        if (completion != null) {
            context.setPendingException(completion);
            return completion;
        }
        return JSUndefined.INSTANCE;
    }

    @Override
    JSValue disposedError(JSContext context, String operation) {
        return context.throwReferenceError(
                "Cannot call DisposableStack.prototype." + operation + " on an already-disposed DisposableStack");
    }

    private JSValue invokeDisposer(JSContext context, DisposeRecord record) {
        try {
            record.function().call(context, record.thisArg(), record.args());
        } catch (JSException e) {
            if (context.hasPendingException()) {
                JSValue pending = context.takePendingException();
                return pending;
            }
            return e.getErrorValue();
        } catch (JSVirtualMachineException e) {
            if (context.hasPendingException()) {
                JSValue pending = context.takePendingException();
                return pending;
            }
            if (e.getJsValue() != null) {
                return e.getJsValue();
            }
            if (e.getJsError() != null) {
                return e.getJsError();
            }
            JSValue error = context.throwError("Error during disposal: " + e.getMessage());
            JSValue pending = context.takePendingException();
            return pending != null ? pending : error;
        }
        // Anything else — a host defect, OutOfMemoryError, termination — is not a JavaScript error: it reaches the
        // virtual machine, which reports it to the embedder uncatchably. It used to become an "Error during disposal"
        // that the script could catch, and execution went on in a JVM that had just failed an allocation.
        if (context.hasPendingException()) {
            JSValue pending = context.takePendingException();
            return pending;
        }
        return null;
    }

    @Override
    public JSValue move(JSContext context) {
        if (disposed) {
            return disposedError(context, "move");
        }
        JSDisposableStack newStack = context.createJSDisposableStack();
        transferRecords(newStack);
        return newStack;
    }

    @Override
    public JSValue use(JSContext context, JSValue value) {
        if (disposed) {
            return disposedError(context, "use");
        }
        if (value.isNullOrUndefined()) {
            return value;
        }
        if (!(value instanceof JSObject objectValue)) {
            return context.throwTypeError("An object is expected with `using` declarations");
        }

        JSValue disposeMethodValue = objectValue.get(PropertyKey.SYMBOL_DISPOSE);
        if (!(disposeMethodValue instanceof JSFunction disposeMethod)) {
            return context.throwTypeError("Object is not disposable");
        }

        disposeRecords.add(new DisposeRecord(disposeMethod, objectValue, JSValue.NO_ARGS, false));
        return value;
    }

    public static JSObject create(JSContext context, JSValue... args) {
        return context.createJSDisposableStack();
    }
}
