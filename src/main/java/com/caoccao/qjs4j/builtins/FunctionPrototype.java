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

package com.caoccao.qjs4j.builtins;

import com.caoccao.qjs4j.core.*;

/**
 * Implementation of JavaScript Function.prototype methods.
 * Based on ES2020 Function.prototype specification.
 */
public final class FunctionPrototype {
    /**
     * Function.prototype.apply(thisArg, argArray)
     * ES2020 19.2.3.1
     */
    public static JSValue apply(JSContext context, JSValue thisArg, JSValue[] args) {
        // thisArg for apply() is the function itself (or a proxy to a function)
        // Following QuickJS: proxies to functions should work with Function.prototype.apply

        JSProxy callableProxy = null;
        JSFunction callableFunction = null;
        if (thisArg instanceof JSProxy proxy) {
            if (!JSTypeChecking.isFunction(proxy.getTarget())) {
                return context.throwTypeError("Function.prototype.apply called on non-function");
            }
            callableProxy = proxy;
        } else if (thisArg instanceof JSFunction func) {
            callableFunction = func;
        } else {
            return context.throwTypeError("Function.prototype.apply called on non-function");
        }

        // First argument is the 'this' value for the called function
        JSValue applyThisArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue arrayArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;
        JSValue[] callArgs = buildArgumentList(context, arrayArg);
        if (callArgs == null) {
            return context.getPendingException();
        }

        if (callableProxy != null) {
            return callableProxy.apply(context, applyThisArg, callArgs);
        } else {
            return callableFunction.call(context, applyThisArg, callArgs);
        }
    }

    /**
     * Function.prototype.bind(thisArg, ...args)
     * ES2024 20.2.3.2
     */
    public static JSValue bind(JSContext context, JSValue thisArg, JSValue[] args) {
        // thisArg for bind() is the function itself
        if (!(thisArg instanceof JSFunction targetFunc)) {
            return context.throwTypeError("Function.prototype.bind called on non-function");
        }

        // First argument is the 'this' value to bind
        JSValue boundThis = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // Remaining arguments are pre-bound arguments
        int argCount = Math.max(0, args.length - 1);
        JSValue[] boundArgs = new JSValue[argCount];
        if (args.length > 1) {
            System.arraycopy(args, 1, boundArgs, 0, args.length - 1);
        }

        // Step 5-7: Compute length from target's "length" own property
        // Per spec, read target's "length" property (not use internal getLength())
        // to handle overridden length values including Infinity, NaN, etc.
        double computedLength = 0;
        PropertyDescriptor lengthDesc = targetFunc.getOwnPropertyDescriptor(PropertyKey.LENGTH);
        if (lengthDesc != null) {
            JSValue targetLenValue = targetFunc.get(context, PropertyKey.LENGTH);
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            if (targetLenValue instanceof JSNumber targetLenNum) {
                double targetLen = targetLenNum.value();
                if (Double.isInfinite(targetLen) && targetLen > 0) {
                    computedLength = Double.POSITIVE_INFINITY;
                } else if (Double.isInfinite(targetLen) && targetLen < 0) {
                    computedLength = 0;
                } else if (Double.isNaN(targetLen)) {
                    computedLength = 0;
                } else {
                    // ToIntegerOrInfinity: truncate towards zero
                    long targetLenInt = (long) targetLen;
                    computedLength = Math.max(targetLenInt - argCount, 0);
                }
            }
            // If targetLen is not a number, L = 0 (default)
        }

        // Step 12-15: Compute name from target's "name" property
        // Per spec, read target's "name" property (not use internal getName())
        JSValue targetNameValue = targetFunc.get(context, PropertyKey.NAME);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        String targetName;
        if (targetNameValue instanceof JSString nameStr) {
            targetName = nameStr.value();
        } else {
            targetName = "";
        }
        String boundName = "bound " + targetName;

        // Create bound function with computed length and name
        JSBoundFunction boundFunc = new JSBoundFunction(targetFunc, boundThis, boundArgs,
                computedLength, boundName);

        // Set [[Prototype]] to Function.prototype
        context.transferPrototype(boundFunc, JSFunction.NAME);
        return boundFunc;
    }

    private static JSValue[] buildArgumentList(JSContext context, JSValue arrayArg) {
        if (arrayArg == null || arrayArg instanceof JSUndefined || arrayArg instanceof JSNull) {
            return new JSValue[0];
        }
        if (!(arrayArg instanceof JSObject arrayLike)) {
            context.throwTypeError("CreateListFromArrayLike called on non-object");
            return null;
        }

        JSValue lengthValue = arrayLike.get(context, PropertyKey.LENGTH);
        if (context.hasPendingException()) {
            return null;
        }

        long length = JSTypeConversions.toLength(context, lengthValue);
        if (context.hasPendingException()) {
            return null;
        }
        if (length > Integer.MAX_VALUE) {
            context.throwRangeError("too many arguments in function call");
            return null;
        }

        JSValue[] callArgs = new JSValue[(int) length];
        for (int i = 0; i < callArgs.length; i++) {
            JSValue argumentValue = arrayLike.get(context, PropertyKey.fromString(String.valueOf(i)));
            if (context.hasPendingException()) {
                return null;
            }
            if (argumentValue instanceof JSUndefined) {
                argumentValue = arrayLike.get(context, PropertyKey.fromString(Integer.toString(i)));
                if (context.hasPendingException()) {
                    return null;
                }
            }
            callArgs[i] = argumentValue;
            if (context.hasPendingException()) {
                return null;
            }
        }
        return callArgs;
    }

    /**
     * Function.prototype.call(thisArg, ...args)
     * ES2020 19.2.3.3
     */
    public static JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        // thisArg for call() is the function itself (or a proxy to a function)
        // Following QuickJS: proxies to functions should work with Function.prototype.call
        if (thisArg instanceof JSProxy proxy) {
            // Check if proxy's target is callable
            if (!JSTypeChecking.isFunction(proxy.getTarget())) {
                return context.throwTypeError("Function.prototype.call called on non-function");
            }
            // Use the proxy's apply mechanism
            JSValue callThisArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            JSValue[] callArgs = new JSValue[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, callArgs, 0, args.length - 1);
            }
            return proxy.apply(context, callThisArg, callArgs);
        } else if (thisArg instanceof JSFunction func) {
            // First argument is the 'this' value for the called function
            JSValue callThisArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

            // Remaining arguments are passed to the function
            JSValue[] callArgs = new JSValue[Math.max(0, args.length - 1)];
            if (args.length > 1) {
                System.arraycopy(args, 1, callArgs, 0, args.length - 1);
            }

            return func.call(context, callThisArg, callArgs);
        } else {
            return context.throwTypeError("Function.prototype.call called on non-function");
        }
    }

    /**
     * get Function.prototype.length
     * ES2020 19.2.3.4
     */
    public static JSValue getLength(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSFunction func)) {
            return context.throwTypeError("Function.prototype.length called on non-function");
        }

        return JSNumber.of(func.getLength());
    }

    /**
     * get Function.prototype.name
     * ES2020 19.2.3.6
     */
    public static JSValue getName(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSFunction func)) {
            return context.throwTypeError("Function.prototype.name called on non-function");
        }

        String name = func.getName();
        return new JSString(name != null ? name : "");
    }

    /**
     * OrdinaryHasInstance(C, O) - ES2024 7.3.21
     */
    private static JSValue ordinaryHasInstance(JSContext context, JSValue constructor, JSValue objectValue) {
        // Step 1: If IsCallable(C) is false, return false.
        if (!JSTypeChecking.isCallable(constructor)) {
            return JSBoolean.FALSE;
        }

        // Step 2: If C has [[BoundTargetFunction]], use InstanceofOperator on bound target
        if (constructor instanceof JSBoundFunction boundFunc) {
            // Recursively check with the bound target function
            return ordinaryHasInstance(context, boundFunc.getTarget(), objectValue);
        }

        // Step 3: If Type(O) is not Object, return false.
        if (!(objectValue instanceof JSObject object)) {
            return JSBoolean.FALSE;
        }

        // Step 4: Let P be ? Get(C, "prototype").
        if (!(constructor instanceof JSObject constructorObj)) {
            return JSBoolean.FALSE;
        }
        JSValue prototypeValue = constructorObj.get(context, PropertyKey.PROTOTYPE);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }

        // Step 5: If Type(P) is not Object, throw TypeError.
        if (!(prototypeValue instanceof JSObject constructorPrototype)) {
            return context.throwTypeError("Function has non-object prototype in instanceof check");
        }

        // Step 7: Repeat - walk prototype chain
        JSObject currentPrototype = object.getPrototype();
        while (currentPrototype != null) {
            if (currentPrototype == constructorPrototype) {
                return JSBoolean.TRUE;
            }
            // Handle proxy getPrototypeOf trap
            if (currentPrototype instanceof JSProxy proxy) {
                JSValue proxyProto = proxy.get(context, PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG));
                // Actually, we need to call [[GetPrototypeOf]] on the proxy
                // For now, get the prototype from the proxy itself
            }
            currentPrototype = currentPrototype.getPrototype();
        }
        return JSBoolean.FALSE;
    }

    /**
     * Function.prototype[Symbol.hasInstance](V)
     * ES2024 20.2.3.6 - OrdinaryHasInstance
     */
    public static JSValue symbolHasInstance(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        return ordinaryHasInstance(context, thisArg, value);
    }

    /**
     * Function.prototype.toString()
     * ES2020 19.2.3.5
     */
    public static JSValue toString_(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSFunction func)) {
            return context.throwTypeError("Function.prototype.toString called on non-function");
        }

        // Use the function's own toString() implementation which handles async, generator, etc.
        return new JSString(func.toString());
    }
}
