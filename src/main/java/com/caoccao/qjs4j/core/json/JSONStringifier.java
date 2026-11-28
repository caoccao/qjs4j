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

/** A single JSON.stringify invocation, including its replacer and active object path. */
final class JSONStringifier {
    private static final PropertyKey ROOT = PropertyKey.fromString("");
    private static final JSString ROOT_NAME = JSString.EMPTY;
    private static final PropertyKey TO_JSON = PropertyKey.fromString("toJSON");
    private final JSContext context;
    private List<CycleEntry> cyclePath;
    private String gap;
    private final JSONObject json;
    private List<String> propertyList;
    private JSValue replacerFunc;
    private StringBuilder sb;
    private IdentityHashMap<JSValue, Boolean> stack;

    JSONStringifier(JSContext context, JSONObject json) {
        this.context = context;
        this.json = json;
    }

    private void appendIndent(String indent) {
        if (!gap.isEmpty()) {
            sb.append('\n').append(indent);
        }
    }

    /**
     * Escape a string for JSON output. Based on QuickJS JS_ToQuotedString. Escapes lone surrogates (0xD800-0xDFFF) as
     * unicode escape sequences.
     */
    private void appendQuotedString(String str) {
        sb.append('"');
        int len = str.length();
        for (int i = 0; i < len; i++) {
            context.checkExecutionInterrupt();
            char ch = str.charAt(i);
            if (Character.isHighSurrogate(ch) && i + 1 < len && Character.isLowSurrogate(str.charAt(i + 1))) {
                sb.append(ch).append(str.charAt(++i));
            } else if (Character.isSurrogate(ch)) {
                QuotedString.appendUnicodeEscape(sb, ch);
            } else {
                QuotedString.appendQuotedChar(sb, ch);
            }
        }
        sb.append("\"");
    }

    /**
     * Build a V8-compatible circular reference error message. Format: "Converting circular structure to JSON\n -->
     * starting at object with constructor 'X'\n --- property 'y' closes the circle"
     */
    private String buildCircularReferenceMessage(JSValue cycleTo, String closingKey) {
        // Find where the cycle starts in the path
        int cycleStart = -1;
        for (int i = 0; i < cyclePath.size(); i++) {
            context.checkExecutionInterrupt();
            if (cyclePath.get(i).obj == cycleTo) {
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
            for (int i = cycleStart + 1; i < cyclePath.size(); i++) {
                context.checkExecutionInterrupt();
                CycleEntry entry = cyclePath.get(i);
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

    private String indentation(JSValue space) {
        JSValue value = unboxStringOrNumber(space);
        if (value instanceof JSNumber num) {
            return " ".repeat(Math.min(10, Math.max(0, (int) num.value())));
        }
        if (value instanceof JSString str) {
            return str.value().substring(0, Math.min(10, str.value().length()));
        }
        return "";
    }

    private boolean isNumericKey(String key) {
        if (key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            context.checkExecutionInterrupt();
            if (!Character.isDigit(key.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check and transform value according to toJSON and replacer. Based on QuickJS js_json_check. Returns the processed
     * value, or null on exception.
     */
    private JSValue jsonCheck(JSValue holder, JSValue value, JSValue key) {
        if ((value instanceof JSObject || value instanceof JSBigInt) && !JSTypeChecking.isCallable(value)) {
            JSValue toJSON;
            if (value instanceof JSObject object) {
                toJSON = object.get(TO_JSON);
            } else {
                // Use the primitive BigInt as receiver for an inherited getter.
                JSObject boxed = JSTypeConversions.toObject(context, value);
                toJSON = boxed == null ? null : boxed.get(TO_JSON, value);
            }
            if (context.hasPendingException()) {
                return null;
            }
            if (JSTypeChecking.isCallable(toJSON)) {
                value = JSCall.call(context, toJSON, value, new JSValue[]{key});
                if (value == null) {
                    return null;
                }
            }
        }

        // Apply replacer function
        if (replacerFunc != null) {
            value = JSCall.call(context, replacerFunc, holder, new JSValue[]{key, value});
            if (value == null) {
                return null;
            }
        }

        // Determine what to keep based on the tag
        // Following QuickJS js_json_check: keep objects (non-function), strings, numbers, booleans, null, bigint
        if (value instanceof JSObject) {
            if (JSTypeChecking.isCallable(value)) {
                return JSUndefined.INSTANCE;
            }
            return value;
        }
        if (value instanceof JSString || value instanceof JSNumber || value instanceof JSBoolean
                || value instanceof JSNull) {
            return value;
        }
        if (value instanceof JSBigInt) {
            return value;
        }
        // Symbols, undefined, etc. become undefined
        return JSUndefined.INSTANCE;
    }

    /**
     * Convert value to JSON string representation. Based on QuickJS js_json_to_str.
     */
    private boolean jsonToStr(JSValue jsValue, String currentIndent, String keyInParent) {

        // Handle wrapper objects: unwrap using proper ToNumber/ToString/etc.
        if (jsValue instanceof JSObject) {
            jsValue = unboxStringOrNumber(jsValue);
            if (context.hasPendingException()) {
                return false;
            }
            if (jsValue instanceof JSBooleanObject boolObj) {
                jsValue = boolObj.getPrimitiveValue();
            } else if (jsValue instanceof JSBigIntObject bigIntObj) {
                jsValue = bigIntObj.getPrimitiveValue();
            }
        }

        // Handle rawJSON objects
        if (jsValue instanceof JSObject rawObj && json.isRawJSONObject(rawObj)) {
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
            } else if (doubleValue == Math.floor(doubleValue) && !Double.isInfinite(doubleValue)
                    && Math.abs(doubleValue) < (1L << 53)) {
                sb.append((long) doubleValue);
            } else {
                sb.append(jsNumber);
            }
            return true;
        }

        if (jsValue instanceof JSString jsString) {
            appendQuotedString(jsString.value());
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
            if (stack.put(jsValue, Boolean.TRUE) != null) {
                context.throwTypeError(buildCircularReferenceMessage(jsValue, keyInParent));
                return false;
            }

            cyclePath.add(new CycleEntry(keyInParent, jsValue));

            String newIndent = gap.isEmpty() ? currentIndent : currentIndent + gap;

            // Use IsArray with proxy unwrapping
            int isArray = JSTypeChecking.isArray(context, jsValue);
            boolean result = isArray >= 0 && (isArray == 1
                    ? writeArray((JSObject) jsValue, currentIndent, newIndent)
                    : writeObject((JSObject) jsValue, currentIndent, newIndent));

            stack.remove(jsValue);
            cyclePath.remove(cyclePath.size() - 1);
            return result;
        }

        // Anything else returns false (undefined behavior)
        return false;
    }

    private JSValue readProperty(JSObject holder, PropertyKey key, JSString name) {
        JSValue value = holder.get(key);
        if (context.hasPendingException()) {
            return null;
        }
        return jsonCheck(holder, value == null ? JSUndefined.INSTANCE : value, name);
    }

    private List<String> replacerProperties(JSObject replacer) {
        Set<String> keys = new LinkedHashSet<>();
        long length = JSONObject.arrayLength(context, replacer);
        for (long i = 0; i < length && !context.hasPendingException(); i++) {
            context.checkExecutionInterrupt();
            JSValue item = replacer.get(PropertyKey.fromIndexString(i));
            if (context.hasPendingException()) {
                break;
            }
            if (item instanceof JSString str) {
                keys.add(str.value());
            } else if (item instanceof JSNumber || item instanceof JSStringObject || item instanceof JSNumberObject) {
                JSString key = JSTypeConversions.toString(context, item);
                if (!context.hasPendingException()) {
                    keys.add(key.value());
                }
            }
        }
        return new ArrayList<>(keys);
    }

    JSValue stringify(JSValue[] args) {
        JSValue value = args[0];

        if (args.length > 1) {
            JSValue replacer = args[1];
            if (JSTypeChecking.isCallable(replacer)) {
                replacerFunc = replacer;
            } else if (replacer instanceof JSObject obj && JSTypeChecking.isArray(context, obj) == 1) {
                propertyList = replacerProperties(obj);
            }
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }
        }
        gap = args.length > 2 ? indentation(args[2]) : "";
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        // Only a callable replacer can observe the root holder as its receiver.
        JSValue holder = JSUndefined.INSTANCE;
        if (replacerFunc != null) {
            JSObject root = context.createJSObject();
            root.defineProperty(ROOT, value, PropertyDescriptor.DataState.All);
            holder = root;
        }
        JSValue processedValue = jsonCheck(holder, value, ROOT_NAME);
        if (processedValue == null || context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }

        if (processedValue instanceof JSUndefined) {
            return JSUndefined.INSTANCE;
        }

        sb = new StringBuilder();
        stack = new IdentityHashMap<>();
        cyclePath = new ArrayList<>();
        if (jsonToStr(processedValue, "", "")) {
            return new JSString(sb.toString());
        }
        return JSUndefined.INSTANCE;
    }

    private JSValue unboxStringOrNumber(JSValue value) {
        if (value instanceof JSStringObject) {
            return JSTypeConversions.toString(context, value);
        }
        if (value instanceof JSNumberObject) {
            return JSTypeConversions.toNumber(context, value);
        }
        return value;
    }

    /**
     * Stringify array with context. Based on QuickJS array branch in js_json_to_str.
     */
    private boolean writeArray(JSObject array, String currentIndent, String newIndent) {
        sb.append('[');
        long arrayLength = JSONObject.arrayLength(context, array);
        if (context.hasPendingException()) {
            return false;
        }

        for (long i = 0; i < arrayLength; i++) {

            context.checkExecutionInterrupt();
            if (i > 0) {
                sb.append(',');
            }

            appendIndent(newIndent);

            JSString indexKey = new JSString(String.valueOf(i));
            JSValue processedElem = readProperty(array, PropertyKey.fromIndex(i), indexKey);
            if (processedElem == null || context.hasPendingException()) {
                return false;
            }

            if (processedElem instanceof JSUndefined) {
                sb.append("null");
            } else {
                if (!jsonToStr(processedElem, newIndent, indexKey.value())) {
                    if (context.hasPendingException()) {
                        return false;
                    }
                    sb.append("null");
                }
            }
        }

        if (arrayLength > 0) {
            appendIndent(currentIndent);
        }
        sb.append(']');
        return true;
    }

    /**
     * Stringify object with context. Based on QuickJS object branch in js_json_to_str.
     */
    private boolean writeObject(JSObject object, String currentIndent, String newIndent) {
        sb.append('{');
        boolean hasContent = false;

        // Determine which properties to stringify
        List<String> keys;
        if (propertyList != null) {
            keys = propertyList;
        } else {
            keys = JSONObject.getEnumerableStringKeys(context, object);
            if (context.hasPendingException()) {
                return false;
            }
        }

        for (String key : keys) {

            context.checkExecutionInterrupt();
            JSValue processedValue = readProperty(object, PropertyKey.fromString(key), new JSString(key));
            if (processedValue == null || context.hasPendingException()) {
                return false;
            }

            if (processedValue instanceof JSUndefined) {
                continue;
            }
            int start = sb.length();
            if (hasContent) {
                sb.append(',');
            }
            appendIndent(newIndent);
            appendQuotedString(key);
            sb.append(gap.isEmpty() ? ":" : ": ");
            if (jsonToStr(processedValue, newIndent, key)) {
                hasContent = true;
            } else if (context.hasPendingException()) {
                return false;
            } else {
                sb.setLength(start);
            }
        }

        if (hasContent) {
            appendIndent(currentIndent);
        }
        sb.append('}');
        return true;
    }

    /**
     * Get the constructor name for a value (for error messages). Returns 'Object' for plain objects, 'Array' for
     * arrays.
     */
    private static String getConstructorName(JSValue val) {
        if (val instanceof JSArray) {
            return "Array";
        }
        return "Object";
    }

    /**
     * Entry in the circular reference detection stack, tracking the property key and object for V8-compatible error
     * messages.
     */
    private record CycleEntry(String key, JSValue obj) {
    }

}
