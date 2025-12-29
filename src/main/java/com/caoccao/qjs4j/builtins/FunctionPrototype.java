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

        // First argument is the 'this' value for the called function
        JSValue applyThisArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // Second argument is the array of arguments
        JSValue[] callArgs;
        if (args.length > 1 && args[1] != null && !(args[1] instanceof JSUndefined) && !(args[1] instanceof JSNull)) {
            if (args[1] instanceof JSArray arr) {
                // Convert JSArray to JSValue[]
                long length = arr.getLength();
                callArgs = new JSValue[(int) length];
                for (int i = 0; i < length; i++) {
                    callArgs[i] = arr.get(i);
                }
            } else {
                return context.throwTypeError("CreateListFromArrayLike called on non-object");
            }
        } else {
            callArgs = new JSValue[0];
        }

        if (thisArg instanceof JSProxy proxy) {
            // Check if proxy's target is callable
            if (!JSTypeChecking.isFunction(proxy.getTarget())) {
                return context.throwTypeError("Function.prototype.apply called on non-function");
            }
            // Use the proxy's apply mechanism
            return proxy.apply(context, applyThisArg, callArgs);
        } else if (thisArg instanceof JSFunction func) {
            return func.call(context, applyThisArg, callArgs);
        } else {
            return context.throwTypeError("Function.prototype.apply called on non-function");
        }
    }

    /**
     * Function.prototype.bind(thisArg, ...args)
     * ES2020 19.2.3.2
     */
    public static JSValue bind(JSContext context, JSValue thisArg, JSValue[] args) {
        // thisArg for bind() is the function itself
        if (!(thisArg instanceof JSFunction targetFunc)) {
            return context.throwTypeError("Function.prototype.bind called on non-function");
        }

        // First argument is the 'this' value to bind
        JSValue boundThis = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // Remaining arguments are pre-bound arguments
        JSValue[] boundArgs = new JSValue[args.length - 1];
        if (args.length > 1) {
            System.arraycopy(args, 1, boundArgs, 0, args.length - 1);
        }

        // Create a bound function
        return new JSBoundFunction(targetFunc, boundThis, boundArgs);
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
            JSValue[] callArgs = new JSValue[args.length - 1];
            if (args.length > 1) {
                System.arraycopy(args, 1, callArgs, 0, args.length - 1);
            }
            return proxy.apply(context, callThisArg, callArgs);
        } else if (thisArg instanceof JSFunction func) {
            // First argument is the 'this' value for the called function
            JSValue callThisArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

            // Remaining arguments are passed to the function
            JSValue[] callArgs = new JSValue[args.length - 1];
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

        return new JSNumber(func.getLength());
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
