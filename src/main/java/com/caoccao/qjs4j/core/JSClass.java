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

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an ES6 class in JavaScript.
 * Based on ES2020 class syntax specification.
 * <p>
 * In JavaScript, classes are special functions:
 * - typeof MyClass === 'function'
 * - Classes can only be called with 'new'
 * - Classes are always in strict mode
 * - Classes have a prototype property
 * - Constructor initializes instances
 * - Methods are added to prototype
 * - Static methods are added to the class itself
 */
public final class JSClass extends JSFunction {
    public static final String NAME = JSFunction.NAME;
    private final JSFunction constructor;
    private final Map<String, PropertyDescriptor> instanceFields;
    private final Map<String, JSFunction> instanceMethods;
    private final String name;
    private final JSObject prototype;
    private final Map<String, PropertyDescriptor> staticFields;
    private final Map<String, JSFunction> staticMethods;
    private final JSClass superClass;

    /**
     * Create a new class.
     *
     * @param name        Class name
     * @param constructor Constructor function
     * @param superClass  Parent class (null for no inheritance)
     */
    public JSClass(String name, JSFunction constructor, JSClass superClass) {
        super();
        this.name = name != null ? name : "";
        this.constructor = constructor;
        this.superClass = superClass;
        this.prototype = new JSObject();
        this.instanceMethods = new HashMap<>();
        this.staticMethods = new HashMap<>();
        this.instanceFields = new HashMap<>();
        this.staticFields = new HashMap<>();

        // Set up prototype chain
        if (superClass != null) {
            // Child.prototype.__proto__ = Parent.prototype
            this.prototype.setPrototype(superClass.prototype);
        }

        // Set constructor property on prototype
        this.prototype.set(PropertyKey.CONSTRUCTOR, this);

        // Set prototype property on class
        this.set(PropertyKey.PROTOTYPE, prototype);
    }

    /**
     * Add an instance field initializer.
     * Instance fields are initialized in the constructor.
     *
     * @param fieldName  Field name
     * @param descriptor Property descriptor
     */
    public void addInstanceField(String fieldName, PropertyDescriptor descriptor) {
        instanceFields.put(fieldName, descriptor);
    }

    /**
     * Add an instance method to the class.
     * Instance methods are added to the prototype.
     *
     * @param methodName Method name
     * @param method     Method function
     */
    public void addInstanceMethod(String methodName, JSFunction method) {
        instanceMethods.put(methodName, method);
        prototype.set(methodName, method);
    }

    /**
     * Add a static field to the class.
     * Static fields are added to the class itself.
     *
     * @param fieldName  Field name
     * @param descriptor Property descriptor
     */
    public void addStaticField(String fieldName, PropertyDescriptor descriptor) {
        staticFields.put(fieldName, descriptor);
        this.set(fieldName, descriptor.value);
    }

    /**
     * Add a static method to the class.
     * Static methods are added to the class itself.
     *
     * @param methodName Method name
     * @param method     Method function
     */
    public void addStaticMethod(String methodName, JSFunction method) {
        staticMethods.put(methodName, method);
        this.set(methodName, method);
    }

    /**
     * Call the class as a function (throws error - classes must be called with 'new').
     */
    @Override
    public JSValue call(JSContext context, JSValue thisArg, JSValue[] args) {
        return context.throwTypeError("Class constructor " + name + " cannot be invoked without 'new'");
    }

    /**
     * Construct a new instance of the class.
     *
     * @param context The execution context
     * @param args    Constructor arguments
     * @return The new instance
     */
    public JSObject construct(JSContext context, JSValue[] args) {
        // Create new instance with this class's prototype
        JSObject instance = new JSObject();
        instance.setPrototype(prototype);

        // Initialize instance fields
        for (Map.Entry<String, PropertyDescriptor> entry : instanceFields.entrySet()) {
            String fieldName = entry.getKey();
            PropertyDescriptor desc = entry.getValue();
            instance.set(fieldName, desc.value);
        }

        // If there's a super class, call super constructor first
        if (superClass != null) {
            // In a full implementation, this would be handled by bytecode
            // For now, we just set up the prototype chain (already done)
        }

        // Call the constructor with the instance as 'this'
        JSValue constructorResult = constructor.call(context, instance, args);

        // If constructor explicitly returns an object, use that
        // Otherwise, return the instance
        if (constructorResult instanceof JSObject && !(constructorResult instanceof JSNull)) {
            return (JSObject) constructorResult;
        }

        return instance;
    }

    /**
     * Get the constructor function.
     */
    public JSFunction getConstructor() {
        return constructor;
    }

    /**
     * Get all instance methods.
     */
    public Map<String, JSFunction> getInstanceMethods() {
        return instanceMethods;
    }

    /**
     * Get the number of constructor parameters.
     */
    @Override
    public int getLength() {
        return constructor.getLength();
    }

    /**
     * Get the class name.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Get the prototype object.
     */
    public JSObject getPrototypeObject() {
        return prototype;
    }

    /**
     * Get all static methods.
     */
    public Map<String, JSFunction> getStaticMethods() {
        return staticMethods;
    }

    /**
     * Get the super class.
     */
    public JSClass getSuperClass() {
        return superClass;
    }

    @Override
    public Object toJavaObject() {
        return toString();
    }

    @Override
    public String toString() {
        return "class " + name + " { [native code] }";
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
    }

    /**
     * Property descriptor for class fields.
     */
    public record PropertyDescriptor(JSValue value, boolean writable, boolean enumerable, boolean configurable) {
    }
}
