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
 *
 * This is a simplified implementation. Full JSON support requires:
 * - Complete recursive object/array serialization
 * - Proper handling of circular references
 * - Support for replacer/reviver functions
 * - Unicode escape sequences
 * - Number formatting edge cases
 */
public final class JSONObject {

    /**
     * JSON.parse(text[, reviver])
     * ES2020 24.5.1
     * Simplified implementation - basic JSON parsing
     */
    public static JSValue parse(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return ctx.throwError("SyntaxError", "JSON.parse requires at least 1 argument");
        }

        String text = JSTypeConversions.toString(args[0]).getValue();

        try {
            return parseValue(text.strip(), 0).value;
        } catch (Exception e) {
            return ctx.throwError("SyntaxError", "Invalid JSON: " + e.getMessage());
        }
    }

    /**
     * JSON.stringify(value[, replacer[, space]])
     * ES2020 24.5.2
     * Simplified implementation - basic JSON stringification
     */
    public static JSValue stringify(JSContext ctx, JSValue thisArg, JSValue[] args) {
        if (args.length == 0) {
            return JSUndefined.INSTANCE;
        }

        JSValue value = args[0];

        // If value is undefined, return undefined
        if (value instanceof JSUndefined) {
            return JSUndefined.INSTANCE;
        }

        // Handle space parameter for indentation (simplified)
        String indent = "";
        if (args.length > 2 && args[2] instanceof JSNumber num) {
            int spaces = Math.min(10, Math.max(0, (int) num.value()));
            indent = " ".repeat(spaces);
        } else if (args.length > 2 && args[2] instanceof JSString str) {
            indent = str.getValue().substring(0, Math.min(10, str.getValue().length()));
        }

        try {
            String result = stringifyValue(value, indent, "");
            if (result == null) {
                return JSUndefined.INSTANCE;
            }
            return new JSString(result);
        } catch (Exception e) {
            return JSUndefined.INSTANCE;
        }
    }

    // ========== JSON Parsing Helper Methods ==========

    private static class ParseResult {
        JSValue value;
        int endIndex;

        ParseResult(JSValue value, int endIndex) {
            this.value = value;
            this.endIndex = endIndex;
        }
    }

    private static ParseResult parseValue(String text, int start) {
        int i = skipWhitespace(text, start);

        if (i >= text.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON input");
        }

        char ch = text.charAt(i);

        return switch (ch) {
            case '"' -> parseString(text, i);
            case '{' -> parseObject(text, i);
            case '[' -> parseArray(text, i);
            case 't' -> parseTrue(text, i);
            case 'f' -> parseFalse(text, i);
            case 'n' -> parseNull(text, i);
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber(text, i);
            default -> throw new IllegalArgumentException("Unexpected character: " + ch);
        };
    }

    private static ParseResult parseString(String text, int start) {
        if (text.charAt(start) != '"') {
            throw new IllegalArgumentException("Expected '\"'");
        }

        StringBuilder sb = new StringBuilder();
        int i = start + 1;

        while (i < text.length()) {
            char ch = text.charAt(i);

            if (ch == '"') {
                return new ParseResult(new JSString(sb.toString()), i + 1);
            }

            if (ch == '\\') {
                i++;
                if (i >= text.length()) {
                    throw new IllegalArgumentException("Unexpected end in string");
                }
                char escaped = text.charAt(i);
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
                        if (i + 4 >= text.length()) {
                            throw new IllegalArgumentException("Invalid unicode escape");
                        }
                        String hex = text.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid escape: \\" + escaped);
                }
            } else {
                sb.append(ch);
            }
            i++;
        }

        throw new IllegalArgumentException("Unterminated string");
    }

    private static ParseResult parseNumber(String text, int start) {
        int i = start;

        // Optional minus
        if (i < text.length() && text.charAt(i) == '-') {
            i++;
        }

        // Digits
        if (i >= text.length() || !Character.isDigit(text.charAt(i))) {
            throw new IllegalArgumentException("Invalid number");
        }

        // Integer part
        if (text.charAt(i) == '0') {
            i++;
        } else {
            while (i < text.length() && Character.isDigit(text.charAt(i))) {
                i++;
            }
        }

        // Optional fraction
        if (i < text.length() && text.charAt(i) == '.') {
            i++;
            if (i >= text.length() || !Character.isDigit(text.charAt(i))) {
                throw new IllegalArgumentException("Invalid number");
            }
            while (i < text.length() && Character.isDigit(text.charAt(i))) {
                i++;
            }
        }

        // Optional exponent
        if (i < text.length() && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
            i++;
            if (i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
                i++;
            }
            if (i >= text.length() || !Character.isDigit(text.charAt(i))) {
                throw new IllegalArgumentException("Invalid number");
            }
            while (i < text.length() && Character.isDigit(text.charAt(i))) {
                i++;
            }
        }

        String numStr = text.substring(start, i);
        return new ParseResult(new JSNumber(Double.parseDouble(numStr)), i);
    }

    private static ParseResult parseObject(String text, int start) {
        if (text.charAt(start) != '{') {
            throw new IllegalArgumentException("Expected '{'");
        }

        JSObject obj = new JSObject();
        int i = skipWhitespace(text, start + 1);

        // Empty object
        if (i < text.length() && text.charAt(i) == '}') {
            return new ParseResult(obj, i + 1);
        }

        while (i < text.length()) {
            // Parse key (must be string)
            ParseResult keyResult = parseString(text, i);
            String key = ((JSString) keyResult.value).getValue();
            i = skipWhitespace(text, keyResult.endIndex);

            // Expect colon
            if (i >= text.length() || text.charAt(i) != ':') {
                throw new IllegalArgumentException("Expected ':'");
            }
            i = skipWhitespace(text, i + 1);

            // Parse value
            ParseResult valueResult = parseValue(text, i);
            obj.set(key, valueResult.value);
            i = skipWhitespace(text, valueResult.endIndex);

            // Check for comma or end
            if (i >= text.length()) {
                throw new IllegalArgumentException("Unexpected end of object");
            }

            if (text.charAt(i) == '}') {
                return new ParseResult(obj, i + 1);
            }

            if (text.charAt(i) != ',') {
                throw new IllegalArgumentException("Expected ',' or '}'");
            }

            i = skipWhitespace(text, i + 1);
        }

        throw new IllegalArgumentException("Unterminated object");
    }

    private static ParseResult parseArray(String text, int start) {
        if (text.charAt(start) != '[') {
            throw new IllegalArgumentException("Expected '['");
        }

        JSArray arr = new JSArray();
        int i = skipWhitespace(text, start + 1);

        // Empty array
        if (i < text.length() && text.charAt(i) == ']') {
            return new ParseResult(arr, i + 1);
        }

        while (i < text.length()) {
            // Parse value
            ParseResult valueResult = parseValue(text, i);
            arr.push(valueResult.value);
            i = skipWhitespace(text, valueResult.endIndex);

            // Check for comma or end
            if (i >= text.length()) {
                throw new IllegalArgumentException("Unexpected end of array");
            }

            if (text.charAt(i) == ']') {
                return new ParseResult(arr, i + 1);
            }

            if (text.charAt(i) != ',') {
                throw new IllegalArgumentException("Expected ',' or ']'");
            }

            i = skipWhitespace(text, i + 1);
        }

        throw new IllegalArgumentException("Unterminated array");
    }

    private static ParseResult parseTrue(String text, int start) {
        if (text.startsWith("true", start)) {
            return new ParseResult(JSBoolean.TRUE, start + 4);
        }
        throw new IllegalArgumentException("Invalid literal");
    }

    private static ParseResult parseFalse(String text, int start) {
        if (text.startsWith("false", start)) {
            return new ParseResult(JSBoolean.FALSE, start + 5);
        }
        throw new IllegalArgumentException("Invalid literal");
    }

    private static ParseResult parseNull(String text, int start) {
        if (text.startsWith("null", start)) {
            return new ParseResult(JSNull.INSTANCE, start + 4);
        }
        throw new IllegalArgumentException("Invalid literal");
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

    // ========== JSON Stringification Helper Methods ==========

    private static String stringifyValue(JSValue value, String indent, String currentIndent) {
        if (value instanceof JSNull || value instanceof JSUndefined) {
            return "null";
        }

        if (value instanceof JSBoolean b) {
            return b.value() ? "true" : "false";
        }

        if (value instanceof JSNumber n) {
            double d = n.value();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return "null";
            }
            // Check if it's an integer
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }

        if (value instanceof JSString s) {
            return stringifyString(s.getValue());
        }

        if (value instanceof JSArray arr) {
            return stringifyArray(arr, indent, currentIndent);
        }

        if (value instanceof JSObject obj) {
            return stringifyObject(obj, indent, currentIndent);
        }

        // Functions and symbols are not serializable
        return null;
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

    private static String stringifyArray(JSArray arr, String indent, String currentIndent) {
        if (indent.isEmpty()) {
            // Compact format
            StringBuilder sb = new StringBuilder("[");
            for (long i = 0; i < arr.getLength(); i++) {
                if (i > 0) sb.append(",");
                String elem = stringifyValue(arr.get(i), indent, currentIndent);
                if (elem == null) {
                    sb.append("null");
                } else {
                    sb.append(elem);
                }
            }
            sb.append("]");
            return sb.toString();
        } else {
            // Pretty format
            StringBuilder sb = new StringBuilder("[\n");
            String newIndent = currentIndent + indent;
            for (long i = 0; i < arr.getLength(); i++) {
                if (i > 0) sb.append(",\n");
                sb.append(newIndent);
                String elem = stringifyValue(arr.get(i), indent, newIndent);
                if (elem == null) {
                    sb.append("null");
                } else {
                    sb.append(elem);
                }
            }
            sb.append("\n").append(currentIndent).append("]");
            return sb.toString();
        }
    }

    private static String stringifyObject(JSObject obj, String indent, String currentIndent) {
        // Get all own property keys
        List<PropertyKey> propertyKeys = obj.getOwnPropertyKeys();
        List<String> keys = new ArrayList<>();

        // Convert PropertyKeys to strings, filtering out non-enumerable properties
        for (PropertyKey key : propertyKeys) {
            if (key.isString()) {
                keys.add(key.asString());
            }
        }

        if (keys.isEmpty()) {
            return "{}";
        }

        if (indent.isEmpty()) {
            // Compact format
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (String key : keys) {
                if (!first) sb.append(",");
                first = false;
                sb.append(stringifyString(key));
                sb.append(":");
                JSValue val = obj.get(key);
                String valStr = stringifyValue(val, indent, currentIndent);
                if (valStr != null) {
                    sb.append(valStr);
                }
            }
            sb.append("}");
            return sb.toString();
        } else {
            // Pretty format
            StringBuilder sb = new StringBuilder("{\n");
            String newIndent = currentIndent + indent;
            boolean first = true;
            for (String key : keys) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append(newIndent);
                sb.append(stringifyString(key));
                sb.append(": ");
                JSValue val = obj.get(key);
                String valStr = stringifyValue(val, indent, newIndent);
                if (valStr != null) {
                    sb.append(valStr);
                }
            }
            sb.append("\n").append(currentIndent).append("}");
            return sb.toString();
        }
    }
}
