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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The global object with built-in functions.
 * Based on ECMAScript specification global properties and functions.
 *
 * Implements:
 * - Global value properties (NaN, Infinity, undefined)
 * - Global function properties (parseInt, parseFloat, isNaN, isFinite, eval)
 * - URI handling functions (encodeURI, decodeURI, encodeURIComponent, decodeURIComponent)
 */
public final class GlobalObject {

    /**
     * Initialize the global object with all built-in global properties and functions.
     */
    public static void initialize(JSContext ctx, JSObject global) {
        // Global value properties
        global.set("undefined", JSUndefined.INSTANCE);
        global.set("NaN", new JSNumber(Double.NaN));
        global.set("Infinity", new JSNumber(Double.POSITIVE_INFINITY));

        // Global function properties
        global.set("parseInt", createNativeFunction(ctx, "parseInt", GlobalObject::parseInt, 2));
        global.set("parseFloat", createNativeFunction(ctx, "parseFloat", GlobalObject::parseFloat, 1));
        global.set("isNaN", createNativeFunction(ctx, "isNaN", GlobalObject::isNaN, 1));
        global.set("isFinite", createNativeFunction(ctx, "isFinite", GlobalObject::isFinite, 1));
        global.set("eval", createNativeFunction(ctx, "eval", GlobalObject::eval, 1));

        // URI handling functions
        global.set("encodeURI", createNativeFunction(ctx, "encodeURI", GlobalObject::encodeURI, 1));
        global.set("decodeURI", createNativeFunction(ctx, "decodeURI", GlobalObject::decodeURI, 1));
        global.set("encodeURIComponent", createNativeFunction(ctx, "encodeURIComponent", GlobalObject::encodeURIComponent, 1));
        global.set("decodeURIComponent", createNativeFunction(ctx, "decodeURIComponent", GlobalObject::decodeURIComponent, 1));

        // Global this reference
        global.set("globalThis", global);

        // Built-in constructors and their prototypes
        initializeArrayConstructor(ctx, global);
        initializeStringConstructor(ctx, global);
        initializeNumberConstructor(ctx, global);
        initializeFunctionConstructor(ctx, global);
        initializeMathObject(ctx, global);
        initializeJSONObject(ctx, global);
    }

    /**
     * Initialize Array constructor and prototype.
     */
    private static void initializeArrayConstructor(JSContext ctx, JSObject global) {
        // Create Array.prototype
        JSObject arrayPrototype = new JSObject();
        arrayPrototype.set("push", createNativeFunction(ctx, "push", ArrayPrototype::push, 1));
        arrayPrototype.set("pop", createNativeFunction(ctx, "pop", ArrayPrototype::pop, 0));
        arrayPrototype.set("shift", createNativeFunction(ctx, "shift", ArrayPrototype::shift, 0));
        arrayPrototype.set("unshift", createNativeFunction(ctx, "unshift", ArrayPrototype::unshift, 1));
        arrayPrototype.set("slice", createNativeFunction(ctx, "slice", ArrayPrototype::slice, 2));
        arrayPrototype.set("splice", createNativeFunction(ctx, "splice", ArrayPrototype::splice, 2));
        arrayPrototype.set("concat", createNativeFunction(ctx, "concat", ArrayPrototype::concat, 1));
        arrayPrototype.set("join", createNativeFunction(ctx, "join", ArrayPrototype::join, 1));
        arrayPrototype.set("reverse", createNativeFunction(ctx, "reverse", ArrayPrototype::reverse, 0));
        arrayPrototype.set("sort", createNativeFunction(ctx, "sort", ArrayPrototype::sort, 1));
        arrayPrototype.set("indexOf", createNativeFunction(ctx, "indexOf", ArrayPrototype::indexOf, 1));
        arrayPrototype.set("lastIndexOf", createNativeFunction(ctx, "lastIndexOf", ArrayPrototype::lastIndexOf, 1));
        arrayPrototype.set("includes", createNativeFunction(ctx, "includes", ArrayPrototype::includes, 1));
        arrayPrototype.set("map", createNativeFunction(ctx, "map", ArrayPrototype::map, 1));
        arrayPrototype.set("filter", createNativeFunction(ctx, "filter", ArrayPrototype::filter, 1));
        arrayPrototype.set("reduce", createNativeFunction(ctx, "reduce", ArrayPrototype::reduce, 1));
        arrayPrototype.set("reduceRight", createNativeFunction(ctx, "reduceRight", ArrayPrototype::reduceRight, 1));
        arrayPrototype.set("forEach", createNativeFunction(ctx, "forEach", ArrayPrototype::forEach, 1));
        arrayPrototype.set("find", createNativeFunction(ctx, "find", ArrayPrototype::find, 1));
        arrayPrototype.set("findIndex", createNativeFunction(ctx, "findIndex", ArrayPrototype::findIndex, 1));
        arrayPrototype.set("every", createNativeFunction(ctx, "every", ArrayPrototype::every, 1));
        arrayPrototype.set("some", createNativeFunction(ctx, "some", ArrayPrototype::some, 1));
        arrayPrototype.set("flat", createNativeFunction(ctx, "flat", ArrayPrototype::flat, 0));
        arrayPrototype.set("toString", createNativeFunction(ctx, "toString", ArrayPrototype::toString, 0));

        // For now, Array constructor is a placeholder
        JSObject arrayConstructor = new JSObject();
        arrayConstructor.set("prototype", arrayPrototype);

        global.set("Array", arrayConstructor);
    }

    /**
     * Initialize String constructor and prototype.
     */
    private static void initializeStringConstructor(JSContext ctx, JSObject global) {
        // Create String.prototype
        JSObject stringPrototype = new JSObject();
        stringPrototype.set("charAt", createNativeFunction(ctx, "charAt", StringPrototype::charAt, 1));
        stringPrototype.set("charCodeAt", createNativeFunction(ctx, "charCodeAt", StringPrototype::charCodeAt, 1));
        stringPrototype.set("codePointAt", createNativeFunction(ctx, "codePointAt", StringPrototype::codePointAt, 1));
        stringPrototype.set("concat", createNativeFunction(ctx, "concat", StringPrototype::concat, 1));
        stringPrototype.set("endsWith", createNativeFunction(ctx, "endsWith", StringPrototype::endsWith, 1));
        stringPrototype.set("startsWith", createNativeFunction(ctx, "startsWith", StringPrototype::startsWith, 1));
        stringPrototype.set("includes", createNativeFunction(ctx, "includes", StringPrototype::includes, 1));
        stringPrototype.set("indexOf", createNativeFunction(ctx, "indexOf", StringPrototype::indexOf, 1));
        stringPrototype.set("lastIndexOf", createNativeFunction(ctx, "lastIndexOf", StringPrototype::lastIndexOf, 1));
        stringPrototype.set("padEnd", createNativeFunction(ctx, "padEnd", StringPrototype::padEnd, 1));
        stringPrototype.set("padStart", createNativeFunction(ctx, "padStart", StringPrototype::padStart, 1));
        stringPrototype.set("repeat", createNativeFunction(ctx, "repeat", StringPrototype::repeat, 1));
        stringPrototype.set("replace", createNativeFunction(ctx, "replace", StringPrototype::replace, 2));
        stringPrototype.set("replaceAll", createNativeFunction(ctx, "replaceAll", StringPrototype::replaceAll, 2));
        stringPrototype.set("slice", createNativeFunction(ctx, "slice", StringPrototype::slice, 2));
        stringPrototype.set("split", createNativeFunction(ctx, "split", StringPrototype::split, 2));
        stringPrototype.set("substring", createNativeFunction(ctx, "substring", StringPrototype::substring, 2));
        stringPrototype.set("substr", createNativeFunction(ctx, "substr", StringPrototype::substr, 2));
        stringPrototype.set("toLowerCase", createNativeFunction(ctx, "toLowerCase", StringPrototype::toLowerCase, 0));
        stringPrototype.set("toUpperCase", createNativeFunction(ctx, "toUpperCase", StringPrototype::toUpperCase, 0));
        stringPrototype.set("trim", createNativeFunction(ctx, "trim", StringPrototype::trim, 0));
        stringPrototype.set("trimStart", createNativeFunction(ctx, "trimStart", StringPrototype::trimStart, 0));
        stringPrototype.set("trimEnd", createNativeFunction(ctx, "trimEnd", StringPrototype::trimEnd, 0));
        stringPrototype.set("toString", createNativeFunction(ctx, "toString", StringPrototype::toStringMethod, 0));
        stringPrototype.set("valueOf", createNativeFunction(ctx, "valueOf", StringPrototype::valueOf, 0));

        // String constructor is a placeholder
        JSObject stringConstructor = new JSObject();
        stringConstructor.set("prototype", stringPrototype);

        global.set("String", stringConstructor);
    }

    /**
     * Initialize Number constructor and prototype.
     */
    private static void initializeNumberConstructor(JSContext ctx, JSObject global) {
        // Create Number.prototype
        JSObject numberPrototype = new JSObject();
        numberPrototype.set("toFixed", createNativeFunction(ctx, "toFixed", NumberPrototype::toFixed, 1));
        numberPrototype.set("toExponential", createNativeFunction(ctx, "toExponential", NumberPrototype::toExponential, 1));
        numberPrototype.set("toPrecision", createNativeFunction(ctx, "toPrecision", NumberPrototype::toPrecision, 1));
        numberPrototype.set("toString", createNativeFunction(ctx, "toString", NumberPrototype::toString, 1));
        numberPrototype.set("toLocaleString", createNativeFunction(ctx, "toLocaleString", NumberPrototype::toLocaleString, 0));
        numberPrototype.set("valueOf", createNativeFunction(ctx, "valueOf", NumberPrototype::valueOf, 0));

        // Number constructor with static methods
        JSObject numberConstructor = new JSObject();
        numberConstructor.set("prototype", numberPrototype);
        numberConstructor.set("isNaN", createNativeFunction(ctx, "isNaN", NumberPrototype::isNaN, 1));
        numberConstructor.set("isFinite", createNativeFunction(ctx, "isFinite", NumberPrototype::isFinite, 1));
        numberConstructor.set("isInteger", createNativeFunction(ctx, "isInteger", NumberPrototype::isInteger, 1));
        numberConstructor.set("isSafeInteger", createNativeFunction(ctx, "isSafeInteger", NumberPrototype::isSafeInteger, 1));
        numberConstructor.set("parseFloat", createNativeFunction(ctx, "parseFloat", NumberPrototype::parseFloat, 1));
        numberConstructor.set("parseInt", createNativeFunction(ctx, "parseInt", NumberPrototype::parseInt, 2));

        global.set("Number", numberConstructor);
    }

    /**
     * Initialize Function constructor and prototype.
     */
    private static void initializeFunctionConstructor(JSContext ctx, JSObject global) {
        // Create Function.prototype
        JSObject functionPrototype = new JSObject();
        functionPrototype.set("call", createNativeFunction(ctx, "call", FunctionPrototype::call, 1));
        functionPrototype.set("apply", createNativeFunction(ctx, "apply", FunctionPrototype::apply, 2));
        functionPrototype.set("bind", createNativeFunction(ctx, "bind", FunctionPrototype::bind, 1));
        functionPrototype.set("toString", createNativeFunction(ctx, "toString", FunctionPrototype::toStringMethod, 0));

        // Function constructor is a placeholder
        JSObject functionConstructor = new JSObject();
        functionConstructor.set("prototype", functionPrototype);

        global.set("Function", functionConstructor);
    }

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
        math.set("abs", createNativeFunction(ctx, "abs", MathObject::abs, 1));
        math.set("acos", createNativeFunction(ctx, "acos", MathObject::acos, 1));
        math.set("acosh", createNativeFunction(ctx, "acosh", MathObject::acosh, 1));
        math.set("asin", createNativeFunction(ctx, "asin", MathObject::asin, 1));
        math.set("asinh", createNativeFunction(ctx, "asinh", MathObject::asinh, 1));
        math.set("atan", createNativeFunction(ctx, "atan", MathObject::atan, 1));
        math.set("atanh", createNativeFunction(ctx, "atanh", MathObject::atanh, 1));
        math.set("atan2", createNativeFunction(ctx, "atan2", MathObject::atan2, 2));
        math.set("cbrt", createNativeFunction(ctx, "cbrt", MathObject::cbrt, 1));
        math.set("ceil", createNativeFunction(ctx, "ceil", MathObject::ceil, 1));
        math.set("clz32", createNativeFunction(ctx, "clz32", MathObject::clz32, 1));
        math.set("cos", createNativeFunction(ctx, "cos", MathObject::cos, 1));
        math.set("cosh", createNativeFunction(ctx, "cosh", MathObject::cosh, 1));
        math.set("exp", createNativeFunction(ctx, "exp", MathObject::exp, 1));
        math.set("expm1", createNativeFunction(ctx, "expm1", MathObject::expm1, 1));
        math.set("floor", createNativeFunction(ctx, "floor", MathObject::floor, 1));
        math.set("fround", createNativeFunction(ctx, "fround", MathObject::fround, 1));
        math.set("hypot", createNativeFunction(ctx, "hypot", MathObject::hypot, 0));
        math.set("imul", createNativeFunction(ctx, "imul", MathObject::imul, 2));
        math.set("log", createNativeFunction(ctx, "log", MathObject::log, 1));
        math.set("log1p", createNativeFunction(ctx, "log1p", MathObject::log1p, 1));
        math.set("log10", createNativeFunction(ctx, "log10", MathObject::log10, 1));
        math.set("log2", createNativeFunction(ctx, "log2", MathObject::log2, 1));
        math.set("max", createNativeFunction(ctx, "max", MathObject::max, 2));
        math.set("min", createNativeFunction(ctx, "min", MathObject::min, 2));
        math.set("pow", createNativeFunction(ctx, "pow", MathObject::pow, 2));
        math.set("random", createNativeFunction(ctx, "random", MathObject::random, 0));
        math.set("round", createNativeFunction(ctx, "round", MathObject::round, 1));
        math.set("sign", createNativeFunction(ctx, "sign", MathObject::sign, 1));
        math.set("sin", createNativeFunction(ctx, "sin", MathObject::sin, 1));
        math.set("sinh", createNativeFunction(ctx, "sinh", MathObject::sinh, 1));
        math.set("sqrt", createNativeFunction(ctx, "sqrt", MathObject::sqrt, 1));
        math.set("tan", createNativeFunction(ctx, "tan", MathObject::tan, 1));
        math.set("tanh", createNativeFunction(ctx, "tanh", MathObject::tanh, 1));
        math.set("trunc", createNativeFunction(ctx, "trunc", MathObject::trunc, 1));

        global.set("Math", math);
    }

    /**
     * Initialize JSON object.
     */
    private static void initializeJSONObject(JSContext ctx, JSObject global) {
        JSObject json = new JSObject();
        json.set("parse", createNativeFunction(ctx, "parse", JSONObject::parse, 1));
        json.set("stringify", createNativeFunction(ctx, "stringify", JSONObject::stringify, 1));

        global.set("JSON", json);
    }

    /**
     * Helper to create a native function.
     */
    private static JSNativeFunction createNativeFunction(JSContext ctx, String name,
                                                         JSNativeFunction.NativeCallback implementation, int length) {
        return new JSNativeFunction(name, length, implementation);
    }

    // Global function implementations

    /**
     * parseInt(string, radix)
     * Parse a string and return an integer of the specified radix.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-parseint-string-radix">ECMAScript parseInt</a>
     */
    public static JSValue parseInt(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // Get input string
        JSValue input = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String inputString = JSTypeConversions.toString(input).getValue().trim();

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
     * parseFloat(string)
     * Parse a string and return a floating point number.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-parsefloat-string">ECMAScript parseFloat</a>
     */
    public static JSValue parseFloat(JSContext ctx, JSValue thisArg, JSValue[] args) {
        // Get input string
        JSValue input = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String inputString = JSTypeConversions.toString(input).getValue().trim();

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

        String code = ((JSString) x).getValue();
        return ctx.eval(code);
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
        String uriString = JSTypeConversions.toString(uriValue).getValue();

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
     * decodeURI(encodedURI)
     * Decode a URI that was encoded by encodeURI.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-decodeuri-encodeduri">ECMAScript decodeURI</a>
     */
    public static JSValue decodeURI(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue encodedValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String encodedString = JSTypeConversions.toString(encodedValue).getValue();

        try {
            String decoded = URLDecoder.decode(encodedString, StandardCharsets.UTF_8);
            return new JSString(decoded);
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
        String componentString = JSTypeConversions.toString(componentValue).getValue();

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
     * decodeURIComponent(encodedURIComponent)
     * Decode a URI component that was encoded by encodeURIComponent.
     *
     * @see <a href="https://tc39.es/ecma262/#sec-decodeuricomponent-encodeduricomponent">ECMAScript decodeURIComponent</a>
     */
    public static JSValue decodeURIComponent(JSContext ctx, JSValue thisArg, JSValue[] args) {
        JSValue encodedValue = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        String encodedString = JSTypeConversions.toString(encodedValue).getValue();

        try {
            String decoded = URLDecoder.decode(encodedString, StandardCharsets.UTF_8);
            return new JSString(decoded);
        } catch (Exception e) {
            return ctx.throwError("URIError", "URI malformed");
        }
    }
}
