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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.error.JSError;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.exceptions.*;

import static com.caoccao.qjs4j.vm.VirtualMachine.GENERATOR_RETURN_SIGNAL;
import com.caoccao.qjs4j.vm.VirtualMachine.PendingExceptionAction;

/** Routes abrupt completions through iterator, catch and finally markers on the operand stack. */
final class ExceptionUnwinder {
    private ExceptionUnwinder() {
    }

    /**
     * Auto-close an iterator during exception unwinding. Following QuickJS JS_IteratorClose semantics: - If
     * isThrowCompletion is true, close errors are suppressed (original error preserved). - If isThrowCompletion is
     * false (a generator's return completion), a close error is left in {@code pendingException} for the unwinder,
     * replacing the return completion.
     */
    private static void autoCloseIterator(ExecutionContext execution, JSObject iterator, boolean throwing) {
        VirtualMachine vm = execution.virtualMachine;
        JSContext context = vm.context;
        try {
            JSValue method = iterator.get(PropertyKey.RETURN);
            if (captureCloseError(vm, throwing) || method.isNullOrUndefined()) {
                return;
            }
            if (method instanceof JSFunction function) {
                JSValue result = function.call(context, iterator);
                if (!captureCloseError(vm, throwing) && !throwing && !(result instanceof JSObject)) {
                    vm.pendingException = context.throwTypeError("iterator result is not an object");
                }
            } else if (!throwing) {
                vm.pendingException = context.throwTypeError("iterator return is not a function");
            }
        } catch (JSVirtualMachineException error) {
            captureCloseException(vm, throwing, error);
        }
    }

    private static boolean captureCloseError(VirtualMachine vm, boolean throwing) {
        if (!vm.context.hasPendingException()) {
            return false;
        }
        JSValue error = vm.context.takePendingException();
        if (!throwing) {
            vm.pendingException = error;
        }
        return true;
    }

    private static void captureCloseException(VirtualMachine vm, boolean throwing, JSVirtualMachineException error) {
        if (throwing) {
            vm.context.clearPendingException();
        } else {
            vm.captureVMException(error);
        }
    }

    private static JSPromise closeAsyncIterator(ExecutionContext execution, JSObject iterator, JSValue completion) {
        VirtualMachine vm = execution.virtualMachine;
        JSContext context = vm.context;
        boolean throwing = completion != GENERATOR_RETURN_SIGNAL;
        JSValue closeResult;
        try {
            JSValue method = iterator.get(PropertyKey.RETURN);
            if (captureCloseError(vm, throwing)) {
                return null;
            }
            if (method.isNullOrUndefined()) {
                return null;
            }
            closeResult = JSCall.call(context, method, iterator);
        } catch (JSVirtualMachineException error) {
            captureCloseException(vm, throwing, error);
            return null;
        }
        if (captureCloseError(vm, throwing)) {
            return null;
        }
        JSPromise awaited = context.createJSPromise();
        awaited.resolve(context, closeResult);
        JSPromise continuation = context.createJSPromise();
        JSNativeFunction fulfilled = new JSNativeFunction(context, "", 1, (ctx, receiver, args) -> {
            JSValue nextCompletion = completion;
            if (!throwing && !(args[0] instanceof JSObject)) {
                nextCompletion = ctx.throwTypeError("iterator result is not an object");
                ctx.clearPendingException();
            }
            continuation.reject(nextCompletion);
            return JSUndefined.INSTANCE;
        });
        JSNativeFunction rejected = new JSNativeFunction(context, "", 1, (ctx, receiver, args) -> {
            continuation.reject(throwing ? completion : args[0]);
            return JSUndefined.INSTANCE;
        });
        awaited.observe(context, fulfilled, rejected);
        return continuation;
    }

    static PendingExceptionAction unwind(ExecutionContext executionContext) {
        VirtualMachine vm = executionContext.virtualMachine;
        if (vm.pendingException == null) {
            return PendingExceptionAction.NONE;
        }

        JSValue exception = vm.pendingException;
        vm.pendingException = null;
        vm.context.clearPendingException();

        boolean foundHandler = false;
        while (vm.valueStack.stackTop > executionContext.frameStackBase) {
            JSStackValue stackValue = vm.valueStack.stack[--vm.valueStack.stackTop];
            if (stackValue instanceof JSCatchOffset catchOffset) {
                if (catchOffset.isIteratorCloseMarker()) {
                    // Iterator enumerator marker (from FOR_OF_START). Following QuickJS:
                    // auto-close the iterator. Stack below is: [iter, next].
                    // Pop next method, then close iterator.
                    vm.valueStack.stackTop--; // pop next
                    int iterIdx = vm.valueStack.stackTop - 1;
                    if (iterIdx >= executionContext.frameStackBase) {
                        JSValue iteratorValue = (JSValue) vm.valueStack.stack[iterIdx];
                        vm.valueStack.stackTop--; // pop iter
                        if (iteratorValue instanceof JSObject iteratorObj && !iteratorValue.isUndefined()) {
                            // A throw completion keeps its exception whatever the close does. A return
                            // completion is replaced by a close that fails, and that failure is an
                            // ordinary exception the catch handlers below may take.
                            if (catchOffset.isAsyncIteratorCloseMarker() && vm.awaitSuspensionEnabled
                                    && vm.activeGeneratorState != null) {
                                JSPromise continuation = closeAsyncIterator(executionContext, iteratorObj, exception);
                                if (continuation != null) {
                                    vm.awaitSuspensionPromise = continuation;
                                    executionContext.sp = vm.valueStack.stackTop;
                                    vm.saveActiveGeneratorSuspendedExecutionState(executionContext.frame,
                                            executionContext.pc, vm.valueStack.stack, executionContext.sp,
                                            executionContext.frameStackBase);
                                    vm.requestOpcodeReturnFromExecute(executionContext, JSUndefined.INSTANCE);
                                    return PendingExceptionAction.RETURN;
                                }
                            } else {
                                autoCloseIterator(executionContext, iteratorObj, exception != GENERATOR_RETURN_SIGNAL);
                            }
                            if (vm.pendingException != null) {
                                exception = vm.pendingException;
                                vm.pendingException = null;
                                vm.context.clearPendingException();
                            }
                        }
                    }
                    continue;
                }
                // A return completion runs finally blocks only; an exception a finally block throws
                // is not the return signal, so it replaces the return and every handler sees it.
                if (exception == GENERATOR_RETURN_SIGNAL && !catchOffset.isFinally()) {
                    continue;
                }
                vm.valueStack.pushStackValue(exception);
                executionContext.pc = catchOffset.offset();
                foundHandler = true;
                vm.context.clearPendingException();
                break;
            }
        }
        executionContext.sp = vm.valueStack.stackTop;

        if (foundHandler) {
            return PendingExceptionAction.CONTINUE;
        }

        if (exception == GENERATOR_RETURN_SIGNAL) {
            vm.finalizeExecuteReturn(executionContext);
            vm.context.clearPendingException();
            executionContext.returnValue = vm.generatorReturnValue;
            return PendingExceptionAction.RETURN;
        }

        vm.finalizeExecuteReturn(executionContext);
        if (exception instanceof JSError jsError) {
            String vmMessage = jsError.getVmMessage();
            if (vmMessage != null && !vmMessage.isEmpty()) {
                throw new JSVirtualMachineException(vmMessage, jsError);
            }
            throw new JSVirtualMachineException(jsError);
        }
        String exceptionMessage = executionContext.virtualMachine.safeExceptionToString(vm.context, exception);
        throw new JSVirtualMachineException("Unhandled exception: " + exceptionMessage, exception);
    }
}
