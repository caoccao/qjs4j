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

package com.caoccao.qjs4j.vm;

import com.caoccao.qjs4j.BaseTest;
import com.caoccao.qjs4j.compilation.compiler.BytecodeEmitter;
import com.caoccao.qjs4j.core.*;

/** Runs a hand-assembled opcode sequence in the test context. */
abstract class OpcodeTest extends BaseTest {
    protected JSValue executeWithStrict(boolean strict, BytecodeEmitter emitter, int localCount, JSValue[] closureVars,
            JSValue thisArg, JSValue... args) {
        JSBytecodeFunction function = new JSBytecodeFunction(context, emitter.build(localCount), "test", args.length,
                closureVars, null, true, false, false, false, strict, "function test() { [bytecode] }");
        return context.getVirtualMachine().execute(function, thisArg, args);
    }
}
