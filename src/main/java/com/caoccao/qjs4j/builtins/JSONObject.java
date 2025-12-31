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

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of JavaScript JSON object.
 * Based on ES2020 JSON specification.
 * <p>
 * This is a simplified implementation. Full JSON support requires:
 * - Complete recursive object/array serialization
 * - Proper handling of circular references
 * - Support for replacer/reviver functions
 * - Unicode escape sequences
 * - Number formatting edge cases
 */
public final class JSONObject {

    /**
     * Recursive internalize operation for JSON.parse reviver
     * Based on ES2020 24.5.1.1 and QuickJS internalize_json_property
     */
    private static JSValue internalizeJSONProperty(JSContext context, JSValue holder, String name, JSFunction reviver) {
        JSValue val = ((JSObject) holder).get(name);
        return internalizeJSONPropertyForValue(context, holder, name, val, reviver);
    }

    /**
     * Helper method to internalize a value that's already been retrieved
     */
    private static JSValue internalizeJSONPropertyForValue(JSContext context, JSValue holder, String name, JSValue val, JSFunction reviver) {
        if (val instanceof JSObject obj && !(val instanceof JSFunction)) {

            if (val instanceof JSArray arr) {
                // Process array elements
                long len = arr.getLength();
                for (long i = 0; i < len; i++) {
                    JSValue element = arr.get(i);
                    JSValue newElement = internalizeJSONPropertyForValue(context, val, String.valueOf(i), element, reviver);
                    if (newElement instanceof JSUndefined) {
                        arr.delete(PropertyKey.fromIndex((int) i));
                    } else {
                        arr.set(i, newElement);
                    }
                }
            } else {
                // Process object properties
                List<PropertyKey> keys = obj.getOwnPropertyKeys();
                for (PropertyKey key : keys) {
                    if (key.isString()) {
                        String prop = key.asString();
                        JSValue newElement = internalizeJSONProperty(context, val, prop, reviver);
                        if (newElement instanceof JSUndefined) {
                            obj.delete(prop);
                        } else {
                            obj.set(prop, newElement);
                        }
                    }
                }
            }
        }

        // Call reviver function with (key, value)
        JSValue nameValue = new JSString(name);
        JSValue result = reviver.call(context, holder, new JSValue[]{nameValue, val});
        return result;
    }

    /**
     * Check and transform value according to toJSON and replacer
     * Based on QuickJS js_json_check
     */
    private static JSValue jsonCheck(JSContext context, StringifyContext ctx, JSValue holder, JSValue val, JSValue key) {
        // Check for toJSON method
        if ((val instanceof JSObject || val instanceof JSBigInt) && !(val instanceof JSFunction)) {
            if (val instanceof JSObject obj) {
                JSValue toJSON = obj.get("toJSON");
                if (toJSON instanceof JSFunction toJSONFunc) {
                    val = toJSONFunc.call(context, val, new JSValue[]{key});
                }
            }
        }

        // Apply replacer function
        if (ctx.replacerFunc != null) {
            val = ctx.replacerFunc.call(context, holder, new JSValue[]{key, val});
        }

        // Filter out functions, symbols, and undefined (except in arrays where they become null)
        if (val instanceof JSFunction) {
            return JSUndefined.INSTANCE;
        }

        return val;
    }

    /**
     * Convert value to JSON string representation
     * Based on QuickJS js_json_to_str
     */
    private static boolean jsonToStr(
            JSContext context,
            StringifyContext stringifyContext,
            StringBuilder sb,
            JSValue holder,
            JSValue val,
            String currentIndent) {
        // Handle primitives
        if (val instanceof JSNull || val instanceof JSUndefined) {
            sb.append("null");
            return true;
        }

        if (val instanceof JSBoolean b) {
            sb.append(b.value() ? "true" : "false");
            return true;
        }

        if (val instanceof JSNumber n) {
            double d = n.value();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else if (d == Math.floor(d) && !Double.isInfinite(d)) {
                sb.append((long) d);
            } else {
                sb.append(n);
            }
            return true;
        }

        if (val instanceof JSString s) {
            sb.append(stringifyString(s.value()));
            return true;
        }

        if (val instanceof JSBigInt) {
            // BigInt cannot be serialized in JSON
            return false;
        }

        // Handle objects and arrays
        if (val instanceof JSObject && !(val instanceof JSFunction)) {
            // Check for circular reference
            if (stringifyContext.stack.contains(val)) {
                // Circular reference - throw error
                // Find where this object appears in the stack
                int circularIndex = stringifyContext.stack.indexOf(val);

                StringBuilder errorMessage = new StringBuilder("Converting circular structure to JSON\n");
                errorMessage.append("    --> starting at object with constructor '")
                        .append((val instanceof JSArray) ? "Array" : "Object")
                        .append("'\n");

                // Add intermediate path entries
                for (int i = circularIndex; i < stringifyContext.path.size(); i++) {
                    PathEntry entry = stringifyContext.path.get(i);
                    errorMessage.append("    |     ");
                    if (entry.isArrayIndex) {
                        errorMessage.append("index ").append(entry.property);
                    } else {
                        errorMessage.append("property '").append(entry.property).append("'");
                    }
                    errorMessage.append(" -> object with constructor '");
                    errorMessage.append((entry.object instanceof JSArray) ? "Array" : "Object");
                    errorMessage.append("'\n");
                }

                // Add the closing property
                if (stringifyContext.currentProperty != null) {
                    errorMessage.append("    --- ");
                    if (stringifyContext.isArrayIndex) {
                        errorMessage.append("index ").append(stringifyContext.currentProperty);
                    } else {
                        errorMessage.append("property '").append(stringifyContext.currentProperty).append("'");
                    }
                    errorMessage.append(" closes the circle");
                }
                throw new CircularReferenceException(errorMessage.toString());
            }

            stringifyContext.stack.add(val);
            // Add to path if we have a current property (not the root)
            if (stringifyContext.currentProperty != null) {
                stringifyContext.path.add(new PathEntry(
                        stringifyContext.currentProperty,
                        stringifyContext.isArrayIndex,
                        val
                ));
            }

            String newIndent = currentIndent + stringifyContext.gap;
            boolean result;

            if (val instanceof JSArray) {
                result = stringifyArrayWithContext(context, stringifyContext, sb, (JSArray) val, currentIndent, newIndent);
            } else {
                result = stringifyObjectWithContext(context, stringifyContext, sb, (JSObject) val, currentIndent, newIndent);
            }

            stringifyContext.stack.remove(stringifyContext.stack.size() - 1);
            // Remove from path if we added to it
            if (stringifyContext.currentProperty != null && !stringifyContext.path.isEmpty()) {
                stringifyContext.path.remove(stringifyContext.path.size() - 1);
            }
            return result;
        }

        // Anything else (symbols, etc.) returns undefined
        return false;
    }

    // ========== JSON Parsing Helper Methods ==========

    /**
     * JSON.parse(text[, reviver])
     * ES2020 24.5.1
     * Parses JSON text with optional reviver function support
     */
    public static JSValue parse(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return context.throwSyntaxError("\"undefined\" is not valid JSON");
        }

        String text = JSTypeConversions.toString(context, args[0]).value();

        JSValue obj;
        try {
            ParseContext parseContext = new ParseContext(text.strip());
            obj = parseValue(parseContext, 0).value;
        } catch (JSONParseException e) {
            return context.throwSyntaxError(e.getMessage());
        } catch (Exception e) {
            return context.throwSyntaxError(e.getMessage());
        }

        // If reviver function is provided, apply it
        if (args.length > 1 && args[1] instanceof JSFunction reviver) {
            JSObject root = new JSObject();
            root.set("", obj);
            return internalizeJSONProperty(context, root, "", reviver);
        }

        return obj;
    }

    private static ParseResult parseArray(ParseContext ctx, int start) {
        if (ctx.text.charAt(start) != '[') {
            throw new JSONParseException("Expected '[' " + ctx.getPositionInfo(start));
        }

        JSArray arr = new JSArray();
        int i = skipWhitespace(ctx.text, start + 1);

        // Empty array
        if (i < ctx.text.length() && ctx.text.charAt(i) == ']') {
            return new ParseResult(arr, i + 1);
        }

        while (i < ctx.text.length()) {
            // Parse value
            ParseResult valueResult = parseValue(ctx, i);
            arr.push(valueResult.value);
            i = skipWhitespace(ctx.text, valueResult.endIndex);

            // Check for comma or end
            if (i >= ctx.text.length()) {
                throw new JSONParseException("Expected ',' or ']' after array element in JSON " + ctx.getPositionInfo(i));
            }

            if (ctx.text.charAt(i) == ']') {
                return new ParseResult(arr, i + 1);
            }

            if (ctx.text.charAt(i) != ',') {
                throw new JSONParseException("Expected ',' or ']' after array element in JSON " + ctx.getPositionInfo(i));
            }

            i = skipWhitespace(ctx.text, i + 1);
        }

        throw new JSONParseException("Unterminated array in JSON " + ctx.getPositionInfo(i));
    }

    private static ParseResult parseFalse(ParseContext ctx, int start) {
        if (ctx.text.startsWith("false", start)) {
            return new ParseResult(JSBoolean.FALSE, start + 5);
        }
        throw new JSONParseException("Invalid literal " + ctx.getPositionInfo(start));
    }

    private static ParseResult parseNull(ParseContext ctx, int start) {
        if (ctx.text.startsWith("null", start)) {
            return new ParseResult(JSNull.INSTANCE, start + 4);
        }
        throw new JSONParseException("Invalid literal " + ctx.getPositionInfo(start));
    }

    private static ParseResult parseNumber(ParseContext ctx, int start) {
        int i = start;

        // Optional minus
        if (i < ctx.text.length() && ctx.text.charAt(i) == '-') {
            i++;
        }

        // Digits
        if (i >= ctx.text.length() || !Character.isDigit(ctx.text.charAt(i))) {
            throw new JSONParseException("Invalid number in JSON " + ctx.getPositionInfo(i));
        }

        // Integer part
        if (ctx.text.charAt(i) == '0') {
            i++;
            // Leading zero must not be followed by another digit
            if (i < ctx.text.length() && Character.isDigit(ctx.text.charAt(i))) {
                throw new JSONParseException("Unexpected number in JSON " + ctx.getPositionInfo(i));
            }
        } else {
            while (i < ctx.text.length() && Character.isDigit(ctx.text.charAt(i))) {
                i++;
            }
        }

        // Optional fraction
        if (i < ctx.text.length() && ctx.text.charAt(i) == '.') {
            i++;
            if (i >= ctx.text.length() || !Character.isDigit(ctx.text.charAt(i))) {
                throw new JSONParseException("Invalid number in JSON " + ctx.getPositionInfo(i));
            }
            while (i < ctx.text.length() && Character.isDigit(ctx.text.charAt(i))) {
                i++;
            }
        }

        // Optional exponent
        if (i < ctx.text.length() && (ctx.text.charAt(i) == 'e' || ctx.text.charAt(i) == 'E')) {
            i++;
            if (i < ctx.text.length() && (ctx.text.charAt(i) == '+' || ctx.text.charAt(i) == '-')) {
                i++;
            }
            if (i >= ctx.text.length() || !Character.isDigit(ctx.text.charAt(i))) {
                throw new JSONParseException("Invalid number in JSON " + ctx.getPositionInfo(i));
            }
            while (i < ctx.text.length() && Character.isDigit(ctx.text.charAt(i))) {
                i++;
            }
        }

        String numStr = ctx.text.substring(start, i);
        return new ParseResult(new JSNumber(Double.parseDouble(numStr)), i);
    }

    private static ParseResult parseObject(ParseContext ctx, int start) {
        if (ctx.text.charAt(start) != '{') {
            throw new JSONParseException("Expected '{' " + ctx.getPositionInfo(start));
        }

        JSObject obj = new JSObject();
        int propertyCount = 0;
        int i = skipWhitespace(ctx.text, start + 1);

        // Empty object
        if (i < ctx.text.length() && ctx.text.charAt(i) == '}') {
            return new ParseResult(obj, i + 1);
        }

        while (i < ctx.text.length()) {
            // Parse key (must be string)
            ParseResult keyResult = parsePropertyName(ctx, i, propertyCount);
            String key = ((JSString) keyResult.value).value();
            i = skipWhitespace(ctx.text, keyResult.endIndex);

            // Expect colon
            if (i >= ctx.text.length() || ctx.text.charAt(i) != ':') {
                throw new JSONParseException("Expected ':' after property name in JSON " + ctx.getPositionInfo(i));
            }
            i = skipWhitespace(ctx.text, i + 1);

            // Parse value
            ParseResult valueResult = parseValue(ctx, i);
            obj.set(key, valueResult.value);
            propertyCount++;
            i = skipWhitespace(ctx.text, valueResult.endIndex);

            // Check for comma or end
            if (i >= ctx.text.length()) {
                throw new JSONParseException("Expected ',' or '}' after property value in JSON " + ctx.getPositionInfo(i));
            }

            if (ctx.text.charAt(i) == '}') {
                return new ParseResult(obj, i + 1);
            }

            if (ctx.text.charAt(i) != ',') {
                throw new JSONParseException("Expected ',' or '}' after property value in JSON " + ctx.getPositionInfo(i));
            }

            i = skipWhitespace(ctx.text, i + 1);
        }

        throw new JSONParseException("Unterminated object in JSON " + ctx.getPositionInfo(i));
    }

    private static ParseResult parsePropertyName(ParseContext ctx, int start, int propertyCount) {
        char firstChar = ctx.text.charAt(start);
        if (firstChar != '"') {
            if (propertyCount > 0) {
                throw new JSONParseException("Expected double-quoted property name in JSON " + ctx.getPositionInfo(start));
            } else {
                throw new JSONParseException("Expected property name or '}' in JSON " + ctx.getPositionInfo(start));
            }
        }

        StringBuilder sb = new StringBuilder();
        int i = start + 1;

        while (i < ctx.text.length()) {
            char ch = ctx.text.charAt(i);

            if (ch == '"') {
                return new ParseResult(new JSString(sb.toString()), i + 1);
            }

            if (ch == '\\') {
                i++;
                if (i >= ctx.text.length()) {
                    throw new JSONParseException("Unexpected end in property name " + ctx.getPositionInfo(i));
                }
                char escaped = ctx.text.charAt(i);
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
                        if (i + 4 >= ctx.text.length()) {
                            throw new JSONParseException("Invalid unicode escape in property name " + ctx.getPositionInfo(i));
                        }
                        String hex = ctx.text.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    default ->
                            throw new JSONParseException("Invalid escape in property name: \\" + escaped + ctx.getPositionInfo(i));
                }
            } else {
                sb.append(ch);
            }
            i++;
        }

        throw new JSONParseException("Unterminated property name in JSON " + ctx.getPositionInfo(i));
    }

    private static ParseResult parseString(ParseContext ctx, int start) {
        if (ctx.text.charAt(start) != '"') {
            throw new JSONParseException("Expected '\"' " + ctx.getPositionInfo(start));
        }

        StringBuilder sb = new StringBuilder();
        int i = start + 1;

        while (i < ctx.text.length()) {
            char ch = ctx.text.charAt(i);

            if (ch == '"') {
                return new ParseResult(new JSString(sb.toString()), i + 1);
            }

            if (ch == '\\') {
                i++;
                if (i >= ctx.text.length()) {
                    throw new JSONParseException("Unexpected end in string " + ctx.getPositionInfo(i));
                }
                char escaped = ctx.text.charAt(i);
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
                        if (i + 4 >= ctx.text.length()) {
                            throw new JSONParseException("Invalid unicode escape " + ctx.getPositionInfo(i));
                        }
                        String hex = ctx.text.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    default -> throw new JSONParseException("Invalid escape: \\" + escaped + ctx.getPositionInfo(i));
                }
            } else {
                sb.append(ch);
            }
            i++;
        }

        throw new JSONParseException("Unterminated string in JSON " + ctx.getPositionInfo(i));
    }

    private static ParseResult parseTrue(ParseContext ctx, int start) {
        if (ctx.text.startsWith("true", start)) {
            return new ParseResult(JSBoolean.TRUE, start + 4);
        }
        throw new JSONParseException("Invalid literal " + ctx.getPositionInfo(start));
    }

    private static ParseResult parseValue(ParseContext ctx, int start) {
        int i = skipWhitespace(ctx.text, start);

        if (i >= ctx.text.length()) {
            throw new JSONParseException("Unexpected end of JSON input " + ctx.getPositionInfo(i));
        }

        char ch = ctx.text.charAt(i);

        return switch (ch) {
            case '"' -> parseString(ctx, i);
            case '{' -> parseObject(ctx, i);
            case '[' -> parseArray(ctx, i);
            case 't' -> parseTrue(ctx, i);
            case 'f' -> parseFalse(ctx, i);
            case 'n' -> parseNull(ctx, i);
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber(ctx, i);
            default -> throw new JSONParseException("Unexpected character: " + ch + ctx.getPositionInfo(i));
        };
    }

    private static int skipWhitespace(String text, int start) {
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
     * ES2020 24.5.2
     * Converts a JavaScript value to a JSON string with support for replacer and space parameters
     */
    public static JSValue stringify(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSUndefined.INSTANCE;
        }

        JSValue value = args[0];

        // If value is undefined, return undefined
        if (value instanceof JSUndefined) {
            return JSUndefined.INSTANCE;
        }

        // Create stringify context
        StringifyContext ctx = new StringifyContext();

        // Process replacer parameter
        if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
            JSValue replacer = args[1];
            if (replacer instanceof JSFunction) {
                ctx.replacerFunc = (JSFunction) replacer;
            } else if (replacer instanceof JSArray replacerArray) {
                // Build property list from array
                List<String> propertyList = new ArrayList<>();
                long len = replacerArray.getLength();
                for (long i = 0; i < len; i++) {
                    JSValue item = replacerArray.get(i);
                    String propName = null;

                    if (item instanceof JSString) {
                        propName = ((JSString) item).value();
                    } else if (item instanceof JSNumber) {
                        propName = String.valueOf((long) ((JSNumber) item).value());
                    } else if (item instanceof JSObject obj) {
                        // Handle String/Number objects
                        // Simplified: just convert to string
                        propName = JSTypeConversions.toString(context, item).value();
                    }

                    if (propName != null && !propertyList.contains(propName)) {
                        propertyList.add(propName);
                    }
                }
                ctx.propertyList = propertyList.isEmpty() ? null : propertyList;
            }
        }

        // Handle space parameter for indentation
        String indent = "";
        if (args.length > 2 && !(args[2] instanceof JSUndefined)) {
            JSValue space = args[2];

            // Handle Number/String objects
            if (space instanceof JSObject && !(space instanceof JSFunction)) {
                // Simplified: convert to primitive
                if (space instanceof JSNumber) {
                    space = space; // already a number
                } else {
                    space = JSTypeConversions.toString(context, space);
                }
            }

            if (space instanceof JSNumber num) {
                int spaces = Math.min(10, Math.max(0, (int) num.value()));
                indent = " ".repeat(spaces);
            } else if (space instanceof JSString str) {
                indent = str.value().substring(0, Math.min(10, str.value().length()));
            }
        }
        ctx.gap = indent;

        // Create wrapper object to hold the value
        JSObject wrapper = new JSObject();
        wrapper.set("", value);

        // Apply initial check (handles toJSON and replacer)
        JSValue processedValue = jsonCheck(context, ctx, wrapper, value, new JSString(""));

        if (processedValue instanceof JSUndefined) {
            return JSUndefined.INSTANCE;
        }

        try {
            StringBuilder sb = new StringBuilder();
            ctx.stack = new ArrayList<>();
            ctx.path = new ArrayList<>();
            if (jsonToStr(context, ctx, sb, wrapper, processedValue, "")) {
                return new JSString(sb.toString());
            }
            return JSUndefined.INSTANCE;
        } catch (CircularReferenceException e) {
            return context.throwTypeError(e.getMessage());
        } catch (Exception e) {
            return JSUndefined.INSTANCE;
        }
    }

    /**
     * Stringify array with context
     */
    private static boolean stringifyArrayWithContext(JSContext context, StringifyContext ctx, StringBuilder sb,
                                                     JSArray arr, String currentIndent, String newIndent) {
        sb.append('[');
        long len = arr.getLength();
        boolean hasContent = false;

        for (long i = 0; i < len; i++) {
            if (hasContent) {
                sb.append(',');
            }
            hasContent = true;

            if (!ctx.gap.isEmpty()) {
                sb.append('\n').append(newIndent);
            }

            JSValue elem = arr.get(i);
            JSValue processedElem = jsonCheck(context, ctx, arr, elem, new JSString(String.valueOf(i)));

            // Track current index for circular reference errors
            String previousProperty = ctx.currentProperty;
            boolean previousIsArrayIndex = ctx.isArrayIndex;
            ctx.currentProperty = String.valueOf(i);
            ctx.isArrayIndex = true;
            try {
                if (processedElem instanceof JSUndefined) {
                    sb.append("null");
                } else {
                    if (!jsonToStr(context, ctx, sb, arr, processedElem, newIndent)) {
                        sb.append("null");
                    }
                }
            } finally {
                ctx.currentProperty = previousProperty;
                ctx.isArrayIndex = previousIsArrayIndex;
            }
        }

        if (hasContent && !ctx.gap.isEmpty()) {
            sb.append('\n').append(currentIndent);
        }
        sb.append(']');
        return true;
    }

    /**
     * Stringify object with context
     */
    private static boolean stringifyObjectWithContext(JSContext context, StringifyContext ctx, StringBuilder sb,
                                                      JSObject obj, String currentIndent, String newIndent) {
        sb.append('{');
        boolean hasContent = false;

        // Determine which properties to stringify
        List<String> keys;
        if (ctx.propertyList != null) {
            keys = ctx.propertyList;
        } else {
            keys = new ArrayList<>();
            List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();
            for (PropertyKey key : propertyKeys) {
                if (key.isString()) {
                    keys.add(key.asString());
                }
            }
        }

        for (String key : keys) {
            JSValue propValue = obj.get(key);
            JSValue processedValue = jsonCheck(context, ctx, obj, propValue, new JSString(key));

            if (!(processedValue instanceof JSUndefined)) {
                // Try stringifying the value first in a temporary buffer
                StringBuilder tempSb = new StringBuilder();
                // Track current property for circular reference errors
                String previousProperty = ctx.currentProperty;
                boolean previousIsArrayIndex = ctx.isArrayIndex;
                ctx.currentProperty = key;
                ctx.isArrayIndex = false;
                try {
                    if (jsonToStr(context, ctx, tempSb, obj, processedValue, newIndent)) {
                        // Successfully stringified, add to output
                        if (hasContent) {
                            sb.append(',');
                        }

                        if (!ctx.gap.isEmpty()) {
                            sb.append('\n').append(newIndent);
                        }

                        sb.append(stringifyString(key));
                        sb.append(':');

                        if (!ctx.gap.isEmpty()) {
                            sb.append(' ');
                        }

                        sb.append(tempSb);
                        hasContent = true;
                    }
                } finally {
                    ctx.currentProperty = previousProperty;
                    ctx.isArrayIndex = previousIsArrayIndex;
                }
            }
        }

        if (hasContent && !ctx.gap.isEmpty()) {
            sb.append('\n').append(currentIndent);
        }
        sb.append('}');
        return true;
    }

    private static String stringifyString(String str) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : str.toCharArray()) {
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
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    // ========== JSON Stringification Helper Methods ==========

    /**
     * Custom exception for circular references during JSON stringification
     */
    private static class CircularReferenceException extends RuntimeException {
        CircularReferenceException(String message) {
            super(message);
        }
    }

    /**
     * Custom exception for JSON parsing errors with position information
     */
    private static class JSONParseException extends RuntimeException {
        JSONParseException(String message) {
            super(message);
        }
    }

    /**
     * Context for JSON parsing with position tracking
     */
    private record ParseContext(String text) {

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
    }

    private static class ParseResult {
        int endIndex;
        JSValue value;

        ParseResult(JSValue value, int endIndex) {
            this.value = value;
            this.endIndex = endIndex;
        }
    }

    /**
     * Entry in the path for circular reference tracking
     */
    private static class PathEntry {
        boolean isArrayIndex;
        JSValue object;
        String property;

        PathEntry(String property, boolean isArrayIndex, JSValue object) {
            this.property = property;
            this.isArrayIndex = isArrayIndex;
            this.object = object;
        }
    }

    /**
     * Context for JSON stringification
     */
    private static class StringifyContext {
        String currentProperty = null; // Track the property being processed for circular reference errors
        String gap = "";
        boolean isArrayIndex = false; // Track if current property is an array index
        List<PathEntry> path = null; // Track the full path for circular reference error messages
        List<String> propertyList = null;
        JSFunction replacerFunc = null;
        List<JSValue> stack = null;
    }
}
