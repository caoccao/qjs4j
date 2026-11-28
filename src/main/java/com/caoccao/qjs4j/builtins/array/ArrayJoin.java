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

package com.caoccao.qjs4j.builtins.array;

import com.caoccao.qjs4j.builtins.text.StringResultBuilder;
import com.caoccao.qjs4j.core.*;

import java.util.Set;

/** Builds join and toLocaleString results from the length captured before coercing the separator. */
final class ArrayJoin {
    private static final JSString COMMA = new JSString(",");
    private final StringBuilder chunk = new StringBuilder();
    private final StringResultBuilder rope;

    private ArrayJoin(JSContext context) {
        rope = new StringResultBuilder(context);
    }

    private boolean append(JSString text) {
        if (rope.exceedsMaxLength((long) chunk.length() + text.length())) {
            return false;
        }
        // Keep large rope operands intact. Buffer short fragments to avoid a rope node for each separator.
        if (text.length() >= 1024) {
            flush();
            rope.append(text);
        } else {
            chunk.append(text.value());
            if (chunk.length() >= 4096) {
                flush();
            }
        }
        return true;
    }

    private boolean appendElement(JSContext context, JSValue element) {
        if (element instanceof JSNumber number) {
            // Integral 32-bit values have the same decimal representation in ECMAScript and Java,
            // including -0. Write them directly to the chunk without allocating an intermediate string.
            double value = number.value();
            int integer = (int) value;
            if (value == integer) {
                chunk.append(integer);
                if (rope.exceedsMaxLength(chunk.length())) {
                    return false;
                }
                if (chunk.length() >= 4096) {
                    flush();
                }
                return true;
            }
        }
        JSString text = JSTypeConversions.toString(context, element);
        return !context.hasPendingException() && append(text);
    }

    private JSString finish() {
        if (rope.length() == 0) {
            return new JSString(chunk.toString());
        }
        flush();
        return rope.build();
    }

    private void flush() {
        if (!chunk.isEmpty()) {
            rope.append(chunk.toString());
            chunk.setLength(0);
        }
    }

    static JSValue join(JSContext context, ArrayLikeElements elements, JSValue[] args, boolean localized) {
        JSString separator = localized || args.length == 0 || args[0] instanceof JSUndefined
                ? COMMA
                : JSTypeConversions.toString(context, args[0]);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        // A separator can call join on the same receiver. Only register it after coercion.
        Set<JSObject> stack = context.getArrayJoinStack();
        if (!stack.add(elements.object)) {
            return JSString.EMPTY;
        }
        try {
            ArrayJoin result = new ArrayJoin(context);
            for (long i = 0; i < elements.length; i++) {
                context.checkExecutionInterrupt();
                if (i != 0 && !result.append(separator)) {
                    return context.getPendingException();
                }
                JSValue element = localized ? elements.getProperty(i) : elements.get(i);
                if (context.hasPendingException()) {
                    return context.getPendingException();
                }
                if (!element.isNullOrUndefined()) {
                    if (localized) {
                        element = localize(context, element, args);
                        if (context.hasPendingException()) {
                            return context.getPendingException();
                        }
                    }
                    if (!result.appendElement(context, element)) {
                        return context.getPendingException();
                    }
                }
            }
            return result.finish();
        } finally {
            stack.remove(elements.object);
        }
    }

    private static JSValue localize(JSContext context, JSValue element, JSValue[] args) {
        JSObject boxed = JSTypeConversions.toObject(context, element);
        if (boxed == null || context.hasPendingException()) {
            return context.getPendingException();
        }
        JSValue method = boxed.get(PropertyKey.fromString("toLocaleString"), element);
        if (context.hasPendingException()) {
            return context.getPendingException();
        }
        if (!JSTypeChecking.isCallable(method)) {
            return context.throwTypeError("toLocaleString is not a function");
        }
        return JSCall.call(context, method, element, JSValue.argumentOrUndefined(args, 0),
                JSValue.argumentOrUndefined(args, 1));
    }
}
