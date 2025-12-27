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
 * Builder for creating ES6 classes programmatically.
 * Simplifies class creation with a fluent API.
 * <p>
 * Example:
 * <pre>
 * JSClass myClass = new ClassBuilder("MyClass")
 *     .constructor((ctx, thisArg, args) -> {
 *         thisArg.set("x", args[0]);
 *         return JSUndefined.INSTANCE;
 *     })
 *     .instanceMethod("getValue", (ctx, thisArg, args) -> {
 *         return thisArg.get("x");
 *     })
 *     .staticMethod("create", (ctx, thisArg, args) -> {
 *         // Create and return new instance
 *     })
 *     .build();
 * </pre>
 */
public final class ClassBuilder {
    private final String name;
    private JSFunction constructor;
    private JSClass superClass;
    private final JSClass classObject;

    /**
     * Create a new class builder.
     *
     * @param name Class name
     */
    public ClassBuilder(String name) {
        this.name = name;
        this.constructor = new JSNativeFunction("constructor", 0, (ctx, thisArg, args) -> JSUndefined.INSTANCE);
        this.superClass = null;
        this.classObject = null; // Will be created in build()
    }

    /**
     * Set the constructor function.
     *
     * @param constructor Constructor function
     * @return This builder
     */
    public ClassBuilder constructor(JSNativeFunction.NativeCallback constructor) {
        this.constructor = new JSNativeFunction("constructor", 0, constructor);
        return this;
    }

    /**
     * Set the constructor function with parameter count.
     *
     * @param length      Number of parameters
     * @param constructor Constructor function
     * @return This builder
     */
    public ClassBuilder constructor(int length, JSNativeFunction.NativeCallback constructor) {
        this.constructor = new JSNativeFunction("constructor", length, constructor);
        return this;
    }

    /**
     * Set the super class for inheritance.
     *
     * @param superClass Parent class
     * @return This builder
     */
    public ClassBuilder extends_(JSClass superClass) {
        this.superClass = superClass;
        return this;
    }

    /**
     * Add an instance method.
     *
     * @param methodName Method name
     * @param callback   Method implementation
     * @return This builder
     */
    public ClassBuilder instanceMethod(String methodName, JSNativeFunction.NativeCallback callback) {
        return instanceMethod(methodName, 0, callback);
    }

    /**
     * Add an instance method with parameter count.
     *
     * @param methodName Method name
     * @param length     Number of parameters
     * @param callback   Method implementation
     * @return This builder
     */
    public ClassBuilder instanceMethod(String methodName, int length, JSNativeFunction.NativeCallback callback) {
        if (classObject == null) {
            throw new IllegalStateException("Must call build() before adding methods");
        }
        JSNativeFunction method = new JSNativeFunction(methodName, length, callback);
        classObject.addInstanceMethod(methodName, method);
        return this;
    }

    /**
     * Add a static method.
     *
     * @param methodName Method name
     * @param callback   Method implementation
     * @return This builder
     */
    public ClassBuilder staticMethod(String methodName, JSNativeFunction.NativeCallback callback) {
        return staticMethod(methodName, 0, callback);
    }

    /**
     * Add a static method with parameter count.
     *
     * @param methodName Method name
     * @param length     Number of parameters
     * @param callback   Method implementation
     * @return This builder
     */
    public ClassBuilder staticMethod(String methodName, int length, JSNativeFunction.NativeCallback callback) {
        if (classObject == null) {
            throw new IllegalStateException("Must call build() before adding methods");
        }
        JSNativeFunction method = new JSNativeFunction(methodName, length, callback);
        classObject.addStaticMethod(methodName, method);
        return this;
    }

    /**
     * Add an instance field with initial value.
     *
     * @param fieldName    Field name
     * @param initialValue Initial value
     * @return This builder
     */
    public ClassBuilder instanceField(String fieldName, JSValue initialValue) {
        if (classObject == null) {
            throw new IllegalStateException("Must call build() before adding fields");
        }
        JSClass.PropertyDescriptor descriptor = new JSClass.PropertyDescriptor(
                initialValue,
                true,  // writable
                true,  // enumerable
                true   // configurable
        );
        classObject.addInstanceField(fieldName, descriptor);
        return this;
    }

    /**
     * Add a static field with value.
     *
     * @param fieldName Field name
     * @param value     Field value
     * @return This builder
     */
    public ClassBuilder staticField(String fieldName, JSValue value) {
        if (classObject == null) {
            throw new IllegalStateException("Must call build() before adding fields");
        }
        JSClass.PropertyDescriptor descriptor = new JSClass.PropertyDescriptor(
                value,
                true,  // writable
                true,  // enumerable
                true   // configurable
        );
        classObject.addStaticField(fieldName, descriptor);
        return this;
    }

    /**
     * Build the class.
     *
     * @return The constructed class
     */
    public JSClass build() {
        JSClass result = new JSClass(name, constructor, superClass);
        return result;
    }

    /**
     * Build the class and immediately add methods/fields.
     * This variant allows method chaining after build().
     *
     * @return A BuilderWithClass that allows adding methods/fields
     */
    public BuilderWithClass buildAndConfigure() {
        JSClass result = new JSClass(name, constructor, superClass);
        return new BuilderWithClass(result);
    }

    /**
     * Builder that wraps an already-created class for configuration.
     */
    public static final class BuilderWithClass {
        private final JSClass classObject;

        private BuilderWithClass(JSClass classObject) {
            this.classObject = classObject;
        }

        public BuilderWithClass instanceMethod(String methodName, JSNativeFunction.NativeCallback callback) {
            return instanceMethod(methodName, 0, callback);
        }

        public BuilderWithClass instanceMethod(String methodName, int length, JSNativeFunction.NativeCallback callback) {
            JSNativeFunction method = new JSNativeFunction(methodName, length, callback);
            classObject.addInstanceMethod(methodName, method);
            return this;
        }

        public BuilderWithClass staticMethod(String methodName, JSNativeFunction.NativeCallback callback) {
            return staticMethod(methodName, 0, callback);
        }

        public BuilderWithClass staticMethod(String methodName, int length, JSNativeFunction.NativeCallback callback) {
            JSNativeFunction method = new JSNativeFunction(methodName, length, callback);
            classObject.addStaticMethod(methodName, method);
            return this;
        }

        public BuilderWithClass instanceField(String fieldName, JSValue initialValue) {
            JSClass.PropertyDescriptor descriptor = new JSClass.PropertyDescriptor(
                    initialValue,
                    true, true, true
            );
            classObject.addInstanceField(fieldName, descriptor);
            return this;
        }

        public BuilderWithClass staticField(String fieldName, JSValue value) {
            JSClass.PropertyDescriptor descriptor = new JSClass.PropertyDescriptor(
                    value,
                    true, true, true
            );
            classObject.addStaticField(fieldName, descriptor);
            return this;
        }

        public JSClass getClass_() {
            return classObject;
        }
    }
}
