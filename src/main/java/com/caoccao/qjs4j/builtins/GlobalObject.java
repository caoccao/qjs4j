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
