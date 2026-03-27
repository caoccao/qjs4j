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

import com.caoccao.qjs4j.exceptions.JSException;
import com.caoccao.qjs4j.exceptions.JSVirtualMachineException;

import java.util.*;

/**
 * Implementation of JavaScript JSON object.
 * Based on ES2024 JSON specification and QuickJS implementation.
 */
public final class JSONObject {
    private final JSContext context;

    /**
     * Internal marker for rawJSON objects using weak-key tracking.
     * This avoids exposing marker properties while allowing GC cleanup.
     */
    private final Map<JSObject, Boolean> rawJSONObjects = new WeakHashMap<>();

    public JSONObject(JSContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    // ========== Safe Function Call Helpers ==========

    /**
     * Build a V8-compatible circular reference error message.
     * Format: "Converting circular structure to JSON\n    --> starting at object with constructor 'X'\n    --- property 'y' closes the circle"
     */
    private String buildCircularReferenceMessage(
            StringifyContext stringifyContext,
            JSValue cycleTo,
            String closingKey) {
        // Find where the cycle starts in the path
        int cycleStart = -1;
        for (int i = 0; i < stringifyContext.cyclePath.size(); i++) {
            if (stringifyContext.cyclePath.get(i).obj == cycleTo) {
                cycleStart = i;
                break;
            }
        }

        StringBuilder msg = new StringBuilder("Converting circular structure to JSON");

        if (cycleStart >= 0) {
            msg.append("\n    --> starting at object with constructor '");
            msg.append(getConstructorName(cycleTo));
            msg.append("'");

            // Add intermediate path entries between cycle start and end
            for (int i = cycleStart + 1; i < stringifyContext.cyclePath.size(); i++) {
                CycleEntry entry = stringifyContext.cyclePath.get(i);
                msg.append("\n    |     property '").append(entry.key).append("' -> object with constructor '");
                msg.append(getConstructorName(entry.obj));
                msg.append("'");
            }

            // The closing key - V8 uses "index N" for numeric keys in arrays
            msg.append("\n    --- ");
            if (isNumericKey(closingKey)) {
                msg.append("index ").append(closingKey);
            } else {
                msg.append("property '").append(closingKey).append("'");
            }
            msg.append(" closes the circle");
        }

        return msg.toString();
    }

    /**
     * Safely call a JS function, catching JSVirtualMachineException and converting
     * to a pending exception on the context.
     * Returns null if an exception occurred.
     */
    private JSValue callSafe(JSFunction function, JSValue thisArg, JSValue[] args) {
        try {
            return function.call(context, thisArg, args);
        } catch (JSVirtualMachineException e) {
            convertVMException(e);
            return null;
        } catch (JSException e) {
            convertJSException(e);
            return null;
        }
    }

    private void convertJSException(JSException e) {
        if (e.getErrorValue() != null) {
            context.setPendingException(e.getErrorValue());
        } else if (!context.hasPendingException()) {
            context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
        }
    }

    private void convertVMException(JSVirtualMachineException e) {
        if (e.getJsValue() != null) {
            context.setPendingException(e.getJsValue());
        } else if (e.getJsError() != null) {
            context.setPendingException(e.getJsError());
        } else if (!context.hasPendingException()) {
            context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
        }
    }

    /**
     * Get the constructor name for a value (for error messages).
     * Returns 'Object' for plain objects, 'Array' for arrays.
     */
    private String getConstructorName(JSValue val) {
        if (val instanceof JSArray) {
            return "Array";
        }
        return "Object";
    }

    /**
     * Get enumerable own string property names from an object.
     * Uses getOwnPropertyKeys() which goes through proxy ownKeys trap,
     * then filters by enumerability using getOwnPropertyDescriptor() which
     * goes through proxy getOwnPropertyDescriptor trap.
     * This follows ES2024 EnumerableOwnPropertyNames.
     */
    private List<String> getEnumerableStringKeys(JSObject obj) {
        List<String> keys = new ArrayList<>();
        List<PropertyKey> ownKeys = obj.getOwnPropertyKeys();
        for (PropertyKey key : ownKeys) {
            if (key.isSymbol()) {
                continue;
            }
            PropertyDescriptor desc = obj.getOwnPropertyDescriptor(key);
            if (desc != null && desc.isEnumerable()) {
                keys.add(key.toPropertyString());
            }
        }
        return keys;
    }

    // ========== JSON.parse ==========

    /**
     * Safely get a property from an object, catching JSVirtualMachineException.
     * Returns null if an exception occurred (check context.hasPendingException()).
     */
    private JSValue getSafe(JSObject obj, String key) {
        try {
            return obj.get(PropertyKey.fromString(key));
        } catch (JSVirtualMachineException e) {
            convertVMException(e);
            return null;
        } catch (JSException e) {
            convertJSException(e);
            return null;
        }
    }

    private JSValue getSafe(JSObject obj, PropertyKey key) {
        try {
            return obj.get(key);
        } catch (JSVirtualMachineException e) {
            convertVMException(e);
            return null;
        } catch (JSException e) {
            convertJSException(e);
            return null;
        }
    }

    private JSValue getSafe(JSObject obj, long index) {
        try {
            if (index >= 0 && index <= Integer.MAX_VALUE) {
                return obj.get(PropertyKey.fromIndex((int) index));
            }
            return obj.get(PropertyKey.fromString(Long.toString(index)));
        } catch (JSVirtualMachineException e) {
            convertVMException(e);
            return null;
        } catch (JSException e) {
            convertJSException(e);
            return null;
        }
    }

    /**
     * Recursive internalize operation for JSON.parse reviver.
     * Based on ES2024 25.5.1.1 InternalizeJSONProperty and QuickJS internalize_json_property.
     * Now supports context.source (json-parse-with-source proposal).
     */
    private JSValue internalizeJSONProperty(JSValue holder, String name,
                                            JSFunction reviver, ParseContext parseContext) {
        JSValue val;
        try {
            val = ((JSObject) holder).get(PropertyKey.fromString(name));
        } catch (JSVirtualMachineException e) {
            convertVMException(e);
            return null;
        } catch (JSException e) {
            convertJSException(e);
            return null;
        }
        if (context.hasPendingException()) {
            return null;
        }

        String sourceText = null;

        if (val instanceof JSObject obj && !(val instanceof JSFunction)) {
            int isArray = JSTypeChecking.isArray(context, val);
            if (isArray < 0) {
                return null; // exception from revoked proxy
            }

            if (isArray == 1) {
                // Process array elements
                long arrayLength;
                try {
                    JSValue lengthVal = obj.get(PropertyKey.LENGTH);
                    if (context.hasPendingException()) {
                        return null;
                    }
                    arrayLength = JSTypeConversions.toLength(context, lengthVal);
                    if (context.hasPendingException()) {
                        return null;
                    }
                } catch (JSVirtualMachineException e) {
                    convertVMException(e);
                    return null;
                } catch (JSException e) {
                    convertJSException(e);
                    return null;
                }
                for (long i = 0; i < arrayLength; i++) {
                    JSValue newElement = internalizeJSONProperty(val, String.valueOf(i), reviver, parseContext);
                    if (context.hasPendingException()) {
                        return null;
                    }
                    try {
                        if (newElement instanceof JSUndefined) {
                            obj.delete(PropertyKey.fromString(Long.toString(i)));
                        } else {
                            // Use CreateDataProperty (defineProperty), not [[Set]]
                            obj.defineProperty(PropertyKey.fromString(Long.toString(i)),
                                    newElement, PropertyDescriptor.DataState.All);
                        }
                    } catch (JSVirtualMachineException e) {
                        convertVMException(e);
                        return null;
                    } catch (JSException e) {
                        convertJSException(e);
                        return null;
                    }
                }
            } else {
                // Process object properties - use EnumerableOwnPropertyNames
                List<String> keys;
                try {
                    keys = getEnumerableStringKeys(obj);
                } catch (JSVirtualMachineException e) {
                    convertVMException(e);
                    return null;
                } catch (JSException e) {
                    convertJSException(e);
                    return null;
                }
                if (context.hasPendingException()) {
                    return null;
                }
                for (String prop : keys) {
                    JSValue newElement = internalizeJSONProperty(val, prop, reviver, parseContext);
                    if (context.hasPendingException()) {
                        return null;
                    }
                    try {
                        if (newElement instanceof JSUndefined) {
                            obj.delete(PropertyKey.fromString(prop));
                        } else {
                            // Use CreateDataProperty, not [[Set]]
                            obj.defineProperty(PropertyKey.fromString(prop),
                                    newElement, PropertyDescriptor.DataState.All);
                        }
                    } catch (JSVirtualMachineException e) {
                        convertVMException(e);
                        return null;
                    } catch (JSException e) {
                        convertJSException(e);
                        return null;
                    }
                }
            }
        } else {
            // For primitive values, look up the source text
            if (parseContext != null) {
                sourceText = parseContext.getSourceForValue(name, holder, val);
            }
        }

        // Call reviver function with (key, value, context)
        JSValue nameValue = new JSString(name);
        JSObject contextObj = context.createJSObject();
        if (sourceText != null) {
            contextObj.defineProperty(PropertyKey.fromString("source"), new JSString(sourceText), PropertyDescriptor.DataState.All);
        }
        JSValue result = callSafe(reviver, holder, new JSValue[]{nameValue, val, contextObj});
        return result;
    }

    private boolean isJSONStringControlCharacter(char ch) {
        return ch <= 0x1F;
    }

    /**
     * Check if a char is a JSON whitespace character (tab, newline, carriage return, space).
     */
    private boolean isJSONWhitespace(char ch) {
        return ch == 0x0009 || ch == 0x000A || ch == 0x000D || ch == 0x0020;
    }

    private boolean isNumericKey(String key) {
        if (key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            if (!Character.isDigit(key.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * JSON.isRawJSON(O)
     * ES2024 proposal: json-parse-with-source
     * Checks if O has [[IsRawJSON]] internal slot.
     */
    public JSValue isRawJSON(JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSBoolean.FALSE;
        }
        JSValue value = args[0];
        if (!(value instanceof JSObject obj)) {
            return JSBoolean.FALSE;
        }
        return JSBoolean.valueOf(isRawJSONObject(obj));
    }

    /**
     * Check if an object was created by JSON.rawJSON
     */
    private boolean isRawJSONObject(JSObject obj) {
        return rawJSONObjects.containsKey(obj);
    }

    /**
     * Check if a char is a surrogate (0xD800-0xDFFF).
     * Based on QuickJS is_surrogate macro.
     */
    private boolean isSurrogate(char ch) {
        return (ch >= 0xD800 && ch <= 0xDFFF);
    }

    /**
     * Check and transform value according to toJSON and replacer.
     * Based on QuickJS js_json_check.
     * Returns the processed value, or null on exception.
     */
    private JSValue jsonCheck(
            StringifyContext stringifyContext,
            JSValue holder,
            JSValue value,
            JSValue key) {
        // Check for toJSON method - applies to both Objects and BigInt
        if (value instanceof JSObject || value instanceof JSBigInt) {
            if (value instanceof JSFunction) {
                // Functions don't have toJSON checked, they just become undefined
            } else {
                JSValue toJSON = null;
                if (value instanceof JSObject obj) {
                    toJSON = getSafe(obj, "toJSON");
                    if (context.hasPendingException()) {
                        return null;
                    }
                } else if (value instanceof JSBigInt) {
                    // For primitive BigInt, auto-box to look up toJSON on prototype
                    // but pass the original primitive as receiver for correct getter this
                    JSObject boxed = JSTypeConversions.toObject(context, value);
                    if (boxed != null) {
                        try {
                            toJSON = boxed.get(PropertyKey.fromString("toJSON"), value);
                        } catch (JSVirtualMachineException e) {
                            convertVMException(e);
                            return null;
                        } catch (JSException e) {
                            convertJSException(e);
                            return null;
                        }
                        if (context.hasPendingException()) {
                            return null;
                        }
                    }
                }
                if (toJSON instanceof JSFunction toJSONFunc) {
                    value = callSafe(toJSONFunc, value, new JSValue[]{key});
                    if (value == null) {
                        return null;
                    }
                }
            }
        }

        // Apply replacer function
        if (stringifyContext.replacerFunc != null) {
            value = callSafe(stringifyContext.replacerFunc, holder, new JSValue[]{key, value});
            if (value == null) {
                return null;
            }
        }

        // Determine what to keep based on the tag
        // Following QuickJS js_json_check: keep objects (non-function), strings, numbers, booleans, null, bigint
        if (value instanceof JSObject) {
            if (value instanceof JSFunction) {
                return JSUndefined.INSTANCE;
            }
            return value;
        }
        if (value instanceof JSString || value instanceof JSNumber || value instanceof JSBoolean || value instanceof JSNull) {
            return value;
        }
        if (value instanceof JSBigInt) {
            return value;
        }
        // Symbols, undefined, etc. become undefined
        return JSUndefined.INSTANCE;
    }

    /**
     * Convert value to JSON string representation.
     * Based on QuickJS js_json_to_str.
     */
    private boolean jsonToStr(
            StringifyContext stringifyContext,
            StringBuilder sb,
            JSValue holder,
            JSValue jsValue,
            String currentIndent,
            String keyInParent) {

        // Handle wrapper objects: unwrap using proper ToNumber/ToString/etc.
        if (jsValue instanceof JSObject jsObject && !(jsValue instanceof JSFunction) && !(jsValue instanceof JSArray)) {
            if (jsObject instanceof JSStringObject) {
                try {
                    jsValue = JSTypeConversions.toString(context, jsValue);
                } catch (JSVirtualMachineException e) {
                    convertVMException(e);
                    return false;
                }
                if (context.hasPendingException()) {
                    return false;
                }
            } else if (jsObject instanceof JSNumberObject) {
                try {
                    jsValue = JSTypeConversions.toNumber(context, jsValue);
                } catch (JSVirtualMachineException e) {
                    convertVMException(e);
                    return false;
                }
                if (context.hasPendingException()) {
                    return false;
                }
            } else if (jsObject instanceof JSBooleanObject boolObj) {
                jsValue = boolObj.getPrimitiveValue();
            } else if (jsObject instanceof JSBigIntObject bigIntObj) {
                jsValue = bigIntObj.getPrimitiveValue();
            }
        }

        // Handle rawJSON objects
        if (jsValue instanceof JSObject rawObj && isRawJSONObject(rawObj)) {
            JSValue rawValue = rawObj.get("rawJSON");
            if (rawValue instanceof JSString rawStr) {
                sb.append(rawStr.value());
                return true;
            }
        }

        // Handle primitives
        if (jsValue instanceof JSNull) {
            sb.append("null");
            return true;
        }

        if (jsValue instanceof JSBoolean jsBoolean) {
            sb.append(jsBoolean.value());
            return true;
        }

        if (jsValue instanceof JSNumber jsNumber) {
            double doubleValue = jsNumber.value();
            if (Double.isNaN(doubleValue) || Double.isInfinite(doubleValue)) {
                sb.append("null");
            } else if (doubleValue == Math.floor(doubleValue) && !Double.isInfinite(doubleValue) && Math.abs(doubleValue) < (1L << 53)) {
                sb.append((long) doubleValue);
            } else {
                sb.append(jsNumber);
            }
            return true;
        }

        if (jsValue instanceof JSString jsString) {
            sb.append(stringifyString(jsString.value()));
            return true;
        }

        if (jsValue instanceof JSBigInt) {
            // BigInt cannot be serialized in JSON
            context.throwTypeError("Do not know how to serialize a BigInt");
            return false;
        }

        if (jsValue instanceof JSUndefined) {
            // Should not happen normally (filtered by jsonCheck), but handle gracefully
            return false;
        }

        // Handle objects and arrays
        if (jsValue instanceof JSObject && !(jsValue instanceof JSFunction)) {
            // Check for circular reference using identity comparison
            if (stringifyContext.stack.containsKey(jsValue)) {
                context.throwTypeError(buildCircularReferenceMessage(stringifyContext, jsValue, keyInParent));
                return false;
            }

            stringifyContext.stack.put(jsValue, Boolean.TRUE);
            stringifyContext.cyclePath.add(new CycleEntry(keyInParent, jsValue));

            String newIndent = currentIndent + stringifyContext.gap;

            // Use IsArray with proxy unwrapping
            int isArray = JSTypeChecking.isArray(context, jsValue);
            if (isArray < 0) {
                stringifyContext.stack.remove(jsValue);
                stringifyContext.cyclePath.remove(stringifyContext.cyclePath.size() - 1);
                return false; // exception
            }

            boolean result;
            if (isArray == 1) {
                result = stringifyArrayWithContext(stringifyContext, sb, (JSObject) jsValue, currentIndent, newIndent);
            } else {
                result = stringifyObjectWithContext(stringifyContext, sb, (JSObject) jsValue, currentIndent, newIndent);
            }

            stringifyContext.stack.remove(jsValue);
            stringifyContext.cyclePath.remove(stringifyContext.cyclePath.size() - 1);
            return result;
        }

        // Anything else returns false (undefined behavior)
        return false;
    }

    // ========== JSON Parsing Helper Methods ==========

    /**
     * JSON.parse(text[, reviver])
     * ES2024 25.5.1
     * Parses JSON text with optional reviver function support.
     * Supports json-parse-with-source (context.source).
     */
    public JSValue parse(JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwSyntaxError("\"undefined\" is not valid JSON");
        }

        JSValue source = args[0];
        JSString textString;
        try {
            textString = JSTypeConversions.toString(context, source);
        } catch (JSVirtualMachineException e) {
            convertVMException(e);
            return JSUndefined.INSTANCE;
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String text = textString.value();

        JSValue jsValue;
        ParseContext parseContext;
        try {
            parseContext = new ParseContext(text);
            ParseResult parseResult = parseValue(parseContext, 0);
            int end = skipWhitespace(parseContext.text, parseResult.endIndex);
            if (end != parseContext.text.length()) {
                throw new JSONParseException("Unexpected data after JSON " + parseContext.getPositionInfo(end));
            }
            jsValue = parseResult.value;
        } catch (JSONParseException e) {
            return context.throwSyntaxError(e.getMessage());
        }

        // If reviver function is provided, apply it
        if (args.length > 1 && args[1] instanceof JSFunction reviver) {
            // Create root with CreateDataPropertyOrThrow (not [[Set]])
            JSObject root = context.createJSObject();
            root.defineProperty(PropertyKey.fromString(""), jsValue, PropertyDescriptor.DataState.All);
            JSValue result = internalizeJSONProperty(root, "", reviver, parseContext);
            if (result == null || context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
            return result;
        }

        return jsValue;
    }

    private ParseResult parseArray(ParseContext parseContext, int start) {
        if (parseContext.text.charAt(start) != '[') {
            throw new JSONParseException("Expected '[' " + parseContext.getPositionInfo(start));
        }

        JSArray arr = context.createJSArray();
        int i = skipWhitespace(parseContext.text, start + 1);

        // Empty array
        if (i < parseContext.text.length() && parseContext.text.charAt(i) == ']') {
            parseContext.recordSourceRange(arr, start, i + 1);
            return new ParseResult(arr, i + 1);
        }

        int elementIndex = 0;
        while (i < parseContext.text.length()) {
            // Parse value
            ParseResult valueResult = parseValue(parseContext, i);
            arr.push(valueResult.value);
            // Record source for array elements
            parseContext.recordElementSource(arr, String.valueOf(elementIndex), valueResult.value, valueResult.sourceStart, valueResult.sourceEnd);
            elementIndex++;
            i = skipWhitespace(parseContext.text, valueResult.endIndex);

            // Check for comma or end
            if (i >= parseContext.text.length()) {
                throw new JSONParseException("Expected ',' or ']' after array element in JSON " + parseContext.getPositionInfo(i));
            }

            if (parseContext.text.charAt(i) == ']') {
                parseContext.recordSourceRange(arr, start, i + 1);
                return new ParseResult(arr, i + 1);
            }

            if (parseContext.text.charAt(i) != ',') {
                throw new JSONParseException("Expected ',' or ']' after array element in JSON " + parseContext.getPositionInfo(i));
            }

            i = skipWhitespace(parseContext.text, i + 1);
        }

        throw new JSONParseException("Unterminated array in JSON " + parseContext.getPositionInfo(i));
    }

    private ParseResult parseFalse(ParseContext parseContext, int start) {
        if (parseContext.text.startsWith("false", start)) {
            return new ParseResult(JSBoolean.FALSE, start + 5, start, start + 5);
        }
        throw new JSONParseException("Invalid literal " + parseContext.getPositionInfo(start));
    }

    private int parseHex4(String hex) {
        if (hex == null || hex.length() != 4) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(hex.charAt(i), 16);
            if (digit < 0) {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    private ParseResult parseNull(ParseContext parseContext, int start) {
        if (parseContext.text.startsWith("null", start)) {
            return new ParseResult(JSNull.INSTANCE, start + 4, start, start + 4);
        }
        throw new JSONParseException("Invalid literal " + parseContext.getPositionInfo(start));
    }

    private ParseResult parseNumber(ParseContext parseContext, int start) {
        int i = start;

        // Optional minus
        if (i < parseContext.text.length() && parseContext.text.charAt(i) == '-') {
            i++;
        }

        // Digits
        if (i >= parseContext.text.length() || !Character.isDigit(parseContext.text.charAt(i))) {
            throw new JSONParseException("Invalid number in JSON " + parseContext.getPositionInfo(i));
        }

        // Integer part
        if (parseContext.text.charAt(i) == '0') {
            i++;
            // Leading zero must not be followed by another digit
            if (i < parseContext.text.length() && Character.isDigit(parseContext.text.charAt(i))) {
                throw new JSONParseException("Unexpected number in JSON " + parseContext.getPositionInfo(i));
            }
        } else {
            while (i < parseContext.text.length() && Character.isDigit(parseContext.text.charAt(i))) {
                i++;
            }
        }

        // Optional fraction
        if (i < parseContext.text.length() && parseContext.text.charAt(i) == '.') {
            i++;
            if (i >= parseContext.text.length() || !Character.isDigit(parseContext.text.charAt(i))) {
                throw new JSONParseException("Invalid number in JSON " + parseContext.getPositionInfo(i));
            }
            while (i < parseContext.text.length() && Character.isDigit(parseContext.text.charAt(i))) {
                i++;
            }
        }

        // Optional exponent
        if (i < parseContext.text.length() && (parseContext.text.charAt(i) == 'e' || parseContext.text.charAt(i) == 'E')) {
            i++;
            if (i < parseContext.text.length() && (parseContext.text.charAt(i) == '+' || parseContext.text.charAt(i) == '-')) {
                i++;
            }
            if (i >= parseContext.text.length() || !Character.isDigit(parseContext.text.charAt(i))) {
                throw new JSONParseException("Invalid number in JSON " + parseContext.getPositionInfo(i));
            }
            while (i < parseContext.text.length() && Character.isDigit(parseContext.text.charAt(i))) {
                i++;
            }
        }

        String numStr = parseContext.text.substring(start, i);
        return new ParseResult(JSNumber.of(Double.parseDouble(numStr)), i, start, i);
    }

    private ParseResult parseObject(ParseContext parseContext, int start) {
        if (parseContext.text.charAt(start) != '{') {
            throw new JSONParseException("Expected '{' " + parseContext.getPositionInfo(start));
        }

        JSObject obj = context.createJSObject();
        int propertyCount = 0;
        int i = skipWhitespace(parseContext.text, start + 1);

        // Empty object
        if (i < parseContext.text.length() && parseContext.text.charAt(i) == '}') {
            parseContext.recordSourceRange(obj, start, i + 1);
            return new ParseResult(obj, i + 1);
        }

        while (i < parseContext.text.length()) {
            // Parse key (must be string)
            ParseResult keyResult = parsePropertyName(parseContext, i, propertyCount);
            String key = ((JSString) keyResult.value).value();
            i = skipWhitespace(parseContext.text, keyResult.endIndex);

            // Expect colon
            if (i >= parseContext.text.length() || parseContext.text.charAt(i) != ':') {
                throw new JSONParseException("Expected ':' after property name in JSON " + parseContext.getPositionInfo(i));
            }
            i = skipWhitespace(parseContext.text, i + 1);

            // Parse value
            ParseResult valueResult = parseValue(parseContext, i);
            // Use DefineOwnProperty (CreateDataProperty), NOT [[Set]]
            // This ensures __proto__ is treated as a regular property
            obj.defineProperty(PropertyKey.fromString(key),
                    valueResult.value, PropertyDescriptor.DataState.All);
            // Record source for object properties
            parseContext.recordElementSource(obj, key, valueResult.value, valueResult.sourceStart, valueResult.sourceEnd);
            propertyCount++;
            i = skipWhitespace(parseContext.text, valueResult.endIndex);

            // Check for comma or end
            if (i >= parseContext.text.length()) {
                throw new JSONParseException("Expected ',' or '}' after property value in JSON " + parseContext.getPositionInfo(i));
            }

            if (parseContext.text.charAt(i) == '}') {
                parseContext.recordSourceRange(obj, start, i + 1);
                return new ParseResult(obj, i + 1);
            }

            if (parseContext.text.charAt(i) != ',') {
                throw new JSONParseException("Expected ',' or '}' after property value in JSON " + parseContext.getPositionInfo(i));
            }

            i = skipWhitespace(parseContext.text, i + 1);
        }

        throw new JSONParseException("Unterminated object in JSON " + parseContext.getPositionInfo(i));
    }

    private ParseResult parsePropertyName(ParseContext parseContext, int start, int propertyCount) {
        char firstChar = parseContext.text.charAt(start);
        if (firstChar != '"') {
            if (propertyCount > 0) {
                throw new JSONParseException("Expected double-quoted property name in JSON " + parseContext.getPositionInfo(start));
            } else {
                throw new JSONParseException("Expected property name or '}' in JSON " + parseContext.getPositionInfo(start));
            }
        }

        StringBuilder sb = new StringBuilder();
        int i = start + 1;

        while (i < parseContext.text.length()) {
            char ch = parseContext.text.charAt(i);

            if (ch == '"') {
                return new ParseResult(new JSString(sb.toString()), i + 1);
            }

            if (ch == '\\') {
                i++;
                if (i >= parseContext.text.length()) {
                    throw new JSONParseException("Unexpected end in property name " + parseContext.getPositionInfo(i));
                }
                char escaped = parseContext.text.charAt(i);
                switch (escaped) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        // Unicode escape
                        if (i + 4 >= parseContext.text.length()) {
                            throw new JSONParseException("Invalid unicode escape in property name " + parseContext.getPositionInfo(i));
                        }
                        String hex = parseContext.text.substring(i + 1, i + 5);
                        int codePoint = parseHex4(hex);
                        if (codePoint < 0) {
                            throw new JSONParseException("Invalid unicode escape in property name " + parseContext.getPositionInfo(i));
                        }
                        sb.append((char) codePoint);
                        i += 4;
                    }
                    default ->
                            throw new JSONParseException("Invalid escape in property name: \\" + escaped + parseContext.getPositionInfo(i));
                }
            } else {
                if (isJSONStringControlCharacter(ch)) {
                    throw new JSONParseException("Bad control character in string literal in JSON " + parseContext.getPositionInfo(i));
                }
                sb.append(ch);
            }
            i++;
        }

        throw new JSONParseException("Unterminated property name in JSON " + parseContext.getPositionInfo(i));
    }

    private ParseResult parseString(ParseContext parseContext, int start) {
        if (parseContext.text.charAt(start) != '"') {
            throw new JSONParseException("Expected '\"' " + parseContext.getPositionInfo(start));
        }

        StringBuilder sb = new StringBuilder();
        int i = start + 1;

        while (i < parseContext.text.length()) {
            char ch = parseContext.text.charAt(i);

            if (ch == '"') {
                return new ParseResult(new JSString(sb.toString()), i + 1, start, i + 1);
            }

            if (ch == '\\') {
                i++;
                if (i >= parseContext.text.length()) {
                    throw new JSONParseException("Unexpected end in string " + parseContext.getPositionInfo(i));
                }
                char escaped = parseContext.text.charAt(i);
                switch (escaped) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        // Unicode escape
                        if (i + 4 >= parseContext.text.length()) {
                            throw new JSONParseException("Invalid unicode escape " + parseContext.getPositionInfo(i));
                        }
                        String hex = parseContext.text.substring(i + 1, i + 5);
                        int codePoint = parseHex4(hex);
                        if (codePoint < 0) {
                            throw new JSONParseException("Invalid unicode escape " + parseContext.getPositionInfo(i));
                        }
                        sb.append((char) codePoint);
                        i += 4;
                    }
                    default ->
                            throw new JSONParseException("Invalid escape: \\" + escaped + parseContext.getPositionInfo(i));
                }
            } else {
                if (isJSONStringControlCharacter(ch)) {
                    throw new JSONParseException("Bad control character in string literal in JSON " + parseContext.getPositionInfo(i));
                }
                sb.append(ch);
            }
            i++;
        }

        throw new JSONParseException("Unterminated string in JSON " + parseContext.getPositionInfo(i));
    }

    private ParseResult parseTrue(ParseContext parseContext, int start) {
        if (parseContext.text.startsWith("true", start)) {
            return new ParseResult(JSBoolean.TRUE, start + 4, start, start + 4);
        }
        throw new JSONParseException("Invalid literal " + parseContext.getPositionInfo(start));
    }

    private ParseResult parseValue(ParseContext parseContext, int start) {
        int i = skipWhitespace(parseContext.text, start);

        if (i >= parseContext.text.length()) {
            throw new JSONParseException("Unexpected end of JSON input");
        }

        char ch = parseContext.text.charAt(i);

        return switch (ch) {
            case '"' -> parseString(parseContext, i);
            case '{' -> parseObject(parseContext, i);
            case '[' -> parseArray(parseContext, i);
            case 't' -> parseTrue(parseContext, i);
            case 'f' -> parseFalse(parseContext, i);
            case 'n' -> parseNull(parseContext, i);
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber(parseContext, i);
            default -> throw new JSONParseException("Unexpected character: " + ch + parseContext.getPositionInfo(i));
        };
    }

    // ========== JSON.stringify ==========

    /**
     * JSON.rawJSON(text)
     * ES2024 proposal: json-parse-with-source
     * Creates a frozen null-prototype object with [[IsRawJSON]] internal slot.
     */
    public JSValue rawJSON(JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return throwRawJSONInvalidJson("undefined");
        }

        // Step 1: Let jsonString be ? ToString(text)
        JSString jsonString;
        try {
            jsonString = JSTypeConversions.toString(context, args[0]);
        } catch (JSVirtualMachineException e) {
            convertVMException(e);
            return JSUndefined.INSTANCE;
        }
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // Step 2: Throw a SyntaxError if jsonString is the empty String, or if
        // either the first or last code unit is 0x0009, 0x000A, 0x000D, or 0x0020
        String text = jsonString.value();
        if (text.isEmpty()) {
            return throwRawJSONInvalidValue();
        }
        char firstChar = text.charAt(0);
        char lastChar = text.charAt(text.length() - 1);
        if (isJSONWhitespace(firstChar) || isJSONWhitespace(lastChar)) {
            return throwRawJSONUnexpectedToken(firstChar, text);
        }

        // Step 3: Parse jsonString and validate it is a single JSON value (not object/array)
        try {
            char ch = text.charAt(0);
            // Must not be an object or array
            if (ch == '{' || ch == '[') {
                return throwRawJSONUnexpectedToken(ch, text);
            }
            ParseContext parseContext = new ParseContext(text);
            ParseResult parseResult = parseValue(parseContext, 0);
            int end = skipWhitespace(text, parseResult.endIndex);
            if (end != text.length()) {
                return throwRawJSONInvalidValue();
            }
        } catch (JSONParseException e) {
            if ("undefined".equals(text)) {
                return throwRawJSONInvalidJson(text);
            }
            return throwRawJSONInvalidValue();
        }

        // Step 5: Let obj be OrdinaryObjectCreate(null, internalSlotsList)
        JSObject obj = context.createJSObject();
        obj.setPrototype(null);

        // Step 6 + Step 7 effective state:
        // Define rawJSON as enumerable but non-writable/non-configurable so the
        // object satisfies frozen integrity checks after freeze() marks it non-extensible.
        obj.defineProperty(PropertyKey.fromString("rawJSON"), jsonString, PropertyDescriptor.DataState.Enumerable);

        // Mark as rawJSON object using Java-level tracking (not visible to JS)
        rawJSONObjects.put(obj, Boolean.TRUE);

        // Step 7: Perform SetIntegrityLevel(obj, frozen)
        obj.freeze();

        // Step 8: Return obj
        return obj;
    }

    // ========== JSON.rawJSON and JSON.isRawJSON ==========

    private int skipWhitespace(String text, int start) {
        int i = start;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    /**
     * JSON.stringify(value[, replacer[, space]])
     * ES2024 25.5.2
     * Based on QuickJS JS_JSONStringify.
     */
    public JSValue stringify(JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSUndefined.INSTANCE;
        }

        JSValue value = args[0];

        // Create stringify context
        StringifyContext stringifyContext = new StringifyContext();

        // Process replacer parameter
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            JSValue replacer = args[1];
            if (replacer instanceof JSFunction) {
                stringifyContext.replacerFunc = (JSFunction) replacer;
            } else if (replacer instanceof JSObject) {
                // Use IsArray with proxy unwrapping
                int isReplacerArray = JSTypeChecking.isArray(context, replacer);
                if (isReplacerArray < 0) {
                    return JSUndefined.INSTANCE; // exception (e.g., revoked proxy)
                }
                if (isReplacerArray == 1) {
                    // Build property list from array
                    List<String> propertyList = new ArrayList<>();
                    long replacerLength;
                    try {
                        JSValue lengthVal = ((JSObject) replacer).get(PropertyKey.LENGTH);
                        if (context.hasPendingException()) {
                            return JSUndefined.INSTANCE;
                        }
                        replacerLength = JSTypeConversions.toLength(context, lengthVal);
                        if (context.hasPendingException()) {
                            return JSUndefined.INSTANCE;
                        }
                    } catch (JSVirtualMachineException e) {
                        convertVMException(e);
                        return JSUndefined.INSTANCE;
                    }
                    for (long i = 0; i < replacerLength; i++) {
                        JSValue item;
                        try {
                            item = ((JSObject) replacer).get(PropertyKey.fromString(Long.toString(i)));
                        } catch (JSVirtualMachineException e) {
                            convertVMException(e);
                            return JSUndefined.INSTANCE;
                        }
                        if (context.hasPendingException()) {
                            return JSUndefined.INSTANCE;
                        }
                        String propName = null;

                        if (item instanceof JSString str) {
                            propName = str.value();
                        } else if (item instanceof JSNumber) {
                            try {
                                propName = JSTypeConversions.toString(context, item).value();
                            } catch (JSVirtualMachineException e) {
                                convertVMException(e);
                                return JSUndefined.INSTANCE;
                            }
                        } else if (item instanceof JSObject itemObj) {
                            if (itemObj instanceof JSStringObject || itemObj instanceof JSNumberObject) {
                                try {
                                    propName = JSTypeConversions.toString(context, item).value();
                                } catch (JSVirtualMachineException e) {
                                    convertVMException(e);
                                    return JSUndefined.INSTANCE;
                                }
                                if (context.hasPendingException()) {
                                    return JSUndefined.INSTANCE;
                                }
                            }
                            // Other objects are skipped
                        }
                        // Non-string, non-number primitives are skipped

                        if (propName != null && !propertyList.contains(propName)) {
                            propertyList.add(propName);
                        }
                    }
                    // Always set propertyList (even if empty) when replacer is an array
                    stringifyContext.propertyList = propertyList;
                }
            }
        }

        // Handle space parameter for indentation
        String indent = "";
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            JSValue space = args[2];

            // Handle Number/String objects using proper ToNumber/ToString
            if (space instanceof JSObject) {
                if (space instanceof JSNumberObject) {
                    try {
                        space = JSTypeConversions.toNumber(context, space);
                    } catch (JSVirtualMachineException e) {
                        convertVMException(e);
                        return JSUndefined.INSTANCE;
                    }
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                } else if (space instanceof JSStringObject) {
                    try {
                        space = JSTypeConversions.toString(context, space);
                    } catch (JSVirtualMachineException e) {
                        convertVMException(e);
                        return JSUndefined.INSTANCE;
                    }
                    if (context.hasPendingException()) {
                        return JSUndefined.INSTANCE;
                    }
                }
            }

            if (space instanceof JSNumber num) {
                int spaces = Math.min(10, Math.max(0, (int) num.value()));
                indent = " ".repeat(spaces);
            } else if (space instanceof JSString str) {
                indent = str.value().substring(0, Math.min(10, str.value().length()));
            }
        }
        stringifyContext.gap = indent;

        // Create wrapper object using CreateDataProperty (not [[Set]])
        JSObject wrapper = context.createJSObject();
        wrapper.defineProperty(PropertyKey.fromString(""), value, PropertyDescriptor.DataState.All);

        // Apply initial check (handles toJSON and replacer)
        JSValue processedValue = jsonCheck(stringifyContext, wrapper, value, new JSString(""));
        if (processedValue == null || context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (processedValue instanceof JSUndefined) {
            return JSUndefined.INSTANCE;
        }

        try {
            StringBuilder sb = new StringBuilder();
            stringifyContext.stack = new IdentityHashMap<>();
            stringifyContext.cyclePath = new ArrayList<>();
            if (jsonToStr(stringifyContext, sb, wrapper, processedValue, "", "")) {
                return new JSString(sb.toString());
            }
            return JSUndefined.INSTANCE;
        } catch (JSVirtualMachineException e) {
            convertVMException(e);
            return JSUndefined.INSTANCE;
        } catch (JSException e) {
            convertJSException(e);
            return JSUndefined.INSTANCE;
        }
    }

    /**
     * Stringify array with context.
     * Based on QuickJS array branch in js_json_to_str.
     */
    private boolean stringifyArrayWithContext(
            StringifyContext stringifyContext,
            StringBuilder sb,
            JSObject array,
            String currentIndent,
            String newIndent) {
        sb.append('[');
        long arrayLength;
        try {
            JSValue lengthVal = array.get(PropertyKey.LENGTH);
            if (context.hasPendingException()) {
                return false;
            }
            arrayLength = JSTypeConversions.toLength(context, lengthVal);
            if (context.hasPendingException()) {
                return false;
            }
        } catch (JSVirtualMachineException e) {
            convertVMException(e);
            return false;
        }

        for (long i = 0; i < arrayLength; i++) {
            if (i > 0) {
                sb.append(',');
            }

            if (!stringifyContext.gap.isEmpty()) {
                sb.append('\n').append(newIndent);
            }

            JSValue elem = getSafe(array, i);
            if (context.hasPendingException()) {
                return false;
            }
            if (elem == null) {
                elem = JSUndefined.INSTANCE;
            }

            JSString indexKey = new JSString(String.valueOf(i));
            JSValue processedElem = jsonCheck(stringifyContext, array, elem, indexKey);
            if (processedElem == null || context.hasPendingException()) {
                return false;
            }

            if (processedElem instanceof JSUndefined) {
                sb.append("null");
            } else {
                if (!jsonToStr(stringifyContext, sb, array, processedElem, newIndent, String.valueOf(i))) {
                    if (context.hasPendingException()) {
                        return false;
                    }
                    sb.append("null");
                }
            }
        }

        if (arrayLength > 0 && !stringifyContext.gap.isEmpty()) {
            sb.append('\n').append(currentIndent);
        }
        sb.append(']');
        return true;
    }

    /**
     * Stringify object with context.
     * Based on QuickJS object branch in js_json_to_str.
     */
    private boolean stringifyObjectWithContext(
            StringifyContext stringifyContext,
            StringBuilder sb,
            JSObject object,
            String currentIndent,
            String newIndent) {
        sb.append('{');
        boolean hasContent = false;

        // Determine which properties to stringify
        List<String> keys;
        if (stringifyContext.propertyList != null) {
            keys = stringifyContext.propertyList;
        } else {
            try {
                keys = getEnumerableStringKeys(object);
            } catch (JSVirtualMachineException e) {
                convertVMException(e);
                return false;
            }
            if (context.hasPendingException()) {
                return false;
            }
        }

        for (String key : keys) {
            JSValue propValue = getSafe(object, key);
            if (context.hasPendingException()) {
                return false;
            }
            if (propValue == null) {
                propValue = JSUndefined.INSTANCE;
            }

            JSValue processedValue = jsonCheck(stringifyContext, object, propValue, new JSString(key));
            if (processedValue == null || context.hasPendingException()) {
                return false;
            }

            if (!(processedValue instanceof JSUndefined)) {
                // Try stringifying the value
                StringBuilder tempSb = new StringBuilder();
                if (jsonToStr(stringifyContext, tempSb, object, processedValue, newIndent, key)) {
                    // Successfully stringified, add to output
                    if (hasContent) {
                        sb.append(',');
                    }

                    if (!stringifyContext.gap.isEmpty()) {
                        sb.append('\n').append(newIndent);
                    }

                    sb.append(stringifyString(key));
                    sb.append(':');

                    if (!stringifyContext.gap.isEmpty()) {
                        sb.append(' ');
                    }

                    sb.append(tempSb);
                    hasContent = true;
                } else if (context.hasPendingException()) {
                    return false;
                }
            }
        }

        if (hasContent && !stringifyContext.gap.isEmpty()) {
            sb.append('\n').append(currentIndent);
        }
        sb.append('}');
        return true;
    }

    // ========== Stringify Helper Methods ==========

    /**
     * Escape a string for JSON output.
     * Based on QuickJS JS_ToQuotedString.
     * Escapes lone surrogates (0xD800-0xDFFF) as unicode escape sequences.
     */
    private String stringifyString(String str) {
        StringBuilder sb = new StringBuilder("\"");
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char ch = str.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else if (isSurrogate(ch)) {
                        // Check if this is a proper surrogate pair
                        if (Character.isHighSurrogate(ch) && i + 1 < len && Character.isLowSurrogate(str.charAt(i + 1))) {
                            // Valid surrogate pair - output both characters as-is
                            sb.append(ch);
                            sb.append(str.charAt(i + 1));
                            i++;
                        } else {
                            // Lone surrogate - escape it
                            sb.append(String.format("\\u%04x", (int) ch));
                        }
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private JSValue throwRawJSONInvalidJson(String text) {
        return context.throwSyntaxError("\"" + text + "\" is not valid JSON");
    }

    private JSValue throwRawJSONInvalidValue() {
        return context.throwSyntaxError("Invalid value for JSON.rawJSON");
    }

    private JSValue throwRawJSONUnexpectedToken(char token, String text) {
        return context.throwSyntaxError("Unexpected token '" + token + "', \"" + text + "\" is not valid JSON");
    }

    // ========== Inner Classes ==========

    /**
     * Entry in the circular reference detection stack, tracking the property key
     * and object for V8-compatible error messages.
     */
    private record CycleEntry(String key, JSValue obj) {
    }

    /**
     * Context for JSON parsing with position tracking and source tracking for reviver.
     */

    /**
     * Custom exception for JSON parsing errors with position information
     */
    private static class JSONParseException extends RuntimeException {
        JSONParseException(String message) {
            super(message);
        }
    }

    private static class ParseContext {
        final String text;
        // Source tracking for primitive values within structured types (arrays/objects)
        // Tracks both the source text and original parsed value per (parent, key) pair
        private final IdentityHashMap<JSValue, Map<String, SourceEntry>> elementSources = new IdentityHashMap<>();
        // Track whether a value is a structured type (object/array)
        private final IdentityHashMap<JSValue, int[]> structuredRanges = new IdentityHashMap<>();

        ParseContext(String text) {
            this.text = text;
        }

        /**
         * Get line and column from position (0-based position, 1-based line/column)
         */
        String getPositionInfo(int position) {
            int line = 1;
            int column = 1;
            for (int i = 0; i < position && i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return "at position " + position + " (line " + line + " column " + column + ")";
        }

        /**
         * Get the source text for a value at the given key in the holder.
         * Returns null for structured types (objects/arrays), if source tracking is not available,
         * or if the value has been modified by the reviver (compared by identity with original parsed value).
         */
        String getSourceForValue(String key, JSValue holder, JSValue currentValue) {
            if (holder instanceof JSObject) {
                Map<String, SourceEntry> sources = elementSources.get(holder);
                if (sources != null) {
                    SourceEntry entry = sources.get(key);
                    if (entry != null) {
                        // If current value is SameValue to original parsed value, source remains visible.
                        // This matches json-parse-with-source semantics when a reviver reassigns an equivalent value.
                        if (isSameValue(entry.originalValue, currentValue)) {
                            return entry.sourceText;
                        }
                        return null;
                    }
                }
            }
            // For the root value
            if (key.isEmpty() && structuredRanges.get(holder) == null) {
                // The root value - return the entire text (trimmed of whitespace)
                int start = 0;
                while (start < text.length()) {
                    char ch = text.charAt(start);
                    if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                        start++;
                    } else {
                        break;
                    }
                }
                int end = text.length();
                // Trim trailing whitespace
                while (end > start && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t'
                        || text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
                    end--;
                }
                return text.substring(start, end);
            }
            return null; // structured types don't have source
        }

        private boolean isSameValue(JSValue leftValue, JSValue rightValue) {
            if (leftValue == rightValue) {
                return true;
            }
            if (leftValue == null || rightValue == null) {
                return false;
            }
            if (leftValue.getClass() != rightValue.getClass()) {
                return false;
            }
            if (leftValue instanceof JSNumber leftNumber && rightValue instanceof JSNumber rightNumber) {
                double leftDoubleValue = leftNumber.value();
                double rightDoubleValue = rightNumber.value();
                if (Double.isNaN(leftDoubleValue) && Double.isNaN(rightDoubleValue)) {
                    return true;
                }
                if (leftDoubleValue == 0.0D && rightDoubleValue == 0.0D) {
                    return Double.doubleToRawLongBits(leftDoubleValue) == Double.doubleToRawLongBits(rightDoubleValue);
                }
                return leftDoubleValue == rightDoubleValue;
            }
            if (leftValue instanceof JSString leftString && rightValue instanceof JSString rightString) {
                return leftString.value().equals(rightString.value());
            }
            if (leftValue instanceof JSBoolean leftBoolean && rightValue instanceof JSBoolean rightBoolean) {
                return leftBoolean.value() == rightBoolean.value();
            }
            if (leftValue instanceof JSBigInt leftBigInt && rightValue instanceof JSBigInt rightBigInt) {
                return leftBigInt.value().equals(rightBigInt.value());
            }
            return false;
        }

        void recordElementSource(JSValue parent, String key, JSValue parsedValue, int sourceStart, int sourceEnd) {
            // Structured types (objects/arrays) have sourceStart=-1, skip them
            if (sourceStart < 0 || sourceEnd < 0) {
                return;
            }
            elementSources.computeIfAbsent(parent, k -> new HashMap<>())
                    .put(key, new SourceEntry(text.substring(sourceStart, sourceEnd), parsedValue));
        }

        void recordSourceRange(JSValue structured, int start, int end) {
            structuredRanges.put(structured, new int[]{start, end});
        }
    }

    private static class ParseResult {
        final int endIndex;
        final int sourceEnd;
        final int sourceStart;
        final JSValue value;

        ParseResult(JSValue value, int endIndex) {
            this.value = value;
            this.endIndex = endIndex;
            this.sourceStart = -1;
            this.sourceEnd = -1;
        }

        ParseResult(JSValue value, int endIndex, int sourceStart, int sourceEnd) {
            this.value = value;
            this.endIndex = endIndex;
            this.sourceStart = sourceStart;
            this.sourceEnd = sourceEnd;
        }
    }

    /**
     * Tracks the source text and original parsed value for a property.
     * Used to detect when a reviver has modified a value (source becomes undefined).
     */
    private record SourceEntry(String sourceText, JSValue originalValue) {
    }

    /**
     * Context for JSON stringification
     */
    private static class StringifyContext {
        // Ordered list of objects in the current stringify path for cycle error messages
        List<CycleEntry> cyclePath = null;
        String gap = "";
        List<String> propertyList = null;
        JSFunction replacerFunc = null;
        IdentityHashMap<JSValue, Boolean> stack = null;
    }
}
