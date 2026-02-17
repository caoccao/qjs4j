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

/**
 * Represents a call frame (activation record) on the call stack.
 */
public final class StackFrame {
    private static final JSValue[] EMPTY_VALUES = new JSValue[0];
    private static final VarRef[] EMPTY_VARREFS = new VarRef[0];

    private final JSValue[] arguments;  // Original arguments passed to function
    private final StackFrame caller;
    private final JSValue[] closureVars;
    private final JSFunction function;
    private final JSValue[] locals;
    private final JSValue thisArg;
    private final VarRef[] varRefs;
    private VarRef[] closedVarRefs;
    private VarRef[] localVarRefs;
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

        // Initialize closure variable references
        // Prefer VarRef[] (reference-based) over JSValue[] (value-based)
        if (function instanceof JSBytecodeFunction bytecodeFunc) {
            VarRef[] funcVarRefs = bytecodeFunc.getVarRefs();
            if (funcVarRefs != null) {
                this.varRefs = funcVarRefs;
                this.closureVars = EMPTY_VALUES;
            } else if (bytecodeFunc.getClosureVars() != null) {
                this.closureVars = bytecodeFunc.getClosureVars();
                this.varRefs = EMPTY_VARREFS;
            } else {
                this.closureVars = EMPTY_VALUES;
                this.varRefs = EMPTY_VARREFS;
            }
        } else {
            this.closureVars = EMPTY_VALUES;
            this.varRefs = EMPTY_VARREFS;
        }
        this.programCounter = 0;
        this.caller = caller;
    }

    /**
     * Close a local variable, following QuickJS close_var_refs pattern.
     * Detaches any VarRef for this local from the locals array, freezing its
     * current value. This enables per-iteration binding in for loops:
     * closures from different iterations each get their own frozen value.
     * Also makes the local accessible via getVarRef() for TDZ checks.
     */
    public void closeLocal(int index) {
        if (index < 0 || index >= locals.length) {
            return;
        }

        // Get existing VarRef or create one
        VarRef ref;
        if (localVarRefs != null && index < localVarRefs.length && localVarRefs[index] != null) {
            ref = localVarRefs[index];
        } else {
            ref = getOrCreateLocalVarRef(index);
        }

        // Detach from locals array — copies current value to standalone storage
        ref.close();

        // Clear localVarRefs entry so next FCLOSURE creates a fresh VarRef
        localVarRefs[index] = null;

        // Store the closed VarRef for access via getVarRef (needed for TDZ checks)
        if (closedVarRefs == null) {
            closedVarRefs = new VarRef[locals.length];
        }
        closedVarRefs[index] = ref;
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

    /**
     * Get or create a VarRef for a local variable at the given index.
     * Used during FCLOSURE to create shared references for captured locals.
     * Multiple closures capturing the same local will share the same VarRef.
     */
    public VarRef getOrCreateLocalVarRef(int localIndex) {
        if (localVarRefs == null) {
            localVarRefs = new VarRef[locals.length];
        }
        if (localVarRefs[localIndex] == null) {
            localVarRefs[localIndex] = new VarRef(locals, localIndex);
        }
        return localVarRefs[localIndex];
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public JSValue getThisArg() {
        return thisArg;
    }

    /**
     * Get a closure variable by index.
     * Checks closed VarRefs (from CLOSE_LOC), then VarRefs (from FCLOSURE),
     * then falls back to direct JSValue access (class methods, legacy).
     */
    public JSValue getVarRef(int index) {
        // Check closed VarRefs (from CLOSE_LOC — promotes local to var ref space)
        if (closedVarRefs != null && index >= 0 && index < closedVarRefs.length) {
            VarRef ref = closedVarRefs[index];
            if (ref != null) {
                return ref.get();
            }
        }
        // Use VarRef if available (reference-based capture from FCLOSURE)
        if (index >= 0 && index < varRefs.length) {
            return varRefs[index].get();
        }
        // Fall back to direct JSValue access (class methods, legacy)
        if (index >= 0 && index < closureVars.length) {
            JSValue value = closureVars[index];
            return value != null ? value : JSUndefined.INSTANCE;
        }
        return JSUndefined.INSTANCE;
    }

    /**
     * Get the VarRef at the given closure variable index.
     * Used during FCLOSURE to share VarRef objects with child closures.
     */
    public VarRef getVarRefCell(int index) {
        if (index >= 0 && index < varRefs.length) {
            return varRefs[index];
        }
        return null;
    }

    public void setProgramCounter(int pc) {
        this.programCounter = pc;
    }

    /**
     * Set a closure variable by index.
     * Checks closed VarRefs, then VarRefs, then falls back to direct access.
     */
    public void setVarRef(int index, JSValue value) {
        // Check closed VarRefs (from CLOSE_LOC)
        if (closedVarRefs != null && index >= 0 && index < closedVarRefs.length) {
            VarRef ref = closedVarRefs[index];
            if (ref != null) {
                ref.set(value);
                return;
            }
        }
        // Use VarRef if available (reference-based capture)
        if (index >= 0 && index < varRefs.length) {
            varRefs[index].set(value);
            return;
        }
        // Fall back to direct JSValue access (class methods, legacy)
        if (index >= 0 && index < closureVars.length) {
            closureVars[index] = value;
        }
    }
}
