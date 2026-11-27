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

import com.caoccao.qjs4j.builtins.DatePrototype;
import java.util.*;
import static com.caoccao.qjs4j.core.QuotedString.*;

final class ConsoleFormatter {
    private static final Map<Class<?>, String> CLASS_NAMES = Map.ofEntries(Map.entry(JSObject.class, JSObject.NAME),
            Map.entry(JSArray.class, JSArray.NAME), Map.entry(JSArguments.class, JSArguments.NAME),
            Map.entry(JSArrayBuffer.class, JSArrayBuffer.NAME),
            Map.entry(JSSharedArrayBuffer.class, JSSharedArrayBuffer.NAME),
            Map.entry(JSDataView.class, JSDataView.NAME), Map.entry(JSBooleanObject.class, JSBooleanObject.NAME),
            Map.entry(JSBigIntObject.class, JSBigIntObject.NAME), Map.entry(JSNumberObject.class, JSNumberObject.NAME),
            Map.entry(JSStringObject.class, JSStringObject.NAME), Map.entry(JSSymbolObject.class, JSSymbolObject.NAME),
            Map.entry(JSDate.class, JSDate.NAME), Map.entry(JSError.class, JSError.NAME),
            Map.entry(JSAggregateError.class, JSAggregateError.NAME), Map.entry(JSEvalError.class, JSEvalError.NAME),
            Map.entry(JSRangeError.class, JSRangeError.NAME), Map.entry(JSReferenceError.class, JSReferenceError.NAME),
            Map.entry(JSSyntaxError.class, JSSyntaxError.NAME), Map.entry(JSTypeError.class, JSTypeError.NAME),
            Map.entry(JSURIError.class, JSURIError.NAME), Map.entry(JSSuppressedError.class, JSSuppressedError.NAME),
            Map.entry(JSFinalizationRegistry.class, JSFinalizationRegistry.NAME),
            Map.entry(JSFunction.class, JSFunction.NAME), Map.entry(JSBytecodeFunction.class, JSBytecodeFunction.NAME),
            Map.entry(JSNativeFunction.class, JSNativeFunction.NAME),
            Map.entry(JSBoundFunction.class, JSBoundFunction.NAME), Map.entry(JSClass.class, JSClass.NAME),
            Map.entry(JSGenerator.class, JSGenerator.NAME), Map.entry(JSAsyncGenerator.class, JSAsyncGenerator.NAME),
            Map.entry(JSAsyncIterator.class, JSAsyncIterator.NAME), Map.entry(JSIterator.class, JSIterator.NAME),
            Map.entry(JSMap.class, JSMap.NAME), Map.entry(JSSet.class, JSSet.NAME),
            Map.entry(JSWeakMap.class, JSWeakMap.NAME), Map.entry(JSWeakSet.class, JSWeakSet.NAME),
            Map.entry(JSWeakRef.class, JSWeakRef.NAME), Map.entry(JSPromise.class, JSPromise.NAME),
            Map.entry(JSProxy.class, JSProxy.NAME), Map.entry(JSRegExp.class, JSRegExp.NAME),
            Map.entry(JSDisposableStack.class, JSDisposableStack.NAME),
            Map.entry(JSAsyncDisposableStack.class, JSAsyncDisposableStack.NAME),
            Map.entry(JSTypedArray.class, JSTypedArray.NAME), Map.entry(JSInt8Array.class, JSInt8Array.NAME),
            Map.entry(JSInt16Array.class, JSInt16Array.NAME), Map.entry(JSInt32Array.class, JSInt32Array.NAME),
            Map.entry(JSUint8Array.class, JSUint8Array.NAME),
            Map.entry(JSUint8ClampedArray.class, JSUint8ClampedArray.NAME),
            Map.entry(JSUint16Array.class, JSUint16Array.NAME), Map.entry(JSUint32Array.class, JSUint32Array.NAME),
            Map.entry(JSBigInt64Array.class, JSBigInt64Array.NAME),
            Map.entry(JSBigUint64Array.class, JSBigUint64Array.NAME),
            Map.entry(JSFloat16Array.class, JSFloat16Array.NAME), Map.entry(JSFloat32Array.class, JSFloat32Array.NAME),
            Map.entry(JSFloat64Array.class, JSFloat64Array.NAME), Map.entry(JSIntlCollator.class, JSIntlCollator.NAME),
            Map.entry(JSIntlDateTimeFormat.class, JSIntlDateTimeFormat.NAME),
            Map.entry(JSIntlListFormat.class, JSIntlListFormat.NAME), Map.entry(JSIntlLocale.class, JSIntlLocale.NAME),
            Map.entry(JSIntlNumberFormat.class, JSIntlNumberFormat.NAME),
            Map.entry(JSIntlPluralRules.class, JSIntlPluralRules.NAME),
            Map.entry(JSIntlRelativeTimeFormat.class, JSIntlRelativeTimeFormat.NAME));
    private static final int MAX_PRINT_DEPTH = 2;
    private static final int MAX_PRINT_ITEM_COUNT = 100;
    private static final int MAX_PRINT_STRING_LENGTH = 1000;

    private final JSContext context;
    private IdentityHashMap<JSObject, Integer> printStack;

    ConsoleFormatter(JSContext context) {
        this.context = context;
    }

    String formatArgument(JSValue value) {
        return formatValue(value, true, 0);
    }

    /**
     * Format console arguments as Node's {@code util.format} does: a first argument that is a string may contain
     * {@code %s %d %i %f %j %o %O %c %%}, each taking the next argument; the arguments left over follow, separated by
     * spaces, strings as they are and anything else inspected. The format string used to be printed verbatim with every
     * argument after it.
     *
     * @param args
     *            the console arguments
     * @return the line to print
     */
    String formatArguments(JSValue[] args) {
        StringBuilder line = new StringBuilder();
        int nextArgument = 0;
        if (args.length > 0 && args[0] instanceof JSString formatString && formatString.value().indexOf('%') >= 0) {
            String format = formatString.value();
            nextArgument = 1;
            int length = format.length();
            for (int i = 0; i < length; i++) {
                char ch = format.charAt(i);
                if (ch != '%' || i + 1 >= length) {
                    line.append(ch);
                    continue;
                }
                char specifier = format.charAt(i + 1);
                if (specifier == '%') {
                    line.append('%');
                    i++;
                    continue;
                }
                if ("sdifjoOc".indexOf(specifier) < 0 || nextArgument >= args.length) {
                    line.append(ch);
                    continue;
                }
                line.append(formatSpecifier(specifier, args[nextArgument++]));
                i++;
            }
        }
        for (int i = nextArgument; i < args.length; i++) {
            if (!line.isEmpty() || i > 0) {
                line.append(' ');
            }
            line.append(formatArgument(args[i]));
        }
        return line.toString();
    }

    private String formatArray(JSArray array, int depth) {
        List<String> items = new ArrayList<>();
        long length = array.getLength();
        List<Long> indexKeys = new ArrayList<>();
        for (PropertyKey key : array.getOwnPropertyKeys()) {
            long index = key.toArrayIndex();
            if (index >= 0 && index < length) {
                indexKeys.add(index);
            }
        }
        indexKeys.sort(Long::compareTo);

        long contiguousCount = 0;
        while (contiguousCount < length && array.hasElement(contiguousCount)) {
            contiguousCount++;
        }

        boolean sparseFormat = !indexKeys.isEmpty() && indexKeys.get(indexKeys.size() - 1) >= contiguousCount;
        if (sparseFormat) {
            int maxIndexItems = Math.min(indexKeys.size(), MAX_PRINT_ITEM_COUNT);
            for (int i = 0; i < maxIndexItems; i++) {
                long index = indexKeys.get(i);
                items.add(index + ": " + formatValue(array.get(index), false, depth + 1));
            }
            if (indexKeys.size() > maxIndexItems) {
                items.add(formatMoreItems(indexKeys.size() - maxIndexItems));
            }
        } else {
            int maxDenseItems = (int) Math.min(contiguousCount, MAX_PRINT_ITEM_COUNT);
            for (int i = 0; i < maxDenseItems; i++) {
                items.add(formatValue(array.get(i), false, depth + 1));
            }
            if (contiguousCount > maxDenseItems) {
                items.add(formatMoreItems(contiguousCount - maxDenseItems));
            }
            if (length > contiguousCount) {
                items.add(formatEmptyItems(length - contiguousCount));
            }
        }

        for (PropertyKey key : array.getOwnPropertyKeys()) {
            if (key.toArrayIndex() >= 0 || "length".equals(key.asString())) {
                continue;
            }
            PropertyDescriptor descriptor = array.getOwnPropertyDescriptor(key);
            if (descriptor != null && !descriptor.isEnumerable()) {
                continue;
            }
            items.add(formatPropertyEntry(array, key, descriptor, depth + 1));
        }

        return items.isEmpty() ? "[ ]" : "[ " + String.join(", ", items) + " ]";
    }

    private String formatEmptyItems(long count) {
        return "<" + count + " empty item" + (count > 1 ? "s" : "") + ">";
    }

    private List<String> formatEnumerableProperties(JSObject object, int depth) {
        List<String> entries = new ArrayList<>();
        int enumerableCount = 0;
        for (PropertyKey key : object.getOwnPropertyKeys()) {
            PropertyDescriptor descriptor = object.getOwnPropertyDescriptor(key);
            if (descriptor != null && !descriptor.isEnumerable()) {
                continue;
            }
            enumerableCount++;
            if (entries.size() < MAX_PRINT_ITEM_COUNT) {
                entries.add(formatPropertyEntry(object, key, descriptor, depth));
            }
        }
        if (enumerableCount > MAX_PRINT_ITEM_COUNT) {
            entries.add(formatMoreItems(enumerableCount - MAX_PRINT_ITEM_COUNT));
        }
        return entries;
    }

    private String formatError(JSObject error) {
        JSValue stackValue = error.get(PropertyKey.STACK);
        // The stack begins with the error's own `Name: message` line, as V8's does.
        if (stackValue instanceof JSString stackString && !stackString.value().isEmpty()) {
            return stackString.value().stripTrailing();
        }
        JSValue nameValue = error.get(PropertyKey.NAME);
        JSValue messageValue = error.get(PropertyKey.MESSAGE);
        String name = nameValue instanceof JSString s ? s.value() : "Error";
        String message = messageValue instanceof JSString s ? s.value() : "";
        return message.isEmpty() ? name : name + ": " + message;
    }

    private String formatFunction(JSFunction function) {
        String functionName = function.getName();
        if (functionName == null || functionName.isEmpty()) {
            functionName = "(anonymous)";
        }
        return "[Function " + functionName + "]";
    }

    private String formatMap(JSMap map, int depth) {
        int size = map.size();
        if (depth >= MAX_PRINT_DEPTH) {
            return "[Map]";
        }
        List<String> items = new ArrayList<>();
        for (Map.Entry<JSMap.KeyWrapper, JSValue> entry : map.entries()) {
            if (items.size() >= MAX_PRINT_ITEM_COUNT) {
                items.add(formatMoreItems(size - items.size()));
                break;
            }
            String key = formatValue(entry.getKey().value(), false, depth + 1);
            String value = formatValue(entry.getValue(), false, depth + 1);
            items.add(key + " => " + value);
        }
        return "Map(" + size + ") " + braces(items);
    }

    private String formatMoreItems(long count) {
        return "... " + count + " more item" + (count > 1 ? "s" : "");
    }

    private String formatNumber(double value) {
        return isNegativeZero(value) ? "-0" : JSNumber.of(value).toString();
    }

    private String formatObject(JSObject object, int depth) {
        if (printStack != null) {
            Integer index = printStack.get(object);
            if (index != null) {
                return "[circular " + index + "]";
            }
        }
        if (object instanceof JSFunction function) {
            return formatFunction(function);
        }
        if (object instanceof JSTypedArray typedArray) {
            return formatTypedArray(typedArray);
        }
        if (object instanceof JSRegExp regexp) {
            return formatRegExp(regexp);
        }
        if (object instanceof JSDate) {
            JSValue isoValue = DatePrototype.toISOString(context, object, JSValue.NO_ARGS);
            if (!context.hasPendingException() && isoValue instanceof JSString jsString) {
                return jsString.value();
            }
            context.clearPendingException();
        }
        boolean collection = object instanceof JSArray || object instanceof JSMap || object instanceof JSSet;
        if (!collection && (object instanceof JSError || object.getPrototype() instanceof JSError)) {
            return formatError(object);
        }

        if (printStack == null) {
            printStack = new IdentityHashMap<>();
        }
        printStack.put(object, printStack.size());
        try {
            if (object instanceof JSArray array) {
                return formatArray(array, depth);
            }
            if (object instanceof JSMap map) {
                return formatMap(map, depth);
            }
            if (object instanceof JSSet set) {
                return formatSet(set, depth);
            }
            String className = getClassName(object);
            if (depth >= MAX_PRINT_DEPTH) {
                return "[" + className + "]";
            }
            List<String> entries = formatEnumerableProperties(object, depth + 1);

            String prefix = "Object".equals(className) ? "" : className + " ";
            return prefix + braces(entries);
        } finally {
            printStack.remove(object);
        }
    }

    private String formatPropertyEntry(JSObject object, PropertyKey key, PropertyDescriptor descriptor, int depth) {
        String keyString = formatPropertyKey(key);
        if (descriptor != null && descriptor.isAccessorDescriptor()) {
            if (descriptor.getGetter() != null && descriptor.getSetter() != null) {
                return keyString + ": [Getter/Setter]";
            }
            if (descriptor.getSetter() != null) {
                return keyString + ": [Setter]";
            }
            return keyString + ": [Getter]";
        }
        JSValue value = descriptor != null && descriptor.hasValue() ? descriptor.getValue() : object.get(key);
        return keyString + ": " + formatValue(value, false, depth);
    }

    private String formatPropertyKey(PropertyKey key) {
        if (key.isIndex()) {
            return Integer.toString(key.asIndex());
        }
        String propertyString = key.toPropertyString();
        return isAsciiIdentifier(propertyString) ? propertyString : quoteString(propertyString);
    }

    private String formatRegExp(JSRegExp regexp) {
        String pattern = regexp.getPattern();
        if (pattern.isEmpty()) {
            pattern = "(?:)";
        }

        StringBuilder builder = new StringBuilder(pattern.length() + regexp.getFlags().length() + 2);
        builder.append('/');
        boolean insideCharClass = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                builder.append(c);
                if (i + 1 < pattern.length()) {
                    builder.append(pattern.charAt(i + 1));
                    i++;
                }
                continue;
            }
            if (c == '[' && !insideCharClass) {
                insideCharClass = true;
            } else if (c == ']' && insideCharClass) {
                insideCharClass = false;
            } else if (c == '/' && !insideCharClass) {
                builder.append("\\/");
                continue;
            } else if (c == '\n') {
                builder.append("\\n");
                continue;
            } else if (c == '\r') {
                builder.append("\\r");
                continue;
            }
            builder.append(c);
        }
        builder.append('/').append(regexp.getFlags());
        return builder.toString();
    }

    private String formatSet(JSSet set, int depth) {
        int size = set.size();
        if (depth >= MAX_PRINT_DEPTH) {
            return "[Set]";
        }
        List<String> items = new ArrayList<>();
        for (JSMap.KeyWrapper wrapper : set.values()) {
            if (items.size() >= MAX_PRINT_ITEM_COUNT) {
                items.add(formatMoreItems(size - items.size()));
                break;
            }
            items.add(formatValue(wrapper.value(), false, depth + 1));
        }
        return "Set(" + size + ") " + braces(items);
    }

    private String formatSpecifier(char specifier, JSValue value) {
        return switch (specifier) {
            case 's' -> {
                if (value instanceof JSNumber number) {
                    yield formatNumber(number.value());
                }
                if (value instanceof JSBigInt bigInt) {
                    yield bigInt + "n";
                }
                if (value instanceof JSSymbol symbol) {
                    yield symbol.toJavaObject();
                }
                // typeof "object" only: a function is converted with String(), as in Node.
                if (value instanceof JSObject object && !(value instanceof JSFunction) && hasBuiltInToString(object)) {
                    yield formatValue(value, false, 0);
                }
                JSString string = JSTypeConversions.toString(context, value);
                yield context.hasPendingException() ? "" : string.value();
            }
            case 'd' -> {
                if (value instanceof JSBigInt bigInt) {
                    yield bigInt + "n";
                }
                if (value instanceof JSSymbol) {
                    yield "NaN";
                }
                double number = JSTypeConversions.toNumber(context, value).value();
                yield context.hasPendingException() ? "" : formatNumber(number);
            }
            case 'i', 'f' -> {
                if (value instanceof JSBigInt bigInt && specifier == 'i') {
                    yield bigInt + "n";
                }
                if (value instanceof JSSymbol) {
                    yield "NaN";
                }
                JSString text = JSTypeConversions.toString(context, value);
                if (context.hasPendingException()) {
                    yield "";
                }
                // Both parse a string argument, which cannot throw, into a number.
                JSValue parsed = specifier == 'i'
                        ? JSGlobalObject.GlobalFunction.parseInt(context, JSUndefined.INSTANCE, new JSValue[]{text})
                        : JSGlobalObject.GlobalFunction.parseFloat(context, JSUndefined.INSTANCE, new JSValue[]{text});
                yield formatNumber(((JSNumber) parsed).value());
            }
            case 'j' -> {
                JSValue json = context.getJSGlobalObject().getJSONObject().stringify(JSUndefined.INSTANCE,
                        new JSValue[]{value});
                if (context.hasPendingException()) {
                    context.clearPendingException();
                    yield "[Circular]";
                }
                yield json instanceof JSString jsonString ? jsonString.value() : "undefined";
            }
            case 'o' -> value instanceof JSArray array
                    ? appendHiddenLength(formatValue(value, false, 0), array.getLength())
                    : formatValue(value, false, 0);
            case 'O' -> formatValue(value, false, 0);
            default -> ""; // %c takes a CSS argument and prints nothing
        };
    }

    private String formatTypedArray(JSTypedArray typedArray) {
        String className = getClassName(typedArray);
        long length = typedArray.getLength();
        int max = (int) Math.min(length, MAX_PRINT_ITEM_COUNT);
        List<String> items = new ArrayList<>(max + 1);
        for (int i = 0; i < max; i++) {
            double element = typedArray.getElement(i);
            if (Double.isFinite(element) && element == Math.rint(element) && !isNegativeZero(element)) {
                items.add(Long.toString((long) element));
            } else {
                items.add(formatNumber(element));
            }
        }
        if (length > max) {
            items.add(formatMoreItems(length - max));
        }
        return className + "(" + length + ") " + (items.isEmpty() ? "[ ]" : "[ " + String.join(", ", items) + " ]");
    }

    String formatValue(JSValue value, boolean printTopLevelStringRaw, int depth) {
        if (value == null || value instanceof JSNull) {
            return "null";
        }
        if (value instanceof JSUndefined) {
            return "undefined";
        }
        if (value instanceof JSString jsString) {
            return printTopLevelStringRaw ? jsString.value() : quoteString(jsString.value());
        }
        if (value instanceof JSNumber number) {
            return formatNumber(number.value());
        }
        if (value instanceof JSBoolean) {
            return value.toString();
        }
        if (value instanceof JSBigInt bigInt) {
            return bigInt + "n";
        }
        if (value instanceof JSSymbol symbol) {
            return symbol.toJavaObject();
        }
        if (value instanceof JSObject object) {
            return formatObject(object, depth);
        }
        return String.valueOf(value);
    }

    private String getClassName(JSObject object) {
        return CLASS_NAMES.getOrDefault(object.getClass(), JSObject.NAME);
    }

    private boolean isAsciiIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        char first = value.charAt(0);
        if (!((first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z') || first == '_' || first == '$')) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '$')) {
                return false;
            }
        }
        return true;
    }

    private String quoteString(String value) {
        int printLength = Math.min(value.length(), MAX_PRINT_STRING_LENGTH);
        StringBuilder stringBuilder = new StringBuilder(printLength + 2);
        stringBuilder.append('"');
        for (int i = 0; i < printLength; i++) {
            char c = value.charAt(i);
            if (c >= 0x7F && c <= 0x9F) {
                appendUnicodeEscape(stringBuilder, c);
            } else {
                appendQuotedChar(stringBuilder, c);
            }
        }
        stringBuilder.append('"');
        if (value.length() > MAX_PRINT_STRING_LENGTH) {
            stringBuilder.append("... ").append(value.length() - MAX_PRINT_STRING_LENGTH).append(" more characters");
        }
        return stringBuilder.toString();
    }

    /**
     * {@code %o} shows an array's hidden {@code length}, as Node's {@code showHidden} inspection does.
     */
    private static String appendHiddenLength(String formatted, long length) {
        // An array prints on one line as "[ ... ]", or "[ ]" when empty.
        String head = formatted.substring(0, formatted.length() - 2);
        return head + (head.equals("[") ? " " : ", ") + "[length]: " + length + " ]";
    }

    private static String braces(List<String> entries) {
        return "{ " + String.join(", ", entries) + " }";
    }

    /**
     * Node's {@code hasBuiltInToString}: whether {@code %s} inspects an object rather than calling its
     * {@code toString}, because the {@code toString} it would get is a built-in one.
     */
    private static boolean hasBuiltInToString(JSObject object) {
        JSValue toString = object.get(PropertyKey.TO_STRING);
        if (!(toString instanceof JSFunction)) {
            return true;
        }
        if (object.hasOwnProperty(PropertyKey.TO_STRING)) {
            return false;
        }
        JSObject pointer = object.getPrototype();
        while (pointer != null && !pointer.hasOwnProperty(PropertyKey.TO_STRING)) {
            pointer = pointer.getPrototype();
        }
        if (pointer == null) {
            return true;
        }
        PropertyDescriptor descriptor = pointer.getOwnPropertyDescriptor(PropertyKey.CONSTRUCTOR);
        return descriptor != null && descriptor.getValue() instanceof JSNativeFunction;
    }

    private static boolean isNegativeZero(double value) {
        return value == 0 && Double.doubleToRawLongBits(value) != 0L;
    }
}
