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

import com.caoccao.qjs4j.builtins.AtomicsObject;

/**
 * Runtime configuration options.
 */
public class JSRuntimeOptions {
    public static final long DEFAULT_MAX_MEMORY_USAGE = 64 * 1024 * 1024; // 64 MB default
    public static final long DEFAULT_MAX_STACK_SIZE = 256 * 1024; // 256 KB default
    protected AtomicsObject atomicsObject;
    protected long maxMemoryUsage;
    protected long maxStackSize;

    public JSRuntimeOptions() {
        atomicsObject = null;
        maxMemoryUsage = DEFAULT_MAX_MEMORY_USAGE;
        maxStackSize = DEFAULT_MAX_STACK_SIZE;
    }

    public AtomicsObject getAtomicsObject() {
        return atomicsObject;
    }

    public long getMaxMemoryUsage() {
        return maxMemoryUsage;
    }

    public long getMaxStackSize() {
        return maxStackSize;
    }

    public JSRuntimeOptions setAtomicsObject(AtomicsObject atomicsObject) {
        this.atomicsObject = atomicsObject;
        return this;
    }

    public JSRuntimeOptions setMaxMemoryUsage(long maxMemoryUsage) {
        this.maxMemoryUsage = maxMemoryUsage;
        return this;
    }

    public JSRuntimeOptions setMaxStackSize(long maxStackSize) {
        this.maxStackSize = maxStackSize;
        return this;
    }
}
