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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
    public static JSValue consoleError(JSContext ctx, JSValue thisArg, JSValue[] args) {
        System.err.print("[ERROR] ");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) System.err.print(" ");
            System.err.print(formatValue(args[i]));
        }
        System.err.println();
        return JSUndefined.INSTANCE;
    }

    /**
     * console.log(...args)
     * Print values to standard output.
     */
    public static JSValue consoleLog(JSContext ctx, JSValue thisArg, JSValue[] args) {
        for (int i = 0; i < args.length; i++) {
            if (i > 0) System.out.print(" ");
            System.out.print(formatValue(args[i]));
        }
        System.out.println();
        return JSUndefined.INSTANCE;
    }

    /**
     * console.warn(...args)
     * Print warning to standard error.
     */
    public static JSValue consoleWarn(JSContext ctx, JSValue thisArg, JSValue[] args) {
        System.err.print("[WARN] ");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) System.err.print(" ");
            System.err.print(formatValue(args[i]));
        }
        System.err.println();
        return JSUndefined.INSTANCE;
    }

    /**
     * Create an Error constructor.
     */
    private static JSObject createErrorConstructor(JSContext ctx, String errorName) {
        // Error.prototype
        JSObject errorPrototype = new JSObject();
        errorPrototype.set("name", new JSString(errorName));
        errorPrototype.set("message", new JSString(""));
        errorPrototype.set("toString", new JSNativeFunction("toString", 0, GlobalObject::errorToString));

        // For now, Error constructor is a placeholder (like Array, String, etc.)
        JSObject errorConstructor = new JSObject();
        errorConstructor.set("prototype", errorPrototype);
        // Store error name for constructor use
        errorConstructor.set("[[ErrorName]]", new JSString(errorName));

        return errorConstructor;
    }

    /**
     * decodeURI(encodedURI)
     * Decode a URI that was encoded by encodeURI.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-decodeuri-encodeduri">ECMAScript decodeURI</a>
     */
    public static JSValue decodeURI(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue encodedValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String encodedString = JSTypeConversions.toString(encodedValue).value();

        try {
            String decoded = URLDecoder.decode(encodedString, StandardCharsets.UTF_8);
            return new JSString(decoded);
        } catch (Exception e) {
            return ctx.throwError("URIError", "URI malformed");
        }
    }

    /**
     * decodeURIComponent(encodedURIComponent)
     * Decode a URI component that was encoded by encodeURIComponent.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-decodeuricomponent-encodeduricomponent">ECMAScript decodeURIComponent</a>
     */
    public static JSValue decodeURIComponent(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue encodedValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String encodedString = JSTypeConversions.toString(encodedValue).value();

        try {
            String decoded = URLDecoder.decode(encodedString, StandardCharsets.UTF_8);
            return new JSString(decoded);
        } catch (Exception e) {
            return ctx.throwError("URIError", "URI malformed");
        }
    }

    /**
     * encodeURI(uri)
     * Encode a URI by escaping certain characters.
     * Does not encode: A-Z a-z 0-9 ; , / ? : @ & = + $ - _ . ! ~ * ' ( ) #
     *
     * @see <a href="https://tc39.es/ecma262/#sec-encodeuri-uri">ECMAScript encodeURI</a>
     */
    public static JSValue encodeURI(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue uriValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String uriString = JSTypeConversions.toString(uriValue).value();

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
            return ctx.throwError("URIError", "URI malformed");
        }
    }

    /**
     * encodeURIComponent(uriComponent)
     * Encode a URI component by escaping certain characters.
     * More aggressive than encodeURI - also encodes: ; , / ? : @ & = + $ #
     *
     * @see <a href="https://tc39.es/ecma262/#sec-encodeuricomponent-uricomponent">ECMAScript encodeURIComponent</a>
     */
    public static JSValue encodeURIComponent(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue componentValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String componentString = JSTypeConversions.toString(componentValue).value();

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
            return ctx.throwError("URIError", "URI malformed");
        }
    }

    /**
     * Error.prototype.toString()
     * Converts an Error object to a string.
     */
    public static JSValue errorToString(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSObject error)) {
            return new JSString("[object Object]");
        }

        JSValue nameValue = error.get("name");
        JSValue messageValue = error.get("message");

        String name = nameValue instanceof JSString ? ((JSString) nameValue).value() : "Error";
        String message = messageValue instanceof JSString ? ((JSString) messageValue).value() : "";

        if (message.isEmpty()) {
            return new JSString(name);
        }

        return new JSString(name + ": " + message);
    }

    /**
     * eval(code)
     * Evaluate JavaScript code in the current context.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-eval-x">ECMAScript eval</a>
     */
    public static JSValue eval(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue x = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // If x is not a string, return it unchanged
        if (!(x instanceof JSString)) {
            return x;
        }

        String code = ((JSString) x).value();
        return ctx.eval(code);
    }

    /**
     * Format a value for console output.
     */
    private static String formatValue(JSValue value) {
        if (value instanceof JSUndefined) {
            return "undefined";
        }
        if (value instanceof JSNull) {
            return "null";
        }
        if (value instanceof JSBoolean b) {
            return String.valueOf(b.value());
        }
        if (value instanceof JSNumber n) {
            return JSTypeConversions.toString(value).value();
        }
        if (value instanceof JSString s) {
            return s.value();
        }
        if (value instanceof JSArray arr) {
            StringBuilder sb = new StringBuilder("[");
            for (long i = 0; i < arr.getLength(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatValue(arr.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof JSObject) {
            return "[object Object]";
        }
        return String.valueOf(value);
    }

    /**
     * Initialize the global object with all built-in global properties and functions.
     */
    public static void initialize(JSContext ctx, JSObject global) {
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
        global.set("queueMicrotask", new JSNativeFunction("queueMicrotask", 1, GlobalObject::queueMicrotask));

        // URI handling functions
        global.set("encodeURI", new JSNativeFunction("encodeURI", 1, GlobalObject::encodeURI));
        global.set("decodeURI", new JSNativeFunction("decodeURI", 1, GlobalObject::decodeURI));
        global.set("encodeURIComponent", new JSNativeFunction("encodeURIComponent", 1, GlobalObject::encodeURIComponent));
        global.set("decodeURIComponent", new JSNativeFunction("decodeURIComponent", 1, GlobalObject::decodeURIComponent));

        // Console object for debugging
        initializeConsoleObject(ctx, global);

        // Global this reference
        global.set("globalThis", global);

        // Built-in constructors and their prototypes
        initializeObjectConstructor(ctx, global);
        initializeBooleanConstructor(ctx, global);
        initializeArrayConstructor(ctx, global);
        initializeStringConstructor(ctx, global);
        initializeNumberConstructor(ctx, global);
        initializeFunctionConstructor(ctx, global);
        initializeDateConstructor(ctx, global);
        initializeRegExpConstructor(ctx, global);
        initializeSymbolConstructor(ctx, global);
        initializeBigIntConstructor(ctx, global);
        initializeMapConstructor(ctx, global);
        initializeSetConstructor(ctx, global);
        initializeWeakMapConstructor(ctx, global);
        initializeWeakSetConstructor(ctx, global);
        initializeWeakRefConstructor(ctx, global);
        initializeFinalizationRegistryConstructor(ctx, global);
        initializeMathObject(ctx, global);
        initializeJSONObject(ctx, global);
        initializeReflectObject(ctx, global);
        initializeProxyConstructor(ctx, global);
        initializePromiseConstructor(ctx, global);
        initializeGeneratorPrototype(ctx, global);

        // Binary data constructors
        initializeArrayBufferConstructor(ctx, global);
        initializeSharedArrayBufferConstructor(ctx, global);
        initializeDataViewConstructor(ctx, global);
        initializeTypedArrayConstructors(ctx, global);
        initializeAtomicsObject(ctx, global);

        // Error constructors
        initializeErrorConstructors(ctx, global);
    }

    /**
     * Initialize ArrayBuffer constructor and prototype.
     */
    private static void initializeArrayBufferConstructor(JSContext ctx, JSObject global) {
        // Create ArrayBuffer.prototype
        JSObject arrayBufferPrototype = new JSObject();
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
        arrayBufferPrototype.set(
                PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                new JSNativeFunction("get [Symbol.toStringTag]", 0, ArrayBufferPrototype::getToStringTag));

        // Create ArrayBuffer constructor
        JSObject arrayBufferConstructor = new JSObject();
        arrayBufferConstructor.set("prototype", arrayBufferPrototype);
        arrayBufferConstructor.set("[[ArrayBufferConstructor]]", JSBoolean.TRUE);

        // Static methods
        arrayBufferConstructor.set("isView", new JSNativeFunction("isView", 1, ArrayBufferConstructor::isView));

        // Symbol.species getter
        arrayBufferConstructor.set(
                PropertyKey.fromSymbol(JSSymbol.SPECIES),
                new JSNativeFunction("get [Symbol.species]", 0, ArrayBufferConstructor::getSpecies));

        global.set("ArrayBuffer", arrayBufferConstructor);
    }

    /**
     * Initialize Array constructor and prototype.
     */
    private static void initializeArrayConstructor(JSContext ctx, JSObject global) {
        // Create Array.prototype
        JSObject arrayPrototype = new JSObject();
        arrayPrototype.set("push", new JSNativeFunction("push", 1, ArrayPrototype::push));
        arrayPrototype.set("pop", new JSNativeFunction("pop", 0, ArrayPrototype::pop));
        arrayPrototype.set("shift", new JSNativeFunction("shift", 0, ArrayPrototype::shift));
        arrayPrototype.set("unshift", new JSNativeFunction("unshift", 1, ArrayPrototype::unshift));
        arrayPrototype.set("slice", new JSNativeFunction("slice", 2, ArrayPrototype::slice));
        arrayPrototype.set("splice", new JSNativeFunction("splice", 2, ArrayPrototype::splice));
        arrayPrototype.set("toSpliced", new JSNativeFunction("toSpliced", 2, ArrayPrototype::toSpliced));
        arrayPrototype.set("with", new JSNativeFunction("with", 2, ArrayPrototype::with));
        arrayPrototype.set("concat", new JSNativeFunction("concat", 1, ArrayPrototype::concat));
        arrayPrototype.set("join", new JSNativeFunction("join", 1, ArrayPrototype::join));
        arrayPrototype.set("reverse", new JSNativeFunction("reverse", 0, ArrayPrototype::reverse));
        arrayPrototype.set("toReversed", new JSNativeFunction("toReversed", 0, ArrayPrototype::toReversed));
        arrayPrototype.set("sort", new JSNativeFunction("sort", 1, ArrayPrototype::sort));
        arrayPrototype.set("toSorted", new JSNativeFunction("toSorted", 1, ArrayPrototype::toSorted));
        arrayPrototype.set("indexOf", new JSNativeFunction("indexOf", 1, ArrayPrototype::indexOf));
        arrayPrototype.set("lastIndexOf", new JSNativeFunction("lastIndexOf", 1, ArrayPrototype::lastIndexOf));
        arrayPrototype.set("includes", new JSNativeFunction("includes", 1, ArrayPrototype::includes));
        arrayPrototype.set("at", new JSNativeFunction("at", 1, ArrayPrototype::at));
        arrayPrototype.set("map", new JSNativeFunction("map", 1, ArrayPrototype::map));
        arrayPrototype.set("filter", new JSNativeFunction("filter", 1, ArrayPrototype::filter));
        arrayPrototype.set("reduce", new JSNativeFunction("reduce", 1, ArrayPrototype::reduce));
        arrayPrototype.set("reduceRight", new JSNativeFunction("reduceRight", 1, ArrayPrototype::reduceRight));
        arrayPrototype.set("forEach", new JSNativeFunction("forEach", 1, ArrayPrototype::forEach));
        // Array.prototype.values and Array.prototype[Symbol.iterator] should be the same function
        JSNativeFunction valuesFunction = new JSNativeFunction("values", 0, IteratorPrototype::arrayValues);
        arrayPrototype.set("values", valuesFunction);
        arrayPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), valuesFunction);
        arrayPrototype.set("keys", new JSNativeFunction("keys", 0, IteratorPrototype::arrayKeys));
        arrayPrototype.set("entries", new JSNativeFunction("entries", 0, IteratorPrototype::arrayEntries));
        arrayPrototype.set("find", new JSNativeFunction("find", 1, ArrayPrototype::find));
        arrayPrototype.set("findIndex", new JSNativeFunction("findIndex", 1, ArrayPrototype::findIndex));
        arrayPrototype.set("findLast", new JSNativeFunction("findLast", 1, ArrayPrototype::findLast));
        arrayPrototype.set("findLastIndex", new JSNativeFunction("findLastIndex", 1, ArrayPrototype::findLastIndex));
        arrayPrototype.set("every", new JSNativeFunction("every", 1, ArrayPrototype::every));
        arrayPrototype.set("some", new JSNativeFunction("some", 1, ArrayPrototype::some));
        arrayPrototype.set("flat", new JSNativeFunction("flat", 0, ArrayPrototype::flat));
        arrayPrototype.set("flatMap", new JSNativeFunction("flatMap", 1, ArrayPrototype::flatMap));
        arrayPrototype.set("toString", new JSNativeFunction("toString", 0, ArrayPrototype::toString));
        arrayPrototype.set("toLocaleString", new JSNativeFunction("toLocaleString", 0, ArrayPrototype::toLocaleString));
        arrayPrototype.set("copyWithin", new JSNativeFunction("copyWithin", 2, ArrayPrototype::copyWithin));
        arrayPrototype.set("fill", new JSNativeFunction("fill", 1, ArrayPrototype::fill));

        // Array.prototype.length getter
        arrayPrototype.set("length", new JSNativeFunction("get length", 0, ArrayPrototype::getLength));

        // Array.prototype[Symbol.unscopables]
        arrayPrototype.set(
                PropertyKey.fromSymbol(JSSymbol.UNSCOPABLES),
                new JSNativeFunction("get [Symbol.unscopables]", 0, ArrayPrototype::getSymbolUnscopables));

        // Create Array constructor with static methods
        JSObject arrayConstructor = new JSObject();
        arrayConstructor.set("prototype", arrayPrototype);

        // Array static methods
        arrayConstructor.set("isArray", new JSNativeFunction("isArray", 1, ArrayConstructor::isArray));
        arrayConstructor.set("of", new JSNativeFunction("of", 0, ArrayConstructor::of));
        arrayConstructor.set("from", new JSNativeFunction("from", 1, ArrayConstructor::from));
        arrayConstructor.set("fromAsync", new JSNativeFunction("fromAsync", 1, ArrayConstructor::fromAsync));

        // Symbol.species getter
        arrayConstructor.set(
                PropertyKey.fromSymbol(JSSymbol.SPECIES),
                new JSNativeFunction("get [Symbol.species]", 0, ArrayConstructor::getSpecies));

        global.set("Array", arrayConstructor);
    }

    /**
     * Initialize Atomics object.
     */
    private static void initializeAtomicsObject(JSContext ctx, JSObject global) {
        JSObject atomics = new JSObject();
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
    private static void initializeBigIntConstructor(JSContext ctx, JSObject global) {
        // Create BigInt.prototype
        JSObject bigIntPrototype = new JSObject();
        bigIntPrototype.set("toString", new JSNativeFunction("toString", 1, BigIntPrototype::toString));
        bigIntPrototype.set("valueOf", new JSNativeFunction("valueOf", 0, BigIntPrototype::valueOf));
        bigIntPrototype.set("toLocaleString", new JSNativeFunction("toLocaleString", 0, BigIntPrototype::toLocaleString));

        // Create BigInt constructor
        JSObject bigIntConstructor = new JSObject();
        bigIntConstructor.set("prototype", bigIntPrototype);
        bigIntConstructor.set("[[BigIntConstructor]]", JSBoolean.TRUE); // Mark as BigInt constructor

        // BigInt static methods
        bigIntConstructor.set("asIntN", new JSNativeFunction("asIntN", 2, BigIntConstructor::asIntN));
        bigIntConstructor.set("asUintN", new JSNativeFunction("asUintN", 2, BigIntConstructor::asUintN));

        global.set("BigInt", bigIntConstructor);
    }

    /**
     * Initialize Boolean constructor and prototype.
     */
    private static void initializeBooleanConstructor(JSContext ctx, JSObject global) {
        // Create Boolean.prototype
        JSObject booleanPrototype = new JSObject();
        booleanPrototype.set("toString", new JSNativeFunction("toString", 0, BooleanPrototype::toString));
        booleanPrototype.set("valueOf", new JSNativeFunction("valueOf", 0, BooleanPrototype::valueOf));

        // Create Boolean constructor (placeholder)
        JSObject booleanConstructor = new JSObject();
        booleanConstructor.set("prototype", booleanPrototype);

        global.set("Boolean", booleanConstructor);
    }

    /**
     * Initialize console object.
     */
    private static void initializeConsoleObject(JSContext ctx, JSObject global) {
        JSObject console = new JSObject();
        console.set("log", new JSNativeFunction("log", 0, GlobalObject::consoleLog));
        console.set("info", new JSNativeFunction("info", 0, GlobalObject::consoleLog));
        console.set("warn", new JSNativeFunction("warn", 0, GlobalObject::consoleWarn));
        console.set("error", new JSNativeFunction("error", 0, GlobalObject::consoleError));

        global.set("console", console);
    }

    /**
     * Initialize DataView constructor and prototype.
     */
    private static void initializeDataViewConstructor(JSContext ctx, JSObject global) {
        // Create DataView.prototype
        JSObject dataViewPrototype = new JSObject();

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

        // Create DataView constructor
        JSObject dataViewConstructor = new JSObject();
        dataViewConstructor.set("prototype", dataViewPrototype);
        dataViewConstructor.set("[[DataViewConstructor]]", JSBoolean.TRUE);

        global.set("DataView", dataViewConstructor);
    }

    /**
     * Initialize Date constructor and prototype.
     */
    private static void initializeDateConstructor(JSContext ctx, JSObject global) {
        // Create Date.prototype
        JSObject datePrototype = new JSObject();
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

        // Create Date constructor with static methods
        JSObject dateConstructor = new JSObject();
        dateConstructor.set("prototype", datePrototype);
        dateConstructor.set("[[DateConstructor]]", JSBoolean.TRUE); // Mark as Date constructor

        // Date static methods
        dateConstructor.set("now", new JSNativeFunction("now", 0, DateConstructor::now));
        dateConstructor.set("parse", new JSNativeFunction("parse", 1, DateConstructor::parse));
        dateConstructor.set("UTC", new JSNativeFunction("UTC", 7, DateConstructor::UTC));

        global.set("Date", dateConstructor);
    }

    /**
     * Initialize Error constructors.
     */
    private static void initializeErrorConstructors(JSContext ctx, JSObject global) {
        // Base Error constructor
        global.set("Error", createErrorConstructor(ctx, "Error"));

        // Derived Error types
        global.set("TypeError", createErrorConstructor(ctx, "TypeError"));
        global.set("ReferenceError", createErrorConstructor(ctx, "ReferenceError"));
        global.set("RangeError", createErrorConstructor(ctx, "RangeError"));
        global.set("SyntaxError", createErrorConstructor(ctx, "SyntaxError"));
        global.set("URIError", createErrorConstructor(ctx, "URIError"));
        global.set("EvalError", createErrorConstructor(ctx, "EvalError"));
    }

    /**
     * Initialize FinalizationRegistry constructor.
     */
    private static void initializeFinalizationRegistryConstructor(JSContext ctx, JSObject global) {
        // Create FinalizationRegistry.prototype
        JSObject finalizationRegistryPrototype = new JSObject();
        // register() and unregister() methods are added in JSFinalizationRegistry constructor

        // Create FinalizationRegistry constructor
        JSObject finalizationRegistryConstructor = new JSObject();
        finalizationRegistryConstructor.set("prototype", finalizationRegistryPrototype);
        finalizationRegistryConstructor.set("[[FinalizationRegistryConstructor]]", JSBoolean.TRUE);

        global.set("FinalizationRegistry", finalizationRegistryConstructor);
    }

    /**
     * Initialize Function constructor and prototype.
     */
    private static void initializeFunctionConstructor(JSContext ctx, JSObject global) {
        // Create Function.prototype
        JSObject functionPrototype = new JSObject();
        functionPrototype.set("call", new JSNativeFunction("call", 1, FunctionPrototype::call));
        functionPrototype.set("apply", new JSNativeFunction("apply", 2, FunctionPrototype::apply));
        functionPrototype.set("bind", new JSNativeFunction("bind", 1, FunctionPrototype::bind));
        functionPrototype.set("toString", new JSNativeFunction("toString", 0, FunctionPrototype::toStringMethod));

        // Function constructor is a placeholder
        JSObject functionConstructor = new JSObject();
        functionConstructor.set("prototype", functionPrototype);

        global.set("Function", functionConstructor);
    }

    /**
     * Initialize Generator prototype methods.
     * Note: Generator functions (function*) would require compiler support.
     * This provides the prototype for manually created generators.
     */
    private static void initializeGeneratorPrototype(JSContext ctx, JSObject global) {
        // Create Generator.prototype
        JSObject generatorPrototype = new JSObject();
        generatorPrototype.set("next", new JSNativeFunction("next", 1, GeneratorPrototype::next));
        generatorPrototype.set("return", new JSNativeFunction("return", 1, GeneratorPrototype::returnMethod));
        generatorPrototype.set("throw", new JSNativeFunction("throw", 1, GeneratorPrototype::throwMethod));
        // Generator.prototype[Symbol.iterator] returns this
        generatorPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR),
                new JSNativeFunction("[Symbol.iterator]", 0, (context, thisArg, args) -> thisArg));

        // Create GeneratorFunction constructor (placeholder)
        JSObject generatorFunction = new JSObject();
        generatorFunction.set("prototype", generatorPrototype);

        // Store for use by generator instances
        global.set("GeneratorFunction", generatorFunction);
    }

    /**
     * Initialize JSON object.
     */
    private static void initializeJSONObject(JSContext ctx, JSObject global) {
        JSObject json = new JSObject();
        json.set("parse", new JSNativeFunction("parse", 1, JSONObject::parse));
        json.set("stringify", new JSNativeFunction("stringify", 1, JSONObject::stringify));

        global.set("JSON", json);
    }

    /**
     * Initialize Map constructor and prototype methods.
     */
    private static void initializeMapConstructor(JSContext ctx, JSObject global) {
        // Create Map.prototype
        JSObject mapPrototype = new JSObject();
        mapPrototype.set("set", new JSNativeFunction("set", 2, MapPrototype::set));
        mapPrototype.set("get", new JSNativeFunction("get", 1, MapPrototype::get));
        mapPrototype.set("has", new JSNativeFunction("has", 1, MapPrototype::has));
        mapPrototype.set("delete", new JSNativeFunction("delete", 1, MapPrototype::delete));
        mapPrototype.set("clear", new JSNativeFunction("clear", 0, MapPrototype::clear));
        mapPrototype.set("forEach", new JSNativeFunction("forEach", 1, MapPrototype::forEach));
        mapPrototype.set("entries", new JSNativeFunction("entries", 0, IteratorPrototype::mapEntriesIterator));
        mapPrototype.set("keys", new JSNativeFunction("keys", 0, IteratorPrototype::mapKeysIterator));
        mapPrototype.set("values", new JSNativeFunction("values", 0, IteratorPrototype::mapValuesIterator));
        // Map.prototype[Symbol.iterator] is the same as entries()
        mapPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), new JSNativeFunction("[Symbol.iterator]", 0, IteratorPrototype::mapEntriesIterator));

        // Create Map constructor
        JSObject mapConstructor = new JSObject();
        mapConstructor.set("prototype", mapPrototype);
        mapConstructor.set("[[MapConstructor]]", JSBoolean.TRUE); // Mark as Map constructor

        // Map static methods
        mapConstructor.set("groupBy", new JSNativeFunction("groupBy", 2, MapConstructor::groupBy));

        global.set("Map", mapConstructor);
    }

    // Global function implementations

    /**
     * Initialize Math object.
     */
    private static void initializeMathObject(JSContext ctx, JSObject global) {
        JSObject math = new JSObject();

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
    private static void initializeNumberConstructor(JSContext ctx, JSObject global) {
        // Create Number.prototype
        JSObject numberPrototype = new JSObject();
        numberPrototype.set("toFixed", new JSNativeFunction("toFixed", 1, NumberPrototype::toFixed));
        numberPrototype.set("toExponential", new JSNativeFunction("toExponential", 1, NumberPrototype::toExponential));
        numberPrototype.set("toPrecision", new JSNativeFunction("toPrecision", 1, NumberPrototype::toPrecision));
        numberPrototype.set("toString", new JSNativeFunction("toString", 1, NumberPrototype::toString));
        numberPrototype.set("toLocaleString", new JSNativeFunction("toLocaleString", 0, NumberPrototype::toLocaleString));
        numberPrototype.set("valueOf", new JSNativeFunction("valueOf", 0, NumberPrototype::valueOf));

        // Number constructor with static methods
        JSObject numberConstructor = new JSObject();
        numberConstructor.set("prototype", numberPrototype);
        numberConstructor.set("isNaN", new JSNativeFunction("isNaN", 1, NumberPrototype::isNaN));
        numberConstructor.set("isFinite", new JSNativeFunction("isFinite", 1, NumberPrototype::isFinite));
        numberConstructor.set("isInteger", new JSNativeFunction("isInteger", 1, NumberPrototype::isInteger));
        numberConstructor.set("isSafeInteger", new JSNativeFunction("isSafeInteger", 1, NumberPrototype::isSafeInteger));
        numberConstructor.set("parseFloat", new JSNativeFunction("parseFloat", 1, NumberPrototype::parseFloat));
        numberConstructor.set("parseInt", new JSNativeFunction("parseInt", 2, NumberPrototype::parseInt));

        global.set("Number", numberConstructor);
    }

    /**
     * Initialize Object constructor and static methods.
     */
    private static void initializeObjectConstructor(JSContext ctx, JSObject global) {
        // Create Object.prototype
        JSObject objectPrototype = new JSObject();
        objectPrototype.set("hasOwnProperty", new JSNativeFunction("hasOwnProperty", 1, ObjectConstructor::hasOwnProperty));
        objectPrototype.set("toString", new JSNativeFunction("toString", 0, ObjectPrototype::toString));
        objectPrototype.set("valueOf", new JSNativeFunction("valueOf", 0, ObjectPrototype::valueOf));

        // Create Object constructor
        JSObject objectConstructor = new JSObject();
        objectConstructor.set("prototype", objectPrototype);

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
    private static void initializePromiseConstructor(JSContext ctx, JSObject global) {
        // Create Promise.prototype
        JSObject promisePrototype = new JSObject();
        promisePrototype.set("then", new JSNativeFunction("then", 2, PromisePrototype::then));
        promisePrototype.set("catch", new JSNativeFunction("catch", 1, PromisePrototype::catchMethod));
        promisePrototype.set("finally", new JSNativeFunction("finally", 1, PromisePrototype::finallyMethod));

        // Create Promise constructor
        JSObject promiseConstructor = new JSObject();
        promiseConstructor.set("prototype", promisePrototype);
        promiseConstructor.set("[[PromiseConstructor]]", JSBoolean.TRUE); // Mark as Promise constructor

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
    private static void initializeProxyConstructor(JSContext ctx, JSObject global) {
        // Create Proxy constructor (special handling required in VM)
        JSObject proxyConstructor = new JSObject();
        proxyConstructor.set("[[ProxyConstructor]]", JSBoolean.TRUE); // Mark as Proxy constructor

        // Add static methods
        proxyConstructor.set("revocable", new JSNativeFunction("revocable", 2, ProxyConstructor::revocable));

        global.set("Proxy", proxyConstructor);
    }

    /**
     * Initialize Reflect object.
     */
    private static void initializeReflectObject(JSContext ctx, JSObject global) {
        JSObject reflect = new JSObject();
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
    private static void initializeRegExpConstructor(JSContext ctx, JSObject global) {
        // Create RegExp.prototype
        JSObject regexpPrototype = new JSObject();
        regexpPrototype.set("test", new JSNativeFunction("test", 1, RegExpPrototype::test));
        regexpPrototype.set("exec", new JSNativeFunction("exec", 1, RegExpPrototype::exec));
        regexpPrototype.set("toString", new JSNativeFunction("toString", 0, RegExpPrototype::toStringMethod));

        // Create RegExp constructor
        JSObject regexpConstructor = new JSObject();
        regexpConstructor.set("prototype", regexpPrototype);
        regexpConstructor.set("[[RegExpConstructor]]", JSBoolean.TRUE); // Mark as RegExp constructor

        global.set("RegExp", regexpConstructor);
    }

    /**
     * Initialize Set constructor and prototype methods.
     */
    private static void initializeSetConstructor(JSContext ctx, JSObject global) {
        // Create Set.prototype
        JSObject setPrototype = new JSObject();
        setPrototype.set("add", new JSNativeFunction("add", 1, SetPrototype::add));
        setPrototype.set("has", new JSNativeFunction("has", 1, SetPrototype::has));
        setPrototype.set("delete", new JSNativeFunction("delete", 1, SetPrototype::delete));
        setPrototype.set("clear", new JSNativeFunction("clear", 0, SetPrototype::clear));
        setPrototype.set("forEach", new JSNativeFunction("forEach", 1, SetPrototype::forEach));
        setPrototype.set("entries", new JSNativeFunction("entries", 0, IteratorPrototype::setEntriesIterator));
        setPrototype.set("keys", new JSNativeFunction("keys", 0, IteratorPrototype::setKeysIterator));
        setPrototype.set("values", new JSNativeFunction("values", 0, IteratorPrototype::setValuesIterator));
        // Set.prototype[Symbol.iterator] is the same as values()
        setPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), new JSNativeFunction("[Symbol.iterator]", 0, IteratorPrototype::setValuesIterator));

        // Create Set constructor
        JSObject setConstructor = new JSObject();
        setConstructor.set("prototype", setPrototype);
        setConstructor.set("[[SetConstructor]]", JSBoolean.TRUE); // Mark as Set constructor

        global.set("Set", setConstructor);
    }

    /**
     * Initialize SharedArrayBuffer constructor and prototype.
     */
    private static void initializeSharedArrayBufferConstructor(JSContext ctx, JSObject global) {
        // Create SharedArrayBuffer.prototype
        JSObject sharedArrayBufferPrototype = new JSObject();
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

        // Create SharedArrayBuffer constructor
        JSObject sharedArrayBufferConstructor = new JSObject();
        sharedArrayBufferConstructor.set("prototype", sharedArrayBufferPrototype);
        sharedArrayBufferConstructor.set("[[SharedArrayBufferConstructor]]", JSBoolean.TRUE); // Mark as SharedArrayBuffer constructor

        global.set("SharedArrayBuffer", sharedArrayBufferConstructor);
    }

    /**
     * Initialize String constructor and prototype.
     */
    private static void initializeStringConstructor(JSContext ctx, JSObject global) {
        // Create String.prototype
        JSObject stringPrototype = new JSObject();
        stringPrototype.set("charAt", new JSNativeFunction("charAt", 1, StringPrototype::charAt));
        stringPrototype.set("charCodeAt", new JSNativeFunction("charCodeAt", 1, StringPrototype::charCodeAt));
        stringPrototype.set("at", new JSNativeFunction("at", 1, StringPrototype::at));
        stringPrototype.set("codePointAt", new JSNativeFunction("codePointAt", 1, StringPrototype::codePointAt));
        stringPrototype.set("concat", new JSNativeFunction("concat", 1, StringPrototype::concat));
        stringPrototype.set("endsWith", new JSNativeFunction("endsWith", 1, StringPrototype::endsWith));
        stringPrototype.set("startsWith", new JSNativeFunction("startsWith", 1, StringPrototype::startsWith));
        stringPrototype.set("includes", new JSNativeFunction("includes", 1, StringPrototype::includes));
        stringPrototype.set("indexOf", new JSNativeFunction("indexOf", 1, StringPrototype::indexOf));
        stringPrototype.set("lastIndexOf", new JSNativeFunction("lastIndexOf", 1, StringPrototype::lastIndexOf));
        stringPrototype.set("padEnd", new JSNativeFunction("padEnd", 1, StringPrototype::padEnd));
        stringPrototype.set("padStart", new JSNativeFunction("padStart", 1, StringPrototype::padStart));
        stringPrototype.set("repeat", new JSNativeFunction("repeat", 1, StringPrototype::repeat));
        stringPrototype.set("replace", new JSNativeFunction("replace", 2, StringPrototype::replace));
        stringPrototype.set("replaceAll", new JSNativeFunction("replaceAll", 2, StringPrototype::replaceAll));
        stringPrototype.set("match", new JSNativeFunction("match", 1, StringPrototype::match));
        stringPrototype.set("matchAll", new JSNativeFunction("matchAll", 1, StringPrototype::matchAll));
        stringPrototype.set("slice", new JSNativeFunction("slice", 2, StringPrototype::slice));
        stringPrototype.set("split", new JSNativeFunction("split", 2, StringPrototype::split));
        stringPrototype.set("substring", new JSNativeFunction("substring", 2, StringPrototype::substring));
        stringPrototype.set("substr", new JSNativeFunction("substr", 2, StringPrototype::substr));
        stringPrototype.set("toLowerCase", new JSNativeFunction("toLowerCase", 0, StringPrototype::toLowerCase));
        stringPrototype.set("toUpperCase", new JSNativeFunction("toUpperCase", 0, StringPrototype::toUpperCase));
        stringPrototype.set("trim", new JSNativeFunction("trim", 0, StringPrototype::trim));
        stringPrototype.set("trimStart", new JSNativeFunction("trimStart", 0, StringPrototype::trimStart));
        stringPrototype.set("trimEnd", new JSNativeFunction("trimEnd", 0, StringPrototype::trimEnd));
        stringPrototype.set("toString", new JSNativeFunction("toString", 0, StringPrototype::toStringMethod));
        stringPrototype.set("valueOf", new JSNativeFunction("valueOf", 0, StringPrototype::valueOf));
        // String.prototype[Symbol.iterator]
        stringPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), new JSNativeFunction("[Symbol.iterator]", 0, IteratorPrototype::stringIterator));

        // String constructor is a placeholder
        JSObject stringConstructor = new JSObject();
        stringConstructor.set("prototype", stringPrototype);

        global.set("String", stringConstructor);
    }

    /**
     * Initialize Symbol constructor and static methods.
     */
    private static void initializeSymbolConstructor(JSContext ctx, JSObject global) {
        // Create Symbol.prototype
        JSObject symbolPrototype = new JSObject();
        symbolPrototype.set("toString", new JSNativeFunction("toString", 0, SymbolPrototype::toString));
        symbolPrototype.set("valueOf", new JSNativeFunction("valueOf", 0, SymbolPrototype::valueOf));
        // Symbol.prototype.description is a getter, set as property for now
        symbolPrototype.set("description", JSUndefined.INSTANCE);
        symbolPrototype.set("[Symbol.toPrimitive]", new JSNativeFunction("[Symbol.toPrimitive]", 1, SymbolPrototype::toPrimitive));
        symbolPrototype.set("[Symbol.toStringTag]", new JSString("Symbol"));

        // Create Symbol constructor
        JSObject symbolConstructor = new JSObject();
        symbolConstructor.set("prototype", symbolPrototype);
        symbolConstructor.set("[[SymbolConstructor]]", JSBoolean.TRUE); // Mark as Symbol constructor

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

        global.set("Symbol", symbolConstructor);
    }

    /**
     * Initialize all TypedArray constructors.
     */
    private static void initializeTypedArrayConstructors(JSContext ctx, JSObject global) {
        // Int8Array
        JSObject int8ArrayConstructor = new JSObject();
        int8ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Int8Array"));
        int8ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(1));
        global.set("Int8Array", int8ArrayConstructor);

        // Uint8Array
        JSObject uint8ArrayConstructor = new JSObject();
        uint8ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Uint8Array"));
        uint8ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(1));
        global.set("Uint8Array", uint8ArrayConstructor);

        // Uint8ClampedArray
        JSObject uint8ClampedArrayConstructor = new JSObject();
        uint8ClampedArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Uint8ClampedArray"));
        uint8ClampedArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(1));
        global.set("Uint8ClampedArray", uint8ClampedArrayConstructor);

        // Int16Array
        JSObject int16ArrayConstructor = new JSObject();
        int16ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Int16Array"));
        int16ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(2));
        global.set("Int16Array", int16ArrayConstructor);

        // Uint16Array
        JSObject uint16ArrayConstructor = new JSObject();
        uint16ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Uint16Array"));
        uint16ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(2));
        global.set("Uint16Array", uint16ArrayConstructor);

        // Int32Array
        JSObject int32ArrayConstructor = new JSObject();
        int32ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Int32Array"));
        int32ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(4));
        global.set("Int32Array", int32ArrayConstructor);

        // Uint32Array
        JSObject uint32ArrayConstructor = new JSObject();
        uint32ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Uint32Array"));
        uint32ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(4));
        global.set("Uint32Array", uint32ArrayConstructor);

        // Float32Array
        JSObject float32ArrayConstructor = new JSObject();
        float32ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Float32Array"));
        float32ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(4));
        global.set("Float32Array", float32ArrayConstructor);

        // Float64Array
        JSObject float64ArrayConstructor = new JSObject();
        float64ArrayConstructor.set("[[TypedArrayConstructor]]", new JSString("Float64Array"));
        float64ArrayConstructor.set("BYTES_PER_ELEMENT", new JSNumber(8));
        global.set("Float64Array", float64ArrayConstructor);
    }

    /**
     * Initialize WeakMap constructor and prototype methods.
     */
    private static void initializeWeakMapConstructor(JSContext ctx, JSObject global) {
        // Create WeakMap.prototype
        JSObject weakMapPrototype = new JSObject();
        weakMapPrototype.set("set", new JSNativeFunction("set", 2, WeakMapPrototype::set));
        weakMapPrototype.set("get", new JSNativeFunction("get", 1, WeakMapPrototype::get));
        weakMapPrototype.set("has", new JSNativeFunction("has", 1, WeakMapPrototype::has));
        weakMapPrototype.set("delete", new JSNativeFunction("delete", 1, WeakMapPrototype::delete));

        // Create WeakMap constructor
        JSObject weakMapConstructor = new JSObject();
        weakMapConstructor.set("prototype", weakMapPrototype);
        weakMapConstructor.set("[[WeakMapConstructor]]", JSBoolean.TRUE); // Mark as WeakMap constructor

        global.set("WeakMap", weakMapConstructor);
    }

    /**
     * Initialize WeakRef constructor.
     */
    private static void initializeWeakRefConstructor(JSContext ctx, JSObject global) {
        // Create WeakRef.prototype
        JSObject weakRefPrototype = new JSObject();
        // deref() method is added in JSWeakRef constructor

        // Create WeakRef constructor
        JSObject weakRefConstructor = new JSObject();
        weakRefConstructor.set("prototype", weakRefPrototype);
        weakRefConstructor.set("[[WeakRefConstructor]]", JSBoolean.TRUE); // Mark as WeakRef constructor

        global.set("WeakRef", weakRefConstructor);
    }

    /**
     * Initialize WeakSet constructor and prototype methods.
     */
    private static void initializeWeakSetConstructor(JSContext ctx, JSObject global) {
        // Create WeakSet.prototype
        JSObject weakSetPrototype = new JSObject();
        weakSetPrototype.set("add", new JSNativeFunction("add", 1, WeakSetPrototype::add));
        weakSetPrototype.set("has", new JSNativeFunction("has", 1, WeakSetPrototype::has));
        weakSetPrototype.set("delete", new JSNativeFunction("delete", 1, WeakSetPrototype::delete));

        // Create WeakSet constructor
        JSObject weakSetConstructor = new JSObject();
        weakSetConstructor.set("prototype", weakSetPrototype);
        weakSetConstructor.set("[[WeakSetConstructor]]", JSBoolean.TRUE); // Mark as WeakSet constructor

        global.set("WeakSet", weakSetConstructor);
    }

    /**
     * isFinite(value)
     * Determine whether a value is a finite number.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-isfinite-number">ECMAScript isFinite</a>
     */
    public static JSValue isFinite(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        double num = JSTypeConversions.toNumber(value).value();
        return JSBoolean.valueOf(!Double.isNaN(num) && !Double.isInfinite(num));
    }

    /**
     * isNaN(value)
     * Determine whether a value is NaN.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-isnan-number">ECMAScript isNaN</a>
     */
    public static JSValue isNaN(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue value = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        double num = JSTypeConversions.toNumber(value).value();
        return JSBoolean.valueOf(Double.isNaN(num));
    }

    /**
     * parseFloat(string)
     * Parse a string and return a floating point number.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-parsefloat-string">ECMAScript parseFloat</a>
     */
    public static JSValue parseFloat(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // Get input string
        JSValue input = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String inputString = JSTypeConversions.toString(input).value().trim();

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
    public static JSValue parseInt(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // Get input string
        JSValue input = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String inputString = JSTypeConversions.toString(input).value().trim();

        // Get radix
        int radix = 10;
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            double radixNum = JSTypeConversions.toNumber(args[1]).value();
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

        // Auto-detect radix 16 for "0x" prefix
        if (radix == 0 || radix == 16) {
            if (index + 1 < inputString.length() &&
                    inputString.charAt(index) == '0' &&
                    (inputString.charAt(index + 1) == 'x' || inputString.charAt(index + 1) == 'X')) {
                radix = 16;
                index += 2;
            } else if (radix == 0) {
                radix = 10;
            }
        }

        // Validate radix
        if (radix < 2 || radix > 36) {
            return new JSNumber(Double.NaN);
        }

        // Parse digits
        long result = 0;
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
     * queueMicrotask(callback)
     * Queues a microtask to be executed.
     *
     * @see <a href="https://html.spec.whatwg.org/multipage/timers-and-user-prompts.html#microtask-queuing">HTML queueMicrotask</a>
     */
    public static JSValue queueMicrotask(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0 || !(args[0] instanceof JSFunction callback)) {
            return ctx.throwError("TypeError", "queueMicrotask requires a function argument");
        }

        // Enqueue the callback as a microtask
        ctx.enqueueMicrotask(() -> {
            try {
                callback.call(ctx, JSUndefined.INSTANCE, new JSValue[0]);
            } catch (Exception e) {
                // In a full implementation, this would trigger the unhandled rejection handler
                System.err.println("Microtask error: " + e.getMessage());
            }
        });

        return JSUndefined.INSTANCE;
    }
}
