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

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of the JavaScript console API.
 * Supports configurable output streams for stdout and stderr.
 */
public final class JSConsole {
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
                err.print(formatValue(context, args[i]));
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
            err.print(formatValue(context, args[i]));
        }
        err.println();
        return JSUndefined.INSTANCE;
    }

    /**
     * Format a value for console output.
     */
    public String formatValue(JSContext context, JSValue value) {
        if (value == null) {
            return "null";
        } else if (value.isSymbol()) {
            return value.toJavaObject().toString();
        } else if (value.isNullOrUndefined()
                || value.isBigInt()
                || value.isBigIntObject()
                || value.isBoolean()
                || value.isBooleanObject()
                || value.isNumber()
                || value.isNumberObject()
                || value.isString()
                || value.isStringObject()) {
            return JSTypeConversions.toString(context, value).value();
        } else if (value instanceof JSArray arr) {
            StringBuilder sb = new StringBuilder("[");
            for (long i = 0; i < arr.getLength(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatValue(context, arr.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else if (value instanceof JSObject) {
            return "[object Object]";
        }
        return String.valueOf(value);
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
            out.println(formatValue(context, args[0]));
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

    /**
     * console.log(...args)
     * Print values to stdout.
     */
    public JSValue log(JSContext context, JSValue thisArg, JSValue[] args) {
        out.print(getGroupIndent());
        for (int i = 0; i < args.length; i++) {
            if (i > 0) out.print(" ");
            out.print(formatValue(context, args[i]));
        }
        out.println();
        return JSUndefined.INSTANCE;
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
                out.print(formatValue(context, args[i]));
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
            err.print(formatValue(context, args[i]));
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
            err.print(formatValue(context, args[i]));
        }
        err.println();
        return JSUndefined.INSTANCE;
    }
}
