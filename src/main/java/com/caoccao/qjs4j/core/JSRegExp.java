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
import com.caoccao.qjs4j.regexp.RegExpBytecode;
import com.caoccao.qjs4j.regexp.RegExpCompiler;
import com.caoccao.qjs4j.regexp.RegExpEngine;

/**
 * Represents a JavaScript RegExp object.
 * Uses QuickJS-based regex compiler and execution engine.
 */
public final class JSRegExp extends JSObject {
    private final RegExpBytecode bytecode;
    private final RegExpEngine engine;
    private final String flags;
    private final String pattern;

    /**
     * Create a RegExp with pattern and flags.
     */
    public JSRegExp(String pattern, String flags) {
        super();
        this.pattern = pattern != null ? pattern : "";
        this.flags = flags != null ? flags : "";

        // Compile the pattern to bytecode
        try {
            RegExpCompiler compiler = new RegExpCompiler();
            this.bytecode = compiler.compile(this.pattern, this.flags);
            this.engine = new RegExpEngine(bytecode);
        } catch (Exception e) {
            throw new RuntimeException("Invalid regular expression: " + e.getMessage(), e);
        }

        // Set properties
        this.set("source", new JSString(this.pattern));
        this.set("flags", new JSString(this.flags));
        this.set("global", JSBoolean.valueOf(this.flags.contains("g")));
        this.set("ignoreCase", JSBoolean.valueOf(this.flags.contains("i")));
        this.set("multiline", JSBoolean.valueOf(this.flags.contains("m")));
        this.set("dotAll", JSBoolean.valueOf(this.flags.contains("s")));
        this.set("unicode", JSBoolean.valueOf(this.flags.contains("u")));
        this.set("sticky", JSBoolean.valueOf(this.flags.contains("y")));
        this.set("lastIndex", new JSNumber(0));
    }

    public static JSRegExp createRegExp(JSContext context, JSValue... args) {
        String pattern = "";
        String flags = "";
        if (args.length > 0) {
            // First argument is the pattern
            JSValue patternArg = args[0];
            if (patternArg instanceof JSRegExp existingRegExp) {
                // Copy from existing RegExp
                pattern = existingRegExp.getPattern();
                flags = args.length > 1 && !(args[1] instanceof JSUndefined)
                        ? JSTypeConversions.toString(context, args[1]).value()
                        : existingRegExp.getFlags();
            } else if (!(patternArg instanceof JSUndefined)) {
                pattern = JSTypeConversions.toString(context, patternArg).value();
                if (args.length > 1 && !(args[1] instanceof JSUndefined)) {
                    flags = JSTypeConversions.toString(context, args[1]).value();
                }
            }
        }
        try {
            return new JSRegExp(pattern, flags);
        } catch (Exception e) {
            throw new JSException(context.throwSyntaxError("Invalid regular expression: " + e.getMessage()));
        }
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
     * Check if global flag is set.
     */
    public boolean isGlobal() {
        return flags.contains("g");
    }

    /**
     * Check if sticky flag is set.
     */
    public boolean isSticky() {
        return flags.contains("y");
    }

    /**
     * Set lastIndex property.
     */
    public void setLastIndex(int index) {
        set("lastIndex", new JSNumber(index));
    }

    @Override
    public String toString() {
        return "JSRegExp[/" + pattern + "/" + flags + "]";
    }
}