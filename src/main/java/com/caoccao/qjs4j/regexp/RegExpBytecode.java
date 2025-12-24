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

package com.caoccao.qjs4j.regexp;

/**
 * Represents compiled regex bytecode.
 */
public final class RegExpBytecode {
    private final byte[] instructions;
    private final boolean global;
    private final boolean ignoreCase;
    private final boolean multiline;
    private final boolean dotAll;
    private final boolean unicode;
    private final boolean sticky;

    public RegExpBytecode(byte[] instructions, boolean global, boolean ignoreCase,
                          boolean multiline, boolean dotAll, boolean unicode, boolean sticky) {
        this.instructions = instructions;
        this.global = global;
        this.ignoreCase = ignoreCase;
        this.multiline = multiline;
        this.dotAll = dotAll;
        this.unicode = unicode;
        this.sticky = sticky;
    }

    public byte[] getInstructions() {
        return instructions;
    }

    public boolean isGlobal() {
        return global;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public boolean isMultiline() {
        return multiline;
    }
}
