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

package com.caoccao.qjs4j.core.json;

import com.caoccao.qjs4j.core.*;

import java.util.*;

/** Parses one JSON input and tracks primitive source text when a reviver requests it. */
final class JSONParser {
    private final JSContext context;
    // Source tracking for primitive values within structured types (arrays/objects)
    // Tracks both the source text and original parsed value per (parent, key) pair
    private final IdentityHashMap<JSObject, Map<String, SourceEntry>> elementSources;
    private final JSValue reviver;
    private final String text;

    JSONParser(JSContext context, String text, JSValue reviver) {
        this.context = context;
        this.reviver = reviver;
        this.text = text;
        elementSources = reviver != null ? new IdentityHashMap<>() : null;
    }

    /**
     * Get line and column from position (0-based position, 1-based line/column)
     */
    private String getPositionInfo(int position) {
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
     * A primitive retains its source while its current value is SameValue to the parsed value.
     */
    private String getSourceForValue(String key, JSObject holder, JSValue currentValue) {
        Map<String, SourceEntry> sources = elementSources.get(holder);
        SourceEntry entry = sources == null ? null : sources.get(key);
        return entry != null && JSObject.sameValue(entry.originalValue, currentValue) ? entry.sourceText : null;
    }

    /**
     * Recursive internalize operation for JSON.parse reviver. Based on ES2024 25.5.1.1 InternalizeJSONProperty and
     * QuickJS internalize_json_property. Now supports context.source (json-parse-with-source proposal).
     */
    private JSValue internalizeJSONProperty(JSObject holder, String name) {
        JSValue val = holder.get(PropertyKey.fromString(name));
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
                long length = JSONObject.arrayLength(context, obj);
                for (long i = 0; i < length; i++) {
                    if (!reviveProperty(obj, PropertyKey.fromIndexString(i))) {
                        return null;
                    }
                }
            } else {
                List<String> keys = JSONObject.getEnumerableStringKeys(context, obj);
                for (String key : keys) {
                    if (!reviveProperty(obj, PropertyKey.fromString(key))) {
                        return null;
                    }
                }
            }
            if (context.hasPendingException()) {
                return null;
            }
        } else {
            // For primitive values, look up the source text
            sourceText = getSourceForValue(name, holder, val);
        }

        // Call reviver function with (key, value, context)
        JSValue nameValue = new JSString(name);
        JSObject contextObj = context.createJSObject();
        if (sourceText != null) {
            contextObj.createDataProperty(PropertyKey.fromString("source"), new JSString(sourceText));
        }
        return JSCall.call(context, reviver, holder, nameValue, val, contextObj);
    }

    boolean isCompleteValue() {
        return skipWhitespace(parseValue().endIndex) == text.length();
    }

    JSValue parse() {
        ParseResult result = parseValue();
        int end = skipWhitespace(result.endIndex);
        if (end != text.length()) {
            throw new JSONParseException("Unexpected data after JSON " + getPositionInfo(end));
        }
        if (reviver == null) {
            return result.value;
        }
        JSObject root = context.createJSObject();
        root.createDataProperty(PropertyKey.fromString(""), result.value);
        recordElementSource(root, "", result);
        JSValue revived = internalizeJSONProperty(root, "");
        return revived == null || context.hasPendingException() ? JSUndefined.INSTANCE : revived;
    }

    private int parseHex4(int offset) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            context.checkExecutionInterrupt();
            int digit = Character.digit(text.charAt(offset + i), 16);
            if (digit < 0) {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    private ParseResult parseLiteral(int start, String literal, JSValue value) {
        if (!text.startsWith(literal, start)) {
            throw new JSONParseException("Invalid literal " + getPositionInfo(start));
        }
        return new ParseResult(value, start + literal.length(), start);
    }

    private ParseResult parseNumber(int start) {
        int i = start;

        // Optional minus
        if (i < text.length() && text.charAt(i) == '-') {
            i++;
        }

        // Digits
        if (i >= text.length() || !Character.isDigit(text.charAt(i))) {
            throw new JSONParseException("Invalid number in JSON " + getPositionInfo(i));
        }

        // Integer part
        if (text.charAt(i) == '0') {
            i++;
            // Leading zero must not be followed by another digit
            if (i < text.length() && Character.isDigit(text.charAt(i))) {
                throw new JSONParseException("Unexpected number in JSON " + getPositionInfo(i));
            }
        } else {
            while (i < text.length() && Character.isDigit(text.charAt(i))) {
                context.checkExecutionInterrupt();
                i++;
            }
        }

        // Optional fraction
        if (i < text.length() && text.charAt(i) == '.') {
            i++;
            i = readNumberDigits(i);
        }

        // Optional exponent
        if (i < text.length() && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
            i++;
            if (i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
                i++;
            }
            i = readNumberDigits(i);
        }

        String numStr = text.substring(start, i);
        return new ParseResult(JSNumber.of(Double.parseDouble(numStr)), i, start);
    }

    /**
     * Parse an object's property name and the colon after it.
     *
     * @return the index of the value
     */
    private int parseObjectKey(ParseFrame frame, int start) {
        ParseResult keyResult = parsePropertyName(start, frame.count);
        frame.key = ((JSString) keyResult.value).value();
        int i = skipWhitespace(keyResult.endIndex);
        if (i >= text.length() || text.charAt(i) != ':') {
            throw new JSONParseException("Expected ':' after property name in JSON " + getPositionInfo(i));
        }
        return skipWhitespace(i + 1);
    }

    private ParseResult parsePropertyName(int start, int propertyCount) {
        char firstChar = text.charAt(start);
        if (firstChar != '"') {
            if (propertyCount > 0) {
                throw new JSONParseException("Expected double-quoted property name in JSON " + getPositionInfo(start));
            } else {
                throw new JSONParseException("Expected property name or '}' in JSON " + getPositionInfo(start));
            }
        }

        return parseQuotedString(start, true);
    }

    private ParseResult parseQuotedString(int start, boolean propertyName) {
        StringBuilder sb = null;
        int segmentStart = start + 1;
        int i = start + 1;

        while (i < text.length()) {

            context.checkExecutionInterrupt();
            char ch = text.charAt(i);

            if (ch == '"') {
                String decoded = sb == null
                        ? text.substring(segmentStart, i)
                        : sb.append(text, segmentStart, i).toString();
                JSString value = new JSString(decoded);
                return propertyName ? new ParseResult(value, i + 1) : new ParseResult(value, i + 1, start);
            }

            if (ch == '\\') {
                if (sb == null) {
                    sb = new StringBuilder();
                }
                sb.append(text, segmentStart, i);
                i++;
                if (i >= text.length()) {
                    throw new JSONParseException(
                            (propertyName ? "Unexpected end in property name " : "Unexpected end in string ")
                                    + getPositionInfo(i));
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
                        int codePoint = i + 4 >= text.length() ? -1 : parseHex4(i + 1);
                        if (codePoint < 0) {
                            throw new JSONParseException((propertyName
                                    ? "Invalid unicode escape in property name "
                                    : "Invalid unicode escape ") + getPositionInfo(i));
                        }
                        sb.append((char) codePoint);
                        i += 4;
                    }
                    default -> throw new JSONParseException(
                            (propertyName ? "Invalid escape in property name: " : "Invalid escape: ") + "\\" + escaped
                                    + getPositionInfo(i));
                }
                segmentStart = i + 1;
            } else if (ch <= 0x1F) {
                throw new JSONParseException("Bad control character in string literal in JSON " + getPositionInfo(i));
            }
            i++;
        }

        throw new JSONParseException(
                (propertyName ? "Unterminated property name in JSON " : "Unterminated string in JSON ")
                        + getPositionInfo(i));
    }

    /**
     * Parse a JSON value without recursion: arrays and objects being filled are kept on an explicit stack, so the
     * nesting depth costs heap rather than Java stack. The parser used to recurse once per level, and
     * {@code JSON.parse("[".repeat(100000) + "]".repeat(100000))}, which V8 parses, overflowed into a
     * {@code RangeError} — or into a raw {@link StackOverflowError} on a thread with a small stack.
     */
    private ParseResult parseValue() {

        int length = text.length();
        Deque<ParseFrame> stack = new ArrayDeque<>();
        int i = 0;
        while (true) {
            context.checkExecutionInterrupt();
            // Read one value; an array or object that is not empty is opened instead, and read element by element.
            i = skipWhitespace(i);
            if (i >= length) {
                throw new JSONParseException("Unexpected end of JSON input");
            }
            char ch = text.charAt(i);
            ParseResult completed;
            if (ch == '[' || ch == '{') {
                boolean isArray = ch == '[';
                JSObject container = isArray ? context.createJSArray() : context.createJSObject();
                int next = skipWhitespace(i + 1);
                if (next < length && text.charAt(next) == (isArray ? ']' : '}')) {

                    completed = new ParseResult(container, next + 1);
                } else {
                    if (next >= length) {
                        throw new JSONParseException(
                                (isArray ? "Unterminated array in JSON " : "Unterminated object in JSON ")
                                        + getPositionInfo(next));
                    }
                    ParseFrame frame = new ParseFrame(container);
                    stack.push(frame);
                    i = isArray ? next : parseObjectKey(frame, next);
                    continue;
                }
            } else {
                completed = switch (ch) {
                    case '"' -> parseQuotedString(i, false);
                    case 't' -> parseLiteral(i, "true", JSBoolean.TRUE);
                    case 'f' -> parseLiteral(i, "false", JSBoolean.FALSE);
                    case 'n' -> parseLiteral(i, "null", JSNull.INSTANCE);
                    case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber(i);
                    default -> throw new JSONParseException("Unexpected character: " + ch + getPositionInfo(i));
                };
            }
            // Hand the completed value to the containers it closes, innermost first.
            while (true) {
                context.checkExecutionInterrupt();
                ParseFrame frame = stack.peek();
                if (frame == null) {
                    return completed;
                }
                boolean isArray = frame.container instanceof JSArray;
                if (isArray) {
                    JSArray array = (JSArray) frame.container;
                    array.push(completed.value);
                    if (elementSources != null && completed.sourceStart >= 0) {
                        recordElementSource(array, String.valueOf(frame.count), completed);
                    }
                } else {
                    // CreateDataProperty, not [[Set]], so __proto__ is an ordinary property.
                    frame.container.createDataProperty(PropertyKey.fromString(frame.key), completed.value);
                    recordElementSource(frame.container, frame.key, completed);
                }
                frame.count++;
                i = skipWhitespace(completed.endIndex);
                String expected = isArray
                        ? "Expected ',' or ']' after array element in JSON "
                        : "Expected ',' or '}' after property value in JSON ";
                if (i >= length) {
                    throw new JSONParseException(expected + getPositionInfo(i));
                }
                if (text.charAt(i) == (isArray ? ']' : '}')) {

                    stack.pop();
                    completed = new ParseResult(frame.container, i + 1);
                    continue;
                }
                if (text.charAt(i) != ',') {
                    throw new JSONParseException(expected + getPositionInfo(i));
                }
                i = skipWhitespace(i + 1);
                if (i >= length) {
                    throw new JSONParseException(
                            (isArray ? "Unterminated array in JSON " : "Unterminated object in JSON ")
                                    + getPositionInfo(i));
                }
                if (!isArray) {
                    i = parseObjectKey(frame, i);
                }
                break;
            }
        }
    }

    private int readNumberDigits(int i) {
        if (i >= text.length() || !Character.isDigit(text.charAt(i))) {
            throw new JSONParseException("Invalid number in JSON " + getPositionInfo(i));
        }
        while (i < text.length() && Character.isDigit(text.charAt(i))) {
            context.checkExecutionInterrupt();
            i++;
        }
        return i;
    }

    private void recordElementSource(JSObject parent, String key, ParseResult result) {
        // Structured types (objects/arrays) have sourceStart=-1, skip them
        if (elementSources == null || result.sourceStart < 0) {
            return;
        }
        elementSources.computeIfAbsent(parent, k -> new HashMap<>()).put(key,
                new SourceEntry(text.substring(result.sourceStart, result.endIndex), result.value));
    }

    private boolean reviveProperty(JSObject holder, PropertyKey key) {
        context.checkExecutionInterrupt();
        if (context.hasPendingException()) {
            return false;
        }
        JSValue result = internalizeJSONProperty(holder, key.toPropertyString());
        if (context.hasPendingException()) {
            return false;
        }
        // InternalizeJSONProperty ignores a false result from [[Delete]] or CreateDataProperty.
        if (result instanceof JSUndefined) {
            holder.delete(key, false);
        } else {
            holder.createDataProperty(key, result);
        }
        return !context.hasPendingException();
    }

    private int skipWhitespace(int start) {
        int i = start;
        while (i < text.length()) {
            context.checkExecutionInterrupt();
            char ch = text.charAt(i);
            if (isWhitespace(ch)) {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    /**
     * Check if a char is a JSON whitespace character (tab, newline, carriage return, space).
     */
    static boolean isWhitespace(char ch) {
        return ch == 0x0009 || ch == 0x000A || ch == 0x000D || ch == 0x0020;
    }

    /**
     * Custom exception for JSON parsing errors with position information
     */
    static final class JSONParseException extends RuntimeException {
        JSONParseException(String message) {
            super(message);
        }
    }

    /**
     * An array or object the parser is filling: the number of completed members and the pending object key.
     */
    private static final class ParseFrame {
        private final JSObject container;
        private int count;
        private String key;

        private ParseFrame(JSObject container) {
            this.container = container;
        }
    }

    private record ParseResult(JSValue value, int endIndex, int sourceStart) {
        ParseResult(JSValue value, int endIndex) {
            this(value, endIndex, -1);
        }

    }
    /**
     * Tracks the source text and original parsed value for a property. Used to detect when a reviver has modified a
     * value (source becomes undefined).
     */
    private record SourceEntry(String sourceText, JSValue originalValue) {
    }
}
