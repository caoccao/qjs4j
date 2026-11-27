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
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.Set;

/** Iterator records, for-of loops, and generator suspension and delegation. */
final class IteratorOpcodes {
    private static final YieldResult INITIAL_YIELD = new YieldResult(YieldResult.Type.INITIAL_YIELD,
            JSUndefined.INSTANCE);

    private IteratorOpcodes() {
    }

    private static JSNumber appendIterable(ExecutionContext executionContext, JSArray array, int position,
            JSValue enumerableObject) {
        try {
            JSContext context = executionContext.virtualMachine.context;
            JSValue iterator = JSIteratorHelper.getIterator(context, enumerableObject);
            if (executionContext.capturePendingException()) {
                return null;
            }
            if (iterator == null) {
                executionContext.virtualMachine.pendingException = context.throwTypeError("Value is not iterable");
                context.clearPendingException();
                return null;
            }

            while (true) {
                JSObject resultObject = JSIteratorHelper.iteratorNext(iterator, context);
                if (executionContext.capturePendingException()) {
                    return null;
                }
                if (resultObject == null) {
                    break;
                }
                JSValue doneValue = resultObject.get(PropertyKey.DONE);
                if (executionContext.capturePendingException()) {
                    return null;
                }
                if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                    break;
                }
                JSValue value = resultObject.get(PropertyKey.VALUE);
                if (executionContext.capturePendingException()) {
                    return null;
                }
                array.set(position++, value);
                if (executionContext.capturePendingException()) {
                    return null;
                }
            }
        } catch (JSVirtualMachineException e) {
            executionContext.virtualMachine.captureVMException(e);
            return null;
        }
        return JSNumber.of(position);
    }

    private static JSObject callDelegateMethod(ExecutionContext executionContext, JSObject iterator, JSValue method,
            JSValue argument, String nonCallableMessage) {
        if (!JSTypeChecking.isCallable(method)) {
            executionContext.raiseTypeError(nonCallableMessage);
            return null;
        }
        VirtualMachine vm = executionContext.virtualMachine;
        JSValue result = JSCall.call(vm.context, method, iterator, vm.singleArg(argument));
        if (executionContext.capturePendingException()) {
            return null;
        }
        if (result instanceof JSObject object) {
            return object;
        }
        executionContext.raiseTypeError("iterator must return an object");
        return null;
    }

    private static JSObject callDelegateNext(ExecutionContext executionContext, JSObject iterator, JSValue method,
            JSValue[] args) {
        JSValue result = JSCall.call(executionContext.virtualMachine.context, method, iterator, args);
        if (executionContext.capturePendingException()) {
            return null;
        }
        if (result instanceof JSObject object) {
            return object;
        }
        executionContext.raiseTypeError("Iterator result must be an object");
        return null;
    }

    private static boolean captureCloseException(VirtualMachine vm, JSValue originalException) {
        if (!vm.context.hasPendingException()) {
            return false;
        }
        JSValue error = vm.context.takePendingException();
        if (originalException == null) {
            vm.pendingException = error;
        }
        return true;
    }

    private static boolean captureForOfStepException(ExecutionContext executionContext, JSStackValue[] preservedMarkers,
            int preservedMarkerCount, int depth, int nextPc) {
        VirtualMachine vm = executionContext.virtualMachine;
        if (!vm.context.hasPendingException()) {
            return false;
        }
        restoreForOfStateWithoutIteratorCloseMarker(executionContext, preservedMarkers, preservedMarkerCount, depth);
        vm.pendingException = vm.context.takePendingException();
        executionContext.finishStackOpcode(nextPc);
        return true;
    }

    private static int findIteratorCloseMarker(JSStackValue[] stack, int top, int base) {
        for (int index = top - 1; index >= base; index--) {
            if (stack[index] instanceof JSCatchOffset marker && marker.isIteratorCloseMarker()) {
                return index;
            }
        }
        return -1;
    }

    static void handleAppend(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue enumerableObject = (JSValue) stack[--sp];
        JSValue positionValue = (JSValue) stack[--sp];
        JSValue arrayValue = (JSValue) stack[--sp];

        if (!(arrayValue instanceof JSArray array)) {
            throw new JSVirtualMachineException("APPEND: first argument must be an array");
        }

        if (!(positionValue instanceof JSNumber positionNumber)) {
            throw new JSVirtualMachineException("APPEND: second argument must be a number");
        }

        JSNumber nextPosition = appendIterable(executionContext, array, (int) positionNumber.value(), enumerableObject);
        stack[sp++] = nextPosition == null ? JSUndefined.INSTANCE : array;
        stack[sp++] = nextPosition == null ? JSUndefined.INSTANCE : nextPosition;
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleAsyncYieldStar(Opcode op, ExecutionContext executionContext) {
        VirtualMachine vm = executionContext.virtualMachine;
        vm.valueStack.stackTop = executionContext.sp;
        JSContext context = vm.context;

        // yield* in async generators uses GetIterator(obj, async) per ES2024 7.4.3
        JSValue asyncYieldStarIterable = vm.valueStack.pop();

        JSObject iterable = vm.toObject(asyncYieldStarIterable);
        if (iterable == null) {
            executionContext.raiseTypeError(asyncYieldStarIterable + " is not iterable");
            return;
        }
        JSValue method = iterable.get(PropertyKey.SYMBOL_ASYNC_ITERATOR);
        if (executionContext.capturePendingException()) {
            return;
        }
        boolean isNativeAsyncIterator = !method.isNullOrUndefined();
        if (!isNativeAsyncIterator) {
            method = iterable.get(PropertyKey.SYMBOL_ITERATOR);
            if (executionContext.capturePendingException()) {
                return;
            }
        }
        if (!JSTypeChecking.isCallable(method)) {
            executionContext.raiseTypeError("is not a function");
            return;
        }
        JSValue iterator = JSCall.call(context, method, asyncYieldStarIterable, JSValue.NO_ARGS);
        if (executionContext.capturePendingException()) {
            return;
        }
        if (!(iterator instanceof JSObject iteratorObj)) {
            executionContext.raiseTypeError(isNativeAsyncIterator
                    ? "Result of the Symbol.asyncIterator method is not an object"
                    : "Result of the Symbol.iterator method is not an object");
            return;
        }

        // Check for RETURN/THROW resume records (yield* delegation protocol per ES2024 27.5.3.3)
        JSGeneratorState.ResumeRecord resumeRecord = vm.currentGeneratorResumeRecord();

        if (resumeRecord != null && (resumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN
                || resumeRecord.kind() == JSGeneratorState.ResumeKind.THROW)) {
            if (!resumeAbruptDelegate(op, executionContext, iteratorObj, resumeRecord, isNativeAsyncIterator)) {
                return;
            }
        } else {
            // Default: NEXT protocol -- call iterator.next()
            JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);
            if (executionContext.capturePendingException()) {
                return;
            }
            if (!JSTypeChecking.isCallable(nextMethod)) {
                executionContext.setErrorAsPending(context.throwTypeError("is not a function"));
                return;
            }

            // Per ES2024 spec: Invoke(iterator, "next", << received.[[Value]] >>)
            JSValue[] nextArgs = new JSValue[]{JSUndefined.INSTANCE};

            // Skip past previously-yielded values during generator replay
            boolean asyncInnerExhausted = false;
            while (vm.yieldSkipCount > 0) {
                ReplayStep step = replayDelegateStep(executionContext, iteratorObj, nextMethod, nextArgs);
                if (step == ReplayStep.ABRUPT) {
                    return;
                }
                if (step == ReplayStep.DONE) {
                    asyncInnerExhausted = true;
                    break;
                }
                vm.yieldSkipCount--;
            }

            if (!asyncInnerExhausted) {
                JSObject result = callDelegateNext(executionContext, iteratorObj, nextMethod, nextArgs);
                if (result == null) {
                    return;
                }

                yieldDelegateResult(executionContext, iteratorObj, result, nextMethod, -1, isNativeAsyncIterator);
            }
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
        if (vm.yieldResult != null) {
            JSValue returnValue = executionContext.pop();
            // Save execution state so the generator can resume from after ASYNC_YIELD_STAR
            // when the delegate iterator completes (done=true), avoiding side-effect replay
            suspendGenerator(executionContext, returnValue);
        }
    }

    static void handleAwait(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue value = executionContext.virtualMachine.valueStack.pop();

        // If the value is already a promise, preserve PromiseResolve observability by
        // accessing `constructor` first (may throw), then use the promise directly.
        // Otherwise, wrap it via Promise.resolve() which handles thenables.
        JSPromise promise;
        if (value instanceof JSPromise promiseValue) {
            promiseValue.get(PropertyKey.CONSTRUCTOR);
            if (executionContext.virtualMachine.context.hasPendingException()) {
                executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                        .getPendingException();
                executionContext.finishStackOpcode(executionContext.pc + op.getSize());
                return;
            }
            promise = promiseValue;
        } else {
            promise = executionContext.virtualMachine.context.createJSPromise();
            promise.resolve(executionContext.virtualMachine.context, value);
        }

        // Per ES2024 spec (25.5.5.3 Await), await always takes exactly 1 microtask tick.
        // When running in suspension mode (inside an async function), always suspend and let
        // the reaction callbacks in resumeAsyncFunctionExecution handle resumption via microtask.
        if (executionContext.virtualMachine.awaitSuspensionEnabled
                && executionContext.virtualMachine.activeGeneratorState != null) {
            executionContext.virtualMachine.awaitSuspensionPromise = promise;
        } else {
            // Fallback for non-suspension mode: always process microtasks to ensure
            // pending reactions (e.g., Promise.resolve().then(cb)) run before code after await.
            // ES2024 25.5.5.3: await always takes at least 1 microtask tick.
            executionContext.virtualMachine.context.processMicrotasks();
            // Awaiting handles the promise's rejection, exactly as attaching a reaction would.
            promise.markHandled();

            // Now the promise should be settled, push the resolved value
            if (promise.getState() == JSPromise.PromiseState.FULFILLED) {
                executionContext.virtualMachine.valueStack.push(promise.getResult());
            } else if (promise.getState() == JSPromise.PromiseState.REJECTED) {
                JSValue result = promise.getResult();
                executionContext.virtualMachine.pendingException = result;
                executionContext.virtualMachine.context.setPendingException(result);
            } else {
                // Promise is still pending - this shouldn't happen
                throw new JSVirtualMachineException("Promise did not settle after processing microtasks");
            }
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
        if (executionContext.virtualMachine.awaitSuspensionPromise != null) {
            suspendGenerator(executionContext, JSUndefined.INSTANCE);
        }
    }

    static void handleForAwaitOfNext(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSStackValue catchOffset = executionContext.virtualMachine.valueStack.popStackValue();
        JSValue nextMethod = executionContext.virtualMachine.valueStack.peek(0);
        JSValue iterator = executionContext.virtualMachine.valueStack.peek(1);
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            throw new JSVirtualMachineException("Next method must be a function");
        }
        JSValue result = nextFunc.call(executionContext.virtualMachine.context, iterator, JSValue.NO_ARGS);
        executionContext.virtualMachine.valueStack.pushStackValue(catchOffset);
        executionContext.virtualMachine.valueStack.push(result);
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleForInNext(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSStackValue stackValue = executionContext.virtualMachine.valueStack.popStackValue();
        if (!(stackValue instanceof JSInternalValue internal)
                || !(internal.value() instanceof JSForInEnumerator enumerator)) {
            throw new JSVirtualMachineException("Invalid for-in enumerator");
        }
        JSValue nextKey = enumerator.next();
        executionContext.virtualMachine.valueStack.pushStackValue(new JSInternalValue(enumerator));
        executionContext.virtualMachine.valueStack.push(nextKey);
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleForInStart(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        JSValue sourceValue = executionContext.virtualMachine.valueStack.pop();
        JSValue enumerableValue = sourceValue;
        if (!sourceValue.isNullOrUndefined() && !(sourceValue instanceof JSObject)) {
            JSObject boxedObject = executionContext.virtualMachine.toObject(sourceValue);
            if (boxedObject != null) {
                enumerableValue = boxedObject;
            } else {
                enumerableValue = JSUndefined.INSTANCE;
            }
        }
        JSForInEnumerator enumerator = new JSForInEnumerator(enumerableValue);
        executionContext.virtualMachine.valueStack.pushStackValue(new JSInternalValue(enumerator));
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
    }

    static void handleForOfNext(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int nextPc = pc + op.getSize();
        VirtualMachine vm = executionContext.virtualMachine;
        int depth = executionContext.bytecode.readU8(pc + 1);
        vm.valueStack.stackTop = executionContext.sp;
        // Stack layout: ... iter next catch_offset [depth values] (bottom to top)
        // The depth parameter tells us how many values are between catch_offset and top
        // Following QuickJS: offset = -3 - depth

        // Pop depth values temporarily
        if (vm.forOfTempValues.length < depth) {
            vm.forOfTempValues = new JSValue[Math.max(depth, vm.forOfTempValues.length * 2)];
        }
        for (int i = 0; i < depth; i++) {
            vm.forOfTempValues[i] = vm.valueStack.pop();
        }
        int markerIndex = findIteratorCloseMarker(vm.valueStack.stack, vm.valueStack.stackTop,
                executionContext.frameStackBase);
        if (markerIndex < 2) {
            executionContext.setErrorAsPending(vm.context.throwError("Invalid iterator state in FOR_OF_NEXT"));
            return;
        }

        int preservedMarkerCount = vm.valueStack.stackTop - markerIndex - 1;
        JSStackValue[] preservedMarkers = preservedMarkerCount > 0 ? new JSStackValue[preservedMarkerCount] : null;
        for (int markerOffset = 0; markerOffset < preservedMarkerCount; markerOffset++) {
            preservedMarkers[markerOffset] = vm.valueStack.popStackValue();
        }

        JSStackValue catchOffset = vm.valueStack.popStackValue();

        int stackTop = vm.valueStack.stackTop;
        if (stackTop < 2) {
            executionContext.setErrorAsPending(vm.context.throwError("Invalid iterator stack depth in FOR_OF_NEXT"));
            return;
        }
        JSStackValue nextMethodStackValue = vm.valueStack.stack[stackTop - 1];
        JSStackValue iteratorStackValue = vm.valueStack.stack[stackTop - 2];
        JSValue nextMethod = nextMethodStackValue instanceof JSValue jsValue ? jsValue : null;
        JSValue iterator = iteratorStackValue instanceof JSValue jsValue ? jsValue : null;

        boolean done = false;
        JSValue value = JSUndefined.INSTANCE;
        boolean iteratorAlreadyDone = iterator instanceof JSObject iteratorObject
                && vm.isForOfIteratorExhausted(iteratorObject);

        if (!iteratorAlreadyDone) {
            // Call iterator.next()
            if (!JSTypeChecking.isCallable(nextMethod)) {
                String actualType = nextMethodStackValue == null
                        ? "null"
                        : nextMethodStackValue.getClass().getSimpleName();
                String iterType = iteratorStackValue == null ? "null" : iteratorStackValue.getClass().getSimpleName();
                restoreForOfStateWithoutIteratorCloseMarker(executionContext, preservedMarkers, preservedMarkerCount,
                        depth);
                vm.pendingException = vm.context
                        .throwTypeError("Next method must be a function in FOR_OF_NEXT (nextMethod=" + actualType
                                + ", iterator=" + iterType + ")");
                executionContext.finishStackOpcode(nextPc);
                return;
            }

            JSValue result;
            try {
                result = JSCall.call(vm.context, nextMethod, iterator, JSValue.NO_ARGS);
            } catch (JSException e) {
                restoreForOfStateWithoutIteratorCloseMarker(executionContext, preservedMarkers, preservedMarkerCount,
                        depth);
                vm.pendingException = e.getErrorValue();
                vm.context.clearPendingException();
                executionContext.finishStackOpcode(nextPc);
                return;
            } catch (JSVirtualMachineException e) {
                restoreForOfStateWithoutIteratorCloseMarker(executionContext, preservedMarkers, preservedMarkerCount,
                        depth);
                vm.captureVMException(e);
                executionContext.finishStackOpcode(nextPc);
                return;
            }

            // Check for pending exception (e.g., TypedArray detachment during iteration)
            if (captureForOfStepException(executionContext, preservedMarkers, preservedMarkerCount, depth, nextPc)) {
                return;
            }

            if (!(result instanceof JSObject resultObj)) {
                restoreForOfStateWithoutIteratorCloseMarker(executionContext, preservedMarkers, preservedMarkerCount,
                        depth);
                vm.pendingException = vm.context.throwTypeError("Iterator result must be an object");
                executionContext.finishStackOpcode(nextPc);
                return;
            }

            // Get the done property
            JSValue doneValue = resultObj.get(PropertyKey.DONE);
            if (captureForOfStepException(executionContext, preservedMarkers, preservedMarkerCount, depth, nextPc)) {
                return;
            }
            done = JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE;
            if (done) {
                if (iterator instanceof JSObject iteratorObject) {
                    vm.markForOfIteratorExhausted(iteratorObject);
                }
            } else {
                if (iterator instanceof JSObject iteratorObject) {
                    vm.clearForOfIteratorExhausted(iteratorObject);
                }
                value = resultObj.get(PropertyKey.VALUE);
                if (captureForOfStepException(executionContext, preservedMarkers, preservedMarkerCount, depth,
                        nextPc)) {
                    return;
                }
                if (value == null) {
                    value = JSUndefined.INSTANCE;
                }
            }
        } else {
            done = true;
        }

        // Push catch_offset back, then restore temp values, then push value and done
        vm.valueStack.pushStackValue(catchOffset);
        for (int markerOffset = preservedMarkerCount - 1; markerOffset >= 0; markerOffset--) {
            vm.valueStack.pushStackValue(preservedMarkers[markerOffset]);
        }
        for (int i = depth - 1; i >= 0; i--) {
            vm.valueStack.push(vm.forOfTempValues[i]);
            vm.forOfTempValues[i] = null;
        }
        vm.valueStack.push(value);
        vm.valueStack.push(done ? JSBoolean.TRUE : JSBoolean.FALSE);
        executionContext.finishStackOpcode(nextPc);
    }

    static void handleForOfStart(Opcode op, ExecutionContext executionContext) {
        int nextPc = executionContext.pc + op.getSize();
        VirtualMachine vm = executionContext.virtualMachine;
        vm.valueStack.stackTop = executionContext.sp;
        startForOf(executionContext, vm.valueStack.pop(), op == Opcode.FOR_AWAIT_OF_START);
        executionContext.finishStackOpcode(nextPc);
    }

    static void handleInitialYield(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        // Initial yield - generator is being created
        // Per ES spec, parameter defaults are evaluated before INITIAL_YIELD,
        // so errors during parameter initialization are thrown during the generator function call.
        if (executionContext.virtualMachine.yieldSkipCount > 0) {
            executionContext.virtualMachine.yieldSkipCount--;
        } else {
            // Signal suspension at INITIAL_YIELD.
            executionContext.virtualMachine.yieldResult = INITIAL_YIELD;
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
        if (executionContext.virtualMachine.yieldResult != null) {
            // Save suspended execution state so the generator resumes after INITIAL_YIELD
            // instead of re-executing from the start (which would re-run parameter
            // destructuring, causing side effects like double iterator close).
            suspendGenerator(executionContext, JSUndefined.INSTANCE);
        }
    }

    static void handleIteratorCall(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int flags = executionContext.bytecode.readU8(pc + 1);
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue argumentValue = (JSValue) stack[sp - 1];
        JSValue iteratorValue = (JSValue) stack[sp - 4];
        if (!(iteratorValue instanceof JSObject iteratorObject)) {
            executionContext.raiseTypeError("iterator call target must be an object");
            return;
        }

        String methodName = (flags & 1) != 0 ? "throw" : "return";
        JSValue methodValue = (flags & 1) != 0
                ? iteratorObject.get(PropertyKey.THROW)
                : iteratorObject.get(PropertyKey.RETURN);
        if (executionContext.capturePendingException()) {
            return;
        }
        boolean noMethod = methodValue.isNullOrUndefined();
        if (!noMethod) {
            if (!JSTypeChecking.isCallable(methodValue)) {
                executionContext.raiseTypeError("iterator " + methodName + " is not a function");
                return;
            }
            JSValue callResult = (flags & 2) != 0
                    ? JSCall.call(executionContext.virtualMachine.context, methodValue, iteratorObject, JSValue.NO_ARGS)
                    : JSCall.call(executionContext.virtualMachine.context, methodValue, iteratorObject,
                            executionContext.virtualMachine.singleArg(argumentValue));
            stack[sp - 1] = callResult;
        }
        stack[sp++] = JSBoolean.valueOf(noMethod);
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleIteratorCheckObject(Opcode op, ExecutionContext executionContext) {
        JSValue iteratorResult = executionContext.peek(0);
        if (!(iteratorResult instanceof JSObject)) {
            // V8's wording, which names the value.
            executionContext.raiseTypeError(
                    "Iterator result " + JSAsyncIterator.describeIteratorResult(iteratorResult) + " is not an object");
            return;
        }
        executionContext.pc += op.getSize();
    }

    static void handleIteratorClose(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue originalPendingException = executionContext.virtualMachine.pendingException;
        int markerIndex = findIteratorCloseMarker(stack, sp, executionContext.frameStackBase);
        if (markerIndex < 0 && sp > executionContext.frameStackBase) {
            JSStackValue markerCandidate = stack[sp - 1];
            if (markerCandidate instanceof JSNumber markerNumber && markerNumber.value() == 0) {
                markerIndex = sp - 1;
            }
        }
        if (markerIndex < 2) {
            executionContext.pc += op.getSize();
            return;
        }

        int removeStartIndex = markerIndex - 2;
        JSValue iteratorValue = stack[removeStartIndex] instanceof JSValue jsValue ? jsValue : JSUndefined.INSTANCE;
        // A for-await loop's iterator is only removed here; the compiled code calls return() and awaits it.
        boolean asyncIterator = stack[markerIndex] instanceof JSCatchOffset marker
                && marker.isAsyncIteratorCloseMarker();
        int tailCount = sp - (markerIndex + 1);
        if (tailCount > 0) {
            System.arraycopy(stack, markerIndex + 1, stack, removeStartIndex, tailCount);
        }
        sp -= 3;
        for (int index = sp; index < sp + 3 && index < stack.length; index++) {
            stack[index] = null;
        }

        if (!asyncIterator && iteratorValue instanceof JSObject iteratorObject) {
            boolean iteratorAlreadyExhausted = executionContext.virtualMachine.isForOfIteratorExhausted(iteratorObject);
            executionContext.virtualMachine.clearForOfIteratorExhausted(iteratorObject);
            if (!iteratorAlreadyExhausted) {
                JSValue returnMethodValue = iteratorObject.get(PropertyKey.RETURN);
                if (captureCloseException(executionContext.virtualMachine, originalPendingException)) {
                    executionContext.finishOpcode(executionContext.pc + op.getSize(), sp);
                    return;
                }
                if (JSTypeChecking.isCallable(returnMethodValue)) {
                    JSValue closeResult = JSCall.call(executionContext.virtualMachine.context, returnMethodValue,
                            iteratorObject, JSValue.NO_ARGS);
                    if (!captureCloseException(executionContext.virtualMachine, originalPendingException)
                            && !(closeResult instanceof JSObject)) {
                        if (originalPendingException == null) {
                            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                                    .throwTypeError("iterator result is not an object");
                        }
                    }
                } else if (returnMethodValue.isNullOrUndefined()) {
                    // No return method.
                } else if (returnMethodValue instanceof JSObject returnObject && returnObject.isHTMLDDA()) {
                    // IsHTMLDDA callable edge case; preserve previous no-op behavior.
                } else {
                    if (originalPendingException == null) {
                        executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                                .throwTypeError("iterator return is not a function");
                    }
                }
            }
        }
        executionContext.finishOpcode(executionContext.pc + op.getSize(), sp);
    }

    static void handleIteratorGetValueDone(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue iteratorResult = (JSValue) stack[sp - 1];
        if (!(iteratorResult instanceof JSObject iteratorResultObject)) {
            executionContext.raiseTypeError("iterator must return an object");
            return;
        }

        JSValue doneValue = iteratorResultObject.get(PropertyKey.DONE);
        JSValue value = iteratorResultObject.get(PropertyKey.VALUE);
        if (value == null) {
            value = JSUndefined.INSTANCE;
        }
        boolean done = JSTypeConversions.toBoolean(doneValue).isBooleanTrue();

        stack[sp - 1] = value;
        stack[sp - 2] = JSNumber.of(0);
        stack[sp++] = JSBoolean.valueOf(done);
        executionContext.finishOpcode(executionContext.pc + op.getSize(), sp);
    }

    static void handleIteratorNext(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
        int sp = executionContext.sp;
        JSValue argumentValue = (JSValue) stack[sp - 1];
        JSValue catchOffsetValue = (JSValue) stack[sp - 2];
        JSValue nextMethodValue = (JSValue) stack[sp - 3];
        JSValue iteratorValue = (JSValue) stack[sp - 4];
        if (!JSTypeChecking.isCallable(nextMethodValue)) {
            executionContext.raiseTypeError("iterator next is not a function");
            return;
        }
        JSValue nextResult = JSCall.call(executionContext.virtualMachine.context, nextMethodValue, iteratorValue,
                executionContext.virtualMachine.singleArg(argumentValue));
        stack[sp - 1] = nextResult;
        stack[sp - 2] = catchOffsetValue;
        executionContext.pc += op.getSize();
    }

    static void handleYield(Opcode op, ExecutionContext executionContext) {
        executionContext.virtualMachine.valueStack.stackTop = executionContext.sp;
        // Check if we should skip this yield (resuming from later point)
        if (executionContext.virtualMachine.yieldSkipCount > 0) {
            executionContext.virtualMachine.yieldSkipCount--;
            JSValue yieldedValue = executionContext.virtualMachine.valueStack.pop();
            JSGeneratorState.ResumeRecord resumeRecord = executionContext.virtualMachine.generatorResumeIndex < executionContext.virtualMachine.generatorResumeRecords
                    .size()
                            ? executionContext.virtualMachine.generatorResumeRecords
                                    .get(executionContext.virtualMachine.generatorResumeIndex++)
                            : null;
            if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.THROW) {
                executionContext.virtualMachine.pendingException = resumeRecord.value();
                executionContext.virtualMachine.context.setPendingException(resumeRecord.value());
            } else if (resumeRecord != null) {
                executionContext.virtualMachine.valueStack.push(resumeRecord.value());
            } else {
                // If resume data is missing, default to undefined to preserve stack shape.
                executionContext.virtualMachine.valueStack.push(JSUndefined.INSTANCE);
            }
            // Don't yield - just continue execution from the resumed generator state.
        } else {
            // At the target yield - check for RETURN/THROW resume records (replay mode)
            JSGeneratorState.ResumeRecord resumeRecord = executionContext.virtualMachine.currentGeneratorResumeRecord();
            if (resumeRecord != null && resumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN) {
                executionContext.virtualMachine.generatorResumeIndex++;
                executionContext.virtualMachine.valueStack.pop(); // Pop the yielded value
                // Trigger force return through finally handlers
                executionContext.virtualMachine.generatorReturnValue = resumeRecord.value();
                executionContext.virtualMachine.pendingException = resumeRecord.value();
                executionContext.virtualMachine.context.setPendingException(resumeRecord.value());
                executionContext.virtualMachine.valueStack.push(resumeRecord.value());
            } else {
                // Pop the yielded value from stack
                JSValue value = executionContext.virtualMachine.valueStack.pop();

                // Create yield result to signal suspension.
                executionContext.virtualMachine.yieldResult = new YieldResult(YieldResult.Type.YIELD, value);

                // Push the value back so it can be returned
                executionContext.virtualMachine.valueStack.push(value);
            }
        }
        executionContext.finishStackOpcode(executionContext.pc + op.getSize());
        if (executionContext.virtualMachine.yieldResult != null) {
            JSValue returnValue = executionContext.pop();
            suspendGenerator(executionContext, returnValue);
        }
    }

    static void handleYieldStar(Opcode op, ExecutionContext executionContext) {
        VirtualMachine vm = executionContext.virtualMachine;
        vm.valueStack.stackTop = executionContext.sp;
        try {

            // Check for RETURN/THROW resume (yield* delegation protocol per ES2024 27.5.3.3)
            JSGeneratorState.ResumeRecord resumeRecord = vm.currentGeneratorResumeRecord();
            YieldResult lastYieldResult = vm.activeGeneratorState != null
                    ? vm.activeGeneratorState.getLastYieldResult()
                    : null;
            boolean reuseDelegateIterator = resumeRecord != null && lastYieldResult != null
                    && lastYieldResult.isYieldStar() && lastYieldResult.delegateIterator() != null
                    && lastYieldResult.delegationProgramCounter() == executionContext.pc;

            JSObject iteratorObj;
            if (reuseDelegateIterator) {
                iteratorObj = lastYieldResult.delegateIterator();
            } else {
                JSValue iterable = vm.valueStack.pop();
                JSObject iterableObj;
                if (iterable instanceof JSObject obj) {
                    iterableObj = obj;
                } else {
                    iterableObj = vm.toObject(iterable);
                    if (iterableObj == null) {
                        executionContext.raiseTypeError("Object is not iterable");
                        return;
                    }
                }

                // Get Symbol.iterator method
                JSValue iteratorMethod = iterableObj.get(PropertyKey.SYMBOL_ITERATOR);
                if (executionContext.capturePendingException()) {
                    return;
                }
                if (!JSTypeChecking.isCallable(iteratorMethod)) {
                    executionContext.raiseTypeError("Object is not iterable");
                    return;
                }

                // Call Symbol.iterator to get the iterator
                JSValue iterator = JSCall.call(vm.context, iteratorMethod, iterable, JSValue.NO_ARGS);
                if (!(iterator instanceof JSObject iteratorObject)) {
                    executionContext.raiseTypeError("Iterator method must return an object");
                    return;
                }
                iteratorObj = iteratorObject;
            }

            if (resumeRecord != null && (resumeRecord.kind() == JSGeneratorState.ResumeKind.RETURN
                    || resumeRecord.kind() == JSGeneratorState.ResumeKind.THROW)) {
                if (!resumeAbruptDelegate(op, executionContext, iteratorObj, resumeRecord, false)) {
                    return;
                }
            } else {
                // Default: NEXT protocol - call iterator.next()
                JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);
                if (executionContext.capturePendingException()) {
                    return;
                }
                if (!JSTypeChecking.isCallable(nextMethod)) {
                    executionContext.raiseTypeError("Iterator must have a next method");
                    return;
                }
                JSValue nextArgument = JSUndefined.INSTANCE;
                if (resumeRecord != null) {
                    vm.generatorResumeIndex++;
                    nextArgument = resumeRecord.value();
                }

                boolean innerExhausted = false;
                if (!reuseDelegateIterator && vm.yieldSkipCount > 0) {
                    JSValue[] replayArgs = {JSUndefined.INSTANCE};
                    int remaining = vm.yieldSkipCount;
                    while (remaining > 0) {
                        ReplayStep step = replayDelegateStep(executionContext, iteratorObj, nextMethod, replayArgs);
                        if (step == ReplayStep.ABRUPT) {
                            return;
                        }
                        if (step == ReplayStep.DONE) {
                            innerExhausted = true;
                            break;
                        }
                        remaining--;
                    }
                    vm.yieldSkipCount = remaining;
                }
                if (!innerExhausted) {
                    JSObject result = callDelegateNext(executionContext, iteratorObj, nextMethod,
                            new JSValue[]{nextArgument});
                    if (result == null) {
                        return;
                    }

                    // Check if the inner iterator is done
                    JSValue doneValue = result.get(PropertyKey.DONE);
                    if (executionContext.capturePendingException()) {
                        return;
                    }
                    if (JSTypeConversions.toBoolean(doneValue).value()) {
                        if (reuseDelegateIterator && vm.yieldSkipCount > 0) {
                            vm.yieldSkipCount--;
                        }
                        // Inner iterator done - yield* expression value is the final value
                        JSValue value = result.get(PropertyKey.VALUE);
                        if (executionContext.capturePendingException()) {
                            return;
                        }
                        vm.valueStack.push(value);
                        // Don't set yieldResult - the yield* expression completes
                    } else {
                        // Set yield result to the raw iterator result object
                        yieldDelegateResult(executionContext, iteratorObj, result, null, executionContext.pc, false);
                    }
                }
            }
            if (vm.yieldResult != null) {
                executionContext.sp = vm.valueStack.stackTop;
                JSValue returnValue = executionContext.pop();
                // Resume at the same YIELD_STAR opcode until delegation completes.
                suspendGenerator(executionContext, returnValue);
                return;
            }
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
        } catch (JSVirtualMachineException exception) {
            vm.captureVMException(exception);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
        }
    }

    /**
     * Create VarRef array from capture source info during FCLOSURE. For LOCAL sources, creates/reuses a VarRef pointing
     * to the parent's local slot. For VAR_REF sources, shares the parent's existing VarRef.
     */
    private static void internalStartGeneratorReturnCompletion(ExecutionContext executionContext, JSValue returnValue) {
        executionContext.virtualMachine.generatorReturnValue = returnValue;
        executionContext.virtualMachine.pendingException = VirtualMachine.GENERATOR_RETURN_SIGNAL;
        executionContext.virtualMachine.context.setPendingException(VirtualMachine.GENERATOR_RETURN_SIGNAL);
    }

    /** Replay one previously yielded value. A completed iterator leaves the yield* expression value on the stack. */
    private static ReplayStep replayDelegateStep(ExecutionContext execution, JSObject iterator, JSValue next,
            JSValue[] args) {
        JSObject result = callDelegateNext(execution, iterator, next, args);
        if (result == null) {
            return ReplayStep.ABRUPT;
        }
        JSValue done = result.get(PropertyKey.DONE);
        if (execution.capturePendingException()) {
            return ReplayStep.ABRUPT;
        }
        if (!JSTypeConversions.toBoolean(done).value()) {
            return ReplayStep.YIELDED;
        }
        JSValue value = result.get(PropertyKey.VALUE);
        if (execution.capturePendingException()) {
            return ReplayStep.ABRUPT;
        }
        execution.virtualMachine.valueStack.push(value);
        return ReplayStep.DONE;
    }

    private static void restoreForOfStateWithoutIteratorCloseMarker(ExecutionContext executionContext,
            JSStackValue[] preservedMarkers, int preservedMarkerCount, int depth) {
        if (preservedMarkers != null) {
            for (int markerOffset = preservedMarkerCount - 1; markerOffset >= 0; markerOffset--) {
                executionContext.virtualMachine.valueStack.pushStackValue(preservedMarkers[markerOffset]);
            }
        }
        for (int valueOffset = 0; valueOffset < depth; valueOffset++) {
            executionContext.virtualMachine.forOfTempValues[valueOffset] = null;
        }
    }

    private static boolean resumeAbruptDelegate(Opcode op, ExecutionContext executionContext, JSObject iterator,
            JSGeneratorState.ResumeRecord record, boolean nativeAsync) {
        VirtualMachine vm = executionContext.virtualMachine;
        boolean synchronous = op == Opcode.YIELD_STAR;
        boolean returning = record.kind() == JSGeneratorState.ResumeKind.RETURN;
        vm.generatorResumeIndex++;
        JSValue method = iterator.get(returning ? PropertyKey.RETURN : PropertyKey.THROW);
        if (executionContext.capturePendingException()) {
            return false;
        }
        JSValue value = record.value();
        if (!method.isNullOrUndefined()) {
            JSObject result = callDelegateMethod(executionContext, iterator, method, value,
                    returning ? "iterator return is not a function" : "iterator throw is not a function");
            if (result == null) {
                return false;
            }
            JSValue done = result.get(PropertyKey.DONE);
            if (executionContext.capturePendingException()) {
                return false;
            }
            if (!JSTypeConversions.toBoolean(done).value()) {
                yieldDelegateResult(executionContext, iterator, result, null, synchronous ? executionContext.pc : -1,
                        nativeAsync);
                return true;
            }
            value = result.get(PropertyKey.VALUE);
            if (executionContext.capturePendingException()) {
                return false;
            }
        } else if (!returning) {
            JSValue close = iterator.get(PropertyKey.RETURN);
            if (synchronous && executionContext.capturePendingException()) {
                return false;
            }
            if (JSTypeChecking.isCallable(close)) {
                JSCall.call(vm.context, close, iterator, JSValue.NO_ARGS);
                if (synchronous && executionContext.capturePendingException()) {
                    return false;
                }
            }
            executionContext.raiseTypeError("iterator does not have a throw method");
            return false;
        }
        if (returning && synchronous) {
            internalStartGeneratorReturnCompletion(executionContext, value);
            executionContext.finishStackOpcode(executionContext.pc + op.getSize());
            return false;
        }
        vm.valueStack.push(value);
        return true;
    }

    private static void startForOf(ExecutionContext execution, JSValue iterable, boolean async) {
        VirtualMachine vm = execution.virtualMachine;
        JSContext context = vm.context;
        JSObject object = vm.toObject(iterable);
        String notIterable = async ? "object is not async iterable" : "Object is not iterable";
        if (object == null) {
            vm.pendingException = context.throwTypeError(notIterable);
            return;
        }

        JSValue method = async ? object.get(PropertyKey.SYMBOL_ASYNC_ITERATOR) : JSUndefined.INSTANCE;
        if (async && execution.capturePendingException()) {
            return;
        }
        boolean syncMethod = method == null || method.isNullOrUndefined();
        if (syncMethod) {
            method = object.get(PropertyKey.SYMBOL_ITERATOR);
            if (execution.capturePendingException()) {
                return;
            }
        }
        if (!JSTypeChecking.isCallable(method)) {
            vm.pendingException = context.throwTypeError(notIterable);
            return;
        }

        JSValue result;
        try {
            result = JSCall.call(context, method, iterable, JSValue.NO_ARGS);
        } catch (JSException e) {
            execution.setErrorAsPending(e.getErrorValue());
            return;
        } catch (JSVirtualMachineException e) {
            vm.captureVMException(e);
            return;
        }
        if (execution.capturePendingException()) {
            return;
        }
        if (!(result instanceof JSObject iterator)) {
            vm.pendingException = context
                    .throwTypeError(async ? "iterator must return an object" : "Iterator method must return an object");
            return;
        }

        JSValue next = iterator.get(PropertyKey.NEXT);
        if (execution.capturePendingException()) {
            return;
        }
        if (async && !JSTypeChecking.isCallable(next)) {
            vm.pendingException = context.throwTypeError("iterator must have a next method");
            return;
        }
        vm.clearForOfIteratorExhausted(iterator);
        if (async && syncMethod) {
            next = JSAsyncIterator.createAsyncFromSyncNextMethod(context, iterator, next);
            // The loop awaits the wrapper's return() on an early exit.
            iterator = JSAsyncIterator.createAsyncFromSyncIteratorForClose(context, iterator);
        }
        vm.valueStack.push(iterator);
        vm.valueStack.push(next == null ? JSUndefined.INSTANCE : next);
        vm.valueStack.pushStackValue(
                async ? JSCatchOffset.ASYNC_ITERATOR_CLOSE_MARKER : JSCatchOffset.ITERATOR_CLOSE_MARKER);
    }

    private static void suspendGenerator(ExecutionContext executionContext, JSValue value) {
        VirtualMachine vm = executionContext.virtualMachine;
        vm.saveActiveGeneratorSuspendedExecutionState(executionContext.frame, executionContext.pc, vm.valueStack.stack,
                executionContext.sp, executionContext.frameStackBase);
        vm.requestOpcodeReturnFromExecute(executionContext, value);
    }

    private static void yieldDelegateResult(ExecutionContext executionContext, JSObject iterator, JSObject result,
            JSValue nextMethod, int resumePc, boolean nativeAsync) {
        VirtualMachine vm = executionContext.virtualMachine;
        vm.yieldResult = new YieldResult(YieldResult.Type.YIELD_STAR, result, iterator, nextMethod, resumePc,
                nativeAsync);
        vm.valueStack.push(result);
    }

    private enum ReplayStep {
        ABRUPT,
        DONE,
        YIELDED
    }
}
