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
import com.caoccao.qjs4j.compilation.ast.*;
import com.caoccao.qjs4j.compilation.compiler.Compiler;
import com.caoccao.qjs4j.exceptions.JSErrorType;
import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.vm.StackFrame;

import java.util.*;
import java.util.stream.Stream;

/**
 * The global object with built-in functions.
 * Based on ECMAScript specification global properties and functions.
 * <p>
 * Implements:
 * - Global value properties (NaN, Infinity, undefined)
 * - Global function properties (parseInt, parseFloat, isNaN, isFinite, eval)
 * - URI handling functions (encodeURI, decodeURI, encodeURIComponent, decodeURIComponent)
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
        this.globalObject = new JSObject();
        this.jsonObject = new JSONObject();
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
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("resize"), new JSNativeFunction("resize", 1, ArrayBufferPrototype::resize), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("slice"), new JSNativeFunction("slice", 2, ArrayBufferPrototype::slice), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("transfer"), new JSNativeFunction("transfer", 0, ArrayBufferPrototype::transfer), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("transferToFixedLength"), new JSNativeFunction("transferToFixedLength", 0, ArrayBufferPrototype::transferToFixedLength), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("transferToImmutable"), new JSNativeFunction("transferToImmutable", 0, ArrayBufferPrototype::transferToImmutable), PropertyDescriptor.DataState.ConfigurableWritable);

        // Define getter properties
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("byteLength"), new JSNativeFunction("get byteLength", 0, ArrayBufferPrototype::getByteLength), PropertyDescriptor.AccessorState.Configurable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("detached"), new JSNativeFunction("get detached", 0, ArrayBufferPrototype::getDetached), PropertyDescriptor.AccessorState.Configurable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("maxByteLength"), new JSNativeFunction("get maxByteLength", 0, ArrayBufferPrototype::getMaxByteLength), PropertyDescriptor.AccessorState.Configurable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("resizable"), new JSNativeFunction("get resizable", 0, ArrayBufferPrototype::getResizable), PropertyDescriptor.AccessorState.Configurable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSArrayBuffer.NAME), PropertyDescriptor.DataState.Configurable);

        // Create ArrayBuffer constructor as a function
        JSNativeFunction arrayBufferConstructor = new JSNativeFunction(JSArrayBuffer.NAME, 1, ArrayBufferConstructor::call, true, true);
        arrayBufferConstructor.defineProperty(PropertyKey.fromString("prototype"), arrayBufferPrototype, PropertyDescriptor.DataState.None);
        arrayBufferConstructor.setConstructorType(JSConstructorType.ARRAY_BUFFER);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("constructor"), arrayBufferConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Static methods
        arrayBufferConstructor.defineProperty(PropertyKey.fromString("isView"), new JSNativeFunction("isView", 1, ArrayBufferConstructor::isView), PropertyDescriptor.DataState.ConfigurableWritable);

        // Symbol.species getter
        arrayBufferConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction("get [Symbol.species]", 0, ArrayBufferConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString(JSArrayBuffer.NAME), arrayBufferConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Array constructor and prototype.
     */
    private void initializeArrayConstructor() {
        // Create Array.prototype as an Array exotic object per ES spec 23.1.3
        // (matching QuickJS JS_NewArray for JS_CLASS_ARRAY)
        JSArray arrayPrototype = new JSArray(0, 0);
        context.transferPrototype(arrayPrototype, JSObject.NAME);
        JSNativeFunction valuesFunction = new JSNativeFunction("values", 0, IteratorPrototype::arrayValues);
        arrayPrototype.defineProperty(PropertyKey.fromString("at"), new JSNativeFunction("at", 1, ArrayPrototype::at), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("concat"), new JSNativeFunction("concat", 1, ArrayPrototype::concat), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("copyWithin"), new JSNativeFunction("copyWithin", 2, ArrayPrototype::copyWithin), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("entries"), new JSNativeFunction("entries", 0, IteratorPrototype::arrayEntries), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("every"), new JSNativeFunction("every", 1, ArrayPrototype::every), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("fill"), new JSNativeFunction("fill", 1, ArrayPrototype::fill), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("filter"), new JSNativeFunction("filter", 1, ArrayPrototype::filter), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("find"), new JSNativeFunction("find", 1, ArrayPrototype::find), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("findIndex"), new JSNativeFunction("findIndex", 1, ArrayPrototype::findIndex), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("findLast"), new JSNativeFunction("findLast", 1, ArrayPrototype::findLast), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("findLastIndex"), new JSNativeFunction("findLastIndex", 1, ArrayPrototype::findLastIndex), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("flat"), new JSNativeFunction("flat", 0, ArrayPrototype::flat), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("flatMap"), new JSNativeFunction("flatMap", 1, ArrayPrototype::flatMap), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("forEach"), new JSNativeFunction("forEach", 1, ArrayPrototype::forEach), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("includes"), new JSNativeFunction("includes", 1, ArrayPrototype::includes), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("indexOf"), new JSNativeFunction("indexOf", 1, ArrayPrototype::indexOf), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("join"), new JSNativeFunction("join", 1, ArrayPrototype::join), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("keys"), new JSNativeFunction("keys", 0, IteratorPrototype::arrayKeys), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("lastIndexOf"), new JSNativeFunction("lastIndexOf", 1, ArrayPrototype::lastIndexOf), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("map"), new JSNativeFunction("map", 1, ArrayPrototype::map), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("pop"), new JSNativeFunction("pop", 0, ArrayPrototype::pop), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("push"), new JSNativeFunction("push", 1, ArrayPrototype::push), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("reduce"), new JSNativeFunction("reduce", 1, ArrayPrototype::reduce), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("reduceRight"), new JSNativeFunction("reduceRight", 1, ArrayPrototype::reduceRight), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("reverse"), new JSNativeFunction("reverse", 0, ArrayPrototype::reverse), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("shift"), new JSNativeFunction("shift", 0, ArrayPrototype::shift), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("slice"), new JSNativeFunction("slice", 2, ArrayPrototype::slice), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("some"), new JSNativeFunction("some", 1, ArrayPrototype::some), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("sort"), new JSNativeFunction("sort", 1, ArrayPrototype::sort), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("splice"), new JSNativeFunction("splice", 2, ArrayPrototype::splice), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction("toLocaleString", 0, ArrayPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("toReversed"), new JSNativeFunction("toReversed", 0, ArrayPrototype::toReversed), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("toSorted"), new JSNativeFunction("toSorted", 1, ArrayPrototype::toSorted), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("toSpliced"), new JSNativeFunction("toSpliced", 2, ArrayPrototype::toSpliced), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction("toString", 0, ArrayPrototype::toString), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("unshift"), new JSNativeFunction("unshift", 1, ArrayPrototype::unshift), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("values"), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("with"), new JSNativeFunction("with", 2, ArrayPrototype::with), PropertyDescriptor.DataState.ConfigurableWritable);

        // Array.prototype[Symbol.*]
        arrayPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.UNSCOPABLES), ArrayPrototype.createUnscopablesObject(context), PropertyDescriptor.DataState.Configurable);

        // Create Array constructor as a function
        JSNativeFunction arrayConstructor = new JSNativeFunction(JSArray.NAME, 1, ArrayConstructor::call, true);
        arrayConstructor.defineProperty(PropertyKey.fromString("prototype"), arrayPrototype, PropertyDescriptor.DataState.None);
        arrayConstructor.setConstructorType(JSConstructorType.ARRAY);
        arrayPrototype.defineProperty(PropertyKey.fromString("constructor"), arrayConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Array static methods (writable, non-enumerable, configurable per spec)
        arrayConstructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction("from", 1, ArrayConstructor::from), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayConstructor.defineProperty(PropertyKey.fromString("fromAsync"), new JSNativeFunction("fromAsync", 1, ArrayConstructor::fromAsync), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayConstructor.defineProperty(PropertyKey.fromString("isArray"), new JSNativeFunction("isArray", 1, ArrayConstructor::isArray), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayConstructor.defineProperty(PropertyKey.fromString("of"), new JSNativeFunction("of", 0, ArrayConstructor::of), PropertyDescriptor.DataState.ConfigurableWritable);

        // Symbol.species getter
        arrayConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction("get [Symbol.species]", 0, ArrayConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString(JSArray.NAME), arrayConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize AsyncDisposableStack constructor and prototype.
     */
    private void initializeAsyncDisposableStackConstructor() {
        JSObject asyncDisposableStackPrototype = context.createJSObject();
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("adopt"), new JSNativeFunction("adopt", 2, AsyncDisposableStackPrototype::adopt), PropertyDescriptor.DataState.ConfigurableWritable);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("defer"), new JSNativeFunction("defer", 1, AsyncDisposableStackPrototype::defer), PropertyDescriptor.DataState.ConfigurableWritable);
        JSNativeFunction disposeAsyncFunction = new JSNativeFunction("disposeAsync", 0, AsyncDisposableStackPrototype::disposeAsync);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("disposeAsync"), disposeAsyncFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("move"), new JSNativeFunction("move", 0, AsyncDisposableStackPrototype::move), PropertyDescriptor.DataState.ConfigurableWritable);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("use"), new JSNativeFunction("use", 1, AsyncDisposableStackPrototype::use), PropertyDescriptor.DataState.ConfigurableWritable);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ASYNC_DISPOSE), disposeAsyncFunction, PropertyDescriptor.DataState.ConfigurableWritable);

        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("disposed"), new JSNativeFunction("get disposed", 0, AsyncDisposableStackPrototype::getDisposed), PropertyDescriptor.AccessorState.Configurable);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSNativeFunction("get [Symbol.toStringTag]", 0, (childContext, thisObj, args) -> {
            if (!(thisObj instanceof JSAsyncDisposableStack)) {
                return childContext.throwTypeError("get AsyncDisposableStack.prototype[Symbol.toStringTag] called on non-AsyncDisposableStack");
            }
            return new JSString(JSAsyncDisposableStack.NAME);
        }), PropertyDescriptor.AccessorState.Configurable);

        JSNativeFunction asyncDisposableStackConstructor = new JSNativeFunction(
                JSAsyncDisposableStack.NAME, 0, AsyncDisposableStackConstructor::call, true, true);
        asyncDisposableStackConstructor.defineProperty(PropertyKey.fromString("prototype"), asyncDisposableStackPrototype, PropertyDescriptor.DataState.None);
        asyncDisposableStackConstructor.setConstructorType(JSConstructorType.ASYNC_DISPOSABLE_STACK);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("constructor"), asyncDisposableStackConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSAsyncDisposableStack.NAME), asyncDisposableStackConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize AsyncFunction constructor and prototype.
     * AsyncFunction is not exposed in global scope but is available via async function constructors.
     */
    private void initializeAsyncFunctionConstructor() {
        // Create AsyncFunction.prototype that inherits from Function.prototype
        JSObject asyncFunctionPrototype = context.createJSObject();
        context.transferPrototype(asyncFunctionPrototype, JSFunction.NAME);
        asyncFunctionPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("AsyncFunction"), PropertyDescriptor.DataState.Configurable);

        // Create AsyncFunction constructor
        // AsyncFunction is not normally exposed, but we need it for the prototype chain
        JSNativeFunction asyncFunctionConstructor = new JSNativeFunction("AsyncFunction", 1,
                FunctionConstructor::callAsync, true);
        JSValue functionConstructorValue = globalObject.get(JSFunction.NAME);
        if (functionConstructorValue instanceof JSObject functionConstructorObject) {
            asyncFunctionConstructor.setPrototype(functionConstructorObject);
        }
        asyncFunctionConstructor.defineProperty(PropertyKey.fromString("prototype"), asyncFunctionPrototype, PropertyDescriptor.DataState.None);
        asyncFunctionPrototype.defineProperty(PropertyKey.fromString("constructor"), asyncFunctionConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Store AsyncFunction in the context (not in global scope)
        // This is used internally for setting up prototype chains for async bytecode functions
        context.setAsyncFunctionConstructor(asyncFunctionConstructor);
    }

    /**
     * Initialize AsyncGenerator and AsyncGeneratorFunction prototypes.
     * Based on QuickJS JS_AddIntrinsicPromise (AsyncGeneratorFunction section).
     * <p>
     * Prototype chain:
     * - AsyncGenerator.prototype inherits from AsyncIteratorPrototype
     * - AsyncGeneratorFunction.prototype inherits from Function.prototype
     * - AsyncGeneratorFunction.prototype.prototype = AsyncGenerator.prototype (configurable)
     * - AsyncGenerator.prototype.constructor = AsyncGeneratorFunction.prototype (configurable)
     */
    private void initializeAsyncGeneratorPrototype() {
        // Create AsyncIteratorPrototype (has Symbol.asyncIterator)
        JSObject asyncIteratorPrototype = context.createJSObject();
        JSNativeFunction asyncIteratorMethod = new JSNativeFunction(
                "[Symbol.asyncIterator]",
                0,
                (ctx, thisArg, args) -> thisArg);
        asyncIteratorMethod.initializePrototypeChain(context);
        asyncIteratorPrototype.defineProperty(
                PropertyKey.fromSymbol(JSSymbol.ASYNC_ITERATOR),
                PropertyDescriptor.dataDescriptor(asyncIteratorMethod, PropertyDescriptor.DataState.ConfigurableWritable));
        JSNativeFunction asyncDisposeMethod = new JSNativeFunction(
                "[Symbol.asyncDispose]",
                0,
                AsyncIteratorPrototype::asyncDispose);
        asyncDisposeMethod.initializePrototypeChain(context);
        asyncIteratorPrototype.defineProperty(
                PropertyKey.fromSymbol(JSSymbol.ASYNC_DISPOSE),
                PropertyDescriptor.dataDescriptor(asyncDisposeMethod, PropertyDescriptor.DataState.ConfigurableWritable));

        // Create AsyncGenerator.prototype inheriting from AsyncIteratorPrototype
        JSObject asyncGeneratorPrototype = context.createJSObject();
        asyncGeneratorPrototype.setPrototype(asyncIteratorPrototype);

        // AsyncGenerator.prototype methods are set up on individual JSAsyncGenerator instances
        // but we need next/return/throw on the prototype too for spec compliance
        JSNativeFunction asyncGeneratorNext = new JSNativeFunction("next", 1, AsyncGeneratorPrototype::next);
        asyncGeneratorNext.initializePrototypeChain(context);
        asyncGeneratorPrototype.defineProperty(PropertyKey.fromString("next"), asyncGeneratorNext, PropertyDescriptor.DataState.ConfigurableWritable);
        JSNativeFunction asyncGeneratorReturn = new JSNativeFunction("return", 1, AsyncGeneratorPrototype::return_);
        asyncGeneratorReturn.initializePrototypeChain(context);
        asyncGeneratorPrototype.defineProperty(PropertyKey.fromString("return"), asyncGeneratorReturn, PropertyDescriptor.DataState.ConfigurableWritable);
        JSNativeFunction asyncGeneratorThrow = new JSNativeFunction("throw", 1, AsyncGeneratorPrototype::throw_);
        asyncGeneratorThrow.initializePrototypeChain(context);
        asyncGeneratorPrototype.defineProperty(PropertyKey.fromString("throw"), asyncGeneratorThrow, PropertyDescriptor.DataState.ConfigurableWritable);

        // AsyncGenerator.prototype[Symbol.toStringTag] = "AsyncGenerator" (configurable)
        asyncGeneratorPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("AsyncGenerator"), PropertyDescriptor.DataState.Configurable);
        context.setAsyncGeneratorPrototype(asyncGeneratorPrototype);

        // Create AsyncGeneratorFunction.prototype (not exposed in global scope)
        // All async generator function objects (async function*) inherit from this
        JSObject asyncGeneratorFunctionPrototype = context.createJSObject();
        context.transferPrototype(asyncGeneratorFunctionPrototype, JSFunction.NAME);

        // AsyncGeneratorFunction.prototype[Symbol.toStringTag] = "AsyncGeneratorFunction" (configurable)
        asyncGeneratorFunctionPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("AsyncGeneratorFunction"), PropertyDescriptor.DataState.Configurable);

        // Link: AsyncGeneratorFunction.prototype.prototype = AsyncGenerator.prototype (configurable)
        asyncGeneratorFunctionPrototype.defineProperty(PropertyKey.fromString("prototype"), asyncGeneratorPrototype, PropertyDescriptor.DataState.Configurable);

        // Link: AsyncGenerator.prototype.constructor = AsyncGeneratorFunction.prototype (configurable)
        asyncGeneratorPrototype.defineProperty(PropertyKey.fromString("constructor"), asyncGeneratorFunctionPrototype, PropertyDescriptor.DataState.Configurable);

        // Create AsyncGeneratorFunction constructor (uses dynamic function construction)
        JSNativeFunction asyncGeneratorFunctionConstructor = new JSNativeFunction(
                "AsyncGeneratorFunction", 1,
                FunctionConstructor::callAsyncGenerator,
                true);
        asyncGeneratorFunctionConstructor.defineProperty(PropertyKey.fromString("prototype"), asyncGeneratorFunctionPrototype, PropertyDescriptor.DataState.None);
        asyncGeneratorFunctionPrototype.defineProperty(PropertyKey.fromString("constructor"), asyncGeneratorFunctionConstructor, PropertyDescriptor.DataState.Configurable);

        // Store in context for async generator function prototype chain setup
        context.setAsyncGeneratorFunctionPrototype(asyncGeneratorFunctionPrototype);
    }

    /**
     * Initialize Atomics object.
     */
    private void initializeAtomicsObject() {
        AtomicsObject atomicsObject = context.getRuntime().getOptions().getAtomicsObject();
        JSObject atomics = context.createJSObject();
        atomics.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction("add", 3, atomicsObject::add), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("and"), new JSNativeFunction("and", 3, atomicsObject::and), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("compareExchange"), new JSNativeFunction("compareExchange", 4, atomicsObject::compareExchange), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("exchange"), new JSNativeFunction("exchange", 3, atomicsObject::exchange), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("isLockFree"), new JSNativeFunction("isLockFree", 1, atomicsObject::isLockFree), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("load"), new JSNativeFunction("load", 2, atomicsObject::load), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("notify"), new JSNativeFunction("notify", 3, atomicsObject::notify), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("or"), new JSNativeFunction("or", 3, atomicsObject::or), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("pause"), new JSNativeFunction("pause", 0, atomicsObject::pause), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("store"), new JSNativeFunction("store", 3, atomicsObject::store), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("sub"), new JSNativeFunction("sub", 3, atomicsObject::sub), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("wait"), new JSNativeFunction("wait", 4, atomicsObject::wait), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("waitAsync"), new JSNativeFunction("waitAsync", 4, atomicsObject::waitAsync), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("xor"), new JSNativeFunction("xor", 3, atomicsObject::xor), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Atomics"), PropertyDescriptor.DataState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString("Atomics"), atomics, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize BigInt constructor and static methods.
     */
    private void initializeBigIntConstructor() {
        // Create BigInt.prototype
        JSObject bigIntPrototype = context.createJSObject();
        bigIntPrototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction("toLocaleString", 0, BigIntPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        bigIntPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction("toString", 0, BigIntPrototype::toString), PropertyDescriptor.DataState.ConfigurableWritable);
        bigIntPrototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction("valueOf", 0, BigIntPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);
        bigIntPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSBigInt.NAME), PropertyDescriptor.DataState.Configurable);

        // Create BigInt constructor
        JSNativeFunction bigIntConstructor = new JSNativeFunction(JSBigInt.NAME, 1, BigIntConstructor::call, true);
        bigIntConstructor.defineProperty(PropertyKey.fromString("prototype"), bigIntPrototype, PropertyDescriptor.DataState.None);
        bigIntConstructor.setConstructorType(JSConstructorType.BIG_INT_OBJECT); // Mark as BigInt constructor
        bigIntPrototype.defineProperty(PropertyKey.fromString("constructor"), bigIntConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // BigInt static methods
        bigIntConstructor.defineProperty(PropertyKey.fromString("asIntN"), new JSNativeFunction("asIntN", 2, BigIntConstructor::asIntN), PropertyDescriptor.DataState.ConfigurableWritable);
        bigIntConstructor.defineProperty(PropertyKey.fromString("asUintN"), new JSNativeFunction("asUintN", 2, BigIntConstructor::asUintN), PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSBigInt.NAME), bigIntConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Boolean constructor and prototype.
     */
    private void initializeBooleanConstructor() {
        // Create Boolean.prototype as a Boolean object with [[BooleanData]] = false
        // Per QuickJS: JS_SetObjectData(ctx, ctx->class_proto[JS_CLASS_BOOLEAN], JS_NewBool(ctx, FALSE))
        JSBooleanObject booleanPrototype = new JSBooleanObject(false);
        context.transferPrototype(booleanPrototype, JSObject.NAME);
        booleanPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction("toString", 0, BooleanPrototype::toString), PropertyDescriptor.DataState.ConfigurableWritable);
        booleanPrototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction("valueOf", 0, BooleanPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);

        // Create Boolean constructor
        JSNativeFunction booleanConstructor = new JSNativeFunction(JSBoolean.NAME, 1, BooleanConstructor::call, true);
        booleanConstructor.defineProperty(PropertyKey.fromString("prototype"), booleanPrototype, PropertyDescriptor.DataState.None);
        booleanConstructor.setConstructorType(JSConstructorType.BOOLEAN_OBJECT);
        booleanPrototype.defineProperty(PropertyKey.fromString("constructor"), booleanConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSBoolean.NAME), booleanConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize console object with all console API methods.
     */
    private void initializeConsoleObject() {
        JSObject consoleObj = context.createJSObject();
        consoleObj.set("assert", new JSNativeFunction("assert", 0, console::assert_));
        consoleObj.set("clear", new JSNativeFunction("clear", 0, console::clear));
        consoleObj.set("count", new JSNativeFunction("count", 0, console::count));
        consoleObj.set("countReset", new JSNativeFunction("countReset", 0, console::countReset));
        consoleObj.set("debug", new JSNativeFunction("debug", 0, console::debug));
        consoleObj.set("dir", new JSNativeFunction("dir", 0, console::dir));
        consoleObj.set("dirxml", new JSNativeFunction("dirxml", 0, console::dirxml));
        consoleObj.set("error", new JSNativeFunction("error", 0, console::error));
        consoleObj.set("group", new JSNativeFunction("group", 0, console::group));
        consoleObj.set("groupCollapsed", new JSNativeFunction("groupCollapsed", 0, console::groupCollapsed));
        consoleObj.set("groupEnd", new JSNativeFunction("groupEnd", 0, console::groupEnd));
        consoleObj.set("info", new JSNativeFunction("info", 0, console::info));
        consoleObj.set("log", new JSNativeFunction("log", 1, console::log));
        consoleObj.set("table", new JSNativeFunction("table", 0, console::table));
        consoleObj.set("time", new JSNativeFunction("time", 0, console::time));
        consoleObj.set("timeEnd", new JSNativeFunction("timeEnd", 0, console::timeEnd));
        consoleObj.set("timeLog", new JSNativeFunction("timeLog", 0, console::timeLog));
        consoleObj.set("trace", new JSNativeFunction("trace", 0, console::trace));
        consoleObj.set("warn", new JSNativeFunction("warn", 0, console::warn));

        globalObject.defineProperty(PropertyKey.fromString("console"), consoleObj, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize DataView constructor and prototype.
     */
    private void initializeDataViewConstructor() {
        // Create DataView.prototype
        JSObject dataViewPrototype = context.createJSObject();

        // Define getter properties
        dataViewPrototype.defineProperty(PropertyKey.fromString("buffer"), new JSNativeFunction("get buffer", 0, DataViewPrototype::getBuffer), PropertyDescriptor.AccessorState.Configurable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("byteLength"), new JSNativeFunction("get byteLength", 0, DataViewPrototype::getByteLength), PropertyDescriptor.AccessorState.Configurable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("byteOffset"), new JSNativeFunction("get byteOffset", 0, DataViewPrototype::getByteOffset), PropertyDescriptor.AccessorState.Configurable);
        dataViewPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("DataView"), PropertyDescriptor.DataState.Configurable);

        // Int8/Uint8 methods
        dataViewPrototype.defineProperty(PropertyKey.fromString("getInt8"), new JSNativeFunction("getInt8", 1, DataViewPrototype::getInt8), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setInt8"), new JSNativeFunction("setInt8", 2, DataViewPrototype::setInt8), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("getUint8"), new JSNativeFunction("getUint8", 1, DataViewPrototype::getUint8), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setUint8"), new JSNativeFunction("setUint8", 2, DataViewPrototype::setUint8), PropertyDescriptor.DataState.ConfigurableWritable);

        // Int16/Uint16 methods
        dataViewPrototype.defineProperty(PropertyKey.fromString("getInt16"), new JSNativeFunction("getInt16", 1, DataViewPrototype::getInt16), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setInt16"), new JSNativeFunction("setInt16", 2, DataViewPrototype::setInt16), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("getUint16"), new JSNativeFunction("getUint16", 1, DataViewPrototype::getUint16), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setUint16"), new JSNativeFunction("setUint16", 2, DataViewPrototype::setUint16), PropertyDescriptor.DataState.ConfigurableWritable);

        // Int32/Uint32 methods
        dataViewPrototype.defineProperty(PropertyKey.fromString("getInt32"), new JSNativeFunction("getInt32", 1, DataViewPrototype::getInt32), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setInt32"), new JSNativeFunction("setInt32", 2, DataViewPrototype::setInt32), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("getUint32"), new JSNativeFunction("getUint32", 1, DataViewPrototype::getUint32), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setUint32"), new JSNativeFunction("setUint32", 2, DataViewPrototype::setUint32), PropertyDescriptor.DataState.ConfigurableWritable);

        // BigInt methods
        dataViewPrototype.defineProperty(PropertyKey.fromString("getBigInt64"), new JSNativeFunction("getBigInt64", 1, DataViewPrototype::getBigInt64), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setBigInt64"), new JSNativeFunction("setBigInt64", 2, DataViewPrototype::setBigInt64), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("getBigUint64"), new JSNativeFunction("getBigUint64", 1, DataViewPrototype::getBigUint64), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setBigUint64"), new JSNativeFunction("setBigUint64", 2, DataViewPrototype::setBigUint64), PropertyDescriptor.DataState.ConfigurableWritable);

        // Float methods
        dataViewPrototype.defineProperty(PropertyKey.fromString("getFloat16"), new JSNativeFunction("getFloat16", 1, DataViewPrototype::getFloat16), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setFloat16"), new JSNativeFunction("setFloat16", 2, DataViewPrototype::setFloat16), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("getFloat32"), new JSNativeFunction("getFloat32", 1, DataViewPrototype::getFloat32), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setFloat32"), new JSNativeFunction("setFloat32", 2, DataViewPrototype::setFloat32), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("getFloat64"), new JSNativeFunction("getFloat64", 1, DataViewPrototype::getFloat64), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setFloat64"), new JSNativeFunction("setFloat64", 2, DataViewPrototype::setFloat64), PropertyDescriptor.DataState.ConfigurableWritable);

        // Create DataView constructor as a function that requires 'new'
        JSNativeFunction dataViewConstructor = new JSNativeFunction("DataView", 1, DataViewConstructor::call, true, true);
        dataViewConstructor.defineProperty(PropertyKey.fromString("prototype"), dataViewPrototype, PropertyDescriptor.DataState.None);
        dataViewConstructor.setConstructorType(JSConstructorType.DATA_VIEW);
        dataViewPrototype.defineProperty(PropertyKey.fromString("constructor"), dataViewConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString("DataView"), dataViewConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Date constructor and prototype.
     */
    private void initializeDateConstructor() {
        JSObject datePrototype = context.createJSObject();
        JSNativeFunction toUTCString = new JSNativeFunction("toUTCString", 0, DatePrototype::toUTCString);
        JSNativeFunction toPrimitive = new JSNativeFunction("[Symbol.toPrimitive]", 1, DatePrototype::symbolToPrimitive);

        datePrototype.defineProperty(PropertyKey.fromString("getDate"), new JSNativeFunction("getDate", 0, DatePrototype::getDate), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getDay"), new JSNativeFunction("getDay", 0, DatePrototype::getDay), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getFullYear"), new JSNativeFunction("getFullYear", 0, DatePrototype::getFullYear), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getHours"), new JSNativeFunction("getHours", 0, DatePrototype::getHours), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getMilliseconds"), new JSNativeFunction("getMilliseconds", 0, DatePrototype::getMilliseconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getMinutes"), new JSNativeFunction("getMinutes", 0, DatePrototype::getMinutes), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getMonth"), new JSNativeFunction("getMonth", 0, DatePrototype::getMonth), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getSeconds"), new JSNativeFunction("getSeconds", 0, DatePrototype::getSeconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getTime"), new JSNativeFunction("getTime", 0, DatePrototype::getTime), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getTimezoneOffset"), new JSNativeFunction("getTimezoneOffset", 0, DatePrototype::getTimezoneOffset), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCDate"), new JSNativeFunction("getUTCDate", 0, DatePrototype::getUTCDate), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCDay"), new JSNativeFunction("getUTCDay", 0, DatePrototype::getUTCDay), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCFullYear"), new JSNativeFunction("getUTCFullYear", 0, DatePrototype::getUTCFullYear), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCHours"), new JSNativeFunction("getUTCHours", 0, DatePrototype::getUTCHours), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCMilliseconds"), new JSNativeFunction("getUTCMilliseconds", 0, DatePrototype::getUTCMilliseconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCMinutes"), new JSNativeFunction("getUTCMinutes", 0, DatePrototype::getUTCMinutes), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCMonth"), new JSNativeFunction("getUTCMonth", 0, DatePrototype::getUTCMonth), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCSeconds"), new JSNativeFunction("getUTCSeconds", 0, DatePrototype::getUTCSeconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getYear"), new JSNativeFunction("getYear", 0, DatePrototype::getYear), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setDate"), new JSNativeFunction("setDate", 1, DatePrototype::setDate), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setFullYear"), new JSNativeFunction("setFullYear", 3, DatePrototype::setFullYear), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setHours"), new JSNativeFunction("setHours", 4, DatePrototype::setHours), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setMilliseconds"), new JSNativeFunction("setMilliseconds", 1, DatePrototype::setMilliseconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setMinutes"), new JSNativeFunction("setMinutes", 3, DatePrototype::setMinutes), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setMonth"), new JSNativeFunction("setMonth", 2, DatePrototype::setMonth), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setSeconds"), new JSNativeFunction("setSeconds", 2, DatePrototype::setSeconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setTime"), new JSNativeFunction("setTime", 1, DatePrototype::setTime), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCDate"), new JSNativeFunction("setUTCDate", 1, DatePrototype::setUTCDate), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCFullYear"), new JSNativeFunction("setUTCFullYear", 3, DatePrototype::setUTCFullYear), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCHours"), new JSNativeFunction("setUTCHours", 4, DatePrototype::setUTCHours), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCMilliseconds"), new JSNativeFunction("setUTCMilliseconds", 1, DatePrototype::setUTCMilliseconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCMinutes"), new JSNativeFunction("setUTCMinutes", 3, DatePrototype::setUTCMinutes), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCMonth"), new JSNativeFunction("setUTCMonth", 2, DatePrototype::setUTCMonth), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCSeconds"), new JSNativeFunction("setUTCSeconds", 2, DatePrototype::setUTCSeconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setYear"), new JSNativeFunction("setYear", 1, DatePrototype::setYear), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toDateString"), new JSNativeFunction("toDateString", 0, DatePrototype::toDateString), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toGMTString"), toUTCString, PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toISOString"), new JSNativeFunction("toISOString", 0, DatePrototype::toISOString), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toJSON"), new JSNativeFunction("toJSON", 1, DatePrototype::toJSON), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toLocaleDateString"), new JSNativeFunction("toLocaleDateString", 0, DatePrototype::toLocaleDateString), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction("toLocaleString", 0, DatePrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toLocaleTimeString"), new JSNativeFunction("toLocaleTimeString", 0, DatePrototype::toLocaleTimeString), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction("toString", 0, DatePrototype::toStringMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toTimeString"), new JSNativeFunction("toTimeString", 0, DatePrototype::toTimeString), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toUTCString"), toUTCString, PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction("valueOf", 0, DatePrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_PRIMITIVE), toPrimitive, PropertyDescriptor.DataState.Configurable);

        JSNativeFunction dateConstructor = new JSNativeFunction(JSDate.NAME, 7, DateConstructor::call, true);
        dateConstructor.defineProperty(PropertyKey.fromString("prototype"), datePrototype, PropertyDescriptor.DataState.None);
        dateConstructor.setConstructorType(JSConstructorType.DATE);
        datePrototype.defineProperty(PropertyKey.fromString("constructor"), dateConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        dateConstructor.defineProperty(PropertyKey.fromString("UTC"), new JSNativeFunction("UTC", 7, DateConstructor::UTC), PropertyDescriptor.DataState.ConfigurableWritable);
        dateConstructor.defineProperty(PropertyKey.fromString("now"), new JSNativeFunction("now", 0, DateConstructor::now), PropertyDescriptor.DataState.ConfigurableWritable);
        dateConstructor.defineProperty(PropertyKey.fromString("parse"), new JSNativeFunction("parse", 1, DateConstructor::parse), PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSDate.NAME), dateConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize DisposableStack constructor and prototype.
     */
    private void initializeDisposableStackConstructor() {
        JSObject disposableStackPrototype = context.createJSObject();
        JSNativeFunction disposeFunction = new JSNativeFunction("dispose", 0, DisposableStackPrototype::dispose);
        disposableStackPrototype.defineProperty(PropertyKey.fromString("adopt"), new JSNativeFunction("adopt", 2, DisposableStackPrototype::adopt), PropertyDescriptor.DataState.ConfigurableWritable);
        disposableStackPrototype.defineProperty(PropertyKey.fromString("defer"), new JSNativeFunction("defer", 1, DisposableStackPrototype::defer), PropertyDescriptor.DataState.ConfigurableWritable);
        disposableStackPrototype.defineProperty(PropertyKey.fromString("dispose"), disposeFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        disposableStackPrototype.defineProperty(PropertyKey.fromString("move"), new JSNativeFunction("move", 0, DisposableStackPrototype::move), PropertyDescriptor.DataState.ConfigurableWritable);
        disposableStackPrototype.defineProperty(PropertyKey.fromString("use"), new JSNativeFunction("use", 1, DisposableStackPrototype::use), PropertyDescriptor.DataState.ConfigurableWritable);
        disposableStackPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.DISPOSE), disposeFunction, PropertyDescriptor.DataState.ConfigurableWritable);

        disposableStackPrototype.defineProperty(PropertyKey.fromString("disposed"), new JSNativeFunction("get disposed", 0, DisposableStackPrototype::getDisposed), PropertyDescriptor.AccessorState.Configurable);
        disposableStackPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSDisposableStack.NAME), PropertyDescriptor.DataState.Configurable);

        JSNativeFunction disposableStackConstructor = new JSNativeFunction(
                JSDisposableStack.NAME, 0, DisposableStackConstructor::call, true, true);
        disposableStackConstructor.defineProperty(PropertyKey.fromString("prototype"), disposableStackPrototype, PropertyDescriptor.DataState.None);
        disposableStackConstructor.setConstructorType(JSConstructorType.DISPOSABLE_STACK);
        disposableStackPrototype.defineProperty(PropertyKey.fromString("constructor"), disposableStackConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSDisposableStack.NAME), disposableStackConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Error constructors.
     */
    private void initializeErrorConstructors() {
        Stream.of(JSErrorType.values()).forEach(type -> globalObject.defineProperty(PropertyKey.fromString(type.name()), type.create(context), PropertyDescriptor.DataState.ConfigurableWritable));
    }

    /**
     * Initialize FinalizationRegistry constructor.
     */
    private void initializeFinalizationRegistryConstructor() {
        JSObject finalizationRegistryPrototype = context.createJSObject();
        finalizationRegistryPrototype.defineProperty(PropertyKey.fromString("register"),
                new JSNativeFunction("register", 2, FinalizationRegistryPrototype::register), PropertyDescriptor.DataState.ConfigurableWritable);
        finalizationRegistryPrototype.defineProperty(PropertyKey.fromString("unregister"),
                new JSNativeFunction("unregister", 1, FinalizationRegistryPrototype::unregister), PropertyDescriptor.DataState.ConfigurableWritable);
        finalizationRegistryPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                new JSString(JSFinalizationRegistry.NAME), PropertyDescriptor.DataState.Configurable);

        JSNativeFunction finalizationRegistryConstructor = new JSNativeFunction(
                JSFinalizationRegistry.NAME, 1, FinalizationRegistryConstructor::call, true, true);
        finalizationRegistryConstructor.defineProperty(PropertyKey.fromString("prototype"), finalizationRegistryPrototype, PropertyDescriptor.DataState.None);
        finalizationRegistryConstructor.setConstructorType(JSConstructorType.FINALIZATION_REGISTRY);
        finalizationRegistryPrototype.defineProperty(PropertyKey.fromString("constructor"), finalizationRegistryConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSFinalizationRegistry.NAME), finalizationRegistryConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Function constructor and prototype.
     */
    private void initializeFunctionConstructor() {
        // Create Function.prototype as a function (not a plain object)
        // According to ECMAScript spec, Function.prototype is itself a function
        // Use null name so toString() shows "function () {}" not "function anonymous() {}"
        JSNativeFunction functionPrototype = new JSNativeFunction(null, 0, (ctx, thisObj, args) -> JSUndefined.INSTANCE);
        // Remove the auto-created properties - Function.prototype has custom property descriptors
        functionPrototype.delete(PropertyKey.LENGTH);
        functionPrototype.delete(PropertyKey.NAME);
        functionPrototype.delete(PropertyKey.PROTOTYPE);

        functionPrototype.defineProperty(PropertyKey.fromString("apply"), new JSNativeFunction("apply", 2, FunctionPrototype::apply), PropertyDescriptor.DataState.ConfigurableWritable);
        functionPrototype.defineProperty(PropertyKey.fromString("bind"), new JSNativeFunction("bind", 1, FunctionPrototype::bind), PropertyDescriptor.DataState.ConfigurableWritable);
        functionPrototype.defineProperty(PropertyKey.fromString("call"), new JSNativeFunction("call", 1, FunctionPrototype::call), PropertyDescriptor.DataState.ConfigurableWritable);
        functionPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction("toString", 0, FunctionPrototype::toString_), PropertyDescriptor.DataState.ConfigurableWritable);

        // Add 'arguments' and 'caller' as accessor properties per QuickJS js_throw_type_error.
        // For non-strict bytecode functions with prototype (regular sloppy functions),
        // the getter returns undefined. For strict, arrow, async, generator functions
        // or setter calls, it throws TypeError.
        JSNativeFunction throwTypeError = new JSNativeFunction(
                "",
                0,
                (childContext, thisObj, args) -> {
                    if (thisObj instanceof JSBytecodeFunction bytecodeFunc) {
                        if (!bytecodeFunc.isStrict() && bytecodeFunc.isConstructor() && args.length == 0) {
                            return JSUndefined.INSTANCE;
                        }
                    }
                    return childContext.throwTypeError(
                            "'caller', 'callee', and 'arguments' properties may not be accessed on strict mode functions or the arguments objects for calls to them");
                });
        // Per ES spec 10.2.4: %ThrowTypeError% is frozen with non-configurable length and name.
        throwTypeError.defineProperty(PropertyKey.LENGTH,
                PropertyDescriptor.dataDescriptor(JSNumber.of(0), PropertyDescriptor.DataState.None));
        throwTypeError.defineProperty(PropertyKey.NAME,
                PropertyDescriptor.dataDescriptor(new JSString(""), PropertyDescriptor.DataState.None));
        throwTypeError.preventExtensions();
        // Store as the %ThrowTypeError% intrinsic for sharing with strict arguments.callee
        context.setThrowTypeErrorIntrinsic(throwTypeError);

        functionPrototype.defineProperty(PropertyKey.fromString("arguments"), throwTypeError, throwTypeError, PropertyDescriptor.AccessorState.Configurable);
        functionPrototype.defineProperty(PropertyKey.fromString("caller"), throwTypeError, throwTypeError, PropertyDescriptor.AccessorState.Configurable);

        // Function.prototype[Symbol.hasInstance] - implements OrdinaryHasInstance
        // Per ES spec 19.2.3.6: writable: false, enumerable: false, configurable: false
        JSNativeFunction hasInstanceFunc = new JSNativeFunction("[Symbol.hasInstance]", 1,
                FunctionPrototype::symbolHasInstance);
        functionPrototype.defineProperty(
                PropertyKey.SYMBOL_HAS_INSTANCE,
                PropertyDescriptor.dataDescriptor(hasInstanceFunc, PropertyDescriptor.DataState.None));

        // Add 'length' and 'name' data properties
        functionPrototype.defineProperty(PropertyKey.fromString("length"), JSNumber.of(0), PropertyDescriptor.DataState.Configurable);
        functionPrototype.defineProperty(PropertyKey.fromString("name"), new JSString(""), PropertyDescriptor.DataState.Configurable);

        // Per ECMAScript spec, Function.prototype's [[Prototype]] is Object.prototype
        context.transferPrototype(functionPrototype, JSObject.NAME);

        // Function constructor should be a function, not a plain object
        JSNativeFunction functionConstructor = new JSNativeFunction(JSFunction.NAME, 1, FunctionConstructor::call, true);
        functionConstructor.defineProperty(PropertyKey.fromString("prototype"), functionPrototype, PropertyDescriptor.DataState.None);
        functionPrototype.defineProperty(PropertyKey.fromString("constructor"), functionConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSFunction.NAME), functionConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Recursively initialize prototype chains for all JSFunction instances in the global object.
     * This must be called after Function.prototype is set up.
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
     * Initialize Generator and GeneratorFunction prototypes.
     * Based on QuickJS JS_AddIntrinsicGenerator.
     * <p>
     * Prototype chain:
     * - Generator.prototype inherits from Iterator.prototype
     * - GeneratorFunction.prototype inherits from Function.prototype
     * - GeneratorFunction.prototype.prototype = Generator.prototype (configurable)
     * - Generator.prototype.constructor = GeneratorFunction.prototype (configurable)
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
        generatorPrototype.defineProperty(PropertyKey.fromString("next"), new JSNativeFunction("next", 1, GeneratorPrototype::next), PropertyDescriptor.DataState.ConfigurableWritable);
        generatorPrototype.defineProperty(PropertyKey.fromString("return"), new JSNativeFunction("return", 1, GeneratorPrototype::returnMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        generatorPrototype.defineProperty(PropertyKey.fromString("throw"), new JSNativeFunction("throw", 1, GeneratorPrototype::throwMethod), PropertyDescriptor.DataState.ConfigurableWritable);

        // Symbol.toStringTag = "Generator" (configurable only)
        // Matches QuickJS JS_PROP_STRING_DEF("[Symbol.toStringTag]", "Generator", JS_PROP_CONFIGURABLE)
        generatorPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Generator"), PropertyDescriptor.DataState.Configurable);

        // Create GeneratorFunction.prototype (not exposed in global scope)
        // All generator function objects (function*) inherit from this
        JSObject generatorFunctionPrototype = context.createJSObject();
        context.transferPrototype(generatorFunctionPrototype, JSFunction.NAME);

        // GeneratorFunction.prototype[Symbol.toStringTag] = "GeneratorFunction" (configurable)
        generatorFunctionPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("GeneratorFunction"), PropertyDescriptor.DataState.Configurable);

        // Link: GeneratorFunction.prototype.prototype = Generator.prototype (configurable)
        generatorFunctionPrototype.defineProperty(PropertyKey.fromString("prototype"), generatorPrototype, PropertyDescriptor.DataState.Configurable);

        // Link: Generator.prototype.constructor = GeneratorFunction.prototype (configurable)
        generatorPrototype.defineProperty(PropertyKey.fromString("constructor"), generatorFunctionPrototype, PropertyDescriptor.DataState.Configurable);

        // Create GeneratorFunction constructor (uses dynamic function construction)
        // Following QuickJS JS_AddIntrinsicGenerator pattern
        JSNativeFunction generatorFunctionConstructor = new JSNativeFunction(
                "GeneratorFunction", 1,
                FunctionConstructor::callGenerator,
                true);
        generatorFunctionConstructor.defineProperty(PropertyKey.fromString("prototype"), generatorFunctionPrototype, PropertyDescriptor.DataState.None);
        generatorFunctionPrototype.defineProperty(
                PropertyKey.CONSTRUCTOR,
                PropertyDescriptor.dataDescriptor(generatorFunctionConstructor, PropertyDescriptor.DataState.Configurable));

        // Store in context for generator function prototype chain setup
        context.setGeneratorFunctionPrototype(generatorFunctionPrototype);
    }

    private void initializeGlobalObject() {
        // Global value properties (non-writable, non-enumerable, non-configurable)
        globalObject.defineProperty(PropertyKey.fromString("Infinity"), JSNumber.of(Double.POSITIVE_INFINITY), PropertyDescriptor.DataState.None);
        globalObject.defineProperty(PropertyKey.fromString("NaN"), JSNumber.of(Double.NaN), PropertyDescriptor.DataState.None);
        globalObject.defineProperty(PropertyKey.fromString("undefined"), JSUndefined.INSTANCE, PropertyDescriptor.DataState.None);

        // Global function properties
        // Capture the realm context so that eval code runs in the correct realm
        // even when called cross-realm (e.g., other.eval('code')).
        final JSContext realmContext = context;
        globalObject.defineProperty(PropertyKey.fromString("eval"), new JSNativeFunction("eval", 1,
                (callerCtx, thisArg, args) -> GlobalFunction.eval(realmContext, callerCtx, thisArg, args)), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("isFinite"), new JSNativeFunction("isFinite", 1, GlobalFunction::isFinite), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("isNaN"), new JSNativeFunction("isNaN", 1, GlobalFunction::isNaN), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("parseFloat"), new JSNativeFunction("parseFloat", 1, GlobalFunction::parseFloat), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("parseInt"), new JSNativeFunction("parseInt", 2, GlobalFunction::parseInt), PropertyDescriptor.DataState.ConfigurableWritable);

        // URI handling functions
        globalObject.defineProperty(PropertyKey.fromString("decodeURI"), new JSNativeFunction("decodeURI", 1, GlobalFunction::decodeURI), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("decodeURIComponent"), new JSNativeFunction("decodeURIComponent", 1, GlobalFunction::decodeURIComponent), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("encodeURI"), new JSNativeFunction("encodeURI", 1, GlobalFunction::encodeURI), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("encodeURIComponent"), new JSNativeFunction("encodeURIComponent", 1, GlobalFunction::encodeURIComponent), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("escape"), new JSNativeFunction("escape", 1, GlobalFunction::escape), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("unescape"), new JSNativeFunction("unescape", 1, GlobalFunction::unescape), PropertyDescriptor.DataState.ConfigurableWritable);

        // Console object for debugging
        initializeConsoleObject();

        // Global this reference
        globalObject.defineProperty(PropertyKey.fromString("globalThis"), globalObject, PropertyDescriptor.DataState.ConfigurableWritable);

        // Object.prototype.toString.call(globalThis) -> [object global]
        globalObject.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("global"), PropertyDescriptor.DataState.Configurable);
    }

    /**
     * Initialize Intl object.
     */
    private void initializeIntlObject() {
        JSObject intlObject = context.createJSObject();
        intlObject.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl"), PropertyDescriptor.DataState.Configurable);
        intlObject.defineProperty(PropertyKey.fromString("getCanonicalLocales"), new JSNativeFunction("getCanonicalLocales", 1, JSIntlObject::getCanonicalLocales), PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("supportedValuesOf"), new JSNativeFunction("supportedValuesOf", 1, JSIntlObject::supportedValuesOf_Intl), PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject dateTimeFormatPrototype = context.createJSObject();
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromString("format"),
                new JSNativeFunction("get format", 0, JSIntlObject::dateTimeFormatFormatGetter),
                PropertyDescriptor.AccessorState.Configurable);
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction("resolvedOptions", 0, JSIntlObject::dateTimeFormatResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromString("formatToParts"), new JSNativeFunction("formatToParts", 1, JSIntlObject::dateTimeFormatFormatToParts), PropertyDescriptor.DataState.ConfigurableWritable);
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromString("formatRange"), new JSNativeFunction("formatRange", 2, JSIntlObject::dateTimeFormatFormatRange), PropertyDescriptor.DataState.ConfigurableWritable);
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromString("formatRangeToParts"), new JSNativeFunction("formatRangeToParts", 2, JSIntlObject::dateTimeFormatFormatRangeToParts), PropertyDescriptor.DataState.ConfigurableWritable);
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.DateTimeFormat"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction dateTimeFormatConstructor = new JSNativeFunction(
                "DateTimeFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createDateTimeFormat(childContext, dateTimeFormatPrototype, args),
                true);
        dateTimeFormatConstructor.defineProperty(PropertyKey.fromString("prototype"), dateTimeFormatPrototype, PropertyDescriptor.DataState.None);
        dateTimeFormatConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromString("constructor"), dateTimeFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("DateTimeFormat"), dateTimeFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject displayNamesPrototype = context.createJSObject();
        displayNamesPrototype.defineProperty(PropertyKey.fromString("of"),
                new JSNativeFunction("of", 1, JSIntlObject::displayNamesOf),
                PropertyDescriptor.DataState.ConfigurableWritable);
        displayNamesPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"),
                new JSNativeFunction("resolvedOptions", 0, JSIntlObject::displayNamesResolvedOptions),
                PropertyDescriptor.DataState.ConfigurableWritable);
        displayNamesPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                new JSString("Intl.DisplayNames"),
                PropertyDescriptor.DataState.Configurable);
        JSNativeFunction displayNamesConstructor = new JSNativeFunction(
                "DisplayNames",
                2,
                (childContext, thisArg, args) -> JSIntlObject.createDisplayNames(childContext, displayNamesPrototype, args),
                true,
                true);
        displayNamesConstructor.defineProperty(PropertyKey.fromString("prototype"), displayNamesPrototype, PropertyDescriptor.DataState.None);
        displayNamesConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"),
                new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf),
                PropertyDescriptor.DataState.ConfigurableWritable);
        displayNamesPrototype.defineProperty(PropertyKey.fromString("constructor"), displayNamesConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("DisplayNames"), displayNamesConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject durationFormatPrototype = context.createJSObject();
        durationFormatPrototype.defineProperty(PropertyKey.fromString("format"),
                new JSNativeFunction("format", 1, JSIntlObject::durationFormatFormat),
                PropertyDescriptor.DataState.ConfigurableWritable);
        durationFormatPrototype.defineProperty(PropertyKey.fromString("formatToParts"),
                new JSNativeFunction("formatToParts", 1, JSIntlObject::durationFormatFormatToParts),
                PropertyDescriptor.DataState.ConfigurableWritable);
        durationFormatPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"),
                new JSNativeFunction("resolvedOptions", 0, JSIntlObject::durationFormatResolvedOptions),
                PropertyDescriptor.DataState.ConfigurableWritable);
        durationFormatPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                new JSString("Intl.DurationFormat"),
                PropertyDescriptor.DataState.Configurable);
        JSNativeFunction durationFormatConstructor = new JSNativeFunction(
                "DurationFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createDurationFormat(childContext, durationFormatPrototype, args),
                true,
                true);
        durationFormatConstructor.defineProperty(PropertyKey.fromString("prototype"), durationFormatPrototype, PropertyDescriptor.DataState.None);
        durationFormatConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"),
                new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf),
                PropertyDescriptor.DataState.ConfigurableWritable);
        durationFormatPrototype.defineProperty(PropertyKey.fromString("constructor"), durationFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("DurationFormat"), durationFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject numberFormatPrototype = context.createJSObject();
        numberFormatPrototype.defineProperty(PropertyKey.fromString("format"),
                new JSNativeFunction("get format", 0, JSIntlObject::numberFormatFormatGetter),
                PropertyDescriptor.AccessorState.Configurable);
        numberFormatPrototype.defineProperty(PropertyKey.fromString("formatRange"),
                new JSNativeFunction("formatRange", 2, JSIntlObject::numberFormatFormatRange),
                PropertyDescriptor.DataState.ConfigurableWritable);
        numberFormatPrototype.defineProperty(PropertyKey.fromString("formatRangeToParts"),
                new JSNativeFunction("formatRangeToParts", 2, JSIntlObject::numberFormatFormatRangeToParts),
                PropertyDescriptor.DataState.ConfigurableWritable);
        numberFormatPrototype.defineProperty(PropertyKey.fromString("formatToParts"), new JSNativeFunction("formatToParts", 1, JSIntlObject::numberFormatFormatToParts), PropertyDescriptor.DataState.ConfigurableWritable);
        numberFormatPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction("resolvedOptions", 0, JSIntlObject::numberFormatResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        numberFormatPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.NumberFormat"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction numberFormatConstructor = new JSNativeFunction(
                "NumberFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createNumberFormat(childContext, numberFormatPrototype, args),
                true);
        numberFormatConstructor.defineProperty(PropertyKey.fromString("prototype"), numberFormatPrototype, PropertyDescriptor.DataState.None);
        numberFormatConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        numberFormatPrototype.defineProperty(PropertyKey.fromString("constructor"), numberFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("NumberFormat"), numberFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject collatorPrototype = context.createJSObject();
        collatorPrototype.defineProperty(PropertyKey.fromString("compare"),
                new JSNativeFunction("get compare", 0, JSIntlObject::collatorCompareGetter),
                PropertyDescriptor.AccessorState.Configurable);
        collatorPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction("resolvedOptions", 0, JSIntlObject::collatorResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        collatorPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.Collator"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction collatorConstructor = new JSNativeFunction(
                "Collator",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createCollator(childContext, collatorPrototype, args),
                true);
        collatorConstructor.defineProperty(PropertyKey.fromString("prototype"), collatorPrototype, PropertyDescriptor.DataState.None);
        collatorConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        collatorPrototype.defineProperty(PropertyKey.fromString("constructor"), collatorConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("Collator"), collatorConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject pluralRulesPrototype = context.createJSObject();
        pluralRulesPrototype.defineProperty(PropertyKey.fromString("select"), new JSNativeFunction("select", 1, JSIntlObject::pluralRulesSelect), PropertyDescriptor.DataState.ConfigurableWritable);
        pluralRulesPrototype.defineProperty(PropertyKey.fromString("selectRange"), new JSNativeFunction("selectRange", 2, JSIntlObject::pluralRulesSelectRange), PropertyDescriptor.DataState.ConfigurableWritable);
        pluralRulesPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction("resolvedOptions", 0, JSIntlObject::pluralRulesResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        pluralRulesPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.PluralRules"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction pluralRulesConstructor = new JSNativeFunction(
                "PluralRules",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createPluralRules(childContext, pluralRulesPrototype, args),
                true,
                true);
        pluralRulesConstructor.defineProperty(PropertyKey.fromString("prototype"), pluralRulesPrototype, PropertyDescriptor.DataState.None);
        pluralRulesConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        pluralRulesPrototype.defineProperty(PropertyKey.fromString("constructor"), pluralRulesConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("PluralRules"), pluralRulesConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject relativeTimeFormatPrototype = context.createJSObject();
        relativeTimeFormatPrototype.defineProperty(PropertyKey.fromString("format"), new JSNativeFunction("format", 2, JSIntlObject::relativeTimeFormatFormat), PropertyDescriptor.DataState.ConfigurableWritable);
        relativeTimeFormatPrototype.defineProperty(PropertyKey.fromString("formatToParts"), new JSNativeFunction("formatToParts", 2, JSIntlObject::relativeTimeFormatFormatToParts), PropertyDescriptor.DataState.ConfigurableWritable);
        relativeTimeFormatPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction("resolvedOptions", 0, JSIntlObject::relativeTimeFormatResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        relativeTimeFormatPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.RelativeTimeFormat"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction relativeTimeFormatConstructor = new JSNativeFunction(
                "RelativeTimeFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createRelativeTimeFormat(childContext, relativeTimeFormatPrototype, args),
                true,
                true);
        relativeTimeFormatConstructor.defineProperty(PropertyKey.fromString("prototype"), relativeTimeFormatPrototype, PropertyDescriptor.DataState.None);
        relativeTimeFormatConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        relativeTimeFormatPrototype.defineProperty(PropertyKey.fromString("constructor"), relativeTimeFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("RelativeTimeFormat"), relativeTimeFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject segmentDataPrototype = context.createJSObject();
        JSNativeFunction segmentsContainingFunction = new JSNativeFunction("containing", 1, JSIntlObject::segmentsContaining);
        segmentsContainingFunction.initializePrototypeChain(context);
        segmentDataPrototype.defineProperty(PropertyKey.fromString("containing"), segmentsContainingFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        JSNativeFunction segmentsIteratorFunction = new JSNativeFunction("[Symbol.iterator]", 0, JSIntlObject::segmentsIterator);
        segmentsIteratorFunction.initializePrototypeChain(context);
        segmentDataPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR), segmentsIteratorFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        JSObject segmenterPrototype = context.createJSObject();
        segmenterPrototype.defineProperty(PropertyKey.fromString("segment"), new JSNativeFunction("segment", 1, JSIntlObject::segmenterSegment), PropertyDescriptor.DataState.ConfigurableWritable);
        segmenterPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction("resolvedOptions", 0, JSIntlObject::segmenterResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        segmenterPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.Segmenter"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction segmenterConstructor = new JSNativeFunction(
                "Segmenter",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createSegmenter(childContext, segmenterPrototype, segmentDataPrototype, args),
                true,
                true);
        segmenterConstructor.defineProperty(PropertyKey.fromString("prototype"), segmenterPrototype, PropertyDescriptor.DataState.None);
        segmenterConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        segmenterPrototype.defineProperty(PropertyKey.fromString("constructor"), segmenterConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("Segmenter"), segmenterConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject listFormatPrototype = context.createJSObject();
        listFormatPrototype.defineProperty(PropertyKey.fromString("format"), new JSNativeFunction("format", 1, JSIntlObject::listFormatFormat), PropertyDescriptor.DataState.ConfigurableWritable);
        listFormatPrototype.defineProperty(PropertyKey.fromString("formatToParts"), new JSNativeFunction("formatToParts", 1, JSIntlObject::listFormatFormatToParts), PropertyDescriptor.DataState.ConfigurableWritable);
        listFormatPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction("resolvedOptions", 0, JSIntlObject::listFormatResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        listFormatPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.ListFormat"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction listFormatConstructor = new JSNativeFunction(
                "ListFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createListFormat(childContext, listFormatPrototype, args),
                true,
                true);
        listFormatConstructor.defineProperty(PropertyKey.fromString("prototype"), listFormatPrototype, PropertyDescriptor.DataState.None);
        listFormatConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        listFormatPrototype.defineProperty(PropertyKey.fromString("constructor"), listFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("ListFormat"), listFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject localePrototype = context.createJSObject();
        localePrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction("toString", 0, JSIntlObject::localeToString), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("baseName"), new JSNativeFunction("get baseName", 0, JSIntlObject::localeGetBaseName), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("calendar"), new JSNativeFunction("get calendar", 0, JSIntlObject::localeGetCalendar), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("caseFirst"), new JSNativeFunction("get caseFirst", 0, JSIntlObject::localeGetCaseFirst), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("collation"), new JSNativeFunction("get collation", 0, JSIntlObject::localeGetCollation), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("hourCycle"), new JSNativeFunction("get hourCycle", 0, JSIntlObject::localeGetHourCycle), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("language"), new JSNativeFunction("get language", 0, JSIntlObject::localeGetLanguage), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("numberingSystem"), new JSNativeFunction("get numberingSystem", 0, JSIntlObject::localeGetNumberingSystem), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("numeric"), new JSNativeFunction("get numeric", 0, JSIntlObject::localeGetNumeric), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("script"), new JSNativeFunction("get script", 0, JSIntlObject::localeGetScript), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("region"), new JSNativeFunction("get region", 0, JSIntlObject::localeGetRegion), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("variants"), new JSNativeFunction("get variants", 0, JSIntlObject::localeGetVariants), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("firstDayOfWeek"), new JSNativeFunction("get firstDayOfWeek", 0, JSIntlObject::localeGetFirstDayOfWeek), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("maximize"), new JSNativeFunction("maximize", 0, JSIntlObject::localeMaximize), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("minimize"), new JSNativeFunction("minimize", 0, JSIntlObject::localeMinimize), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getCalendars"), new JSNativeFunction("getCalendars", 0, JSIntlObject::localeGetCalendars), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getCollations"), new JSNativeFunction("getCollations", 0, JSIntlObject::localeGetCollations), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getHourCycles"), new JSNativeFunction("getHourCycles", 0, JSIntlObject::localeGetHourCycles), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getNumberingSystems"), new JSNativeFunction("getNumberingSystems", 0, JSIntlObject::localeGetNumberingSystems), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getTextInfo"), new JSNativeFunction("getTextInfo", 0, JSIntlObject::localeGetTextInfo), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getTimeZones"), new JSNativeFunction("getTimeZones", 0, JSIntlObject::localeGetTimeZones), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getWeekInfo"), new JSNativeFunction("getWeekInfo", 0, JSIntlObject::localeGetWeekInfo), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.Locale"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction localeConstructor = new JSNativeFunction(
                "Locale",
                1,
                (childContext, thisArg, args) -> JSIntlObject.createLocale(childContext, localePrototype, args),
                true,
                true);
        localeConstructor.defineProperty(PropertyKey.fromString("prototype"), localePrototype, PropertyDescriptor.DataState.None);
        localePrototype.defineProperty(PropertyKey.fromString("constructor"), localeConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("Locale"), localeConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString("Intl"), intlObject, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Iterator constructor and prototype.
     * Based on ECMAScript 2024 Iterator specification.
     */
    private void initializeIteratorConstructor() {
        JSObject iteratorPrototype = context.createJSObject();

        iteratorPrototype.defineProperty(PropertyKey.fromString("drop"), new JSNativeFunction("drop", 1, IteratorPrototype::drop), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("every"), new JSNativeFunction("every", 1, IteratorPrototype::every), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("filter"), new JSNativeFunction("filter", 1, IteratorPrototype::filter), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("find"), new JSNativeFunction("find", 1, IteratorPrototype::find), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("flatMap"), new JSNativeFunction("flatMap", 1, IteratorPrototype::flatMap), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("forEach"), new JSNativeFunction("forEach", 1, IteratorPrototype::forEach), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("map"), new JSNativeFunction("map", 1, IteratorPrototype::map), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("next"), new JSNativeFunction("next", 0, IteratorPrototype::next), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("reduce"), new JSNativeFunction("reduce", 1, IteratorPrototype::reduce), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("some"), new JSNativeFunction("some", 1, IteratorPrototype::some), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("take"), new JSNativeFunction("take", 1, IteratorPrototype::take), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("toArray"), new JSNativeFunction("toArray", 0, IteratorPrototype::toArray), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR), new JSNativeFunction("[Symbol.iterator]", 0, (childContext, thisArg, args) -> thisArg), PropertyDescriptor.DataState.ConfigurableWritable);

        // Iterator.prototype[Symbol.dispose] - calls this.return() per explicit-resource-management spec
        iteratorPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.DISPOSE), new JSNativeFunction("[Symbol.dispose]", 0, (childContext, thisArg, args) -> {
            if (!(thisArg instanceof JSObject thisObject)) {
                return childContext.throwTypeError("not an object");
            }
            JSValue returnMethod = thisObject.get(childContext, PropertyKey.RETURN);
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

        JSNativeFunction iteratorConstructor = new JSNativeFunction(JSIterator.NAME, 0, IteratorConstructor::call, true, true);
        iteratorConstructor.defineProperty(
                PropertyKey.PROTOTYPE,
                PropertyDescriptor.dataDescriptor(iteratorPrototype, PropertyDescriptor.DataState.None));
        iteratorConstructor.defineProperty(PropertyKey.fromString("concat"), new JSNativeFunction("concat", 0, IteratorPrototype::concat), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorConstructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction("from", 1, IteratorPrototype::from), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorConstructor.defineProperty(PropertyKey.fromString("zip"), new JSNativeFunction("zip", 1, IteratorPrototype::zip), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorConstructor.defineProperty(PropertyKey.fromString("zipKeyed"), new JSNativeFunction("zipKeyed", 1, IteratorPrototype::zipKeyed), PropertyDescriptor.DataState.ConfigurableWritable);

        JSNativeFunction constructorAccessor = new JSNativeFunction("constructor", 0, (childContext, thisArg, args) -> {
            if (args.length > 0) {
                if (!(args[0] instanceof JSObject valueObject)) {
                    return childContext.throwTypeError("not an object");
                }
                if (!(thisArg instanceof JSObject thisObject)) {
                    return childContext.throwTypeError("not an object");
                }
                thisObject.defineProperty(
                        PropertyKey.CONSTRUCTOR,
                        PropertyDescriptor.dataDescriptor(valueObject, PropertyDescriptor.DataState.ConfigurableWritable));
                return JSUndefined.INSTANCE;
            }
            return iteratorConstructor;
        });
        iteratorPrototype.defineProperty(
                PropertyKey.CONSTRUCTOR,
                PropertyDescriptor.accessorDescriptor(constructorAccessor, constructorAccessor, PropertyDescriptor.AccessorState.Configurable));

        JSNativeFunction toStringTagGetter = new JSNativeFunction(
                "get [Symbol.toStringTag]",
                0,
                (childContext, thisArg, args) -> new JSString(JSIterator.NAME),
                false);
        JSNativeFunction toStringTagSetter = new JSNativeFunction(
                "set [Symbol.toStringTag]",
                1,
                (childContext, thisArg, args) -> {
                    if (!(thisArg instanceof JSObject thisObject)) {
                        return childContext.throwTypeError("not an object");
                    }
                    if (thisObject == iteratorPrototype) {
                        return childContext.throwTypeError("Cannot assign to read only property");
                    }
                    JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                    PropertyKey toStringTagKey = PropertyKey.SYMBOL_TO_STRING_TAG;
                    PropertyDescriptor descriptor = thisObject.getOwnPropertyDescriptor(toStringTagKey);
                    if (descriptor != null) {
                        thisObject.set(childContext, toStringTagKey, value);
                    } else {
                        thisObject.defineProperty(
                                toStringTagKey,
                                PropertyDescriptor.dataDescriptor(value, PropertyDescriptor.DataState.All));
                    }
                    return JSUndefined.INSTANCE;
                },
                false);
        iteratorPrototype.defineProperty(
                PropertyKey.SYMBOL_TO_STRING_TAG,
                PropertyDescriptor.accessorDescriptor(toStringTagGetter, toStringTagSetter, PropertyDescriptor.AccessorState.Configurable));

        // Create shared iterator-type-specific prototypes inheriting from Iterator.prototype
        // Each has its own 'next' (writable, configurable) and Symbol.toStringTag per ES2024 spec
        for (String tag : new String[]{"Array Iterator", "Map Iterator", "Set Iterator", "String Iterator", "Iterator Helper"}) {
            JSObject proto = new JSObject();
            proto.setPrototype(iteratorPrototype);
            proto.defineProperty(PropertyKey.fromString("next"), new JSNativeFunction("next", 0, IteratorPrototype::next), PropertyDescriptor.DataState.ConfigurableWritable);
            proto.defineProperty(
                    PropertyKey.SYMBOL_TO_STRING_TAG,
                    PropertyDescriptor.dataDescriptor(new JSString(tag), PropertyDescriptor.DataState.Configurable));
            context.registerIteratorPrototype(tag, proto);
        }

        JSObject regExpStringIteratorPrototype = new JSObject();
        regExpStringIteratorPrototype.setPrototype(iteratorPrototype);
        regExpStringIteratorPrototype.defineProperty(
                PropertyKey.fromString("next"),
                new JSNativeFunction("next", 0, RegExpPrototype::regExpStringIteratorNext),
                PropertyDescriptor.DataState.ConfigurableWritable);
        regExpStringIteratorPrototype.defineProperty(
                PropertyKey.SYMBOL_TO_STRING_TAG,
                PropertyDescriptor.dataDescriptor(new JSString("RegExp String Iterator"), PropertyDescriptor.DataState.Configurable));
        context.registerIteratorPrototype("RegExp String Iterator", regExpStringIteratorPrototype);

        // Create %WrapForValidIteratorPrototype% for Iterator.from wrapper objects
        // This prototype inherits from Iterator.prototype and provides next/return methods
        JSObject wrapForValidIteratorPrototype = new JSObject();
        wrapForValidIteratorPrototype.setPrototype(iteratorPrototype);
        context.registerIteratorPrototype("Iterator Wrap", wrapForValidIteratorPrototype);

        globalObject.defineProperty(PropertyKey.fromString(JSIterator.NAME), iteratorConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize JSON object.
     */
    private void initializeJSONObject() {
        JSObject json = context.createJSObject();
        json.defineProperty(PropertyKey.fromString("parse"), new JSNativeFunction("parse", 2, jsonObject::parse), PropertyDescriptor.DataState.ConfigurableWritable);
        json.defineProperty(PropertyKey.fromString("stringify"), new JSNativeFunction("stringify", 3, jsonObject::stringify), PropertyDescriptor.DataState.ConfigurableWritable);
        json.defineProperty(PropertyKey.fromString("rawJSON"), new JSNativeFunction("rawJSON", 1, jsonObject::rawJSON), PropertyDescriptor.DataState.ConfigurableWritable);
        json.defineProperty(PropertyKey.fromString("isRawJSON"), new JSNativeFunction("isRawJSON", 1, jsonObject::isRawJSON), PropertyDescriptor.DataState.ConfigurableWritable);
        json.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("JSON"), PropertyDescriptor.DataState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString("JSON"), json, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Map constructor and prototype methods.
     */
    private void initializeMapConstructor() {
        // Create Map.prototype
        JSObject mapPrototype = context.createJSObject();
        mapPrototype.defineProperty(PropertyKey.fromString("clear"), new JSNativeFunction("clear", 0, MapPrototype::clear), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("delete"), new JSNativeFunction("delete", 1, MapPrototype::delete), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("forEach"), new JSNativeFunction("forEach", 1, MapPrototype::forEach), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("get"), new JSNativeFunction("get", 1, MapPrototype::get), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("getOrInsert"), new JSNativeFunction("getOrInsert", 2, MapPrototype::getOrInsert), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("getOrInsertComputed"), new JSNativeFunction("getOrInsertComputed", 2, MapPrototype::getOrInsertComputed), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("has"), new JSNativeFunction("has", 1, MapPrototype::has), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("set"), new JSNativeFunction("set", 2, MapPrototype::set), PropertyDescriptor.DataState.ConfigurableWritable);
        // Create entries function once and use it for both entries and Symbol.iterator (ES spec requirement)
        JSNativeFunction entriesFunction = new JSNativeFunction("entries", 0, IteratorPrototype::mapEntriesIterator);
        mapPrototype.defineProperty(PropertyKey.fromString("entries"), entriesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("keys"), new JSNativeFunction("keys", 0, IteratorPrototype::mapKeysIterator), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("values"), new JSNativeFunction("values", 0, IteratorPrototype::mapValuesIterator), PropertyDescriptor.DataState.ConfigurableWritable);
        // Map.prototype[Symbol.iterator] is the same function object as entries() (QuickJS uses JS_ALIAS_DEF)
        mapPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR), entriesFunction, PropertyDescriptor.DataState.ConfigurableWritable);

        // Map.prototype.size getter
        mapPrototype.defineProperty(PropertyKey.fromString("size"), new JSNativeFunction("get size", 0, MapPrototype::getSize), PropertyDescriptor.AccessorState.Configurable);
        mapPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSMap.NAME), PropertyDescriptor.DataState.Configurable);

        // Create Map constructor as JSNativeFunction
        JSNativeFunction mapConstructor = new JSNativeFunction(JSMap.NAME, 0, MapConstructor::call, true, true);
        mapConstructor.defineProperty(PropertyKey.fromString("prototype"), mapPrototype, PropertyDescriptor.DataState.None);
        mapConstructor.setConstructorType(JSConstructorType.MAP); // Mark as Map constructor
        mapPrototype.defineProperty(PropertyKey.fromString("constructor"), mapConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Map static methods
        mapConstructor.defineProperty(PropertyKey.fromString("groupBy"), new JSNativeFunction("groupBy", 2, MapConstructor::groupBy), PropertyDescriptor.DataState.ConfigurableWritable);
        mapConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction("get [Symbol.species]", 0, MapConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString(JSMap.NAME), mapConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Math object.
     */
    private void initializeMathObject() {
        JSObject math = context.createJSObject();

        // Math constants
        math.defineProperty(PropertyKey.fromString("E"), JSNumber.of(MathObject.E), PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("LN10"), JSNumber.of(MathObject.LN10), PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("LN2"), JSNumber.of(MathObject.LN2), PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("LOG10E"), JSNumber.of(MathObject.LOG10E), PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("LOG2E"), JSNumber.of(MathObject.LOG2E), PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("PI"), JSNumber.of(MathObject.PI), PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("SQRT1_2"), JSNumber.of(MathObject.SQRT1_2), PropertyDescriptor.DataState.None);
        math.defineProperty(PropertyKey.fromString("SQRT2"), JSNumber.of(MathObject.SQRT2), PropertyDescriptor.DataState.None);

        // Math methods
        math.defineProperty(PropertyKey.fromString("abs"), new JSNativeFunction("abs", 1, MathObject::abs), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("acos"), new JSNativeFunction("acos", 1, MathObject::acos), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("acosh"), new JSNativeFunction("acosh", 1, MathObject::acosh), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("asin"), new JSNativeFunction("asin", 1, MathObject::asin), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("asinh"), new JSNativeFunction("asinh", 1, MathObject::asinh), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("atan"), new JSNativeFunction("atan", 1, MathObject::atan), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("atanh"), new JSNativeFunction("atanh", 1, MathObject::atanh), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("atan2"), new JSNativeFunction("atan2", 2, MathObject::atan2), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("cbrt"), new JSNativeFunction("cbrt", 1, MathObject::cbrt), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("ceil"), new JSNativeFunction("ceil", 1, MathObject::ceil), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("clz32"), new JSNativeFunction("clz32", 1, MathObject::clz32), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("cos"), new JSNativeFunction("cos", 1, MathObject::cos), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("cosh"), new JSNativeFunction("cosh", 1, MathObject::cosh), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("exp"), new JSNativeFunction("exp", 1, MathObject::exp), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("expm1"), new JSNativeFunction("expm1", 1, MathObject::expm1), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("f16round"), new JSNativeFunction("f16round", 1, MathObject::f16round), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("floor"), new JSNativeFunction("floor", 1, MathObject::floor), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("fround"), new JSNativeFunction("fround", 1, MathObject::fround), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("hypot"), new JSNativeFunction("hypot", 2, MathObject::hypot), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("imul"), new JSNativeFunction("imul", 2, MathObject::imul), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("log"), new JSNativeFunction("log", 1, MathObject::log), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("log1p"), new JSNativeFunction("log1p", 1, MathObject::log1p), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("log10"), new JSNativeFunction("log10", 1, MathObject::log10), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("log2"), new JSNativeFunction("log2", 1, MathObject::log2), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("max"), new JSNativeFunction("max", 2, MathObject::max), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("min"), new JSNativeFunction("min", 2, MathObject::min), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("pow"), new JSNativeFunction("pow", 2, MathObject::pow), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("random"), new JSNativeFunction("random", 0, MathObject::random), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("round"), new JSNativeFunction("round", 1, MathObject::round), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("sign"), new JSNativeFunction("sign", 1, MathObject::sign), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("sin"), new JSNativeFunction("sin", 1, MathObject::sin), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("sinh"), new JSNativeFunction("sinh", 1, MathObject::sinh), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("sqrt"), new JSNativeFunction("sqrt", 1, MathObject::sqrt), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("sumPrecise"), new JSNativeFunction("sumPrecise", 1, MathObject::sumPrecise), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("tan"), new JSNativeFunction("tan", 1, MathObject::tan), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("tanh"), new JSNativeFunction("tanh", 1, MathObject::tanh), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("trunc"), new JSNativeFunction("trunc", 1, MathObject::trunc), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Math"), PropertyDescriptor.DataState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString("Math"), math, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Set [[Prototype]] of each NativeError constructor to the Error constructor.
     * Per ES2024 20.5.6.2: The value of the [[Prototype]] internal slot of a
     * NativeError constructor is the Error constructor.
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
        JSNumberObject numberPrototype = new JSNumberObject(0.0);
        context.transferPrototype(numberPrototype, JSObject.NAME);
        numberPrototype.defineProperty(PropertyKey.fromString("toExponential"), new JSNativeFunction("toExponential", 1, NumberPrototype::toExponential), PropertyDescriptor.DataState.ConfigurableWritable);
        numberPrototype.defineProperty(PropertyKey.fromString("toFixed"), new JSNativeFunction("toFixed", 1, NumberPrototype::toFixed), PropertyDescriptor.DataState.ConfigurableWritable);
        numberPrototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction("toLocaleString", 0, NumberPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        numberPrototype.defineProperty(PropertyKey.fromString("toPrecision"), new JSNativeFunction("toPrecision", 1, NumberPrototype::toPrecision), PropertyDescriptor.DataState.ConfigurableWritable);
        numberPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction("toString", 1, NumberPrototype::toString), PropertyDescriptor.DataState.ConfigurableWritable);
        numberPrototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction("valueOf", 0, NumberPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);

        // Create Number constructor
        JSNativeFunction numberConstructor = new JSNativeFunction(JSNumberObject.NAME, 1, NumberConstructor::call, true);
        numberConstructor.defineProperty(PropertyKey.fromString("prototype"), numberPrototype, PropertyDescriptor.DataState.None);
        numberConstructor.setConstructorType(JSConstructorType.NUMBER_OBJECT); // Mark as Number constructor
        numberPrototype.defineProperty(PropertyKey.fromString("constructor"), numberConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Number static methods
        numberConstructor.defineProperty(PropertyKey.fromString("isFinite"), new JSNativeFunction("isFinite", 1, NumberPrototype::isFinite), PropertyDescriptor.DataState.ConfigurableWritable);
        numberConstructor.defineProperty(PropertyKey.fromString("isInteger"), new JSNativeFunction("isInteger", 1, NumberPrototype::isInteger), PropertyDescriptor.DataState.ConfigurableWritable);
        numberConstructor.defineProperty(PropertyKey.fromString("isNaN"), new JSNativeFunction("isNaN", 1, NumberPrototype::isNaN), PropertyDescriptor.DataState.ConfigurableWritable);
        numberConstructor.defineProperty(PropertyKey.fromString("isSafeInteger"), new JSNativeFunction("isSafeInteger", 1, NumberPrototype::isSafeInteger), PropertyDescriptor.DataState.ConfigurableWritable);

        // QuickJS compatibility: Number.parseInt/parseFloat are aliases of global parseInt/parseFloat.
        JSValue globalParseInt = globalObject.get("parseInt");
        JSValue globalParseFloat = globalObject.get("parseFloat");
        numberConstructor.defineProperty(PropertyKey.fromString("EPSILON"), JSNumber.of(Math.ulp(1.0)), PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("MAX_SAFE_INTEGER"), JSNumber.of((double) NumberPrototype.MAX_SAFE_INTEGER), PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("MAX_VALUE"), JSNumber.of(Double.MAX_VALUE), PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("MIN_SAFE_INTEGER"), JSNumber.of((double) -NumberPrototype.MAX_SAFE_INTEGER), PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("MIN_VALUE"), JSNumber.of(Double.MIN_VALUE), PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("NEGATIVE_INFINITY"), JSNumber.of(Double.NEGATIVE_INFINITY), PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("NaN"), JSNumber.of(Double.NaN), PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("POSITIVE_INFINITY"), JSNumber.of(Double.POSITIVE_INFINITY), PropertyDescriptor.DataState.None);
        numberConstructor.defineProperty(PropertyKey.fromString("parseFloat"), globalParseFloat, PropertyDescriptor.DataState.ConfigurableWritable);
        numberConstructor.defineProperty(PropertyKey.fromString("parseInt"), globalParseInt, PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSNumberObject.NAME), numberConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Object constructor and static methods.
     */
    private void initializeObjectConstructor() {
        // Create Object.prototype
        JSObject objectPrototype = context.createJSObject();
        objectPrototype.defineProperty(PropertyKey.fromString("__defineGetter__"), new JSNativeFunction("__defineGetter__", 2, ObjectPrototype::__defineGetter__), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("__defineSetter__"), new JSNativeFunction("__defineSetter__", 2, ObjectPrototype::__defineSetter__), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("__lookupGetter__"), new JSNativeFunction("__lookupGetter__", 1, ObjectPrototype::__lookupGetter__), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("__lookupSetter__"), new JSNativeFunction("__lookupSetter__", 1, ObjectPrototype::__lookupSetter__), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("hasOwnProperty"), new JSNativeFunction("hasOwnProperty", 1, ObjectPrototype::hasOwnProperty), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("isPrototypeOf"), new JSNativeFunction("isPrototypeOf", 1, ObjectPrototype::isPrototypeOf), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("propertyIsEnumerable"), new JSNativeFunction("propertyIsEnumerable", 1, ObjectPrototype::propertyIsEnumerable), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction("toLocaleString", 0, ObjectPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction("toString", 0, ObjectPrototype::toString), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction("valueOf", 0, ObjectPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);

        // Define __proto__ as an accessor property
        PropertyDescriptor protoDesc = PropertyDescriptor.accessorDescriptor(
                new JSNativeFunction("get __proto__", 0, ObjectPrototype::__proto__Getter),
                new JSNativeFunction("set __proto__", 1, ObjectPrototype::__proto__Setter),
                PropertyDescriptor.AccessorState.Configurable
        );
        objectPrototype.defineProperty(PropertyKey.PROTO, protoDesc);

        // Object.prototype is an immutable prototype exotic object per ES2024 9.4.7
        objectPrototype.setImmutablePrototype();

        // Create Object constructor
        JSNativeFunction objectConstructor = new JSNativeFunction(JSObject.NAME, 1, ObjectConstructor::call, true);
        objectConstructor.defineProperty(PropertyKey.fromString("prototype"), objectPrototype, PropertyDescriptor.DataState.None);
        objectPrototype.defineProperty(PropertyKey.fromString("constructor"), objectConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Object static methods
        objectConstructor.defineProperty(PropertyKey.fromString("assign"), new JSNativeFunction("assign", 2, ObjectConstructor::assign), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("create"), new JSNativeFunction("create", 2, ObjectConstructor::create), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("defineProperties"), new JSNativeFunction("defineProperties", 2, ObjectConstructor::defineProperties), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("defineProperty"), new JSNativeFunction("defineProperty", 3, ObjectPrototype::defineProperty), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("entries"), new JSNativeFunction("entries", 1, ObjectConstructor::entries), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("freeze"), new JSNativeFunction("freeze", 1, ObjectConstructor::freeze), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("fromEntries"), new JSNativeFunction("fromEntries", 1, ObjectConstructor::fromEntries), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("getOwnPropertyDescriptor"), new JSNativeFunction("getOwnPropertyDescriptor", 2, ObjectConstructor::getOwnPropertyDescriptor), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("getOwnPropertyDescriptors"), new JSNativeFunction("getOwnPropertyDescriptors", 1, ObjectConstructor::getOwnPropertyDescriptors), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("getOwnPropertyNames"), new JSNativeFunction("getOwnPropertyNames", 1, ObjectConstructor::getOwnPropertyNames), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("getOwnPropertySymbols"), new JSNativeFunction("getOwnPropertySymbols", 1, ObjectConstructor::getOwnPropertySymbols), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("getPrototypeOf"), new JSNativeFunction("getPrototypeOf", 1, ObjectConstructor::getPrototypeOf), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("groupBy"), new JSNativeFunction("groupBy", 2, ObjectConstructor::groupBy), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("hasOwn"), new JSNativeFunction("hasOwn", 2, ObjectConstructor::hasOwn), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("is"), new JSNativeFunction("is", 2, ObjectConstructor::is), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("isExtensible"), new JSNativeFunction("isExtensible", 1, ObjectConstructor::isExtensible), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("isFrozen"), new JSNativeFunction("isFrozen", 1, ObjectConstructor::isFrozen), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("isSealed"), new JSNativeFunction("isSealed", 1, ObjectConstructor::isSealed), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("keys"), new JSNativeFunction("keys", 1, ObjectConstructor::keys), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("preventExtensions"), new JSNativeFunction("preventExtensions", 1, ObjectConstructor::preventExtensions), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("seal"), new JSNativeFunction("seal", 1, ObjectConstructor::seal), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("setPrototypeOf"), new JSNativeFunction("setPrototypeOf", 2, ObjectConstructor::setPrototypeOf), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("values"), new JSNativeFunction("values", 1, ObjectConstructor::values), PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSObject.NAME), objectConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Promise constructor and prototype methods.
     */
    private void initializePromiseConstructor() {
        // Create Promise.prototype
        JSObject promisePrototype = context.createJSObject();
        promisePrototype.defineProperty(PropertyKey.fromString("catch"), new JSNativeFunction("catch", 1, PromisePrototype::catchMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        promisePrototype.defineProperty(PropertyKey.fromString("finally"), new JSNativeFunction("finally", 1, PromisePrototype::finallyMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        promisePrototype.defineProperty(PropertyKey.fromString("then"), new JSNativeFunction("then", 2, PromisePrototype::then), PropertyDescriptor.DataState.ConfigurableWritable);
        promisePrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSPromise.NAME), PropertyDescriptor.DataState.Configurable);

        // Create Promise constructor as JSNativeFunction
        JSNativeFunction promiseConstructor = new JSNativeFunction(JSPromise.NAME, 1, PromiseConstructor::call, true, true);
        context.transferPrototype(promiseConstructor, JSFunction.NAME);
        promiseConstructor.defineProperty(PropertyKey.fromString("prototype"), promisePrototype, PropertyDescriptor.DataState.None);
        promiseConstructor.setConstructorType(JSConstructorType.PROMISE); // Mark as Promise constructor
        promisePrototype.defineProperty(PropertyKey.fromString("constructor"), promiseConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Static methods
        promiseConstructor.defineProperty(PropertyKey.fromString("all"), new JSNativeFunction("all", 1, PromiseConstructor::all), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("allSettled"), new JSNativeFunction("allSettled", 1, PromiseConstructor::allSettled), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("any"), new JSNativeFunction("any", 1, PromiseConstructor::any), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("race"), new JSNativeFunction("race", 1, PromiseConstructor::race), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("reject"), new JSNativeFunction("reject", 1, PromiseConstructor::reject), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("resolve"), new JSNativeFunction("resolve", 1, PromiseConstructor::resolve), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("try"), new JSNativeFunction("try", 1, PromiseConstructor::tryMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("withResolvers"), new JSNativeFunction("withResolvers", 0, PromiseConstructor::withResolvers), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction("get [Symbol.species]", 0, PromiseConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString(JSPromise.NAME), promiseConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Proxy constructor.
     */
    private void initializeProxyConstructor() {
        // Create Proxy constructor as JSNativeFunction
        // Proxy requires 'new' and takes 2 arguments (target, handler)
        JSNativeFunction proxyConstructor = new JSNativeFunction(JSProxy.NAME, 2, ProxyConstructor::call, true, true);
        proxyConstructor.setConstructorType(JSConstructorType.PROXY);
        context.transferPrototype(proxyConstructor, JSFunction.NAME);

        // Proxy has no "prototype" own property per QuickJS / spec.
        proxyConstructor.delete(PropertyKey.PROTOTYPE);

        // Add static methods.
        proxyConstructor.defineProperty(PropertyKey.fromString("revocable"), new JSNativeFunction("revocable", 2, ProxyConstructor::revocable), PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSProxy.NAME), proxyConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Reflect object.
     */
    private void initializeReflectObject() {
        JSObject reflect = context.createJSObject();
        reflect.defineProperty(PropertyKey.fromString("apply"), new JSNativeFunction("apply", 3, JSReflectObject::apply), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("construct"), new JSNativeFunction("construct", 2, JSReflectObject::construct), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("defineProperty"), new JSNativeFunction("defineProperty", 3, JSReflectObject::defineProperty), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("deleteProperty"), new JSNativeFunction("deleteProperty", 2, JSReflectObject::deleteProperty), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("get"), new JSNativeFunction("get", 2, JSReflectObject::get), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("getOwnPropertyDescriptor"), new JSNativeFunction("getOwnPropertyDescriptor", 2, JSReflectObject::getOwnPropertyDescriptor), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("getPrototypeOf"), new JSNativeFunction("getPrototypeOf", 1, JSReflectObject::getPrototypeOf), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("has"), new JSNativeFunction("has", 2, JSReflectObject::has), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("isExtensible"), new JSNativeFunction("isExtensible", 1, JSReflectObject::isExtensible), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("ownKeys"), new JSNativeFunction("ownKeys", 1, JSReflectObject::ownKeys), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("preventExtensions"), new JSNativeFunction("preventExtensions", 1, JSReflectObject::preventExtensions), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("set"), new JSNativeFunction("set", 3, JSReflectObject::set), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("setPrototypeOf"), new JSNativeFunction("setPrototypeOf", 2, JSReflectObject::setPrototypeOf), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Reflect"), PropertyDescriptor.DataState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString("Reflect"), reflect, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize RegExp constructor and prototype.
     */
    private void initializeRegExpConstructor() {
        // Create RegExp.prototype
        JSObject regexpPrototype = context.createJSObject();
        regexpPrototype.defineProperty(PropertyKey.fromString("test"), new JSNativeFunction("test", 1, RegExpPrototype::test), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.EXEC, new JSNativeFunction("exec", 1, RegExpPrototype::exec), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromString("compile"), new JSNativeFunction("compile", 2, RegExpPrototype::compile), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction("toString", 0, RegExpPrototype::toStringMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPLIT), new JSNativeFunction("[Symbol.split]", 2, RegExpPrototype::symbolSplit), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.MATCH), new JSNativeFunction("[Symbol.match]", 1, RegExpPrototype::symbolMatch), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.MATCH_ALL), new JSNativeFunction("[Symbol.matchAll]", 1, RegExpPrototype::symbolMatchAll), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.REPLACE), new JSNativeFunction("[Symbol.replace]", 2, RegExpPrototype::symbolReplace), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.SEARCH), new JSNativeFunction("[Symbol.search]", 1, RegExpPrototype::symbolSearch), PropertyDescriptor.DataState.ConfigurableWritable);

        // Accessor properties
        regexpPrototype.defineProperty(PropertyKey.fromString("dotAll"), new JSNativeFunction("get dotAll", 0, RegExpPrototype::getDotAllAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("flags"), new JSNativeFunction("get flags", 0, RegExpPrototype::getFlagsAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("global"), new JSNativeFunction("get global", 0, RegExpPrototype::getGlobalAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("hasIndices"), new JSNativeFunction("get hasIndices", 0, RegExpPrototype::getHasIndicesAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("ignoreCase"), new JSNativeFunction("get ignoreCase", 0, RegExpPrototype::getIgnoreCaseAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("multiline"), new JSNativeFunction("get multiline", 0, RegExpPrototype::getMultilineAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("source"), new JSNativeFunction("get source", 0, RegExpPrototype::getSourceAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("sticky"), new JSNativeFunction("get sticky", 0, RegExpPrototype::getStickyAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("unicode"), new JSNativeFunction("get unicode", 0, RegExpPrototype::getUnicodeAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("unicodeSets"), new JSNativeFunction("get unicodeSets", 0, RegExpPrototype::getUnicodeSetsAccessor), PropertyDescriptor.AccessorState.Configurable);

        // Create RegExp constructor as a function
        JSNativeFunction regexpConstructor = new JSNativeFunction(JSRegExp.NAME, 2, RegExpConstructor::call, true);
        regexpConstructor.defineProperty(PropertyKey.fromString("prototype"), regexpPrototype, PropertyDescriptor.DataState.None);
        regexpConstructor.setConstructorType(JSConstructorType.REGEXP);
        regexpPrototype.defineProperty(PropertyKey.fromString("constructor"), regexpConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // AnnexB legacy RegExp static accessor properties.
        // Each getter validates SameValue(C, thisValue) per GetLegacyRegExpStaticProperty.
        for (String legacyGetterName : new String[]{"$&", "$'", "$+", "$`", "lastMatch", "lastParen", "leftContext", "rightContext"}) {
            JSNativeFunction legacyGetter = new JSNativeFunction("get " + legacyGetterName, 0, (ctx, thisArg, args) -> {
                if (thisArg != regexpConstructor) {
                    return ctx.throwTypeError("Generic static accessor property access is not supported");
                }
                return new JSString("");
            }, false);
            regexpConstructor.defineProperty(PropertyKey.fromString(legacyGetterName), legacyGetter, PropertyDescriptor.AccessorState.Configurable);
        }
        for (String legacyGetterSetterName : new String[]{"$_", "input"}) {
            JSNativeFunction legacyGSGetter = new JSNativeFunction("get " + legacyGetterSetterName, 0, (ctx, thisArg, args) -> {
                if (thisArg != regexpConstructor) {
                    return ctx.throwTypeError("Generic static accessor property access is not supported");
                }
                return new JSString("");
            }, false);
            JSNativeFunction legacyGSSetter = new JSNativeFunction("set " + legacyGetterSetterName, 1, (ctx, thisArg, args) -> {
                if (thisArg != regexpConstructor) {
                    return ctx.throwTypeError("Generic static accessor property access is not supported");
                }
                return JSUndefined.INSTANCE;
            }, false);
            regexpConstructor.defineProperty(PropertyKey.fromString(legacyGetterSetterName), legacyGSGetter, legacyGSSetter, PropertyDescriptor.AccessorState.Configurable);
        }
        // $1..$9
        for (int i = 1; i <= 9; i++) {
            final String dollarName = "$" + i;
            JSNativeFunction dollarGetter = new JSNativeFunction("get " + dollarName, 0, (ctx, thisArg, args) -> {
                if (thisArg != regexpConstructor) {
                    return ctx.throwTypeError("Generic static accessor property access is not supported");
                }
                return new JSString("");
            }, false);
            regexpConstructor.defineProperty(PropertyKey.fromString(dollarName), dollarGetter, PropertyDescriptor.AccessorState.Configurable);
        }

        // Symbol.species getter - ES2024 22.2.4.2 get RegExp[@@species]
        regexpConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction("get [Symbol.species]", 0, RegExpConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);
        // ES2024 RegExp.escape static method
        regexpConstructor.defineProperty(PropertyKey.fromString("escape"), new JSNativeFunction("escape", 1, RegExpConstructor::escape), PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSRegExp.NAME), regexpConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Set constructor and prototype methods.
     */
    private void initializeSetConstructor() {
        // Create Set.prototype
        JSObject setPrototype = context.createJSObject();
        setPrototype.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction("add", 1, SetPrototype::add), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("clear"), new JSNativeFunction("clear", 0, SetPrototype::clear), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("delete"), new JSNativeFunction("delete", 1, SetPrototype::delete), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("difference"), new JSNativeFunction("difference", 1, SetPrototype::difference), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("entries"), new JSNativeFunction("entries", 0, SetPrototype::entries), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("forEach"), new JSNativeFunction("forEach", 1, SetPrototype::forEach), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("has"), new JSNativeFunction("has", 1, SetPrototype::has), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("intersection"), new JSNativeFunction("intersection", 1, SetPrototype::intersection), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("isDisjointFrom"), new JSNativeFunction("isDisjointFrom", 1, SetPrototype::isDisjointFrom), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("isSubsetOf"), new JSNativeFunction("isSubsetOf", 1, SetPrototype::isSubsetOf), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("isSupersetOf"), new JSNativeFunction("isSupersetOf", 1, SetPrototype::isSupersetOf), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("symmetricDifference"), new JSNativeFunction("symmetricDifference", 1, SetPrototype::symmetricDifference), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("union"), new JSNativeFunction("union", 1, SetPrototype::union), PropertyDescriptor.DataState.ConfigurableWritable);

        // Create values function - keys and Symbol.iterator will alias to this
        JSNativeFunction valuesFunction = new JSNativeFunction("values", 0, SetPrototype::values);
        setPrototype.defineProperty(PropertyKey.fromString("values"), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        // Set.prototype.keys is the same function object as values (ES spec requirement)
        setPrototype.defineProperty(PropertyKey.fromString("keys"), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        // Set.prototype[Symbol.iterator] is the same as values()
        setPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);

        // Set.prototype.size getter
        setPrototype.defineProperty(PropertyKey.fromString("size"), new JSNativeFunction("get size", 0, SetPrototype::getSize), PropertyDescriptor.AccessorState.Configurable);
        setPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSSet.NAME), PropertyDescriptor.DataState.Configurable);

        // Create Set constructor as a function
        JSNativeFunction setConstructor = new JSNativeFunction(JSSet.NAME, 0, SetConstructor::call, true, true);
        setConstructor.defineProperty(PropertyKey.fromString("prototype"), setPrototype, PropertyDescriptor.DataState.None);
        setConstructor.setConstructorType(JSConstructorType.SET);
        setPrototype.defineProperty(PropertyKey.fromString("constructor"), setConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        setConstructor.defineProperty(PropertyKey.fromString("groupBy"), new JSNativeFunction("groupBy", 2, SetConstructor::groupBy), PropertyDescriptor.DataState.ConfigurableWritable);
        setConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction("get [Symbol.species]", 0, SetConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString(JSSet.NAME), setConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize ShadowRealm constructor and prototype.
     * ShadowRealm is not provided by QuickJS; implemented per proposal/test262 behavior.
     */
    private void initializeShadowRealmConstructor() {
        JSObject shadowRealmPrototype = context.createJSObject();
        shadowRealmPrototype.defineProperty(
                PropertyKey.fromString("evaluate"),
                new JSNativeFunction("evaluate", 1, ShadowRealmPrototype::evaluate),
                PropertyDescriptor.DataState.ConfigurableWritable);
        shadowRealmPrototype.defineProperty(
                PropertyKey.fromString("importValue"),
                new JSNativeFunction("importValue", 2, ShadowRealmPrototype::importValue),
                PropertyDescriptor.DataState.ConfigurableWritable);
        shadowRealmPrototype.defineProperty(
                PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                new JSString(JSShadowRealm.NAME),
                PropertyDescriptor.DataState.Configurable);

        JSNativeFunction shadowRealmConstructor = new JSNativeFunction(
                JSShadowRealm.NAME,
                0,
                ShadowRealmConstructor::call,
                true,
                true);
        shadowRealmConstructor.defineProperty(PropertyKey.fromString("prototype"), shadowRealmPrototype, PropertyDescriptor.DataState.None);
        shadowRealmPrototype.defineProperty(PropertyKey.fromString("constructor"), shadowRealmConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSShadowRealm.NAME), shadowRealmConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize SharedArrayBuffer constructor and prototype.
     */
    private void initializeSharedArrayBufferConstructor() {
        // Create SharedArrayBuffer.prototype
        JSObject sharedArrayBufferPrototype = context.createJSObject();
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("grow"), new JSNativeFunction("grow", 1, SharedArrayBufferPrototype::grow), PropertyDescriptor.DataState.ConfigurableWritable);
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("slice"), new JSNativeFunction("slice", 2, SharedArrayBufferPrototype::slice), PropertyDescriptor.DataState.ConfigurableWritable);

        // Define getter properties
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("byteLength"), new JSNativeFunction("get byteLength", 0, SharedArrayBufferPrototype::getByteLength), PropertyDescriptor.AccessorState.Configurable);
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("growable"), new JSNativeFunction("get growable", 0, SharedArrayBufferPrototype::getGrowable), PropertyDescriptor.AccessorState.Configurable);
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("maxByteLength"), new JSNativeFunction("get maxByteLength", 0, SharedArrayBufferPrototype::getMaxByteLength), PropertyDescriptor.AccessorState.Configurable);
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSSharedArrayBuffer.NAME), PropertyDescriptor.DataState.Configurable);

        // Create SharedArrayBuffer constructor as a function
        JSNativeFunction sharedArrayBufferConstructor = new JSNativeFunction(
                JSSharedArrayBuffer.NAME,
                1,
                SharedArrayBufferConstructor::call,
                true,  // isConstructor
                true   // requiresNew - SharedArrayBuffer() must be called with new
        );
        sharedArrayBufferConstructor.defineProperty(PropertyKey.fromString("prototype"), sharedArrayBufferPrototype, PropertyDescriptor.DataState.None);
        sharedArrayBufferConstructor.setConstructorType(JSConstructorType.SHARED_ARRAY_BUFFER);
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("constructor"), sharedArrayBufferConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        sharedArrayBufferConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction("get [Symbol.species]", 0, SharedArrayBufferConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString(JSSharedArrayBuffer.NAME), sharedArrayBufferConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize String constructor and prototype.
     */
    private void initializeStringConstructor() {
        // Create String.prototype - per ES spec, it is itself a String object whose value is ""
        JSObject stringPrototype = new JSStringObject();
        context.transferPrototype(stringPrototype, JSObject.NAME);
        stringPrototype.defineProperty(PropertyKey.fromString("at"), new JSNativeFunction("at", 1, StringPrototype::at), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("charAt"), new JSNativeFunction("charAt", 1, StringPrototype::charAt), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("charCodeAt"), new JSNativeFunction("charCodeAt", 1, StringPrototype::charCodeAt), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("codePointAt"), new JSNativeFunction("codePointAt", 1, StringPrototype::codePointAt), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("concat"), new JSNativeFunction("concat", 1, StringPrototype::concat), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("endsWith"), new JSNativeFunction("endsWith", 1, StringPrototype::endsWith), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("includes"), new JSNativeFunction("includes", 1, StringPrototype::includes), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("indexOf"), new JSNativeFunction("indexOf", 1, StringPrototype::indexOf), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("lastIndexOf"), new JSNativeFunction("lastIndexOf", 1, StringPrototype::lastIndexOf), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("localeCompare"), new JSNativeFunction("localeCompare", 1, StringPrototype::localeCompare), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("match"), new JSNativeFunction("match", 1, StringPrototype::match), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("matchAll"), new JSNativeFunction("matchAll", 1, StringPrototype::matchAll), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("padEnd"), new JSNativeFunction("padEnd", 1, StringPrototype::padEnd), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("padStart"), new JSNativeFunction("padStart", 1, StringPrototype::padStart), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("repeat"), new JSNativeFunction("repeat", 1, StringPrototype::repeat), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("replace"), new JSNativeFunction("replace", 2, StringPrototype::replace), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("replaceAll"), new JSNativeFunction("replaceAll", 2, StringPrototype::replaceAll), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("search"), new JSNativeFunction("search", 1, StringPrototype::search), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("slice"), new JSNativeFunction("slice", 2, StringPrototype::slice), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("split"), new JSNativeFunction("split", 2, StringPrototype::split), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("startsWith"), new JSNativeFunction("startsWith", 1, StringPrototype::startsWith), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("substr"), new JSNativeFunction("substr", 2, StringPrototype::substr), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("substring"), new JSNativeFunction("substring", 2, StringPrototype::substring), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("toLowerCase"), new JSNativeFunction("toLowerCase", 0, StringPrototype::toLowerCase), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction("toString", 0, StringPrototype::toString_), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("toUpperCase"), new JSNativeFunction("toUpperCase", 0, StringPrototype::toUpperCase), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("trim"), new JSNativeFunction("trim", 0, StringPrototype::trim), PropertyDescriptor.DataState.ConfigurableWritable);
        JSNativeFunction trimEnd = new JSNativeFunction("trimEnd", 0, StringPrototype::trimEnd);
        JSNativeFunction trimStart = new JSNativeFunction("trimStart", 0, StringPrototype::trimStart);
        stringPrototype.defineProperty(PropertyKey.fromString("trimEnd"), trimEnd, PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("trimRight"), trimEnd, PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("trimStart"), trimStart, PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("trimLeft"), trimStart, PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction("valueOf", 0, StringPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);

        // HTML wrapper methods (deprecated but still part of spec)
        stringPrototype.defineProperty(PropertyKey.fromString("anchor"), new JSNativeFunction("anchor", 1, StringPrototype::anchor), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("big"), new JSNativeFunction("big", 0, StringPrototype::big), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("blink"), new JSNativeFunction("blink", 0, StringPrototype::blink), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("bold"), new JSNativeFunction("bold", 0, StringPrototype::bold), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("fixed"), new JSNativeFunction("fixed", 0, StringPrototype::fixed), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("fontcolor"), new JSNativeFunction("fontcolor", 1, StringPrototype::fontcolor), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("fontsize"), new JSNativeFunction("fontsize", 1, StringPrototype::fontsize), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("italics"), new JSNativeFunction("italics", 0, StringPrototype::italics), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("link"), new JSNativeFunction("link", 1, StringPrototype::link), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("small"), new JSNativeFunction("small", 0, StringPrototype::small), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("strike"), new JSNativeFunction("strike", 0, StringPrototype::strike), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("sub"), new JSNativeFunction("sub", 0, StringPrototype::sub), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("sup"), new JSNativeFunction("sup", 0, StringPrototype::sup), PropertyDescriptor.DataState.ConfigurableWritable);

        // Unicode methods
        stringPrototype.defineProperty(PropertyKey.fromString("isWellFormed"), new JSNativeFunction("isWellFormed", 0, StringPrototype::isWellFormed), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("normalize"), new JSNativeFunction("normalize", 0, StringPrototype::normalize), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("toLocaleLowerCase"), new JSNativeFunction("toLocaleLowerCase", 0, StringPrototype::toLocaleLowerCase), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("toLocaleUpperCase"), new JSNativeFunction("toLocaleUpperCase", 0, StringPrototype::toLocaleUpperCase), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("toWellFormed"), new JSNativeFunction("toWellFormed", 0, StringPrototype::toWellFormed), PropertyDescriptor.DataState.ConfigurableWritable);

        // String.prototype[Symbol.iterator]
        stringPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR),
                new JSNativeFunction("[Symbol.iterator]", 0, IteratorPrototype::stringIterator), PropertyDescriptor.DataState.ConfigurableWritable);

        // String.prototype.length is a data property with value 0 (not writable, not enumerable, not configurable)
        stringPrototype.defineProperty(PropertyKey.fromString("length"), JSNumber.of(0), PropertyDescriptor.DataState.None);

        // Create String constructor
        JSNativeFunction stringConstructor = new JSNativeFunction(JSString.NAME, 1, StringConstructor::call, true);
        stringConstructor.defineProperty(PropertyKey.fromString("prototype"), stringPrototype, PropertyDescriptor.DataState.None);
        stringConstructor.setConstructorType(JSConstructorType.STRING_OBJECT); // Mark as String constructor
        stringPrototype.defineProperty(PropertyKey.fromString("constructor"), stringConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Add static methods
        stringConstructor.defineProperty(PropertyKey.fromString("fromCharCode"), new JSNativeFunction("fromCharCode", 1, StringConstructor::fromCharCode), PropertyDescriptor.DataState.ConfigurableWritable);
        stringConstructor.defineProperty(PropertyKey.fromString("fromCodePoint"), new JSNativeFunction("fromCodePoint", 1, StringConstructor::fromCodePoint), PropertyDescriptor.DataState.ConfigurableWritable);
        stringConstructor.defineProperty(PropertyKey.fromString("raw"), new JSNativeFunction("raw", 1, StringConstructor::raw), PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSString.NAME), stringConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Symbol constructor and static methods.
     */
    private void initializeSymbolConstructor() {
        // Create Symbol.prototype that inherits from Object.prototype
        JSObject symbolPrototype = context.createJSObject();
        context.transferPrototype(symbolPrototype, JSObject.NAME);

        JSNativeFunction symbolToString = new JSNativeFunction("toString", 0, SymbolPrototype::toString);
        symbolToString.initializePrototypeChain(context);
        symbolPrototype.defineProperty(PropertyKey.fromString("toString"), symbolToString, PropertyDescriptor.DataState.ConfigurableWritable);

        JSNativeFunction symbolValueOf = new JSNativeFunction("valueOf", 0, SymbolPrototype::valueOf);
        symbolValueOf.initializePrototypeChain(context);
        symbolPrototype.defineProperty(PropertyKey.fromString("valueOf"), symbolValueOf, PropertyDescriptor.DataState.ConfigurableWritable);

        // Symbol.prototype.description is a getter
        symbolPrototype.defineProperty(PropertyKey.fromString("description"), new JSNativeFunction("get description", 0, SymbolPrototype::getDescription), PropertyDescriptor.AccessorState.Configurable);

        JSNativeFunction symbolToPrimitive = new JSNativeFunction("[Symbol.toPrimitive]", 1, SymbolPrototype::toPrimitive);
        symbolToPrimitive.initializePrototypeChain(context);
        symbolPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_PRIMITIVE), symbolToPrimitive, PropertyDescriptor.DataState.Configurable);

        symbolPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSSymbol.NAME), PropertyDescriptor.DataState.Configurable);

        // Create Symbol constructor
        // Note: Symbol cannot be called with 'new' in JavaScript (throws TypeError)
        // Symbol objects are created using Object(symbolValue) for use with Proxy
        JSNativeFunction symbolConstructor = new JSNativeFunction(JSSymbol.NAME, 0, SymbolConstructor::call, true);
        symbolConstructor.defineProperty(PropertyKey.fromString("prototype"), symbolPrototype, PropertyDescriptor.DataState.None);
        symbolConstructor.setConstructorType(JSConstructorType.SYMBOL_OBJECT); // Mark as Symbol constructor
        symbolPrototype.defineProperty(PropertyKey.fromString("constructor"), symbolConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Symbol static methods
        symbolConstructor.defineProperty(PropertyKey.fromString("for"), new JSNativeFunction("for", 1, SymbolConstructor::symbolFor), PropertyDescriptor.DataState.ConfigurableWritable);
        symbolConstructor.defineProperty(PropertyKey.fromString("keyFor"), new JSNativeFunction("keyFor", 1, SymbolConstructor::keyFor), PropertyDescriptor.DataState.ConfigurableWritable);

        // Well-known symbols (ES2015+)
        symbolConstructor.defineProperty(PropertyKey.fromString("iterator"), JSSymbol.ITERATOR, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("asyncIterator"), JSSymbol.ASYNC_ITERATOR, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("toStringTag"), JSSymbol.TO_STRING_TAG, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("hasInstance"), JSSymbol.HAS_INSTANCE, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("isConcatSpreadable"), JSSymbol.IS_CONCAT_SPREADABLE, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("toPrimitive"), JSSymbol.TO_PRIMITIVE, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("match"), JSSymbol.MATCH, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("matchAll"), JSSymbol.MATCH_ALL, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("replace"), JSSymbol.REPLACE, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("search"), JSSymbol.SEARCH, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("split"), JSSymbol.SPLIT, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("species"), JSSymbol.SPECIES, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("unscopables"), JSSymbol.UNSCOPABLES, PropertyDescriptor.DataState.None);
        // ES2024 additions kept intentionally even though not in upstream QuickJS.
        symbolConstructor.defineProperty(PropertyKey.fromString("dispose"), JSSymbol.DISPOSE, PropertyDescriptor.DataState.None);
        symbolConstructor.defineProperty(PropertyKey.fromString("asyncDispose"), JSSymbol.ASYNC_DISPOSE, PropertyDescriptor.DataState.None);

        globalObject.defineProperty(PropertyKey.fromString(JSSymbol.NAME), symbolConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize all TypedArray constructors.
     * Per ES spec, creates the %TypedArray% intrinsic (shared parent) and
     * individual typed array constructors that inherit from it.
     */
    private void initializeTypedArrayConstructors() {
        record TypedArrayDef(String name, JSNativeFunction.NativeCallback callback, JSConstructorType type,
                             int bytesPerElement) {
        }
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

        JSNativeFunction valuesFunction = new JSNativeFunction("values", 0, TypedArrayPrototype::values);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("at"), new JSNativeFunction("at", 1, TypedArrayPrototype::at), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("copyWithin"), new JSNativeFunction("copyWithin", 2, TypedArrayPrototype::copyWithin), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("entries"), new JSNativeFunction("entries", 0, TypedArrayPrototype::entries), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("every"), new JSNativeFunction("every", 1, TypedArrayPrototype::every), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("fill"), new JSNativeFunction("fill", 1, TypedArrayPrototype::fill), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("filter"), new JSNativeFunction("filter", 1, TypedArrayPrototype::filter), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("find"), new JSNativeFunction("find", 1, TypedArrayPrototype::find), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("findIndex"), new JSNativeFunction("findIndex", 1, TypedArrayPrototype::findIndex), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("findLast"), new JSNativeFunction("findLast", 1, TypedArrayPrototype::findLast), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("findLastIndex"), new JSNativeFunction("findLastIndex", 1, TypedArrayPrototype::findLastIndex), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("forEach"), new JSNativeFunction("forEach", 1, TypedArrayPrototype::forEach), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("includes"), new JSNativeFunction("includes", 1, TypedArrayPrototype::includes), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("indexOf"), new JSNativeFunction("indexOf", 1, TypedArrayPrototype::indexOf), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("join"), new JSNativeFunction("join", 1, TypedArrayPrototype::join), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("keys"), new JSNativeFunction("keys", 0, TypedArrayPrototype::keys), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("lastIndexOf"), new JSNativeFunction("lastIndexOf", 1, TypedArrayPrototype::lastIndexOf), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("map"), new JSNativeFunction("map", 1, TypedArrayPrototype::map), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("reduce"), new JSNativeFunction("reduce", 1, TypedArrayPrototype::reduce), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("reduceRight"), new JSNativeFunction("reduceRight", 1, TypedArrayPrototype::reduceRight), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("reverse"), new JSNativeFunction("reverse", 0, TypedArrayPrototype::reverse), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("set"), new JSNativeFunction("set", 1, TypedArrayPrototype::set), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("slice"), new JSNativeFunction("slice", 2, TypedArrayPrototype::slice), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("some"), new JSNativeFunction("some", 1, TypedArrayPrototype::some), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("sort"), new JSNativeFunction("sort", 1, TypedArrayPrototype::sort), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("subarray"), new JSNativeFunction("subarray", 2, TypedArrayPrototype::subarray), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction("toLocaleString", 0, TypedArrayPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("toReversed"), new JSNativeFunction("toReversed", 0, TypedArrayPrototype::toReversed), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("toSorted"), new JSNativeFunction("toSorted", 1, TypedArrayPrototype::toSorted), PropertyDescriptor.DataState.ConfigurableWritable);
        if (arrayToString instanceof JSFunction) {
            typedArrayPrototype.defineProperty(PropertyKey.fromString("toString"), arrayToString, PropertyDescriptor.DataState.ConfigurableWritable);
        } else {
            typedArrayPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction("toString", 0, TypedArrayPrototype::toString), PropertyDescriptor.DataState.ConfigurableWritable);
        }
        typedArrayPrototype.defineProperty(PropertyKey.fromString("with"), new JSNativeFunction("with", 2, TypedArrayPrototype::withMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("values"), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);

        typedArrayPrototype.defineProperty(PropertyKey.fromString("buffer"), new JSNativeFunction("get buffer", 0, TypedArrayPrototype::getBuffer), PropertyDescriptor.AccessorState.Configurable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("byteLength"), new JSNativeFunction("get byteLength", 0, TypedArrayPrototype::getByteLength), PropertyDescriptor.AccessorState.Configurable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("byteOffset"), new JSNativeFunction("get byteOffset", 0, TypedArrayPrototype::getByteOffset), PropertyDescriptor.AccessorState.Configurable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("length"), new JSNativeFunction("get length", 0, TypedArrayPrototype::getLength), PropertyDescriptor.AccessorState.Configurable);
        typedArrayPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSNativeFunction("get [Symbol.toStringTag]", 0, TypedArrayPrototype::getToStringTag), PropertyDescriptor.AccessorState.Configurable);

        // Create %TypedArray% constructor — abstract, throws if called directly
        // Per spec: %TypedArray% is not exposed as a global but is the [[Prototype]] of all typed array constructors
        JSNativeFunction typedArrayConstructor = new JSNativeFunction("TypedArray", 0,
                (ctx, thisArg, args) -> ctx.throwTypeError("Abstract class TypedArray not directly constructable"),
                true, true);
        context.transferPrototype(typedArrayConstructor, JSFunction.NAME);
        typedArrayConstructor.defineProperty(PropertyKey.fromString("prototype"), typedArrayPrototype, PropertyDescriptor.DataState.None);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("constructor"), typedArrayConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Add static methods to %TypedArray%
        typedArrayConstructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction("from", 1, TypedArrayConstructor::from), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayConstructor.defineProperty(PropertyKey.fromString("of"), new JSNativeFunction("of", 0, TypedArrayConstructor::of), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction("get [Symbol.species]", 0, TypedArrayConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

        for (TypedArrayDef def : List.of(
                new TypedArrayDef(JSInt8Array.NAME, Int8ArrayConstructor::call, JSConstructorType.TYPED_ARRAY_INT8, JSInt8Array.BYTES_PER_ELEMENT),
                new TypedArrayDef(JSUint8Array.NAME, Uint8ArrayConstructor::call, JSConstructorType.TYPED_ARRAY_UINT8, JSUint8Array.BYTES_PER_ELEMENT),
                new TypedArrayDef(JSUint8ClampedArray.NAME, Uint8ClampedArrayConstructor::call, JSConstructorType.TYPED_ARRAY_UINT8_CLAMPED, JSUint8ClampedArray.BYTES_PER_ELEMENT),
                new TypedArrayDef(JSInt16Array.NAME, Int16ArrayConstructor::call, JSConstructorType.TYPED_ARRAY_INT16, JSInt16Array.BYTES_PER_ELEMENT),
                new TypedArrayDef(JSUint16Array.NAME, Uint16ArrayConstructor::call, JSConstructorType.TYPED_ARRAY_UINT16, JSUint16Array.BYTES_PER_ELEMENT),
                new TypedArrayDef(JSInt32Array.NAME, Int32ArrayConstructor::call, JSConstructorType.TYPED_ARRAY_INT32, JSInt32Array.BYTES_PER_ELEMENT),
                new TypedArrayDef(JSUint32Array.NAME, Uint32ArrayConstructor::call, JSConstructorType.TYPED_ARRAY_UINT32, JSUint32Array.BYTES_PER_ELEMENT),
                new TypedArrayDef(JSFloat16Array.NAME, Float16ArrayConstructor::call, JSConstructorType.TYPED_ARRAY_FLOAT16, JSFloat16Array.BYTES_PER_ELEMENT),
                new TypedArrayDef(JSFloat32Array.NAME, Float32ArrayConstructor::call, JSConstructorType.TYPED_ARRAY_FLOAT32, JSFloat32Array.BYTES_PER_ELEMENT),
                new TypedArrayDef(JSFloat64Array.NAME, Float64ArrayConstructor::call, JSConstructorType.TYPED_ARRAY_FLOAT64, JSFloat64Array.BYTES_PER_ELEMENT),
                new TypedArrayDef(JSBigInt64Array.NAME, BigInt64ArrayConstructor::call, JSConstructorType.TYPED_ARRAY_BIGINT64, JSBigInt64Array.BYTES_PER_ELEMENT),
                new TypedArrayDef(JSBigUint64Array.NAME, BigUint64ArrayConstructor::call, JSConstructorType.TYPED_ARRAY_BIGUINT64, JSBigUint64Array.BYTES_PER_ELEMENT)
        )) {
            // Each concrete typed array prototype inherits from %TypedArray%.prototype
            JSObject prototype = context.createJSObject();
            prototype.setPrototype(typedArrayPrototype);
            prototype.defineProperty(PropertyKey.fromString("BYTES_PER_ELEMENT"), JSNumber.of(def.bytesPerElement), PropertyDescriptor.DataState.None);

            // Each concrete constructor inherits from %TypedArray%
            JSNativeFunction constructor = new JSNativeFunction(def.name, 3, def.callback, true, true);
            constructor.setPrototype(typedArrayConstructor);
            constructor.defineProperty(PropertyKey.fromString("prototype"), prototype, PropertyDescriptor.DataState.None);
            constructor.setConstructorType(def.type);
            constructor.defineProperty(PropertyKey.fromString("BYTES_PER_ELEMENT"), JSNumber.of(def.bytesPerElement), PropertyDescriptor.DataState.None);

            prototype.defineProperty(PropertyKey.fromString("constructor"), constructor, PropertyDescriptor.DataState.ConfigurableWritable);

            // Add Uint8Array-specific base64/hex methods
            if (JSUint8Array.NAME.equals(def.name)) {
                // Static methods on constructor
                constructor.defineProperty(PropertyKey.fromString("fromBase64"), new JSNativeFunction("fromBase64", 1, Uint8ArrayBase64Hex::fromBase64), PropertyDescriptor.DataState.ConfigurableWritable);
                constructor.defineProperty(PropertyKey.fromString("fromHex"), new JSNativeFunction("fromHex", 1, Uint8ArrayBase64Hex::fromHex), PropertyDescriptor.DataState.ConfigurableWritable);

                // Prototype methods
                prototype.defineProperty(PropertyKey.fromString("toBase64"), new JSNativeFunction("toBase64", 0, Uint8ArrayBase64Hex::toBase64), PropertyDescriptor.DataState.ConfigurableWritable);
                prototype.defineProperty(PropertyKey.fromString("toHex"), new JSNativeFunction("toHex", 0, Uint8ArrayBase64Hex::toHex), PropertyDescriptor.DataState.ConfigurableWritable);
                prototype.defineProperty(PropertyKey.fromString("setFromBase64"), new JSNativeFunction("setFromBase64", 1, Uint8ArrayBase64Hex::setFromBase64), PropertyDescriptor.DataState.ConfigurableWritable);
                prototype.defineProperty(PropertyKey.fromString("setFromHex"), new JSNativeFunction("setFromHex", 1, Uint8ArrayBase64Hex::setFromHex), PropertyDescriptor.DataState.ConfigurableWritable);
            }

            globalObject.defineProperty(PropertyKey.fromString(def.name), constructor, PropertyDescriptor.DataState.ConfigurableWritable);
        }
    }

    /**
     * Initialize WeakMap constructor and prototype methods.
     */
    private void initializeWeakMapConstructor() {
        JSObject weakMapPrototype = context.createJSObject();
        weakMapPrototype.defineProperty(PropertyKey.fromString("set"), new JSNativeFunction("set", 2, WeakMapPrototype::set), PropertyDescriptor.DataState.ConfigurableWritable);
        weakMapPrototype.defineProperty(PropertyKey.fromString("get"), new JSNativeFunction("get", 1, WeakMapPrototype::get), PropertyDescriptor.DataState.ConfigurableWritable);
        weakMapPrototype.defineProperty(PropertyKey.fromString("getOrInsert"), new JSNativeFunction("getOrInsert", 2, WeakMapPrototype::getOrInsert), PropertyDescriptor.DataState.ConfigurableWritable);
        weakMapPrototype.defineProperty(PropertyKey.fromString("getOrInsertComputed"), new JSNativeFunction("getOrInsertComputed", 2, WeakMapPrototype::getOrInsertComputed), PropertyDescriptor.DataState.ConfigurableWritable);
        weakMapPrototype.defineProperty(PropertyKey.fromString("has"), new JSNativeFunction("has", 1, WeakMapPrototype::has), PropertyDescriptor.DataState.ConfigurableWritable);
        weakMapPrototype.defineProperty(PropertyKey.fromString("delete"), new JSNativeFunction("delete", 1, WeakMapPrototype::delete), PropertyDescriptor.DataState.ConfigurableWritable);
        weakMapPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSWeakMap.NAME), PropertyDescriptor.DataState.Configurable);

        JSNativeFunction weakMapConstructor = new JSNativeFunction(
                JSWeakMap.NAME,
                0,
                WeakMapConstructor::call,
                true,
                true
        );
        weakMapConstructor.defineProperty(PropertyKey.fromString("prototype"), weakMapPrototype, PropertyDescriptor.DataState.None);
        weakMapConstructor.setConstructorType(JSConstructorType.WEAK_MAP);
        weakMapPrototype.defineProperty(PropertyKey.fromString("constructor"), weakMapConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSWeakMap.NAME), weakMapConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize WeakRef constructor.
     */
    private void initializeWeakRefConstructor() {
        JSObject weakRefPrototype = context.createJSObject();
        weakRefPrototype.defineProperty(PropertyKey.fromString("deref"), new JSNativeFunction("deref", 0, WeakRefPrototype::deref), PropertyDescriptor.DataState.ConfigurableWritable);
        weakRefPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSWeakRef.NAME), PropertyDescriptor.DataState.Configurable);

        JSNativeFunction weakRefConstructor = new JSNativeFunction(
                JSWeakRef.NAME,
                1,
                WeakRefConstructor::call,
                true,
                true
        );
        weakRefConstructor.defineProperty(PropertyKey.fromString("prototype"), weakRefPrototype, PropertyDescriptor.DataState.None);
        weakRefConstructor.setConstructorType(JSConstructorType.WEAK_REF);
        weakRefPrototype.defineProperty(PropertyKey.fromString("constructor"), weakRefConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSWeakRef.NAME), weakRefConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize WeakSet constructor and prototype methods.
     */
    private void initializeWeakSetConstructor() {
        JSObject weakSetPrototype = context.createJSObject();
        weakSetPrototype.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction("add", 1, WeakSetPrototype::add), PropertyDescriptor.DataState.ConfigurableWritable);
        weakSetPrototype.defineProperty(PropertyKey.fromString("has"), new JSNativeFunction("has", 1, WeakSetPrototype::has), PropertyDescriptor.DataState.ConfigurableWritable);
        weakSetPrototype.defineProperty(PropertyKey.fromString("delete"), new JSNativeFunction("delete", 1, WeakSetPrototype::delete), PropertyDescriptor.DataState.ConfigurableWritable);
        weakSetPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSWeakSet.NAME), PropertyDescriptor.DataState.Configurable);

        JSNativeFunction weakSetConstructor = new JSNativeFunction(
                JSWeakSet.NAME,
                0,
                WeakSetConstructor::call,
                true,
                true
        );
        weakSetConstructor.defineProperty(PropertyKey.fromString("prototype"), weakSetPrototype, PropertyDescriptor.DataState.None);
        weakSetConstructor.setConstructorType(JSConstructorType.WEAK_SET);
        weakSetPrototype.defineProperty(PropertyKey.fromString("constructor"), weakSetConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSWeakSet.NAME), weakSetConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    public static class GlobalFunction {

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

        /**
         * Collect only body-level var declaration names from a function (excludes parameter names).
         * Used to distinguish parameter bindings from body var bindings in functions with
         * parameter expressions, for eval var conflict detection.
         */
        private static Set<String> collectBodyVarNames(JSBytecodeFunction callerBytecodeFunction) {
            String source = callerBytecodeFunction.getSourceCode();
            if (source == null || source.isBlank()) {
                return null;
            }
            String wrappedSource = "(" + source + ")";
            try {
                Program parsedProgram = new Compiler(wrappedSource, "<eval-caller>").parse(false);
                if (parsedProgram.body().isEmpty()
                        || !(parsedProgram.body().get(0) instanceof ExpressionStatement expressionStatement)) {
                    return null;
                }
                Expression expression = expressionStatement.expression();
                Set<String> bodyVarNames = new HashSet<>();
                if (expression instanceof FunctionExpression functionExpression) {
                    collectVarEnvironmentNamesFromStatements(functionExpression.body().body(), bodyVarNames);
                    return bodyVarNames;
                }
                if (expression instanceof ArrowFunctionExpression arrowFunctionExpression) {
                    if (arrowFunctionExpression.body() instanceof BlockStatement blockStatement) {
                        collectVarEnvironmentNamesFromStatements(blockStatement.body(), bodyVarNames);
                    }
                    return bodyVarNames;
                }
                return null;
            } catch (Exception ignored) {
                return null;
            }
        }

        private static Set<String> collectFunctionDeclarationNames(List<Statement> body) {
            Set<String> functionDeclarationNames = new LinkedHashSet<>();
            if (body == null) {
                return functionDeclarationNames;
            }
            for (int statementIndex = body.size() - 1; statementIndex >= 0; statementIndex--) {
                Statement statement = body.get(statementIndex);
                if (statement instanceof FunctionDeclaration functionDeclaration && functionDeclaration.id() != null) {
                    functionDeclarationNames.add(functionDeclaration.id().name());
                }
            }
            return functionDeclarationNames;
        }

        private static Set<String> collectFunctionVarEnvironmentNames(JSBytecodeFunction callerBytecodeFunction) {
            String source = callerBytecodeFunction.getSourceCode();
            if (source == null || source.isBlank()) {
                return null;
            }
            String wrappedSource = "(" + source + ")";
            try {
                Program parsedProgram = new Compiler(wrappedSource, "<eval-caller>").parse(false);
                if (parsedProgram.body().isEmpty()
                        || !(parsedProgram.body().get(0) instanceof ExpressionStatement expressionStatement)) {
                    return null;
                }
                Expression expression = expressionStatement.expression();
                Set<String> functionVarEnvironmentNames = new HashSet<>();
                if (expression instanceof FunctionExpression functionExpression) {
                    for (Pattern parameter : functionExpression.params()) {
                        collectPatternNames(parameter, functionVarEnvironmentNames);
                    }
                    if (functionExpression.restParameter() != null) {
                        collectPatternNames(functionExpression.restParameter().argument(), functionVarEnvironmentNames);
                    }
                    collectVarEnvironmentNamesFromStatements(functionExpression.body().body(), functionVarEnvironmentNames);
                    return functionVarEnvironmentNames;
                }
                if (expression instanceof ArrowFunctionExpression arrowFunctionExpression) {
                    for (Pattern parameter : arrowFunctionExpression.params()) {
                        collectPatternNames(parameter, functionVarEnvironmentNames);
                    }
                    if (arrowFunctionExpression.restParameter() != null) {
                        collectPatternNames(arrowFunctionExpression.restParameter().argument(), functionVarEnvironmentNames);
                    }
                    if (arrowFunctionExpression.body() instanceof BlockStatement blockStatement) {
                        collectVarEnvironmentNamesFromStatements(blockStatement.body(), functionVarEnvironmentNames);
                    }
                    return functionVarEnvironmentNames;
                }
                return null;
            } catch (Exception ignored) {
                return null;
            }
        }

        private static void collectPatternNames(Pattern pattern, Set<String> names) {
            if (pattern instanceof Identifier identifier) {
                names.add(identifier.name());
            } else if (pattern instanceof ArrayPattern arrayPattern) {
                for (Pattern element : arrayPattern.elements()) {
                    if (element != null) {
                        collectPatternNames(element, names);
                    }
                }
            } else if (pattern instanceof ObjectPattern objectPattern) {
                for (ObjectPattern.Property property : objectPattern.properties()) {
                    collectPatternNames(property.value(), names);
                }
                if (objectPattern.restElement() != null) {
                    collectPatternNames(objectPattern.restElement().argument(), names);
                }
            } else if (pattern instanceof RestElement restElement) {
                collectPatternNames(restElement.argument(), names);
            } else if (pattern instanceof AssignmentPattern assignmentPattern) {
                collectPatternNames(assignmentPattern.left(), names);
            }
        }

        private static void collectVarEnvironmentNamesFromStatement(Statement statement, Set<String> names) {
            if (statement instanceof FunctionDeclaration functionDeclaration && functionDeclaration.id() != null) {
                names.add(functionDeclaration.id().name());
                return;
            }
            if (statement instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.kind() == VariableKind.VAR) {
                for (VariableDeclaration.VariableDeclarator declaration : variableDeclaration.declarations()) {
                    collectPatternNames(declaration.id(), names);
                }
                return;
            }
            if (statement instanceof BlockStatement blockStatement) {
                collectVarEnvironmentNamesFromStatements(blockStatement.body(), names);
                return;
            }
            if (statement instanceof IfStatement ifStatement) {
                collectVarEnvironmentNamesFromStatement(ifStatement.consequent(), names);
                if (ifStatement.alternate() != null) {
                    collectVarEnvironmentNamesFromStatement(ifStatement.alternate(), names);
                }
                return;
            }
            if (statement instanceof ForStatement forStatement) {
                if (forStatement.init() instanceof VariableDeclaration variableDeclaration
                        && variableDeclaration.kind() == VariableKind.VAR) {
                    for (VariableDeclaration.VariableDeclarator declaration : variableDeclaration.declarations()) {
                        collectPatternNames(declaration.id(), names);
                    }
                }
                collectVarEnvironmentNamesFromStatement(forStatement.body(), names);
                return;
            }
            if (statement instanceof ForInStatement forInStatement) {
                if (forInStatement.left() instanceof VariableDeclaration variableDeclaration
                        && variableDeclaration.kind() == VariableKind.VAR) {
                    for (VariableDeclaration.VariableDeclarator declaration : variableDeclaration.declarations()) {
                        collectPatternNames(declaration.id(), names);
                    }
                }
                collectVarEnvironmentNamesFromStatement(forInStatement.body(), names);
                return;
            }
            if (statement instanceof ForOfStatement forOfStatement) {
                if (forOfStatement.left() instanceof VariableDeclaration variableDeclaration
                        && variableDeclaration.kind() == VariableKind.VAR) {
                    for (VariableDeclaration.VariableDeclarator declaration : variableDeclaration.declarations()) {
                        collectPatternNames(declaration.id(), names);
                    }
                }
                collectVarEnvironmentNamesFromStatement(forOfStatement.body(), names);
                return;
            }
            if (statement instanceof WhileStatement whileStatement) {
                collectVarEnvironmentNamesFromStatement(whileStatement.body(), names);
                return;
            }
            if (statement instanceof DoWhileStatement doWhileStatement) {
                collectVarEnvironmentNamesFromStatement(doWhileStatement.body(), names);
                return;
            }
            if (statement instanceof SwitchStatement switchStatement) {
                for (SwitchStatement.SwitchCase switchCase : switchStatement.cases()) {
                    collectVarEnvironmentNamesFromStatements(switchCase.consequent(), names);
                }
                return;
            }
            if (statement instanceof TryStatement tryStatement) {
                collectVarEnvironmentNamesFromStatements(tryStatement.block().body(), names);
                if (tryStatement.handler() != null) {
                    if (tryStatement.handler().param() != null) {
                        collectPatternNames(tryStatement.handler().param(), names);
                    }
                    collectVarEnvironmentNamesFromStatements(tryStatement.handler().body().body(), names);
                }
                if (tryStatement.finalizer() != null) {
                    collectVarEnvironmentNamesFromStatements(tryStatement.finalizer().body(), names);
                }
                return;
            }
            if (statement instanceof LabeledStatement labeledStatement) {
                collectVarEnvironmentNamesFromStatement(labeledStatement.body(), names);
            }
        }

        private static void collectVarEnvironmentNamesFromStatements(List<Statement> statements, Set<String> names) {
            for (Statement statement : statements) {
                collectVarEnvironmentNamesFromStatement(statement, names);
            }
        }

        /**
         * decodeURI(encodedURI)
         * Decode a URI that was encoded by encodeURI.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-decodeuri-encodeduri">ECMAScript decodeURI</a>
         */
        public static JSValue decodeURI(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue encodedValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            String encodedString = JSTypeConversions.toString(context, encodedValue).value();
            return decodeURIImpl(context, encodedString, false);
        }

        /**
         * decodeURIComponent(encodedURIComponent)
         * Decode a URI component that was encoded by encodeURIComponent.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-decodeuricomponent-encodeduricomponent">ECMAScript decodeURIComponent</a>
         */
        public static JSValue decodeURIComponent(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue encodedValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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
                        result.append('%')
                                .append(encodedString.charAt(percentIndex + 1))
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

                if (codePoint < minCodePoint
                        || codePoint > Character.MAX_CODE_POINT
                        || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)) {
                    return context.throwURIError("malformed UTF-8");
                }
                result.appendCodePoint(codePoint);
            }
            return new JSString(result.toString());
        }

        /**
         * encodeURI(uri)
         * Encode a URI by escaping certain characters.
         * Does not encode: A-Z a-z 0-9 ; , / ? : @ & = + $ - _ . ! ~ * ' ( ) #
         *
         * @see <a href="https://tc39.es/ecma262/#sec-encodeuri-uri">ECMAScript encodeURI</a>
         */
        public static JSValue encodeURI(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue uriValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            String uriString = JSTypeConversions.toString(context, uriValue).value();
            return encodeURIImpl(context, uriString, false);
        }

        /**
         * encodeURIComponent(uriComponent)
         * Encode a URI component by escaping certain characters.
         * More aggressive than encodeURI - also encodes: ; , / ? : @ & = + $ #
         *
         * @see <a href="https://tc39.es/ecma262/#sec-encodeuricomponent-uricomponent">ECMAScript encodeURIComponent</a>
         */
        public static JSValue encodeURIComponent(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue componentValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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
         * escape(string)
         * Deprecated function that encodes a string for use in a URL.
         * Encodes all characters except: A-Z a-z 0-9 @ * _ + - . /
         * Uses %XX for characters < 256 and %uXXXX for characters >= 256.
         *
         * @see <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/escape">MDN escape</a>
         */
        public static JSValue escape(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue stringValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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
         * eval(code)
         * Evaluate JavaScript code in the realm context.
         * <p>
         * Per ES spec, eval evaluates code in the realm of the eval function itself,
         * not the calling realm. This matters for cross-realm calls like other.eval('code').
         *
         * @param realmContext  the context where this eval function was created (the eval's realm)
         * @param callerContext the context of the calling code (for scope overlay and exception propagation)
         * @see <a href="https://tc39.es/ecma262/#sec-eval-x">ECMAScript eval</a>
         */
        public static JSValue eval(JSContext realmContext, JSContext callerContext, JSValue thisArg, JSValue[] args) {
            JSValue x = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

            // If x is not a string, return it unchanged
            if (!(x instanceof JSString)) {
                return x;
            }

            String code = ((JSString) x).value();
            boolean isDirectEvalCall = callerContext.consumeScheduledDirectEvalCall();
            boolean isSameRealm = (realmContext == callerContext);
            boolean shouldUseCallerFrameSemantics = isDirectEvalCall && isSameRealm;

            // Scope overlay: capture enclosing function's local variables onto the global
            // object so that eval code's GET_VAR/PUT_VAR can access them.
            StackFrame callerFrame = callerContext.getVirtualMachine().getCurrentFrame();
            boolean hasSameRealmCallerFrame = isSameRealm && callerFrame != null;
            // Eval is "inside a function" only if same-realm and the callerFrame is NOT the top-level program.
            boolean isEvalInFunction = shouldUseCallerFrameSemantics
                    && hasSameRealmCallerFrame
                    && callerFrame.getFunction() instanceof JSBytecodeFunction
                    && callerFrame.getCaller() != null;
            boolean shouldOverlayLocals = shouldUseCallerFrameSemantics
                    && hasSameRealmCallerFrame
                    && callerFrame.getFunction() instanceof JSBytecodeFunction;
            boolean inheritedStrictMode = shouldUseCallerFrameSemantics
                    && callerFrame != null
                    && callerFrame.getFunction() instanceof JSBytecodeFunction bytecodeFunction && bytecodeFunction.isStrict();
            String[] localVarNames = null;
            Set<String> localVarNameSet = null;
            Map<String, JSValue> savedGlobals = null;
            Set<String> absentKeys = null;
            Set<String> touchedOverlayKeys = null;
            Set<String> evalVarDeclarations = null;
            Set<String> evalLexDeclarations = null;
            Set<String> evalFunctionDeclarations = null;
            boolean parsedEvalDeclarations = false;
            boolean evalCodeStrict = inheritedStrictMode;
            boolean overlayStatePushed = false;
            JSObject global = realmContext.getGlobalObject();
            JSBytecodeFunction callerBytecodeFunction =
                    callerFrame != null && callerFrame.getFunction() instanceof JSBytecodeFunction bytecodeFunction
                            ? bytecodeFunction
                            : null;
            // Track function expression name binding index so eval cannot overwrite it.
            // Per ES2024 14.1.2 step 27, the binding is immutable (TypeError in strict,
            // silently ignored in sloppy mode).
            int functionNameLocalIndex = callerBytecodeFunction != null
                    ? callerBytecodeFunction.getSelfLocalIndex() : -1;

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

            if (shouldOverlayLocals
                    && callerBytecodeFunction != null
                    && callerBytecodeFunction.getBytecode().getLocalVarNames() != null) {
                localVarNames = callerBytecodeFunction.getBytecode().getLocalVarNames();
                localVarNameSet = new HashSet<>();
                savedGlobals = new HashMap<>();
                absentKeys = new HashSet<>();
                touchedOverlayKeys = new HashSet<>();
                JSValue[] locals = callerFrame.getLocals();
                for (int i = 0; i < localVarNames.length && i < locals.length; i++) {
                    String name = localVarNames[i];
                    if (name == null) {
                        continue;
                    }
                    if (name.startsWith("$")) {
                        continue;
                    }
                    localVarNameSet.add(name);
                    overlayBinding(global, name, locals[i], savedGlobals, absentKeys, touchedOverlayKeys);
                    // Function name bindings are immutable: make the overlay property
                    // non-writable so SET_VAR throws TypeError in strict mode and
                    // silently fails in sloppy mode.
                    if (functionNameLocalIndex >= 0 && i == functionNameLocalIndex) {
                        PropertyDescriptor fnDesc = new PropertyDescriptor();
                        fnDesc.setValue(locals[i] != null ? locals[i] : JSUndefined.INSTANCE);
                        fnDesc.setWritable(false);
                        fnDesc.setEnumerable(true);
                        fnDesc.setConfigurable(true);
                        global.defineProperty(PropertyKey.fromString(name), fnDesc);
                    }
                }
                Map<String, JSValue> dynamicVarBindings = callerFrame.getDynamicVarBindings();
                if (dynamicVarBindings != null) {
                    for (Map.Entry<String, JSValue> entry : dynamicVarBindings.entrySet()) {
                        overlayBinding(global, entry.getKey(), entry.getValue(), savedGlobals, absentKeys, touchedOverlayKeys);
                    }
                }
                List<WithObjectCandidate> withObjectCandidates = new ArrayList<>();
                for (int i = 0; i < localVarNames.length && i < locals.length; i++) {
                    String name = localVarNames[i];
                    JSValue localValue = locals[i];
                    if (name == null || localValue == null || localValue == JSUndefined.INSTANCE) {
                        continue;
                    }
                    if (!(localValue instanceof JSObject withObject) || !name.startsWith("$withObject")) {
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
                        overlayBinding(global, keyName, withObject.get(propertyKey), savedGlobals, absentKeys, touchedOverlayKeys);
                    }
                }
            }

            if (shouldOverlayLocals && savedGlobals != null && absentKeys != null) {
                realmContext.pushEvalOverlay(savedGlobals, absentKeys);
                overlayStatePushed = true;
            }

            try {
                if (hasSameRealmCallerFrame) {
                    try {
                        Compiler evalCompiler = new Compiler(code, "<eval>");
                        Program evalAst = evalCompiler.parse(false);
                        evalVarDeclarations = new HashSet<>();
                        evalLexDeclarations = new HashSet<>();
                        AstUtils.collectGlobalDeclarations(evalAst, evalVarDeclarations, evalLexDeclarations);
                        evalFunctionDeclarations = collectFunctionDeclarationNames(evalAst.body());
                        parsedEvalDeclarations = true;
                        evalCodeStrict = evalCodeStrict || evalAst.strict();
                    } catch (Exception ignored) {
                        // Parse error in eval code - let the normal eval path report it.
                    }
                }

                // EvalDeclarationInstantiation: check if eval code declares "var arguments"
                // in a function with parameter expressions. Per QuickJS add_arguments_arg(),
                // a lexical "arguments" binding exists in the argument scope when the function
                // has parameter expressions (non-strict mode), preventing eval from redeclaring it.
                if (isEvalInFunction
                        && callerBytecodeFunction != null
                        && callerBytecodeFunction.hasParameterExpressions()
                        && !callerBytecodeFunction.isStrict()
                        && !evalCodeStrict
                        && parsedEvalDeclarations
                        && evalVarDeclarations != null
                        && evalVarDeclarations.contains("arguments")) {
                    if (!callerBytecodeFunction.isArrow()
                            || callerBytecodeFunction.hasArgumentsParameterBinding()) {
                        throw new JSException(
                                realmContext.throwError("SyntaxError",
                                        "Identifier 'arguments' has already been declared"));
                    }
                }
                // EvalDeclarationInstantiation: in functions with parameter expressions,
                // eval cannot create a var with the same name as a parameter.
                // Per spec, the parameter scope is separate from the var scope,
                // so "var x" in eval conflicts with parameter "x".
                if (isEvalInFunction
                        && callerBytecodeFunction != null
                        && callerBytecodeFunction.hasParameterExpressions()
                        && !callerBytecodeFunction.isStrict()
                        && !evalCodeStrict
                        && parsedEvalDeclarations
                        && evalVarDeclarations != null
                        && localVarNameSet != null) {
                    Set<String> bodyVarNames = collectBodyVarNames(callerBytecodeFunction);
                    if (bodyVarNames != null) {
                        for (String evalVar : evalVarDeclarations) {
                            if (JSKeyword.ARGUMENTS.equals(evalVar)) {
                                continue;
                            }
                            if (localVarNameSet.contains(evalVar) && !bodyVarNames.contains(evalVar)) {
                                throw new JSException(
                                        realmContext.throwError("SyntaxError",
                                                "Identifier '" + evalVar + "' has already been declared"));
                            }
                        }
                    }
                }
                if (hasSameRealmCallerFrame
                        && !evalCodeStrict
                        && parsedEvalDeclarations
                        && evalVarDeclarations != null
                        && (callerBytecodeFunction == null || !callerBytecodeFunction.isStrict())) {
                    Set<String> functionVarEnvironmentNames = null;
                    if (callerBytecodeFunction != null
                            && callerFrame != null
                            && callerFrame.getCaller() != null) {
                        functionVarEnvironmentNames = collectFunctionVarEnvironmentNames(callerBytecodeFunction);
                    }
                    for (String declarationName : evalVarDeclarations) {
                        if (isEvalInFunction
                                && JSKeyword.ARGUMENTS.equals(declarationName)
                                && callerBytecodeFunction != null) {
                            continue;
                        }
                        if (callerBytecodeFunction != null
                                && callerBytecodeFunction.isArrow()
                                && JSKeyword.ARGUMENTS.equals(declarationName)
                                && !callerBytecodeFunction.hasArgumentsParameterBinding()) {
                            continue;
                        }
                        if (callerFrame != null
                                && callerFrame.getCaller() == null
                                && callerContext.hasGlobalLexDeclaration(declarationName)) {
                            throw new JSException(
                                    realmContext.throwError("SyntaxError",
                                            "Identifier '" + declarationName + "' has already been declared"));
                        }
                        if (localVarNameSet != null
                                && localVarNameSet.contains(declarationName)
                                && functionVarEnvironmentNames != null
                                && !functionVarEnvironmentNames.contains(declarationName)) {
                            throw new JSException(
                                    realmContext.throwError("SyntaxError",
                                            "Identifier '" + declarationName + "' has already been declared"));
                        }
                    }
                }

                if (isEvalInFunction
                        && parsedEvalDeclarations
                        && evalVarDeclarations != null
                        && savedGlobals != null
                        && absentKeys != null
                        && touchedOverlayKeys != null) {
                    for (String declarationName : evalVarDeclarations) {
                        if (localVarNameSet != null && localVarNameSet.contains(declarationName)) {
                            continue;
                        }
                        PropertyKey declarationKey = PropertyKey.fromString(declarationName);
                        JSValue initialValue = global.has(declarationKey)
                                ? global.get(declarationKey)
                                : JSUndefined.INSTANCE;
                        overlayBinding(global, declarationName, initialValue, savedGlobals, absentKeys, touchedOverlayKeys);
                    }
                }

                if (!isDirectEvalCall
                        && !evalCodeStrict
                        && parsedEvalDeclarations) {
                    if (evalFunctionDeclarations != null) {
                        Map<String, PropertyDescriptor> existingFunctionDescriptors = new HashMap<>();
                        for (String functionDeclarationName : evalFunctionDeclarations) {
                            PropertyKey functionKey = PropertyKey.fromString(functionDeclarationName);
                            PropertyDescriptor existingDescriptor = global.getOwnPropertyDescriptor(functionKey);
                            existingFunctionDescriptors.put(functionDeclarationName, existingDescriptor);
                            if (existingDescriptor != null && !existingDescriptor.isConfigurable()) {
                                if (existingDescriptor.isAccessorDescriptor()
                                        || !(existingDescriptor.isWritable() && existingDescriptor.isEnumerable())) {
                                    throw new JSException(realmContext.throwTypeError("cannot define variable '" + functionDeclarationName + "'"));
                                }
                            }
                            if (existingDescriptor == null && !global.isExtensible()) {
                                throw new JSException(realmContext.throwTypeError("cannot define variable '" + functionDeclarationName + "'"));
                            }
                        }
                        for (String functionDeclarationName : evalFunctionDeclarations) {
                            PropertyDescriptor existingDescriptor = existingFunctionDescriptors.get(functionDeclarationName);
                            if (existingDescriptor == null || existingDescriptor.isConfigurable()) {
                                JSValue initialValue = existingDescriptor != null && existingDescriptor.hasValue()
                                        ? existingDescriptor.getValue()
                                        : JSUndefined.INSTANCE;
                                global.defineProperty(
                                        PropertyKey.fromString(functionDeclarationName),
                                        PropertyDescriptor.dataDescriptor(initialValue, PropertyDescriptor.DataState.All));
                            }
                        }
                    }
                    if (evalVarDeclarations != null) {
                        for (String declarationName : evalVarDeclarations) {
                            if (evalFunctionDeclarations != null && evalFunctionDeclarations.contains(declarationName)) {
                                continue;
                            }
                            PropertyKey declarationKey = PropertyKey.fromString(declarationName);
                            if (!global.has(declarationKey) && !global.isExtensible()) {
                                throw new JSException(realmContext.throwTypeError("cannot define variable '" + declarationName + "'"));
                            }
                        }
                    }
                }

                JSContext.EvalOverlaySnapshot suspendedOverlaySnapshot = null;
                if (!isDirectEvalCall) {
                    suspendedOverlaySnapshot = realmContext.suspendEvalOverlays();
                }
                JSValue result;
                try {
                    result = isDirectEvalCall
                            ? realmContext.evalDirect(code, "<eval>", inheritedStrictMode)
                            : realmContext.evalIndirect(code, "<eval>");
                } finally {
                    if (suspendedOverlaySnapshot != null) {
                        realmContext.resumeEvalOverlays(suspendedOverlaySnapshot);
                    }
                }

                // Copy modified values back to caller's locals
                if (localVarNames != null) {
                    JSValue[] locals = callerFrame.getLocals();
                    for (int i = 0; i < localVarNames.length && i < locals.length; i++) {
                        String name = localVarNames[i];
                        if (name == null) {
                            continue;
                        }
                        if (name.startsWith("$")) {
                            continue;
                        }
                        // Skip function name bindings — they are immutable and
                        // the non-writable overlay property prevented modification.
                        if (functionNameLocalIndex >= 0 && i == functionNameLocalIndex) {
                            continue;
                        }
                        PropertyKey key = PropertyKey.fromString(name);
                        if (global.has(key)) {
                            locals[i] = global.get(key);
                        }
                    }
                }
                if (isEvalInFunction
                        && !evalCodeStrict
                        && parsedEvalDeclarations
                        && evalVarDeclarations != null
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

                if (!isDirectEvalCall
                        && !evalCodeStrict
                        && evalFunctionDeclarations != null) {
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
                // Delete non-writable function name binding before restoring globals,
                // since global.set() cannot overwrite a non-writable property.
                if (functionNameLocalIndex >= 0 && savedGlobals != null
                        && localVarNames != null && functionNameLocalIndex < localVarNames.length
                        && localVarNames[functionNameLocalIndex] != null
                        && savedGlobals.containsKey(localVarNames[functionNameLocalIndex])) {
                    global.delete(realmContext, PropertyKey.fromString(localVarNames[functionNameLocalIndex]));
                }
                // Restore global object state for scope overlay
                if (savedGlobals != null) {
                    boolean isTopLevelCallerFrame = callerFrame != null && callerFrame.getCaller() == null;
                    for (var entry : savedGlobals.entrySet()) {
                        if (isTopLevelCallerFrame
                                && !evalCodeStrict
                                && parsedEvalDeclarations
                                && evalVarDeclarations != null
                                && evalVarDeclarations.contains(entry.getKey())
                                && !callerContext.hasGlobalLexDeclaration(entry.getKey())) {
                            continue;
                        }
                        global.set(PropertyKey.fromString(entry.getKey()), entry.getValue());
                    }
                }
                if (absentKeys != null) {
                    for (String name : absentKeys) {
                        global.delete(realmContext, PropertyKey.fromString(name));
                    }
                }
                // Clean up eval-created bindings that should be function-scoped (not global).
                // Per ES2024 B.3.3.3, var/function declarations in eval inside a function
                // go to the function's variable environment, not the global.
                if (hasSameRealmCallerFrame
                        && globalKeysBefore != null
                        && parsedEvalDeclarations
                        && evalVarDeclarations != null
                        && (isEvalInFunction || evalCodeStrict)) {
                    for (String declarationName : evalVarDeclarations) {
                        if (globalKeysBefore.contains(declarationName)) {
                            continue;
                        }
                        if (localVarNameSet != null && localVarNameSet.contains(declarationName)) {
                            continue;
                        }
                        global.delete(realmContext, PropertyKey.fromString(declarationName));
                    }
                }
                if (globalKeysBefore != null && parsedEvalDeclarations && evalLexDeclarations != null) {
                    for (String declarationName : evalLexDeclarations) {
                        if (globalKeysBefore.contains(declarationName)) {
                            continue;
                        }
                        global.delete(realmContext, PropertyKey.fromString(declarationName));
                    }
                }
                if (overlayStatePushed) {
                    realmContext.popEvalOverlay();
                }
                // For functions with parameter expressions, eval-created vars need to
                // persist on the global object so closures created in parameter defaults
                // can see them. Per spec, these vars go into the parameter scope's var
                // environment, which closures in defaults close over.
                if (isEvalInFunction
                        && callerBytecodeFunction != null
                        && callerBytecodeFunction.hasParameterExpressions()
                        && !evalCodeStrict
                        && parsedEvalDeclarations
                        && evalVarDeclarations != null
                        && callerFrame != null) {
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
                case '\t', '\n', '\u000B', '\f', '\r', ' ', '\u00A0', '\u1680', '\u2028',
                     '\u2029', '\u202F', '\u205F', '\u3000', '\uFEFF' -> true;
                default -> c >= '\u2000' && c <= '\u200A';
            };
        }

        /**
         * isFinite(value)
         * Determine whether a value is a finite number.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-isfinite-number">ECMAScript isFinite</a>
         */
        public static JSValue isFinite(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            if (value.isSymbol() || value.isSymbolObject()) {
                return context.throwTypeError("cannot convert symbol to number");
            }
            if (value.isBigInt() || value.isBigIntObject()) {
                return context.throwTypeError("cannot convert bigint to number");
            }
            double num = JSTypeConversions.toNumber(context, value).value();
            return JSBoolean.valueOf(!Double.isNaN(num) && !Double.isInfinite(num));
        }

        private static boolean isInfinityPrefix(String str, int start) {
            return start + 8 <= str.length() && str.startsWith("Infinity", start);
        }

        /**
         * isNaN(value)
         * Determine whether a value is NaN.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-isnan-number">ECMAScript isNaN</a>
         */
        public static JSValue isNaN(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            if (value.isSymbol() || value.isSymbolObject()) {
                return context.throwTypeError("cannot convert symbol to number");
            }
            if (value.isBigInt() || value.isBigIntObject()) {
                return context.throwTypeError("cannot convert bigint to number");
            }
            double num = JSTypeConversions.toNumber(context, value).value();
            return JSBoolean.valueOf(Double.isNaN(num));
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

        /**
         * Helper function to check if a character should not be escaped.
         * Returns true for: A-Z a-z 0-9 @ * _ + - . /
         */
        private static boolean isUnescaped(char c) {
            return (c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9') ||
                    c == '@' || c == '*' || c == '_' ||
                    c == '+' || c == '-' || c == '.' || c == '/';
        }

        private static void overlayBinding(
                JSObject global,
                String name,
                JSValue value,
                Map<String, JSValue> savedGlobals,
                Set<String> absentKeys,
                Set<String> touchedOverlayKeys) {
            if (name == null || touchedOverlayKeys == null || savedGlobals == null || absentKeys == null) {
                return;
            }
            if (touchedOverlayKeys.add(name)) {
                PropertyKey key = PropertyKey.fromString(name);
                if (global.has(key)) {
                    savedGlobals.put(name, global.get(key));
                } else {
                    absentKeys.add(name);
                }
            }
            global.set(PropertyKey.fromString(name), value != null ? value : JSUndefined.INSTANCE);
        }

        /**
         * parseFloat(string)
         * Parse a string and return a floating point number.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-parsefloat-string">ECMAScript parseFloat</a>
         */
        public static JSValue parseFloat(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue input = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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
            try {
                return JSNumber.of(Double.parseDouble(validNumber));
            } catch (NumberFormatException ignored) {
                return JSNumber.of(Double.NaN);
            }
        }

        /**
         * parseInt(string, radix)
         * Parse a string and return an integer of the specified radix.
         *
         * @see <a href="https://tc39.es/ecma262/#sec-parseint-string-radix">ECMAScript parseInt</a>
         */
        public static JSValue parseInt(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue input = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
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
            if (stripPrefix
                    && index + 1 < inputString.length()
                    && inputString.charAt(index) == '0'
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

        private static int skipLeadingWhitespace(String str) {
            int index = 0;
            while (index < str.length() && isEcmaWhitespace(str.charAt(index))) {
                index++;
            }
            return index;
        }

        /**
         * unescape(string)
         * Deprecated function that decodes a string encoded by escape().
         * Decodes %XX and %uXXXX sequences.
         *
         * @see <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/unescape">MDN unescape</a>
         */
        public static JSValue unescape(JSContext context, JSValue thisArg, JSValue[] args) {
            JSValue stringValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
            String str = JSTypeConversions.toString(context, stringValue).value();

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);

                if (c == '%') {
                    // Try to parse %uXXXX format
                    if (i + 6 <= str.length() && str.charAt(i + 1) == 'u') {
                        try {
                            int codePoint = Integer.parseInt(str.substring(i + 2, i + 6), 16);
                            result.append((char) codePoint);
                            i += 5; // Skip the next 5 characters (uXXXX)
                            continue;
                        } catch (NumberFormatException e) {
                            // Not a valid %uXXXX sequence, treat as literal
                        }
                    }

                    // Try to parse %XX format
                    if (i + 3 <= str.length()) {
                        try {
                            int codePoint = Integer.parseInt(str.substring(i + 1, i + 3), 16);
                            result.append((char) codePoint);
                            i += 2; // Skip the next 2 characters (XX)
                            continue;
                        } catch (NumberFormatException e) {
                            // Not a valid %XX sequence, treat as literal
                        }
                    }
                }

                // Not an escape sequence, append as is
                result.append(c);
            }

            return new JSString(result.toString());
        }

        private record WithObjectCandidate(int depth, JSObject object) {
        }
    }
}
