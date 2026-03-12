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

import com.caoccao.qjs4j.regexp.RegExpBytecode;
import com.caoccao.qjs4j.regexp.RegExpCompiler;
import com.caoccao.qjs4j.regexp.RegExpEngine;

/**
 * Represents a JavaScript RegExp object.
 * Uses QuickJS-based regex compiler and execution engine.
 */
public final class JSRegExp extends JSObject {
    public static final String NAME = "RegExp";
    private RegExpBytecode bytecode;
    private RegExpEngine engine;
    private String flags;
    private String pattern;

    /**
     * Create a RegExp with pattern and flags.
     */
    public JSRegExp(JSContext context, String pattern, String flags) {
        super(context);
        this.pattern = pattern != null ? pattern : "";
        String rawFlags = flags != null ? flags : "";

        // Compile the pattern to bytecode
        RegExpCompiler compiler = new RegExpCompiler();
        this.bytecode = compiler.compile(this.pattern, rawFlags);
        this.engine = new RegExpEngine(bytecode);
        this.flags = this.bytecode.flagsToString();

        // Per spec, lastIndex is an own data property:
        // writable, non-enumerable, non-configurable.
        this.defineProperty(
                PropertyKey.LAST_INDEX,
                PropertyDescriptor.dataDescriptor(JSNumber.of(0), PropertyDescriptor.DataState.Writable)
        );
    }

    public static JSObject create(JSContext context, JSValue... args) {
        JSValue[] rawArgs = extractRawArgs(context, args);
        if (rawArgs == null) {
            return (JSObject) context.getPendingException();
        }
        return createFromRawArgs(context, rawArgs[0], rawArgs[1]);
    }

    /**
     * ES2024 22.2.3.1 steps 7-10: ToString the raw args and compile the RegExp.
     * Called AFTER RegExpAlloc (prototype resolution).
     */
    public static JSObject createFromRawArgs(JSContext context, JSValue patternValue, JSValue flagsValue) {
        String pattern = "";
        String flags = "";
        if (!(patternValue instanceof JSUndefined)) {
            pattern = JSTypeConversions.toString(context, patternValue).value();
            if (context.hasPendingException()) {
                return (JSObject) context.getPendingException();
            }
        }
        if (!(flagsValue instanceof JSUndefined)) {
            flags = JSTypeConversions.toString(context, flagsValue).value();
            if (context.hasPendingException()) {
                return (JSObject) context.getPendingException();
            }
        }

        try {
            return context.createJSRegExp(pattern, flags);
        } catch (Exception e) {
            return context.throwSyntaxError("Invalid regular expression: /" + pattern + "/: " + e.getMessage());
        }
    }

    /**
     * ES2024 22.2.3.1 steps 4-5: Extract raw pattern/flags values from arguments.
     * This must be called BEFORE RegExpAlloc (prototype resolution) per spec ordering.
     * Returns [patternValue, flagsValue] as raw JSValues (not yet ToString'd).
     */
    public static JSValue[] extractRawArgs(JSContext context, JSValue[] args) {
        JSValue patternArg = args.length > 0 ? args[0] : JSUndefined.INSTANCE;
        JSValue flagsArg = args.length > 1 ? args[1] : JSUndefined.INSTANCE;

        JSValue patternValue;
        JSValue flagsValue;

        if (patternArg instanceof JSRegExp patternRegExp) {
            // ES2024 22.2.3.1 step 4: pattern has [[RegExpMatcher]] internal slot
            // Use internal slots directly, NOT property getters
            patternValue = new JSString(patternRegExp.getPattern());
            if (flagsArg instanceof JSUndefined) {
                flagsValue = new JSString(patternRegExp.getFlags());
            } else {
                flagsValue = flagsArg;
            }
        } else if (isRegExpLike(context, patternArg)) {
            // ES2024 22.2.3.1 step 5: patternIsRegExp but no [[RegExpMatcher]]
            if (context.hasPendingException()) {
                return null;
            }
            if (!(patternArg instanceof JSObject patternObject)) {
                context.throwTypeError("Invalid RegExp pattern");
                return null;
            }
            patternValue = patternObject.get(PropertyKey.fromString("source"));
            if (context.hasPendingException()) {
                return null;
            }
            if (flagsArg instanceof JSUndefined) {
                flagsValue = patternObject.get(PropertyKey.fromString("flags"));
                if (context.hasPendingException()) {
                    return null;
                }
            } else {
                flagsValue = flagsArg;
            }
        } else {
            if (context.hasPendingException()) {
                return null;
            }
            patternValue = patternArg instanceof JSUndefined ? new JSString("") : patternArg;
            flagsValue = flagsArg instanceof JSUndefined ? new JSString("") : flagsArg;
        }

        return new JSValue[]{patternValue, flagsValue};
    }

    private static boolean isRegExpLike(JSContext context, JSValue value) {
        if (!(value instanceof JSObject objectValue)) {
            return false;
        }
        JSValue matchValue = objectValue.get(PropertyKey.SYMBOL_MATCH);
        if (context.hasPendingException()) {
            return false;
        }
        if (!(matchValue instanceof JSUndefined)) {
            return JSTypeConversions.toBoolean(matchValue).value();
        }
        return value instanceof JSRegExp;
    }

    /**
     * Get the bytecode for this regex.
     */
    public RegExpBytecode getBytecode() {
        return bytecode;
    }

    /**
     * Get the execution engine.
     */
    public RegExpEngine getEngine() {
        return engine;
    }

    /**
     * Get the flags string.
     */
    public String getFlags() {
        return flags;
    }

    /**
     * Get lastIndex property.
     */
    public int getLastIndex() {
        JSValue lastIndexValue = get("lastIndex");
        if (lastIndexValue instanceof JSNumber num) {
            return (int) num.value();
        }
        return 0;
    }

    /**
     * Get the pattern string.
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Check if hasIndices flag is set.
     */
    public boolean hasIndices() {
        return bytecode != null && bytecode.hasIndices();
    }

    /**
     * Check if dotAll flag is set.
     */
    public boolean isDotAll() {
        return bytecode != null && bytecode.isDotAll();
    }

    /**
     * Check if global flag is set.
     */
    public boolean isGlobal() {
        return bytecode != null && bytecode.isGlobal();
    }

    /**
     * Check if ignoreCase flag is set.
     */
    public boolean isIgnoreCase() {
        return bytecode != null && bytecode.isIgnoreCase();
    }

    /**
     * Check if multiline flag is set.
     */
    public boolean isMultiline() {
        return bytecode != null && bytecode.isMultiline();
    }

    /**
     * Check if sticky flag is set.
     */
    public boolean isSticky() {
        return bytecode != null && bytecode.isSticky();
    }

    /**
     * Check if unicode flag is set.
     */
    public boolean isUnicode() {
        return bytecode != null && bytecode.isUnicode();
    }

    /**
     * Check if unicodeSets flag is set.
     */
    public boolean isUnicodeSets() {
        return bytecode != null && bytecode.hasUnicodeSets();
    }

    /**
     * Reinitialize this RegExp in-place (AnnexB RegExp.prototype.compile).
     */
    public void reinitialize(String pattern, String flags) {
        String nextPattern = pattern != null ? pattern : "";
        String rawFlags = flags != null ? flags : "";

        RegExpCompiler compiler = new RegExpCompiler();
        RegExpBytecode nextBytecode = compiler.compile(nextPattern, rawFlags);
        RegExpEngine nextEngine = new RegExpEngine(nextBytecode);
        String nextFlags = nextBytecode.flagsToString();

        // Update internal slots only after successful compilation.
        this.pattern = nextPattern;
        this.bytecode = nextBytecode;
        this.engine = nextEngine;
        this.flags = nextFlags;

        // Note: caller is responsible for setting lastIndex (spec step 12:
        // Set(obj, "lastIndex", 0, true) which may throw TypeError if non-writable).
    }

    /**
     * Set lastIndex property.
     */
    public void setLastIndex(int index) {
        set("lastIndex", JSNumber.of(index));
    }

    @Override
    public String toString() {
        return "JSRegExp[/" + pattern + "/" + flags + "]";
    }
}
