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
 * Abstract base class for JavaScript function types. In JavaScript, functions are objects, so JSFunction extends
 * JSObject.
 */
public abstract sealed class JSFunction extends JSObject implements JSNativeCallback
        permits JSBytecodeFunction, JSNativeFunction, JSBoundFunction, JSClass {
    public static final String NAME = "Function";
    /**
     * For code that came from {@code eval}, where it was evaluated, in V8's stack-trace form:
     * {@code eval at outer (file.js:1:27)}. Null for code that did not come from eval.
     */
    /** The direct eval whose lexical environment this function belongs to; null outside eval code. */
    private JSEvalEnvironment evalEnvironment;
    private String evalOrigin;
    private JSContext homeContext;
    private JSObject homeObject;
    private String importMetaFilename;
    private JSModuleEnvironment moduleEnvironment;

    protected JSFunction(JSContext context) {
        super(context);
    }

    /**
     * Call this function with the given context, this value, and arguments.
     */
    @Override
    public abstract JSValue call(JSContext context, JSValue thisArg, JSValue... args);

    public JSEvalEnvironment getEvalEnvironment() {
        return evalEnvironment;
    }

    public String getEvalOrigin() {
        return evalOrigin;
    }

    protected JSContext getHomeContext() {
        return homeContext;
    }

    public JSObject getHomeObject() {
        return homeObject;
    }

    public String getImportMetaFilename() {
        return importMetaFilename;
    }

    /**
     * Get the number of formal parameters.
     */
    public abstract int getLength();

    public JSModuleEnvironment getModuleEnvironment() {
        return moduleEnvironment;
    }

    /**
     * Get the function name.
     */
    public abstract String getName();

    public JSContext getRealmContext() {
        return homeContext != null ? homeContext : getContext();
    }

    final void initializeConstructorPrototype(PropertyDescriptor.DataState attributes) {
        JSObject functionPrototype = new JSObject(context);
        functionPrototype.definePropertyInternal(PropertyKey.CONSTRUCTOR,
                PropertyDescriptor.dataDescriptor(this, PropertyDescriptor.DataState.ConfigurableWritable));
        definePropertyInternal(PropertyKey.PROTOTYPE, PropertyDescriptor.dataDescriptor(functionPrototype, attributes));
    }

    /** Installs the initial own properties, in ECMAScript order, on a newly allocated function. */
    final void initializeNameAndLength(String functionName, JSNumber functionLength) {
        // Both descriptors are already complete and these slots have no existing attributes to merge.
        definePropertyInternal(PropertyKey.LENGTH,
                PropertyDescriptor.dataDescriptor(functionLength, PropertyDescriptor.DataState.Configurable));
        definePropertyInternal(PropertyKey.NAME, PropertyDescriptor.dataDescriptor(new JSString(functionName),
                PropertyDescriptor.DataState.Configurable));
    }

    /**
     * Initialize the function's prototype chain to inherit from Function.prototype. This should be called after
     * creation when the context is available.
     * <p>
     * For generator functions, also sets up the function's own "prototype" property to inherit from
     * Generator.prototype, matching QuickJS js_closure2 behavior.
     */
    public void initializePrototypeChain(JSContext context) {
        if (homeContext == null) {
            homeContext = context;
        }
        if (this instanceof JSBytecodeFunction bytecodeFunc) {
            // For async generator functions, use AsyncGeneratorFunction.prototype
            if (bytecodeFunc.isGenerator() && bytecodeFunc.isAsync()) {
                JSObject asyncGeneratorFunctionPrototype = context.getAsyncGeneratorFunctionPrototype();
                if (asyncGeneratorFunctionPrototype != null) {
                    this.setPrototype(asyncGeneratorFunctionPrototype);
                    // Set up the function's own "prototype" property:
                    // Each async generator function gets a prototype object
                    // that inherits from AsyncGenerator.prototype (writable, not configurable)
                    JSValue asyncGenProto = asyncGeneratorFunctionPrototype.get(PropertyKey.PROTOTYPE);
                    if (asyncGenProto instanceof JSObject asyncGenProtoObj) {
                        JSObject funcPrototype = new JSObject(context);
                        funcPrototype.setPrototype(asyncGenProtoObj);
                        this.defineProperty(PropertyKey.PROTOTYPE, PropertyDescriptor.dataDescriptor(funcPrototype,
                                PropertyDescriptor.DataState.Writable));
                    }
                    return;
                }
            }
            // For sync generator functions, use GeneratorFunction.prototype
            if (bytecodeFunc.isGenerator() && !bytecodeFunc.isAsync()) {
                JSObject generatorFunctionPrototype = context.getGeneratorFunctionPrototype();
                if (generatorFunctionPrototype != null) {
                    this.setPrototype(generatorFunctionPrototype);
                    // Set up the function's own "prototype" property:
                    // In QuickJS, each generator function gets a prototype object
                    // that inherits from Generator.prototype (writable, not configurable)
                    JSValue genProto = generatorFunctionPrototype.get(PropertyKey.PROTOTYPE);
                    if (genProto instanceof JSObject genProtoObj) {
                        JSObject funcPrototype = new JSObject(context);
                        funcPrototype.setPrototype(genProtoObj);
                        this.defineProperty(PropertyKey.PROTOTYPE, PropertyDescriptor.dataDescriptor(funcPrototype,
                                PropertyDescriptor.DataState.Writable));
                    }
                    return;
                }
            }
            // For async (non-generator) functions, use AsyncFunction.prototype if available
            if (bytecodeFunc.isAsync()) {
                if (context.transferPrototype(this, context.getAsyncFunctionConstructor())) {
                    return;
                }
            }
        }
        context.transferPrototype(this, NAME);
        // Also initialize the function's .prototype property's [[Prototype]] to Object.prototype.
        // JSBytecodeFunction creates .prototype with new JSObject() which has [[Prototype]] = null,
        // because the context is not available at construction time.
        if (this instanceof JSBytecodeFunction) {
            JSValue protoVal = this.get(PropertyKey.PROTOTYPE);
            if (protoVal instanceof JSObject protoObj && protoObj.getPrototype() == null) {
                context.transferPrototype(protoObj, JSObject.NAME);
            }
        }
    }

    public void setEvalEnvironment(JSEvalEnvironment evalEnvironment) {
        this.evalEnvironment = evalEnvironment;
    }

    public void setEvalOrigin(String evalOrigin) {
        this.evalOrigin = evalOrigin;
    }

    public void setHomeObject(JSObject homeObject) {
        this.homeObject = homeObject;
    }

    public void setImportMetaFilename(String importMetaFilename) {
        this.importMetaFilename = importMetaFilename;
    }

    public void setModuleEnvironment(JSModuleEnvironment moduleEnvironment) {
        this.moduleEnvironment = moduleEnvironment;
    }

    @Override
    public Object toJavaObject() {
        return null;
    }
}
