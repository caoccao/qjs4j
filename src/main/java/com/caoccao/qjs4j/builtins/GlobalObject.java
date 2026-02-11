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
import com.caoccao.qjs4j.exceptions.JSErrorType;
import com.caoccao.qjs4j.exceptions.JSException;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
public final class GlobalObject {

    /**
     * console.error(...args)
     * Print error to standard error.
     */
    public static JSValue consoleError(JSContext context, JSValue thisArg, JSValue[] args) {
        System.err.print("[ERROR] ");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) System.err.print(" ");
            System.err.print(formatValue(context, args[i]));
        }
        System.err.println();
        return JSUndefined.INSTANCE;
    }

    /**
     * console.log(...args)
     * Print values to standard output.
     */
    public static JSValue consoleLog(JSContext context, JSValue thisArg, JSValue[] args) {
        for (int i = 0; i < args.length; i++) {
            if (i > 0) System.out.print(" ");
            System.out.print(formatValue(context, args[i]));
        }
        System.out.println();
        return JSUndefined.INSTANCE;
    }

    /**
     * console.warn(...args)
     * Print warning to standard error.
     */
    public static JSValue consoleWarn(JSContext context, JSValue thisArg, JSValue[] args) {
        System.err.print("[WARN] ");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) System.err.print(" ");
            System.err.print(formatValue(context, args[i]));
        }
        System.err.println();
        return JSUndefined.INSTANCE;
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

        try {
            String decoded = URLDecoder.decode(encodedString, StandardCharsets.UTF_8);
            return new JSString(decoded);
        } catch (Exception e) {
            return context.throwURIError("URI malformed");
        }
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

        try {
            String decoded = URLDecoder.decode(encodedString, StandardCharsets.UTF_8);
            return new JSString(decoded);
        } catch (Exception e) {
            return context.throwURIError("URI malformed");
        }
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

        try {
            // Encode, but preserve URI structure characters
            String encoded = URLEncoder.encode(uriString, StandardCharsets.UTF_8);

            // Restore characters that should not be encoded by encodeURI
            encoded = encoded
                    .replace("%3B", ";").replace("%2C", ",")
                    .replace("%2F", "/").replace("%3F", "?")
                    .replace("%3A", ":").replace("%40", "@")
                    .replace("%26", "&").replace("%3D", "=")
                    .replace("%2B", "+").replace("%24", "$")
                    .replace("%2D", "-").replace("%5F", "_")
                    .replace("%2E", ".").replace("%21", "!")
                    .replace("%7E", "~").replace("%2A", "*")
                    .replace("%27", "'").replace("%28", "(")
                    .replace("%29", ")").replace("%23", "#");

            return new JSString(encoded);
        } catch (Exception e) {
            return context.throwURIError("URI malformed");
        }
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

        try {
            String encoded = URLEncoder.encode(componentString, StandardCharsets.UTF_8);

            // Restore only the unreserved characters: - _ . ! ~ * ' ( )
            encoded = encoded
                    .replace("%2D", "-").replace("%5F", "_")
                    .replace("%2E", ".").replace("%21", "!")
                    .replace("%7E", "~").replace("%2A", "*")
                    .replace("%27", "'").replace("%28", "(")
                    .replace("%29", ")");

            return new JSString(encoded);
        } catch (Exception e) {
            return context.throwURIError("URI malformed");
        }
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
                    result.append('%');
                    result.append(String.format("%02X", (int) c));
                } else {
                    // Use %uXXXX format for characters >= 256
                    result.append("%u");
                    result.append(String.format("%04X", (int) c));
                }
            }
        }

        return new JSString(result.toString());
    }

    /**
     * eval(code)
     * Evaluate JavaScript code in the current context.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-eval-x">ECMAScript eval</a>
     */
    public static JSValue eval(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue x = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // If x is not a string, return it unchanged
        if (!(x instanceof JSString)) {
            return x;
        }

        String code = ((JSString) x).value();
        try {
            return context.eval(code);
        } catch (JSException e) {
            // Re-throw as pending exception so JavaScript try-catch can handle it
            // Don't set pending exception here - instead, convert to a throw by setting pending and returning undefined
            context.setPendingException(e.getErrorValue());
            return JSUndefined.INSTANCE;
        }
    }

    /**
     * Format a value for console output.
     */
    private static String formatValue(JSContext context, JSValue value) {
        if (value == null) {
            return "null";
        } else if (value.isNullOrUndefined()
                || value.isBigInt()
                || value.isBigIntObject()
                || value.isBoolean()
                || value.isBooleanObject()
                || value.isNumber()
                || value.isNumberObject()
                || value.isString()
                || value.isStringObject()
                || value.isSymbol()) {
            return JSTypeConversions.toString(context, value).value();
        } else if (value instanceof JSArray arr) {
            StringBuilder sb = new StringBuilder("[");
            for (long i = 0; i < arr.getLength(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatValue(context, arr.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else if (value instanceof JSObject) {
            return "[object Object]";
        }
        return String.valueOf(value);
    }

    /**
     * Initialize the global object with all built-in global properties and functions.
     */
    public static void initialize(JSContext context, JSObject global) {
        // Global value properties
        global.set("undefined", JSUndefined.INSTANCE);
        global.set("NaN", new JSNumber(Double.NaN));
        global.set("Infinity", new JSNumber(Double.POSITIVE_INFINITY));

        // Global function properties
        global.set("parseInt", new JSNativeFunction("parseInt", 2, GlobalObject::parseInt));
        global.set("parseFloat", new JSNativeFunction("parseFloat", 1, GlobalObject::parseFloat));
        global.set("isNaN", new JSNativeFunction("isNaN", 1, GlobalObject::isNaN));
        global.set("isFinite", new JSNativeFunction("isFinite", 1, GlobalObject::isFinite));
        global.set("eval", new JSNativeFunction("eval", 1, GlobalObject::eval));

        // URI handling functions
        global.set("encodeURI", new JSNativeFunction("encodeURI", 1, GlobalObject::encodeURI));
        global.set("decodeURI", new JSNativeFunction("decodeURI", 1, GlobalObject::decodeURI));
        global.set("encodeURIComponent", new JSNativeFunction("encodeURIComponent", 1, GlobalObject::encodeURIComponent));
        global.set("decodeURIComponent", new JSNativeFunction("decodeURIComponent", 1, GlobalObject::decodeURIComponent));
        global.set("escape", new JSNativeFunction("escape", 1, GlobalObject::escape, false));
        global.set("unescape", new JSNativeFunction("unescape", 1, GlobalObject::unescape, false));

        // Console object for debugging
        initializeConsoleObject(context, global);

        // Global this reference
        global.set("globalThis", global);

        // Built-in constructors and their prototypes
        initializeObjectConstructor(context, global);
        initializeBooleanConstructor(context, global);
        initializeArrayConstructor(context, global);
        initializeStringConstructor(context, global);
        initializeNumberConstructor(context, global);
        initializeFunctionConstructor(context, global);
        initializeAsyncFunctionConstructor(context, global);
        initializeDateConstructor(context, global);
        initializeRegExpConstructor(context, global);
        initializeSymbolConstructor(context, global);
        initializeBigIntConstructor(context, global);
        initializeMapConstructor(context, global);
        initializeSetConstructor(context, global);
        initializeWeakMapConstructor(context, global);
        initializeWeakSetConstructor(context, global);
        initializeWeakRefConstructor(context, global);
        initializeFinalizationRegistryConstructor(context, global);
        initializeMathObject(context, global);
        initializeJSONObject(context, global);
        initializeIntlObject(context, global);
        initializeReflectObject(context, global);
        initializeProxyConstructor(context, global);
        initializePromiseConstructor(context, global);
        initializeDisposableStackConstructor(context, global);
        initializeAsyncDisposableStackConstructor(context, global);
        initializeGeneratorPrototype(context, global);
        initializeIteratorConstructor(context, global);

        // Binary data constructors
        initializeArrayBufferConstructor(context, global);
        initializeSharedArrayBufferConstructor(context, global);
        initializeDataViewConstructor(context, global);
        initializeTypedArrayConstructors(context, global);
        initializeAtomicsObject(context, global);

        // Error constructors
        initializeErrorConstructors(context, global);

        // Initialize function prototype chains after all built-ins are set up
        initializeFunctionPrototypeChains(context, global, new HashSet<>());
    }

    /**
     * Initialize ArrayBuffer constructor and prototype.
     */
    private static void initializeArrayBufferConstructor(JSContext context, JSObject global) {
        // Create ArrayBuffer.prototype
        JSObject arrayBufferPrototype = context.createJSObject();
        arrayBufferPrototype.set("slice", new JSNativeFunction("slice", 2, ArrayBufferPrototype::slice));
        arrayBufferPrototype.set("resize", new JSNativeFunction("resize", 1, ArrayBufferPrototype::resize));
        arrayBufferPrototype.set("transfer", new JSNativeFunction("transfer", 0, ArrayBufferPrototype::transfer));
        arrayBufferPrototype.set("transferToFixedLength", new JSNativeFunction("transferToFixedLength", 0, ArrayBufferPrototype::transferToFixedLength));

        // Define getter properties
        JSNativeFunction byteLengthGetter = new JSNativeFunction("get byteLength", 0, ArrayBufferPrototype::getByteLength);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("byteLength"),
                PropertyDescriptor.accessorDescriptor(byteLengthGetter, null, false, true));

        JSNativeFunction detachedGetter = new JSNativeFunction("get detached", 0, ArrayBufferPrototype::getDetached);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("detached"),
                PropertyDescriptor.accessorDescriptor(detachedGetter, null, false, true));

        JSNativeFunction maxByteLengthGetter = new JSNativeFunction("get maxByteLength", 0, ArrayBufferPrototype::getMaxByteLength);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("maxByteLength"),
                PropertyDescriptor.accessorDescriptor(maxByteLengthGetter, null, false, true));

        JSNativeFunction resizableGetter = new JSNativeFunction("get resizable", 0, ArrayBufferPrototype::getResizable);
        arrayBufferPrototype.defineProperty(PropertyKey.fromString("resizable"),
                PropertyDescriptor.accessorDescriptor(resizableGetter, null, false, true));

        // Symbol.toStringTag
        JSNativeFunction toStringTagGetter = new JSNativeFunction("get [Symbol.toStringTag]", 0, ArrayBufferPrototype::getToStringTag);
        arrayBufferPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                PropertyDescriptor.accessorDescriptor(toStringTagGetter, null, false, true));

        // Create ArrayBuffer constructor as a function
        JSNativeFunction arrayBufferConstructor = new JSNativeFunction("ArrayBuffer", 1, ArrayBufferConstructor::call);
        arrayBufferConstructor.set("prototype", arrayBufferPrototype);
        arrayBufferConstructor.setConstructorType(JSConstructorType.ARRAY_BUFFER);
        arrayBufferPrototype.set("constructor", arrayBufferConstructor);

        // Static methods
        arrayBufferConstructor.set("isView", new JSNativeFunction("isView", 1, ArrayBufferConstructor::isView));

        // Symbol.species getter
        JSNativeFunction speciesGetter = new JSNativeFunction("get [Symbol.species]", 0, ArrayBufferConstructor::getSpecies);
        arrayBufferConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES),
                PropertyDescriptor.accessorDescriptor(speciesGetter, null, false, true));

        global.set("ArrayBuffer", arrayBufferConstructor);
    }

    /**
     * Initialize Array constructor and prototype.
     */
    private static void initializeArrayConstructor(JSContext context, JSObject global) {
        // Create Array.prototype
        JSObject arrayPrototype = context.createJSObject();
        JSNativeFunction valuesFunction = new JSNativeFunction("values", 0, IteratorPrototype::arrayValues);
        arrayPrototype.set("at", new JSNativeFunction("at", 1, ArrayPrototype::at));
        arrayPrototype.set("concat", new JSNativeFunction("concat", 1, ArrayPrototype::concat));
        arrayPrototype.set("copyWithin", new JSNativeFunction("copyWithin", 2, ArrayPrototype::copyWithin));
        arrayPrototype.set("entries", new JSNativeFunction("entries", 0, IteratorPrototype::arrayEntries));
        arrayPrototype.set("every", new JSNativeFunction("every", 1, ArrayPrototype::every));
        arrayPrototype.set("fill", new JSNativeFunction("fill", 1, ArrayPrototype::fill));
        arrayPrototype.set("filter", new JSNativeFunction("filter", 1, ArrayPrototype::filter));
        arrayPrototype.set("find", new JSNativeFunction("find", 1, ArrayPrototype::find));
        arrayPrototype.set("findIndex", new JSNativeFunction("findIndex", 1, ArrayPrototype::findIndex));
        arrayPrototype.set("findLast", new JSNativeFunction("findLast", 1, ArrayPrototype::findLast));
        arrayPrototype.set("findLastIndex", new JSNativeFunction("findLastIndex", 1, ArrayPrototype::findLastIndex));
        arrayPrototype.set("flat", new JSNativeFunction("flat", 0, ArrayPrototype::flat));
        arrayPrototype.set("flatMap", new JSNativeFunction("flatMap", 1, ArrayPrototype::flatMap));
        arrayPrototype.set("forEach", new JSNativeFunction("forEach", 1, ArrayPrototype::forEach));
        arrayPrototype.set("includes", new JSNativeFunction("includes", 1, ArrayPrototype::includes));
        arrayPrototype.set("indexOf", new JSNativeFunction("indexOf", 1, ArrayPrototype::indexOf));
        arrayPrototype.set("join", new JSNativeFunction("join", 1, ArrayPrototype::join));
        arrayPrototype.set("keys", new JSNativeFunction("keys", 0, IteratorPrototype::arrayKeys));
        arrayPrototype.set("lastIndexOf", new JSNativeFunction("lastIndexOf", 1, ArrayPrototype::lastIndexOf));
        arrayPrototype.set("map", new JSNativeFunction("map", 1, ArrayPrototype::map));
        arrayPrototype.set("pop", new JSNativeFunction("pop", 0, ArrayPrototype::pop));
        arrayPrototype.set("push", new JSNativeFunction("push", 1, ArrayPrototype::push));
        arrayPrototype.set("reduce", new JSNativeFunction("reduce", 1, ArrayPrototype::reduce));
        arrayPrototype.set("reduceRight", new JSNativeFunction("reduceRight", 1, ArrayPrototype::reduceRight));
        arrayPrototype.set("reverse", new JSNativeFunction("reverse", 0, ArrayPrototype::reverse));
        arrayPrototype.set("shift", new JSNativeFunction("shift", 0, ArrayPrototype::shift));
        arrayPrototype.set("slice", new JSNativeFunction("slice", 2, ArrayPrototype::slice));
        arrayPrototype.set("some", new JSNativeFunction("some", 1, ArrayPrototype::some));
        arrayPrototype.set("sort", new JSNativeFunction("sort", 1, ArrayPrototype::sort));
        arrayPrototype.set("splice", new JSNativeFunction("splice", 2, ArrayPrototype::splice));
        arrayPrototype.set("toLocaleString", new JSNativeFunction("toLocaleString", 0, ArrayPrototype::toLocaleString));
        arrayPrototype.set("toReversed", new JSNativeFunction("toReversed", 0, ArrayPrototype::toReversed));
        arrayPrototype.set("toSorted", new JSNativeFunction("toSorted", 1, ArrayPrototype::toSorted));
        arrayPrototype.set("toSpliced", new JSNativeFunction("toSpliced", 2, ArrayPrototype::toSpliced));
        arrayPrototype.set("toString", new JSNativeFunction("toString", 0, ArrayPrototype::toString));
        arrayPrototype.set("unshift", new JSNativeFunction("unshift", 1, ArrayPrototype::unshift));
        arrayPrototype.set("values", valuesFunction);
        arrayPrototype.set("with", new JSNativeFunction("with", 2, ArrayPrototype::with));

        // Array.prototype.length is a data property with value 0 (not writable, not enumerable, not configurable)
        arrayPrototype.defineProperty(PropertyKey.fromString("length"),
                PropertyDescriptor.dataDescriptor(new JSNumber(0), false, false, false));

        // Array.prototype[Symbol.*]
        arrayPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), valuesFunction);
        JSNativeFunction unscopablesGetter = new JSNativeFunction("get [Symbol.unscopables]", 0, ArrayPrototype::getSymbolUnscopables);
        arrayPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.UNSCOPABLES),
                PropertyDescriptor.accessorDescriptor(unscopablesGetter, null, false, true));

        // Create Array constructor as a function
        JSNativeFunction arrayConstructor = new JSNativeFunction("Array", 1, ArrayConstructor::call);
        arrayConstructor.set("prototype", arrayPrototype);
        arrayConstructor.setConstructorType(JSConstructorType.ARRAY);
        arrayPrototype.set("constructor", arrayConstructor);

        // Array static methods
        arrayConstructor.set("from", new JSNativeFunction("from", 1, ArrayConstructor::from));
        arrayConstructor.set("fromAsync", new JSNativeFunction("fromAsync", 1, ArrayConstructor::fromAsync));
        arrayConstructor.set("isArray", new JSNativeFunction("isArray", 1, ArrayConstructor::isArray));
        arrayConstructor.set("of", new JSNativeFunction("of", 0, ArrayConstructor::of));

        // Symbol.species getter
        JSNativeFunction arraySpeciesGetter = new JSNativeFunction("get [Symbol.species]", 0, ArrayConstructor::getSpecies);
        arrayConstructor.defineProperty(PropertyKey.fromSymbol(JSSymbol.SPECIES),
                PropertyDescriptor.accessorDescriptor(arraySpeciesGetter, null, false, true));

        global.set("Array", arrayConstructor);
    }

    /**
     * Initialize AsyncDisposableStack constructor and prototype.
     */
    private static void initializeAsyncDisposableStackConstructor(JSContext context, JSObject global) {
        JSObject asyncDisposableStackPrototype = context.createJSObject();
        asyncDisposableStackPrototype.set("adopt", new JSNativeFunction("adopt", 2, AsyncDisposableStackPrototype::adopt));
        asyncDisposableStackPrototype.set("defer", new JSNativeFunction("defer", 1, AsyncDisposableStackPrototype::defer));
        JSNativeFunction disposeAsyncFunction = new JSNativeFunction("disposeAsync", 0, AsyncDisposableStackPrototype::disposeAsync);
        asyncDisposableStackPrototype.set("disposeAsync", disposeAsyncFunction);
        asyncDisposableStackPrototype.set("move", new JSNativeFunction("move", 0, AsyncDisposableStackPrototype::move));
        asyncDisposableStackPrototype.set("use", new JSNativeFunction("use", 1, AsyncDisposableStackPrototype::use));
        asyncDisposableStackPrototype.set(PropertyKey.fromSymbol(JSSymbol.ASYNC_DISPOSE), disposeAsyncFunction);

        JSNativeFunction disposedGetter = new JSNativeFunction("get disposed", 0, AsyncDisposableStackPrototype::getDisposed);
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromString("disposed"),
                PropertyDescriptor.accessorDescriptor(disposedGetter, null, false, true));

        JSNativeFunction toStringTagGetter = new JSNativeFunction("get [Symbol.toStringTag]", 0, (childContext, thisObj, args) -> {
            if (!(thisObj instanceof JSAsyncDisposableStack)) {
                return childContext.throwTypeError("get AsyncDisposableStack.prototype[Symbol.toStringTag] called on non-AsyncDisposableStack");
            }
            return new JSString(JSAsyncDisposableStack.NAME);
        });
        asyncDisposableStackPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                PropertyDescriptor.accessorDescriptor(toStringTagGetter, null, false, true));

        JSNativeFunction asyncDisposableStackConstructor = new JSNativeFunction(
                JSAsyncDisposableStack.NAME, 0, AsyncDisposableStackConstructor::call, true, true);
        asyncDisposableStackConstructor.set("prototype", asyncDisposableStackPrototype);
        asyncDisposableStackPrototype.set("constructor", asyncDisposableStackConstructor);

        global.set(JSAsyncDisposableStack.NAME, asyncDisposableStackConstructor);
    }

    /**
     * Initialize AsyncFunction constructor and prototype.
     * AsyncFunction is not exposed in global scope but is available via async function constructors.
     */
    private static void initializeAsyncFunctionConstructor(JSContext context, JSObject global) {
        // Create AsyncFunction.prototype that inherits from Function.prototype
        JSObject asyncFunctionPrototype = context.createJSObject();
        context.transferPrototype(asyncFunctionPrototype, JSFunction.NAME);

        // Create AsyncFunction constructor
        // AsyncFunction is not normally exposed but we need it for the prototype chain
        JSNativeFunction asyncFunctionConstructor = new JSNativeFunction("AsyncFunction", 1,
                (ctx, thisObj, args) -> ctx.throwTypeError("AsyncFunction is not a constructor"));
        asyncFunctionConstructor.set("prototype", asyncFunctionPrototype);
        asyncFunctionPrototype.set("constructor", asyncFunctionConstructor);

        // Store AsyncFunction in the context (not in global scope)
        // This is used internally for setting up prototype chains for async bytecode functions
        context.setAsyncFunctionConstructor(asyncFunctionConstructor);
    }

    /**
     * Initialize Atomics object.
     */
    private static void initializeAtomicsObject(JSContext context, JSObject global) {
        JSObject atomics = context.createJSObject();
        atomics.set("add", new JSNativeFunction("add", 3, AtomicsObject::add));
        atomics.set("sub", new JSNativeFunction("sub", 3, AtomicsObject::sub));
        atomics.set("and", new JSNativeFunction("and", 3, AtomicsObject::and));
        atomics.set("or", new JSNativeFunction("or", 3, AtomicsObject::or));
        atomics.set("xor", new JSNativeFunction("xor", 3, AtomicsObject::xor));
        atomics.set("load", new JSNativeFunction("load", 2, AtomicsObject::load));
        atomics.set("store", new JSNativeFunction("store", 3, AtomicsObject::store));
        atomics.set("compareExchange", new JSNativeFunction("compareExchange", 4, AtomicsObject::compareExchange));
        atomics.set("exchange", new JSNativeFunction("exchange", 3, AtomicsObject::exchange));
        atomics.set("isLockFree", new JSNativeFunction("isLockFree", 1, AtomicsObject::isLockFree));
        atomics.set("notify", new JSNativeFunction("notify", 3, AtomicsObject::notify));
        atomics.set("pause", new JSNativeFunction("pause", 0, AtomicsObject::pause));
        atomics.set("wait", new JSNativeFunction("wait", 4, AtomicsObject::wait));
        atomics.set("waitAsync", new JSNativeFunction("waitAsync", 4, AtomicsObject::waitAsync));

        global.set("Atomics", atomics);
    }

    /**
     * Initialize BigInt constructor and static methods.
     */
    private static void initializeBigIntConstructor(JSContext context, JSObject global) {
        // Create BigInt.prototype
        JSObject bigIntPrototype = context.createJSObject();
        bigIntPrototype.set("toString", new JSNativeFunction("toString", 1, BigIntPrototype::toString));
        bigIntPrototype.set("valueOf", new JSNativeFunction("valueOf", 0, BigIntPrototype::valueOf));
        bigIntPrototype.set("toLocaleString", new JSNativeFunction("toLocaleString", 0, BigIntPrototype::toLocaleString));

        // Create BigInt constructor
        JSNativeFunction bigIntConstructor = new JSNativeFunction("BigInt", 1, BigIntConstructor::call);
        bigIntConstructor.set("prototype", bigIntPrototype);
        bigIntConstructor.setConstructorType(JSConstructorType.BIG_INT_OBJECT); // Mark as BigInt constructor
        bigIntPrototype.set("constructor", bigIntConstructor);

        // BigInt static methods
        bigIntConstructor.set("asIntN", new JSNativeFunction("asIntN", 2, BigIntConstructor::asIntN));
        bigIntConstructor.set("asUintN", new JSNativeFunction("asUintN", 2, BigIntConstructor::asUintN));

        global.set("BigInt", bigIntConstructor);
    }

    /**
     * Initialize Boolean constructor and prototype.
     */
    private static void initializeBooleanConstructor(JSContext context, JSObject global) {
        // Create Boolean.prototype
        JSObject booleanPrototype = context.createJSObject();
        booleanPrototype.set("toString", new JSNativeFunction("toString", 0, BooleanPrototype::toString));
        booleanPrototype.set("valueOf", new JSNativeFunction("valueOf", 0, BooleanPrototype::valueOf));

        // Create Boolean constructor
        JSNativeFunction booleanConstructor = new JSNativeFunction("Boolean", 1, BooleanConstructor::call);
        booleanConstructor.set("prototype", booleanPrototype);
        booleanConstructor.setConstructorType(JSConstructorType.BOOLEAN_OBJECT); // Mark as Boolean constructor
        booleanPrototype.set("constructor", booleanConstructor);

        global.set("Boolean", booleanConstructor);
    }

    /**
     * Initialize console object.
     */
    private static void initializeConsoleObject(JSContext context, JSObject global) {
        JSObject console = context.createJSObject();
        console.set("log", new JSNativeFunction("log", 0, GlobalObject::consoleLog));
        console.set("info", new JSNativeFunction("info", 0, GlobalObject::consoleLog));
        console.set("warn", new JSNativeFunction("warn", 0, GlobalObject::consoleWarn));
        console.set("error", new JSNativeFunction("error", 0, GlobalObject::consoleError));

        global.set("console", console);
    }

    /**
     * Initialize DataView constructor and prototype.
     */
    private static void initializeDataViewConstructor(JSContext context, JSObject global) {
        // Create DataView.prototype
        JSObject dataViewPrototype = context.createJSObject();

        // Define getter properties
        JSNativeFunction bufferGetter = new JSNativeFunction("get buffer", 0, DataViewPrototype::getBuffer);
        dataViewPrototype.defineProperty(PropertyKey.fromString("buffer"),
                PropertyDescriptor.accessorDescriptor(bufferGetter, null, false, true));

        JSNativeFunction byteLengthGetter = new JSNativeFunction("get byteLength", 0, DataViewPrototype::getByteLength);
        dataViewPrototype.defineProperty(PropertyKey.fromString("byteLength"),
                PropertyDescriptor.accessorDescriptor(byteLengthGetter, null, false, true));

        JSNativeFunction byteOffsetGetter = new JSNativeFunction("get byteOffset", 0, DataViewPrototype::getByteOffset);
        dataViewPrototype.defineProperty(PropertyKey.fromString("byteOffset"),
                PropertyDescriptor.accessorDescriptor(byteOffsetGetter, null, false, true));

        // Int8/Uint8 methods
        dataViewPrototype.set("getInt8", new JSNativeFunction("getInt8", 1, DataViewPrototype::getInt8));
        dataViewPrototype.set("setInt8", new JSNativeFunction("setInt8", 2, DataViewPrototype::setInt8));
        dataViewPrototype.set("getUint8", new JSNativeFunction("getUint8", 1, DataViewPrototype::getUint8));
        dataViewPrototype.set("setUint8", new JSNativeFunction("setUint8", 2, DataViewPrototype::setUint8));

        // Int16 methods
        dataViewPrototype.set("getInt16", new JSNativeFunction("getInt16", 2, DataViewPrototype::getInt16));
        dataViewPrototype.set("setInt16", new JSNativeFunction("setInt16", 3, DataViewPrototype::setInt16));

        // Int32 methods
        dataViewPrototype.set("getInt32", new JSNativeFunction("getInt32", 2, DataViewPrototype::getInt32));
        dataViewPrototype.set("setInt32", new JSNativeFunction("setInt32", 3, DataViewPrototype::setInt32));

        // Float32 methods
        dataViewPrototype.set("getFloat32", new JSNativeFunction("getFloat32", 2, DataViewPrototype::getFloat32));
        dataViewPrototype.set("setFloat32", new JSNativeFunction("setFloat32", 3, DataViewPrototype::setFloat32));

        // Float64 methods
        dataViewPrototype.set("getFloat64", new JSNativeFunction("getFloat64", 2, DataViewPrototype::getFloat64));
        dataViewPrototype.set("setFloat64", new JSNativeFunction("setFloat64", 3, DataViewPrototype::setFloat64));

        // Create DataView constructor as a function that requires 'new'
        JSNativeFunction dataViewConstructor = new JSNativeFunction("DataView", 1, DataViewConstructor::call, true, true);
        dataViewConstructor.set("prototype", dataViewPrototype);
        dataViewConstructor.setConstructorType(JSConstructorType.DATA_VIEW);
        dataViewPrototype.set("constructor", dataViewConstructor);

        global.set("DataView", dataViewConstructor);
    }

    /**
     * Initialize Date constructor and prototype.
     */
    private static void initializeDateConstructor(JSContext context, JSObject global) {
        // Create Date.prototype
        JSObject datePrototype = context.createJSObject();
        datePrototype.set("getTime", new JSNativeFunction("getTime", 0, DatePrototype::getTime));
        datePrototype.set("getFullYear", new JSNativeFunction("getFullYear", 0, DatePrototype::getFullYear));
        datePrototype.set("getMonth", new JSNativeFunction("getMonth", 0, DatePrototype::getMonth));
        datePrototype.set("getDate", new JSNativeFunction("getDate", 0, DatePrototype::getDate));
        datePrototype.set("getDay", new JSNativeFunction("getDay", 0, DatePrototype::getDay));
        datePrototype.set("getHours", new JSNativeFunction("getHours", 0, DatePrototype::getHours));
        datePrototype.set("getMinutes", new JSNativeFunction("getMinutes", 0, DatePrototype::getMinutes));
        datePrototype.set("getSeconds", new JSNativeFunction("getSeconds", 0, DatePrototype::getSeconds));
        datePrototype.set("getMilliseconds", new JSNativeFunction("getMilliseconds", 0, DatePrototype::getMilliseconds));
        datePrototype.set("getUTCFullYear", new JSNativeFunction("getUTCFullYear", 0, DatePrototype::getUTCFullYear));
        datePrototype.set("getUTCMonth", new JSNativeFunction("getUTCMonth", 0, DatePrototype::getUTCMonth));
        datePrototype.set("getUTCDate", new JSNativeFunction("getUTCDate", 0, DatePrototype::getUTCDate));
        datePrototype.set("getUTCHours", new JSNativeFunction("getUTCHours", 0, DatePrototype::getUTCHours));
        datePrototype.set("toISOString", new JSNativeFunction("toISOString", 0, DatePrototype::toISOString));
        datePrototype.set("toJSON", new JSNativeFunction("toJSON", 0, DatePrototype::toJSON));
        datePrototype.set("toString", new JSNativeFunction("toString", 0, DatePrototype::toStringMethod));
        datePrototype.set("valueOf", new JSNativeFunction("valueOf", 0, DatePrototype::valueOf));

        // Create Date constructor as a JSNativeFunction
        // When called without 'new', DateConstructor.call returns a string
        // When called with 'new', VM calls JSDate.create via constructorType
        JSNativeFunction dateConstructor = new JSNativeFunction("Date", 7, DateConstructor::call);
        dateConstructor.set("prototype", datePrototype);
        dateConstructor.setConstructorType(JSConstructorType.DATE); // Mark as Date constructor
        datePrototype.set("constructor", dateConstructor);

        // Date static methods
        dateConstructor.set("now", new JSNativeFunction("now", 0, DateConstructor::now));
        dateConstructor.set("parse", new JSNativeFunction("parse", 1, DateConstructor::parse));
        dateConstructor.set("UTC", new JSNativeFunction("UTC", 7, DateConstructor::UTC));

        global.set("Date", dateConstructor);
    }

    /**
     * Initialize DisposableStack constructor and prototype.
     */
    private static void initializeDisposableStackConstructor(JSContext context, JSObject global) {
        JSObject disposableStackPrototype = context.createJSObject();
        disposableStackPrototype.set("adopt", new JSNativeFunction("adopt", 2, DisposableStackPrototype::adopt));
        disposableStackPrototype.set("defer", new JSNativeFunction("defer", 1, DisposableStackPrototype::defer));
        JSNativeFunction disposeFunction = new JSNativeFunction("dispose", 0, DisposableStackPrototype::dispose);
        disposableStackPrototype.set("dispose", disposeFunction);
        disposableStackPrototype.set("move", new JSNativeFunction("move", 0, DisposableStackPrototype::move));
        disposableStackPrototype.set("use", new JSNativeFunction("use", 1, DisposableStackPrototype::use));
        disposableStackPrototype.set(PropertyKey.fromSymbol(JSSymbol.DISPOSE), disposeFunction);

        JSNativeFunction disposedGetter = new JSNativeFunction("get disposed", 0, DisposableStackPrototype::getDisposed);
        disposableStackPrototype.defineProperty(PropertyKey.fromString("disposed"),
                PropertyDescriptor.accessorDescriptor(disposedGetter, null, false, true));

        JSNativeFunction toStringTagGetter = new JSNativeFunction("get [Symbol.toStringTag]", 0, (childContext, thisObj, args) -> {
            if (!(thisObj instanceof JSDisposableStack)) {
                return childContext.throwTypeError("get DisposableStack.prototype[Symbol.toStringTag] called on non-DisposableStack");
            }
            return new JSString(JSDisposableStack.NAME);
        });
        disposableStackPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                PropertyDescriptor.accessorDescriptor(toStringTagGetter, null, false, true));

        JSNativeFunction disposableStackConstructor = new JSNativeFunction(
                JSDisposableStack.NAME, 0, DisposableStackConstructor::call, true, true);
        disposableStackConstructor.set("prototype", disposableStackPrototype);
        disposableStackPrototype.set("constructor", disposableStackConstructor);

        global.set(JSDisposableStack.NAME, disposableStackConstructor);
    }

    /**
     * Initialize Error constructors.
     */
    private static void initializeErrorConstructors(JSContext context, JSObject global) {
        Stream.of(JSErrorType.values()).forEach(type -> global.set(type.name(), type.create(context)));
    }

    /**
     * Initialize FinalizationRegistry constructor.
     */
    private static void initializeFinalizationRegistryConstructor(JSContext context, JSObject global) {
        // Create FinalizationRegistry.prototype
        JSObject finalizationRegistryPrototype = context.createJSObject();
        // register() and unregister() methods are added in JSFinalizationRegistry constructor

        // Create FinalizationRegistry constructor as JSNativeFunction
        // FinalizationRegistry requires 'new'
        JSNativeFunction finalizationRegistryConstructor = new JSNativeFunction(
                "FinalizationRegistry", 1, FinalizationRegistryConstructor::call, true, true);
        finalizationRegistryConstructor.set("prototype", finalizationRegistryPrototype);
        finalizationRegistryConstructor.setConstructorType(JSConstructorType.FINALIZATION_REGISTRY);
        finalizationRegistryPrototype.set("constructor", finalizationRegistryConstructor);

        global.set("FinalizationRegistry", finalizationRegistryConstructor);
    }

    /**
     * Initialize Function constructor and prototype.
     */
    private static void initializeFunctionConstructor(JSContext context, JSObject global) {
        // Create Function.prototype as a function (not a plain object)
        // According to ECMAScript spec, Function.prototype is itself a function
        // Use null name so toString() shows "function () {}" not "function anonymous() {}"
        JSNativeFunction functionPrototype = new JSNativeFunction(null, 0, (ctx, thisObj, args) -> JSUndefined.INSTANCE);
        // Remove the auto-created properties - Function.prototype has custom property descriptors
        functionPrototype.delete(PropertyKey.fromString("prototype"));
        functionPrototype.delete(PropertyKey.fromString("length"));
        functionPrototype.delete(PropertyKey.fromString("name"));

        functionPrototype.set("call", new JSNativeFunction("call", 1, FunctionPrototype::call));
        functionPrototype.set("apply", new JSNativeFunction("apply", 2, FunctionPrototype::apply));
        functionPrototype.set("bind", new JSNativeFunction("bind", 1, FunctionPrototype::bind));
        functionPrototype.set("toString", new JSNativeFunction("toString", 0, FunctionPrototype::toString_));

        // Add 'arguments' and 'caller' as accessor properties that throw TypeError
        // These properties exist for backwards compatibility but throw when accessed
        JSNativeFunction throwTypeError = new JSNativeFunction(
                "throwTypeError",
                0,
                (childContext, thisObj, args) ->
                        childContext.throwTypeError(
                                "'caller', 'callee', and 'arguments' properties may not be accessed on strict mode functions or the arguments objects for calls to them"));

        functionPrototype.defineProperty(PropertyKey.fromString("arguments"),
                PropertyDescriptor.accessorDescriptor(throwTypeError, throwTypeError, false, true));
        functionPrototype.defineProperty(PropertyKey.fromString("caller"),
                PropertyDescriptor.accessorDescriptor(throwTypeError, throwTypeError, false, true));

        // Add 'length' and 'name' data properties
        functionPrototype.defineProperty(PropertyKey.fromString("length"),
                PropertyDescriptor.dataDescriptor(new JSNumber(0), false, false, true));
        functionPrototype.defineProperty(PropertyKey.fromString("name"),
                PropertyDescriptor.dataDescriptor(new JSString(""), false, false, true));

        // Function constructor should be a function, not a plain object
        JSNativeFunction functionConstructor = new JSNativeFunction(JSFunction.NAME, 1, FunctionConstructor::call);
        functionConstructor.set("prototype", functionPrototype);
        functionPrototype.set("constructor", functionConstructor);

        global.set(JSFunction.NAME, functionConstructor);
    }

    /**
     * Recursively initialize prototype chains for all JSFunction instances in the global object.
     * This must be called after Function.prototype is set up.
     */
    private static void initializeFunctionPrototypeChains(JSContext context, JSObject obj, Set<JSObject> visitedObjectSet) {
        // This ensures all JSFunction instances inherit from Function.prototype
        // Avoid infinite recursion by tracking visited objects
        if (visitedObjectSet.contains(obj)) {
            return;
        }
        visitedObjectSet.add(obj);

        List<PropertyKey> keys = obj.getOwnPropertyKeys();
        for (PropertyKey key : keys) {
            // Get the property value using the property string
            String keyStr = key.toPropertyString();
            JSValue value = obj.get(keyStr);
            if (value instanceof JSFunction func) {
                // Initialize this function's prototype chain
                func.initializePrototypeChain(context);
                // Recursively process the function's own properties
                initializeFunctionPrototypeChains(context, func, visitedObjectSet);
            } else if (value instanceof JSObject subObj) {
                // Recursively process nested objects
                initializeFunctionPrototypeChains(context, subObj, visitedObjectSet);
            }
        }
    }

    /**
     * Initialize Generator prototype methods.
     * Note: Generator functions (function*) would require compiler support.
     * This provides the prototype for manually created generators.
     */
    private static void initializeGeneratorPrototype(JSContext context, JSObject global) {
        // Create Generator.prototype
        JSObject generatorPrototype = context.createJSObject();
        generatorPrototype.set("next", new JSNativeFunction("next", 1, GeneratorPrototype::next));
        generatorPrototype.set("return", new JSNativeFunction("return", 1, GeneratorPrototype::returnMethod));
        generatorPrototype.set("throw", new JSNativeFunction("throw", 1, GeneratorPrototype::throwMethod));
        // Generator.prototype[Symbol.iterator] returns this
        generatorPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR),
                new JSNativeFunction("[Symbol.iterator]", 0, (childContext, thisArg, args) -> thisArg));
    }

    /**
     * Initialize Iterator constructor and prototype.
     * Based on ECMAScript 2024 Iterator specification.
     */
    private static void initializeIteratorConstructor(JSContext context, JSObject global) {
        // Create Iterator.prototype with helper methods
        JSObject iteratorPrototype = context.createJSObject();

        // Iterator.prototype methods (ES2024)
        // Note: These are placeholders. Full implementation requires iterator helper support.
        iteratorPrototype.set("drop", new JSNativeFunction("drop", 1, IteratorPrototype::drop));
        iteratorPrototype.set("filter", new JSNativeFunction("filter", 1, IteratorPrototype::filter));
        iteratorPrototype.set("flatMap", new JSNativeFunction("flatMap", 1, IteratorPrototype::flatMap));
        iteratorPrototype.set("map", new JSNativeFunction("map", 1, IteratorPrototype::map));
        iteratorPrototype.set("take", new JSNativeFunction("take", 1, IteratorPrototype::take));
        iteratorPrototype.set("every", new JSNativeFunction("every", 1, IteratorPrototype::every));
        iteratorPrototype.set("find", new JSNativeFunction("find", 1, IteratorPrototype::find));
        iteratorPrototype.set("forEach", new JSNativeFunction("forEach", 1, IteratorPrototype::forEach));
        iteratorPrototype.set("some", new JSNativeFunction("some", 1, IteratorPrototype::some));
        iteratorPrototype.set("reduce", new JSNativeFunction("reduce", 1, IteratorPrototype::reduce));
        iteratorPrototype.set("toArray", new JSNativeFunction("toArray", 0, IteratorPrototype::toArray));

        // Iterator.prototype[Symbol.iterator] returns this
        iteratorPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR),
                new JSNativeFunction("[Symbol.iterator]", 0, (childContext, thisArg, args) -> thisArg));

        // Create Iterator constructor as JSNativeFunction
        // Iterator is an abstract class - it requires 'new' but throws when constructed directly
        JSNativeFunction iteratorConstructor = new JSNativeFunction("Iterator", 0, IteratorConstructor::call, true, true);
        iteratorConstructor.set("prototype", iteratorPrototype);

        // Iterator static methods (ES2024)
        iteratorConstructor.set("from", new JSNativeFunction("from", 1, IteratorPrototype::from));

        // Set Iterator.prototype.constructor
        iteratorPrototype.set("constructor", iteratorConstructor);

        // Add Iterator to global object
        global.set("Iterator", iteratorConstructor);
    }

    /**
     * Initialize JSON object.
     */
    private static void initializeJSONObject(JSContext context, JSObject global) {
        JSObject json = context.createJSObject();
        json.set("parse", new JSNativeFunction("parse", 1, JSONObject::parse));
        json.set("stringify", new JSNativeFunction("stringify", 1, JSONObject::stringify));

        global.set("JSON", json);
    }

    /**
     * Initialize Intl object.
     */
    private static void initializeIntlObject(JSContext context, JSObject global) {
        JSObject intlObject = context.createJSObject();
        intlObject.set("getCanonicalLocales", new JSNativeFunction("getCanonicalLocales", 1, IntlObject::getCanonicalLocales));

        JSObject dateTimeFormatPrototype = context.createJSObject();
        dateTimeFormatPrototype.set("format", new JSNativeFunction("format", 1, IntlObject::dateTimeFormatFormat));
        dateTimeFormatPrototype.set("resolvedOptions", new JSNativeFunction("resolvedOptions", 0, IntlObject::dateTimeFormatResolvedOptions));
        JSNativeFunction dateTimeFormatConstructor = new JSNativeFunction(
                "DateTimeFormat",
                0,
                (childContext, thisArg, args) -> IntlObject.createDateTimeFormat(childContext, dateTimeFormatPrototype, args));
        dateTimeFormatConstructor.set("prototype", dateTimeFormatPrototype);
        dateTimeFormatConstructor.set("supportedLocalesOf", new JSNativeFunction("supportedLocalesOf", 1, IntlObject::supportedLocalesOf));
        dateTimeFormatPrototype.set("constructor", dateTimeFormatConstructor);
        intlObject.set("DateTimeFormat", dateTimeFormatConstructor);

        JSObject numberFormatPrototype = context.createJSObject();
        numberFormatPrototype.set("format", new JSNativeFunction("format", 1, IntlObject::numberFormatFormat));
        numberFormatPrototype.set("resolvedOptions", new JSNativeFunction("resolvedOptions", 0, IntlObject::numberFormatResolvedOptions));
        JSNativeFunction numberFormatConstructor = new JSNativeFunction(
                "NumberFormat",
                0,
                (childContext, thisArg, args) -> IntlObject.createNumberFormat(childContext, numberFormatPrototype, args));
        numberFormatConstructor.set("prototype", numberFormatPrototype);
        numberFormatConstructor.set("supportedLocalesOf", new JSNativeFunction("supportedLocalesOf", 1, IntlObject::supportedLocalesOf));
        numberFormatPrototype.set("constructor", numberFormatConstructor);
        intlObject.set("NumberFormat", numberFormatConstructor);

        JSObject collatorPrototype = context.createJSObject();
        collatorPrototype.set("compare", new JSNativeFunction("compare", 2, IntlObject::collatorCompare));
        collatorPrototype.set("resolvedOptions", new JSNativeFunction("resolvedOptions", 0, IntlObject::collatorResolvedOptions));
        JSNativeFunction collatorConstructor = new JSNativeFunction(
                "Collator",
                0,
                (childContext, thisArg, args) -> IntlObject.createCollator(childContext, collatorPrototype, args));
        collatorConstructor.set("prototype", collatorPrototype);
        collatorConstructor.set("supportedLocalesOf", new JSNativeFunction("supportedLocalesOf", 1, IntlObject::supportedLocalesOf));
        collatorPrototype.set("constructor", collatorConstructor);
        intlObject.set("Collator", collatorConstructor);

        JSObject pluralRulesPrototype = context.createJSObject();
        pluralRulesPrototype.set("select", new JSNativeFunction("select", 1, IntlObject::pluralRulesSelect));
        pluralRulesPrototype.set("resolvedOptions", new JSNativeFunction("resolvedOptions", 0, IntlObject::pluralRulesResolvedOptions));
        JSNativeFunction pluralRulesConstructor = new JSNativeFunction(
                "PluralRules",
                0,
                (childContext, thisArg, args) -> IntlObject.createPluralRules(childContext, pluralRulesPrototype, args),
                true,
                true);
        pluralRulesConstructor.set("prototype", pluralRulesPrototype);
        pluralRulesConstructor.set("supportedLocalesOf", new JSNativeFunction("supportedLocalesOf", 1, IntlObject::supportedLocalesOf));
        pluralRulesPrototype.set("constructor", pluralRulesConstructor);
        intlObject.set("PluralRules", pluralRulesConstructor);

        JSObject relativeTimeFormatPrototype = context.createJSObject();
        relativeTimeFormatPrototype.set("format", new JSNativeFunction("format", 2, IntlObject::relativeTimeFormatFormat));
        relativeTimeFormatPrototype.set("resolvedOptions", new JSNativeFunction("resolvedOptions", 0, IntlObject::relativeTimeFormatResolvedOptions));
        JSNativeFunction relativeTimeFormatConstructor = new JSNativeFunction(
                "RelativeTimeFormat",
                0,
                (childContext, thisArg, args) -> IntlObject.createRelativeTimeFormat(childContext, relativeTimeFormatPrototype, args),
                true,
                true);
        relativeTimeFormatConstructor.set("prototype", relativeTimeFormatPrototype);
        relativeTimeFormatConstructor.set("supportedLocalesOf", new JSNativeFunction("supportedLocalesOf", 1, IntlObject::supportedLocalesOf));
        relativeTimeFormatPrototype.set("constructor", relativeTimeFormatConstructor);
        intlObject.set("RelativeTimeFormat", relativeTimeFormatConstructor);

        JSObject listFormatPrototype = context.createJSObject();
        listFormatPrototype.set("format", new JSNativeFunction("format", 1, IntlObject::listFormatFormat));
        listFormatPrototype.set("resolvedOptions", new JSNativeFunction("resolvedOptions", 0, IntlObject::listFormatResolvedOptions));
        JSNativeFunction listFormatConstructor = new JSNativeFunction(
                "ListFormat",
                0,
                (childContext, thisArg, args) -> IntlObject.createListFormat(childContext, listFormatPrototype, args),
                true,
                true);
        listFormatConstructor.set("prototype", listFormatPrototype);
        listFormatConstructor.set("supportedLocalesOf", new JSNativeFunction("supportedLocalesOf", 1, IntlObject::supportedLocalesOf));
        listFormatPrototype.set("constructor", listFormatConstructor);
        intlObject.set("ListFormat", listFormatConstructor);

        JSObject localePrototype = context.createJSObject();
        localePrototype.set("toString", new JSNativeFunction("toString", 0, IntlObject::localeToString));
        localePrototype.defineProperty(
                PropertyKey.fromString("baseName"),
                PropertyDescriptor.accessorDescriptor(
                        new JSNativeFunction("get baseName", 0, IntlObject::localeGetBaseName),
                        null,
                        false,
                        true));
        localePrototype.defineProperty(
                PropertyKey.fromString("language"),
                PropertyDescriptor.accessorDescriptor(
                        new JSNativeFunction("get language", 0, IntlObject::localeGetLanguage),
                        null,
                        false,
                        true));
        localePrototype.defineProperty(
                PropertyKey.fromString("script"),
                PropertyDescriptor.accessorDescriptor(
                        new JSNativeFunction("get script", 0, IntlObject::localeGetScript),
                        null,
                        false,
                        true));
        localePrototype.defineProperty(
                PropertyKey.fromString("region"),
                PropertyDescriptor.accessorDescriptor(
                        new JSNativeFunction("get region", 0, IntlObject::localeGetRegion),
                        null,
                        false,
                        true));
        JSNativeFunction localeConstructor = new JSNativeFunction(
                "Locale",
                1,
                (childContext, thisArg, args) -> IntlObject.createLocale(childContext, localePrototype, args),
                true,
                true);
        localeConstructor.set("prototype", localePrototype);
        localePrototype.set("constructor", localeConstructor);
        intlObject.set("Locale", localeConstructor);

        global.set("Intl", intlObject);
    }

    /**
     * Initialize Map constructor and prototype methods.
     */
    private static void initializeMapConstructor(JSContext context, JSObject global) {
        // Create Map.prototype
        JSObject mapPrototype = context.createJSObject();
        mapPrototype.set("set", new JSNativeFunction("set", 2, MapPrototype::set));
        mapPrototype.set("get", new JSNativeFunction("get", 1, MapPrototype::get));
        mapPrototype.set("has", new JSNativeFunction("has", 1, MapPrototype::has));
        mapPrototype.set("delete", new JSNativeFunction("delete", 1, MapPrototype::delete));
        mapPrototype.set("clear", new JSNativeFunction("clear", 0, MapPrototype::clear));
        mapPrototype.set("forEach", new JSNativeFunction("forEach", 1, MapPrototype::forEach));
        // Create entries function once and use it for both entries and Symbol.iterator (ES spec requirement)
        JSNativeFunction entriesFunction = new JSNativeFunction("entries", 0, IteratorPrototype::mapEntriesIterator);
        mapPrototype.set("entries", entriesFunction);
        mapPrototype.set("keys", new JSNativeFunction("keys", 0, IteratorPrototype::mapKeysIterator));
        mapPrototype.set("values", new JSNativeFunction("values", 0, IteratorPrototype::mapValuesIterator));
        // Map.prototype[Symbol.iterator] is the same function object as entries() (QuickJS uses JS_ALIAS_DEF)
        mapPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), entriesFunction);

        // Map.prototype.size getter
        JSNativeFunction mapSizeGetter = new JSNativeFunction("get size", 0, MapPrototype::getSize);
        mapPrototype.defineProperty(PropertyKey.fromString("size"),
                PropertyDescriptor.accessorDescriptor(mapSizeGetter, null, false, true));

        // Create Map constructor as JSNativeFunction
        JSNativeFunction mapConstructor = new JSNativeFunction("Map", 0, MapConstructor::call, true, true);
        mapConstructor.set("prototype", mapPrototype);
        mapConstructor.setConstructorType(JSConstructorType.MAP); // Mark as Map constructor
        mapPrototype.set("constructor", mapConstructor);

        // Map static methods
        mapConstructor.set("groupBy", new JSNativeFunction("groupBy", 2, MapConstructor::groupBy));

        global.set("Map", mapConstructor);
    }

    /**
     * Initialize Math object.
     */
    private static void initializeMathObject(JSContext context, JSObject global) {
        JSObject math = context.createJSObject();

        // Math constants
        math.set("E", new JSNumber(MathObject.E));
        math.set("LN10", new JSNumber(MathObject.LN10));
        math.set("LN2", new JSNumber(MathObject.LN2));
        math.set("LOG10E", new JSNumber(MathObject.LOG10E));
        math.set("LOG2E", new JSNumber(MathObject.LOG2E));
        math.set("PI", new JSNumber(MathObject.PI));
        math.set("SQRT1_2", new JSNumber(MathObject.SQRT1_2));
        math.set("SQRT2", new JSNumber(MathObject.SQRT2));

        // Math methods
        math.set("abs", new JSNativeFunction("abs", 1, MathObject::abs));
        math.set("acos", new JSNativeFunction("acos", 1, MathObject::acos));
        math.set("acosh", new JSNativeFunction("acosh", 1, MathObject::acosh));
        math.set("asin", new JSNativeFunction("asin", 1, MathObject::asin));
        math.set("asinh", new JSNativeFunction("asinh", 1, MathObject::asinh));
        math.set("atan", new JSNativeFunction("atan", 1, MathObject::atan));
        math.set("atanh", new JSNativeFunction("atanh", 1, MathObject::atanh));
        math.set("atan2", new JSNativeFunction("atan2", 2, MathObject::atan2));
        math.set("cbrt", new JSNativeFunction("cbrt", 1, MathObject::cbrt));
        math.set("ceil", new JSNativeFunction("ceil", 1, MathObject::ceil));
        math.set("clz32", new JSNativeFunction("clz32", 1, MathObject::clz32));
        math.set("cos", new JSNativeFunction("cos", 1, MathObject::cos));
        math.set("cosh", new JSNativeFunction("cosh", 1, MathObject::cosh));
        math.set("exp", new JSNativeFunction("exp", 1, MathObject::exp));
        math.set("expm1", new JSNativeFunction("expm1", 1, MathObject::expm1));
        math.set("floor", new JSNativeFunction("floor", 1, MathObject::floor));
        math.set("fround", new JSNativeFunction("fround", 1, MathObject::fround));
        math.set("hypot", new JSNativeFunction("hypot", 0, MathObject::hypot));
        math.set("imul", new JSNativeFunction("imul", 2, MathObject::imul));
        math.set("log", new JSNativeFunction("log", 1, MathObject::log));
        math.set("log1p", new JSNativeFunction("log1p", 1, MathObject::log1p));
        math.set("log10", new JSNativeFunction("log10", 1, MathObject::log10));
        math.set("log2", new JSNativeFunction("log2", 1, MathObject::log2));
        math.set("max", new JSNativeFunction("max", 2, MathObject::max));
        math.set("min", new JSNativeFunction("min", 2, MathObject::min));
        math.set("pow", new JSNativeFunction("pow", 2, MathObject::pow));
        math.set("random", new JSNativeFunction("random", 0, MathObject::random));
        math.set("round", new JSNativeFunction("round", 1, MathObject::round));
        math.set("sign", new JSNativeFunction("sign", 1, MathObject::sign));
        math.set("sin", new JSNativeFunction("sin", 1, MathObject::sin));
        math.set("sinh", new JSNativeFunction("sinh", 1, MathObject::sinh));
        math.set("sqrt", new JSNativeFunction("sqrt", 1, MathObject::sqrt));
        math.set("tan", new JSNativeFunction("tan", 1, MathObject::tan));
        math.set("tanh", new JSNativeFunction("tanh", 1, MathObject::tanh));
        math.set("trunc", new JSNativeFunction("trunc", 1, MathObject::trunc));

        global.set("Math", math);
    }

    /**
     * Initialize Number constructor and prototype.
     */
    private static void initializeNumberConstructor(JSContext context, JSObject global) {
        // Create Number.prototype
        JSObject numberPrototype = context.createJSObject();
        numberPrototype.defineProperty(
                PropertyKey.fromString("toFixed"),
                PropertyDescriptor.dataDescriptor(new JSNativeFunction("toFixed", 1, NumberPrototype::toFixed), true, false, true));
        numberPrototype.defineProperty(
                PropertyKey.fromString("toExponential"),
                PropertyDescriptor.dataDescriptor(new JSNativeFunction("toExponential", 1, NumberPrototype::toExponential), true, false, true));
        numberPrototype.defineProperty(
                PropertyKey.fromString("toPrecision"),
                PropertyDescriptor.dataDescriptor(new JSNativeFunction("toPrecision", 1, NumberPrototype::toPrecision), true, false, true));
        numberPrototype.defineProperty(
                PropertyKey.fromString("toString"),
                PropertyDescriptor.dataDescriptor(new JSNativeFunction("toString", 1, NumberPrototype::toString), true, false, true));
        numberPrototype.defineProperty(
                PropertyKey.fromString("toLocaleString"),
                PropertyDescriptor.dataDescriptor(new JSNativeFunction("toLocaleString", 0, NumberPrototype::toLocaleString), true, false, true));
        numberPrototype.defineProperty(
                PropertyKey.fromString("valueOf"),
                PropertyDescriptor.dataDescriptor(new JSNativeFunction("valueOf", 0, NumberPrototype::valueOf), true, false, true));

        // Create Number constructor
        JSNativeFunction numberConstructor = new JSNativeFunction("Number", 1, NumberConstructor::call);
        numberConstructor.defineProperty(
                PropertyKey.fromString("prototype"),
                PropertyDescriptor.dataDescriptor(numberPrototype, false, false, false));
        numberConstructor.setConstructorType(JSConstructorType.NUMBER_OBJECT); // Mark as Number constructor
        numberPrototype.defineProperty(
                PropertyKey.fromString("constructor"),
                PropertyDescriptor.dataDescriptor(numberConstructor, true, false, true));

        // Number static methods
        numberConstructor.defineProperty(
                PropertyKey.fromString("isNaN"),
                PropertyDescriptor.dataDescriptor(new JSNativeFunction("isNaN", 1, NumberPrototype::isNaN), true, false, true));
        numberConstructor.defineProperty(
                PropertyKey.fromString("isFinite"),
                PropertyDescriptor.dataDescriptor(new JSNativeFunction("isFinite", 1, NumberPrototype::isFinite), true, false, true));
        numberConstructor.defineProperty(
                PropertyKey.fromString("isInteger"),
                PropertyDescriptor.dataDescriptor(new JSNativeFunction("isInteger", 1, NumberPrototype::isInteger), true, false, true));
        numberConstructor.defineProperty(
                PropertyKey.fromString("isSafeInteger"),
                PropertyDescriptor.dataDescriptor(new JSNativeFunction("isSafeInteger", 1, NumberPrototype::isSafeInteger), true, false, true));

        // QuickJS compatibility: Number.parseInt/parseFloat are aliases of global parseInt/parseFloat.
        JSValue globalParseInt = global.get("parseInt");
        JSValue globalParseFloat = global.get("parseFloat");
        numberConstructor.defineProperty(
                PropertyKey.fromString("parseInt"),
                PropertyDescriptor.dataDescriptor(globalParseInt, true, false, true));
        numberConstructor.defineProperty(
                PropertyKey.fromString("parseFloat"),
                PropertyDescriptor.dataDescriptor(globalParseFloat, true, false, true));
        numberConstructor.defineProperty(
                PropertyKey.fromString("EPSILON"),
                PropertyDescriptor.dataDescriptor(new JSNumber(Math.ulp(1.0)), false, false, false));
        numberConstructor.defineProperty(
                PropertyKey.fromString("MAX_SAFE_INTEGER"),
                PropertyDescriptor.dataDescriptor(new JSNumber(9007199254740991d), false, false, false));
        numberConstructor.defineProperty(
                PropertyKey.fromString("MAX_VALUE"),
                PropertyDescriptor.dataDescriptor(new JSNumber(Double.MAX_VALUE), false, false, false));
        numberConstructor.defineProperty(
                PropertyKey.fromString("MIN_SAFE_INTEGER"),
                PropertyDescriptor.dataDescriptor(new JSNumber(-9007199254740991d), false, false, false));
        numberConstructor.defineProperty(
                PropertyKey.fromString("MIN_VALUE"),
                PropertyDescriptor.dataDescriptor(new JSNumber(Double.MIN_VALUE), false, false, false));
        numberConstructor.defineProperty(
                PropertyKey.fromString("NaN"),
                PropertyDescriptor.dataDescriptor(new JSNumber(Double.NaN), false, false, false));
        numberConstructor.defineProperty(
                PropertyKey.fromString("NEGATIVE_INFINITY"),
                PropertyDescriptor.dataDescriptor(new JSNumber(Double.NEGATIVE_INFINITY), false, false, false));
        numberConstructor.defineProperty(
                PropertyKey.fromString("POSITIVE_INFINITY"),
                PropertyDescriptor.dataDescriptor(new JSNumber(Double.POSITIVE_INFINITY), false, false, false));

        global.set("Number", numberConstructor);
    }

    // Global function implementations

    /**
     * Initialize Object constructor and static methods.
     */
    private static void initializeObjectConstructor(JSContext context, JSObject global) {
        // Create Object.prototype
        JSObject objectPrototype = context.createJSObject();
        objectPrototype.set("hasOwnProperty", new JSNativeFunction("hasOwnProperty", 1, ObjectConstructor::hasOwnProperty));
        objectPrototype.set("toString", new JSNativeFunction("toString", 0, ObjectPrototype::toString));
        objectPrototype.set("valueOf", new JSNativeFunction("valueOf", 0, ObjectPrototype::valueOf));
        objectPrototype.set("isPrototypeOf", new JSNativeFunction("isPrototypeOf", 1, ObjectPrototype::isPrototypeOf));
        objectPrototype.set("propertyIsEnumerable", new JSNativeFunction("propertyIsEnumerable", 1, ObjectPrototype::propertyIsEnumerable));
        objectPrototype.set("toLocaleString", new JSNativeFunction("toLocaleString", 0, ObjectPrototype::toLocaleString));
        objectPrototype.set("__defineGetter__", new JSNativeFunction("__defineGetter__", 2, ObjectPrototype::__defineGetter__));
        objectPrototype.set("__defineSetter__", new JSNativeFunction("__defineSetter__", 2, ObjectPrototype::__defineSetter__));
        objectPrototype.set("__lookupGetter__", new JSNativeFunction("__lookupGetter__", 1, ObjectPrototype::__lookupGetter__));
        objectPrototype.set("__lookupSetter__", new JSNativeFunction("__lookupSetter__", 1, ObjectPrototype::__lookupSetter__));

        // Define __proto__ as an accessor property
        PropertyDescriptor protoDesc = PropertyDescriptor.accessorDescriptor(
                new JSNativeFunction("get __proto__", 0, ObjectPrototype::__proto__Getter),
                new JSNativeFunction("set __proto__", 1, ObjectPrototype::__proto__Setter),
                true,
                true
        );
        objectPrototype.defineProperty(PropertyKey.fromString("__proto__"), protoDesc);

        // Create Object constructor
        JSNativeFunction objectConstructor = new JSNativeFunction("Object", 1, ObjectConstructor::call);
        objectConstructor.set("prototype", objectPrototype);
        objectPrototype.set("constructor", objectConstructor);

        // Object static methods
        objectConstructor.set("keys", new JSNativeFunction("keys", 1, ObjectConstructor::keys));
        objectConstructor.set("values", new JSNativeFunction("values", 1, ObjectConstructor::values));
        objectConstructor.set("entries", new JSNativeFunction("entries", 1, ObjectConstructor::entries));
        objectConstructor.set("fromEntries", new JSNativeFunction("fromEntries", 1, ObjectConstructor::fromEntries));
        objectConstructor.set("assign", new JSNativeFunction("assign", 2, ObjectConstructor::assign));
        objectConstructor.set("create", new JSNativeFunction("create", 2, ObjectConstructor::create));
        objectConstructor.set("defineProperty", new JSNativeFunction("defineProperty", 3, ObjectPrototype::defineProperty));
        objectConstructor.set("defineProperties", new JSNativeFunction("defineProperties", 2, ObjectConstructor::defineProperties));
        objectConstructor.set("getOwnPropertyDescriptor", new JSNativeFunction("getOwnPropertyDescriptor", 2, ObjectConstructor::getOwnPropertyDescriptor));
        objectConstructor.set("getOwnPropertyDescriptors", new JSNativeFunction("getOwnPropertyDescriptors", 1, ObjectConstructor::getOwnPropertyDescriptors));
        objectConstructor.set("getOwnPropertyNames", new JSNativeFunction("getOwnPropertyNames", 1, ObjectConstructor::getOwnPropertyNames));
        objectConstructor.set("getOwnPropertySymbols", new JSNativeFunction("getOwnPropertySymbols", 1, ObjectConstructor::getOwnPropertySymbols));
        objectConstructor.set("getPrototypeOf", new JSNativeFunction("getPrototypeOf", 1, ObjectConstructor::getPrototypeOf));
        objectConstructor.set("setPrototypeOf", new JSNativeFunction("setPrototypeOf", 2, ObjectConstructor::setPrototypeOf));
        objectConstructor.set("freeze", new JSNativeFunction("freeze", 1, ObjectConstructor::freeze));
        objectConstructor.set("seal", new JSNativeFunction("seal", 1, ObjectConstructor::seal));
        objectConstructor.set("preventExtensions", new JSNativeFunction("preventExtensions", 1, ObjectConstructor::preventExtensions));
        objectConstructor.set("isFrozen", new JSNativeFunction("isFrozen", 1, ObjectConstructor::isFrozen));
        objectConstructor.set("isSealed", new JSNativeFunction("isSealed", 1, ObjectConstructor::isSealed));
        objectConstructor.set("isExtensible", new JSNativeFunction("isExtensible", 1, ObjectConstructor::isExtensible));
        objectConstructor.set("is", new JSNativeFunction("is", 2, ObjectConstructor::is));
        objectConstructor.set("hasOwn", new JSNativeFunction("hasOwn", 2, ObjectConstructor::hasOwn));
        objectConstructor.set("groupBy", new JSNativeFunction("groupBy", 2, ObjectConstructor::groupBy));

        global.set("Object", objectConstructor);
    }

    /**
     * Initialize Promise constructor and prototype methods.
     */
    private static void initializePromiseConstructor(JSContext context, JSObject global) {
        // Create Promise.prototype
        JSObject promisePrototype = context.createJSObject();
        promisePrototype.set("then", new JSNativeFunction("then", 2, PromisePrototype::then));
        promisePrototype.set("catch", new JSNativeFunction("catch", 1, PromisePrototype::catchMethod));
        promisePrototype.set("finally", new JSNativeFunction("finally", 1, PromisePrototype::finallyMethod));

        // Create Promise constructor as JSNativeFunction
        JSNativeFunction promiseConstructor = new JSNativeFunction("Promise", 1, PromiseConstructor::call, true, true);
        promiseConstructor.set("prototype", promisePrototype);
        promiseConstructor.setConstructorType(JSConstructorType.PROMISE); // Mark as Promise constructor
        promisePrototype.set("constructor", promiseConstructor);

        // Static methods
        promiseConstructor.set("resolve", new JSNativeFunction("resolve", 1, PromiseConstructor::resolve));
        promiseConstructor.set("reject", new JSNativeFunction("reject", 1, PromiseConstructor::reject));
        promiseConstructor.set("all", new JSNativeFunction("all", 1, PromiseConstructor::all));
        promiseConstructor.set("race", new JSNativeFunction("race", 1, PromiseConstructor::race));
        promiseConstructor.set("allSettled", new JSNativeFunction("allSettled", 1, PromiseConstructor::allSettled));
        promiseConstructor.set("any", new JSNativeFunction("any", 1, PromiseConstructor::any));
        promiseConstructor.set("withResolvers", new JSNativeFunction("withResolvers", 0, PromiseConstructor::withResolvers));

        global.set("Promise", promiseConstructor);
    }

    /**
     * Initialize Proxy constructor.
     */
    private static void initializeProxyConstructor(JSContext context, JSObject global) {
        // Create Proxy constructor as JSNativeFunction
        // Proxy requires 'new' and takes 2 arguments (target, handler)
        JSNativeFunction proxyConstructor = new JSNativeFunction("Proxy", 2, ProxyConstructor::call, true, true);
        proxyConstructor.setConstructorType(JSConstructorType.PROXY);
        context.transferPrototype(proxyConstructor, JSFunction.NAME);

        // Add static methods
        proxyConstructor.set("revocable", new JSNativeFunction("revocable", 2, ProxyConstructor::revocable));

        global.set("Proxy", proxyConstructor);
    }

    /**
     * Initialize Reflect object.
     */
    private static void initializeReflectObject(JSContext context, JSObject global) {
        JSObject reflect = context.createJSObject();
        reflect.set("get", new JSNativeFunction("get", 2, ReflectObject::get));
        reflect.set("set", new JSNativeFunction("set", 3, ReflectObject::set));
        reflect.set("has", new JSNativeFunction("has", 2, ReflectObject::has));
        reflect.set("deleteProperty", new JSNativeFunction("deleteProperty", 2, ReflectObject::deleteProperty));
        reflect.set("getPrototypeOf", new JSNativeFunction("getPrototypeOf", 1, ReflectObject::getPrototypeOf));
        reflect.set("setPrototypeOf", new JSNativeFunction("setPrototypeOf", 2, ReflectObject::setPrototypeOf));
        reflect.set("ownKeys", new JSNativeFunction("ownKeys", 1, ReflectObject::ownKeys));
        reflect.set("apply", new JSNativeFunction("apply", 3, ReflectObject::apply));
        reflect.set("construct", new JSNativeFunction("construct", 2, ReflectObject::construct));
        reflect.set("isExtensible", new JSNativeFunction("isExtensible", 1, ReflectObject::isExtensible));
        reflect.set("preventExtensions", new JSNativeFunction("preventExtensions", 1, ReflectObject::preventExtensions));

        global.set("Reflect", reflect);
    }

    /**
     * Initialize RegExp constructor and prototype.
     */
    private static void initializeRegExpConstructor(JSContext context, JSObject global) {
        // Create RegExp.prototype
        JSObject regexpPrototype = context.createJSObject();
        regexpPrototype.set("test", new JSNativeFunction("test", 1, RegExpPrototype::test));
        regexpPrototype.set("exec", new JSNativeFunction("exec", 1, RegExpPrototype::exec));
        regexpPrototype.set("toString", new JSNativeFunction("toString", 0, RegExpPrototype::toStringMethod));

        // Create RegExp constructor as a function
        JSNativeFunction regexpConstructor = new JSNativeFunction("RegExp", 2, RegExpConstructor::call);
        regexpConstructor.set("prototype", regexpPrototype);
        regexpConstructor.setConstructorType(JSConstructorType.REGEXP);
        regexpPrototype.set("constructor", regexpConstructor);

        global.set("RegExp", regexpConstructor);
    }

    /**
     * Initialize Set constructor and prototype methods.
     */
    private static void initializeSetConstructor(JSContext context, JSObject global) {
        // Create Set.prototype
        JSObject setPrototype = context.createJSObject();
        setPrototype.set("add", new JSNativeFunction("add", 1, SetPrototype::add));
        setPrototype.set("has", new JSNativeFunction("has", 1, SetPrototype::has));
        setPrototype.set("delete", new JSNativeFunction("delete", 1, SetPrototype::delete));
        setPrototype.set("clear", new JSNativeFunction("clear", 0, SetPrototype::clear));
        setPrototype.set("forEach", new JSNativeFunction("forEach", 1, SetPrototype::forEach));
        setPrototype.set("entries", new JSNativeFunction("entries", 0, IteratorPrototype::setEntriesIterator));

        // Create values function - keys and Symbol.iterator will alias to this
        JSNativeFunction valuesFunction = new JSNativeFunction("values", 0, IteratorPrototype::setValuesIterator);
        setPrototype.set("values", valuesFunction);
        // Set.prototype.keys is the same function object as values (ES spec requirement)
        setPrototype.set("keys", valuesFunction);
        // Set.prototype[Symbol.iterator] is the same as values()
        setPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), valuesFunction);

        // Set.prototype.size getter
        JSNativeFunction setSizeGetter = new JSNativeFunction("get size", 0, SetPrototype::getSize);
        setPrototype.defineProperty(PropertyKey.fromString("size"),
                PropertyDescriptor.accessorDescriptor(setSizeGetter, null, false, true));

        // Create Set constructor as a function
        JSNativeFunction setConstructor = new JSNativeFunction("Set", 0, SetConstructor::call, true, true);
        setConstructor.set("prototype", setPrototype);
        setConstructor.setConstructorType(JSConstructorType.SET);
        setPrototype.set("constructor", setConstructor);

        global.set("Set", setConstructor);
    }

    /**
     * Initialize SharedArrayBuffer constructor and prototype.
     */
    private static void initializeSharedArrayBufferConstructor(JSContext context, JSObject global) {
        // Create SharedArrayBuffer.prototype
        JSObject sharedArrayBufferPrototype = context.createJSObject();
        sharedArrayBufferPrototype.set("slice", new JSNativeFunction("slice", 2, SharedArrayBufferPrototype::slice));

        // Define byteLength as a proper getter property
        JSNativeFunction byteLengthGetter = new JSNativeFunction("get byteLength", 0, SharedArrayBufferPrototype::getByteLength);
        PropertyDescriptor byteLengthDesc = PropertyDescriptor.accessorDescriptor(
                byteLengthGetter,  // getter
                null,              // no setter
                false,             // not enumerable
                true               // configurable
        );
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromString("byteLength"), byteLengthDesc);

        // Symbol.toStringTag
        JSNativeFunction toStringTagGetter = new JSNativeFunction("get [Symbol.toStringTag]", 0, SharedArrayBufferPrototype::getToStringTag);
        sharedArrayBufferPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                PropertyDescriptor.accessorDescriptor(toStringTagGetter, null, false, true));

        // Create SharedArrayBuffer constructor as a function
        JSNativeFunction sharedArrayBufferConstructor = new JSNativeFunction(
                "SharedArrayBuffer",
                1,
                SharedArrayBufferConstructor::call,
                true,  // isConstructor
                true   // requiresNew - SharedArrayBuffer() must be called with new
        );
        sharedArrayBufferConstructor.set("prototype", sharedArrayBufferPrototype);
        sharedArrayBufferConstructor.setConstructorType(JSConstructorType.SHARED_ARRAY_BUFFER);
        sharedArrayBufferPrototype.set("constructor", sharedArrayBufferConstructor);

        global.set("SharedArrayBuffer", sharedArrayBufferConstructor);
    }

    /**
     * Initialize String constructor and prototype.
     */
    private static void initializeStringConstructor(JSContext context, JSObject global) {
        // Create String.prototype
        JSObject stringPrototype = context.createJSObject();
        stringPrototype.set("at", new JSNativeFunction("at", 1, StringPrototype::at));
        stringPrototype.set("charAt", new JSNativeFunction("charAt", 1, StringPrototype::charAt));
        stringPrototype.set("charCodeAt", new JSNativeFunction("charCodeAt", 1, StringPrototype::charCodeAt));
        stringPrototype.set("codePointAt", new JSNativeFunction("codePointAt", 1, StringPrototype::codePointAt));
        stringPrototype.set("concat", new JSNativeFunction("concat", 1, StringPrototype::concat));
        stringPrototype.set("endsWith", new JSNativeFunction("endsWith", 1, StringPrototype::endsWith));
        stringPrototype.set("includes", new JSNativeFunction("includes", 1, StringPrototype::includes));
        stringPrototype.set("indexOf", new JSNativeFunction("indexOf", 1, StringPrototype::indexOf));
        stringPrototype.set("lastIndexOf", new JSNativeFunction("lastIndexOf", 1, StringPrototype::lastIndexOf));
        stringPrototype.set("localeCompare", new JSNativeFunction("localeCompare", 1, StringPrototype::localeCompare));
        stringPrototype.set("match", new JSNativeFunction("match", 1, StringPrototype::match));
        stringPrototype.set("matchAll", new JSNativeFunction("matchAll", 1, StringPrototype::matchAll));
        stringPrototype.set("padEnd", new JSNativeFunction("padEnd", 1, StringPrototype::padEnd));
        stringPrototype.set("padStart", new JSNativeFunction("padStart", 1, StringPrototype::padStart));
        stringPrototype.set("repeat", new JSNativeFunction("repeat", 1, StringPrototype::repeat));
        stringPrototype.set("replace", new JSNativeFunction("replace", 2, StringPrototype::replace));
        stringPrototype.set("replaceAll", new JSNativeFunction("replaceAll", 2, StringPrototype::replaceAll));
        stringPrototype.set("search", new JSNativeFunction("search", 1, StringPrototype::search));
        stringPrototype.set("slice", new JSNativeFunction("slice", 2, StringPrototype::slice));
        stringPrototype.set("split", new JSNativeFunction("split", 2, StringPrototype::split));
        stringPrototype.set("startsWith", new JSNativeFunction("startsWith", 1, StringPrototype::startsWith));
        stringPrototype.set("substr", new JSNativeFunction("substr", 2, StringPrototype::substr));
        stringPrototype.set("substring", new JSNativeFunction("substring", 2, StringPrototype::substring));
        stringPrototype.set("toLowerCase", new JSNativeFunction("toLowerCase", 0, StringPrototype::toLowerCase));
        stringPrototype.set("toString", new JSNativeFunction("toString", 0, StringPrototype::toString_));
        stringPrototype.set("toUpperCase", new JSNativeFunction("toUpperCase", 0, StringPrototype::toUpperCase));
        stringPrototype.set("trim", new JSNativeFunction("trim", 0, StringPrototype::trim));
        stringPrototype.set("trimEnd", new JSNativeFunction("trimEnd", 0, StringPrototype::trimEnd));
        stringPrototype.set("trimStart", new JSNativeFunction("trimStart", 0, StringPrototype::trimStart));
        stringPrototype.set("trimLeft", new JSNativeFunction("trimLeft", 0, StringPrototype::trimStart));   // Alias for trimStart
        stringPrototype.set("trimRight", new JSNativeFunction("trimRight", 0, StringPrototype::trimEnd));   // Alias for trimEnd
        stringPrototype.set("valueOf", new JSNativeFunction("valueOf", 0, StringPrototype::valueOf));

        // HTML wrapper methods (deprecated but still part of spec)
        stringPrototype.set("anchor", new JSNativeFunction("anchor", 1, StringPrototype::anchor));
        stringPrototype.set("big", new JSNativeFunction("big", 0, StringPrototype::big));
        stringPrototype.set("blink", new JSNativeFunction("blink", 0, StringPrototype::blink));
        stringPrototype.set("bold", new JSNativeFunction("bold", 0, StringPrototype::bold));
        stringPrototype.set("fixed", new JSNativeFunction("fixed", 0, StringPrototype::fixed));
        stringPrototype.set("fontcolor", new JSNativeFunction("fontcolor", 1, StringPrototype::fontcolor));
        stringPrototype.set("fontsize", new JSNativeFunction("fontsize", 1, StringPrototype::fontsize));
        stringPrototype.set("italics", new JSNativeFunction("italics", 0, StringPrototype::italics));
        stringPrototype.set("link", new JSNativeFunction("link", 1, StringPrototype::link));
        stringPrototype.set("small", new JSNativeFunction("small", 0, StringPrototype::small));
        stringPrototype.set("strike", new JSNativeFunction("strike", 0, StringPrototype::strike));
        stringPrototype.set("sub", new JSNativeFunction("sub", 0, StringPrototype::sub));
        stringPrototype.set("sup", new JSNativeFunction("sup", 0, StringPrototype::sup));

        // Unicode methods
        stringPrototype.set("isWellFormed", new JSNativeFunction("isWellFormed", 0, StringPrototype::isWellFormed));
        stringPrototype.set("normalize", new JSNativeFunction("normalize", 0, StringPrototype::normalize));
        stringPrototype.set("toLocaleLowerCase", new JSNativeFunction("toLocaleLowerCase", 0, StringPrototype::toLocaleLowerCase));
        stringPrototype.set("toLocaleUpperCase", new JSNativeFunction("toLocaleUpperCase", 0, StringPrototype::toLocaleUpperCase));
        stringPrototype.set("toWellFormed", new JSNativeFunction("toWellFormed", 0, StringPrototype::toWellFormed));

        // String.prototype[Symbol.iterator]
        stringPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), new JSNativeFunction("[Symbol.iterator]", 0, IteratorPrototype::stringIterator));

        // String.prototype.length is a data property with value 0 (not writable, not enumerable, not configurable)
        stringPrototype.defineProperty(PropertyKey.fromString("length"),
                PropertyDescriptor.dataDescriptor(new JSNumber(0), false, false, false));

        // Create String constructor
        JSNativeFunction stringConstructor = new JSNativeFunction("String", 1, StringConstructor::call);
        stringConstructor.set("prototype", stringPrototype);
        stringConstructor.setConstructorType(JSConstructorType.STRING_OBJECT); // Mark as String constructor
        stringPrototype.set("constructor", stringConstructor);

        // Add static methods
        stringConstructor.set("fromCharCode", new JSNativeFunction("fromCharCode", 1, StringConstructor::fromCharCode));
        stringConstructor.set("fromCodePoint", new JSNativeFunction("fromCodePoint", 1, StringConstructor::fromCodePoint));
        stringConstructor.set("raw", new JSNativeFunction("raw", 1, StringConstructor::raw));

        global.set("String", stringConstructor);
    }

    /**
     * Initialize Symbol constructor and static methods.
     */
    private static void initializeSymbolConstructor(JSContext context, JSObject global) {
        // Create Symbol.prototype that inherits from Object.prototype
        JSObject symbolPrototype = context.createJSObject();
        context.transferPrototype(symbolPrototype, JSObject.NAME);

        JSNativeFunction symbolToString = new JSNativeFunction("toString", 0, SymbolPrototype::toString);
        symbolToString.initializePrototypeChain(context);
        symbolPrototype.set("toString", symbolToString);

        JSNativeFunction symbolValueOf = new JSNativeFunction("valueOf", 0, SymbolPrototype::valueOf);
        symbolValueOf.initializePrototypeChain(context);
        symbolPrototype.set("valueOf", symbolValueOf);

        // Symbol.prototype.description is a getter
        JSNativeFunction descriptionGetter = new JSNativeFunction("get description", 0, SymbolPrototype::getDescription);
        descriptionGetter.initializePrototypeChain(context);
        symbolPrototype.defineProperty(PropertyKey.fromString("description"),
                PropertyDescriptor.accessorDescriptor(descriptionGetter, null, false, true));

        JSNativeFunction symbolToPrimitive = new JSNativeFunction("[Symbol.toPrimitive]", 1, SymbolPrototype::toPrimitive);
        symbolToPrimitive.initializePrototypeChain(context);
        symbolPrototype.set(PropertyKey.fromSymbol(JSSymbol.TO_PRIMITIVE), symbolToPrimitive);

        symbolPrototype.set(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG), new JSString("Symbol"));

        // Create Symbol constructor
        // Note: Symbol cannot be called with 'new' in JavaScript (throws TypeError)
        // Symbol objects are created using Object(symbolValue) for use with Proxy
        JSNativeFunction symbolConstructor = new JSNativeFunction(JSSymbol.NAME, 0, SymbolConstructor::call);
        symbolConstructor.set("prototype", symbolPrototype);
        symbolConstructor.setConstructorType(JSConstructorType.SYMBOL_OBJECT); // Mark as Symbol constructor
        symbolPrototype.set("constructor", symbolConstructor);

        // Symbol static methods
        symbolConstructor.set("for", new JSNativeFunction("for", 1, SymbolConstructor::symbolFor));
        symbolConstructor.set("keyFor", new JSNativeFunction("keyFor", 1, SymbolConstructor::keyFor));

        // Well-known symbols (ES2015+)
        symbolConstructor.set("iterator", JSSymbol.ITERATOR);
        symbolConstructor.set("asyncIterator", JSSymbol.ASYNC_ITERATOR);
        symbolConstructor.set("toStringTag", JSSymbol.TO_STRING_TAG);
        symbolConstructor.set("hasInstance", JSSymbol.HAS_INSTANCE);
        symbolConstructor.set("isConcatSpreadable", JSSymbol.IS_CONCAT_SPREADABLE);
        symbolConstructor.set("toPrimitive", JSSymbol.TO_PRIMITIVE);
        symbolConstructor.set("match", JSSymbol.MATCH);
        symbolConstructor.set("matchAll", JSSymbol.MATCH_ALL);
        symbolConstructor.set("replace", JSSymbol.REPLACE);
        symbolConstructor.set("search", JSSymbol.SEARCH);
        symbolConstructor.set("split", JSSymbol.SPLIT);
        symbolConstructor.set("species", JSSymbol.SPECIES);
        symbolConstructor.set("unscopables", JSSymbol.UNSCOPABLES);
        symbolConstructor.set("dispose", JSSymbol.DISPOSE);
        symbolConstructor.set("asyncDispose", JSSymbol.ASYNC_DISPOSE);

        global.set("Symbol", symbolConstructor);
    }

    /**
     * Initialize all TypedArray constructors.
     */
    private static void initializeTypedArrayConstructors(JSContext context, JSObject global) {
        // Int8Array
        JSObject int8ArrayPrototype = context.createJSObject();
        int8ArrayPrototype.set("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
        JSNativeFunction int8ArrayConstructor = new JSNativeFunction("Int8Array", 3, Int8ArrayConstructor::call, true, true);
        int8ArrayConstructor.set("prototype", int8ArrayPrototype);
        int8ArrayPrototype.set("constructor", int8ArrayConstructor);
        int8ArrayConstructor.setConstructorType(JSConstructorType.TYPED_ARRAY_INT8);
        int8ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(JSInt8Array.BYTES_PER_ELEMENT));
        int8ArrayPrototype.set("BYTES_PER_ELEMENT", new JSNumber(JSInt8Array.BYTES_PER_ELEMENT));
        global.set("Int8Array", int8ArrayConstructor);

        // Uint8Array
        JSObject uint8ArrayPrototype = context.createJSObject();
        uint8ArrayPrototype.set("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
        JSNativeFunction uint8ArrayConstructor = new JSNativeFunction("Uint8Array", 3, Uint8ArrayConstructor::call, true, true);
        uint8ArrayConstructor.set("prototype", uint8ArrayPrototype);
        uint8ArrayPrototype.set("constructor", uint8ArrayConstructor);
        uint8ArrayConstructor.setConstructorType(JSConstructorType.TYPED_ARRAY_UINT8);
        uint8ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(JSUint8Array.BYTES_PER_ELEMENT));
        uint8ArrayPrototype.set("BYTES_PER_ELEMENT", new JSNumber(JSUint8Array.BYTES_PER_ELEMENT));
        global.set("Uint8Array", uint8ArrayConstructor);

        // Uint8ClampedArray
        JSObject uint8ClampedArrayPrototype = context.createJSObject();
        uint8ClampedArrayPrototype.set("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
        JSNativeFunction uint8ClampedArrayConstructor = new JSNativeFunction("Uint8ClampedArray", 3, Uint8ClampedArrayConstructor::call, true, true);
        uint8ClampedArrayConstructor.set("prototype", uint8ClampedArrayPrototype);
        uint8ClampedArrayPrototype.set("constructor", uint8ClampedArrayConstructor);
        uint8ClampedArrayConstructor.setConstructorType(JSConstructorType.TYPED_ARRAY_UINT8_CLAMPED);
        uint8ClampedArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(JSUint8ClampedArray.BYTES_PER_ELEMENT));
        uint8ClampedArrayPrototype.set("BYTES_PER_ELEMENT", new JSNumber(JSUint8ClampedArray.BYTES_PER_ELEMENT));
        global.set("Uint8ClampedArray", uint8ClampedArrayConstructor);

        // Int16Array
        JSObject int16ArrayPrototype = context.createJSObject();
        int16ArrayPrototype.set("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
        JSNativeFunction int16ArrayConstructor = new JSNativeFunction("Int16Array", 3, Int16ArrayConstructor::call, true, true);
        int16ArrayConstructor.set("prototype", int16ArrayPrototype);
        int16ArrayPrototype.set("constructor", int16ArrayConstructor);
        int16ArrayConstructor.setConstructorType(JSConstructorType.TYPED_ARRAY_INT16);
        int16ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(JSInt16Array.BYTES_PER_ELEMENT));
        int16ArrayPrototype.set("BYTES_PER_ELEMENT", new JSNumber(JSInt16Array.BYTES_PER_ELEMENT));
        global.set("Int16Array", int16ArrayConstructor);

        // Uint16Array
        JSObject uint16ArrayPrototype = context.createJSObject();
        uint16ArrayPrototype.set("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
        JSNativeFunction uint16ArrayConstructor = new JSNativeFunction("Uint16Array", 3, Uint16ArrayConstructor::call, true, true);
        uint16ArrayConstructor.set("prototype", uint16ArrayPrototype);
        uint16ArrayPrototype.set("constructor", uint16ArrayConstructor);
        uint16ArrayConstructor.setConstructorType(JSConstructorType.TYPED_ARRAY_UINT16);
        uint16ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(JSUint16Array.BYTES_PER_ELEMENT));
        uint16ArrayPrototype.set("BYTES_PER_ELEMENT", new JSNumber(JSUint16Array.BYTES_PER_ELEMENT));
        global.set("Uint16Array", uint16ArrayConstructor);

        // Int32Array
        JSObject int32ArrayPrototype = context.createJSObject();
        int32ArrayPrototype.set("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
        JSNativeFunction int32ArrayConstructor = new JSNativeFunction("Int32Array", 3, Int32ArrayConstructor::call, true, true);
        int32ArrayConstructor.set("prototype", int32ArrayPrototype);
        int32ArrayPrototype.set("constructor", int32ArrayConstructor);
        int32ArrayConstructor.setConstructorType(JSConstructorType.TYPED_ARRAY_INT32);
        int32ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(JSInt32Array.BYTES_PER_ELEMENT));
        int32ArrayPrototype.set("BYTES_PER_ELEMENT", new JSNumber(JSInt32Array.BYTES_PER_ELEMENT));
        global.set("Int32Array", int32ArrayConstructor);

        // Uint32Array
        JSObject uint32ArrayPrototype = context.createJSObject();
        uint32ArrayPrototype.set("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
        JSNativeFunction uint32ArrayConstructor = new JSNativeFunction("Uint32Array", 3, Uint32ArrayConstructor::call, true, true);
        uint32ArrayConstructor.set("prototype", uint32ArrayPrototype);
        uint32ArrayPrototype.set("constructor", uint32ArrayConstructor);
        uint32ArrayConstructor.setConstructorType(JSConstructorType.TYPED_ARRAY_UINT32);
        uint32ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(JSUint32Array.BYTES_PER_ELEMENT));
        uint32ArrayPrototype.set("BYTES_PER_ELEMENT", new JSNumber(JSUint32Array.BYTES_PER_ELEMENT));
        global.set("Uint32Array", uint32ArrayConstructor);

        // Float16Array
        JSObject float16ArrayPrototype = context.createJSObject();
        float16ArrayPrototype.set("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
        JSNativeFunction float16ArrayConstructor = new JSNativeFunction("Float16Array", 3, Float16ArrayConstructor::call, true, true);
        float16ArrayConstructor.set("prototype", float16ArrayPrototype);
        float16ArrayPrototype.set("constructor", float16ArrayConstructor);
        float16ArrayConstructor.setConstructorType(JSConstructorType.TYPED_ARRAY_FLOAT16);
        float16ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(JSFloat16Array.BYTES_PER_ELEMENT));
        float16ArrayPrototype.set("BYTES_PER_ELEMENT", new JSNumber(JSFloat16Array.BYTES_PER_ELEMENT));
        global.set("Float16Array", float16ArrayConstructor);

        // Float32Array
        JSObject float32ArrayPrototype = context.createJSObject();
        float32ArrayPrototype.set("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
        JSNativeFunction float32ArrayConstructor = new JSNativeFunction("Float32Array", 3, Float32ArrayConstructor::call, true, true);
        float32ArrayConstructor.set("prototype", float32ArrayPrototype);
        float32ArrayPrototype.set("constructor", float32ArrayConstructor);
        float32ArrayConstructor.setConstructorType(JSConstructorType.TYPED_ARRAY_FLOAT32);
        float32ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(JSFloat32Array.BYTES_PER_ELEMENT));
        float32ArrayPrototype.set("BYTES_PER_ELEMENT", new JSNumber(JSFloat32Array.BYTES_PER_ELEMENT));
        global.set("Float32Array", float32ArrayConstructor);

        // Float64Array
        JSObject float64ArrayPrototype = context.createJSObject();
        float64ArrayPrototype.set("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
        JSNativeFunction float64ArrayConstructor = new JSNativeFunction("Float64Array", 3, Float64ArrayConstructor::call, true, true);
        float64ArrayConstructor.set("prototype", float64ArrayPrototype);
        float64ArrayPrototype.set("constructor", float64ArrayConstructor);
        float64ArrayConstructor.setConstructorType(JSConstructorType.TYPED_ARRAY_FLOAT64);
        float64ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(JSFloat64Array.BYTES_PER_ELEMENT));
        float64ArrayPrototype.set("BYTES_PER_ELEMENT", new JSNumber(JSFloat64Array.BYTES_PER_ELEMENT));
        global.set("Float64Array", float64ArrayConstructor);

        // BigInt64Array
        JSObject bigInt64ArrayPrototype = context.createJSObject();
        bigInt64ArrayPrototype.set("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
        JSNativeFunction bigInt64ArrayConstructor = new JSNativeFunction("BigInt64Array", 3, BigInt64ArrayConstructor::call, true, true);
        bigInt64ArrayConstructor.set("prototype", bigInt64ArrayPrototype);
        bigInt64ArrayPrototype.set("constructor", bigInt64ArrayConstructor);
        bigInt64ArrayConstructor.setConstructorType(JSConstructorType.TYPED_ARRAY_BIGINT64);
        bigInt64ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(JSBigInt64Array.BYTES_PER_ELEMENT));
        bigInt64ArrayPrototype.set("BYTES_PER_ELEMENT", new JSNumber(JSBigInt64Array.BYTES_PER_ELEMENT));
        global.set("BigInt64Array", bigInt64ArrayConstructor);

        // BigUint64Array
        JSObject bigUint64ArrayPrototype = context.createJSObject();
        bigUint64ArrayPrototype.set("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
        JSNativeFunction bigUint64ArrayConstructor = new JSNativeFunction("BigUint64Array", 3, BigUint64ArrayConstructor::call, true, true);
        bigUint64ArrayConstructor.set("prototype", bigUint64ArrayPrototype);
        bigUint64ArrayPrototype.set("constructor", bigUint64ArrayConstructor);
        bigUint64ArrayConstructor.setConstructorType(JSConstructorType.TYPED_ARRAY_BIGUINT64);
        bigUint64ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(JSBigUint64Array.BYTES_PER_ELEMENT));
        bigUint64ArrayPrototype.set("BYTES_PER_ELEMENT", new JSNumber(JSBigUint64Array.BYTES_PER_ELEMENT));
        global.set("BigUint64Array", bigUint64ArrayConstructor);
    }

    /**
     * Initialize WeakMap constructor and prototype methods.
     */
    private static void initializeWeakMapConstructor(JSContext context, JSObject global) {
        // Create WeakMap.prototype
        JSObject weakMapPrototype = context.createJSObject();
        weakMapPrototype.set("set", new JSNativeFunction("set", 2, WeakMapPrototype::set));
        weakMapPrototype.set("get", new JSNativeFunction("get", 1, WeakMapPrototype::get));
        weakMapPrototype.set("has", new JSNativeFunction("has", 1, WeakMapPrototype::has));
        weakMapPrototype.set("delete", new JSNativeFunction("delete", 1, WeakMapPrototype::delete));

        // Symbol.toStringTag
        JSNativeFunction toStringTagGetter = new JSNativeFunction("get [Symbol.toStringTag]", 0, WeakMapPrototype::getToStringTag);
        weakMapPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                PropertyDescriptor.accessorDescriptor(toStringTagGetter, null, false, true));

        // Create WeakMap constructor as a function
        JSNativeFunction weakMapConstructor = new JSNativeFunction(
                "WeakMap",
                0,
                WeakMapConstructor::call,
                true,  // isConstructor
                true   // requiresNew - WeakMap() must be called with new
        );
        weakMapConstructor.set("prototype", weakMapPrototype);
        weakMapConstructor.setConstructorType(JSConstructorType.WEAK_MAP);
        weakMapPrototype.set("constructor", weakMapConstructor);

        global.set("WeakMap", weakMapConstructor);
    }

    /**
     * Initialize WeakRef constructor.
     */
    private static void initializeWeakRefConstructor(JSContext context, JSObject global) {
        // Create WeakRef.prototype
        JSObject weakRefPrototype = context.createJSObject();
        // deref() method is added in JSWeakRef constructor

        // Symbol.toStringTag
        JSNativeFunction toStringTagGetter = new JSNativeFunction("get [Symbol.toStringTag]", 0, WeakRefPrototype::getToStringTag);
        weakRefPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                PropertyDescriptor.accessorDescriptor(toStringTagGetter, null, false, true));

        // Create WeakRef constructor as a function
        JSNativeFunction weakRefConstructor = new JSNativeFunction(
                "WeakRef",
                1,
                WeakRefConstructor::call,
                true,  // isConstructor
                true   // requiresNew - WeakRef() must be called with new
        );
        weakRefConstructor.set("prototype", weakRefPrototype);
        weakRefConstructor.setConstructorType(JSConstructorType.WEAK_REF);
        weakRefPrototype.set("constructor", weakRefConstructor);

        global.set("WeakRef", weakRefConstructor);
    }

    /**
     * Initialize WeakSet constructor and prototype methods.
     */
    private static void initializeWeakSetConstructor(JSContext context, JSObject global) {
        // Create WeakSet.prototype
        JSObject weakSetPrototype = context.createJSObject();
        weakSetPrototype.set("add", new JSNativeFunction("add", 1, WeakSetPrototype::add));
        weakSetPrototype.set("has", new JSNativeFunction("has", 1, WeakSetPrototype::has));
        weakSetPrototype.set("delete", new JSNativeFunction("delete", 1, WeakSetPrototype::delete));

        // Symbol.toStringTag
        JSNativeFunction toStringTagGetter = new JSNativeFunction("get [Symbol.toStringTag]", 0, WeakSetPrototype::getToStringTag);
        weakSetPrototype.defineProperty(PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                PropertyDescriptor.accessorDescriptor(toStringTagGetter, null, false, true));

        // Create WeakSet constructor as a function
        JSNativeFunction weakSetConstructor = new JSNativeFunction(
                "WeakSet",
                0,
                WeakSetConstructor::call,
                true,  // isConstructor
                true   // requiresNew - WeakSet() must be called with new
        );
        weakSetConstructor.set("prototype", weakSetPrototype);
        weakSetConstructor.setConstructorType(JSConstructorType.WEAK_SET);
        weakSetPrototype.set("constructor", weakSetConstructor);

        global.set("WeakSet", weakSetConstructor);
    }

    /**
     * isFinite(value)
     * Determine whether a value is a finite number.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-isfinite-number">ECMAScript isFinite</a>
     */
    public static JSValue isFinite(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        double num = JSTypeConversions.toNumber(context, value).value();
        return JSBoolean.valueOf(!Double.isNaN(num) && !Double.isInfinite(num));
    }

    /**
     * isNaN(value)
     * Determine whether a value is NaN.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-isnan-number">ECMAScript isNaN</a>
     */
    public static JSValue isNaN(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        double num = JSTypeConversions.toNumber(context, value).value();
        return JSBoolean.valueOf(Double.isNaN(num));
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

    /**
     * parseFloat(string)
     * Parse a string and return a floating point number.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-parsefloat-string">ECMAScript parseFloat</a>
     */
    public static JSValue parseFloat(JSContext context, JSValue thisArg, JSValue[] args) {
        // Get input string
        JSValue input = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String inputString = JSTypeConversions.toString(context, input).value().trim();

        if (inputString.isEmpty()) {
            return new JSNumber(Double.NaN);
        }

        // Try to parse as double
        try {
            // Handle Infinity
            if (inputString.startsWith("Infinity") || inputString.startsWith("+Infinity")) {
                return new JSNumber(Double.POSITIVE_INFINITY);
            }
            if (inputString.startsWith("-Infinity")) {
                return new JSNumber(Double.NEGATIVE_INFINITY);
            }

            // Parse as much as possible from the beginning
            StringBuilder validPart = new StringBuilder();
            boolean hasDecimal = false;
            boolean hasExponent = false;
            int i = 0;

            // Sign
            if (i < inputString.length() && (inputString.charAt(i) == '+' || inputString.charAt(i) == '-')) {
                validPart.append(inputString.charAt(i));
                i++;
            }

            // Digits, decimal point, exponent
            while (i < inputString.length()) {
                char c = inputString.charAt(i);
                if (Character.isDigit(c)) {
                    validPart.append(c);
                } else if (c == '.' && !hasDecimal && !hasExponent) {
                    validPart.append(c);
                    hasDecimal = true;
                } else if ((c == 'e' || c == 'E') && !hasExponent && validPart.length() > 0) {
                    validPart.append(c);
                    hasExponent = true;
                    // Check for exponent sign
                    if (i + 1 < inputString.length() &&
                            (inputString.charAt(i + 1) == '+' || inputString.charAt(i + 1) == '-')) {
                        i++;
                        validPart.append(inputString.charAt(i));
                    }
                } else {
                    break;
                }
                i++;
            }

            if (validPart.length() == 0 || validPart.toString().equals("+") || validPart.toString().equals("-")) {
                return new JSNumber(Double.NaN);
            }

            double result = Double.parseDouble(validPart.toString());
            return new JSNumber(result);
        } catch (NumberFormatException e) {
            return new JSNumber(Double.NaN);
        }
    }

    /**
     * parseInt(string, radix)
     * Parse a string and return an integer of the specified radix.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-parseint-string-radix">ECMAScript parseInt</a>
     */
    public static JSValue parseInt(JSContext context, JSValue thisArg, JSValue[] args) {
        // Get input string
        JSValue input = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String inputString = JSTypeConversions.toString(context, input).value().trim();

        // Get radix
        int radix = 0;
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            double radixNum = JSTypeConversions.toNumber(context, args[1]).value();
            radix = (int) radixNum;
        }

        // Handle empty string
        if (inputString.isEmpty()) {
            return new JSNumber(Double.NaN);
        }

        // Determine sign
        int sign = 1;
        int index = 0;
        char firstChar = inputString.charAt(0);
        if (firstChar == '+') {
            index = 1;
        } else if (firstChar == '-') {
            sign = -1;
            index = 1;
        }

        // Auto-detect radix 16 for 0x/0X prefix (after optional sign).
        if (radix == 0 || radix == 16) {
            if (index + 1 < inputString.length() &&
                    inputString.charAt(index) == '0' &&
                    (inputString.charAt(index + 1) == 'x' || inputString.charAt(index + 1) == 'X')) {
                radix = 16;
                index += 2;
            }
        }
        if (radix == 0) {
            radix = 10;
        }

        // Validate radix
        if (radix < 2 || radix > 36) {
            return new JSNumber(Double.NaN);
        }

        // Parse digits using double accumulation to match JavaScript number range.
        double result = 0.0;
        boolean foundDigit = false;
        while (index < inputString.length()) {
            char c = inputString.charAt(index);
            int digit = Character.digit(c, radix);
            if (digit == -1) {
                break; // Stop at first invalid character
            }
            result = result * radix + digit;
            foundDigit = true;
            index++;
        }

        if (!foundDigit) {
            return new JSNumber(Double.NaN);
        }

        return new JSNumber(sign * result);
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
}
