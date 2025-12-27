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
 * Wraps a compiled regular expression with flags.
 */
public final class JSRegExp extends JSObject {
    private final String pattern;
    private final String flags;
    private final RegExpEngine engine;
    private final int lastIndex = 0;

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
            RegExpBytecode bytecode = compiler.compile(this.pattern, this.flags);
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

    /**
     * Get the pattern string.
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Get the flags string.
     */
    public String getFlags() {
        return flags;
    }

    /**
     * Get the RegExp engine.
     */
    public RegExpEngine getEngine() {
        return engine;
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
     * Set lastIndex property.
     */
    public void setLastIndex(int index) {
        set("lastIndex", new JSNumber(index));
    }

    /**
     * Check if global flag is set.
     */
    public boolean isGlobal() {
        return flags.contains("g");
    }

    @Override
    public String toString() {
        return "JSRegExp[/" + pattern + "/" + flags + "]";
    }
}
