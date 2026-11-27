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
 * Implementation of JavaScript JSON object. Based on ES2024 JSON specification and QuickJS implementation.
 */
public final class JSONObject {
    private final JSContext context;

    /**
     * Internal marker for rawJSON objects using weak-key tracking. This avoids exposing marker properties while
     * allowing GC cleanup.
     */
    private final Map<JSObject, Boolean> rawJSONObjects = new WeakHashMap<>();

    public JSONObject(JSContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    private void convertJSException(JSException e) {
        if (e.getErrorValue() != null) {
            context.setPendingException(e.getErrorValue());
        } else if (!context.hasPendingException()) {
            context.throwError(e.getMessage() != null ? e.getMessage() : "Unhandled exception");
        }
    }

    /**
     * JSON.isRawJSON(O) ES2024 proposal: json-parse-with-source Checks if O has [[IsRawJSON]] internal slot.
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
    boolean isRawJSONObject(JSObject obj) {
        return rawJSONObjects.containsKey(obj);
    }

    /**
     * JSON.parse(text[, reviver]) ES2024 25.5.1 Parses JSON text with optional reviver function support. Supports
     * json-parse-with-source (context.source).
     */
    public JSValue parse(JSValue thisArg, JSValue[] args) {
        try {
            return parseText(args);
        } catch (JSVirtualMachineException e) {
            context.restoreVMException(e);
        } catch (JSException e) {
            convertJSException(e);
        }
        return JSUndefined.INSTANCE;
    }

    private JSValue parseText(JSValue[] args) {
        if (args.length == 0) {
            return context.throwSyntaxError("\"undefined\" is not valid JSON");
        }

        JSValue source = args[0];
        JSString textString = JSTypeConversions.toString(context, source);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        String text = textString.value();

        JSValue reviver = args.length > 1 && JSTypeChecking.isCallable(args[1]) ? args[1] : null;
        try {
            return new JSONParser(context, text, reviver).parse();
        } catch (JSONParser.JSONParseException e) {
            return context.throwSyntaxError(e.getMessage());
        }
    }

    /**
     * JSON.rawJSON(text) ES2024 proposal: json-parse-with-source Creates a frozen null-prototype object with
     * [[IsRawJSON]] internal slot.
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
            context.restoreVMException(e);
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
        if (JSONParser.isWhitespace(firstChar) || JSONParser.isWhitespace(lastChar)) {
            return throwRawJSONUnexpectedToken(firstChar, text);
        }

        // Step 3: Parse jsonString and validate it is a single JSON value (not object/array)
        try {
            char ch = text.charAt(0);
            // Must not be an object or array
            if (ch == '{' || ch == '[') {
                return throwRawJSONUnexpectedToken(ch, text);
            }
            if (!new JSONParser(context, text, null).isCompleteValue()) {
                return throwRawJSONInvalidValue();
            }
        } catch (JSONParser.JSONParseException e) {
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

    /**
     * JSON.stringify(value[, replacer[, space]]) ES2024 25.5.2 Based on QuickJS JS_JSONStringify.
     */
    public JSValue stringify(JSValue thisArg, JSValue[] args) {
        try {
            return args.length == 0 ? JSUndefined.INSTANCE : new JSONStringifier(context, this).stringify(args);
        } catch (JSVirtualMachineException e) {
            context.restoreVMException(e);
        } catch (JSException e) {
            convertJSException(e);
        }
        return JSUndefined.INSTANCE;
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

    static long arrayLength(JSContext context, JSObject array) {
        JSValue value = array.get(PropertyKey.LENGTH);
        return context.hasPendingException() ? 0 : JSTypeConversions.toLength(context, value);
    }

    /**
     * Get enumerable own string property names from an object. Uses getOwnPropertyKeys() which goes through proxy
     * ownKeys trap, then filters by enumerability using getOwnPropertyDescriptor() which goes through proxy
     * getOwnPropertyDescriptor trap. This follows ES2024 EnumerableOwnPropertyNames.
     */
    static List<String> getEnumerableStringKeys(JSContext context, JSObject obj) {
        List<String> keys = new ArrayList<>();
        List<PropertyKey> ownKeys = obj.getOwnPropertyKeys();
        for (PropertyKey key : ownKeys) {
            context.getVirtualMachine().checkExecutionInterruptForNative();
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

}
