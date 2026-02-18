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

import java.io.PrintStream;
import java.util.*;

/**
 * Implementation of the JavaScript console API.
 * Supports configurable output streams for stdout and stderr.
 */
public final class JSConsole {
    private static final int MAX_PRINT_DEPTH = 2;
    private static final int MAX_PRINT_ITEM_COUNT = 100;
    private static final int MAX_PRINT_STRING_LENGTH = 1000;
    private final Map<String, Integer> counters = new HashMap<>();
    private final Map<String, Long> timers = new HashMap<>();
    private PrintStream err;
    private int groupDepth = 0;
    private PrintStream out;

    public JSConsole() {
        this.err = System.err;
        this.out = System.out;
    }

    /**
     * console.assert(condition, ...args)
     * If condition is falsy, print "Assertion failed:" followed by remaining args.
     */
    public JSValue assert_(JSContext context, JSValue thisArg, JSValue[] args) {
        boolean condition = args.length > 0 && JSTypeConversions.toBoolean(args[0]).value();
        if (!condition) {
            err.print(getGroupIndent());
            err.print("Assertion failed:");
            for (int i = 1; i < args.length; i++) {
                err.print(" ");
                err.print(formatArgument(context, args[i]));
            }
            err.println();
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * console.clear()
     * No-op in non-interactive environments.
     */
    public JSValue clear(JSContext context, JSValue thisArg, JSValue[] args) {
        return JSUndefined.INSTANCE;
    }

    /**
     * console.count(label)
     * Increment and print the named counter.
     */
    public JSValue count(JSContext context, JSValue thisArg, JSValue[] args) {
        String label = args.length > 0 && !(args[0] instanceof JSUndefined)
                ? JSTypeConversions.toString(context, args[0]).value()
                : "default";
        int count = counters.merge(label, 1, Integer::sum);
        out.print(getGroupIndent());
        out.println(label + ": " + count);
        return JSUndefined.INSTANCE;
    }

    /**
     * console.countReset(label)
     * Reset the named counter.
     */
    public JSValue countReset(JSContext context, JSValue thisArg, JSValue[] args) {
        String label = args.length > 0 && !(args[0] instanceof JSUndefined)
                ? JSTypeConversions.toString(context, args[0]).value()
                : "default";
        counters.remove(label);
        return JSUndefined.INSTANCE;
    }

    /**
     * console.debug(...args)
     * Same as console.log.
     */
    public JSValue debug(JSContext context, JSValue thisArg, JSValue[] args) {
        return log(context, thisArg, args);
    }

    /**
     * console.dir(obj)
     * Print object representation.
     */
    public JSValue dir(JSContext context, JSValue thisArg, JSValue[] args) {
        return log(context, thisArg, args);
    }

    /**
     * console.dirxml(...args)
     * Same as console.dir.
     */
    public JSValue dirxml(JSContext context, JSValue thisArg, JSValue[] args) {
        return dir(context, thisArg, args);
    }

    /**
     * console.error(...args)
     * Print to stderr.
     */
    public JSValue error(JSContext context, JSValue thisArg, JSValue[] args) {
        err.print(getGroupIndent());
        for (int i = 0; i < args.length; i++) {
            if (i > 0) err.print(" ");
            err.print(formatArgument(context, args[i]));
        }
        err.println();
        return JSUndefined.INSTANCE;
    }

    private String formatArgument(JSContext context, JSValue value) {
        return formatValue(context, value, true, new IdentityHashMap<>(), 0);
    }

    private String formatArray(
            JSContext context,
            JSArray array,
            IdentityHashMap<JSObject, Integer> printStack,
            int depth) {
        List<String> items = new ArrayList<>();
        long length = array.getLength();
        List<Long> indexKeys = new ArrayList<>();
        for (PropertyKey key : array.getOwnPropertyKeys()) {
            if (isArrayIndexKey(key)) {
                long index = key.isIndex() ? key.asIndex() : Long.parseLong(key.asString());
                if (index >= 0 && index < length) {
                    indexKeys.add(index);
                }
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
                items.add(index + ": " + formatValue(context, array.get(index), false, printStack, depth + 1));
            }
            if (indexKeys.size() > maxIndexItems) {
                items.add(formatMoreItems(indexKeys.size() - maxIndexItems));
            }
        } else {
            int maxDenseItems = (int) Math.min(contiguousCount, MAX_PRINT_ITEM_COUNT);
            for (int i = 0; i < maxDenseItems; i++) {
                items.add(formatValue(context, array.get(i), false, printStack, depth + 1));
            }
            if (contiguousCount > maxDenseItems) {
                items.add(formatMoreItems(contiguousCount - maxDenseItems));
            }
            if (length > contiguousCount) {
                items.add(formatEmptyItems(length - contiguousCount));
            }
        }

        for (PropertyKey key : array.getOwnPropertyKeys()) {
            if (isArrayIndexKey(key) || "length".equals(key.asString())) {
                continue;
            }
            PropertyDescriptor descriptor = array.getOwnPropertyDescriptor(key);
            if (descriptor != null && !descriptor.isEnumerable()) {
                continue;
            }
            items.add(formatPropertyEntry(context, array, key, descriptor, printStack, depth + 1));
        }

        return items.isEmpty() ? "[ ]" : "[ " + String.join(", ", items) + " ]";
    }

    private String formatEmptyItems(long count) {
        return "<" + count + " empty item" + (count > 1 ? "s" : "") + ">";
    }

    private List<String> formatEnumerableProperties(
            JSContext context,
            JSObject object,
            IdentityHashMap<JSObject, Integer> printStack,
            int depth) {
        List<String> entries = new ArrayList<>();
        int enumerableCount = 0;
        for (PropertyKey key : object.getOwnPropertyKeys()) {
            PropertyDescriptor descriptor = object.getOwnPropertyDescriptor(key);
            if (descriptor != null && !descriptor.isEnumerable()) {
                continue;
            }
            enumerableCount++;
            if (entries.size() < MAX_PRINT_ITEM_COUNT) {
                entries.add(formatPropertyEntry(context, object, key, descriptor, printStack, depth));
            }
        }
        if (enumerableCount > MAX_PRINT_ITEM_COUNT) {
            entries.add(formatMoreItems(enumerableCount - MAX_PRINT_ITEM_COUNT));
        }
        return entries;
    }

    private String formatError(JSObject error) {
        JSValue stackValue = error.get("stack");
        JSValue nameValue = error.get(PropertyKey.NAME);
        JSValue messageValue = error.get(PropertyKey.MESSAGE);
        String name = nameValue instanceof JSString s ? s.value() : "Error";
        String message = messageValue instanceof JSString s ? s.value() : "";
        StringBuilder stringBuilder = new StringBuilder();
        if (message.isEmpty()) {
            stringBuilder.append(name);
        } else {
            stringBuilder.append(name).append(": ").append(message);
        }
        if (stackValue instanceof JSString s) {
            String stack = s.value();
            if (!stack.isEmpty() && stack.charAt(stack.length() - 1) == '\n') {
                stack = stack.substring(0, stack.length() - 1);
            }
            stringBuilder.append('\n').append(stack);
        }
        return stringBuilder.toString();
    }

    private String formatFunction(JSFunction function) {
        String functionName = function.getName();
        if (functionName == null || functionName.isEmpty()) {
            functionName = "(anonymous)";
        }
        return "[Function " + functionName + "]";
    }

    private String formatMap(
            JSContext context,
            JSMap map,
            IdentityHashMap<JSObject, Integer> printStack,
            int depth) {
        int size = map.size();
        if (depth >= MAX_PRINT_DEPTH) {
            return "[Map]";
        }
        List<String> items = new ArrayList<>();
        int count = 0;
        for (Map.Entry<JSMap.KeyWrapper, JSValue> entry : map.entries()) {
            if (count >= MAX_PRINT_ITEM_COUNT) {
                items.add(formatMoreItems(size - count));
                break;
            }
            String key = formatValue(context, entry.getKey().value(), false, printStack, depth + 1);
            String value = formatValue(context, entry.getValue(), false, printStack, depth + 1);
            items.add(key + " => " + value);
            count++;
        }
        return "Map(" + size + ") " +
                (items.isEmpty() ? "{  }" : "{ " + String.join(", ", items) + " }");
    }

    private String formatMoreItems(long count) {
        return "... " + count + " more item" + (count > 1 ? "s" : "");
    }

    private String formatObject(
            JSContext context,
            JSObject object,
            IdentityHashMap<JSObject, Integer> printStack,
            int depth) {
        Integer stackIndex = printStack.get(object);
        if (stackIndex != null) {
            return "[circular " + stackIndex + "]";
        }

        if (object instanceof JSArray array) {
            printStack.put(object, printStack.size());
            try {
                return formatArray(context, array, printStack, depth);
            } finally {
                printStack.remove(object);
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
            JSValue isoValue = DatePrototype.toISOString(context, object, new JSValue[0]);
            if (!context.hasPendingException() && isoValue instanceof JSString jsString) {
                return jsString.value();
            }
            context.clearPendingException();
        }
        if (object instanceof JSMap map) {
            printStack.put(object, printStack.size());
            try {
                return formatMap(context, map, printStack, depth);
            } finally {
                printStack.remove(object);
            }
        }
        if (object instanceof JSSet set) {
            printStack.put(object, printStack.size());
            try {
                return formatSet(context, set, printStack, depth);
            } finally {
                printStack.remove(object);
            }
        }
        if (object instanceof JSError || object.getPrototype() instanceof JSError) {
            return formatError(object);
        }

        String className = getClassName(object);
        if (depth >= MAX_PRINT_DEPTH) {
            return "[" + className + "]";
        }

        printStack.put(object, printStack.size());
        try {
            List<String> entries = formatEnumerableProperties(context, object, printStack, depth + 1);

            String prefix = "Object".equals(className) ? "" : className + " ";
            return entries.isEmpty()
                    ? prefix + "{  }"
                    : prefix + "{ " + String.join(", ", entries) + " }";
        } finally {
            printStack.remove(object);
        }
    }

    private String formatPropertyEntry(
            JSContext context,
            JSObject object,
            PropertyKey key,
            PropertyDescriptor descriptor,
            IdentityHashMap<JSObject, Integer> printStack,
            int depth) {
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
        JSValue value = descriptor != null && descriptor.hasValue()
                ? descriptor.getValue()
                : object.get(key);
        return keyString + ": " + formatValue(context, value, false, printStack, depth);
    }

    private String formatPropertyKey(PropertyKey key) {
        if (key.isIndex()) {
            return Integer.toString(key.asIndex());
        }
        String propertyString = key.toPropertyString();
        return isAsciiIdentifier(propertyString)
                ? propertyString
                : quoteString(propertyString);
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

    private String formatSet(
            JSContext context,
            JSSet set,
            IdentityHashMap<JSObject, Integer> printStack,
            int depth) {
        int size = set.size();
        if (depth >= MAX_PRINT_DEPTH) {
            return "[Set]";
        }
        List<String> items = new ArrayList<>();
        int count = 0;
        for (JSMap.KeyWrapper wrapper : set.values()) {
            if (count >= MAX_PRINT_ITEM_COUNT) {
                items.add(formatMoreItems(size - count));
                break;
            }
            items.add(formatValue(context, wrapper.value(), false, printStack, depth + 1));
            count++;
        }
        return "Set(" + size + ") " +
                (items.isEmpty() ? "{  }" : "{ " + String.join(", ", items) + " }");
    }

    private String formatTypedArray(JSTypedArray typedArray) {
        String className = getClassName(typedArray);
        int length = typedArray.getLength();
        int max = Math.min(length, MAX_PRINT_ITEM_COUNT);
        List<String> items = new ArrayList<>(max + 1);
        for (int i = 0; i < max; i++) {
            double element = typedArray.getElement(i);
            if (Double.isFinite(element) && element == Math.rint(element)) {
                items.add(Long.toString((long) element));
            } else {
                items.add(JSNumber.of(element).toString());
            }
        }
        if (length > max) {
            items.add(formatMoreItems(length - max));
        }
        return className + "(" + length + ") " +
                (items.isEmpty() ? "[ ]" : "[ " + String.join(", ", items) + " ]");
    }

    /**
     * Format a value for console output.
     */
    public String formatValue(JSContext context, JSValue value) {
        return formatValue(context, value, false, new IdentityHashMap<>(), 0);
    }

    private String formatValue(
            JSContext context,
            JSValue value,
            boolean printTopLevelStringRaw,
            IdentityHashMap<JSObject, Integer> printStack,
            int depth) {
        if (value == null || value instanceof JSNull) {
            return "null";
        }
        if (value instanceof JSUndefined) {
            return "undefined";
        }
        if (value instanceof JSString jsString) {
            return printTopLevelStringRaw
                    ? jsString.value()
                    : quoteString(jsString.value());
        }
        if (value instanceof JSNumber
                || value instanceof JSBoolean) {
            return value.toString();
        }
        if (value instanceof JSBigInt bigInt) {
            return bigInt + "n";
        }
        if (value instanceof JSSymbol symbol) {
            return symbol.toJavaObject();
        }
        if (value instanceof JSObject object) {
            return formatObject(context, object, printStack, depth);
        }
        return String.valueOf(value);
    }

    private String getClassName(JSObject object) {
        if (object instanceof JSStringObject) {
            return "String";
        }
        if (object instanceof JSNumberObject) {
            return "Number";
        }
        if (object instanceof JSBooleanObject) {
            return "Boolean";
        }
        if (object instanceof JSBigIntObject) {
            return "BigInt";
        }
        if (object instanceof JSDate) {
            return "Date";
        }
        if (object instanceof JSTypedArray typedArray) {
            return typedArray.getClass().getSimpleName().replaceFirst("^JS", "");
        }
        if (object instanceof JSArrayBuffer) {
            return "ArrayBuffer";
        }
        if (object instanceof JSSharedArrayBuffer) {
            return "SharedArrayBuffer";
        }
        String simpleName = object.getClass().getSimpleName();
        if (simpleName.startsWith("JS")) {
            return simpleName.substring(2);
        }
        return "Object";
    }

    public PrintStream getErr() {
        return err;
    }

    private String getGroupIndent() {
        return groupDepth > 0 ? "  ".repeat(groupDepth) : "";
    }

    public PrintStream getOut() {
        return out;
    }

    /**
     * console.group(label)
     * Print label and increase indent level.
     */
    public JSValue group(JSContext context, JSValue thisArg, JSValue[] args) {
        if (args.length > 0) {
            out.print(getGroupIndent());
            out.println(formatArgument(context, args[0]));
        }
        groupDepth++;
        return JSUndefined.INSTANCE;
    }

    /**
     * console.groupCollapsed(label)
     * Same as console.group.
     */
    public JSValue groupCollapsed(JSContext context, JSValue thisArg, JSValue[] args) {
        return group(context, thisArg, args);
    }

    /**
     * console.groupEnd()
     * Decrease indent level.
     */
    public JSValue groupEnd(JSContext context, JSValue thisArg, JSValue[] args) {
        if (groupDepth > 0) {
            groupDepth--;
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * console.info(...args)
     * Same as console.log.
     */
    public JSValue info(JSContext context, JSValue thisArg, JSValue[] args) {
        return log(context, thisArg, args);
    }

    private boolean isArrayIndexKey(PropertyKey key) {
        if (key.isIndex()) {
            return true;
        }
        if (!key.isString()) {
            return false;
        }
        String value = key.asString();
        if (value == null || value.isEmpty()) {
            return false;
        }
        if ("0".equals(value)) {
            return true;
        }
        if (value.charAt(0) == '0') {
            return false;
        }
        long index = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            index = index * 10 + (c - '0');
            if (index > 0xFFFF_FFFEL) {
                return false;
            }
        }
        return true;
    }

    private boolean isAsciiIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        char first = value.charAt(0);
        if (!((first >= 'a' && first <= 'z')
                || (first >= 'A' && first <= 'Z')
                || first == '_'
                || first == '$')) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '$')) {
                return false;
            }
        }
        return true;
    }

    /**
     * console.log(...args)
     * Print values to stdout.
     */
    public JSValue log(JSContext context, JSValue thisArg, JSValue[] args) {
        out.print(getGroupIndent());
        for (int i = 0; i < args.length; i++) {
            if (i > 0) out.print(" ");
            out.print(formatArgument(context, args[i]));
        }
        out.println();
        out.flush();
        return JSUndefined.INSTANCE;
    }

    private String quoteString(String value) {
        int printLength = Math.min(value.length(), MAX_PRINT_STRING_LENGTH);
        StringBuilder stringBuilder = new StringBuilder(printLength + 2);
        stringBuilder.append('"');
        for (int i = 0; i < printLength; i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\t' -> stringBuilder.append("\\t");
                case '\r' -> stringBuilder.append("\\r");
                case '\n' -> stringBuilder.append("\\n");
                case '\b' -> stringBuilder.append("\\b");
                case '\f' -> stringBuilder.append("\\f");
                case '\\' -> stringBuilder.append("\\\\");
                case '"' -> stringBuilder.append("\\\"");
                default -> {
                    if (c < 32 || (c >= 0x7F && c <= 0x9F)) {
                        stringBuilder.append(String.format("\\u%04x", (int) c));
                    } else {
                        stringBuilder.append(c);
                    }
                }
            }
        }
        stringBuilder.append('"');
        if (value.length() > MAX_PRINT_STRING_LENGTH) {
            stringBuilder.append("... ").append(value.length() - MAX_PRINT_STRING_LENGTH).append(" more characters");
        }
        return stringBuilder.toString();
    }

    public void setErr(PrintStream err) {
        this.err = Objects.requireNonNullElse(err, System.err);
    }

    public void setOut(PrintStream out) {
        this.out = Objects.requireNonNullElse(out, System.out);
    }

    /**
     * console.table(data)
     * Simplified implementation: same as log.
     */
    public JSValue table(JSContext context, JSValue thisArg, JSValue[] args) {
        return log(context, thisArg, args);
    }

    /**
     * console.time(label)
     * Start a named timer.
     */
    public JSValue time(JSContext context, JSValue thisArg, JSValue[] args) {
        String label = args.length > 0 && !(args[0] instanceof JSUndefined)
                ? JSTypeConversions.toString(context, args[0]).value()
                : "default";
        timers.put(label, System.currentTimeMillis());
        return JSUndefined.INSTANCE;
    }

    /**
     * console.timeEnd(label)
     * Print elapsed time and remove the timer.
     */
    public JSValue timeEnd(JSContext context, JSValue thisArg, JSValue[] args) {
        String label = args.length > 0 && !(args[0] instanceof JSUndefined)
                ? JSTypeConversions.toString(context, args[0]).value()
                : "default";
        Long start = timers.remove(label);
        if (start != null) {
            long elapsed = System.currentTimeMillis() - start;
            out.print(getGroupIndent());
            out.println(label + ": " + elapsed + "ms");
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * console.timeLog(label)
     * Print elapsed time without removing the timer.
     */
    public JSValue timeLog(JSContext context, JSValue thisArg, JSValue[] args) {
        String label = args.length > 0 && !(args[0] instanceof JSUndefined)
                ? JSTypeConversions.toString(context, args[0]).value()
                : "default";
        Long start = timers.get(label);
        if (start != null) {
            long elapsed = System.currentTimeMillis() - start;
            out.print(getGroupIndent());
            out.print(label + ": " + elapsed + "ms");
            for (int i = 1; i < args.length; i++) {
                out.print(" ");
                out.print(formatArgument(context, args[i]));
            }
            out.println();
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * console.trace(...args)
     * Print "Trace:" followed by message.
     */
    public JSValue trace(JSContext context, JSValue thisArg, JSValue[] args) {
        err.print(getGroupIndent());
        err.print("Trace:");
        for (int i = 0; i < args.length; i++) {
            err.print(" ");
            err.print(formatArgument(context, args[i]));
        }
        err.println();
        return JSUndefined.INSTANCE;
    }

    /**
     * console.warn(...args)
     * Print to stderr.
     */
    public JSValue warn(JSContext context, JSValue thisArg, JSValue[] args) {
        err.print(getGroupIndent());
        for (int i = 0; i < args.length; i++) {
            if (i > 0) err.print(" ");
            err.print(formatArgument(context, args[i]));
        }
        err.println();
        return JSUndefined.INSTANCE;
    }
}
