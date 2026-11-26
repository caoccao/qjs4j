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

import com.caoccao.qjs4j.builtins.*;
import com.caoccao.qjs4j.builtins.temporal.*;
import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.exceptions.JSErrorType;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.vm.StackFrame;
import com.caoccao.qjs4j.vm.VarRef;

import java.util.*;

/**
 * The global object with built-in functions. Based on ECMAScript specification global properties and functions.
 * <p>
 * Implements: - Global value properties (NaN, Infinity, undefined) - Global function properties (parseInt, parseFloat,
 * isNaN, isFinite, eval) - URI handling functions (encodeURI, decodeURI, encodeURIComponent, decodeURIComponent)
 */
public final class JSGlobalObject {
    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();
    private static final boolean[] URI_RESERVED_TABLE = new boolean[128];
    private static final boolean[] URI_UNESCAPED_COMPONENT_TABLE = new boolean[128];
    private static final boolean[] URI_UNESCAPED_TABLE = new boolean[128];

    static {
        for (char c = 'a'; c <= 'z'; c++) {
            URI_UNESCAPED_COMPONENT_TABLE[c] = true;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            URI_UNESCAPED_COMPONENT_TABLE[c] = true;
        }
        for (char c = '0'; c <= '9'; c++) {
            URI_UNESCAPED_COMPONENT_TABLE[c] = true;
        }
        for (char c : "-_.!~*'()".toCharArray()) {
            URI_UNESCAPED_COMPONENT_TABLE[c] = true;
        }
        for (char c : ";/?:@&=+$,#".toCharArray()) {
            URI_RESERVED_TABLE[c] = true;
        }
        for (int i = 0; i < URI_UNESCAPED_TABLE.length; i++) {
            URI_UNESCAPED_TABLE[i] = URI_UNESCAPED_COMPONENT_TABLE[i] || URI_RESERVED_TABLE[i];
        }
    }

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

    private JSObject createTemporalPrototype(String tag) {
        JSObject prototype = context.createJSObject();
        defineToStringTag(prototype, tag);
        return prototype;
    }

    private void defineGetter(JSObject prototype, String name, JSNativeCallback getter) {
        JSNativeFunction getterFunc = new JSNativeFunction(context, "get " + name, 0, getter);
        prototype.defineProperty(PropertyKey.fromString(name), getterFunc,
                PropertyDescriptor.AccessorState.Configurable);
    }

    private void defineMethod(JSObject target, String name, int length, JSNativeCallback callback) {
        defineBuiltin(target, name, new JSNativeFunction(context, name, length, callback));
    }

    private void defineSpeciesGetter(JSObject constructor, JSNativeCallback callback) {
        constructor.defineProperty(PropertyKey.SYMBOL_SPECIES,
                new JSNativeFunction(context, "get [Symbol.species]", 0, callback),
                PropertyDescriptor.AccessorState.Configurable);
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
        initializeMapConstructor();
        initializeSetConstructor();
        initializeWeakMapConstructor();
        initializeWeakSetConstructor();
        initializeWeakRefConstructor();
        initializeFinalizationRegistryConstructor();
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
            initializeShadowRealmConstructor();
        }
        initializeDisposableStackConstructor();
        initializeAsyncDisposableStackConstructor();
        initializeIteratorConstructor();
        initializeGeneratorPrototype();
        initializeAsyncGeneratorPrototype();

        // Binary data constructors
        initializeArrayBufferConstructor();
        initializeSharedArrayBufferConstructor();
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
     * Initialize ArrayBuffer constructor and prototype.
     */
    private void initializeArrayBufferConstructor() {
        // Create ArrayBuffer.prototype
        JSObject arrayBufferPrototype = context.createJSObject();
        defineMethod(arrayBufferPrototype, "resize", 1, ArrayBufferPrototype::resize);
        defineMethod(arrayBufferPrototype, "slice", 2, ArrayBufferPrototype::slice);
        defineMethod(arrayBufferPrototype, "transfer", 0, ArrayBufferPrototype::transfer);
        defineMethod(arrayBufferPrototype, "transferToFixedLength", 0, ArrayBufferPrototype::transferToFixedLength);
        defineMethod(arrayBufferPrototype, "transferToImmutable", 0, ArrayBufferPrototype::transferToImmutable);

        // Define getter properties
        defineGetter(arrayBufferPrototype, "byteLength", ArrayBufferPrototype::getByteLength);
        defineGetter(arrayBufferPrototype, "detached", ArrayBufferPrototype::getDetached);
        defineGetter(arrayBufferPrototype, "immutable", ArrayBufferPrototype::getImmutable);
        defineGetter(arrayBufferPrototype, "maxByteLength", ArrayBufferPrototype::getMaxByteLength);
        defineGetter(arrayBufferPrototype, "resizable", ArrayBufferPrototype::getResizable);
        defineToStringTag(arrayBufferPrototype, JSArrayBuffer.NAME);

        // Create ArrayBuffer constructor as a function
        JSNativeFunction arrayBufferConstructor = new JSNativeFunction(context, JSArrayBuffer.NAME, 1,
                ArrayBufferConstructor::call, true, true);
        linkConstructor(arrayBufferConstructor, arrayBufferPrototype, JSConstructorType.ARRAY_BUFFER);

        // Static methods
        defineMethod(arrayBufferConstructor, "isView", 1, ArrayBufferConstructor::isView);

        // Symbol.species getter
        defineSpeciesGetter(arrayBufferConstructor, ArrayBufferConstructor::getSpecies);

        defineBuiltin(globalObject, JSArrayBuffer.NAME, arrayBufferConstructor);
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
        defineMethod(arrayPrototype, "at", 1, ArrayPrototype::at);
        defineMethod(arrayPrototype, "concat", 1, ArrayPrototype::concat);
        defineMethod(arrayPrototype, "copyWithin", 2, ArrayPrototype::copyWithin);
        defineMethod(arrayPrototype, "entries", 0, IteratorPrototype::arrayEntries);
        defineMethod(arrayPrototype, "every", 1, ArrayPrototype::every);
        defineMethod(arrayPrototype, "fill", 1, ArrayPrototype::fill);
        defineMethod(arrayPrototype, "filter", 1, ArrayPrototype::filter);
        defineMethod(arrayPrototype, "find", 1, ArrayPrototype::find);
        defineMethod(arrayPrototype, "findIndex", 1, ArrayPrototype::findIndex);
        defineMethod(arrayPrototype, "findLast", 1, ArrayPrototype::findLast);
        defineMethod(arrayPrototype, "findLastIndex", 1, ArrayPrototype::findLastIndex);
        defineMethod(arrayPrototype, "flat", 0, ArrayPrototype::flat);
        defineMethod(arrayPrototype, "flatMap", 1, ArrayPrototype::flatMap);
        defineMethod(arrayPrototype, "forEach", 1, ArrayPrototype::forEach);
        defineMethod(arrayPrototype, "includes", 1, ArrayPrototype::includes);
        defineMethod(arrayPrototype, "indexOf", 1, ArrayPrototype::indexOf);
        defineMethod(arrayPrototype, "join", 1, ArrayPrototype::join);
        defineMethod(arrayPrototype, "keys", 0, IteratorPrototype::arrayKeys);
        defineMethod(arrayPrototype, "lastIndexOf", 1, ArrayPrototype::lastIndexOf);
        defineMethod(arrayPrototype, "map", 1, ArrayPrototype::map);
        defineMethod(arrayPrototype, "pop", 0, ArrayPrototype::pop);
        defineMethod(arrayPrototype, "push", 1, ArrayPrototype::push);
        defineMethod(arrayPrototype, "reduce", 1, ArrayPrototype::reduce);
        defineMethod(arrayPrototype, "reduceRight", 1, ArrayPrototype::reduceRight);
        defineMethod(arrayPrototype, "reverse", 0, ArrayPrototype::reverse);
        defineMethod(arrayPrototype, "shift", 0, ArrayPrototype::shift);
        defineMethod(arrayPrototype, "slice", 2, ArrayPrototype::slice);
        defineMethod(arrayPrototype, "some", 1, ArrayPrototype::some);
        defineMethod(arrayPrototype, "sort", 1, ArrayPrototype::sort);
        defineMethod(arrayPrototype, "splice", 2, ArrayPrototype::splice);
        defineMethod(arrayPrototype, "toLocaleString", 0, ArrayPrototype::toLocaleString);
        defineMethod(arrayPrototype, "toReversed", 0, ArrayPrototype::toReversed);
        defineMethod(arrayPrototype, "toSorted", 1, ArrayPrototype::toSorted);
        defineMethod(arrayPrototype, "toSpliced", 2, ArrayPrototype::toSpliced);
        defineMethod(arrayPrototype, "toString", 0, ArrayPrototype::toString);
        defineMethod(arrayPrototype, "unshift", 1, ArrayPrototype::unshift);
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
        defineMethod(arrayConstructor, "from", 1, ArrayConstructor::from);
        defineMethod(arrayConstructor, "fromAsync", 1, ArrayConstructor::fromAsync);
        defineMethod(arrayConstructor, "isArray", 1, ArrayConstructor::isArray);
        defineMethod(arrayConstructor, "of", 0, ArrayConstructor::of);

        // Symbol.species getter
        defineSpeciesGetter(arrayConstructor, ArrayConstructor::getSpecies);

        defineBuiltin(globalObject, JSArray.NAME, arrayConstructor);
    }

    /**
     * Initialize AsyncDisposableStack constructor and prototype.
     */
    private void initializeAsyncDisposableStackConstructor() {
        JSObject asyncDisposableStackPrototype = context.createJSObject();
        defineMethod(asyncDisposableStackPrototype, "adopt", 2, AsyncDisposableStackPrototype::adopt);
        defineMethod(asyncDisposableStackPrototype, "defer", 1, AsyncDisposableStackPrototype::defer);
        JSNativeFunction disposeAsyncFunction = new JSNativeFunction(context, "disposeAsync", 0,
                AsyncDisposableStackPrototype::disposeAsync);
        defineBuiltin(asyncDisposableStackPrototype, "disposeAsync", disposeAsyncFunction);
        defineMethod(asyncDisposableStackPrototype, "move", 0, AsyncDisposableStackPrototype::move);
        defineMethod(asyncDisposableStackPrototype, "use", 1, AsyncDisposableStackPrototype::use);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.SYMBOL_ASYNC_DISPOSE, disposeAsyncFunction,
                PropertyDescriptor.DataState.ConfigurableWritable);

        defineGetter(asyncDisposableStackPrototype, "disposed", AsyncDisposableStackPrototype::getDisposed);
        defineToStringTag(asyncDisposableStackPrototype, JSAsyncDisposableStack.NAME);

        JSNativeFunction asyncDisposableStackConstructor = new JSNativeFunction(context, JSAsyncDisposableStack.NAME, 0,
                AsyncDisposableStackConstructor::call, true, true);
        linkConstructor(asyncDisposableStackConstructor, asyncDisposableStackPrototype,
                JSConstructorType.ASYNC_DISPOSABLE_STACK);

        defineBuiltin(globalObject, JSAsyncDisposableStack.NAME, asyncDisposableStackConstructor);
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

        // Create AsyncGeneratorFunction.prototype (not exposed in global scope)
        // All async generator function objects (async function*) inherit from this
        JSObject asyncGeneratorFunctionPrototype = context.createJSObject();
        context.transferPrototype(asyncGeneratorFunctionPrototype, JSFunction.NAME);

        // AsyncGeneratorFunction.prototype[Symbol.toStringTag] = "AsyncGeneratorFunction" (configurable)
        defineToStringTag(asyncGeneratorFunctionPrototype, "AsyncGeneratorFunction");

        // Link: AsyncGeneratorFunction.prototype.prototype = AsyncGenerator.prototype (configurable)
        asyncGeneratorFunctionPrototype.defineProperty(PropertyKey.PROTOTYPE, asyncGeneratorPrototype,
                PropertyDescriptor.DataState.Configurable);

        // Link: AsyncGenerator.prototype.constructor = AsyncGeneratorFunction.prototype (configurable)
        asyncGeneratorPrototype.defineProperty(PropertyKey.CONSTRUCTOR, asyncGeneratorFunctionPrototype,
                PropertyDescriptor.DataState.Configurable);

        // Create AsyncGeneratorFunction constructor (uses dynamic function construction)
        JSNativeFunction asyncGeneratorFunctionConstructor = new JSNativeFunction(context, "AsyncGeneratorFunction", 1,
                FunctionConstructor::callAsyncGenerator, true);
        asyncGeneratorFunctionConstructor.defineProperty(PropertyKey.PROTOTYPE, asyncGeneratorFunctionPrototype,
                PropertyDescriptor.DataState.None);
        asyncGeneratorFunctionPrototype.defineProperty(PropertyKey.CONSTRUCTOR, asyncGeneratorFunctionConstructor,
                PropertyDescriptor.DataState.Configurable);

        // Store in context for async generator function prototype chain setup
        context.setAsyncGeneratorFunctionPrototype(asyncGeneratorFunctionPrototype);
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
        defineMethod(bigIntPrototype, "toLocaleString", 0, BigIntPrototype::toLocaleString);
        defineMethod(bigIntPrototype, "toString", 0, BigIntPrototype::toString);
        defineMethod(bigIntPrototype, "valueOf", 0, BigIntPrototype::valueOf);
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

    /**
     * Initialize DataView constructor and prototype.
     */
    private void initializeDataViewConstructor() {
        // Create DataView.prototype
        JSObject dataViewPrototype = context.createJSObject();

        // Define getter properties
        defineGetter(dataViewPrototype, "buffer", DataViewPrototype::getBuffer);
        defineGetter(dataViewPrototype, "byteLength", DataViewPrototype::getByteLength);
        defineGetter(dataViewPrototype, "byteOffset", DataViewPrototype::getByteOffset);
        defineToStringTag(dataViewPrototype, "DataView");

        // Int8/Uint8 methods
        defineMethod(dataViewPrototype, "getInt8", 1, DataViewPrototype::getInt8);
        defineMethod(dataViewPrototype, "setInt8", 2, DataViewPrototype::setInt8);
        defineMethod(dataViewPrototype, "getUint8", 1, DataViewPrototype::getUint8);
        defineMethod(dataViewPrototype, "setUint8", 2, DataViewPrototype::setUint8);

        // Int16/Uint16 methods
        defineMethod(dataViewPrototype, "getInt16", 1, DataViewPrototype::getInt16);
        defineMethod(dataViewPrototype, "setInt16", 2, DataViewPrototype::setInt16);
        defineMethod(dataViewPrototype, "getUint16", 1, DataViewPrototype::getUint16);
        defineMethod(dataViewPrototype, "setUint16", 2, DataViewPrototype::setUint16);

        // Int32/Uint32 methods
        defineMethod(dataViewPrototype, "getInt32", 1, DataViewPrototype::getInt32);
        defineMethod(dataViewPrototype, "setInt32", 2, DataViewPrototype::setInt32);
        defineMethod(dataViewPrototype, "getUint32", 1, DataViewPrototype::getUint32);
        defineMethod(dataViewPrototype, "setUint32", 2, DataViewPrototype::setUint32);

        // BigInt methods
        defineMethod(dataViewPrototype, "getBigInt64", 1, DataViewPrototype::getBigInt64);
        defineMethod(dataViewPrototype, "setBigInt64", 2, DataViewPrototype::setBigInt64);
        defineMethod(dataViewPrototype, "getBigUint64", 1, DataViewPrototype::getBigUint64);
        defineMethod(dataViewPrototype, "setBigUint64", 2, DataViewPrototype::setBigUint64);

        // Float methods
        defineMethod(dataViewPrototype, "getFloat16", 1, DataViewPrototype::getFloat16);
        defineMethod(dataViewPrototype, "setFloat16", 2, DataViewPrototype::setFloat16);
        defineMethod(dataViewPrototype, "getFloat32", 1, DataViewPrototype::getFloat32);
        defineMethod(dataViewPrototype, "setFloat32", 2, DataViewPrototype::setFloat32);
        defineMethod(dataViewPrototype, "getFloat64", 1, DataViewPrototype::getFloat64);
        defineMethod(dataViewPrototype, "setFloat64", 2, DataViewPrototype::setFloat64);

        // Create DataView constructor as a function that requires 'new'
        JSNativeFunction dataViewConstructor = new JSNativeFunction(context, "DataView", 1, DataViewConstructor::call,
                true, true);
        linkConstructor(dataViewConstructor, dataViewPrototype, JSConstructorType.DATA_VIEW);

        defineBuiltin(globalObject, "DataView", dataViewConstructor);
    }

    /**
     * Initialize Date constructor and prototype.
     */
    private void initializeDateConstructor() {
        JSObject datePrototype = context.createJSObject();
        JSNativeFunction toUTCString = new JSNativeFunction(context, "toUTCString", 0, DatePrototype::toUTCString);
        JSNativeFunction toPrimitive = new JSNativeFunction(context, "[Symbol.toPrimitive]", 1,
                DatePrototype::symbolToPrimitive);

        defineMethod(datePrototype, "getDate", 0, DatePrototype::getDate);
        defineMethod(datePrototype, "getDay", 0, DatePrototype::getDay);
        defineMethod(datePrototype, "getFullYear", 0, DatePrototype::getFullYear);
        defineMethod(datePrototype, "getHours", 0, DatePrototype::getHours);
        defineMethod(datePrototype, "getMilliseconds", 0, DatePrototype::getMilliseconds);
        defineMethod(datePrototype, "getMinutes", 0, DatePrototype::getMinutes);
        defineMethod(datePrototype, "getMonth", 0, DatePrototype::getMonth);
        defineMethod(datePrototype, "getSeconds", 0, DatePrototype::getSeconds);
        defineMethod(datePrototype, "getTime", 0, DatePrototype::getTime);
        defineMethod(datePrototype, "getTimezoneOffset", 0, DatePrototype::getTimezoneOffset);
        defineMethod(datePrototype, "getUTCDate", 0, DatePrototype::getUTCDate);
        defineMethod(datePrototype, "getUTCDay", 0, DatePrototype::getUTCDay);
        defineMethod(datePrototype, "getUTCFullYear", 0, DatePrototype::getUTCFullYear);
        defineMethod(datePrototype, "getUTCHours", 0, DatePrototype::getUTCHours);
        defineMethod(datePrototype, "getUTCMilliseconds", 0, DatePrototype::getUTCMilliseconds);
        defineMethod(datePrototype, "getUTCMinutes", 0, DatePrototype::getUTCMinutes);
        defineMethod(datePrototype, "getUTCMonth", 0, DatePrototype::getUTCMonth);
        defineMethod(datePrototype, "getUTCSeconds", 0, DatePrototype::getUTCSeconds);
        defineMethod(datePrototype, "getYear", 0, DatePrototype::getYear);
        defineMethod(datePrototype, "setDate", 1, DatePrototype::setDate);
        defineMethod(datePrototype, "setFullYear", 3, DatePrototype::setFullYear);
        defineMethod(datePrototype, "setHours", 4, DatePrototype::setHours);
        defineMethod(datePrototype, "setMilliseconds", 1, DatePrototype::setMilliseconds);
        defineMethod(datePrototype, "setMinutes", 3, DatePrototype::setMinutes);
        defineMethod(datePrototype, "setMonth", 2, DatePrototype::setMonth);
        defineMethod(datePrototype, "setSeconds", 2, DatePrototype::setSeconds);
        defineMethod(datePrototype, "setTime", 1, DatePrototype::setTime);
        defineMethod(datePrototype, "setUTCDate", 1, DatePrototype::setUTCDate);
        defineMethod(datePrototype, "setUTCFullYear", 3, DatePrototype::setUTCFullYear);
        defineMethod(datePrototype, "setUTCHours", 4, DatePrototype::setUTCHours);
        defineMethod(datePrototype, "setUTCMilliseconds", 1, DatePrototype::setUTCMilliseconds);
        defineMethod(datePrototype, "setUTCMinutes", 3, DatePrototype::setUTCMinutes);
        defineMethod(datePrototype, "setUTCMonth", 2, DatePrototype::setUTCMonth);
        defineMethod(datePrototype, "setUTCSeconds", 2, DatePrototype::setUTCSeconds);
        defineMethod(datePrototype, "setYear", 1, DatePrototype::setYear);
        defineMethod(datePrototype, "toDateString", 0, DatePrototype::toDateString);
        defineBuiltin(datePrototype, "toGMTString", toUTCString);
        defineMethod(datePrototype, "toISOString", 0, DatePrototype::toISOString);
        defineMethod(datePrototype, "toJSON", 1, DatePrototype::toJSON);
        defineMethod(datePrototype, "toLocaleDateString", 0, DatePrototype::toLocaleDateString);
        defineMethod(datePrototype, "toLocaleString", 0, DatePrototype::toLocaleString);
        defineMethod(datePrototype, "toLocaleTimeString", 0, DatePrototype::toLocaleTimeString);
        defineMethod(datePrototype, "toString", 0, DatePrototype::toStringMethod);
        defineMethod(datePrototype, "toTimeString", 0, DatePrototype::toTimeString);
        defineBuiltin(datePrototype, "toUTCString", toUTCString);
        if (context.getRuntime().getOptions().isTemporalEnabled()) {
            defineMethod(datePrototype, "toTemporalInstant", 0, DatePrototype::toTemporalInstant);
        }
        defineMethod(datePrototype, "valueOf", 0, DatePrototype::valueOf);
        datePrototype.defineProperty(PropertyKey.SYMBOL_TO_PRIMITIVE, toPrimitive,
                PropertyDescriptor.DataState.Configurable);

        JSNativeFunction dateConstructor = new JSNativeFunction(context, JSDate.NAME, 7, DateConstructor::call, true);
        linkConstructor(dateConstructor, datePrototype, JSConstructorType.DATE);

        defineMethod(dateConstructor, "UTC", 7, DateConstructor::UTC);
        defineMethod(dateConstructor, "now", 0, DateConstructor::now);
        defineMethod(dateConstructor, "parse", 1, DateConstructor::parse);

        defineBuiltin(globalObject, JSDate.NAME, dateConstructor);
    }

    /**
     * Initialize DisposableStack constructor and prototype.
     */
    private void initializeDisposableStackConstructor() {
        JSObject disposableStackPrototype = context.createJSObject();
        JSNativeFunction disposeFunction = new JSNativeFunction(context, "dispose", 0,
                DisposableStackPrototype::dispose);
        defineMethod(disposableStackPrototype, "adopt", 2, DisposableStackPrototype::adopt);
        defineMethod(disposableStackPrototype, "defer", 1, DisposableStackPrototype::defer);
        defineBuiltin(disposableStackPrototype, "dispose", disposeFunction);
        defineMethod(disposableStackPrototype, "move", 0, DisposableStackPrototype::move);
        defineMethod(disposableStackPrototype, "use", 1, DisposableStackPrototype::use);
        disposableStackPrototype.defineProperty(PropertyKey.SYMBOL_DISPOSE, disposeFunction,
                PropertyDescriptor.DataState.ConfigurableWritable);

        defineGetter(disposableStackPrototype, "disposed", DisposableStackPrototype::getDisposed);
        defineToStringTag(disposableStackPrototype, JSDisposableStack.NAME);

        JSNativeFunction disposableStackConstructor = new JSNativeFunction(context, JSDisposableStack.NAME, 0,
                DisposableStackConstructor::call, true, true);
        linkConstructor(disposableStackConstructor, disposableStackPrototype, JSConstructorType.DISPOSABLE_STACK);

        defineBuiltin(globalObject, JSDisposableStack.NAME, disposableStackConstructor);
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
     * Initialize FinalizationRegistry constructor.
     */
    private void initializeFinalizationRegistryConstructor() {
        JSObject finalizationRegistryPrototype = context.createJSObject();
        defineMethod(finalizationRegistryPrototype, "register", 2, FinalizationRegistryPrototype::register);
        defineMethod(finalizationRegistryPrototype, "unregister", 1, FinalizationRegistryPrototype::unregister);
        defineToStringTag(finalizationRegistryPrototype, JSFinalizationRegistry.NAME);

        JSNativeFunction finalizationRegistryConstructor = new JSNativeFunction(context, JSFinalizationRegistry.NAME, 1,
                FinalizationRegistryConstructor::call, true, true);
        linkConstructor(finalizationRegistryConstructor, finalizationRegistryPrototype,
                JSConstructorType.FINALIZATION_REGISTRY);

        defineBuiltin(globalObject, JSFinalizationRegistry.NAME, finalizationRegistryConstructor);
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

        defineMethod(functionPrototype, "apply", 2, FunctionPrototype::apply);
        defineMethod(functionPrototype, "bind", 1, FunctionPrototype::bind);
        defineMethod(functionPrototype, "call", 1, FunctionPrototype::call);
        defineMethod(functionPrototype, "toString", 0, FunctionPrototype::toString_);

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
        defineMethod(generatorPrototype, "next", 1, GeneratorPrototype::next);
        defineMethod(generatorPrototype, "return", 1, GeneratorPrototype::returnMethod);
        defineMethod(generatorPrototype, "throw", 1, GeneratorPrototype::throwMethod);

        // Symbol.toStringTag = "Generator" (configurable only)
        // Matches QuickJS JS_PROP_STRING_DEF("[Symbol.toStringTag]", "Generator", JS_PROP_CONFIGURABLE)
        defineToStringTag(generatorPrototype, "Generator");

        // Create GeneratorFunction.prototype (not exposed in global scope)
        // All generator function objects (function*) inherit from this
        JSObject generatorFunctionPrototype = context.createJSObject();
        context.transferPrototype(generatorFunctionPrototype, JSFunction.NAME);

        // GeneratorFunction.prototype[Symbol.toStringTag] = "GeneratorFunction" (configurable)
        defineToStringTag(generatorFunctionPrototype, "GeneratorFunction");

        // Link: GeneratorFunction.prototype.prototype = Generator.prototype (configurable)
        generatorFunctionPrototype.defineProperty(PropertyKey.PROTOTYPE, generatorPrototype,
                PropertyDescriptor.DataState.Configurable);

        // Link: Generator.prototype.constructor = GeneratorFunction.prototype (configurable)
        generatorPrototype.defineProperty(PropertyKey.CONSTRUCTOR, generatorFunctionPrototype,
                PropertyDescriptor.DataState.Configurable);

        // Create GeneratorFunction constructor (uses dynamic function construction)
        // Following QuickJS JS_AddIntrinsicGenerator pattern
        JSNativeFunction generatorFunctionConstructor = new JSNativeFunction(context, "GeneratorFunction", 1,
                FunctionConstructor::callGenerator, true);
        JSValue functionConstructorValue = globalObject.get(PropertyKey.fromString(JSFunction.NAME));
        if (functionConstructorValue instanceof JSObject functionConstructorObject) {
            generatorFunctionConstructor.setPrototype(functionConstructorObject);
        }
        generatorFunctionConstructor.defineProperty(PropertyKey.PROTOTYPE, generatorFunctionPrototype,
                PropertyDescriptor.DataState.None);
        generatorFunctionPrototype.defineProperty(PropertyKey.CONSTRUCTOR, PropertyDescriptor
                .dataDescriptor(generatorFunctionConstructor, PropertyDescriptor.DataState.Configurable));

        // Store in context for generator function prototype chain setup
        context.setGeneratorFunctionPrototype(generatorFunctionPrototype);
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
        defineMethod(globalObject, "isFinite", 1, GlobalFunction::isFinite);
        defineMethod(globalObject, "isNaN", 1, GlobalFunction::isNaN);
        defineMethod(globalObject, "parseFloat", 1, GlobalFunction::parseFloat);
        defineMethod(globalObject, "parseInt", 2, GlobalFunction::parseInt);

        // URI handling functions
        defineMethod(globalObject, "decodeURI", 1, GlobalFunction::decodeURI);
        defineMethod(globalObject, "decodeURIComponent", 1, GlobalFunction::decodeURIComponent);
        defineMethod(globalObject, "encodeURI", 1, GlobalFunction::encodeURI);
        defineMethod(globalObject, "encodeURIComponent", 1, GlobalFunction::encodeURIComponent);
        defineMethod(globalObject, "escape", 1, GlobalFunction::escape);
        defineMethod(globalObject, "unescape", 1, GlobalFunction::unescape);

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
        defineGetter(dateTimeFormatPrototype, "format", JSIntlObject::dateTimeFormatFormatGetter);
        defineMethod(dateTimeFormatPrototype, "resolvedOptions", 0, JSIntlObject::dateTimeFormatResolvedOptions);
        defineMethod(dateTimeFormatPrototype, "formatToParts", 1, JSIntlObject::dateTimeFormatFormatToParts);
        defineMethod(dateTimeFormatPrototype, "formatRange", 2, JSIntlObject::dateTimeFormatFormatRange);
        defineMethod(dateTimeFormatPrototype, "formatRangeToParts", 2, JSIntlObject::dateTimeFormatFormatRangeToParts);
        defineToStringTag(dateTimeFormatPrototype, "Intl.DateTimeFormat");
        JSNativeFunction dateTimeFormatConstructor = new JSNativeFunction(context, "DateTimeFormat", 0, (childContext,
                thisArg, args) -> JSIntlObject.createDateTimeFormat(childContext, dateTimeFormatPrototype, args), true);
        installIntlConstructor(intlObject, "DateTimeFormat", dateTimeFormatConstructor, dateTimeFormatPrototype);

        JSObject displayNamesPrototype = context.createJSObject();
        defineMethod(displayNamesPrototype, "of", 1, JSIntlObject::displayNamesOf);
        defineMethod(displayNamesPrototype, "resolvedOptions", 0, JSIntlObject::displayNamesResolvedOptions);
        defineToStringTag(displayNamesPrototype, "Intl.DisplayNames");
        JSNativeFunction displayNamesConstructor = new JSNativeFunction(context, "DisplayNames", 2, (childContext,
                thisArg, args) -> JSIntlObject.createDisplayNames(childContext, displayNamesPrototype, args), true,
                true);
        installIntlConstructor(intlObject, "DisplayNames", displayNamesConstructor, displayNamesPrototype);

        JSObject durationFormatPrototype = context.createJSObject();
        defineMethod(durationFormatPrototype, "format", 1, JSIntlObject::durationFormatFormat);
        defineMethod(durationFormatPrototype, "formatToParts", 1, JSIntlObject::durationFormatFormatToParts);
        defineMethod(durationFormatPrototype, "resolvedOptions", 0, JSIntlObject::durationFormatResolvedOptions);
        defineToStringTag(durationFormatPrototype, "Intl.DurationFormat");
        JSNativeFunction durationFormatConstructor = new JSNativeFunction(context, "DurationFormat", 0, (childContext,
                thisArg, args) -> JSIntlObject.createDurationFormat(childContext, durationFormatPrototype, args), true,
                true);
        installIntlConstructor(intlObject, "DurationFormat", durationFormatConstructor, durationFormatPrototype);

        JSObject numberFormatPrototype = context.createJSObject();
        defineGetter(numberFormatPrototype, "format", JSIntlObject::numberFormatFormatGetter);
        defineMethod(numberFormatPrototype, "formatRange", 2, JSIntlObject::numberFormatFormatRange);
        defineMethod(numberFormatPrototype, "formatRangeToParts", 2, JSIntlObject::numberFormatFormatRangeToParts);
        defineMethod(numberFormatPrototype, "formatToParts", 1, JSIntlObject::numberFormatFormatToParts);
        defineMethod(numberFormatPrototype, "resolvedOptions", 0, JSIntlObject::numberFormatResolvedOptions);
        defineToStringTag(numberFormatPrototype, "Intl.NumberFormat");
        JSNativeFunction numberFormatConstructor = new JSNativeFunction(context, "NumberFormat", 0, (childContext,
                thisArg, args) -> JSIntlObject.createNumberFormat(childContext, numberFormatPrototype, args), true);
        installIntlConstructor(intlObject, "NumberFormat", numberFormatConstructor, numberFormatPrototype);

        JSObject collatorPrototype = context.createJSObject();
        defineGetter(collatorPrototype, "compare", JSIntlObject::collatorCompareGetter);
        defineMethod(collatorPrototype, "resolvedOptions", 0, JSIntlObject::collatorResolvedOptions);
        defineToStringTag(collatorPrototype, "Intl.Collator");
        JSNativeFunction collatorConstructor = new JSNativeFunction(context, "Collator", 0,
                (childContext, thisArg, args) -> JSIntlObject.createCollator(childContext, collatorPrototype, args),
                true);
        installIntlConstructor(intlObject, "Collator", collatorConstructor, collatorPrototype);

        JSObject pluralRulesPrototype = context.createJSObject();
        defineMethod(pluralRulesPrototype, "select", 1, JSIntlObject::pluralRulesSelect);
        defineMethod(pluralRulesPrototype, "selectRange", 2, JSIntlObject::pluralRulesSelectRange);
        defineMethod(pluralRulesPrototype, "resolvedOptions", 0, JSIntlObject::pluralRulesResolvedOptions);
        defineToStringTag(pluralRulesPrototype, "Intl.PluralRules");
        JSNativeFunction pluralRulesConstructor = new JSNativeFunction(context, "PluralRules", 0, (childContext,
                thisArg, args) -> JSIntlObject.createPluralRules(childContext, pluralRulesPrototype, args), true, true);
        installIntlConstructor(intlObject, "PluralRules", pluralRulesConstructor, pluralRulesPrototype);

        JSObject relativeTimeFormatPrototype = context.createJSObject();
        defineMethod(relativeTimeFormatPrototype, "format", 2, JSIntlObject::relativeTimeFormatFormat);
        defineMethod(relativeTimeFormatPrototype, "formatToParts", 2, JSIntlObject::relativeTimeFormatFormatToParts);
        defineMethod(relativeTimeFormatPrototype, "resolvedOptions", 0,
                JSIntlObject::relativeTimeFormatResolvedOptions);
        defineToStringTag(relativeTimeFormatPrototype, "Intl.RelativeTimeFormat");
        JSNativeFunction relativeTimeFormatConstructor = new JSNativeFunction(context, "RelativeTimeFormat", 0,
                (childContext, thisArg, args) -> JSIntlObject.createRelativeTimeFormat(childContext,
                        relativeTimeFormatPrototype, args),
                true, true);
        installIntlConstructor(intlObject, "RelativeTimeFormat", relativeTimeFormatConstructor,
                relativeTimeFormatPrototype);

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
        JSNativeFunction segmenterConstructor = new JSNativeFunction(context, "Segmenter", 0, (childContext, thisArg,
                args) -> JSIntlObject.createSegmenter(childContext, segmenterPrototype, segmentDataPrototype, args),
                true, true);
        installIntlConstructor(intlObject, "Segmenter", segmenterConstructor, segmenterPrototype);

        JSObject listFormatPrototype = context.createJSObject();
        defineMethod(listFormatPrototype, "format", 1, JSIntlObject::listFormatFormat);
        defineMethod(listFormatPrototype, "formatToParts", 1, JSIntlObject::listFormatFormatToParts);
        defineMethod(listFormatPrototype, "resolvedOptions", 0, JSIntlObject::listFormatResolvedOptions);
        defineToStringTag(listFormatPrototype, "Intl.ListFormat");
        JSNativeFunction listFormatConstructor = new JSNativeFunction(context, "ListFormat", 0,
                (childContext, thisArg, args) -> JSIntlObject.createListFormat(childContext, listFormatPrototype, args),
                true, true);
        installIntlConstructor(intlObject, "ListFormat", listFormatConstructor, listFormatPrototype);

        JSObject localePrototype = context.createJSObject();
        defineMethod(localePrototype, "toString", 0, JSIntlObject::localeToString);
        defineGetter(localePrototype, "baseName", JSIntlObject::localeGetBaseName);
        defineGetter(localePrototype, "calendar", JSIntlObject::localeGetCalendar);
        defineGetter(localePrototype, "caseFirst", JSIntlObject::localeGetCaseFirst);
        defineGetter(localePrototype, "collation", JSIntlObject::localeGetCollation);
        defineGetter(localePrototype, "hourCycle", JSIntlObject::localeGetHourCycle);
        defineGetter(localePrototype, "language", JSIntlObject::localeGetLanguage);
        defineGetter(localePrototype, "numberingSystem", JSIntlObject::localeGetNumberingSystem);
        defineGetter(localePrototype, "numeric", JSIntlObject::localeGetNumeric);
        defineGetter(localePrototype, "script", JSIntlObject::localeGetScript);
        defineGetter(localePrototype, "region", JSIntlObject::localeGetRegion);
        defineGetter(localePrototype, "variants", JSIntlObject::localeGetVariants);
        defineGetter(localePrototype, "firstDayOfWeek", JSIntlObject::localeGetFirstDayOfWeek);
        defineMethod(localePrototype, "maximize", 0, JSIntlObject::localeMaximize);
        defineMethod(localePrototype, "minimize", 0, JSIntlObject::localeMinimize);
        defineMethod(localePrototype, "getCalendars", 0, JSIntlObject::localeGetCalendars);
        defineMethod(localePrototype, "getCollations", 0, JSIntlObject::localeGetCollations);
        defineMethod(localePrototype, "getHourCycles", 0, JSIntlObject::localeGetHourCycles);
        defineMethod(localePrototype, "getNumberingSystems", 0, JSIntlObject::localeGetNumberingSystems);
        defineMethod(localePrototype, "getTextInfo", 0, JSIntlObject::localeGetTextInfo);
        defineMethod(localePrototype, "getTimeZones", 0, JSIntlObject::localeGetTimeZones);
        defineMethod(localePrototype, "getWeekInfo", 0, JSIntlObject::localeGetWeekInfo);
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

        defineMethod(iteratorPrototype, "drop", 1, IteratorPrototype::drop);
        defineMethod(iteratorPrototype, "every", 1, IteratorPrototype::every);
        defineMethod(iteratorPrototype, "filter", 1, IteratorPrototype::filter);
        defineMethod(iteratorPrototype, "find", 1, IteratorPrototype::find);
        defineMethod(iteratorPrototype, "flatMap", 1, IteratorPrototype::flatMap);
        defineMethod(iteratorPrototype, "forEach", 1, IteratorPrototype::forEach);
        defineMethod(iteratorPrototype, "map", 1, IteratorPrototype::map);
        defineMethod(iteratorPrototype, "next", 0, IteratorPrototype::next);
        defineMethod(iteratorPrototype, "reduce", 1, IteratorPrototype::reduce);
        defineMethod(iteratorPrototype, "some", 1, IteratorPrototype::some);
        defineMethod(iteratorPrototype, "take", 1, IteratorPrototype::take);
        defineMethod(iteratorPrototype, "toArray", 0, IteratorPrototype::toArray);
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
        defineMethod(iteratorConstructor, "concat", 0, IteratorPrototype::concat);
        defineMethod(iteratorConstructor, "from", 1, IteratorPrototype::from);
        defineMethod(iteratorConstructor, "zip", 1, IteratorPrototype::zip);
        defineMethod(iteratorConstructor, "zipKeyed", 1, IteratorPrototype::zipKeyed);

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
     * Initialize Map constructor and prototype methods.
     */
    private void initializeMapConstructor() {
        // Create Map.prototype
        JSObject mapPrototype = context.createJSObject();
        defineMethod(mapPrototype, "clear", 0, MapPrototype::clear);
        defineMethod(mapPrototype, "delete", 1, MapPrototype::delete);
        defineMethod(mapPrototype, "forEach", 1, MapPrototype::forEach);
        defineMethod(mapPrototype, "get", 1, MapPrototype::get);
        defineMethod(mapPrototype, "getOrInsert", 2, MapPrototype::getOrInsert);
        defineMethod(mapPrototype, "getOrInsertComputed", 2, MapPrototype::getOrInsertComputed);
        defineMethod(mapPrototype, "has", 1, MapPrototype::has);
        defineMethod(mapPrototype, "set", 2, MapPrototype::set);
        // Create entries function once and use it for both entries and Symbol.iterator (ES spec requirement)
        JSNativeFunction entriesFunction = new JSNativeFunction(context, "entries", 0,
                IteratorPrototype::mapEntriesIterator);
        defineBuiltin(mapPrototype, "entries", entriesFunction);
        defineMethod(mapPrototype, "keys", 0, IteratorPrototype::mapKeysIterator);
        defineMethod(mapPrototype, "values", 0, IteratorPrototype::mapValuesIterator);
        // Map.prototype[Symbol.iterator] is the same function object as entries() (QuickJS uses JS_ALIAS_DEF)
        mapPrototype.defineProperty(PropertyKey.SYMBOL_ITERATOR, entriesFunction,
                PropertyDescriptor.DataState.ConfigurableWritable);

        // Map.prototype.size getter
        defineGetter(mapPrototype, "size", MapPrototype::getSize);
        defineToStringTag(mapPrototype, JSMap.NAME);

        // Create Map constructor as JSNativeFunction
        JSNativeFunction mapConstructor = new JSNativeFunction(context, JSMap.NAME, 0, MapConstructor::call, true,
                true);
        linkConstructor(mapConstructor, mapPrototype, JSConstructorType.MAP);

        // Map static methods
        defineMethod(mapConstructor, "groupBy", 2, MapConstructor::groupBy);
        defineSpeciesGetter(mapConstructor, MapConstructor::getSpecies);

        defineBuiltin(globalObject, JSMap.NAME, mapConstructor);
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
        defineMethod(math, "abs", 1, MathObject::abs);
        defineMethod(math, "acos", 1, MathObject::acos);
        defineMethod(math, "acosh", 1, MathObject::acosh);
        defineMethod(math, "asin", 1, MathObject::asin);
        defineMethod(math, "asinh", 1, MathObject::asinh);
        defineMethod(math, "atan", 1, MathObject::atan);
        defineMethod(math, "atanh", 1, MathObject::atanh);
        defineMethod(math, "atan2", 2, MathObject::atan2);
        defineMethod(math, "cbrt", 1, MathObject::cbrt);
        defineMethod(math, "ceil", 1, MathObject::ceil);
        defineMethod(math, "clz32", 1, MathObject::clz32);
        defineMethod(math, "cos", 1, MathObject::cos);
        defineMethod(math, "cosh", 1, MathObject::cosh);
        defineMethod(math, "exp", 1, MathObject::exp);
        defineMethod(math, "expm1", 1, MathObject::expm1);
        defineMethod(math, "f16round", 1, MathObject::f16round);
        defineMethod(math, "floor", 1, MathObject::floor);
        defineMethod(math, "fround", 1, MathObject::fround);
        defineMethod(math, "hypot", 2, MathObject::hypot);
        defineMethod(math, "imul", 2, MathObject::imul);
        defineMethod(math, "log", 1, MathObject::log);
        defineMethod(math, "log1p", 1, MathObject::log1p);
        defineMethod(math, "log10", 1, MathObject::log10);
        defineMethod(math, "log2", 1, MathObject::log2);
        defineMethod(math, "max", 2, MathObject::max);
        defineMethod(math, "min", 2, MathObject::min);
        defineMethod(math, "pow", 2, MathObject::pow);
        defineMethod(math, "random", 0, MathObject::random);
        defineMethod(math, "round", 1, MathObject::round);
        defineMethod(math, "sign", 1, MathObject::sign);
        defineMethod(math, "sin", 1, MathObject::sin);
        defineMethod(math, "sinh", 1, MathObject::sinh);
        defineMethod(math, "sqrt", 1, MathObject::sqrt);
        defineMethod(math, "sumPrecise", 1, MathObject::sumPrecise);
        defineMethod(math, "tan", 1, MathObject::tan);
        defineMethod(math, "tanh", 1, MathObject::tanh);
        defineMethod(math, "trunc", 1, MathObject::trunc);
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
        defineMethod(numberPrototype, "toExponential", 1, NumberPrototype::toExponential);
        defineMethod(numberPrototype, "toFixed", 1, NumberPrototype::toFixed);
        defineMethod(numberPrototype, "toLocaleString", 0, NumberPrototype::toLocaleString);
        defineMethod(numberPrototype, "toPrecision", 1, NumberPrototype::toPrecision);
        defineMethod(numberPrototype, "toString", 1, NumberPrototype::toString);
        defineMethod(numberPrototype, "valueOf", 0, NumberPrototype::valueOf);

        // Create Number constructor
        JSNativeFunction numberConstructor = new JSNativeFunction(context, JSNumberObject.NAME, 1,
                NumberConstructor::call, true);
        linkConstructor(numberConstructor, numberPrototype, JSConstructorType.NUMBER_OBJECT);

        // Number static methods
        defineMethod(numberConstructor, "isFinite", 1, NumberPrototype::isFinite);
        defineMethod(numberConstructor, "isInteger", 1, NumberPrototype::isInteger);
        defineMethod(numberConstructor, "isNaN", 1, NumberPrototype::isNaN);
        defineMethod(numberConstructor, "isSafeInteger", 1, NumberPrototype::isSafeInteger);

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
        defineMethod(objectPrototype, "__defineGetter__", 2, ObjectPrototype::__defineGetter__);
        defineMethod(objectPrototype, "__defineSetter__", 2, ObjectPrototype::__defineSetter__);
        defineMethod(objectPrototype, "__lookupGetter__", 1, ObjectPrototype::__lookupGetter__);
        defineMethod(objectPrototype, "__lookupSetter__", 1, ObjectPrototype::__lookupSetter__);
        defineMethod(objectPrototype, "hasOwnProperty", 1, ObjectPrototype::hasOwnProperty);
        defineMethod(objectPrototype, "isPrototypeOf", 1, ObjectPrototype::isPrototypeOf);
        defineMethod(objectPrototype, "propertyIsEnumerable", 1, ObjectPrototype::propertyIsEnumerable);
        defineMethod(objectPrototype, "toLocaleString", 0, ObjectPrototype::toLocaleString);
        defineMethod(objectPrototype, "toString", 0, ObjectPrototype::toString);
        defineMethod(objectPrototype, "valueOf", 0, ObjectPrototype::valueOf);

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
        defineMethod(objectConstructor, "assign", 2, ObjectConstructor::assign);
        defineMethod(objectConstructor, "create", 2, ObjectConstructor::create);
        defineMethod(objectConstructor, "defineProperties", 2, ObjectConstructor::defineProperties);
        defineMethod(objectConstructor, "defineProperty", 3, ObjectPrototype::defineProperty);
        defineMethod(objectConstructor, "entries", 1, ObjectConstructor::entries);
        defineMethod(objectConstructor, "freeze", 1, ObjectConstructor::freeze);
        defineMethod(objectConstructor, "fromEntries", 1, ObjectConstructor::fromEntries);
        defineMethod(objectConstructor, "getOwnPropertyDescriptor", 2, ObjectConstructor::getOwnPropertyDescriptor);
        defineMethod(objectConstructor, "getOwnPropertyDescriptors", 1, ObjectConstructor::getOwnPropertyDescriptors);
        defineMethod(objectConstructor, "getOwnPropertyNames", 1, ObjectConstructor::getOwnPropertyNames);
        defineMethod(objectConstructor, "getOwnPropertySymbols", 1, ObjectConstructor::getOwnPropertySymbols);
        defineMethod(objectConstructor, "getPrototypeOf", 1, ObjectConstructor::getPrototypeOf);
        defineMethod(objectConstructor, "groupBy", 2, ObjectConstructor::groupBy);
        defineMethod(objectConstructor, "hasOwn", 2, ObjectConstructor::hasOwn);
        defineMethod(objectConstructor, "is", 2, ObjectConstructor::is);
        defineMethod(objectConstructor, "isExtensible", 1, ObjectConstructor::isExtensible);
        defineMethod(objectConstructor, "isFrozen", 1, ObjectConstructor::isFrozen);
        defineMethod(objectConstructor, "isSealed", 1, ObjectConstructor::isSealed);
        defineMethod(objectConstructor, "keys", 1, ObjectConstructor::keys);
        defineMethod(objectConstructor, "preventExtensions", 1, ObjectConstructor::preventExtensions);
        defineMethod(objectConstructor, "seal", 1, ObjectConstructor::seal);
        defineMethod(objectConstructor, "setPrototypeOf", 2, ObjectConstructor::setPrototypeOf);
        defineMethod(objectConstructor, "values", 1, ObjectConstructor::values);

        defineBuiltin(globalObject, JSObject.NAME, objectConstructor);
    }

    /**
     * Initialize Promise constructor and prototype methods.
     */
    private void initializePromiseConstructor() {
        // Create Promise.prototype
        JSObject promisePrototype = context.createJSObject();
        defineMethod(promisePrototype, "catch", 1, PromisePrototype::catchMethod);
        defineMethod(promisePrototype, "finally", 1, PromisePrototype::finallyMethod);
        defineMethod(promisePrototype, "then", 2, PromisePrototype::then);
        defineToStringTag(promisePrototype, JSPromise.NAME);

        // Create Promise constructor as JSNativeFunction
        JSNativeFunction promiseConstructor = new JSNativeFunction(context, JSPromise.NAME, 1, PromiseConstructor::call,
                true, true);
        context.transferPrototype(promiseConstructor, JSFunction.NAME);
        linkConstructor(promiseConstructor, promisePrototype, JSConstructorType.PROMISE);

        // Static methods
        defineMethod(promiseConstructor, "all", 1, PromiseConstructor::all);
        defineMethod(promiseConstructor, "allKeyed", 1, PromiseConstructor::allKeyed);
        defineMethod(promiseConstructor, "allSettled", 1, PromiseConstructor::allSettled);
        defineMethod(promiseConstructor, "allSettledKeyed", 1, PromiseConstructor::allSettledKeyed);
        defineMethod(promiseConstructor, "any", 1, PromiseConstructor::any);
        defineMethod(promiseConstructor, "race", 1, PromiseConstructor::race);
        defineMethod(promiseConstructor, "reject", 1, PromiseConstructor::reject);
        defineMethod(promiseConstructor, "resolve", 1, PromiseConstructor::resolve);
        defineMethod(promiseConstructor, "try", 1, PromiseConstructor::tryMethod);
        defineMethod(promiseConstructor, "withResolvers", 0, PromiseConstructor::withResolvers);
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
        defineMethod(reflect, "apply", 3, JSReflectObject::apply);
        defineMethod(reflect, "construct", 2, JSReflectObject::construct);
        defineMethod(reflect, "defineProperty", 3, JSReflectObject::defineProperty);
        defineMethod(reflect, "deleteProperty", 2, JSReflectObject::deleteProperty);
        defineMethod(reflect, "get", 2, JSReflectObject::get);
        defineMethod(reflect, "getOwnPropertyDescriptor", 2, JSReflectObject::getOwnPropertyDescriptor);
        defineMethod(reflect, "getPrototypeOf", 1, JSReflectObject::getPrototypeOf);
        defineMethod(reflect, "has", 2, JSReflectObject::has);
        defineMethod(reflect, "isExtensible", 1, JSReflectObject::isExtensible);
        defineMethod(reflect, "ownKeys", 1, JSReflectObject::ownKeys);
        defineMethod(reflect, "preventExtensions", 1, JSReflectObject::preventExtensions);
        defineMethod(reflect, "set", 3, JSReflectObject::set);
        defineMethod(reflect, "setPrototypeOf", 2, JSReflectObject::setPrototypeOf);
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
        defineGetter(regexpPrototype, "dotAll", RegExpPrototype::getDotAllAccessor);
        defineGetter(regexpPrototype, "flags", RegExpPrototype::getFlagsAccessor);
        defineGetter(regexpPrototype, "global", RegExpPrototype::getGlobalAccessor);
        defineGetter(regexpPrototype, "hasIndices", RegExpPrototype::getHasIndicesAccessor);
        defineGetter(regexpPrototype, "ignoreCase", RegExpPrototype::getIgnoreCaseAccessor);
        defineGetter(regexpPrototype, "multiline", RegExpPrototype::getMultilineAccessor);
        defineGetter(regexpPrototype, "source", RegExpPrototype::getSourceAccessor);
        defineGetter(regexpPrototype, "sticky", RegExpPrototype::getStickyAccessor);
        defineGetter(regexpPrototype, "unicode", RegExpPrototype::getUnicodeAccessor);
        defineGetter(regexpPrototype, "unicodeSets", RegExpPrototype::getUnicodeSetsAccessor);

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
     * Initialize Set constructor and prototype methods.
     */
    private void initializeSetConstructor() {
        // Create Set.prototype
        JSObject setPrototype = context.createJSObject();
        defineMethod(setPrototype, "add", 1, SetPrototype::add);
        defineMethod(setPrototype, "clear", 0, SetPrototype::clear);
        defineMethod(setPrototype, "delete", 1, SetPrototype::delete);
        defineMethod(setPrototype, "difference", 1, SetPrototype::difference);
        defineMethod(setPrototype, "entries", 0, SetPrototype::entries);
        defineMethod(setPrototype, "forEach", 1, SetPrototype::forEach);
        defineMethod(setPrototype, "has", 1, SetPrototype::has);
        defineMethod(setPrototype, "intersection", 1, SetPrototype::intersection);
        defineMethod(setPrototype, "isDisjointFrom", 1, SetPrototype::isDisjointFrom);
        defineMethod(setPrototype, "isSubsetOf", 1, SetPrototype::isSubsetOf);
        defineMethod(setPrototype, "isSupersetOf", 1, SetPrototype::isSupersetOf);
        defineMethod(setPrototype, "symmetricDifference", 1, SetPrototype::symmetricDifference);
        defineMethod(setPrototype, "union", 1, SetPrototype::union);

        // Create values function - keys and Symbol.iterator will alias to this
        JSNativeFunction valuesFunction = new JSNativeFunction(context, "values", 0, SetPrototype::values);
        defineBuiltin(setPrototype, "values", valuesFunction);
        // Set.prototype.keys is the same function object as values (ES spec requirement)
        setPrototype.defineProperty(PropertyKey.KEYS, valuesFunction,
                PropertyDescriptor.DataState.ConfigurableWritable);
        // Set.prototype[Symbol.iterator] is the same as values()
        setPrototype.defineProperty(PropertyKey.SYMBOL_ITERATOR, valuesFunction,
                PropertyDescriptor.DataState.ConfigurableWritable);

        // Set.prototype.size getter
        defineGetter(setPrototype, "size", SetPrototype::getSize);
        defineToStringTag(setPrototype, JSSet.NAME);

        // Create Set constructor as a function
        JSNativeFunction setConstructor = new JSNativeFunction(context, JSSet.NAME, 0, SetConstructor::call, true,
                true);
        linkConstructor(setConstructor, setPrototype, JSConstructorType.SET);

        defineMethod(setConstructor, "groupBy", 2, SetConstructor::groupBy);
        defineSpeciesGetter(setConstructor, SetConstructor::getSpecies);

        defineBuiltin(globalObject, JSSet.NAME, setConstructor);
    }

    /**
     * Initialize ShadowRealm constructor and prototype. ShadowRealm is not provided by QuickJS; implemented per
     * proposal/test262 behavior.
     */
    private void initializeShadowRealmConstructor() {
        JSObject shadowRealmPrototype = context.createJSObject();
        defineMethod(shadowRealmPrototype, "evaluate", 1, ShadowRealmPrototype::evaluate);
        defineMethod(shadowRealmPrototype, "importValue", 2, ShadowRealmPrototype::importValue);
        defineToStringTag(shadowRealmPrototype, JSShadowRealm.NAME);

        JSNativeFunction shadowRealmConstructor = new JSNativeFunction(context, JSShadowRealm.NAME, 0,
                ShadowRealmConstructor::call, true, true);
        linkConstructor(shadowRealmConstructor, shadowRealmPrototype);

        defineBuiltin(globalObject, JSShadowRealm.NAME, shadowRealmConstructor);
    }

    /**
     * Initialize SharedArrayBuffer constructor and prototype.
     */
    private void initializeSharedArrayBufferConstructor() {
        // Create SharedArrayBuffer.prototype
        JSObject sharedArrayBufferPrototype = context.createJSObject();
        defineMethod(sharedArrayBufferPrototype, "grow", 1, SharedArrayBufferPrototype::grow);
        defineMethod(sharedArrayBufferPrototype, "slice", 2, SharedArrayBufferPrototype::slice);

        // Define getter properties
        defineGetter(sharedArrayBufferPrototype, "byteLength", SharedArrayBufferPrototype::getByteLength);
        defineGetter(sharedArrayBufferPrototype, "growable", SharedArrayBufferPrototype::getGrowable);
        defineGetter(sharedArrayBufferPrototype, "maxByteLength", SharedArrayBufferPrototype::getMaxByteLength);
        defineToStringTag(sharedArrayBufferPrototype, JSSharedArrayBuffer.NAME);

        // Create SharedArrayBuffer constructor as a function
        JSNativeFunction sharedArrayBufferConstructor = new JSNativeFunction(context, JSSharedArrayBuffer.NAME, 1,
                SharedArrayBufferConstructor::call, true, // isConstructor
                true // requiresNew - SharedArrayBuffer() must be called with new
        );
        linkConstructor(sharedArrayBufferConstructor, sharedArrayBufferPrototype,
                JSConstructorType.SHARED_ARRAY_BUFFER);
        defineSpeciesGetter(sharedArrayBufferConstructor, SharedArrayBufferConstructor::getSpecies);

        defineBuiltin(globalObject, JSSharedArrayBuffer.NAME, sharedArrayBufferConstructor);
    }

    /**
     * Initialize String constructor and prototype.
     */
    private void initializeStringConstructor() {
        // Create String.prototype - per ES spec, it is itself a String object whose value is ""
        JSObject stringPrototype = new JSStringObject(context);
        context.transferPrototype(stringPrototype, JSObject.NAME);
        defineMethod(stringPrototype, "at", 1, StringPrototype::at);
        defineMethod(stringPrototype, "charAt", 1, StringPrototype::charAt);
        defineMethod(stringPrototype, "charCodeAt", 1, StringPrototype::charCodeAt);
        defineMethod(stringPrototype, "codePointAt", 1, StringPrototype::codePointAt);
        defineMethod(stringPrototype, "concat", 1, StringPrototype::concat);
        defineMethod(stringPrototype, "endsWith", 1, StringPrototype::endsWith);
        defineMethod(stringPrototype, "includes", 1, StringPrototype::includes);
        defineMethod(stringPrototype, "indexOf", 1, StringPrototype::indexOf);
        defineMethod(stringPrototype, "lastIndexOf", 1, StringPrototype::lastIndexOf);
        defineMethod(stringPrototype, "localeCompare", 1, StringPrototype::localeCompare);
        defineMethod(stringPrototype, "match", 1, StringPrototype::match);
        defineMethod(stringPrototype, "matchAll", 1, StringPrototype::matchAll);
        defineMethod(stringPrototype, "padEnd", 1, StringPrototype::padEnd);
        defineMethod(stringPrototype, "padStart", 1, StringPrototype::padStart);
        defineMethod(stringPrototype, "repeat", 1, StringPrototype::repeat);
        defineMethod(stringPrototype, "replace", 2, StringPrototype::replace);
        defineMethod(stringPrototype, "replaceAll", 2, StringPrototype::replaceAll);
        defineMethod(stringPrototype, "search", 1, StringPrototype::search);
        defineMethod(stringPrototype, "slice", 2, StringPrototype::slice);
        defineMethod(stringPrototype, "split", 2, StringPrototype::split);
        defineMethod(stringPrototype, "startsWith", 1, StringPrototype::startsWith);
        defineMethod(stringPrototype, "substr", 2, StringPrototype::substr);
        defineMethod(stringPrototype, "substring", 2, StringPrototype::substring);
        defineMethod(stringPrototype, "toLowerCase", 0, StringPrototype::toLowerCase);
        defineMethod(stringPrototype, "toString", 0, StringPrototype::toString_);
        defineMethod(stringPrototype, "toUpperCase", 0, StringPrototype::toUpperCase);
        defineMethod(stringPrototype, "trim", 0, StringPrototype::trim);
        JSNativeFunction trimEnd = new JSNativeFunction(context, "trimEnd", 0, StringPrototype::trimEnd);
        JSNativeFunction trimStart = new JSNativeFunction(context, "trimStart", 0, StringPrototype::trimStart);
        defineBuiltin(stringPrototype, "trimEnd", trimEnd);
        defineBuiltin(stringPrototype, "trimRight", trimEnd);
        defineBuiltin(stringPrototype, "trimStart", trimStart);
        defineBuiltin(stringPrototype, "trimLeft", trimStart);
        defineMethod(stringPrototype, "valueOf", 0, StringPrototype::valueOf);

        // HTML wrapper methods (deprecated but still part of spec)
        defineMethod(stringPrototype, "anchor", 1, StringPrototype::anchor);
        defineMethod(stringPrototype, "big", 0, StringPrototype::big);
        defineMethod(stringPrototype, "blink", 0, StringPrototype::blink);
        defineMethod(stringPrototype, "bold", 0, StringPrototype::bold);
        defineMethod(stringPrototype, "fixed", 0, StringPrototype::fixed);
        defineMethod(stringPrototype, "fontcolor", 1, StringPrototype::fontcolor);
        defineMethod(stringPrototype, "fontsize", 1, StringPrototype::fontsize);
        defineMethod(stringPrototype, "italics", 0, StringPrototype::italics);
        defineMethod(stringPrototype, "link", 1, StringPrototype::link);
        defineMethod(stringPrototype, "small", 0, StringPrototype::small);
        defineMethod(stringPrototype, "strike", 0, StringPrototype::strike);
        defineMethod(stringPrototype, "sub", 0, StringPrototype::sub);
        defineMethod(stringPrototype, "sup", 0, StringPrototype::sup);

        // Unicode methods
        defineMethod(stringPrototype, "isWellFormed", 0, StringPrototype::isWellFormed);
        defineMethod(stringPrototype, "normalize", 0, StringPrototype::normalize);
        defineMethod(stringPrototype, "toLocaleLowerCase", 0, StringPrototype::toLocaleLowerCase);
        defineMethod(stringPrototype, "toLocaleUpperCase", 0, StringPrototype::toLocaleUpperCase);
        defineMethod(stringPrototype, "toWellFormed", 0, StringPrototype::toWellFormed);

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
        defineMethod(stringConstructor, "fromCharCode", 1, StringConstructor::fromCharCode);
        defineMethod(stringConstructor, "fromCodePoint", 1, StringConstructor::fromCodePoint);
        defineMethod(stringConstructor, "raw", 1, StringConstructor::raw);

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

    private void initializeTemporalDuration(JSObject temporalObject) {
        JSObject prototype = createTemporalPrototype("Temporal.Duration");

        // Getters
        defineGetter(prototype, "years", TemporalDurationPrototype::years);
        defineGetter(prototype, "months", TemporalDurationPrototype::months);
        defineGetter(prototype, "weeks", TemporalDurationPrototype::weeks);
        defineGetter(prototype, "days", TemporalDurationPrototype::days);
        defineGetter(prototype, "hours", TemporalDurationPrototype::hours);
        defineGetter(prototype, "minutes", TemporalDurationPrototype::minutes);
        defineGetter(prototype, "seconds", TemporalDurationPrototype::seconds);
        defineGetter(prototype, "milliseconds", TemporalDurationPrototype::milliseconds);
        defineGetter(prototype, "microseconds", TemporalDurationPrototype::microseconds);
        defineGetter(prototype, "nanoseconds", TemporalDurationPrototype::nanoseconds);
        defineGetter(prototype, "sign", TemporalDurationPrototype::sign);
        defineGetter(prototype, "blank", TemporalDurationPrototype::blank);

        // Methods
        defineMethod(prototype, "with", 1, TemporalDurationPrototype::with);
        defineMethod(prototype, "negated", 0, TemporalDurationPrototype::negated);
        defineMethod(prototype, "abs", 0, TemporalDurationPrototype::abs);
        defineMethod(prototype, "add", 1, TemporalDurationPrototype::add);
        defineMethod(prototype, "subtract", 1, TemporalDurationPrototype::subtract);
        defineMethod(prototype, "round", 1, TemporalDurationPrototype::round);
        defineMethod(prototype, "total", 1, TemporalDurationPrototype::total);
        defineMethod(prototype, "toString", 0, TemporalDurationPrototype::toStringMethod);
        defineMethod(prototype, "toJSON", 0, TemporalDurationPrototype::toJSON);
        defineMethod(prototype, "toLocaleString", 0, TemporalDurationPrototype::toLocaleString);
        defineMethod(prototype, "valueOf", 0, TemporalDurationPrototype::valueOf);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "Duration", 0,
                TemporalDurationConstructor::construct, true, false);
        defineMethod(constructor, "from", 1, TemporalDurationConstructor::from);
        defineMethod(constructor, "compare", 2, TemporalDurationConstructor::compare);

        installTemporalConstructor(temporalObject, "Duration", constructor, prototype);
    }

    private void initializeTemporalInstant(JSObject temporalObject) {
        JSObject prototype = createTemporalPrototype("Temporal.Instant");

        // Getters
        defineGetter(prototype, "epochMilliseconds", TemporalInstantPrototype::epochMilliseconds);
        defineGetter(prototype, "epochNanoseconds", TemporalInstantPrototype::epochNanoseconds);

        // Methods
        defineMethod(prototype, "add", 1, TemporalInstantPrototype::add);
        defineMethod(prototype, "subtract", 1, TemporalInstantPrototype::subtract);
        defineMethod(prototype, "until", 1, TemporalInstantPrototype::until);
        defineMethod(prototype, "since", 1, TemporalInstantPrototype::since);
        defineMethod(prototype, "round", 1, TemporalInstantPrototype::round);
        defineMethod(prototype, "equals", 1, TemporalInstantPrototype::equals);
        defineMethod(prototype, "toString", 0, TemporalInstantPrototype::toStringMethod);
        defineMethod(prototype, "toJSON", 0, TemporalInstantPrototype::toJSON);
        defineMethod(prototype, "toLocaleString", 0, TemporalInstantPrototype::toLocaleString);
        defineMethod(prototype, "valueOf", 0, TemporalInstantPrototype::valueOf);
        defineMethod(prototype, "toZonedDateTimeISO", 1, TemporalInstantPrototype::toZonedDateTimeISO);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "Instant", 1,
                TemporalInstantConstructor::construct, true, false);
        defineMethod(constructor, "from", 1, TemporalInstantConstructor::from);
        defineMethod(constructor, "fromEpochMilliseconds", 1, TemporalInstantConstructor::fromEpochMilliseconds);
        defineMethod(constructor, "fromEpochNanoseconds", 1, TemporalInstantConstructor::fromEpochNanoseconds);
        defineMethod(constructor, "compare", 2, TemporalInstantConstructor::compare);

        installTemporalConstructor(temporalObject, "Instant", constructor, prototype);
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

        // Temporal.Duration (registered before PlainDateTime so Duration prototype is available)
        initializeTemporalDuration(temporalObject);

        // Temporal.PlainDate
        initializeTemporalPlainDate(temporalObject);

        // Temporal.PlainTime
        initializeTemporalPlainTime(temporalObject);

        // Temporal.PlainDateTime
        initializeTemporalPlainDateTime(temporalObject);

        // Temporal.PlainYearMonth
        initializeTemporalPlainYearMonth(temporalObject);

        // Temporal.PlainMonthDay
        initializeTemporalPlainMonthDay(temporalObject);

        // Temporal.Instant
        initializeTemporalInstant(temporalObject);

        // Temporal.ZonedDateTime
        initializeTemporalZonedDateTime(temporalObject);

        // Temporal.Now
        initializeTemporalNow(temporalObject);
    }

    private void initializeTemporalPlainDate(JSObject temporalObject) {
        JSObject prototype = createTemporalPrototype("Temporal.PlainDate");

        // Getters (registered as zero-arg functions accessed as properties via get accessor)
        defineGetter(prototype, "calendarId", TemporalPlainDatePrototype::calendarId);
        defineGetter(prototype, "day", TemporalPlainDatePrototype::day);
        defineGetter(prototype, "dayOfWeek", TemporalPlainDatePrototype::dayOfWeek);
        defineGetter(prototype, "dayOfYear", TemporalPlainDatePrototype::dayOfYear);
        defineGetter(prototype, "daysInMonth", TemporalPlainDatePrototype::daysInMonth);
        defineGetter(prototype, "daysInWeek", TemporalPlainDatePrototype::daysInWeek);
        defineGetter(prototype, "daysInYear", TemporalPlainDatePrototype::daysInYear);
        defineGetter(prototype, "era", TemporalPlainDatePrototype::era);
        defineGetter(prototype, "eraYear", TemporalPlainDatePrototype::eraYear);
        defineGetter(prototype, "inLeapYear", TemporalPlainDatePrototype::inLeapYear);
        defineGetter(prototype, "month", TemporalPlainDatePrototype::month);
        defineGetter(prototype, "monthCode", TemporalPlainDatePrototype::monthCode);
        defineGetter(prototype, "monthsInYear", TemporalPlainDatePrototype::monthsInYear);
        defineGetter(prototype, "weekOfYear", TemporalPlainDatePrototype::weekOfYear);
        defineGetter(prototype, "year", TemporalPlainDatePrototype::year);
        defineGetter(prototype, "yearOfWeek", TemporalPlainDatePrototype::yearOfWeek);

        // Methods
        defineMethod(prototype, "add", 1, TemporalPlainDatePrototype::add);
        defineMethod(prototype, "equals", 1, TemporalPlainDatePrototype::equals);
        defineMethod(prototype, "since", 1, TemporalPlainDatePrototype::since);
        defineMethod(prototype, "subtract", 1, TemporalPlainDatePrototype::subtract);
        defineMethod(prototype, "toJSON", 0, TemporalPlainDatePrototype::toJSON);
        defineMethod(prototype, "toLocaleString", 0, TemporalPlainDatePrototype::toLocaleString);
        defineMethod(prototype, "toPlainDateTime", 0, TemporalPlainDatePrototype::toPlainDateTime);
        defineMethod(prototype, "toPlainMonthDay", 0, TemporalPlainDatePrototype::toPlainMonthDay);
        defineMethod(prototype, "toPlainYearMonth", 0, TemporalPlainDatePrototype::toPlainYearMonth);
        defineMethod(prototype, "toString", 0, TemporalPlainDatePrototype::toStringMethod);
        defineMethod(prototype, "toZonedDateTime", 1, TemporalPlainDatePrototype::toZonedDateTime);
        defineMethod(prototype, "until", 1, TemporalPlainDatePrototype::until);
        defineMethod(prototype, "valueOf", 0, TemporalPlainDatePrototype::valueOf);
        defineMethod(prototype, "with", 1, TemporalPlainDatePrototype::with);
        defineMethod(prototype, "withCalendar", 1, TemporalPlainDatePrototype::withCalendar);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "PlainDate", 3,
                TemporalPlainDateConstructor::construct, true, false);
        defineMethod(constructor, "compare", 2, TemporalPlainDateConstructor::compare);
        defineMethod(constructor, "from", 1, TemporalPlainDateConstructor::from);

        installTemporalConstructor(temporalObject, "PlainDate", constructor, prototype);
    }

    private void initializeTemporalPlainDateTime(JSObject temporalObject) {
        JSObject prototype = createTemporalPrototype("Temporal.PlainDateTime");

        // Getters
        defineGetter(prototype, "calendarId", TemporalPlainDateTimePrototype::calendarId);
        defineGetter(prototype, "day", TemporalPlainDateTimePrototype::day);
        defineGetter(prototype, "dayOfWeek", TemporalPlainDateTimePrototype::dayOfWeek);
        defineGetter(prototype, "dayOfYear", TemporalPlainDateTimePrototype::dayOfYear);
        defineGetter(prototype, "daysInMonth", TemporalPlainDateTimePrototype::daysInMonth);
        defineGetter(prototype, "daysInWeek", TemporalPlainDateTimePrototype::daysInWeek);
        defineGetter(prototype, "daysInYear", TemporalPlainDateTimePrototype::daysInYear);
        defineGetter(prototype, "era", TemporalPlainDateTimePrototype::era);
        defineGetter(prototype, "eraYear", TemporalPlainDateTimePrototype::eraYear);
        defineGetter(prototype, "hour", TemporalPlainDateTimePrototype::hour);
        defineGetter(prototype, "inLeapYear", TemporalPlainDateTimePrototype::inLeapYear);
        defineGetter(prototype, "microsecond", TemporalPlainDateTimePrototype::microsecond);
        defineGetter(prototype, "millisecond", TemporalPlainDateTimePrototype::millisecond);
        defineGetter(prototype, "minute", TemporalPlainDateTimePrototype::minute);
        defineGetter(prototype, "month", TemporalPlainDateTimePrototype::month);
        defineGetter(prototype, "monthCode", TemporalPlainDateTimePrototype::monthCode);
        defineGetter(prototype, "monthsInYear", TemporalPlainDateTimePrototype::monthsInYear);
        defineGetter(prototype, "nanosecond", TemporalPlainDateTimePrototype::nanosecond);
        defineGetter(prototype, "second", TemporalPlainDateTimePrototype::second);
        defineGetter(prototype, "weekOfYear", TemporalPlainDateTimePrototype::weekOfYear);
        defineGetter(prototype, "year", TemporalPlainDateTimePrototype::year);
        defineGetter(prototype, "yearOfWeek", TemporalPlainDateTimePrototype::yearOfWeek);

        // Methods
        defineMethod(prototype, "add", 1, TemporalPlainDateTimePrototype::add);
        defineMethod(prototype, "equals", 1, TemporalPlainDateTimePrototype::equals);
        defineMethod(prototype, "round", 1, TemporalPlainDateTimePrototype::round);
        defineMethod(prototype, "since", 1, TemporalPlainDateTimePrototype::since);
        defineMethod(prototype, "subtract", 1, TemporalPlainDateTimePrototype::subtract);
        defineMethod(prototype, "toJSON", 0, TemporalPlainDateTimePrototype::toJSON);
        defineMethod(prototype, "toLocaleString", 0, TemporalPlainDateTimePrototype::toLocaleString);
        defineMethod(prototype, "toPlainDate", 0, TemporalPlainDateTimePrototype::toPlainDate);
        defineMethod(prototype, "toPlainTime", 0, TemporalPlainDateTimePrototype::toPlainTime);
        defineMethod(prototype, "toString", 0, TemporalPlainDateTimePrototype::toStringMethod);
        defineMethod(prototype, "toZonedDateTime", 1, TemporalPlainDateTimePrototype::toZonedDateTime);
        defineMethod(prototype, "until", 1, TemporalPlainDateTimePrototype::until);
        defineMethod(prototype, "valueOf", 0, TemporalPlainDateTimePrototype::valueOf);
        defineMethod(prototype, "with", 1, TemporalPlainDateTimePrototype::with);
        defineMethod(prototype, "withCalendar", 1, TemporalPlainDateTimePrototype::withCalendar);
        defineMethod(prototype, "withPlainTime", 0, TemporalPlainDateTimePrototype::withPlainTime);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "PlainDateTime", 3,
                TemporalPlainDateTimeConstructor::construct, true, false);
        defineMethod(constructor, "from", 1, TemporalPlainDateTimeConstructor::from);
        defineMethod(constructor, "compare", 2, TemporalPlainDateTimeConstructor::compare);

        installTemporalConstructor(temporalObject, "PlainDateTime", constructor, prototype);
    }

    private void initializeTemporalPlainMonthDay(JSObject temporalObject) {
        JSObject prototype = createTemporalPrototype("Temporal.PlainMonthDay");

        // Getters
        defineGetter(prototype, "calendarId", TemporalPlainMonthDayPrototype::calendarId);
        defineGetter(prototype, "day", TemporalPlainMonthDayPrototype::day);
        defineGetter(prototype, "monthCode", TemporalPlainMonthDayPrototype::monthCode);
        defineGetter(prototype, "referenceISOYear", TemporalPlainMonthDayPrototype::referenceISOYear);

        // Methods
        defineMethod(prototype, "equals", 1, TemporalPlainMonthDayPrototype::equals);
        defineMethod(prototype, "toJSON", 0, TemporalPlainMonthDayPrototype::toJSON);
        defineMethod(prototype, "toLocaleString", 0, TemporalPlainMonthDayPrototype::toLocaleString);
        defineMethod(prototype, "toPlainDate", 1, TemporalPlainMonthDayPrototype::toPlainDate);
        defineMethod(prototype, "toString", 0, TemporalPlainMonthDayPrototype::toStringMethod);
        defineMethod(prototype, "valueOf", 0, TemporalPlainMonthDayPrototype::valueOf);
        defineMethod(prototype, "with", 1, TemporalPlainMonthDayPrototype::with);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "PlainMonthDay", 2,
                TemporalPlainMonthDayConstructor::construct, true, false);
        defineMethod(constructor, "from", 1, TemporalPlainMonthDayConstructor::from);

        installTemporalConstructor(temporalObject, "PlainMonthDay", constructor, prototype);
    }

    private void initializeTemporalPlainTime(JSObject temporalObject) {
        JSObject prototype = createTemporalPrototype("Temporal.PlainTime");

        // Getters
        defineGetter(prototype, "hour", TemporalPlainTimePrototype::hour);
        defineGetter(prototype, "microsecond", TemporalPlainTimePrototype::microsecond);
        defineGetter(prototype, "millisecond", TemporalPlainTimePrototype::millisecond);
        defineGetter(prototype, "minute", TemporalPlainTimePrototype::minute);
        defineGetter(prototype, "nanosecond", TemporalPlainTimePrototype::nanosecond);
        defineGetter(prototype, "second", TemporalPlainTimePrototype::second);

        // Methods
        defineMethod(prototype, "add", 1, TemporalPlainTimePrototype::add);
        defineMethod(prototype, "equals", 1, TemporalPlainTimePrototype::equals);
        defineMethod(prototype, "round", 1, TemporalPlainTimePrototype::round);
        defineMethod(prototype, "since", 1, TemporalPlainTimePrototype::since);
        defineMethod(prototype, "subtract", 1, TemporalPlainTimePrototype::subtract);
        defineMethod(prototype, "toJSON", 0, TemporalPlainTimePrototype::toJSON);
        defineMethod(prototype, "toLocaleString", 0, TemporalPlainTimePrototype::toLocaleString);
        defineMethod(prototype, "toString", 0, TemporalPlainTimePrototype::toStringMethod);
        defineMethod(prototype, "until", 1, TemporalPlainTimePrototype::until);
        defineMethod(prototype, "valueOf", 0, TemporalPlainTimePrototype::valueOf);
        defineMethod(prototype, "with", 1, TemporalPlainTimePrototype::with);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "PlainTime", 0,
                TemporalPlainTimeConstructor::construct, true, false);
        defineMethod(constructor, "compare", 2, TemporalPlainTimeConstructor::compare);
        defineMethod(constructor, "from", 1, TemporalPlainTimeConstructor::from);

        installTemporalConstructor(temporalObject, "PlainTime", constructor, prototype);
    }

    private void initializeTemporalPlainYearMonth(JSObject temporalObject) {
        JSObject prototype = createTemporalPrototype("Temporal.PlainYearMonth");

        // Getters
        defineGetter(prototype, "calendarId", TemporalPlainYearMonthPrototype::calendarId);
        defineGetter(prototype, "daysInMonth", TemporalPlainYearMonthPrototype::daysInMonth);
        defineGetter(prototype, "daysInYear", TemporalPlainYearMonthPrototype::daysInYear);
        defineGetter(prototype, "era", TemporalPlainYearMonthPrototype::era);
        defineGetter(prototype, "eraYear", TemporalPlainYearMonthPrototype::eraYear);
        defineGetter(prototype, "inLeapYear", TemporalPlainYearMonthPrototype::inLeapYear);
        defineGetter(prototype, "month", TemporalPlainYearMonthPrototype::month);
        defineGetter(prototype, "monthCode", TemporalPlainYearMonthPrototype::monthCode);
        defineGetter(prototype, "monthsInYear", TemporalPlainYearMonthPrototype::monthsInYear);
        defineGetter(prototype, "referenceISODay", TemporalPlainYearMonthPrototype::referenceISODay);
        defineGetter(prototype, "year", TemporalPlainYearMonthPrototype::year);

        // Methods
        defineMethod(prototype, "add", 1, TemporalPlainYearMonthPrototype::add);
        defineMethod(prototype, "equals", 1, TemporalPlainYearMonthPrototype::equals);
        defineMethod(prototype, "since", 1, TemporalPlainYearMonthPrototype::since);
        defineMethod(prototype, "subtract", 1, TemporalPlainYearMonthPrototype::subtract);
        defineMethod(prototype, "toJSON", 0, TemporalPlainYearMonthPrototype::toJSON);
        defineMethod(prototype, "toLocaleString", 0, TemporalPlainYearMonthPrototype::toLocaleString);
        defineMethod(prototype, "toPlainDate", 1, TemporalPlainYearMonthPrototype::toPlainDate);
        defineMethod(prototype, "toString", 0, TemporalPlainYearMonthPrototype::toStringMethod);
        defineMethod(prototype, "until", 1, TemporalPlainYearMonthPrototype::until);
        defineMethod(prototype, "valueOf", 0, TemporalPlainYearMonthPrototype::valueOf);
        defineMethod(prototype, "with", 1, TemporalPlainYearMonthPrototype::with);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "PlainYearMonth", 2,
                TemporalPlainYearMonthConstructor::construct, true, false);
        defineMethod(constructor, "compare", 2, TemporalPlainYearMonthConstructor::compare);
        defineMethod(constructor, "from", 1, TemporalPlainYearMonthConstructor::from);

        installTemporalConstructor(temporalObject, "PlainYearMonth", constructor, prototype);
    }

    private void initializeTemporalZonedDateTime(JSObject temporalObject) {
        JSObject prototype = createTemporalPrototype("Temporal.ZonedDateTime");

        // Getters (28)
        defineGetter(prototype, "calendar", TemporalZonedDateTimePrototype::calendar);
        defineGetter(prototype, "calendarId", TemporalZonedDateTimePrototype::calendarId);
        defineGetter(prototype, "day", TemporalZonedDateTimePrototype::day);
        defineGetter(prototype, "dayOfWeek", TemporalZonedDateTimePrototype::dayOfWeek);
        defineGetter(prototype, "dayOfYear", TemporalZonedDateTimePrototype::dayOfYear);
        defineGetter(prototype, "daysInMonth", TemporalZonedDateTimePrototype::daysInMonth);
        defineGetter(prototype, "daysInWeek", TemporalZonedDateTimePrototype::daysInWeek);
        defineGetter(prototype, "daysInYear", TemporalZonedDateTimePrototype::daysInYear);
        defineGetter(prototype, "epochMilliseconds", TemporalZonedDateTimePrototype::epochMilliseconds);
        defineGetter(prototype, "epochNanoseconds", TemporalZonedDateTimePrototype::epochNanoseconds);
        defineGetter(prototype, "era", TemporalZonedDateTimePrototype::era);
        defineGetter(prototype, "eraYear", TemporalZonedDateTimePrototype::eraYear);
        defineGetter(prototype, "hour", TemporalZonedDateTimePrototype::hour);
        defineGetter(prototype, "hoursInDay", TemporalZonedDateTimePrototype::hoursInDay);
        defineGetter(prototype, "inLeapYear", TemporalZonedDateTimePrototype::inLeapYear);
        defineGetter(prototype, "microsecond", TemporalZonedDateTimePrototype::microsecond);
        defineGetter(prototype, "millisecond", TemporalZonedDateTimePrototype::millisecond);
        defineGetter(prototype, "minute", TemporalZonedDateTimePrototype::minute);
        defineGetter(prototype, "month", TemporalZonedDateTimePrototype::month);
        defineGetter(prototype, "monthCode", TemporalZonedDateTimePrototype::monthCode);
        defineGetter(prototype, "monthsInYear", TemporalZonedDateTimePrototype::monthsInYear);
        defineGetter(prototype, "nanosecond", TemporalZonedDateTimePrototype::nanosecond);
        defineGetter(prototype, "offset", TemporalZonedDateTimePrototype::offset);
        defineGetter(prototype, "offsetNanoseconds", TemporalZonedDateTimePrototype::offsetNanoseconds);
        defineGetter(prototype, "second", TemporalZonedDateTimePrototype::second);
        defineGetter(prototype, "timeZoneId", TemporalZonedDateTimePrototype::timeZoneId);
        defineGetter(prototype, "weekOfYear", TemporalZonedDateTimePrototype::weekOfYear);
        defineGetter(prototype, "year", TemporalZonedDateTimePrototype::year);
        defineGetter(prototype, "yearOfWeek", TemporalZonedDateTimePrototype::yearOfWeek);

        // Methods (20)
        defineMethod(prototype, "add", 1, TemporalZonedDateTimePrototype::add);
        defineMethod(prototype, "equals", 1, TemporalZonedDateTimePrototype::equals);
        defineMethod(prototype, "getTimeZoneTransition", 1, TemporalZonedDateTimePrototype::getTimeZoneTransition);
        defineMethod(prototype, "round", 1, TemporalZonedDateTimePrototype::round);
        defineMethod(prototype, "since", 1, TemporalZonedDateTimePrototype::since);
        defineMethod(prototype, "startOfDay", 0, TemporalZonedDateTimePrototype::startOfDay);
        defineMethod(prototype, "subtract", 1, TemporalZonedDateTimePrototype::subtract);
        defineMethod(prototype, "toInstant", 0, TemporalZonedDateTimePrototype::toInstant);
        defineMethod(prototype, "toJSON", 0, TemporalZonedDateTimePrototype::toJSON);
        defineMethod(prototype, "toLocaleString", 0, TemporalZonedDateTimePrototype::toLocaleString);
        defineMethod(prototype, "toPlainDate", 0, TemporalZonedDateTimePrototype::toPlainDate);
        defineMethod(prototype, "toPlainDateTime", 0, TemporalZonedDateTimePrototype::toPlainDateTime);
        defineMethod(prototype, "toPlainTime", 0, TemporalZonedDateTimePrototype::toPlainTime);
        defineMethod(prototype, "toString", 0, TemporalZonedDateTimePrototype::toStringMethod);
        defineMethod(prototype, "until", 1, TemporalZonedDateTimePrototype::until);
        defineMethod(prototype, "valueOf", 0, TemporalZonedDateTimePrototype::valueOf);
        defineMethod(prototype, "with", 1, TemporalZonedDateTimePrototype::with);
        defineMethod(prototype, "withCalendar", 1, TemporalZonedDateTimePrototype::withCalendar);
        defineMethod(prototype, "withPlainTime", 0, TemporalZonedDateTimePrototype::withPlainTime);
        defineMethod(prototype, "withTimeZone", 1, TemporalZonedDateTimePrototype::withTimeZone);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "ZonedDateTime", 2,
                TemporalZonedDateTimeConstructor::construct, true, false);
        defineMethod(constructor, "compare", 2, TemporalZonedDateTimeConstructor::compare);
        defineMethod(constructor, "from", 1, TemporalZonedDateTimeConstructor::from);

        installTemporalConstructor(temporalObject, "ZonedDateTime", constructor, prototype);
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
        defineMethod(typedArrayPrototype, "at", 1, TypedArrayPrototype::at);
        defineMethod(typedArrayPrototype, "copyWithin", 2, TypedArrayPrototype::copyWithin);
        defineMethod(typedArrayPrototype, "entries", 0, TypedArrayPrototype::entries);
        defineMethod(typedArrayPrototype, "every", 1, TypedArrayPrototype::every);
        defineMethod(typedArrayPrototype, "fill", 1, TypedArrayPrototype::fill);
        defineMethod(typedArrayPrototype, "filter", 1, TypedArrayPrototype::filter);
        defineMethod(typedArrayPrototype, "find", 1, TypedArrayPrototype::find);
        defineMethod(typedArrayPrototype, "findIndex", 1, TypedArrayPrototype::findIndex);
        defineMethod(typedArrayPrototype, "findLast", 1, TypedArrayPrototype::findLast);
        defineMethod(typedArrayPrototype, "findLastIndex", 1, TypedArrayPrototype::findLastIndex);
        defineMethod(typedArrayPrototype, "forEach", 1, TypedArrayPrototype::forEach);
        defineMethod(typedArrayPrototype, "includes", 1, TypedArrayPrototype::includes);
        defineMethod(typedArrayPrototype, "indexOf", 1, TypedArrayPrototype::indexOf);
        defineMethod(typedArrayPrototype, "join", 1, TypedArrayPrototype::join);
        defineMethod(typedArrayPrototype, "keys", 0, TypedArrayPrototype::keys);
        defineMethod(typedArrayPrototype, "lastIndexOf", 1, TypedArrayPrototype::lastIndexOf);
        defineMethod(typedArrayPrototype, "map", 1, TypedArrayPrototype::map);
        defineMethod(typedArrayPrototype, "reduce", 1, TypedArrayPrototype::reduce);
        defineMethod(typedArrayPrototype, "reduceRight", 1, TypedArrayPrototype::reduceRight);
        defineMethod(typedArrayPrototype, "reverse", 0, TypedArrayPrototype::reverse);
        defineMethod(typedArrayPrototype, "set", 1, TypedArrayPrototype::set);
        defineMethod(typedArrayPrototype, "slice", 2, TypedArrayPrototype::slice);
        defineMethod(typedArrayPrototype, "some", 1, TypedArrayPrototype::some);
        defineMethod(typedArrayPrototype, "sort", 1, TypedArrayPrototype::sort);
        defineMethod(typedArrayPrototype, "subarray", 2, TypedArrayPrototype::subarray);
        defineMethod(typedArrayPrototype, "toLocaleString", 0, TypedArrayPrototype::toLocaleString);
        defineMethod(typedArrayPrototype, "toReversed", 0, TypedArrayPrototype::toReversed);
        defineMethod(typedArrayPrototype, "toSorted", 1, TypedArrayPrototype::toSorted);
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

        defineGetter(typedArrayPrototype, "buffer", TypedArrayPrototype::getBuffer);
        defineGetter(typedArrayPrototype, "byteLength", TypedArrayPrototype::getByteLength);
        defineGetter(typedArrayPrototype, "byteOffset", TypedArrayPrototype::getByteOffset);
        defineGetter(typedArrayPrototype, "length", TypedArrayPrototype::getLength);
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
                defineMethod(prototype, "toBase64", 0, Uint8ArrayBase64Hex::toBase64);
                defineMethod(prototype, "toHex", 0, Uint8ArrayBase64Hex::toHex);
                defineMethod(prototype, "setFromBase64", 1, Uint8ArrayBase64Hex::setFromBase64);
                defineMethod(prototype, "setFromHex", 1, Uint8ArrayBase64Hex::setFromHex);
            }

            defineBuiltin(globalObject, def.name, constructor);
        }
    }

    /**
     * Initialize WeakMap constructor and prototype methods.
     */
    private void initializeWeakMapConstructor() {
        JSObject weakMapPrototype = context.createJSObject();
        defineMethod(weakMapPrototype, "set", 2, WeakMapPrototype::set);
        defineMethod(weakMapPrototype, "get", 1, WeakMapPrototype::get);
        defineMethod(weakMapPrototype, "getOrInsert", 2, WeakMapPrototype::getOrInsert);
        defineMethod(weakMapPrototype, "getOrInsertComputed", 2, WeakMapPrototype::getOrInsertComputed);
        defineMethod(weakMapPrototype, "has", 1, WeakMapPrototype::has);
        defineMethod(weakMapPrototype, "delete", 1, WeakMapPrototype::delete);
        defineToStringTag(weakMapPrototype, JSWeakMap.NAME);

        JSNativeFunction weakMapConstructor = new JSNativeFunction(context, JSWeakMap.NAME, 0, WeakMapConstructor::call,
                true, true);
        linkConstructor(weakMapConstructor, weakMapPrototype, JSConstructorType.WEAK_MAP);

        defineBuiltin(globalObject, JSWeakMap.NAME, weakMapConstructor);
    }

    /**
     * Initialize WeakRef constructor.
     */
    private void initializeWeakRefConstructor() {
        JSObject weakRefPrototype = context.createJSObject();
        defineMethod(weakRefPrototype, "deref", 0, WeakRefPrototype::deref);
        defineToStringTag(weakRefPrototype, JSWeakRef.NAME);

        JSNativeFunction weakRefConstructor = new JSNativeFunction(context, JSWeakRef.NAME, 1, WeakRefConstructor::call,
                true, true);
        linkConstructor(weakRefConstructor, weakRefPrototype, JSConstructorType.WEAK_REF);

        defineBuiltin(globalObject, JSWeakRef.NAME, weakRefConstructor);
    }

    /**
     * Initialize WeakSet constructor and prototype methods.
     */
    private void initializeWeakSetConstructor() {
        JSObject weakSetPrototype = context.createJSObject();
        defineMethod(weakSetPrototype, "add", 1, WeakSetPrototype::add);
        defineMethod(weakSetPrototype, "has", 1, WeakSetPrototype::has);
        defineMethod(weakSetPrototype, "delete", 1, WeakSetPrototype::delete);
        defineToStringTag(weakSetPrototype, JSWeakSet.NAME);

        JSNativeFunction weakSetConstructor = new JSNativeFunction(context, JSWeakSet.NAME, 0, WeakSetConstructor::call,
                true, true);
        linkConstructor(weakSetConstructor, weakSetPrototype, JSConstructorType.WEAK_SET);

        defineBuiltin(globalObject, JSWeakSet.NAME, weakSetConstructor);
    }

    private void installIntlConstructor(JSObject intl, String name, JSObject constructor, JSObject prototype) {
        constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        defineMethod(constructor, "supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf);
        prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor,
                PropertyDescriptor.DataState.ConfigurableWritable);
        defineBuiltin(intl, name, constructor);
    }

    private static void defineBuiltin(JSObject target, String name, JSValue value) {
        target.defineProperty(PropertyKey.fromString(name), value, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    private static void defineToStringTag(JSObject target, String tag) {
        target.defineProperty(PropertyKey.SYMBOL_TO_STRING_TAG, new JSString(tag),
                PropertyDescriptor.DataState.Configurable);
    }

    private static void installTemporalConstructor(JSObject temporalObject, String name, JSNativeFunction constructor,
            JSObject prototype) {
        linkConstructor(constructor, prototype);
        defineBuiltin(temporalObject, name, constructor);
    }

    private static void linkConstructor(JSObject constructor, JSObject prototype) {
        linkConstructor(constructor, prototype, null);
    }

    private static void linkConstructor(JSObject constructor, JSObject prototype, JSConstructorType type) {
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

        /**
         * Analyze caller function var-environment names in one parse. bodyVarNames excludes parameter names.
         * functionVarEnvironmentNames includes parameter names and body var declarations.
         */
        private static CallerVarEnvironmentAnalysis analyzeCallerVarEnvironment(
                JSBytecodeFunction callerBytecodeFunction) {
            String source = callerBytecodeFunction.getSourceCode();
            if (source == null || source.isBlank()) {
                return null;
            }
            Expression functionLikeExpression = extractFunctionLikeExpression(source);
            if (!(functionLikeExpression instanceof FunctionNode function)) {
                return null;
            }
            Set<String> bodyVars = new HashSet<>();
            Set<String> constants = new HashSet<>();
            Set<String> names = function.getParameterNames();
            if (function.getRestParameter() != null) {
                names.addAll(function.getRestParameter().getArgument().getBoundNames());
            }
            ASTNode body = function instanceof FunctionExpression ordinary
                    ? ordinary.getBody()
                    : ((ArrowFunctionExpression) function).getBody();
            if (body instanceof BlockStatement block) {
                collectCallerBindings(block, bodyVars, constants);
            }
            names.addAll(bodyVars);
            return new CallerVarEnvironmentAnalysis(bodyVars, names, constants);
        }

        private static void appendPercentHex(StringBuilder result, int c) {
            result.append('%');
            result.append(HEX_CHARS[(c >> 4) & 0xF]);
            result.append(HEX_CHARS[c & 0xF]);
        }

        private static int asciiDigitValue(char c) {
            if (c >= '0' && c <= '9') {
                return c - '0';
            }
            if (c >= 'A' && c <= 'Z') {
                return c - 'A' + 10;
            }
            if (c >= 'a' && c <= 'z') {
                return c - 'a' + 10;
            }
            return -1;
        }

        private static void collectCallerBindings(ASTNode node, Set<String> vars, Set<String> constants) {
            if (node instanceof FunctionDeclaration function && function.getId() != null) {
                vars.add(function.getId().getName());
            } else if (node instanceof VariableDeclaration declaration) {
                Set<String> target = switch (declaration.getKind()) {
                    case VAR -> vars;
                    case CONST -> constants;
                    default -> null;
                };
                if (target != null) {
                    for (VariableDeclarator binding : declaration.getDeclarations()) {
                        target.addAll(binding.getId().getBoundNames());
                    }
                }
            } else if (node instanceof BlockStatement block) {
                collectCallerBindings(block.getBody(), vars, constants);
            } else if (node instanceof IfStatement branch) {
                collectCallerBindings(branch.getConsequent(), vars, constants);
                collectCallerBindings(branch.getAlternate(), vars, constants);
            } else if (node instanceof ForStatement loop) {
                collectCallerBindings(loop.getInit(), vars, constants);
                collectCallerBindings(loop.getBody(), vars, constants);
            } else if (node instanceof ForEachStatement loop) {
                collectCallerBindings(loop.getLeft(), vars, constants);
                collectCallerBindings(loop.getBody(), vars, constants);
            } else if (node instanceof WhileLoop loop) {
                collectCallerBindings(loop.getBody(), vars, constants);
            } else if (node instanceof SwitchStatement selection) {
                for (SwitchStatement.SwitchCase clause : selection.getCases()) {
                    collectCallerBindings(clause.getConsequent(), vars, constants);
                }
            } else if (node instanceof TryStatement attempt) {
                collectCallerBindings(attempt.getBlock(), vars, constants);
                if (attempt.getHandler() != null) {
                    if (attempt.getHandler().getParam() != null) {
                        vars.addAll(attempt.getHandler().getParam().getBoundNames());
                    }
                    collectCallerBindings(attempt.getHandler().getBody(), vars, constants);
                }
                collectCallerBindings(attempt.getFinalizer(), vars, constants);
            } else if (node instanceof LabeledStatement labeled) {
                collectCallerBindings(labeled.getBody(), vars, constants);
            }
        }

        private static void collectCallerBindings(List<Statement> statements, Set<String> vars, Set<String> constants) {
            for (Statement statement : statements) {
                collectCallerBindings(statement, vars, constants);
            }
        }

        private static boolean containsIdentifierReference(String sourceCode, String identifierName) {
            if (sourceCode == null || sourceCode.isEmpty() || identifierName == null || identifierName.isEmpty()) {
                return false;
            }
            int searchIndex = 0;
            while (searchIndex <= sourceCode.length() - identifierName.length()) {
                int matchIndex = sourceCode.indexOf(identifierName, searchIndex);
                if (matchIndex < 0) {
                    return false;
                }
                int beforeIndex = matchIndex - 1;
                int afterIndex = matchIndex + identifierName.length();
                boolean hasIdentifierCharBefore = beforeIndex >= 0
                        && isIdentifierPartAscii(sourceCode.charAt(beforeIndex));
                boolean hasIdentifierCharAfter = afterIndex < sourceCode.length()
                        && isIdentifierPartAscii(sourceCode.charAt(afterIndex));
                if (!hasIdentifierCharBefore && !hasIdentifierCharAfter) {
                    return true;
                }
                searchIndex = matchIndex + identifierName.length();
            }
            return false;
        }

        /**
         * decodeURI(encodedURI) Decode a URI that was encoded by encodeURI.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-decodeuri-encodeduri">ECMAScript decodeURI</a>
         */
        public static JSValue decodeURI(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue encodedValue = JSValue.argumentOrUndefined(args, 0);
            String encodedString = JSTypeConversions.toString(context, encodedValue).value();
            return decodeURIImpl(context, encodedString, false);
        }

        /**
         * decodeURIComponent(encodedURIComponent) Decode a URI component that was encoded by encodeURIComponent.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-decodeuricomponent-encodeduricomponent">ECMAScript
         *      decodeURIComponent</a>
         */
        public static JSValue decodeURIComponent(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue encodedValue = JSValue.argumentOrUndefined(args, 0);
            String encodedString = JSTypeConversions.toString(context, encodedValue).value();
            return decodeURIImpl(context, encodedString, true);
        }

        private static JSValue decodeURIImpl(JSContext context, String encodedString, boolean isComponent) {
            if (encodedString.indexOf('%') < 0) {
                return new JSString(encodedString);
            }

            int length = encodedString.length();
            StringBuilder result = new StringBuilder(length);
            int k = 0;
            while (k < length) {
                int c = encodedString.charAt(k);
                if (c != '%') {
                    result.append((char) c);
                    k++;
                    continue;
                }

                if (k + 2 >= length) {
                    return context.throwURIError("expecting hex digit");
                }
                int high = fromHex(encodedString.charAt(k + 1));
                int low = fromHex(encodedString.charAt(k + 2));
                if (high < 0 || low < 0) {
                    return context.throwURIError("expecting hex digit");
                }

                int decodedByte = (high << 4) | low;
                int percentIndex = k;
                k += 3;

                if (decodedByte < 0x80) {
                    if (!isComponent && isURIReserved(decodedByte)) {
                        result.append('%').append(encodedString.charAt(percentIndex + 1))
                                .append(encodedString.charAt(percentIndex + 2));
                    } else {
                        result.append((char) decodedByte);
                    }
                    continue;
                }

                int continuationCount;
                int minCodePoint;
                int codePoint;
                if (decodedByte >= 0xC0 && decodedByte <= 0xDF) {
                    continuationCount = 1;
                    minCodePoint = 0x80;
                    codePoint = decodedByte & 0x1F;
                } else if (decodedByte >= 0xE0 && decodedByte <= 0xEF) {
                    continuationCount = 2;
                    minCodePoint = 0x800;
                    codePoint = decodedByte & 0x0F;
                } else if (decodedByte >= 0xF0 && decodedByte <= 0xF7) {
                    continuationCount = 3;
                    minCodePoint = 0x10000;
                    codePoint = decodedByte & 0x07;
                } else {
                    return context.throwURIError("malformed UTF-8");
                }

                for (int i = 0; i < continuationCount; i++) {
                    if (k + 2 >= length || encodedString.charAt(k) != '%') {
                        return context.throwURIError("expecting %");
                    }
                    int contHigh = fromHex(encodedString.charAt(k + 1));
                    int contLow = fromHex(encodedString.charAt(k + 2));
                    if (contHigh < 0 || contLow < 0) {
                        return context.throwURIError("expecting hex digit");
                    }
                    int continuationByte = (contHigh << 4) | contLow;
                    k += 3;
                    if ((continuationByte & 0xC0) != 0x80) {
                        return context.throwURIError("malformed UTF-8");
                    }
                    codePoint = (codePoint << 6) | (continuationByte & 0x3F);
                }

                if (codePoint < minCodePoint || codePoint > Character.MAX_CODE_POINT
                        || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)) {
                    return context.throwURIError("malformed UTF-8");
                }
                result.appendCodePoint(codePoint);
            }
            return new JSString(result.toString());
        }

        /**
         * encodeURI(uri) Encode a URI by escaping certain characters. Does not encode:
         * {@code A-Z a-z 0-9 ; , / ? : @ & = + $ - _ . ! ~ * ' ( ) #}
         *
         * @see <a href="https://tc39.es/ecma262/#sec-encodeuri-uri">ECMAScript encodeURI</a>
         */
        public static JSValue encodeURI(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue uriValue = JSValue.argumentOrUndefined(args, 0);
            String uriString = JSTypeConversions.toString(context, uriValue).value();
            return encodeURIImpl(context, uriString, false);
        }

        /**
         * encodeURIComponent(uriComponent) Encode a URI component by escaping certain characters. More aggressive than
         * encodeURI - also encodes: {@code ; , / ? : @ & = + $ #}
         *
         * @see <a href="https://tc39.es/ecma262/#sec-encodeuricomponent-uricomponent">ECMAScript encodeURIComponent</a>
         */
        public static JSValue encodeURIComponent(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue componentValue = JSValue.argumentOrUndefined(args, 0);
            String componentString = JSTypeConversions.toString(context, componentValue).value();
            return encodeURIImpl(context, componentString, true);
        }

        private static JSValue encodeURIImpl(JSContext context, String inputString, boolean isComponent) {
            int length = inputString.length();
            StringBuilder result = null;
            int k = 0;
            while (k < length) {
                char ch = inputString.charAt(k);
                int c = ch;
                if (isURIUnescaped(c, isComponent)) {
                    if (result != null) {
                        result.append(ch);
                    }
                    k++;
                    continue;
                }
                if (result == null) {
                    result = new StringBuilder(length + 16);
                    result.append(inputString, 0, k);
                }
                if (ch >= Character.MIN_LOW_SURROGATE && ch <= Character.MAX_LOW_SURROGATE) {
                    return context.throwURIError("invalid character");
                }
                k++;
                if (ch >= Character.MIN_HIGH_SURROGATE && ch <= Character.MAX_HIGH_SURROGATE) {
                    if (k >= length) {
                        return context.throwURIError("expecting surrogate pair");
                    }
                    char c1 = inputString.charAt(k++);
                    if (c1 < Character.MIN_LOW_SURROGATE || c1 > Character.MAX_LOW_SURROGATE) {
                        return context.throwURIError("expecting surrogate pair");
                    }
                    c = Character.toCodePoint(ch, c1);
                }

                if (c < 0x80) {
                    appendPercentHex(result, c);
                } else if (c < 0x800) {
                    appendPercentHex(result, (c >> 6) | 0xC0);
                    appendPercentHex(result, (c & 0x3F) | 0x80);
                } else if (c < 0x10000) {
                    appendPercentHex(result, (c >> 12) | 0xE0);
                    appendPercentHex(result, ((c >> 6) & 0x3F) | 0x80);
                    appendPercentHex(result, (c & 0x3F) | 0x80);
                } else {
                    appendPercentHex(result, (c >> 18) | 0xF0);
                    appendPercentHex(result, ((c >> 12) & 0x3F) | 0x80);
                    appendPercentHex(result, ((c >> 6) & 0x3F) | 0x80);
                    appendPercentHex(result, (c & 0x3F) | 0x80);
                }
            }
            if (result == null) {
                return new JSString(inputString);
            }
            return new JSString(result.toString());
        }

        /**
         * escape(string) Deprecated function that encodes a string for use in a URL. Encodes all characters except: A-Z
         * a-z 0-9 @ * _ + - . / Uses %XX for characters {@code < 256} and %uXXXX for characters {@code >= 256}.
         *
         * @see <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/escape">MDN
         *      escape</a>
         */
        public static JSValue escape(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue stringValue = JSValue.argumentOrUndefined(args, 0);
            String str = JSTypeConversions.toString(context, stringValue).value();

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);

                // Check if character should not be escaped
                if (isUnescaped(c)) {
                    result.append(c);
                } else {
                    // Escape the character
                    if (c < 256) {
                        // Use %XX format for characters < 256
                        appendPercentHex(result, c);
                    } else {
                        // Use %uXXXX format for characters >= 256
                        result.append('%').append('u');
                        result.append(HEX_CHARS[(c >> 12) & 0xF]);
                        result.append(HEX_CHARS[(c >> 8) & 0xF]);
                        result.append(HEX_CHARS[(c >> 4) & 0xF]);
                        result.append(HEX_CHARS[c & 0xF]);
                    }
                }
            }

            return new JSString(result.toString());
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
            JSValue x = JSValue.argumentOrUndefined(args, 0);

            // If x is not a string, return it unchanged
            if (!(x instanceof JSString)) {
                return x;
            }

            String code = ((JSString) x).value();
            boolean isDirectEvalCall = forceDirectEvalCall;
            boolean isSameRealm = (realmContext == callerContext);
            boolean shouldUseCallerFrameSemantics = isDirectEvalCall && isSameRealm;

            // Scope overlay: capture enclosing function's local variables onto the global
            // object so that eval code's GET_VAR/PUT_VAR can access them.
            StackFrame callerFrame = callerContext.getVirtualMachine().getCurrentFrame();
            boolean hasSameRealmCallerFrame = isSameRealm && callerFrame != null;
            // Eval is "inside a function" only if same-realm and the callerFrame is NOT the top-level program.
            boolean isEvalInFunction = shouldUseCallerFrameSemantics && hasSameRealmCallerFrame
                    && callerFrame.getFunction() instanceof JSBytecodeFunction && callerFrame.getCaller() != null;
            boolean shouldOverlayLocals = isEvalInFunction;
            boolean shouldOverlayTopLevelLexicals = shouldUseCallerFrameSemantics && hasSameRealmCallerFrame
                    && !isEvalInFunction && callerFrame.getFunction() instanceof JSBytecodeFunction;
            boolean inheritedStrictMode = shouldUseCallerFrameSemantics && callerFrame != null
                    && callerFrame.getFunction() instanceof JSBytecodeFunction bytecodeFunction
                    && bytecodeFunction.isStrict();
            String[] localVarNames = null;
            Map<String, Integer> localVarNameToIndex = null;
            Set<String> localVarNameSet = null;
            Map<String, Integer> capturedVarOverlaySlots = null;
            Map<String, JSValue> savedGlobals = null;
            Map<String, PropertyDescriptor> savedDescriptors = null;
            Set<String> constantOverlayBindingNames = new HashSet<>();
            Set<String> absentKeys = null;
            Set<String> immutableOverlayBindingNames = null;
            Set<String> touchedOverlayKeys = null;
            Set<String> evalVarDeclarations = null;
            Set<String> evalLexDeclarations = null;
            Set<String> evalFunctionDeclarations = null;
            Set<String> functionVarEnvironmentNames = null;
            Set<String> constLexicalNames = null;
            CallerVarEnvironmentAnalysis callerVarEnvironmentAnalysis = null;
            boolean parsedEvalDeclarations = false;
            boolean evalCodeStrict = inheritedStrictMode;
            boolean overlayStatePushed = false;
            JSObject global = realmContext.getGlobalObject();
            JSBytecodeFunction callerBytecodeFunction = callerFrame != null
                    && callerFrame.getFunction() instanceof JSBytecodeFunction bytecodeFunction
                            ? bytecodeFunction
                            : null;
            // Track function expression name binding index so eval cannot overwrite it.
            // Per ES2024 14.1.2 step 27, the binding is immutable (TypeError in strict,
            // silently ignored in sloppy mode).
            int functionNameLocalIndex = callerBytecodeFunction != null
                    ? callerBytecodeFunction.getSelfLocalIndex()
                    : -1;
            // The named-function-expression bindings this frame resolves through a parameter
            // environment rather than through an ordinary local. The two are kept apart because
            // they are not the same claim, and conflating them was a defect: a name is not a
            // binding identity, so an *inherited* name says nothing about a binding of the same
            // spelling that this frame declares itself. A nested closure written in an enclosing
            // initializer may have its own parameter, `var`, lexical, class or function called `n`,
            // and that nearer binding shadows the function-expression one and is mutable — but
            // membership in a set of names classified it as the immutable one, so direct eval's
            // assignment to it was discarded. The frame's *own* name is a binding of this frame, so
            // it stays authoritative. See overlayParameterScopeFunctionNames.
            String activeOwnParameterScopeFunctionName = callerBytecodeFunction == null
                    ? null
                    : callerBytecodeFunction.getActiveParameterScopeFunctionName(callerFrame);
            Set<String> inheritedParameterScopeFunctionNames = callerBytecodeFunction == null
                    ? Set.of()
                    : callerBytecodeFunction.getInheritedParameterScopeFunctionNames();

            // When eval runs inside a function, snapshot global property names so we can
            // clean up var/function bindings that should be function-scoped (not global).
            Set<String> globalKeysBefore = null;
            if (hasSameRealmCallerFrame) {
                globalKeysBefore = new HashSet<>();
                for (PropertyKey propertyKey : global.ownPropertyKeys()) {
                    if (propertyKey.isString()) {
                        globalKeysBefore.add(propertyKey.asString());
                    }
                }
            }
            if (callerBytecodeFunction != null && shouldOverlayLocals) {
                callerVarEnvironmentAnalysis = analyzeCallerVarEnvironment(callerBytecodeFunction);
                if (callerVarEnvironmentAnalysis != null) {
                    functionVarEnvironmentNames = callerVarEnvironmentAnalysis.functionVarEnvironmentNames();
                    constLexicalNames = callerVarEnvironmentAnalysis.constLexicalNames();
                }
            }

            if ((shouldOverlayLocals || shouldOverlayTopLevelLexicals) && callerBytecodeFunction != null
                    && callerBytecodeFunction.getBytecode().getLocalVarNames() != null) {
                localVarNames = callerBytecodeFunction.getBytecode().getLocalVarNames();
                localVarNameToIndex = new HashMap<>();
                localVarNameSet = new HashSet<>();
                capturedVarOverlaySlots = new LinkedHashMap<>();
                savedGlobals = new HashMap<>();
                savedDescriptors = new HashMap<>();
                absentKeys = new HashSet<>();
                immutableOverlayBindingNames = new HashSet<>();
                touchedOverlayKeys = new HashSet<>();
                JSValue[] locals = callerFrame.getLocals();
                for (int i = 0; i < localVarNames.length && i < locals.length; i++) {
                    String name = localVarNames[i];
                    if (name == null) {
                        continue;
                    }
                    if (name.startsWith("#")) {
                        continue;
                    }
                    if (shouldOverlayTopLevelLexicals && locals[i] instanceof JSSymbol symbolValue
                            && "UninitializedMarker".equals(symbolValue.getDescription())) {
                        continue;
                    }
                    if (shouldOverlayTopLevelLexicals && !callerContext.hasGlobalLexDeclaration(name)) {
                        boolean localBindingIsUndefined = locals[i] == null || locals[i] == JSUndefined.INSTANCE;
                        boolean evalSourceMentionsBinding = containsIdentifierReference(code, name);
                        if (localBindingIsUndefined || !evalSourceMentionsBinding) {
                            continue;
                        }
                    }
                    localVarNameSet.add(name);
                    localVarNameToIndex.putIfAbsent(name, i);
                    overlayBinding(global, name, locals[i], savedGlobals, savedDescriptors, absentKeys,
                            touchedOverlayKeys);
                    // Function name bindings are immutable: make the overlay property
                    // non-writable so PUT_VAR throws TypeError in strict mode and
                    // silently fails in sloppy mode.
                    boolean isFunctionNameBinding = functionNameLocalIndex >= 0 && i == functionNameLocalIndex;
                    boolean isConstLexicalBinding = shouldOverlayLocals && constLexicalNames != null
                            && constLexicalNames.contains(name);
                    if (isFunctionNameBinding || isConstLexicalBinding) {
                        PropertyDescriptor fnDesc = new PropertyDescriptor();
                        fnDesc.setValue(locals[i] != null ? locals[i] : JSUndefined.INSTANCE);
                        fnDesc.setWritable(false);
                        fnDesc.setEnumerable(true);
                        fnDesc.setConfigurable(true);
                        global.defineProperty(PropertyKey.fromString(name), fnDesc);
                        immutableOverlayBindingNames.add(name);
                        if (isConstLexicalBinding && !isFunctionNameBinding) {
                            constantOverlayBindingNames.add(name);
                        }
                    }
                }
                if (shouldOverlayLocals) {
                    IdentityHashMap<StackFrame, Boolean> visitedLexicalFrames = new IdentityHashMap<>();
                    if (callerBytecodeFunction.isEvalDynamicScopeLookupEnabled()
                            && callerBytecodeFunction.getEvalDynamicScopeFrame() != null) {
                        StackFrame evalScopeFrame = callerBytecodeFunction.getEvalDynamicScopeFrame();
                        while (evalScopeFrame != null
                                && visitedLexicalFrames.put(evalScopeFrame, Boolean.TRUE) == null) {
                            if (evalScopeFrame != callerFrame) {
                                overlayFrameLocals(realmContext, global, evalScopeFrame, localVarNameSet, savedGlobals,
                                        savedDescriptors, absentKeys, touchedOverlayKeys);
                                Map<String, JSValue> evalScopeDynamicBindings = evalScopeFrame.getDynamicVarBindings();
                                if (evalScopeDynamicBindings != null) {
                                    for (Map.Entry<String, JSValue> entry : evalScopeDynamicBindings.entrySet()) {
                                        overlayBinding(global, entry.getKey(), entry.getValue(), savedGlobals,
                                                savedDescriptors, absentKeys, touchedOverlayKeys);
                                    }
                                }
                            }
                            evalScopeFrame = evalScopeFrame.getCaller();
                        }
                    }
                    VarRef[] callerVarRefs = callerBytecodeFunction.getVarRefs();
                    if (callerVarRefs != null) {
                        int selfCaptureIndex = callerBytecodeFunction.getSelfCaptureIndex();
                        for (int captureSlot = 0; captureSlot < callerVarRefs.length; captureSlot++) {
                            String capturedVarName = callerBytecodeFunction.getCapturedVarName(captureSlot);
                            if (capturedVarName == null || capturedVarName.startsWith("#")) {
                                continue;
                            }
                            if (localVarNameSet.contains(capturedVarName)) {
                                continue;
                            }
                            JSValue capturedValue = callerFrame.getVarRef(captureSlot);
                            overlayBinding(global, capturedVarName, capturedValue, savedGlobals, savedDescriptors,
                                    absentKeys, touchedOverlayKeys);
                            capturedVarOverlaySlots.put(capturedVarName, captureSlot);
                            if (inheritedParameterScopeFunctionNames.contains(capturedVarName)) {
                                // A closure created in a default initializer captured a named
                                // function expression's binding, which is immutable. Reached only
                                // for a name this frame has no local of — a local of the same
                                // spelling shadows the captured binding and skipped this loop
                                // above, which is what keeps the classification about the binding
                                // rather than about the name.
                                PropertyDescriptor capturedSelfDescriptor = new PropertyDescriptor();
                                capturedSelfDescriptor
                                        .setValue(capturedValue != null ? capturedValue : JSUndefined.INSTANCE);
                                capturedSelfDescriptor.setWritable(false);
                                capturedSelfDescriptor.setEnumerable(true);
                                capturedSelfDescriptor.setConfigurable(true);
                                global.defineProperty(PropertyKey.fromString(capturedVarName), capturedSelfDescriptor);
                                immutableOverlayBindingNames.add(capturedVarName);
                            }
                            if (selfCaptureIndex >= 0 && captureSlot == selfCaptureIndex) {
                                PropertyDescriptor capturedFunctionNameDescriptor = new PropertyDescriptor();
                                capturedFunctionNameDescriptor
                                        .setValue(capturedValue != null ? capturedValue : JSUndefined.INSTANCE);
                                capturedFunctionNameDescriptor.setWritable(false);
                                capturedFunctionNameDescriptor.setEnumerable(true);
                                capturedFunctionNameDescriptor.setConfigurable(true);
                                global.defineProperty(PropertyKey.fromString(capturedVarName),
                                        capturedFunctionNameDescriptor);
                                immutableOverlayBindingNames.add(capturedVarName);
                            }
                        }
                    }
                    // ES2024 15.2.5: a default initializer runs in the parameter environment, whose
                    // outer environment holds the named function expression's own binding. A `var`,
                    // lexical, class or function declaration of that name in the body is a
                    // different binding, and it does not exist yet. Both are slots of one flattened
                    // frame and the name maps to the body one, so while the frame is still
                    // initializing parameters the name has to be pointed back at the function.
                    overlayParameterScopeFunctionNames(global, callerFrame, callerBytecodeFunction, savedGlobals,
                            savedDescriptors, absentKeys, touchedOverlayKeys, immutableOverlayBindingNames);
                    Map<String, JSValue> dynamicVarBindings = callerFrame.getDynamicVarBindings();
                    if (dynamicVarBindings != null) {
                        for (Map.Entry<String, JSValue> entry : dynamicVarBindings.entrySet()) {
                            overlayBinding(global, entry.getKey(), entry.getValue(), savedGlobals, savedDescriptors,
                                    absentKeys, touchedOverlayKeys);
                        }
                    }
                    List<WithObjectCandidate> withObjectCandidates = new ArrayList<>();
                    JSValue[] callerLocals = callerFrame.getLocals();
                    for (int i = 0; i < localVarNames.length && i < callerLocals.length; i++) {
                        String name = localVarNames[i];
                        JSValue localValue = callerLocals[i];
                        if (name == null || localValue == null || localValue == JSUndefined.INSTANCE) {
                            continue;
                        }
                        if (!(localValue instanceof JSObject withObject) || !name.startsWith("#withObject")) {
                            continue;
                        }
                        withObjectCandidates.add(new WithObjectCandidate(parseWithDepth(name), withObject));
                    }
                    withObjectCandidates.sort(Comparator.comparingInt(WithObjectCandidate::depth));
                    for (WithObjectCandidate withObjectCandidate : withObjectCandidates) {
                        JSObject withObject = withObjectCandidate.object();
                        for (PropertyKey propertyKey : withObject.ownPropertyKeys()) {
                            if (!propertyKey.isString()) {
                                continue;
                            }
                            String keyName = propertyKey.asString();
                            overlayBinding(global, keyName, withObject.get(propertyKey), savedGlobals, savedDescriptors,
                                    absentKeys, touchedOverlayKeys);
                        }
                    }
                }
            }

            constantOverlayBindingNames.remove(activeOwnParameterScopeFunctionName);

            // This eval's lexical environment, nested in the caller's when the caller is itself eval code. The
            // compiled eval code and every function created inside it carry it; only they may consult the overlay.
            JSEvalEnvironment evalEnvironment = new JSEvalEnvironment(
                    callerFrame != null && callerFrame.getFunction() != null
                            ? callerFrame.getFunction().getEvalEnvironment()
                            : null);
            realmContext.setPendingDirectEvalEnvironment(evalEnvironment);
            if ((shouldOverlayLocals || shouldOverlayTopLevelLexicals) && savedGlobals != null && absentKeys != null) {
                realmContext.pushEvalOverlay(savedGlobals, savedDescriptors, absentKeys, evalEnvironment,
                        constantOverlayBindingNames);
                overlayStatePushed = true;
            }

            try {
                if (hasSameRealmCallerFrame) {
                    try {
                        Compiler evalCompiler = new Compiler(code, "<eval>").setContext(realmContext);
                        Program evalAst = evalCompiler.parse(false);
                        Program.GlobalDeclarations globalDeclarations = evalAst.getGlobalDeclarations();
                        evalVarDeclarations = globalDeclarations.varDeclarations();
                        evalLexDeclarations = globalDeclarations.lexicalDeclarations();
                        evalFunctionDeclarations = globalDeclarations.functionDeclarations();
                        parsedEvalDeclarations = true;
                        evalCodeStrict = evalCodeStrict || evalAst.isStrict();
                    } catch (Exception ignored) {
                        // Parse error in eval code - let the normal eval path report it.
                    }
                }

                // EvalDeclarationInstantiation: check if eval code declares "var arguments"
                // in a function with parameter expressions. Per QuickJS add_arguments_arg(),
                // a lexical "arguments" binding exists in the argument scope when the function
                // has parameter expressions (non-strict mode), preventing eval from redeclaring it.
                if (isEvalInFunction && callerBytecodeFunction != null
                        && callerBytecodeFunction.hasParameterExpressions() && !callerBytecodeFunction.isStrict()
                        && !evalCodeStrict && parsedEvalDeclarations && evalVarDeclarations != null
                        && evalVarDeclarations.contains("arguments")) {
                    if (!callerBytecodeFunction.isArrow() || callerBytecodeFunction.hasArgumentsParameterBinding()) {
                        throw new JSException(realmContext.throwError("SyntaxError",
                                "Identifier 'arguments' has already been declared"));
                    }
                }
                // EvalDeclarationInstantiation: in functions with parameter expressions,
                // eval cannot create a var with the same name as a parameter.
                // Per spec, the parameter scope is separate from the var scope,
                // so "var x" in eval conflicts with parameter "x".
                if (isEvalInFunction && callerBytecodeFunction != null
                        && callerBytecodeFunction.hasParameterExpressions() && !callerBytecodeFunction.isStrict()
                        && !evalCodeStrict && parsedEvalDeclarations && evalVarDeclarations != null
                        && localVarNameSet != null) {
                    callerVarEnvironmentAnalysis = analyzeCallerVarEnvironment(callerBytecodeFunction);
                    Set<String> bodyVarNames = callerVarEnvironmentAnalysis != null
                            ? callerVarEnvironmentAnalysis.bodyVarNames()
                            : null;
                    if (bodyVarNames != null) {
                        for (String evalVar : evalVarDeclarations) {
                            if (JSKeyword.ARGUMENTS.equals(evalVar)) {
                                continue;
                            }
                            if (localVarNameSet.contains(evalVar) && !bodyVarNames.contains(evalVar)) {
                                throw new JSException(realmContext.throwError("SyntaxError",
                                        "Identifier '" + evalVar + "' has already been declared"));
                            }
                        }
                    }
                }
                // EvalDeclarationInstantiation step 5 is conditioned on the strictness of the
                // *eval code*, not of the caller. A direct eval in a strict function is strict by
                // inheritance and is already excluded by !evalCodeStrict; an indirect eval is
                // never strict by inheritance, so testing the caller's strictness here silently
                // dropped the global-lexical collision check for `(0,eval)('var x')` written
                // inside a strict script.
                if (hasSameRealmCallerFrame && !evalCodeStrict && parsedEvalDeclarations
                        && evalVarDeclarations != null) {
                    if (callerBytecodeFunction != null && callerFrame != null && callerFrame.getCaller() != null) {
                        if (callerVarEnvironmentAnalysis == null) {
                            callerVarEnvironmentAnalysis = analyzeCallerVarEnvironment(callerBytecodeFunction);
                        }
                        functionVarEnvironmentNames = callerVarEnvironmentAnalysis != null
                                ? callerVarEnvironmentAnalysis.functionVarEnvironmentNames()
                                : null;
                    }
                    for (String declarationName : evalVarDeclarations) {
                        if (isEvalInFunction && JSKeyword.ARGUMENTS.equals(declarationName)
                                && callerBytecodeFunction != null) {
                            continue;
                        }
                        if (callerBytecodeFunction != null && callerBytecodeFunction.isArrow()
                                && JSKeyword.ARGUMENTS.equals(declarationName)
                                && !callerBytecodeFunction.hasArgumentsParameterBinding()) {
                            continue;
                        }
                        // An indirect eval's variable environment is the global environment
                        // wherever the call appears, so the global-lexical collision check applies
                        // even when the calling frame is a function. Only a direct eval has to be
                        // at top level for that to be true.
                        if (callerFrame != null && (!isDirectEvalCall || callerFrame.getCaller() == null)
                                && callerContext.hasGlobalLexDeclaration(declarationName)) {
                            throw new JSException(realmContext.throwError("SyntaxError",
                                    "Identifier '" + declarationName + "' has already been declared"));
                        }
                        if (localVarNameSet != null && localVarNameSet.contains(declarationName)
                                && functionVarEnvironmentNames != null
                                && !functionVarEnvironmentNames.contains(declarationName)) {
                            throw new JSException(realmContext.throwError("SyntaxError",
                                    "Identifier '" + declarationName + "' has already been declared"));
                        }
                    }
                }

                if (isEvalInFunction && parsedEvalDeclarations && evalVarDeclarations != null && savedGlobals != null
                        && absentKeys != null && touchedOverlayKeys != null) {
                    for (String declarationName : evalVarDeclarations) {
                        boolean shadowsCallerLocal = localVarNameSet != null
                                && localVarNameSet.contains(declarationName);
                        if (shadowsCallerLocal) {
                            if (!evalCodeStrict && callerFrame != null && localVarNameToIndex != null) {
                                Integer localIndex = localVarNameToIndex.get(declarationName);
                                if (localIndex != null && functionVarEnvironmentNames != null
                                        && functionVarEnvironmentNames.contains(declarationName)
                                        && (functionNameLocalIndex < 0 || localIndex != functionNameLocalIndex)) {
                                    callerFrame.setDynamicVarBindingAlias(declarationName, localIndex);
                                }
                            }
                            continue;
                        }
                        PropertyKey declarationKey = PropertyKey.fromString(declarationName);
                        JSValue initialValue;
                        if (isEvalInFunction) {
                            JSValue dynamicVarBindingValue = callerFrame != null
                                    ? callerFrame.getDynamicVarBinding(declarationName)
                                    : null;
                            if (dynamicVarBindingValue != null) {
                                initialValue = dynamicVarBindingValue;
                            } else {
                                // Direct eval var declarations in functions create or reuse
                                // function var-environment bindings, not global properties.
                                initialValue = JSUndefined.INSTANCE;
                            }
                        } else if (absentKeys.contains(declarationName)) {
                            // Outer lexical overlays should not initialize eval-introduced var bindings.
                            // Var declarations created by eval start as undefined in the caller var environment.
                            initialValue = JSUndefined.INSTANCE;
                        } else {
                            initialValue = global.has(declarationKey)
                                    ? global.get(declarationKey)
                                    : JSUndefined.INSTANCE;
                        }
                        overlayBinding(global, declarationName, initialValue, savedGlobals, savedDescriptors,
                                absentKeys, touchedOverlayKeys);
                        if (!evalCodeStrict && callerFrame != null) {
                            callerFrame.setDynamicVarBinding(declarationName, initialValue);
                        }
                    }
                }

                if (!isDirectEvalCall && !evalCodeStrict && parsedEvalDeclarations) {
                    if (evalFunctionDeclarations != null) {
                        Map<String, PropertyDescriptor> existingFunctionDescriptors = new HashMap<>();
                        for (String functionDeclarationName : evalFunctionDeclarations) {
                            PropertyKey functionKey = PropertyKey.fromString(functionDeclarationName);
                            PropertyDescriptor existingDescriptor = global.getOwnPropertyDescriptor(functionKey);
                            existingFunctionDescriptors.put(functionDeclarationName, existingDescriptor);
                            if (existingDescriptor != null && !existingDescriptor.isConfigurable()) {
                                if (existingDescriptor.isAccessorDescriptor()
                                        || !(existingDescriptor.isWritable() && existingDescriptor.isEnumerable())) {
                                    throw new JSException(realmContext.throwTypeError(
                                            "cannot define variable '" + functionDeclarationName + "'"));
                                }
                            }
                            if (existingDescriptor == null && !global.isExtensible()) {
                                throw new JSException(realmContext
                                        .throwTypeError("cannot define variable '" + functionDeclarationName + "'"));
                            }
                        }
                        for (String functionDeclarationName : evalFunctionDeclarations) {
                            PropertyDescriptor existingDescriptor = existingFunctionDescriptors
                                    .get(functionDeclarationName);
                            if (existingDescriptor == null || existingDescriptor.isConfigurable()) {
                                JSValue initialValue = existingDescriptor != null && existingDescriptor.hasValue()
                                        ? existingDescriptor.getValue()
                                        : JSUndefined.INSTANCE;
                                global.defineProperty(PropertyKey.fromString(functionDeclarationName),
                                        PropertyDescriptor.dataDescriptor(initialValue,
                                                PropertyDescriptor.DataState.All));
                            }
                        }
                    }
                    if (evalVarDeclarations != null) {
                        for (String declarationName : evalVarDeclarations) {
                            if (evalFunctionDeclarations != null
                                    && evalFunctionDeclarations.contains(declarationName)) {
                                continue;
                            }
                            PropertyKey declarationKey = PropertyKey.fromString(declarationName);
                            if (!global.has(declarationKey) && !global.isExtensible()) {
                                throw new JSException(realmContext
                                        .throwTypeError("cannot define variable '" + declarationName + "'"));
                            }
                        }
                    }
                }

                EvalOverlaySnapshot suspendedOverlaySnapshot = null;
                if (!isDirectEvalCall) {
                    suspendedOverlaySnapshot = realmContext.suspendEvalOverlays();
                    realmContext.pushEvalOverlayLookupSuppression();
                }
                String evalFilename = "<eval>";
                JSStackFrame callerStackFrame = callerContext.getCurrentStackFrame();
                if (callerStackFrame != null && callerStackFrame.filename() != null
                        && !callerStackFrame.filename().isEmpty()) {
                    evalFilename = callerStackFrame.filename();
                }
                JSValue result = isDirectEvalCall
                        ? realmContext.evalDirectInternal(code, evalFilename, inheritedStrictMode)
                        : realmContext.evalIndirectInternal(code, evalFilename);
                if (!isDirectEvalCall) {
                    realmContext.popEvalOverlayLookupSuppression();
                }
                if (suspendedOverlaySnapshot != null) {
                    realmContext.resumeEvalOverlays(suspendedOverlaySnapshot);
                }
                if (result == null) {
                    JSValue error = realmContext.getPendingException();
                    if (error != null) {
                        realmContext.clearPendingException();
                        callerContext.setPendingException(error);
                    }
                    return JSUndefined.INSTANCE;
                }

                // Copy modified values back to caller's locals
                if (localVarNames != null) {
                    JSValue[] locals = callerFrame.getLocals();
                    for (int i = 0; i < localVarNames.length && i < locals.length; i++) {
                        String name = localVarNames[i];
                        if (name == null) {
                            continue;
                        }
                        if (name.startsWith("#")) {
                            continue;
                        }
                        if (evalCodeStrict && evalVarDeclarations != null && evalVarDeclarations.contains(name)) {
                            continue;
                        }
                        // Skip function name bindings — they are immutable and
                        // the non-writable overlay property prevented modification.
                        if (functionNameLocalIndex >= 0 && i == functionNameLocalIndex) {
                            continue;
                        }
                        // While the parameter environment is what the name resolves through, the
                        // overlay holds the function, not the body's binding of the same name.
                        // Copying it back would put the function into the body's slot.
                        //
                        // Only this frame's own name. Every name in this loop is a local of this
                        // frame, and a local is a nearer binding than anything an enclosing
                        // parameter environment provides, so an inherited name here belongs to a
                        // binding that is being shadowed. Skipping those discarded the write: a
                        // nested closure's `let n = 1; eval('n = 2')` left n at 1.
                        if (name.equals(activeOwnParameterScopeFunctionName)) {
                            continue;
                        }
                        PropertyKey key = PropertyKey.fromString(name);
                        if (global.has(key)) {
                            locals[i] = global.get(key);
                        }
                    }
                }
                if (capturedVarOverlaySlots != null && callerBytecodeFunction != null) {
                    int selfCaptureIndex = callerBytecodeFunction.getSelfCaptureIndex();
                    for (Map.Entry<String, Integer> entry : capturedVarOverlaySlots.entrySet()) {
                        String name = entry.getKey();
                        int captureSlot = entry.getValue();
                        if (evalCodeStrict && evalVarDeclarations != null && evalVarDeclarations.contains(name)) {
                            continue;
                        }
                        if (selfCaptureIndex >= 0 && captureSlot == selfCaptureIndex) {
                            continue;
                        }
                        // Immutable, like any other named-function-expression binding. A capture is
                        // only reached for a name this frame has no local of, so the entry really
                        // does describe the binding the overlay holds.
                        if (inheritedParameterScopeFunctionNames.contains(name)) {
                            continue;
                        }
                        PropertyKey key = PropertyKey.fromString(name);
                        if (global.has(key)) {
                            callerFrame.setVarRef(captureSlot, global.get(key));
                        }
                    }
                }
                if (isEvalInFunction && !evalCodeStrict && parsedEvalDeclarations && evalVarDeclarations != null
                        && callerFrame != null) {
                    for (String declarationName : evalVarDeclarations) {
                        if (localVarNameSet != null && localVarNameSet.contains(declarationName)) {
                            continue;
                        }
                        PropertyKey key = PropertyKey.fromString(declarationName);
                        if (global.has(key)) {
                            callerFrame.setDynamicVarBinding(declarationName, global.get(key));
                        }
                    }
                }

                if (!isDirectEvalCall && !evalCodeStrict && evalFunctionDeclarations != null) {
                    for (String functionDeclarationName : evalFunctionDeclarations) {
                        PropertyKey functionKey = PropertyKey.fromString(functionDeclarationName);
                        if (!global.has(functionKey)) {
                            continue;
                        }
                        JSValue functionValue = global.get(functionKey);
                        PropertyDescriptor existingDescriptor = global.getOwnPropertyDescriptor(functionKey);
                        PropertyDescriptor descriptor = new PropertyDescriptor();
                        descriptor.setValue(functionValue);
                        if (existingDescriptor == null || existingDescriptor.isConfigurable()) {
                            descriptor.setWritable(true);
                            descriptor.setEnumerable(true);
                            descriptor.setConfigurable(true);
                        } else {
                            descriptor.setWritable(existingDescriptor.isWritable());
                            descriptor.setEnumerable(existingDescriptor.isEnumerable());
                            descriptor.setConfigurable(false);
                        }
                        global.defineProperty(functionKey, descriptor);
                    }
                }

                return result;
            } catch (JSException e) {
                // Propagate exception to the caller's context so the caller's VM sees it
                callerContext.setPendingException(e.getErrorValue());
                return JSUndefined.INSTANCE;
            } finally {
                // Delete all temporary immutable overlay bindings before restoring globals,
                // since global.set() cannot overwrite a non-writable property.
                if (immutableOverlayBindingNames != null && !immutableOverlayBindingNames.isEmpty()) {
                    for (String immutableOverlayBindingName : immutableOverlayBindingNames) {
                        if (immutableOverlayBindingName == null || immutableOverlayBindingName.isEmpty()) {
                            continue;
                        }
                        global.delete(PropertyKey.fromString(immutableOverlayBindingName));
                    }
                }
                // Restore global object state for scope overlay
                if (savedGlobals != null) {
                    boolean isTopLevelCallerFrame = callerFrame != null && callerFrame.getCaller() == null;
                    for (var entry : savedGlobals.entrySet()) {
                        if (isTopLevelCallerFrame && !evalCodeStrict && parsedEvalDeclarations
                                && evalVarDeclarations != null && evalVarDeclarations.contains(entry.getKey())
                                && !callerContext.hasGlobalLexDeclaration(entry.getKey())) {
                            continue;
                        }
                        PropertyKey key = PropertyKey.fromString(entry.getKey());
                        PropertyDescriptor descriptor = savedDescriptors.get(entry.getKey());
                        if (descriptor != null) {
                            if (descriptor.isDataDescriptor()) {
                                descriptor.setValue(entry.getValue());
                            }
                            global.defineProperty(key, descriptor);
                        } else {
                            global.set(key, entry.getValue());
                        }
                    }
                }
                if (absentKeys != null) {
                    for (String name : absentKeys) {
                        global.delete(PropertyKey.fromString(name));
                    }
                }
                // Clean up eval-created bindings that should be function-scoped (not global).
                // Per ES2024 B.3.3.3, var/function declarations in eval inside a function
                // go to the function's variable environment, not the global.
                if (hasSameRealmCallerFrame && globalKeysBefore != null && parsedEvalDeclarations
                        && evalVarDeclarations != null && (isEvalInFunction || evalCodeStrict)) {
                    for (String declarationName : evalVarDeclarations) {
                        if (globalKeysBefore.contains(declarationName)) {
                            continue;
                        }
                        if (localVarNameSet != null && localVarNameSet.contains(declarationName)) {
                            continue;
                        }
                        global.delete(PropertyKey.fromString(declarationName));
                    }
                }
                if (globalKeysBefore != null && parsedEvalDeclarations && evalLexDeclarations != null) {
                    for (String declarationName : evalLexDeclarations) {
                        if (globalKeysBefore.contains(declarationName)) {
                            continue;
                        }
                        global.delete(PropertyKey.fromString(declarationName));
                    }
                }
                if (overlayStatePushed) {
                    realmContext.popEvalOverlay();
                }
                // For functions with parameter expressions, eval-created vars need to
                // persist on the global object so closures created in parameter defaults
                // can see them. Per spec, these vars go into the parameter scope's var
                // environment, which closures in defaults close over.
                if (isEvalInFunction && callerBytecodeFunction != null
                        && callerBytecodeFunction.hasParameterExpressions() && !evalCodeStrict && parsedEvalDeclarations
                        && evalVarDeclarations != null && callerFrame != null) {
                    for (String declarationName : evalVarDeclarations) {
                        if (localVarNameSet != null && localVarNameSet.contains(declarationName)) {
                            continue;
                        }
                        JSValue dynValue = callerFrame.getDynamicVarBinding(declarationName);
                        if (dynValue != null) {
                            global.set(PropertyKey.fromString(declarationName), dynValue);
                        }
                    }
                }
            }
        }

        private static Expression extractFunctionLikeExpression(String source) {
            Expression directExpression = parseTopLevelExpression("(" + source + ")");
            if (directExpression instanceof FunctionExpression || directExpression instanceof ArrowFunctionExpression) {
                return directExpression;
            }

            Expression objectLiteralExpression = parseTopLevelExpression("({" + source + "})");
            if (!(objectLiteralExpression instanceof ObjectExpression objectExpression)
                    || objectExpression.getProperties().isEmpty()) {
                return null;
            }
            ObjectExpressionProperty firstProperty = objectExpression.getProperties().get(0);
            if (firstProperty.getValue() instanceof FunctionExpression functionExpression) {
                return functionExpression;
            }
            return null;
        }

        private static int fromHex(char c) {
            if (c >= '0' && c <= '9') {
                return c - '0';
            }
            if (c >= 'a' && c <= 'f') {
                return c - 'a' + 10;
            }
            if (c >= 'A' && c <= 'F') {
                return c - 'A' + 10;
            }
            return -1;
        }

        private static boolean isAsciiDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isEcmaWhitespace(char c) {
            return switch (c) {
                case '\t', '\n', '\u000B', '\f', '\r', ' ', '\u00A0', '\u1680', '\u2028', '\u2029', '\u202F', '\u205F',
                        '\u3000', '\uFEFF' ->
                    true;
                default -> c >= '\u2000' && c <= '\u200A';
            };
        }

        /**
         * isFinite(value) Determine whether a value is a finite number.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-isfinite-number">ECMAScript isFinite</a>
         */
        public static JSValue isFinite(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue value = JSValue.argumentOrUndefined(args, 0);
            if (value.isSymbol() || value.isSymbolObject()) {
                return context.throwTypeError("cannot convert symbol to number");
            }
            if (value.isBigInt() || value.isBigIntObject()) {
                return context.throwTypeError("cannot convert bigint to number");
            }
            double num = JSTypeConversions.toNumber(context, value).value();
            return JSBoolean.valueOf(!Double.isNaN(num) && !Double.isInfinite(num));
        }

        private static boolean isIdentifierPartAscii(char character) {
            if (character >= 'a' && character <= 'z') {
                return true;
            }
            if (character >= 'A' && character <= 'Z') {
                return true;
            }
            if (character >= '0' && character <= '9') {
                return true;
            }
            return character == '_' || character == '$';
        }

        private static boolean isInfinityPrefix(String str, int start) {
            return start + 8 <= str.length() && str.startsWith("Infinity", start);
        }

        /**
         * isNaN(value) Determine whether a value is NaN.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-isnan-number">ECMAScript isNaN</a>
         */
        public static JSValue isNaN(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue value = JSValue.argumentOrUndefined(args, 0);
            if (value.isSymbol() || value.isSymbolObject()) {
                return context.throwTypeError("cannot convert symbol to number");
            }
            if (value.isBigInt() || value.isBigIntObject()) {
                return context.throwTypeError("cannot convert bigint to number");
            }
            double num = JSTypeConversions.toNumber(context, value).value();
            return JSBoolean.valueOf(Double.isNaN(num));
        }

        /**
         * Helper function to check if a character should not be escaped. Returns true for: A-Z a-z 0-9 @ * _ + - . /
         */
        private static boolean isUnescaped(char c) {
            return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '@' || c == '*'
                    || c == '_' || c == '+' || c == '-' || c == '.' || c == '/';
        }

        private static boolean isURIReserved(int c) {
            return c < URI_RESERVED_TABLE.length && URI_RESERVED_TABLE[c];
        }

        private static boolean isURIUnescaped(int c, boolean isComponent) {
            if (c >= URI_UNESCAPED_TABLE.length) {
                return false;
            }
            return isComponent ? URI_UNESCAPED_COMPONENT_TABLE[c] : URI_UNESCAPED_TABLE[c];
        }

        private static void overlayBinding(JSObject global, String name, JSValue value,
                Map<String, JSValue> savedGlobals, Map<String, PropertyDescriptor> savedDescriptors,
                Set<String> absentKeys, Set<String> touchedOverlayKeys) {
            if (name == null || touchedOverlayKeys == null || savedGlobals == null || absentKeys == null) {
                return;
            }
            rememberOverlayKey(global, name, savedGlobals, savedDescriptors, absentKeys, touchedOverlayKeys);
            PropertyKey key = PropertyKey.fromString(name);
            JSValue overlayValue = value != null ? value : JSUndefined.INSTANCE;
            if (global.getContext().hasEvalOverlayBinding(name)) {
                PropertyDescriptor current = global.getOwnPropertyDescriptor(key);
                if (current != null && !current.isConfigurable() && current.isDataDescriptor()
                        && current.isWritable()) {
                    // A global var keeps its descriptor when a nested eval overlays its value.
                    global.set(key, overlayValue);
                } else {
                    global.defineProperty(key, overlayValue, PropertyDescriptor.DataState.All);
                }
            } else {
                global.set(key, overlayValue);
            }
        }

        private static void overlayFrameLocals(JSContext realmContext, JSObject global, StackFrame frame,
                Set<String> localVarNameSet, Map<String, JSValue> savedGlobals,
                Map<String, PropertyDescriptor> savedDescriptors, Set<String> absentKeys,
                Set<String> touchedOverlayKeys) {
            if (frame == null || !(frame.getFunction() instanceof JSBytecodeFunction lexicalBytecodeFunction)) {
                return;
            }
            String[] lexicalLocalVarNames = lexicalBytecodeFunction.getBytecode().getLocalVarNames();
            JSValue[] lexicalLocals = frame.getLocals();
            if (lexicalLocalVarNames == null || lexicalLocals == null) {
                return;
            }
            for (int localIndex = 0; localIndex < lexicalLocalVarNames.length
                    && localIndex < lexicalLocals.length; localIndex++) {
                String localName = lexicalLocalVarNames[localIndex];
                if (localName == null || localName.startsWith("#")) {
                    continue;
                }
                if (localVarNameSet != null && localVarNameSet.contains(localName)) {
                    continue;
                }
                if (touchedOverlayKeys != null && touchedOverlayKeys.contains(localName)) {
                    continue;
                }
                if (realmContext.hasEvalOverlayBinding(localName)) {
                    continue;
                }
                JSValue lexicalValue = lexicalLocals[localIndex];
                if (lexicalValue instanceof JSSymbol symbolValue
                        && "UninitializedMarker".equals(symbolValue.getDescription())) {
                    continue;
                }
                overlayBinding(global, localName, lexicalValue, savedGlobals, savedDescriptors, absentKeys,
                        touchedOverlayKeys);
            }
        }

        /**
         * Point a named function expression's own name back at the function while its default initializers are running.
         * <p>
         * Only the frame that owns the two slots can read the function out of one of them. The bindings a closure
         * inherited from enclosing initializers reach the same environments through its captures, which the
         * captured-variable overlay above has already applied.
         *
         * @param global
         *            the realm's global object, which carries the overlay
         * @param callerFrame
         *            the frame the direct eval runs on behalf of
         * @param callerBytecodeFunction
         *            that frame's function
         * @param savedGlobals
         *            globals displaced by the overlay
         * @param absentKeys
         *            overlay names that did not exist before
         * @param touchedOverlayKeys
         *            overlay names already saved
         * @param immutableOverlayBindingNames
         *            names the overlay makes non-writable
         */
        private static void overlayParameterScopeFunctionNames(JSObject global, StackFrame callerFrame,
                JSBytecodeFunction callerBytecodeFunction, Map<String, JSValue> savedGlobals,
                Map<String, PropertyDescriptor> savedDescriptors, Set<String> absentKeys,
                Set<String> touchedOverlayKeys, Set<String> immutableOverlayBindingNames) {
            if (callerBytecodeFunction == null || !callerBytecodeFunction.isOwnParameterScopeActive(callerFrame)) {
                return;
            }
            int selfLocalIndex = callerBytecodeFunction.getSelfLocalIndex();
            JSValue[] locals = callerFrame.getLocals();
            if (locals == null || selfLocalIndex < 0 || selfLocalIndex >= locals.length) {
                return;
            }
            String name = callerBytecodeFunction.getParameterScopeFunctionName();
            JSValue selfValue = locals[selfLocalIndex] != null ? locals[selfLocalIndex] : JSUndefined.INSTANCE;
            rememberOverlayKey(global, name, savedGlobals, savedDescriptors, absentKeys, touchedOverlayKeys);
            // ES2024 15.2.5 makes the binding immutable: assignment throws in strict mode and is
            // ignored in sloppy mode. Without this it would instead reach the body's slot.
            //
            // Defined rather than assigned, because the body's declaration of the same name may
            // itself be a `const`, in which case the loop above has already made this very
            // property non-writable — and assigning to a non-writable property throws in strict
            // mode, which turned a shadowing `const` into a TypeError from the parameter list.
            PropertyDescriptor selfDescriptor = new PropertyDescriptor();
            selfDescriptor.setValue(selfValue);
            selfDescriptor.setWritable(false);
            selfDescriptor.setEnumerable(true);
            selfDescriptor.setConfigurable(true);
            global.defineProperty(PropertyKey.fromString(name), selfDescriptor);
            if (immutableOverlayBindingNames != null) {
                immutableOverlayBindingNames.add(name);
            }
        }

        private static int parseFixedHex(String str, int start, int length) {
            int end = start + length;
            if (end > str.length()) {
                return -1;
            }
            int value = 0;
            for (int i = start; i < end; i++) {
                int digit = Character.digit(str.charAt(i), 16);
                if (digit < 0) {
                    return -1;
                }
                value = (value << 4) | digit;
            }
            return value;
        }

        /**
         * parseFloat(string) Parse a string and return a floating point number.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-parsefloat-string">ECMAScript parseFloat</a>
         */
        public static JSValue parseFloat(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue input = JSValue.argumentOrUndefined(args, 0);
            String inputString = JSTypeConversions.toString(context, input).value();
            int index = skipLeadingWhitespace(inputString);
            if (index >= inputString.length()) {
                return JSNumber.of(Double.NaN);
            }

            int signIndex = index;
            if (inputString.charAt(index) == '+' || inputString.charAt(index) == '-') {
                index++;
            }
            if (isInfinityPrefix(inputString, index)) {
                boolean isNegative = inputString.charAt(signIndex) == '-';
                return JSNumber.of(isNegative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
            }

            int i = index;
            boolean hasLeadingDigits = false;
            while (i < inputString.length() && isAsciiDigit(inputString.charAt(i))) {
                hasLeadingDigits = true;
                i++;
            }

            boolean hasFractionDigits = false;
            if (i < inputString.length() && inputString.charAt(i) == '.') {
                i++;
                while (i < inputString.length() && isAsciiDigit(inputString.charAt(i))) {
                    hasFractionDigits = true;
                    i++;
                }
            }

            if (!hasLeadingDigits && !hasFractionDigits) {
                return JSNumber.of(Double.NaN);
            }

            if (i < inputString.length() && (inputString.charAt(i) == 'e' || inputString.charAt(i) == 'E')) {
                int exponentMarkerIndex = i;
                i++;
                if (i < inputString.length() && (inputString.charAt(i) == '+' || inputString.charAt(i) == '-')) {
                    i++;
                }
                int exponentDigitsStart = i;
                while (i < inputString.length() && isAsciiDigit(inputString.charAt(i))) {
                    i++;
                }
                if (i == exponentDigitsStart) {
                    i = exponentMarkerIndex;
                }
            }

            String validNumber = inputString.substring(signIndex, i);
            return JSNumber.of(Double.parseDouble(validNumber));
        }

        /**
         * parseInt(string, radix) Parse a string and return an integer of the specified radix.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-parseint-string-radix">ECMAScript parseInt</a>
         */
        public static JSValue parseInt(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue input = JSValue.argumentOrUndefined(args, 0);
            String inputString = JSTypeConversions.toString(context, input).value();
            int index = skipLeadingWhitespace(inputString);
            if (index >= inputString.length()) {
                return JSNumber.of(Double.NaN);
            }

            int sign = 1;
            if (inputString.charAt(index) == '+') {
                index++;
            } else if (inputString.charAt(index) == '-') {
                sign = -1;
                index++;
            }

            int radix = 0;
            if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
                JSValue radixValue = args[1];
                if (radixValue.isSymbol() || radixValue.isSymbolObject()) {
                    return context.throwTypeError("cannot convert symbol to number");
                }
                if (radixValue.isBigInt() || radixValue.isBigIntObject()) {
                    return context.throwTypeError("cannot convert bigint to number");
                }
                radix = JSTypeConversions.toInt32(context, radixValue);
            }

            if (radix != 0 && (radix < 2 || radix > 36)) {
                return JSNumber.of(Double.NaN);
            }

            boolean stripPrefix = radix == 0 || radix == 16;
            if (radix == 0) {
                radix = 10;
            }
            if (stripPrefix && index + 1 < inputString.length() && inputString.charAt(index) == '0'
                    && (inputString.charAt(index + 1) == 'x' || inputString.charAt(index + 1) == 'X')) {
                index += 2;
                radix = 16;
            }

            double result = 0.0;
            boolean foundDigit = false;
            while (index < inputString.length()) {
                int digit = asciiDigitValue(inputString.charAt(index));
                if (digit < 0 || digit >= radix) {
                    break;
                }
                result = result * radix + digit;
                foundDigit = true;
                index++;
            }

            if (!foundDigit) {
                return JSNumber.of(Double.NaN);
            }

            return JSNumber.of(sign * result);
        }

        private static Expression parseTopLevelExpression(String wrappedSource) {
            try {
                Program parsedProgram = new Compiler(wrappedSource, "<eval-caller>").parse(false);
                if (parsedProgram.getBody().isEmpty()
                        || !(parsedProgram.getBody().get(0) instanceof ExpressionStatement expressionStatement)) {
                    return null;
                }
                return expressionStatement.getExpression();
            } catch (Exception ignored) {
                return null;
            }
        }

        private static int parseWithDepth(String localName) {
            int depth = 0;
            int index = "$withObject".length();
            while (index < localName.length()) {
                char current = localName.charAt(index);
                if (current < '0' || current > '9') {
                    return depth;
                }
                depth = depth * 10 + (current - '0');
                index++;
            }
            return depth;
        }

        /**
         * Note what the global held under a name before the overlay took it over, so the restore can put it back.
         *
         * @param global
         *            the realm's global object
         * @param name
         *            the overlaid name
         * @param savedGlobals
         *            globals displaced by the overlay
         * @param absentKeys
         *            overlay names that did not exist before
         * @param touchedOverlayKeys
         *            overlay names already saved
         */
        private static void rememberOverlayKey(JSObject global, String name, Map<String, JSValue> savedGlobals,
                Map<String, PropertyDescriptor> savedDescriptors, Set<String> absentKeys,
                Set<String> touchedOverlayKeys) {
            if (name == null || touchedOverlayKeys == null || savedGlobals == null || absentKeys == null) {
                return;
            }
            if (touchedOverlayKeys.add(name)) {
                PropertyKey key = PropertyKey.fromString(name);
                if (global.has(key)) {
                    savedGlobals.put(name, global.get(key));
                    PropertyDescriptor descriptor = global.getOwnPropertyDescriptor(key);
                    if (descriptor != null) {
                        savedDescriptors.put(name, descriptor);
                    }
                } else {
                    absentKeys.add(name);
                }
            }
        }

        private static int skipLeadingWhitespace(String str) {
            int index = 0;
            while (index < str.length() && isEcmaWhitespace(str.charAt(index))) {
                index++;
            }
            return index;
        }

        /**
         * unescape(string) Deprecated function that decodes a string encoded by escape(). Decodes %XX and %uXXXX
         * sequences.
         *
         * @see <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/unescape">MDN
         *      unescape</a>
         */
        public static JSValue unescape(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue stringValue = JSValue.argumentOrUndefined(args, 0);
            String str = JSTypeConversions.toString(context, stringValue).value();

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);

                if (c == '%') {
                    // Try to parse %uXXXX format
                    if (i + 6 <= str.length() && str.charAt(i + 1) == 'u') {
                        int codePoint = parseFixedHex(str, i + 2, 4);
                        if (codePoint >= 0) {
                            result.append((char) codePoint);
                            i += 5; // Skip the next 5 characters (uXXXX)
                            continue;
                        }
                    }

                    // Try to parse %XX format
                    if (i + 3 <= str.length()) {
                        int codePoint = parseFixedHex(str, i + 1, 2);
                        if (codePoint >= 0) {
                            result.append((char) codePoint);
                            i += 2; // Skip the next 2 characters (XX)
                            continue;
                        }
                    }
                }

                // Not an escape sequence, append as is
                result.append(c);
            }

            return new JSString(result.toString());
        }

        private record CallerVarEnvironmentAnalysis(Set<String> bodyVarNames, Set<String> functionVarEnvironmentNames,
                Set<String> constLexicalNames) {
        }

        private record WithObjectCandidate(int depth, JSObject object) {
        }
    }
}
