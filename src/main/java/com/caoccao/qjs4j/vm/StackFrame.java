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

import com.caoccao.qjs4j.core.JSBytecodeFunction;
import com.caoccao.qjs4j.core.JSFunction;
import com.caoccao.qjs4j.core.JSUndefined;
import com.caoccao.qjs4j.core.JSValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a call frame (activation record) on the call stack.
 */
public final class StackFrame {
    private final JSValue[] arguments;  // Original arguments passed to function
    private final StackFrame caller;
    private final JSValue[] closureVars;
    private final Map<Integer, LocalReference> closedLocals;
    private final JSFunction function;
    private final JSValue[] locals;
    private final JSValue thisArg;
    private int programCounter;

    public StackFrame(JSFunction function, JSValue thisArg, JSValue[] args, StackFrame caller) {
        this.function = function;
        this.thisArg = thisArg;
        this.arguments = args;  // Store original arguments for arguments object

        // Allocate locals array based on function's local count
        // For bytecode functions, get local count from bytecode metadata
        // For native functions, just use args
        int localCount = 0;
        if (function instanceof JSBytecodeFunction bytecodeFunc) {
            localCount = bytecodeFunc.getBytecode().getLocalCount();
        }

        if (localCount > 0) {
            this.locals = new JSValue[localCount];
            // Copy args into the first slots
            System.arraycopy(args, 0, this.locals, 0, Math.min(args.length, localCount));
            // Initialize remaining locals to undefined
            for (int i = args.length; i < localCount; i++) {
                this.locals[i] = JSUndefined.INSTANCE;
            }
        } else {
            // For native functions or functions with no locals
            this.locals = args;
        }

        if (function instanceof JSBytecodeFunction bytecodeFunc && bytecodeFunc.getClosureVars() != null) {
            this.closureVars = bytecodeFunc.getClosureVars();
        } else {
            this.closureVars = new JSValue[0];
        }
        this.closedLocals = new HashMap<>();
        this.programCounter = 0;
        this.caller = caller;
    }

    public void closeLocal(int index) {
        if (index < 0 || index >= locals.length) {
            return;
        }
        closedLocals.put(index, new LocalReference(locals, index));
    }

    public JSValue[] getArguments() {
        return arguments;
    }

    public StackFrame getCaller() {
        return caller;
    }

    public JSFunction getFunction() {
        return function;
    }

    public JSValue[] getLocals() {
        return locals;
    }

    public JSValue getVarRef(int index) {
        LocalReference localReference = closedLocals.get(index);
        if (localReference != null) {
            return localReference.get();
        }
        if (index >= 0 && index < closureVars.length) {
            JSValue value = closureVars[index];
            return value != null ? value : JSUndefined.INSTANCE;
        }
        return JSUndefined.INSTANCE;
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public JSValue getThisArg() {
        return thisArg;
    }

    public void setVarRef(int index, JSValue value) {
        LocalReference localReference = closedLocals.get(index);
        if (localReference != null) {
            localReference.set(value);
            return;
        }
        if (index >= 0 && index < closureVars.length) {
            closureVars[index] = value;
        }
    }

    public void setProgramCounter(int pc) {
        this.programCounter = pc;
    }

    private record LocalReference(JSValue[] storage, int index) {
        private JSValue get() {
            JSValue value = storage[index];
            return value != null ? value : JSUndefined.INSTANCE;
        }

        private void set(JSValue value) {
            storage[index] = value;
        }
    }
}
