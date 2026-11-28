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

/** The ECMAScript Call operation, including callable proxies. */
public final class JSCall {
    private JSCall() {
    }

    public static JSValue[] argumentsFromArrayLike(JSContext context, JSValue arrayArg) {
        if (!(arrayArg instanceof JSObject arrayLike)) {
            return context.rejectTypeError("CreateListFromArrayLike called on non-object");
        }

        JSValue lengthValue = arrayLike.get(PropertyKey.LENGTH);
        if (context.hasPendingException()) {
            return null;
        }

        long length = JSTypeConversions.toLength(context, lengthValue);
        if (context.hasPendingException()) {
            return null;
        }
        if (length > Integer.MAX_VALUE) {
            return context.rejectRangeError("too many arguments in function call");
        }

        JSValue[] callArgs = new JSValue[(int) length];
        for (int i = 0; i < callArgs.length; i++) {
            JSValue argumentValue = arrayLike.get(PropertyKey.fromIndex(i));
            if (context.hasPendingException()) {
                return null;
            }
            callArgs[i] = argumentValue;
        }
        return callArgs;
    }

    public static JSValue call(JSContext context, JSValue callable, JSValue thisArg) {
        return call(context, callable, thisArg, JSValue.NO_ARGS);
    }

    public static JSValue call(JSContext context, JSValue callable, JSValue thisArg, JSValue... args) {
        if (callable instanceof JSProxy proxy) {
            return proxy.apply(context, thisArg, args);
        }
        if (callable instanceof JSFunction function) {
            return function.call(context, thisArg, args);
        }
        return context.throwTypeError("Value is not callable");
    }

    static JSValue callConstructor(JSContext context, JSContext constructorContext, JSFunction function,
            JSValue constructThis, JSValue[] args, JSValue newTarget) {
        JSValue savedNewTarget = context.getConstructorNewTarget();
        JSValue savedConstructorContextNewTarget = null;
        JSValue savedNativeConstructorContextNewTarget = null;
        context.setConstructorNewTarget(newTarget);
        if (constructorContext != context) {
            savedConstructorContextNewTarget = constructorContext.getConstructorNewTarget();
            constructorContext.setConstructorNewTarget(newTarget);
        }
        JSValue result;
        try {
            if (function instanceof JSNativeFunction nativeFunc) {
                savedNativeConstructorContextNewTarget = constructorContext.getNativeConstructorNewTarget();
                constructorContext.setNativeConstructorNewTarget(newTarget);
                result = nativeFunc.call(context, constructThis, args);
            } else if (function instanceof JSBytecodeFunction bytecodeFunction) {
                result = constructorContext.getVirtualMachine().execute(bytecodeFunction, constructThis, args,
                        newTarget);
            } else {
                result = function.call(constructorContext, constructThis, args);
            }
            if (constructorContext != context && constructorContext.hasPendingException()) {
                context.setPendingException(constructorContext.getPendingException());
                constructorContext.clearPendingException();
            }
        } finally {
            context.setConstructorNewTarget(savedNewTarget);
            if (function instanceof JSNativeFunction) {
                constructorContext.setNativeConstructorNewTarget(savedNativeConstructorContextNewTarget);
            }
            if (constructorContext != context) {
                constructorContext.setConstructorNewTarget(savedConstructorContextNewTarget);
            }
        }
        return result;
    }

    public static JSValue constructOrdinary(JSContext context, JSFunction function, JSValue[] args, JSValue newTarget) {
        JSObject thisObject = new JSObject(context);
        JSObject prototypeSource = newTarget instanceof JSObject targetObject ? targetObject : function;
        JSObject prototype = context.getPrototypeFromConstructor(prototypeSource,
                context.getIntrinsicDefaultPrototypeName(function));
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (prototype != null) {
            thisObject.setPrototype(prototype);
        }

        // Check if this is a derived constructor
        boolean isDerived = function instanceof JSBytecodeFunction bcFunc && bcFunc.isDerivedConstructor();
        // For derived constructors, use JSUndefined as initial this
        // (this must be initialized by super() call)
        JSValue constructThis = isDerived ? JSUndefined.INSTANCE : thisObject;

        JSContext constructorContext = function.getRealmContext() != null ? function.getRealmContext() : context;
        JSValue result = callConstructor(context, constructorContext, function, constructThis, args, newTarget);

        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (result instanceof JSObject) {
            return result;
        }
        if (isDerived) {
            if (!(result instanceof JSUndefined)) {
                return context.throwTypeError("Derived constructors may only return object or undefined");
            }
            // Use lastConstructorThisArg saved by RETURN/RETURN_UNDEF before frame was popped
            JSValue finalThis = constructorContext.getVirtualMachine().getLastConstructorThisArg();
            if (finalThis instanceof JSObject) {
                return finalThis;
            }
            return context.throwReferenceError(
                    "Must call super constructor in derived class before accessing 'this' or returning from derived constructor");
        }
        return thisObject;
    }
}
