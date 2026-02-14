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
    private static final String URI_RESERVED = ";/?:@&=+$,#";
    private final JSConsole console;

    public JSGlobalObject() {
        this.console = new JSConsole();
    }

    private void appendPercentHex(StringBuilder result, int c) {
        result.append('%');
        result.append(HEX_CHARS[(c >> 4) & 0xF]);
        result.append(HEX_CHARS[c & 0xF]);
    }

    private int asciiDigitValue(char c) {
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

    private int decodeHexByte(JSContext context, String str, int index) {
        if (index >= str.length() || str.charAt(index) != '%') {
            context.throwURIError("expecting %");
            return -1;
        }
        if (index + 2 >= str.length()) {
            context.throwURIError("expecting hex digit");
            return -1;
        }
        int high = fromHex(str.charAt(index + 1));
        int low = fromHex(str.charAt(index + 2));
        if (high < 0 || low < 0) {
            context.throwURIError("expecting hex digit");
            return -1;
        }
        return (high << 4) | low;
    }

    /**
     * decodeURI(encodedURI)
     * Decode a URI that was encoded by encodeURI.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-decodeuri-encodeduri">ECMAScript decodeURI</a>
     */
    public JSValue decodeURI(JSContext context, JSValue thisArg, JSValue[] args) {
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
    public JSValue decodeURIComponent(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue encodedValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String encodedString = JSTypeConversions.toString(context, encodedValue).value();
        return decodeURIImpl(context, encodedString, true);
    }

    private JSValue decodeURIImpl(JSContext context, String encodedString, boolean isComponent) {
        StringBuilder result = new StringBuilder(encodedString.length());
        int k = 0;
        while (k < encodedString.length()) {
            int c = encodedString.charAt(k);
            if (c == '%') {
                int decodedByte = decodeHexByte(context, encodedString, k);
                if (decodedByte < 0) {
                    return context.throwURIError("expecting hex digit");
                }
                k += 3;
                if (decodedByte < 0x80) {
                    if (!isComponent && isURIReserved(decodedByte)) {
                        result.append('%');
                        k -= 2;
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
                    continuationCount = 0;
                    minCodePoint = 1;
                    codePoint = 0;
                }

                while (continuationCount-- > 0) {
                    int continuationByte = decodeHexByte(context, encodedString, k);
                    if (continuationByte < 0) {
                        return context.throwURIError("expecting hex digit");
                    }
                    k += 3;
                    if ((continuationByte & 0xC0) != 0x80) {
                        codePoint = 0;
                        break;
                    }
                    codePoint = (codePoint << 6) | (continuationByte & 0x3F);
                }
                if (codePoint < minCodePoint
                        || codePoint > Character.MAX_CODE_POINT
                        || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)) {
                    return context.throwURIError("malformed UTF-8");
                }
                result.appendCodePoint(codePoint);
            } else {
                result.append((char) c);
                k++;
            }
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
    public JSValue encodeURI(JSContext context, JSValue thisArg, JSValue[] args) {
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
    public JSValue encodeURIComponent(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue componentValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String componentString = JSTypeConversions.toString(context, componentValue).value();
        return encodeURIImpl(context, componentString, true);
    }

    private JSValue encodeURIImpl(JSContext context, String inputString, boolean isComponent) {
        StringBuilder result = new StringBuilder(inputString.length());
        int k = 0;
        while (k < inputString.length()) {
            int c = inputString.charAt(k++);
            if (isURIUnescaped(c, isComponent)) {
                result.append((char) c);
                continue;
            }
            if (Character.isLowSurrogate((char) c)) {
                return context.throwURIError("invalid character");
            }
            if (Character.isHighSurrogate((char) c)) {
                if (k >= inputString.length()) {
                    return context.throwURIError("expecting surrogate pair");
                }
                char c1 = inputString.charAt(k++);
                if (!Character.isLowSurrogate(c1)) {
                    return context.throwURIError("expecting surrogate pair");
                }
                c = Character.toCodePoint((char) c, c1);
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
    public JSValue escape(JSContext context, JSValue thisArg, JSValue[] args) {
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
     * Evaluate JavaScript code in the current context.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-eval-x">ECMAScript eval</a>
     */
    public JSValue eval(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue x = args.length > 0 ? args[0] : JSUndefined.INSTANCE;

        // If x is not a string, return it unchanged
        if (!(x instanceof JSString)) {
            return x;
        }

        String code = ((JSString) x).value();
        // Per ES2024 19.2.1.1 PerformEval: eval inherits strict mode from caller
        if (context.isStrictMode()) {
            code = "'use strict';\n" + code;
        }

        // Scope overlay: capture enclosing function's local variables onto the global
        // object so that eval code's GET_VAR/PUT_VAR can access them.
        StackFrame callerFrame = context.getVirtualMachine().getCurrentFrame();
        String[] localVarNames = null;
        Map<String, JSValue> savedGlobals = null;
        Set<String> absentKeys = null;
        JSObject global = context.getGlobalObject();

        if (callerFrame != null
                && callerFrame.getFunction() instanceof JSBytecodeFunction bcFunc
                && bcFunc.getBytecode().getLocalVarNames() != null) {
            localVarNames = bcFunc.getBytecode().getLocalVarNames();
            savedGlobals = new HashMap<>();
            absentKeys = new HashSet<>();
            JSValue[] locals = callerFrame.getLocals();
            for (int i = 0; i < localVarNames.length && i < locals.length; i++) {
                String name = localVarNames[i];
                if (name == null) continue;
                PropertyKey key = PropertyKey.fromString(name);
                if (global.has(key)) {
                    savedGlobals.put(name, global.get(key));
                } else {
                    absentKeys.add(name);
                }
                global.set(key, locals[i]);
            }
        }

        try {
            JSValue result = context.eval(code);

            // Copy modified values back to caller's locals
            if (localVarNames != null) {
                JSValue[] locals = callerFrame.getLocals();
                for (int i = 0; i < localVarNames.length && i < locals.length; i++) {
                    String name = localVarNames[i];
                    if (name == null) continue;
                    PropertyKey key = PropertyKey.fromString(name);
                    if (global.has(key)) {
                        locals[i] = global.get(key);
                    }
                }
            }

            return result;
        } catch (JSException e) {
            // Re-throw as pending exception so JavaScript try-catch can handle it
            context.setPendingException(e.getErrorValue());
            return JSUndefined.INSTANCE;
        } finally {
            // Restore global object state
            if (savedGlobals != null) {
                for (var entry : savedGlobals.entrySet()) {
                    global.set(PropertyKey.fromString(entry.getKey()), entry.getValue());
                }
            }
            if (absentKeys != null) {
                for (String name : absentKeys) {
                    global.delete(PropertyKey.fromString(name), context);
                }
            }
        }
    }

    private int fromHex(char c) {
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

    public JSConsole getConsole() {
        return console;
    }

    /**
     * Initialize the global object with all built-in global properties and functions.
     */
    public void initialize(JSContext context, JSObject global) {
        // Global value properties (non-writable, non-enumerable, non-configurable)
        global.definePropertyReadonlyNonConfigurable("Infinity", new JSNumber(Double.POSITIVE_INFINITY));
        global.definePropertyReadonlyNonConfigurable("NaN", new JSNumber(Double.NaN));
        global.definePropertyReadonlyNonConfigurable("undefined", JSUndefined.INSTANCE);

        // Global function properties
        global.definePropertyWritableConfigurable("eval", new JSNativeFunction("eval", 1, this::eval));
        global.definePropertyWritableConfigurable("isFinite", new JSNativeFunction("isFinite", 1, this::isFinite));
        global.definePropertyWritableConfigurable("isNaN", new JSNativeFunction("isNaN", 1, this::isNaN));
        global.definePropertyWritableConfigurable("parseFloat", new JSNativeFunction("parseFloat", 1, this::parseFloat));
        global.definePropertyWritableConfigurable("parseInt", new JSNativeFunction("parseInt", 2, this::parseInt));

        // URI handling functions
        global.definePropertyWritableConfigurable("decodeURI", new JSNativeFunction("decodeURI", 1, this::decodeURI));
        global.definePropertyWritableConfigurable("decodeURIComponent", new JSNativeFunction("decodeURIComponent", 1, this::decodeURIComponent));
        global.definePropertyWritableConfigurable("encodeURI", new JSNativeFunction("encodeURI", 1, this::encodeURI));
        global.definePropertyWritableConfigurable("encodeURIComponent", new JSNativeFunction("encodeURIComponent", 1, this::encodeURIComponent));
        global.definePropertyWritableConfigurable("escape", new JSNativeFunction("escape", 1, this::escape));
        global.definePropertyWritableConfigurable("unescape", new JSNativeFunction("unescape", 1, this::unescape));

        // Console object for debugging
        initializeConsoleObject(context, global);

        // Global this reference
        global.definePropertyWritableConfigurable("globalThis", global);

        // Object.prototype.toString.call(globalThis) -> [object global]
        global.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString("global"));

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
        initializeIteratorConstructor(context, global);
        initializeGeneratorPrototype(context, global);

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
    private void initializeArrayBufferConstructor(JSContext context, JSObject global) {
        // Create ArrayBuffer.prototype
        JSObject arrayBufferPrototype = context.createJSObject();
        arrayBufferPrototype.set("resize", new JSNativeFunction("resize", 1, ArrayBufferPrototype::resize));
        arrayBufferPrototype.set("slice", new JSNativeFunction("slice", 2, ArrayBufferPrototype::slice));
        arrayBufferPrototype.set("transfer", new JSNativeFunction("transfer", 0, ArrayBufferPrototype::transfer));
        arrayBufferPrototype.set("transferToFixedLength", new JSNativeFunction("transferToFixedLength", 0, ArrayBufferPrototype::transferToFixedLength));

        // Define getter properties
        arrayBufferPrototype.defineGetterConfigurable("byteLength", ArrayBufferPrototype::getByteLength);
        arrayBufferPrototype.defineGetterConfigurable("detached", ArrayBufferPrototype::getDetached);
        arrayBufferPrototype.defineGetterConfigurable("maxByteLength", ArrayBufferPrototype::getMaxByteLength);
        arrayBufferPrototype.defineGetterConfigurable("resizable", ArrayBufferPrototype::getResizable);
        arrayBufferPrototype.defineGetterConfigurable(JSSymbol.TO_STRING_TAG, ArrayBufferPrototype::getToStringTag);

        // Create ArrayBuffer constructor as a function
        JSNativeFunction arrayBufferConstructor = new JSNativeFunction("ArrayBuffer", 1, ArrayBufferConstructor::call, true);
        arrayBufferConstructor.set("prototype", arrayBufferPrototype);
        arrayBufferConstructor.setConstructorType(JSConstructorType.ARRAY_BUFFER);
        arrayBufferPrototype.set("constructor", arrayBufferConstructor);

        // Static methods
        arrayBufferConstructor.set("isView", new JSNativeFunction("isView", 1, ArrayBufferConstructor::isView));

        // Symbol.species getter
        arrayBufferConstructor.defineGetterConfigurable(JSSymbol.SPECIES, ArrayBufferConstructor::getSpecies);

        global.definePropertyWritableConfigurable("ArrayBuffer", arrayBufferConstructor);
    }

    /**
     * Initialize Array constructor and prototype.
     */
    private void initializeArrayConstructor(JSContext context, JSObject global) {
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
        arrayPrototype.definePropertyReadonlyNonConfigurable("length", new JSNumber(0));

        // Array.prototype[Symbol.*]
        arrayPrototype.set(PropertyKey.fromSymbol(JSSymbol.ITERATOR), valuesFunction);
        arrayPrototype.defineGetterConfigurable(JSSymbol.UNSCOPABLES, ArrayPrototype::getSymbolUnscopables);

        // Create Array constructor as a function
        JSNativeFunction arrayConstructor = new JSNativeFunction("Array", 1, ArrayConstructor::call, true);
        arrayConstructor.set("prototype", arrayPrototype);
        arrayConstructor.setConstructorType(JSConstructorType.ARRAY);
        arrayPrototype.set("constructor", arrayConstructor);

        // Array static methods
        arrayConstructor.set("from", new JSNativeFunction("from", 1, ArrayConstructor::from));
        arrayConstructor.set("fromAsync", new JSNativeFunction("fromAsync", 1, ArrayConstructor::fromAsync));
        arrayConstructor.set("isArray", new JSNativeFunction("isArray", 1, ArrayConstructor::isArray));
        arrayConstructor.set("of", new JSNativeFunction("of", 0, ArrayConstructor::of));

        // Symbol.species getter
        arrayConstructor.defineGetterConfigurable(JSSymbol.SPECIES, ArrayConstructor::getSpecies);

        global.definePropertyWritableConfigurable("Array", arrayConstructor);
    }

    /**
     * Initialize AsyncDisposableStack constructor and prototype.
     */
    private void initializeAsyncDisposableStackConstructor(JSContext context, JSObject global) {
        JSObject asyncDisposableStackPrototype = context.createJSObject();
        asyncDisposableStackPrototype.set("adopt", new JSNativeFunction("adopt", 2, AsyncDisposableStackPrototype::adopt));
        asyncDisposableStackPrototype.set("defer", new JSNativeFunction("defer", 1, AsyncDisposableStackPrototype::defer));
        JSNativeFunction disposeAsyncFunction = new JSNativeFunction("disposeAsync", 0, AsyncDisposableStackPrototype::disposeAsync);
        asyncDisposableStackPrototype.set("disposeAsync", disposeAsyncFunction);
        asyncDisposableStackPrototype.set("move", new JSNativeFunction("move", 0, AsyncDisposableStackPrototype::move));
        asyncDisposableStackPrototype.set("use", new JSNativeFunction("use", 1, AsyncDisposableStackPrototype::use));
        asyncDisposableStackPrototype.set(PropertyKey.fromSymbol(JSSymbol.ASYNC_DISPOSE), disposeAsyncFunction);

        asyncDisposableStackPrototype.defineGetterConfigurable("disposed", AsyncDisposableStackPrototype::getDisposed);
        asyncDisposableStackPrototype.defineGetterConfigurable(JSSymbol.TO_STRING_TAG, (childContext, thisObj, args) -> {
            if (!(thisObj instanceof JSAsyncDisposableStack)) {
                return childContext.throwTypeError("get AsyncDisposableStack.prototype[Symbol.toStringTag] called on non-AsyncDisposableStack");
            }
            return new JSString(JSAsyncDisposableStack.NAME);
        });

        JSNativeFunction asyncDisposableStackConstructor = new JSNativeFunction(
                JSAsyncDisposableStack.NAME, 0, AsyncDisposableStackConstructor::call, true, true);
        asyncDisposableStackConstructor.set("prototype", asyncDisposableStackPrototype);
        asyncDisposableStackPrototype.set("constructor", asyncDisposableStackConstructor);

        global.definePropertyWritableConfigurable(JSAsyncDisposableStack.NAME, asyncDisposableStackConstructor);
    }

    /**
     * Initialize AsyncFunction constructor and prototype.
     * AsyncFunction is not exposed in global scope but is available via async function constructors.
     */
    private void initializeAsyncFunctionConstructor(JSContext context, JSObject global) {
        // Create AsyncFunction.prototype that inherits from Function.prototype
        JSObject asyncFunctionPrototype = context.createJSObject();
        context.transferPrototype(asyncFunctionPrototype, JSFunction.NAME);

        // Create AsyncFunction constructor
        // AsyncFunction is not normally exposed, but we need it for the prototype chain
        JSNativeFunction asyncFunctionConstructor = new JSNativeFunction("AsyncFunction", 1,
                (ctx, thisObj, args) -> ctx.throwTypeError("AsyncFunction is not a constructor"), true);
        asyncFunctionConstructor.set("prototype", asyncFunctionPrototype);
        asyncFunctionPrototype.set("constructor", asyncFunctionConstructor);

        // Store AsyncFunction in the context (not in global scope)
        // This is used internally for setting up prototype chains for async bytecode functions
        context.setAsyncFunctionConstructor(asyncFunctionConstructor);
    }

    /**
     * Initialize Atomics object.
     */
    private void initializeAtomicsObject(JSContext context, JSObject global) {
        JSObject atomics = context.createJSObject();
        atomics.set("add", new JSNativeFunction("add", 3, AtomicsObject::add));
        atomics.set("and", new JSNativeFunction("and", 3, AtomicsObject::and));
        atomics.set("compareExchange", new JSNativeFunction("compareExchange", 4, AtomicsObject::compareExchange));
        atomics.set("exchange", new JSNativeFunction("exchange", 3, AtomicsObject::exchange));
        atomics.set("isLockFree", new JSNativeFunction("isLockFree", 1, AtomicsObject::isLockFree));
        atomics.set("load", new JSNativeFunction("load", 2, AtomicsObject::load));
        atomics.set("notify", new JSNativeFunction("notify", 3, AtomicsObject::notify));
        atomics.set("or", new JSNativeFunction("or", 3, AtomicsObject::or));
        atomics.set("pause", new JSNativeFunction("pause", 0, AtomicsObject::pause));
        atomics.set("store", new JSNativeFunction("store", 3, AtomicsObject::store));
        atomics.set("sub", new JSNativeFunction("sub", 3, AtomicsObject::sub));
        atomics.set("wait", new JSNativeFunction("wait", 4, AtomicsObject::wait));
        atomics.set("waitAsync", new JSNativeFunction("waitAsync", 4, AtomicsObject::waitAsync));
        atomics.set("xor", new JSNativeFunction("xor", 3, AtomicsObject::xor));

        global.definePropertyWritableConfigurable("Atomics", atomics);
    }

    /**
     * Initialize BigInt constructor and static methods.
     */
    private void initializeBigIntConstructor(JSContext context, JSObject global) {
        // Create BigInt.prototype
        JSObject bigIntPrototype = context.createJSObject();
        bigIntPrototype.set("toLocaleString", new JSNativeFunction("toLocaleString", 0, BigIntPrototype::toLocaleString));
        bigIntPrototype.set("toString", new JSNativeFunction("toString", 1, BigIntPrototype::toString));
        bigIntPrototype.set("valueOf", new JSNativeFunction("valueOf", 0, BigIntPrototype::valueOf));

        // Create BigInt constructor
        JSNativeFunction bigIntConstructor = new JSNativeFunction("BigInt", 1, BigIntConstructor::call);
        bigIntConstructor.set("prototype", bigIntPrototype);
        bigIntConstructor.setConstructorType(JSConstructorType.BIG_INT_OBJECT); // Mark as BigInt constructor
        bigIntPrototype.set("constructor", bigIntConstructor);

        // BigInt static methods
        bigIntConstructor.set("asIntN", new JSNativeFunction("asIntN", 2, BigIntConstructor::asIntN));
        bigIntConstructor.set("asUintN", new JSNativeFunction("asUintN", 2, BigIntConstructor::asUintN));

        global.definePropertyWritableConfigurable("BigInt", bigIntConstructor);
    }

    /**
     * Initialize Boolean constructor and prototype.
     */
    private void initializeBooleanConstructor(JSContext context, JSObject global) {
        // Create Boolean.prototype as a Boolean object with [[BooleanData]] = false
        // Per QuickJS: JS_SetObjectData(ctx, ctx->class_proto[JS_CLASS_BOOLEAN], JS_NewBool(ctx, FALSE))
        JSBooleanObject booleanPrototype = new JSBooleanObject(false);
        context.transferPrototype(booleanPrototype, JSObject.NAME);
        booleanPrototype.definePropertyWritableConfigurable("toString", new JSNativeFunction("toString", 0, BooleanPrototype::toString));
        booleanPrototype.definePropertyWritableConfigurable("valueOf", new JSNativeFunction("valueOf", 0, BooleanPrototype::valueOf));

        // Create Boolean constructor
        JSNativeFunction booleanConstructor = new JSNativeFunction("Boolean", 1, BooleanConstructor::call, true);
        booleanConstructor.definePropertyReadonlyNonConfigurable("prototype", booleanPrototype);
        booleanConstructor.setConstructorType(JSConstructorType.BOOLEAN_OBJECT);
        booleanPrototype.definePropertyWritableConfigurable("constructor", booleanConstructor);

        global.definePropertyWritableConfigurable("Boolean", booleanConstructor);
    }

    /**
     * Initialize console object with all console API methods.
     */
    private void initializeConsoleObject(JSContext context, JSObject global) {
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

        global.definePropertyWritableConfigurable("console", consoleObj);
    }

    /**
     * Initialize DataView constructor and prototype.
     */
    private void initializeDataViewConstructor(JSContext context, JSObject global) {
        // Create DataView.prototype
        JSObject dataViewPrototype = context.createJSObject();

        // Define getter properties
        dataViewPrototype.defineGetterConfigurable("buffer", DataViewPrototype::getBuffer);
        dataViewPrototype.defineGetterConfigurable("byteLength", DataViewPrototype::getByteLength);
        dataViewPrototype.defineGetterConfigurable("byteOffset", DataViewPrototype::getByteOffset);
        dataViewPrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString("DataView"));

        // Int8/Uint8 methods
        dataViewPrototype.definePropertyWritableConfigurable("getInt8", new JSNativeFunction("getInt8", 1, DataViewPrototype::getInt8));
        dataViewPrototype.definePropertyWritableConfigurable("setInt8", new JSNativeFunction("setInt8", 2, DataViewPrototype::setInt8));
        dataViewPrototype.definePropertyWritableConfigurable("getUint8", new JSNativeFunction("getUint8", 1, DataViewPrototype::getUint8));
        dataViewPrototype.definePropertyWritableConfigurable("setUint8", new JSNativeFunction("setUint8", 2, DataViewPrototype::setUint8));

        // Int16/Uint16 methods
        dataViewPrototype.definePropertyWritableConfigurable("getInt16", new JSNativeFunction("getInt16", 1, DataViewPrototype::getInt16));
        dataViewPrototype.definePropertyWritableConfigurable("setInt16", new JSNativeFunction("setInt16", 2, DataViewPrototype::setInt16));
        dataViewPrototype.definePropertyWritableConfigurable("getUint16", new JSNativeFunction("getUint16", 1, DataViewPrototype::getUint16));
        dataViewPrototype.definePropertyWritableConfigurable("setUint16", new JSNativeFunction("setUint16", 2, DataViewPrototype::setUint16));

        // Int32/Uint32 methods
        dataViewPrototype.definePropertyWritableConfigurable("getInt32", new JSNativeFunction("getInt32", 1, DataViewPrototype::getInt32));
        dataViewPrototype.definePropertyWritableConfigurable("setInt32", new JSNativeFunction("setInt32", 2, DataViewPrototype::setInt32));
        dataViewPrototype.definePropertyWritableConfigurable("getUint32", new JSNativeFunction("getUint32", 1, DataViewPrototype::getUint32));
        dataViewPrototype.definePropertyWritableConfigurable("setUint32", new JSNativeFunction("setUint32", 2, DataViewPrototype::setUint32));

        // BigInt methods
        dataViewPrototype.definePropertyWritableConfigurable("getBigInt64", new JSNativeFunction("getBigInt64", 1, DataViewPrototype::getBigInt64));
        dataViewPrototype.definePropertyWritableConfigurable("setBigInt64", new JSNativeFunction("setBigInt64", 2, DataViewPrototype::setBigInt64));
        dataViewPrototype.definePropertyWritableConfigurable("getBigUint64", new JSNativeFunction("getBigUint64", 1, DataViewPrototype::getBigUint64));
        dataViewPrototype.definePropertyWritableConfigurable("setBigUint64", new JSNativeFunction("setBigUint64", 2, DataViewPrototype::setBigUint64));

        // Float methods
        dataViewPrototype.definePropertyWritableConfigurable("getFloat16", new JSNativeFunction("getFloat16", 1, DataViewPrototype::getFloat16));
        dataViewPrototype.definePropertyWritableConfigurable("setFloat16", new JSNativeFunction("setFloat16", 2, DataViewPrototype::setFloat16));
        dataViewPrototype.definePropertyWritableConfigurable("getFloat32", new JSNativeFunction("getFloat32", 1, DataViewPrototype::getFloat32));
        dataViewPrototype.definePropertyWritableConfigurable("setFloat32", new JSNativeFunction("setFloat32", 2, DataViewPrototype::setFloat32));
        dataViewPrototype.definePropertyWritableConfigurable("getFloat64", new JSNativeFunction("getFloat64", 1, DataViewPrototype::getFloat64));
        dataViewPrototype.definePropertyWritableConfigurable("setFloat64", new JSNativeFunction("setFloat64", 2, DataViewPrototype::setFloat64));

        // Create DataView constructor as a function that requires 'new'
        JSNativeFunction dataViewConstructor = new JSNativeFunction("DataView", 1, DataViewConstructor::call, true, true);
        dataViewConstructor.set("prototype", dataViewPrototype);
        dataViewConstructor.setConstructorType(JSConstructorType.DATA_VIEW);
        dataViewPrototype.definePropertyWritableConfigurable("constructor", dataViewConstructor);

        global.definePropertyWritableConfigurable("DataView", dataViewConstructor);
    }

    /**
     * Initialize Date constructor and prototype.
     */
    private void initializeDateConstructor(JSContext context, JSObject global) {
        JSObject datePrototype = context.createJSObject();
        JSNativeFunction toUTCString = new JSNativeFunction("toUTCString", 0, DatePrototype::toUTCString);
        JSNativeFunction toPrimitive = new JSNativeFunction("[Symbol.toPrimitive]", 1, DatePrototype::symbolToPrimitive);

        datePrototype.definePropertyWritableConfigurable("getDate", new JSNativeFunction("getDate", 0, DatePrototype::getDate));
        datePrototype.definePropertyWritableConfigurable("getDay", new JSNativeFunction("getDay", 0, DatePrototype::getDay));
        datePrototype.definePropertyWritableConfigurable("getFullYear", new JSNativeFunction("getFullYear", 0, DatePrototype::getFullYear));
        datePrototype.definePropertyWritableConfigurable("getHours", new JSNativeFunction("getHours", 0, DatePrototype::getHours));
        datePrototype.definePropertyWritableConfigurable("getMilliseconds", new JSNativeFunction("getMilliseconds", 0, DatePrototype::getMilliseconds));
        datePrototype.definePropertyWritableConfigurable("getMinutes", new JSNativeFunction("getMinutes", 0, DatePrototype::getMinutes));
        datePrototype.definePropertyWritableConfigurable("getMonth", new JSNativeFunction("getMonth", 0, DatePrototype::getMonth));
        datePrototype.definePropertyWritableConfigurable("getSeconds", new JSNativeFunction("getSeconds", 0, DatePrototype::getSeconds));
        datePrototype.definePropertyWritableConfigurable("getTime", new JSNativeFunction("getTime", 0, DatePrototype::getTime));
        datePrototype.definePropertyWritableConfigurable("getTimezoneOffset", new JSNativeFunction("getTimezoneOffset", 0, DatePrototype::getTimezoneOffset));
        datePrototype.definePropertyWritableConfigurable("getUTCDate", new JSNativeFunction("getUTCDate", 0, DatePrototype::getUTCDate));
        datePrototype.definePropertyWritableConfigurable("getUTCDay", new JSNativeFunction("getUTCDay", 0, DatePrototype::getUTCDay));
        datePrototype.definePropertyWritableConfigurable("getUTCFullYear", new JSNativeFunction("getUTCFullYear", 0, DatePrototype::getUTCFullYear));
        datePrototype.definePropertyWritableConfigurable("getUTCHours", new JSNativeFunction("getUTCHours", 0, DatePrototype::getUTCHours));
        datePrototype.definePropertyWritableConfigurable("getUTCMilliseconds", new JSNativeFunction("getUTCMilliseconds", 0, DatePrototype::getUTCMilliseconds));
        datePrototype.definePropertyWritableConfigurable("getUTCMinutes", new JSNativeFunction("getUTCMinutes", 0, DatePrototype::getUTCMinutes));
        datePrototype.definePropertyWritableConfigurable("getUTCMonth", new JSNativeFunction("getUTCMonth", 0, DatePrototype::getUTCMonth));
        datePrototype.definePropertyWritableConfigurable("getUTCSeconds", new JSNativeFunction("getUTCSeconds", 0, DatePrototype::getUTCSeconds));
        datePrototype.definePropertyWritableConfigurable("getYear", new JSNativeFunction("getYear", 0, DatePrototype::getYear));
        datePrototype.definePropertyWritableConfigurable("setDate", new JSNativeFunction("setDate", 1, DatePrototype::setDate));
        datePrototype.definePropertyWritableConfigurable("setFullYear", new JSNativeFunction("setFullYear", 3, DatePrototype::setFullYear));
        datePrototype.definePropertyWritableConfigurable("setHours", new JSNativeFunction("setHours", 4, DatePrototype::setHours));
        datePrototype.definePropertyWritableConfigurable("setMilliseconds", new JSNativeFunction("setMilliseconds", 1, DatePrototype::setMilliseconds));
        datePrototype.definePropertyWritableConfigurable("setMinutes", new JSNativeFunction("setMinutes", 3, DatePrototype::setMinutes));
        datePrototype.definePropertyWritableConfigurable("setMonth", new JSNativeFunction("setMonth", 2, DatePrototype::setMonth));
        datePrototype.definePropertyWritableConfigurable("setSeconds", new JSNativeFunction("setSeconds", 2, DatePrototype::setSeconds));
        datePrototype.definePropertyWritableConfigurable("setTime", new JSNativeFunction("setTime", 1, DatePrototype::setTime));
        datePrototype.definePropertyWritableConfigurable("setUTCDate", new JSNativeFunction("setUTCDate", 1, DatePrototype::setUTCDate));
        datePrototype.definePropertyWritableConfigurable("setUTCFullYear", new JSNativeFunction("setUTCFullYear", 3, DatePrototype::setUTCFullYear));
        datePrototype.definePropertyWritableConfigurable("setUTCHours", new JSNativeFunction("setUTCHours", 4, DatePrototype::setUTCHours));
        datePrototype.definePropertyWritableConfigurable("setUTCMilliseconds", new JSNativeFunction("setUTCMilliseconds", 1, DatePrototype::setUTCMilliseconds));
        datePrototype.definePropertyWritableConfigurable("setUTCMinutes", new JSNativeFunction("setUTCMinutes", 3, DatePrototype::setUTCMinutes));
        datePrototype.definePropertyWritableConfigurable("setUTCMonth", new JSNativeFunction("setUTCMonth", 2, DatePrototype::setUTCMonth));
        datePrototype.definePropertyWritableConfigurable("setUTCSeconds", new JSNativeFunction("setUTCSeconds", 2, DatePrototype::setUTCSeconds));
        datePrototype.definePropertyWritableConfigurable("setYear", new JSNativeFunction("setYear", 1, DatePrototype::setYear));
        datePrototype.definePropertyWritableConfigurable("toDateString", new JSNativeFunction("toDateString", 0, DatePrototype::toDateString));
        datePrototype.definePropertyWritableConfigurable("toGMTString", toUTCString);
        datePrototype.definePropertyWritableConfigurable("toISOString", new JSNativeFunction("toISOString", 0, DatePrototype::toISOString));
        datePrototype.definePropertyWritableConfigurable("toJSON", new JSNativeFunction("toJSON", 1, DatePrototype::toJSON));
        datePrototype.definePropertyWritableConfigurable("toLocaleDateString", new JSNativeFunction("toLocaleDateString", 0, DatePrototype::toLocaleDateString));
        datePrototype.definePropertyWritableConfigurable("toLocaleString", new JSNativeFunction("toLocaleString", 0, DatePrototype::toLocaleString));
        datePrototype.definePropertyWritableConfigurable("toLocaleTimeString", new JSNativeFunction("toLocaleTimeString", 0, DatePrototype::toLocaleTimeString));
        datePrototype.definePropertyWritableConfigurable("toString", new JSNativeFunction("toString", 0, DatePrototype::toStringMethod));
        datePrototype.definePropertyWritableConfigurable("toTimeString", new JSNativeFunction("toTimeString", 0, DatePrototype::toTimeString));
        datePrototype.definePropertyWritableConfigurable("toUTCString", toUTCString);
        datePrototype.definePropertyWritableConfigurable("valueOf", new JSNativeFunction("valueOf", 0, DatePrototype::valueOf));
        datePrototype.definePropertyWritableConfigurable(JSSymbol.TO_PRIMITIVE, toPrimitive);

        JSNativeFunction dateConstructor = new JSNativeFunction("Date", 7, DateConstructor::call, true);
        dateConstructor.definePropertyReadonlyNonConfigurable("prototype", datePrototype);
        dateConstructor.setConstructorType(JSConstructorType.DATE);
        datePrototype.definePropertyWritableConfigurable("constructor", dateConstructor);

        dateConstructor.definePropertyWritableConfigurable("UTC", new JSNativeFunction("UTC", 7, DateConstructor::UTC));
        dateConstructor.definePropertyWritableConfigurable("now", new JSNativeFunction("now", 0, DateConstructor::now));
        dateConstructor.definePropertyWritableConfigurable("parse", new JSNativeFunction("parse", 1, DateConstructor::parse));

        global.definePropertyWritableConfigurable("Date", dateConstructor);
    }

    /**
     * Initialize DisposableStack constructor and prototype.
     */
    private void initializeDisposableStackConstructor(JSContext context, JSObject global) {
        JSObject disposableStackPrototype = context.createJSObject();
        JSNativeFunction disposeFunction = new JSNativeFunction("dispose", 0, DisposableStackPrototype::dispose);
        disposableStackPrototype.set("adopt", new JSNativeFunction("adopt", 2, DisposableStackPrototype::adopt));
        disposableStackPrototype.set("defer", new JSNativeFunction("defer", 1, DisposableStackPrototype::defer));
        disposableStackPrototype.set("dispose", disposeFunction);
        disposableStackPrototype.set("move", new JSNativeFunction("move", 0, DisposableStackPrototype::move));
        disposableStackPrototype.set("use", new JSNativeFunction("use", 1, DisposableStackPrototype::use));
        disposableStackPrototype.set(PropertyKey.fromSymbol(JSSymbol.DISPOSE), disposeFunction);

        disposableStackPrototype.defineGetterConfigurable("disposed", DisposableStackPrototype::getDisposed);
        disposableStackPrototype.defineGetterConfigurable(JSSymbol.TO_STRING_TAG, (childContext, thisObj, args) -> {
            if (!(thisObj instanceof JSDisposableStack)) {
                return childContext.throwTypeError("get DisposableStack.prototype[Symbol.toStringTag] called on non-DisposableStack");
            }
            return new JSString(JSDisposableStack.NAME);
        });

        JSNativeFunction disposableStackConstructor = new JSNativeFunction(
                JSDisposableStack.NAME, 0, DisposableStackConstructor::call, true, true);
        disposableStackConstructor.set("prototype", disposableStackPrototype);
        disposableStackPrototype.set("constructor", disposableStackConstructor);

        global.definePropertyWritableConfigurable(JSDisposableStack.NAME, disposableStackConstructor);
    }

    /**
     * Initialize Error constructors.
     */
    private void initializeErrorConstructors(JSContext context, JSObject global) {
        Stream.of(JSErrorType.values()).forEach(type -> global.definePropertyWritableConfigurable(type.name(), type.create(context)));
    }

    /**
     * Initialize FinalizationRegistry constructor.
     */
    private void initializeFinalizationRegistryConstructor(JSContext context, JSObject global) {
        JSObject finalizationRegistryPrototype = context.createJSObject();
        finalizationRegistryPrototype.definePropertyWritableConfigurable("register",
                new JSNativeFunction("register", 2, FinalizationRegistryPrototype::register));
        finalizationRegistryPrototype.definePropertyWritableConfigurable("unregister",
                new JSNativeFunction("unregister", 1, FinalizationRegistryPrototype::unregister));
        finalizationRegistryPrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG,
                new JSString("FinalizationRegistry"));

        JSNativeFunction finalizationRegistryConstructor = new JSNativeFunction(
                "FinalizationRegistry", 1, FinalizationRegistryConstructor::call, true, true);
        finalizationRegistryConstructor.definePropertyReadonlyNonConfigurable("prototype", finalizationRegistryPrototype);
        finalizationRegistryConstructor.setConstructorType(JSConstructorType.FINALIZATION_REGISTRY);
        finalizationRegistryPrototype.definePropertyWritableConfigurable("constructor", finalizationRegistryConstructor);

        global.definePropertyWritableConfigurable("FinalizationRegistry", finalizationRegistryConstructor);
    }

    /**
     * Initialize Function constructor and prototype.
     */
    private void initializeFunctionConstructor(JSContext context, JSObject global) {
        // Create Function.prototype as a function (not a plain object)
        // According to ECMAScript spec, Function.prototype is itself a function
        // Use null name so toString() shows "function () {}" not "function anonymous() {}"
        JSNativeFunction functionPrototype = new JSNativeFunction(null, 0, (ctx, thisObj, args) -> JSUndefined.INSTANCE);
        // Remove the auto-created properties - Function.prototype has custom property descriptors
        functionPrototype.delete(PropertyKey.fromString("length"));
        functionPrototype.delete(PropertyKey.fromString("name"));
        functionPrototype.delete(PropertyKey.fromString("prototype"));

        functionPrototype.definePropertyWritableConfigurable("apply", new JSNativeFunction("apply", 2, FunctionPrototype::apply));
        functionPrototype.definePropertyWritableConfigurable("bind", new JSNativeFunction("bind", 1, FunctionPrototype::bind));
        functionPrototype.definePropertyWritableConfigurable("call", new JSNativeFunction("call", 1, FunctionPrototype::call));
        functionPrototype.definePropertyWritableConfigurable("toString", new JSNativeFunction("toString", 0, FunctionPrototype::toString_));

        // Add 'arguments' and 'caller' as accessor properties that throw TypeError
        // These properties exist for backwards compatibility but throw when accessed
        JSNativeFunction throwTypeError = new JSNativeFunction(
                "throwTypeError",
                0,
                (childContext, thisObj, args) ->
                        childContext.throwTypeError(
                                "'caller', 'callee', and 'arguments' properties may not be accessed on strict mode functions or the arguments objects for calls to them"));

        functionPrototype.defineGetterSetterConfigurable("arguments", throwTypeError, throwTypeError);
        functionPrototype.defineGetterSetterConfigurable("caller", throwTypeError, throwTypeError);

        // Add 'length' and 'name' data properties
        functionPrototype.definePropertyConfigurable("length", new JSNumber(0));
        functionPrototype.definePropertyConfigurable("name", new JSString(""));

        // Function constructor should be a function, not a plain object
        JSNativeFunction functionConstructor = new JSNativeFunction(JSFunction.NAME, 1, FunctionConstructor::call, true);
        functionConstructor.definePropertyReadonlyNonConfigurable("prototype", functionPrototype);
        functionPrototype.definePropertyWritableConfigurable("constructor", functionConstructor);

        global.definePropertyWritableConfigurable(JSFunction.NAME, functionConstructor);
    }

    /**
     * Recursively initialize prototype chains for all JSFunction instances in the global object.
     * This must be called after Function.prototype is set up.
     */
    private void initializeFunctionPrototypeChains(JSContext context, JSObject obj, Set<JSObject> visitedObjectSet) {
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
                if (func.getPrototype() == null) {
                    func.initializePrototypeChain(context);
                }
                // Recursively process the function's own properties
                initializeFunctionPrototypeChains(context, func, visitedObjectSet);
            } else if (value instanceof JSObject subObj) {
                // Recursively process nested objects
                initializeFunctionPrototypeChains(context, subObj, visitedObjectSet);
            }
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
    private void initializeGeneratorPrototype(JSContext context, JSObject global) {
        // Get Iterator.prototype for Generator.prototype to inherit from
        JSObject iteratorConstructor = (JSObject) global.get(JSIterator.NAME);
        JSObject iteratorPrototype = (JSObject) iteratorConstructor.get("prototype");

        // Create Generator.prototype inheriting from Iterator.prototype
        // This gives generators Symbol.iterator automatically
        JSObject generatorPrototype = context.createJSObject();
        generatorPrototype.setPrototype(iteratorPrototype);

        // Generator.prototype methods: writable+configurable (not enumerable)
        // Matches QuickJS js_generator_proto_funcs using JS_ITERATOR_NEXT_DEF
        generatorPrototype.definePropertyWritableConfigurable("next", new JSNativeFunction("next", 1, GeneratorPrototype::next));
        generatorPrototype.definePropertyWritableConfigurable("return", new JSNativeFunction("return", 1, GeneratorPrototype::returnMethod));
        generatorPrototype.definePropertyWritableConfigurable("throw", new JSNativeFunction("throw", 1, GeneratorPrototype::throwMethod));

        // Symbol.toStringTag = "Generator" (configurable only)
        // Matches QuickJS JS_PROP_STRING_DEF("[Symbol.toStringTag]", "Generator", JS_PROP_CONFIGURABLE)
        generatorPrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString("Generator"));

        // Create GeneratorFunction.prototype (not exposed in global scope)
        // All generator function objects (function*) inherit from this
        JSObject generatorFunctionPrototype = context.createJSObject();
        context.transferPrototype(generatorFunctionPrototype, JSFunction.NAME);

        // GeneratorFunction.prototype[Symbol.toStringTag] = "GeneratorFunction" (configurable)
        generatorFunctionPrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString("GeneratorFunction"));

        // Link: GeneratorFunction.prototype.prototype = Generator.prototype (configurable)
        generatorFunctionPrototype.definePropertyConfigurable("prototype", generatorPrototype);

        // Link: Generator.prototype.constructor = GeneratorFunction.prototype (configurable)
        generatorPrototype.definePropertyConfigurable("constructor", generatorFunctionPrototype);

        // Store in context for generator function prototype chain setup
        context.setGeneratorFunctionPrototype(generatorFunctionPrototype);
    }

    /**
     * Initialize Intl object.
     */
    private void initializeIntlObject(JSContext context, JSObject global) {
        JSObject intlObject = context.createJSObject();
        intlObject.set("getCanonicalLocales", new JSNativeFunction("getCanonicalLocales", 1, JSIntlObject::getCanonicalLocales));

        JSObject dateTimeFormatPrototype = context.createJSObject();
        dateTimeFormatPrototype.set("format", new JSNativeFunction("format", 1, JSIntlObject::dateTimeFormatFormat));
        dateTimeFormatPrototype.set("resolvedOptions", new JSNativeFunction("resolvedOptions", 0, JSIntlObject::dateTimeFormatResolvedOptions));
        JSNativeFunction dateTimeFormatConstructor = new JSNativeFunction(
                "DateTimeFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createDateTimeFormat(childContext, dateTimeFormatPrototype, args),
                true);
        dateTimeFormatConstructor.set("prototype", dateTimeFormatPrototype);
        dateTimeFormatConstructor.set("supportedLocalesOf", new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf));
        dateTimeFormatPrototype.set("constructor", dateTimeFormatConstructor);
        intlObject.set("DateTimeFormat", dateTimeFormatConstructor);

        JSObject numberFormatPrototype = context.createJSObject();
        numberFormatPrototype.set("format", new JSNativeFunction("format", 1, JSIntlObject::numberFormatFormat));
        numberFormatPrototype.set("resolvedOptions", new JSNativeFunction("resolvedOptions", 0, JSIntlObject::numberFormatResolvedOptions));
        JSNativeFunction numberFormatConstructor = new JSNativeFunction(
                "NumberFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createNumberFormat(childContext, numberFormatPrototype, args),
                true);
        numberFormatConstructor.set("prototype", numberFormatPrototype);
        numberFormatConstructor.set("supportedLocalesOf", new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf));
        numberFormatPrototype.set("constructor", numberFormatConstructor);
        intlObject.set("NumberFormat", numberFormatConstructor);

        JSObject collatorPrototype = context.createJSObject();
        collatorPrototype.set("compare", new JSNativeFunction("compare", 2, JSIntlObject::collatorCompare));
        collatorPrototype.set("resolvedOptions", new JSNativeFunction("resolvedOptions", 0, JSIntlObject::collatorResolvedOptions));
        JSNativeFunction collatorConstructor = new JSNativeFunction(
                "Collator",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createCollator(childContext, collatorPrototype, args),
                true);
        collatorConstructor.set("prototype", collatorPrototype);
        collatorConstructor.set("supportedLocalesOf", new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf));
        collatorPrototype.set("constructor", collatorConstructor);
        intlObject.set("Collator", collatorConstructor);

        JSObject pluralRulesPrototype = context.createJSObject();
        pluralRulesPrototype.set("select", new JSNativeFunction("select", 1, JSIntlObject::pluralRulesSelect));
        pluralRulesPrototype.set("resolvedOptions", new JSNativeFunction("resolvedOptions", 0, JSIntlObject::pluralRulesResolvedOptions));
        JSNativeFunction pluralRulesConstructor = new JSNativeFunction(
                "PluralRules",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createPluralRules(childContext, pluralRulesPrototype, args),
                true,
                true);
        pluralRulesConstructor.set("prototype", pluralRulesPrototype);
        pluralRulesConstructor.set("supportedLocalesOf", new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf));
        pluralRulesPrototype.set("constructor", pluralRulesConstructor);
        intlObject.set("PluralRules", pluralRulesConstructor);

        JSObject relativeTimeFormatPrototype = context.createJSObject();
        relativeTimeFormatPrototype.set("format", new JSNativeFunction("format", 2, JSIntlObject::relativeTimeFormatFormat));
        relativeTimeFormatPrototype.set("resolvedOptions", new JSNativeFunction("resolvedOptions", 0, JSIntlObject::relativeTimeFormatResolvedOptions));
        JSNativeFunction relativeTimeFormatConstructor = new JSNativeFunction(
                "RelativeTimeFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createRelativeTimeFormat(childContext, relativeTimeFormatPrototype, args),
                true,
                true);
        relativeTimeFormatConstructor.set("prototype", relativeTimeFormatPrototype);
        relativeTimeFormatConstructor.set("supportedLocalesOf", new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf));
        relativeTimeFormatPrototype.set("constructor", relativeTimeFormatConstructor);
        intlObject.set("RelativeTimeFormat", relativeTimeFormatConstructor);

        JSObject listFormatPrototype = context.createJSObject();
        listFormatPrototype.set("format", new JSNativeFunction("format", 1, JSIntlObject::listFormatFormat));
        listFormatPrototype.set("resolvedOptions", new JSNativeFunction("resolvedOptions", 0, JSIntlObject::listFormatResolvedOptions));
        JSNativeFunction listFormatConstructor = new JSNativeFunction(
                "ListFormat",
                0,
                (childContext, thisArg, args) -> JSIntlObject.createListFormat(childContext, listFormatPrototype, args),
                true,
                true);
        listFormatConstructor.set("prototype", listFormatPrototype);
        listFormatConstructor.set("supportedLocalesOf", new JSNativeFunction("supportedLocalesOf", 1, JSIntlObject::supportedLocalesOf));
        listFormatPrototype.set("constructor", listFormatConstructor);
        intlObject.set("ListFormat", listFormatConstructor);

        JSObject localePrototype = context.createJSObject();
        localePrototype.set("toString", new JSNativeFunction("toString", 0, JSIntlObject::localeToString));
        localePrototype.defineGetterConfigurable("baseName", JSIntlObject::localeGetBaseName);
        localePrototype.defineGetterConfigurable("language", JSIntlObject::localeGetLanguage);
        localePrototype.defineGetterConfigurable("script", JSIntlObject::localeGetScript);
        localePrototype.defineGetterConfigurable("region", JSIntlObject::localeGetRegion);
        JSNativeFunction localeConstructor = new JSNativeFunction(
                "Locale",
                1,
                (childContext, thisArg, args) -> JSIntlObject.createLocale(childContext, localePrototype, args),
                true,
                true);
        localeConstructor.set("prototype", localePrototype);
        localePrototype.set("constructor", localeConstructor);
        intlObject.set("Locale", localeConstructor);

        global.definePropertyWritableConfigurable("Intl", intlObject);
    }

    /**
     * Initialize Iterator constructor and prototype.
     * Based on ECMAScript 2024 Iterator specification.
     */
    private void initializeIteratorConstructor(JSContext context, JSObject global) {
        JSObject iteratorPrototype = context.createJSObject();

        iteratorPrototype.definePropertyWritableConfigurable("drop", new JSNativeFunction("drop", 1, IteratorPrototype::drop));
        iteratorPrototype.definePropertyWritableConfigurable("every", new JSNativeFunction("every", 1, IteratorPrototype::every));
        iteratorPrototype.definePropertyWritableConfigurable("filter", new JSNativeFunction("filter", 1, IteratorPrototype::filter));
        iteratorPrototype.definePropertyWritableConfigurable("find", new JSNativeFunction("find", 1, IteratorPrototype::find));
        iteratorPrototype.definePropertyWritableConfigurable("flatMap", new JSNativeFunction("flatMap", 1, IteratorPrototype::flatMap));
        iteratorPrototype.definePropertyWritableConfigurable("forEach", new JSNativeFunction("forEach", 1, IteratorPrototype::forEach));
        iteratorPrototype.definePropertyWritableConfigurable("map", new JSNativeFunction("map", 1, IteratorPrototype::map));
        iteratorPrototype.definePropertyWritableConfigurable("reduce", new JSNativeFunction("reduce", 1, IteratorPrototype::reduce));
        iteratorPrototype.definePropertyWritableConfigurable("some", new JSNativeFunction("some", 1, IteratorPrototype::some));
        iteratorPrototype.definePropertyWritableConfigurable("take", new JSNativeFunction("take", 1, IteratorPrototype::take));
        iteratorPrototype.definePropertyWritableConfigurable("toArray", new JSNativeFunction("toArray", 0, IteratorPrototype::toArray));
        iteratorPrototype.definePropertyWritableConfigurable(JSSymbol.ITERATOR, new JSNativeFunction("[Symbol.iterator]", 0, (childContext, thisArg, args) -> thisArg));

        JSNativeFunction iteratorConstructor = new JSNativeFunction(JSIterator.NAME, 0, IteratorConstructor::call, true, true);
        iteratorConstructor.definePropertyWritableConfigurable("prototype", iteratorPrototype);
        iteratorConstructor.definePropertyWritableConfigurable("concat", new JSNativeFunction("concat", 0, IteratorPrototype::concat));
        iteratorConstructor.definePropertyWritableConfigurable("from", new JSNativeFunction("from", 1, IteratorPrototype::from));

        JSNativeFunction constructorAccessor = new JSNativeFunction("constructor", 0, (childContext, thisArg, args) -> {
            if (args.length > 0) {
                if (!(args[0] instanceof JSObject valueObject)) {
                    return childContext.throwTypeError("not an object");
                }
                if (!(thisArg instanceof JSObject thisObject)) {
                    return childContext.throwTypeError("not an object");
                }
                thisObject.defineProperty(
                        PropertyKey.fromString("constructor"),
                        PropertyDescriptor.dataDescriptor(valueObject, true, false, true));
                return JSUndefined.INSTANCE;
            }
            return iteratorConstructor;
        });
        iteratorPrototype.defineProperty(
                PropertyKey.fromString("constructor"),
                PropertyDescriptor.accessorDescriptor(constructorAccessor, constructorAccessor, false, true));

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
                    PropertyKey toStringTagKey = PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG);
                    PropertyDescriptor descriptor = thisObject.getOwnPropertyDescriptor(toStringTagKey);
                    if (descriptor != null) {
                        thisObject.set(toStringTagKey, value, childContext);
                    } else {
                        thisObject.defineProperty(
                                toStringTagKey,
                                PropertyDescriptor.dataDescriptor(value, true, true, true));
                    }
                    return JSUndefined.INSTANCE;
                },
                false);
        iteratorPrototype.defineProperty(
                PropertyKey.fromSymbol(JSSymbol.TO_STRING_TAG),
                PropertyDescriptor.accessorDescriptor(toStringTagGetter, toStringTagSetter, false, true));

        global.definePropertyWritableConfigurable(JSIterator.NAME, iteratorConstructor);
    }

    /**
     * Initialize JSON object.
     */
    private void initializeJSONObject(JSContext context, JSObject global) {
        JSObject json = context.createJSObject();
        json.definePropertyWritableConfigurable("parse", new JSNativeFunction("parse", 2, JSONObject::parse));
        json.definePropertyWritableConfigurable("stringify", new JSNativeFunction("stringify", 3, JSONObject::stringify));
        json.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString("JSON"));

        global.definePropertyWritableConfigurable("JSON", json);
    }

    /**
     * Initialize Map constructor and prototype methods.
     */
    private void initializeMapConstructor(JSContext context, JSObject global) {
        // Create Map.prototype
        JSObject mapPrototype = context.createJSObject();
        mapPrototype.definePropertyWritableConfigurable("clear", new JSNativeFunction("clear", 0, MapPrototype::clear));
        mapPrototype.definePropertyWritableConfigurable("delete", new JSNativeFunction("delete", 1, MapPrototype::delete));
        mapPrototype.definePropertyWritableConfigurable("forEach", new JSNativeFunction("forEach", 1, MapPrototype::forEach));
        mapPrototype.definePropertyWritableConfigurable("get", new JSNativeFunction("get", 1, MapPrototype::get));
        mapPrototype.definePropertyWritableConfigurable("getOrInsert", new JSNativeFunction("getOrInsert", 2, MapPrototype::getOrInsert));
        mapPrototype.definePropertyWritableConfigurable("getOrInsertComputed", new JSNativeFunction("getOrInsertComputed", 2, MapPrototype::getOrInsertComputed));
        mapPrototype.definePropertyWritableConfigurable("has", new JSNativeFunction("has", 1, MapPrototype::has));
        mapPrototype.definePropertyWritableConfigurable("set", new JSNativeFunction("set", 2, MapPrototype::set));
        // Create entries function once and use it for both entries and Symbol.iterator (ES spec requirement)
        JSNativeFunction entriesFunction = new JSNativeFunction("entries", 0, IteratorPrototype::mapEntriesIterator);
        mapPrototype.definePropertyWritableConfigurable("entries", entriesFunction);
        mapPrototype.definePropertyWritableConfigurable("keys", new JSNativeFunction("keys", 0, IteratorPrototype::mapKeysIterator));
        mapPrototype.definePropertyWritableConfigurable("values", new JSNativeFunction("values", 0, IteratorPrototype::mapValuesIterator));
        // Map.prototype[Symbol.iterator] is the same function object as entries() (QuickJS uses JS_ALIAS_DEF)
        mapPrototype.definePropertyWritableConfigurable(JSSymbol.ITERATOR, entriesFunction);

        // Map.prototype.size getter
        mapPrototype.defineGetterConfigurable("size", MapPrototype::getSize);
        mapPrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString(JSMap.NAME));

        // Create Map constructor as JSNativeFunction
        JSNativeFunction mapConstructor = new JSNativeFunction("Map", 0, MapConstructor::call, true, true);
        mapConstructor.set("prototype", mapPrototype);
        mapConstructor.setConstructorType(JSConstructorType.MAP); // Mark as Map constructor
        mapPrototype.definePropertyWritableConfigurable("constructor", mapConstructor);

        // Map static methods
        mapConstructor.definePropertyWritableConfigurable("groupBy", new JSNativeFunction("groupBy", 2, MapConstructor::groupBy));
        mapConstructor.defineGetterConfigurable(JSSymbol.SPECIES, MapConstructor::getSpecies);

        global.definePropertyWritableConfigurable("Map", mapConstructor);
    }

    /**
     * Initialize Math object.
     */
    private void initializeMathObject(JSContext context, JSObject global) {
        JSObject math = context.createJSObject();

        // Math constants
        math.definePropertyReadonlyNonConfigurable("E", new JSNumber(MathObject.E));
        math.definePropertyReadonlyNonConfigurable("LN10", new JSNumber(MathObject.LN10));
        math.definePropertyReadonlyNonConfigurable("LN2", new JSNumber(MathObject.LN2));
        math.definePropertyReadonlyNonConfigurable("LOG10E", new JSNumber(MathObject.LOG10E));
        math.definePropertyReadonlyNonConfigurable("LOG2E", new JSNumber(MathObject.LOG2E));
        math.definePropertyReadonlyNonConfigurable("PI", new JSNumber(MathObject.PI));
        math.definePropertyReadonlyNonConfigurable("SQRT1_2", new JSNumber(MathObject.SQRT1_2));
        math.definePropertyReadonlyNonConfigurable("SQRT2", new JSNumber(MathObject.SQRT2));

        // Math methods
        math.definePropertyWritableConfigurable("abs", new JSNativeFunction("abs", 1, MathObject::abs));
        math.definePropertyWritableConfigurable("acos", new JSNativeFunction("acos", 1, MathObject::acos));
        math.definePropertyWritableConfigurable("acosh", new JSNativeFunction("acosh", 1, MathObject::acosh));
        math.definePropertyWritableConfigurable("asin", new JSNativeFunction("asin", 1, MathObject::asin));
        math.definePropertyWritableConfigurable("asinh", new JSNativeFunction("asinh", 1, MathObject::asinh));
        math.definePropertyWritableConfigurable("atan", new JSNativeFunction("atan", 1, MathObject::atan));
        math.definePropertyWritableConfigurable("atanh", new JSNativeFunction("atanh", 1, MathObject::atanh));
        math.definePropertyWritableConfigurable("atan2", new JSNativeFunction("atan2", 2, MathObject::atan2));
        math.definePropertyWritableConfigurable("cbrt", new JSNativeFunction("cbrt", 1, MathObject::cbrt));
        math.definePropertyWritableConfigurable("ceil", new JSNativeFunction("ceil", 1, MathObject::ceil));
        math.definePropertyWritableConfigurable("clz32", new JSNativeFunction("clz32", 1, MathObject::clz32));
        math.definePropertyWritableConfigurable("cos", new JSNativeFunction("cos", 1, MathObject::cos));
        math.definePropertyWritableConfigurable("cosh", new JSNativeFunction("cosh", 1, MathObject::cosh));
        math.definePropertyWritableConfigurable("exp", new JSNativeFunction("exp", 1, MathObject::exp));
        math.definePropertyWritableConfigurable("expm1", new JSNativeFunction("expm1", 1, MathObject::expm1));
        math.definePropertyWritableConfigurable("f16round", new JSNativeFunction("f16round", 1, MathObject::f16round));
        math.definePropertyWritableConfigurable("floor", new JSNativeFunction("floor", 1, MathObject::floor));
        math.definePropertyWritableConfigurable("fround", new JSNativeFunction("fround", 1, MathObject::fround));
        math.definePropertyWritableConfigurable("hypot", new JSNativeFunction("hypot", 2, MathObject::hypot));
        math.definePropertyWritableConfigurable("imul", new JSNativeFunction("imul", 2, MathObject::imul));
        math.definePropertyWritableConfigurable("log", new JSNativeFunction("log", 1, MathObject::log));
        math.definePropertyWritableConfigurable("log1p", new JSNativeFunction("log1p", 1, MathObject::log1p));
        math.definePropertyWritableConfigurable("log10", new JSNativeFunction("log10", 1, MathObject::log10));
        math.definePropertyWritableConfigurable("log2", new JSNativeFunction("log2", 1, MathObject::log2));
        math.definePropertyWritableConfigurable("max", new JSNativeFunction("max", 2, MathObject::max));
        math.definePropertyWritableConfigurable("min", new JSNativeFunction("min", 2, MathObject::min));
        math.definePropertyWritableConfigurable("pow", new JSNativeFunction("pow", 2, MathObject::pow));
        math.definePropertyWritableConfigurable("random", new JSNativeFunction("random", 0, MathObject::random));
        math.definePropertyWritableConfigurable("round", new JSNativeFunction("round", 1, MathObject::round));
        math.definePropertyWritableConfigurable("sign", new JSNativeFunction("sign", 1, MathObject::sign));
        math.definePropertyWritableConfigurable("sin", new JSNativeFunction("sin", 1, MathObject::sin));
        math.definePropertyWritableConfigurable("sinh", new JSNativeFunction("sinh", 1, MathObject::sinh));
        math.definePropertyWritableConfigurable("sqrt", new JSNativeFunction("sqrt", 1, MathObject::sqrt));
        math.definePropertyWritableConfigurable("sumPrecise", new JSNativeFunction("sumPrecise", 1, MathObject::sumPrecise));
        math.definePropertyWritableConfigurable("tan", new JSNativeFunction("tan", 1, MathObject::tan));
        math.definePropertyWritableConfigurable("tanh", new JSNativeFunction("tanh", 1, MathObject::tanh));
        math.definePropertyWritableConfigurable("trunc", new JSNativeFunction("trunc", 1, MathObject::trunc));
        math.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString("Math"));

        global.definePropertyWritableConfigurable("Math", math);
    }

    /**
     * Initialize Number constructor and prototype.
     */
    private void initializeNumberConstructor(JSContext context, JSObject global) {
        // Create Number.prototype
        JSObject numberPrototype = context.createJSObject();
        numberPrototype.definePropertyWritableConfigurable("toExponential", new JSNativeFunction("toExponential", 1, NumberPrototype::toExponential));
        numberPrototype.definePropertyWritableConfigurable("toFixed", new JSNativeFunction("toFixed", 1, NumberPrototype::toFixed));
        numberPrototype.definePropertyWritableConfigurable("toLocaleString", new JSNativeFunction("toLocaleString", 0, NumberPrototype::toLocaleString));
        numberPrototype.definePropertyWritableConfigurable("toPrecision", new JSNativeFunction("toPrecision", 1, NumberPrototype::toPrecision));
        numberPrototype.definePropertyWritableConfigurable("toString", new JSNativeFunction("toString", 1, NumberPrototype::toString));
        numberPrototype.definePropertyWritableConfigurable("valueOf", new JSNativeFunction("valueOf", 0, NumberPrototype::valueOf));

        // Create Number constructor
        JSNativeFunction numberConstructor = new JSNativeFunction("Number", 1, NumberConstructor::call, true);
        numberConstructor.definePropertyReadonlyNonConfigurable("prototype", numberPrototype);
        numberConstructor.setConstructorType(JSConstructorType.NUMBER_OBJECT); // Mark as Number constructor
        numberPrototype.definePropertyWritableConfigurable("constructor", numberConstructor);

        // Number static methods
        numberConstructor.definePropertyWritableConfigurable("isFinite", new JSNativeFunction("isFinite", 1, NumberPrototype::isFinite));
        numberConstructor.definePropertyWritableConfigurable("isInteger", new JSNativeFunction("isInteger", 1, NumberPrototype::isInteger));
        numberConstructor.definePropertyWritableConfigurable("isNaN", new JSNativeFunction("isNaN", 1, NumberPrototype::isNaN));
        numberConstructor.definePropertyWritableConfigurable("isSafeInteger", new JSNativeFunction("isSafeInteger", 1, NumberPrototype::isSafeInteger));

        // QuickJS compatibility: Number.parseInt/parseFloat are aliases of global parseInt/parseFloat.
        JSValue globalParseInt = global.get("parseInt");
        JSValue globalParseFloat = global.get("parseFloat");
        numberConstructor.definePropertyReadonlyNonConfigurable("EPSILON", new JSNumber(Math.ulp(1.0)));
        numberConstructor.definePropertyReadonlyNonConfigurable("MAX_SAFE_INTEGER", new JSNumber(9007199254740991d));
        numberConstructor.definePropertyReadonlyNonConfigurable("MAX_VALUE", new JSNumber(Double.MAX_VALUE));
        numberConstructor.definePropertyReadonlyNonConfigurable("MIN_SAFE_INTEGER", new JSNumber(-9007199254740991d));
        numberConstructor.definePropertyReadonlyNonConfigurable("MIN_VALUE", new JSNumber(Double.MIN_VALUE));
        numberConstructor.definePropertyReadonlyNonConfigurable("NEGATIVE_INFINITY", new JSNumber(Double.NEGATIVE_INFINITY));
        numberConstructor.definePropertyReadonlyNonConfigurable("NaN", new JSNumber(Double.NaN));
        numberConstructor.definePropertyReadonlyNonConfigurable("POSITIVE_INFINITY", new JSNumber(Double.POSITIVE_INFINITY));
        numberConstructor.definePropertyWritableConfigurable("parseFloat", globalParseFloat);
        numberConstructor.definePropertyWritableConfigurable("parseInt", globalParseInt);

        global.definePropertyWritableConfigurable("Number", numberConstructor);
    }

    /**
     * Initialize Object constructor and static methods.
     */
    private void initializeObjectConstructor(JSContext context, JSObject global) {
        // Create Object.prototype
        JSObject objectPrototype = context.createJSObject();
        objectPrototype.definePropertyWritableConfigurable("__defineGetter__", new JSNativeFunction("__defineGetter__", 2, ObjectPrototype::__defineGetter__));
        objectPrototype.definePropertyWritableConfigurable("__defineSetter__", new JSNativeFunction("__defineSetter__", 2, ObjectPrototype::__defineSetter__));
        objectPrototype.definePropertyWritableConfigurable("__lookupGetter__", new JSNativeFunction("__lookupGetter__", 1, ObjectPrototype::__lookupGetter__));
        objectPrototype.definePropertyWritableConfigurable("__lookupSetter__", new JSNativeFunction("__lookupSetter__", 1, ObjectPrototype::__lookupSetter__));
        objectPrototype.definePropertyWritableConfigurable("hasOwnProperty", new JSNativeFunction("hasOwnProperty", 1, ObjectPrototype::hasOwnProperty));
        objectPrototype.definePropertyWritableConfigurable("isPrototypeOf", new JSNativeFunction("isPrototypeOf", 1, ObjectPrototype::isPrototypeOf));
        objectPrototype.definePropertyWritableConfigurable("propertyIsEnumerable", new JSNativeFunction("propertyIsEnumerable", 1, ObjectPrototype::propertyIsEnumerable));
        objectPrototype.definePropertyWritableConfigurable("toLocaleString", new JSNativeFunction("toLocaleString", 0, ObjectPrototype::toLocaleString));
        objectPrototype.definePropertyWritableConfigurable("toString", new JSNativeFunction("toString", 0, ObjectPrototype::toString));
        objectPrototype.definePropertyWritableConfigurable("valueOf", new JSNativeFunction("valueOf", 0, ObjectPrototype::valueOf));

        // Define __proto__ as an accessor property
        PropertyDescriptor protoDesc = PropertyDescriptor.accessorDescriptor(
                new JSNativeFunction("get __proto__", 0, ObjectPrototype::__proto__Getter),
                new JSNativeFunction("set __proto__", 1, ObjectPrototype::__proto__Setter),
                false,
                true
        );
        objectPrototype.defineProperty(PropertyKey.fromString("__proto__"), protoDesc);

        // Create Object constructor
        JSNativeFunction objectConstructor = new JSNativeFunction("Object", 1, ObjectConstructor::call, true);
        objectConstructor.definePropertyReadonlyNonConfigurable("prototype", objectPrototype);
        objectPrototype.definePropertyWritableConfigurable("constructor", objectConstructor);

        // Object static methods
        objectConstructor.definePropertyWritableConfigurable("assign", new JSNativeFunction("assign", 2, ObjectConstructor::assign));
        objectConstructor.definePropertyWritableConfigurable("create", new JSNativeFunction("create", 2, ObjectConstructor::create));
        objectConstructor.definePropertyWritableConfigurable("defineProperties", new JSNativeFunction("defineProperties", 2, ObjectConstructor::defineProperties));
        objectConstructor.definePropertyWritableConfigurable("defineProperty", new JSNativeFunction("defineProperty", 3, ObjectPrototype::defineProperty));
        objectConstructor.definePropertyWritableConfigurable("entries", new JSNativeFunction("entries", 1, ObjectConstructor::entries));
        objectConstructor.definePropertyWritableConfigurable("freeze", new JSNativeFunction("freeze", 1, ObjectConstructor::freeze));
        objectConstructor.definePropertyWritableConfigurable("fromEntries", new JSNativeFunction("fromEntries", 1, ObjectConstructor::fromEntries));
        objectConstructor.definePropertyWritableConfigurable("getOwnPropertyDescriptor", new JSNativeFunction("getOwnPropertyDescriptor", 2, ObjectConstructor::getOwnPropertyDescriptor));
        objectConstructor.definePropertyWritableConfigurable("getOwnPropertyDescriptors", new JSNativeFunction("getOwnPropertyDescriptors", 1, ObjectConstructor::getOwnPropertyDescriptors));
        objectConstructor.definePropertyWritableConfigurable("getOwnPropertyNames", new JSNativeFunction("getOwnPropertyNames", 1, ObjectConstructor::getOwnPropertyNames));
        objectConstructor.definePropertyWritableConfigurable("getOwnPropertySymbols", new JSNativeFunction("getOwnPropertySymbols", 1, ObjectConstructor::getOwnPropertySymbols));
        objectConstructor.definePropertyWritableConfigurable("getPrototypeOf", new JSNativeFunction("getPrototypeOf", 1, ObjectConstructor::getPrototypeOf));
        objectConstructor.definePropertyWritableConfigurable("groupBy", new JSNativeFunction("groupBy", 2, ObjectConstructor::groupBy));
        objectConstructor.definePropertyWritableConfigurable("hasOwn", new JSNativeFunction("hasOwn", 2, ObjectConstructor::hasOwn));
        objectConstructor.definePropertyWritableConfigurable("is", new JSNativeFunction("is", 2, ObjectConstructor::is));
        objectConstructor.definePropertyWritableConfigurable("isExtensible", new JSNativeFunction("isExtensible", 1, ObjectConstructor::isExtensible));
        objectConstructor.definePropertyWritableConfigurable("isFrozen", new JSNativeFunction("isFrozen", 1, ObjectConstructor::isFrozen));
        objectConstructor.definePropertyWritableConfigurable("isSealed", new JSNativeFunction("isSealed", 1, ObjectConstructor::isSealed));
        objectConstructor.definePropertyWritableConfigurable("keys", new JSNativeFunction("keys", 1, ObjectConstructor::keys));
        objectConstructor.definePropertyWritableConfigurable("preventExtensions", new JSNativeFunction("preventExtensions", 1, ObjectConstructor::preventExtensions));
        objectConstructor.definePropertyWritableConfigurable("seal", new JSNativeFunction("seal", 1, ObjectConstructor::seal));
        objectConstructor.definePropertyWritableConfigurable("setPrototypeOf", new JSNativeFunction("setPrototypeOf", 2, ObjectConstructor::setPrototypeOf));
        objectConstructor.definePropertyWritableConfigurable("values", new JSNativeFunction("values", 1, ObjectConstructor::values));

        global.definePropertyWritableConfigurable("Object", objectConstructor);
    }

    /**
     * Initialize Promise constructor and prototype methods.
     */
    private void initializePromiseConstructor(JSContext context, JSObject global) {
        // Create Promise.prototype
        JSObject promisePrototype = context.createJSObject();
        promisePrototype.definePropertyWritableConfigurable("catch", new JSNativeFunction("catch", 1, PromisePrototype::catchMethod));
        promisePrototype.definePropertyWritableConfigurable("finally", new JSNativeFunction("finally", 1, PromisePrototype::finallyMethod));
        promisePrototype.definePropertyWritableConfigurable("then", new JSNativeFunction("then", 2, PromisePrototype::then));
        promisePrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString("Promise"));

        // Create Promise constructor as JSNativeFunction
        JSNativeFunction promiseConstructor = new JSNativeFunction("Promise", 1, PromiseConstructor::call, true, true);
        context.transferPrototype(promiseConstructor, JSFunction.NAME);
        promiseConstructor.definePropertyReadonlyNonConfigurable("prototype", promisePrototype);
        promiseConstructor.setConstructorType(JSConstructorType.PROMISE); // Mark as Promise constructor
        promisePrototype.definePropertyWritableConfigurable("constructor", promiseConstructor);

        // Static methods
        promiseConstructor.definePropertyWritableConfigurable("all", new JSNativeFunction("all", 1, PromiseConstructor::all));
        promiseConstructor.definePropertyWritableConfigurable("allSettled", new JSNativeFunction("allSettled", 1, PromiseConstructor::allSettled));
        promiseConstructor.definePropertyWritableConfigurable("any", new JSNativeFunction("any", 1, PromiseConstructor::any));
        promiseConstructor.definePropertyWritableConfigurable("race", new JSNativeFunction("race", 1, PromiseConstructor::race));
        promiseConstructor.definePropertyWritableConfigurable("reject", new JSNativeFunction("reject", 1, PromiseConstructor::reject));
        promiseConstructor.definePropertyWritableConfigurable("resolve", new JSNativeFunction("resolve", 1, PromiseConstructor::resolve));
        promiseConstructor.definePropertyWritableConfigurable("try", new JSNativeFunction("try", 1, PromiseConstructor::tryMethod));
        promiseConstructor.definePropertyWritableConfigurable("withResolvers", new JSNativeFunction("withResolvers", 0, PromiseConstructor::withResolvers));
        promiseConstructor.defineGetterConfigurable(JSSymbol.SPECIES, PromiseConstructor::getSpecies);

        global.definePropertyWritableConfigurable("Promise", promiseConstructor);
    }

    /**
     * Initialize Proxy constructor.
     */
    private void initializeProxyConstructor(JSContext context, JSObject global) {
        // Create Proxy constructor as JSNativeFunction
        // Proxy requires 'new' and takes 2 arguments (target, handler)
        JSNativeFunction proxyConstructor = new JSNativeFunction("Proxy", 2, ProxyConstructor::call, true, true);
        proxyConstructor.setConstructorType(JSConstructorType.PROXY);
        context.transferPrototype(proxyConstructor, JSFunction.NAME);

        // Proxy has no "prototype" own property per QuickJS / spec.
        proxyConstructor.delete(PropertyKey.fromString("prototype"));

        // Add static methods.
        proxyConstructor.definePropertyWritableConfigurable("revocable", new JSNativeFunction("revocable", 2, ProxyConstructor::revocable));

        global.definePropertyWritableConfigurable("Proxy", proxyConstructor);
    }

    /**
     * Initialize Reflect object.
     */
    private void initializeReflectObject(JSContext context, JSObject global) {
        JSObject reflect = context.createJSObject();
        reflect.definePropertyWritableConfigurable("apply", new JSNativeFunction("apply", 3, JSReflectObject::apply));
        reflect.definePropertyWritableConfigurable("construct", new JSNativeFunction("construct", 2, JSReflectObject::construct));
        reflect.definePropertyWritableConfigurable("defineProperty", new JSNativeFunction("defineProperty", 3, JSReflectObject::defineProperty));
        reflect.definePropertyWritableConfigurable("deleteProperty", new JSNativeFunction("deleteProperty", 2, JSReflectObject::deleteProperty));
        reflect.definePropertyWritableConfigurable("get", new JSNativeFunction("get", 2, JSReflectObject::get));
        reflect.definePropertyWritableConfigurable("getOwnPropertyDescriptor", new JSNativeFunction("getOwnPropertyDescriptor", 2, JSReflectObject::getOwnPropertyDescriptor));
        reflect.definePropertyWritableConfigurable("getPrototypeOf", new JSNativeFunction("getPrototypeOf", 1, JSReflectObject::getPrototypeOf));
        reflect.definePropertyWritableConfigurable("has", new JSNativeFunction("has", 2, JSReflectObject::has));
        reflect.definePropertyWritableConfigurable("isExtensible", new JSNativeFunction("isExtensible", 1, JSReflectObject::isExtensible));
        reflect.definePropertyWritableConfigurable("ownKeys", new JSNativeFunction("ownKeys", 1, JSReflectObject::ownKeys));
        reflect.definePropertyWritableConfigurable("preventExtensions", new JSNativeFunction("preventExtensions", 1, JSReflectObject::preventExtensions));
        reflect.definePropertyWritableConfigurable("set", new JSNativeFunction("set", 3, JSReflectObject::set));
        reflect.definePropertyWritableConfigurable("setPrototypeOf", new JSNativeFunction("setPrototypeOf", 2, JSReflectObject::setPrototypeOf));
        reflect.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString("Reflect"));

        global.definePropertyWritableConfigurable("Reflect", reflect);
    }

    /**
     * Initialize RegExp constructor and prototype.
     */
    private void initializeRegExpConstructor(JSContext context, JSObject global) {
        // Create RegExp.prototype
        JSObject regexpPrototype = context.createJSObject();
        regexpPrototype.set("test", new JSNativeFunction("test", 1, RegExpPrototype::test));
        regexpPrototype.set("exec", new JSNativeFunction("exec", 1, RegExpPrototype::exec));
        regexpPrototype.definePropertyWritableConfigurable("compile", new JSNativeFunction("compile", 2, RegExpPrototype::compile));
        regexpPrototype.set("toString", new JSNativeFunction("toString", 0, RegExpPrototype::toStringMethod));
        regexpPrototype.set(PropertyKey.fromSymbol(JSSymbol.SPLIT), new JSNativeFunction("[Symbol.split]", 2, RegExpPrototype::symbolSplit));

        // Accessor properties
        regexpPrototype.defineGetterConfigurable("dotAll", RegExpPrototype::getDotAll);
        regexpPrototype.defineGetterConfigurable("flags", RegExpPrototype::getFlags);
        regexpPrototype.defineGetterConfigurable("global", RegExpPrototype::getGlobal);
        regexpPrototype.defineGetterConfigurable("hasIndices", RegExpPrototype::getHasIndices);
        regexpPrototype.defineGetterConfigurable("ignoreCase", RegExpPrototype::getIgnoreCase);
        regexpPrototype.defineGetterConfigurable("multiline", RegExpPrototype::getMultiline);
        regexpPrototype.defineGetterConfigurable("source", RegExpPrototype::getSource);
        regexpPrototype.defineGetterConfigurable("sticky", RegExpPrototype::getSticky);
        regexpPrototype.defineGetterConfigurable("unicode", RegExpPrototype::getUnicode);
        regexpPrototype.defineGetterConfigurable("unicodeSets", RegExpPrototype::getUnicodeSets);
        regexpPrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString("RegExp"));

        // Create RegExp constructor as a function
        JSNativeFunction regexpConstructor = new JSNativeFunction("RegExp", 2, RegExpConstructor::call, true);
        regexpConstructor.set("prototype", regexpPrototype);
        regexpConstructor.setConstructorType(JSConstructorType.REGEXP);
        regexpPrototype.set("constructor", regexpConstructor);

        // AnnexB legacy RegExp static accessor properties.
        // Each getter validates SameValue(C, thisValue) per GetLegacyRegExpStaticProperty.
        regexpConstructor.defineGetterConfigurable("$&");
        regexpConstructor.defineGetterConfigurable("$'");
        regexpConstructor.defineGetterConfigurable("$+");
        regexpConstructor.defineGetterConfigurable("$`");
        regexpConstructor.defineGetterConfigurable("lastMatch");
        regexpConstructor.defineGetterConfigurable("lastParen");
        regexpConstructor.defineGetterConfigurable("leftContext");
        regexpConstructor.defineGetterConfigurable("rightContext");
        regexpConstructor.defineGetterSetterConfigurable("$_");
        regexpConstructor.defineGetterSetterConfigurable("input");
        // $1..$9
        for (int i = 1; i <= 9; i++) {
            regexpConstructor.defineGetterConfigurable("$" + i);
        }

        global.definePropertyWritableConfigurable("RegExp", regexpConstructor);
    }

    /**
     * Initialize Set constructor and prototype methods.
     */
    private void initializeSetConstructor(JSContext context, JSObject global) {
        // Create Set.prototype
        JSObject setPrototype = context.createJSObject();
        setPrototype.definePropertyWritableConfigurable("add", new JSNativeFunction("add", 1, SetPrototype::add));
        setPrototype.definePropertyWritableConfigurable("clear", new JSNativeFunction("clear", 0, SetPrototype::clear));
        setPrototype.definePropertyWritableConfigurable("delete", new JSNativeFunction("delete", 1, SetPrototype::delete));
        setPrototype.definePropertyWritableConfigurable("difference", new JSNativeFunction("difference", 1, SetPrototype::difference));
        setPrototype.definePropertyWritableConfigurable("entries", new JSNativeFunction("entries", 0, SetPrototype::entries));
        setPrototype.definePropertyWritableConfigurable("forEach", new JSNativeFunction("forEach", 1, SetPrototype::forEach));
        setPrototype.definePropertyWritableConfigurable("has", new JSNativeFunction("has", 1, SetPrototype::has));
        setPrototype.definePropertyWritableConfigurable("intersection", new JSNativeFunction("intersection", 1, SetPrototype::intersection));
        setPrototype.definePropertyWritableConfigurable("isDisjointFrom", new JSNativeFunction("isDisjointFrom", 1, SetPrototype::isDisjointFrom));
        setPrototype.definePropertyWritableConfigurable("isSubsetOf", new JSNativeFunction("isSubsetOf", 1, SetPrototype::isSubsetOf));
        setPrototype.definePropertyWritableConfigurable("isSupersetOf", new JSNativeFunction("isSupersetOf", 1, SetPrototype::isSupersetOf));
        setPrototype.definePropertyWritableConfigurable("symmetricDifference", new JSNativeFunction("symmetricDifference", 1, SetPrototype::symmetricDifference));
        setPrototype.definePropertyWritableConfigurable("union", new JSNativeFunction("union", 1, SetPrototype::union));

        // Create values function - keys and Symbol.iterator will alias to this
        JSNativeFunction valuesFunction = new JSNativeFunction("values", 0, SetPrototype::values);
        setPrototype.definePropertyWritableConfigurable("values", valuesFunction);
        // Set.prototype.keys is the same function object as values (ES spec requirement)
        setPrototype.definePropertyWritableConfigurable("keys", valuesFunction);
        // Set.prototype[Symbol.iterator] is the same as values()
        setPrototype.definePropertyWritableConfigurable(JSSymbol.ITERATOR, valuesFunction);

        // Set.prototype.size getter
        setPrototype.defineGetterConfigurable("size", SetPrototype::getSize);
        setPrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString(JSSet.NAME));

        // Create Set constructor as a function
        JSNativeFunction setConstructor = new JSNativeFunction("Set", 0, SetConstructor::call, true, true);
        setConstructor.set("prototype", setPrototype);
        setConstructor.setConstructorType(JSConstructorType.SET);
        setPrototype.definePropertyWritableConfigurable("constructor", setConstructor);

        setConstructor.definePropertyWritableConfigurable("groupBy", new JSNativeFunction("groupBy", 2, SetConstructor::groupBy));
        setConstructor.defineGetterConfigurable(JSSymbol.SPECIES, SetConstructor::getSpecies);

        global.definePropertyWritableConfigurable("Set", setConstructor);
    }

    /**
     * Initialize SharedArrayBuffer constructor and prototype.
     */
    private void initializeSharedArrayBufferConstructor(JSContext context, JSObject global) {
        // Create SharedArrayBuffer.prototype
        JSObject sharedArrayBufferPrototype = context.createJSObject();
        sharedArrayBufferPrototype.definePropertyWritableConfigurable("grow", new JSNativeFunction("grow", 1, SharedArrayBufferPrototype::grow));
        sharedArrayBufferPrototype.definePropertyWritableConfigurable("slice", new JSNativeFunction("slice", 2, SharedArrayBufferPrototype::slice));

        // Define getter properties
        sharedArrayBufferPrototype.defineGetterConfigurable("byteLength", SharedArrayBufferPrototype::getByteLength);
        sharedArrayBufferPrototype.defineGetterConfigurable("growable", SharedArrayBufferPrototype::getGrowable);
        sharedArrayBufferPrototype.defineGetterConfigurable("maxByteLength", SharedArrayBufferPrototype::getMaxByteLength);
        sharedArrayBufferPrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString("SharedArrayBuffer"));

        // Create SharedArrayBuffer constructor as a function
        JSNativeFunction sharedArrayBufferConstructor = new JSNativeFunction(
                "SharedArrayBuffer",
                1,
                SharedArrayBufferConstructor::call,
                true,  // isConstructor
                true   // requiresNew - SharedArrayBuffer() must be called with new
        );
        sharedArrayBufferConstructor.definePropertyReadonlyNonConfigurable("prototype", sharedArrayBufferPrototype);
        sharedArrayBufferConstructor.setConstructorType(JSConstructorType.SHARED_ARRAY_BUFFER);
        sharedArrayBufferPrototype.definePropertyWritableConfigurable("constructor", sharedArrayBufferConstructor);
        sharedArrayBufferConstructor.defineGetterConfigurable(JSSymbol.SPECIES, SharedArrayBufferConstructor::getSpecies);

        global.definePropertyWritableConfigurable("SharedArrayBuffer", sharedArrayBufferConstructor);
    }

    /**
     * Initialize String constructor and prototype.
     */
    private void initializeStringConstructor(JSContext context, JSObject global) {
        // Create String.prototype
        JSObject stringPrototype = context.createJSObject();
        stringPrototype.definePropertyWritableConfigurable("at", new JSNativeFunction("at", 1, StringPrototype::at));
        stringPrototype.definePropertyWritableConfigurable("charAt", new JSNativeFunction("charAt", 1, StringPrototype::charAt));
        stringPrototype.definePropertyWritableConfigurable("charCodeAt", new JSNativeFunction("charCodeAt", 1, StringPrototype::charCodeAt));
        stringPrototype.definePropertyWritableConfigurable("codePointAt", new JSNativeFunction("codePointAt", 1, StringPrototype::codePointAt));
        stringPrototype.definePropertyWritableConfigurable("concat", new JSNativeFunction("concat", 1, StringPrototype::concat));
        stringPrototype.definePropertyWritableConfigurable("endsWith", new JSNativeFunction("endsWith", 1, StringPrototype::endsWith));
        stringPrototype.definePropertyWritableConfigurable("includes", new JSNativeFunction("includes", 1, StringPrototype::includes));
        stringPrototype.definePropertyWritableConfigurable("indexOf", new JSNativeFunction("indexOf", 1, StringPrototype::indexOf));
        stringPrototype.definePropertyWritableConfigurable("lastIndexOf", new JSNativeFunction("lastIndexOf", 1, StringPrototype::lastIndexOf));
        stringPrototype.definePropertyWritableConfigurable("localeCompare", new JSNativeFunction("localeCompare", 1, StringPrototype::localeCompare));
        stringPrototype.definePropertyWritableConfigurable("match", new JSNativeFunction("match", 1, StringPrototype::match));
        stringPrototype.definePropertyWritableConfigurable("matchAll", new JSNativeFunction("matchAll", 1, StringPrototype::matchAll));
        stringPrototype.definePropertyWritableConfigurable("padEnd", new JSNativeFunction("padEnd", 1, StringPrototype::padEnd));
        stringPrototype.definePropertyWritableConfigurable("padStart", new JSNativeFunction("padStart", 1, StringPrototype::padStart));
        stringPrototype.definePropertyWritableConfigurable("repeat", new JSNativeFunction("repeat", 1, StringPrototype::repeat));
        stringPrototype.definePropertyWritableConfigurable("replace", new JSNativeFunction("replace", 2, StringPrototype::replace));
        stringPrototype.definePropertyWritableConfigurable("replaceAll", new JSNativeFunction("replaceAll", 2, StringPrototype::replaceAll));
        stringPrototype.definePropertyWritableConfigurable("search", new JSNativeFunction("search", 1, StringPrototype::search));
        stringPrototype.definePropertyWritableConfigurable("slice", new JSNativeFunction("slice", 2, StringPrototype::slice));
        stringPrototype.definePropertyWritableConfigurable("split", new JSNativeFunction("split", 2, StringPrototype::split));
        stringPrototype.definePropertyWritableConfigurable("startsWith", new JSNativeFunction("startsWith", 1, StringPrototype::startsWith));
        stringPrototype.definePropertyWritableConfigurable("substr", new JSNativeFunction("substr", 2, StringPrototype::substr));
        stringPrototype.definePropertyWritableConfigurable("substring", new JSNativeFunction("substring", 2, StringPrototype::substring));
        stringPrototype.definePropertyWritableConfigurable("toLowerCase", new JSNativeFunction("toLowerCase", 0, StringPrototype::toLowerCase));
        stringPrototype.definePropertyWritableConfigurable("toString", new JSNativeFunction("toString", 0, StringPrototype::toString_));
        stringPrototype.definePropertyWritableConfigurable("toUpperCase", new JSNativeFunction("toUpperCase", 0, StringPrototype::toUpperCase));
        stringPrototype.definePropertyWritableConfigurable("trim", new JSNativeFunction("trim", 0, StringPrototype::trim));
        JSNativeFunction trimEnd = new JSNativeFunction("trimEnd", 0, StringPrototype::trimEnd);
        JSNativeFunction trimStart = new JSNativeFunction("trimStart", 0, StringPrototype::trimStart);
        stringPrototype.definePropertyWritableConfigurable("trimEnd", trimEnd);
        stringPrototype.definePropertyWritableConfigurable("trimRight", trimEnd);
        stringPrototype.definePropertyWritableConfigurable("trimStart", trimStart);
        stringPrototype.definePropertyWritableConfigurable("trimLeft", trimStart);
        stringPrototype.definePropertyWritableConfigurable("valueOf", new JSNativeFunction("valueOf", 0, StringPrototype::valueOf));

        // HTML wrapper methods (deprecated but still part of spec)
        stringPrototype.definePropertyWritableConfigurable("anchor", new JSNativeFunction("anchor", 1, StringPrototype::anchor));
        stringPrototype.definePropertyWritableConfigurable("big", new JSNativeFunction("big", 0, StringPrototype::big));
        stringPrototype.definePropertyWritableConfigurable("blink", new JSNativeFunction("blink", 0, StringPrototype::blink));
        stringPrototype.definePropertyWritableConfigurable("bold", new JSNativeFunction("bold", 0, StringPrototype::bold));
        stringPrototype.definePropertyWritableConfigurable("fixed", new JSNativeFunction("fixed", 0, StringPrototype::fixed));
        stringPrototype.definePropertyWritableConfigurable("fontcolor", new JSNativeFunction("fontcolor", 1, StringPrototype::fontcolor));
        stringPrototype.definePropertyWritableConfigurable("fontsize", new JSNativeFunction("fontsize", 1, StringPrototype::fontsize));
        stringPrototype.definePropertyWritableConfigurable("italics", new JSNativeFunction("italics", 0, StringPrototype::italics));
        stringPrototype.definePropertyWritableConfigurable("link", new JSNativeFunction("link", 1, StringPrototype::link));
        stringPrototype.definePropertyWritableConfigurable("small", new JSNativeFunction("small", 0, StringPrototype::small));
        stringPrototype.definePropertyWritableConfigurable("strike", new JSNativeFunction("strike", 0, StringPrototype::strike));
        stringPrototype.definePropertyWritableConfigurable("sub", new JSNativeFunction("sub", 0, StringPrototype::sub));
        stringPrototype.definePropertyWritableConfigurable("sup", new JSNativeFunction("sup", 0, StringPrototype::sup));

        // Unicode methods
        stringPrototype.definePropertyWritableConfigurable("isWellFormed", new JSNativeFunction("isWellFormed", 0, StringPrototype::isWellFormed));
        stringPrototype.definePropertyWritableConfigurable("normalize", new JSNativeFunction("normalize", 0, StringPrototype::normalize));
        stringPrototype.definePropertyWritableConfigurable("toLocaleLowerCase", new JSNativeFunction("toLocaleLowerCase", 0, StringPrototype::toLocaleLowerCase));
        stringPrototype.definePropertyWritableConfigurable("toLocaleUpperCase", new JSNativeFunction("toLocaleUpperCase", 0, StringPrototype::toLocaleUpperCase));
        stringPrototype.definePropertyWritableConfigurable("toWellFormed", new JSNativeFunction("toWellFormed", 0, StringPrototype::toWellFormed));

        // String.prototype[Symbol.iterator]
        stringPrototype.definePropertyWritableConfigurable(
                JSSymbol.ITERATOR,
                new JSNativeFunction("[Symbol.iterator]", 0, IteratorPrototype::stringIterator));

        // String.prototype.length is a data property with value 0 (not writable, not enumerable, not configurable)
        stringPrototype.definePropertyReadonlyNonConfigurable("length", new JSNumber(0));

        // Create String constructor
        JSNativeFunction stringConstructor = new JSNativeFunction("String", 1, StringConstructor::call, true);
        stringConstructor.set("prototype", stringPrototype);
        stringConstructor.setConstructorType(JSConstructorType.STRING_OBJECT); // Mark as String constructor
        stringPrototype.definePropertyWritableConfigurable("constructor", stringConstructor);

        // Add static methods
        stringConstructor.definePropertyWritableConfigurable("fromCharCode", new JSNativeFunction("fromCharCode", 1, StringConstructor::fromCharCode));
        stringConstructor.definePropertyWritableConfigurable("fromCodePoint", new JSNativeFunction("fromCodePoint", 1, StringConstructor::fromCodePoint));
        stringConstructor.definePropertyWritableConfigurable("raw", new JSNativeFunction("raw", 1, StringConstructor::raw));

        global.definePropertyWritableConfigurable("String", stringConstructor);
    }

    /**
     * Initialize Symbol constructor and static methods.
     */
    private void initializeSymbolConstructor(JSContext context, JSObject global) {
        // Create Symbol.prototype that inherits from Object.prototype
        JSObject symbolPrototype = context.createJSObject();
        context.transferPrototype(symbolPrototype, JSObject.NAME);

        JSNativeFunction symbolToString = new JSNativeFunction("toString", 0, SymbolPrototype::toString);
        symbolToString.initializePrototypeChain(context);
        symbolPrototype.definePropertyWritableConfigurable("toString", symbolToString);

        JSNativeFunction symbolValueOf = new JSNativeFunction("valueOf", 0, SymbolPrototype::valueOf);
        symbolValueOf.initializePrototypeChain(context);
        symbolPrototype.definePropertyWritableConfigurable("valueOf", symbolValueOf);

        // Symbol.prototype.description is a getter
        symbolPrototype.defineGetterConfigurable("description", SymbolPrototype::getDescription);

        JSNativeFunction symbolToPrimitive = new JSNativeFunction("[Symbol.toPrimitive]", 1, SymbolPrototype::toPrimitive);
        symbolToPrimitive.initializePrototypeChain(context);
        symbolPrototype.definePropertyWritableConfigurable(JSSymbol.TO_PRIMITIVE, symbolToPrimitive);

        symbolPrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString("Symbol"));

        // Create Symbol constructor
        // Note: Symbol cannot be called with 'new' in JavaScript (throws TypeError)
        // Symbol objects are created using Object(symbolValue) for use with Proxy
        JSNativeFunction symbolConstructor = new JSNativeFunction(JSSymbol.NAME, 0, SymbolConstructor::call);
        symbolConstructor.definePropertyReadonlyNonConfigurable("prototype", symbolPrototype);
        symbolConstructor.setConstructorType(JSConstructorType.SYMBOL_OBJECT); // Mark as Symbol constructor
        symbolPrototype.definePropertyWritableConfigurable("constructor", symbolConstructor);

        // Symbol static methods
        symbolConstructor.definePropertyWritableConfigurable("for", new JSNativeFunction("for", 1, SymbolConstructor::symbolFor));
        symbolConstructor.definePropertyWritableConfigurable("keyFor", new JSNativeFunction("keyFor", 1, SymbolConstructor::keyFor));

        // Well-known symbols (ES2015+)
        symbolConstructor.definePropertyReadonlyNonConfigurable("iterator", JSSymbol.ITERATOR);
        symbolConstructor.definePropertyReadonlyNonConfigurable("asyncIterator", JSSymbol.ASYNC_ITERATOR);
        symbolConstructor.definePropertyReadonlyNonConfigurable("toStringTag", JSSymbol.TO_STRING_TAG);
        symbolConstructor.definePropertyReadonlyNonConfigurable("hasInstance", JSSymbol.HAS_INSTANCE);
        symbolConstructor.definePropertyReadonlyNonConfigurable("isConcatSpreadable", JSSymbol.IS_CONCAT_SPREADABLE);
        symbolConstructor.definePropertyReadonlyNonConfigurable("toPrimitive", JSSymbol.TO_PRIMITIVE);
        symbolConstructor.definePropertyReadonlyNonConfigurable("match", JSSymbol.MATCH);
        symbolConstructor.definePropertyReadonlyNonConfigurable("matchAll", JSSymbol.MATCH_ALL);
        symbolConstructor.definePropertyReadonlyNonConfigurable("replace", JSSymbol.REPLACE);
        symbolConstructor.definePropertyReadonlyNonConfigurable("search", JSSymbol.SEARCH);
        symbolConstructor.definePropertyReadonlyNonConfigurable("split", JSSymbol.SPLIT);
        symbolConstructor.definePropertyReadonlyNonConfigurable("species", JSSymbol.SPECIES);
        symbolConstructor.definePropertyReadonlyNonConfigurable("unscopables", JSSymbol.UNSCOPABLES);
        // ES2024 additions kept intentionally even though not in upstream QuickJS.
        symbolConstructor.definePropertyReadonlyNonConfigurable("dispose", JSSymbol.DISPOSE);
        symbolConstructor.definePropertyReadonlyNonConfigurable("asyncDispose", JSSymbol.ASYNC_DISPOSE);

        global.definePropertyWritableConfigurable("Symbol", symbolConstructor);
    }

    /**
     * Initialize all TypedArray constructors.
     */
    private void initializeTypedArrayConstructors(JSContext context, JSObject global) {
        record TypedArrayDef(String name, JSNativeFunction.NativeCallback callback, JSConstructorType type,
                             int bytesPerElement) {
        }
        JSValue arrayToString = JSUndefined.INSTANCE;
        JSValue arrayConstructorValue = global.get(JSArray.NAME);
        if (arrayConstructorValue instanceof JSObject arrayConstructor) {
            JSValue arrayPrototypeValue = arrayConstructor.get("prototype");
            if (arrayPrototypeValue instanceof JSObject arrayPrototype) {
                arrayToString = arrayPrototype.get("toString");
            }
        }

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
            JSObject prototype = context.createJSObject();

            JSNativeFunction valuesFunction = new JSNativeFunction("values", 0, TypedArrayPrototype::values);
            prototype.definePropertyWritableConfigurable("at", new JSNativeFunction("at", 1, TypedArrayPrototype::at));
            prototype.definePropertyWritableConfigurable("entries", new JSNativeFunction("entries", 0, TypedArrayPrototype::entries));
            prototype.definePropertyWritableConfigurable("join", new JSNativeFunction("join", 1, TypedArrayPrototype::join));
            prototype.definePropertyWritableConfigurable("keys", new JSNativeFunction("keys", 0, TypedArrayPrototype::keys));
            prototype.definePropertyWritableConfigurable("set", new JSNativeFunction("set", 1, TypedArrayPrototype::set));
            prototype.definePropertyWritableConfigurable("subarray", new JSNativeFunction("subarray", 2, TypedArrayPrototype::subarray));
            if (arrayToString instanceof JSFunction) {
                prototype.definePropertyWritableConfigurable("toString", arrayToString);
            } else {
                prototype.definePropertyWritableConfigurable("toString", new JSNativeFunction("toString", 0, TypedArrayPrototype::toString));
            }
            prototype.definePropertyWritableConfigurable("values", valuesFunction);
            prototype.definePropertyWritableConfigurable(JSSymbol.ITERATOR, valuesFunction);

            prototype.defineGetterConfigurable("buffer", TypedArrayPrototype::getBuffer);
            prototype.defineGetterConfigurable("byteLength", TypedArrayPrototype::getByteLength);
            prototype.defineGetterConfigurable("byteOffset", TypedArrayPrototype::getByteOffset);
            prototype.defineGetterConfigurable("length", TypedArrayPrototype::getLength);
            prototype.defineGetterConfigurable(JSSymbol.TO_STRING_TAG, TypedArrayPrototype::getToStringTag);
            prototype.definePropertyReadonlyNonConfigurable("BYTES_PER_ELEMENT", new JSNumber(def.bytesPerElement));

            JSNativeFunction constructor = new JSNativeFunction(def.name, 3, def.callback, true, true);
            constructor.definePropertyReadonlyNonConfigurable("prototype", prototype);
            constructor.setConstructorType(def.type);
            constructor.definePropertyReadonlyNonConfigurable("BYTES_PER_ELEMENT", new JSNumber(def.bytesPerElement));
            constructor.definePropertyWritableConfigurable("from", new JSNativeFunction("from", 1, TypedArrayConstructor::from));
            constructor.definePropertyWritableConfigurable("of", new JSNativeFunction("of", 0, TypedArrayConstructor::of));
            constructor.defineGetterConfigurable(JSSymbol.SPECIES, TypedArrayConstructor::getSpecies);

            prototype.definePropertyWritableConfigurable("constructor", constructor);
            global.definePropertyWritableConfigurable(def.name, constructor);
        }
    }

    /**
     * Initialize WeakMap constructor and prototype methods.
     */
    private void initializeWeakMapConstructor(JSContext context, JSObject global) {
        JSObject weakMapPrototype = context.createJSObject();
        weakMapPrototype.definePropertyWritableConfigurable("set", new JSNativeFunction("set", 2, WeakMapPrototype::set));
        weakMapPrototype.definePropertyWritableConfigurable("get", new JSNativeFunction("get", 1, WeakMapPrototype::get));
        weakMapPrototype.definePropertyWritableConfigurable("getOrInsert", new JSNativeFunction("getOrInsert", 2, WeakMapPrototype::getOrInsert));
        weakMapPrototype.definePropertyWritableConfigurable("getOrInsertComputed", new JSNativeFunction("getOrInsertComputed", 2, WeakMapPrototype::getOrInsertComputed));
        weakMapPrototype.definePropertyWritableConfigurable("has", new JSNativeFunction("has", 1, WeakMapPrototype::has));
        weakMapPrototype.definePropertyWritableConfigurable("delete", new JSNativeFunction("delete", 1, WeakMapPrototype::delete));
        weakMapPrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString(JSWeakMap.NAME));

        JSNativeFunction weakMapConstructor = new JSNativeFunction(
                "WeakMap",
                0,
                WeakMapConstructor::call,
                true,
                true
        );
        weakMapConstructor.definePropertyReadonlyNonConfigurable("prototype", weakMapPrototype);
        weakMapConstructor.setConstructorType(JSConstructorType.WEAK_MAP);
        weakMapPrototype.definePropertyWritableConfigurable("constructor", weakMapConstructor);

        global.definePropertyWritableConfigurable("WeakMap", weakMapConstructor);
    }

    /**
     * Initialize WeakRef constructor.
     */
    private void initializeWeakRefConstructor(JSContext context, JSObject global) {
        JSObject weakRefPrototype = context.createJSObject();
        weakRefPrototype.definePropertyWritableConfigurable("deref", new JSNativeFunction("deref", 0, WeakRefPrototype::deref));
        weakRefPrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString(JSWeakRef.NAME));

        JSNativeFunction weakRefConstructor = new JSNativeFunction(
                "WeakRef",
                1,
                WeakRefConstructor::call,
                true,
                true
        );
        weakRefConstructor.definePropertyReadonlyNonConfigurable("prototype", weakRefPrototype);
        weakRefConstructor.setConstructorType(JSConstructorType.WEAK_REF);
        weakRefPrototype.definePropertyWritableConfigurable("constructor", weakRefConstructor);

        global.definePropertyWritableConfigurable("WeakRef", weakRefConstructor);
    }

    /**
     * Initialize WeakSet constructor and prototype methods.
     */
    private void initializeWeakSetConstructor(JSContext context, JSObject global) {
        JSObject weakSetPrototype = context.createJSObject();
        weakSetPrototype.definePropertyWritableConfigurable("add", new JSNativeFunction("add", 1, WeakSetPrototype::add));
        weakSetPrototype.definePropertyWritableConfigurable("has", new JSNativeFunction("has", 1, WeakSetPrototype::has));
        weakSetPrototype.definePropertyWritableConfigurable("delete", new JSNativeFunction("delete", 1, WeakSetPrototype::delete));
        weakSetPrototype.definePropertyConfigurable(JSSymbol.TO_STRING_TAG, new JSString(JSWeakSet.NAME));

        JSNativeFunction weakSetConstructor = new JSNativeFunction(
                "WeakSet",
                0,
                WeakSetConstructor::call,
                true,
                true
        );
        weakSetConstructor.definePropertyReadonlyNonConfigurable("prototype", weakSetPrototype);
        weakSetConstructor.setConstructorType(JSConstructorType.WEAK_SET);
        weakSetPrototype.definePropertyWritableConfigurable("constructor", weakSetConstructor);

        global.definePropertyWritableConfigurable("WeakSet", weakSetConstructor);
    }

    private boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isEcmaWhitespace(char c) {
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
    public JSValue isFinite(JSContext context, JSValue thisArg, JSValue[] args) {
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

    private boolean isInfinityPrefix(String str, int start) {
        return start + 8 <= str.length() && str.startsWith("Infinity", start);
    }

    /**
     * isNaN(value)
     * Determine whether a value is NaN.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-isnan-number">ECMAScript isNaN</a>
     */
    public JSValue isNaN(JSContext context, JSValue thisArg, JSValue[] args) {
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

    private boolean isURIReserved(int c) {
        return c < 0x100 && URI_RESERVED.indexOf(c) >= 0;
    }

    private boolean isURIUnescaped(int c, boolean isComponent) {
        return c < 0x100
                && ((c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || "-_.!~*'()".indexOf(c) >= 0
                || (!isComponent && isURIReserved(c)));
    }

    /**
     * Helper function to check if a character should not be escaped.
     * Returns true for: A-Z a-z 0-9 @ * _ + - . /
     */
    private boolean isUnescaped(char c) {
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
    public JSValue parseFloat(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue input = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String inputString = JSTypeConversions.toString(context, input).value();
        int index = skipLeadingWhitespace(inputString);
        if (index >= inputString.length()) {
            return new JSNumber(Double.NaN);
        }

        int signIndex = index;
        if (inputString.charAt(index) == '+' || inputString.charAt(index) == '-') {
            index++;
        }
        if (isInfinityPrefix(inputString, index)) {
            boolean isNegative = inputString.charAt(signIndex) == '-';
            return new JSNumber(isNegative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
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
            return new JSNumber(Double.NaN);
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
            return new JSNumber(Double.parseDouble(validNumber));
        } catch (NumberFormatException ignored) {
            return new JSNumber(Double.NaN);
        }
    }

    /**
     * parseInt(string, radix)
     * Parse a string and return an integer of the specified radix.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-parseint-string-radix">ECMAScript parseInt</a>
     */
    public JSValue parseInt(JSContext context, JSValue thisArg, JSValue[] args) {
        JSValue input = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String inputString = JSTypeConversions.toString(context, input).value();
        int index = skipLeadingWhitespace(inputString);
        if (index >= inputString.length()) {
            return new JSNumber(Double.NaN);
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
            return new JSNumber(Double.NaN);
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
            return new JSNumber(Double.NaN);
        }

        return new JSNumber(sign * result);
    }

    private int skipLeadingWhitespace(String str) {
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
    public JSValue unescape(JSContext context, JSValue thisArg, JSValue[] args) {
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
