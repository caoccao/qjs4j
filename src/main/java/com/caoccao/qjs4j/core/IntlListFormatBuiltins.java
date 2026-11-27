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

import java.util.*;
import static com.caoccao.qjs4j.core.JSIntlObject.*;

/** Intl.ListFormat construction and formatting of iterable string lists. */
final class IntlListFormatBuiltins {
    private IntlListFormatBuiltins() {
    }

    static JSValue createListFormat(JSContext context, JSObject prototype, JSValue[] args) {
        try {
            Locale locale = resolveLocale(context, args, 0);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // GetOptionsObject: validate options argument
            JSValue optionsArg = JSValue.argumentOrUndefined(args, 1);
            JSValue optionsObj = IntlFormatOptions.getOptionsObject(context, optionsArg);
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            // Read options in spec order: localeMatcher, type, style
            JSValue matcherError = IntlFormatOptions.localeMatcherError(context, optionsObj, "Intl.ListFormat");
            if (matcherError != null) {
                return matcherError;
            }

            String type = IntlFormatOptions.normalizeOption(
                    IntlFormatOptions.readStringOption(context, optionsObj, "type"), "conjunction", "conjunction",
                    "disjunction", "unit");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            String style = IntlFormatOptions.normalizeOption(
                    IntlFormatOptions.readStringOption(context, optionsObj, "style"), "long", "long", "short",
                    "narrow");
            if (context.hasPendingException()) {
                return JSUndefined.INSTANCE;
            }

            JSIntlListFormat listFormat = new JSIntlListFormat(context, locale, style, type);
            JSObject resolvedPrototype = IntlFormatOptions.resolveIntlPrototype(context, prototype, "ListFormat");
            if (context.hasPendingException()) {
                return context.getPendingException();
            }
            listFormat.setPrototype(resolvedPrototype);
            return listFormat;
        } catch (IllegalArgumentException e) {
            return context.throwRangeError(e.getMessage());
        }
    }

    private static JSValue format(JSContext context, JSValue thisArg, JSValue[] args, boolean toParts) {
        if (!(thisArg instanceof JSIntlListFormat listFormat)) {
            String method = toParts ? "formatToParts" : "format";
            return context.throwTypeError("Intl.ListFormat.prototype." + method + " called on incompatible receiver");
        }
        JSValue listArg = JSValue.argumentOrUndefined(args, 0);
        List<String> values = stringListFromIterable(context, listArg);
        if (context.hasPendingException()) {
            return JSUndefined.INSTANCE;
        }
        if (!toParts) {
            return new JSString(listFormat.format(values));
        }
        List<JSIntlDurationFormat.ListFormatPart> parts = JSIntlDurationFormat.formatListToParts(listFormat, values);
        JSArray result = context.createJSArray();
        for (int i = 0; i < parts.size(); i++) {
            JSIntlDurationFormat.ListFormatPart part = parts.get(i);
            JSObject partObj = createPartObject(context, part.type(), part.value());
            result.set(i, partObj);
        }
        return result;
    }

    static JSValue listFormatFormat(JSContext context, JSValue thisArg, JSValue[] args) {
        return format(context, thisArg, args, false);
    }

    static JSValue listFormatFormatToParts(JSContext context, JSValue thisArg, JSValue[] args) {
        return format(context, thisArg, args, true);
    }

    static JSValue listFormatResolvedOptions(JSContext context, JSValue thisArg, JSValue[] args) {
        if (!(thisArg instanceof JSIntlListFormat listFormat)) {
            return context.throwTypeError("Intl.ListFormat.prototype.resolvedOptions called on incompatible receiver");
        }
        JSObject resolvedOptions = context.createJSObject();
        // Use CreateDataProperty (defineProperty) instead of [[Set]] to avoid
        // triggering getter-only accessors on Object.prototype (per spec §9.1.6.3)
        createDataProperty(resolvedOptions, "locale", new JSString(listFormat.getLocale().toLanguageTag()));
        createDataProperty(resolvedOptions, "type", new JSString(listFormat.getType()));
        createDataProperty(resolvedOptions, "style", new JSString(listFormat.getStyle()));
        return resolvedOptions;
    }

    /**
     * StringListFromIterable (ECMA-402 §13.5.1). Converts an iterable to a list of strings, throwing TypeError for
     * non-string elements.
     */
    private static List<String> stringListFromIterable(JSContext context, JSValue list) {
        List<String> result = new ArrayList<>();
        if (list == null || list instanceof JSUndefined) {
            return result;
        }

        // Get the iterator
        if (list instanceof JSString jsString) {
            // Strings are iterable character by character
            String s = jsString.value();
            for (int i = 0; i < s.length();) {
                int cp = s.codePointAt(i);
                result.add(new String(Character.toChars(cp)));
                i += Character.charCount(cp);
            }
            return result;
        }

        if (!(list instanceof JSObject listObj)) {
            context.throwTypeError(JSTypeConversions.toString(context, list).value() + " is not iterable");
            return result;
        }

        // Get [Symbol.iterator]
        JSValue iteratorMethod = listObj.get(PropertyKey.SYMBOL_ITERATOR);
        if (context.hasPendingException()) {
            return result;
        }
        if (!(iteratorMethod instanceof JSFunction iteratorFunc)) {
            context.throwTypeError("object is not iterable");
            return result;
        }

        // Call the iterator method
        JSValue iterator = iteratorFunc.call(context, list, JSValue.NO_ARGS);
        if (context.hasPendingException()) {
            return result;
        }
        if (!(iterator instanceof JSObject iteratorObj)) {
            context.throwTypeError("iterator must return an object");
            return result;
        }

        // Get next method
        JSValue nextMethod = iteratorObj.get(PropertyKey.NEXT);
        if (context.hasPendingException()) {
            return result;
        }
        if (!(nextMethod instanceof JSFunction nextFunc)) {
            context.throwTypeError("iterator next is not a function");
            return result;
        }

        // Iterate
        while (true) {
            JSValue nextResult = nextFunc.call(context, iterator, JSValue.NO_ARGS);
            if (context.hasPendingException()) {
                return result;
            }
            if (!(nextResult instanceof JSObject nextResultObj)) {
                context.throwTypeError("iterator result is not an object");
                return result;
            }

            JSValue doneValue = nextResultObj.get(PropertyKey.DONE);
            if (context.hasPendingException()) {
                return result;
            }
            if (JSTypeConversions.toBoolean(doneValue) == JSBoolean.TRUE) {
                break;
            }

            JSValue value = nextResultObj.get(PropertyKey.VALUE);
            if (context.hasPendingException()) {
                return result;
            }

            // Per spec, each element must be a string
            if (!(value instanceof JSString)) {
                // IteratorClose: call return() if it exists
                JSValue returnMethod = iteratorObj.get(PropertyKey.RETURN);
                if (returnMethod instanceof JSFunction returnFunc) {
                    returnFunc.call(context, iterator, JSValue.NO_ARGS);
                }
                context.throwTypeError("Iterable yielded " + JSTypeConversions.toString(context, value).value()
                        + " which is not a string");
                return result;
            }
            result.add(((JSString) value).value());
        }
        return result;
    }
}
