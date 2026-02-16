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

/**
 * Helper for implementing super keyword in ES6 classes.
 * Provides access to parent class constructor and methods.
 * <p>
 * The super keyword has two uses in JavaScript:
 * 1. super() - calls parent constructor (must be called in derived class constructor)
 * 2. super.method() - calls parent class method
 */
public final class SuperHelper {

    /**
     * Call the super constructor.
     * Must be called in a derived class constructor before accessing 'this'.
     *
     * @param context      The execution context
     * @param derivedClass The derived class
     * @param thisArg      The instance being constructed
     * @param args         Arguments to pass to super constructor
     * @return Undefined (super() doesn't return a value)
     */
    public static JSValue callSuperConstructor(JSContext context, JSClass derivedClass, JSObject thisArg, JSValue[] args) {
        JSClass superClass = derivedClass.getSuperClass();
        if (superClass == null) {
            return context.throwReferenceError("super() called without a parent class");
        }

        // Call super constructor with the current instance as 'this'
        JSFunction superConstructor = superClass.getConstructor();
        superConstructor.call(context, thisArg, args);

        return JSUndefined.INSTANCE;
    }

    /**
     * Create a super reference object for use in bytecode.
     * This object provides access to super constructor and methods.
     *
     * @param context      The execution context
     * @param derivedClass The derived class
     * @param instance     The current instance
     * @return A super reference object
     */
    public static JSObject createSuperReference(JSContext context, JSClass derivedClass, JSObject instance) {
        JSObject superRef = context.createJSObject();

        // Add __call__ for super() constructor calls
        superRef.set("__call__", new JSNativeFunction(
                "super",
                0,
                (childContext, thisArg, args) -> callSuperConstructor(childContext, derivedClass, instance, args)));

        // Add __get__ for super.method() calls
        superRef.set("__get__", new JSNativeFunction("getSuperMethod", 1, (childContext, thisArg, args) -> {
            if (args.length == 0) {
                return JSUndefined.INSTANCE;
            }
            String methodName = JSTypeConversions.toString(childContext, args[0]).value();
            JSValue method = getSuperMethod(derivedClass, methodName);

            // If method is a function, bind it to the current instance
            if (method instanceof JSFunction func) {
                return new JSBoundFunction(func, instance, new JSValue[0]);
            }

            return method;
        }));

        return superRef;
    }

    /**
     * Get a method from the super class.
     * Used for super.methodName() calls.
     *
     * @param derivedClass The derived class
     * @param methodName   The method name to look up
     * @return The method from the parent class, or null if not found
     */
    public static JSValue getSuperMethod(JSClass derivedClass, String methodName) {
        JSClass superClass = derivedClass.getSuperClass();
        if (superClass == null) {
            return JSUndefined.INSTANCE;
        }

        // Look up method in parent class prototype
        JSObject superPrototype = superClass.getPrototypeObject();
        JSValue method = superPrototype.get(methodName);

        return method != null ? method : JSUndefined.INSTANCE;
    }

}
