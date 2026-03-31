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
        this.globalObject = new JSObject(context);
        this.jsonObject = new JSONObject(context);
    }

    private void defineTemporalGetter(JSObject prototype, String name, JSNativeCallback getter) {
        JSNativeFunction getterFunc = new JSNativeFunction(context, "get " + name, 0, getter);
        prototype.defineProperty(
                PropertyKey.fromString(name),
                PropertyDescriptor.accessorDescriptor(getterFunc, null, PropertyDescriptor.AccessorState.Configurable));
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
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("resize"), new JSNativeFunction(context, "resize", 1, ArrayBufferPrototype::resize), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("slice"), new JSNativeFunction(context, "slice", 2, ArrayBufferPrototype::slice), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("transfer"), new JSNativeFunction(context, "transfer", 0, ArrayBufferPrototype::transfer), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("transferToFixedLength"), new JSNativeFunction(context, "transferToFixedLength", 0, ArrayBufferPrototype::transferToFixedLength), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("transferToImmutable"), new JSNativeFunction(context, "transferToImmutable", 0, ArrayBufferPrototype::transferToImmutable), PropertyDescriptor.DataState.ConfigurableWritable);

        // Define getter properties
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("byteLength"), new JSNativeFunction(context, "get byteLength", 0, ArrayBufferPrototype::getByteLength), PropertyDescriptor.AccessorState.Configurable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("detached"), new JSNativeFunction(context, "get detached", 0, ArrayBufferPrototype::getDetached), PropertyDescriptor.AccessorState.Configurable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("immutable"), new JSNativeFunction(context, "get immutable", 0, ArrayBufferPrototype::getImmutable), PropertyDescriptor.AccessorState.Configurable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("maxByteLength"), new JSNativeFunction(context, "get maxByteLength", 0, ArrayBufferPrototype::getMaxByteLength), PropertyDescriptor.AccessorState.Configurable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("resizable"), new JSNativeFunction(context, "get resizable", 0, ArrayBufferPrototype::getResizable), PropertyDescriptor.AccessorState.Configurable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSArrayBuffer.NAME), PropertyDescriptor.DataState.Configurable);

        // Create ArrayBuffer constructor as a function
        JSNativeFunction arrayBufferConstructor = new JSNativeFunction(context, JSArrayBuffer.NAME, 1, ArrayBufferConstructor::call, true, true);
        arrayBufferConstructor.defineProperty(PropertyKey.fromString("prototype"), arrayBufferPrototype, PropertyDescriptor.DataState.None);
        arrayBufferConstructor.setConstructorType(JSConstructorType.ARRAY_BUFFER);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("constructor"), arrayBufferConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Static methods
        arrayBufferConstructor.defineProperty(PropertyKey.fromString("isView"), new JSNativeFunction(context, "isView", 1, ArrayBufferConstructor::isView), PropertyDescriptor.DataState.ConfigurableWritable);

        // Symbol.species getter
        arrayBufferConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction(context, "get [Symbol.species]", 0, ArrayBufferConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString(JSArrayBuffer.NAME), arrayBufferConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
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
        arrayPrototype.defineProperty(PropertyKey.fromString("at"), new JSNativeFunction(context, "at", 1, ArrayPrototype::at), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("concat"), new JSNativeFunction(context, "concat", 1, ArrayPrototype::concat), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("copyWithin"), new JSNativeFunction(context, "copyWithin", 2, ArrayPrototype::copyWithin), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("entries"), new JSNativeFunction(context, "entries", 0, IteratorPrototype::arrayEntries), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("every"), new JSNativeFunction(context, "every", 1, ArrayPrototype::every), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("fill"), new JSNativeFunction(context, "fill", 1, ArrayPrototype::fill), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("filter"), new JSNativeFunction(context, "filter", 1, ArrayPrototype::filter), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("find"), new JSNativeFunction(context, "find", 1, ArrayPrototype::find), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("findIndex"), new JSNativeFunction(context, "findIndex", 1, ArrayPrototype::findIndex), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("findLast"), new JSNativeFunction(context, "findLast", 1, ArrayPrototype::findLast), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("findLastIndex"), new JSNativeFunction(context, "findLastIndex", 1, ArrayPrototype::findLastIndex), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("flat"), new JSNativeFunction(context, "flat", 0, ArrayPrototype::flat), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("flatMap"), new JSNativeFunction(context, "flatMap", 1, ArrayPrototype::flatMap), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("forEach"), new JSNativeFunction(context, "forEach", 1, ArrayPrototype::forEach), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("includes"), new JSNativeFunction(context, "includes", 1, ArrayPrototype::includes), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("indexOf"), new JSNativeFunction(context, "indexOf", 1, ArrayPrototype::indexOf), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("join"), new JSNativeFunction(context, "join", 1, ArrayPrototype::join), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("keys"), new JSNativeFunction(context, "keys", 0, IteratorPrototype::arrayKeys), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("lastIndexOf"), new JSNativeFunction(context, "lastIndexOf", 1, ArrayPrototype::lastIndexOf), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("map"), new JSNativeFunction(context, "map", 1, ArrayPrototype::map), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("pop"), new JSNativeFunction(context, "pop", 0, ArrayPrototype::pop), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("push"), new JSNativeFunction(context, "push", 1, ArrayPrototype::push), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("reduce"), new JSNativeFunction(context, "reduce", 1, ArrayPrototype::reduce), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("reduceRight"), new JSNativeFunction(context, "reduceRight", 1, ArrayPrototype::reduceRight), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("reverse"), new JSNativeFunction(context, "reverse", 0, ArrayPrototype::reverse), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("shift"), new JSNativeFunction(context, "shift", 0, ArrayPrototype::shift), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("slice"), new JSNativeFunction(context, "slice", 2, ArrayPrototype::slice), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("some"), new JSNativeFunction(context, "some", 1, ArrayPrototype::some), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("sort"), new JSNativeFunction(context, "sort", 1, ArrayPrototype::sort), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("splice"), new JSNativeFunction(context, "splice", 2, ArrayPrototype::splice), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, ArrayPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("toReversed"), new JSNativeFunction(context, "toReversed", 0, ArrayPrototype::toReversed), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("toSorted"), new JSNativeFunction(context, "toSorted", 1, ArrayPrototype::toSorted), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("toSpliced"), new JSNativeFunction(context, "toSpliced", 2, ArrayPrototype::toSpliced), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, ArrayPrototype::toString), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("unshift"), new JSNativeFunction(context, "unshift", 1, ArrayPrototype::unshift), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("values"), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromString("with"), new JSNativeFunction(context, "with", 2, ArrayPrototype::with), PropertyDescriptor.DataState.ConfigurableWritable);

        // Array.prototype[Symbol.*]
        arrayPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        arrayPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.UNSCOPABLES), ArrayPrototype.createUnscopablesObject(context), PropertyDescriptor.DataState.Configurable);

        // Create Array constructor as a function
        JSNativeFunction arrayConstructor = new JSNativeFunction(context, JSArray.NAME, 1, ArrayConstructor::call, true);
        arrayConstructor.defineProperty(PropertyKey.fromString("prototype"), arrayPrototype, PropertyDescriptor.DataState.None);
        arrayConstructor.setConstructorType(JSConstructorType.ARRAY);
        arrayPrototype.defineProperty(PropertyKey.fromString("constructor"), arrayConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Array static methods (writable, non-enumerable, configurable per spec)
        arrayConstructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction(context, "from", 1, ArrayConstructor::from), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayConstructor.defineProperty(PropertyKey.fromString("fromAsync"), new JSNativeFunction(context, "fromAsync", 1, ArrayConstructor::fromAsync), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayConstructor.defineProperty(PropertyKey.fromString("isArray"), new JSNativeFunction(context, "isArray", 1, ArrayConstructor::isArray), PropertyDescriptor.DataState.ConfigurableWritable);
        arrayConstructor.defineProperty(PropertyKey.fromString("of"), new JSNativeFunction(context, "of", 0, ArrayConstructor::of), PropertyDescriptor.DataState.ConfigurableWritable);

        // Symbol.species getter
        arrayConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction(context, "get [Symbol.species]", 0, ArrayConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString(JSArray.NAME), arrayConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize AsyncDisposableStack constructor and prototype.
     */
    private void initializeAsyncDisposableStackConstructor() {
        JSObject asyncDisposableStackPrototype = context.createJSObject();
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("adopt"), new JSNativeFunction(context, "adopt", 2, AsyncDisposableStackPrototype::adopt), PropertyDescriptor.DataState.ConfigurableWritable);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("defer"), new JSNativeFunction(context, "defer", 1, AsyncDisposableStackPrototype::defer), PropertyDescriptor.DataState.ConfigurableWritable);
        JSNativeFunction disposeAsyncFunction = new JSNativeFunction(context, "disposeAsync", 0, AsyncDisposableStackPrototype::disposeAsync);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("disposeAsync"), disposeAsyncFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("move"), new JSNativeFunction(context, "move", 0, AsyncDisposableStackPrototype::move), PropertyDescriptor.DataState.ConfigurableWritable);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("use"), new JSNativeFunction(context, "use", 1, AsyncDisposableStackPrototype::use), PropertyDescriptor.DataState.ConfigurableWritable);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ASYNC_DISPOSE), disposeAsyncFunction, PropertyDescriptor.DataState.ConfigurableWritable);

        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("disposed"), new JSNativeFunction(context, "get disposed", 0, AsyncDisposableStackPrototype::getDisposed), PropertyDescriptor.AccessorState.Configurable);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSAsyncDisposableStack.NAME), PropertyDescriptor.DataState.Configurable);

        JSNativeFunction asyncDisposableStackConstructor = new JSNativeFunction(context, JSAsyncDisposableStack.NAME, 0, AsyncDisposableStackConstructor::call, true, true);
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
        JSNativeFunction asyncFunctionConstructor = new JSNativeFunction(context, "AsyncFunction", 1,
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
        JSNativeFunction asyncIteratorMethod = new JSNativeFunction(context, "[Symbol.asyncIterator]",
                0,
                (ctx, thisArg, args) -> thisArg);
        asyncIteratorMethod.initializePrototypeChain(context);
        asyncIteratorPrototype.defineProperty(
                PropertyKey.fromSymbol(JSSymbol.ASYNC_ITERATOR),
                PropertyDescriptor.dataDescriptor(asyncIteratorMethod, PropertyDescriptor.DataState.ConfigurableWritable));
        JSNativeFunction asyncDisposeMethod = new JSNativeFunction(context, "[Symbol.asyncDispose]",
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
        JSNativeFunction asyncGeneratorNext = new JSNativeFunction(context, "next", 1, AsyncGeneratorPrototype::next);
        asyncGeneratorNext.initializePrototypeChain(context);
        asyncGeneratorPrototype.defineProperty(PropertyKey.fromString("next"), asyncGeneratorNext, PropertyDescriptor.DataState.ConfigurableWritable);
        JSNativeFunction asyncGeneratorReturn = new JSNativeFunction(context, "return", 1, AsyncGeneratorPrototype::return_);
        asyncGeneratorReturn.initializePrototypeChain(context);
        asyncGeneratorPrototype.defineProperty(PropertyKey.fromString("return"), asyncGeneratorReturn, PropertyDescriptor.DataState.ConfigurableWritable);
        JSNativeFunction asyncGeneratorThrow = new JSNativeFunction(context, "throw", 1, AsyncGeneratorPrototype::throw_);
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
        JSNativeFunction asyncGeneratorFunctionConstructor = new JSNativeFunction(context, "AsyncGeneratorFunction", 1,
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
        atomics.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction(context, "add", 3, atomicsObject::add), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("and"), new JSNativeFunction(context, "and", 3, atomicsObject::and), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("compareExchange"), new JSNativeFunction(context, "compareExchange", 4, atomicsObject::compareExchange), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("exchange"), new JSNativeFunction(context, "exchange", 3, atomicsObject::exchange), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("isLockFree"), new JSNativeFunction(context, "isLockFree", 1, atomicsObject::isLockFree), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("load"), new JSNativeFunction(context, "load", 2, atomicsObject::load), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("notify"), new JSNativeFunction(context, "notify", 3, atomicsObject::notify), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("or"), new JSNativeFunction(context, "or", 3, atomicsObject::or), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("pause"), new JSNativeFunction(context, "pause", 0, atomicsObject::pause), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("store"), new JSNativeFunction(context, "store", 3, atomicsObject::store), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("sub"), new JSNativeFunction(context, "sub", 3, atomicsObject::sub), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("wait"), new JSNativeFunction(context, "wait", 4, atomicsObject::wait), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("waitAsync"), new JSNativeFunction(context, "waitAsync", 4, atomicsObject::waitAsync), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromString("xor"), new JSNativeFunction(context, "xor", 3, atomicsObject::xor), PropertyDescriptor.DataState.ConfigurableWritable);
        atomics.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Atomics"), PropertyDescriptor.DataState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString("Atomics"), atomics, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize BigInt constructor and static methods.
     */
    private void initializeBigIntConstructor() {
        // Create BigInt.prototype
        JSObject bigIntPrototype = context.createJSObject();
        bigIntPrototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, BigIntPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        bigIntPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, BigIntPrototype::toString), PropertyDescriptor.DataState.ConfigurableWritable);
        bigIntPrototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, BigIntPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);
        bigIntPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSBigInt.NAME), PropertyDescriptor.DataState.Configurable);

        // Create BigInt constructor
        JSNativeFunction bigIntConstructor = new JSNativeFunction(context, JSBigInt.NAME, 1, BigIntConstructor::call, true);
        bigIntConstructor.defineProperty(PropertyKey.fromString("prototype"), bigIntPrototype, PropertyDescriptor.DataState.None);
        bigIntConstructor.setConstructorType(JSConstructorType.BIG_INT_OBJECT); // Mark as BigInt constructor
        bigIntPrototype.defineProperty(PropertyKey.fromString("constructor"), bigIntConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // BigInt static methods
        bigIntConstructor.defineProperty(PropertyKey.fromString("asIntN"), new JSNativeFunction(context, "asIntN", 2, BigIntConstructor::asIntN), PropertyDescriptor.DataState.ConfigurableWritable);
        bigIntConstructor.defineProperty(PropertyKey.fromString("asUintN"), new JSNativeFunction(context, "asUintN", 2, BigIntConstructor::asUintN), PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSBigInt.NAME), bigIntConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Boolean constructor and prototype.
     */
    private void initializeBooleanConstructor() {
        // Create Boolean.prototype as a Boolean object with [[BooleanData]] = false
        // Per QuickJS: JS_SetObjectData(ctx, ctx->class_proto[JS_CLASS_BOOLEAN], JS_NewBool(ctx, FALSE))
        JSBooleanObject booleanPrototype = new JSBooleanObject(context, false);
        context.transferPrototype(booleanPrototype, JSObject.NAME);
        booleanPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, BooleanPrototype::toString), PropertyDescriptor.DataState.ConfigurableWritable);
        booleanPrototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, BooleanPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);

        // Create Boolean constructor
        JSNativeFunction booleanConstructor = new JSNativeFunction(context, JSBoolean.NAME, 1, BooleanConstructor::call, true);
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

        globalObject.defineProperty(PropertyKey.fromString("console"), consoleObj, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize DataView constructor and prototype.
     */
    private void initializeDataViewConstructor() {
        // Create DataView.prototype
        JSObject dataViewPrototype = context.createJSObject();

        // Define getter properties
        dataViewPrototype.defineProperty(PropertyKey.fromString("buffer"), new JSNativeFunction(context, "get buffer", 0, DataViewPrototype::getBuffer), PropertyDescriptor.AccessorState.Configurable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("byteLength"), new JSNativeFunction(context, "get byteLength", 0, DataViewPrototype::getByteLength), PropertyDescriptor.AccessorState.Configurable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("byteOffset"), new JSNativeFunction(context, "get byteOffset", 0, DataViewPrototype::getByteOffset), PropertyDescriptor.AccessorState.Configurable);
        dataViewPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("DataView"), PropertyDescriptor.DataState.Configurable);

        // Int8/Uint8 methods
        dataViewPrototype.defineProperty(PropertyKey.fromString("getInt8"), new JSNativeFunction(context, "getInt8", 1, DataViewPrototype::getInt8), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setInt8"), new JSNativeFunction(context, "setInt8", 2, DataViewPrototype::setInt8), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("getUint8"), new JSNativeFunction(context, "getUint8", 1, DataViewPrototype::getUint8), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setUint8"), new JSNativeFunction(context, "setUint8", 2, DataViewPrototype::setUint8), PropertyDescriptor.DataState.ConfigurableWritable);

        // Int16/Uint16 methods
        dataViewPrototype.defineProperty(PropertyKey.fromString("getInt16"), new JSNativeFunction(context, "getInt16", 1, DataViewPrototype::getInt16), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setInt16"), new JSNativeFunction(context, "setInt16", 2, DataViewPrototype::setInt16), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("getUint16"), new JSNativeFunction(context, "getUint16", 1, DataViewPrototype::getUint16), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setUint16"), new JSNativeFunction(context, "setUint16", 2, DataViewPrototype::setUint16), PropertyDescriptor.DataState.ConfigurableWritable);

        // Int32/Uint32 methods
        dataViewPrototype.defineProperty(PropertyKey.fromString("getInt32"), new JSNativeFunction(context, "getInt32", 1, DataViewPrototype::getInt32), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setInt32"), new JSNativeFunction(context, "setInt32", 2, DataViewPrototype::setInt32), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("getUint32"), new JSNativeFunction(context, "getUint32", 1, DataViewPrototype::getUint32), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setUint32"), new JSNativeFunction(context, "setUint32", 2, DataViewPrototype::setUint32), PropertyDescriptor.DataState.ConfigurableWritable);

        // BigInt methods
        dataViewPrototype.defineProperty(PropertyKey.fromString("getBigInt64"), new JSNativeFunction(context, "getBigInt64", 1, DataViewPrototype::getBigInt64), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setBigInt64"), new JSNativeFunction(context, "setBigInt64", 2, DataViewPrototype::setBigInt64), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("getBigUint64"), new JSNativeFunction(context, "getBigUint64", 1, DataViewPrototype::getBigUint64), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setBigUint64"), new JSNativeFunction(context, "setBigUint64", 2, DataViewPrototype::setBigUint64), PropertyDescriptor.DataState.ConfigurableWritable);

        // Float methods
        dataViewPrototype.defineProperty(PropertyKey.fromString("getFloat16"), new JSNativeFunction(context, "getFloat16", 1, DataViewPrototype::getFloat16), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setFloat16"), new JSNativeFunction(context, "setFloat16", 2, DataViewPrototype::setFloat16), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("getFloat32"), new JSNativeFunction(context, "getFloat32", 1, DataViewPrototype::getFloat32), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setFloat32"), new JSNativeFunction(context, "setFloat32", 2, DataViewPrototype::setFloat32), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("getFloat64"), new JSNativeFunction(context, "getFloat64", 1, DataViewPrototype::getFloat64), PropertyDescriptor.DataState.ConfigurableWritable);
        dataViewPrototype.defineProperty(PropertyKey.fromString("setFloat64"), new JSNativeFunction(context, "setFloat64", 2, DataViewPrototype::setFloat64), PropertyDescriptor.DataState.ConfigurableWritable);

        // Create DataView constructor as a function that requires 'new'
        JSNativeFunction dataViewConstructor = new JSNativeFunction(context, "DataView", 1, DataViewConstructor::call, true, true);
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
        JSNativeFunction toUTCString = new JSNativeFunction(context, "toUTCString", 0, DatePrototype::toUTCString);
        JSNativeFunction toPrimitive = new JSNativeFunction(context, "[Symbol.toPrimitive]", 1, DatePrototype::symbolToPrimitive);

        datePrototype.defineProperty(PropertyKey.fromString("getDate"), new JSNativeFunction(context, "getDate", 0, DatePrototype::getDate), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getDay"), new JSNativeFunction(context, "getDay", 0, DatePrototype::getDay), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getFullYear"), new JSNativeFunction(context, "getFullYear", 0, DatePrototype::getFullYear), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getHours"), new JSNativeFunction(context, "getHours", 0, DatePrototype::getHours), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getMilliseconds"), new JSNativeFunction(context, "getMilliseconds", 0, DatePrototype::getMilliseconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getMinutes"), new JSNativeFunction(context, "getMinutes", 0, DatePrototype::getMinutes), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getMonth"), new JSNativeFunction(context, "getMonth", 0, DatePrototype::getMonth), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getSeconds"), new JSNativeFunction(context, "getSeconds", 0, DatePrototype::getSeconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getTime"), new JSNativeFunction(context, "getTime", 0, DatePrototype::getTime), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getTimezoneOffset"), new JSNativeFunction(context, "getTimezoneOffset", 0, DatePrototype::getTimezoneOffset), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCDate"), new JSNativeFunction(context, "getUTCDate", 0, DatePrototype::getUTCDate), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCDay"), new JSNativeFunction(context, "getUTCDay", 0, DatePrototype::getUTCDay), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCFullYear"), new JSNativeFunction(context, "getUTCFullYear", 0, DatePrototype::getUTCFullYear), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCHours"), new JSNativeFunction(context, "getUTCHours", 0, DatePrototype::getUTCHours), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCMilliseconds"), new JSNativeFunction(context, "getUTCMilliseconds", 0, DatePrototype::getUTCMilliseconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCMinutes"), new JSNativeFunction(context, "getUTCMinutes", 0, DatePrototype::getUTCMinutes), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCMonth"), new JSNativeFunction(context, "getUTCMonth", 0, DatePrototype::getUTCMonth), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getUTCSeconds"), new JSNativeFunction(context, "getUTCSeconds", 0, DatePrototype::getUTCSeconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("getYear"), new JSNativeFunction(context, "getYear", 0, DatePrototype::getYear), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setDate"), new JSNativeFunction(context, "setDate", 1, DatePrototype::setDate), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setFullYear"), new JSNativeFunction(context, "setFullYear", 3, DatePrototype::setFullYear), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setHours"), new JSNativeFunction(context, "setHours", 4, DatePrototype::setHours), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setMilliseconds"), new JSNativeFunction(context, "setMilliseconds", 1, DatePrototype::setMilliseconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setMinutes"), new JSNativeFunction(context, "setMinutes", 3, DatePrototype::setMinutes), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setMonth"), new JSNativeFunction(context, "setMonth", 2, DatePrototype::setMonth), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setSeconds"), new JSNativeFunction(context, "setSeconds", 2, DatePrototype::setSeconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setTime"), new JSNativeFunction(context, "setTime", 1, DatePrototype::setTime), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCDate"), new JSNativeFunction(context, "setUTCDate", 1, DatePrototype::setUTCDate), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCFullYear"), new JSNativeFunction(context, "setUTCFullYear", 3, DatePrototype::setUTCFullYear), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCHours"), new JSNativeFunction(context, "setUTCHours", 4, DatePrototype::setUTCHours), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCMilliseconds"), new JSNativeFunction(context, "setUTCMilliseconds", 1, DatePrototype::setUTCMilliseconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCMinutes"), new JSNativeFunction(context, "setUTCMinutes", 3, DatePrototype::setUTCMinutes), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCMonth"), new JSNativeFunction(context, "setUTCMonth", 2, DatePrototype::setUTCMonth), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setUTCSeconds"), new JSNativeFunction(context, "setUTCSeconds", 2, DatePrototype::setUTCSeconds), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("setYear"), new JSNativeFunction(context, "setYear", 1, DatePrototype::setYear), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toDateString"), new JSNativeFunction(context, "toDateString", 0, DatePrototype::toDateString), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toGMTString"), toUTCString, PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toISOString"), new JSNativeFunction(context, "toISOString", 0, DatePrototype::toISOString), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toJSON"), new JSNativeFunction(context, "toJSON", 1, DatePrototype::toJSON), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toLocaleDateString"), new JSNativeFunction(context, "toLocaleDateString", 0, DatePrototype::toLocaleDateString), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, DatePrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toLocaleTimeString"), new JSNativeFunction(context, "toLocaleTimeString", 0, DatePrototype::toLocaleTimeString), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, DatePrototype::toStringMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toTimeString"), new JSNativeFunction(context, "toTimeString", 0, DatePrototype::toTimeString), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromString("toUTCString"), toUTCString, PropertyDescriptor.DataState.ConfigurableWritable);
        if (context.getRuntime().getOptions().isTemporalEnabled()) {
            datePrototype.defineProperty(
                    PropertyKey.fromString("toTemporalInstant"),
                    new JSNativeFunction(context, "toTemporalInstant", 0, DatePrototype::toTemporalInstant),
                    PropertyDescriptor.DataState.ConfigurableWritable);
        }
        datePrototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, DatePrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);
        datePrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_PRIMITIVE), toPrimitive, PropertyDescriptor.DataState.Configurable);

        JSNativeFunction dateConstructor = new JSNativeFunction(context, JSDate.NAME, 7, DateConstructor::call, true);
        dateConstructor.defineProperty(PropertyKey.fromString("prototype"), datePrototype, PropertyDescriptor.DataState.None);
        dateConstructor.setConstructorType(JSConstructorType.DATE);
        datePrototype.defineProperty(PropertyKey.fromString("constructor"), dateConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        dateConstructor.defineProperty(PropertyKey.fromString("UTC"), new JSNativeFunction(context, "UTC", 7, DateConstructor::UTC), PropertyDescriptor.DataState.ConfigurableWritable);
        dateConstructor.defineProperty(PropertyKey.fromString("now"), new JSNativeFunction(context, "now", 0, DateConstructor::now), PropertyDescriptor.DataState.ConfigurableWritable);
        dateConstructor.defineProperty(PropertyKey.fromString("parse"), new JSNativeFunction(context, "parse", 1, DateConstructor::parse), PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSDate.NAME), dateConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize DisposableStack constructor and prototype.
     */
    private void initializeDisposableStackConstructor() {
        JSObject disposableStackPrototype = context.createJSObject();
        JSNativeFunction disposeFunction = new JSNativeFunction(context, "dispose", 0, DisposableStackPrototype::dispose);
        disposableStackPrototype.defineProperty(PropertyKey.fromString("adopt"), new JSNativeFunction(context, "adopt", 2, DisposableStackPrototype::adopt), PropertyDescriptor.DataState.ConfigurableWritable);
        disposableStackPrototype.defineProperty(PropertyKey.fromString("defer"), new JSNativeFunction(context, "defer", 1, DisposableStackPrototype::defer), PropertyDescriptor.DataState.ConfigurableWritable);
        disposableStackPrototype.defineProperty(PropertyKey.fromString("dispose"), disposeFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        disposableStackPrototype.defineProperty(PropertyKey.fromString("move"), new JSNativeFunction(context, "move", 0, DisposableStackPrototype::move), PropertyDescriptor.DataState.ConfigurableWritable);
        disposableStackPrototype.defineProperty(PropertyKey.fromString("use"), new JSNativeFunction(context, "use", 1, DisposableStackPrototype::use), PropertyDescriptor.DataState.ConfigurableWritable);
        disposableStackPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.DISPOSE), disposeFunction, PropertyDescriptor.DataState.ConfigurableWritable);

        disposableStackPrototype.defineProperty(PropertyKey.fromString("disposed"), new JSNativeFunction(context, "get disposed", 0, DisposableStackPrototype::getDisposed), PropertyDescriptor.AccessorState.Configurable);
        disposableStackPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSDisposableStack.NAME), PropertyDescriptor.DataState.Configurable);

        JSNativeFunction disposableStackConstructor = new JSNativeFunction(context, JSDisposableStack.NAME, 0, DisposableStackConstructor::call, true, true);
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
                new JSNativeFunction(context, "register", 2, FinalizationRegistryPrototype::register), PropertyDescriptor.DataState.ConfigurableWritable);
        finalizationRegistryPrototype.defineProperty(PropertyKey.fromString("unregister"),
                new JSNativeFunction(context, "unregister", 1, FinalizationRegistryPrototype::unregister), PropertyDescriptor.DataState.ConfigurableWritable);
        finalizationRegistryPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                new JSString(JSFinalizationRegistry.NAME), PropertyDescriptor.DataState.Configurable);

        JSNativeFunction finalizationRegistryConstructor = new JSNativeFunction(context, JSFinalizationRegistry.NAME, 1, FinalizationRegistryConstructor::call, true, true);
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
        JSNativeFunction functionPrototype = new JSNativeFunction(context, null, 0, (ctx, thisObj, args) -> JSUndefined.INSTANCE);
        // Remove the auto-created properties - Function.prototype has custom property descriptors
        functionPrototype.delete(PropertyKey.LENGTH);
        functionPrototype.delete(PropertyKey.NAME);
        functionPrototype.delete(PropertyKey.PROTOTYPE);

        functionPrototype.defineProperty(PropertyKey.fromString("apply"), new JSNativeFunction(context, "apply", 2, FunctionPrototype::apply), PropertyDescriptor.DataState.ConfigurableWritable);
        functionPrototype.defineProperty(PropertyKey.fromString("bind"), new JSNativeFunction(context, "bind", 1, FunctionPrototype::bind), PropertyDescriptor.DataState.ConfigurableWritable);
        functionPrototype.defineProperty(PropertyKey.fromString("call"), new JSNativeFunction(context, "call", 1, FunctionPrototype::call), PropertyDescriptor.DataState.ConfigurableWritable);
        functionPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, FunctionPrototype::toString_), PropertyDescriptor.DataState.ConfigurableWritable);

        String restrictedFunctionPropertyMessage =
                "'caller', 'callee', and 'arguments' properties may not be accessed on strict mode functions or the arguments objects for calls to them";

        // Shared %ThrowTypeError% intrinsic for strict arguments.callee and restricted accessors.
        JSNativeFunction throwTypeError = new JSNativeFunction(context, "",
                0,
                (childContext, thisObj, args) -> {
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

        functionPrototype.defineProperty(
                PropertyKey.fromString("arguments"),
                throwTypeError,
                throwTypeError,
                PropertyDescriptor.AccessorState.Configurable);
        functionPrototype.defineProperty(
                PropertyKey.fromString("caller"),
                throwTypeError,
                throwTypeError,
                PropertyDescriptor.AccessorState.Configurable);

        // Function.prototype[Symbol.hasInstance] - implements OrdinaryHasInstance
        // Per ES spec 19.2.3.6: writable: false, enumerable: false, configurable: false
        JSNativeFunction hasInstanceFunc = new JSNativeFunction(context, "[Symbol.hasInstance]", 1,
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
        JSNativeFunction functionConstructor = new JSNativeFunction(context, JSFunction.NAME, 1, FunctionConstructor::call, true);
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
        generatorPrototype.defineProperty(PropertyKey.fromString("next"), new JSNativeFunction(context, "next", 1, GeneratorPrototype::next), PropertyDescriptor.DataState.ConfigurableWritable);
        generatorPrototype.defineProperty(PropertyKey.fromString("return"), new JSNativeFunction(context, "return", 1, GeneratorPrototype::returnMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        generatorPrototype.defineProperty(PropertyKey.fromString("throw"), new JSNativeFunction(context, "throw", 1, GeneratorPrototype::throwMethod), PropertyDescriptor.DataState.ConfigurableWritable);

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
        JSNativeFunction generatorFunctionConstructor = new JSNativeFunction(context, "GeneratorFunction", 1,
                FunctionConstructor::callGenerator,
                true);
        JSValue functionConstructorValue = globalObject.get(PropertyKey.fromString(JSFunction.NAME));
        if (functionConstructorValue instanceof JSObject functionConstructorObject) {
            generatorFunctionConstructor.setPrototype(functionConstructorObject);
        }
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
        globalObject.defineProperty(PropertyKey.fromString("eval"), new JSNativeFunction(context, "eval", 1,
                (callerContext, thisArg, args) -> GlobalFunction.eval(realmContext, callerContext, args)), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("isFinite"), new JSNativeFunction(context, "isFinite", 1, GlobalFunction::isFinite), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("isNaN"), new JSNativeFunction(context, "isNaN", 1, GlobalFunction::isNaN), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("parseFloat"), new JSNativeFunction(context, "parseFloat", 1, GlobalFunction::parseFloat), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("parseInt"), new JSNativeFunction(context, "parseInt", 2, GlobalFunction::parseInt), PropertyDescriptor.DataState.ConfigurableWritable);

        // URI handling functions
        globalObject.defineProperty(PropertyKey.fromString("decodeURI"), new JSNativeFunction(context, "decodeURI", 1, GlobalFunction::decodeURI), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("decodeURIComponent"), new JSNativeFunction(context, "decodeURIComponent", 1, GlobalFunction::decodeURIComponent), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("encodeURI"), new JSNativeFunction(context, "encodeURI", 1, GlobalFunction::encodeURI), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("encodeURIComponent"), new JSNativeFunction(context, "encodeURIComponent", 1, GlobalFunction::encodeURIComponent), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("escape"), new JSNativeFunction(context, "escape", 1, GlobalFunction::escape), PropertyDescriptor.DataState.ConfigurableWritable);
        globalObject.defineProperty(PropertyKey.fromString("unescape"), new JSNativeFunction(context, "unescape", 1, GlobalFunction::unescape), PropertyDescriptor.DataState.ConfigurableWritable);

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
        intlObject.defineProperty(PropertyKey.fromString("getCanonicalLocales"), new JSNativeFunction(context, "getCanonicalLocales", 1, JSIntlObject::getCanonicalLocales), PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("supportedValuesOf"), new JSNativeFunction(context, "supportedValuesOf", 1, JSIntlObject::supportedValuesOf_Intl), PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject dateTimeFormatPrototype = context.createJSObject();
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromString("format"),
                new JSNativeFunction(context, "get format", 0, JSIntlObject::dateTimeFormatFormatGetter),
                PropertyDescriptor.AccessorState.Configurable);
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction(context, "resolvedOptions", 0, JSIntlObject::dateTimeFormatResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromString("formatToParts"), new JSNativeFunction(context, "formatToParts", 1, JSIntlObject::dateTimeFormatFormatToParts), PropertyDescriptor.DataState.ConfigurableWritable);
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromString("formatRange"), new JSNativeFunction(context, "formatRange", 2, JSIntlObject::dateTimeFormatFormatRange), PropertyDescriptor.DataState.ConfigurableWritable);
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromString("formatRangeToParts"), new JSNativeFunction(context, "formatRangeToParts", 2, JSIntlObject::dateTimeFormatFormatRangeToParts), PropertyDescriptor.DataState.ConfigurableWritable);
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.DateTimeFormat"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction dateTimeFormatConstructor = new JSNativeFunction(context, "DateTimeFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createDateTimeFormat(childContext, dateTimeFormatPrototype, args),
                true);
        dateTimeFormatConstructor.defineProperty(PropertyKey.fromString("prototype"), dateTimeFormatPrototype, PropertyDescriptor.DataState.None);
        dateTimeFormatConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction(context, "supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        dateTimeFormatPrototype.defineProperty(PropertyKey.fromString("constructor"), dateTimeFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("DateTimeFormat"), dateTimeFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject displayNamesPrototype = context.createJSObject();
        displayNamesPrototype.defineProperty(PropertyKey.fromString("of"),
                new JSNativeFunction(context, "of", 1, JSIntlObject::displayNamesOf),
                PropertyDescriptor.DataState.ConfigurableWritable);
        displayNamesPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"),
                new JSNativeFunction(context, "resolvedOptions", 0, JSIntlObject::displayNamesResolvedOptions),
                PropertyDescriptor.DataState.ConfigurableWritable);
        displayNamesPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                new JSString("Intl.DisplayNames"),
                PropertyDescriptor.DataState.Configurable);
        JSNativeFunction displayNamesConstructor = new JSNativeFunction(context, "DisplayNames",
                2,
                (childContext, thisArg, args) -> JSIntlObject.createDisplayNames(childContext, displayNamesPrototype, args),
                true,
                true);
        displayNamesConstructor.defineProperty(PropertyKey.fromString("prototype"), displayNamesPrototype, PropertyDescriptor.DataState.None);
        displayNamesConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"),
                new JSNativeFunction(context, "supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf),
                PropertyDescriptor.DataState.ConfigurableWritable);
        displayNamesPrototype.defineProperty(PropertyKey.fromString("constructor"), displayNamesConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("DisplayNames"), displayNamesConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject durationFormatPrototype = context.createJSObject();
        durationFormatPrototype.defineProperty(PropertyKey.fromString("format"),
                new JSNativeFunction(context, "format", 1, JSIntlObject::durationFormatFormat),
                PropertyDescriptor.DataState.ConfigurableWritable);
        durationFormatPrototype.defineProperty(PropertyKey.fromString("formatToParts"),
                new JSNativeFunction(context, "formatToParts", 1, JSIntlObject::durationFormatFormatToParts),
                PropertyDescriptor.DataState.ConfigurableWritable);
        durationFormatPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"),
                new JSNativeFunction(context, "resolvedOptions", 0, JSIntlObject::durationFormatResolvedOptions),
                PropertyDescriptor.DataState.ConfigurableWritable);
        durationFormatPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                new JSString("Intl.DurationFormat"),
                PropertyDescriptor.DataState.Configurable);
        JSNativeFunction durationFormatConstructor = new JSNativeFunction(context, "DurationFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createDurationFormat(childContext, durationFormatPrototype, args),
                true,
                true);
        durationFormatConstructor.defineProperty(PropertyKey.fromString("prototype"), durationFormatPrototype, PropertyDescriptor.DataState.None);
        durationFormatConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"),
                new JSNativeFunction(context, "supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf),
                PropertyDescriptor.DataState.ConfigurableWritable);
        durationFormatPrototype.defineProperty(PropertyKey.fromString("constructor"), durationFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("DurationFormat"), durationFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject numberFormatPrototype = context.createJSObject();
        numberFormatPrototype.defineProperty(PropertyKey.fromString("format"),
                new JSNativeFunction(context, "get format", 0, JSIntlObject::numberFormatFormatGetter),
                PropertyDescriptor.AccessorState.Configurable);
        numberFormatPrototype.defineProperty(PropertyKey.fromString("formatRange"),
                new JSNativeFunction(context, "formatRange", 2, JSIntlObject::numberFormatFormatRange),
                PropertyDescriptor.DataState.ConfigurableWritable);
        numberFormatPrototype.defineProperty(PropertyKey.fromString("formatRangeToParts"),
                new JSNativeFunction(context, "formatRangeToParts", 2, JSIntlObject::numberFormatFormatRangeToParts),
                PropertyDescriptor.DataState.ConfigurableWritable);
        numberFormatPrototype.defineProperty(PropertyKey.fromString("formatToParts"), new JSNativeFunction(context, "formatToParts", 1, JSIntlObject::numberFormatFormatToParts), PropertyDescriptor.DataState.ConfigurableWritable);
        numberFormatPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction(context, "resolvedOptions", 0, JSIntlObject::numberFormatResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        numberFormatPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.NumberFormat"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction numberFormatConstructor = new JSNativeFunction(context, "NumberFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createNumberFormat(childContext, numberFormatPrototype, args),
                true);
        numberFormatConstructor.defineProperty(PropertyKey.fromString("prototype"), numberFormatPrototype, PropertyDescriptor.DataState.None);
        numberFormatConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction(context, "supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        numberFormatPrototype.defineProperty(PropertyKey.fromString("constructor"), numberFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("NumberFormat"), numberFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject collatorPrototype = context.createJSObject();
        collatorPrototype.defineProperty(PropertyKey.fromString("compare"),
                new JSNativeFunction(context, "get compare", 0, JSIntlObject::collatorCompareGetter),
                PropertyDescriptor.AccessorState.Configurable);
        collatorPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction(context, "resolvedOptions", 0, JSIntlObject::collatorResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        collatorPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.Collator"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction collatorConstructor = new JSNativeFunction(context, "Collator",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createCollator(childContext, collatorPrototype, args),
                true);
        collatorConstructor.defineProperty(PropertyKey.fromString("prototype"), collatorPrototype, PropertyDescriptor.DataState.None);
        collatorConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction(context, "supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        collatorPrototype.defineProperty(PropertyKey.fromString("constructor"), collatorConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("Collator"), collatorConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject pluralRulesPrototype = context.createJSObject();
        pluralRulesPrototype.defineProperty(PropertyKey.fromString("select"), new JSNativeFunction(context, "select", 1, JSIntlObject::pluralRulesSelect), PropertyDescriptor.DataState.ConfigurableWritable);
        pluralRulesPrototype.defineProperty(PropertyKey.fromString("selectRange"), new JSNativeFunction(context, "selectRange", 2, JSIntlObject::pluralRulesSelectRange), PropertyDescriptor.DataState.ConfigurableWritable);
        pluralRulesPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction(context, "resolvedOptions", 0, JSIntlObject::pluralRulesResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        pluralRulesPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.PluralRules"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction pluralRulesConstructor = new JSNativeFunction(context, "PluralRules",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createPluralRules(childContext, pluralRulesPrototype, args),
                true,
                true);
        pluralRulesConstructor.defineProperty(PropertyKey.fromString("prototype"), pluralRulesPrototype, PropertyDescriptor.DataState.None);
        pluralRulesConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction(context, "supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        pluralRulesPrototype.defineProperty(PropertyKey.fromString("constructor"), pluralRulesConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("PluralRules"), pluralRulesConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject relativeTimeFormatPrototype = context.createJSObject();
        relativeTimeFormatPrototype.defineProperty(PropertyKey.fromString("format"), new JSNativeFunction(context, "format", 2, JSIntlObject::relativeTimeFormatFormat), PropertyDescriptor.DataState.ConfigurableWritable);
        relativeTimeFormatPrototype.defineProperty(PropertyKey.fromString("formatToParts"), new JSNativeFunction(context, "formatToParts", 2, JSIntlObject::relativeTimeFormatFormatToParts), PropertyDescriptor.DataState.ConfigurableWritable);
        relativeTimeFormatPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction(context, "resolvedOptions", 0, JSIntlObject::relativeTimeFormatResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        relativeTimeFormatPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.RelativeTimeFormat"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction relativeTimeFormatConstructor = new JSNativeFunction(context, "RelativeTimeFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createRelativeTimeFormat(childContext, relativeTimeFormatPrototype, args),
                true,
                true);
        relativeTimeFormatConstructor.defineProperty(PropertyKey.fromString("prototype"), relativeTimeFormatPrototype, PropertyDescriptor.DataState.None);
        relativeTimeFormatConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction(context, "supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        relativeTimeFormatPrototype.defineProperty(PropertyKey.fromString("constructor"), relativeTimeFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("RelativeTimeFormat"), relativeTimeFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject segmentDataPrototype = context.createJSObject();
        JSNativeFunction segmentsContainingFunction = new JSNativeFunction(context, "containing", 1, JSIntlObject::segmentsContaining);
        segmentsContainingFunction.initializePrototypeChain(context);
        segmentDataPrototype.defineProperty(PropertyKey.fromString("containing"), segmentsContainingFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        JSNativeFunction segmentsIteratorFunction = new JSNativeFunction(context, "[Symbol.iterator]", 0, JSIntlObject::segmentsIterator);
        segmentsIteratorFunction.initializePrototypeChain(context);
        segmentDataPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR), segmentsIteratorFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        JSObject segmenterPrototype = context.createJSObject();
        segmenterPrototype.defineProperty(PropertyKey.fromString("segment"), new JSNativeFunction(context, "segment", 1, JSIntlObject::segmenterSegment), PropertyDescriptor.DataState.ConfigurableWritable);
        segmenterPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction(context, "resolvedOptions", 0, JSIntlObject::segmenterResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        segmenterPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.Segmenter"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction segmenterConstructor = new JSNativeFunction(context, "Segmenter",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createSegmenter(childContext, segmenterPrototype, segmentDataPrototype, args),
                true,
                true);
        segmenterConstructor.defineProperty(PropertyKey.fromString("prototype"), segmenterPrototype, PropertyDescriptor.DataState.None);
        segmenterConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction(context, "supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        segmenterPrototype.defineProperty(PropertyKey.fromString("constructor"), segmenterConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("Segmenter"), segmenterConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject listFormatPrototype = context.createJSObject();
        listFormatPrototype.defineProperty(PropertyKey.fromString("format"), new JSNativeFunction(context, "format", 1, JSIntlObject::listFormatFormat), PropertyDescriptor.DataState.ConfigurableWritable);
        listFormatPrototype.defineProperty(PropertyKey.fromString("formatToParts"), new JSNativeFunction(context, "formatToParts", 1, JSIntlObject::listFormatFormatToParts), PropertyDescriptor.DataState.ConfigurableWritable);
        listFormatPrototype.defineProperty(PropertyKey.fromString("resolvedOptions"), new JSNativeFunction(context, "resolvedOptions", 0, JSIntlObject::listFormatResolvedOptions), PropertyDescriptor.DataState.ConfigurableWritable);
        listFormatPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.ListFormat"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction listFormatConstructor = new JSNativeFunction(context, "ListFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createListFormat(childContext, listFormatPrototype, args),
                true,
                true);
        listFormatConstructor.defineProperty(PropertyKey.fromString("prototype"), listFormatPrototype, PropertyDescriptor.DataState.None);
        listFormatConstructor.defineProperty(PropertyKey.fromString("supportedLocalesOf"), new JSNativeFunction(context, "supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf), PropertyDescriptor.DataState.ConfigurableWritable);
        listFormatPrototype.defineProperty(PropertyKey.fromString("constructor"), listFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        intlObject.defineProperty(PropertyKey.fromString("ListFormat"), listFormatConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        JSObject localePrototype = context.createJSObject();
        localePrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, JSIntlObject::localeToString), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("baseName"), new JSNativeFunction(context, "get baseName", 0, JSIntlObject::localeGetBaseName), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("calendar"), new JSNativeFunction(context, "get calendar", 0, JSIntlObject::localeGetCalendar), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("caseFirst"), new JSNativeFunction(context, "get caseFirst", 0, JSIntlObject::localeGetCaseFirst), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("collation"), new JSNativeFunction(context, "get collation", 0, JSIntlObject::localeGetCollation), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("hourCycle"), new JSNativeFunction(context, "get hourCycle", 0, JSIntlObject::localeGetHourCycle), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("language"), new JSNativeFunction(context, "get language", 0, JSIntlObject::localeGetLanguage), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("numberingSystem"), new JSNativeFunction(context, "get numberingSystem", 0, JSIntlObject::localeGetNumberingSystem), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("numeric"), new JSNativeFunction(context, "get numeric", 0, JSIntlObject::localeGetNumeric), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("script"), new JSNativeFunction(context, "get script", 0, JSIntlObject::localeGetScript), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("region"), new JSNativeFunction(context, "get region", 0, JSIntlObject::localeGetRegion), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("variants"), new JSNativeFunction(context, "get variants", 0, JSIntlObject::localeGetVariants), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("firstDayOfWeek"), new JSNativeFunction(context, "get firstDayOfWeek", 0, JSIntlObject::localeGetFirstDayOfWeek), PropertyDescriptor.AccessorState.Configurable);
        localePrototype.defineProperty(PropertyKey.fromString("maximize"), new JSNativeFunction(context, "maximize", 0, JSIntlObject::localeMaximize), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("minimize"), new JSNativeFunction(context, "minimize", 0, JSIntlObject::localeMinimize), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getCalendars"), new JSNativeFunction(context, "getCalendars", 0, JSIntlObject::localeGetCalendars), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getCollations"), new JSNativeFunction(context, "getCollations", 0, JSIntlObject::localeGetCollations), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getHourCycles"), new JSNativeFunction(context, "getHourCycles", 0, JSIntlObject::localeGetHourCycles), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getNumberingSystems"), new JSNativeFunction(context, "getNumberingSystems", 0, JSIntlObject::localeGetNumberingSystems), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getTextInfo"), new JSNativeFunction(context, "getTextInfo", 0, JSIntlObject::localeGetTextInfo), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getTimeZones"), new JSNativeFunction(context, "getTimeZones", 0, JSIntlObject::localeGetTimeZones), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromString("getWeekInfo"), new JSNativeFunction(context, "getWeekInfo", 0, JSIntlObject::localeGetWeekInfo), PropertyDescriptor.DataState.ConfigurableWritable);
        localePrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Intl.Locale"), PropertyDescriptor.DataState.Configurable);
        JSNativeFunction localeConstructor = new JSNativeFunction(context, "Locale",
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

        iteratorPrototype.defineProperty(PropertyKey.fromString("drop"), new JSNativeFunction(context, "drop", 1, IteratorPrototype::drop), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("every"), new JSNativeFunction(context, "every", 1, IteratorPrototype::every), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("filter"), new JSNativeFunction(context, "filter", 1, IteratorPrototype::filter), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("find"), new JSNativeFunction(context, "find", 1, IteratorPrototype::find), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("flatMap"), new JSNativeFunction(context, "flatMap", 1, IteratorPrototype::flatMap), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("forEach"), new JSNativeFunction(context, "forEach", 1, IteratorPrototype::forEach), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("map"), new JSNativeFunction(context, "map", 1, IteratorPrototype::map), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("next"), new JSNativeFunction(context, "next", 0, IteratorPrototype::next), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("reduce"), new JSNativeFunction(context, "reduce", 1, IteratorPrototype::reduce), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("some"), new JSNativeFunction(context, "some", 1, IteratorPrototype::some), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("take"), new JSNativeFunction(context, "take", 1, IteratorPrototype::take), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromString("toArray"), new JSNativeFunction(context, "toArray", 0, IteratorPrototype::toArray), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR), new JSNativeFunction(context, "[Symbol.iterator]", 0, (childContext, thisArg, args) -> thisArg), PropertyDescriptor.DataState.ConfigurableWritable);

        // Iterator.prototype[Symbol.dispose] - calls this.return() per explicit-resource-management spec
        iteratorPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.DISPOSE), new JSNativeFunction(context, "[Symbol.dispose]", 0, (childContext, thisArg, args) -> {
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

        JSNativeFunction iteratorConstructor = new JSNativeFunction(context, JSIterator.NAME, 0, IteratorConstructor::call, true, true);
        iteratorConstructor.defineProperty(
                PropertyKey.PROTOTYPE,
                PropertyDescriptor.dataDescriptor(iteratorPrototype, PropertyDescriptor.DataState.None));
        iteratorConstructor.defineProperty(PropertyKey.fromString("concat"), new JSNativeFunction(context, "concat", 0, IteratorPrototype::concat), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorConstructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction(context, "from", 1, IteratorPrototype::from), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorConstructor.defineProperty(PropertyKey.fromString("zip"), new JSNativeFunction(context, "zip", 1, IteratorPrototype::zip), PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorConstructor.defineProperty(PropertyKey.fromString("zipKeyed"), new JSNativeFunction(context, "zipKeyed", 1, IteratorPrototype::zipKeyed), PropertyDescriptor.DataState.ConfigurableWritable);

        JSNativeFunction constructorAccessor = new JSNativeFunction(context, "constructor", 0, (childContext, thisArg, args) -> {
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

        JSNativeFunction toStringTagGetter = new JSNativeFunction(context, "get [Symbol.toStringTag]",
                0,
                (childContext, thisArg, args) -> new JSString(JSIterator.NAME),
                false);
        JSNativeFunction toStringTagSetter = new JSNativeFunction(context, "set [Symbol.toStringTag]",
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
                        thisObject.set(toStringTagKey, value);
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
        for (String tag : new String[]{"Array Iterator", "Map Iterator", "Set Iterator", "String Iterator"}) {
            JSObject proto = new JSObject(context);
            proto.setPrototype(iteratorPrototype);
            proto.defineProperty(PropertyKey.fromString("next"), new JSNativeFunction(context, "next", 0, IteratorPrototype::next), PropertyDescriptor.DataState.ConfigurableWritable);
            proto.defineProperty(
                    PropertyKey.SYMBOL_TO_STRING_TAG,
                    PropertyDescriptor.dataDescriptor(new JSString(tag), PropertyDescriptor.DataState.Configurable));
            context.registerIteratorPrototype(tag, proto);
        }

        JSObject iteratorHelperPrototype = new JSObject(context);
        iteratorHelperPrototype.setPrototype(iteratorPrototype);
        iteratorHelperPrototype.defineProperty(
                PropertyKey.fromString("next"),
                new JSNativeFunction(context, "next", 0, IteratorPrototype::wrapOrHelperNext),
                PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorHelperPrototype.defineProperty(
                PropertyKey.fromString("return"),
                new JSNativeFunction(context, "return", 0, IteratorPrototype::wrapOrHelperReturn),
                PropertyDescriptor.DataState.ConfigurableWritable);
        iteratorHelperPrototype.defineProperty(
                PropertyKey.SYMBOL_TO_STRING_TAG,
                PropertyDescriptor.dataDescriptor(new JSString("Iterator Helper"), PropertyDescriptor.DataState.Configurable));
        context.registerIteratorPrototype("Iterator Helper", iteratorHelperPrototype);

        JSObject regExpStringIteratorPrototype = new JSObject(context);
        regExpStringIteratorPrototype.setPrototype(iteratorPrototype);
        regExpStringIteratorPrototype.defineProperty(
                PropertyKey.fromString("next"),
                new JSNativeFunction(context, "next", 0, RegExpPrototype::regExpStringIteratorNext),
                PropertyDescriptor.DataState.ConfigurableWritable);
        regExpStringIteratorPrototype.defineProperty(
                PropertyKey.SYMBOL_TO_STRING_TAG,
                PropertyDescriptor.dataDescriptor(new JSString("RegExp String Iterator"), PropertyDescriptor.DataState.Configurable));
        context.registerIteratorPrototype("RegExp String Iterator", regExpStringIteratorPrototype);

        // Create %WrapForValidIteratorPrototype% for Iterator.from wrapper objects
        // This prototype inherits from Iterator.prototype and provides next/return methods
        JSObject wrapForValidIteratorPrototype = new JSObject(context);
        wrapForValidIteratorPrototype.setPrototype(iteratorPrototype);
        wrapForValidIteratorPrototype.defineProperty(
                PropertyKey.fromString("next"),
                new JSNativeFunction(context, "next", 0, IteratorPrototype::wrapOrHelperNext),
                PropertyDescriptor.DataState.ConfigurableWritable);
        wrapForValidIteratorPrototype.defineProperty(
                PropertyKey.fromString("return"),
                new JSNativeFunction(context, "return", 0, IteratorPrototype::wrapOrHelperReturn),
                PropertyDescriptor.DataState.ConfigurableWritable);
        context.registerIteratorPrototype("Iterator Wrap", wrapForValidIteratorPrototype);

        globalObject.defineProperty(PropertyKey.fromString(JSIterator.NAME), iteratorConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize JSON object.
     */
    private void initializeJSONObject() {
        JSObject json = context.createJSObject();
        json.defineProperty(PropertyKey.fromString("parse"),
                new JSNativeFunction(context, "parse", 2, (ignoredContext, thisArg, args) -> jsonObject.parse(thisArg, args)),
                PropertyDescriptor.DataState.ConfigurableWritable);
        json.defineProperty(PropertyKey.fromString("stringify"),
                new JSNativeFunction(context, "stringify", 3, (ignoredContext, thisArg, args) -> jsonObject.stringify(thisArg, args)),
                PropertyDescriptor.DataState.ConfigurableWritable);
        json.defineProperty(PropertyKey.fromString("rawJSON"),
                new JSNativeFunction(context, "rawJSON", 1, (ignoredContext, thisArg, args) -> jsonObject.rawJSON(thisArg, args)),
                PropertyDescriptor.DataState.ConfigurableWritable);
        json.defineProperty(PropertyKey.fromString("isRawJSON"),
                new JSNativeFunction(context, "isRawJSON", 1, (ignoredContext, thisArg, args) -> jsonObject.isRawJSON(thisArg, args)),
                PropertyDescriptor.DataState.ConfigurableWritable);
        json.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("JSON"), PropertyDescriptor.DataState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString("JSON"), json, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Map constructor and prototype methods.
     */
    private void initializeMapConstructor() {
        // Create Map.prototype
        JSObject mapPrototype = context.createJSObject();
        mapPrototype.defineProperty(PropertyKey.fromString("clear"), new JSNativeFunction(context, "clear", 0, MapPrototype::clear), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("delete"), new JSNativeFunction(context, "delete", 1, MapPrototype::delete), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("forEach"), new JSNativeFunction(context, "forEach", 1, MapPrototype::forEach), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("get"), new JSNativeFunction(context, "get", 1, MapPrototype::get), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("getOrInsert"), new JSNativeFunction(context, "getOrInsert", 2, MapPrototype::getOrInsert), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("getOrInsertComputed"), new JSNativeFunction(context, "getOrInsertComputed", 2, MapPrototype::getOrInsertComputed), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("has"), new JSNativeFunction(context, "has", 1, MapPrototype::has), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("set"), new JSNativeFunction(context, "set", 2, MapPrototype::set), PropertyDescriptor.DataState.ConfigurableWritable);
        // Create entries function once and use it for both entries and Symbol.iterator (ES spec requirement)
        JSNativeFunction entriesFunction = new JSNativeFunction(context, "entries", 0, IteratorPrototype::mapEntriesIterator);
        mapPrototype.defineProperty(PropertyKey.fromString("entries"), entriesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("keys"), new JSNativeFunction(context, "keys", 0, IteratorPrototype::mapKeysIterator), PropertyDescriptor.DataState.ConfigurableWritable);
        mapPrototype.defineProperty(PropertyKey.fromString("values"), new JSNativeFunction(context, "values", 0, IteratorPrototype::mapValuesIterator), PropertyDescriptor.DataState.ConfigurableWritable);
        // Map.prototype[Symbol.iterator] is the same function object as entries() (QuickJS uses JS_ALIAS_DEF)
        mapPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR), entriesFunction, PropertyDescriptor.DataState.ConfigurableWritable);

        // Map.prototype.size getter
        mapPrototype.defineProperty(PropertyKey.fromString("size"), new JSNativeFunction(context, "get size", 0, MapPrototype::getSize), PropertyDescriptor.AccessorState.Configurable);
        mapPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSMap.NAME), PropertyDescriptor.DataState.Configurable);

        // Create Map constructor as JSNativeFunction
        JSNativeFunction mapConstructor = new JSNativeFunction(context, JSMap.NAME, 0, MapConstructor::call, true, true);
        mapConstructor.defineProperty(PropertyKey.fromString("prototype"), mapPrototype, PropertyDescriptor.DataState.None);
        mapConstructor.setConstructorType(JSConstructorType.MAP); // Mark as Map constructor
        mapPrototype.defineProperty(PropertyKey.fromString("constructor"), mapConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Map static methods
        mapConstructor.defineProperty(PropertyKey.fromString("groupBy"), new JSNativeFunction(context, "groupBy", 2, MapConstructor::groupBy), PropertyDescriptor.DataState.ConfigurableWritable);
        mapConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction(context, "get [Symbol.species]", 0, MapConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

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
        math.defineProperty(PropertyKey.fromString("abs"), new JSNativeFunction(context, "abs", 1, MathObject::abs), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("acos"), new JSNativeFunction(context, "acos", 1, MathObject::acos), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("acosh"), new JSNativeFunction(context, "acosh", 1, MathObject::acosh), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("asin"), new JSNativeFunction(context, "asin", 1, MathObject::asin), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("asinh"), new JSNativeFunction(context, "asinh", 1, MathObject::asinh), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("atan"), new JSNativeFunction(context, "atan", 1, MathObject::atan), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("atanh"), new JSNativeFunction(context, "atanh", 1, MathObject::atanh), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("atan2"), new JSNativeFunction(context, "atan2", 2, MathObject::atan2), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("cbrt"), new JSNativeFunction(context, "cbrt", 1, MathObject::cbrt), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("ceil"), new JSNativeFunction(context, "ceil", 1, MathObject::ceil), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("clz32"), new JSNativeFunction(context, "clz32", 1, MathObject::clz32), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("cos"), new JSNativeFunction(context, "cos", 1, MathObject::cos), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("cosh"), new JSNativeFunction(context, "cosh", 1, MathObject::cosh), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("exp"), new JSNativeFunction(context, "exp", 1, MathObject::exp), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("expm1"), new JSNativeFunction(context, "expm1", 1, MathObject::expm1), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("f16round"), new JSNativeFunction(context, "f16round", 1, MathObject::f16round), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("floor"), new JSNativeFunction(context, "floor", 1, MathObject::floor), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("fround"), new JSNativeFunction(context, "fround", 1, MathObject::fround), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("hypot"), new JSNativeFunction(context, "hypot", 2, MathObject::hypot), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("imul"), new JSNativeFunction(context, "imul", 2, MathObject::imul), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("log"), new JSNativeFunction(context, "log", 1, MathObject::log), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("log1p"), new JSNativeFunction(context, "log1p", 1, MathObject::log1p), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("log10"), new JSNativeFunction(context, "log10", 1, MathObject::log10), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("log2"), new JSNativeFunction(context, "log2", 1, MathObject::log2), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("max"), new JSNativeFunction(context, "max", 2, MathObject::max), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("min"), new JSNativeFunction(context, "min", 2, MathObject::min), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("pow"), new JSNativeFunction(context, "pow", 2, MathObject::pow), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("random"), new JSNativeFunction(context, "random", 0, MathObject::random), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("round"), new JSNativeFunction(context, "round", 1, MathObject::round), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("sign"), new JSNativeFunction(context, "sign", 1, MathObject::sign), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("sin"), new JSNativeFunction(context, "sin", 1, MathObject::sin), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("sinh"), new JSNativeFunction(context, "sinh", 1, MathObject::sinh), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("sqrt"), new JSNativeFunction(context, "sqrt", 1, MathObject::sqrt), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("sumPrecise"), new JSNativeFunction(context, "sumPrecise", 1, MathObject::sumPrecise), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("tan"), new JSNativeFunction(context, "tan", 1, MathObject::tan), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("tanh"), new JSNativeFunction(context, "tanh", 1, MathObject::tanh), PropertyDescriptor.DataState.ConfigurableWritable);
        math.defineProperty(PropertyKey.fromString("trunc"), new JSNativeFunction(context, "trunc", 1, MathObject::trunc), PropertyDescriptor.DataState.ConfigurableWritable);
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
        JSNumberObject numberPrototype = new JSNumberObject(context, 0.0);
        context.transferPrototype(numberPrototype, JSObject.NAME);
        numberPrototype.defineProperty(PropertyKey.fromString("toExponential"), new JSNativeFunction(context, "toExponential", 1, NumberPrototype::toExponential), PropertyDescriptor.DataState.ConfigurableWritable);
        numberPrototype.defineProperty(PropertyKey.fromString("toFixed"), new JSNativeFunction(context, "toFixed", 1, NumberPrototype::toFixed), PropertyDescriptor.DataState.ConfigurableWritable);
        numberPrototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, NumberPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        numberPrototype.defineProperty(PropertyKey.fromString("toPrecision"), new JSNativeFunction(context, "toPrecision", 1, NumberPrototype::toPrecision), PropertyDescriptor.DataState.ConfigurableWritable);
        numberPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 1, NumberPrototype::toString), PropertyDescriptor.DataState.ConfigurableWritable);
        numberPrototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, NumberPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);

        // Create Number constructor
        JSNativeFunction numberConstructor = new JSNativeFunction(context, JSNumberObject.NAME, 1, NumberConstructor::call, true);
        numberConstructor.defineProperty(PropertyKey.fromString("prototype"), numberPrototype, PropertyDescriptor.DataState.None);
        numberConstructor.setConstructorType(JSConstructorType.NUMBER_OBJECT); // Mark as Number constructor
        numberPrototype.defineProperty(PropertyKey.fromString("constructor"), numberConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Number static methods
        numberConstructor.defineProperty(PropertyKey.fromString("isFinite"), new JSNativeFunction(context, "isFinite", 1, NumberPrototype::isFinite), PropertyDescriptor.DataState.ConfigurableWritable);
        numberConstructor.defineProperty(PropertyKey.fromString("isInteger"), new JSNativeFunction(context, "isInteger", 1, NumberPrototype::isInteger), PropertyDescriptor.DataState.ConfigurableWritable);
        numberConstructor.defineProperty(PropertyKey.fromString("isNaN"), new JSNativeFunction(context, "isNaN", 1, NumberPrototype::isNaN), PropertyDescriptor.DataState.ConfigurableWritable);
        numberConstructor.defineProperty(PropertyKey.fromString("isSafeInteger"), new JSNativeFunction(context, "isSafeInteger", 1, NumberPrototype::isSafeInteger), PropertyDescriptor.DataState.ConfigurableWritable);

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
        objectPrototype.defineProperty(PropertyKey.fromString("__defineGetter__"), new JSNativeFunction(context, "__defineGetter__", 2, ObjectPrototype::__defineGetter__), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("__defineSetter__"), new JSNativeFunction(context, "__defineSetter__", 2, ObjectPrototype::__defineSetter__), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("__lookupGetter__"), new JSNativeFunction(context, "__lookupGetter__", 1, ObjectPrototype::__lookupGetter__), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("__lookupSetter__"), new JSNativeFunction(context, "__lookupSetter__", 1, ObjectPrototype::__lookupSetter__), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("hasOwnProperty"), new JSNativeFunction(context, "hasOwnProperty", 1, ObjectPrototype::hasOwnProperty), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("isPrototypeOf"), new JSNativeFunction(context, "isPrototypeOf", 1, ObjectPrototype::isPrototypeOf), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("propertyIsEnumerable"), new JSNativeFunction(context, "propertyIsEnumerable", 1, ObjectPrototype::propertyIsEnumerable), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, ObjectPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, ObjectPrototype::toString), PropertyDescriptor.DataState.ConfigurableWritable);
        objectPrototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, ObjectPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);

        // Define __proto__ as an accessor property
        PropertyDescriptor protoDesc = PropertyDescriptor.accessorDescriptor(
                new JSNativeFunction(context, "get __proto__", 0, ObjectPrototype::__proto__Getter),
                new JSNativeFunction(context, "set __proto__", 1, ObjectPrototype::__proto__Setter),
                PropertyDescriptor.AccessorState.Configurable
        );
        objectPrototype.defineProperty(PropertyKey.PROTO, protoDesc);

        // Object.prototype is an immutable prototype exotic object per ES2024 9.4.7
        objectPrototype.setImmutablePrototype();

        // Create Object constructor
        JSNativeFunction objectConstructor = new JSNativeFunction(context, JSObject.NAME, 1, ObjectConstructor::call, true);
        objectConstructor.defineProperty(PropertyKey.fromString("prototype"), objectPrototype, PropertyDescriptor.DataState.None);
        objectPrototype.defineProperty(PropertyKey.fromString("constructor"), objectConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Object static methods
        objectConstructor.defineProperty(PropertyKey.fromString("assign"), new JSNativeFunction(context, "assign", 2, ObjectConstructor::assign), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("create"), new JSNativeFunction(context, "create", 2, ObjectConstructor::create), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("defineProperties"), new JSNativeFunction(context, "defineProperties", 2, ObjectConstructor::defineProperties), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("defineProperty"), new JSNativeFunction(context, "defineProperty", 3, ObjectPrototype::defineProperty), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("entries"), new JSNativeFunction(context, "entries", 1, ObjectConstructor::entries), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("freeze"), new JSNativeFunction(context, "freeze", 1, ObjectConstructor::freeze), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("fromEntries"), new JSNativeFunction(context, "fromEntries", 1, ObjectConstructor::fromEntries), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("getOwnPropertyDescriptor"), new JSNativeFunction(context, "getOwnPropertyDescriptor", 2, ObjectConstructor::getOwnPropertyDescriptor), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("getOwnPropertyDescriptors"), new JSNativeFunction(context, "getOwnPropertyDescriptors", 1, ObjectConstructor::getOwnPropertyDescriptors), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("getOwnPropertyNames"), new JSNativeFunction(context, "getOwnPropertyNames", 1, ObjectConstructor::getOwnPropertyNames), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("getOwnPropertySymbols"), new JSNativeFunction(context, "getOwnPropertySymbols", 1, ObjectConstructor::getOwnPropertySymbols), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("getPrototypeOf"), new JSNativeFunction(context, "getPrototypeOf", 1, ObjectConstructor::getPrototypeOf), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("groupBy"), new JSNativeFunction(context, "groupBy", 2, ObjectConstructor::groupBy), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("hasOwn"), new JSNativeFunction(context, "hasOwn", 2, ObjectConstructor::hasOwn), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("is"), new JSNativeFunction(context, "is", 2, ObjectConstructor::is), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("isExtensible"), new JSNativeFunction(context, "isExtensible", 1, ObjectConstructor::isExtensible), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("isFrozen"), new JSNativeFunction(context, "isFrozen", 1, ObjectConstructor::isFrozen), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("isSealed"), new JSNativeFunction(context, "isSealed", 1, ObjectConstructor::isSealed), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("keys"), new JSNativeFunction(context, "keys", 1, ObjectConstructor::keys), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("preventExtensions"), new JSNativeFunction(context, "preventExtensions", 1, ObjectConstructor::preventExtensions), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("seal"), new JSNativeFunction(context, "seal", 1, ObjectConstructor::seal), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("setPrototypeOf"), new JSNativeFunction(context, "setPrototypeOf", 2, ObjectConstructor::setPrototypeOf), PropertyDescriptor.DataState.ConfigurableWritable);
        objectConstructor.defineProperty(PropertyKey.fromString("values"), new JSNativeFunction(context, "values", 1, ObjectConstructor::values), PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSObject.NAME), objectConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Promise constructor and prototype methods.
     */
    private void initializePromiseConstructor() {
        // Create Promise.prototype
        JSObject promisePrototype = context.createJSObject();
        promisePrototype.defineProperty(PropertyKey.fromString("catch"), new JSNativeFunction(context, "catch", 1, PromisePrototype::catchMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        promisePrototype.defineProperty(PropertyKey.fromString("finally"), new JSNativeFunction(context, "finally", 1, PromisePrototype::finallyMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        promisePrototype.defineProperty(PropertyKey.fromString("then"), new JSNativeFunction(context, "then", 2, PromisePrototype::then), PropertyDescriptor.DataState.ConfigurableWritable);
        promisePrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSPromise.NAME), PropertyDescriptor.DataState.Configurable);

        // Create Promise constructor as JSNativeFunction
        JSNativeFunction promiseConstructor = new JSNativeFunction(context, JSPromise.NAME, 1, PromiseConstructor::call, true, true);
        context.transferPrototype(promiseConstructor, JSFunction.NAME);
        promiseConstructor.defineProperty(PropertyKey.fromString("prototype"), promisePrototype, PropertyDescriptor.DataState.None);
        promiseConstructor.setConstructorType(JSConstructorType.PROMISE); // Mark as Promise constructor
        promisePrototype.defineProperty(PropertyKey.fromString("constructor"), promiseConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Static methods
        promiseConstructor.defineProperty(PropertyKey.fromString("all"), new JSNativeFunction(context, "all", 1, PromiseConstructor::all), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("allKeyed"), new JSNativeFunction(context, "allKeyed", 1, PromiseConstructor::allKeyed), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("allSettled"), new JSNativeFunction(context, "allSettled", 1, PromiseConstructor::allSettled), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("allSettledKeyed"), new JSNativeFunction(context, "allSettledKeyed", 1, PromiseConstructor::allSettledKeyed), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("any"), new JSNativeFunction(context, "any", 1, PromiseConstructor::any), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("race"), new JSNativeFunction(context, "race", 1, PromiseConstructor::race), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("reject"), new JSNativeFunction(context, "reject", 1, PromiseConstructor::reject), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("resolve"), new JSNativeFunction(context, "resolve", 1, PromiseConstructor::resolve), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("try"), new JSNativeFunction(context, "try", 1, PromiseConstructor::tryMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromString("withResolvers"), new JSNativeFunction(context, "withResolvers", 0, PromiseConstructor::withResolvers), PropertyDescriptor.DataState.ConfigurableWritable);
        promiseConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction(context, "get [Symbol.species]", 0, PromiseConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString(JSPromise.NAME), promiseConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Proxy constructor.
     */
    private void initializeProxyConstructor() {
        // Create Proxy constructor as JSNativeFunction
        // Proxy requires 'new' and takes 2 arguments (target, handler)
        JSNativeFunction proxyConstructor = new JSNativeFunction(context, JSProxy.NAME, 2, ProxyConstructor::call, true, true);
        proxyConstructor.setConstructorType(JSConstructorType.PROXY);
        context.transferPrototype(proxyConstructor, JSFunction.NAME);

        // Proxy has no "prototype" own property per QuickJS / spec.
        proxyConstructor.delete(PropertyKey.PROTOTYPE);

        // Add static methods.
        proxyConstructor.defineProperty(PropertyKey.fromString("revocable"), new JSNativeFunction(context, "revocable", 2, ProxyConstructor::revocable), PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSProxy.NAME), proxyConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Reflect object.
     */
    private void initializeReflectObject() {
        JSObject reflect = context.createJSObject();
        reflect.defineProperty(PropertyKey.fromString("apply"), new JSNativeFunction(context, "apply", 3, JSReflectObject::apply), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("construct"), new JSNativeFunction(context, "construct", 2, JSReflectObject::construct), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("defineProperty"), new JSNativeFunction(context, "defineProperty", 3, JSReflectObject::defineProperty), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("deleteProperty"), new JSNativeFunction(context, "deleteProperty", 2, JSReflectObject::deleteProperty), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("get"), new JSNativeFunction(context, "get", 2, JSReflectObject::get), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("getOwnPropertyDescriptor"), new JSNativeFunction(context, "getOwnPropertyDescriptor", 2, JSReflectObject::getOwnPropertyDescriptor), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("getPrototypeOf"), new JSNativeFunction(context, "getPrototypeOf", 1, JSReflectObject::getPrototypeOf), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("has"), new JSNativeFunction(context, "has", 2, JSReflectObject::has), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("isExtensible"), new JSNativeFunction(context, "isExtensible", 1, JSReflectObject::isExtensible), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("ownKeys"), new JSNativeFunction(context, "ownKeys", 1, JSReflectObject::ownKeys), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("preventExtensions"), new JSNativeFunction(context, "preventExtensions", 1, JSReflectObject::preventExtensions), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("set"), new JSNativeFunction(context, "set", 3, JSReflectObject::set), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromString("setPrototypeOf"), new JSNativeFunction(context, "setPrototypeOf", 2, JSReflectObject::setPrototypeOf), PropertyDescriptor.DataState.ConfigurableWritable);
        reflect.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Reflect"), PropertyDescriptor.DataState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString("Reflect"), reflect, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize RegExp constructor and prototype.
     */
    private void initializeRegExpConstructor() {
        // Create RegExp.prototype
        JSObject regexpPrototype = context.createJSObject();
        regexpPrototype.defineProperty(PropertyKey.fromString("test"), new JSNativeFunction(context, "test", 1, RegExpPrototype::test), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.EXEC, new JSNativeFunction(context, "exec", 1, RegExpPrototype::exec), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromString("compile"), new JSNativeFunction(context, "compile", 2, RegExpPrototype::compile), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, RegExpPrototype::toStringMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPLIT), new JSNativeFunction(context, "[Symbol.split]", 2, RegExpPrototype::symbolSplit), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.MATCH), new JSNativeFunction(context, "[Symbol.match]", 1, RegExpPrototype::symbolMatch), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.MATCH_ALL), new JSNativeFunction(context, "[Symbol.matchAll]", 1, RegExpPrototype::symbolMatchAll), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.REPLACE), new JSNativeFunction(context, "[Symbol.replace]", 2, RegExpPrototype::symbolReplace), PropertyDescriptor.DataState.ConfigurableWritable);
        regexpPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.SEARCH), new JSNativeFunction(context, "[Symbol.search]", 1, RegExpPrototype::symbolSearch), PropertyDescriptor.DataState.ConfigurableWritable);

        // Accessor properties
        regexpPrototype.defineProperty(PropertyKey.fromString("dotAll"), new JSNativeFunction(context, "get dotAll", 0, RegExpPrototype::getDotAllAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("flags"), new JSNativeFunction(context, "get flags", 0, RegExpPrototype::getFlagsAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("global"), new JSNativeFunction(context, "get global", 0, RegExpPrototype::getGlobalAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("hasIndices"), new JSNativeFunction(context, "get hasIndices", 0, RegExpPrototype::getHasIndicesAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("ignoreCase"), new JSNativeFunction(context, "get ignoreCase", 0, RegExpPrototype::getIgnoreCaseAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("multiline"), new JSNativeFunction(context, "get multiline", 0, RegExpPrototype::getMultilineAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("source"), new JSNativeFunction(context, "get source", 0, RegExpPrototype::getSourceAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("sticky"), new JSNativeFunction(context, "get sticky", 0, RegExpPrototype::getStickyAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("unicode"), new JSNativeFunction(context, "get unicode", 0, RegExpPrototype::getUnicodeAccessor), PropertyDescriptor.AccessorState.Configurable);
        regexpPrototype.defineProperty(PropertyKey.fromString("unicodeSets"), new JSNativeFunction(context, "get unicodeSets", 0, RegExpPrototype::getUnicodeSetsAccessor), PropertyDescriptor.AccessorState.Configurable);

        // Create RegExp constructor as a function
        JSNativeFunction regexpConstructor = new JSNativeFunction(context, JSRegExp.NAME, 2, RegExpConstructor::call, true);
        regexpConstructor.defineProperty(PropertyKey.fromString("prototype"), regexpPrototype, PropertyDescriptor.DataState.None);
        regexpConstructor.setConstructorType(JSConstructorType.REGEXP);
        regexpPrototype.defineProperty(PropertyKey.fromString("constructor"), regexpConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // AnnexB legacy RegExp static accessor properties.
        // Each getter validates SameValue(C, thisValue) per GetLegacyRegExpStaticProperty.
        for (String legacyGetterName : new String[]{"$&", "$'", "$+", "$`", "lastMatch", "lastParen", "leftContext", "rightContext"}) {
            JSNativeFunction legacyGetter = new JSNativeFunction(context, "get " + legacyGetterName, 0, (ctx, thisArg, args) -> {
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
            regexpConstructor.defineProperty(PropertyKey.fromString(legacyGetterName), legacyGetter, PropertyDescriptor.AccessorState.Configurable);
        }
        for (String legacyGetterSetterName : new String[]{"$_", "input"}) {
            JSNativeFunction legacyGSGetter = new JSNativeFunction(context, "get " + legacyGetterSetterName, 0, (ctx, thisArg, args) -> {
                if (thisArg != regexpConstructor) {
                    return ctx.throwTypeError("Generic static accessor property access is not supported");
                }
                return new JSString(ctx.getRegExpLegacyInput());
            }, false);
            JSNativeFunction legacyGSSetter = new JSNativeFunction(context, "set " + legacyGetterSetterName, 1, (ctx, thisArg, args) -> {
                if (thisArg != regexpConstructor) {
                    return ctx.throwTypeError("Generic static accessor property access is not supported");
                }
                JSValue inputArgumentValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
                JSString inputString = JSTypeConversions.toString(ctx, inputArgumentValue);
                if (ctx.hasPendingException()) {
                    return JSUndefined.INSTANCE;
                }
                ctx.setRegExpLegacyInput(inputString.value());
                return JSUndefined.INSTANCE;
            }, false);
            regexpConstructor.defineProperty(PropertyKey.fromString(legacyGetterSetterName), legacyGSGetter, legacyGSSetter, PropertyDescriptor.AccessorState.Configurable);
        }
        // $1..$9
        for (int i = 1; i <= 9; i++) {
            final String dollarName = "$" + i;
            final int captureIndex = i;
            JSNativeFunction dollarGetter = new JSNativeFunction(context, "get " + dollarName, 0, (ctx, thisArg, args) -> {
                if (thisArg != regexpConstructor) {
                    return ctx.throwTypeError("Generic static accessor property access is not supported");
                }
                return new JSString(ctx.getRegExpLegacyCapture(captureIndex));
            }, false);
            regexpConstructor.defineProperty(PropertyKey.fromString(dollarName), dollarGetter, PropertyDescriptor.AccessorState.Configurable);
        }

        // Symbol.species getter - ES2024 22.2.4.2 get RegExp[@@species]
        regexpConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction(context, "get [Symbol.species]", 0, RegExpConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);
        // ES2024 RegExp.escape static method
        regexpConstructor.defineProperty(PropertyKey.fromString("escape"), new JSNativeFunction(context, "escape", 1, RegExpConstructor::escape), PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSRegExp.NAME), regexpConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize Set constructor and prototype methods.
     */
    private void initializeSetConstructor() {
        // Create Set.prototype
        JSObject setPrototype = context.createJSObject();
        setPrototype.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction(context, "add", 1, SetPrototype::add), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("clear"), new JSNativeFunction(context, "clear", 0, SetPrototype::clear), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("delete"), new JSNativeFunction(context, "delete", 1, SetPrototype::delete), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("difference"), new JSNativeFunction(context, "difference", 1, SetPrototype::difference), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("entries"), new JSNativeFunction(context, "entries", 0, SetPrototype::entries), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("forEach"), new JSNativeFunction(context, "forEach", 1, SetPrototype::forEach), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("has"), new JSNativeFunction(context, "has", 1, SetPrototype::has), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("intersection"), new JSNativeFunction(context, "intersection", 1, SetPrototype::intersection), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("isDisjointFrom"), new JSNativeFunction(context, "isDisjointFrom", 1, SetPrototype::isDisjointFrom), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("isSubsetOf"), new JSNativeFunction(context, "isSubsetOf", 1, SetPrototype::isSubsetOf), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("isSupersetOf"), new JSNativeFunction(context, "isSupersetOf", 1, SetPrototype::isSupersetOf), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("symmetricDifference"), new JSNativeFunction(context, "symmetricDifference", 1, SetPrototype::symmetricDifference), PropertyDescriptor.DataState.ConfigurableWritable);
        setPrototype.defineProperty(PropertyKey.fromString("union"), new JSNativeFunction(context, "union", 1, SetPrototype::union), PropertyDescriptor.DataState.ConfigurableWritable);

        // Create values function - keys and Symbol.iterator will alias to this
        JSNativeFunction valuesFunction = new JSNativeFunction(context, "values", 0, SetPrototype::values);
        setPrototype.defineProperty(PropertyKey.fromString("values"), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        // Set.prototype.keys is the same function object as values (ES spec requirement)
        setPrototype.defineProperty(PropertyKey.fromString("keys"), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        // Set.prototype[Symbol.iterator] is the same as values()
        setPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);

        // Set.prototype.size getter
        setPrototype.defineProperty(PropertyKey.fromString("size"), new JSNativeFunction(context, "get size", 0, SetPrototype::getSize), PropertyDescriptor.AccessorState.Configurable);
        setPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSSet.NAME), PropertyDescriptor.DataState.Configurable);

        // Create Set constructor as a function
        JSNativeFunction setConstructor = new JSNativeFunction(context, JSSet.NAME, 0, SetConstructor::call, true, true);
        setConstructor.defineProperty(PropertyKey.fromString("prototype"), setPrototype, PropertyDescriptor.DataState.None);
        setConstructor.setConstructorType(JSConstructorType.SET);
        setPrototype.defineProperty(PropertyKey.fromString("constructor"), setConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        setConstructor.defineProperty(PropertyKey.fromString("groupBy"), new JSNativeFunction(context, "groupBy", 2, SetConstructor::groupBy), PropertyDescriptor.DataState.ConfigurableWritable);
        setConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction(context, "get [Symbol.species]", 0, SetConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

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
                new JSNativeFunction(context, "evaluate", 1, ShadowRealmPrototype::evaluate),
                PropertyDescriptor.DataState.ConfigurableWritable);
        shadowRealmPrototype.defineProperty(
                PropertyKey.fromString("importValue"),
                new JSNativeFunction(context, "importValue", 2, ShadowRealmPrototype::importValue),
                PropertyDescriptor.DataState.ConfigurableWritable);
        shadowRealmPrototype.defineProperty(
                PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                new JSString(JSShadowRealm.NAME),
                PropertyDescriptor.DataState.Configurable);

        JSNativeFunction shadowRealmConstructor = new JSNativeFunction(context, JSShadowRealm.NAME,
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
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("grow"), new JSNativeFunction(context, "grow", 1, SharedArrayBufferPrototype::grow), PropertyDescriptor.DataState.ConfigurableWritable);
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("slice"), new JSNativeFunction(context, "slice", 2, SharedArrayBufferPrototype::slice), PropertyDescriptor.DataState.ConfigurableWritable);

        // Define getter properties
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("byteLength"), new JSNativeFunction(context, "get byteLength", 0, SharedArrayBufferPrototype::getByteLength), PropertyDescriptor.AccessorState.Configurable);
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("growable"), new JSNativeFunction(context, "get growable", 0, SharedArrayBufferPrototype::getGrowable), PropertyDescriptor.AccessorState.Configurable);
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("maxByteLength"), new JSNativeFunction(context, "get maxByteLength", 0, SharedArrayBufferPrototype::getMaxByteLength), PropertyDescriptor.AccessorState.Configurable);
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSSharedArrayBuffer.NAME), PropertyDescriptor.DataState.Configurable);

        // Create SharedArrayBuffer constructor as a function
        JSNativeFunction sharedArrayBufferConstructor = new JSNativeFunction(context, JSSharedArrayBuffer.NAME,
                1,
                SharedArrayBufferConstructor::call,
                true,  // isConstructor
                true   // requiresNew - SharedArrayBuffer() must be called with new
        );
        sharedArrayBufferConstructor.defineProperty(PropertyKey.fromString("prototype"), sharedArrayBufferPrototype, PropertyDescriptor.DataState.None);
        sharedArrayBufferConstructor.setConstructorType(JSConstructorType.SHARED_ARRAY_BUFFER);
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("constructor"), sharedArrayBufferConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
        sharedArrayBufferConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction(context, "get [Symbol.species]", 0, SharedArrayBufferConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

        globalObject.defineProperty(PropertyKey.fromString(JSSharedArrayBuffer.NAME), sharedArrayBufferConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize String constructor and prototype.
     */
    private void initializeStringConstructor() {
        // Create String.prototype - per ES spec, it is itself a String object whose value is ""
        JSObject stringPrototype = new JSStringObject(context);
        context.transferPrototype(stringPrototype, JSObject.NAME);
        stringPrototype.defineProperty(PropertyKey.fromString("at"), new JSNativeFunction(context, "at", 1, StringPrototype::at), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("charAt"), new JSNativeFunction(context, "charAt", 1, StringPrototype::charAt), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("charCodeAt"), new JSNativeFunction(context, "charCodeAt", 1, StringPrototype::charCodeAt), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("codePointAt"), new JSNativeFunction(context, "codePointAt", 1, StringPrototype::codePointAt), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("concat"), new JSNativeFunction(context, "concat", 1, StringPrototype::concat), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("endsWith"), new JSNativeFunction(context, "endsWith", 1, StringPrototype::endsWith), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("includes"), new JSNativeFunction(context, "includes", 1, StringPrototype::includes), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("indexOf"), new JSNativeFunction(context, "indexOf", 1, StringPrototype::indexOf), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("lastIndexOf"), new JSNativeFunction(context, "lastIndexOf", 1, StringPrototype::lastIndexOf), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("localeCompare"), new JSNativeFunction(context, "localeCompare", 1, StringPrototype::localeCompare), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("match"), new JSNativeFunction(context, "match", 1, StringPrototype::match), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("matchAll"), new JSNativeFunction(context, "matchAll", 1, StringPrototype::matchAll), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("padEnd"), new JSNativeFunction(context, "padEnd", 1, StringPrototype::padEnd), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("padStart"), new JSNativeFunction(context, "padStart", 1, StringPrototype::padStart), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("repeat"), new JSNativeFunction(context, "repeat", 1, StringPrototype::repeat), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("replace"), new JSNativeFunction(context, "replace", 2, StringPrototype::replace), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("replaceAll"), new JSNativeFunction(context, "replaceAll", 2, StringPrototype::replaceAll), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("search"), new JSNativeFunction(context, "search", 1, StringPrototype::search), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("slice"), new JSNativeFunction(context, "slice", 2, StringPrototype::slice), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("split"), new JSNativeFunction(context, "split", 2, StringPrototype::split), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("startsWith"), new JSNativeFunction(context, "startsWith", 1, StringPrototype::startsWith), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("substr"), new JSNativeFunction(context, "substr", 2, StringPrototype::substr), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("substring"), new JSNativeFunction(context, "substring", 2, StringPrototype::substring), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("toLowerCase"), new JSNativeFunction(context, "toLowerCase", 0, StringPrototype::toLowerCase), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, StringPrototype::toString_), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("toUpperCase"), new JSNativeFunction(context, "toUpperCase", 0, StringPrototype::toUpperCase), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("trim"), new JSNativeFunction(context, "trim", 0, StringPrototype::trim), PropertyDescriptor.DataState.ConfigurableWritable);
        JSNativeFunction trimEnd = new JSNativeFunction(context, "trimEnd", 0, StringPrototype::trimEnd);
        JSNativeFunction trimStart = new JSNativeFunction(context, "trimStart", 0, StringPrototype::trimStart);
        stringPrototype.defineProperty(PropertyKey.fromString("trimEnd"), trimEnd, PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("trimRight"), trimEnd, PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("trimStart"), trimStart, PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("trimLeft"), trimStart, PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, StringPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);

        // HTML wrapper methods (deprecated but still part of spec)
        stringPrototype.defineProperty(PropertyKey.fromString("anchor"), new JSNativeFunction(context, "anchor", 1, StringPrototype::anchor), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("big"), new JSNativeFunction(context, "big", 0, StringPrototype::big), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("blink"), new JSNativeFunction(context, "blink", 0, StringPrototype::blink), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("bold"), new JSNativeFunction(context, "bold", 0, StringPrototype::bold), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("fixed"), new JSNativeFunction(context, "fixed", 0, StringPrototype::fixed), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("fontcolor"), new JSNativeFunction(context, "fontcolor", 1, StringPrototype::fontcolor), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("fontsize"), new JSNativeFunction(context, "fontsize", 1, StringPrototype::fontsize), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("italics"), new JSNativeFunction(context, "italics", 0, StringPrototype::italics), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("link"), new JSNativeFunction(context, "link", 1, StringPrototype::link), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("small"), new JSNativeFunction(context, "small", 0, StringPrototype::small), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("strike"), new JSNativeFunction(context, "strike", 0, StringPrototype::strike), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("sub"), new JSNativeFunction(context, "sub", 0, StringPrototype::sub), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("sup"), new JSNativeFunction(context, "sup", 0, StringPrototype::sup), PropertyDescriptor.DataState.ConfigurableWritable);

        // Unicode methods
        stringPrototype.defineProperty(PropertyKey.fromString("isWellFormed"), new JSNativeFunction(context, "isWellFormed", 0, StringPrototype::isWellFormed), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("normalize"), new JSNativeFunction(context, "normalize", 0, StringPrototype::normalize), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("toLocaleLowerCase"), new JSNativeFunction(context, "toLocaleLowerCase", 0, StringPrototype::toLocaleLowerCase), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("toLocaleUpperCase"), new JSNativeFunction(context, "toLocaleUpperCase", 0, StringPrototype::toLocaleUpperCase), PropertyDescriptor.DataState.ConfigurableWritable);
        stringPrototype.defineProperty(PropertyKey.fromString("toWellFormed"), new JSNativeFunction(context, "toWellFormed", 0, StringPrototype::toWellFormed), PropertyDescriptor.DataState.ConfigurableWritable);

        // String.prototype[Symbol.iterator]
        stringPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR),
                new JSNativeFunction(context, "[Symbol.iterator]", 0, IteratorPrototype::stringIterator), PropertyDescriptor.DataState.ConfigurableWritable);

        // String.prototype.length is a data property with value 0 (not writable, not enumerable, not configurable)
        stringPrototype.defineProperty(PropertyKey.fromString("length"), JSNumber.of(0), PropertyDescriptor.DataState.None);

        // Create String constructor
        JSNativeFunction stringConstructor = new JSNativeFunction(context, JSString.NAME, 1, StringConstructor::call, true);
        stringConstructor.defineProperty(PropertyKey.fromString("prototype"), stringPrototype, PropertyDescriptor.DataState.None);
        stringConstructor.setConstructorType(JSConstructorType.STRING_OBJECT); // Mark as String constructor
        stringPrototype.defineProperty(PropertyKey.fromString("constructor"), stringConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Add static methods
        stringConstructor.defineProperty(PropertyKey.fromString("fromCharCode"), new JSNativeFunction(context, "fromCharCode", 1, StringConstructor::fromCharCode), PropertyDescriptor.DataState.ConfigurableWritable);
        stringConstructor.defineProperty(PropertyKey.fromString("fromCodePoint"), new JSNativeFunction(context, "fromCodePoint", 1, StringConstructor::fromCodePoint), PropertyDescriptor.DataState.ConfigurableWritable);
        stringConstructor.defineProperty(PropertyKey.fromString("raw"), new JSNativeFunction(context, "raw", 1, StringConstructor::raw), PropertyDescriptor.DataState.ConfigurableWritable);

        globalObject.defineProperty(PropertyKey.fromString(JSString.NAME), stringConstructor, PropertyDescriptor.DataState.ConfigurableWritable);
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
        symbolPrototype.defineProperty(PropertyKey.fromString("toString"), symbolToString, PropertyDescriptor.DataState.ConfigurableWritable);

        JSNativeFunction symbolValueOf = new JSNativeFunction(context, "valueOf", 0, SymbolPrototype::valueOf);
        symbolValueOf.initializePrototypeChain(context);
        symbolPrototype.defineProperty(PropertyKey.fromString("valueOf"), symbolValueOf, PropertyDescriptor.DataState.ConfigurableWritable);

        // Symbol.prototype.description is a getter
        symbolPrototype.defineProperty(PropertyKey.fromString("description"), new JSNativeFunction(context, "get description", 0, SymbolPrototype::getDescription), PropertyDescriptor.AccessorState.Configurable);

        JSNativeFunction symbolToPrimitive = new JSNativeFunction(context, "[Symbol.toPrimitive]", 1, SymbolPrototype::toPrimitive);
        symbolToPrimitive.initializePrototypeChain(context);
        symbolPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_PRIMITIVE), symbolToPrimitive, PropertyDescriptor.DataState.Configurable);

        symbolPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSSymbol.NAME), PropertyDescriptor.DataState.Configurable);

        // Create Symbol constructor
        // Note: Symbol cannot be called with 'new' in JavaScript (throws TypeError)
        // Symbol objects are created using Object(symbolValue) for use with Proxy
        JSNativeFunction symbolConstructor = new JSNativeFunction(context, JSSymbol.NAME, 0, SymbolConstructor::call, true);
        symbolConstructor.defineProperty(PropertyKey.fromString("prototype"), symbolPrototype, PropertyDescriptor.DataState.None);
        symbolConstructor.setConstructorType(JSConstructorType.SYMBOL_OBJECT); // Mark as Symbol constructor
        symbolPrototype.defineProperty(PropertyKey.fromString("constructor"), symbolConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Symbol static methods
        symbolConstructor.defineProperty(PropertyKey.fromString("for"), new JSNativeFunction(context, "for", 1, SymbolConstructor::symbolFor), PropertyDescriptor.DataState.ConfigurableWritable);
        symbolConstructor.defineProperty(PropertyKey.fromString("keyFor"), new JSNativeFunction(context, "keyFor", 1, SymbolConstructor::keyFor), PropertyDescriptor.DataState.ConfigurableWritable);

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

    private void initializeTemporalDuration(JSObject temporalObject) {
        JSObject prototype = context.createJSObject();
        prototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Temporal.Duration"), PropertyDescriptor.DataState.Configurable);

        // Getters
        defineTemporalGetter(prototype, "years", TemporalDurationPrototype::years);
        defineTemporalGetter(prototype, "months", TemporalDurationPrototype::months);
        defineTemporalGetter(prototype, "weeks", TemporalDurationPrototype::weeks);
        defineTemporalGetter(prototype, "days", TemporalDurationPrototype::days);
        defineTemporalGetter(prototype, "hours", TemporalDurationPrototype::hours);
        defineTemporalGetter(prototype, "minutes", TemporalDurationPrototype::minutes);
        defineTemporalGetter(prototype, "seconds", TemporalDurationPrototype::seconds);
        defineTemporalGetter(prototype, "milliseconds", TemporalDurationPrototype::milliseconds);
        defineTemporalGetter(prototype, "microseconds", TemporalDurationPrototype::microseconds);
        defineTemporalGetter(prototype, "nanoseconds", TemporalDurationPrototype::nanoseconds);
        defineTemporalGetter(prototype, "sign", TemporalDurationPrototype::sign);
        defineTemporalGetter(prototype, "blank", TemporalDurationPrototype::blank);

        // Methods
        prototype.defineProperty(PropertyKey.fromString("with"), new JSNativeFunction(context, "with", 1, TemporalDurationPrototype::with), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("negated"), new JSNativeFunction(context, "negated", 0, TemporalDurationPrototype::negated), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("abs"), new JSNativeFunction(context, "abs", 0, TemporalDurationPrototype::abs), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction(context, "add", 1, TemporalDurationPrototype::add), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("subtract"), new JSNativeFunction(context, "subtract", 1, TemporalDurationPrototype::subtract), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("round"), new JSNativeFunction(context, "round", 1, TemporalDurationPrototype::round), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("total"), new JSNativeFunction(context, "total", 1, TemporalDurationPrototype::total), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, TemporalDurationPrototype::toStringMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toJSON"), new JSNativeFunction(context, "toJSON", 0, TemporalDurationPrototype::toJSON), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, TemporalDurationPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, TemporalDurationPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "Duration", 0, TemporalDurationConstructor::construct, true, false);
        constructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction(context, "from", 1, TemporalDurationConstructor::from), PropertyDescriptor.DataState.ConfigurableWritable);
        constructor.defineProperty(PropertyKey.fromString("compare"), new JSNativeFunction(context, "compare", 2, TemporalDurationConstructor::compare), PropertyDescriptor.DataState.ConfigurableWritable);

        // Link constructor <-> prototype
        constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor, PropertyDescriptor.DataState.ConfigurableWritable);

        temporalObject.defineProperty(PropertyKey.fromString("Duration"), constructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    private void initializeTemporalInstant(JSObject temporalObject) {
        JSObject prototype = context.createJSObject();
        prototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Temporal.Instant"), PropertyDescriptor.DataState.Configurable);

        // Getters
        defineTemporalGetter(prototype, "epochMilliseconds", TemporalInstantPrototype::epochMilliseconds);
        defineTemporalGetter(prototype, "epochNanoseconds", TemporalInstantPrototype::epochNanoseconds);

        // Methods
        prototype.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction(context, "add", 1, TemporalInstantPrototype::add), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("subtract"), new JSNativeFunction(context, "subtract", 1, TemporalInstantPrototype::subtract), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("until"), new JSNativeFunction(context, "until", 1, TemporalInstantPrototype::until), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("since"), new JSNativeFunction(context, "since", 1, TemporalInstantPrototype::since), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("round"), new JSNativeFunction(context, "round", 1, TemporalInstantPrototype::round), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("equals"), new JSNativeFunction(context, "equals", 1, TemporalInstantPrototype::equals), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, TemporalInstantPrototype::toStringMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toJSON"), new JSNativeFunction(context, "toJSON", 0, TemporalInstantPrototype::toJSON), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, TemporalInstantPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, TemporalInstantPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toZonedDateTimeISO"), new JSNativeFunction(context, "toZonedDateTimeISO", 1, TemporalInstantPrototype::toZonedDateTimeISO), PropertyDescriptor.DataState.ConfigurableWritable);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "Instant", 1, TemporalInstantConstructor::construct, true, false);
        constructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction(context, "from", 1, TemporalInstantConstructor::from), PropertyDescriptor.DataState.ConfigurableWritable);
        constructor.defineProperty(PropertyKey.fromString("fromEpochMilliseconds"), new JSNativeFunction(context, "fromEpochMilliseconds", 1, TemporalInstantConstructor::fromEpochMilliseconds), PropertyDescriptor.DataState.ConfigurableWritable);
        constructor.defineProperty(PropertyKey.fromString("fromEpochNanoseconds"), new JSNativeFunction(context, "fromEpochNanoseconds", 1, TemporalInstantConstructor::fromEpochNanoseconds), PropertyDescriptor.DataState.ConfigurableWritable);
        constructor.defineProperty(PropertyKey.fromString("compare"), new JSNativeFunction(context, "compare", 2, TemporalInstantConstructor::compare), PropertyDescriptor.DataState.ConfigurableWritable);

        // Link constructor <-> prototype
        constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor, PropertyDescriptor.DataState.ConfigurableWritable);

        temporalObject.defineProperty(PropertyKey.fromString("Instant"), constructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    private void initializeTemporalNow(JSObject temporalObject) {
        JSObject nowObject = context.createJSObject();
        nowObject.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Temporal.Now"), PropertyDescriptor.DataState.Configurable);

        nowObject.defineProperty(PropertyKey.fromString("instant"), new JSNativeFunction(context, "instant", 0, TemporalNow::instant), PropertyDescriptor.DataState.ConfigurableWritable);
        nowObject.defineProperty(PropertyKey.fromString("timeZoneId"), new JSNativeFunction(context, "timeZoneId", 0, TemporalNow::timeZoneId), PropertyDescriptor.DataState.ConfigurableWritable);
        nowObject.defineProperty(PropertyKey.fromString("plainDateTimeISO"), new JSNativeFunction(context, "plainDateTimeISO", 0, TemporalNow::plainDateTimeISO), PropertyDescriptor.DataState.ConfigurableWritable);
        nowObject.defineProperty(PropertyKey.fromString("plainDateISO"), new JSNativeFunction(context, "plainDateISO", 0, TemporalNow::plainDateISO), PropertyDescriptor.DataState.ConfigurableWritable);
        nowObject.defineProperty(PropertyKey.fromString("plainTimeISO"), new JSNativeFunction(context, "plainTimeISO", 0, TemporalNow::plainTimeISO), PropertyDescriptor.DataState.ConfigurableWritable);
        nowObject.defineProperty(PropertyKey.fromString("zonedDateTimeISO"), new JSNativeFunction(context, "zonedDateTimeISO", 0, TemporalNow::zonedDateTimeISO), PropertyDescriptor.DataState.ConfigurableWritable);

        temporalObject.defineProperty(PropertyKey.fromString("Now"), nowObject, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    private void initializeTemporalObject() {
        JSObject temporalObject = context.createJSObject();
        temporalObject.defineProperty(
                PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                new JSString("Temporal"),
                PropertyDescriptor.DataState.Configurable);

        // Register Temporal on globalThis first so prototype lookup works during construction
        globalObject.defineProperty(
                PropertyKey.fromString("Temporal"),
                temporalObject,
                PropertyDescriptor.DataState.ConfigurableWritable);

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
        JSObject prototype = context.createJSObject();
        prototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Temporal.PlainDate"), PropertyDescriptor.DataState.Configurable);

        // Getters (registered as zero-arg functions accessed as properties via get accessor)
        defineTemporalGetter(prototype, "calendarId", TemporalPlainDatePrototype::calendarId);
        defineTemporalGetter(prototype, "day", TemporalPlainDatePrototype::day);
        defineTemporalGetter(prototype, "dayOfWeek", TemporalPlainDatePrototype::dayOfWeek);
        defineTemporalGetter(prototype, "dayOfYear", TemporalPlainDatePrototype::dayOfYear);
        defineTemporalGetter(prototype, "daysInMonth", TemporalPlainDatePrototype::daysInMonth);
        defineTemporalGetter(prototype, "daysInWeek", TemporalPlainDatePrototype::daysInWeek);
        defineTemporalGetter(prototype, "daysInYear", TemporalPlainDatePrototype::daysInYear);
        defineTemporalGetter(prototype, "era", TemporalPlainDatePrototype::era);
        defineTemporalGetter(prototype, "eraYear", TemporalPlainDatePrototype::eraYear);
        defineTemporalGetter(prototype, "inLeapYear", TemporalPlainDatePrototype::inLeapYear);
        defineTemporalGetter(prototype, "month", TemporalPlainDatePrototype::month);
        defineTemporalGetter(prototype, "monthCode", TemporalPlainDatePrototype::monthCode);
        defineTemporalGetter(prototype, "monthsInYear", TemporalPlainDatePrototype::monthsInYear);
        defineTemporalGetter(prototype, "weekOfYear", TemporalPlainDatePrototype::weekOfYear);
        defineTemporalGetter(prototype, "year", TemporalPlainDatePrototype::year);
        defineTemporalGetter(prototype, "yearOfWeek", TemporalPlainDatePrototype::yearOfWeek);

        // Methods
        prototype.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction(context, "add", 1, TemporalPlainDatePrototype::add), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("equals"), new JSNativeFunction(context, "equals", 1, TemporalPlainDatePrototype::equals), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("since"), new JSNativeFunction(context, "since", 1, TemporalPlainDatePrototype::since), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("subtract"), new JSNativeFunction(context, "subtract", 1, TemporalPlainDatePrototype::subtract), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toJSON"), new JSNativeFunction(context, "toJSON", 0, TemporalPlainDatePrototype::toJSON), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, TemporalPlainDatePrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toPlainDateTime"), new JSNativeFunction(context, "toPlainDateTime", 0, TemporalPlainDatePrototype::toPlainDateTime), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toPlainMonthDay"), new JSNativeFunction(context, "toPlainMonthDay", 0, TemporalPlainDatePrototype::toPlainMonthDay), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toPlainYearMonth"), new JSNativeFunction(context, "toPlainYearMonth", 0, TemporalPlainDatePrototype::toPlainYearMonth), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, TemporalPlainDatePrototype::toStringMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toZonedDateTime"), new JSNativeFunction(context, "toZonedDateTime", 1, TemporalPlainDatePrototype::toZonedDateTime), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("until"), new JSNativeFunction(context, "until", 1, TemporalPlainDatePrototype::until), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, TemporalPlainDatePrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("with"), new JSNativeFunction(context, "with", 1, TemporalPlainDatePrototype::with), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("withCalendar"), new JSNativeFunction(context, "withCalendar", 1, TemporalPlainDatePrototype::withCalendar), PropertyDescriptor.DataState.ConfigurableWritable);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "PlainDate", 3, TemporalPlainDateConstructor::construct, true, false);
        constructor.defineProperty(PropertyKey.fromString("compare"), new JSNativeFunction(context, "compare", 2, TemporalPlainDateConstructor::compare), PropertyDescriptor.DataState.ConfigurableWritable);
        constructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction(context, "from", 1, TemporalPlainDateConstructor::from), PropertyDescriptor.DataState.ConfigurableWritable);

        // Link constructor <-> prototype
        constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor, PropertyDescriptor.DataState.ConfigurableWritable);

        temporalObject.defineProperty(PropertyKey.fromString("PlainDate"), constructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    private void initializeTemporalPlainDateTime(JSObject temporalObject) {
        JSObject prototype = context.createJSObject();
        prototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Temporal.PlainDateTime"), PropertyDescriptor.DataState.Configurable);

        // Getters
        defineTemporalGetter(prototype, "calendarId", TemporalPlainDateTimePrototype::calendarId);
        defineTemporalGetter(prototype, "day", TemporalPlainDateTimePrototype::day);
        defineTemporalGetter(prototype, "dayOfWeek", TemporalPlainDateTimePrototype::dayOfWeek);
        defineTemporalGetter(prototype, "dayOfYear", TemporalPlainDateTimePrototype::dayOfYear);
        defineTemporalGetter(prototype, "daysInMonth", TemporalPlainDateTimePrototype::daysInMonth);
        defineTemporalGetter(prototype, "daysInWeek", TemporalPlainDateTimePrototype::daysInWeek);
        defineTemporalGetter(prototype, "daysInYear", TemporalPlainDateTimePrototype::daysInYear);
        defineTemporalGetter(prototype, "era", TemporalPlainDateTimePrototype::era);
        defineTemporalGetter(prototype, "eraYear", TemporalPlainDateTimePrototype::eraYear);
        defineTemporalGetter(prototype, "hour", TemporalPlainDateTimePrototype::hour);
        defineTemporalGetter(prototype, "inLeapYear", TemporalPlainDateTimePrototype::inLeapYear);
        defineTemporalGetter(prototype, "microsecond", TemporalPlainDateTimePrototype::microsecond);
        defineTemporalGetter(prototype, "millisecond", TemporalPlainDateTimePrototype::millisecond);
        defineTemporalGetter(prototype, "minute", TemporalPlainDateTimePrototype::minute);
        defineTemporalGetter(prototype, "month", TemporalPlainDateTimePrototype::month);
        defineTemporalGetter(prototype, "monthCode", TemporalPlainDateTimePrototype::monthCode);
        defineTemporalGetter(prototype, "monthsInYear", TemporalPlainDateTimePrototype::monthsInYear);
        defineTemporalGetter(prototype, "nanosecond", TemporalPlainDateTimePrototype::nanosecond);
        defineTemporalGetter(prototype, "second", TemporalPlainDateTimePrototype::second);
        defineTemporalGetter(prototype, "weekOfYear", TemporalPlainDateTimePrototype::weekOfYear);
        defineTemporalGetter(prototype, "year", TemporalPlainDateTimePrototype::year);
        defineTemporalGetter(prototype, "yearOfWeek", TemporalPlainDateTimePrototype::yearOfWeek);

        // Methods
        prototype.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction(context, "add", 1, TemporalPlainDateTimePrototype::add), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("equals"), new JSNativeFunction(context, "equals", 1, TemporalPlainDateTimePrototype::equals), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("round"), new JSNativeFunction(context, "round", 1, TemporalPlainDateTimePrototype::round), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("since"), new JSNativeFunction(context, "since", 1, TemporalPlainDateTimePrototype::since), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("subtract"), new JSNativeFunction(context, "subtract", 1, TemporalPlainDateTimePrototype::subtract), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toJSON"), new JSNativeFunction(context, "toJSON", 0, TemporalPlainDateTimePrototype::toJSON), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, TemporalPlainDateTimePrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toPlainDate"), new JSNativeFunction(context, "toPlainDate", 0, TemporalPlainDateTimePrototype::toPlainDate), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toPlainTime"), new JSNativeFunction(context, "toPlainTime", 0, TemporalPlainDateTimePrototype::toPlainTime), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, TemporalPlainDateTimePrototype::toStringMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toZonedDateTime"), new JSNativeFunction(context, "toZonedDateTime", 1, TemporalPlainDateTimePrototype::toZonedDateTime), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("until"), new JSNativeFunction(context, "until", 1, TemporalPlainDateTimePrototype::until), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, TemporalPlainDateTimePrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("with"), new JSNativeFunction(context, "with", 1, TemporalPlainDateTimePrototype::with), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("withCalendar"), new JSNativeFunction(context, "withCalendar", 1, TemporalPlainDateTimePrototype::withCalendar), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("withPlainTime"), new JSNativeFunction(context, "withPlainTime", 0, TemporalPlainDateTimePrototype::withPlainTime), PropertyDescriptor.DataState.ConfigurableWritable);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "PlainDateTime", 3, TemporalPlainDateTimeConstructor::construct, true, false);
        constructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction(context, "from", 1, TemporalPlainDateTimeConstructor::from), PropertyDescriptor.DataState.ConfigurableWritable);
        constructor.defineProperty(PropertyKey.fromString("compare"), new JSNativeFunction(context, "compare", 2, TemporalPlainDateTimeConstructor::compare), PropertyDescriptor.DataState.ConfigurableWritable);

        // Link constructor <-> prototype
        constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor, PropertyDescriptor.DataState.ConfigurableWritable);

        temporalObject.defineProperty(PropertyKey.fromString("PlainDateTime"), constructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    private void initializeTemporalPlainMonthDay(JSObject temporalObject) {
        JSObject prototype = context.createJSObject();
        prototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Temporal.PlainMonthDay"), PropertyDescriptor.DataState.Configurable);

        // Getters
        defineTemporalGetter(prototype, "calendarId", TemporalPlainMonthDayPrototype::calendarId);
        defineTemporalGetter(prototype, "day", TemporalPlainMonthDayPrototype::day);
        defineTemporalGetter(prototype, "monthCode", TemporalPlainMonthDayPrototype::monthCode);
        defineTemporalGetter(prototype, "referenceISOYear", TemporalPlainMonthDayPrototype::referenceISOYear);

        // Methods
        prototype.defineProperty(PropertyKey.fromString("equals"), new JSNativeFunction(context, "equals", 1, TemporalPlainMonthDayPrototype::equals), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toJSON"), new JSNativeFunction(context, "toJSON", 0, TemporalPlainMonthDayPrototype::toJSON), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, TemporalPlainMonthDayPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toPlainDate"), new JSNativeFunction(context, "toPlainDate", 1, TemporalPlainMonthDayPrototype::toPlainDate), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, TemporalPlainMonthDayPrototype::toStringMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, TemporalPlainMonthDayPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("with"), new JSNativeFunction(context, "with", 1, TemporalPlainMonthDayPrototype::with), PropertyDescriptor.DataState.ConfigurableWritable);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "PlainMonthDay", 2, TemporalPlainMonthDayConstructor::construct, true, false);
        constructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction(context, "from", 1, TemporalPlainMonthDayConstructor::from), PropertyDescriptor.DataState.ConfigurableWritable);

        // Link constructor <-> prototype
        constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor, PropertyDescriptor.DataState.ConfigurableWritable);

        temporalObject.defineProperty(PropertyKey.fromString("PlainMonthDay"), constructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    private void initializeTemporalPlainTime(JSObject temporalObject) {
        JSObject prototype = context.createJSObject();
        prototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Temporal.PlainTime"), PropertyDescriptor.DataState.Configurable);

        // Getters
        defineTemporalGetter(prototype, "hour", TemporalPlainTimePrototype::hour);
        defineTemporalGetter(prototype, "microsecond", TemporalPlainTimePrototype::microsecond);
        defineTemporalGetter(prototype, "millisecond", TemporalPlainTimePrototype::millisecond);
        defineTemporalGetter(prototype, "minute", TemporalPlainTimePrototype::minute);
        defineTemporalGetter(prototype, "nanosecond", TemporalPlainTimePrototype::nanosecond);
        defineTemporalGetter(prototype, "second", TemporalPlainTimePrototype::second);

        // Methods
        prototype.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction(context, "add", 1, TemporalPlainTimePrototype::add), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("equals"), new JSNativeFunction(context, "equals", 1, TemporalPlainTimePrototype::equals), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("round"), new JSNativeFunction(context, "round", 1, TemporalPlainTimePrototype::round), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("since"), new JSNativeFunction(context, "since", 1, TemporalPlainTimePrototype::since), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("subtract"), new JSNativeFunction(context, "subtract", 1, TemporalPlainTimePrototype::subtract), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toJSON"), new JSNativeFunction(context, "toJSON", 0, TemporalPlainTimePrototype::toJSON), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, TemporalPlainTimePrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, TemporalPlainTimePrototype::toStringMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("until"), new JSNativeFunction(context, "until", 1, TemporalPlainTimePrototype::until), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, TemporalPlainTimePrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("with"), new JSNativeFunction(context, "with", 1, TemporalPlainTimePrototype::with), PropertyDescriptor.DataState.ConfigurableWritable);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "PlainTime", 0, TemporalPlainTimeConstructor::construct, true, false);
        constructor.defineProperty(PropertyKey.fromString("compare"), new JSNativeFunction(context, "compare", 2, TemporalPlainTimeConstructor::compare), PropertyDescriptor.DataState.ConfigurableWritable);
        constructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction(context, "from", 1, TemporalPlainTimeConstructor::from), PropertyDescriptor.DataState.ConfigurableWritable);

        // Link constructor <-> prototype
        constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor, PropertyDescriptor.DataState.ConfigurableWritable);

        temporalObject.defineProperty(PropertyKey.fromString("PlainTime"), constructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    private void initializeTemporalPlainYearMonth(JSObject temporalObject) {
        JSObject prototype = context.createJSObject();
        prototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Temporal.PlainYearMonth"), PropertyDescriptor.DataState.Configurable);

        // Getters
        defineTemporalGetter(prototype, "calendarId", TemporalPlainYearMonthPrototype::calendarId);
        defineTemporalGetter(prototype, "daysInMonth", TemporalPlainYearMonthPrototype::daysInMonth);
        defineTemporalGetter(prototype, "daysInYear", TemporalPlainYearMonthPrototype::daysInYear);
        defineTemporalGetter(prototype, "era", TemporalPlainYearMonthPrototype::era);
        defineTemporalGetter(prototype, "eraYear", TemporalPlainYearMonthPrototype::eraYear);
        defineTemporalGetter(prototype, "inLeapYear", TemporalPlainYearMonthPrototype::inLeapYear);
        defineTemporalGetter(prototype, "month", TemporalPlainYearMonthPrototype::month);
        defineTemporalGetter(prototype, "monthCode", TemporalPlainYearMonthPrototype::monthCode);
        defineTemporalGetter(prototype, "monthsInYear", TemporalPlainYearMonthPrototype::monthsInYear);
        defineTemporalGetter(prototype, "referenceISODay", TemporalPlainYearMonthPrototype::referenceISODay);
        defineTemporalGetter(prototype, "year", TemporalPlainYearMonthPrototype::year);

        // Methods
        prototype.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction(context, "add", 1, TemporalPlainYearMonthPrototype::add), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("equals"), new JSNativeFunction(context, "equals", 1, TemporalPlainYearMonthPrototype::equals), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("since"), new JSNativeFunction(context, "since", 1, TemporalPlainYearMonthPrototype::since), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("subtract"), new JSNativeFunction(context, "subtract", 1, TemporalPlainYearMonthPrototype::subtract), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toJSON"), new JSNativeFunction(context, "toJSON", 0, TemporalPlainYearMonthPrototype::toJSON), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, TemporalPlainYearMonthPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toPlainDate"), new JSNativeFunction(context, "toPlainDate", 1, TemporalPlainYearMonthPrototype::toPlainDate), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, TemporalPlainYearMonthPrototype::toStringMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("until"), new JSNativeFunction(context, "until", 1, TemporalPlainYearMonthPrototype::until), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, TemporalPlainYearMonthPrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("with"), new JSNativeFunction(context, "with", 1, TemporalPlainYearMonthPrototype::with), PropertyDescriptor.DataState.ConfigurableWritable);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "PlainYearMonth", 2, TemporalPlainYearMonthConstructor::construct, true, false);
        constructor.defineProperty(PropertyKey.fromString("compare"), new JSNativeFunction(context, "compare", 2, TemporalPlainYearMonthConstructor::compare), PropertyDescriptor.DataState.ConfigurableWritable);
        constructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction(context, "from", 1, TemporalPlainYearMonthConstructor::from), PropertyDescriptor.DataState.ConfigurableWritable);

        // Link constructor <-> prototype
        constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor, PropertyDescriptor.DataState.ConfigurableWritable);

        temporalObject.defineProperty(PropertyKey.fromString("PlainYearMonth"), constructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    private void initializeTemporalZonedDateTime(JSObject temporalObject) {
        JSObject prototype = context.createJSObject();
        prototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Temporal.ZonedDateTime"), PropertyDescriptor.DataState.Configurable);

        // Getters (28)
        defineTemporalGetter(prototype, "calendar", TemporalZonedDateTimePrototype::calendar);
        defineTemporalGetter(prototype, "calendarId", TemporalZonedDateTimePrototype::calendarId);
        defineTemporalGetter(prototype, "day", TemporalZonedDateTimePrototype::day);
        defineTemporalGetter(prototype, "dayOfWeek", TemporalZonedDateTimePrototype::dayOfWeek);
        defineTemporalGetter(prototype, "dayOfYear", TemporalZonedDateTimePrototype::dayOfYear);
        defineTemporalGetter(prototype, "daysInMonth", TemporalZonedDateTimePrototype::daysInMonth);
        defineTemporalGetter(prototype, "daysInWeek", TemporalZonedDateTimePrototype::daysInWeek);
        defineTemporalGetter(prototype, "daysInYear", TemporalZonedDateTimePrototype::daysInYear);
        defineTemporalGetter(prototype, "epochMilliseconds", TemporalZonedDateTimePrototype::epochMilliseconds);
        defineTemporalGetter(prototype, "epochNanoseconds", TemporalZonedDateTimePrototype::epochNanoseconds);
        defineTemporalGetter(prototype, "era", TemporalZonedDateTimePrototype::era);
        defineTemporalGetter(prototype, "eraYear", TemporalZonedDateTimePrototype::eraYear);
        defineTemporalGetter(prototype, "hour", TemporalZonedDateTimePrototype::hour);
        defineTemporalGetter(prototype, "hoursInDay", TemporalZonedDateTimePrototype::hoursInDay);
        defineTemporalGetter(prototype, "inLeapYear", TemporalZonedDateTimePrototype::inLeapYear);
        defineTemporalGetter(prototype, "microsecond", TemporalZonedDateTimePrototype::microsecond);
        defineTemporalGetter(prototype, "millisecond", TemporalZonedDateTimePrototype::millisecond);
        defineTemporalGetter(prototype, "minute", TemporalZonedDateTimePrototype::minute);
        defineTemporalGetter(prototype, "month", TemporalZonedDateTimePrototype::month);
        defineTemporalGetter(prototype, "monthCode", TemporalZonedDateTimePrototype::monthCode);
        defineTemporalGetter(prototype, "monthsInYear", TemporalZonedDateTimePrototype::monthsInYear);
        defineTemporalGetter(prototype, "nanosecond", TemporalZonedDateTimePrototype::nanosecond);
        defineTemporalGetter(prototype, "offset", TemporalZonedDateTimePrototype::offset);
        defineTemporalGetter(prototype, "offsetNanoseconds", TemporalZonedDateTimePrototype::offsetNanoseconds);
        defineTemporalGetter(prototype, "second", TemporalZonedDateTimePrototype::second);
        defineTemporalGetter(prototype, "timeZoneId", TemporalZonedDateTimePrototype::timeZoneId);
        defineTemporalGetter(prototype, "weekOfYear", TemporalZonedDateTimePrototype::weekOfYear);
        defineTemporalGetter(prototype, "year", TemporalZonedDateTimePrototype::year);
        defineTemporalGetter(prototype, "yearOfWeek", TemporalZonedDateTimePrototype::yearOfWeek);

        // Methods (20)
        prototype.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction(context, "add", 1, TemporalZonedDateTimePrototype::add), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("equals"), new JSNativeFunction(context, "equals", 1, TemporalZonedDateTimePrototype::equals), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("getTimeZoneTransition"), new JSNativeFunction(context, "getTimeZoneTransition", 1, TemporalZonedDateTimePrototype::getTimeZoneTransition), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("round"), new JSNativeFunction(context, "round", 1, TemporalZonedDateTimePrototype::round), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("since"), new JSNativeFunction(context, "since", 1, TemporalZonedDateTimePrototype::since), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("startOfDay"), new JSNativeFunction(context, "startOfDay", 0, TemporalZonedDateTimePrototype::startOfDay), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("subtract"), new JSNativeFunction(context, "subtract", 1, TemporalZonedDateTimePrototype::subtract), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toInstant"), new JSNativeFunction(context, "toInstant", 0, TemporalZonedDateTimePrototype::toInstant), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toJSON"), new JSNativeFunction(context, "toJSON", 0, TemporalZonedDateTimePrototype::toJSON), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, TemporalZonedDateTimePrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toPlainDate"), new JSNativeFunction(context, "toPlainDate", 0, TemporalZonedDateTimePrototype::toPlainDate), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toPlainDateTime"), new JSNativeFunction(context, "toPlainDateTime", 0, TemporalZonedDateTimePrototype::toPlainDateTime), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toPlainTime"), new JSNativeFunction(context, "toPlainTime", 0, TemporalZonedDateTimePrototype::toPlainTime), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, TemporalZonedDateTimePrototype::toStringMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("until"), new JSNativeFunction(context, "until", 1, TemporalZonedDateTimePrototype::until), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("valueOf"), new JSNativeFunction(context, "valueOf", 0, TemporalZonedDateTimePrototype::valueOf), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("with"), new JSNativeFunction(context, "with", 1, TemporalZonedDateTimePrototype::with), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("withCalendar"), new JSNativeFunction(context, "withCalendar", 1, TemporalZonedDateTimePrototype::withCalendar), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("withPlainTime"), new JSNativeFunction(context, "withPlainTime", 0, TemporalZonedDateTimePrototype::withPlainTime), PropertyDescriptor.DataState.ConfigurableWritable);
        prototype.defineProperty(PropertyKey.fromString("withTimeZone"), new JSNativeFunction(context, "withTimeZone", 1, TemporalZonedDateTimePrototype::withTimeZone), PropertyDescriptor.DataState.ConfigurableWritable);

        // Constructor
        JSNativeFunction constructor = new JSNativeFunction(context, "ZonedDateTime", 2, TemporalZonedDateTimeConstructor::construct, true, false);
        constructor.defineProperty(PropertyKey.fromString("compare"), new JSNativeFunction(context, "compare", 2, TemporalZonedDateTimeConstructor::compare), PropertyDescriptor.DataState.ConfigurableWritable);
        constructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction(context, "from", 1, TemporalZonedDateTimeConstructor::from), PropertyDescriptor.DataState.ConfigurableWritable);

        // Link constructor <-> prototype
        constructor.defineProperty(PropertyKey.PROTOTYPE, prototype, PropertyDescriptor.DataState.None);
        prototype.defineProperty(PropertyKey.CONSTRUCTOR, constructor, PropertyDescriptor.DataState.ConfigurableWritable);

        temporalObject.defineProperty(PropertyKey.fromString("ZonedDateTime"), constructor, PropertyDescriptor.DataState.ConfigurableWritable);
    }

    /**
     * Initialize all TypedArray constructors.
     * Per ES spec, creates the %TypedArray% intrinsic (shared parent) and
     * individual typed array constructors that inherit from it.
     */
    private void initializeTypedArrayConstructors() {
        record TypedArrayDef(String name, JSNativeCallback callback, JSConstructorType type,
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

        JSNativeFunction valuesFunction = new JSNativeFunction(context, "values", 0, TypedArrayPrototype::values);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("at"), new JSNativeFunction(context, "at", 1, TypedArrayPrototype::at), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("copyWithin"), new JSNativeFunction(context, "copyWithin", 2, TypedArrayPrototype::copyWithin), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("entries"), new JSNativeFunction(context, "entries", 0, TypedArrayPrototype::entries), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("every"), new JSNativeFunction(context, "every", 1, TypedArrayPrototype::every), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("fill"), new JSNativeFunction(context, "fill", 1, TypedArrayPrototype::fill), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("filter"), new JSNativeFunction(context, "filter", 1, TypedArrayPrototype::filter), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("find"), new JSNativeFunction(context, "find", 1, TypedArrayPrototype::find), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("findIndex"), new JSNativeFunction(context, "findIndex", 1, TypedArrayPrototype::findIndex), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("findLast"), new JSNativeFunction(context, "findLast", 1, TypedArrayPrototype::findLast), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("findLastIndex"), new JSNativeFunction(context, "findLastIndex", 1, TypedArrayPrototype::findLastIndex), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("forEach"), new JSNativeFunction(context, "forEach", 1, TypedArrayPrototype::forEach), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("includes"), new JSNativeFunction(context, "includes", 1, TypedArrayPrototype::includes), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("indexOf"), new JSNativeFunction(context, "indexOf", 1, TypedArrayPrototype::indexOf), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("join"), new JSNativeFunction(context, "join", 1, TypedArrayPrototype::join), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("keys"), new JSNativeFunction(context, "keys", 0, TypedArrayPrototype::keys), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("lastIndexOf"), new JSNativeFunction(context, "lastIndexOf", 1, TypedArrayPrototype::lastIndexOf), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("map"), new JSNativeFunction(context, "map", 1, TypedArrayPrototype::map), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("reduce"), new JSNativeFunction(context, "reduce", 1, TypedArrayPrototype::reduce), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("reduceRight"), new JSNativeFunction(context, "reduceRight", 1, TypedArrayPrototype::reduceRight), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("reverse"), new JSNativeFunction(context, "reverse", 0, TypedArrayPrototype::reverse), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("set"), new JSNativeFunction(context, "set", 1, TypedArrayPrototype::set), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("slice"), new JSNativeFunction(context, "slice", 2, TypedArrayPrototype::slice), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("some"), new JSNativeFunction(context, "some", 1, TypedArrayPrototype::some), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("sort"), new JSNativeFunction(context, "sort", 1, TypedArrayPrototype::sort), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("subarray"), new JSNativeFunction(context, "subarray", 2, TypedArrayPrototype::subarray), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("toLocaleString"), new JSNativeFunction(context, "toLocaleString", 0, TypedArrayPrototype::toLocaleString), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("toReversed"), new JSNativeFunction(context, "toReversed", 0, TypedArrayPrototype::toReversed), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("toSorted"), new JSNativeFunction(context, "toSorted", 1, TypedArrayPrototype::toSorted), PropertyDescriptor.DataState.ConfigurableWritable);
        if (arrayToString instanceof JSFunction) {
            typedArrayPrototype.defineProperty(PropertyKey.fromString("toString"), arrayToString, PropertyDescriptor.DataState.ConfigurableWritable);
        } else {
            typedArrayPrototype.defineProperty(PropertyKey.fromString("toString"), new JSNativeFunction(context, "toString", 0, TypedArrayPrototype::toString), PropertyDescriptor.DataState.ConfigurableWritable);
        }
        typedArrayPrototype.defineProperty(PropertyKey.fromString("with"), new JSNativeFunction(context, "with", 2, TypedArrayPrototype::withMethod), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("values"), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.ITERATOR), valuesFunction, PropertyDescriptor.DataState.ConfigurableWritable);

        typedArrayPrototype.defineProperty(PropertyKey.fromString("buffer"), new JSNativeFunction(context, "get buffer", 0, TypedArrayPrototype::getBuffer), PropertyDescriptor.AccessorState.Configurable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("byteLength"), new JSNativeFunction(context, "get byteLength", 0, TypedArrayPrototype::getByteLength), PropertyDescriptor.AccessorState.Configurable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("byteOffset"), new JSNativeFunction(context, "get byteOffset", 0, TypedArrayPrototype::getByteOffset), PropertyDescriptor.AccessorState.Configurable);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("length"), new JSNativeFunction(context, "get length", 0, TypedArrayPrototype::getLength), PropertyDescriptor.AccessorState.Configurable);
        typedArrayPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSNativeFunction(context, "get [Symbol.toStringTag]", 0, TypedArrayPrototype::getToStringTag), PropertyDescriptor.AccessorState.Configurable);

        // Create %TypedArray% constructor — abstract, throws if called directly
        // Per spec: %TypedArray% is not exposed as a global but is the [[Prototype]] of all typed array constructors
        JSNativeFunction typedArrayConstructor = new JSNativeFunction(context, "TypedArray", 0,
                (ctx, thisArg, args) -> ctx.throwTypeError("Abstract class TypedArray not directly constructable"),
                true, true);
        context.transferPrototype(typedArrayConstructor, JSFunction.NAME);
        typedArrayConstructor.defineProperty(PropertyKey.fromString("prototype"), typedArrayPrototype, PropertyDescriptor.DataState.None);
        typedArrayPrototype.defineProperty(PropertyKey.fromString("constructor"), typedArrayConstructor, PropertyDescriptor.DataState.ConfigurableWritable);

        // Add static methods to %TypedArray%
        typedArrayConstructor.defineProperty(PropertyKey.fromString("from"), new JSNativeFunction(context, "from", 1, TypedArrayConstructor::from), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayConstructor.defineProperty(PropertyKey.fromString("of"), new JSNativeFunction(context, "of", 0, TypedArrayConstructor::of), PropertyDescriptor.DataState.ConfigurableWritable);
        typedArrayConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES), new JSNativeFunction(context, "get [Symbol.species]", 0, TypedArrayConstructor::getSpecies), PropertyDescriptor.AccessorState.Configurable);

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
            JSNativeFunction constructor = new JSNativeFunction(context, def.name, 3, def.callback, true, true);
            constructor.setPrototype(typedArrayConstructor);
            constructor.defineProperty(PropertyKey.fromString("prototype"), prototype, PropertyDescriptor.DataState.None);
            constructor.setConstructorType(def.type);
            constructor.defineProperty(PropertyKey.fromString("BYTES_PER_ELEMENT"), JSNumber.of(def.bytesPerElement), PropertyDescriptor.DataState.None);

            prototype.defineProperty(PropertyKey.fromString("constructor"), constructor, PropertyDescriptor.DataState.ConfigurableWritable);

            // Add Uint8Array-specific base64/hex methods
            if (JSUint8Array.NAME.equals(def.name)) {
                // Static methods on constructor
                constructor.defineProperty(PropertyKey.fromString("fromBase64"), new JSNativeFunction(context, "fromBase64", 1, Uint8ArrayBase64Hex::fromBase64), PropertyDescriptor.DataState.ConfigurableWritable);
                constructor.defineProperty(PropertyKey.fromString("fromHex"), new JSNativeFunction(context, "fromHex", 1, Uint8ArrayBase64Hex::fromHex), PropertyDescriptor.DataState.ConfigurableWritable);

                // Prototype methods
                prototype.defineProperty(PropertyKey.fromString("toBase64"), new JSNativeFunction(context, "toBase64", 0, Uint8ArrayBase64Hex::toBase64), PropertyDescriptor.DataState.ConfigurableWritable);
                prototype.defineProperty(PropertyKey.fromString("toHex"), new JSNativeFunction(context, "toHex", 0, Uint8ArrayBase64Hex::toHex), PropertyDescriptor.DataState.ConfigurableWritable);
                prototype.defineProperty(PropertyKey.fromString("setFromBase64"), new JSNativeFunction(context, "setFromBase64", 1, Uint8ArrayBase64Hex::setFromBase64), PropertyDescriptor.DataState.ConfigurableWritable);
                prototype.defineProperty(PropertyKey.fromString("setFromHex"), new JSNativeFunction(context, "setFromHex", 1, Uint8ArrayBase64Hex::setFromHex), PropertyDescriptor.DataState.ConfigurableWritable);
            }

            globalObject.defineProperty(PropertyKey.fromString(def.name), constructor, PropertyDescriptor.DataState.ConfigurableWritable);
        }
    }

    /**
     * Initialize WeakMap constructor and prototype methods.
     */
    private void initializeWeakMapConstructor() {
        JSObject weakMapPrototype = context.createJSObject();
        weakMapPrototype.defineProperty(PropertyKey.fromString("set"), new JSNativeFunction(context, "set", 2, WeakMapPrototype::set), PropertyDescriptor.DataState.ConfigurableWritable);
        weakMapPrototype.defineProperty(PropertyKey.fromString("get"), new JSNativeFunction(context, "get", 1, WeakMapPrototype::get), PropertyDescriptor.DataState.ConfigurableWritable);
        weakMapPrototype.defineProperty(PropertyKey.fromString("getOrInsert"), new JSNativeFunction(context, "getOrInsert", 2, WeakMapPrototype::getOrInsert), PropertyDescriptor.DataState.ConfigurableWritable);
        weakMapPrototype.defineProperty(PropertyKey.fromString("getOrInsertComputed"), new JSNativeFunction(context, "getOrInsertComputed", 2, WeakMapPrototype::getOrInsertComputed), PropertyDescriptor.DataState.ConfigurableWritable);
        weakMapPrototype.defineProperty(PropertyKey.fromString("has"), new JSNativeFunction(context, "has", 1, WeakMapPrototype::has), PropertyDescriptor.DataState.ConfigurableWritable);
        weakMapPrototype.defineProperty(PropertyKey.fromString("delete"), new JSNativeFunction(context, "delete", 1, WeakMapPrototype::delete), PropertyDescriptor.DataState.ConfigurableWritable);
        weakMapPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSWeakMap.NAME), PropertyDescriptor.DataState.Configurable);

        JSNativeFunction weakMapConstructor = new JSNativeFunction(context, JSWeakMap.NAME,
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
        weakRefPrototype.defineProperty(PropertyKey.fromString("deref"), new JSNativeFunction(context, "deref", 0, WeakRefPrototype::deref), PropertyDescriptor.DataState.ConfigurableWritable);
        weakRefPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSWeakRef.NAME), PropertyDescriptor.DataState.Configurable);

        JSNativeFunction weakRefConstructor = new JSNativeFunction(context, JSWeakRef.NAME,
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
        weakSetPrototype.defineProperty(PropertyKey.fromString("add"), new JSNativeFunction(context, "add", 1, WeakSetPrototype::add), PropertyDescriptor.DataState.ConfigurableWritable);
        weakSetPrototype.defineProperty(PropertyKey.fromString("has"), new JSNativeFunction(context, "has", 1, WeakSetPrototype::has), PropertyDescriptor.DataState.ConfigurableWritable);
        weakSetPrototype.defineProperty(PropertyKey.fromString("delete"), new JSNativeFunction(context, "delete", 1, WeakSetPrototype::delete), PropertyDescriptor.DataState.ConfigurableWritable);
        weakSetPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString(JSWeakSet.NAME), PropertyDescriptor.DataState.Configurable);

        JSNativeFunction weakSetConstructor = new JSNativeFunction(context, JSWeakSet.NAME,
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

    public record EvalOverlaySnapshot(Map<String, JSValue> values, Set<String> absentKeys) {
    }

    public static class GlobalFunction {

        /**
         * Analyze caller function var-environment names in one parse.
         * bodyVarNames excludes parameter names.
         * functionVarEnvironmentNames includes parameter names and body var declarations.
         */
        private static CallerVarEnvironmentAnalysis analyzeCallerVarEnvironment(JSBytecodeFunction callerBytecodeFunction) {
            String source = callerBytecodeFunction.getSourceCode();
            if (source == null || source.isBlank()) {
                return null;
            }
            Expression functionLikeExpression = extractFunctionLikeExpression(source);
            if (functionLikeExpression == null) {
                return null;
            }
            Set<String> bodyVarNames = new HashSet<>();
            Set<String> functionVarEnvironmentNames = new HashSet<>();
            Set<String> constLexicalNames = new HashSet<>();
            if (functionLikeExpression instanceof FunctionExpression functionExpression) {
                for (Pattern parameter : functionExpression.getParams()) {
                    functionVarEnvironmentNames.addAll(parameter.getBoundNames());
                }
                if (functionExpression.getRestParameter() != null) {
                    functionVarEnvironmentNames.addAll(functionExpression.getRestParameter().getArgument().getBoundNames());
                }
                collectVarEnvironmentNamesFromStatements(functionExpression.getBody().getBody(), bodyVarNames);
                collectConstLexicalNamesFromStatements(functionExpression.getBody().getBody(), constLexicalNames);
                functionVarEnvironmentNames.addAll(bodyVarNames);
                return new CallerVarEnvironmentAnalysis(bodyVarNames, functionVarEnvironmentNames, constLexicalNames);
            }
            if (functionLikeExpression instanceof ArrowFunctionExpression arrowFunctionExpression) {
                for (Pattern parameter : arrowFunctionExpression.getParams()) {
                    functionVarEnvironmentNames.addAll(parameter.getBoundNames());
                }
                if (arrowFunctionExpression.getRestParameter() != null) {
                    functionVarEnvironmentNames.addAll(arrowFunctionExpression.getRestParameter().getArgument().getBoundNames());
                }
                if (arrowFunctionExpression.getBody() instanceof BlockStatement blockStatement) {
                    collectVarEnvironmentNamesFromStatements(blockStatement.getBody(), bodyVarNames);
                    collectConstLexicalNamesFromStatements(blockStatement.getBody(), constLexicalNames);
                }
                functionVarEnvironmentNames.addAll(bodyVarNames);
                return new CallerVarEnvironmentAnalysis(bodyVarNames, functionVarEnvironmentNames, constLexicalNames);
            }
            return null;
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

        private static void collectConstLexicalNamesFromStatement(Statement statement, Set<String> names) {
            if (statement instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() == VariableKind.CONST) {
                for (VariableDeclarator declaration : variableDeclaration.getDeclarations()) {
                    names.addAll(declaration.getId().getBoundNames());
                }
                return;
            }
            if (statement instanceof BlockStatement blockStatement) {
                collectConstLexicalNamesFromStatements(blockStatement.getBody(), names);
                return;
            }
            if (statement instanceof IfStatement ifStatement) {
                collectConstLexicalNamesFromStatement(ifStatement.getConsequent(), names);
                if (ifStatement.getAlternate() != null) {
                    collectConstLexicalNamesFromStatement(ifStatement.getAlternate(), names);
                }
                return;
            }
            if (statement instanceof ForStatement forStatement) {
                if (forStatement.getInit() instanceof VariableDeclaration variableDeclaration
                        && variableDeclaration.getKind() == VariableKind.CONST) {
                    for (VariableDeclarator declaration : variableDeclaration.getDeclarations()) {
                        names.addAll(declaration.getId().getBoundNames());
                    }
                }
                collectConstLexicalNamesFromStatement(forStatement.getBody(), names);
                return;
            }
            if (statement instanceof ForInStatement forInStatement) {
                if (forInStatement.getLeft() instanceof VariableDeclaration variableDeclaration
                        && variableDeclaration.getKind() == VariableKind.CONST) {
                    for (VariableDeclarator declaration : variableDeclaration.getDeclarations()) {
                        names.addAll(declaration.getId().getBoundNames());
                    }
                }
                collectConstLexicalNamesFromStatement(forInStatement.getBody(), names);
                return;
            }
            if (statement instanceof ForOfStatement forOfStatement) {
                if (forOfStatement.getLeft() instanceof VariableDeclaration variableDeclaration
                        && variableDeclaration.getKind() == VariableKind.CONST) {
                    for (VariableDeclarator declaration : variableDeclaration.getDeclarations()) {
                        names.addAll(declaration.getId().getBoundNames());
                    }
                }
                collectConstLexicalNamesFromStatement(forOfStatement.getBody(), names);
                return;
            }
            if (statement instanceof WhileStatement whileStatement) {
                collectConstLexicalNamesFromStatement(whileStatement.getBody(), names);
                return;
            }
            if (statement instanceof DoWhileStatement doWhileStatement) {
                collectConstLexicalNamesFromStatement(doWhileStatement.getBody(), names);
                return;
            }
            if (statement instanceof SwitchStatement switchStatement) {
                for (SwitchStatement.SwitchCase switchCase : switchStatement.getCases()) {
                    collectConstLexicalNamesFromStatements(switchCase.getConsequent(), names);
                }
                return;
            }
            if (statement instanceof TryStatement tryStatement) {
                collectConstLexicalNamesFromStatements(tryStatement.getBlock().getBody(), names);
                if (tryStatement.getHandler() != null) {
                    collectConstLexicalNamesFromStatements(tryStatement.getHandler().getBody().getBody(), names);
                }
                if (tryStatement.getFinalizer() != null) {
                    collectConstLexicalNamesFromStatements(tryStatement.getFinalizer().getBody(), names);
                }
                return;
            }
            if (statement instanceof LabeledStatement labeledStatement) {
                collectConstLexicalNamesFromStatement(labeledStatement.getBody(), names);
            }
        }

        private static void collectConstLexicalNamesFromStatements(List<Statement> statements, Set<String> names) {
            for (Statement statement : statements) {
                collectConstLexicalNamesFromStatement(statement, names);
            }
        }

        private static void collectVarEnvironmentNamesFromStatement(Statement statement, Set<String> names) {
            if (statement instanceof FunctionDeclaration functionDeclaration && functionDeclaration.getId() != null) {
                names.add(functionDeclaration.getId().getName());
                return;
            }
            if (statement instanceof VariableDeclaration variableDeclaration
                    && variableDeclaration.getKind() == VariableKind.VAR) {
                for (VariableDeclarator declaration : variableDeclaration.getDeclarations()) {
                    names.addAll(declaration.getId().getBoundNames());
                }
                return;
            }
            if (statement instanceof BlockStatement blockStatement) {
                collectVarEnvironmentNamesFromStatements(blockStatement.getBody(), names);
                return;
            }
            if (statement instanceof IfStatement ifStatement) {
                collectVarEnvironmentNamesFromStatement(ifStatement.getConsequent(), names);
                if (ifStatement.getAlternate() != null) {
                    collectVarEnvironmentNamesFromStatement(ifStatement.getAlternate(), names);
                }
                return;
            }
            if (statement instanceof ForStatement forStatement) {
                if (forStatement.getInit() instanceof VariableDeclaration variableDeclaration
                        && variableDeclaration.getKind() == VariableKind.VAR) {
                    for (VariableDeclarator declaration : variableDeclaration.getDeclarations()) {
                        names.addAll(declaration.getId().getBoundNames());
                    }
                }
                collectVarEnvironmentNamesFromStatement(forStatement.getBody(), names);
                return;
            }
            if (statement instanceof ForInStatement forInStatement) {
                if (forInStatement.getLeft() instanceof VariableDeclaration variableDeclaration
                        && variableDeclaration.getKind() == VariableKind.VAR) {
                    for (VariableDeclarator declaration : variableDeclaration.getDeclarations()) {
                        names.addAll(declaration.getId().getBoundNames());
                    }
                }
                collectVarEnvironmentNamesFromStatement(forInStatement.getBody(), names);
                return;
            }
            if (statement instanceof ForOfStatement forOfStatement) {
                if (forOfStatement.getLeft() instanceof VariableDeclaration variableDeclaration
                        && variableDeclaration.getKind() == VariableKind.VAR) {
                    for (VariableDeclarator declaration : variableDeclaration.getDeclarations()) {
                        names.addAll(declaration.getId().getBoundNames());
                    }
                }
                collectVarEnvironmentNamesFromStatement(forOfStatement.getBody(), names);
                return;
            }
            if (statement instanceof WhileStatement whileStatement) {
                collectVarEnvironmentNamesFromStatement(whileStatement.getBody(), names);
                return;
            }
            if (statement instanceof DoWhileStatement doWhileStatement) {
                collectVarEnvironmentNamesFromStatement(doWhileStatement.getBody(), names);
                return;
            }
            if (statement instanceof SwitchStatement switchStatement) {
                for (SwitchStatement.SwitchCase switchCase : switchStatement.getCases()) {
                    collectVarEnvironmentNamesFromStatements(switchCase.getConsequent(), names);
                }
                return;
            }
            if (statement instanceof TryStatement tryStatement) {
                collectVarEnvironmentNamesFromStatements(tryStatement.getBlock().getBody(), names);
                if (tryStatement.getHandler() != null) {
                    if (tryStatement.getHandler().getParam() != null) {
                        names.addAll(tryStatement.getHandler().getParam().getBoundNames());
                    }
                    collectVarEnvironmentNamesFromStatements(tryStatement.getHandler().getBody().getBody(), names);
                }
                if (tryStatement.getFinalizer() != null) {
                    collectVarEnvironmentNamesFromStatements(tryStatement.getFinalizer().getBody(), names);
                }
                return;
            }
            if (statement instanceof LabeledStatement labeledStatement) {
                collectVarEnvironmentNamesFromStatement(labeledStatement.getBody(), names);
            }
        }

        private static void collectVarEnvironmentNamesFromStatements(List<Statement> statements, Set<String> names) {
            for (Statement statement : statements) {
                collectVarEnvironmentNamesFromStatement(statement, names);
            }
        }

        private static boolean containsIdentifierReference(String sourceCode, String identifierName) {
            if (sourceCode == null
                    || sourceCode.isEmpty()
                    || identifierName == null
                    || identifierName.isEmpty()) {
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
        public static JSValue eval(JSContext realmContext, JSContext callerContext, JSValue[] args) {
            return eval(realmContext, callerContext, args, false);
        }

        public static JSValue eval(
                JSContext realmContext,
                JSContext callerContext,
                JSValue[] args,
                boolean forceDirectEvalCall) {
            JSValue x = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

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
            boolean isEvalInFunction = shouldUseCallerFrameSemantics
                    && hasSameRealmCallerFrame
                    && callerFrame.getFunction() instanceof JSBytecodeFunction
                    && callerFrame.getCaller() != null;
            boolean shouldOverlayLocals = isEvalInFunction;
            boolean shouldOverlayTopLevelLexicals = shouldUseCallerFrameSemantics
                    && hasSameRealmCallerFrame
                    && !isEvalInFunction
                    && callerFrame.getFunction() instanceof JSBytecodeFunction;
            boolean inheritedStrictMode = shouldUseCallerFrameSemantics
                    && callerFrame != null
                    && callerFrame.getFunction() instanceof JSBytecodeFunction bytecodeFunction && bytecodeFunction.isStrict();
            String[] localVarNames = null;
            Map<String, Integer> localVarNameToIndex = null;
            Set<String> localVarNameSet = null;
            Map<String, Integer> capturedVarOverlaySlots = null;
            Map<String, JSValue> savedGlobals = null;
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
            if (callerBytecodeFunction != null && shouldOverlayLocals) {
                callerVarEnvironmentAnalysis = analyzeCallerVarEnvironment(callerBytecodeFunction);
                if (callerVarEnvironmentAnalysis != null) {
                    functionVarEnvironmentNames = callerVarEnvironmentAnalysis.functionVarEnvironmentNames();
                    constLexicalNames = callerVarEnvironmentAnalysis.constLexicalNames();
                }
            }

            if ((shouldOverlayLocals || shouldOverlayTopLevelLexicals)
                    && callerBytecodeFunction != null
                    && callerBytecodeFunction.getBytecode().getLocalVarNames() != null) {
                localVarNames = callerBytecodeFunction.getBytecode().getLocalVarNames();
                localVarNameToIndex = new HashMap<>();
                localVarNameSet = new HashSet<>();
                capturedVarOverlaySlots = new LinkedHashMap<>();
                savedGlobals = new HashMap<>();
                absentKeys = new HashSet<>();
                immutableOverlayBindingNames = new HashSet<>();
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
                    if (shouldOverlayTopLevelLexicals
                            && locals[i] instanceof JSSymbol symbolValue
                            && "UninitializedMarker".equals(symbolValue.getDescription())) {
                        continue;
                    }
                    if (shouldOverlayTopLevelLexicals
                            && !callerContext.hasGlobalLexDeclaration(name)) {
                        boolean localBindingIsUndefined = locals[i] == null || locals[i] == JSUndefined.INSTANCE;
                        boolean evalSourceMentionsBinding = containsIdentifierReference(code, name);
                        if (localBindingIsUndefined || !evalSourceMentionsBinding) {
                            continue;
                        }
                    }
                    localVarNameSet.add(name);
                    localVarNameToIndex.putIfAbsent(name, i);
                    overlayBinding(global, name, locals[i], savedGlobals, absentKeys, touchedOverlayKeys);
                    // Function name bindings are immutable: make the overlay property
                    // non-writable so PUT_VAR throws TypeError in strict mode and
                    // silently fails in sloppy mode.
                    boolean isFunctionNameBinding = functionNameLocalIndex >= 0 && i == functionNameLocalIndex;
                    boolean isConstLexicalBinding = shouldOverlayLocals
                            && constLexicalNames != null
                            && constLexicalNames.contains(name);
                    if (isFunctionNameBinding || isConstLexicalBinding) {
                        PropertyDescriptor fnDesc = new PropertyDescriptor();
                        fnDesc.setValue(locals[i] != null ? locals[i] : JSUndefined.INSTANCE);
                        fnDesc.setWritable(false);
                        fnDesc.setEnumerable(true);
                        fnDesc.setConfigurable(true);
                        global.defineProperty(PropertyKey.fromString(name), fnDesc);
                        immutableOverlayBindingNames.add(name);
                    }
                }
                if (shouldOverlayLocals) {
                    IdentityHashMap<StackFrame, Boolean> visitedLexicalFrames = new IdentityHashMap<>();
                    if (callerBytecodeFunction.isEvalDynamicScopeLookupEnabled()
                            && callerBytecodeFunction.getEvalDynamicScopeFrame() != null) {
                        StackFrame evalScopeFrame = callerBytecodeFunction.getEvalDynamicScopeFrame();
                        while (evalScopeFrame != null && visitedLexicalFrames.put(evalScopeFrame, Boolean.TRUE) == null) {
                            if (evalScopeFrame != callerFrame) {
                                overlayFrameLocals(
                                        realmContext,
                                        global,
                                        evalScopeFrame,
                                        localVarNameSet,
                                        savedGlobals,
                                        absentKeys,
                                        touchedOverlayKeys);
                                Map<String, JSValue> evalScopeDynamicBindings = evalScopeFrame.getDynamicVarBindings();
                                if (evalScopeDynamicBindings != null) {
                                    for (Map.Entry<String, JSValue> entry : evalScopeDynamicBindings.entrySet()) {
                                        overlayBinding(
                                                global,
                                                entry.getKey(),
                                                entry.getValue(),
                                                savedGlobals,
                                                absentKeys,
                                                touchedOverlayKeys);
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
                            if (capturedVarName == null || capturedVarName.startsWith("$")) {
                                continue;
                            }
                            if (localVarNameSet.contains(capturedVarName)) {
                                continue;
                            }
                            JSValue capturedValue = callerFrame.getVarRef(captureSlot);
                            overlayBinding(
                                    global,
                                    capturedVarName,
                                    capturedValue,
                                    savedGlobals,
                                    absentKeys,
                                    touchedOverlayKeys);
                            capturedVarOverlaySlots.put(capturedVarName, captureSlot);
                            if (selfCaptureIndex >= 0 && captureSlot == selfCaptureIndex) {
                                PropertyDescriptor capturedFunctionNameDescriptor = new PropertyDescriptor();
                                capturedFunctionNameDescriptor.setValue(
                                        capturedValue != null ? capturedValue : JSUndefined.INSTANCE);
                                capturedFunctionNameDescriptor.setWritable(false);
                                capturedFunctionNameDescriptor.setEnumerable(true);
                                capturedFunctionNameDescriptor.setConfigurable(true);
                                global.defineProperty(PropertyKey.fromString(capturedVarName), capturedFunctionNameDescriptor);
                                immutableOverlayBindingNames.add(capturedVarName);
                            }
                        }
                    }
                    Map<String, JSValue> dynamicVarBindings = callerFrame.getDynamicVarBindings();
                    if (dynamicVarBindings != null) {
                        for (Map.Entry<String, JSValue> entry : dynamicVarBindings.entrySet()) {
                            overlayBinding(global, entry.getKey(), entry.getValue(), savedGlobals, absentKeys, touchedOverlayKeys);
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
            }

            if ((shouldOverlayLocals || shouldOverlayTopLevelLexicals)
                    && savedGlobals != null && absentKeys != null) {
                realmContext.pushEvalOverlay(savedGlobals, absentKeys);
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
                    if (callerBytecodeFunction != null
                            && callerFrame != null
                            && callerFrame.getCaller() != null) {
                        if (callerVarEnvironmentAnalysis == null) {
                            callerVarEnvironmentAnalysis = analyzeCallerVarEnvironment(callerBytecodeFunction);
                        }
                        functionVarEnvironmentNames = callerVarEnvironmentAnalysis != null
                                ? callerVarEnvironmentAnalysis.functionVarEnvironmentNames()
                                : null;
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
                        boolean shadowsCallerLocal = localVarNameSet != null
                                && localVarNameSet.contains(declarationName);
                        if (shadowsCallerLocal) {
                            if (!evalCodeStrict
                                    && callerFrame != null
                                    && localVarNameToIndex != null) {
                                Integer localIndex = localVarNameToIndex.get(declarationName);
                                if (localIndex != null
                                        && functionVarEnvironmentNames != null
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
                        overlayBinding(global, declarationName, initialValue, savedGlobals, absentKeys, touchedOverlayKeys);
                        if (!evalCodeStrict && callerFrame != null) {
                            callerFrame.setDynamicVarBinding(declarationName, initialValue);
                        }
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

                EvalOverlaySnapshot suspendedOverlaySnapshot = null;
                if (!isDirectEvalCall) {
                    suspendedOverlaySnapshot = realmContext.suspendEvalOverlays();
                    realmContext.pushEvalOverlayLookupSuppression();
                }
                String evalFilename = "<eval>";
                JSStackFrame callerStackFrame = callerContext.getCurrentStackFrame();
                if (callerStackFrame != null
                        && callerStackFrame.filename() != null
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
                        if (name.startsWith("$")) {
                            continue;
                        }
                        if (evalCodeStrict
                                && evalVarDeclarations != null
                                && evalVarDeclarations.contains(name)) {
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
                if (capturedVarOverlaySlots != null
                        && callerBytecodeFunction != null) {
                    int selfCaptureIndex = callerBytecodeFunction.getSelfCaptureIndex();
                    for (Map.Entry<String, Integer> entry : capturedVarOverlaySlots.entrySet()) {
                        String name = entry.getKey();
                        int captureSlot = entry.getValue();
                        if (evalCodeStrict
                                && evalVarDeclarations != null
                                && evalVarDeclarations.contains(name)) {
                            continue;
                        }
                        if (selfCaptureIndex >= 0 && captureSlot == selfCaptureIndex) {
                            continue;
                        }
                        PropertyKey key = PropertyKey.fromString(name);
                        if (global.has(key)) {
                            callerFrame.setVarRef(captureSlot, global.get(key));
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
                        global.delete(PropertyKey.fromString(name));
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

        private static void overlayFrameLocals(
                JSContext realmContext,
                JSObject global,
                StackFrame frame,
                Set<String> localVarNameSet,
                Map<String, JSValue> savedGlobals,
                Set<String> absentKeys,
                Set<String> touchedOverlayKeys) {
            if (frame == null || !(frame.getFunction() instanceof JSBytecodeFunction lexicalBytecodeFunction)) {
                return;
            }
            String[] lexicalLocalVarNames = lexicalBytecodeFunction.getBytecode().getLocalVarNames();
            JSValue[] lexicalLocals = frame.getLocals();
            if (lexicalLocalVarNames == null || lexicalLocals == null) {
                return;
            }
            for (int localIndex = 0;
                 localIndex < lexicalLocalVarNames.length && localIndex < lexicalLocals.length;
                 localIndex++) {
                String localName = lexicalLocalVarNames[localIndex];
                if (localName == null || localName.startsWith("$")) {
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
                overlayBinding(
                        global,
                        localName,
                        lexicalValue,
                        savedGlobals,
                        absentKeys,
                        touchedOverlayKeys);
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
            return JSNumber.of(Double.parseDouble(validNumber));
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

        private record CallerVarEnvironmentAnalysis(
                Set<String> bodyVarNames,
                Set<String> functionVarEnvironmentNames,
                Set<String> constLexicalNames) {
        }

        private record WithObjectCandidate(int depth, JSObject object) {
        }
    }
}
