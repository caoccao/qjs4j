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
import com.caoccao.qjs4j.core.error.JSSuppressedError;
import java.util.ArrayList;
import java.util.List;

/** A stack owns its disposal records until it disposes them or transfers them to a new stack. */
public abstract sealed class JSDisposableStackBase extends JSObject permits JSDisposableStack, JSAsyncDisposableStack {
    boolean disposed;
    List<DisposeRecord> disposeRecords = new ArrayList<>();

    JSDisposableStackBase(JSContext context) {
        super(context);
    }

    public final JSValue adopt(JSContext context, JSValue value, JSValue onDispose) {
        if (disposed) {
            return disposedError(context, "adopt");
        }
        if (!(onDispose instanceof JSFunction callback)) {
            return context.throwTypeError(stackName() + ".adopt requires a function");
        }
        disposeRecords.add(new DisposeRecord(callback, JSUndefined.INSTANCE, new JSValue[]{value},
                this instanceof JSAsyncDisposableStack));
        return value;
    }

    public final JSValue defer(JSContext context, JSValue onDispose) {
        if (disposed) {
            return disposedError(context, "defer");
        }
        if (!(onDispose instanceof JSFunction callback)) {
            return context.throwTypeError(stackName() + ".defer requires a function");
        }
        disposeRecords.add(new DisposeRecord(callback, JSUndefined.INSTANCE, JSValue.NO_ARGS,
                this instanceof JSAsyncDisposableStack));
        return JSUndefined.INSTANCE;
    }

    abstract JSValue disposedError(JSContext context, String operation);

    public final boolean isDisposed() {
        return disposed;
    }

    public abstract JSValue move(JSContext context);

    private String stackName() {
        return this instanceof JSAsyncDisposableStack ? JSAsyncDisposableStack.NAME : JSDisposableStack.NAME;
    }

    final void transferRecords(JSDisposableStackBase target) {
        // The destination has just been constructed. Swapping lists transfers ownership in constant time.
        List<DisposeRecord> empty = target.disposeRecords;
        target.disposeRecords = disposeRecords;
        disposeRecords = empty;
        disposed = true;
    }

    public abstract JSValue use(JSContext context, JSValue value);

    static JSValue mergeDisposalError(JSContext context, JSValue currentError, JSValue accumulatedError) {
        if (currentError == null) {
            return accumulatedError;
        }
        if (accumulatedError == null) {
            return currentError;
        }
        return JSSuppressedError.create(context, currentError, accumulatedError,
                new JSString("An error was suppressed during disposal"));
    }

    record DisposeRecord(JSFunction function, JSValue thisArg, JSValue[] args, boolean awaitResult) {
    }
}
