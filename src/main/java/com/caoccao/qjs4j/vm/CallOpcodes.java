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
import com.caoccao.qjs4j.builtins.BigIntConstructor;
import com.caoccao.qjs4j.builtins.SymbolConstructor;
import com.caoccao.qjs4j.exceptions.*;

/** Function, constructor and direct eval calls, including tail-call frame replacement. */
final class CallOpcodes {
    private CallOpcodes() {
    }

    private static void call(ExecutionContext executionContext, int argCount, boolean directEvalSyntax) {
        VirtualMachine virtualMachine = executionContext.virtualMachine;
        JSContext context = virtualMachine.context;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
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

        // SWAP locks property tracking while evaluating method-call arguments.
        // Unlock before invoking the callee so nested calls can build their own chains.
        virtualMachine.valueStack.stackTop = sp;

        // Fast path for native functions (most common case for built-in method calls).
        // Checked first to avoid Proxy/constructor-type/class-constructor checks on every call.
        if (callee instanceof JSNativeFunction nativeFunc) {
            JSConstructorType ctorType = nativeFunc.getConstructorType();
            if (ctorType == JSConstructorType.SYMBOL_OBJECT) {
                JSValue result = SymbolConstructor.call(context, receiver, args);
                stack[sp++] = result;
                executionContext.sp = sp;
                virtualMachine.valueStack.stackTop = sp;
                return;
            }
            if (ctorType == JSConstructorType.BIG_INT_OBJECT) {
                JSValue result = BigIntConstructor.call(context, receiver, args);
                stack[sp++] = result;
                executionContext.sp = sp;
                virtualMachine.valueStack.stackTop = sp;
                return;
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
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                virtualMachine.valueStack.stackTop = sp;
                return;
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
                stack[sp++] = callResult(virtualMachine, result);
            } catch (JSException e) {
                virtualMachine.pendingException = e.getErrorValue();
                context.clearPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
            } catch (JSVirtualMachineException e) {
                virtualMachine.captureVMException(e);
                stack[sp++] = JSUndefined.INSTANCE;
            } catch (JSErrorException e) {
                virtualMachine.pendingException = context.throwError(e);
                context.clearPendingException();
                stack[sp++] = JSUndefined.INSTANCE;
            }
        } else if (callee instanceof JSProxy proxy) {
            JSValue result = virtualMachine.proxyApply(proxy, receiver, args);
            stack[sp++] = result;
        } else if (callee instanceof JSFunction function) {
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
                stack[sp++] = JSUndefined.INSTANCE;
                executionContext.sp = sp;
                virtualMachine.valueStack.stackTop = sp;
                return;
            }

            if (function instanceof JSBytecodeFunction bytecodeFunc) {
                try {
                    // Call through the function's call method to handle async wrapping.
                    // Pass argCount so the borrowed buffer (which may be oversized) is
                    // handled correctly by StackFrame and generator/async paths.
                    JSValue result = bytecodeFunc.call(context, receiver, args, argCount);
                    stack[sp++] = callResult(virtualMachine, result);
                } catch (JSException e) {
                    virtualMachine.pendingException = e.getErrorValue();
                    context.clearPendingException();
                    stack[sp++] = JSUndefined.INSTANCE;
                } catch (JSVirtualMachineException e) {
                    virtualMachine.captureVMException(e);
                    stack[sp++] = JSUndefined.INSTANCE;
                }
            } else if (function instanceof JSBoundFunction boundFunc) {
                // Call bound function - the receiver is ignored for bound functions
                try {
                    JSValue result = boundFunc.call(context, receiver, args);
                    // Check for pending exception after bound function call
                    stack[sp++] = callResult(virtualMachine, result);
                } catch (JSVirtualMachineException e) {
                    virtualMachine.captureVMException(e);
                    stack[sp++] = JSUndefined.INSTANCE;
                }
            } else {
                stack[sp++] = JSUndefined.INSTANCE;
            }
            // Clear property access tracking after successful call
        } else {
            // Not a function - set pending TypeError so JS catch handlers can process it
            virtualMachine.pendingException = context
                    .throwTypeError(describeCallee(executionContext, callee) + " is not a function");
            context.clearPendingException();
            stack[sp++] = JSUndefined.INSTANCE;
        }

        executionContext.sp = sp;
        virtualMachine.valueStack.stackTop = sp;
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

    static void handleApply(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int isConstructorCall = executionContext.bytecode.readU16(pc + 1);
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
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
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.finishOpcode(pc + op.getSize(), sp);
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
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.finishOpcode(pc + op.getSize(), sp);
                    return;
                }
                if (functionValue instanceof JSProxy proxyFunction) {
                    result = executionContext.virtualMachine.proxyApply(proxyFunction, thisArgValue, applyArgs);
                } else if (functionValue instanceof JSFunction applyFunction) {
                    result = applyFunction.call(executionContext.virtualMachine.context, thisArgValue, applyArgs);
                } else {
                    executionContext
                            .raiseTypeError(describeCallee(executionContext, functionValue) + " is not a function");
                    stack[sp++] = JSUndefined.INSTANCE;
                    executionContext.finishOpcode(pc + op.getSize(), sp);
                    return;
                }
            }
        } catch (JSVirtualMachineException exception) {
            executionContext.virtualMachine.captureVMException(exception);
            stack[sp++] = JSUndefined.INSTANCE;
            executionContext.finishOpcode(pc + op.getSize(), sp);
            return;
        }

        stack[sp++] = callResult(executionContext.virtualMachine, result);
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleApplyEval(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        JSStackValue[] stack = executionContext.virtualMachine.valueStack.stack;
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
        executionContext.finishOpcode(pc + op.getSize(), sp);
    }

    static void handleCall(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentCount = executionContext.bytecode.readU16(pc + 1);
        call(executionContext, argumentCount, false);
        executionContext.pc += op.getSize();
    }

    static void handleCall0(Opcode op, ExecutionContext executionContext) {
        call(executionContext, 0, false);
        executionContext.pc += op.getSize();
    }

    static void handleCall1(Opcode op, ExecutionContext executionContext) {
        call(executionContext, 1, false);
        executionContext.pc += op.getSize();
    }

    static void handleCall2(Opcode op, ExecutionContext executionContext) {
        call(executionContext, 2, false);
        executionContext.pc += op.getSize();
    }

    static void handleCall3(Opcode op, ExecutionContext executionContext) {
        call(executionContext, 3, false);
        executionContext.pc += op.getSize();
    }

    static void handleCallConstructor(Opcode op, ExecutionContext executionContext) {
        int pc = executionContext.pc;
        int argumentCount = executionContext.bytecode.readU16(pc + 1);
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
        int argumentCount = executionContext.bytecode.readU16(pc + 1);
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
        tailCall(op, ctx, ctx.bytecode.readU16(ctx.pc + 1), false);
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
                ctx.pc += op.getSize();
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
