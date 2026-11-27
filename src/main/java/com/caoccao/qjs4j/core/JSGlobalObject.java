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

import com.caoccao.qjs4j.builtins.array.*;
import com.caoccao.qjs4j.builtins.buffer.AtomicsObject;
import com.caoccao.qjs4j.builtins.buffer.DataViewConstructor;
import com.caoccao.qjs4j.builtins.collection.*;
import com.caoccao.qjs4j.builtins.date.DateConstructor;
import com.caoccao.qjs4j.builtins.date.DatePrototype;
import com.caoccao.qjs4j.builtins.iterator.AsyncGeneratorPrototype;
import com.caoccao.qjs4j.builtins.iterator.AsyncIteratorPrototype;
import com.caoccao.qjs4j.builtins.iterator.IteratorConstructor;
import com.caoccao.qjs4j.builtins.iterator.IteratorPrototype;
import com.caoccao.qjs4j.builtins.number.BigIntConstructor;
import com.caoccao.qjs4j.builtins.number.MathObject;
import com.caoccao.qjs4j.builtins.number.NumberConstructor;
import com.caoccao.qjs4j.builtins.number.NumberPrototype;
import com.caoccao.qjs4j.builtins.object.*;
import com.caoccao.qjs4j.builtins.promise.PromiseConstructor;
import com.caoccao.qjs4j.builtins.regexp.RegExpConstructor;
import com.caoccao.qjs4j.builtins.regexp.RegExpPrototype;
import com.caoccao.qjs4j.builtins.temporal.TemporalNow;
import com.caoccao.qjs4j.builtins.text.StringConstructor;
import com.caoccao.qjs4j.builtins.text.StringPrototype;
import com.caoccao.qjs4j.core.buffer.JSDataView;
import com.caoccao.qjs4j.core.buffer.JSUint8Array;
import com.caoccao.qjs4j.core.buffer.TypedArrayKind;
import com.caoccao.qjs4j.core.collection.*;
import com.caoccao.qjs4j.core.console.JSConsole;
import com.caoccao.qjs4j.core.date.JSDate;
import com.caoccao.qjs4j.core.error.JSError;
import com.caoccao.qjs4j.core.iterator.JSIterator;
import com.caoccao.qjs4j.core.json.JSONObject;
import com.caoccao.qjs4j.core.promise.JSPromise;
import com.caoccao.qjs4j.core.regexp.JSRegExp;
import com.caoccao.qjs4j.exceptions.JSErrorType;

import java.util.*;

/**
 * The global object with built-in functions. Based on ECMAScript specification global properties and functions.
 * <p>
 * Implements: - Global value properties (NaN, Infinity, undefined) - Global function properties (parseInt, parseFloat,
 * isNaN, isFinite, eval) - URI handling functions (encodeURI, decodeURI, encodeURIComponent, decodeURIComponent)
 */
public final class JSGlobalObject {
    private final JSConsole console;
    private final JSContext context;
    private final JSObject globalObject;
    private final JSONObject jsonObject;

    public JSGlobalObject(JSContext context) {
        this.context = context;
        this.console = new JSConsole();
        this.globalObject = new JSObject(context);
        this.jsonObject = new JSONObject(context);
    }

    private JSObject createGeneratorFunctionPrototype(JSObject generator, String name, JSNativeCallback call,
            boolean inheritFunctionConstructor) {
        JSObject prototype = context.createJSObject();
        context.transferPrototype(prototype, JSFunction.NAME);
        defineToStringTag(prototype, name);
        prototype.defineProperty(PropertyKey.PROTOTYPE, generator, PropertyDescriptor.DataState.Configurable);
        generator.defineProperty(PropertyKey.CONSTRUCTOR, prototype, PropertyDescriptor.DataState.Configurable);
        JSNativeFunction constructor = new JSNativeFunction(context, name, 1, call, true);
        if (inheritFunctionConstructor
                && globalObject.get(PropertyKey.fromString(JSFunction.NAME)) instanceof JSObject function) {
            constructor.setPrototype(function);
        }
        constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor, PropertyDescriptor.DataState.Configurable);
        return prototype;
    }

    public JSConsole getConsole() {
        return console;
    }

    public JSObject getGlobalObject() {
        return globalObject;
    }

    public JSONObject getJSONObject() {
        return jsonObject;
    }

    /**
     * Initialize the global object with all built-in global properties and functions.
     */
    public void initialize() {
        initializeGlobalObject();

        // Built-in constructors and their prototypes
        initializeObjectConstructor();
        initializeBooleanConstructor();
        initializeArrayConstructor();
        initializeStringConstructor();
        initializeNumberConstructor();
        initializeFunctionConstructor();
        initializeAsyncFunctionConstructor();
        initializeDateConstructor();
        initializeRegExpConstructor();
        initializeSymbolConstructor();
        initializeBigIntConstructor();
        GlobalConstructor.MAP.install(globalObject);
        GlobalConstructor.SET.install(globalObject);
        GlobalConstructor.WEAK_MAP.install(globalObject);
        GlobalConstructor.WEAK_SET.install(globalObject);
        GlobalConstructor.WEAK_REF.install(globalObject);
        GlobalConstructor.FINALIZATION_REGISTRY.install(globalObject);
        initializeMathObject();
        initializeJSONObject();
        initializeIntlObject();
        if (context.getRuntime().getOptions().isTemporalEnabled()) {
            initializeTemporalObject();
        }
        initializeReflectObject();
        initializeProxyConstructor();
        initializePromiseConstructor();
        if (context.getRuntime().getOptions().isShadowRealmEnabled()) {
            GlobalConstructor.SHADOW_REALM.install(globalObject);
        }
        GlobalConstructor.DISPOSABLE_STACK.install(globalObject);
        GlobalConstructor.ASYNC_DISPOSABLE_STACK.install(globalObject);
        initializeIteratorConstructor();
        initializeGeneratorPrototype();
        initializeAsyncGeneratorPrototype();

        // Binary data constructors
        GlobalConstructor.ARRAY_BUFFER.install(globalObject);
        GlobalConstructor.SHARED_ARRAY_BUFFER.install(globalObject);
        initializeDataViewConstructor();
        initializeTypedArrayConstructors();
        initializeAtomicsObject();

        // Error constructors
        initializeErrorConstructors();

        // Initialize function prototype chains after all built-ins are set up.
        // Walk global properties AND context-stored objects not reachable from global
        // (iterator prototypes, generator/async-generator function prototypes).
        Set<JSObject> visited = new HashSet<>();
        initializeFunctionPrototypeChains(globalObject, visited);
        for (JSObject iterProto : context.getIteratorPrototypes()) {
            initializeFunctionPrototypeChains(iterProto, visited);
        }
        if (context.getGeneratorFunctionPrototype() != null) {
            initializeFunctionPrototypeChains(context.getGeneratorFunctionPrototype(), visited);
        }
        if (context.getAsyncGeneratorFunctionPrototype() != null) {
            initializeFunctionPrototypeChains(context.getAsyncGeneratorFunctionPrototype(), visited);
        }
        if (context.getAsyncGeneratorPrototype() != null) {
            initializeFunctionPrototypeChains(context.getAsyncGeneratorPrototype(), visited);
        }

        // Per ES2024 20.5.6.2: The [[Prototype]] of each NativeError constructor is Error.
        // This must run after initializeFunctionPrototypeChains which sets all to Function.prototype.
        initializeNativeErrorPrototypeChains();

        // Set the global object's prototype to Object.prototype so inherited
        // methods like propertyIsEnumerable, hasOwnProperty, toString are available.
        context.transferPrototype(globalObject, JSObject.NAME);
    }

    /**
     * Initialize Array constructor and prototype.
     */
    private void initializeArrayConstructor() {
        // Create Array.prototype as an Array exotic object per ES spec 23.1.3
        // (matching QuickJS JS_NewArray for JS_CLASS_ARRAY)
        JSArray arrayPrototype = new JSArray(context, 0, 0);
        context.transferPrototype(arrayPrototype, JSObject.NAME);
        JSNativeFunction valuesFunction = new JSNativeFunction(context, "values", 0, IteratorPrototype::arrayValues);
        IntrinsicFunctions.ARRAY_PROTOTYPE.install(arrayPrototype);
        defineBuiltin(arrayPrototype, "values", valuesFunction);
        defineMethod(arrayPrototype, "with", 2, ArrayPrototype::with);

        // Array.prototype[Symbol.*]
        arrayPrototype.defineProperty(PropertyKey.SYMBOL_ITERATOR, valuesFunction,
                PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.SYMBOL_UNSCOPABLES, ArrayPrototype.createUnscopablesObject(context),
                PropertyDescriptor.DataState.Configurable);

        // Create Array constructor as a function
        JSNativeFunction arrayConstructor = new JSNativeFunction(context, JSArray.NAME, 1, ArrayConstructor::call,
                true);
        linkConstructor(arrayConstructor, arrayPrototype, JSConstructorType.ARRAY);

        // Array static methods (writable, non-enumerable, configurable per spec)
        IntrinsicFunctions.ARRAY_CONSTRUCTOR.install(arrayConstructor);

        // Symbol.species getter
        defineSpeciesGetter(arrayConstructor, ArrayConstructor::getSpecies);

        defineBuiltin(globalObject, JSArray.NAME, arrayConstructor);
    }

    /**
     * Initialize AsyncFunction constructor and prototype. AsyncFunction is not exposed in global scope but is available
     * via async function constructors.
     */
    private void initializeAsyncFunctionConstructor() {
        // Create AsyncFunction.prototype that inherits from Function.prototype
        JSObject asyncFunctionPrototype = context.createJSObject();
        context.transferPrototype(asyncFunctionPrototype, JSFunction.NAME);
        defineToStringTag(asyncFunctionPrototype, "AsyncFunction");

        // Create AsyncFunction constructor
        // AsyncFunction is not normally exposed, but we need it for the prototype chain
        JSNativeFunction asyncFunctionConstructor = new JSNativeFunction(context, "AsyncFunction", 1,
                FunctionConstructor::callAsync, true);
        JSValue functionConstructorValue = globalObject.get(JSFunction.NAME);
        if (functionConstructorValue instanceof JSObject functionConstructorObject) {
            asyncFunctionConstructor.setPrototype(functionConstructorObject);
        }
        linkConstructor(asyncFunctionConstructor, asyncFunctionPrototype);

        // Store AsyncFunction in the context (not in global scope)
        // This is used internally for setting up prototype chains for async bytecode functions
        context.setAsyncFunctionConstructor(asyncFunctionConstructor);
    }

    /**
     * Initialize AsyncGenerator and AsyncGeneratorFunction prototypes. Based on QuickJS JS_AddIntrinsicPromise
     * (AsyncGeneratorFunction section).
     * <p>
     * Prototype chain: - AsyncGenerator.prototype inherits from AsyncIteratorPrototype -
     * AsyncGeneratorFunction.prototype inherits from Function.prototype - AsyncGeneratorFunction.prototype.prototype =
     * AsyncGenerator.prototype (configurable) - AsyncGenerator.prototype.constructor = AsyncGeneratorFunction.prototype
     * (configurable)
     */
    private void initializeAsyncGeneratorPrototype() {
        // Create AsyncIteratorPrototype (has Symbol.asyncIterator)
        JSObject asyncIteratorPrototype = context.createJSObject();
        JSNativeFunction asyncIteratorMethod = new JSNativeFunction(context, "[Symbol.asyncIterator]", 0,
                (ctx, thisArg, args) -> thisArg);
        asyncIteratorMethod.initializePrototypeChain(context);
        asyncIteratorPrototype.defineProperty(PropertyKey.SYMBOL_ASYNC_ITERATOR, PropertyDescriptor
                .dataDescriptor(asyncIteratorMethod, PropertyDescriptor.DataState.ConfigurableWritable));
        JSNativeFunction asyncDisposeMethod = new JSNativeFunction(context, "[Symbol.asyncDispose]", 0,
                AsyncIteratorPrototype::asyncDispose);
        asyncDisposeMethod.initializePrototypeChain(context);
        asyncIteratorPrototype.defineProperty(PropertyKey.SYMBOL_ASYNC_DISPOSE, PropertyDescriptor
                .dataDescriptor(asyncDisposeMethod, PropertyDescriptor.DataState.ConfigurableWritable));

        // Create AsyncGenerator.prototype inheriting from AsyncIteratorPrototype
        JSObject asyncGeneratorPrototype = context.createJSObject();
        asyncGeneratorPrototype.setPrototype(asyncIteratorPrototype);

        // Shared methods dispatch using the receiver's internal generator state.
        JSNativeFunction asyncGeneratorNext = new JSNativeFunction(context, "next", 1, AsyncGeneratorPrototype::next);
        asyncGeneratorNext.initializePrototypeChain(context);
        asyncGeneratorPrototype.defineProperty(PropertyKey.NEXT, asyncGeneratorNext,
                PropertyDescriptor.DataState.ConfigurableWritable);
        JSNativeFunction asyncGeneratorReturn = new JSNativeFunction(context, "return", 1,
                AsyncGeneratorPrototype::return_);
        asyncGeneratorReturn.initializePrototypeChain(context);
        asyncGeneratorPrototype.defineProperty(PropertyKey.RETURN, asyncGeneratorReturn,
                PropertyDescriptor.DataState.ConfigurableWritable);
        JSNativeFunction asyncGeneratorThrow = new JSNativeFunction(context, "throw", 1,
                AsyncGeneratorPrototype::throw_);
        asyncGeneratorThrow.initializePrototypeChain(context);
        asyncGeneratorPrototype.defineProperty(PropertyKey.THROW, asyncGeneratorThrow,
                PropertyDescriptor.DataState.ConfigurableWritable);

        // AsyncGenerator.prototype[Symbol.toStringTag] = "AsyncGenerator" (configurable)
        defineToStringTag(asyncGeneratorPrototype, "AsyncGenerator");
        context.setAsyncGeneratorPrototype(asyncGeneratorPrototype);

        context.setAsyncGeneratorFunctionPrototype(createGeneratorFunctionPrototype(asyncGeneratorPrototype,
                "AsyncGeneratorFunction", FunctionConstructor::callAsyncGenerator, false));
    }

    /**
     * Initialize Atomics object.
     */
    private void initializeAtomicsObject() {
        // The runtime's own instance, not whatever its options object currently holds: every
        // context of one runtime has to bind these functions to the same object, and a mutation of
        // the options between two createContext() calls must not put them on different ones.
        AtomicsObject atomicsObject = context.getRuntime().getAtomicsObject();
        JSObject atomics = context.createJSObject();
        defineMethod(atomics, "add", 3, atomicsObject::add);
        defineMethod(atomics, "and", 3, atomicsObject::and);
        defineMethod(atomics, "compareExchange", 4, atomicsObject::compareExchange);
        defineMethod(atomics, "exchange", 3, atomicsObject::exchange);
        defineMethod(atomics, "isLockFree", 1, atomicsObject::isLockFree);
        defineMethod(atomics, "load", 2, atomicsObject::load);
        defineMethod(atomics, "notify", 3, atomicsObject::notify);
        defineMethod(atomics, "or", 3, atomicsObject::or);
        defineMethod(atomics, "pause", 0, atomicsObject::pause);
        defineMethod(atomics, "store", 3, atomicsObject::store);
        defineMethod(atomics, "sub", 3, atomicsObject::sub);
        defineMethod(atomics, "wait", 4, atomicsObject::wait);
        defineMethod(atomics, "waitAsync", 4, atomicsObject::waitAsync);
        defineMethod(atomics, "xor", 3, atomicsObject::xor);
        defineToStringTag(atomics, "Atomics");

        defineBuiltin(globalObject, "Atomics", atomics);
    }

    /**
     * Initialize BigInt constructor and static methods.
     */
    private void initializeBigIntConstructor() {
        // Create BigInt.prototype
        JSObject bigIntPrototype = context.createJSObject();
        IntrinsicFunctions.BIG_INT_PROTOTYPE.install(bigIntPrototype);
        defineToStringTag(bigIntPrototype, JSBigInt.NAME);

        // Create BigInt constructor
        JSNativeFunction bigIntConstructor = new JSNativeFunction(context, JSBigInt.NAME, 1, BigIntConstructor::call,
                true);
        linkConstructor(bigIntConstructor, bigIntPrototype, JSConstructorType.BIG_INT_OBJECT);

        // BigInt static methods
        defineMethod(bigIntConstructor, "asIntN", 2, BigIntConstructor::asIntN);
        defineMethod(bigIntConstructor, "asUintN", 2, BigIntConstructor::asUintN);

        defineBuiltin(globalObject, JSBigInt.NAME, bigIntConstructor);
    }

    /**
     * Initialize Boolean constructor and prototype.
     */
    private void initializeBooleanConstructor() {
        // Create Boolean.prototype as a Boolean object with [[BooleanData]] = false
        // Per QuickJS: JS_SetObjectData(ctx, ctx->class_proto[JS_CLASS_BOOLEAN], JS_NewBool(ctx, FALSE))
        JSBooleanObject booleanPrototype = new JSBooleanObject(context, false);
        context.transferPrototype(booleanPrototype, JSObject.NAME);
        defineMethod(booleanPrototype, "toString", 0, BooleanPrototype::toString);
        defineMethod(booleanPrototype, "valueOf", 0, BooleanPrototype::valueOf);

        // Create Boolean constructor
        JSNativeFunction booleanConstructor = new JSNativeFunction(context, JSBoolean.NAME, 1, BooleanConstructor::call,
                true);
        linkConstructor(booleanConstructor, booleanPrototype, JSConstructorType.BOOLEAN_OBJECT);

        defineBuiltin(globalObject, JSBoolean.NAME, booleanConstructor);
    }

    /**
     * Initialize console object with all console API methods.
     */
    private void initializeConsoleObject() {
        JSObject consoleObj = context.createJSObject();
        consoleObj.set("assert", new JSNativeFunction(context, "assert", 0, console::assert_));
        consoleObj.set("clear", new JSNativeFunction(context, "clear", 0, console::clear));
        consoleObj.set("count", new JSNativeFunction(context, "count", 0, console::count));
        consoleObj.set("countReset", new JSNativeFunction(context, "countReset", 0, console::countReset));
        consoleObj.set("debug", new JSNativeFunction(context, "debug", 0, console::debug));
        consoleObj.set("dir", new JSNativeFunction(context, "dir", 0, console::dir));
        consoleObj.set("dirxml", new JSNativeFunction(context, "dirxml", 0, console::dirxml));
        consoleObj.set("error", new JSNativeFunction(context, "error", 0, console::error));
        consoleObj.set("group", new JSNativeFunction(context, "group", 0, console::group));
        consoleObj.set("groupCollapsed", new JSNativeFunction(context, "groupCollapsed", 0, console::groupCollapsed));
        consoleObj.set("groupEnd", new JSNativeFunction(context, "groupEnd", 0, console::groupEnd));
        consoleObj.set("info", new JSNativeFunction(context, "info", 0, console::info));
        consoleObj.set("log", new JSNativeFunction(context, "log", 1, console::log));
        consoleObj.set("table", new JSNativeFunction(context, "table", 0, console::table));
        consoleObj.set("time", new JSNativeFunction(context, "time", 0, console::time));
        consoleObj.set("timeEnd", new JSNativeFunction(context, "timeEnd", 0, console::timeEnd));
        consoleObj.set("timeLog", new JSNativeFunction(context, "timeLog", 0, console::timeLog));
        consoleObj.set("trace", new JSNativeFunction(context, "trace", 0, console::trace));
        consoleObj.set("warn", new JSNativeFunction(context, "warn", 0, console::warn));

        defineBuiltin(globalObject, "console", consoleObj);
    }

    private void initializeDataViewConstructor() {
        JSObject prototype = context.createJSObject();
        IntrinsicFunctions.DATA_VIEW_ACCESSORS.install(prototype);
        defineToStringTag(prototype, JSDataView.NAME);
        IntrinsicFunctions.DATA_VIEW_METHODS.install(prototype);
        JSNativeFunction constructor = new JSNativeFunction(context, JSDataView.NAME, 1, DataViewConstructor::call,
                true, true);
        linkConstructor(constructor, prototype, JSConstructorType.DATA_VIEW);
        defineBuiltin(globalObject, JSDataView.NAME, constructor);
    }

    /**
     * Initialize Date constructor and prototype.
     */
    private void initializeDateConstructor() {
        JSObject datePrototype = context.createJSObject();
        JSNativeFunction toUTCString = new JSNativeFunction(context, "toUTCString", 0, DatePrototype::toUTCString);
        JSNativeFunction toPrimitive = new JSNativeFunction(context, "[Symbol.toPrimitive]", 1,
                DatePrototype::symbolToPrimitive);

        IntrinsicFunctions.DATE_PROTOTYPE.install(datePrototype);
        defineBuiltin(datePrototype, "toGMTString", toUTCString);
        IntrinsicFunctions.DATE_STRING_METHODS.install(datePrototype);
        defineBuiltin(datePrototype, "toUTCString", toUTCString);
        if (context.getRuntime().getOptions().isTemporalEnabled()) {
            defineMethod(datePrototype, "toTemporalInstant", 0, DatePrototype::toTemporalInstant);
        }
        defineMethod(datePrototype, "valueOf", 0, DatePrototype::valueOf);
        datePrototype.defineProperty(PropertyKey.SYMBOL_TO_PRIMITIVE, toPrimitive,
                PropertyDescriptor.DataState.Configurable);

        JSNativeFunction dateConstructor = new JSNativeFunction(context, JSDate.NAME, 7, DateConstructor::call, true);
        linkConstructor(dateConstructor, datePrototype, JSConstructorType.DATE);

        IntrinsicFunctions.DATE_CONSTRUCTOR.install(dateConstructor);

        defineBuiltin(globalObject, JSDate.NAME, dateConstructor);
    }

    /**
     * Initialize Error constructors.
     */
    private void initializeErrorConstructors() {
        // NativeError prototypes inherit from Error.prototype, so create Error first,
        // independently of the declaration order of JSErrorType.
        defineBuiltin(globalObject, JSError.NAME, JSErrorType.Error.create(context));
        for (JSErrorType type : JSErrorType.values()) {
            if (type != JSErrorType.Error) {
                defineBuiltin(globalObject, type.name(), type.create(context));
            }
        }
    }

    /**
     * Initialize Function constructor and prototype.
     */
    private void initializeFunctionConstructor() {
        // Create Function.prototype as a function (not a plain object)
        // According to ECMAScript spec, Function.prototype is itself a function
        // Use null name so toString() shows "function () {}" not "function anonymous() {}"
        JSNativeFunction functionPrototype = new JSNativeFunction(context, null, 0,
                (ctx, thisObj, args) -> JSUndefined.INSTANCE);
        // Remove the auto-created properties - Function.prototype has custom property descriptors
        functionPrototype.delete(PropertyKey.LENGTH);
        functionPrototype.delete(PropertyKey.NAME);
        functionPrototype.delete(PropertyKey.PROTOTYPE);

        IntrinsicFunctions.FUNCTION_PROTOTYPE.install(functionPrototype);

        String restrictedFunctionPropertyMessage = "'caller', 'callee', and 'arguments' properties may not be accessed on strict mode functions or the arguments objects for calls to them";

        // Shared %ThrowTypeError% intrinsic for strict arguments.callee and restricted accessors.
        JSNativeFunction throwTypeError = new JSNativeFunction(context, "", 0, (childContext, thisObj, args) -> {
            return childContext.throwTypeError(restrictedFunctionPropertyMessage);
        });
        // Per ES spec 10.2.4: %ThrowTypeError% is frozen with non-configurable length and name.
        throwTypeError.defineProperty(PropertyKey.LENGTH,
                PropertyDescriptor.dataDescriptor(JSNumber.of(0), PropertyDescriptor.DataState.None));
        throwTypeError.defineProperty(PropertyKey.NAME,
                PropertyDescriptor.dataDescriptor(new JSString(""), PropertyDescriptor.DataState.None));
        throwTypeError.preventExtensions();
        // Store as the %ThrowTypeError% intrinsic for sharing with strict arguments.callee
        context.setThrowTypeErrorIntrinsic(throwTypeError);

        functionPrototype.defineProperty(PropertyKey.fromString("arguments"), throwTypeError, throwTypeError,
                PropertyDescriptor.AccessorState.Configurable);
        functionPrototype.defineProperty(PropertyKey.fromString("caller"), throwTypeError, throwTypeError,
                PropertyDescriptor.AccessorState.Configurable);

        // Function.prototype[Symbol.hasInstance] - implements OrdinaryHasInstance
        // Per ES spec 19.2.3.6: writable: false, enumerable: false, configurable: false
        JSNativeFunction hasInstanceFunc = new JSNativeFunction(context, "[Symbol.hasInstance]", 1,
                FunctionPrototype::symbolHasInstance);
        functionPrototype.defineProperty(PropertyKey.SYMBOL_HAS_INSTANCE,
                PropertyDescriptor.dataDescriptor(hasInstanceFunc, PropertyDescriptor.DataState.None));

        // Add 'length' and 'name' data properties
        functionPrototype.defineProperty(PropertyKey.LENGTH, JSNumber.of(0), PropertyDescriptor.DataState.Configurable);
        functionPrototype.defineProperty(PropertyKey.NAME, new JSString(""), PropertyDescriptor.DataState.Configurable);

        // Per ECMAScript spec, Function.prototype's [[Prototype]] is Object.prototype
        context.transferPrototype(functionPrototype, JSObject.NAME);

        // Function constructor should be a function, not a plain object
        JSNativeFunction functionConstructor = new JSNativeFunction(context, JSFunction.NAME, 1,
                FunctionConstructor::call, true);
        linkConstructor(functionConstructor, functionPrototype);

        defineBuiltin(globalObject, JSFunction.NAME, functionConstructor);
    }

    /**
     * Recursively initialize prototype chains for all JSFunction instances in the global object. This must be called
     * after Function.prototype is set up.
     */
    private void initializeFunctionPrototypeChains(JSObject obj, Set<JSObject> visitedObjectSet) {
        // This ensures all JSFunction instances inherit from Function.prototype
        // Avoid infinite recursion by tracking visited objects
        if (visitedObjectSet.contains(obj)) {
            return;
        }
        visitedObjectSet.add(obj);

        List<PropertyKey> keys = obj.getOwnPropertyKeys();
        for (PropertyKey key : keys) {
            PropertyDescriptor descriptor = obj.getOwnPropertyDescriptor(key);
            if (descriptor == null) {
                continue;
            }

            // Walk data descriptor values using the current slot value.
            // Do not read descriptor.getValue() because data descriptors in this runtime
            // are not updated when writable properties are reassigned.
            if (descriptor.isDataDescriptor()) {
                initializeFunctionPrototypeChainsForValue(obj.get(key), visitedObjectSet);
            }
            // Walk accessor descriptor functions.
            if (descriptor.hasGetter()) {
                initializeFunctionPrototypeChainsForValue(descriptor.getGetter(), visitedObjectSet);
            }
            if (descriptor.hasSetter()) {
                initializeFunctionPrototypeChainsForValue(descriptor.getSetter(), visitedObjectSet);
            }
        }

        // Also walk the [[Prototype]] chain to reach functions on non-global intrinsics
        // (e.g., %TypedArray% which is the prototype of typed array constructors)
        JSObject proto = obj.getPrototype();
        if (proto != null) {
            initializeFunctionPrototypeChains(proto, visitedObjectSet);
        }
    }

    private void initializeFunctionPrototypeChainsForValue(JSValue value, Set<JSObject> visitedObjectSet) {
        if (value instanceof JSFunction func) {
            if (func.getPrototype() == null) {
                func.initializePrototypeChain(context);
            }
            initializeFunctionPrototypeChains(func, visitedObjectSet);
        } else if (value instanceof JSObject subObj) {
            initializeFunctionPrototypeChains(subObj, visitedObjectSet);
        }
    }

    /**
     * Initialize Generator and GeneratorFunction prototypes. Based on QuickJS JS_AddIntrinsicGenerator.
     * <p>
     * Prototype chain: - Generator.prototype inherits from Iterator.prototype - GeneratorFunction.prototype inherits
     * from Function.prototype - GeneratorFunction.prototype.prototype = Generator.prototype (configurable) -
     * Generator.prototype.constructor = GeneratorFunction.prototype (configurable)
     */
    private void initializeGeneratorPrototype() {
        // Get Iterator.prototype for Generator.prototype to inherit from
        JSObject iteratorConstructor = (JSObject) globalObject.get(JSIterator.NAME);
        JSObject iteratorPrototype = (JSObject) iteratorConstructor.get(PropertyKey.PROTOTYPE);

        // Create Generator.prototype inheriting from Iterator.prototype
        // This gives generators Symbol.iterator automatically
        JSObject generatorPrototype = context.createJSObject();
        generatorPrototype.setPrototype(iteratorPrototype);

        // Generator.prototype methods: writable+configurable (not enumerable)
        // Matches QuickJS js_generator_proto_funcs using JS_ITERATOR_NEXT_DEF
        IntrinsicFunctions.GENERATOR_PROTOTYPE.install(generatorPrototype);

        // Symbol.toStringTag = "Generator" (configurable only)
        // Matches QuickJS JS_PROP_STRING_DEF("[Symbol.toStringTag]", "Generator", JS_PROP_CONFIGURABLE)
        defineToStringTag(generatorPrototype, "Generator");

        context.setGeneratorFunctionPrototype(createGeneratorFunctionPrototype(generatorPrototype, "GeneratorFunction",
                FunctionConstructor::callGenerator, true));
    }

    private void initializeGlobalObject() {
        // Global value properties (non-writable, non-enumerable, non-configurable)
        globalObject.defineProperty(PropertyKey.fromString("Infinity"), JSNumber.of(Double.POSITIVE_INFINITY),
                PropertyDescriptor.DataState.None);
        globalObject.defineProperty(PropertyKey.fromString("NaN"), JSNumber.of(Double.NaN),
                PropertyDescriptor.DataState.None);
        globalObject.defineProperty(PropertyKey.fromString("undefined"), JSUndefined.INSTANCE,
                PropertyDescriptor.DataState.None);

        // Global function properties
        // Capture the realm context so that eval code runs in the correct realm
        // even when called cross-realm (e.g., other.eval('code')).
        final JSContext realmContext = context;
        globalObject.defineProperty(PropertyKey.fromString("eval"),
                new JSNativeFunction(context, "eval", 1,
                        (callerContext, thisArg, args) -> GlobalFunction.eval(realmContext, callerContext, args)),
                PropertyDescriptor.DataState.ConfigurableWritable);
        IntrinsicFunctions.GLOBAL_OBJECT.install(globalObject);

        // Console object for debugging
        initializeConsoleObject();

        // Global this reference
        defineBuiltin(globalObject, "globalThis", globalObject);

        // Object.prototype.toString.call(globalThis) -> [object global]
        defineToStringTag(globalObject, "global");
    }

    /**
     * Initialize Intl object.
     */
    private void initializeIntlObject() {
        JSObject intlObject = context.createJSObject();
        defineToStringTag(intlObject, "Intl");
        defineMethod(intlObject, "getCanonicalLocales", 1, JSIntlObject::getCanonicalLocales);
        defineMethod(intlObject, "supportedValuesOf", 1, JSIntlObject::supportedValuesOf_Intl);

        JSObject dateTimeFormatPrototype = context.createJSObject();
        IntrinsicFunctions.DATE_TIME_FORMAT_PROTOTYPE.install(dateTimeFormatPrototype);
        defineToStringTag(dateTimeFormatPrototype, "Intl.DateTimeFormat");
        installIntlConstructor(intlObject, "DateTimeFormat", 0, dateTimeFormatPrototype, false, (childContext, thisArg,
                args) -> JSIntlObject.createDateTimeFormat(childContext, dateTimeFormatPrototype, args));

        JSObject displayNamesPrototype = context.createJSObject();
        defineMethod(displayNamesPrototype, "of", 1, JSIntlObject::displayNamesOf);
        defineMethod(displayNamesPrototype, "resolvedOptions", 0, JSIntlObject::displayNamesResolvedOptions);
        defineToStringTag(displayNamesPrototype, "Intl.DisplayNames");
        installIntlConstructor(intlObject, "DisplayNames", 2, displayNamesPrototype, true, (childContext, thisArg,
                args) -> JSIntlObject.createDisplayNames(childContext, displayNamesPrototype, args));

        JSObject durationFormatPrototype = context.createJSObject();
        IntrinsicFunctions.DURATION_FORMAT_PROTOTYPE.install(durationFormatPrototype);
        defineToStringTag(durationFormatPrototype, "Intl.DurationFormat");
        installIntlConstructor(intlObject, "DurationFormat", 0, durationFormatPrototype, true, (childContext, thisArg,
                args) -> JSIntlObject.createDurationFormat(childContext, durationFormatPrototype, args));

        JSObject numberFormatPrototype = context.createJSObject();
        IntrinsicFunctions.NUMBER_FORMAT_PROTOTYPE.install(numberFormatPrototype);
        defineToStringTag(numberFormatPrototype, "Intl.NumberFormat");
        installIntlConstructor(intlObject, "NumberFormat", 0, numberFormatPrototype, false, (childContext, thisArg,
                args) -> JSIntlObject.createNumberFormat(childContext, numberFormatPrototype, args));

        JSObject collatorPrototype = context.createJSObject();
        defineGetter(collatorPrototype, "compare", JSIntlObject::collatorCompareGetter);
        defineMethod(collatorPrototype, "resolvedOptions", 0, JSIntlObject::collatorResolvedOptions);
        defineToStringTag(collatorPrototype, "Intl.Collator");
        installIntlConstructor(intlObject, "Collator", 0, collatorPrototype, false,
                (childContext, thisArg, args) -> JSIntlObject.createCollator(childContext, collatorPrototype, args));

        JSObject pluralRulesPrototype = context.createJSObject();
        IntrinsicFunctions.PLURAL_RULES_PROTOTYPE.install(pluralRulesPrototype);
        defineToStringTag(pluralRulesPrototype, "Intl.PluralRules");
        installIntlConstructor(intlObject, "PluralRules", 0, pluralRulesPrototype, true, (childContext, thisArg,
                args) -> JSIntlObject.createPluralRules(childContext, pluralRulesPrototype, args));

        JSObject relativeTimeFormatPrototype = context.createJSObject();
        IntrinsicFunctions.RELATIVE_TIME_FORMAT_PROTOTYPE.install(relativeTimeFormatPrototype);
        defineToStringTag(relativeTimeFormatPrototype, "Intl.RelativeTimeFormat");
        installIntlConstructor(intlObject, "RelativeTimeFormat", 0, relativeTimeFormatPrototype, true,
                (childContext, thisArg, args) -> JSIntlObject.createRelativeTimeFormat(childContext,
                        relativeTimeFormatPrototype, args));

        JSObject segmentDataPrototype = context.createJSObject();
        JSNativeFunction segmentsContainingFunction = new JSNativeFunction(context, "containing", 1,
                JSIntlObject::segmentsContaining);
        segmentsContainingFunction.initializePrototypeChain(context);
        defineBuiltin(segmentDataPrototype, "containing", segmentsContainingFunction);
        JSNativeFunction segmentsIteratorFunction = new JSNativeFunction(context, "[Symbol.iterator]", 0,
                JSIntlObject::segmentsIterator);
        segmentsIteratorFunction.initializePrototypeChain(context);
        segmentDataPrototype.defineProperty(PropertyKey.SYMBOL_ITERATOR, segmentsIteratorFunction,
                PropertyDescriptor.DataState.ConfigurableWritable);
        JSObject segmenterPrototype = context.createJSObject();
        defineMethod(segmenterPrototype, "segment", 1, JSIntlObject::segmenterSegment);
        defineMethod(segmenterPrototype, "resolvedOptions", 0, JSIntlObject::segmenterResolvedOptions);
        defineToStringTag(segmenterPrototype, "Intl.Segmenter");
        installIntlConstructor(intlObject, "Segmenter", 0, segmenterPrototype, true, (childContext, thisArg,
                args) -> JSIntlObject.createSegmenter(childContext, segmenterPrototype, segmentDataPrototype, args));

        JSObject listFormatPrototype = context.createJSObject();
        IntrinsicFunctions.LIST_FORMAT_PROTOTYPE.install(listFormatPrototype);
        defineToStringTag(listFormatPrototype, "Intl.ListFormat");
        installIntlConstructor(intlObject, "ListFormat", 0, listFormatPrototype, true, (childContext, thisArg,
                args) -> JSIntlObject.createListFormat(childContext, listFormatPrototype, args));

        JSObject localePrototype = context.createJSObject();
        IntrinsicFunctions.LOCALE_PROTOTYPE.install(localePrototype);
        defineToStringTag(localePrototype, "Intl.Locale");
        JSNativeFunction localeConstructor = new JSNativeFunction(context, "Locale", 1,
                (childContext, thisArg, args) -> JSIntlObject.createLocale(childContext, localePrototype, args), true,
                true);
        linkConstructor(localeConstructor, localePrototype);
        defineBuiltin(intlObject, "Locale", localeConstructor);

        defineBuiltin(globalObject, "Intl", intlObject);
    }

    /**
     * Initialize Iterator constructor and prototype. Based on ECMAScript 2024 Iterator specification.
     */
    private void initializeIteratorConstructor() {
        JSObject iteratorPrototype = context.createJSObject();

        IntrinsicFunctions.ITERATOR_PROTOTYPE.install(iteratorPrototype);
        iteratorPrototype.defineProperty(PropertyKey.SYMBOL_ITERATOR,
                new JSNativeFunction(context, "[Symbol.iterator]", 0, (childContext, thisArg, args) -> thisArg),
                PropertyDescriptor.DataState.ConfigurableWritable);

        // Iterator.prototype[Symbol.dispose] - calls this.return() per explicit-resource-management spec
        iteratorPrototype.defineProperty(PropertyKey.SYMBOL_DISPOSE,
                new JSNativeFunction(context, "[Symbol.dispose]", 0, (childContext, thisArg, args) -> {
                    if (!(thisArg instanceof JSObject thisObject)) {
                        return childContext.throwTypeError("not an object");
                    }
                    JSValue returnMethod = thisObject.get(PropertyKey.RETURN);
                    if (childContext.hasPendingException()) {
                        return childContext.getPendingException();
                    }
                    if (returnMethod instanceof JSUndefined || returnMethod instanceof JSNull) {
                        return JSUndefined.INSTANCE;
                    }
                    if (!(returnMethod instanceof JSFunction returnFunction)) {
                        return childContext.throwTypeError("not a function");
                    }
                    return returnFunction.call(childContext, thisObject, JSValue.NO_ARGS);
                }), PropertyDescriptor.DataState.ConfigurableWritable);

        JSNativeFunction iteratorConstructor = new JSNativeFunction(context, JSIterator.NAME, 0,
                IteratorConstructor::call, true, true);
        iteratorConstructor.defineProperty(PropertyKey.PROTOTYPE,
                PropertyDescriptor.dataDescriptor(iteratorPrototype, PropertyDescriptor.DataState.None));
        IntrinsicFunctions.ITERATOR_CONSTRUCTOR.install(iteratorConstructor);

        JSNativeFunction constructorAccessor = new JSNativeFunction(context, "constructor", 0,
                (childContext, thisArg, args) -> {
                    if (args.length > 0) {
                        if (!(args[0] instanceof JSObject valueObject)) {
                            return childContext.throwTypeError("not an object");
                        }
                        if (!(thisArg instanceof JSObject thisObject)) {
                            return childContext.throwTypeError("not an object");
                        }
                        thisObject.defineProperty(PropertyKey.CONSTRUCTOR, PropertyDescriptor
                                .dataDescriptor(valueObject, PropertyDescriptor.DataState.ConfigurableWritable));
                        return JSUndefined.INSTANCE;
                    }
                    return iteratorConstructor;
                });
        iteratorPrototype.defineProperty(PropertyKey.CONSTRUCTOR, PropertyDescriptor.accessorDescriptor(
                constructorAccessor, constructorAccessor, PropertyDescriptor.AccessorState.Configurable));

        JSNativeFunction toStringTagGetter = new JSNativeFunction(context, "get [Symbol.toStringTag]", 0,
                (childContext, thisArg, args) -> new JSString(JSIterator.NAME), false);
        JSNativeFunction toStringTagSetter = new JSNativeFunction(context, "set [Symbol.toStringTag]", 1,
                (childContext, thisArg, args) -> {
                    if (!(thisArg instanceof JSObject thisObject)) {
                        return childContext.throwTypeError("not an object");
                    }
                    if (thisObject == iteratorPrototype) {
                        return childContext.throwTypeError("Cannot assign to read only property");
                    }
                    JSValue value = JSValue.argumentOrUndefined(args, 0);
                    PropertyKey toStringTagKey = PropertyKey.SYMBOL_TO_STRING_TAG;
                    PropertyDescriptor descriptor = thisObject.getOwnPropertyDescriptor(toStringTagKey);
                    if (descriptor != null) {
                        thisObject.set(toStringTagKey, value);
                    } else {
                        thisObject.defineProperty(toStringTagKey,
                                PropertyDescriptor.dataDescriptor(value, PropertyDescriptor.DataState.All));
                    }
                    return JSUndefined.INSTANCE;
                }, false);
        iteratorPrototype.defineProperty(PropertyKey.SYMBOL_TO_STRING_TAG, PropertyDescriptor.accessorDescriptor(
                toStringTagGetter, toStringTagSetter, PropertyDescriptor.AccessorState.Configurable));

        // Create shared iterator-type-specific prototypes inheriting from Iterator.prototype
        // Each has its own 'next' (writable, configurable) and Symbol.toStringTag per ES2024 spec
        for (String tag : new String[]{"Array Iterator", "Map Iterator", "Set Iterator", "String Iterator"}) {
            JSObject proto = new JSObject(context);
            proto.setPrototype(iteratorPrototype);
            defineMethod(proto, "next", 0, IteratorPrototype::next);
            proto.defineProperty(PropertyKey.SYMBOL_TO_STRING_TAG,
                    PropertyDescriptor.dataDescriptor(new JSString(tag), PropertyDescriptor.DataState.Configurable));
            context.registerIteratorPrototype(tag, proto);
        }

        JSObject iteratorHelperPrototype = new JSObject(context);
        iteratorHelperPrototype.setPrototype(iteratorPrototype);
        defineMethod(iteratorHelperPrototype, "next", 0, IteratorPrototype::wrapOrHelperNext);
        defineMethod(iteratorHelperPrototype, "return", 0, IteratorPrototype::wrapOrHelperReturn);
        iteratorHelperPrototype.defineProperty(PropertyKey.SYMBOL_TO_STRING_TAG, PropertyDescriptor
                .dataDescriptor(new JSString("Iterator Helper"), PropertyDescriptor.DataState.Configurable));
        context.registerIteratorPrototype("Iterator Helper", iteratorHelperPrototype);

        JSObject regExpStringIteratorPrototype = new JSObject(context);
        regExpStringIteratorPrototype.setPrototype(iteratorPrototype);
        defineMethod(regExpStringIteratorPrototype, "next", 0, RegExpPrototype::regExpStringIteratorNext);
        regExpStringIteratorPrototype.defineProperty(PropertyKey.SYMBOL_TO_STRING_TAG, PropertyDescriptor
                .dataDescriptor(new JSString("RegExp String Iterator"), PropertyDescriptor.DataState.Configurable));
        context.registerIteratorPrototype("RegExp String Iterator", regExpStringIteratorPrototype);

        // Create %WrapForValidIteratorPrototype% for Iterator.from wrapper objects
        // This prototype inherits from Iterator.prototype and provides next/return methods
        JSObject wrapForValidIteratorPrototype = new JSObject(context);
        wrapForValidIteratorPrototype.setPrototype(iteratorPrototype);
        defineMethod(wrapForValidIteratorPrototype, "next", 0, IteratorPrototype::wrapOrHelperNext);
        defineMethod(wrapForValidIteratorPrototype, "return", 0, IteratorPrototype::wrapOrHelperReturn);
        context.registerIteratorPrototype("Iterator Wrap", wrapForValidIteratorPrototype);

        defineBuiltin(globalObject, JSIterator.NAME, iteratorConstructor);
    }

    /**
     * Initialize JSON object.
     */
    private void initializeJSONObject() {
        JSObject json = context.createJSObject();
        json.defineProperty(PropertyKey.fromString("parse"),
                new JSNativeFunction(context, "parse", 2,
                        (ignoredContext, thisArg, args) -> jsonObject.parse(thisArg, args)),
                PropertyDescriptor.DataState.ConfigurableWritable);
        json.defineProperty(PropertyKey.fromString("stringify"),
                new JSNativeFunction(context, "stringify", 3,
                        (ignoredContext, thisArg, args) -> jsonObject.stringify(thisArg, args)),
                PropertyDescriptor.DataState.ConfigurableWritable);
        json.defineProperty(PropertyKey.fromString("rawJSON"),
                new JSNativeFunction(context, "rawJSON", 1,
                        (ignoredContext, thisArg, args) -> jsonObject.rawJSON(thisArg, args)),
                PropertyDescriptor.DataState.ConfigurableWritable);
        json.defineProperty(PropertyKey.fromString("isRawJSON"),
                new JSNativeFunction(context, "isRawJSON", 1,
                        (ignoredContext, thisArg, args) -> jsonObject.isRawJSON(thisArg, args)),
                PropertyDescriptor.DataState.ConfigurableWritable);
        defineToStringTag(json, "JSON");

        defineBuiltin(globalObject, "JSON", json);
    }

    /**
     * Initialize Math object.
     */
    private void initializeMathObject() {
        JSObject math = context.createJSObject();

        // Math constants
        math.defineProperty(PropertyKey.fromString("E"), JSNumber.of(MathObject.E), PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("LN10"), JSNumber.of(MathObject.LN10),
                PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("LN2"), JSNumber.of(MathObject.LN2),
                PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("LOG10E"), JSNumber.of(MathObject.LOG10E),
                PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("LOG2E"), JSNumber.of(MathObject.LOG2E),
                PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("PI"), JSNumber.of(MathObject.PI),
                PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("SQRT1_2"), JSNumber.of(MathObject.SQRT1_2),
                PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("SQRT2"), JSNumber.of(MathObject.SQRT2),
                PropertyDescriptor.DataState.None);

        // Math methods
        IntrinsicFunctions.MATH.install(math);
        defineToStringTag(math, "Math");

        defineBuiltin(globalObject, "Math", math);
    }

    /**
     * Set [[Prototype]] of each NativeError constructor to the Error constructor. Per ES2024 20.5.6.2: The value of the
     * [[Prototype]] internal slot of a NativeError constructor is the Error constructor.
     */
    private void initializeNativeErrorPrototypeChains() {
        JSValue errorConstructor = globalObject.get(JSError.NAME);
        if (!(errorConstructor instanceof JSObject errorObj)) {
            return;
        }
        for (JSErrorType type : JSErrorType.values()) {
            if (type == JSErrorType.Error) {
                continue;
            }
            JSValue constructor = globalObject.get(type.name());
            if (constructor instanceof JSObject constructorObj) {
                constructorObj.setPrototype(errorObj);
            }
        }
    }

    /**
     * Initialize Number constructor and prototype.
     */
    private void initializeNumberConstructor() {
        // Create Number.prototype as a Number object with [[NumberData]] = +0 (ES2024 20.1.3)
        JSNumberObject numberPrototype = new JSNumberObject(context, 0.0);
        context.transferPrototype(numberPrototype, JSObject.NAME);
        IntrinsicFunctions.NUMBER_PROTOTYPE.install(numberPrototype);

        // Create Number constructor
        JSNativeFunction numberConstructor = new JSNativeFunction(context, JSNumberObject.NAME, 1,
                NumberConstructor::call, true);
        linkConstructor(numberConstructor, numberPrototype, JSConstructorType.NUMBER_OBJECT);

        // Number static methods
        IntrinsicFunctions.NUMBER_CONSTRUCTOR.install(numberConstructor);

        // QuickJS compatibility: Number.parseInt/parseFloat are aliases of global parseInt/parseFloat.
        JSValue globalParseInt = globalObject.get("parseInt");
        JSValue globalParseFloat = globalObject.get("parseFloat");
        numberConstructor.defineProperty(PropertyKey.fromString("EPSILON"), JSNumber.of(Math.ulp(1.0)),
                PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("MAX_SAFE_INTEGER"),
                JSNumber.of((double) NumberPrototype.MAX_SAFE_INTEGER), PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("MAX_VALUE"), JSNumber.of(Double.MAX_VALUE),
                PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("MIN_SAFE_INTEGER"),
                JSNumber.of((double) -NumberPrototype.MAX_SAFE_INTEGER), PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("MIN_VALUE"), JSNumber.of(Double.MIN_VALUE),
                PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("NEGATIVE_INFINITY"),
                JSNumber.of(Double.NEGATIVE_INFINITY), PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("NaN"), JSNumber.of(Double.NaN),
                PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("POSITIVE_INFINITY"),
                JSNumber.of(Double.POSITIVE_INFINITY), PropertyDescriptor.DataState.None);
        defineBuiltin(numberConstructor, "parseFloat", globalParseFloat);
        defineBuiltin(numberConstructor, "parseInt", globalParseInt);

        defineBuiltin(globalObject, JSNumberObject.NAME, numberConstructor);
    }

    /**
     * Initialize Object constructor and static methods.
     */
    private void initializeObjectConstructor() {
        // Create Object.prototype
        JSObject objectPrototype = context.createJSObject();
        IntrinsicFunctions.OBJECT_PROTOTYPE.install(objectPrototype);

        // Define __proto__ as an accessor property
        PropertyDescriptor protoDesc = PropertyDescriptor.accessorDescriptor(
                new JSNativeFunction(context, "get __proto__", 0, ObjectPrototype::__proto__Getter),
                new JSNativeFunction(context, "set __proto__", 1, ObjectPrototype::__proto__Setter),
                PropertyDescriptor.AccessorState.Configurable);
        objectPrototype.defineProperty(PropertyKey.PROTO, protoDesc);

        // Object.prototype is an immutable prototype exotic object per ES2024 9.4.7
        objectPrototype.setImmutablePrototype();

        // Create Object constructor
        JSNativeFunction objectConstructor = new JSNativeFunction(context, JSObject.NAME, 1, ObjectConstructor::call,
                true);
        linkConstructor(objectConstructor, objectPrototype);

        // Object static methods
        IntrinsicFunctions.OBJECT_CONSTRUCTOR.install(objectConstructor);

        defineBuiltin(globalObject, JSObject.NAME, objectConstructor);
    }

    /**
     * Initialize Promise constructor and prototype methods.
     */
    private void initializePromiseConstructor() {
        // Create Promise.prototype
        JSObject promisePrototype = context.createJSObject();
        IntrinsicFunctions.PROMISE_PROTOTYPE.install(promisePrototype);
        defineToStringTag(promisePrototype, JSPromise.NAME);

        // Create Promise constructor as JSNativeFunction
        JSNativeFunction promiseConstructor = new JSNativeFunction(context, JSPromise.NAME, 1, PromiseConstructor::call,
                true, true);
        context.transferPrototype(promiseConstructor, JSFunction.NAME);
        linkConstructor(promiseConstructor, promisePrototype, JSConstructorType.PROMISE);

        // Static methods
        IntrinsicFunctions.PROMISE_CONSTRUCTOR.install(promiseConstructor);
        defineSpeciesGetter(promiseConstructor, PromiseConstructor::getSpecies);

        defineBuiltin(globalObject, JSPromise.NAME, promiseConstructor);
    }

    /**
     * Initialize Proxy constructor.
     */
    private void initializeProxyConstructor() {
        // Create Proxy constructor as JSNativeFunction
        // Proxy requires 'new' and takes 2 arguments (target, handler)
        JSNativeFunction proxyConstructor = new JSNativeFunction(context, JSProxy.NAME, 2, ProxyConstructor::call, true,
                true);
        proxyConstructor.setConstructorType(JSConstructorType.PROXY);
        context.transferPrototype(proxyConstructor, JSFunction.NAME);

        // Proxy has no "prototype" own property per QuickJS / spec.
        proxyConstructor.delete(PropertyKey.PROTOTYPE);

        // Add static methods.
        defineMethod(proxyConstructor, "revocable", 2, ProxyConstructor::revocable);

        defineBuiltin(globalObject, JSProxy.NAME, proxyConstructor);
    }

    /**
     * Initialize Reflect object.
     */
    private void initializeReflectObject() {
        JSObject reflect = context.createJSObject();
        IntrinsicFunctions.REFLECT.install(reflect);
        defineToStringTag(reflect, "Reflect");

        defineBuiltin(globalObject, "Reflect", reflect);
    }

    /**
     * Initialize RegExp constructor and prototype.
     */
    private void initializeRegExpConstructor() {
        // Create RegExp.prototype
        JSObject regexpPrototype = context.createJSObject();
        defineMethod(regexpPrototype, "test", 1, RegExpPrototype::test);
        regexpPrototype.defineProperty(PropertyKey.EXEC,
                new JSNativeFunction(context, "exec", 1, RegExpPrototype::exec),
                PropertyDescriptor.DataState.ConfigurableWritable);
        defineMethod(regexpPrototype, "compile", 2, RegExpPrototype::compile);
        defineMethod(regexpPrototype, "toString", 0, RegExpPrototype::toStringMethod);
        regexpPrototype.defineProperty(PropertyKey.SYMBOL_SPLIT,
                new JSNativeFunction(context, "[Symbol.split]", 2, RegExpPrototype::symbolSplit),
                PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.SYMBOL_MATCH,
                new JSNativeFunction(context, "[Symbol.match]", 1, RegExpPrototype::symbolMatch),
                PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.SYMBOL_MATCH_ALL,
                new JSNativeFunction(context, "[Symbol.matchAll]", 1, RegExpPrototype::symbolMatchAll),
                PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.SYMBOL_REPLACE,
                new JSNativeFunction(context, "[Symbol.replace]", 2, RegExpPrototype::symbolReplace),
                PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.SYMBOL_SEARCH,
                new JSNativeFunction(context, "[Symbol.search]", 1, RegExpPrototype::symbolSearch),
                PropertyDescriptor.DataState.ConfigurableWritable);

        // Accessor properties
        IntrinsicFunctions.REGEXP_PROTOTYPE.install(regexpPrototype);

        // Create RegExp constructor as a function
        JSNativeFunction regexpConstructor = new JSNativeFunction(context, JSRegExp.NAME, 2, RegExpConstructor::call,
                true);
        linkConstructor(regexpConstructor, regexpPrototype, JSConstructorType.REGEXP);

        // AnnexB legacy RegExp static accessor properties.
        // Each getter validates SameValue(C, thisValue) per GetLegacyRegExpStaticProperty.
        for (String legacyGetterName : new String[]{"$&", "$'", "$+", "$`", "lastMatch", "lastParen", "leftContext",
                "rightContext"}) {
            JSNativeFunction legacyGetter = new JSNativeFunction(context, "get " + legacyGetterName, 0,
                    (ctx, thisArg, args) -> {
                        if (thisArg != regexpConstructor) {
                            return ctx.throwTypeError("Generic static accessor property access is not supported");
                        }
                        String legacyValue;
                        if ("$&".equals(legacyGetterName) || "lastMatch".equals(legacyGetterName)) {
                            legacyValue = ctx.getRegExpLegacyLastMatch();
                        } else if ("$'".equals(legacyGetterName) || "rightContext".equals(legacyGetterName)) {
                            legacyValue = ctx.getRegExpLegacyRightContext();
                        } else if ("$+".equals(legacyGetterName) || "lastParen".equals(legacyGetterName)) {
                            legacyValue = ctx.getRegExpLegacyLastParen();
                        } else if ("$`".equals(legacyGetterName) || "leftContext".equals(legacyGetterName)) {
                            legacyValue = ctx.getRegExpLegacyLeftContext();
                        } else {
                            legacyValue = "";
                        }
                        return new JSString(legacyValue);
                    }, false);
            regexpConstructor.defineProperty(PropertyKey.fromString(legacyGetterName), legacyGetter,
                    PropertyDescriptor.AccessorState.Configurable);
        }
        for (String legacyGetterSetterName : new String[]{"$_", "input"}) {
            JSNativeFunction legacyGSGetter = new JSNativeFunction(context, "get " + legacyGetterSetterName, 0,
                    (ctx, thisArg, args) -> {
                        if (thisArg != regexpConstructor) {
                            return ctx.throwTypeError("Generic static accessor property access is not supported");
                        }
                        return new JSString(ctx.getRegExpLegacyInput());
                    }, false);
            JSNativeFunction legacyGSSetter = new JSNativeFunction(context, "set " + legacyGetterSetterName, 1,
                    (ctx, thisArg, args) -> {
                        if (thisArg != regexpConstructor) {
                            return ctx.throwTypeError("Generic static accessor property access is not supported");
                        }
                        JSValue inputArgumentValue = JSValue.argumentOrUndefined(args, 0);
                        JSString inputString = JSTypeConversions.toString(ctx, inputArgumentValue);
                        if (ctx.hasPendingException()) {
                            return JSUndefined.INSTANCE;
                        }
                        ctx.setRegExpLegacyInput(inputString.value());
                        return JSUndefined.INSTANCE;
                    }, false);
            regexpConstructor.defineProperty(PropertyKey.fromString(legacyGetterSetterName), legacyGSGetter,
                    legacyGSSetter, PropertyDescriptor.AccessorState.Configurable);
        }
        // $1..$9
        for (int i = 1; i <= 9; i++) {
            final String dollarName = "$" + i;
            final int captureIndex = i;
            JSNativeFunction dollarGetter = new JSNativeFunction(context, "get " + dollarName, 0,
                    (ctx, thisArg, args) -> {
                        if (thisArg != regexpConstructor) {
                            return ctx.throwTypeError("Generic static accessor property access is not supported");
                        }
                        return new JSString(ctx.getRegExpLegacyCapture(captureIndex));
                    }, false);
            regexpConstructor.defineProperty(PropertyKey.fromString(dollarName), dollarGetter,
                    PropertyDescriptor.AccessorState.Configurable);
        }

        // Symbol.species getter - ES2024 22.2.4.2 get RegExp[@@species]
        defineSpeciesGetter(regexpConstructor, RegExpConstructor::getSpecies);
        // ES2024 RegExp.escape static method
        defineMethod(regexpConstructor, "escape", 1, RegExpConstructor::escape);

        defineBuiltin(globalObject, JSRegExp.NAME, regexpConstructor);
    }

    /**
     * Initialize String constructor and prototype.
     */
    private void initializeStringConstructor() {
        // Create String.prototype - per ES spec, it is itself a String object whose value is ""
        JSObject stringPrototype = new JSStringObject(context);
        context.transferPrototype(stringPrototype, JSObject.NAME);
        IntrinsicFunctions.STRING_PROTOTYPE.install(stringPrototype);
        JSNativeFunction trimEnd = new JSNativeFunction(context, "trimEnd", 0, StringPrototype::trimEnd);
        JSNativeFunction trimStart = new JSNativeFunction(context, "trimStart", 0, StringPrototype::trimStart);
        defineBuiltin(stringPrototype, "trimEnd", trimEnd);
        defineBuiltin(stringPrototype, "trimRight", trimEnd);
        defineBuiltin(stringPrototype, "trimStart", trimStart);
        defineBuiltin(stringPrototype, "trimLeft", trimStart);
        IntrinsicFunctions.STRING_PROTOTYPE_AFTER_TRIM.install(stringPrototype);

        // String.prototype[Symbol.iterator]
        stringPrototype.defineProperty(PropertyKey.SYMBOL_ITERATOR,
                new JSNativeFunction(context, "[Symbol.iterator]", 0, IteratorPrototype::stringIterator),
                PropertyDescriptor.DataState.ConfigurableWritable);

        // String.prototype.length is a data property with value 0 (not writable, not enumerable, not configurable)
        stringPrototype.defineProperty(PropertyKey.LENGTH, JSNumber.of(0), PropertyDescriptor.DataState.None);

        // Create String constructor
        JSNativeFunction stringConstructor = new JSNativeFunction(context, JSString.NAME, 1, StringConstructor::call,
                true);
        linkConstructor(stringConstructor, stringPrototype, JSConstructorType.STRING_OBJECT);

        // Add static methods
        IntrinsicFunctions.STRING_CONSTRUCTOR.install(stringConstructor);

        defineBuiltin(globalObject, JSString.NAME, stringConstructor);
    }

    /**
     * Initialize Symbol constructor and static methods.
     */
    private void initializeSymbolConstructor() {
        // Create Symbol.prototype that inherits from Object.prototype
        JSObject symbolPrototype = context.createJSObject();
        context.transferPrototype(symbolPrototype, JSObject.NAME);

        JSNativeFunction symbolToString = new JSNativeFunction(context, "toString", 0, SymbolPrototype::toString);
        symbolToString.initializePrototypeChain(context);
        symbolPrototype.defineProperty(PropertyKey.TO_STRING, symbolToString,
                PropertyDescriptor.DataState.ConfigurableWritable);

        JSNativeFunction symbolValueOf = new JSNativeFunction(context, "valueOf", 0, SymbolPrototype::valueOf);
        symbolValueOf.initializePrototypeChain(context);
        defineBuiltin(symbolPrototype, "valueOf", symbolValueOf);

        // Symbol.prototype.description is a getter
        defineGetter(symbolPrototype, "description", SymbolPrototype::getDescription);

        JSNativeFunction symbolToPrimitive = new JSNativeFunction(context, "[Symbol.toPrimitive]", 1,
                SymbolPrototype::toPrimitive);
        symbolToPrimitive.initializePrototypeChain(context);
        symbolPrototype.defineProperty(PropertyKey.SYMBOL_TO_PRIMITIVE, symbolToPrimitive,
                PropertyDescriptor.DataState.Configurable);

        defineToStringTag(symbolPrototype, JSSymbol.NAME);

        // Create Symbol constructor
        // Note: Symbol cannot be called with 'new' in JavaScript (throws TypeError)
        // Symbol objects are created using Object(symbolValue) for use with Proxy
        JSNativeFunction symbolConstructor = new JSNativeFunction(context, JSSymbol.NAME, 0, SymbolConstructor::call,
                true);
        linkConstructor(symbolConstructor, symbolPrototype, JSConstructorType.SYMBOL_OBJECT);

        // Symbol static methods
        defineMethod(symbolConstructor, "for", 1, SymbolConstructor::symbolFor);
        defineMethod(symbolConstructor, "keyFor", 1, SymbolConstructor::keyFor);

        // Well-known symbols (ES2015+)
        symbolConstructor.defineProperty(PropertyKey.ITERATOR, JSSymbol.ITERATOR, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("asyncIterator"), JSSymbol.ASYNC_ITERATOR,
                PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("toStringTag"), JSSymbol.TO_STRING_TAG,
                PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("hasInstance"), JSSymbol.HAS_INSTANCE,
                PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("isConcatSpreadable"), JSSymbol.IS_CONCAT_SPREADABLE,
                PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("toPrimitive"), JSSymbol.TO_PRIMITIVE,
                PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("match"), JSSymbol.MATCH,
                PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("matchAll"), JSSymbol.MATCH_ALL,
                PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("replace"), JSSymbol.REPLACE,
                PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("search"), JSSymbol.SEARCH,
                PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("split"), JSSymbol.SPLIT,
                PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("species"), JSSymbol.SPECIES,
                PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("unscopables"), JSSymbol.UNSCOPABLES,
                PropertyDescriptor.DataState.None);
        // ES2024 additions kept intentionally even though not in upstream QuickJS.
        symbolConstructor.defineProperty(PropertyKey.fromString("dispose"), JSSymbol.DISPOSE,
                PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("asyncDispose"), JSSymbol.ASYNC_DISPOSE,
                PropertyDescriptor.DataState.None);

        defineBuiltin(globalObject, JSSymbol.NAME, symbolConstructor);
    }

    private void initializeTemporalNow(JSObject temporalObject) {
        JSObject nowObject = context.createJSObject();
        defineToStringTag(nowObject, "Temporal.Now");

        defineMethod(nowObject, "instant", 0, TemporalNow::instant);
        defineMethod(nowObject, "timeZoneId", 0, TemporalNow::timeZoneId);
        defineMethod(nowObject, "plainDateTimeISO", 0, TemporalNow::plainDateTimeISO);
        defineMethod(nowObject, "plainDateISO", 0, TemporalNow::plainDateISO);
        defineMethod(nowObject, "plainTimeISO", 0, TemporalNow::plainTimeISO);
        defineMethod(nowObject, "zonedDateTimeISO", 0, TemporalNow::zonedDateTimeISO);

        defineBuiltin(temporalObject, "Now", nowObject);
    }

    private void initializeTemporalObject() {
        JSObject temporalObject = context.createJSObject();
        defineToStringTag(temporalObject, "Temporal");

        // Register Temporal on globalThis first so prototype lookup works during construction
        defineBuiltin(globalObject, "Temporal", temporalObject);

        // Duration must be available before PlainDateTime is installed.
        TemporalIntrinsic.DURATION.install(temporalObject);
        TemporalIntrinsic.PLAIN_DATE.install(temporalObject);
        TemporalIntrinsic.PLAIN_TIME.install(temporalObject);
        TemporalIntrinsic.PLAIN_DATE_TIME.install(temporalObject);
        TemporalIntrinsic.PLAIN_YEAR_MONTH.install(temporalObject);
        TemporalIntrinsic.PLAIN_MONTH_DAY.install(temporalObject);
        TemporalIntrinsic.INSTANT.install(temporalObject);
        TemporalIntrinsic.ZONED_DATE_TIME.install(temporalObject);

        // Temporal.Now
        initializeTemporalNow(temporalObject);
    }

    /**
     * Initialize all TypedArray constructors. Per ES spec, creates the %TypedArray% intrinsic (shared parent) and
     * individual typed array constructors that inherit from it.
     */
    private void initializeTypedArrayConstructors() {
        JSValue arrayToString = JSUndefined.INSTANCE;
        JSValue arrayConstructorValue = globalObject.get(JSArray.NAME);
        if (arrayConstructorValue instanceof JSObject arrayConstructor) {
            JSValue arrayPrototypeValue = arrayConstructor.get(PropertyKey.PROTOTYPE);
            if (arrayPrototypeValue instanceof JSObject arrayPrototype) {
                arrayToString = arrayPrototype.get(PropertyKey.TO_STRING);
            }
        }

        // Create %TypedArray%.prototype — the shared prototype for all typed array prototypes
        JSObject typedArrayPrototype = context.createJSObject();

        JSNativeFunction valuesFunction = new JSNativeFunction(context, "values", 0, TypedArrayPrototype::values);
        IntrinsicFunctions.TYPED_ARRAY_PROTOTYPE.install(typedArrayPrototype);
        if (arrayToString instanceof JSFunction) {
            typedArrayPrototype.defineProperty(PropertyKey.TO_STRING, arrayToString,
                    PropertyDescriptor.DataState.ConfigurableWritable);
        } else {
            defineMethod(typedArrayPrototype, "toString", 0, TypedArrayPrototype::toString);
        }
        defineMethod(typedArrayPrototype, "with", 2, TypedArrayPrototype::withMethod);
        defineBuiltin(typedArrayPrototype, "values", valuesFunction);
        typedArrayPrototype.defineProperty(PropertyKey.SYMBOL_ITERATOR, valuesFunction,
                PropertyDescriptor.DataState.ConfigurableWritable);

        IntrinsicFunctions.TYPED_ARRAY_ACCESSORS.install(typedArrayPrototype);
        typedArrayPrototype.defineProperty(PropertyKey.SYMBOL_TO_STRING_TAG,
                new JSNativeFunction(context, "get [Symbol.toStringTag]", 0, TypedArrayPrototype::getToStringTag),
                PropertyDescriptor.AccessorState.Configurable);

        // Create %TypedArray% constructor — abstract, throws if called directly
        // Per spec: %TypedArray% is not exposed as a global but is the [[Prototype]] of all typed array constructors
        JSNativeFunction typedArrayConstructor = new JSNativeFunction(context, "TypedArray", 0,
                (ctx, thisArg, args) -> ctx.throwTypeError("Abstract class TypedArray not directly constructable"),
                true, true);
        context.transferPrototype(typedArrayConstructor, JSFunction.NAME);
        linkConstructor(typedArrayConstructor, typedArrayPrototype);

        // Add static methods to %TypedArray%
        defineMethod(typedArrayConstructor, "from", 1, TypedArrayConstructor::from);
        defineMethod(typedArrayConstructor, "of", 0, TypedArrayConstructor::of);
        defineSpeciesGetter(typedArrayConstructor, TypedArrayConstructor::getSpecies);

        for (TypedArrayKind def : TypedArrayKind.INSTALLATION_ORDER) {
            // Each concrete typed array prototype inherits from %TypedArray%.prototype
            JSObject prototype = context.createJSObject();
            prototype.setPrototype(typedArrayPrototype);
            prototype.defineProperty(PropertyKey.fromString("BYTES_PER_ELEMENT"), JSNumber.of(def.bytesPerElement),
                    PropertyDescriptor.DataState.None);

            // Each concrete constructor inherits from %TypedArray%
            JSNativeFunction constructor = new JSNativeFunction(context, def.name, 3, def.callback, true, true);
            constructor.setPrototype(typedArrayConstructor);
            constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
            constructor.setConstructorType(def.type);
            constructor.defineProperty(PropertyKey.fromString("BYTES_PER_ELEMENT"), JSNumber.of(def.bytesPerElement),
                    PropertyDescriptor.DataState.None);

            prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor,
                    PropertyDescriptor.DataState.ConfigurableWritable);

            // Add Uint8Array-specific base64/hex methods
            if (JSUint8Array.NAME.equals(def.name)) {
                // Static methods on constructor
                defineMethod(constructor, "fromBase64", 1, Uint8ArrayBase64Hex::fromBase64);
                defineMethod(constructor, "fromHex", 1, Uint8ArrayBase64Hex::fromHex);

                // Prototype methods
                IntrinsicFunctions.UINT8_ARRAY_PROTOTYPE.install(prototype);
            }

            defineBuiltin(globalObject, def.name, constructor);
        }
    }

    private void installIntlConstructor(JSObject intl, String name, int length, JSObject prototype, boolean requiresNew,
            JSNativeCallback callback) {
        JSNativeFunction constructor = new JSNativeFunction(context, name, length, callback, true, requiresNew);
        constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        defineMethod(constructor, "supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf);
        prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor,
                PropertyDescriptor.DataState.ConfigurableWritable);
        defineBuiltin(intl, name, constructor);
    }

    static void defineBuiltin(JSObject target, String name, JSValue value) {
        target.defineProperty(PropertyKey.fromString(name), value, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    static void defineGetter(JSObject prototype, String name, JSNativeCallback getter) {
        JSNativeFunction getterFunc = new JSNativeFunction(prototype.getContext(), "get " + name, 0, getter);
        prototype.defineProperty(PropertyKey.fromString(name), getterFunc,
                PropertyDescriptor.AccessorState.Configurable);
    }

    static void defineMethod(JSObject target, String name, int length, JSNativeCallback callback) {
        defineBuiltin(target, name, new JSNativeFunction(target.getContext(), name, length, callback));
    }

    static void defineSpeciesGetter(JSObject constructor, JSNativeCallback callback) {
        constructor.defineProperty(PropertyKey.SYMBOL_SPECIES,
                new JSNativeFunction(constructor.getContext(), "get [Symbol.species]", 0, callback),
                PropertyDescriptor.AccessorState.Configurable);
    }

    static void defineToStringTag(JSObject target, String tag) {
        target.defineProperty(PropertyKey.SYMBOL_TO_STRING_TAG, new JSString(tag),
                PropertyDescriptor.DataState.Configurable);
    }

    static void linkConstructor(JSObject constructor, JSObject prototype) {
        linkConstructor(constructor, prototype, null);
    }

    static void linkConstructor(JSObject constructor, JSObject prototype, JSConstructorType type) {
        constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        if (type != null) {
            constructor.setConstructorType(type);
        }
        prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor,
                PropertyDescriptor.DataState.ConfigurableWritable);
    }

    public record EvalOverlaySnapshot(Map<String, JSValue> values, Set<String> absentKeys) {
    }

    public static class GlobalFunction {

        private static String argumentString(JSContext context, JSValue[] args) {
            JSValue value = JSValue.argumentOrUndefined(args, 0);
            return value instanceof JSString string
                    ? string.value()
                    : JSTypeConversions.toString(context, value).value();
        }

        /**
         * decodeURI(encodedURI) Decode a URI that was encoded by encodeURI.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-decodeuri-encodeduri">ECMAScript decodeURI</a>
         */
        public static JSValue decodeURI(JSContext context, JSValue thisArg, JSValue[] args) {
            return UriCodec.decode(context, argumentString(context, args), false);
        }

        /**
         * decodeURIComponent(encodedURIComponent) Decode a URI component that was encoded by encodeURIComponent.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-decodeuricomponent-encodeduricomponent">ECMAScript
         *      decodeURIComponent</a>
         */
        public static JSValue decodeURIComponent(JSContext context, JSValue thisArg, JSValue[] args) {
            return UriCodec.decode(context, argumentString(context, args), true);
        }

        /**
         * encodeURI(uri) Encode a URI by escaping certain characters. Does not encode:
         * {@code A-Z a-z 0-9 ; , / ? : @ & = + $ - _ . ! ~ * ' ( ) #}
         *
         * @see <a href="https://tc39.es/ecma262/#sec-encodeuri-uri">ECMAScript encodeURI</a>
         */
        public static JSValue encodeURI(JSContext context, JSValue thisArg, JSValue[] args) {
            return UriCodec.encode(context, argumentString(context, args), false);
        }

        /**
         * encodeURIComponent(uriComponent) Encode a URI component by escaping certain characters. More aggressive than
         * encodeURI - also encodes: {@code ; , / ? : @ & = + $ #}
         *
         * @see <a href="https://tc39.es/ecma262/#sec-encodeuricomponent-uricomponent">ECMAScript encodeURIComponent</a>
         */
        public static JSValue encodeURIComponent(JSContext context, JSValue thisArg, JSValue[] args) {
            return UriCodec.encode(context, argumentString(context, args), true);
        }

        /**
         * escape(string) Deprecated function that encodes a string for use in a URL. Encodes all characters except: A-Z
         * a-z 0-9 @ * _ + - . / Uses %XX for characters {@code < 256} and %uXXXX for characters {@code >= 256}.
         *
         * @see <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/escape">MDN
         *      escape</a>
         */
        public static JSValue escape(JSContext context, JSValue thisArg, JSValue[] args) {
            return UriCodec.escape(argumentString(context, args));
        }

        /**
         * eval(code) Evaluate JavaScript code in the realm context.
         * <p>
         * Per ES spec, eval evaluates code in the realm of the eval function itself, not the calling realm. This
         * matters for cross-realm calls like other.eval('code').
         *
         * @param realmContext
         *            the context where this eval function was created (the eval's realm)
         * @param callerContext
         *            the context of the calling code (for scope overlay and exception propagation)
         * @see <a href="https://tc39.es/ecma262/#sec-eval-x">ECMAScript eval</a>
         */
        public static JSValue eval(JSContext realmContext, JSContext callerContext, JSValue[] args) {
            return eval(realmContext, callerContext, args, false);
        }

        public static JSValue eval(JSContext realmContext, JSContext callerContext, JSValue[] args,
                boolean forceDirectEvalCall) {
            return GlobalEval.evaluate(realmContext, callerContext, args, forceDirectEvalCall);
        }

        /**
         * isFinite(value) Determine whether a value is a finite number.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-isfinite-number">ECMAScript isFinite</a>
         */
        public static JSValue isFinite(JSContext context, JSValue thisArg, JSValue[] args) {
            return testNumber(context, args, false);
        }

        /**
         * isNaN(value) Determine whether a value is NaN.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-isnan-number">ECMAScript isNaN</a>
         */
        public static JSValue isNaN(JSContext context, JSValue thisArg, JSValue[] args) {
            return testNumber(context, args, true);
        }

        /**
         * parseFloat(string) Parse a string and return a floating point number.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-parsefloat-string">ECMAScript parseFloat</a>
         */
        public static JSValue parseFloat(JSContext context, JSValue thisArg, JSValue[] args) {
            return GlobalNumberParser.parseFloat(argumentString(context, args));
        }

        /**
         * parseInt(string, radix) Parse a string and return an integer of the specified radix.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-parseint-string-radix">ECMAScript parseInt</a>
         */
        public static JSValue parseInt(JSContext context, JSValue thisArg, JSValue[] args) {
            return GlobalNumberParser.parseInt(context, args, argumentString(context, args));
        }

        private static JSValue testNumber(JSContext context, JSValue[] args, boolean nan) {
            JSValue value = JSValue.argumentOrUndefined(args, 0);
            JSValue error = GlobalNumberParser.numberConversionError(context, value);
            if (error != null) {
                return error;
            }
            double number = JSTypeConversions.toNumber(context, value).value();
            return JSBoolean.valueOf(nan ? Double.isNaN(number) : Double.isFinite(number));
        }

        /**
         * unescape(string) Deprecated function that decodes a string encoded by escape(). Decodes %XX and %uXXXX
         * sequences.
         *
         * @see <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/unescape">MDN
         *      unescape</a>
         */
        public static JSValue unescape(JSContext context, JSValue thisArg, JSValue[] args) {
            return UriCodec.unescape(argumentString(context, args));
        }

    }
}
