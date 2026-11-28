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

import com.caoccao.qjs4j.builtins.number.BigIntConstructor;
import com.caoccao.qjs4j.builtins.object.SymbolConstructor;
import com.caoccao.qjs4j.core.*;
import com.caoccao.qjs4j.core.iterator.JSAsyncIterator;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.exceptions.*;

/** Function, constructor and direct eval calls, including tail-call frame replacement. */
final class CallOpcodes {
    private CallOpcodes() {
    }

    private static void call(ExecutionContext executionContext, int argCount, boolean directEvalSyntax) {
        VirtualMachine virtualMachine = executionContext.virtualMachine;
        JSStackValue[] stack = executionContext.stack();
        int sp = executionContext.sp;

        // Stack layout (bottom to top): method, receiver, arg1, arg2, ...
        // Peek at callee to decide buffer strategy (callee is below receiver and args)
        int calleeIndex = directEvalSyntax ? (sp - argCount - 1) : (sp - argCount - 2);
        JSValue calleePreview = (JSValue) stack[calleeIndex];
        boolean useBorrowedBuffer = calleePreview instanceof JSBytecodeFunction;

        // Pop arguments from stack — reuse buffer for bytecode functions
        JSValue[] args;
        if (useBorrowedBuffer) {
            args = virtualMachine.borrowArgsBuffer(argCount);
        } else {
            args = argCount == 0 ? JSValue.NO_ARGS : new JSValue[argCount];
        }
        sp -= argCount;
        copyArguments(stack, sp, args, argCount);

        JSValue receiver = JSUndefined.INSTANCE;
        if (!directEvalSyntax) {
            receiver = (JSValue) stack[--sp];
        }

        // Pop callee (method)
        JSValue callee = (JSValue) stack[--sp];

        virtualMachine.valueStack.stackTop = sp;
        JSValue result = invoke(executionContext, callee, receiver, args, argCount, directEvalSyntax);
        // Calling script may reallocate the stack.
        virtualMachine.valueStack.stack[sp] = result;
        executionContext.sp = sp + 1;
        virtualMachine.valueStack.stackTop = sp + 1;
    }

    private static JSValue callResult(VirtualMachine vm, JSValue result) {
        if (vm.context.hasPendingException()) {
            vm.pendingException = vm.context.getPendingException();
            return JSUndefined.INSTANCE;
        }
        return result;
    }

    private static void copyArguments(JSStackValue[] stack, int start, JSValue[] args, int count) {
        for (int i = 0; i < count; i++) {
            args[i] = (JSValue) stack[start + i];
        }
    }

    /**
     * How the TypeError a call or {@code new} with an unusable callee raises names it: as the compiler named the callee
     * ({@code o.a.b is not a function}), or, for a literal callee, by its value.
     */
    private static String describeCallee(ExecutionContext executionContext, JSValue callee) {
        String description = executionContext.bytecode.getCallDescription(executionContext.pc);
        if (description != null) {
            return description;
        }
        // Described without running script: a literal's text, or "object" for a value the compiler did not name.
        if (callee instanceof JSString str) {
            return "\"" + str.value() + "\"";
        }
        if (callee instanceof JSObject) {
            return "object";
        }
        return JSAsyncIterator.describeIteratorResult(callee);
    }

    private static JSValue guestException(VirtualMachine vm, JSException exception) {
        vm.pendingException = exception.getErrorValue();
        vm.context.clearPendingException();
        return JSUndefined.INSTANCE;
    }

    static void handleApply(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int isConstructorCall = executionContext.operandU16();
        JSStackValue[] stack = executionContext.stack();
        int sp = executionContext.sp;
        JSValue argsArrayValue = (JSValue) stack[--sp];
        JSValue functionValue = (JSValue) stack[--sp];
        JSValue thisArgValue = (JSValue) stack[--sp];

        JSValue result;
        try {
            if (isConstructorCall != 0) {
                // A `new` with spread arguments names its callee; a super(...) call leaves the check to construct.
                String description = executionContext.bytecode.getCallDescription(pc);
                if (description != null && !JSTypeChecking.isConstructor(functionValue)) {
                    executionContext.raiseTypeError(description + " is not a constructor");
                    executionContext.finishWithResult(op, sp, JSUndefined.INSTANCE);
                    return;
                }
                JSValue constructorNewTarget = thisArgValue;
                if (constructorNewTarget.isNullOrUndefined()) {
                    constructorNewTarget = functionValue;
                }
                result = JSReflectObject.construct(executionContext.virtualMachine.context, JSUndefined.INSTANCE,
                        new JSValue[]{functionValue, argsArrayValue, constructorNewTarget});
            } else {
                JSValue[] applyArgs = executionContext.virtualMachine.buildApplyArguments(argsArrayValue, true);
                if (applyArgs == null) {
                    executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                            .getPendingException();
                    executionContext.finishWithResult(op, sp, JSUndefined.INSTANCE);
                    return;
                }
                if (functionValue instanceof JSProxy proxyFunction) {
                    result = executionContext.virtualMachine.proxyApply(proxyFunction, thisArgValue, applyArgs);
                } else if (functionValue instanceof JSFunction applyFunction) {
                    result = applyFunction.call(executionContext.virtualMachine.context, thisArgValue, applyArgs);
                } else {
                    executionContext
                            .raiseTypeError(describeCallee(executionContext, functionValue) + " is not a function");
                    executionContext.finishWithResult(op, sp, JSUndefined.INSTANCE);
                    return;
                }
            }
        } catch (JSVirtualMachineException exception) {
            executionContext.virtualMachine.captureVMException(exception);
            executionContext.finishWithResult(op, sp, JSUndefined.INSTANCE);
            return;
        }

        executionContext.finishWithResult(op, sp, callResult(executionContext.virtualMachine, result));
    }

    static void handleApplyEval(Opcode op, ExecutionContext executionContext) {
        JSStackValue[] stack = executionContext.stack();
        int sp = executionContext.sp;
        JSValue argsArrayValue = (JSValue) stack[--sp];
        JSValue callee = (JSValue) stack[--sp];

        JSValue[] applyArgs = executionContext.virtualMachine.buildApplyArguments(argsArrayValue, true);
        if (applyArgs == null) {
            executionContext.virtualMachine.pendingException = executionContext.virtualMachine.context
                    .getPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        } else {
            stack[sp++] = callee;
            for (JSValue applyArg : applyArgs) {
                stack[sp++] = applyArg;
            }
            executionContext.sp = sp;
            call(executionContext, applyArgs.length, true);
            sp = executionContext.sp;
        }
        executionContext.finishOpcode(op, sp);
    }

    static void handleCall(Opcode op, ExecutionContext executionContext) {
        int argumentCount = executionContext.indexOperand(op, Opcode.CALL0);
        call(executionContext, argumentCount, false);
        executionContext.advance(op);
    }

    static void handleCallConstructor(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentCount = executionContext.operandU16();
        VirtualMachine virtualMachine = executionContext.virtualMachine;
        JSContext context = virtualMachine.context;
        JSStackValue[] stack = virtualMachine.valueStack.stack;
        int sp = executionContext.sp;

        // Pop arguments (constructors pass args to native/bytecode functions that rely on args.length)
        JSValue[] args = argumentCount == 0 ? JSValue.NO_ARGS : new JSValue[argumentCount];
        sp -= argumentCount;
        copyArguments(stack, sp, args, argumentCount);
        // Pop constructor
        JSValue constructor = (JSValue) stack[--sp];
        virtualMachine.valueStack.stackTop = sp;

        // Handle proxy construct trap (QuickJS: js_proxy_call_constructor)
        if (constructor instanceof JSProxy jsProxy) {
            // Following QuickJS JS_CallConstructorInternal:
            // Check if target is a constructor BEFORE checking for construct trap
            JSValue target = jsProxy.getTarget();
            if (JSTypeChecking.isConstructor(target)) {
                stack[sp++] = virtualMachine.proxyConstruct(jsProxy, args, jsProxy);
            } else {
                context.throwTypeError("proxy is not a constructor");
                virtualMachine.pendingException = context.getPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
            }
        } else if (constructor instanceof JSFunction jsFunction) {
            // Check if the function is constructable
            if (!JSTypeChecking.isConstructor(jsFunction)) {
                String description = executionContext.bytecode.getCallDescription(executionContext.pc);
                context.throwTypeError(
                        (description != null ? description : jsFunction.getName()) + " is not a constructor");
                virtualMachine.pendingException = context.getPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
            } else {
                JSValue result;
                try {
                    result = virtualMachine.constructFunction(jsFunction, args, jsFunction);
                } catch (JSVirtualMachineException e) {
                    virtualMachine.captureVMException(e);
                    result = null;
                }
                if (result == null || context.hasPendingException()) {
                    if (virtualMachine.pendingException == null) {
                        virtualMachine.pendingException = context.takePendingException();
                    }
                    stack[sp++] = JSUndefined.INSTANCE;
                } else {
                    stack[sp++] = result;
                }
            }
        } else {
            context.throwTypeError(describeCallee(executionContext, constructor) + " is not a constructor");
            virtualMachine.pendingException = context.getPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        }
        executionContext.sp = sp;
        virtualMachine.valueStack.stackTop = sp;
        executionContext.pc = pc + op.getSize();
    }

    static void handleEval(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentCount = executionContext.operandU16();
        int evalFlags = executionContext.bytecode.readU16(pc + 3);
        boolean inClassFieldInitializer = (evalFlags & 2) != 0;
        int tailFlag = evalFlags & 1;

        // Schedule class field eval flag before the eval function is called
        if (inClassFieldInitializer) {
            executionContext.virtualMachine.context.scheduleClassFieldEvalCall();
        }

        if (tailFlag != 0) {
            tailCall(op, executionContext, argumentCount, true);
            return;
        }
        call(executionContext, argumentCount, true);
        executionContext.pc = pc + op.getSize();
    }

    static void handleTailCall(Opcode op, ExecutionContext ctx) {
        tailCall(op, ctx, ctx.operandU16(), false);
    }

    private static JSValue invoke(ExecutionContext executionContext, JSValue callee, JSValue receiver, JSValue[] args,
            int argCount, boolean directEvalSyntax) {
        VirtualMachine vm = executionContext.virtualMachine;
        if (callee instanceof JSNativeFunction nativeFunction) {
            return invokeNative(vm, nativeFunction, receiver, args, directEvalSyntax);
        }
        if (callee instanceof JSProxy proxy) {
            return vm.proxyApply(proxy, receiver, args);
        }
        if (callee instanceof JSFunction function) {
            return invokeFunction(vm, function, receiver, args, argCount);
        }
        executionContext.raiseTypeError(describeCallee(executionContext, callee) + " is not a function");
        return JSUndefined.INSTANCE;
    }

    private static JSValue invokeFunction(VirtualMachine virtualMachine, JSFunction function, JSValue receiver,
            JSValue[] args, int argCount) {
        JSContext context = virtualMachine.context;
        if (function.getHomeObject() == null && receiver instanceof JSObject receiverObject
                && function instanceof JSBytecodeFunction bytecodeFunction) {
            String functionName = bytecodeFunction.getName();
            if ("<static initializer>".equals(functionName) || "<static field initializer>".equals(functionName)) {
                bytecodeFunction.setHomeObject(receiverObject);
            }
        }

        boolean isClassCtor = function instanceof JSClass;
        if (!isClassCtor && function instanceof JSBytecodeFunction bytecodeFunc) {
            isClassCtor = bytecodeFunc.isClassConstructor();
        }
        if (isClassCtor) {
            JSContext errorContext = function.getRealmContext() != null ? function.getRealmContext() : context;
            virtualMachine.pendingException = errorContext
                    .throwTypeError("Class constructor " + function.getName() + " cannot be invoked without 'new'");
            errorContext.clearPendingException();
            return JSUndefined.INSTANCE;
        }

        if (function instanceof JSBytecodeFunction bytecodeFunc) {
            try {
                // Call through the function's call method to handle async wrapping.
                // Pass argCount so the borrowed buffer (which may be oversized) is
                // handled correctly by StackFrame and generator/async paths.
                JSValue result = bytecodeFunc.call(context, receiver, args, argCount);
                return callResult(virtualMachine, result);
            } catch (JSException e) {
                return guestException(virtualMachine, e);
            } catch (JSVirtualMachineException e) {
                virtualMachine.captureVMException(e);
                return JSUndefined.INSTANCE;
            }
        } else if (function instanceof JSBoundFunction boundFunc) {
            // Call bound function - the receiver is ignored for bound functions
            try {
                JSValue result = boundFunc.call(context, receiver, args);
                // Check for pending exception after bound function call
                return callResult(virtualMachine, result);
            } catch (JSVirtualMachineException e) {
                virtualMachine.captureVMException(e);
                return JSUndefined.INSTANCE;
            }
        } else {
            return JSUndefined.INSTANCE;
        }
    }

    private static JSValue invokeNative(VirtualMachine virtualMachine, JSNativeFunction nativeFunc, JSValue receiver,
            JSValue[] args, boolean directEvalSyntax) {
        JSContext context = virtualMachine.context;
        JSConstructorType ctorType = nativeFunc.getConstructorType();
        if (ctorType == JSConstructorType.SYMBOL_OBJECT) {
            JSValue result = SymbolConstructor.call(context, receiver, args);
            return result;
        }
        if (ctorType == JSConstructorType.BIG_INT_OBJECT) {
            JSValue result = BigIntConstructor.call(context, receiver, args);
            return result;
        }
        if (nativeFunc.requiresNew()) {
            JSContext errorContext = nativeFunc.getRealmContext() != null ? nativeFunc.getRealmContext() : context;
            String constructorName = nativeFunc.getName() != null ? nativeFunc.getName() : "constructor";
            String errorMessage = switch (constructorName) {
                case JSPromise.NAME -> "Promise constructor cannot be invoked without 'new'";
                default -> "Constructor " + constructorName + " requires 'new'";
            };
            virtualMachine.pendingException = errorContext.throwTypeError(errorMessage);
            errorContext.clearPendingException();
            return JSUndefined.INSTANCE;
        }
        try {
            JSValue result;
            if (directEvalSyntax && JSKeyword.EVAL.equals(nativeFunc.getName())) {
                JSContext evalRealmContext = nativeFunc.getRealmContext() != null
                        ? nativeFunc.getRealmContext()
                        : context;
                result = JSGlobalObject.GlobalFunction.eval(evalRealmContext, context, args, true);
            } else {
                result = nativeFunc.call(context, receiver, args);
            }
            return callResult(virtualMachine, result);
        } catch (JSException e) {
            return guestException(virtualMachine, e);
        } catch (JSVirtualMachineException e) {
            virtualMachine.captureVMException(e);
            return JSUndefined.INSTANCE;
        } catch (JSErrorException e) {
            virtualMachine.pendingException = context.throwError(e);
            context.clearPendingException();
            return JSUndefined.INSTANCE;
        }
    }

    private static void tailCall(Opcode op, ExecutionContext ctx, int count, boolean directEval) {
        VirtualMachine vm = ctx.virtualMachine;
        JSValue callee = ctx.peek(count + (directEval ? 0 : 1));
        if (callee instanceof JSBytecodeFunction function && !(callee instanceof JSClass)
                && !function.isClassConstructor() && !function.isAsync() && !function.isGenerator()) {
            JSValue[] args = vm.borrowArgsBuffer(count);
            int firstArgument = ctx.sp - count;
            copyArguments(vm.valueStack.stack, firstArgument, args, count);
            vm.valueStack.stackTop = firstArgument - (directEval ? 1 : 2);
            JSValue receiver = directEval ? JSUndefined.INSTANCE : tailReceiver(vm.context, function, ctx.peek(count));
            vm.tailCallPending = new VirtualMachine.TailCallRequest(function, receiver, args, count);
        } else {
            call(ctx, count, directEval);
            if (directEval && vm.pendingException != null) {
                ctx.advance(op);
                return;
            }
            ctx.returnValue = ctx.pop();
        }
        vm.lastConstructorThisArg = ctx.frame.getThisArg();
        vm.finalizeExecuteReturn(ctx);
        ctx.opcodeRequestedReturn = true;
    }

    private static JSValue tailReceiver(JSContext context, JSBytecodeFunction function, JSValue receiver) {
        if (function.isArrow() && function.getCapturedThisArg() != null) {
            receiver = function.getCapturedThisArg();
        }
        if (function.isStrict() || receiver instanceof JSObject) {
            return receiver;
        }
        return receiver.isNullOrUndefined() ? context.getGlobalObject() : JSTypeConversions.toObject(context, receiver);
    }
}
