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

import com.caoccao.qjs4j.vm.Bytecode;

/**
 * Represents a JavaScript function compiled to bytecode.
 */
public final class JSBytecodeFunction implements JSFunction {
    private final Bytecode bytecode;
    private final JSValue[] closureVars;
    private final JSObject prototype;
    private final String name;
    private final int length;

    public JSBytecodeFunction(Bytecode bytecode, String name, int length) {
        this.bytecode = bytecode;
        this.name = name;
        this.length = length;
        this.closureVars = new JSValue[0];
        this.prototype = null;
    }

    public Bytecode getBytecode() {
        return bytecode;
    }

    public JSValue[] getClosureVars() {
        return closureVars;
    }

    @Override
    public JSValue call(JSContext ctx, JSValue thisArg, JSValue[] args) {
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public JSValueType type() {
        return JSValueType.FUNCTION;
    }

    @Override
    public Object toJavaObject() {
        return this;
    }
}
